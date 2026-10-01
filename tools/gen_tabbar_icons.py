# -*- coding: utf-8 -*-
"""
tabBar 图标生成器（零第三方依赖）

小程序的 tabBar iconPath 只认本地图片文件，不接受 base64 或字体图标，
所以这里直接把图标画出来存成 PNG。

做法是「超采样」：先在 4 倍分辨率上按硬边缘画布尔遮罩，再按 4x4 块求平均
降采样，等价于得到抗锯齿的边缘。这样不用装 Pillow 之类的图形库。

用法：
  python tools/gen_tabbar_icons.py
"""

import os
import struct
import zlib

# 输出尺寸与超采样倍率
SIZE = 96
SS = 4
BIG = SIZE * SS

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "miniprogram", "src", "static", "tabbar",
)

# 与 pages.json 里 tabBar 的 color / selectedColor 保持一致
COLOR_NORMAL = (138, 148, 166)
COLOR_ACTIVE = (47, 107, 255)


# ----------------------------------------------------------------------
# PNG 编码（RGBA / 8bit，无隔行）
# ----------------------------------------------------------------------

def write_png(path, width, height, alpha_rows, rgb):
    """把单通道 alpha 遮罩 + 固定颜色写成 RGBA PNG"""
    r, g, b = rgb
    raw = bytearray()
    for row in alpha_rows:
        raw.append(0)  # 每行的 filter type = 0 (None)
        for a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        body = tag + data
        return (struct.pack(">I", len(data)) + body
                + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    return len(png)


# ----------------------------------------------------------------------
# 形状谓词：输入超采样坐标系下的 (x, y)，返回是否落在形状内
# ----------------------------------------------------------------------

def circle(cx, cy, r):
    rr = r * r
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= rr


def rect(x0, y0, x1, y1):
    return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1


def round_rect(x0, y0, x1, y1, r):
    def f(x, y):
        if not (x0 <= x <= x1 and y0 <= y <= y1):
            return False
        # 四个角做圆形裁切
        for cx in (x0 + r, x1 - r):
            for cy in (y0 + r, y1 - r):
                if (x < x0 + r or x > x1 - r) and (y < y0 + r or y > y1 - r):
                    corner_x = x0 + r if x < x0 + r else x1 - r
                    corner_y = y0 + r if y < y0 + r else y1 - r
                    return (x - corner_x) ** 2 + (y - corner_y) ** 2 <= r * r
        return True
    return f


def triangle(p1, p2, p3):
    def sign(ax, ay, bx, by, cx, cy):
        return (ax - cx) * (by - cy) - (bx - cx) * (ay - cy)

    def f(x, y):
        d1 = sign(x, y, p1[0], p1[1], p2[0], p2[1])
        d2 = sign(x, y, p2[0], p2[1], p3[0], p3[1])
        d3 = sign(x, y, p3[0], p3[1], p1[0], p1[1])
        has_neg = d1 < 0 or d2 < 0 or d3 < 0
        has_pos = d1 > 0 or d2 > 0 or d3 > 0
        return not (has_neg and has_pos)
    return f


# ----------------------------------------------------------------------
# 渲染
# ----------------------------------------------------------------------

def render(shapes):
    """shapes: [(op, predicate)]，op 为 'add' 或 'sub'，按顺序叠加"""
    mask = [[False] * BIG for _ in range(BIG)]
    for op, pred in shapes:
        for y in range(BIG):
            row = mask[y]
            for x in range(BIG):
                if pred(x, y):
                    row[x] = op == "add"

    # 超采样降采样：4x4 块求平均得到覆盖率
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            hit = 0
            for dy in range(SS):
                mrow = mask[y * SS + dy]
                base = x * SS
                for dx in range(SS):
                    if mrow[base + dx]:
                        hit += 1
            row.append(int(round(hit * 255 / (SS * SS))))
        rows.append(row)
    return rows


# 画布在超采样坐标系里的边长
S = BIG
C = S // 2          # 中心
U = S / 192.0       # 单位：设计稿按 192 网格作图，这里换算成超采样坐标


def u(v):
    return v * U


# ----------------------------------------------------------------------
# 四个图标的形状定义
# ----------------------------------------------------------------------

def icon_dashboard():
    """工作台：2x2 圆角方块（仪表盘 / 模块总览）"""
    pads = [(26, 26), (106, 26), (26, 106), (106, 106)]
    shapes = []
    for px, py in pads:
        shapes.append(("add", round_rect(u(px), u(py), u(px + 60), u(py + 60), u(14))))
    return shapes


def icon_stock():
    """库存：堆叠的货箱（俯视三层，中间一条封箱带）"""
    shapes = [
        # 箱体
        ("add", round_rect(u(30), u(46), u(162), u(160), u(12))),
        # 箱盖（略宽，做出立体错层感）
        ("add", round_rect(u(18), u(30), u(174), u(64), u(10))),
        # 挖掉中间封箱带
        ("sub", rect(u(85), u(30), u(107), u(90))),
    ]
    return shapes


def icon_alert():
    """预警：警告三角 + 中间感叹号"""
    shapes = [
        ("add", triangle((C, u(22)), (u(10), u(166)), (u(182), u(166)))),
        # 感叹号竖条
        ("sub", round_rect(u(88), u(72), u(104), u(122), u(8))),
        # 感叹号圆点
        ("sub", circle(C, u(142), u(10))),
    ]
    return shapes


def icon_mine():
    """我的：头像剪影（圆头 + 肩部圆弧）"""
    shapes = [
        ("add", circle(C, u(62), u(36))),
        ("add", circle(C, u(196), u(72))),
    ]
    return shapes


ICONS = {
    "dashboard": icon_dashboard,
    "stock": icon_stock,
    "alert": icon_alert,
    "mine": icon_mine,
}


def main():
    total = 0
    for name, builder in ICONS.items():
        rows = render(builder())
        for suffix, color in (("", COLOR_NORMAL), ("-active", COLOR_ACTIVE)):
            path = os.path.join(OUT_DIR, "%s%s.png" % (name, suffix))
            size = write_png(path, SIZE, SIZE, rows, color)
            total += 1
            print("  %-28s %5d B" % (os.path.basename(path), size))
    print("\n共生成 %d 个图标 -> %s" % (total, OUT_DIR))


if __name__ == "__main__":
    main()
