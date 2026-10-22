#!/usr/bin/env python3
"""Map the engine's block identifiers to the reference's 1.8.8 blast
resistance (the br_read values extracted by extract-blast-resistance.py)
and emit the Java side table + an audit report.

The translation: exact names first, then the state-suffix rules (the
engine's flattened state identifiers), then the hand map for the 1.8.8
pre-flattening names (the same translation family BlockBehaviorTable's
build applies for the community dataset).

Usage:
  python3 scripts/map-blast-resistance.py            # audit report
  python3 scripts/map-blast-resistance.py --java      # Java table source
"""
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BEHAVIOR = REPO / "zamin-core" / "src" / "main" / "java" / "net" / "zaminmc" / "torch" / "server" / "block" / "BlockBehaviorTable.java"

# The engine identifier -> 1.8.8 reference name hand map (the pre-flattening
# renames + the state families folded onto their 1.8.8 single-id blocks).
HAND = {
    "grass_block": "grass",
    "oak_planks": "planks",
    "oak_log": "log",
    "oak_leaves": "leaves",
    "oak_fence": "fence",
    "oak_slab": "wooden_slab",
    "oak_stairs_east": "oak_stairs", "oak_stairs_east_top": "oak_stairs",
    "oak_stairs_north": "oak_stairs", "oak_stairs_north_top": "oak_stairs",
    "oak_stairs_south": "oak_stairs", "oak_stairs_south_top": "oak_stairs",
    "oak_stairs_west": "oak_stairs", "oak_stairs_west_top": "oak_stairs",
    "oak_door": "wooden_door", "oak_door_east": "wooden_door",
    "oak_door_north": "wooden_door", "oak_door_south": "wooden_door",
    "oak_door_west": "wooden_door", "oak_door_upper": "wooden_door",
    "oak_door_open": "wooden_door", "oak_door_open_east": "wooden_door",
    "oak_door_open_north": "wooden_door", "oak_door_open_south": "wooden_door",
    "oak_door_upper_open": "wooden_door",
    "tall_grass": "tallgrass", "fern": "double_plant",
    "dandelion": "yellow_flower", "poppy": "red_flower",
    "dead_bush": "deadbush", "sugar_cane": "reeds",
    "nether_bricks": "nether_brick",
    "stone_bricks": "stonebrick", "mossy_stone_bricks": "stonebrick",
    "cracked_stone_bricks": "stonebrick", "chiseled_stone_bricks": "stonebrick",
    "stone_brick_stairs_east": "stone_brick_stairs", "stone_brick_stairs_east_top": "stone_brick_stairs",
    "stone_brick_stairs_north": "stone_brick_stairs", "stone_brick_stairs_north_top": "stone_brick_stairs",
    "stone_brick_stairs_south": "stone_brick_stairs", "stone_brick_stairs_south_top": "stone_brick_stairs",
    "stone_brick_stairs_west": "stone_brick_stairs", "stone_brick_stairs_west_top": "stone_brick_stairs",
    "stone_brick_slab": "stone_slab", "stone_brick_slab_top": "stone_slab",
    "chiseled_sandstone": "sandstone", "smooth_sandstone": "sandstone",
    "sandstone_stairs_east": "sandstone_stairs", "sandstone_stairs_east_top": "sandstone_stairs",
    "sandstone_stairs_north": "sandstone_stairs", "sandstone_stairs_north_top": "sandstone_stairs",
    "sandstone_stairs_south": "sandstone_stairs", "sandstone_stairs_south_top": "sandstone_stairs",
    "sandstone_stairs_west": "sandstone_stairs", "sandstone_stairs_west_top": "sandstone_stairs",
    "sandstone_slab": "stone_slab", "sandstone_slab_top": "stone_slab",
    "double_sandstone_slab": "double_stone_slab",
    "cobblestone_slab": "stone_slab", "cobblestone_slab_top": "stone_slab",
    "double_cobblestone_slab": "double_stone_slab",
    "double_stone_brick_slab": "double_stone_slab",
    "double_stone_slab_seamless": "double_stone_slab",
    "cobblestone_stairs_east": "stone_stairs", "cobblestone_stairs_east_top": "stone_stairs",
    "cobblestone_stairs_north": "stone_stairs", "cobblestone_stairs_north_top": "stone_stairs",
    "cobblestone_stairs_south": "stone_stairs", "cobblestone_stairs_south_top": "stone_stairs",
    "cobblestone_stairs_west": "stone_stairs", "cobblestone_stairs_west_top": "stone_stairs",
    "nether_brick_stairs_east": "nether_brick_stairs", "nether_brick_stairs_east_top": "nether_brick_stairs",
    "nether_brick_stairs_north": "nether_brick_stairs", "nether_brick_stairs_north_top": "nether_brick_stairs",
    "nether_brick_stairs_south": "nether_brick_stairs", "nether_brick_stairs_south_top": "nether_brick_stairs",
    "nether_brick_stairs_west": "nether_brick_stairs", "nether_brick_stairs_west_top": "nether_brick_stairs",
    "bed_east": "bed", "bed_north": "bed", "bed_west": "bed", "bed_south": "bed",
    "bed_head": "bed", "bed_head_east": "bed", "bed_head_north": "bed", "bed_head_west": "bed", "bed_head_south": "bed",
    "oak_slab_top": "wooden_slab",
    "chest_east": "chest", "chest_north": "chest", "chest_west": "chest", "chest_south": "chest",
    "ender_chest_east": "ender_chest", "ender_chest_north": "ender_chest",
    "ender_chest_west": "ender_chest", "ender_chest_south": "ender_chest",
    "trapped_chest_east": "trapped_chest", "trapped_chest_north": "trapped_chest",
    "trapped_chest_west": "trapped_chest", "trapped_chest_south": "trapped_chest",
    "furnace_east": "furnace", "furnace_north": "furnace", "furnace_west": "furnace", "furnace_south": "furnace",
    "furnace_lit": "lit_furnace", "furnace_lit_east": "lit_furnace", "furnace_lit_north": "lit_furnace",
    "furnace_lit_west": "lit_furnace", "furnace_lit_south": "lit_furnace",
    "sign": "standing_sign", "sign_east": "standing_sign", "sign_north": "standing_sign", "sign_west": "standing_sign", "sign_south": "standing_sign",
    "wall_sign_east": "wall_sign", "wall_sign_south": "wall_sign", "wall_sign_west": "wall_sign", "wall_sign_north": "wall_sign",
    "skull_east": "skull", "skull_north": "skull", "skull_south": "skull", "skull_west": "skull", "skull_down": "skull",
    "iron_door_east": "iron_door", "iron_door_north": "iron_door", "iron_door_south": "iron_door",
    "iron_door_west": "iron_door", "iron_door_upper": "iron_door",
    "rail_ew": "rail",
    "jukebox_record": "jukebox",
    "command_block_triggered": "command_block",
    "farmland_wet": "farmland",
    "wet_sponge": "sponge",
    "slime_block": "slime",
    "prismarine_bricks": "prismarine", "prismarine_dark": "prismarine", "prismarine_rough": "prismarine",
    "stained_clay_orange": "stained_hardened_clay", "stained_clay_yellow": "stained_hardened_clay",
    "infested_stone": "monster_egg", "infested_cobblestone": "monster_egg",
    "infested_stone_bricks": "monster_egg", "infested_mossy_stone_bricks": "monster_egg",
    "infested_cracked_stone_bricks": "monster_egg", "infested_chiseled_stone_bricks": "monster_egg",
    "stone_slab_top": "stone_slab",
    "ladder_east": "ladder", "ladder_south": "ladder", "ladder_west": "ladder", "ladder_north": "ladder",
    "end_portal_frame_east": "end_portal_frame", "end_portal_frame_north": "end_portal_frame",
    "end_portal_frame_south": "end_portal_frame", "end_portal_frame_west": "end_portal_frame",
    "end_portal_frame_eye_east": "end_portal_frame", "end_portal_frame_eye_north": "end_portal_frame",
    "end_portal_frame_eye_south": "end_portal_frame", "end_portal_frame_eye_west": "end_portal_frame",
}

