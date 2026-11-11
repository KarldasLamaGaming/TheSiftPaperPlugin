#!/usr/bin/env python3
"""Draws the 16x16 item texture of the temple rumour note (phase 4, alpha.41b) and writes its model and item
definition into the pack overlay.

Own art, no Mojang file: a torn parchment note with a soul-blue temple glyph (arch on a stepped base) and a
charoite wax seal. Deterministic (fixed seed), a second run writes the same bytes. Replace the PNG
pack-src/resourcepack-overlay/assets/the_sift/textures/item/temple_rumor.png with a hand-made one at any time.

Usage: python3 -I scripts/gen_temple_rumor_texture.py   (from the repository root)
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("pack-src/resourcepack-overlay/assets/the_sift")


def rgb(value, alpha=255):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, alpha)


PAPER = (rgb(0x8C7A5B), rgb(0xC9B48A), rgb(0xE6D6AE))  # edge, body, highlight
SOUL = (rgb(0x14304A), rgb(0x2A9FD6), rgb(0x8FE3FF))
CHAROITE = (rgb(0x3B1C5E), rgb(0x7A45C2), rgb(0xC8A6F2))


def draw():
    rnd = random.Random(4141)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # sheet: x 2..13, y 1..14, slightly skewed rows, torn bottom edge
    torn = [14 - (1 if rnd.random() < 0.35 else 0) for _ in range(16)]
    for y in range(1, 15):
        shift = 1 if y < 5 else 0
        for x in range(2 + shift, 14 + shift - 1):
            if y > torn[x]:
                continue
            edge = x == 2 + shift or x == 12 + shift or y == 1 or y == torn[x]
            colour = PAPER[0] if edge else PAPER[2] if (x + y * 3) % 11 == 0 else PAPER[1]
            im.putpixel((x, y), colour)
    # fold line
    for x in range(4, 12):
        if im.getpixel((x, 7))[3]:
            im.putpixel((x, 7), PAPER[0] if x % 2 else PAPER[1])
    # temple glyph: arch over a stepped base
    glyph = ["..###..", ".#...#.", ".#...#.", ".#...#.", "#######", ".......", "#######"]
    for gy, row in enumerate(glyph):
        for gx, c in enumerate(row):
            if c == "#":
                im.putpixel((5 + gx, 2 + gy if gy < 5 else 3 + gy), SOUL[1] if gy % 2 else SOUL[0])
    im.putpixel((7, 3), SOUL[2])  # the glow inside the arch
    # charoite seal at the lower right
    for x, y, c in [(10, 11, 1), (11, 11, 1), (10, 12, 1), (11, 12, 0), (9, 12, 0), (10, 13, 0), (11, 10, 2)]:
        im.putpixel((x, y), CHAROITE[c])
    return im


def main():
    textures = ASSETS / "textures/item"
    textures.mkdir(parents=True, exist_ok=True)
    draw().save(textures / "temple_rumor.png")
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": "the_sift:item/temple_rumor"}}
    (ASSETS / "models/item/temple_rumor.json").write_text(json.dumps(model, indent=2) + "\n")
    item = {"model": {"type": "minecraft:model", "model": "the_sift:item/temple_rumor"}}
    (ASSETS / "items/temple_rumor.json").write_text(json.dumps(item, indent=2) + "\n")


if __name__ == "__main__":
    main()
