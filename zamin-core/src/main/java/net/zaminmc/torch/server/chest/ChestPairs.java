package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.CollisionShapes;
import net.zaminmc.torch.server.block.MaterialSolidity;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Identifier;

/**
 * The chest family's merged-inventory resolution — the reference's
 * {@code ChestBlock.getInventory} (lines 339-370) over the engine's world:
 * the twin scan across the four horizontal neighbors (a plain chest joins a
 * plain chest, a trapped chest a trapped chest — the reference's
 * {@code block == this} identity law), the {@code isLocked} gate on BOTH
 * halves (a solid block or a sitting ocelot above either one refuses the
 * whole view), and the join order — a WEST/NORTH twin comes FIRST, an
 * EAST/SOUTH twin SECOND ({@code DoubleInventory(this, neighbor)} vs
 * {@code DoubleInventory(neighbor, this)}, lines 360-364).
 *
 * <p>One consult, four consumers (the eighteenth verifier pass's F-DC1
 * finding): the GUI open (the 54-slot window when the twin exists), the
 * hopper/dropper transfer consult ({@code HopperBlockEntity.getInventoryAt}
 * lines 460-463 rides the same static), the comparator's analog signal
 * ({@code ChestBlock.getAnalogSignal} line 430), and the placement's
 * no-triple gate ({@code ChestBlock.canBePlaced} lines 244-284 — the
 * separate {@link #canPlaceChestAt} consult).</p>
 *
 * <p>The reference's twin scan iterates {@code Direction.Plane.HORIZONTAL}
 * in the enum's own order — NORTH, EAST, SOUTH, WEST — and never breaks:
 * with two same-type neighbors (a world-edited state the placement gate
 * refuses in legal play) the LAST match in that order wins. The law is
 * kept 1:1 here.</p>
 */
public final class ChestPairs {

    private ChestPairs() {
    }

    /**
     * The merged view of the chest at the position: the clicked half alone
     * (a 27-slot window) or the pair in the reference's join order (54
     * slots — the first half's 27 then the second's). Null answers the
     * reference's own null: the chest is locked ({@code isLocked} on either
     * half — the block-above/ocelot-above gate, never the named lock key,
     * which rides the menu's own open walk).
     */
    public static View resolve(EngineWorld world, ChestManager chests,
                               MobManager mobs, BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        boolean trapped = isTrappedChest(type);
        if (!trapped && !isPlainChest(type)) {
            return null; // getInventory's first gate: not a chest block entity
        }
        if (isLocked(world, mobs, pos)) {
            return null; // line 346: the pos half's own lock refuses the view
        }
        ChestBlockEntity first = chests.getOrCreate(pos);
        BlockPosition firstPos = pos;
        ChestBlockEntity second = null;
        BlockPosition secondPos = null;
        // Direction.Plane.HORIZONTAL's own order: NORTH, EAST, SOUTH, WEST.
        int[][] offsets = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
        for (int[] offset : offsets) {
            BlockPosition neighborPos = pos.offset(offset[0], offset[1], offset[2]);
            BlockType neighbor = world.getBlock(neighborPos);
            if (trapped ? isTrappedChest(neighbor) : isPlainChest(neighbor)) {
                if (isLocked(world, mobs, neighborPos)) {
                    return null; // line 354: the twin's lock refuses the view
                }
                ChestBlockEntity neighborEntity = chests.getOrCreate(neighborPos);
                // The join order (lines 360-364): a WEST/NORTH twin comes
                // FIRST, an EAST/SOUTH twin SECOND.
                boolean twinFirst = offset[0] < 0 || offset[2] < 0;
                if (twinFirst) {
                    first = neighborEntity;
                    firstPos = neighborPos;
                    second = chests.getOrCreate(pos);
                    secondPos = pos;
                } else {
                    second = neighborEntity;
                    secondPos = neighborPos;
                }
            }
        }
        return new View(first, second, firstPos, secondPos);
    }

