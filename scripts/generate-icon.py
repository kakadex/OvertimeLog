# 生成"单线表盘 + 记"应用图标
#
# 设计：浅蓝底 + 白色单线圆、白色刻度/指针、中间白色「记」。
# 中文没法用手写矢量路径画，所以用系统字体（微软雅黑粗体）渲染成 PNG，再按各密度输出。
#
# 产出（与 res/mipmap-* 一一对应）：
#   ic_launcher.png / ic_launcher_round.png        传统图标，48/72/96/144/192
#   ic_launcher_foreground.png                     自适应图标前景，108dp 画布（安全区 66dp）
#   ic_launcher_monochrome.png                     主题图标单色层（Android 13+）
#
# 用法：
#   python scripts/generate-icon.py
# 输出到 dist/icons/，再复制进 app/src/main/res。

from pathlib import Path
import math

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "dist" / "icons"

# 浅蓝底 + 白色线条（与 res/values/colors.xml 的 ic_launcher_background 保持一致）
LIGHT_BLUE = (91, 155, 213, 255)
WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)
BLACK = (0, 0, 0, 255)

FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
FONT_FALLBACK = "C:/Windows/Fonts/simhei.ttf"

GLYPH = "记"


def load_font(size_px: int) -> ImageFont.FreeTypeFont:
    for path in (FONT_BOLD, FONT_FALLBACK):
        try:
            return ImageFont.truetype(path, size_px)
        except OSError:
            continue
    raise SystemExit("找不到中文字体（msyhbd.ttc / simhei.ttf）")


def draw_dial(draw: ImageDraw.ImageDraw, size: int, radius_ratio: float, monochrome: bool):
    """
    在 size×size 的画布中央画表盘。

    radius_ratio 是表盘半径相对画布宽度的比例：传统图标用 0.36，
    自适应图标前景用 30/108（安全区内），保证被系统裁成圆形时不切边。

    非单色模式所有线条为白色；单色模式（主题图标）统一用黑色，由系统按壁纸取色。
    """
    cx = cy = size / 2
    r = size * radius_ratio
    ink = BLACK if monochrome else WHITE

    # 单线圆：只描边，不填充
    ring_w = max(1, int(r * 0.055))
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=ink, width=ring_w)

    # 12 个刻度
    tick_outer = r * 0.88
    tick_inner = r * 0.76
    tick_w = max(1, int(r * 0.050))
    for i in range(12):
        angle = math.radians(i * 30)
        draw.line(
            [
                cx + tick_outer * math.sin(angle),
                cy - tick_outer * math.cos(angle),
                cx + tick_inner * math.sin(angle),
                cy - tick_inner * math.cos(angle),
            ],
            fill=ink,
            width=tick_w,
        )

    # 指针：10:10
    def hand(angle_deg: float, length: float, width: int):
        a = math.radians(angle_deg)
        draw.line(
            [cx, cy, cx + length * math.sin(a), cy - length * math.cos(a)],
            fill=ink,
            width=width,
        )

    hand(305, r * 0.52, max(2, int(r * 0.070)))   # 时针
    hand(60, r * 0.72, max(2, int(r * 0.050)))    # 分针

    # 汉字：用实际包围盒居中。彩色版本给文字加一圈"底色描边"，
    # 让指针在文字附近被自然遮住，比再画一个圆形徽标更干净。
    font = load_font(int(r * 0.56))
    bbox = draw.textbbox((0, 0), GLYPH, font=font)
    gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = cx - gw / 2 - bbox[0]
    ty = cy - gh / 2 - bbox[1] + r * 0.01
    if monochrome:
        draw.text((tx, ty), GLYPH, font=font, fill=ink)
    else:
        draw.text(
            (tx, ty),
            GLYPH,
            font=font,
            fill=WHITE,
            stroke_width=max(1, int(r * 0.075)),
            stroke_fill=LIGHT_BLUE,
        )


def legacy_icon(size: int, round_shape: bool, monochrome: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(img)

    if not monochrome:
        if round_shape:
            draw.ellipse([0, 0, size - 1, size - 1], fill=LIGHT_BLUE)
        else:
            # 圆角方块：半径约为边长的 22%
            draw.rounded_rectangle(
                [0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=LIGHT_BLUE
            )

    draw_dial(draw, size, 0.36, monochrome)
    return img


def foreground(size: int, monochrome: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    # 108dp 画布，安全区直径 66dp -> 半径 30/108
    draw_dial(draw, size, 0.300, monochrome)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    densities = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

    for name, scale in densities.items():
        legacy_px = int(48 * scale)
        legacy_icon(legacy_px, round_shape=False).save(OUT / f"ic_launcher-{name}.png")
        legacy_icon(legacy_px, round_shape=True).save(OUT / f"ic_launcher_round-{name}.png")

        fg_px = int(108 * scale)
        foreground(fg_px).save(OUT / f"ic_launcher_foreground-{name}.png")
        foreground(fg_px, monochrome=True).save(OUT / f"ic_launcher_monochrome-{name}.png")

    preview = Image.new("RGBA", (720, 260), (245, 245, 245, 255))
    for i, px in enumerate([192, 108, 48]):
        preview.alpha_composite(legacy_icon(px, round_shape=False), (30 + i * 230, 30))
    preview.save(OUT / "preview.png")

    round_preview = Image.new("RGBA", (240, 240), (245, 245, 245, 255))
    round_preview.alpha_composite(legacy_icon(192, round_shape=True), (24, 24))
    round_preview.save(OUT / "preview_round.png")

    print(f"图标已生成：{OUT}")


if __name__ == "__main__":
    main()
