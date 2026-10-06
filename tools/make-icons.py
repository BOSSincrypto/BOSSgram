r"""Generate BOSSgram launcher art (dark + orange B, full-bleed).

Covers BOTH launcher chains actually used by the app:
- icon_6_* (activity-alias icons)
- ic_launcher* / icon_foreground* / icon_background* / icon_plane (the effective
  launcher, see aapt dump badging -> mipmap-anydpi-v26/ic_launcher.xml)

Full-bleed: тёмный фон на всю ширину, B занимает ~78% иконки.
Output mirrors upstream res layout so it can be copied over:
  bossgram/assets/icons/mipmap-<dpi>/*.png
  bossgram/assets/icons/drawable-<dpi>/ic_launcher_dr.webp
(Vector/xml slots icon_*_background_sa.xml and icon_plane.xml ship in patches/0002.)
Usage: python tools/make-icons.py
Then: xcopy bossgram\assets\icons ..\Telegram-upstream\TMessagesProj\src\main\res /E /Y
"""
from PIL import Image, ImageDraw
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "bossgram" / "assets" / "icons"

BG = (27, 27, 34, 255)      # graphite, full-bleed dark фон на всю ширину
FG = (255, 107, 26, 255)    # boss orange
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FG_SCALE = 2.25  # upstream foreground artwork is 2.25x launcher (192 -> 432)
# Full-bleed: B занимает ~78% иконки (минимальные поля 11%), фон залит целиком.
LEGACY_CAP_RATIO = 0.78
FG_CAP_RATIO = 0.44


def draw_b(draw, s, ox, oy, color):
    """Geometric 'B': stem + two bowls. s = cap height in px."""
    stem_w = max(1, int(s * 0.16))
    # stem
    draw.rounded_rectangle([ox, oy, ox + stem_w, oy + s], radius=stem_w // 2, fill=color)
    # bowls as thick ellipse outlines, right of stem
    for half, yy in ((0, oy), (1, oy + s // 2)):
        x0 = ox + stem_w // 3
        y0 = yy
        x1 = ox + int(s * 0.78)
        y1 = yy + s // 2
        draw.ellipse([x0, y0, x1, y1], outline=color, width=max(2, int(s * 0.13)))


def circle_mask(im):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, im.size[0], im.size[1]], fill=255)
    out = im.copy()
    out.putalpha(Image.composite(im.getchannel("A"), Image.new("L", im.size, 0), m))
    # re-apply circle over rgb too is unnecessary; alpha is what matters
    return out


def rounded_bg(size, radius_ratio=0.22):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle(
        [0, 0, size, size], radius=int(size * radius_ratio), fill=BG)
    return im


def main():
    for dpi, px in DENSITIES.items():
        d = OUT / f"mipmap-{dpi}"
        d.mkdir(parents=True, exist_ok=True)

        # legacy launcher (rounded square + B, full-bleed: фон на всю ширину)
        icon = rounded_bg(px)
        dr = ImageDraw.Draw(icon)
        cap = int(px * LEGACY_CAP_RATIO)
        draw_b(dr, cap, (px - int(cap * 0.78)) // 2, (px - cap) // 2, FG)
        icon.save(d / "icon_6_launcher.png")

        # legacy round (circle + B, full-bleed)
        circ = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        ImageDraw.Draw(circ).ellipse([0, 0, px, px], fill=BG)
        dr = ImageDraw.Draw(circ)
        cap = int(px * LEGACY_CAP_RATIO)
        draw_b(dr, cap, (px - int(cap * 0.78)) // 2, (px - cap) // 2, FG)
        circ.save(d / "icon_6_launcher_round.png")

        # adaptive foreground (transparent + B, artwork 2.25x, full-bleed)
        fpx = int(px * FG_SCALE)
        fg = Image.new("RGBA", (fpx, fpx), (0, 0, 0, 0))
        dr = ImageDraw.Draw(fg)
        cap = int(fpx * FG_CAP_RATIO)
        draw_b(dr, cap, (fpx - int(cap * 0.78)) // 2, (fpx - cap) // 2, FG)
        fg.save(d / "icon_6_foreground.png")
        fg.save(d / "icon_6_foreground_sa.png")
        circle_mask(fg).save(d / "icon_6_foreground_round.png")

        # ic_launcher chain (the effective launcher): same art, telegram names
        icon.save(d / "ic_launcher.png")
        circ.save(d / "ic_launcher_round.png")
        fg.save(d / "icon_foreground.png")
        fg.save(d / "icon_foreground_sa.png")
        circle_mask(fg).save(d / "icon_foreground_round.png")

        # direct-share webp
        dd = OUT / f"drawable-{dpi}"
        dd.mkdir(parents=True, exist_ok=True)
        icon.save(dd / "ic_launcher_dr.webp", format="WEBP", quality=90)

    total = len(list(OUT.rglob("*.png")))
    print(f"OK: {total} icons in {OUT}")


if __name__ == "__main__":
    main()
