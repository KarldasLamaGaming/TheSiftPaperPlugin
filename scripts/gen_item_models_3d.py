#!/usr/bin/env python3
"""3D item models for the boss items, the Charoit-Kern and the enchanter crystal (phase 4, alpha.40.2).

Karl's choice: massive raw boss drops (Herz des Wächters, Kern des Withers, Drachenschuppe), refined items in a
Siftit setting (Golem-Trophäe, Charoit-Kern), and a charoite crystal cluster that floats above the Sculk Enchanter.
The models are built from elements (cuboids); the inventory shows the 3D model like a block. The item definition of
the Charoit-Kern picks the crystal for the display context "none", which only item displays use (the enchanter's
floating crystal is an item display of the Charoit-Kern), and the core everywhere else.

Textures are small material swatches (16x16, own art, no Mojang files) under textures/item/m3d/; the flat icons of
scripts/gen_boss_item_textures.py stay as particle textures. Output is deterministic (fixed seeds).

Usage: python3 -I scripts/gen_item_models_3d.py   (from the repository root)
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("pack-src/resourcepack-overlay/assets/the_sift")

# Sift palette: dark siftslate base, charoite violet, sculk teal / deep blue, siftite silver
PALETTES = {
    "slate": [0x15151B, 0x1F1F28, 0x2A2A35, 0x3A3A48],
    "silver": [0x535A66, 0x767E8B, 0x9EA6B2, 0xCDD3DB],
    "charoite": [0x3B1F5C, 0x5E3592, 0x8E5BC9, 0xB98CEB],
    "charoite_light": [0x6A3FA3, 0x8E5BC9, 0xB98CEB, 0xE2C8FF],
    "teal": [0x0B3B44, 0x125A63, 0x1C8A93, 0x2FB3BB],
    "glow": [0x2FB3BB, 0x4FD3D9, 0x6FE6EA, 0xC8FBFC],
    "core_dark": [0x041418, 0x062227, 0x0B3B44, 0x125A63],
    "soul": [0x14304A, 0x2A9FD6, 0x5BC3F0, 0xB4F2FF],
    "bone": [0xB8B49A, 0xD2CFB4, 0xE8E6C8, 0xF7F6E6],
    "black": [0x0A0A0D, 0x111114, 0x1A1A20, 0x26262E],
    "dviolet": [0x221036, 0x3A1C5C, 0x5A2E8A, 0x7A45C2],
}


def rgba(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)


def swatch(name):
    pal = [rgba(v) for v in PALETTES[name]]
    r = random.Random(sum(ord(ch) * (i + 1) for i, ch in enumerate(name)))  # stable, unlike hash()
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = r.choice([0, 1, 1, 2, 2, 2]) if name not in ("glow", "soul") else r.choice([1, 2, 2, 3])
            if name == "silver" and (x + y) % 7 == 0:
                k = 3
            if name == "dviolet" and (y % 4 == 0 or (x + (y // 4) * 2) % 4 == 0 and y % 4 == 1):
                k = 0  # rows of scales
            if name in ("charoite", "charoite_light"):
                k = (k + (1 if (x * 3 + y * 2 + r.randrange(3)) % 7 == 0 else 0)) % 4
            im.putpixel((x, y), pal[k])
    for i in range(16):  # lighter top and left edge: reads as a bevel at any size
        im.putpixel((i, 0), pal[3])
        im.putpixel((0, i), pal[min(3, 2 + (i % 2))])
    return im


def box(f, t, mat, rot=None, faces=None, mats=None):
    """One element; the uv of each face is sized to the face, so all faces have the same texel density."""
    w, h, d = t[0] - f[0], t[1] - f[1], t[2] - f[2]
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    el = {"from": [round(v, 3) for v in f], "to": [round(v, 3) for v in t], "faces": {}}
    for face, (a, b) in dims.items():
        if faces and face not in faces:
            continue
        el["faces"][face] = {"uv": [0, 0, round(min(16, max(0.5, a)), 3), round(min(16, max(0.5, b)), 3)],
                             "texture": "#" + (mats or {}).get(face, mat)}
    if rot:
        el["rotation"] = {"angle": rot[1], "axis": rot[0], "origin": list(rot[2])}
    return el


Y45 = ("y", 45, (8, 8, 8))


def warden_heart():
    """Massive sculk heart with a dark core and glowing veins (raw boss drop)."""
    return [
        box((3, 8, 6), (8, 13, 10), "teal"), box((8, 8, 6), (13, 13, 10), "teal"),
        box((4, 5, 6), (12, 8, 10), "teal"), box((5, 3, 6.5), (11, 5, 9.5), "teal"), box((7, 1.5, 7), (9, 3, 9), "teal"),
        box((6.5, 6, 5.5), (9.5, 9.5, 10.5), "core_dark"),
        box((7, 13, 7), (8.5, 15, 8.5), "teal"), box((9, 12.5, 7.5), (10.5, 14.5, 9), "core_dark"),
        box((4.5, 9.5, 5.8), (5.2, 12, 6), "glow", faces=["north"]), box((10.6, 8.5, 5.8), (11.3, 11.5, 6), "glow", faces=["north"]),
        box((7.6, 3.5, 6.3), (8.4, 5.5, 6.5), "glow", faces=["north"]),
    ]


def wither_core():
    """Black skull core with pale eyes and teeth, three skull knobs on top (raw boss drop)."""
    return [box((4, 4, 4), (12, 12, 12), "black"),
            box((5, 8, 3.8), (7, 10, 4), "bone", faces=["north"]), box((9, 8, 3.8), (11, 10, 4), "bone", faces=["north"]),
            box((6, 5, 3.8), (7, 6, 4), "bone", faces=["north"]), box((8, 5, 3.8), (9, 6, 4), "bone", faces=["north"]),
            box((10, 5, 3.8), (10.5, 6, 4), "bone", faces=["north"]),
            box((4.5, 12, 6), (6.5, 14, 8), "black"), box((7, 12, 6), (9, 15, 9), "black"), box((9.5, 12, 6), (11.5, 14, 8), "black")]


def dragon_scale():
    """Thick tilted dragon scale with a light middle ridge (raw boss drop)."""
    r = ("x", -22.5, (8, 8, 8))
    edge = {"north": "dviolet", "south": "dviolet", "up": "charoite", "down": "charoite", "east": "charoite", "west": "charoite"}
    return [box((4, 11, 6.5), (12, 14, 9.5), "dviolet", rot=r, mats=edge), box((3, 5, 6.5), (13, 11, 9.5), "dviolet", rot=r, mats=edge),
            box((5, 2, 6.5), (11, 5, 9.5), "dviolet", rot=r, mats=edge), box((7, 0.5, 6.5), (9, 2, 9.5), "dviolet", rot=r, mats=edge),
            box((7.5, 1, 6.1), (8.5, 13.5, 6.5), "charoite_light", rot=r, faces=["north", "east", "west", "up", "down"]),
            box((5, 8, 6.2), (6, 12, 6.5), "charoite_light", rot=r, faces=["north"]),
            box((10, 8, 6.2), (11, 12, 6.5), "charoite_light", rot=r, faces=["north"])]


def golem_trophy():
    """Soul crystal cluster on a siftslate pedestal with a siftite ring (refined)."""
    return [box((3, 0, 3), (13, 1.5, 13), "silver"), box((4, 1.5, 4), (12, 4, 12), "slate"),
            box((6.5, 4, 6.5), (9.5, 13, 9.5), "soul", rot=Y45), box((7.2, 13, 7.2), (8.8, 15, 8.8), "soul", rot=Y45),
            box((3.5, 4, 7), (5.5, 9.5, 9), "soul", rot=("z", 22.5, (4.5, 4, 8))),
            box((10.5, 4, 7), (12.5, 8.5, 9), "soul", rot=("z", -22.5, (11.5, 4, 8))),
            box((3.8, 3.6, 3.8), (12.2, 4.2, 12.2), "silver", faces=["up"])]


def charoite_core():
    """Charoite sphere with glowing windows in a siftite gimbal (refined)."""
    win = "glow"
    return [box((4, 5, 5), (12, 11, 11), "charoite"), box((5, 5, 4), (11, 11, 12), "charoite"), box((5, 4, 5), (11, 12, 11), "charoite_light"),
            box((7, 7, 3.8), (9, 9, 4), win, faces=["north"]), box((7, 7, 12), (9, 9, 12.2), win, faces=["south"]),
            box((3.8, 7, 7), (4, 9, 9), win, faces=["west"]), box((12, 7, 7), (12.2, 9, 9), win, faces=["east"]),
            box((7, 12, 7), (9, 12.2, 9), win, faces=["up"]),
            box((2.5, 7.5, 2.5), (13.5, 8.5, 3), "silver"), box((2.5, 7.5, 13), (13.5, 8.5, 13.5), "silver"),
            box((2.5, 7.5, 2.5), (3, 8.5, 13.5), "silver"), box((13, 7.5, 2.5), (13.5, 8.5, 13.5), "silver"),
            box((7.5, 2.5, 2.4), (8.5, 13.5, 2.9), "silver"), box((7.5, 2.5, 13.1), (8.5, 13.5, 13.6), "silver"),
            box((7.5, 2.5, 2.4), (8.5, 3, 13.6), "silver"), box((7.5, 13, 2.4), (8.5, 13.5, 13.6), "silver")]


def octa(f, t, dark, light):
    """Octagonal prism: a square and a 45° square of the same size, so the faces alternate dark and light."""
    return [box(f, t, light), box(f, t, dark, rot=Y45)]


def enchanter_crystal():
    """Faceted charoite crystal with stepped points and three small side crystals (floats above the enchanter)."""
    els = []
    els += octa((5.8, 3.5, 5.8), (10.2, 12.5, 10.2), "charoite", "charoite_light")
    els += octa((6.5, 12.5, 6.5), (9.5, 14, 9.5), "charoite", "charoite_light")
    els += octa((7.2, 14, 7.2), (8.8, 15.2, 8.8), "charoite", "charoite_light")
    els += [box((7.7, 15.2, 7.7), (8.3, 16, 8.3), "charoite_light")]
    els += octa((6.5, 2, 6.5), (9.5, 3.5, 9.5), "charoite", "charoite_light")
    els += octa((7.2, 0.8, 7.2), (8.8, 2, 8.8), "charoite", "charoite_light")
    els += [box((7.7, 0, 7.7), (8.3, 0.8, 8.3), "charoite_light"),
            box((4.3, 3, 7.2), (5.9, 8.5, 8.8), "charoite_light", rot=("z", 22.5, (5.9, 3, 8))),
            box((10.1, 4, 7.2), (11.7, 9, 8.8), "charoite", rot=("z", -22.5, (10.1, 4, 8))),
            box((7.2, 3.5, 4.3), (8.8, 8, 5.9), "charoite_light", rot=("x", -22.5, (8, 3.5, 5.9)))]
    return els


ITEMS = {"warden_heart": warden_heart, "wither_core": wither_core, "dragon_scale": dragon_scale,
         "golem_trophy": golem_trophy, "charoite_core": charoite_core}

# like a block, scaled for objects that fill most of the 16-unit box
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 1, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [0.8, 0.8, 0.8]},
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def model(elements, particle, display=True):
    used = sorted({face["texture"][1:] for el in elements for face in el["faces"].values()})
    textures = {m: f"the_sift:item/m3d/{m}" for m in used}
    textures["particle"] = particle
    data = {"textures": textures, "elements": elements}
    if display:
        data["display"] = DISPLAY
    return data


def main():
    out = ASSETS / "textures/item/m3d"
    out.mkdir(parents=True, exist_ok=True)
    for name in PALETTES:
        swatch(name).save(out / f"{name}.png")
    for item, build in ITEMS.items():
        write_json(ASSETS / f"models/item/{item}.json", model(build(), f"the_sift:item/{item}"))
    write_json(ASSETS / "models/item/enchanter_crystal.json", model(enchanter_crystal(), "the_sift:item/charoite_core", display=False))
    for item in ITEMS:
        definition = {"type": "minecraft:model", "model": f"the_sift:item/{item}"}
        if item == "charoite_core":
            # item displays (the enchanter's floating crystal) render in the context "none"
            definition = {"type": "minecraft:select", "property": "minecraft:display_context",
                          "cases": [{"when": "none", "model": {"type": "minecraft:model", "model": "the_sift:item/enchanter_crystal"}}],
                          "fallback": definition}
        write_json(ASSETS / f"items/{item}.json", {"model": definition})
    print("wrote", len(ITEMS), "3D item models, the enchanter crystal and", len(PALETTES), "material textures to", ASSETS)


if __name__ == "__main__":
    main()
