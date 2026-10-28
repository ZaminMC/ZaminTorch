package net.zaminmc.torch.server.block;

import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.item.ToolClass;
import net.zaminmc.torch.server.item.ToolMaterial;
import net.zaminmc.torch.server.item.ToolSpec;
import net.zaminmc.torch.server.item.Tools;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Survival behavior data for registered blocks, keyed by canonical identifier.
 *
 * <p>Data source (community, per the reuse rule): values were generated from
 * PrismarineJS/minecraft-data, {@code data/pc/1.8/blocks.json} + {@code items.json}
 * + {@code materials.json} (MIT license, https://github.com/PrismarineJS/minecraft-data,
 * snapshot 2026-10), cross-checked against historical 1.8.8 behavior. No Java
 * binding of that dataset exists; embedding a generated subset with attribution
 * is the established pattern for JVM engines (the same approach Glowstone/Nukkit
 * take with their tables). The table only contains blocks the engine registers;
 * it grows with the registry, never speculatively.</p>
 *
 * <p>Drops reference canonical item identifiers. Dataset names differing from
 * modern canonical names are translated here (dataset {@code planks} →
 * {@code minecraft:oak_planks}, dataset {@code log} → {@code minecraft:oak_log});
 * the 1.8 adapter separately translates legacy numeric ids at the wire only.</p>
 */
public final class BlockBehaviorTable {

    private BlockBehaviorTable() {
    }

    /**
     * The silk-touch gate (the reference's {@code hasSilkTouchDrops}:
     * {@code isCube() && !hasBlockEntity}, reference/1.8.8 block/Block
     * lines 784-787) encoded as the registered block set where the silk
     * arm diverges from the normal drops. Blocks whose normal drop IS
     * themselves (planks, logs, chests, furnaces, torches) are observably
     * identical under either arm and stay outside the set; crops and the
     * other non-cubes fall through to the normal path (the reference gate
     * excludes them); TE blocks (chest, furnace, the enchanting table)
     * take their own drop walk in vanilla and join them.
     */
    private static final Set<Identifier> SILK_TOUCHABLE = Set.of(
            Identifier.parse("minecraft:stone"),
            Identifier.parse("minecraft:grass_block"),
            Identifier.parse("minecraft:gravel"),
            Identifier.parse("minecraft:glass"),
            Identifier.parse("minecraft:oak_leaves"),
            Identifier.parse("minecraft:coal_ore"),
            Identifier.parse("minecraft:diamond_ore"),
            Identifier.parse("minecraft:farmland"),
            Identifier.parse("minecraft:farmland_wet"),
            Identifier.parse("minecraft:bookshelf"),
            // The mineral vault's silk arms (Slice 32 — the OreBlock silk
            // gate is the default hasSilkTouchDrops, so lapis/emerald ore
            // take themselves; PackedIceBlock rides the same default with
            // its bare-hand count 0; plain IceBlock carries the same gate
            // — its bare hand melts it to nothing, silk takes the cube).
            Identifier.parse("minecraft:lapis_ore"),
            Identifier.parse("minecraft:emerald_ore"),
            Identifier.parse("minecraft:redstone_ore"),
            Identifier.parse("minecraft:packed_ice"),
            Identifier.parse("minecraft:ice"),
            // The silk-arm parity audit (the follow-up to the drop-band
            // audit — every cube-without-TE whose normal row DIVERGES from
            // the block itself, each read from the reference before the
            // fix): the quartz ore (hand → the quartz item, silk → the
            // ore itself), the glowstone (hand → the dust band, silk →
            // the block), the clay (hand → four balls, silk → the block),
            // the snow block (hand → four snowballs, silk → the block),
            // the mycelium (hand → dirt, silk → the block), the glass
            // pane (hand → nothing, silk → the pane item 102), and the
            // cobweb (hand → one string, silk → the web item 30).
            Identifier.parse("minecraft:quartz_ore"),
            Identifier.parse("minecraft:glowstone"),
            Identifier.parse("minecraft:clay"),
            Identifier.parse("minecraft:snow"),
            Identifier.parse("minecraft:mycelium"),
            Identifier.parse("minecraft:glass_pane"),
            Identifier.parse("minecraft:cobweb"),
            // The sea lantern's own gate (SeaLanternBlock line 41:
            // hasSilkTouchDrops true — silk takes the lantern itself,
            // the bare hand walks the crystals band).
            Identifier.parse("minecraft:sea_lantern"),
            // The huge-mushroom pair (Slice 40 — the full-cube default's
            // own silk arm: every state takes itself under silk touch).
            Identifier.parse("minecraft:brown_mushroom_block"),
            Identifier.parse("minecraft:red_mushroom_block"));

    /** @return whether the block's silk touch drops its own item (the reference gate). */
    public static boolean isSilkTouchable(Identifier block) {
        return SILK_TOUCHABLE.contains(block);
    }

    private static final Map<Identifier, BlockBehavior> BEHAVIORS = buildBehaviors();

