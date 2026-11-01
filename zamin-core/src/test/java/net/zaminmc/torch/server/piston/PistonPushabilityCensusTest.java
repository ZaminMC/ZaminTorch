package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.WoodDoorFamily;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * VT-016's whole-registry pin — the piston pushability table's membership
 * held to the reference's material census, every registered block at once
 * (the vine wire repair's own net idiom: the skip set is EMPTY and pinned,
 * so nothing may join the registry without a conscious classification).
 *
 * <p>THE LAW (the reference, 1.8.8): {@code Block.getPistonMoveBehavior()}
 * answers the MATERIAL's piston setter — Material.java lines 10-43 —
 * {@code setDestroyOnPistonMove()} → 1 for WATER, LAVA, LEAVES, PLANT,
 * REPLACEABLE_PLANT, FIRE, DECORATION, CORAL, SNOW_LAYER, CACTUS, PUMPKIN,
 * EGG, CAKE and COBWEB; {@code setBlocksPistonMove()} → 2 for ANVIL, PORTAL,
 * PISTON and BARRIER; plain 0 for everything else — PLUS the BLOCK-level
 * overrides: BedBlock line 175 → 1, DoorBlock line 218 → 1 (wood AND iron),
 * AbstractPressurePlateBlock line 180 → 1 (all four plates), IceBlock line
 * 72 → 0 and AbstractRailBlock line 121 → 0 (already 0 by material).</p>
 *
 * <p>THE EXEMPTIONS (pinned closed): the twenty-four piston bases (the
 * reference's canMoveBlock exempts PISTON/STICKY_PISTON explicitly before
 * the material arm — PistonBaseBlock lines 260-272 — so their material-2
 * answer never fires; the engine answers PUSH and the exemption carries
 * them identically), the barrier and the end portal (the reference's
 * getMiningTime == -1 gate fires before the material arm, lines 259-263;
 * the engine's -1-hardness arm the same). Their pistonMoveBehavior answers
 * are UNOBSERVABLE — canMoveBlock refuses them earlier in both shapes.</p>
 *
 * <p>THE GUARD: every registered block must be classified by this census —
 * an identifier the census does not know fails the test by name, so a new
 * family can never silently inherit PUSH. The classifier is written from
 * the reference side (each rule cites its material), never from the
 * engine's own table.</p>
 */
class PistonPushabilityCensusTest {

    @TempDir
    Path dataDir;

    @Test
    void everyRegisteredBlockMatchesTheReferenceMaterialCensus() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ppc" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            Collection<BlockType> registry = registryOf(server);
            PistonSystem system = pistonSystemOf(server);

            List<String> unclassified = new ArrayList<>();
            List<String> divergent = new ArrayList<>();
            List<String> exemptSeen = new ArrayList<>();
            for (BlockType type : registry) {
                String value = type.identifier().value();
                if (isExempt(type)) {
                    exemptSeen.add(value);
                    continue;
                }
                Integer expected = referenceCensus(type, value);
                if (expected == null) {
                    unclassified.add(value);
                    continue;
                }
                int actual = system.pistonMoveBehavior(type);
                if (actual != expected) {
                    divergent.add(value + " expected=" + materialOf(expected)
                            + " but was=" + materialOf(actual));
                }
            }
            // The guard: the census must classify every registered block.
            assertTrue(unclassified.isEmpty(),
                    "the reference census must classify every registered block — "
                            + "unknown identifiers must be added to the census consciously: "
                            + unclassified);
            // The pin: the engine's table must match the census everywhere.
            assertTrue(divergent.isEmpty(),
                    "VT-016: the piston pushability table diverges from the reference's "
                            + "material census (" + divergent.size() + " ids): " + divergent);
            // The exemptions are pinned closed (24 piston bases + barrier + end portal).
            assertEquals(26, exemptSeen.size(),
                    "the exemption set is closed: the 24 piston bases, the barrier and the "
                            + "end portal — anything else joining it must be a conscious "
                            + "correction (observed: " + exemptSeen + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** The engine's frozen block registry, read reflectively (the boot
     *  chain's own assembled vocabulary — every block the world can hold). */
    private static Collection<BlockType> registryOf(EngineServer server) throws Exception {
        Field registryField = EngineServer.class.getDeclaredField("blockRegistry");
        registryField.setAccessible(true);
        Object registry = registryField.get(server);
        Field allField = registry.getClass().getDeclaredField("all");
        allField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Collection<BlockType> all = (Collection<BlockType>) allField.get(registry);
        return all;
    }

    /** The booted server's own PistonSystem (the live table). */
    private static PistonSystem pistonSystemOf(EngineServer server) throws Exception {
        Field pistonField = EngineServer.class.getDeclaredField("pistonSystem");
        pistonField.setAccessible(true);
        return (PistonSystem) pistonField.get(server);
    }

    /** The closed exemption set: the piston bases (the reference's explicit
     *  PISTON/STICKY_PISTON arm — isPiston excludes the head and carrier),
     *  the barrier and the end portal (both -1 hardness — the reference's
     *  own double gate fires before the material arm). */
    private static boolean isExempt(BlockType type) {
        String value = type.identifier().value();
        return PistonBlocks.isPiston(type)
                || value.equals("barrier")
                || value.equals("end_portal");
    }

    /**
     * The reference's material census (1.8.8), written from the reference
     * side: each rule cites the Material (or the block override) it rides.
     *
     * @return 2 for the BLOCK materials, 1 for the DESTROY materials and
     *         overrides, 0 for plain PUSH, or null when the census does not
     *         know the identifier (the loud guard fails on it).
     */
    private static Integer referenceCensus(BlockType type, String value) {
        // ---- setBlocksPistonMove (Material.java lines 10/34/42) ----
        // ANVIL: the twelve anvil states. PORTAL: the nether pair. PISTON:
        // the head and the moving carrier (the bases are exempt above).
        if (value.startsWith("anvil")
                || value.startsWith("nether_portal")
                || value.startsWith("piston_head")
                || value.startsWith("moving_piston")) {
            return 2;
        }
        // ---- the block-level overrides (== 1) ----
        // DoorBlock line 218: every door, wood AND iron (either half).
        if (WoodDoorFamily.isDoor(type)) {
            return 1;
        }
        // BedBlock line 175: the bed's exact shapes (bedrock excluded — it
        // is Material.ROCK and rides the -1-hardness gate). AbstractPres-
        // surePlateBlock line 180: all four plates, every power state.
        if (value.equals("bed") || value.startsWith("bed_")
                || value.startsWith("stone_pressure_plate")
                || value.startsWith("wooden_pressure_plate")
                || value.startsWith("light_weighted_pressure_plate")
                || value.startsWith("heavy_weighted_pressure_plate")) {
            return 1;
        }
        // ---- Material.DECORATION (Material.java line 20) ----
        // The wiring family, the ladder, the tripwire family, the pot and
        // its thirteen potted forms.
        if (value.equals("torch")
                || value.startsWith("redstone_wire")
                || value.startsWith("redstone_torch")
                || value.startsWith("unlit_redstone_torch")
                || value.startsWith("repeater_")
                || value.startsWith("powered_repeater_")
                || value.startsWith("comparator_")
                || value.startsWith("powered_comparator_")
                || value.startsWith("lever_")
                || value.startsWith("stone_button_")
                || value.startsWith("wooden_button_")
                || value.startsWith("ladder")
                || value.startsWith("tripwire")
                || value.equals("flower_pot")
                || value.startsWith("potted_")) {
            return 1;
        }
        // ---- Material.PLANT (line 14) ----
        // The flowers, the tufts, the crops' stages, the stems, the gourds'
        // facing states, the cane, the cocoa ages/facings, the wart stages,
        // the lily, the small mushrooms (the huge mushroom blocks are
        // Material.WOOD — PUSH), the saplings (<wood>_sapling suffix).
        if (value.startsWith("dandelion")
                || value.startsWith("poppy")
                || value.startsWith("blue_orchid")
                || value.startsWith("allium")
                || value.startsWith("houstonia")
                || value.startsWith("red_tulip")
                || value.startsWith("orange_tulip")
                || value.startsWith("white_tulip")
                || value.startsWith("pink_tulip")
                || value.startsWith("oxeye")
                || value.startsWith("tall_grass")
                || value.startsWith("fern")
                || value.contains("_sapling")
                || value.startsWith("wheat_stage")
                || value.startsWith("carrots_stage")
                || value.startsWith("potatoes_stage")
                || value.startsWith("melon")
                || value.startsWith("pumpkin")
                || value.startsWith("lit_pumpkin")
                || value.equals("sugar_cane")
                || value.startsWith("cocoa")
                || value.startsWith("nether_wart")
                || value.equals("waterlily")
                || value.equals("brown_mushroom")
                || value.equals("red_mushroom")) {
            return 1;
        }
        // ---- Material.REPLACEABLE_PLANT (line 15) ----
        // The bush, the vine's sixteen face states, the double plants.
        if (value.startsWith("dead_bush")
                || value.startsWith("vine")
                || value.startsWith("sunflower")
                || value.startsWith("lilac")
                || value.startsWith("double_grass")
                || value.startsWith("double_fern")
                || value.startsWith("rose")
                || value.startsWith("peony")) {
            return 1;
        }
        // ---- the remaining destroy materials ----
        // CACTUS (30), SNOW_LAYER (28), EGG (33 — the dragon egg block),
        // CAKE (35 — every bite state), COBWEB (38-41), FIRE (18 — the
        // age ladder), LEAVES (13 — the six woods and their states),
        // WATER/LAVA (11-12 — the source cells and the whole
        // flowing_/falling_/still_ state vocabulary, LiquidMaterial's own
        // setter at LiquidMaterial.java line 7).
        if (value.equals("cactus")
                || value.equals("snow_layer")
                || value.equals("dragon_egg")
                || value.startsWith("cake")
                || value.equals("cobweb")
                || value.equals("fire")
                || value.startsWith("fire_age")
                || value.contains("_leaves")
                || value.equals("water")
                || value.equals("lava")
                || value.startsWith("flowing_")
                || value.startsWith("falling_")
                || value.startsWith("still_")) {
            return 1;
        }
        // ---- plain PUSH (0) — the families the census knows by name ----
        // AIR (isAir's own gate), STONE/DIRT/GRASS/WOOD/SPONGE/WOOL/SAND/
        // CLAY/IRON/PISTON-exempt... the building stones, the ores, the
        // dirt band (grass_block — Material.GRASS line 4, no setter), the
        // woods (logs, planks, fences, gates, stairs, slabs, the trapdoors
        // — Material.WOOD/IRON), the carpets (CARPET line 22), the glasses,
        // the wools, ICE (line 27 — override 0) and PACKED_ICE, SNOW
        // (line 29 — the full block), the rails (override 0), the slime
        // block (CLAY line 26), TNT (line 24), the mushroom BLOCKS (WOOD),
        // the infested family (CLAY), obsidian/bedrock (the -1 gate), the
        // world stones (quartz, prismarine, sandstone, red sandstone,
        // nether brick, end stone, ...), the entity-backed containers
        // (chests, furnaces, dispensers, droppers, hoppers, brewing
        // stands, banners, skulls, beacons, enchanting tables, jukeboxes,
        // note blocks, spawners, command blocks, daylight detectors —
        // their immovability rides the block-entity arm of canMoveBlock,
        // not the material; the material-level answer is PUSH and that
        // arm is VT-019's own lane), and the end portal frames (STONE).
        if (value.equals("air")
                || value.equals("stone") || value.startsWith("stone_brick")
                || value.startsWith("stone_slab") || value.startsWith("cobblestone")
                || value.startsWith("mossy_cobblestone")
                || value.equals("dirt") || value.equals("coarse_dirt")
                || value.equals("grass_block") || value.equals("podzol") || value.equals("mycelium")
                || value.equals("farmland") || value.equals("farmland_wet")
                || value.equals("gravel") || value.equals("sand") || value.equals("red_sand")
                || value.equals("clay") || value.equals("brick_block")
                || value.equals("snow") || value.equals("ice") || value.equals("packed_ice")
                || value.equals("obsidian") || value.equals("bedrock")
                || value.equals("glowstone") || value.equals("sea_lantern")
                || value.equals("sponge") || value.equals("wet_sponge")
                || value.equals("tnt") || value.equals("slime_block")
                || value.equals("netherrack") || value.equals("soul_sand")
                || value.equals("end_stone") || value.equals("bookshelf")
                || value.equals("crafting_table") || value.equals("glass")
                || value.equals("glass_pane") || value.equals("iron_bars")
                || value.equals("iron_block") || value.equals("iron_trapdoor")
                || value.startsWith("iron_trapdoor_")
                || value.equals("wool") || value.endsWith("_wool")
                || value.startsWith("carpet_")
                || value.startsWith("stained_glass") || value.startsWith("stained_clay")
                || value.startsWith("hardened_clay")
                || value.endsWith("_log") || value.endsWith("_planks")
                || value.endsWith("_fence")
                || value.contains("_fence_gate") || value.contains("_stairs_")
                || value.equals("nether_bricks")
                || value.contains("_slab")
                || value.endsWith("_trapdoor") || value.startsWith("trapdoor")) {
            return 0;
        }
        if (isKnownPushFamily(value)) {
            return 0;
        }
        return null; // the loud guard: unknown to the census
    }

    /** The push families with irregular id shapes (the census's second
     *  half — each cited with its plain material, no piston setter). */
    private static boolean isKnownPushFamily(String value) {
        // The ores and their blocks (STONE/IRON/GOLD...), the quartz and
        // sandstone bands, the prismarine quartet, the red sandstones.
        if (value.endsWith("_ore") || value.endsWith("_block")
                || value.startsWith("quartz") || value.startsWith("sandstone")
                || value.startsWith("smooth_") || value.startsWith("chiseled_")
                || value.startsWith("cracked_") || value.startsWith("mossy_")
                || value.startsWith("prismarine") || value.startsWith("red_sandstone")
                || value.startsWith("double_stone_") || value.startsWith("double_sandstone_")
                || value.startsWith("double_acacia_") || value.startsWith("double_birch_")
                || value.startsWith("double_dark_oak_") || value.startsWith("double_jungle_")
                || value.startsWith("double_spruce_") || value.startsWith("double_cobblestone_")) {
            return true;
        }
        // The rails (AbstractRailBlock line 121 — override 0) and their
        // powered/ascending shapes.
        if (value.equals("rail") || value.startsWith("rail_")
                || value.startsWith("golden_rail") || value.startsWith("detector_rail")
                || value.startsWith("activator_rail")) {
            return true;
        }
        // The huge mushroom blocks (Material.WOOD) and the infested family
        // (CLAY). The end portal frames (STONE).
        if (value.startsWith("brown_mushroom_block") || value.startsWith("red_mushroom_block")
                || value.startsWith("infested_") || value.startsWith("end_portal_frame")) {
            return true;
        }
        // The redstone family's plain members (ROCK/IRON — no setter):
        // the ore, the block, the lamp pair.
        if (value.equals("redstone_ore") || value.equals("lit_redstone_ore")
                || value.equals("redstone_block") || value.equals("redstone_lamp")
                || value.equals("lit_redstone_lamp")) {
            return true;
        }
        // The entity-backed containers' ids (the material-level answer is
        // PUSH; the block-entity arm of canMoveBlock — VT-019's lane — is
        // the immovability that matters for them): the chest quartet, the
        // furnace pair, the dispenser/dropper facings, the hopper band,
        // the brewing stand bottles, the banner pair, the skulls, the
        // beacon, the enchanting table, the jukebox pair, the note block,
        // the spawner, the command block pair, the daylight detectors,
        // the cauldron levels.
        if (value.equals("chest") || value.startsWith("chest_")
                || value.equals("trapped_chest") || value.startsWith("trapped_chest_")
                || value.equals("ender_chest") || value.startsWith("ender_chest_")
                || value.equals("furnace") || value.startsWith("furnace_")
                || value.equals("dispenser") || value.startsWith("dispenser_")
                || value.equals("dropper") || value.startsWith("dropper_")
                || value.equals("hopper") || value.startsWith("hopper_")
                || value.equals("brewing_stand") || value.startsWith("brewing_stand_")
                || value.equals("standing_banner") || value.startsWith("standing_banner_")
                || value.equals("wall_banner") || value.startsWith("wall_banner_")
                || value.equals("skull") || value.startsWith("skull_")
                || value.equals("beacon") || value.equals("enchanting_table")
                || value.equals("jukebox") || value.equals("jukebox_record")
                || value.equals("noteblock") || value.equals("mob_spawner")
                || value.equals("command_block") || value.equals("command_block_triggered")
                || value.startsWith("daylight_detector_") || value.equals("cauldron")
                || value.startsWith("cauldron_")) {
            return true;
        }
        // The signs (WOOD — the entity arm is their immovability) and the
        // wall forms.
        if (value.equals("sign") || value.startsWith("sign_")
                || value.equals("wall_sign") || value.startsWith("wall_sign_")) {
            return true;
        }
        // The fence-gate band's bare family heads (oak_fence_gate and the
        // six woods' heads — the powered/open state forms ride the
        // contains-rule above).
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            if (value.equals(wood + "_fence_gate")) {
                return true;
            }
        }
        return false;
    }

    /** The material name for a behavior value (the failure messages' own
     *  vocabulary). */
    private static String materialOf(int behavior) {
        return switch (behavior) {
            case PistonSystem.BEHAVIOR_DESTROY -> "DESTROY(1)";
            case PistonSystem.BEHAVIOR_BLOCK -> "BLOCK(2)";
            default -> "PUSH(0)";
        };
    }
}
