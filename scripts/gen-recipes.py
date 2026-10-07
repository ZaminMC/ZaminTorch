#!/usr/bin/env python3
"""Generates BuiltinRecipes.java for ZaminTorch from PrismarineJS/minecraft-data.

Data source: data/pc/1.8/recipes.json (+ items.json for legacy id -> name),
MIT license, https://github.com/PrismarineJS/minecraft-data (snapshot 2026-10).

Hard rule (user directive): community data first; embed a generated subset with
attribution instead of inventing values. The subset only contains recipes whose
ingredients AND results are registered engine items and whose pattern fits the
3x3 crafting table grid (the 2x2 player grid matches the same list: the
bounding-box matcher means a 3x3 pattern can never match a 2x2 grid).

Run: python3 gen-recipes.py
Reads: /home/z/my-project/tools/mc-1.8-{recipes,items}.json
Writes: /home/z/my-project/ZaminTorch/zamin-core/src/main/java/net/zamin/engine/crafting/BuiltinRecipes.java
"""

import json
import sys

TOOLS = "/home/z/my-project/tools"
OUT = ("/home/z/my-project/ZaminTorch/zamin-core/src/main/java/"
       "net/zamin/engine/crafting/BuiltinRecipes.java")

# Items the engine registers today (BuiltinItems). The table grows with the
# registry, never speculatively (same principle as BlockBehaviorTable).
REGISTERED = {
    "minecraft:dirt", "minecraft:cobblestone", "minecraft:oak_planks",
    "minecraft:oak_log", "minecraft:coal", "minecraft:diamond",
    "minecraft:iron_ingot", "minecraft:stone",
    "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:diamond_ore",
    "minecraft:stick", "minecraft:crafting_table", "minecraft:torch",
    "minecraft:furnace", "minecraft:chest",
    "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe",
    "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe",
    "minecraft:golden_pickaxe",
    "minecraft:wooden_axe", "minecraft:stone_axe", "minecraft:iron_axe",
    "minecraft:diamond_axe", "minecraft:golden_axe",
    "minecraft:wooden_shovel", "minecraft:stone_shovel", "minecraft:iron_shovel",
    "minecraft:diamond_shovel", "minecraft:golden_shovel",
    "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword",
    "minecraft:diamond_sword", "minecraft:golden_sword",
    "minecraft:shears",
}

# Dataset name -> canonical engine identifier (mirrors BlockBehaviorTable).
NAME_TRANSLATION = {
    "planks": "oak_planks",
    "log": "oak_log",
}


def canonical(item_id, items):
    if item_id not in items:
        raise KeyError(item_id)  # caller skips unresolvable legacy ids
    name = items[item_id]["name"]
    name = NAME_TRANSLATION.get(name, name)
    return "minecraft:" + name


def metadata_of_result(result):
    return result.get("metadata", 0)


def is_tool_repair(cells, result, items):
    # Shapeless where every ingredient is the same item as the result.
    if not cells:
        return False
    idents = set()
    for c in cells:
        raw = c if isinstance(c, int) else c["id"]
        idents.add(try_canonical(raw, items))
    return len(idents) == 1 and next(iter(idents)) == try_canonical(result["id"], items)


def try_canonical(item_id, items):
    try:
        return canonical(item_id, items)
    except KeyError:
        return None


def java_string(s):
    return '"' + s + '"'


