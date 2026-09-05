#!/usr/bin/env python3
"""
Renders the two images Google Play requires as uploads: the 512x512 hi-res icon and the
1024x500 feature graphic.

Both are drawn from the same source of truth as the in-app launcher icon --
android/app/src/main/res/drawable/ic_launcher_{background,foreground}_wine.xml -- rather than
exported by hand, so the store artwork can never drift from the icon on the phone. The wine
palette is the app's default and the one the launcher ships enabled.

Play rejects alpha in both images, so everything is composited onto an opaque background.

Usage:  python3 play/generate_assets.py
"""

from PIL import Image, ImageDraw, ImageFont

OUT_DIR = __file__.rsplit("/", 1)[0]
FONT_DIR = "/mnt/skills/examples/canvas-design/canvas-fonts"

# --- straight from ic_launcher_background_wine.xml / ic_launcher_foreground_wine.xml ---
VIEWPORT = 108.0
BG_FROM, BG_TO = (0xFF, 0xEF, 0xE1), (0xFC, 0xE1, 0xEC)
STROKE_FROM, STROKE_TO = (0xB0, 0x24, 0x5C), (0xFF, 0x5C, 0x8A)
STROKE_GRADIENT_X = (10.0, 90.0)

# Each wave: (list of cubic segments as 4 control points, stroke width) in viewport units.
WAVES = [
    ([((14, 74), (10, 80), (14, 86), (21, 80)),
      ((21, 80), (31, 62), (45, 58), (55, 68)),
      ((55, 68), (65, 78), (77, 82), (87, 62))], 9.0),
    ([((18, 66), (28, 48), (42, 44), (52, 54)),
      ((52, 54), (62, 64), (74, 68), (84, 48))], 8.0),
    ([((21, 60), (31, 42), (45, 38), (55, 48)),
      ((55, 48), (65, 58), (77, 62), (87, 42)),
      ((87, 42), (90, 36), (86, 30), (78, 34))], 7.0),
]

SS = 4  # supersampling factor; everything is drawn this many times larger, then box-filtered down


def lerp_rgb(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def bezier(p0, p1, p2, p3, steps):
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        yield (u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
               u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1])


def diagonal_gradient(size, c_from, c_to):
    """Linear gradient along the top-left -> bottom-right diagonal, matching the vector's."""
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = lerp_rgb(c_from, c_to, (x / max(w - 1, 1) + y / max(h - 1, 1)) / 2)
    return img


def draw_waves(draw, scale, offset=(0.0, 0.0), alpha_layer=False):
    """Strokes the three waves. Round caps and joins come free from stamping a disc at every
    sampled point, which also lets each point take its own color off the horizontal gradient --
    a single polyline could not vary color along its length."""
    gx0, gx1 = STROKE_GRADIENT_X
    for segments, width in WAVES:
        radius = width * scale / 2.0
        for seg in segments:
            # Enough samples that consecutive discs overlap heavily even on the tightest curl.
            for (x, y) in bezier(*seg, steps=400):
                color = lerp_rgb(STROKE_FROM, STROKE_TO, (x - gx0) / (gx1 - gx0))
                cx = offset[0] + x * scale
                cy = offset[1] + y * scale
                draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius],
                             fill=color + (255,) if alpha_layer else color)


def build_icon(px=512):
    big = px * SS
    img = diagonal_gradient((big, big), BG_FROM, BG_TO)
    draw_waves(ImageDraw.Draw(img), scale=big / VIEWPORT)
    return img.resize((px, px), Image.LANCZOS)


def load_font(name, size):
    return ImageFont.truetype(f"{FONT_DIR}/{name}", size)


def build_feature_graphic(w=1024, h=500):
    """Play crops this differently across surfaces, so the wordmark and tagline stay well inside
    the middle and nothing load-bearing touches an edge."""
    big_w, big_h = w * SS, h * SS
    img = diagonal_gradient((big_w, big_h), BG_FROM, BG_TO)

    # The glyph, oversized and bled off the right edge -- decoration, not information, so losing
    # its tip to a crop costs nothing.
    glyph = Image.new("RGBA", (big_w, big_h), (0, 0, 0, 0))
    glyph_scale = big_h / VIEWPORT * 1.55
    draw_waves(ImageDraw.Draw(glyph), scale=glyph_scale,
               offset=(big_w - VIEWPORT * glyph_scale * 0.92, -big_h * 0.30), alpha_layer=True)
    img = Image.alpha_composite(img.convert("RGBA"), glyph).convert("RGB")

    draw = ImageDraw.Draw(img)
    title = load_font("Outfit-Bold.ttf", int(96 * SS))
    tagline = load_font("Outfit-Regular.ttf", int(38 * SS))
    sub = load_font("Outfit-Regular.ttf", int(28 * SS))

    x = int(72 * SS)
    draw.text((x, int(150 * SS)), "Interlude", font=title, fill=(0x2A, 0x21, 0x1C))
    draw.text((x, int(268 * SS)), "Цикл и близость —", font=tagline, fill=(0xB0, 0x24, 0x5C))
    draw.text((x, int(316 * SS)), "в одном календаре", font=tagline, fill=(0xB0, 0x24, 0x5C))
    draw.text((x, int(378 * SS)), "Без аккаунта. Без интернета. Только на телефоне.",
              font=sub, fill=(0x8A, 0x7A, 0x6E))

    return img.resize((w, h), Image.LANCZOS)


if __name__ == "__main__":
    build_icon().save(f"{OUT_DIR}/icon-512.png", "PNG")
    build_feature_graphic().save(f"{OUT_DIR}/feature-graphic-1024x500.png", "PNG")
    print("wrote icon-512.png and feature-graphic-1024x500.png")