    private static Map<Identifier, BlockBehavior> buildBehaviors() {
        Map<Identifier, BlockBehavior> behaviors = new java.util.HashMap<>(Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:air"),
                    new BlockBehavior(0.0, false, false, null, 0, List.of())),

            Map.entry(Identifier.parse("minecraft:stone"),
                    new BlockBehavior(1.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone"), 1)))),

            Map.entry(Identifier.parse("minecraft:grass_block"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:dirt"),
                    new BlockBehavior(0.5, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            // The silk-arm parity audit — the dirt band's missing variants
            // (DirtBlock.getDropItemMetadata lines 72-79: the dropped
            // item's damage is the variant's own id, with PODZOL
            // downgraded to plain DIRT for the by-hand walk; the silk arm
            // keeps the state's metadata instead — the podzol takes
            // damage 2 under silk, a dedicated branch in the DropService
            // owns that arm since the generic silk drop cannot carry the
            // dirt item's damage domain). The coarse dirt answers the
            // SAME stack under either arm (damage 1 — 1.8's coarse dirt
            // IS the dirt item), so it rides the plain row alone.
            Map.entry(Identifier.parse("minecraft:coarse_dirt"),
                    new BlockBehavior(0.5, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1, 1)))),
            Map.entry(Identifier.parse("minecraft:podzol"),
                    new BlockBehavior(0.5, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:cobblestone"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone"), 1)))),

            Map.entry(Identifier.parse("minecraft:oak_planks"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_planks"), 1)))),

            // Community data (pc/1.8 blocks.json): wool hardness 0.8, material
            // wool (shears accelerate), no tool gate, drops itself.
            Map.entry(Identifier.parse("minecraft:wool"),
                    new BlockBehavior(0.8, true, false, "wool", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wool"), 1)))),

            Map.entry(Identifier.parse("minecraft:oak_log"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_log"), 1)))),

            // Community data (pc/1.8 blocks.json): crafting_table hardness 2.5,
            // material wood, no harvest requirement, drops itself; torch is
            // instant-break (hardness 0) and drops itself.
            Map.entry(Identifier.parse("minecraft:crafting_table"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:crafting_table"), 1)))),

            // The bookshelf (BookshelfBlock — setStrength(1.5F), the wood
            // material, hand-breakable): getDropItem the BOOK with
            // getBaseDropCount 3 — exactly three books by hand, the shelf
            // itself under silk (the silk set's own row). The pre-fix
            // table carried NO row at all, so a break answered nothing.
            Map.entry(Identifier.parse("minecraft:bookshelf"),
                    new BlockBehavior(1.5, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:book"), 3)))),

            Map.entry(Identifier.parse("minecraft:torch"),
                    new BlockBehavior(0.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:torch"), 1)))),

            // Community data (pc/1.8 blocks.json): furnace hardness 3.5, material
            // rock, pickaxe required (harvestTools: all pickaxe tiers), drops
            // itself; chest hardness 2.5, material wood, no tool requirement,
            // drops itself.
            Map.entry(Identifier.parse("minecraft:furnace"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            // The furnace pair's facing states (Slice L3 — the same 3.5
            // rock rows, every state dropping the furnace item).
            Map.entry(Identifier.parse("minecraft:furnace_north"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_south"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_west"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_east"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),

            // Community data (pc/1.8 blocks.json): noteblock hardness 0.8
            // (the reference's own setStrength(0.8F) registration), material
            // wood, no harvest requirement, drops itself.
            Map.entry(Identifier.parse("minecraft:noteblock"),
                    new BlockBehavior(0.8, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:noteblock"), 1)))),

            // The jukebox (Slice 27b — the reference's line-1181
            // registration: setStrength(2.0F), setBlastResistance(10.0F)):
            // stone material, no harvest requirement, drops itself; the
            // HAS_RECORD variant mirrors it (the record ejects through the
            // break hook, not the drop table — the reference's own
            // onRemoved walk).
            Map.entry(Identifier.parse("minecraft:jukebox"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:jukebox"), 1)))),
            Map.entry(Identifier.parse("minecraft:jukebox_record"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:jukebox"), 1)))),

            Map.entry(Identifier.parse("minecraft:chest"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),
            // The chest pair's facing states (Slice L2 — the same 2.5 WOOD
            // rows, every state dropping its family item).
            Map.entry(Identifier.parse("minecraft:chest_north"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:chest_south"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:chest_west"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:chest_east"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:chest"), 1)))),
            // The trapped chest (Slice 49b — ChestBlock(1)'s own row:
            // setStrength(2.5F) WOOD_SOUNDS dropping itself).
            Map.entry(Identifier.parse("minecraft:trapped_chest"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:trapped_chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:trapped_chest_north"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:trapped_chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:trapped_chest_south"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:trapped_chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:trapped_chest_west"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:trapped_chest"), 1)))),
            Map.entry(Identifier.parse("minecraft:trapped_chest_east"),
                    new BlockBehavior(2.5, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:trapped_chest"), 1)))),

            // The ender chest family (Slice 29a — Block.java lines 1281-1282:
            // setStrength(22.5F), setBlastResistance(1000.0F), STONE sounds):
            // obsidian-tier pickaxe harvest (the dataset's harvestTools:
            // diamond), the foreign-drop walk yields eight obsidian
            // (EnderChestBlock's own getDropItem + getBaseDropCount 8), and
            // silk takes the chest itself (hasSilkTouchDrops true). The
            // facing variants mirror the base row.
            Map.entry(Identifier.parse("minecraft:ender_chest_north"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),
            Map.entry(Identifier.parse("minecraft:ender_chest_south"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),
            Map.entry(Identifier.parse("minecraft:ender_chest_west"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),
            Map.entry(Identifier.parse("minecraft:ender_chest_east"),
                    new BlockBehavior(22.5, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 8)))),

            // Obsidian itself (Slice 29a — Block.java line 1103's
            // setStrength(50.0F).setBlastResistance(2000.0F)): the ender
            // chest's drop yields it as an item, so the mining row lands with
            // the family (the dataset's harvestTools: diamond tier, drops
            // itself).
            Map.entry(Identifier.parse("minecraft:obsidian"),
                    new BlockBehavior(50.0, true, true, "rock", 4,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:obsidian"), 1)))),

            // The beacon (Slice 29b — BeaconBlock's own setStrength(3.0F),
            // the GLASS material): no tool gate, drops itself.
            Map.entry(Identifier.parse("minecraft:beacon"),
                    new BlockBehavior(3.0, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:beacon"), 1)))),

            // The beacon pyramid's minerals (Slice 29b — the dataset's own
            // rows: diamond block hardness 5, emerald block 5, both
            // pickaxe-gated iron tier (3), dropping themselves).
            Map.entry(Identifier.parse("minecraft:diamond_block"),
                    new BlockBehavior(5.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:diamond_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:emerald_block"),
                    new BlockBehavior(5.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:emerald_block"), 1)))),

            // The mineral vault family (Slice 32 — the reference's own
            // rows). Harvest gates are PickaxeItem.canMineBlock's: lapis
            // ore/block tier >= 1 (the engine's band 2 — stone pickaxe),
            // emerald ore >= 2 (iron, band 3); the plain stone-material
            // blocks take any pickaxe (band 1). Clay is the shovel family
            // (dirt band, ClayBlock's 4 clay balls); hay and slime need no
            // tool at all.
            Map.entry(Identifier.parse("minecraft:lapis_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 2,
                            // The count is the DropService's family arm:
                            // OreBlock.getBaseDropCount's 4 + nextInt(5)
                            // (the fixed 4 here only rides non-family
                            // callers that read the table raw).
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lapis"), 4)))),
            Map.entry(Identifier.parse("minecraft:emerald_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:emerald"), 1)))),
            Map.entry(Identifier.parse("minecraft:lapis_block"),
                    new BlockBehavior(3.0, true, true, "rock", 2,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lapis_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:redstone_block"),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:coal_block"),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:coal_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:quartz_block"),
                    new BlockBehavior(0.8, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:quartz_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:brick_block"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:brick_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:clay"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:clay_ball"), 4)))),
            Map.entry(Identifier.parse("minecraft:hay_block"),
                    new BlockBehavior(0.5, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:hay_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:slime_block"),
                    new BlockBehavior(0.0, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:slime_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:end_stone"),
                    new BlockBehavior(3.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:end_stone"), 1)))),
            // PackedIceBlock.getBaseDropCount 0: the bare pickaxe gets
            // nothing; silk takes the block itself (the silk set below).
            Map.entry(Identifier.parse("minecraft:packed_ice"),
                    new BlockBehavior(0.5, true, true, "rock", 1, List.of())),
            // The plain ice (IceBlock — setStrength(0.5F), the ICE
            // material, hand-breakable): the bare hand melts it to
            // nothing (getDropItem null / getBaseDropCount 0 — the row's
            // empty drop list) and silk takes the cube itself (the silk
            // set's own row above). The pre-fix table carried NO row at
            // all, so even silk answered nothing.
            Map.entry(Identifier.parse("minecraft:ice"),
                    new BlockBehavior(0.5, true, false, null, 0, List.of())),

            // The redstone power family (Slice 33): the lamp pair — one
            // item (the dark form — RedstoneLampBlock.getDropItem lines
            // 62-65 returns the DARK lamp for BOTH states), the glass
            // sounds' 0.3 hardness, no tool gate. The lit ore shares its
            // plain twin's row exactly (same dust drops, same gates — the
            // drop arm reads the family walk either way).
            Map.entry(Identifier.parse("minecraft:redstone_lamp"),
                    new BlockBehavior(0.3, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_lamp"), 1)))),
            Map.entry(Identifier.parse("minecraft:lit_redstone_lamp"),
                    new BlockBehavior(0.3, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_lamp"), 1)))),
            Map.entry(Identifier.parse("minecraft:lit_redstone_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone"), 4)))),

            // Community data (pc/1.8 blocks.json): sand hardness 0.5, material
            // dirt, no tool requirement, drops itself (metadata 0: normal sand;
            // red sand is variant 1, /give reachable). Glass hardness 0.3, no
            // material, drops nothing — the historical shatter (only silk touch
            // returned glass, which is a later slice).
            Map.entry(Identifier.parse("minecraft:sand"),
                    new BlockBehavior(0.5, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sand"), 1)))),

            Map.entry(Identifier.parse("minecraft:glass"),
                    new BlockBehavior(0.3, true, false, null, 0, List.of())),

            // The TNT block (Slice 9f, community blocks.json: hardness 0,
            // material tnt): drops the item when broken by hand.
            Map.entry(Identifier.parse("minecraft:tnt"),
                    new BlockBehavior(0.0, true, false, "tnt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:tnt"), 1)))),

            // Community data (pc/1.8 blocks.json): gravel 0.6, material dirt,
            // no tool requirement. Drops model the historical quantityDropped
            // override: 10% flint, otherwise gravel (first-hit-wins rolls).
            Map.entry(Identifier.parse("minecraft:gravel"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:flint"), 1, 0.1),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:gravel"), 1, 1.0)))),

            Map.entry(Identifier.parse("minecraft:coal_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:coal"), 1)))),

            Map.entry(Identifier.parse("minecraft:iron_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 2,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_ore"), 1)))),

            Map.entry(Identifier.parse("minecraft:diamond_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:diamond"), 1)))),

            // The full-world ladder (community blocks.json): gold and redstone
            // share iron's pickaxe gate; the oak trunk is wood (axe, drops
            // itself); the canopy shatters like glass (silk touches are later).
            Map.entry(Identifier.parse("minecraft:gold_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:gold_ore"), 1)))),
            Map.entry(Identifier.parse("minecraft:redstone_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 3,
                            // The 1:1 repair (Slice 32 — RedstoneOreBlock
                            // lines 66-77): the drop is the DUST, base
                            // 4 + nextInt(2), fortune ADDS nextInt(f+1)
                            // — the DropService's family arm owns the
                            // walk; the fixed 4 here is the raw-table
                            // fallback only.
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone"), 4)))),
            Map.entry(Identifier.parse("minecraft:oak_leaves"),
                    new BlockBehavior(0.2, true, false, "leaves", 1,
                            List.of())),

            // Decoration flora (community blocks.json): all instant-break
            // (hardness 0), no tool, plants material. 1.8 drop model: the
            // flowers drop themselves; tall grass and the dead bush drop
            // nothing by hand (shears drops are a later slice; seeds/farming
            // do not exist yet).
            // The farming slice (community blocks.json): farmland hardness
            // 0.6, dirt material, drops dirt (the historical BlockFarmland).
            // The wheat crop (block 59) is instant-break; the mature stage
            // yields the wheat plus a seed (the historical 0-3 roll folded to
            // one, the Drop model's fixed-count shape), younger stages drop
            // only the seed.
            Map.entry(Identifier.parse("minecraft:farmland"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:farmland_wet"),
                    new BlockBehavior(0.6, true, false, "dirt", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dirt"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage4"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage5"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage6"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            Map.entry(Identifier.parse("minecraft:wheat_stage7"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat"), 1),
                                    new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1)))),

            // The village slice's crop families (community blocks.json:
            // carrots 141 / potatoes 142, instant-break plants like the
            // wheat; the mature stage yields the crop item — the reference's
            // 0-4 quantity roll folded to the Drop model's fixed one).
            Map.entry(Identifier.parse("minecraft:carrots_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage4"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage5"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage6"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 1)))),
            Map.entry(Identifier.parse("minecraft:carrots_stage7"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:carrot"), 2)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage4"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage5"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage6"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 1)))),
            Map.entry(Identifier.parse("minecraft:potatoes_stage7"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:potato"), 2)))),

            // The village's thin solids and stone pieces (community
            // blocks.json): the glass pane 102 shatters like glass (0.3,
            // no drops), the iron bars 101 hardness 5 drop themselves,
            // the double stone slab 43 hardness 2, the smooth sandstone
            // skin and the sandstone stairs pair the sandstone's own 0.8.
            Map.entry(Identifier.parse("minecraft:glass_pane"),
                    new BlockBehavior(0.3, true, false, null, 0, List.of())),
            // The silk-arm parity audit — the cobweb's missing row
            // (CobwebBlock lines 41-45: getDropItem STRING with the base
            // count ONE — the pre-fix table carried no row at all, so
            // every break answered nothing; silk takes the web itself,
            // the silk set's own row above).
            Map.entry(Identifier.parse("minecraft:cobweb"),
                    new BlockBehavior(4.0, true, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:string"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_bars"),
                    new BlockBehavior(5.0, true, false, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_bars"), 1)))),
            // Slice L5 — the drop repair: DoubleStoneSlabBlock.getDropItem
            // is the SINGLE slab item and getBaseDropCount's isDouble ? 2
            // : 1 — the double never dropped itself.
            Map.entry(Identifier.parse("minecraft:double_stone_slab"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_slab"), 2)))),
            Map.entry(Identifier.parse("minecraft:double_sandstone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_slab"), 2)))),
            Map.entry(Identifier.parse("minecraft:double_cobblestone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_slab"), 2)))),
            Map.entry(Identifier.parse("minecraft:double_stone_brick_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_brick_slab"), 2)))),
            Map.entry(Identifier.parse("minecraft:double_stone_slab_seamless"),
                    new BlockBehavior(2.0, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_slab"), 2)))),
            Map.entry(Identifier.parse("minecraft:smooth_sandstone"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:smooth_sandstone"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_east"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_east"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_west"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_west"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_south"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_south"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_north"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_north"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_east_top"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_east_top"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_west_top"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_west_top"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_south_top"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_south_top"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_north_top"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_stairs_north_top"), 1)))),

            // The stronghold set (Slice 12c — community blocks.json 1.8
            // values): the stone-brick family 1.5 rock pickaxe-gated
            // (harvest 0) dropping itself — the mossy/cracked variants
            // drop the PLAIN brick (the reference's getStateFromMeta 0 —
            // Item.getItemFromBlock over the default state); the spawner
            // 5.0 rock pickaxe-gated dropping nothing (the reference's
            // null drop item); the smooth brick slab 2.0
            // rock like the stone slab; the iron door 5.0 rock
            // pickaxe-gated dropping itself; the end portal frames -1
            // undiggable dropless (the reference's unbreakable
            // portal-frame rule). The infested family (Slice 18c —
            // InfestedBlock's own rows): setStrength(0.0F) — the egg
            // breaks INSTANTLY by hand (the clay material, no tool gate),
            // drops NOTHING (getBaseDropCount 0) — the silk arm's host
            // drop walks the DropService's own infested arm, and the
            // silverfish burst rides the break path's spawn sink (never
            // a drop row).
            Map.entry(Identifier.parse("minecraft:stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:mossy_stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:cracked_stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:chiseled_stone_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:infested_stone"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_cobblestone"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_mossy_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_cracked_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:infested_chiseled_stone_bricks"),
                    new BlockBehavior(0.0, true, false, "clay", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_brick_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_brick_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:mob_spawner"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of())),
            Map.entry(Identifier.parse("minecraft:iron_door_west"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_north"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_east"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_south"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:iron_door_upper"),
                    new BlockBehavior(5.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_south"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_west"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_north"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_east"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_south"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_west"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_north"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_east"),
                    new BlockBehavior(-1.0, false, false, "rock", 0, List.of())),

            // The fortress set (Slice 12d — community blocks.json 1.8
            // values): the nether-brick family 2.0 rock pickaxe-gated
            // (harvest 0) dropping itself; the wart a plant (hardness 0,
            // the reference's drop rule: 1 below age 3, 2-4 at maturity —
            // the random band ledgers with the farming slice, the table
            // carries the floor).
            Map.entry(Identifier.parse("minecraft:nether_bricks"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_bricks"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_fence"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_fence"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east_top"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west_top"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south_top"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north_top"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_brick_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage0"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage1"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage2"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 1)))),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage3"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:nether_wart"), 2)))),

            // The monument set (Slice 12e — community blocks.json 1.8
            // values): the prismarine trio 1.5 rock pickaxe-gated dropping
            // the prismarine item (the variant's damage nuance ledgers with
            // the item-damage slice), the sea lantern 0.3 dropping its
            // crystals (the 2-3 band floors at 2, the silk arm ledgers),
            // the sponge pair 0.6 dropping the dry sponge, the gold block
            // 3.0 rock pickaxe-gated dropping itself.
            Map.entry(Identifier.parse("minecraft:prismarine_rough"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine"), 1)))),
            Map.entry(Identifier.parse("minecraft:prismarine_bricks"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine"), 1)))),
            Map.entry(Identifier.parse("minecraft:prismarine_dark"),
                    new BlockBehavior(1.5, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine"), 1)))),
            Map.entry(Identifier.parse("minecraft:sea_lantern"),
                    new BlockBehavior(0.3, true, false, "glass", 0,
                            // the SeaLanternBlock band (clamp(2 + nextInt(2)
                            // + nextInt(fortune + 1), 1, 5)) rides the
                            // DropService's sea-lantern family arm; the row's
                            // fixed 2 is the raw-table floor, the lapis-ore
                            // convention.
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:prismarine_crystals"), 2)))),
            Map.entry(Identifier.parse("minecraft:sponge"),
                    new BlockBehavior(0.6, true, false, "sponge", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sponge"), 1)))),
            Map.entry(Identifier.parse("minecraft:wet_sponge"),
                    new BlockBehavior(0.6, true, false, "sponge", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sponge"), 1)))),
            Map.entry(Identifier.parse("minecraft:gold_block"),
                    new BlockBehavior(3.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:gold_block"), 1)))),

            // The temple set (Slice 12f — community blocks.json 1.8
            // values): the chiseled sandstone drops the plain sandstone
            // (the reference's Item.getItemFromBlock over the default
            // state), the clay pair drops itself, the mossy cobblestone
            // 2.0 rock pickaxe-gated dropping itself, the vine a plant.
            Map.entry(Identifier.parse("minecraft:chiseled_sandstone"),
                    new BlockBehavior(0.8, true, false, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone"), 1)))),
            Map.entry(Identifier.parse("minecraft:stained_clay_orange"),
                    new BlockBehavior(1.25, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stained_clay_orange"), 1)))),
            Map.entry(Identifier.parse("minecraft:stained_clay_yellow"),
                    new BlockBehavior(1.25, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stained_clay_yellow"), 1)))),
            Map.entry(Identifier.parse("minecraft:mossy_cobblestone"),
                    new BlockBehavior(2.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:mossy_cobblestone"), 1)))),
            Map.entry(Identifier.parse("minecraft:vine"),
                    new BlockBehavior(0.2, true, false, "plants", 0, List.of())),

            // The golem family's blocks (Slice 17d — community blocks.json
            // 1.8 values): the snow block drops the 4 snowballs
            // (SnowBlock.getDropItem + quantityDropped 4), the snow layer
            // drops nothing at all (SnowLayerBlock.getDropItem's null arm
            // without silk touch), the iron block is a stone-tier pickaxe
            // rock dropping itself, and the pumpkin pair are woody
            // self-droppers (the historical 1.0 axe family).
            Map.entry(Identifier.parse("minecraft:snow"),
                    new BlockBehavior(0.2, true, false, "snow", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:snowball"), 4)))),
            Map.entry(Identifier.parse("minecraft:snow_layer"),
                    new BlockBehavior(0.1, true, false, "snow", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:iron_block"),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:iron_block"), 1)))),
            Map.entry(Identifier.parse("minecraft:pumpkin"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:pumpkin"), 1)))),
            Map.entry(Identifier.parse("minecraft:lit_pumpkin"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lit_pumpkin"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_west"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_north"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_east"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head_west"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head_north"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            Map.entry(Identifier.parse("minecraft:bed_head_east"),
                    new BlockBehavior(0.2, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:bed"), 1)))),

            // The standing sign (community blocks.json: hardness 1.0, wood
            // material, drops itself — the sign item, legacy 323).
            Map.entry(Identifier.parse("minecraft:sign"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            Map.entry(Identifier.parse("minecraft:sign_west"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            Map.entry(Identifier.parse("minecraft:sign_north"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            Map.entry(Identifier.parse("minecraft:sign_east"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),

            // The wall signs (block 68): the hanging model, same wood feel,
            // the same sign item drop (legacy 323).
            Map.entry(Identifier.parse("minecraft:wall_sign"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            Map.entry(Identifier.parse("minecraft:wall_sign_south"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            Map.entry(Identifier.parse("minecraft:wall_sign_west"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            Map.entry(Identifier.parse("minecraft:wall_sign_east"),
                    new BlockBehavior(1.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sign"), 1)))),
            // The skull family (Slice 60 — block 144, setStrength(1.0F)
            // STONE_SOUNDS): no table drop — the typed drop rides the
            // break hook's tile read (the reference's empty dropItems +
            // onRemoved's own walk).
            Map.entry(Identifier.parse("minecraft:skull"),
                    new BlockBehavior(1.0, true, false, "stone", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:skull_down"),
                    new BlockBehavior(1.0, true, false, "stone", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:skull_north"),
                    new BlockBehavior(1.0, true, false, "stone", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:skull_south"),
                    new BlockBehavior(1.0, true, false, "stone", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:skull_west"),
                    new BlockBehavior(1.0, true, false, "stone", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:skull_east"),
                    new BlockBehavior(1.0, true, false, "stone", 0, List.of())),


            Map.entry(Identifier.parse("minecraft:oak_door"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_north"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_east"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_south"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open_north"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open_east"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_open_south"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_upper"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_door_upper_open"),
                    new BlockBehavior(3.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_door"), 1)))),
            // The ladders (community blocks.json: hardness 0.4, drops itself)
            Map.entry(Identifier.parse("minecraft:ladder"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            // The redstone family (community blocks.json: hardness 0, the
            // wire drops the dust, the torches drop the torch, the repeater
            // drops itself — every power/facing/delay variant shares the row).
            Map.entry(Identifier.parse("minecraft:redstone_wire"),
                    new BlockBehavior(0.0, false, false, null, 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone"), 1)))),

            Map.entry(Identifier.parse("minecraft:ladder_south"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            Map.entry(Identifier.parse("minecraft:ladder_west"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            Map.entry(Identifier.parse("minecraft:ladder_east"),
                    new BlockBehavior(0.4, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:ladder"), 1)))),

            // The oak fence (hardness 2.0, wood, drops itself)
            Map.entry(Identifier.parse("minecraft:oak_fence"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_fence"), 1)))),

            // The tall-grass family (TallPlantBlock lines 22-93): the bare
            // hand rolls ONLY the 1/8 wheat-seed walk (getDropItem lines
            // 63-65 — never the plant item itself; the self-drop is the
            // shears arm's alone, afterMinedByPlayer lines 73-80, which the
            // DropService's family arm owns). The table rows stay the
            // no-tool walk for the table-driven consumers (the piston
            // destroy arm's dropItems(pos, 0), the wash-out path and the
            // BlockData read) — the pre-fix rows also listed a self-drop
            // at chance 1.0, which made every by-hand break yield the
            // tallgrass item (wire item 31 damage 0, the "Shrub" the
            // clients show) and let the fern row reference the never
            // registered minecraft:fern item (an ISE on every fern break).
            Map.entry(Identifier.parse("minecraft:tall_grass"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1, 0.125)))),
            Map.entry(Identifier.parse("minecraft:fern"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            // the fern rides the family's own drop law
                            // (TallPlantBlock covers TYPE 2 — the same
                            // 1/8 seed walk, never a self-drop)
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wheat_seeds"), 1, 0.125)))),
            Map.entry(Identifier.parse("minecraft:dead_bush"),
                    new BlockBehavior(0.0, true, false, "plants", 0, List.of())),
            Map.entry(Identifier.parse("minecraft:dandelion"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dandelion"), 1)))),
            Map.entry(Identifier.parse("minecraft:poppy"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:poppy"), 1)))),

            // Community data (pc/1.8 blocks.json): sandstone hardness 0.8,
            // material rock, pickaxe required for drops, drops itself.
            Map.entry(Identifier.parse("minecraft:sandstone"),
                    new BlockBehavior(0.8, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone"), 1)))),

            // The nether's materials (community blocks.json 1.8 values):
            // netherrack 0.4 rock pickaxe-gated (harvest level 0 — the wooden
            // pick suffices), drops itself; soul sand 0.5 sand-material
            // shovel-block, drops itself; quartz ore 3.0 rock pickaxe-gated
            // (harvest 0) dropping the quartz item; glowstone 0.3
            // glass-material dropping the GlowstoneBlock band
            // (clamp(2 + nextInt(3) + nextInt(fortune + 1), 1, 4) — the
            // DropService's glowstone family arm owns the walk; the row's
            // fixed 2 only rides non-family callers that read the table raw,
            // the lapis-ore convention).
            Map.entry(Identifier.parse("minecraft:netherrack"),
                    new BlockBehavior(0.4, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:netherrack"), 1)))),
            Map.entry(Identifier.parse("minecraft:soul_sand"),
                    new BlockBehavior(0.5, true, false, "sand", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:soul_sand"), 1)))),
            Map.entry(Identifier.parse("minecraft:quartz_ore"),
                    new BlockBehavior(3.0, true, true, "rock", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:quartz"), 1)))),
            Map.entry(Identifier.parse("minecraft:glowstone"),
                    new BlockBehavior(0.3, true, false, "glass", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:glowstone_dust"), 2)))),

            // The mushrooms (community blocks.json): instant-break plants
            // dropping themselves (the PlantFeature pair the nether grows on
            // its netherrack floors).
            Map.entry(Identifier.parse("minecraft:brown_mushroom"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:brown_mushroom"), 1)))),
            Map.entry(Identifier.parse("minecraft:red_mushroom"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:red_mushroom"), 1)))),

            // Community data (pc/1.8 blocks.json): furnace_lit mirrors the
            // furnace (3.5, rock, pickaxe 1, drops the furnace item); the
            // reed and the cactus are instant-break plants that drop
            // themselves (the cactus's slight inset is the collision slice's
            // concern).
            Map.entry(Identifier.parse("minecraft:furnace_lit"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_lit_north"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_lit_south"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_lit_west"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),
            Map.entry(Identifier.parse("minecraft:furnace_lit_east"),
                    new BlockBehavior(3.5, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:furnace"), 1)))),

            // The collision-shape slice (community blocks.json: slabs and
            // stairs hardness 2.0; the stone families gate drops on a
            // pickaxe like their parent cubes, the wooden families drop by
            // hand; every variant drops its own item form).
            Map.entry(Identifier.parse("minecraft:oak_slab"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_slab_top"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:stone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:stone_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_slab"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:sandstone_slab_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sandstone_slab"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_east"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            // The HALF bit's upside-down forms (StairsBlock's TOP fold).
            Map.entry(Identifier.parse("minecraft:oak_stairs_east_top"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west_top"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south_top"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north_top"),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:oak_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north_top"),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cobblestone_stairs"), 1)))),

            // Community data (pc/1.8 blocks.json): rail hardness 0.7,
            // instant-ish, drops itself (any pick breaks it by hand speed).
            Map.entry(Identifier.parse("minecraft:rail"),
                    new BlockBehavior(0.7, true, false, "stone", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:rail"), 1)))),
            Map.entry(Identifier.parse("minecraft:rail_ew"),
                    new BlockBehavior(0.7, true, false, "stone", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:rail"), 1)))),
            Map.entry(Identifier.parse("minecraft:sugar_cane"),
                    new BlockBehavior(0.0, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sugar_cane"), 1)))),
            Map.entry(Identifier.parse("minecraft:cactus"),
                    new BlockBehavior(0.4, true, false, "plants", 0,
                            List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cactus"), 1)))),

            // Community data (pc/1.8 blocks.json): fire hardness 0 (instant
            // break), no material, drops nothing — the historical extinguish.
            // The AGE band (FireBlocks): every state breaks the same.
            Map.entry(Identifier.parse("minecraft:fire"),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(1),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(2),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(3),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(4),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(5),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(6),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(7),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(8),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(9),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(10),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(11),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(12),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(13),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(14),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),
            Map.entry(FireBlocks.stateId(15),
                    new BlockBehavior(0.0, true, false, null, 0, List.of())),

            Map.entry(Identifier.parse("minecraft:bedrock"),
                    BlockBehavior.unbreakable()),

            // The command-block family (Slice 72 — Block.java line 1299:
            // setUnbreakable + setResistance(6000000)): creative-only
            // break, no drops (getBaseDropCount line 93's empty override).
            Map.entry(Identifier.parse("minecraft:command_block"),
                    BlockBehavior.unbreakable()),
            Map.entry(Identifier.parse("minecraft:command_block_triggered"),
                    BlockBehavior.unbreakable())));

        // The redstone family's variants (Slice 9a): every power level of
        // the wire, both torch pairs and all the repeater states share the
        // family row — the drops are the item, the break is instant by hand.
        BlockBehavior wire = behaviors.get(Identifier.parse("minecraft:redstone_wire"));
        for (int power = 1; power <= 15; power++) {
            behaviors.put(Identifier.parse("minecraft:redstone_wire_" + power), wire);
        }
        BlockBehavior torch = new BlockBehavior(0.0, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:redstone_torch"), 1)));
        for (String prefix : new String[]{"redstone_torch", "unlit_redstone_torch"}) {
            behaviors.put(Identifier.parse("minecraft:" + prefix), torch);
            for (String wall : new String[]{"east", "west", "south", "north"}) {
                behaviors.put(Identifier.parse("minecraft:" + prefix + "_" + wall), torch);
            }
        }
        BlockBehavior repeater = new BlockBehavior(0.0, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:repeater"), 1)));
        for (String prefix : new String[]{"repeater", "powered_repeater"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (int delay = 1; delay <= 4; delay++) {
                    behaviors.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + delay),
                            repeater);
                }
            }
        }
        // The comparator (Slice 9d, community blocks.json: hardness 0, the
        // break is instant by hand): every facing/mode/powered variant
        // drops the comparator item.
        BlockBehavior comparator = new BlockBehavior(0.0, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:comparator"), 1)));
        for (String prefix : new String[]{"comparator", "powered_comparator"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (String mode : new String[]{"compare", "subtract"}) {
                    behaviors.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + mode),
                            comparator);
                }
            }
        }
        // The piston family (Slice 9e, community blocks.json: the base 0.5):
        // every base variant drops its item; the head drops nothing (the
        // reference's zero drop count — the base carries the item); the
        // moving carrier is unbreakable (MovingBlock's strength -1).
        String[] directions = {"down", "up", "north", "south", "west", "east"};
        BlockBehavior piston = new BlockBehavior(0.5, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:piston"), 1)));
        BlockBehavior stickyPiston = new BlockBehavior(0.5, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:sticky_piston"), 1)));
        for (int facing = 0; facing < 6; facing++) {
            for (int extended = 0; extended <= 1; extended++) {
                String suffix = directions[facing] + (extended == 1 ? "_extended" : "");
                behaviors.put(Identifier.parse("minecraft:piston_" + suffix), piston);
                behaviors.put(Identifier.parse("minecraft:sticky_piston_" + suffix), stickyPiston);
            }
        }
        BlockBehavior pistonHead = new BlockBehavior(0.5, true, false, "rock", 0, List.of());
        for (int facing = 0; facing < 6; facing++) {
            behaviors.put(Identifier.parse("minecraft:piston_head_" + directions[facing]), pistonHead);
            behaviors.put(Identifier.parse("minecraft:piston_head_" + directions[facing] + "_sticky"),
                    pistonHead);
        }
        BlockBehavior movingPiston = new BlockBehavior(-1.0, false, false, "rock", 0, List.of());
        for (int facing = 0; facing < 6; facing++) {
            behaviors.put(Identifier.parse("minecraft:moving_piston_" + directions[facing]),
                    movingPiston);
            behaviors.put(Identifier.parse("minecraft:moving_piston_" + directions[facing] + "_sticky"),
                    movingPiston);
        }
        // The hopper (Slice 10b, community blocks.json: hardness 3.0): every
        // facing/enabled variant drops the hopper item.
        BlockBehavior hopper = new BlockBehavior(3.0, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:hopper"), 1)));
        for (int facing : new int[]{0, 2, 3, 4, 5}) { // no UP — the FACING predicate
            behaviors.put(Identifier.parse("minecraft:hopper_" + directions[facing]), hopper);
            behaviors.put(Identifier.parse("minecraft:hopper_" + directions[facing] + "_disabled"),
                    hopper);
        }
        // The daylight detector (Slice 27c — the reference's own
        // registration: setStrength(0.2F), WOOD sounds, Material.WOOD, a
        // 0.375-high non-cube): all thirty-two power states of both forms
        // drop the NORMAL detector item (getDropItem lines 88-90 — the
        // inverted form never drops itself).
        BlockBehavior daylightDetector = new BlockBehavior(0.2, true, false, "wood", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:daylight_detector"), 1)));
        for (int power = 0; power <= 15; power++) {
            behaviors.put(Identifier.parse("minecraft:daylight_detector_p" + power),
                    daylightDetector);
            behaviors.put(Identifier.parse("minecraft:daylight_detector_inverted_p" + power),
                    daylightDetector);
        }
        // The dispenser and the dropper (Slice 11a, Block.java line 1041's
        // strength 3.5): every facing/triggered variant drops its item.
        BlockBehavior dispenser = new BlockBehavior(3.5, true, false, "stone", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dispenser"), 1)));
        BlockBehavior dropper = new BlockBehavior(3.5, true, false, "stone", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:dropper"), 1)));
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            behaviors.put(Identifier.parse("minecraft:dispenser_" + facing), dispenser);
            behaviors.put(Identifier.parse("minecraft:dispenser_" + facing + "_triggered"),
                    dispenser);
            behaviors.put(Identifier.parse("minecraft:dropper_" + facing), dropper);
            behaviors.put(Identifier.parse("minecraft:dropper_" + facing + "_triggered"),
                    dropper);
        }
        // The brewing stand (Slice 11e — Block.java line 1241's strength 0.5):
        // every bottle-bit variant drops the stand item (getDropItem lines
        // 120-123); instant-ish by hand, no tool gate.
        BlockBehavior brewingStand = new BlockBehavior(0.5, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:brewing_stand"), 1)));
        for (int bits = 0; bits < 8; bits++) {
            StringBuilder name = new StringBuilder("minecraft:brewing_stand");
            for (int slot = 0; slot < 3; slot++) {
                if ((bits & (1 << slot)) != 0) {
                    name.append("_bottle").append(slot);
                }
            }
            behaviors.put(Identifier.parse(name.toString()), brewingStand);
        }
        // The cauldron family (Block.java line 1242's strength 2.0): every
        // level state drops the cauldron item (getDropItem lines 178-180);
        // pickaxe-shaped by hand, no tool gate.
        BlockBehavior cauldron = new BlockBehavior(2.0, true, false, "rock", 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:cauldron"), 1)));
        behaviors.put(Identifier.parse("minecraft:cauldron"), cauldron);
        for (int level = 1; level <= 3; level++) {
            behaviors.put(Identifier.parse("minecraft:cauldron_level" + level), cauldron);
        }
        // The tripwire (Slice 10c, community blocks.json: hardness 0, instant
        // by hand): the wire drops string unless disarmed (the shears arm);
        // the hook drops the hook item.
        List<BlockBehavior.Drop> stringDrop =
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:string"), 1));
        List<BlockBehavior.Drop> hookDrop =
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:tripwire_hook"), 1));
        for (int bits = 0; bits < 16; bits++) {
            StringBuilder name = new StringBuilder("minecraft:tripwire");
            if ((bits & 1) != 0) {
                name.append("_powered");
            }
            if ((bits & 2) != 0) {
                name.append("_suspended");
            }
            if ((bits & 4) != 0) {
                name.append("_attached");
            }
            if ((bits & 8) != 0) {
                name.append("_disarmed");
            }
            // A disarmed wire drops nothing (the shears already paid the
            // string out — the reference's DISARMED arm).
            behaviors.put(Identifier.parse(name.toString()),
                    new BlockBehavior(0.0, false, false, null, 0,
                            (bits & 8) != 0 ? List.of() : stringDrop));
        }
        for (String facing : new String[]{"south", "west", "north", "east"}) {
            for (int attached = 0; attached <= 1; attached++) {
                for (int powered = 0; powered <= 1; powered++) {
                    behaviors.put(Identifier.parse("minecraft:tripwire_hook_" + facing
                            + (attached == 1 ? "_attached" : "")
                            + (powered == 1 ? "_powered" : "")),
                            new BlockBehavior(0.0, false, false, null, 0, hookDrop));
                }
            }
        }
        // The player-driven sources (Slice 9c): the lever, the buttons and
        // the plates — every facing/powered variant drops its item, the
        // break is instant by hand (community blocks.json: hardness 0.5 for
        // the lever/buttons, 0.5 for the plates).
        BlockBehavior lever = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:lever"), 1)));
        for (String key : new String[]{"down_x", "east", "west", "south", "north", "up_z", "up_x", "down_z"}) {
            behaviors.put(Identifier.parse("minecraft:lever_" + key), lever);
            behaviors.put(Identifier.parse("minecraft:lever_" + key + "_powered"), lever);
        }
        BlockBehavior stoneButton = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_button"), 1)));
        BlockBehavior woodButton = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wooden_button"), 1)));
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            behaviors.put(Identifier.parse("minecraft:stone_button_" + facing), stoneButton);
            behaviors.put(Identifier.parse("minecraft:stone_button_" + facing + "_powered"), stoneButton);
            behaviors.put(Identifier.parse("minecraft:wooden_button_" + facing), woodButton);
            behaviors.put(Identifier.parse("minecraft:wooden_button_" + facing + "_powered"), woodButton);
        }
        BlockBehavior stonePlate = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:stone_pressure_plate"), 1)));
        BlockBehavior woodPlate = new BlockBehavior(0.5, false, false, null, 0,
                List.of(new BlockBehavior.Drop(Identifier.parse("minecraft:wooden_pressure_plate"), 1)));
        behaviors.put(Identifier.parse("minecraft:stone_pressure_plate"), stonePlate);
        behaviors.put(Identifier.parse("minecraft:stone_pressure_plate_powered"), stonePlate);
        behaviors.put(Identifier.parse("minecraft:wooden_pressure_plate"), woodPlate);
        behaviors.put(Identifier.parse("minecraft:wooden_pressure_plate_powered"), woodPlate);

        // The dye-coloring world families (Slice 31 — the reference's Block
        // registrations: wool 35 `setStrength(0.8F)` CLOTH, stained glass 95
        // `setStrength(0.3F)` (getBaseDropCount 0 — the silk-only family),
        // stained clay 159 `setStrength(1.25F)` STONE, the stained pane 160
        // `setStrength(0.3F)` (PaneBlock hasDrops false), the carpet 171
        // `setStrength(0.1F)` CLOTH `setOpacity(0)`, hardened clay 172
        // `setStrength(1.25F)` STONE). Every member drops its family's own
        // item with the damage field carrying the color (the reference's
        // getDropItemMetadata); the glass pair drops nothing by hand (silk
        // returns the family item — the DropService's family arm).
        for (int color = 0; color < 16; color++) {
            // wool (the white keeps the plain minecraft:wool row above)
            if (color != 0) {
                behaviors.put(DyeFamilyBlocks.blockOf("wool", color),
                        new BlockBehavior(0.8, true, false, "wool", 0,
                                List.of(new BlockBehavior.Drop(DyeFamilyBlocks.WOOL_ITEM, 1, color))));
            }
            // stained glass: silk-only (no hand rows), the null material
            // matching the plain glass/pane rows (no tool class)
            behaviors.put(DyeFamilyBlocks.stainedGlassBlock(color),
                    new BlockBehavior(0.3, true, false, null, 0, List.of()));
            // stained glass pane: silk-only (PaneBlock hasDrops false)
            behaviors.put(DyeFamilyBlocks.stainedGlassPaneBlock(color),
                    new BlockBehavior(0.3, true, false, null, 0, List.of()));
            // carpet: the 1/16-tall cover, no tool class (the reference's
            // own Material.CARPET — shears accelerate WOOL, never CARPET)
            behaviors.put(DyeFamilyBlocks.carpetBlock(color),
                    new BlockBehavior(0.1, true, false, "carpet", 0,
                            List.of(new BlockBehavior.Drop(DyeFamilyBlocks.CARPET_ITEM, 1, color))));
            // stained clay: the stone pickaxe family (any tier drops)
            if (color != 1 && color != 4) {
                behaviors.put(DyeFamilyBlocks.stainedClayBlock(color),
                        new BlockBehavior(1.25, true, true, "rock", 0,
                                List.of(new BlockBehavior.Drop(
                                        DyeFamilyBlocks.STAINED_HARDENED_CLAY_ITEM, 1, color))));
            }
        }
        // the temple pair joins the family's rows (the loop skips their
        // colors — the constants predate the family)
        behaviors.put(Identifier.parse("minecraft:stained_clay_orange"),
                new BlockBehavior(1.25, true, true, "rock", 0,
                        List.of(new BlockBehavior.Drop(
                                DyeFamilyBlocks.STAINED_HARDENED_CLAY_ITEM, 1, 1))));
        behaviors.put(Identifier.parse("minecraft:stained_clay_yellow"),
                new BlockBehavior(1.25, true, true, "rock", 0,
                        List.of(new BlockBehavior.Drop(
                                DyeFamilyBlocks.STAINED_HARDENED_CLAY_ITEM, 1, 4))));
        // hardened clay itself (172 — the parent of the stained family)
        behaviors.put(Identifier.parse("minecraft:hardened_clay"),
                new BlockBehavior(1.25, true, true, "rock", 0,
                        List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:hardened_clay"), 1))));

        // The wood-variant foundation (Slice 35 — the reference's own
        // registrations: planks `setStrength(2.0F)` WOOD, logs 2.0 WOOD,
        // leaves 0.2 LEAVES, saplings 0.0 PLANT): the planks and logs drop
        // the family item with the variant damage (Item.java's BlockItem
        // walk — the oak keeps its shipped plain-item rows), the leaves
        // roll the reference's chance band (the shears arm and the
        // sapling/apple walks ride the DropService family branch), and the
        // saplings drop themselves.
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            Identifier planks = WoodVariantBlocks.planksBlock(variant);
            Identifier log = WoodVariantBlocks.logBlock(variant);
            Identifier leaves = WoodVariantBlocks.leavesBlock(variant);
            Identifier sapling = WoodVariantBlocks.saplingBlock(variant);
            if (variant != 0) {
                behaviors.put(planks, new BlockBehavior(2.0, true, false, "wood", 0,
                        List.of(new BlockBehavior.Drop(WoodVariantBlocks.PLANKS_ITEM, 1, variant))));
                behaviors.put(log, new BlockBehavior(2.0, true, false, "wood", 0,
                        List.of(new BlockBehavior.Drop(
                                variant < 4 ? WoodVariantBlocks.LOG_ITEM : WoodVariantBlocks.LOG2_ITEM,
                                1, WoodVariantBlocks.wireMetaOf("log", variant)))));
                behaviors.put(leaves, new BlockBehavior(0.2, true, false, "leaves", 0,
                        List.of())); // the shears/sapling/apple walks ride the DropService branch
            }
            behaviors.put(sapling, new BlockBehavior(0.0, true, false, "plant", 0,
                    List.of(new BlockBehavior.Drop(WoodVariantBlocks.SAPLING_ITEM, 1, variant))));
        }
        // The tree-growth family's derived bands: the leaf decay states
        // break exactly like their fresh form (0.2 LEAVES, the
        // shears/sapling/apple walks ride the DropService branch), the
        // ready saplings drop the fresh family item with the variant
        // damage (the stage bit never reaches the item).
        for (EngineBlockType state : LeafDecayBlocks.familyBlocks()) {
            behaviors.put(state.identifier(), new BlockBehavior(0.2, true, false, "leaves", 0,
                    List.of()));
        }
        for (EngineBlockType state : SaplingBlocks.familyBlocks()) {
            behaviors.put(state.identifier(), new BlockBehavior(0.0, true, false, "plant", 0,
                    List.of(new BlockBehavior.Drop(WoodVariantBlocks.SAPLING_ITEM, 1,
                            WoodVariantBlocks.variantOfSapling(state.identifier())))));
        }

        // The building-shape families (Slice 36 — the reference's own
        // registrations: every wood stair `setStrength(2.0F)` WOOD, the
        // brick stair 2.0 ROCK, the stone brick 1.5, the quartz 0.8, the
        // fences 2.0 WOOD, the wooden slabs 2.0 WOOD): every state drops
        // its family's own item (the stairs over the bare ids, the slabs
        // over the family item with the variant damage).
        for (String family : WoodShapeBlocks.STAIRS.keySet()) {
            boolean wood = !family.equals("brick") && !family.equals("stone_brick")
                    && !family.equals("quartz");
            double strength = switch (family) {
                case "brick" -> 2.0;
                case "stone_brick" -> 1.5;
                case "quartz" -> 0.8;
                default -> 2.0;
            };
            Identifier item = Identifier.parse("minecraft:" + family + "_stairs");
            BlockBehavior stairs = new BlockBehavior(strength, true, !wood,
                    wood ? "wood" : "rock", 0, List.of(new BlockBehavior.Drop(item, 1)));
            for (EngineBlockType state : WoodShapeBlocks.stairsStates(family)) {
                behaviors.put(state.identifier(), stairs);
            }
            for (EngineBlockType state : WoodShapeBlocks.stairsTopStates(family)) {
                behaviors.put(state.identifier(), stairs);
            }
        }
        for (String wood : WoodShapeBlocks.FENCES.keySet()) {
            behaviors.put(Identifier.parse("minecraft:" + wood + "_fence"),
                    new BlockBehavior(2.0, true, false, "wood", 0, List.of(new BlockBehavior.Drop(
                            Identifier.parse("minecraft:" + wood + "_fence"), 1))));
        }
        for (int variant = 1; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            BlockBehavior slab = new BlockBehavior(2.0, true, false, "wood", 0,
                    List.of(new BlockBehavior.Drop(
                            Identifier.parse("minecraft:wooden_slab"), 1, variant)));
            behaviors.put(WoodShapeBlocks.slabStates(variant).get(0).identifier(), slab);
            behaviors.put(WoodShapeBlocks.slabStates(variant).get(1).identifier(), slab);
        }

        // The double wooden slabs (Slice 58 — DoubleWoodenSlabBlock's own
        // row: setStrength(2.0F).setBlastResistance(5.0F) WOOD_SOUNDS; the
        // drop TWO single slabs (SlabBlock.getBaseDropCount's isDouble ? 2
        // : 1) with the variant damage (getDropItemMetadata's own band).
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            behaviors.put(WoodShapeBlocks.doubleSlabState(variant).identifier(),
                    new BlockBehavior(2.0, true, false, "wood", 0,
                            List.of(new BlockBehavior.Drop(
                                    Identifier.parse("minecraft:wooden_slab"), 2, variant))));
        }

        // The fence-gate families (Slice 37 — FenceGateBlock's own rows:
        // every gate `setStrength(2.0F).setBlastResistance(5.0F)` WOOD_SOUNDS
        // — the wood class, no tool gate): all sixteen states per family
        // drop the family item (the reference's single BlockItem).
        for (String family : FenceGateBlocks.GATES.keySet()) {
            BlockBehavior gate = new BlockBehavior(2.0, true, false, "wood", 0,
                    List.of(new BlockBehavior.Drop(FenceGateBlocks.gateItem(family), 1)));
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        behaviors.put(FenceGateBlocks.state(family, facing, open, powered)
                                .identifier(), gate);
                    }
                }
            }
        }

        // The rail trio (Slice 38 — Block.java's own rows: every rail
        // `setStrength(0.7F)` METAL_SOUNDS over Material.DECORATION — no
        // tool gate, drops itself): all twelve states per family.
        for (String family : RailBlocks.RAILS.keySet()) {
            BlockBehavior rail = new BlockBehavior(0.7, true, false, "stone", 0,
                    List.of(new BlockBehavior.Drop(RailBlocks.railItem(family), 1)));
            for (int shape = 0; shape < 6; shape++) {
                for (boolean powered : new boolean[] {false, true}) {
                    behaviors.put(RailBlocks.state(family, shape, powered).identifier(), rail);
                }
            }
        }

        // The trapdoor pair (Slice 39 — TrapdoorBlock's own rows: the wood
        // `setStrength(3.0F)` WOOD_SOUNDS, the iron `setStrength(5.0F)`
        // METAL_SOUNDS — Material.IRON's own setRequiresTool gates the iron
        // pair to pickaxe-class drops): every state drops the family item.
        for (String family : TrapdoorBlocks.TRAPDOORS.keySet()) {
            boolean iron = TrapdoorBlocks.isIron(family);
            BlockBehavior trapdoor = new BlockBehavior(iron ? 5.0 : 3.0,
                    true, iron, iron ? "rock" : "wood", 0,
                    List.of(new BlockBehavior.Drop(TrapdoorBlocks.trapdoorItem(family), 1)));
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean top : new boolean[] {false, true}) {
                        behaviors.put(TrapdoorBlocks.state(family, facing, open, top)
                                .identifier(), trapdoor);
                    }
                }
            }
        }

        // The cocoa family (Slice 43 — CocoaBlock's own row
        // `setStrength(0.2F).setBlastResistance(5.0F)` WOOD_SOUNDS over
        // Material.PLANT): the drop count rides the DropService's own walk
        // (dropItems' 1-or-3 dye rolls over the AGE band).
        for (net.zaminmc.torch.server.block.EngineBlockType pod
                : CocoaBlocks.familyBlocks()) {
            behaviors.put(pod.identifier(),
                    new BlockBehavior(0.2, true, false, "plant", 0,
                            List.of(new BlockBehavior.Drop(
                                    Identifier.parse("minecraft:dye"), 1,
                                    1.0, CocoaBlocks.BROWN_DYE_DAMAGE))));
        }

        // The flower family (Slice 44 — RedFlowerBlock's own row
        // `setStrength(0.0F).setSounds(GRASS_SOUNDS)` over Material.PLANT,
        // Block.java line 1057): the eight new variants drop the family
        // item with the TYPE id in the damage field (getDropItemMetadata's
        // own consult) — the poppy keeps its shipped self-drop row.
        for (net.zaminmc.torch.server.block.EngineBlockType flower
                : FlowerBlocks.familyBlocks()) {
            behaviors.put(flower.identifier(),
                    new BlockBehavior(0.0, true, false, "plant", 0,
                            List.of(new BlockBehavior.Drop(
                                    FlowerBlocks.FAMILY_ITEM, 1,
                                    FlowerBlocks.typeOf(flower)))));
        }

        // The double-plant family (Slice 45 — DoublePlantBlock's own row
        // `setStrength(0.0F)` GRASS_SOUNDS over Material.REPLACEABLE_PLANT):
        // the lower flowers drop the family item with the VARIANT damage
        // (getDropItemMetadata lines 97-101), the double grass rolls the
        // 1/8 seed walk, the large fern and every upper drop nothing.
        for (net.zaminmc.torch.server.block.EngineBlockType plant
                : DoublePlantBlocks.familyBlocks()) {
            java.util.List<BlockBehavior.Drop> drops = java.util.List.of();
            if (!DoublePlantBlocks.isUpper(plant.identifier())) {
                int variant = DoublePlantBlocks.variantOf(plant.identifier());
                if (variant == 0 || variant == 1 || variant == 4 || variant == 5) {
                    drops = java.util.List.of(new BlockBehavior.Drop(
                            DoublePlantBlocks.FAMILY_ITEM, 1, variant));
                } else if (variant == 2) {
                    // The grass's 1/8 seed roll (getDropItem lines 86-89).
                    drops = java.util.List.of(new BlockBehavior.Drop(
                            Identifier.parse("minecraft:wheat_seeds"), 1, 0.125));
                }
            }
            behaviors.put(plant.identifier(),
                    new BlockBehavior(0.0, true, false, "plant", 0, drops));
        }

        // The cake family (Slice 46 — CakeBlock's own row: no setStrength
        // over Material.CAKE — the 0.0 default; getDropItem line 113 the
        // null drop, getBaseDropCount line 108 the 0: the broken cake
        // yields nothing, the slices come only from the click's eat arm).
        for (net.zaminmc.torch.server.block.EngineBlockType slice
                : CakeBlocks.familyBlocks()) {
            behaviors.put(slice.identifier(),
                    new BlockBehavior(0.0, true, false, "plant", 0, java.util.List.of()));
        }

        // The wall family (Slice 47 — WallBlock's own rows: the
        // cobblestone's strength carried through
        // setStrength(block.miningTime) — 2.0 ROCK with the pickaxe gate;
        // getDropItemMetadata the VARIANT damage).
        for (net.zaminmc.torch.server.block.EngineBlockType wall
                : WallBlocks.familyBlocks()) {
            behaviors.put(wall.identifier(),
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            java.util.List.of(new BlockBehavior.Drop(
                                    WallBlocks.FAMILY_ITEM, 1,
                                    WallBlocks.variantOf(wall.identifier())))));
        }

        // The red sandstone family (Slice 52 — 1.8's own four, the
        // reference's own rows): the red sand rides the sand's own
        // 0.5-shovel row (SandBlock's setStrength(0.5F) — no tool gate,
        // the drop the sand item itself at the RED_SAND variant's damage
        // 1); the three TYPE states and the four stairs carry the block's
        // own 0.8 pickaxe row (RedSandstoneBlock's setStrength(0.8F) —
        // Material.STONE; the stairs inherit the SMOOTH base's strength
        // exactly like the reference's new StairsBlock(state)); the slab
        // pair and the double carry the SingleRedSandstoneSlabBlock rows
        // (setStrength(2.0F).setBlastResistance(10.0F)) with every form's
        // drop the single-slab item (getDropItem's own consult — the
        // double and the top alike yield the one slab, getDropItemMetadata
        // the RED_SANDSTONE variant id 0).
        behaviors.put(net.zaminmc.torch.server.block.RedSandstoneBlocks.RED_SAND,
                new BlockBehavior(0.5, true, false, "dirt", 0,
                        java.util.List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:sand"), 1, 1))));
        for (net.zaminmc.torch.server.block.EngineBlockType member
                : net.zaminmc.torch.server.block.RedSandstoneBlocks.familyBlocks()) {
            if (net.zaminmc.torch.server.block.RedSandstoneBlocks.isRedSand(member.identifier())) {
                continue; // the red sand rides the sand row above
            }
            if (net.zaminmc.torch.server.block.RedSandstoneBlocks.isStairs(member.identifier())) {
                behaviors.put(member.identifier(),
                        new BlockBehavior(0.8, true, true, "rock", 1,
                                java.util.List.of(new BlockBehavior.Drop(
                                        Identifier.parse("minecraft:red_sandstone_stairs"), 1))));
                continue;
            }
            behaviors.put(member.identifier(),
                    new BlockBehavior(0.8, true, true, "rock", 1,
                            java.util.List.of(new BlockBehavior.Drop(
                                    net.zaminmc.torch.server.block.RedSandstoneBlocks.FAMILY_ITEM, 1,
                                    net.zaminmc.torch.server.block.RedSandstoneBlocks.typeOf(
                                            member.identifier())))));
        }
        for (Identifier slab : new Identifier[] {
                Identifier.parse("minecraft:stone_slab2"),
                Identifier.parse("minecraft:stone_slab2_top")}) {
            behaviors.put(slab,
                    new BlockBehavior(2.0, true, true, "rock", 1,
                            java.util.List.of(new BlockBehavior.Drop(
                                    Identifier.parse("minecraft:stone_slab2"), 1))));
        }
        // Slice L5 — the drop repair: the 181 double yields TWO singles
        // (RedSandstoneSlab.getBaseDropCount's isDouble ? 2 : 1), and the
        // seamless skin drops the same pair.
        behaviors.put(Identifier.parse("minecraft:double_stone_slab2"),
                new BlockBehavior(2.0, true, true, "rock", 1,
                        java.util.List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:stone_slab2"), 2))));
        behaviors.put(Identifier.parse("minecraft:double_stone_slab2_seamless"),
                new BlockBehavior(2.0, true, true, "rock", 1,
                        java.util.List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:stone_slab2"), 2))));

        // The anvil family (Slice 53 — AnvilBlock's own row:
        // setStrength(5.0F) over Material.ANVIL with the pickaxe gate —
        // the harvest-level 1 like the reference's own canHarvestBlock
        // consult; the drop the family item with the DAMAGE damage
        // (getDropItemMetadata's own band: the mined chipped form yields
        // the chipped stack).
        for (net.zaminmc.torch.server.block.EngineBlockType anvil
                : net.zaminmc.torch.server.block.AnvilBlocks.familyBlocks()) {
            behaviors.put(anvil.identifier(),
                    new BlockBehavior(5.0, true, true, "rock", 1,
                            java.util.List.of(new BlockBehavior.Drop(
                                    net.zaminmc.torch.server.block.AnvilBlocks.FAMILY_ITEM, 1,
                                    net.zaminmc.torch.server.block.AnvilBlocks.damageOf(
                                            anvil.identifier())))));
        }

        // The end portal (Slice 54 — EndPortalBlock's own row:
        // setStrength(-1.0F) — unbreakable, never diggable, no drops).
        behaviors.put(net.zaminmc.torch.server.block.EndPortalBlocks.END_PORTAL,
                new BlockBehavior(-1.0, false, false, "portal", 0, java.util.List.of()));

        // The dragon egg (Slice 55 — DragonEggBlock's own row:
        // setStrength(3.0F) over Material.EGG — diggable by anything, but
        // the drop consult finds no item (the reference registers none:
        // getPickItem null, getItemFromBlock null), so the egg never
        // yields a stack).
        behaviors.put(net.zaminmc.torch.server.block.DragonEggBlocks.DRAGON_EGG,
                new BlockBehavior(3.0, true, false, "dirt", 0, java.util.List.of()));

        // The barrier (Slice 56 — BarrierBlock's own row: setUnbreakable —
        // the invisible indestructible solid; dropItems' empty override).
        behaviors.put(Identifier.parse("minecraft:barrier"),
                new BlockBehavior(-1.0, false, false, "barrier", 0, java.util.List.of()));

        // The weighted pressure-plate pair (Slice 49a —
        // WeightedPressurePlateBlock's own rows: setStrength(0.5F)
        // WOOD_SOUNDS over Material.IRON; every state drops its family
        // item — the POWER nibble never carries into the drop).
        for (net.zaminmc.torch.server.block.EngineBlockType plate
                : net.zaminmc.torch.server.redstone.WeightedPlateBlocks.familyBlocks()) {
            behaviors.put(plate.identifier(),
                    new BlockBehavior(0.5, true, false, "plant", 0,
                            java.util.List.of(new BlockBehavior.Drop(
                                    plate.identifier().value().startsWith("light_weighted")
                                            ? Identifier.parse("minecraft:light_weighted_pressure_plate")
                                            : Identifier.parse("minecraft:heavy_weighted_pressure_plate"),
                                    1))));
        }

        // The flower-pot family (Slice 48 — FlowerPotBlock's own row:
        // setStrength(0.0F) DEFAULT_SOUNDS over Material.DECORATION; the
        // survival break drops the pot AND the plant (getDropItem the pot,
        // onRemoved the TE's plant item with its metadata — the saplings
        // over the wood damage, the fern the tall grass's damage 2).
        for (net.zaminmc.torch.server.block.EngineBlockType pot
                : FlowerPotBlocks.familyBlocks()) {
            int content = FlowerPotBlocks.contentOf(pot.identifier());
            java.util.List<BlockBehavior.Drop> drops = new java.util.ArrayList<>(
                    java.util.List.of(new BlockBehavior.Drop(
                            FlowerPotBlocks.POT_ITEM, 1)));
            Identifier plant = FlowerPotBlocks.plantItemOf(content);
            if (plant != null) {
                drops.add(new BlockBehavior.Drop(plant, 1,
                        FlowerPotBlocks.plantDamageOf(content)));
            }
            behaviors.put(pot.identifier(),
                    new BlockBehavior(0.0, true, false, "plant", 0,
                            java.util.List.copyOf(drops)));
        }

        // The ground-cover pair (Slice 42 — MyceliumBlock/LilyPadBlock's
        // own rows: the mycelium `setStrength(0.6F)` GRASS_SOUNDS dropping
        // its dirt — getDropItem's own consult; the lily pad
        // `setStrength(0.0F)` GRASS_SOUNDS dropping itself).
        behaviors.put(Identifier.parse("minecraft:mycelium"),
                new BlockBehavior(0.6, true, false, "grass", 0,
                        List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:dirt"), 1))));
        behaviors.put(Identifier.parse("minecraft:waterlily"),
                new BlockBehavior(0.0, true, false, "plant", 0,
                        List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:waterlily"), 1))));

        // The melon family (Slice 41 — MelonBlock/StemBlock's own rows:
        // the melon `setStrength(1.0F)` over Material.PUMPKIN like the
        // shipped pumpkin, the stems `setStrength(0.0F)` — the melon's
        // slice walk and the stems' seed walk ride the DropService).
        behaviors.put(Identifier.parse("minecraft:melon_block"),
                new BlockBehavior(1.0, true, false, "wood", 0,
                        List.of(new BlockBehavior.Drop(
                                Identifier.parse("minecraft:melon"), 1))));
        for (String family : MelonBlocks.STEMS.keySet()) {
            BlockBehavior stem = new BlockBehavior(0.0, true, false, "plant", 0,
                    List.of(new BlockBehavior.Drop(MelonBlocks.seedsOf(family), 1)));
            for (int stage = 0; stage <= 7; stage++) {
                behaviors.put(MelonBlocks.stemStage(family, stage).identifier(), stem);
            }
        }

        // The huge-mushroom pair (Slice 40 — MushroomBlock's own rows: both
        // `setStrength(0.2F)` WOOD_SOUNDS over Material.WOOD): the drops
        // ride the DropService's own walk (getBaseDropCount's 0-2 plants).
        for (String family : MushroomBlocks.MUSHROOMS.keySet()) {
            BlockBehavior mushroom = new BlockBehavior(0.2, true, false, "wood", 0,
                    List.of(new BlockBehavior.Drop(MushroomBlocks.plantOf(family), 1)));
            for (MushroomBlocks.Variant variant : MushroomBlocks.VARIANTS) {
                behaviors.put(MushroomBlocks.state(family, variant.id()).identifier(), mushroom);
            }
        }

        // The complete door families (Slice 34 — DoorBlock's own
        // registrations: every wood door `setStrength(3.0F)` WOOD_SOUNDS,
        // the iron door `setStrength(5.0F)` Material.IRON — the material's
        // own setRequiresTool gates the iron pair to pickaxe-class drops).
        // The reference's getDropItem returns the family item from EITHER
        // half (UPPER → null, but the orphaned lower's neighborChanged
        // dropItems covers the pair: the engine's broken-cell drop model
        // nets the same one item per door — the sibling cleanup removes
        // the survivor silently). The five new woods mirror oak's rows;
        // the iron's new open/hinge states mirror its shipped five.
        for (String family : WoodDoorFamily.FAMILIES) {
            boolean iron = family.equals("iron");
            BlockBehavior door = new BlockBehavior(iron ? 5.0 : 3.0,
                    true, iron, iron ? "rock" : "wood", 0,
                    List.of(new BlockBehavior.Drop(WoodDoorFamily.doorItem(family), 1)));
            for (int meta = 0; meta < 4; meta++) {
                behaviors.put(WoodDoorFamily.lower(family, meta, false).identifier(), door);
                behaviors.put(WoodDoorFamily.lower(family, meta, true).identifier(), door);
            }
            for (boolean right : new boolean[] {false, true}) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        behaviors.put(WoodDoorFamily.upper(family, right, open, powered)
                                .identifier(), door);
                    }
                }
            }
        }
        return Map.copyOf(behaviors);
    }

    /** @return the behavior of a block type, or empty for unregistered identifiers. */
    public static java.util.Optional<BlockBehavior> of(Identifier blockIdentifier) {
        return java.util.Optional.ofNullable(BEHAVIORS.get(blockIdentifier));
    }

    /**
     * @return whether the given held item (empty hand = {@code null}) can harvest
     *         drops from the block. Blocks without a tool requirement are
     *         harvestable by hand; tool-gated blocks need a tool of the matching
     *         class whose material tier satisfies the block's harvest level
     *         (historical: breaking works regardless, it just yields nothing).
     */
    public static boolean canHarvest(BlockBehavior behavior, net.zaminmc.torch.item.ItemType held) {
        if (!behavior.requiresTool()) {
            return true;
        }
        return Tools.specOf(held)
                .map(spec -> spec.toolClass() == requiredToolClass(behavior)
                        && spec.material().harvestLevel() >= behavior.harvestLevel())
                .orElse(false);
    }

    /**
     * @return the mining speed multiplier the held item contributes to the
     *         block: the tool material's speed when its class matches the
     *         block's material, 1.0 otherwise (hand or mismatched tool).
     */
    public static double speedMultiplier(BlockBehavior behavior, net.zaminmc.torch.item.ItemType held) {
        return Tools.specOf(held)
                .filter(spec -> spec.toolClass() == requiredToolClass(behavior))
                .map(spec -> spec.material().speedMultiplier())
                .orElse(1.0);
    }

    /**
     * The dataset's material-to-tool-class mapping: pickaxes work "rock",
     * shovels work "dirt", axes work "wood", shears work leaves/wool/web.
     * Unmapped materials have no accelerating tool class.
     */
    private static ToolClass requiredToolClass(BlockBehavior behavior) {
        return switch (behavior.material() == null ? "" : behavior.material()) {
            case "rock" -> ToolClass.PICKAXE;
            case "dirt", "grass", "snow", "clay", "sand" -> ToolClass.SHOVEL;
            case "wood" -> ToolClass.AXE;
            case "leaves", "wool", "web", "plant" -> ToolClass.SHEARS;
            default -> null;
        };
    }

    /**
     * The shears durability-wear band (the tool-wear parity audit — the
     * reference's ShearsItem.mineBlock lines 20-29: the WEB, the TALLGRASS
     * block's own pair, the VINE, the TRIPWIRE, the WOOL family and every
     * leaves material; a shears dig of anything else delegates to Item's
     * own default and never wears). The DEADBUSH and the double plants sit
     * OUTSIDE the band exactly (the reference registers the band by block,
     * not by flora).
     *
     * @return whether a shears dig of the block wears one unit.
     */
    public static boolean inShearsWearBand(net.zaminmc.torch.block.BlockType broken) {
        String value = broken.identifier().value();
        if (value.equals("cobweb") || value.equals("vine")
                || value.equals("tall_grass") || value.equals("fern")) {
            return true;
        }
        // The tripwire WIRE family (the reference's Blocks.TRIPWIRE, block
        // 132 — the sixteen state-suffixed forms the behavior table
        // registers; the HOOK, block 131, is its own block and sits
        // OUTSIDE the reference's band).
        if (value.equals("tripwire")
                || (value.startsWith("tripwire_") && !value.startsWith("tripwire_hook"))) {
            return true;
        }
        BlockBehavior behavior = of(broken.identifier()).orElse(null);
        String material = behavior == null ? null : behavior.material();
        return "leaves".equals(material) || "wool".equals(material);
    }
}
