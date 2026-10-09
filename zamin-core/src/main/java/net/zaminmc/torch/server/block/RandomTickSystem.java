package net.zaminmc.torch.server.block;

import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.Objects;
import java.util.Random;

/**
 * Random block ticks (§471 "random tick + neighbor state + scheduled
 * behavior"), the historical growth driver: every tick, each loaded chunk
 * within the historical 128-block player range samples three random positions
 * per section ({@code randomTickSpeed} 3), and only a few block kinds carry
 * random-tick rules — this slice: grass.
 *
 * <p>Grass rules are the historical 1.8 {@code BlockGrass.updateTick}: decay
 * to dirt when its own light drops below 4; otherwise up to four spread
 * attempts pick a nearby dirt cell (±1 x/z, −3..+1 y) and grow grass into it
 * when its light is at least 9 — the historical light gates, now served by
 * the real light storage (block light + skylight, §475) the LightEngine
 * maintains. This upgrades the earlier "nothing opaque above" approximation
 * with the same semantics for covered cells and the added torch-based spread
 * indoors (cave farms light their grass like the historical game).</p>
 */
public final class RandomTickSystem {

    /** Historical {@code randomTickSpeed} default: three picks per section. */
    public static final int PICKS_PER_SECTION = 3;
    /** Historical range: chunks within this distance of a player receive random ticks. */
    public static final double PLAYER_RANGE = 128.0;
    /** Historical spread-attempt count per randomly-ticked grass block. */
    private static final int SPREAD_ATTEMPTS = 4;
    private static final int SECTIONS = 16;

    private final EngineWorld world;
    private final Random random;

    public RandomTickSystem(EngineWorld world, Random random) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Advances one tick of random growth. Tick-thread context. */
    public void tick(Iterable<PlayerSession> players) {
        for (EngineChunk chunk : world.loadedChunks()) {
            if (!withinPlayerRange(chunk.position(), players)) {
                continue;
            }
            for (int section = 0; section < SECTIONS; section++) {
                for (int pick = 0; pick < PICKS_PER_SECTION; pick++) {
                    int x = chunk.position().x() * 16 + random.nextInt(16);
                    int y = section * 16 + random.nextInt(16);
                    int z = chunk.position().z() * 16 + random.nextInt(16);
                    randomTick(new BlockPosition(x, y, z));
                }
            }
        }
    }

