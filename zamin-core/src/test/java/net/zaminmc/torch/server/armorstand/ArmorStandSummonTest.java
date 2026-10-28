package net.zaminmc.torch.server.armorstand;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * The stand's summon surface (VT-7i — SummonCommand's Entities.create
 * + addEntity walk over the reference's EntityArmorStand): the plain
 * {@code /summon ArmorStand} (the default body — the plain-constructor
 * pose with NO random roll, the yaw 0, every flag down), the whole NBT
 * read (readCustomNbt lines 153-172 — the five status booleans, the
 * Invisible flag, the DisabledSlots bitfield, the Pose compound's
 * set-and-reset law, the Equipment list's own-index slots, the base
 * Motion/Rotation/Fire/OnGround rows), the base-flags byte (the fire
 * bit + the invisible bit), and the ZAD v3 round trip (the invisible
 * row).
 */
class ArmorStandSummonTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The plain summon (the default body)
    // ------------------------------------------------------------------

    @Test
    void thePlainSummonLandsTheDefaultBody() throws Exception {
        EngineServer server = boot();
        try {
            // A floor under the body: the plain constructor carries gravity
            // (every flag down) — the stand settles onto the stone's top
            // face exactly, onGround.
            place(server, 10, 39, 10, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");
            AtomicReference<ArmorStand> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.armorStands().summonAt(10.5, 40.0, 10.5, 0, null)));
            await(server, () -> spawned.get() != null, "the summon answered");
            ArmorStand stand = spawned.get();
            await(server, () -> stand.onGround() && stand.y() == 40.0,
                    "the body settled onto the floor");
            assertEquals(10.5, stand.x(), 1e-9);
            assertEquals(40.0, stand.y(), 1e-9);
            assertEquals(10.5, stand.z(), 1e-9);
            // The plain-constructor shape: the yaw 0, the DEFAULT pose
            // exactly (no random roll — the roll is the item path alone,
            // ArmorStandItem.pickRotation), every flag down.
            assertEquals(0.0f, stand.yaw(), 1e-6, "the plain constructor's yaw 0");
            assertEquals(ArmorStand.DEFAULT_HEAD, stand.head(), "the default head");
            assertEquals(ArmorStand.DEFAULT_BODY, stand.body(), "the default body");
            assertEquals(ArmorStand.DEFAULT_LEFT_ARM, stand.leftArm(), "the default left arm");
            assertEquals(ArmorStand.DEFAULT_RIGHT_ARM, stand.rightArm(), "the default right arm");
            assertEquals(ArmorStand.DEFAULT_LEFT_LEG, stand.leftLeg(), "the default left leg");
            assertEquals(ArmorStand.DEFAULT_RIGHT_LEG, stand.rightLeg(), "the default right leg");
            assertEquals(0, stand.status(), "every flag down");
            assertFalse(stand.invisible(), "the visible default");
            assertEquals(0, stand.disabledSlots(), "every swap legal");
            assertEquals(0, stand.flags(), "the base-flags byte empty");
        } finally {
            server.shutdown(() -> {
            });
        }
    }

    // ------------------------------------------------------------------
    // The NBT read (readCustomNbt 1:1)
    // ------------------------------------------------------------------

    /** The parsed compound of one SNBT text (the command's own parse). */
    private static java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parse(String snbt) {
        return net.zaminmc.torch.server.nbt.Snbt.parseCompound(snbt);
    }

    @Test
    void theStatusFlagsAndTheDisabledBitfieldReadTheirRows() throws Exception {
        EngineServer server = boot();
        try {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt =
                    parse("{Small:1b,ShowArms:1b,NoBasePlate:1b,Marker:1b,"
                            + "NoGravity:1b,Invisible:1b,DisabledSlots:65793}");
            AtomicReference<ArmorStand> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.armorStands().summonAt(10.5, 40.0, 10.5, 0, nbt)));
            await(server, () -> spawned.get() != null, "the summon answered");
            ArmorStand stand = spawned.get();
            // The status byte: small 0x01 | noGravity 0x02 | showArms 0x04
            // | noBasePlate 0x08 | marker 0x10 = 0x1F.
            assertEquals(0x1F, stand.status() & 0xFF, "the five status flags");
            assertTrue(stand.invisible(), "the Invisible row");
            assertEquals(65793, stand.disabledSlots(), "the DisabledSlots bitfield");
            // The base-flags byte: the invisible bit alone.
            assertEquals(0x20, stand.flags() & 0xFF, "the wire's slot-0 invisible bit");
        } finally {
            server.shutdown(() -> {
            });
        }
    }

    /**
     * The Pose compound's own law (setPose lines 175-218): a present
     * 3-float row sets its part, every MISSING part resetting to the
     * reference's own default (the else arms — a head-only pose never
     * keeps a previously-set arm).
     */
    @Test
    void thePoseCompoundSetsAndResets() throws Exception {
        EngineServer server = boot();
        try {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt =
                    parse("{Pose:{Head:[15f,25f,35f],LeftLeg:[2f,4f,6f]}}");
            AtomicReference<ArmorStand> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.armorStands().summonAt(10.5, 40.0, 10.5, 0, nbt)));
            await(server, () -> spawned.get() != null, "the summon answered");
            ArmorStand stand = spawned.get();
            assertEquals(new ArmorStand.Pose(15.0f, 25.0f, 35.0f), stand.head(),
                    "the present head row sets");
            assertEquals(new ArmorStand.Pose(2.0f, 4.0f, 6.0f), stand.leftLeg(),
                    "the present left-leg row sets");
            assertEquals(ArmorStand.DEFAULT_BODY, stand.body(), "the missing body resets");
            assertEquals(ArmorStand.DEFAULT_LEFT_ARM, stand.leftArm(), "the missing arm resets");
            assertEquals(ArmorStand.DEFAULT_RIGHT_ARM, stand.rightArm(), "the missing arm resets");
            assertEquals(ArmorStand.DEFAULT_RIGHT_LEG, stand.rightLeg(), "the missing leg resets");
        } finally {
            server.shutdown(() -> {
            });
        }
    }

    /**
     * The Equipment list (readCustomNbt lines 155-160): the list's own
     * index IS the slot — a sword at 0, a helmet at 4, an empty row
     * clearing its slot.
     */
    @Test
    void theEquipmentListRidesTheSlotIndexes() throws Exception {
        EngineServer server = boot();
        try {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt =
                    parse("{Equipment:[{id:\"minecraft:diamond_sword\",Count:1b},{},{},{},"
                            + "{id:\"minecraft:golden_helmet\",Count:1b}]}");
            AtomicReference<ArmorStand> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.armorStands().summonAt(10.5, 40.0, 10.5, 0, nbt)));
            await(server, () -> spawned.get() != null, "the summon answered");
            ArmorStand stand = spawned.get();
            assertEquals(BuiltinItems.DIAMOND_SWORD, stand.equipment(0).type(),
                    "the sword at the hand slot");
            assertTrue(stand.equipment(1).isEmpty(), "the empty row clears the boots");
            assertTrue(stand.equipment(2).isEmpty(), "the empty row clears the legs");
            assertTrue(stand.equipment(3).isEmpty(), "the empty row clears the chest");
            assertEquals(BuiltinItems.GOLDEN_HELMET, stand.equipment(4).type(),
                    "the helmet at the head slot");
        } finally {
            server.shutdown(() -> {
            });
        }
    }

    /**
     * The base rows (Entity.readNbt): the Rotation's yaw (the pitch row
     * consumed unused — the stand carries no pitch field), the Motion
     * triple, the Fire clock (the burn-down's own countdown walk), the
     * OnGround flag. The NoGravity row holds the body still — the
     * motion rows stay exactly what the summon read.
     */
    @Test
    void theBaseRowsReadTheMotionRotationFireAndGround() throws Exception {
        EngineServer server = boot();
        try {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt =
                    parse("{Rotation:[90f,0f],Motion:[0.1d,0.2d,0.3d],Fire:10s,"
                            + "OnGround:1b,NoGravity:1b}");
            AtomicReference<ArmorStand> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.armorStands().summonAt(10.5, 40.0, 10.5, 0, nbt)));
            await(server, () -> spawned.get() != null, "the summon answered");
            ArmorStand stand = spawned.get();
            assertEquals(90.0f, stand.yaw(), 1e-6, "the Rotation row's yaw");
            assertEquals(0.1, stand.velocityX(), 1e-9, "the Motion x");
            assertEquals(0.2, stand.velocityY(), 1e-9, "the Motion y");
            assertEquals(0.3, stand.velocityZ(), 1e-9, "the Motion z");
            assertTrue(stand.fireTicks() > 0 && stand.fireTicks() <= 10,
                    "the Fire clock rides (the burn-down counting: " + stand.fireTicks() + ")");
            assertTrue(stand.onGround(), "the OnGround flag");
            // The base-flags byte over the live fire clock: the 0x01 bit
            // while the clock still burns.
            assertEquals(0x01, stand.flags() & 0xFF, "the burning bit rides the wire byte");
        } finally {
            server.shutdown(() -> {
            });
        }
    }

    // ------------------------------------------------------------------
    // The ZAD v3 round trip
    // ------------------------------------------------------------------

    /** The invisible + marker + disabled stand rides the v3 row through the store. */
    @Test
    void theV3RowCarriesTheInvisibleFlagThroughTheStore() throws Exception {
        EngineServer server = boot();
        Path store = dataDir.resolve("zad-v3").resolve("armorstands.bin");
        try {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> nbt =
                    parse("{Invisible:1b,Marker:1b,ShowArms:1b,DisabledSlots:4145,"
                            + "Pose:{Body:[5f,10f,15f]}}");
            AtomicReference<ArmorStand> spawned = new AtomicReference<>();
            server.ticker().submit(() -> spawned.set(
                    server.armorStands().summonAt(20.5, 40.0, 20.5, 0, nbt)));
            await(server, () -> spawned.get() != null, "the summon answered");
            AtomicReference<List<ArmorStandSystem.StandRecord>> snapshot =
                    new AtomicReference<>();
            server.ticker().submit(() -> snapshot.set(server.armorStands().snapshot()));
            await(server, () -> snapshot.get() != null, "the snapshot taken");
            new ArmorStandDataStore(store).save(snapshot.get());
            assertTrue(Files.exists(store), "the store wrote");
        } finally {
            server.shutdown(() -> {
            });
        }

        List<ArmorStandSystem.StandRecord> loaded = new ArmorStandDataStore(store).load();
        assertEquals(1, loaded.size());
        ArmorStandSystem.StandRecord record = loaded.get(0);
        assertEquals(20.5, record.x(), 1e-9);
        assertTrue(record.invisible(), "the invisible row rides the v3 store");
        // The status: marker 0x10 | showArms 0x04 = 0x14.
        assertEquals(0x14, record.status() & 0xFF, "the status flags ride");
        assertEquals(4145, record.disabledSlots(), "the bitfield rides");
        assertEquals(new ArmorStand.Pose(5.0f, 10.0f, 15.0f), record.body(),
                "the pose rides");
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "asum" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    /** The block-place helper (the stand tests' own shape). */
    private static void place(EngineServer server, int x, int y, int z,
                              net.zaminmc.torch.block.BlockType type) {
        server.ticker().submit(() -> server.world().setBlock(
                new net.zaminmc.torch.block.BlockPosition(x, y, z), type));
    }

    private static void await(EngineServer server, BooleanSupplier condition,
                              String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
