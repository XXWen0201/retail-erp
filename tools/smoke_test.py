# -*- coding: utf-8 -*-
"""
后端端到端冒烟测试

覆盖：认证 → 基础数据 → 采购入库 → 销售出库 → 退货 → 盘点 → 预警 → 报表 → AI 补货。
只依赖标准库，避免 sandbox 里装包。

用法：
    python tools/smoke_test.py            # 只跑不依赖外部网络的部分
    python tools/smoke_test.py --with-ai  # 额外跑一次真实的模型问答
"""
import http.cookiejar
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"

cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
token = None

PASS, FAIL = [], []


def call(method, path, body=None, auth=True, timeout=180):
    url = BASE + path
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if auth and token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with opener.open(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        return {"code": -e.code, "message": e.read().decode("utf-8")[:300]}
    except Exception as e:  # noqa: BLE001
        return {"code": -999, "message": "%s: %s" % (type(e).__name__, e)}


def check(name, ok, detail=""):
    tag = "PASS" if ok else "FAIL"
    (PASS if ok else FAIL).append(name)
    print("[%s] %-38s %s" % (tag, name, detail))


def ok(resp, expect=200):
    return isinstance(resp, dict) and resp.get("code") == expect


def as_id(data):
    """新建类接口返回的是 id（R<Long>），详情类接口返回对象，两种都兼容"""
    if isinstance(data, int):
        return data
    if isinstance(data, dict):
        return data.get("id")
    return None


# ---------------------------------------------------------------- 认证
print("=" * 78)
print("1. 认证 / 鉴权")
print("=" * 78)

for user in ("admin", "manager", "staff"):
    r = call("POST", "/auth/login", {"username": user, "password": "123456"}, auth=False)
    if user == "admin":
        token = (r.get("data") or {}).get("accessToken")
    check("登录 %s" % user, ok(r), (r.get("data") or {}).get("user", {}).get("realName", r.get("message", "")))

r = call("POST", "/auth/login", {"username": "admin", "password": "wrongpass"}, auth=False)
check("错误密码被拒绝(code=4010)", r.get("code") == 4010, r.get("message", ""))

saved, token = token, ""
r = call("GET", "/products?page=1&size=1")
check("无令牌访问返回 401", r.get("code") == 401, r.get("message", ""))
token = saved

r = call("GET", "/auth/me")
check("获取当前用户", ok(r), (r.get("data") or {}).get("username", ""))

# ---------------------------------------------------------------- 基础数据
print()
print("=" * 78)
print("2. 基础数据")
print("=" * 78)

r = call("GET", "/categories")
cats = r.get("data") or []
check("分类列表", ok(r) and len(cats) > 0, "%d 个分类" % len(cats))

r = call("GET", "/suppliers/options")
sups = r.get("data") or []
check("供应商下拉", ok(r) and len(sups) > 0, "%d 家" % len(sups))
sup_id = sups[0]["value"] if sups else None

r = call("GET", "/products?page=1&size=10")
prods = (r.get("data") or {}).get("records") or []
check("商品分页", ok(r) and len(prods) == 10, "总数 %s" % (r.get("data") or {}).get("total"))

r = call("GET", "/products?page=1&size=50&lowStockOnly=true")
low = (r.get("data") or {}).get("records") or []
check("低库存筛选", ok(r), "%d 个商品低于下限" % len(low))

# 挑一个库存充足的商品做后续业务测试，避免把演示用的低库存数据搅乱
target = max(prods, key=lambda p: p.get("stockQuantity") or 0)
stock_before = int(target.get("stockQuantity") or 0)
print("    测试用商品：%s（id=%s，当前库存 %s）" % (target["name"], target["id"], stock_before))

# ---------------------------------------------------------------- 采购入库
print()
print("=" * 78)
print("3. 采购 → 入库（批次 + 库存 + 流水）")
print("=" * 78)

r = call("POST", "/purchases", {
    "supplierId": sup_id,
    "orderDate": "2026-09-30",
    "remark": "冒烟测试采购单",
    "items": [{"productId": target["id"], "quantity": 10, "price": target.get("purchasePrice") or 1}],
})
purchase_id = as_id(r.get("data"))
check("新建采购单", ok(r) and purchase_id, "id=%s" % purchase_id)

r = call("POST", "/purchases/%s/receive" % purchase_id)
check("采购入库", ok(r), r.get("message", ""))

r = call("GET", "/products/%s" % target["id"])
after_in = int((r.get("data") or {}).get("stockQuantity") or 0)
check("入库后库存 +10", after_in == stock_before + 10, "%d → %d" % (stock_before, after_in))

r = call("GET", "/stock/batches?productId=%s&page=1&size=5" % target["id"])
batches = (r.get("data") or {}).get("records") or []
check("批次已生成", ok(r) and len(batches) > 0, "最新批次 %s" % (batches[0]["batchNo"] if batches else "无"))

r = call("GET", "/stock/records?productId=%s&page=1&size=5" % target["id"])
recs = (r.get("data") or {}).get("records") or []
check("库存流水已写入", ok(r) and len(recs) > 0,
      "最新：%s %+d → %s" % (recs[0].get("bizTypeLabel"), recs[0].get("changeQuantity"), recs[0].get("afterQuantity")) if recs else "")

# ---------------------------------------------------------------- 销售出库
print()
print("=" * 78)
print("4. 销售 → 出库（FEFO 扣批次）")
print("=" * 78)

r = call("POST", "/sales", {
    "customerName": "冒烟测试客户",
    "orderDate": "2026-09-30",
    "discountAmount": 0,
    "items": [{"productId": target["id"], "quantity": 5, "price": target.get("salePrice") or 2}],
})
sale_id = as_id(r.get("data"))
check("新建销售单并出库", ok(r) and sale_id, "id=%s %s" % (sale_id, r.get("message", "")))

r = call("GET", "/products/%s" % target["id"])
after_out = int((r.get("data") or {}).get("stockQuantity") or 0)
check("出库后库存 -5", after_out == after_in - 5, "%d → %d" % (after_in, after_out))

# 库存不足必须被挡住
r = call("POST", "/sales", {
    "customerName": "超卖测试",
    "orderDate": "2026-09-30",
    "items": [{"productId": target["id"], "quantity": 999999, "price": 1}],
})
check("超量销售被拒绝(code=4001)", r.get("code") == 4001, r.get("message", ""))

# 作废回滚
r = call("POST", "/sales/%s/cancel" % sale_id)
check("销售作废", ok(r), r.get("message", ""))
r = call("GET", "/products/%s" % target["id"])
after_cancel = int((r.get("data") or {}).get("stockQuantity") or 0)
check("作废后库存回滚 +5", after_cancel == after_out + 5, "%d → %d" % (after_out, after_cancel))

# ---------------------------------------------------------------- 退货
print()
print("=" * 78)
print("5. 退货（原路退回批次）")
print("=" * 78)

r = call("POST", "/returns", {
    "returnType": "PURCHASE_RETURN",
    "returnDate": "2026-09-30",
    "reason": "冒烟测试采购退货",
    "items": [{"productId": target["id"], "quantity": 2, "price": target.get("purchasePrice") or 1}],
})
ret_id = as_id(r.get("data"))
r2 = call("GET", "/returns/%s" % ret_id) if ret_id else {}
ret_no = ((r2.get("data") or {}) or {}).get("orderNo", "")
check("采购退货（库存减少）", ok(r) and ret_id, "单号 %s" % ret_no)

r = call("GET", "/products/%s" % target["id"])
after_return = int((r.get("data") or {}).get("stockQuantity") or 0)
check("退货后库存 -2", after_return == after_cancel - 2, "%d → %d" % (after_cancel, after_return))

r = call("GET", "/returns?page=1&size=5")
check("退货单分页", ok(r), "总数 %s" % (r.get("data") or {}).get("total"))

# ---------------------------------------------------------------- 盘点
print()
print("=" * 78)
print("6. 盘点（差异追溯）")
print("=" * 78)

r = call("POST", "/stock-checks", {
    "checkDate": "2026-09-30",
    "remark": "冒烟测试盘点",
    "productIds": [target["id"]],
})
check_id = as_id(r.get("data"))
r = call("GET", "/stock-checks/%s" % check_id) if check_id else {}
check_no = ((r.get("data") or {}) or {}).get("checkNo", "")
check("新建盘点单", ok(r) and check_id, "单号 %s" % check_no)

r = call("GET", "/stock-checks/%s" % check_id)
items = (r.get("data") or {}).get("items") or []
check("盘点明细已抓账面数", ok(r) and len(items) == 1,
      "账面 %s" % (items[0].get("bookQuantity") if items else "-"))

if items:
    book = int(items[0].get("bookQuantity") or 0)
    actual = book - 3
    r = call("PUT", "/stock-checks/%s/items" % check_id, {
        "items": [{"id": items[0]["id"], "actualQuantity": actual, "reason": "冒烟测试盘亏 3 个"}]
    })
    check("录入实盘数", ok(r), "账面 %d → 实盘 %d" % (book, actual))

    r = call("POST", "/stock-checks/%s/finish" % check_id)
    check("提交盘点", ok(r), r.get("message", ""))

    r = call("GET", "/products/%s" % target["id"])
    after_check = int((r.get("data") or {}).get("stockQuantity") or 0)
    check("盘点后库存按实盘数调平", after_check == actual, "%d → %d" % (after_return, after_check))

    r = call("GET", "/stock/records?productId=%s&page=1&size=3" % target["id"])
    r0 = ((r.get("data") or {}).get("records") or [{}])[0]
    check("盘点差异已写流水", "CHECK" in str(r0.get("bizType")),
          "%s %+s" % (r0.get("bizTypeLabel"), r0.get("changeQuantity")))

# ---------------------------------------------------------------- 预警
print()
print("=" * 78)
print("7. 库存预警（幂等扫描）")
print("=" * 78)

r = call("POST", "/alerts/scan")
d = r.get("data") or {}
check("手动扫描", ok(r), "新增 %s / 更新 %s / 自动关闭 %s / 未处理 %s"
      % (d.get("createdCount"), d.get("updatedCount"), d.get("closedCount"), d.get("unhandledTotal")))

r = call("GET", "/alerts/summary")
s = r.get("data") or {}
check("预警汇总", ok(r), "下限 %s / 断货 %s / 超储 %s / 临期 %s / 过期 %s"
      % (s.get("lowStockCount"), s.get("outOfStockCount"), s.get("overStockCount"),
         s.get("nearExpiryCount"), s.get("expiredCount")))

r = call("GET", "/alerts?page=1&size=5&status=UNHANDLED")
alerts = (r.get("data") or {}).get("records") or []
check("预警分页", ok(r), "共 %s 条未处理" % (r.get("data") or {}).get("total"))

if alerts:
    aid = alerts[0]["id"]
    r = call("PUT", "/alerts/%s/handle" % aid, {"handleRemark": "冒烟测试：已处理"})
    check("处理预警", ok(r), r.get("message", ""))

# ---------------------------------------------------------------- 报表
print()
print("=" * 78)
print("8. 统计报表")
print("=" * 78)

r = call("GET", "/reports/dashboard")
d = r.get("data") or {}
check("工作台汇总", ok(r), "今日销售 %s / 未处理预警 %s / 库存成本 %s"
      % (d.get("todaySalesAmount"), d.get("unhandledAlertCount"), d.get("totalStockValue")))
check("工作台含销售趋势", len(d.get("salesTrend") or []) > 0, "%d 个数据点" % len(d.get("salesTrend") or []))

for path, key in (("purchase", "totalAmount"), ("sales", "totalAmount"), ("profit", "totalProfit")):
    r = call("GET", "/reports/%s?startDate=2026-08-01&endDate=2026-09-30&groupBy=day" % path)
    d = r.get("data") or {}
    check("%s 报表" % path, ok(r), "%s=%s，趋势 %d 点"
          % (key, d.get(key), len(d.get("trend") or [])))

r = call("GET", "/reports/stock")
d = r.get("data") or {}
check("stock 报表", ok(r), "SKU %s / 库存成本 %s / 低库存 %s 个"
      % (d.get("skuCount"), d.get("totalCostValue"), len(d.get("lowStockList") or [])))

r = call("GET", "/reports/top-products?startDate=2026-08-01&endDate=2026-09-30")
check("商品销量排行", ok(r), "%d 条" % len(r.get("data") or []))

# ---------------------------------------------------------------- AI（本地算法）
print()
print("=" * 78)
print("9. AI 智能补货（本地算法部分，不依赖模型）")
print("=" * 78)

r = call("GET", "/ai/replenish/suggestions?days=30")
d = r.get("data") or {}
items = d.get("items") or []
check("补货建议列表", ok(r), "算法：%s，建议 %d 个商品补货" % (d.get("algorithm"), len(items)))
if items:
    it = items[0]
    print("    示例：%s 当前 %s / 补货点 %s / 建议补货 %s / 紧急度 %s"
          % (it.get("productName"), it.get("currentStock"), it.get("reorderPoint"),
             it.get("suggestQuantity"), it.get("urgency")))

r = call("GET", "/ai/logs?page=1&size=5")
check("AI 调用记录", ok(r), "共 %s 条" % (r.get("data") or {}).get("total"))

if "--with-ai" in sys.argv and items:
    print()
    print("=" * 78)
    print("10. AI 真实调用（需要外网 + 有效 API Key）")
    print("=" * 78)
    r = call("POST", "/ai/replenish/analyze", {"productId": items[0]["productId"], "days": 30})
    d = r.get("data") or {}
    check("AI 补货分析", ok(r), "模型 %s，降级 %s，%d 字"
          % (d.get("model"), d.get("degraded"), len(d.get("analysis") or "")))
    if d.get("analysis"):
        print("    " + (d["analysis"][:180].replace("\n", " ") + "…"))

    r = call("POST", "/ai/chat", {"question": "现在有哪些商品低于库存下限？"}, timeout=180)
    d = r.get("data") or {}
    check("AI 库存问答", ok(r), "工具 %s，耗时 %sms" % (d.get("toolsUsed"), d.get("costMs")))
    if d.get("answer"):
        print("    " + (d["answer"][:180].replace("\n", " ") + "…"))

# ---------------------------------------------------------------- 汇总
print()
print("=" * 78)
print("汇总：通过 %d 项，失败 %d 项" % (len(PASS), len(FAIL)))
if FAIL:
    print("失败项：")
    for f in FAIL:
        print("  - " + f)
print("=" * 78)
sys.exit(1 if FAIL else 0)