# State suffixes that fold onto the family base when the base itself is
# registered (crops etc. register as themselves + stage states).
STAGE_RE = re.compile(r"_stage[0-9]+$")


def engine_identifiers():
    src = BEHAVIOR.read_text(encoding="utf-8")
    ids = re.findall(r'Map\.entry\(Identifier\.parse\("minecraft:([a-z0-9_]+)"\)', src)
    return sorted(set(ids))


def main():
    ref = {r["name"]: r for r in json.load(open(REPO.parent / "my-project" / "blast.json" if (REPO.parent / "my-project" / "blast.json").exists() else "/home/z/my-project/blast.json"))}
    eng = engine_identifiers()
    rows = []
    unmapped = []
    for ident in eng:
        name = None
        if ident in HAND:
            name = HAND[ident]
        elif ident in ref:
            name = ident
        else:
            base = STAGE_RE.sub("", ident)
            if base in ref:
                name = base
            elif base in HAND:
                name = HAND[base]
        if name and name in ref:
            rows.append((ident, name, ref[name]["br_read"]))
        else:
            unmapped.append((ident, name))
    if "--java" in sys.argv:
        print("    /** The per-block blast-resistance READ values (1:1 the reference's")
        print("     * Block.getBlastResistance family — see BlastResistanceTable's own")
        print("     * javadoc for the model). */")
        print("    private static final Map<Identifier, Float> BLAST_RESISTANCE = Map.ofEntries(")
        for ident, name, br in rows:
            if br == int(br):
                val = f"{int(br)}f"
            else:
                val = f"{br}f"
            print(f'        Map.entry(Identifier.parse("minecraft:{ident}"), {val}),')
        print("    );")
    else:
        print(f"engine identifiers: {len(eng)}  mapped: {len(rows)}  unmapped: {len(unmapped)}")
        for ident, name in unmapped:
            print(f"  UNMAPPED {ident} (tried {name})")
        # spot report
        for ident, name, br in rows:
            if ident in ("stone", "bedrock", "obsidian", "water", "grass_block", "oak_planks", "furnace_lit", "fire", "tnt"):
                print(f"  {ident:24} <- {name:22} {br}")


if __name__ == "__main__":
    main()
