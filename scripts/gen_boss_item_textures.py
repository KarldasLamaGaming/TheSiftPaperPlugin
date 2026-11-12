#!/usr/bin/env python3
"""Draws the 16x16 item textures of the boss items (phase 4, alpha.39).

Own art, no Mojang files: Herz des Wächters (sculk-teal heart with a dark core), Drachenschuppe (violet scale),
Kern des Withers (black skull-shaped core with a pale glow), Nachtkristall (golem_trophy, the golem boss item; soul-blue crystal in a
deepslate setting). Replace the PNGs in pack-src/resourcepack-overlay/assets/the_sift/textures/item/ with hand-made
ones at any time (same names). Since alpha.40.2 the items are 3D models (scripts/gen_item_models_3d.py writes the
models and item definitions); these flat images are their particle textures.

Usage: python3 -I scripts/gen_boss_item_textures.py   (from the repository root)
"""
import math
from pathlib import Path

from PIL import Image

ASSETS = Path("pack-src/resourcepack-overlay/assets/the_sift")
OUT = ASSETS / "textures/item"
ITEMS = ("warden_heart", "dragon_scale", "wither_core", "golem_trophy", "charoite_core")


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def heart_shape(x, y):
    # implicit heart curve, scaled to the 16x16 grid
    u = (x + 0.5 - 8) / 5.4
    v = (7.6 - (y + 0.5)) / 5.4
    return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0


def warden_heart():
    dark, mid, light, core = rgb(0x0B3B44), rgb(0x1C8A93), rgb(0x6FE6EA), rgb(0x062227)
    im = blank()
    for y in range(16):
        for x in range(16):
            if heart_shape(x, y):
                edge = not all(heart_shape(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                im.putpixel((x, y), dark if edge else mid)
    for x, y in ((4, 5), (5, 4), (5, 5)):
        im.putpixel((x, y), light)
    for x, y in ((7, 8), (8, 8), (7, 9), (8, 9), (8, 7)):
        im.putpixel((x, y), core)
    return im


def dragon_scale():
    dark, mid, light = rgb(0x2E1452), rgb(0x7A45C2), rgb(0xD8B8FF)
    im = blank()
    for y in range(16):
        for x in range(16):
            # a pointed scale: rounded top, tip at the bottom
            u = (x + 0.5 - 8) / 6.0
            v = (y + 0.5 - 6.0) / 6.0
            inside = u * u + v * v <= 1 if v <= 0 else abs(u) <= 1 - v * 0.75 and v <= 1.33
            if inside:
                im.putpixel((x, y), mid)
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3] and any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or not im.getpixel((x + dx, y + dy))[3]
                                              for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), dark)
    for y in range(3, 12):  # ridge
        im.putpixel((8, y), light)
    for x, y in ((6, 4), (5, 5), (10, 4), (11, 5)):
        im.putpixel((x, y), light)
    return im


def wither_core():
    black, grey, pale = rgb(0x111114), rgb(0x34343F), rgb(0xE8E6C8)
    im = blank()
    for y in range(16):
        for x in range(16):
            if math.hypot(x + 0.5 - 8, y + 0.5 - 8) <= 6.3:
                im.putpixel((x, y), black)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.4 <= d <= 6.3:
                im.putpixel((x, y), grey)
    for x, y in ((5, 6), (6, 6), (5, 7), (9, 6), (10, 6), (10, 7)):  # eyes
        im.putpixel((x, y), pale)
    for x in range(6, 10, 2):  # teeth
        im.putpixel((x, 10), pale)
    im.putpixel((7, 11), pale)
    im.putpixel((8, 11), pale)
    return im


def golem_trophy():
    shade, body, rim = rgb(0x1B1B22), rgb(0x34343F), rgb(0x5A5A68)
    dark, mid, light = rgb(0x14304A), rgb(0x2A9FD6), rgb(0xB4F2FF)
    im = blank()
    for x in range(3, 13):  # deepslate base
        for y in (12, 13):
            im.putpixel((x, y), rim if y == 12 else body)
    for y in range(2, 12):  # crystal
        half = 3 - abs(y - 7) // 2
        for x in range(8 - half, 8 + half):
            im.putpixel((x, y), light if x == 7 and y < 8 else mid if x < 8 else dark)
    for x in (2, 13):
        im.putpixel((x, 13), shade)
    return im


def charoite_core():
    """Charoit-Kern (alpha.40, working title): violet charoite swirls around a teal-white heart."""
    dark, mid, light = rgb(0x3B1F5C), rgb(0x8E5BC9), rgb(0xE2C8FF)
    teal = rgb(0x6FE6EA)
    im = blank()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 6.2:
                swirl = (math.atan2(y + 0.5 - 8, x + 0.5 - 8) * 2 + d * 0.9) % (2 * math.pi)
                im.putpixel((x, y), dark if d > 5.3 else light if swirl < 0.9 else mid)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        im.putpixel((x, y), teal)
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, draw in (("warden_heart", warden_heart), ("dragon_scale", dragon_scale), ("wither_core", wither_core),
                       ("golem_trophy", golem_trophy), ("charoite_core", charoite_core)):
        draw().save(OUT / f"{name}.png")
    print("wrote", len(ITEMS), "boss item textures to", OUT, "(models: scripts/gen_item_models_3d.py)")


if __name__ == "__main__":
    main()
