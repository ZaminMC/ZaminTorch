package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;



/**
 * THE VERIFIER'S VT-009 DISCOVERY SEAT (docs/vanilla-conformance-audit.md,
 * the twenty-seventh pass): the mob's portal re-entry cooldown decays at
 * DOUBLE rate — the PR #98 regression.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the cooldown ticks down ONCE
 * per ticked body — {@code Entity.baseTick} lines 311-313
 * ({@code if (this.portalCooldown > 0) this.portalCooldown--}), the one
 * call site in the whole per-tick walk. A body that crossed and armed the
 * 300-tick store ({@code getPortalCooldown()} lines 1457-1459) is immune
 * to re-entry for EXACTLY 300 ticks.</p>
 *
 * <p>THE ENGINE'S SHAPE (the regression): PR #98 added the entity-level
 * portal arm — {@code MobManager.tickPortalArm} consults
 * {@code MobEntity.advanceEntityPortalClock}, whose walk carries the tail
 * decrement (decrement #1) — but the PRE-EXISTING row inside
 * {@code MobEntity.tick} (the old "the row round-trips for the NBT
 * surface" comment's decrement, the pre-PR#98 stand-in) was left in place
 * (decrement #2). A living mob pays the cooldown TWICE per tick: the
 * 300-tick immunity window halves to ~150 ticks.</p>
 *
 * <p>THE SEAT: a NoAI cow in the open (no portal anywhere — the clock
 * runs regardless), the store set to 300, then exactly 40 engine ticks:
 * the reference's answer is 260; the regression's answer is 220. This
 * seat is EXPECTED RED on develop until VT-009's correction lands. Do
 * not re-pin it to the engine's current answer; the finding record owns
 * the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MobPortalCooldownDecayParityTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "vt009decay" + Math.abs(new java.util.Random().nextInt(100000)),
                "cd", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * VT-009: the cooldown decays at ONE tick per tick — the reference's
     * single tail decrement (baseTick lines 311-313), not the doubled
     * walk the regression carries. THE RATE FORM: two reads around a
     * 20-tick window — the boundary between the store's arming and the
     * window's start carries an unavoidable ±1 tick alignment (the
     * polling thread races the tick lane), so the honest assertion is
     * the RATE: 20 ± 1 for the reference's single decrement; the
     * regression's doubled walk answers ~40, far outside the band.
     */
    @Test
    void thePortalCooldownDecaysAtOnePerTick() throws Exception {
        var spawned = new java.util.concurrent.atomic.AtomicReference<MobEntity>();
        server.ticker().submit(() -> {
            MobEntity cow = server.mobs().spawnAt(MobType.COW,
                    new Position(8.5, 8.0, 8.5));
            if (cow != null) {
                cow.setNoAi(true); // the body stands still; the clock runs
                cow.setPortalCooldown(300);
                spawned.set(cow);
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> spawned.get() != null,
                100, "the cow spawned with the armed store");
        MobEntity cow = spawned.get();

        // Let the store settle past the arming boundary (both shapes
        // reach a steady decay within a few ticks).
        EngineAwaits.awaitCondition(server.world(),
                () -> cow.portalCooldown() <= 290, 100, "the decay running");

        // The rate window: two reads around exactly 20 completed ticks.
        int first = cow.portalCooldown();
        EngineAwaits.awaitTicks(server.world(), 20, "the rate window");
        int second = cow.portalCooldown();

        // THE REFERENCE'S ANSWER: first - second == 20 (±1 for the
        // polling boundary) — ONE decrement per tick. THE REGRESSION'S
        // ANSWER: ~40 — the tickPortalArm decrement plus the leftover
        // in-tick row.
        int decayed = first - second;
        org.junit.jupiter.api.Assertions.assertTrue(decayed >= 19 && decayed <= 21,
                "VT-009: the portal re-entry cooldown decays at one per tick "
                        + "(the reference's single tail decrement, Entity.baseTick "
                        + "lines 311-313 — the 300-tick immunity window the "
                        + "crossing arms); observed a decay of " + decayed
                        + " over the 20-tick window");
    }
}
