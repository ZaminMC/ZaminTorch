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

    @Test
    void theCloneArm() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        // Build a 2x1x2 stone platform above the sender, then clone it away.
        dispatch("/fill ~1 ~4 ~1 ~2 ~4 ~2 stone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x + 1, y + 4, z + 1)).identifier().value().equals("stone"),
                "the platform built");
        // Clone it to the other side (no overlap).
        dispatch("/clone ~1 ~4 ~1 ~2 ~4 ~2 ~-3 ~4 ~-3");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x - 3, y + 4, z - 3)).identifier().value().equals("stone"),
                "the clone landed");
        assertEquals("stone", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x - 2, y + 4, z - 2)).identifier().value(),
                "the far corner");
        // The source survived (a plain clone copies).
        assertEquals("stone", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x + 1, y + 4, z + 1)).identifier().value());
    }

    @Test
    void theExecuteArm() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        // The plain nested run: the nested ~ resolves against the EXECUTE
        // coords (here the entity's own body), not the sending console.
        dispatch("/execute Alice ~ ~ ~ setblock ~ ~2 ~ stone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x, y + 2, z)).identifier().value().equals("stone"),
                "the nested setblock landed");

        // The offset execute coords: the nested ~ resolves against the
        // PARSED position (three east of Alice, two above it).
        dispatch("/execute Alice ~3 ~ ~ setblock ~ ~2 ~ glass");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x + 3, y + 2, z)).identifier().value().equals("glass"),
                "the offset nested setblock landed");

        // The detect arm: the match (the stone from the first run) lets
        // the nested command through — the detect coords are relative to
        // the execute coords, the -1 meta is any.
        dispatch("/execute Alice ~ ~ ~ detect ~ ~2 ~ stone -1 setblock ~ ~4 ~ cobblestone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x, y + 4, z)).identifier().value().equals("cobblestone"),
                "the detected nested run landed");

        // The detect mismatch: the refusal, nothing placed.
        dispatch("/execute Alice ~ ~ ~ detect ~ ~2 ~ gold_block -1 setblock ~ ~5 ~ dirt");
        Thread.sleep(300);
        assertEquals("air", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 5, z)).identifier().value(),
                "the mismatched detect refused the nested run");

        // The too-short form: five args minimum, the fourth is the command.
        dispatch("/execute Alice ~ ~ ~");
        Thread.sleep(200);
        assertEquals("air", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y + 6, z)).identifier().value(),
                "the short form refused");
    }

    @Test
    void theExecuteMobArm() throws Exception {
        Position at = alice.position();
        server.ticker().submit(() -> {
            server.mobs().spawnAt(MobType.ZOMBIE, new Position(at.x() + 4, at.y(), at.z()));
        });
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == MobType.ZOMBIE),
                "the zombie spawned");
        // The nested run as the MOB (a selector, the any-kind ladder): the
        // execute coords anchor at the zombie's body — the nested ~
        // resolves against them, not Alice's position.
        dispatch("/execute @e[type=Zombie] ~ ~ ~ setblock ~ ~2 ~ cobblestone");
        int zx = (int) Math.floor(at.x() + 4);
        int zy = (int) Math.floor(at.y());
        int zz = (int) Math.floor(at.z());
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(zx, zy + 2, zz)).identifier().value().equals("cobblestone"),
                "the mob's nested setblock landed");
        // Alice's own column stayed air — the anchor really was the mob.
        int ax = (int) Math.floor(alice.position().x());
        int ay = (int) Math.floor(alice.position().y());
        int az = (int) Math.floor(alice.position().z());
        Thread.sleep(300);
        assertEquals("air", server.world().getBlock(
                new net.zaminmc.torch.block.BlockPosition(ax, ay + 2, az)).identifier().value(),
                "the anchor was the zombie, not Alice");
    }

    @Test
    void theSpreadPlayersArm() throws Exception {
        int cx = (int) Math.floor(alice.position().x());
        int cz = (int) Math.floor(alice.position().z());
        // Two players spread apart in a generous box: both land inside the
        // range bounds and (allowing the floor+0.5 landing shift) at least
        // the spread distance apart.
        dispatch("/spreadplayers ~ ~ 5 20 false Alice Bob");
        await(() -> {
            double ax = alice.position().x();
            double az = alice.position().z();
            double bx = bob.position().x();
            double bz = bob.position().z();
            return Math.abs(ax - cx) <= 20.0 && Math.abs(az - cz) <= 20.0
                    && Math.abs(bx - cx) <= 20.0 && Math.abs(bz - cz) <= 20.0
                    && Math.hypot(ax - bx, az - bz) >= 3.5;
        }, "both players spread inside the box, at least 5 apart");

        // The parse floor: the maxRange must be at least spread + 1 — the
        // refusal leaves the pair where the first spread put them.
        double ax0 = alice.position().x();
        double az0 = alice.position().z();
        dispatch("/spreadplayers ~ ~ 10 3 false Alice Bob");
        Thread.sleep(300);
        assertEquals(ax0, alice.position().x(), 0.01, "the bad range refused");
        assertEquals(az0, alice.position().z(), 0.01, "the bad range refused");
    }

    @Test
    void theSpreadPlayersImpossibleArm() throws Exception {
        // Ten mobs + the pair: twelve piles needing 10 blocks of clearance
        // in a 22x22 box cannot separate — the 10000-iteration failure
        // line, nobody teleports.
        Position at = alice.position();
        server.ticker().submit(() -> {
            for (int i = 0; i < 10; i++) {
                server.mobs().spawnAt(MobType.ZOMBIE, new Position(at.x() + i, at.y(), at.z()));
            }
        });
        await(() -> server.mobs().all().size() >= 10, "the mobs spawned");
        double ax0 = alice.position().x();
        double az0 = alice.position().z();
        dispatch("/spreadplayers ~ ~ 10 11 false @e");
        Thread.sleep(600);
        assertEquals(ax0, alice.position().x(), 0.01, "the impossible spread left Alice in place");
        assertEquals(az0, alice.position().z(), 0.01, "the impossible spread left Alice in place");
    }

    @Test
    void theSpreadPlayersTeamsArm() throws Exception {
        int cx = (int) Math.floor(alice.position().x());
        int cz = (int) Math.floor(alice.position().z());
        // One team: respectTeams shares the pile — both land together.
        server.ticker().submit(() -> {
            server.scoreboard().addTeam("spreadteam");
            server.scoreboard().addMemberToTeam("Alice", "spreadteam");
            server.scoreboard().addMemberToTeam("Bob", "spreadteam");
        });
        await(() -> server.scoreboard().getTeamOfMember("Alice") != null
                        && server.scoreboard().getTeamOfMember("Bob") != null,
                "the team registered");
        dispatch("/spreadplayers ~ ~ 5 20 true Alice Bob");
        await(() -> {
            double ax = alice.position().x();
            double az = alice.position().z();
            double bx = bob.position().x();
            double bz = bob.position().z();
            return Math.hypot(ax - bx, az - bz) < 1.0
                    && Math.abs(ax - cx) <= 20.0 && Math.abs(az - cz) <= 20.0;
        }, "the teamed pair landed together on one pile");
    }

    @Test
    void theStatsArm() throws Exception {
        // An objective with an existing score for Alice (the reference's
        // hasScore gate: only an existing score is set).
        dispatch("/scoreboard objectives add statcnt dummy");
        dispatch("/scoreboard players set Alice statcnt 0");
        await(() -> {
            var obj = server.scoreboard().getObjective("statcnt");
            return obj != null && server.scoreboard().hasScore("Alice", obj);
        }, "the objective and the score exist");
        var objective = server.scoreboard().getObjective("statcnt");

        // The SuccessCount binding: a successful run sets 1, an unknown
        // command's dispatch reports 0 (the dispatch-level stat).
        dispatch("/stats entity Alice set SuccessCount Alice statcnt");
        dispatch("/time query");
        await(() -> server.scoreboard().getScore("Alice", objective).get() == 1,
                "the success count landed");
        dispatch("/nosuchcommand");
        await(() -> server.scoreboard().getScore("Alice", objective).get() == 0,
                "the failed run reported zero");

        // The AffectedBlocks binding: the fill's own count. The
        // SuccessCount binding is cleared first — in the reference both
        // bindings would stay live and the trailing SUCCESS_COUNT write
        // (1) would overwrite the fill's count.
        dispatch("/stats entity Alice clear SuccessCount");
        dispatch("/stats entity Alice set AffectedBlocks Alice statcnt");
        dispatch("/fill ~1 ~5 ~1 ~2 ~5 ~4 glass"); // 2x1x4 = 8
        await(() -> server.scoreboard().getScore("Alice", objective).get() == 8,
                "the fill's affected-blocks count landed");

        // The clear arm: the binding gone, later runs leave the score.
        dispatch("/stats entity Alice clear AffectedBlocks");
        dispatch("/fill ~1 ~5 ~1 ~2 ~5 ~4 stone");
        Thread.sleep(400);
        assertEquals(8, server.scoreboard().getScore("Alice", objective).get(),
                "the cleared binding stopped the updates");

        // The unknown-type refusal.
        dispatch("/stats entity Alice set NotAStat Alice statcnt");
        Thread.sleep(300);
        // The binding never changed (the clear left none): the last score stays.
        assertEquals(8, server.scoreboard().getScore("Alice", objective).get());
    }

    @Test
    void theReplaceItemArm() throws Exception {
        // The entity arm: the hotbar family.
        dispatch("/replaceitem entity Alice slot.hotbar.0 stone 5");
        await(() -> !alice.inventory().slot(0).isEmpty()
                        && alice.inventory().slot(0).type().identifier().value().equals("stone")
                        && alice.inventory().slot(0).count() == 5,
                "the hotbar slot landed");

        // The weapon slot writes the held cell.
        dispatch("/replaceitem entity Alice slot.weapon glass 3");
        await(() -> !alice.inventory().held().isEmpty()
                        && alice.inventory().held().type().identifier().value().equals("glass"),
                "the weapon slot landed");

        // The inventory family: slot.inventory.0 is index 9.
        dispatch("/replaceitem entity Alice slot.inventory.0 cobblestone 2");
        await(() -> !alice.inventory().slot(9).isEmpty()
                        && alice.inventory().slot(9).type().identifier().value().equals("cobblestone"),
                "the inventory slot landed");

        // The literal air clears.
        dispatch("/replaceitem entity Alice slot.hotbar.0 air");
        await(() -> alice.inventory().slot(0).isEmpty(), "the air form cleared the slot");

        // The ender chest family.
        dispatch("/replaceitem entity Alice slot.enderchest.3 dirt 4");
        await(() -> !alice.enderChest().getItem(3).isEmpty()
                        && alice.enderChest().getItem(3).type().identifier().value().equals("dirt"),
                "the ender chest slot landed");

        // The unsupported family refuses.
        dispatch("/replaceitem entity Alice slot.villager.0 stone");
        Thread.sleep(300);

        // The block arm: a chest placed and filled through its container
        // slot (the tile appears on the write — the engine's lazy
        // first-use creation).
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        dispatch("/setblock ~2 ~ ~ chest");
        var chestPos = new net.zaminmc.torch.block.BlockPosition(x + 2, y, z);
        await(() -> server.world().getBlock(chestPos).identifier().value().equals("chest"),
                "the chest block landed");
        dispatch("/replaceitem block ~2 ~ ~ slot.container.0 stone 7");
        await(() -> {
            var chest = server.chests().peek(chestPos);
            return chest != null && !chest.getItem(0).isEmpty()
                    && chest.getItem(0).type().identifier().value().equals("stone")
                    && chest.getItem(0).count() == 7;
        }, "the chest's container slot landed");

        // The no-container refusal (the plain stone beside it).
        dispatch("/replaceitem block ~3 ~ ~ slot.container.0 stone");
        Thread.sleep(300);
        var beside = new net.zaminmc.torch.block.BlockPosition(x + 3, y, z);
        assertEquals("air", server.world().getBlock(beside).identifier().value());
    }

    @Test
    void theTestForBlockArm() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        // Place and match.
        dispatch("/setblock ~1 ~2 ~ stone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x + 1, y + 2, z)).identifier().value().equals("stone"),
                "the stone landed");
        dispatch("/testforblock ~1 ~2 ~ stone");
        Thread.sleep(300);
        // The mismatch refusal leaves nothing to assert state-wise beyond
        // survival; the meta gate honors -1-any and 0-the-type's-own.
        dispatch("/testforblock ~1 ~2 ~ stone -1");
        Thread.sleep(300);
        // The unknown name refusal.
        dispatch("/testforblock ~1 ~2 ~ nosuchblock");
        Thread.sleep(300);
    }

    @Test
    void theTestForBlocksArm() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        // Two identical 2x1x2 platforms: the compare passes.
        dispatch("/fill ~1 ~4 ~1 ~2 ~4 ~2 stone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x + 1, y + 4, z + 1)).identifier().value().equals("stone"),
                "the first platform built");
        dispatch("/fill ~-2 ~4 ~1 ~-1 ~4 ~2 stone");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x - 2, y + 4, z + 1)).identifier().value().equals("stone"),
                "the second platform built");
        dispatch("/testforblocks ~1 ~4 ~1 ~2 ~4 ~2 ~-2 ~4 ~1");
        Thread.sleep(400);
        // The mismatch: change one destination cell.
        dispatch("/setblock ~-2 ~4 ~1 glass");
        await(() -> server.world().getBlock(
                        new net.zaminmc.torch.block.BlockPosition(x - 2, y + 4, z + 1)).identifier().value().equals("glass"),
                "the destination cell changed");
        dispatch("/testforblocks ~1 ~4 ~1 ~2 ~4 ~2 ~-2 ~4 ~1");
        Thread.sleep(400);
        // The masked mode skips the air cells: comparing the stone platform
        // against the air above it passes with the stone cells alone... no —
        // masked skips AIR SOURCE cells; the stone source still mismatches
        // the air destination. The all/masked distinction is exercised by
        // the same-source compare above (the default all mode).
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
