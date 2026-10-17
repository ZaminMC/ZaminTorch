package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.stat.EngineAchievements;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item thrower family (Slice 93/D1 — ItemEntity lines 24-25 + 262-306):
 * the owner-window pickup gate ({@code owner == null || 6000 - age <= 200 ||
 * owner.equals(name)}), the pickup-effects callback's every-successful-add
 * semantics (the reference's addItem-returned-true shape — the partial
 * pickup included), and the GIVE_DIAMOND arm over the live engine (the
 * Q-key drop's thrower stamp, the other player's pickup stamping the
 * thrower, the self-pickup exemption).
 */
class ItemThrowerPickupTest {

    // ------------------------------------------------------------------
    // Level 1 — the manager-level model (the gate + the effects callback)
    // ------------------------------------------------------------------

    /** Solid everywhere below y=4 (the flat world's own surface). */
    private static final ItemEntity.Ground GROUND = (x, y, z) -> y < 4.0;

    private PlayerSession stubPlayer(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
                return java.util.List.of();
            }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(
                    net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        }, Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    @Test
    void theOwnerWindowGatesThePickupWalk() {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        PlayerSession owner = stubPlayer("Owner", new Position(2.5, 4, 2.5));
        PlayerSession stranger = stubPlayer("Stranger", new Position(2.5, 4, 2.5));

        ItemEntity owned = manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        owned.setOwner("Owner");

        // The stranger within the window: refused (the body is owned).
        manager.tick(List.of(stranger));
        assertEquals(1, manager.size());

        // The owner's own name passes.
        manager.tick(List.of(owner));
        assertEquals(0, manager.size());

        // The last-200-ticks window opens an owned body to everyone: age
        // the body to 5801 (6000 - 5801 <= 200) and the stranger passes.
        ItemEntity aged = manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        aged.setOwner("SomeoneElse");
        for (int i = 0; i < ItemEntity.DESPAWN_TICKS - 200; i++) {
            manager.tick(List.of());
        }
        manager.tick(List.of(stranger));
        assertEquals(0, manager.size());
    }

    @Test
    void theUnownedBodyPassesEveryCollector() {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        PlayerSession anyone = stubPlayer("Anyone", new Position(2.5, 4, 2.5));
        ItemEntity plain = manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        assertTrue(plain.pickupAllowedFor("Anyone"));
        assertTrue(plain.pickupAllowedFor("SomeoneElseEntirely"));
        manager.tick(List.of(anyone));
        assertEquals(0, manager.size());
    }

