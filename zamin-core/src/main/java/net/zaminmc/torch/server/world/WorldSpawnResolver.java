package net.zaminmc.torch.server.world;

import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The world-spawn resolution (T-SPAWN — the terrain family audit's second
 * fix): the reference's own placement laws, resolved against the GENERATED
 * blocks at boot — never the pure height function.
 *
 * <p><b>THE GRASS WALK</b> ({@code ServerWorld.initSpawnPoint} lines
 * 660-680, 1:1): from the scan answer, drift {@code += random.nextInt(64) -
 * random.nextInt(64)} per axis until {@code isValidSpawnPoint} holds —
 * {@code Dimension.isValidSpawnPoint} = the column's {@code
 * getSurfaceBlock == GRASS} (a tree column answers leaves, an ocean column
 * water) — capped at 1000 attempts (the reference's own break keeps the
 * last column), the stream seeded {@code new Random(worldSeed)}.</p>
 *
 * <p><b>THE SURFACE CONSULT</b> ({@code World.getSurfaceHeight} lines
 * 1132-1148): scan down from the section top; skip the leaf family
 * (material {@code blocksMovement() && != LEAVES}); land on the first free
 * cell above the top solid — a canopy hangs overhead harmlessly.</p>
 *
 * <p><b>THE UNSTUCK WALK</b> ({@code ServerPlayerEntity}'s constructor
 * lines 158-160): while the landing cell or the head cell is solid, step
 * up — the trunk-through-canopy escape, capped at 255.</p>
 *
 * <p>The scatter band (the reference places each fresh player within
 * {@code max(5, spawnProtection - 6)} of the stored point, resolving each
 * placement against the live world) stays the engine's ledgered
 * adaptation: the single world-spawn law resolves ONCE at boot, every
 * fresh body landing at the one cell.</p>
 */
public final class WorldSpawnResolver {

    /** The world surface the laws consult (the boot adapts it over the live world). */
    public interface Ground {
        /**
         * getSurfaceBlock (World lines 158-168): the column's top block —
         * climb from the sea level while the cell above is not air, answer
         * the block there (a tree column answers its leaves, an ocean
         * column its water, a grass column the grass).
         */
        Object surfaceBlock(int x, int z);

        /**
         * getSurfaceHeight (World lines 1132-1148): the first free cell
         * above the top blocksMovement non-leaves block — the leaf family
         * skipped, the reference's own law.
         */
        int surfaceHeight(int x, int z);

        /**
         * Whether the cell is solid for the unstuck consult (the body's
         * own collision family — the engine's WorldSolidity consult).
         */
        boolean isSolid(int x, int y, int z);
    }

    private WorldSpawnResolver() {
    }

    /**
     * The boot-time resolution: the grass walk over the scan answer, then
     * the surface consult, then the unstuck walk — the settled spawn.
     *
     * @param ground     the live world (the generated spawn area)
     * @param worldSeed  the persisted seed (the walk's stream)
     * @param scanAnswer the generator's X/Z scan (the biome-search stand-in)
     */
    public static Position resolve(Ground ground, long worldSeed,
                                   Position scanAnswer) {
        Random random = new Random(worldSeed);
        int x = (int) Math.floor(scanAnswer.x());
        int z = (int) Math.floor(scanAnswer.z());
        // The grass walk (initSpawnPoint lines 671-680): drift until the
        // column's surface block is grass, the reference's own cap.
        int attempts = 0;
        while (!isGrassColumn(ground, x, z)) {
            x += random.nextInt(64) - random.nextInt(64);
            z += random.nextInt(64) - random.nextInt(64);
            if (++attempts == 1000) {
                break; // the reference's own break keeps the last column
            }
        }
        // The surface consult (getSurfaceHeight): the first free cell above
        // the top solid non-leaves block.
        int y = ground.surfaceHeight(x, z);
        // The unstuck walk (the fresh-body placement's own escape): while
        // the landing or head cell is solid, step up.
        while (y < 255 && (ground.isSolid(x, y, z) || ground.isSolid(x, y + 1, z))) {
            y++;
        }
        return new Position(x + 0.5, y, z + 0.5);
    }

    /**
     * Dimension.isValidSpawnPoint (line 72): the column's surface block is
     * the grass — the identifier consult (registry-instance safe).
     */
    private static boolean isGrassColumn(Ground ground, int x, int z) {
        Object block = ground.surfaceBlock(x, z);
        return block instanceof net.zaminmc.torch.block.BlockType type
                && type.identifier().value().equals("grass_block");
    }
}
