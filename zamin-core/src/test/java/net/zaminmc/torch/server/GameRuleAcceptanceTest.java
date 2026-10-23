package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The gamerule family (Slice 24a — the reference port of
 * reference/1.8.8 world/GameRules.java + GameRuleCommand.java): the
 * fifteen-rule table with its defaults and types, the command's three
 * forms (the listArgs list, the query line, the validated set), and
 * every gameplay gate the engine consults — doDaylightCycle over the
 * day clock, naturalRegeneration over both regen arms, keepInventory
 * and showDeathMessages over the death walk, doMobLoot over the loot
 * and XP releases, doTileDrops over the break's drops, mobGriefing
 * over the creeper's block-damaging blast, and the persistence
 * round-trip through the ZGR store.
 */
class GameRuleAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "grs" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
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

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    /** A recording console sender: captures every command reply. */
    private static final class RecordingConsole implements CommandSender {
        final List<String> replies = new CopyOnWriteArrayList<>();

        @Override public String name() { return "Console"; }
        @Override public void sendMessage(String message) { replies.add(message); }
        @Override public int opLevel() { return 4; }
        @Override public PlayerSession player() { return null; }
    }

    /**
     * Runs a console command on the tick lane (the honest route — the
     * engine's own console input submits the same way) and awaits the
     * reply.
     */
    private String command(String line) throws InterruptedException {
        RecordingConsole console = new RecordingConsole();
        server.ticker().submit(() -> server.chatService().dispatchCommand(console, line));
        await(() -> !console.replies.isEmpty(), "the command answered: " + line);
        return console.replies.get(0);
    }

    /** Sets a rule through the command (the console route, level 2+). */
    private void setRule(String rule, String value) throws InterruptedException {
        command("/gamerule " + rule + " " + value);
    }

    @Test
    void theTableMatchesTheReferenceDefaults() {
        // The public handle IS the engine table (§27.5): reads through
        // Server.gameRules() without casting to engine internals.
        var rules = ((Server) server).gameRules();
        assertEquals(15, rules.names().size(), "the reference's fifteen rules");
        // The defaults (GameRules' own constructor table, verbatim).
        assertTrue(rules.getBoolean("doFireTick"));
        assertTrue(rules.getBoolean("mobGriefing"));
        assertFalse(rules.getBoolean("keepInventory"));
        assertTrue(rules.getBoolean("doMobSpawning"));
        assertTrue(rules.getBoolean("doMobLoot"));
        assertTrue(rules.getBoolean("doTileDrops"));
        assertTrue(rules.getBoolean("doEntityDrops"));
        assertTrue(rules.getBoolean("commandBlockOutput"));
        assertTrue(rules.getBoolean("naturalRegeneration"));
        assertTrue(rules.getBoolean("doDaylightCycle"));
        assertTrue(rules.getBoolean("logAdminCommands"));
        assertTrue(rules.getBoolean("showDeathMessages"));
        assertEquals(3, rules.getInt("randomTickSpeed"));
        assertTrue(rules.getBoolean("sendCommandFeedback"));
        assertFalse(rules.getBoolean("reducedDebugInfo"));
        // The raw-string views (the reference's get): the miss arm
        // answers the empty string, a known rule its stored value.
        assertEquals("3", rules.get("randomTickSpeed"));
        assertEquals("", rules.get("noSuchRule"));
        assertFalse(rules.contains("noSuchRule"));
        assertTrue(rules.contains("keepInventory"));
        // The names answer in sorted order (the reference's TreeMap).
        var names = rules.names().stream().toList();
        assertEquals(names.stream().sorted().toList(), names, "sorted enumeration");
    }

    @Test
    void theCommandFormsMatchTheReference() throws Exception {
        // The query line (GameRuleCommand case 1: "rule = value").
        assertEquals("doFireTick = true", command("/gamerule doFireTick"));
        // The miss (commands.gamerule.norule).
        assertEquals("No game rule called 'noSuchRule' exists",
                command("/gamerule noSuchRule"));
        // The boolean validation (commands.generic.boolean.invalid).
        assertEquals("'maybe' is not true or false",
                command("/gamerule doFireTick maybe"));
        // The rule did NOT change under the refused value.
        assertTrue(((Server) server).gameRules().getBoolean("doFireTick"));
        // The set arm (commands.gamerule.success).
        assertEquals("Game rule keepInventory has been updated to: true",
                command("/gamerule keepInventory true"));
        assertTrue(((Server) server).gameRules().getBoolean("keepInventory"));
        // The numeric rule takes any value the parse accepts.
        command("/gamerule randomTickSpeed 0");
        assertEquals(0, ((Server) server).gameRules().getInt("randomTickSpeed"));
        // The reference's extension arm: an unknown name ADDS a custom
        // any-type rule (GameRules.set's else branch).
        assertEquals("Game rule myCustomRule has been updated to: hello",
                command("/gamerule myCustomRule hello"));
        assertEquals("hello", ((Server) server).gameRules().get("myCustomRule"));
        // The bare form: listArgs' own shape — comma-separated with
        // " and " before the last (AbstractCommand lines 370-382).
        String list = command("/gamerule");
        assertTrue(list.startsWith("commandBlockOutput, doDaylightCycle"),
                "the sorted list head: " + list);
        assertTrue(list.endsWith(" and showDeathMessages"), "the listArgs tail: " + list);
    }

    @Test
    void keepInventoryOnKeepsTheInventoryOnDeath() throws Exception {
        PlayerSession keeper = join("Keeper", new Position(0.5, 5, 0.5));
        await(() -> keeper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        var stone = BuiltinItems.lookup(Identifier.parse("minecraft:stone")).orElseThrow();
        server.ticker().submit(() -> {
            keeper.give(ItemStack.of(stone, 10));
            keeper.setTotalXp(500);
        });
        await(() -> keeper.countOf(stone) >= 10, "the items are in the inventory");
        // The rule (PlayerEntity.onDeath line 636's gate): the death
        // keeps the inventory and the experience; the death itself still
        // resolves.
        setRule("keepInventory", "true");
        server.damage(keeper, 1000.0f);
        await(() -> keeper.dead(), "the body dies");
        assertEquals(10, keeper.countOf(stone), "the rule kept the inventory");
        assertTrue(keeper.totalExperience() > 0, "the rule kept the experience");
    }

    @Test
    void showDeathMessagesOffSuppressesTheBroadcast() throws Exception {
        PlayerSession victim = join("Silent", new Position(0.5, 5, 0.5));
        await(() -> victim.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        List<String> delivered = new CopyOnWriteArrayList<>();
        server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override public void onChatMessage(PlayerSession sender, String content) { }
            @Override public void onSystemMessage(PlayerSession recipient, String content) {
                if (recipient == victim) {
                    delivered.add(content);
                }
            }
        });
        setRule("showDeathMessages", "false");
        server.damage(victim, 1000.0f);
        await(() -> victim.dead(), "the body dies");
        EngineAwaits.awaitTicks(server.world(), 4, "any broadcast would land within the same tick");
        assertTrue(delivered.stream().noneMatch(line -> line.contains("Silent")),
                "the death line never broadcast");
        // The twin (the default-true rule): the line lands.
        PlayerSession loud = join("Loud", new Position(0.5, 5, 0.5));
        await(() -> loud.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        setRule("showDeathMessages", "true");
        AtomicReference<String> seen = new AtomicReference<>();
        server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override public void onChatMessage(PlayerSession sender, String content) { }
            @Override public void onSystemMessage(PlayerSession recipient, String content) {
                if (recipient == loud && content.contains("Loud")) {
                    seen.set(content);
                }
            }
        });
        server.damage(loud, 1000.0f);
        await(() -> seen.get() != null, "the death line broadcast under the rule");
    }

    @Test
    void doMobLootOffWithholdsTheDropsAndTheXp() throws Exception {
        // The control arm first: a killed pig drops loot and XP.
        PlayerSession hunter = join("Hunter", new Position(0.5, 5, 0.5));
        await(() -> hunter.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        var pig = server.mobs().spawnAt(MobType.PIG, new Position(8.5, 5, 0.5));
        assertNotNull(pig, "the pig spawned");
        server.ticker().submit(() -> server.mobs().hurt(pig, 1000.0f, 0.0));
        await(() -> server.itemEntities().all().stream()
                .anyMatch(item -> item.stack().count() > 0), "the control drop landed");
        assertTrue(server.experienceOrbs().size() > 0, "the control XP landed");

        // The rule arm (LivingEntity.die's gate): nothing lands.
        setRule("doMobLoot", "false");
        var cow = server.mobs().spawnAt(MobType.COW, new Position(-8.5, 5, 0.5));
        assertNotNull(cow, "the cow spawned");
        int itemsBefore = server.itemEntities().all().size();
        int orbsBefore = server.experienceOrbs().size();
        server.ticker().submit(() -> server.mobs().hurt(cow, 1000.0f, 0.0));
        await(() -> cow.dead() || server.mobs().all().stream()
                .noneMatch(m -> m == cow), "the cow died");
        EngineAwaits.awaitTicks(server.world(), 4, "the death animation's drop walk is same-tick");
        assertEquals(itemsBefore, server.itemEntities().all().size(),
                "no loot landed under the rule");
        assertEquals(orbsBefore, server.experienceOrbs().size(),
                "no XP landed under the rule");
    }

    @Test
    void doTileDropsOffWithholdsTheBlockDrops() throws Exception {
        // The player stands at the target's own height, 3.5 blocks off
        // (in reach, the honest dig window).
        PlayerSession miner = join("Miner", new Position(3.5, 4, 0.5));
        await(() -> miner.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        BlockPosition dirt = new BlockPosition(0, 4, 0);
        server.ticker().submit(() -> server.world().setBlock(dirt,
                BuiltinBlocks.DIRT));
        setRule("doTileDrops", "false");
        int itemsBefore = server.itemEntities().all().size();
        // The honest dig rhythm (the reference's dirt-with-a-hand clock,
        // 0.75 s nominal — the lenient floor needs the start first).
        server.blockInteraction().submitMiningStart(miner, dirt);
        EngineAwaits.awaitTicks(server.world(), 20, "engine progress (a 1000 wall settle)");
        server.blockInteraction().submitMiningFinished(miner, dirt);
        await(() -> server.world().getBlock(dirt).equals(BuiltinBlocks.AIR),
                "the block broke");
        EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
        assertEquals(itemsBefore, server.itemEntities().all().size(),
                "no drop landed under the rule");
    }

    @Test
    void doDaylightCycleOffFreezesTheDayClock() throws Exception {
        // The rule lands FIRST (the command rides the tick lane); the
        // frozen value is then snapshotted from the same lane's view.
        setRule("doDaylightCycle", "false");
        long timeFrozen = server.world().timeOfDay();
        long totalBefore = server.world().totalTicks();
        // The total clock keeps walking (ServerWorld.tick line 190 runs
        // unconditionally); the day clock freezes (line 192's gate).
        await(() -> server.world().totalTicks() >= totalBefore + 40,
                "the total clock walks");
        assertEquals(timeFrozen, server.world().timeOfDay(),
                "the day clock froze");
        // The rule back on: the clock resumes.
        setRule("doDaylightCycle", "true");
        await(() -> server.world().timeOfDay() != timeFrozen,
                "the day clock resumed");
    }

    @Test
    void mobGriefingOffKeepsTheBlocksInTheBlast() throws Exception {
        // The control arm: the default-true rule's blast tears the
        // ground (the flat world's y=3 layer).
        PlayerSession walker = join("Walker", new Position(0.5, 5, 0.5));
        await(() -> walker.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        var firstCreeper = server.mobs().spawnAt(MobType.CREEPER,
                new Position(2.5, 5, 0.5));
        assertNotNull(firstCreeper, "the creeper spawned");
        AtomicReference<net.zaminmc.torch.event.world.ExplosionEvent> blast =
                new AtomicReference<>();
        var sub = ((Server) server).events().on(
                net.zaminmc.torch.event.world.ExplosionEvent.class, blast::set);
        await(() -> blast.get() != null, "the control blast detonated");
        await(() -> server.world().getBlock(new BlockPosition(0, 3, 0))
                .equals(BuiltinBlocks.AIR), "the control blast tore the ground");
        sub.unsubscribe();

        // The rule arm (CreeperEntity.explode line 208): the same blast
        // wounds but never breaks. The fresh sample sits on INTACT
        // ground away from the control blast's random crater: the
        // rule arm's subject is the BLAST's block preservation, not
        // the approach walk, so the second stage plays out on terrain
        // the first blast never touched — a fuse-range spawn on solid
        // ground detonates deterministically. (The historical
        // same-spot stage walked the creeper through the crater to a
        // player floating 4 blocks above a 3-deep pit, where the
        // prime gate's vertical 2.0 never engages — the pre-existing
        // flake the physics-solver diagnosis caught, both walk models
        // susceptible: the legacy walk even freezes a body inside a
        // solid cell with the velocity accumulating forever.)
        setRule("mobGriefing", "false");
        walker.applyMovement(new Position(8.5, 5.0, 8.5),
                Rotation.ZERO, true);
        BlockPosition groundSample = new BlockPosition(8, 4, 8);
        server.ticker().submit(() -> server.world().setBlock(groundSample,
                BuiltinBlocks.DIRT));
        await(() -> !server.world().getBlock(groundSample).equals(BuiltinBlocks.AIR),
                "the sample block stands");
        var creeper = server.mobs().spawnAt(MobType.CREEPER, new Position(9.5, 5, 9.5));
        assertNotNull(creeper, "the second creeper spawned");
        float healthBefore = walker.health();
        AtomicReference<net.zaminmc.torch.event.world.ExplosionEvent> blast2 =
                new AtomicReference<>();
        var sub2 = ((Server) server).events().on(
                net.zaminmc.torch.event.world.ExplosionEvent.class, blast2::set);
        await(() -> blast2.get() != null, "the blast still detonated under the rule");
        EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
        assertFalse(server.world().getBlock(groundSample).equals(BuiltinBlocks.AIR),
                "the blocks stayed under the rule");
        assertTrue(walker.health() < healthBefore || walker.dead(),
                "the player still took the blast");
        sub2.unsubscribe();
    }

    @Test
    void naturalRegenerationOffStopsTheFoodRegen() throws Exception {
        PlayerSession patient = join("Patient", new Position(0.5, 5, 0.5));
        await(() -> patient.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        // The rule off (HungerManager.tick line 41's gate): a well-fed
        // wounded body never heals.
        setRule("naturalRegeneration", "false");
        server.ticker().submit(() -> patient.setBody(10.0f, 20, 5.0f));
        EngineAwaits.awaitTicks(server.world(), 120, "the 80-tick regen window, tripled");
        assertEquals(10.0f, patient.health(), "the rule held the wounds");
        // The rule back on: the food regen resumes (the control arm).
        setRule("naturalRegeneration", "true");
        await(() -> patient.health() > 10.0f, "the regen resumed under the rule");
    }

    @Test
    void theTableSurvivesTheRestart() throws Exception {
        // The rule pairs land on a server whose world directory BOTH boots
        // share (the ZGR store lives under world/data — the world name is
        // the key, not the temp root).
        server.shutdown(null);
        EngineConfig first = new EngineConfig("127.0.0.1", 0,
                "grs-persist", "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer boot = new EngineServer(first);
        boot.start();
        try {
            var console = new RecordingConsole();
            boot.ticker().submit(() -> {
                boot.chatService().dispatchCommand(console, "/gamerule keepInventory true");
                boot.chatService().dispatchCommand(console, "/gamerule randomTickSpeed 17");
                boot.chatService().dispatchCommand(console, "/gamerule myCustomRule kept");
            });
            await(() -> console.replies.size() >= 3, "the three sets answered");
            boot.saveAllNow();
        } finally {
            boot.shutdown(null);
        }

        // A fresh server over the same world directory: the pairs
        // rehydrate (WorldData's own GameRules NBT round-trip).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "grs-persist", "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer second = new EngineServer(config);
        second.start();
        try {
            var rules = ((Server) second).gameRules();
            assertTrue(rules.getBoolean("keepInventory"), "the boolean pair survived");
            assertEquals(17, rules.getInt("randomTickSpeed"), "the numeric pair survived");
            assertEquals("kept", rules.get("myCustomRule"),
                    "the custom any-type pair survived");
            // The untouched rules keep their defaults.
            assertTrue(rules.getBoolean("doFireTick"));
            assertFalse(rules.getBoolean("reducedDebugInfo"));
        } finally {
            second.shutdown(null);
        }
    }

    @Test
    void theApiSurfaceReadsOffLaneAndRefusesOffLaneSets() throws Exception {
        var rules = ((Server) server).gameRules();
        // The reads are any-thread safe (the published snapshot): a
        // foreign thread sees the current table.
        setRule("doFireTick", "false");
        AtomicReference<Boolean> offLaneRead = new AtomicReference<>();
        Thread foreign = new Thread(() -> offLaneRead.set(rules.getBoolean("doFireTick")));
        foreign.start();
        foreign.join(5000);
        assertEquals(Boolean.FALSE, offLaneRead.get(), "the off-lane read is fresh");
        // The off-lane set: the loud ownership refusal (§27.6), never a
        // silent data race.
        assertThrows(IllegalStateException.class,
                () -> rules.set("doFireTick", "true"),
                "the off-lane set refused");
        assertFalse(rules.getBoolean("doFireTick"), "the refused set landed nothing");
        // The on-lane set through the same public surface works.
        server.ticker().submit(() -> rules.set("doFireTick", "true"));
        await(() -> rules.getBoolean("doFireTick"), "the on-lane set landed");
    }
}