    /**
     * The reference's {@code isLocked} (lines 402-421): a solid block above
     * OR a sitting ocelot on top — either one locks the chest's view (no
     * GUI, no transfer surface, a 0 comparator read).
     */
    public static boolean isLocked(EngineWorld world, MobManager mobs, BlockPosition pos) {
        return isLockedByBlock(world, pos) || isLockedByOcelot(mobs, pos);
    }

    /**
     * The reference's {@code isLockedByBlock} (lines 406-408):
     * {@code world.getBlockState(pos.up()).getBlock().isSolid()} — the
     * reference's OWN isSolid law
     * ({@code material.isSolidBlocking() && isCube() && !isSignalSource()},
     * Block.java lines 274-276), not the movement model: a glass block, a
     * chest, a slab, a stair, a fence, the leaves, the ice, the TNT and the
     * glowstone above NEVER lock (isCube false or the translucent
     * material); a stone block, packed ice, a double slab, a bookshelf or
     * a furnace DOES; a redstone block or a daylight detector never locks
     * (signal sources), and neither does a trapped chest (its own
     * isSignalSource override, ChestBlock line 379).
     */
    private static boolean isLockedByBlock(EngineWorld world, BlockPosition pos) {
        BlockType above = world.getBlock(pos.offset(0, 1, 0));
        Identifier id = above.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false; // an unregistered kind defaults to the base law's false
        }
        // material.isSolidBlocking() = !translucent && blocksMovement.
        if (MaterialSolidity.isTranslucentMaterial(id)
                || !MaterialSolidity.blocksMovement(id)) {
            return false;
        }
        // isCube(): the render cube — the full collision box minus the
        // isCube-false full-box families (the glass pair, GlassBlock lines
        // 25-27 + StainedGlassBlock lines 60-62, and the piston bases,
        // PistonBaseBlock's own override).
        if (!CollisionShapes.isFullCube(above)
                || isGlassFamily(id)
                || net.zaminmc.torch.server.piston.PistonBlocks.isPiston(above)) {
            return false;
        }
        // !isSignalSource(): the full-cube signal sources alone reach this
        // arm (the partial shapes died at isCube) — the redstone block
        // (RedstoneBlock lines 20-22), the daylight detector family
        // (DaylightDetectorBlock lines 25-27), the trapped chest (ChestBlock
        // line 379 — dead here, its own isCube already refused it).
        return !id.equals(BuiltinBlocks.REDSTONE_BLOCK.identifier())
                && !id.value().startsWith("daylight_detector");
    }

    /** The glass family (the isCube-false full-box pair). */
    private static boolean isGlassFamily(Identifier id) {
        String value = id.value();
        return value.equals("glass") || value.startsWith("stained_glass");
    }

    /**
     * The reference's {@code isLockedByOcelot} (lines 410-421): a SITTING
     * ocelot whose box intersects the cell above the chest
     * ({@code new Box(x, y+1, z, x+1, y+2, z+1)}).
     */
    private static boolean isLockedByOcelot(MobManager mobs, BlockPosition pos) {
        if (mobs == null) {
            return false;
        }
        for (MobEntity mob : mobs.all()) {
            if (mob.dead() || mob.type() != MobType.OCELOT || !mob.wolfSitting()) {
                continue;
            }
            double halfWidth = mob.type().width / 2.0;
            double atX = mob.position().x();
            double atY = mob.position().y();
            double atZ = mob.position().z();
            boolean overlaps = atX + halfWidth > pos.x()
                    && atX - halfWidth < pos.x() + 1
                    && atY + mob.type().height > pos.y() + 1
                    && atY < pos.y() + 2
                    && atZ + halfWidth > pos.z()
                    && atZ - halfWidth < pos.z() + 1;
            if (overlaps) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reference's {@code ChestBlock.canBePlaced} (lines 244-284,
     * through {@code World.canPlace} lines 2274-2282): a chest refuses to
     * place beside TWO same-type chests, and beside any chest that is
     * itself already doubled ({@code isDoubleChest}, lines 286-298 — any
     * same-type chest beside the neighbor). No triples or quads.
     */
    public static boolean canPlaceChestAt(EngineWorld world, BlockPosition target,
                                          boolean trapped) {
        int count = 0;
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}};
        for (int[] offset : offsets) {
            BlockPosition neighbor = target.offset(offset[0], offset[1], offset[2]);
            BlockType neighborType = world.getBlock(neighbor);
            if (trapped ? isTrappedChest(neighborType) : isPlainChest(neighborType)) {
                if (isDoubleChest(world, neighbor, trapped)) {
                    return false; // the neighbor is already half of a pair
                }
                count++;
            }
        }
        return count <= 1;
    }

    /** The reference's isDoubleChest (lines 286-298): any same-type twin beside the chest. */
    private static boolean isDoubleChest(EngineWorld world, BlockPosition pos, boolean trapped) {
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}};
        for (int[] offset : offsets) {
            BlockPosition neighbor = pos.offset(offset[0], offset[1], offset[2]);
            BlockType neighborType = world.getBlock(neighbor);
            if (trapped ? isTrappedChest(neighborType) : isPlainChest(neighborType)) {
                return true;
            }
        }
        return false;
    }

    /** The plain family whole: the base form plus the four facing states. */
    private static boolean isPlainChest(BlockType type) {
        return type.identifier().equals(BuiltinBlocks.CHEST.identifier())
                || ChestFacingBlocks.isPlainFamily(type);
    }

    /** The trapped family whole: the base form plus the four facing states. */
    private static boolean isTrappedChest(BlockType type) {
        return type.identifier().equals(BuiltinBlocks.TRAPPED_CHEST.identifier())
                || ChestFacingBlocks.isTrappedFamily(type);
    }

    /**
     * The merged view the reference's {@code DoubleInventory} serves: the
     * slot addressing over the ordered pair ({@code getItem} line 67's
     * {@code slot >= inventory1.getSize()} split), the merged size, the
     * display-name law (the first half's custom name, then the second's,
     * then the family's own double title — lines 47-53), and the merged
     * mark-dirty (both halves, lines 99-102 — the comparator wake's own
     * law: a write through the pair re-arms the comparators reading BOTH
     * halves).
     */
    public record View(ChestBlockEntity first, ChestBlockEntity second,
                       BlockPosition firstPos, BlockPosition secondPos) {

        /** The merged slot count: 27 alone, 54 over the pair. */
        public int size() {
            return ChestBlockEntity.SLOT_COUNT
                    + (second == null ? 0 : ChestBlockEntity.SLOT_COUNT);
        }

        /** @return whether the twin exists (the 54-slot window). */
        public boolean isPair() {
            return second != null;
        }

        /** The half owning the merged slot. */
        public ChestBlockEntity halfOf(int mergedSlot) {
            return second != null && mergedSlot >= ChestBlockEntity.SLOT_COUNT
                    ? second : first;
        }

        /** The slot's own index inside its half. */
        public int localOf(int mergedSlot) {
            return second != null && mergedSlot >= ChestBlockEntity.SLOT_COUNT
                    ? mergedSlot - ChestBlockEntity.SLOT_COUNT : mergedSlot;
        }

        /** Both halves' snapshots concatenated (the merged comparator read). */
        public ItemStack[] snapshotAll() {
            ItemStack[] firstSlots = first.snapshotSlots();
            if (second == null) {
                return firstSlots;
            }
            ItemStack[] merged = new ItemStack[firstSlots.length + ChestBlockEntity.SLOT_COUNT];
            System.arraycopy(firstSlots, 0, merged, 0, firstSlots.length);
            System.arraycopy(second.snapshotSlots(), 0, merged, firstSlots.length,
                    ChestBlockEntity.SLOT_COUNT);
            return merged;
        }

        /** The DoubleInventory's markDirty (lines 99-102): BOTH halves wake. */
        public void markDirty() {
            first.markDirty();
            if (second != null) {
                second.markDirty();
            }
        }
    }
}
