package net.zamin.engine.block;

import net.zamin.api.BlockPosition;
import net.zamin.api.BlockType;
import net.zamin.api.ChunkPosition;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.world.EngineChunk;
import net.zamin.engine.world.EngineWorld;

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
 * to dirt under an opaque block; otherwise up to four spread attempts pick a
 * nearby dirt cell (±1 x/z, −3..+1 y) and grow grass into it when nothing
 * opaque covers the target. Light levels are approximated by the same
 * "nothing opaque above" test — the flat world carries full skylight and the
 * engine has no block-light propagation yet (documented simplification, and
 * the reason covered cells never regrow grass).</p>
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

    private void randomTick(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (!type.identifier().toString().equals("minecraft:grass_block")) {
            return;
        }
        if (BlockUpdateSystem.isOpaque(world.getBlock(position.offset(0, 1, 0)))) {
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
            if (BlockUpdateSystem.isOpaque(world.getBlock(target.offset(0, 1, 0)))) {
                continue;
            }
            world.setBlock(target, BuiltinBlocks.GRASS_BLOCK);
        }
    }

    private boolean withinPlayerRange(ChunkPosition chunk, Iterable<PlayerSession> players) {
        double centerX = chunk.x() * 16 + 8.0;
        double centerZ = chunk.z() * 16 + 8.0;
        boolean anyPlaying = false;
        for (PlayerSession player : players) {
            if (player.state() != net.zamin.api.PlayerState.PLAYING) {
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
