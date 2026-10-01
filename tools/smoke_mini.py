# -*- coding: utf-8 -*-
"""
小程序端认证冒烟测试

重点验证「同一套认证体系适配双端」这件事真的成立：
  - 小程序模式（client=MINI）：刷新令牌在响应体里返回，且不下发 Cookie
  - Web 模式（不传 client）  ：刷新令牌走 httpOnly Cookie，响应体里没有它
  - 令牌轮转、重放拦截、退出吊销这三条安全线在两种模式下都成立

用法：
  python tools/smoke_mini.py
"""

import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"

PASS = 0
FAIL = 0


def call(method, path, body=None, token=None):
    """返回 (响应体, 响应头)"""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        resp = urllib.request.urlopen(req, timeout=90)
        return json.loads(resp.read().decode("utf-8")), dict(resp.headers)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return json.loads(raw), dict(e.headers)
        except Exception:
            return {"code": e.code, "message": raw[:200]}, dict(e.headers)


def check(name, ok, detail=""):
    global PASS, FAIL
    if ok:
        PASS += 1
        print("  [OK]   %-38s %s" % (name, detail))
    else:
        FAIL += 1
        print("  [FAIL] %-38s %s" % (name, detail))


def has_set_cookie(headers):
    for k in headers:
        if k.lower() == "set-cookie":
            return True
    return False


def main():
    print("=" * 78)
    print("小程序端认证冒烟测试")
    print("=" * 78)

    # ------------------------------------------------------------------
    print("\n[1] 小程序模式登录（client=MINI）")
    # ------------------------------------------------------------------
    body, headers = call("POST", "/auth/login", {
        "username": "admin", "password": "123456", "client": "MINI"
    })
    check("登录返回 200", body.get("code") == 200, body.get("message", ""))
    data = body.get("data") or {}
    access = data.get("accessToken") or ""
    refresh = data.get("refreshToken") or ""

    check("响应体带 accessToken", bool(access), access[:24] + "…" if access else "无")
    check("响应体带 refreshToken", bool(refresh), refresh[:24] + "…" if refresh else "无")
    check("响应体带 refreshExpiresIn", data.get("refreshExpiresIn") is not None,
          str(data.get("refreshExpiresIn")))
    check("不下发 Set-Cookie", not has_set_cookie(headers),
          "存在则说明小程序端也被写了 Cookie")
    check("用户信息完整", bool((data.get("user") or {}).get("username")),
          str((data.get("user") or {}).get("role")))

    # ------------------------------------------------------------------
    print("\n[2] 用访问令牌访问受保护接口")
    # ------------------------------------------------------------------
    body, _ = call("GET", "/auth/me", token=access)
    check("GET /auth/me", body.get("code") == 200, str((body.get("data") or {}).get("realName")))

    body, _ = call("GET", "/reports/dashboard", token=access)
    d = body.get("data") or {}
    check("GET /reports/dashboard", body.get("code") == 200,
          "今日销售 %s / 趋势 %d 点" % (d.get("todaySalesAmount"), len(d.get("salesTrend") or [])))

    body, _ = call("GET", "/products?page=1&size=3", token=access)
    check("GET /products", body.get("code") == 200,
          "共 %s 种商品" % ((body.get("data") or {}).get("total")))

    # ------------------------------------------------------------------
    print("\n[3] 无令牌访问应被拦下")
    # ------------------------------------------------------------------
    body, _ = call("GET", "/auth/me")
    check("无令牌返回 401", body.get("code") == 401, body.get("message", ""))

    body, _ = call("GET", "/products?page=1&size=3")
    check("无令牌查商品返回 401", body.get("code") == 401, body.get("message", ""))

    # ------------------------------------------------------------------
    print("\n[4] 刷新令牌轮转")
    # ------------------------------------------------------------------
    body, headers = call("POST", "/auth/refresh", {"refreshToken": refresh})
    check("刷新返回 200", body.get("code") == 200, body.get("message", ""))
    data2 = body.get("data") or {}
    access2 = data2.get("accessToken") or ""
    refresh2 = data2.get("refreshToken") or ""

    check("拿到新的 accessToken", bool(access2) and access2 != access)
    check("拿到新的 refreshToken（轮转）", bool(refresh2) and refresh2 != refresh)
    check("刷新也不下发 Cookie", not has_set_cookie(headers))

    body, _ = call("GET", "/auth/me", token=access2)
    check("新 accessToken 可用", body.get("code") == 200)

    # ------------------------------------------------------------------
    print("\n[5] 旧刷新令牌重放应被拒")
    # ------------------------------------------------------------------
    body, _ = call("POST", "/auth/refresh", {"refreshToken": refresh})
    check("旧 refreshToken 失效", body.get("code") == 401, body.get("message", ""))

    # ------------------------------------------------------------------
    print("\n[6] Web 模式不受影响（走 Cookie）")
    # ------------------------------------------------------------------
    body, headers = call("POST", "/auth/login", {"username": "admin", "password": "123456"})
    web = body.get("data") or {}
    check("Web 登录成功", body.get("code") == 200, body.get("message", ""))
    check("Web 模式响应体无 refreshToken", not web.get("refreshToken"),
          "有值则说明改造影响了 PC 端")
    check("Web 模式下发 Set-Cookie", has_set_cookie(headers))

    # ------------------------------------------------------------------
    print("\n[7] 退出吊销")
    # ------------------------------------------------------------------
    body, _ = call("POST", "/auth/logout", {"refreshToken": refresh2})
    check("小程序端退出成功", body.get("code") == 200, body.get("message", ""))

    body, _ = call("POST", "/auth/refresh", {"refreshToken": refresh2})
    check("退出后刷新被拒", body.get("code") == 401, body.get("message", ""))

    # ------------------------------------------------------------------
    print("\n[8] 登录失败仍返回统一提示")
    # ------------------------------------------------------------------
    body, _ = call("POST", "/auth/login", {
        "username": "admin", "password": "wrong-password", "client": "MINI"
    })
    check("密码错误返回 4010", body.get("code") == 4010, body.get("message", ""))

    print("\n" + "=" * 78)
    print("通过 %d 项，失败 %d 项" % (PASS, FAIL))
    print("=" * 78)
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
