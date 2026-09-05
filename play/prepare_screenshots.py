#!/usr/bin/env python3
"""
Turns raw phone screenshots into Play-legal store screenshots.

Play requires each side between 320 and 3840 px and the long side no more than twice the
short one. A modern phone screenshot breaks that last rule on its own -- 1080x2340 is 2.17:1
-- so raw captures are rejected before anyone looks at them. This letterboxes each one onto a
1080x1920 (exactly 9:16) canvas painted in the app's own background gradient, so the padding
reads as part of the design rather than as empty bars, and adds an optional caption.

Usage:
    python3 play/prepare_screenshots.py OUT_DIR shot1.jpg "Подпись" shot2.jpg "Другая" ...

Pass an empty string for a caption to leave that screenshot uncaptioned.
"""

import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_DIR = "/mnt/skills/examples/canvas-design/canvas-fonts"
CANVAS = (1080, 1920)
BG_FROM, BG_TO = (0xFF, 0xEF, 0xE1), (0xFC, 0xE1, 0xEC)
INK = (0x2A, 0x21, 0x1C)

CAPTION_BAND = 210     # vertical space reserved at the top when a caption is given
SIDE_MARGIN = 56
BOTTOM_MARGIN = 40
CORNER_RADIUS = 34


def gradient(size, c_from, c_to):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / max(w - 1, 1) + y / max(h - 1, 1)) / 2
            px[x, y] = tuple(round(c_from[i] + (c_to[i] - c_from[i]) * t) for i in range(3))
    return img


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                                           radius=radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def fit_font(text, max_width, start=54, minimum=30):
    """Largest size at which the caption still fits one line, down to a floor."""
    for size in range(start, minimum - 1, -2):
        font = ImageFont.truetype(f"{FONT_DIR}/Outfit-Bold.ttf", size)
        if font.getbbox(text)[2] <= max_width:
            return font
    return ImageFont.truetype(f"{FONT_DIR}/Outfit-Bold.ttf", minimum)


def compose(shot_path, caption):
    canvas = gradient(CANVAS, BG_FROM, BG_TO).convert("RGBA")
    top = CAPTION_BAND if caption else BOTTOM_MARGIN

    box_w = CANVAS[0] - SIDE_MARGIN * 2
    box_h = CANVAS[1] - top - BOTTOM_MARGIN
    shot = Image.open(shot_path).convert("RGB")
    scale = min(box_w / shot.width, box_h / shot.height)
    shot = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    shot = rounded(shot, CORNER_RADIUS)

    x = (CANVAS[0] - shot.width) // 2
    y = top + (box_h - shot.height) // 2

    # A soft drop shadow lifts the screenshot off the gradient; without it the rounded corners
    # read as a rendering glitch rather than a deliberate frame.
    shadow = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [x, y + 10, x + shot.width, y + shot.height + 10], radius=CORNER_RADIUS,
        fill=(90, 40, 60, 70))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(22)))
    canvas.alpha_composite(shot, (x, y))

    if caption:
        draw = ImageDraw.Draw(canvas)
        font = fit_font(caption, CANVAS[0] - SIDE_MARGIN * 2)
        w = font.getbbox(caption)[2]
        draw.text(((CANVAS[0] - w) // 2, 96), caption, font=font, fill=INK)

    return canvas.convert("RGB")


def main(argv):
    if len(argv) < 4 or len(argv) % 2 != 0:
        sys.exit(__doc__.strip())
    out_dir = argv[1].rstrip("/")
    pairs = list(zip(argv[2::2], argv[3::2]))
    for i, (path, caption) in enumerate(pairs, start=1):
        out = f"{out_dir}/screenshot-{i}.png"
        compose(path, caption).save(out, "PNG")
        print(f"{out}  ({CANVAS[0]}x{CANVAS[1]})  {caption or 'без подписи'}")


if __name__ == "__main__":
    main(sys.argv)
