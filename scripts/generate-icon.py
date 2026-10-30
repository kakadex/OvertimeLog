# 生成"表盘 + 记"应用图标
#
# 中文没法用手写矢量路径画，所以这里用系统字体（微软雅黑粗体）渲染成 PNG，
# 再按 Android 的各密度规格输出。
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

# 品牌色：与应用主题主色一致
INDIGO = (63, 90, 166, 255)
INDIGO_SOFT = (63, 90, 166, 210)
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


def draw_dial(draw: ImageDraw.ImageDraw, size: int, radius_ratio: float, ink, monochrome: bool):
    """
    在 size×size 的画布中央画表盘。

    radius_ratio 是表盘半径相对画布宽度的比例：传统图标用 0.36，
    自适应图标前景用 30/108（安全区内），保证被系统裁成圆形时不切边。
    """
    cx = cy = size / 2
    r = size * radius_ratio

    dial_fill = TRANSPARENT if monochrome else WHITE
    detail = BLACK if monochrome else ink

    # 表盘底
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=dial_fill, outline=detail, width=max(1, int(r * 0.045)))

    # 12 个刻度（先画在盘面上，再让中心徽标盖住内侧）
    tick_outer = r * 0.90
    tick_inner = r * 0.78
    tick_w = max(1, int(r * 0.055))
    for i in range(12):
        angle = math.radians(i * 30)
        x1 = cx + tick_outer * math.sin(angle)
        y1 = cy - tick_outer * math.cos(angle)
        x2 = cx + tick_inner * math.sin(angle)
        y2 = cy - tick_inner * math.cos(angle)
        draw.line([x1, y1, x2, y2], fill=detail, width=tick_w)

    # 指针：10:10，经典钟表姿态；只画外侧一段，内侧会被中心徽标盖住
    def hand(angle_deg: float, length: float, width: int):
        a = math.radians(angle_deg)
        draw.line(
            [cx, cy, cx + length * math.sin(a), cy - length * math.cos(a)],
            fill=detail,
            width=width,
        )

    hand(305, r * 0.58, max(2, int(r * 0.070)))   # 时针
    hand(60, r * 0.78, max(2, int(r * 0.050)))    # 分针

    # 中心徽标：一块白底圆，用来遮住指针内端，让"记"干干净净地放在中间
    badge_r = r * 0.41
    if not monochrome:
        draw.ellipse([cx - badge_r, cy - badge_r, cx + badge_r, cy + badge_r], fill=WHITE)
    draw.ellipse(
        [cx - badge_r, cy - badge_r, cx + badge_r, cy + badge_r],
        outline=detail,
        width=max(1, int(r * 0.025)),
    )

    # 汉字：用字体实际包围盒居中，避免不同字体基线导致偏移
    font = load_font(int(r * 0.52))
    bbox = draw.textbbox((0, 0), GLYPH, font=font)
    gw, gh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(
        (cx - gw / 2 - bbox[0], cy - gh / 2 - bbox[1]),
        GLYPH,
        font=font,
        fill=detail,
    )


def legacy_icon(size: int, round_shape: bool, monochrome: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(img)

    if monochrome:
        draw.ellipse([0, 0, size, size], fill=TRANSPARENT)
    elif round_shape:
        draw.ellipse([0, 0, size - 1, size - 1], fill=INDIGO)
    else:
        # 圆角方块：半径约为边长的 22%，接近 Android 与各家桌面的一致观感
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=INDIGO)

    draw_dial(draw, size, 0.36, INDIGO, monochrome)
    return img


def foreground(size: int, monochrome: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    draw = ImageDraw.Draw(img)
    # 108dp 画布，安全区直径 66dp -> 半径 30/108，取 0.278
    draw_dial(draw, size, 0.300, INDIGO, monochrome)
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

    # 预览图（给文档和肉眼检查用）
    preview = Image.new("RGBA", (720, 260), (245, 245, 245, 255))
    for i, (px, label) in enumerate([(192, "192"), (108, "108"), (48, "48")]):
        icon = legacy_icon(px, round_shape=False)
        preview.alpha_composite(icon, (30 + i * 230, 30))
    preview.save(OUT / "preview.png")

    round_preview = Image.new("RGBA", (240, 240), (245, 245, 245, 255))
    round_preview.alpha_composite(legacy_icon(192, round_shape=True), (24, 24))
    round_preview.save(OUT / "preview_round.png")

    print(f"图标已生成：{OUT}")
    for f in sorted(OUT.glob("*.png")):
        print(f"  {f.name}  {f.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
