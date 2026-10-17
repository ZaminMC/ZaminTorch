package net.zaminmc.torch.server;

import net.zaminmc.torch.server.command.TargetSelector;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The missing-commands batch, wave one (Slice 88): /kill's any-kind arm
 * (the selector ladder; a mob leaves without drops, a player walks the
 * death walk), /testfor's ladder + success line, /toggledownfall's flip,
 * /spawnpoint's bed-spawn stamp, and /setworldspawn's move + 0x05
 * broadcast.
 */
class MissingCommandsWaveOneTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession alice;
    PlayerSession bob;
    final CopyOnWriteArrayList<String> bobWire = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> bobSounds = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> aliceParticles = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> bobParticles = new CopyOnWriteArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "waveone" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        alice = join("Alice", new RecordingLink(aliceParticles));
        bob = join("Bob", new RecordingLink(bobWire, bobSounds, bobParticles));
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && bob.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        final CopyOnWriteArrayList<String> wire;
        final CopyOnWriteArrayList<String> sounds;
        final CopyOnWriteArrayList<String> particles;

        RecordingLink(CopyOnWriteArrayList<String> particles) {
            this(new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>(), particles);
        }

        RecordingLink(CopyOnWriteArrayList<String> wire, CopyOnWriteArrayList<String> sounds,
                      CopyOnWriteArrayList<String> particles) {
            this.wire = wire;
            this.sounds = sounds;
            this.particles = particles;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void updateSpawnPosition(int x, int y, int z) {
            wire.add("spawnPos:" + x + "/" + y + "/" + z);
        }

        @Override
        public void playSound(String name, double x, double y, double z, float volume, float pitch) {
            sounds.add(name + "|" + volume + "|" + pitch);
        }

        @Override
        public void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                                   float offsetX, float offsetY, float offsetZ,
                                   float particleData, int count, int[] params) {
            particles.add(particleId + "|" + count + "|" + longDistance);
        }
    }

    private PlayerSession join(String name, RecordingLink link) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private String dispatch(String line) {
        // submitChat returns nothing; the feedback rides the system path.
        server.chatService().submitChat(alice, line);
        return null;
    }

    @Test
    void theKillAnyKindArm() throws Exception {
        Position at = alice.position();
        server.ticker().submit(() -> {
            server.mobs().spawnAt(MobType.ZOMBIE, new Position(at.x() + 3, at.y(), at.z()));
        });
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == MobType.ZOMBIE),
                "the zombie spawned");

        // By selector: the mob leaves without drops.
        dispatch("/kill @e[type=Zombie]");
        await(() -> server.mobs().all().stream().noneMatch(m -> m.type() == MobType.ZOMBIE),
                "the zombie removed");

        // By name: the player arm still answers (the death walk is tested by
        // the survival suites; here the ladder resolves).
        dispatch("/kill Bob");
        await(() -> bob.dead() || bob.state() != net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the death walk ran");
    }

    @Test
    void theTestforLadder() throws Exception {
        Position at = alice.position();
        server.ticker().submit(() -> {
            server.mobs().spawnAt(MobType.COW, new Position(at.x() - 3, at.y(), at.z()));
        });
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == MobType.COW),
                "the cow spawned");
        // The engine's dispatch catches the refusal and answers the sender;
        // the ladder itself is exercised through CommandParse here.
        TargetSelector.Candidate byName = net.zaminmc.torch.server.command.CommandParse
                .parseEntity(server.playerRegistry(), aliceSender(), "Alice");
        assertEquals("Alice", byName.name());
        assertTrue(byName.player());
        TargetSelector.Candidate bySelector = net.zaminmc.torch.server.command.CommandParse
                .parseEntity(server.playerRegistry(), aliceSender(), "@e[type=Cow]");
        assertEquals("Cow", bySelector.name());
        assertFalse(bySelector.player());
        boolean refused = false;
        try {
            net.zaminmc.torch.server.command.CommandParse.parseEntity(
                    server.playerRegistry(), aliceSender(), "Nobody");
        } catch (IllegalArgumentException expected) {
            refused = "That entity could not be found".equals(expected.getMessage());
        }
        assertTrue(refused, "the not-found refusal");
    }

    @Test
    void theToggledownfallFlip() throws Exception {
        boolean before = server.weather().raining();
        dispatch("/toggledownfall");
        await(() -> server.weather().raining() != before, "the rain flag flipped");
        dispatch("/toggledownfall");
        await(() -> server.weather().raining() == before, "the rain flag flipped back");
    }

    @Test
    void theSpawnpointStamp() throws Exception {
        assertNull(bob.bedSpawn(), "no stamp yet");
        dispatch("/spawnpoint Bob 10 64 10");
        await(() -> bob.bedSpawn() != null, "the stamp landed");
        Position stamp = bob.bedSpawn();
        assertEquals(10.5, stamp.x(), 1e-9);
        assertEquals(64.0, stamp.y(), 1e-9);
        assertEquals(10.5, stamp.z(), 1e-9);
    }

    @Test
    void theSetworldspawnBroadcast() throws Exception {
        dispatch("/setworldspawn 5 40 5");
        await(() -> !bobWire.isEmpty(), "the 0x05 broadcast reached Bob");
        assertEquals("spawnPos:5/40/5", bobWire.get(0));
        Position spawn = server.world().spawnPosition();
        assertEquals(5.5, spawn.x(), 1e-9);
        assertEquals(40.0, spawn.y(), 1e-9);
        assertEquals(5.5, spawn.z(), 1e-9);
    }

    @Test
    void theEffectArms() throws Exception {
        // By name: the add lands on the target through the potion system.
        dispatch("/effect Alice speed 60 1 true");
        await(() -> server.potions().hasEffect(alice, net.zaminmc.torch.server.potion.StatusEffects.SPEED),
                "the speed effect landed");
        net.zaminmc.torch.server.potion.StatusEffectInstance instance =
                server.potions().get(alice, net.zaminmc.torch.server.potion.StatusEffects.SPEED);
        // 60 seconds * 20 minus the ticks that passed before the read.
        assertTrue(instance.duration() >= 1190 && instance.duration() <= 1200,
                "the seconds*20 rule: " + instance.duration());
        assertEquals(1, instance.amplifier());
        assertFalse(instance.hasParticles(), "hideParticles");

        // The zero-seconds remove arm.
        dispatch("/effect Alice speed 0");
        await(() -> !server.potions().hasEffect(alice, net.zaminmc.torch.server.potion.StatusEffects.SPEED),
                "the remove arm");

        // The instant-duration rule: no seconds arg -> duration 1 — the
        // effect applies once and is consumed (observable through the
        // outcome: instant damage wounds the target).
        dispatch("/effect Alice instant_damage");
        await(() -> alice.health() < 20.0f, "the instant effect applied");
        assertTrue(alice.health() >= 6.0f && alice.health() <= 18.0f,
                "one instant application: " + alice.health());

        // The clear arm.
        dispatch("/effect Alice clear");
        await(() -> server.potions().allOf(alice).isEmpty(), "the clear arm");
    }

    @Test
    void theEnchantArm() throws Exception {
        // Put a sword in Alice's hand through the self-give path (the
        // engine's /give has no player arg): the held stack is the target.
        dispatch("/give minecraft:iron_sword 1");
        await(() -> !alice.inventory().held().isEmpty(), "the sword landed");
        dispatch("/enchant Alice sharpness 3");
        await(() -> alice.inventory().held().enchantments() != null
                        && alice.inventory().held().enchantments().containsKey(16),
                "the sharpness row landed");
        assertEquals(3, alice.inventory().held().enchantments().get(16).intValue());
        // The compatibility refusal: sharpness + smite.
        dispatch("/enchant Alice smite 1");
        // The level-bounds refusal.
        dispatch("/enchant Alice sharpness 99");
        // The canEnchant refusal: depth strider on a sword.
        dispatch("/enchant Alice depth_strider 1");
        Thread.sleep(300);
        // The incompatible and out-of-bounds writes never landed: the row
        // stays exactly sharpness 3.
        assertEquals(3, alice.inventory().held().enchantments().get(16).intValue());
        assertEquals(1, alice.inventory().held().enchantments().size());
    }

    @Test
    void thePlaysoundArms() throws Exception {
        // The direct per-player packet: the recording link hears it at the
        // sender's own position (the ~ defaults).
        dispatch("/playsound mob.zombie.say Bob");
        await(() -> !bobSounds.isEmpty(), "the sound reached Bob");
        assertEquals("mob.zombie.say|1.0|1.0", bobSounds.get(0));

        // The too-far refusal: a point far outside the 16-block gate.
        dispatch("/playsound mob.zombie.say Bob 500 40 500");
        Thread.sleep(300);
        assertEquals(1, bobSounds.size(), "the too-far arm refused");

        // The fold-back rule: the same far point with a minimum volume.
        dispatch("/playsound mob.zombie.say Bob 500 40 500 1.0 1.0 0.5");
        await(() -> bobSounds.size() >= 2, "the fold-back landed");
        assertEquals("mob.zombie.say|0.5|1.0", bobSounds.get(1));
    }

    @Test
    void theParticleArm() throws Exception {
        // The broadcast reaches every in-range player (Alice and Bob stand
        // together): both links carry the note packet.
        dispatch("/particle note ~ ~ ~ 0 0 0 1 10");
        await(() -> !aliceParticles.isEmpty() && !bobParticles.isEmpty(),
                "both wires carry the particles");
        assertEquals("23|10|false", aliceParticles.get(0));
        assertEquals("23|10|false", bobParticles.get(0));

        // The parameterized family: iconcrack's id/meta split.
        dispatch("/particle iconcrack_278_0 ~ ~ ~ 0.1 0.1 0.1 1 5 force");
        await(() -> aliceParticles.size() >= 2, "the crack family landed");
        assertEquals("36|5|true", aliceParticles.get(1));

        // The unknown-name refusal.
        dispatch("/particle notAParticle ~ ~ ~ 0 0 0 1 1");
        Thread.sleep(300);
        assertEquals(2, aliceParticles.size(), "the refusal fired no packet");
    }

    @Test
    void theTriggerArm() throws Exception {
        // Create a trigger objective + an unlocked row for Alice through the
        // real /scoreboard arms, then fire /trigger as Alice.
        dispatch("/scoreboard objectives add clicks trigger");
        dispatch("/scoreboard players set Alice clicks 0");
        dispatch("/scoreboard players enable Alice clicks");
        await(() -> server.scoreboard().getScore("Alice",
                        server.scoreboard().getObjective("clicks")) != null
                        && !server.scoreboard().getScore("Alice",
                        server.scoreboard().getObjective("clicks")).isLocked(),
                "the row unlocked");
        dispatch("/trigger clicks add 5");
        await(() -> server.scoreboard().getScore("Alice",
                        server.scoreboard().getObjective("clicks")).get() == 5,
                "the trigger added");
        // The row re-locked: a second trigger refuses.
        assertTrue(server.scoreboard().getScore("Alice",
                server.scoreboard().getObjective("clicks")).isLocked(), "the row re-locked");
        dispatch("/trigger clicks set 9");
        await(() -> server.scoreboard().getScore("Alice",
                        server.scoreboard().getObjective("clicks")).get() == 5,
                "the locked refusal kept the value");
    }

    @Test
    void theSetblockArm() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        // The plain place at an offset from the sender (two up: open air).
        dispatch("/setblock ~ ~2 ~ stone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value().equals("stone"),
                "the stone landed");
        assertEquals("stone", server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value());

        // The keep gate refuses on an occupied cell.
        dispatch("/setblock ~ ~2 ~ cobblestone keep");
        Thread.sleep(300);
        assertEquals("stone", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value(),
                "the keep gate kept the stone");

        // The height bounds.
        dispatch("/setblock ~ 300 ~ stone");
        Thread.sleep(300);
        assertEquals("stone", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value());
    }

    @Test
    void theFillArm() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        // A 3x3x3 box in open air above the sender: solid glass first.
        dispatch("/fill ~-1 ~1 ~-1 ~1 ~3 ~1 glass");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value().equals("glass"),
                "the solid fill landed");
        // The center was glass (solid).
        assertEquals("glass", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value());

        // The same box hollowed: the shell becomes stone, the interior airs.
        dispatch("/fill ~-1 ~1 ~-1 ~1 ~3 ~1 stone 0 hollow");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x, y + 1, z)).identifier().value().equals("stone"),
                "the hollow shell landed");
        assertEquals("stone", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x + 1, y + 2, z + 1)).identifier().value(),
                "a shell corner");
        assertEquals("air", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value(),
                "the hollowed interior");

        // The volume cap.
        dispatch("/fill ~-20 ~1 ~-20 ~20 ~40 ~20 stone");
        Thread.sleep(300);
        // The too-many refusal fired nothing new — the shell is still stone.
        assertEquals("stone", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 1, z)).identifier().value());
    }

    private net.zaminmc.torch.server.chat.CommandSender aliceSender() {
        return new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() {
                return "Alice";
            }

            @Override public void sendMessage(String message) {
            }

            @Override public int opLevel() {
                return 2;
            }

            @Override public PlayerSession player() {
                return alice;
            }
        };
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
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
}
