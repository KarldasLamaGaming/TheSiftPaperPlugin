#!/usr/bin/env python3
"""Generates willow-plank variants of the vanilla recipes that take planks (issue #14).

In the original mod willow planks are in #minecraft:planks (and willow logs in #minecraft:logs), so every
vanilla wood recipe accepts them. Here willow planks are a custom block item, so vanilla tags cannot reach
them. For each vanilla recipe that uses #minecraft:planks, #minecraft:wooden_tool_materials or
#minecraft:logs this script writes a copy in which that slot takes the willow item instead (willow only:
mixing willow and vanilla planks in one craft is not possible). Run once per Minecraft version and commit the output:

    python3 scripts/gen_willow_recipes.py <server-26.3.jar>

Output: plugin/src/main/resources/extra-recipes/willow_<recipe>.json (old willow_* files are replaced).
"""
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "plugin/src/main/resources/extra-recipes"
# recipes of the original that use #minecraft:planks (e.g. piston_from_cobbled_siftslate)
SIFT_RECIPES = ROOT / "pack-src/item-data/recipe"
# willow only, so a craft with vanilla wood still matches only the vanilla recipe (no ambiguity)
WIDEN = {
    "#minecraft:planks": "the_sift:overgrown_willow_planks",
    "#minecraft:wooden_tool_materials": "the_sift:overgrown_willow_planks",
    "#minecraft:logs": "#the_sift:overgrown_willow_logs",
}
# covered by the hand-written extra recipes that already exist (sticks, crafting table)
SKIP = {"stick", "crafting_table"}


def widen(value):
    if isinstance(value, str):
        return WIDEN.get(value, value), value in WIDEN
    if isinstance(value, list):
        out, changed = [], False
        for v in value:
            new, c = widen(v)
            changed |= c
            out.extend(new if isinstance(new, list) else [new])
        return out, changed
    return value, False


def convert(recipe_id, recipe, written):
    if recipe_id in SKIP or recipe.get("type") not in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
        return
    changed = False
    if "key" in recipe:
        for symbol, value in recipe["key"].items():
            recipe["key"][symbol], c = widen(value)
            changed |= c
    if "ingredients" in recipe:
        new = []
        for value in recipe["ingredients"]:
            v, c = widen(value)
            changed |= c
            new.append(v)
        recipe["ingredients"] = new
    if changed:
        (OUT / f"willow_{recipe_id}.json").write_text(json.dumps(recipe, indent=2) + "\n")
        written.append(recipe_id)


def main(jar_path):
    for old in OUT.glob("willow_*.json"):
        old.unlink()
    written = []
    with zipfile.ZipFile(jar_path) as jar:
        for name in sorted(jar.namelist()):
            if name.startswith("data/minecraft/recipe/") and name.endswith(".json"):
                convert(name.rsplit("/", 1)[1][:-5], json.loads(jar.read(name)), written)
    for path in sorted(SIFT_RECIPES.glob("*.json")):
        convert(path.stem, json.loads(path.read_text()), written)
    print(f"{len(written)} recipes: {' '.join(written)}")


if __name__ == "__main__":
    main(sys.argv[1])
