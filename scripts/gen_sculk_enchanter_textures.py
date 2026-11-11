#!/usr/bin/env python3
"""Draws the Sculk Enchanter block (phase 4, alpha.40.1; Karl's choice: top of variant B, sides of variant A).

Top: violet charoite rune circle on sculk. Sides: siftslate masonry with a glowing soul-blue engraved band and
sculk veins below. Bottom: plain dark siftslate. Own art, no Mojang files; replace the PNGs in
pack-src/resourcepack-overlay/assets/the_sift/textures/block/sculk_enchanter_{top,side,bottom}.png at any time.
Also writes the block model, the blockstate (read by packgen for the mushroom-state override) and the item
definition (the item shows the block as a cube in hand, inventory and recipe book).

Usage: python3 -I scripts/gen_sculk_enchanter_textures.py   (from the repository root)
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("pack-src/resourcepack-overlay/assets/the_sift")


def c(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


SL = [c(0x15151B), c(0x1F1F28), c(0x2A2A35), c(0x3A3A48)]  # dark siftslate
SC = [c(0x062227), c(0x0B3B44), c(0x1C8A93), c(0x6FE6EA)]  # sculk teal
CH = [c(0x2B1545), c(0x5E3592), c(0x9B6BD6), c(0xE2C8FF)]  # charoite violet
SO = [c(0x14304A), c(0x2A9FD6), c(0x7FD8FF), c(0xD6F6FF)]  # soul blue


def slate(seed):
    r = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), SL[r.choice([0, 1, 1, 1, 2, 2])])
    return im


def border(im, col):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel(p, col)


def top():
    """Variant B: charoite rune circle on sculk."""
    im = Image.new("RGBA", (16, 16))
    r = random.Random(5)
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), SC[r.choice([0, 1, 1, 0])])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 5.6 <= d <= 6.6:
                im.putpixel((x, y), CH[2])
            elif 6.6 < d <= 7.2:
                im.putpixel((x, y), CH[1])
    for i in range(-3, 4):
        im.putpixel((8 + i // 2, 8 + i), CH[3])
        im.putpixel((7, 8 + i), CH[3])
    for x in range(5, 11):
        im.putpixel((x, 8), CH[2])
    for a in range(4):
        ang = a * math.pi / 2 + math.pi / 4
        im.putpixel((int(8 + math.cos(ang) * 6.1), int(8 + math.sin(ang) * 6.1)), SO[3])
    return im


def side():
    """Variant A: masonry with a glowing soul-blue engraved band, sculk veins below."""
    im = slate(2)
    for y in (0, 5, 10, 15):
        for x in range(16):
            im.putpixel((x, y), SL[0])
    for row, y0 in enumerate((0, 5, 10)):
        for x in range(0 if row % 2 == 0 else 4, 16, 8):
            for y in range(y0, min(y0 + 5, 16)):
                im.putpixel((x, y), SL[0])
    border(im, SL[3])
    for x in range(1, 15):
        im.putpixel((x, 6), SO[0])
        im.putpixel((x, 9), SO[0])
    for x in range(2, 14):
        if x % 3 != 1:
            im.putpixel((x, 7), SO[2])
            im.putpixel((x, 8), SO[1] if x % 2 else SO[3])
    r = random.Random(3)
    for _ in range(3):
        x, y = r.randrange(16), r.choice(range(11, 15))
        for _ in range(6):
            im.putpixel((x % 16, y % 16), SC[2])
            if r.random() < 0.3:
                im.putpixel(((x + 1) % 16, y % 16), SC[3])
            x += r.choice([-1, 0, 1])
            y += r.choice([-1, 0])
    return im


def bottom():
    im = slate(4)
    border(im, SL[2])
    return im


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    textures = ASSETS / "textures/block"
    textures.mkdir(parents=True, exist_ok=True)
    for name, draw in (("top", top), ("side", side), ("bottom", bottom)):
        draw().save(textures / f"sculk_enchanter_{name}.png")
    write_json(ASSETS / "models/block/sculk_enchanter.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": "the_sift:block/sculk_enchanter_top", "side": "the_sift:block/sculk_enchanter_side",
                     "bottom": "the_sift:block/sculk_enchanter_bottom"}})
    write_json(ASSETS / "blockstates/sculk_enchanter.json", {"variants": {"": {"model": "the_sift:block/sculk_enchanter"}}})
    write_json(ASSETS / "items/sculk_enchanter.json",
               {"model": {"type": "minecraft:model", "model": "the_sift:block/sculk_enchanter"}})
    print("wrote the Sculk Enchanter block textures, model, blockstate and item definition to", ASSETS)


if __name__ == "__main__":
    main()