    @Test
    void thePickupEffectsFireOnEverySuccessfulAdd() {
        List<int[]> effectCalls = new java.util.ArrayList<>();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(new ItemEntityManager.Listener() {
            @Override public void onItemSpawned(ItemEntity entity) { }
            @Override public void onItemMoved(ItemEntity entity) { }
            @Override public void onItemCollected(ItemEntity entity,
                    PlayerSession collector, int collectedCount) { }
            @Override public void onItemStackChanged(ItemEntity entity) { }
            @Override public void onItemRemoved(ItemEntity entity, String reason) { }
            @Override public void onItemPickupEffects(ItemEntity entity,
                    PlayerSession collector, int collectedCount) {
                effectCalls.add(new int[] {collectedCount});
            }
        });

        // The full pickup: one effects call.
        PlayerSession hoarder = stubPlayer("Hoarder", new Position(2.5, 4, 2.5));
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIAMOND, 3), 0);
        manager.tick(List.of(hoarder));
        assertEquals(1, effectCalls.size());
        assertEquals(3, effectCalls.get(0)[0]);

        // The partial pickup (the inventory nearly full): the effects call
        // still fires — the reference's addItem-returned-true semantics.
        PlayerSession fuller = stubPlayer("Fuller", new Position(2.5, 4, 2.5));
        fuller.inventory().pickUp(ItemStack.of(BuiltinItems.DIAMOND, 62));
        for (int i = 0; i < 35; i++) {
            fuller.inventory().pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        }
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIAMOND, 10), 0);
        manager.tick(List.of(fuller));
        assertEquals(2, effectCalls.size());
        assertEquals(2, effectCalls.get(1)[0]); // 62 + 2 fills the diamond stack
        assertEquals(1, manager.size()); // the remainder stays in the world
    }

    // ------------------------------------------------------------------
    // Level 2 — the live engine (the GIVE_DIAMOND arm)
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "itp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private PlayerSession join(String name, Position at, Rotation looking) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, looking, true);
        return session;
    }

    /**
     * Hands the unearned parent chain out root-first — the /achievement
     * give walk's own shape (the vanilla gate: an achievement only counts
     * when its parent chain is satisfied, PlayerStats.increment's own
     * condition). A diamond-bearing thrower realistically carries
     * GET_DIAMOND already; the collector needs the chain up to
     * GET_IRON_INGOT for the pickup's own GET_DIAMOND to land.
     */
    private void earnChain(PlayerSession session,
            net.zaminmc.torch.server.stat.EngineAchievements.Achievement top) {
        server.ticker().submit(() -> {
            java.util.List<net.zaminmc.torch.server.stat.EngineAchievements.Achievement> missing =
                    new java.util.ArrayList<>();
            for (var walk = top; walk.parent() != null
                    && !session.stats().hasAchievement(walk.parent().key());
                    walk = walk.parent()) {
                missing.add(walk.parent());
            }
            for (int i = missing.size() - 1; i >= 0; i--) {
                session.stats().increment(missing.get(i).key(), 1);
            }
            session.stats().increment(top.key(), 1);
        });
    }

    @Test
    void theThrownDiamondStampsTheThrowerWithTheAchievement() throws Exception {
        // The thrower at the drop spot looking straight DOWN (the pitch-90
        // rotation: the item spawns at the eye - 0.4 and falls at the
        // thrower's own feet, no horizontal drift). The thrower carries the
        // GET_DIAMOND chain (they mined the diamond they are giving — the
        // realistic body), the collector the chain up to GET_IRON_INGOT.
        Rotation down = new Rotation(0.0f, 90.0f);
        PlayerSession thrower = join("Thrower", new Position(30.5, 4, 30.5), down);
        PlayerSession collector = join("Collector", new Position(8.5, 4, 8.5), Rotation.ZERO);
        earnChain(thrower, EngineAchievements.GET_DIAMOND);
        earnChain(collector, EngineAchievements.GET_IRON_INGOT);
        await(() -> thrower.stats().hasAchievement(EngineAchievements.GET_DIAMOND.key())
                && collector.stats().hasAchievement(EngineAchievements.GET_IRON_INGOT.key()),
                "the chains earned");

        server.ticker().submit(() ->
                thrower.inventory().pickUp(ItemStack.of(BuiltinItems.DIAMOND, 1)));
        await(() -> !thrower.inventory().held().isEmpty(), "the diamond held");

        // The Q-key drop: the only site that stamps the thrower.
        server.dropHeld(thrower, false);
        await(() -> thrower.inventory().held().isEmpty(), "the diamond dropped");

        // The thrower steps away (the pickup walk iterates every playing
        // body at the item — the collector alone stands there); after the
        // player-throw pickup delay (40 ticks) the pickup walk fires: the
        // collector's GET_DIAMOND and the thrower's GIVE_DIAMOND land
        // together.
        thrower.applyMovement(new Position(8.5, 4, 8.5), Rotation.ZERO, true);
        collector.applyMovement(new Position(30.5, 4, 30.5), Rotation.ZERO, true);
        await(() -> collector.stats().hasAchievement(EngineAchievements.GET_DIAMOND.key()),
                "the collector's GET_DIAMOND");
        assertTrue(thrower.stats().hasAchievement(EngineAchievements.GIVE_DIAMOND.key()),
                "the thrower's GIVE_DIAMOND (a different player picked the diamond up)");
    }

    @Test
    void theSelfPickupNeverStampsTheThrower() throws Exception {
        Rotation down = new Rotation(0.0f, 90.0f);
        PlayerSession thrower = join("Loner", new Position(40.5, 4, 40.5), down);
        earnChain(thrower, EngineAchievements.GET_DIAMOND);
        await(() -> thrower.stats().hasAchievement(EngineAchievements.GET_DIAMOND.key()),
                "the chain earned");

        server.ticker().submit(() ->
                thrower.inventory().pickUp(ItemStack.of(BuiltinItems.DIAMOND, 1)));
        await(() -> !thrower.inventory().held().isEmpty(), "the diamond held");
        server.dropHeld(thrower, false);
        await(() -> thrower.inventory().held().isEmpty(), "the diamond dropped");

        // The thrower stands at their own drop: after the 40-tick delay the
        // pickup fires (the diamond lands back in the inventory), but the
        // reference's own playerentity != player gate holds the GIVE_DIAMOND
        // stamp. The body never leaves the spot — the self-pickup is the
        // whole point.
        await(() -> !thrower.inventory().held().isEmpty()
                        && thrower.inventory().held().type().equals(BuiltinItems.DIAMOND),
                "the self-pickup's diamond back in hand");
        assertFalse(thrower.stats().hasAchievement(EngineAchievements.GIVE_DIAMOND.key()),
                "no GIVE_DIAMOND for the self-pickup");
    }

    @Test
    void theContainerSpillCarriesNoThrowerStamp() throws Exception {
        // The reference's call-shape split: only the Q-key drop stamps the
        // thrower; the window-click outside (wireSlot -999) rides the
        // two-arg form. A spilled diamond picked up by another player
        // stamps nothing.
        PlayerSession spiller = join("Spiller", new Position(50.5, 4, 50.5),
                new Rotation(0.0f, 90.0f));
        PlayerSession collector = join("Finder", new Position(8.5, 4, 8.5), Rotation.ZERO);
        earnChain(collector, EngineAchievements.GET_IRON_INGOT);
        await(() -> collector.stats().hasAchievement(EngineAchievements.GET_IRON_INGOT.key()),
                "the chain earned");

        // Put a diamond in the cursor through the inventory's own surface,
        // then spill it through the window-click's outside arm.
        server.ticker().submit(() -> {
            spiller.inventory().pickUp(ItemStack.of(BuiltinItems.DIAMOND, 1));
            spiller.inventory().placeOnCursor(spiller.inventory().consumeHeld(1));
        });
        await(() -> !spiller.inventory().cursor().isEmpty(), "the cursor carried");

        server.windowClick(spiller, 0, -999, 0, 0, ok -> { });
        await(() -> spiller.inventory().cursor().isEmpty(), "the cursor spilled");

        // The spill itself must not duplicate: the cursor's stack leaves
        // the body once (the reference's own shape — the pre-fix pair here
        // dropped the world copy AND refilled the inventory). The spiller
        // steps away FIRST (the 40-tick delay would hand the world copy
        // right back), then the settled inventory asserts empty.
        spiller.applyMovement(new Position(8.5, 4, 8.5), Rotation.ZERO, true);
        assertTrue(java.util.Arrays.stream(spiller.inventory().slots())
                        .allMatch(net.zaminmc.torch.item.ItemStack::isEmpty),
                "the spill refills nothing (one copy leaves the body)");
        collector.applyMovement(new Position(50.5, 4, 50.5), Rotation.ZERO, true);
        await(() -> collector.stats().hasAchievement(EngineAchievements.GET_DIAMOND.key()),
                "the finder's GET_DIAMOND");
        assertFalse(spiller.stats().hasAchievement(EngineAchievements.GIVE_DIAMOND.key()),
                "the spill stamps no thrower (the two-arg form)");
    }
}