def main():
    recipes = json.load(open(f"{TOOLS}/mc-1.8-recipes.json"))
    items_raw = json.load(open(f"{TOOLS}/mc-1.8-items.json"))
    items = {i["id"]: i for i in items_raw}

    shaped_out = []
    shapeless_out = []

    def cell_ident(cell):
        # cell: bare int (any metadata) | {"id":..,"metadata":..} (exact) | None
        if cell is None:
            return None
        raw = cell if isinstance(cell, int) else cell["id"]
        return try_canonical(raw, items)

    def ingredient_java(cell):
        if cell is None:
            return "null"
        if isinstance(cell, int):
            return f"ing({java_string(canonical(cell, items))}, false, 0)"
        return f"ing({java_string(canonical(cell['id'], items))}, true, {cell['metadata']})"

    def registered_cell(cell):
        if cell is None:
            return True  # an empty pattern cell (must stay empty) is legal
        ident = cell_ident(cell)
        return ident is not None and ident in REGISTERED

    for result_id, variants in sorted(recipes.items(), key=lambda kv: int(kv[0])):
        for recipe in variants:
            result = recipe["result"]
            result_ident = try_canonical(result["id"], items)
            if result_ident is None or result_ident not in REGISTERED:
                continue
            if metadata_of_result(result) != 0:
                # Variant results (e.g. spruce planks) need the registry to
                # carry those variants; the oak-canonical engine skips them.
                continue
            if "inShape" in recipe:
                shape = recipe["inShape"]
                if not all(registered_cell(c) for row in shape for c in row):
                    continue
                # trim empty rows / columns so the pattern is canonical
                rows = [list(r) for r in shape]
                while rows and all(c is None for c in rows[0]):
                    rows.pop(0)
                while rows and all(c is None for c in rows[-1]):
                    rows.pop()
                if not rows:
                    continue
                while all(r[0] is None for r in rows):
                    for r in rows:
                        r.pop(0)
                while all(r[-1] is None for r in rows):
                    for r in rows:
                        r.pop()
                if len(rows) > 3 or max(len(r) for r in rows) > 3:
                    continue  # does not fit the 3x3 crafting grid
                # rectangularize (padded rows with nulls)
                width = max(len(r) for r in rows)
                for r in rows:
                    r.extend([None] * (width - len(r)))
                count = result.get("count", 1)
                metadata = result.get("metadata", 0)
                rows_java = ",\n                    ".join(
                    "{ " + ", ".join(ingredient_java(c) for c in row) + " }"
                    for row in rows)
                shaped_out.append(
                    f"            CraftingRecipe.shaped(new Ingredient[][] {{\n                    {rows_java}\n            }},\n"
                    f"                    res({java_string(result_ident)}, {count}, {metadata}))")
            elif "ingredients" in recipe:
                cells = recipe["ingredients"]
                if len(cells) > 9:
                    continue  # cannot fit the 3x3 crafting grid
                if not all(registered_cell(c) for c in cells):
                    continue
                if is_tool_repair(cells, result, items):
                    # Two tools -> one tool: vanilla computes the merged
                    # durability from the inputs. A fixed result damage cannot
                    # express that; repair arrives with a dedicated slice.
                    print("skip tool-repair recipe (dynamic durability): "
                          + result_ident, file=sys.stderr)
                    continue
                count = result.get("count", 1)
                ingredients_java = ",\n                    ".join(
                    ingredient_java(c) for c in cells)
                shapeless_out.append(
                    f"            CraftingRecipe.shapeless(java.util.List.of(\n                    {ingredients_java}),\n"
                    f"                    res({java_string(result_ident)}, {count}, {metadata}))")
            else:
                print(f"skip unknown recipe form for result {result_id}: {recipe}",
                      file=sys.stderr)

    body = f"""package net.zamin.engine.crafting;

import java.util.List;

/**
 * Crafting recipes the engine currently serves, generated from community data.
 *
 * <p>Data source (per the reuse rule): PrismarineJS/minecraft-data,
 * {{@code data/pc/1.8/recipes.json}} (MIT license,
 * https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10), filtered
 * to patterns that fit the 2x2 player crafting grid and to ingredients and
 * results the engine has registered. The set grows with the registry, never
 * speculatively; regenerate with {{@code scripts/gen-recipes.py}}. Dataset
 * names differing from canonical names are translated ({{@code planks}} ->
 * {{@code minecraft:oak_planks}}, {{@code log}} -> {{@code minecraft:oak_log}});
 * the 1.8 adapter separately translates legacy numeric ids at the wire.</p>
 *
 * <p>Semantics mirror vanilla 1.8 shaped/shapeless matching: a bare ingredient
 * accepts any metadata, an exact (id, metadata) ingredient demands the exact
 * damage-field value; shaped patterns match at the grid's bounding box in
 * normal or mirrored orientation. One list serves both grids: the bounding-box
 * matcher keeps 3x3 patterns from matching a 2x2 grid, and small patterns
 * match anywhere in the 3x3 crafting-table grid — the historical semantics.</p>
 */
public final class BuiltinRecipes {{

    private BuiltinRecipes() {{
    }}

    private static Ingredient ing(String item, boolean exactMetadata, int metadata) {{
        return new Ingredient(net.zamin.api.Identifier.parse(item), exactMetadata, metadata);
    }}

    private static CraftingRecipe.Result res(String item, int count, int damage) {{
        return new CraftingRecipe.Result(net.zamin.api.Identifier.parse(item), count, damage);
    }}

    /** All generated recipes, in dataset order (deterministic matching). */
    public static final List<CraftingRecipe> ALL = List.of(
{",\n".join(shaped_out + shapeless_out)}
    );
}}
"""
    with open(OUT, "w") as f:
        f.write(body)
    print(f"wrote {OUT}")
    print(f"shaped: {len(shaped_out)}, shapeless: {len(shapeless_out)}")


if __name__ == "__main__":
    main()
