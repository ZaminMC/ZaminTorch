package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The leaf-decay family (the 2026-10-21 audit's VIII3 — the reference's
 * {@code AbstractLeavesBlock} 1:1): the DECAYABLE/CHECK_DECAY model the
 * fresh leaf states never carried. The wire band (LeavesBlock
 * .getStateFromMetadata lines 91-101): the variant in the low pair, bit 4
 * (metadata +4) the PERSISTENT form ({@code !DECAYABLE} — never decays),
 * bit 8 (metadata +8) the CHECK_DECAY flag (the decay poll armed).
 *
 * <p><b>The band</b>: the engine's per-wood {@code <wood>_leaves} id IS
 * the fresh form (DECAYABLE + !CHECK_DECAY — the placement and worldgen
 * form); three derived states per wood carry the other combos, the
 * fire-AGE precedent's own shape (a derived-state band over the family
 * owner, {@link WoodVariantBlocks}).</p>
 *
 * <p><b>The two fans</b> (the reference's {@code onRemoved} pair — the
 * leaf's own 3³ box at AbstractLeavesBlock lines 46-66, the log's 5³ box
 * at AbstractLogBlock lines 23-36): when a leaf or a log cell departs,
 * every leaf in the box with CHECK_DECAY unset gets it set — the
 * tree-chop trigger, the moment a felled trunk arms its canopy. The
 * writes are the reference's flag-4 form ({@code setBlockState(pos,
 * state.set(CHECK_DECAY, true), 4)} — no neighbor reactions); the
 * engine's commit path always publishes (the fire slice's own named
 * quirk), and the fan's writes never re-enter the fan (the
 * {@link #inFanWrite} guard — the reference's flag-4 replace calls no
 * onRemoved at all).</p>
 *
 * <p><b>The decay poll</b> (the reference's {@code tick} — the random
 * tick's own body through Block.randomTick's delegation, lines 434-436):
 * a leaf with CHECK_DECAY && DECAYABLE runs the 9³ distance walk — the
 * 32³ scratch array, logs seeded 0, leaves -2, everything else -1, the
 * four-wave spread through the -2 cells, and the center's own verdict:
 * reachable (value &gt;= 0) clears the flag, unreachable
 * ({@code breakLeaves}) drops the sapling/apple walk at fortune 0 and
 * removes the cell. The PERSISTENT form never polls (DECAYABLE false);
 * the worldgen-placed fresh form arms only through the fans.</p>
 */
public final class LeafDecayBlocks {

    private LeafDecayBlocks() {
    }

    // ------------------------------------------------------------- the band

    /** The CHECK_DECAY suffix (the wire's metadata +8 form). */
    static final String CHECK_SUFFIX = "_check";
    /** The PERSISTENT suffix (the wire's metadata +4 form — !DECAYABLE). */
    static final String PERSISTENT_SUFFIX = "_persistent";

    private static final Map<Identifier, EngineBlockType> TYPES = buildTypes();

