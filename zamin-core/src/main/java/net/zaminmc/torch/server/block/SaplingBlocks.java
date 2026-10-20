package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The sapling-growth family (the 2026-10-21 audit's VIII2 — the reference's
 * {@code SaplingBlock} 1:1): the STAGE bit the fresh sapling states never
 * carried, the random-tick grow roll, the two-stage tryGrow, and the
 * variant's feature dispatch with the 2×2 big-form checks.
 *
 * <p><b>The band</b>: the engine's per-wood {@code <wood>_sapling} id IS
 * the stage-0 form (the placement form — the item's damage carries the
 * variant alone); one derived state per wood carries the stage-1 "ready"
 * form ({@code <wood>_sapling_ready}, the wire's metadata bit 8 —
 * SaplingBlock.getMetadataFromState lines 197-204: the variant in the low
 * trio, {@code STAGE << 3} in bit 8).</p>
 *
 * <p><b>The roll</b> (SaplingBlock.tick lines 44-57 over the random tick's
 * own delegation): the light gate ({@code getRawBrightness(pos.up()) >= 9})
 * and the 1-in-7 roll arm the growth — a ready sapling rolls the tree, a
 * fresh one flips its stage bit first ({@code setBlockState(pos,
 * state.next(STAGE), 4)} — the engine's publish-with-quirk, the fire
 * slice's own named convention).</p>
 *
 * <p><b>The dispatch</b> (grow lines 59-129): the oak's 1-in-10 fancy roll;
 * the spruce/jungle 2×2 cluster scan ({@code i} and {@code j} walking 0
 * then -1 — the cluster's own corner) with the giant forms on a hit and
 * the plain form at the home cell on a miss; the birch's plain feature;
 * the acacia's; the dark oak's REQUIRED cluster (a lone dark-oak sapling
 * never grows); the cluster's four cells clear to air before the feature
 * places and restore their sapling states when the placement refuses
 * (lines 130-158).</p>
 *
 * <p><b>The bone-meal arm</b> (the Fertilizable pair — canBeFertilized's
 * {@code 0.45} roll at the item's own consult, grow = tryGrow): the
 * instant form bypasses the light gate and the 1-in-7 entirely.</p>
 */
public final class SaplingBlocks {

    private SaplingBlocks() {
    }

    // ------------------------------------------------------------- the band

    /** The stage-1 suffix (the wire's metadata bit 8). */
    static final String READY_SUFFIX = "_ready";

    private static final Map<Identifier, EngineBlockType> TYPES = buildTypes();

    private static Map<Identifier, EngineBlockType> buildTypes() {
        Map<Identifier, EngineBlockType> types = new HashMap<>();
        // The six derived ready states over the fresh members (the legacy
        // nibble carries the variant | the stage bit — the reference's own
        // getMetadataFromState band).
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            Identifier id = stateId(variant, true);
            types.put(id, new EngineBlockType(id,
                    WoodVariantBlocks.WOODS.get(variant) + " Sapling",
                    WoodVariantBlocks.wireMetaOf("sapling", variant) | 8));
        }
        return Map.copyOf(types);
    }

    /** The sapling state's identifier (the stage-0 form keeps the family name). */
    public static Identifier stateId(int variant, boolean ready) {
        if (!ready) {
            return WoodVariantBlocks.saplingBlock(variant);
        }
        return Identifier.parse("minecraft:" + WoodVariantBlocks.WOODS.get(variant)
                + "_sapling" + READY_SUFFIX);
    }

    /** The registered band member (the stage-0 form is the family's own). */
    public static BlockType stateOf(int variant, boolean ready) {
        if (!ready) {
            return saplingType(variant);
        }
        return TYPES.get(stateId(variant, true));
    }

    /** The six derived ready states (registration; the fresh members ride WoodVariantBlocks). */
    public static List<EngineBlockType> familyBlocks() {
        return List.copyOf(TYPES.values());
    }

    /** Registers the derived states into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : TYPES.values()) {
            builder.register(type);
        }
        return builder;
    }

    /** @return whether the identifier is the ready (stage-1) form. */
    public static boolean isReady(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && id.value().endsWith("_sapling" + READY_SUFFIX);
    }

    // ------------------------------------------------------------- the laws

    /** The light gate's band (SaplingBlock.tick line 46: brightness >= 9). */
    static final int GROW_LIGHT = 9;

    /**
     * The random-tick roll (SaplingBlock.tick lines 44-57): the light
     * gate at the cell above, then the 1-in-7 arm — a fresh sapling
     * flips to ready, a ready one rolls its tree. The light consult is
     * the brighter of block and sky light (the engine's own grass-band
     * convention — the reference's getRawBrightness).
     */
    public static void randomTick(EngineWorld world, BlockPosition pos, BlockType type,
                                  Random random) {
        int variant = WoodVariantBlocks.variantOfSapling(type.identifier());
        if (variant < 0) {
            return;
        }
        BlockPosition above = pos.offset(0, 1, 0);
        int brightness = Math.max(world.blockLightAt(above), world.skyLightAt(above));
        if (brightness >= GROW_LIGHT && random.nextInt(7) == 0) {
            tryGrow(world, pos, variant, isReady(type.identifier()), random);
        }
    }

    /**
     * The two-stage grow (tryGrow lines 49-56): a fresh sapling flips its
     * stage bit; a ready one grows the tree.
     */
    public static void tryGrow(EngineWorld world, BlockPosition pos, int variant,
                               boolean ready, Random random) {
        if (!ready) {
            world.setBlock(pos, stateOf(variant, true));
        } else {
            grow(world, pos, variant, random);
        }
    }

    /**
     * The feature dispatch (grow lines 59-129): the variant's feature with
     * the 2×2 cluster checks for the spruce/jungle/dark-oak giant forms,
     * the cluster clear-and-restore around the placement, and the plain
     * features at the home cell on a cluster miss. A lone dark-oak
     * sapling never grows (line 118's bare return).
     */
    public static void grow(EngineWorld world, BlockPosition pos, int variant, Random random) {
        BlockType sapling = world.getBlock(pos);
        Dispatch dispatch = dispatch(world, pos, variant, random);
        if (dispatch == null) {
            return;
        }
        placeWithClearAndRestore(world, pos, dispatch.i(), dispatch.j(), variant,
                dispatch.feature(), dispatch.cluster(), sapling, random);
    }

    /** One dispatch's answer: the cluster corner (i, j) — (0, 0) for the
     * plain forms AND for a cluster at the home corner — the feature, and
     * whether the 2×2 cluster HIT (the four-cell clear-and-restore rides
     * the hit, never the corner's own value — the reference's flag). */
    private record Dispatch(int i, int j, TreeFeatures.Feature feature, boolean cluster) {
    }

    /** The variant's own dispatch table (grow's switch, 1:1). */
    private static Dispatch dispatch(EngineWorld world, BlockPosition pos, int variant,
                                     Random random) {
        return switch (variant) {
            case 0 -> new Dispatch(0, 0, random.nextInt(10) == 0 // OAK — the 1-in-10 fancy roll
                    ? TreeFeatures.largeOak(random)
                    : TreeFeatures.oak(random), false);
            case 1 -> { // SPRUCE — the 2×2 cluster scan
                int[] corner = clusterCorner(world, pos, variant);
                if (corner == null) {
                    yield new Dispatch(0, 0, TreeFeatures.spruce(random), false);
                }
                yield new Dispatch(corner[0], corner[1],
                        TreeFeatures.giantSpruce(random, random.nextBoolean()), true);
            }
            case 2 -> new Dispatch(0, 0, TreeFeatures.birch(random), false); // BIRCH
            case 3 -> { // JUNGLE — the 2×2 cluster or the tall small form
                int[] corner = clusterCorner(world, pos, variant);
                if (corner == null) {
                    yield new Dispatch(0, 0, TreeFeatures.jungleSmall(random), false);
                }
                yield new Dispatch(corner[0], corner[1], TreeFeatures.giantJungle(random), true);
            }
            case 4 -> new Dispatch(0, 0, TreeFeatures.acacia(random), false); // ACACIA
            case 5 -> { // DARK_OAK — the cluster REQUIRED
                int[] corner = clusterCorner(world, pos, variant);
                if (corner == null) {
                    yield null; // a lone dark-oak sapling never grows
                }
                yield new Dispatch(corner[0], corner[1], TreeFeatures.darkOak(random), true);
            }
            default -> null;
        };
    }

    /**
     * The 2×2 cluster scan (grow's labeled loops, lines 65-82/86-102/106-117):
     * {@code i} walks 0 then -1, {@code j} inside it the same — the FIRST
     * matching corner in that scan order answers; null on no cluster.
     */
    private static int[] clusterCorner(EngineWorld world, BlockPosition pos, int variant) {
        for (int i = 0; i >= -1; i--) {
            for (int j = 0; j >= -1; j--) {
                if (isCluster(world, pos, i, j, variant)) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    /**
     * The cluster's clear-and-restore around the placement (grow lines
     * 130-158): the 2×2 cells (or the home cell alone on a plain form)
     * clear to air — the reference's flag-4 writes — then the feature
     * places; a refused placement restores the sapling states it cleared.
     */
    private static void placeWithClearAndRestore(EngineWorld world, BlockPosition pos,
                                                 int i, int j, int variant,
                                                 TreeFeatures.Feature feature,
                                                 boolean cluster,
                                                 BlockType sapling, Random random) {
        BlockType air = world.airType();
        BlockType freshSapling = stateOf(variant, false);
        BlockType readySapling = stateOf(variant, true);
        if (cluster) {
            world.setBlock(pos.offset(i, 0, j), air);
            world.setBlock(pos.offset(i + 1, 0, j), air);
            world.setBlock(pos.offset(i, 0, j + 1), air);
            world.setBlock(pos.offset(i + 1, 0, j + 1), air);
        } else {
            world.setBlock(pos, air);
        }
        BlockPosition anchor = pos.offset(i, 0, j);
        if (!feature.place(world, random, anchor)) {
            if (cluster) {
                world.setBlock(pos.offset(i, 0, j), sapling);
                world.setBlock(pos.offset(i + 1, 0, j), isReady(sapling.identifier())
                        ? readySapling : freshSapling);
                world.setBlock(pos.offset(i, 0, j + 1), isReady(sapling.identifier())
                        ? readySapling : freshSapling);
                world.setBlock(pos.offset(i + 1, 0, j + 1), isReady(sapling.identifier())
                        ? readySapling : freshSapling);
            } else {
                world.setBlock(pos, sapling);
            }
        }
    }

    /**
     * The 2×2 cluster consult (isSapling lines 160-172): all four cells of
     * the corner at {@code (i, j)} carry the same variant's sapling —
     * either stage.
     */
    private static boolean isCluster(EngineWorld world, BlockPosition pos,
                                     int i, int j, int variant) {
        return isSaplingOf(world, pos.offset(i, 0, j), variant)
                && isSaplingOf(world, pos.offset(i + 1, 0, j), variant)
                && isSaplingOf(world, pos.offset(i, 0, j + 1), variant)
                && isSaplingOf(world, pos.offset(i + 1, 0, j + 1), variant);
    }

    /** One cell's own consult (the block IS the sapling family, the variant matching). */
    private static boolean isSaplingOf(EngineWorld world, BlockPosition pos, int variant) {
        return WoodVariantBlocks.variantOfSapling(world.getBlock(pos).identifier()) == variant;
    }

    // --------------------------------------------------------- the fresh type

    /** The cached fresh sapling types (the canonical registered members). */
    private static final List<EngineBlockType> FRESH_TYPES = buildFreshTypes();

    private static List<EngineBlockType> buildFreshTypes() {
        java.util.ArrayList<EngineBlockType> types = new java.util.ArrayList<>();
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            types.add(new EngineBlockType(WoodVariantBlocks.saplingBlock(variant),
                    WoodVariantBlocks.displayOf(variant) + " Sapling",
                    WoodVariantBlocks.wireMetaOf("sapling", variant)));
        }
        return List.copyOf(types);
    }

    /** The stage-0 form's canonical type (the registration's own member). */
    public static BlockType saplingType(int variant) {
        return FRESH_TYPES.get(variant);
    }
}
