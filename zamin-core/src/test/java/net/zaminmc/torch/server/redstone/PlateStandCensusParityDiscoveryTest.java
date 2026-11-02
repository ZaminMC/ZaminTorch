package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-3 — the plate family's class census (the Phase E boundary
 * arms' coverage tail over the plate probes): the reference's MOBS
 * rule gathers {@code LivingEntity.class}
 * (PressurePlateBlock.calculateOutputSignal lines 42-43) — the mobs,
 * the players AND THE ARMOR STANDS (ArmorStandEntity extends
 * LivingEntity: a stone plate fires under a stand, exactly vanilla) —
 * and the weighted count gathers {@code Entity.class}
 * (WeightedPressurePlateBlock.calculateOutputSignal lines 25-36 —
 * every family counts).
 *
 * <p>The pre-fix engine's occupancy and count probes walked the mobs,
 * the players and the items alone: a stone plate never fired under an
 * armor stand, and a weighted plate never counted one. RED pre-fix on
 * both seats.</p>
 */
class PlateStandCensusParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "psc" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR: the stone plate fires under an
    // armor stand (the MOBS rule's LivingEntity census)
    // ------------------------------------------------------------------

    /**
     * A stand's own 0.5x1.975 box (ArmorStandEntity's
     * setSize(0.5F, 1.975F), feet-anchored) standing on the plate's
     * cell — the box [y, y+1.975] overlapping the activation box
     * [y, y+0.25] — presses the STONE plate (the MOBS rule's
     * LivingEntity.class gather: the stand IS a living body). Pre-fix
     * the stand walk was absent: RED pre-fix.
     */
    @Test
    void theStonePlateFiresUnderAnArmorStand() throws Exception {
        int x = 10, z = 10, y = 40;
        placeStonePlateRig(x, y, z);
        AtomicReference<net.zaminmc.torch.server.armorstand.ArmorStand> stand =
                new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                x + 0.5, y, z + 0.5, 0, null)));
        await(() -> stand.get() != null, "the stand summoned on the plate");
        await(() -> RedstoneBlocks.platePowered(typeAt(x, y, z)),
                "the stone plate fires under the armor stand (the MOBS rule's "
                        + "LivingEntity census)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE DISCRIMINATOR: the weighted plate counts the stand
    // (the Entity.class census)
    // ------------------------------------------------------------------

    /**
     * The weighted count walks {@code Entity.class} — the stand counts
     * ONE on the light plate (ceil(1/15 * 15) = 1). Pre-fix the count
     * never saw the stand: RED pre-fix.
     */
    @Test
    void theWeightedPlateCountsTheArmorStand() throws Exception {
        int x = 30, z = 10, y = 40;
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x, y, z),
                    WeightedPlateBlocks.state(0, 0));
        });
        await(() -> WeightedPlateBlocks.isWeightedPlate(
                        typeAt(x, y, z).identifier()),
                "the light weighted plate placed");
        AtomicReference<net.zaminmc.torch.server.armorstand.ArmorStand> stand =
                new AtomicReference<>();
        server.ticker().submit(() -> stand.set(server.armorStands().summonAt(
                x + 0.5, y, z + 0.5, 0, null)));
        await(() -> stand.get() != null, "the stand summoned on the plate");
        await(() -> WeightedPlateBlocks.powerOf(typeAt(x, y, z).identifier()) == 1,
                "the weighted plate counts the armor stand (the Entity "
                        + "census — the light plate reading 1)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE COVERAGE: the wooden plate fires under an orb (the
    // EVERYTHING rule's Entity census)
    // ------------------------------------------------------------------

    /**
     * The EVERYTHING rule gathers {@code Entity.class} — the orb
     * resting in the plate's cell (its own box [y, y+0.25] inside the
     * activation box) presses the WOODEN plate. Pre-fix the orb walk
     * was absent: RED pre-fix.
     */
    @Test
    void theWoodenPlateFiresUnderAnOrb() throws Exception {
        int x = 50, z = 10, y = 40;
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x, y, z),
                    RedstoneBlocks.plateOf(false, true));
        });
        await(() -> RedstoneBlocks.isPlate(typeAt(x, y, z)), "the wooden plate placed");
        AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> orb =
                new AtomicReference<>();
        server.ticker().submit(() -> orb.set(server.experienceOrbs().spawnRestored(
                new Position(x + 0.5, y + 0.125, z + 0.5), 3, 0, 0)));
        await(() -> orb.get() != null, "the orb spawned at rest in the plate's cell");
        await(() -> RedstoneBlocks.platePowered(typeAt(x, y, z)),
                "the wooden plate fires under the orb (the EVERYTHING rule's "
                        + "Entity census)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** The stone plate on its stone mount. */
    private void placeStonePlateRig(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x, y, z),
                    RedstoneBlocks.plateOf(false, false));
        });
        await(() -> RedstoneBlocks.isPlate(typeAt(x, y, z)), "the stone plate placed");
    }

    private BlockType typeAt(int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
