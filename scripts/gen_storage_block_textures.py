#!/usr/bin/env python3
"""Draws the Siftit-Block and the Charoit-Block (phase 4, alpha.41.1): storage blocks (9 ingots / 9 charoite) and the
ornament blocks of the temples.

Siftit-Block: pale blue-silver metal in four bevelled plates with a soul-blue seam. Charoit-Block: polished charoite,
violet with fibrous white-lilac swirls (like the real mineral). Own art, no Mojang files, fixed seeds (a second run
writes the same bytes). Also writes the block models (cube_all), the blockstates (read by packgen for the
mushroom-state override) and the item definitions. Replace the PNGs in
pack-src/resourcepack-overlay/assets/the_sift/textures/block/{siftite_block,charoite_block}.png at any time.

Usage: python3 -I scripts/gen_storage_block_textures.py   (from the repository root)
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("pack-src/resourcepack-overlay/assets/the_sift")


def c(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


SILVER = [c(0x5E6A78), c(0x8C99A8), c(0xB7C3CF), c(0xDCE6EE), c(0xF4F9FC)]
SOUL = c(0x55C8EC)
CHAROITE = [c(0x3A1E5E), c(0x5B3290), c(0x7C4CC0), c(0xA27BDD), c(0xD9C6F5)]


def siftite():
    r = random.Random(4101)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px, py = x % 8, y % 8
            if px == 0 or py == 0:
                col = SILVER[3] if (px == 0 and py != 0 and x < 8) or (py == 0 and y < 8) else SILVER[1]
            elif px == 7 or py == 7:
                col = SILVER[0]
            else:
                # diagonal sheen per plate
                t = (px + py) / 12
                col = SILVER[2] if r.random() < .8 else SILVER[3]
                if t < .35:
                    col = SILVER[3]
            im.putpixel((x, y), col)
    # outer rim and a soul-blue seam around the centre
    for i in range(16):
        im.putpixel((i, 0), SILVER[4]); im.putpixel((0, i), SILVER[4])
        im.putpixel((i, 15), SILVER[0]); im.putpixel((15, i), SILVER[0])
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        im.putpixel((x, y), SOUL)
    return im


def charoite():
    r = random.Random(4102)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            # swirling fibres: phase of a slowly turning sine field
            # periodic over 16 px so the block tiles without seams
            t = 2 * math.pi / 16
            a = math.sin(t * x + 1.6 * math.sin(t * y)) + 0.8 * math.cos(t * 2 * y - t * x)
            v = (a + 1.8) / 3.6
            idx = max(0, min(3, int(v * 4 + r.uniform(-.35, .35))))
            col = CHAROITE[idx]
            if abs(a - 0.9) < 0.12 and r.random() < .8:
                col = CHAROITE[4]  # white-lilac fibre
            im.putpixel((x, y), col)
    return im


def write(block, image):
    tex = ASSETS / "textures/block"
    tex.mkdir(parents=True, exist_ok=True)
    image.save(tex / f"{block}.png")
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / f"models/block/{block}.json").write_text(json.dumps(
        {"parent": "minecraft:block/cube_all", "textures": {"all": f"the_sift:block/{block}"}}, indent=2) + "\n")
    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    (ASSETS / f"blockstates/{block}.json").write_text(json.dumps(
        {"variants": {"": {"model": f"the_sift:block/{block}"}}}, indent=2) + "\n")
    (ASSETS / f"items/{block}.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"the_sift:block/{block}"}}, indent=2) + "\n")


def main():
    write("siftite_block", siftite())
    write("charoite_block", charoite())


if __name__ == "__main__":
    main()
