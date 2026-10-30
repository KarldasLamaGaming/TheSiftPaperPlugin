#!/usr/bin/env python3
"""Draws the 16x16 item textures of the portal keys and the Sift compass (step 7b).

The original has no such items, so the art is generated here once and committed. Colours: sculk teal
(echo resonator), soul fire blue (soul ember core), ichor violet (void resonator, compass needle) on
sonorous-deepslate grey. Replace the PNGs in pack-src/resourcepack-overlay/assets/the_sift/textures/item/
with hand-made ones at any time (same names).

Compass frames follow vanilla: sift_compass_16 points straight ahead (up), the needle turns clockwise by
11.25 degrees per frame (20 = up-right, 24 = right, 00 = down, 08 = left).

Usage: python3 -I scripts/gen_portal_item_textures.py   (from the repository root)
"""
import json
import math
from pathlib import Path

from PIL import Image

PACK = Path("pack-src/resourcepack/assets/the_sift/textures/block")
ASSETS = Path("pack-src/resourcepack-overlay/assets/the_sift")
OUT = ASSETS / "textures/item"
KEYS = ("echo_resonator", "soul_ember_core", "void_resonator")


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255, 255)


# dark, mid, light
SCULK_TEAL = (rgb(0x0B3B44), rgb(0x1C8A93), rgb(0x6FE6EA))
SOUL_BLUE = (rgb(0x14304A), rgb(0x2A9FD6), rgb(0xB4F2FF))
ICHOR_VIOLET = (rgb(0x2E1452), rgb(0x7A45C2), rgb(0xD8B8FF))
DEEPSLATE = (rgb(0x1B1B22), rgb(0x34343F), rgb(0x5A5A68))


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def disc(im, cx, cy, r, colour):
    for y in range(16):
        for x in range(16):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                im.putpixel((x, y), colour)


def ring(im, cx, cy, r, colour):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r - 0.75 <= d <= r + 0.25:
                im.putpixel((x, y), colour)


def echo_resonator():
    dark, mid, light = SCULK_TEAL
    im = blank()
    ring(im, 8, 8, 6.5, dark)
    ring(im, 8, 8, 5.5, mid)
    for y in range(3, 13):  # crystal in the middle
        half = 2 - abs(y - 7.5) // 2.5
        for x in range(int(8 - half) - 1, int(8 + half) + 1):
            im.putpixel((x, y), light if x in (7, 8) else mid)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        im.putpixel((x, y), light)
    return im


def soul_ember_core():
    dark, mid, light = SOUL_BLUE
    cage = DEEPSLATE[1]
    im = blank()
    disc(im, 8, 8, 6.0, dark)
    disc(im, 8, 8, 4.5, mid)
    disc(im, 8, 8, 2.5, light)
    for i in range(2, 14):  # blackstone cage
        if abs(i - 7.5) > 1.5:
            im.putpixel((i, 7), cage)
            im.putpixel((7, i), cage)
    im.putpixel((7, 7), (255, 255, 255, 255))
    return im


def void_resonator():
    dark, mid, light = ICHOR_VIOLET
    im = blank()
    for y in range(16):
        for x in range(16):
            e = ((x + 0.5 - 8) / 7) ** 2 + ((y + 0.5 - 8) / 4.5) ** 2
            if e <= 1:
                im.putpixel((x, y), mid if e > 0.55 else light)
    disc(im, 8, 8, 2.2, dark)
    im.putpixel((7, 7), light)
    return im


def compass_frames():
    shade, body, rim = DEEPSLATE
    violet_dark, violet, violet_light = ICHOR_VIOLET
    frames = []
    for frame in range(32):
        im = blank()
        disc(im, 8, 8, 6.5, shade)
        ring(im, 8, 8, 6.0, rim)
        disc(im, 8, 8, 5.0, violet_dark)
        disc(im, 8, 8, 4.2, body)
        angle = math.radians((frame - 16) * 11.25)  # clockwise from up
        dx, dy = math.sin(angle), -math.cos(angle)
        steps = [i / 2 for i in range(-4, 9)]  # half steps keep the line unbroken
        for step in steps:
            x = int(math.floor(8 + dx * step))
            y = int(math.floor(8 + dy * step))
            if 0 <= x < 16 and 0 <= y < 16:
                im.putpixel((x, y), violet_light if step >= 2.5 else violet if step >= 0 else rim)
        frames.append(im)
    return frames


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def generated(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": "the_sift:item/" + texture}}


def compass_dispatch(target):
    """Like vanilla's compass: value 0 (target ahead) shows frame 16, every 1/32 turn the next frame."""
    entries = [{"threshold": 0.0, "model": {"type": "minecraft:model", "model": "the_sift:item/sift_compass_16"}}]
    for k in range(1, 32):
        entries.append({"threshold": k - 0.5,
                        "model": {"type": "minecraft:model", "model": f"the_sift:item/sift_compass_{(16 + k) % 32:02d}"}})
    entries.append({"threshold": 31.5, "model": {"type": "minecraft:model", "model": "the_sift:item/sift_compass_16"}})
    return {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": target, "scale": 32.0,
            "entries": entries}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    echo_resonator().save(OUT / "echo_resonator.png")
    soul_ember_core().save(OUT / "soul_ember_core.png")
    void_resonator().save(OUT / "void_resonator.png")
    for i, im in enumerate(compass_frames()):
        im.save(OUT / f"sift_compass_{i:02d}.png")
        write_json(ASSETS / f"models/item/sift_compass_{i:02d}.json", generated(f"sift_compass_{i:02d}"))
    for key in KEYS:
        write_json(ASSETS / f"models/item/{key}.json", generated(key))
        write_json(ASSETS / f"items/{key}.json", {"model": {"type": "minecraft:model", "model": "the_sift:item/" + key}})
    # needle on the lodestone target; without one (never set yet) it spins like a compass in the nether
    write_json(ASSETS / "items/sift_compass.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": "minecraft:lodestone_tracker",
        "on_true": compass_dispatch("lodestone"), "on_false": compass_dispatch("none")}})
    print("wrote", len(list(OUT.glob("*.png"))), "textures and their models to", ASSETS)


if __name__ == "__main__":
    main()
