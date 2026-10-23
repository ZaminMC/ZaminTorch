package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;

/**
 * The pumpkin's golem laws (Slice 17d's placement scan, lifted to the
 * family file for the dispenser's arm — the reference's own shape:
 * {@code Blocks.PUMPKIN} carries {@code canSpawnGolem} +
 * {@code onAdded}→{@code trySpawnGolem} and every placement path
 * consults the same block object, reference/1.8.8 block/PumpkinBlock.java
 * lines 40-75).
 *
 * <p><b>The body-pattern check ({@link #canSpawnGolem}, lines 45-47):</b>
 * {@code getSnowGolemBodyPattern().find != null || getIronGolemBodyPattern
 * .find != null} — the SNOW body (aisle {@code " ", "#", "#"} lines
 * 137-146: the head cell a wildcard, two snow blocks under it) or the
 * IRON body (aisle {@code "~ ~", "###", "~#~"} lines 160-169: the
 * wildcard head cell over the 3-wide iron chest row, air flanking it,
 * the single iron base under the middle with air flanks). The pattern's
 * own find anchors the given cell at any of its rows; the dispense arm
 * only consults an AIR head cell, where the lower-row anchors cannot
 * match — the head-cell law is the two-block/cross check below it.</p>
 *
 * <p>Ownership: the tick lane (the placement path and the dispense walk
 * both run there).</p>
 */
public final class GolemSpawns {

    private GolemSpawns() {
    }

    /**
     * The body-pattern check (PumpkinBlock.canSpawnGolem lines 45-47): a
     * hypothetical pumpkin at {@code head} would complete a golem — the
     * snow column (two snow blocks under the cell) or the iron cross
     * (the 3-wide chest row + the base + the air guards at the head's
     * and the base's flanks).
     *
     * @return whether the pattern matches at the head cell.
     */
    public static boolean canSpawnGolem(EngineWorld world, BlockPosition head) {
        // The snow body (getSnowGolemBodyPattern lines 137-146): the
        // wildcard head over SNOW / SNOW.
        if (isBlockAt(world, head.offset(0, -1, 0), BuiltinBlocks.SNOW)
                && isBlockAt(world, head.offset(0, -2, 0), BuiltinBlocks.SNOW)) {
            return true;
        }
        // The iron body (getIronGolemBodyPattern lines 160-169): the
        // wildcard head over the 3-wide iron row, the base under the
        // middle, air at both rows' flanks — the reference's find tries
        // both horizontal axes.
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0;
            int dz = axis == 0 ? 0 : 1;
            BlockPosition chest = head.offset(0, -1, 0);
            BlockPosition base = head.offset(0, -2, 0);
            if (isBlockAt(world, chest, BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, chest.offset(dx, 0, dz), BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, chest.offset(-dx, 0, -dz), BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, base, BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, head.offset(dx, 0, dz), world.airType())
                    && isBlockAt(world, head.offset(-dx, 0, -dz), world.airType())
                    && isBlockAt(world, base.offset(dx, 0, dz), world.airType())
                    && isBlockAt(world, base.offset(-dx, 0, -dz), world.airType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The golem build scan (trySpawnGolem 1:1 — the snow column first, the
     * iron cross second, the body blocks clearing to air on the match):
     * the snow golem spawns at the bottom snow cell, the iron golem at
     * the base's center cell with the player-created flag.
     */
    public static void trySpawnGolem(EngineWorld world, MobManager mobs, BlockPosition head) {
        // The snow pattern (getSnowGolemPattern): "^" / "#" / "#" — the
        // head over two snow blocks.
        boolean snowBody = isBlockAt(world, head.offset(0, -1, 0), BuiltinBlocks.SNOW)
                && isBlockAt(world, head.offset(0, -2, 0), BuiltinBlocks.SNOW);
        if (snowBody) {
            world.setBlock(head, world.airType());
            world.setBlock(head.offset(0, -1, 0), world.airType());
            world.setBlock(head.offset(0, -2, 0), world.airType());
            BlockPosition at = head.offset(0, -2, 0);
            mobs.spawnAt(net.zaminmc.torch.server.entity.MobType.SNOW_GOLEM,
                    new Position(at.x() + 0.5, at.y() + 0.05, at.z() + 0.5));
            return;
        }
        // The iron pattern (getIronGolemPattern): "~^~" / "###" / "~#~" — the
        // head over the 3-wide chest row, the single base under the middle
        // — the reference's BlockPattern.find tries both horizontal axes.
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0;
            int dz = axis == 0 ? 0 : 1;
            BlockPosition chest = head.offset(0, -1, 0);
            BlockPosition base = head.offset(0, -2, 0);
            boolean ironCross = isBlockAt(world, chest, BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, chest.offset(dx, 0, dz), BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, chest.offset(-dx, 0, -dz), BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, base, BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(world, head.offset(dx, 0, dz), world.airType())
                    && isBlockAt(world, head.offset(-dx, 0, -dz), world.airType())
                    && isBlockAt(world, base.offset(dx, 0, dz), world.airType())
                    && isBlockAt(world, base.offset(-dx, 0, -dz), world.airType());
            if (!ironCross) {
                continue;
            }
            world.setBlock(head, world.airType());
            world.setBlock(chest, world.airType());
            world.setBlock(chest.offset(dx, 0, dz), world.airType());
            world.setBlock(chest.offset(-dx, 0, -dz), world.airType());
            world.setBlock(base, world.airType());
            MobEntity golem = mobs.spawnAt(
                    net.zaminmc.torch.server.entity.MobType.IRON_GOLEM,
                    new Position(base.x() + 0.5, base.y() + 0.05, base.z() + 0.5));
            if (golem != null) {
                // setPlayerCreated (IronGolemEntity lines 229-236): the
                // pattern-built golem never despawns AND never turns on
                // its builder's village (VIII10's −5 reputation arm
                // refuses) — the engine's flag + persistence pair.
                golem.setGolemPlayerCreated(true);
            }
            return;
        }
    }

    /** @return whether the cell holds exactly the given block type. */
    private static boolean isBlockAt(EngineWorld world, BlockPosition at, BlockType type) {
        return world.getBlock(at).equals(type);
    }
}