    private static Map<Identifier, EngineBlockType> buildTypes() {
        Map<Identifier, EngineBlockType> types = new HashMap<>();
        // The eighteen derived states: three per wood over the fresh member
        // (WoodVariantBlocks owns the fresh member's own registration). The
        // legacy metadata nibble carries the whole wire form — the variant
        // pair | persistent bit 4 | check bit 8 (the reference's own
        // getMetadataFromState, LeavesBlock lines 96-104).
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            int base = WoodVariantBlocks.wireMetaOf("leaves", variant);
            types.put(stateId(variant, false, true),
                    new EngineBlockType(stateId(variant, false, true),
                            WoodVariantBlocks.WOODS.get(variant) + " Leaves", base | 8));
            types.put(stateId(variant, true, false),
                    new EngineBlockType(stateId(variant, true, false),
                            WoodVariantBlocks.WOODS.get(variant) + " Leaves", base | 4));
            types.put(stateId(variant, true, true),
                    new EngineBlockType(stateId(variant, true, true),
                            WoodVariantBlocks.WOODS.get(variant) + " Leaves", base | 12));
        }
        return Map.copyOf(types);
    }

    /** The decay state's identifier over the bits (the fresh form keeps the plain family name). */
    public static Identifier stateId(int variant, boolean persistent, boolean check) {
        String wood = WoodVariantBlocks.WOODS.get(variant);
        if (persistent && check) {
            return Identifier.parse("minecraft:" + wood + "_leaves" + PERSISTENT_SUFFIX + CHECK_SUFFIX);
        }
        if (persistent) {
            return Identifier.parse("minecraft:" + wood + "_leaves" + PERSISTENT_SUFFIX);
        }
        if (check) {
            return Identifier.parse("minecraft:" + wood + "_leaves" + CHECK_SUFFIX);
        }
        return WoodVariantBlocks.leavesBlock(variant);
    }

    /** The registered band member for the bits (the fresh form is the
     *  family's own canonical member). */
    public static BlockType stateOf(int variant, boolean persistent, boolean check) {
        if (!persistent && !check) {
            return WoodVariantBlocks.leafType(variant);
        }
        return TYPES.get(stateId(variant, persistent, check));
    }

    /**
     * The decay bits of a leaf-family identifier: bit 0 set = persistent
     * (metadata +4), bit 1 set = check (metadata +8); -1 for a non-member.
     */
    public static int decayBitsOf(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return -1;
        }
        String value = id.value();
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            String base = WoodVariantBlocks.WOODS.get(variant) + "_leaves";
            if (value.equals(base)) {
                return 0;
            }
            if (value.equals(base + CHECK_SUFFIX)) {
                return 2;
            }
            if (value.equals(base + PERSISTENT_SUFFIX)) {
                return 1;
            }
            if (value.equals(base + PERSISTENT_SUFFIX + CHECK_SUFFIX)) {
                return 3;
            }
        }
        return -1;
    }

    /** @return whether the identifier is any leaf state — the fresh form or a decay-band member. */
    public static boolean isLeafState(Identifier id) {
        return decayBitsOf(id) >= 0;
    }

    /** The eighteen derived states (registration; the fresh members ride WoodVariantBlocks). */
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

    // -------------------------------------------------------- the decay poll

    /** The BFS bound (AbstractLeavesBlock line 70: {@code int i = 4}). */
    static final int DISTANCE = 4;

    /** The scratch array's own half-width (line 74: {@code int l1 = j1 / 2} over j1 = 32). */
    private static final int SCRATCH_HALF = 16;
    private static final int SCRATCH_WIDTH = 32;
    private static final int SCRATCH_PLANE = SCRATCH_WIDTH * SCRATCH_WIDTH;

    /**
     * The decay poll (AbstractLeavesBlock.tick lines 68-161, 1:1): the
     * CHECK_DECAY && DECAYABLE gate, the loaded-area consult (the 5-margin
     * box — the reference's isAreaLoaded), the 9³ census (logs 0, leaves
     * -2, else -1), the four-wave spread, and the center's verdict — the
     * flag clear (flag 4, the engine's publish-with-quirk) or
     * {@code breakLeaves} (the fortune-0 drop walk + the removal).
     */
    public static void decayTick(EngineWorld world, BlockPosition pos, BlockType type,
                                 Random random, ItemEntityManager itemEntities,
                                 DropService drops) {
        int bits = decayBitsOf(type.identifier());
        if (bits < 0) {
            return;
        }
        boolean persistent = (bits & 1) != 0;
        boolean check = (bits & 2) != 0;
        if (!check || persistent) {
            return; // the persistent forms never poll; an unarmed leaf has nothing to do
        }
        int variant = WoodVariantBlocks.variantOfLeaf(type.identifier());
        int i = DISTANCE;
        int j = i + 1;
        int x = pos.x();
        int y = pos.y();
        int z = pos.z();
        if (!areaLoaded(world, pos, j)) {
            return; // the reference's isAreaLoaded gate — the poll defers
        }
        int[] decayRegion = new int[SCRATCH_WIDTH * SCRATCH_WIDTH * SCRATCH_WIDTH];
        int l1 = SCRATCH_HALF;
        int k1 = SCRATCH_PLANE;
        int j1 = SCRATCH_WIDTH;
        for (int i2 = -i; i2 <= i; i2++) {
            for (int j2 = -i; j2 <= i; j2++) {
                for (int k2 = -i; k2 <= i; k2++) {
                    BlockType scanned = world.getBlock(
                            new BlockPosition(x + i2, y + j2, z + k2));
                    Identifier id = scanned.identifier();
                    if (WoodVariantBlocks.isLog(id)) {
                        decayRegion[(i2 + l1) * k1 + (j2 + l1) * j1 + k2 + l1] = 0;
                    } else if (isLeafState(id)) {
                        decayRegion[(i2 + l1) * k1 + (j2 + l1) * j1 + k2 + l1] = -2;
                    } else {
                        decayRegion[(i2 + l1) * k1 + (j2 + l1) * j1 + k2 + l1] = -1;
                    }
                }
            }
        }
        for (int i3 = 1; i3 <= 4; i3++) {
            for (int j3 = -i; j3 <= i; j3++) {
                for (int k3 = -i; k3 <= i; k3++) {
                    for (int l3 = -i; l3 <= i; l3++) {
                        if (decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1] == i3 - 1) {
                            if (decayRegion[(j3 + l1 - 1) * k1 + (k3 + l1) * j1 + l3 + l1] == -2) {
                                decayRegion[(j3 + l1 - 1) * k1 + (k3 + l1) * j1 + l3 + l1] = i3;
                            }
                            if (decayRegion[(j3 + l1 + 1) * k1 + (k3 + l1) * j1 + l3 + l1] == -2) {
                                decayRegion[(j3 + l1 + 1) * k1 + (k3 + l1) * j1 + l3 + l1] = i3;
                            }
                            if (decayRegion[(j3 + l1) * k1 + (k3 + l1 - 1) * j1 + l3 + l1] == -2) {
                                decayRegion[(j3 + l1) * k1 + (k3 + l1 - 1) * j1 + l3 + l1] = i3;
                            }
                            if (decayRegion[(j3 + l1) * k1 + (k3 + l1 + 1) * j1 + l3 + l1] == -2) {
                                decayRegion[(j3 + l1) * k1 + (k3 + l1 + 1) * j1 + l3 + l1] = i3;
                            }
                            if (decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1 - 1] == -2) {
                                decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1 - 1] = i3;
                            }
                            if (decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1 + 1] == -2) {
                                decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1 + 1] = i3;
                            }
                        }
                    }
                }
            }
        }
        int center = decayRegion[l1 * k1 + l1 * j1 + l1];
        if (center >= 0) {
            // The flag clear (line 144's flag-4 write): the engine's commit
            // path always publishes — the fire slice's own named quirk.
            world.setBlock(pos, stateOf(variant, false, false));
        } else {
            breakLeaves(world, pos, type, itemEntities, drops);
        }
    }

    /**
     * The reference's {@code breakLeaves} (lines 165-168): the drop walk at
     * luck 0 / fortune 0 — the bare-hand sapling band (1/20, the jungle
     * 1/40) and the oak's apple at 1/200 — then the removal. The drops
     * land through the engine's own {@link DropService} leaf branch (the
     * same walk a hand break rolls) at the cell's own position.
     */
    private static void breakLeaves(EngineWorld world, BlockPosition pos, BlockType leaf,
                                    ItemEntityManager itemEntities, DropService drops) {
        for (net.zaminmc.torch.item.ItemStack stack : drops.dropsFor(leaf, null, false, 0)) {
            itemEntities.spawnDropAtBlock(
                    new net.zaminmc.torch.util.Position(
                            pos.x(), pos.y(), pos.z()),
                    stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }
        world.setBlock(pos, world.airType());
    }

    /** The loaded-area consult (the reference's isAreaLoaded over the box:
     *  every chunk column the box spans must be loaded). */
    private static boolean areaLoaded(EngineWorld world, BlockPosition center, int margin) {
        int minChunkX = Math.floorDiv(center.x() - margin, 16);
        int maxChunkX = Math.floorDiv(center.x() + margin, 16);
        int minChunkZ = Math.floorDiv(center.z() - margin, 16);
        int maxChunkZ = Math.floorDiv(center.z() + margin, 16);
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(new net.zaminmc.torch.block.ChunkPosition(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    // -------------------------------------------------------------- the fans

    /**
     * The two onRemoved fans as one world listener (the leaf's 3³ box —
     * AbstractLeavesBlock lines 46-66; the log's 5³ box — AbstractLogBlock
     * lines 23-36): when a leaf or a log departs a cell, every leaf in the
     * box with CHECK_DECAY unset gets it set.
     *
     * <p><b>The block-identity gate</b> (the reference's own law,
     * WorldChunk.setBlockState line 502: {@code block1 != block} — the old
     * block's onRemoved fires only when the BLOCK itself changes, never on
     * a state flip within the same block): the engine's flattened model
     * carries each state band member as its own id, so the family gate
     * restores the reference's law — a decay-bit flip within the leaf
     * family never fires the fan (the clear write and the fan's own arm
     * writes stay silent, exactly the reference's flag-4 semantics), while
     * a leaf departing to air, to a log, or to any other family fires it
     * (the decay cascade and the tree-chop trigger).</p>
     */
    public static final class RemovalFan implements WorldChangeListener {

        @Override
        public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
            // The fan reads the DEPARTING type alone (onBlockReplaced); the
            // commit arm carries no law of its own.
        }

        @Override
        public void onBlockReplaced(EngineWorld changedWorld, BlockPosition position,
                                    BlockType oldType, BlockType newType) {
            if (sameLeafFamily(oldType, newType)) {
                return; // a decay-bit flip never fires the reference's onRemoved
            }
            Identifier id = oldType.identifier();
            int radius;
            if (isLeafState(id)) {
                radius = 1; // the 3³ box (line 47's int i = 1)
            } else if (WoodVariantBlocks.isLog(id)) {
                radius = 4; // the 5³ box (AbstractLogBlock line 24's int i = 4)
            } else {
                return;
            }
            fanCheckDecay(changedWorld, position, radius);
        }
    }

    /** @return whether both types are leaf states of the SAME wood (the reference's same-block law). */
    private static boolean sameLeafFamily(BlockType oldType, BlockType newType) {
        return isLeafState(oldType.identifier())
                && WoodVariantBlocks.variantOfLeaf(oldType.identifier())
                        == WoodVariantBlocks.variantOfLeaf(newType.identifier())
                && isLeafState(newType.identifier());
    }

    /**
     * The shared fan walk: the box's every leaf with CHECK_DECAY unset gets
     * it set (the reference's {@code setBlockState(pos, state.set(
     * CHECK_DECAY, true), 4)}), the persistent forms untouched (the bit
     * stays on the DECAYABLE gate — a persistent leaf never arms).
     */
    static void fanCheckDecay(EngineWorld world, BlockPosition center, int radius) {
        int j = radius + 1;
        if (!areaLoaded(world, center, j)) {
            return; // the reference's isAreaLoaded gate — the fan defers
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPosition target = center.offset(dx, dy, dz);
                    if (target.y() < BlockPosition.MIN_Y || target.y() > BlockPosition.MAX_Y) {
                        continue;
                    }
                    BlockType leaf = world.getBlock(target);
                    int bits = decayBitsOf(leaf.identifier());
                    if (bits == 0 || bits == 1) {
                        // The armed form: the same persistent bit, the
                        // check bit set (the reference keeps DECAYABLE).
                        world.setBlock(target, stateOf(
                                WoodVariantBlocks.variantOfLeaf(leaf.identifier()),
                                (bits & 1) != 0, true));
                    }
                }
            }
        }
    }
}
