#!/usr/bin/env python3
"""Draws the Charoite Wisp (alpha.42d, our own mob for zone 3 of the temple brood spawners): model, texture, animations.

A sculk-turquoise core with a bright eye, eight charoite shards circling it, a siftslate halo above and a fading
sculk tail below. Writes the GeckoLib-style files packgen turns into the display rig, as for the Dark Sniffer:
packgen/src/main/resources/mobs/charoite_wisp.{geo.json,animation.json,png}. Own art, no Mojang files; the PNG can be
replaced (same layout) at any time, then run packgen importSift and release.

Usage: python3 -I scripts/gen_charoite_wisp.py   (from the repository root)
"""
import json
import random
from pathlib import Path

from PIL import Image

OUT = Path("packgen/src/main/resources/mobs")
W = H = 64


def c(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


SC = [c(0x062227), c(0x0B3B44), c(0x1C8A93), c(0x6FE6EA), c(0xC8FBFF)]  # sculk turquoise, dark to bright
CH = [c(0x2B1545), c(0x5E3592), c(0x9B6BD6), c(0xE2C8FF)]  # charoite violet
SL = [c(0x1F1F28), c(0x2A2A35), c(0x3A3A48), c(0x55556A)]  # siftslate

# name, bone, origin, size, uv, material, pivot, rotation
CUBES = []
CUBES.append(("core", "core", [-3, 13, -3], [6, 6, 6], [0, 0], "core", None, None))
CUBES.append(("eye", "core", [-1, 15, -4], [2, 2, 1], [24, 0], "eye", None, None))
for k in range(8):
    tilt = 25 if k % 2 == 0 else -20
    CUBES.append((f"shard{k}", "shards", [-1, 14, 4], [2, 5, 2], [0, 12], "shard", [0, 16, 0], [tilt, k * 45, 0]))
for k in range(4):
    CUBES.append((f"halo{k}", "halo", [-4, 22, -5], [8, 1, 1], [0, 20], "halo", [0, 22.5, 0], [0, k * 90, 0]))
CUBES.append(("tail0", "tail", [-2, 9, -2], [4, 3, 4], [24, 12], "tail0", None, None))
CUBES.append(("tail1", "tail", [-1, 6, -1], [2, 3, 2], [40, 12], "tail1", None, None))
CUBES.append(("tail2", "tail", [-0.5, 4, -0.5], [1, 2, 1], [48, 12], "tail2", None, None))

BONES = [("root", None, [0, 0, 0]), ("core", "root", [0, 16, 0]), ("shards", "root", [0, 16, 0]),
         ("halo", "root", [0, 22.5, 0]), ("tail", "root", [0, 12, 0])]


def color(material, x, y, fw, fh, r):
    """Pixel colour of a material at (x, y) of a face fw x fh."""
    cx, cy = (fw - 1) / 2, (fh - 1) / 2
    edge = min(x, y, fw - 1 - x, fh - 1 - y)
    if material == "core":
        d = max(abs(x - cx), abs(y - cy)) / max(cx, 0.5)
        return SC[4] if d < 0.3 else SC[3] if d < 0.6 else SC[2] if r.random() < 0.8 else SC[1]
    if material == "eye":
        return SC[4]
    if material == "shard":
        if fh > fw:  # long sides: dark root, bright tip
            t = y / max(fh - 1, 1)
            return CH[3] if t < 0.25 else CH[2] if t < 0.6 else CH[1]
        return CH[3]
    if material == "halo":
        return SL[3] if (x + y) % 3 == 0 else SL[2] if edge == 0 else SL[1]
    if material == "tail0":
        return SC[2] if r.random() < 0.7 else SC[3]
    if material == "tail1":
        return SC[1] if r.random() < 0.6 else SC[2]
    return SC[1]


def paint(img, cube, r):
    _, _, _, (w, h, d), (u, v), material, _, _ = cube
    w, h, d = int(round(w)) or 1, int(round(h)) or 1, int(round(d)) or 1
    faces = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h),
             (u + 2 * d + w, v + d, w, h)]
    for fx, fy, fw, fh in faces:
        for y in range(fh):
            for x in range(fw):
                if 0 <= fx + x < W and 0 <= fy + y < H:
                    img.putpixel((fx + x, fy + y), color(material, x, y, fw, fh, r))


def geo():
    bones = []
    for name, parent, pivot in BONES:
        bone = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        cubes = []
        for cube in CUBES:
            if cube[1] != name:
                continue
            out = {"origin": cube[2], "size": cube[3], "uv": cube[4]}
            if cube[6]:
                out["pivot"] = cube[6]
                out["rotation"] = cube[7]
            cubes.append(out)
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.the_sift.charoite_wisp", "texture_width": W, "texture_height": H}, "bones": bones}]}


def kf(pairs):
    return {str(t): {"vector": v} for t, v in pairs}


def animations():
    return {"format_version": "1.8.0", "animations": {
        "idle": {"loop": True, "animation_length": 2.0, "bones": {
            "shards": {"rotation": kf([(0.0, [0, 0, 0]), (1.0, [0, 180, 0]), (2.0, [0, 360, 0])])},
            "halo": {"rotation": kf([(0.0, [0, 0, 0]), (1.0, [0, -180, 0]), (2.0, [0, -360, 0])])},
            "core": {"position": kf([(0.0, [0, 0, 0]), (1.0, [0, 0.8, 0]), (2.0, [0, 0, 0])])},
            "tail": {"rotation": kf([(0.0, [0, 0, 0]), (0.5, [12, 0, 0]), (1.0, [0, 0, 0]), (1.5, [-12, 0, 0]), (2.0, [0, 0, 0])])}}},
        "attack": {"animation_length": 0.5, "bones": {
            "shards": {"scale": kf([(0.0, [1, 1, 1]), (0.2, [1.35, 1.35, 1.35]), (0.5, [1, 1, 1])])},
            "core": {"position": kf([(0.0, [0, 0, 0]), (0.2, [0, 0, -1.5]), (0.5, [0, 0, 0])])}}},
        "blink": {"animation_length": 0.4, "bones": {
            "root": {"scale": kf([(0.0, [1, 1, 1]), (0.15, [0.1, 0.1, 0.1]), (0.25, [0.1, 0.1, 0.1]), (0.4, [1, 1, 1])])}}}}}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    r = random.Random(42)
    for cube in CUBES:
        if cube[0] in ("core", "eye", "shard0", "halo0", "tail0", "tail1", "tail2"):
            paint(img, cube, r)
    img.save(OUT / "charoite_wisp.png")
    (OUT / "charoite_wisp.geo.json").write_text(json.dumps(geo(), indent=2) + "\n")
    (OUT / "charoite_wisp.animation.json").write_text(json.dumps(animations(), indent=2) + "\n")
    print("wrote", OUT / "charoite_wisp.{png,geo.json,animation.json}")


if __name__ == "__main__":
    main()
