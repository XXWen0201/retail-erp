#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
零售进销存系统 —— 种子数据生成器

为什么不用手写 INSERT：
    1. 需要 90 天连续销售流水，手写不现实
    2. 库存必须自洽：stock.quantity 必须等于该商品所有有效批次 stock_quantity 之和
    3. 批次扣减必须按 FEFO（先到期先出），否则保质期预警演示不出效果
    4. 需要刻意造出「低于下限 / 临近到期 / 零库存」三类数据，方便答辩演示预警

因此用一个确定性模拟器跑一遍真实进销存流程，每一步库存变化同时写进
stock / stock_batch / stock_record 三张表，保证完全对账。
random.seed 固定，任何人重跑都得到完全相同的数据。

用法: python gen_seed_data.py > ../backend/src/main/resources/db/data.sql
"""

import random
import sys
from datetime import date, datetime, timedelta

random.seed(20260929)

TODAY = date.today()
START = TODAY - timedelta(days=89)          # 90 天数据窗口
OUT = []


def w(line=""):
    OUT.append(line)


def q(s):
    """SQL 字符串转义"""
    return str(s).replace("\\", "\\\\").replace("'", "''")


def ts_lit(dt):
    return "'" + dt.strftime("%Y-%m-%d %H:%M:%S") + "'"


def d_lit(d):
    return "NULL" if d is None else "'" + d.strftime("%Y-%m-%d") + "'"


# =====================================================================
# 1. 基础字典
# =====================================================================
CATEGORIES = [
    (1, "文具用品", 1),
    (2, "休闲零食", 2),
    (3, "饮料酒水", 3),
    (4, "日用百货", 4),
    (5, "粮油调味", 5),
    (6, "乳品烘焙", 6),
]

# (id, code, name, contact, phone, address)
SUPPLIERS = [
    (1, "SUP001", "长沙晨光文具批发有限公司", "刘建国", "13873100011", "长沙市雨花区高桥大市场文具城 A12"),
    (2, "SUP002", "湖南旺达食品贸易有限公司", "陈美玲", "13873100022", "长沙市开福区湘江北路 268 号"),
    (3, "SUP003", "长沙百川饮料供应链有限公司", "赵宏", "13873100033", "长沙市岳麓区枫林三路 118 号"),
    (4, "SUP004", "湖南家和日化用品有限公司", "孙丽娟", "13873100044", "长沙市芙蓉区人民东路 55 号"),
    (5, "SUP005", "长沙金穗粮油有限公司", "周德海", "13873100055", "长沙市望城区高塘岭街道粮库路 8 号"),
    (6, "SUP006", "湖南新希望乳业配送中心", "吴晓燕", "13873100066", "长沙市长沙县星沙大道 199 号"),
]

# (id, code, name, category_id, spec, unit, purchase_price, sale_price,
#  stock_upper, stock_lower, shelf_life_days, supplier_id)
PRODUCTS = [
    (1, "P1001", "晨光中性笔 0.5mm 黑", 1, "12支/盒", "支", 1.20, 2.50, 800, 150, 0, 1),
    (2, "P1002", "中华 2B 铅笔", 1, "12支/盒", "支", 0.60, 1.50, 600, 120, 0, 1),
    (3, "P1003", "得力订书机 12 号", 1, "单个装", "个", 8.50, 15.90, 80, 15, 0, 1),
    (4, "P1004", "笔记本 A5 60 页", 1, "本", "本", 2.30, 5.00, 300, 60, 0, 1),
    (5, "P1005", "得力固体胶 21g", 1, "支", "支", 1.80, 3.50, 200, 40, 0, 1),
    (6, "P1006", "晨光橡皮擦", 1, "块", "块", 0.80, 2.00, 300, 50, 0, 1),
    (7, "P2001", "乐事薯片原味 70g", 2, "袋", "袋", 3.50, 6.50, 400, 80, 180, 2),
    (8, "P2002", "奥利奥夹心饼干 116g", 2, "袋", "袋", 4.20, 7.90, 350, 70, 270, 2),
    (9, "P2003", "旺旺雪饼 84g", 2, "袋", "袋", 3.80, 6.90, 300, 60, 240, 2),
    (10, "P2004", "双汇火腿肠 30g", 2, "根", "根", 1.10, 2.00, 600, 120, 90, 2),
    (11, "P2005", "洽洽香瓜子 108g", 2, "袋", "袋", 4.50, 8.50, 250, 50, 300, 2),
    (12, "P3001", "农夫山泉 550ml", 3, "瓶", "瓶", 1.00, 2.00, 1200, 250, 365, 3),
    (13, "P3002", "可口可乐 330ml", 3, "罐", "罐", 1.80, 3.00, 900, 200, 270, 3),
    (14, "P3003", "康师傅冰红茶 500ml", 3, "瓶", "瓶", 2.20, 3.50, 700, 150, 270, 3),
    (15, "P3004", "红牛维生素饮料 250ml", 3, "罐", "罐", 4.30, 6.50, 300, 60, 540, 3),
    (16, "P3005", "青岛啤酒 330ml", 3, "罐", "罐", 2.90, 4.50, 500, 100, 360, 3),
    (17, "P4001", "洁柔抽纸 3 层", 4, "包", "包", 3.60, 6.00, 500, 100, 0, 4),
    (18, "P4002", "蓝月亮洗手液 500g", 4, "瓶", "瓶", 9.80, 16.90, 120, 25, 730, 4),
    (19, "P4003", "高露洁牙膏 120g", 4, "支", "支", 6.50, 11.90, 150, 30, 1095, 4),
    (20, "P4004", "舒肤佳香皂 115g", 4, "块", "块", 3.20, 5.90, 200, 40, 1095, 4),
    (21, "P5001", "金龙鱼调和油 5L", 5, "桶", "桶", 52.00, 69.90, 60, 12, 540, 5),
    (22, "P5002", "海天生抽 500ml", 5, "瓶", "瓶", 7.50, 12.90, 180, 35, 730, 5),
    (23, "P5003", "太太乐鸡精 200g", 5, "袋", "袋", 5.60, 9.90, 150, 30, 730, 5),
    (24, "P6001", "伊利纯牛奶 250ml", 6, "盒", "盒", 2.40, 3.50, 600, 120, 180, 6),
    (25, "P6002", "蒙牛益生菌酸奶 100g", 6, "杯", "杯", 2.80, 4.50, 300, 60, 21, 6),
    (26, "P6003", "达利园小面包 400g", 6, "袋", "袋", 8.50, 13.90, 150, 30, 60, 6),
    (27, "P6004", "桃李醇熟吐司 400g", 6, "袋", "袋", 6.80, 10.90, 100, 20, 15, 6),
    (28, "P6005", "旺仔牛奶 245ml", 6, "罐", "罐", 3.20, 4.90, 400, 80, 240, 6),
    (29, "P6006", "雀巢速溶咖啡 15 条", 6, "盒", "盒", 16.50, 26.90, 80, 15, 540, 6),
    (30, "P6007", "益达木糖醇口香糖", 6, "瓶", "瓶", 6.90, 11.50, 120, 25, 540, 6),
]

PINFO = {p[0]: {
    "code": p[1], "name": p[2], "cid": p[3], "spec": p[4], "unit": p[5],
    "pp": p[6], "sp": p[7], "upper": p[8], "lower": p[9], "life": p[10], "sup": p[11],
} for p in PRODUCTS}

# 商品日均销量基数，决定销售活跃度
DAILY_BASE = {
    1: 28, 2: 22, 3: 3, 4: 11, 5: 8, 6: 12,
    7: 15, 8: 12, 9: 9, 10: 30, 11: 8,
    12: 45, 13: 38, 14: 26, 15: 10, 16: 18,
    17: 20, 18: 4, 19: 5, 20: 7,
    21: 2, 22: 6, 23: 4,
    24: 25, 25: 14, 26: 6, 27: 9, 28: 16, 29: 3, 30: 5,
}

# =====================================================================
# 2. 内存态模拟
# =====================================================================
batches = []                 # 批次
stock_qty = {p[0]: 0 for p in PRODUCTS}
stock_avg_cost = {p[0]: 0.0 for p in PRODUCTS}

purchase_orders, purchase_items = [], []
sale_orders, sale_items = [], []
records = []

batch_seq = po_seq = so_seq = poi_seq = soi_seq = rec_seq = 0
OP_NAMES = ["文嘉仪", "张伟", "李静"]
# 不能用内置 hash()：CPython 对 str 的 hash 带随机盐，每次进程结果都不同
OP_IDS = {name: i + 1 for i, name in enumerate(OP_NAMES)}


def add_record(pid, batch_id, biz_type, change, before, after,
               unit_cost, biz_no, biz_id, op, remark, ts):
    global rec_seq
    rec_seq += 1
    records.append({
        "id": rec_seq, "pid": pid, "bid": batch_id, "type": biz_type,
        "change": change, "before": before, "after": after, "cost": unit_cost,
        "biz_no": biz_no, "biz_id": biz_id, "op": op, "remark": remark, "ts": ts,
    })


def inbound(pid, qty, cost, prod_date, expire_date, po_id, po_no, op, ts):
    """采购入库：建批次 + 加总库存 + 写流水"""
    global batch_seq
    batch_seq += 1
    before = stock_qty[pid]
    stock_qty[pid] += qty
    # 移动加权平均成本
    prev_amt = stock_avg_cost[pid] * before
    stock_avg_cost[pid] = (prev_amt + cost * qty) / stock_qty[pid] if stock_qty[pid] else cost

    batch_no = f"B{ts.strftime('%Y%m%d')}{batch_seq:04d}"
    batches.append({
        "id": batch_seq, "pid": pid, "batch_no": batch_no, "po_id": po_id,
        "prod_date": prod_date, "expire_date": expire_date,
        "init": qty, "out": 0, "stock": qty, "cost": cost,
    })
    add_record(pid, batch_seq, "PURCHASE_IN", qty, before, stock_qty[pid],
               cost, po_no, po_id, op, "采购入库", ts)
    return batch_seq, batch_no


def outbound(pid, qty, so_id, so_no, op, ts):
    """销售出库：FEFO 先到期先出，跨批次自动拆分"""
    remaining = qty
    picked = []
    candidates = sorted(
        [b for b in batches if b["pid"] == pid and b["stock"] > 0],
        key=lambda b: (b["expire_date"] is None,
                       b["expire_date"] or date.max, b["id"]),
    )
    for b in candidates:
        if remaining <= 0:
            break
        take = min(remaining, b["stock"])
        before = stock_qty[pid]
        b["stock"] -= take
        b["out"] += take
        stock_qty[pid] -= take
        remaining -= take
        picked.append({"bid": b["id"], "batch_no": b["batch_no"],
                       "qty": take, "cost": b["cost"]})
        add_record(pid, b["id"], "SALE_OUT", -take, before, stock_qty[pid],
                   b["cost"], so_no, so_id, op, "销售出库", ts)
    return qty - remaining, picked


def adjust(pid, target_qty, op, remark, ts, biz_no, biz_id, biz_type):
    """盘点调整：把总库存直接调到目标值（盘盈为正差、盘亏为负差）"""
    diff = target_qty - stock_qty[pid]
    if diff == 0:
        return
    before = stock_qty[pid]
    stock_qty[pid] = target_qty
    # 差额落在最早的有效批次上，保持批次合计与总库存一致
    cands = [b for b in batches if b["pid"] == pid and b["stock"] > 0] or \
            [b for b in batches if b["pid"] == pid]
    if not cands:
        return
    b = cands[-1]
    b["stock"] += diff
    if b["stock"] < 0:
        b["stock"] = 0
    add_record(pid, b["id"], biz_type, diff, before, stock_qty[pid],
               b["cost"], biz_no, biz_id, op, remark, ts)


# ---------------- 2.1 开店首批铺货 ----------------
for pid, info in PINFO.items():
    po_seq += 1
    ts = datetime.combine(START - timedelta(days=2), datetime.min.time()).replace(hour=9, minute=20)
    po_no = f"PO{ts.strftime('%Y%m%d')}{po_seq:03d}"
    # 首批铺货约 24 天销量，保证第一次周期补货（第 21 天）前不会断货
    target = max(int(DAILY_BASE[pid] * 30 * 0.8), info["lower"] * 2, 20)
    prod_date = ts.date() - timedelta(days=10)
    expire_date = (prod_date + timedelta(days=info["life"])) if info["life"] else None

    poi_seq += 1
    _, batch_no = inbound(pid, target, info["pp"], prod_date, expire_date,
                          po_seq, po_no, OP_NAMES[0], ts)
    purchase_items.append({
        "id": poi_seq, "oid": po_seq, "pid": pid, "name": info["name"],
        "qty": target, "price": info["pp"], "amount": round(target * info["pp"], 2),
        "batch_no": batch_no, "prod_date": prod_date, "expire_date": expire_date,
    })
    purchase_orders.append({
        "id": po_seq, "no": po_no, "sid": info["sup"],
        "sname": next(s[2] for s in SUPPLIERS if s[0] == info["sup"]),
        "qty": target, "amount": round(target * info["pp"], 2),
        "status": "FINISHED", "date": ts.date(), "recv": ts,
    })

# ---------------- 2.2 补货逻辑（按天调用，不能一次性跑完） ----------------
# 关键：补货必须与销售按时间交错。若先把所有补货跑完再开始销售，
# 补货决策依据的是"几乎未消耗的初始库存"，之后 90 天便无货可卖，数据严重失真。
def do_replenish(order_day):
    """对低于安全水位的商品，按供应商合并成采购单"""
    global po_seq, poi_seq
    groups = {}
    for pid, info in PINFO.items():
        if stock_qty[pid] < info["lower"] * 2.5:
            groups.setdefault(info["sup"], []).append(pid)

    for sup, pids in groups.items():
        po_seq += 1
        ts = datetime.combine(order_day, datetime.min.time()).replace(hour=10, minute=15)
        po_no = f"PO{ts.strftime('%Y%m%d')}{po_seq:03d}"
        tqty, tamt = 0, 0.0
        for pid in pids:
            info = PINFO[pid]
            # 一次补足约 28 天销量
            need = max(int(DAILY_BASE[pid] * 28 - stock_qty[pid]), 20)
            prod_date = ts.date() - timedelta(days=random.randint(2, 12))
            expire_date = (prod_date + timedelta(days=info["life"])) if info["life"] else None
            poi_seq += 1
            _, batch_no = inbound(pid, need, info["pp"], prod_date, expire_date,
                                  po_seq, po_no, OP_NAMES[0], ts)
            purchase_items.append({
                "id": poi_seq, "oid": po_seq, "pid": pid, "name": info["name"],
                "qty": need, "price": info["pp"], "amount": round(need * info["pp"], 2),
                "batch_no": batch_no, "prod_date": prod_date, "expire_date": expire_date,
            })
            tqty += need
            tamt += need * info["pp"]
        purchase_orders.append({
            "id": po_seq, "no": po_no, "sid": sup,
            "sname": next(s[2] for s in SUPPLIERS if s[0] == sup),
            "qty": tqty, "amount": round(tamt, 2),
            "status": "FINISHED", "date": ts.date(), "recv": ts,
        })

# ---------------- 2.3 按天推进销售，与补货交错 ----------------
for d in range(90):
    day = START + timedelta(days=d)

    # 每 21 天周期性补货，必须发生在当天销售之前
    if d > 0 and d % 21 == 0:
        do_replenish(day)

    weekend = day.weekday() >= 5
    factor = 1.35 if weekend else (0.85 if day.weekday() == 0 else 1.0)
    trend = 1.0 + d / 90 * 0.28          # 缓慢上升，让 AI 能看出趋势

    so_seq += 1
    so_id = so_seq
    so_no = f"SO{day.strftime('%Y%m%d')}{so_id:03d}"
    ts = datetime.combine(day, datetime.min.time()).replace(hour=20, minute=30, second=0)
    op = OP_NAMES[(d + so_id) % len(OP_NAMES)]

    total_amount = total_cost = 0.0
    total_qty = 0
    has_item = False

    for pid, info in PINFO.items():
        lam = DAILY_BASE[pid] * factor * trend
        qty = int(random.gauss(lam, lam * 0.35))
        if qty <= 0 or stock_qty[pid] <= 0:
            continue
        qty = min(qty, stock_qty[pid])
        real_qty, picked = outbound(pid, qty, so_id, so_no, op, ts)
        if real_qty <= 0:
            continue

        amount = round(real_qty * info["sp"], 2)
        cost_amt = round(sum(p["qty"] * p["cost"] for p in picked), 2)
        main = max(picked, key=lambda p: p["qty"])
        soi_seq += 1
        sale_items.append({
            "id": soi_seq, "oid": so_id, "pid": pid, "name": info["name"],
            "bid": main["bid"], "batch_no": main["batch_no"], "qty": real_qty,
            "price": info["sp"], "amount": amount, "cost": main["cost"],
            "cost_amount": cost_amt,
        })
        total_amount += amount
        total_cost += cost_amt
        total_qty += real_qty
        has_item = True

    if not has_item:
        continue

    discount = round(total_amount // 100 * 5, 2)          # 满 100 减 5
    pay = round(total_amount - discount, 2)
    sale_orders.append({
        "id": so_id, "no": so_no,
        "cust": random.choice(["散客", "散客", "散客", "散客",
                               "会员-王女士", "会员-李先生", "周边餐馆"]),
        "qty": total_qty, "amount": round(total_amount, 2), "discount": discount,
        "pay": pay, "cost": round(total_cost, 2), "gross": round(pay - total_cost, 2),
        "status": "FINISHED", "date": day, "op": op,
    })

# ---------------- 2.4 一次盘点，制造差异追溯数据 ----------------
check_ts = TODAY - timedelta(days=20)
check_pids = [1, 7, 12, 24, 25]
check_seq = 1
check_no = f"PC{check_ts.strftime('%Y%m%d')}{check_seq:03d}"
check_items = []
for pid in check_pids:
    book = stock_qty[pid]
    # 模拟真实盘点：少量串货/丢失，差异在 ±5 以内
    diff = random.randint(-5, 3) if pid in (1, 7) else 0
    if pid == 25:
        diff = -3
    actual = max(book + diff, 0)
    if diff != 0:
        adjust(pid, actual, OP_NAMES[1],
               "盘点差异：货架串位与临期损耗" if diff < 0 else "盘点差异：来货未及时上架",
               datetime.combine(check_ts, datetime.min.time()).replace(hour=21),
               check_no, check_seq, "CHECK_LOSS" if diff < 0 else "CHECK_GAIN")
    check_items.append({"pid": pid, "book": book, "actual": actual, "diff": diff,
                        "batch_id": None,
                        "reason": "货架串位与临期损耗" if diff < 0
                                  else ("来货未及时上架" if diff > 0 else "账实相符")})

# ---------------- 2.5 演示用异常数据 ----------------
DEMO_NOW = datetime.combine(TODAY, datetime.min.time()).replace(hour=8, minute=0)

# 低库存：得力订书机、桃李吐司 压到下限以下
for pid, keep in ((3, 9), (27, 6)):
    if stock_qty[pid] > keep:
        adjust(pid, keep, OP_NAMES[0], "演示数据：制造低库存场景",
               DEMO_NOW, "DEMO-LOW", None, "CHECK_LOSS")

# 零库存：达利园小面包 清零
if stock_qty[26] > 0:
    adjust(26, 0, OP_NAMES[0], "演示数据：制造零库存场景",
           DEMO_NOW, "DEMO-ZERO", None, "CHECK_LOSS")

# 临期 / 过期：蒙牛酸奶剩 4 天、桃李吐司已过期 2 天
for pid, days_left in ((25, 4), (27, -2)):
    cands = [b for b in batches if b["pid"] == pid and b["stock"] > 0]
    if cands:
        cands[0]["expire_date"] = TODAY + timedelta(days=days_left)

# =====================================================================
# 3. 输出 SQL
# =====================================================================
w("-- =====================================================================")
w("--  中小零售门店进销存与库存预警管理系统  ——  种子数据")
w("--  由 tools/gen_seed_data.py 自动生成，请勿手工修改")
w(f"--  生成日期: {TODAY}   数据窗口: {START} ~ {TODAY}（90 天）")
w(f"--  数据量: 商品 {len(PRODUCTS)} / 供应商 {len(SUPPLIERS)} / "
  f"采购单 {len(purchase_orders)} / 销售单 {len(sale_orders)} / "
  f"批次 {len(batches)} / 库存流水 {len(records)}")
w("-- =====================================================================")
w("USE retail_erp;")
w("SET NAMES utf8mb4;")
w("")

PW = "$2a$10$oSACpN41X1.iKVsIhFZf2ueut0vBmvfaVknAfCD2bA6IOwLQssGr2"   # 123456
w("-- ---------- 系统用户（密码统一 123456） ----------")
w("INSERT INTO sys_user (id, username, password, real_name, role, phone, status) VALUES")
w(f"(1, 'admin',   '{PW}', '文嘉仪', 'ADMIN',   '13907310001', 1),")
w(f"(2, 'manager', '{PW}', '张伟',   'MANAGER', '13907310002', 1),")
w(f"(3, 'staff',   '{PW}', '李静',   'STAFF',   '13907310003', 1);")
w("")

w("-- ---------- 商品分类 ----------")
w("INSERT INTO category (id, name, parent_id, sort, status) VALUES")
w(",\n".join(f"({i}, '{q(n)}', 0, {s}, 1)" for i, n, s in CATEGORIES) + ";")
w("")

w("-- ---------- 供应商 ----------")
w("INSERT INTO supplier (id, code, name, contact, phone, address, status) VALUES")
w(",\n".join(f"({i}, '{c}', '{q(n)}', '{q(ct)}', '{ph}', '{q(ad)}', 1)"
             for i, c, n, ct, ph, ad in SUPPLIERS) + ";")
w("")

w("-- ---------- 商品 ----------")
w("INSERT INTO product (id, code, barcode, name, category_id, spec, unit, "
  "purchase_price, sale_price, stock_upper, stock_lower, shelf_life_days, status) VALUES")
rows = []
for pid, info in PINFO.items():
    barcode = f"69{pid:04d}{random.randint(100000, 999999)}"
    rows.append(f"({pid}, '{info['code']}', '{barcode}', '{q(info['name'])}', "
                f"{info['cid']}, '{q(info['spec'])}', '{info['unit']}', "
                f"{info['pp']:.2f}, {info['sp']:.2f}, {info['upper']}, "
                f"{info['lower']}, {info['life']}, 1)")
w(",\n".join(rows) + ";")
w("")

w("-- ---------- 采购单 ----------")
w("INSERT INTO purchase_order (id, order_no, supplier_id, supplier_name, total_quantity, "
  "total_amount, status, order_date, receive_time, operator_id, operator_name, remark) VALUES")
w(",\n".join(
    f"({o['id']}, '{o['no']}', {o['sid']}, '{q(o['sname'])}', {o['qty']}, "
    f"{o['amount']:.2f}, '{o['status']}', {d_lit(o['date'])}, {ts_lit(o['recv'])}, "
    f"1, '{OP_NAMES[0]}', '周期补货')" for o in purchase_orders) + ";")
w("")

w("-- ---------- 采购明细 ----------")
CHUNK = 200
for i in range(0, len(purchase_items), CHUNK):
    part = purchase_items[i:i + CHUNK]
    body = ",\n".join(
        f"({it['id']}, {it['oid']}, {it['pid']}, '{q(it['name'])}', {it['qty']}, "
        f"{it['price']:.2f}, {it['amount']:.2f}, '{it['batch_no']}', "
        f"{d_lit(it['prod_date'])}, {d_lit(it['expire_date'])})" for it in part)
    w("INSERT INTO purchase_order_item (id, order_id, product_id, product_name, quantity, "
      "price, amount, batch_no, production_date, expire_date) VALUES")
    w(body + ";")
w("")

w("-- ---------- 库存批次 ----------")
for i in range(0, len(batches), CHUNK):
    part = batches[i:i + CHUNK]
    w("INSERT INTO stock_batch (id, product_id, batch_no, purchase_order_id, "
      "production_date, expire_date, init_quantity, out_quantity, stock_quantity, "
      "cost_price, status) VALUES")
    body = ",\n".join(
        f"({b['id']}, {b['pid']}, '{b['batch_no']}', {b['po_id']}, "
        f"{d_lit(b['prod_date'])}, {d_lit(b['expire_date'])}, {b['init']}, "
        f"{b['out']}, {b['stock']}, {b['cost']:.2f}, {1 if b['stock'] > 0 else 0})"
        for b in part)
    w(body + ";")
w("")

w("-- ---------- 销售单 ----------")
for i in range(0, len(sale_orders), CHUNK):
    part = sale_orders[i:i + CHUNK]
    w("INSERT INTO sale_order (id, order_no, customer_name, total_quantity, total_amount, "
      "discount_amount, pay_amount, total_cost, gross_profit, status, order_date, "
      "operator_id, operator_name, remark) VALUES")
    body = ",\n".join(
        f"({o['id']}, '{o['no']}', '{q(o['cust'])}', {o['qty']}, {o['amount']:.2f}, "
        f"{o['discount']:.2f}, {o['pay']:.2f}, {o['cost']:.2f}, {o['gross']:.2f}, "
        f"'{o['status']}', {d_lit(o['date'])}, "
        f"{OP_IDS[o['op']]}, '{o['op']}', '门店零售')" for o in part)
    w(body + ";")
w("")

w("-- ---------- 销售明细 ----------")
for i in range(0, len(sale_items), CHUNK):
    part = sale_items[i:i + CHUNK]
    w("INSERT INTO sale_order_item (id, order_id, product_id, product_name, batch_id, "
      "batch_no, quantity, price, amount, cost_price, cost_amount) VALUES")
    body = ",\n".join(
        f"({it['id']}, {it['oid']}, {it['pid']}, '{q(it['name'])}', {it['bid']}, "
        f"'{it['batch_no']}', {it['qty']}, {it['price']:.2f}, {it['amount']:.2f}, "
        f"{it['cost']:.4f}, {it['cost_amount']:.2f})" for it in part)
    w(body + ";")
w("")

w("-- ---------- 库存流水 ----------")
for i in range(0, len(records), CHUNK):
    part = records[i:i + CHUNK]
    w("INSERT INTO stock_record (id, product_id, batch_id, biz_type, change_quantity, "
      "before_quantity, after_quantity, unit_cost, biz_no, biz_id, operator_id, "
      "operator_name, remark, create_time) VALUES")
    body = ",\n".join(
        f"({r['id']}, {r['pid']}, {r['bid']}, '{r['type']}', {r['change']}, "
        f"{r['before']}, {r['after']}, {r['cost']:.4f}, '{r['biz_no']}', "
        f"{r['biz_id'] if r['biz_id'] is not None else 'NULL'}, "
        f"{OP_IDS[r['op']]}, '{r['op']}', '{q(r['remark'])}', {ts_lit(r['ts'])})"
        for r in part)
    w(body + ";")
w("")

w("-- ---------- 盘点单 ----------")
w(f"INSERT INTO stock_check (id, check_no, status, check_date, total_diff_quantity, "
  f"diff_item_count, operator_id, operator_name, finish_time, remark) VALUES")
w(f"(1, '{check_no}', 'FINISHED', {d_lit(check_ts)}, "
  f"{sum(abs(c['diff']) for c in check_items)}, "
  f"{sum(1 for c in check_items if c['diff'] != 0)}, 2, '张伟', "
  f"{ts_lit(datetime.combine(check_ts, datetime.min.time()).replace(hour=21))}, "
  f"'月度例行盘点');")
w("")

w("-- ---------- 盘点明细 ----------")
w("INSERT INTO stock_check_item (id, check_id, product_id, product_name, batch_id, "
  "batch_no, book_quantity, actual_quantity, diff_quantity, reason) VALUES")
ck_rows = []
for idx, c in enumerate(check_items, start=1):
    info = PINFO[c["pid"]]
    ck_rows.append(f"({idx}, 1, {c['pid']}, '{q(info['name'])}', NULL, NULL, "
                   f"{c['book']}, {c['actual']}, {c['diff']}, '{q(c['reason'])}')")
w(",\n".join(ck_rows) + ";")
w("")

w("-- ---------- 库存汇总（= 各商品有效批次剩余之和） ----------")
w("INSERT INTO stock (product_id, quantity, locked_quantity, avg_cost, version) VALUES")
rows = []
for pid in sorted(stock_qty):
    rows.append(f"({pid}, {stock_qty[pid]}, 0, {stock_avg_cost[pid]:.4f}, 0)")
w(",\n".join(rows) + ";")
w("")

w("-- ---------- 自检：库存汇总与批次合计必须一致 ----------")
w("-- 若下面这条查询返回任何行，说明种子数据不自洽，请重新生成")
w("SELECT s.product_id, s.quantity AS stock_qty, IFNULL(b.qty, 0) AS batch_qty")
w("FROM stock s LEFT JOIN (")
w("    SELECT product_id, SUM(stock_quantity) AS qty FROM stock_batch")
w("    WHERE status = 1 GROUP BY product_id")
w(") b ON b.product_id = s.product_id")
w("WHERE s.quantity <> IFNULL(b.qty, 0);")
w("")

sys.stdout.write("\n".join(OUT))