    /**
     * The per-cell rule the per-section sampling drives. Public: the growth
     * acceptance tests probe named cells deterministically (the sampler's
     * 3-of-4096 odds make seeded end-to-end assertions flaky).
     */
    public void randomTick(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (WorldSolidity.isWheatCrop(type.identifier())) {
            tickWheatGrowth(position);
            return;
        }
        if (type.identifier().equals(BuiltinBlocks.FARMLAND.identifier())
                || type.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier())) {
            tickFarmlandHydration(position, type);
            return;
        }
        if (!type.identifier().toString().equals("minecraft:grass_block")) {
            return;
        }
        // The historical light gates, sampled at the cell ABOVE (vanilla
        // getLightFromNeighborsFor(pos.up()) — opaque cells store no light,
        // so the gates read the air that would host the sapling/grass top).
        if (lightAt(position.offset(0, 1, 0)) < 4) {
            world.setBlock(position, BuiltinBlocks.DIRT); // the historical decay
            return;
        }
        for (int attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++) {
            BlockPosition target = position.offset(
                    random.nextInt(3) - 1,
                    random.nextInt(5) - 3,
                    random.nextInt(3) - 1);
            if (target.y() < BlockPosition.MIN_Y || target.y() > BlockPosition.MAX_Y) {
                continue;
            }
            if (!world.getBlock(target).identifier().toString().equals("minecraft:dirt")) {
                continue;
            }
            if (lightAt(target.offset(0, 1, 0)) < 9) {
                continue;
            }
            world.setBlock(target, BuiltinBlocks.GRASS_BLOCK);
        }
    }

    /** The historical grass light: the brighter of block light and skylight at the cell. */
    private int lightAt(BlockPosition position) {
        return Math.max(world.blockLightAt(position), world.skyLightAt(position));
    }

    /**
     * The historical BlockCrop growth: a randomly-ticked stage advances one
     * age when the light above is at least 9, twice as likely over hydrated
     * farmland (the historical moisture growth-rate split, folded from the
     * per-biome surface to a fixed 1-in-3 / 1-in-6 chance per pick).
     */
    private void tickWheatGrowth(BlockPosition position) {
        if (lightAt(position.offset(0, 1, 0)) < 9) {
            return;
        }
        int stage = wheatStageAt(position);
        if (stage >= 7) {
            return; // fully grown: nothing to advance
        }
        BlockType soil = world.getBlock(position.offset(0, -1, 0));
        boolean hydrated = soil.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier());
        if (random.nextInt(hydrated ? 3 : 6) == 0) {
            world.setBlock(position, wheatStage(stage + 1));
        }
    }

    /** @return the wheat stage (0..7) at the position, -1 when not a crop. */
    public static int wheatStageAt(net.zaminmc.torch.server.world.EngineWorld world, BlockPosition position) {
        BlockType type = world.getBlock(position);
        return WorldSolidity.isWheatCrop(type.identifier())
                ? type.identifier().value().charAt("wheat_stage".length()) - '0'
                : -1;
    }

    private int wheatStageAt(BlockPosition position) {
        return wheatStageAt(world, position);
    }

    /** The block type of wheat stage 0..7. */
    public static net.zaminmc.torch.block.BlockType wheatStage(int stage) {
        return switch (stage) {
            case 0 -> BuiltinBlocks.WHEAT_STAGE0;
            case 1 -> BuiltinBlocks.WHEAT_STAGE1;
            case 2 -> BuiltinBlocks.WHEAT_STAGE2;
            case 3 -> BuiltinBlocks.WHEAT_STAGE3;
            case 4 -> BuiltinBlocks.WHEAT_STAGE4;
            case 5 -> BuiltinBlocks.WHEAT_STAGE5;
            case 6 -> BuiltinBlocks.WHEAT_STAGE6;
            default -> BuiltinBlocks.WHEAT_STAGE7;
        };
    }

    /**
     * The historical BlockFarmland hydration refresh: farmland with water
     * within four blocks (the same row and one above/below, the historical
     * scan box) turns wet; wet farmland without water dries back out.
     */
    private void tickFarmlandHydration(BlockPosition position, BlockType type) {
        boolean wet = type.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier());
        boolean waterNear = waterWithinFour(position);
        if (waterNear && !wet) {
            world.setBlock(position, BuiltinBlocks.FARMLAND_WET);
        } else if (!waterNear && wet) {
            world.setBlock(position, BuiltinBlocks.FARMLAND);
        }
    }

    /** The historical hydration scan: dx in [-4,4], dz in [-4,4], dy in [-1,1]. */
    private boolean waterWithinFour(BlockPosition position) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int y = position.y() + dy;
                    if (y < BlockPosition.MIN_Y || y > BlockPosition.MAX_Y) {
                        continue;
                    }
                    BlockType at = world.getBlock(new BlockPosition(
                            position.x() + dx, y, position.z() + dz));
                    if (FluidBlocks.isFluid(at.identifier())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean withinPlayerRange(ChunkPosition chunk, Iterable<PlayerSession> players) {
        double centerX = chunk.x() * 16 + 8.0;
        double centerZ = chunk.z() * 16 + 8.0;
        boolean anyPlaying = false;
        for (PlayerSession player : players) {
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING) {
                continue;
            }
            anyPlaying = true;
            double dx = player.position().x() - centerX;
            double dz = player.position().z() - centerZ;
            if (dx * dx + dz * dz <= PLAYER_RANGE * PLAYER_RANGE) {
                return true;
            }
        }
        // Nobody watching: the gate collapses and every loaded chunk ticks.
        // The historical rule exists to bound work at scale, not for semantics;
        // the slice-scale cost of an idle world is trivial and the world stays
        // alive for the next visitor. Revisit with the chunk-lifecycle slice.
        return !anyPlaying;
    }
}
