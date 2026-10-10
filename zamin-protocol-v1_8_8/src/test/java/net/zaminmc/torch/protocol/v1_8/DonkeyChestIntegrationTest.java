package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The donkey chest + love burst over the real wire (the HorseMenu layout):
 * the chest equips a tamed donkey from the hand (the flag rides the index-16
 * Int), the sneak-open grows the EntityHorse window to the vanilla 53 slots
 * (the Open Window count is the full menu size — HorseMenu.inventorySlots),
 * the 3x5 grid plays the shared cursor semantics (place from the window's
 * own player tail, the resync shows the stack in the grid row), and the
 * love burst rides Entity Status 18 when wheat lands on a cow.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DonkeyChestIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    @Test
    void chestedDonkeyOpensThe53SlotWindowAndStoresStacks() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("PackMule");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            client.readWindowItems(5_000); // join sync

            var anchor = server.playerRegistry().byName("PackMule").orElseThrow().position();
            int ax = (int) Math.floor(anchor.x());
            int ay = (int) Math.floor(anchor.y());
            int az = (int) Math.floor(anchor.z());
            var wall = net.zaminmc.torch.server.block.BuiltinBlocks.COBBLESTONE;
            server.ticker().submit(() -> {
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != 4) {
                            continue;
                        }
                        for (int dy = -2; dy <= 3; dy++) {
                            server.world().setBlock(
                                    new net.zaminmc.torch.block.BlockPosition(ax + dx, ay + dy, az + dz),
                                    wall);
                        }
                    }
                }
            });
            awaitCondition(() -> server.world().getBlock(
                    new net.zaminmc.torch.block.BlockPosition(ax + 4, ay, az)).equals(wall),
                    "pen built");
            client.sendChat("/spawnmob horse 1");
            awaitCondition(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == net.zaminmc.torch.server.entity.MobType.HORSE
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0),
                    "the commanded horse registered near the anchor");
            var mount = server.mobs().all().stream()
                    .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.HORSE
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0)
                    .findFirst().orElseThrow();
            int horseId = mount.entityId();
            // The test pins the long ears (the wild roll is 1-in-5 donkey);
            // the wire id equals the engine id for mobs.
            server.ticker().submit(() -> mount.setHorseSubtype(
                    net.zaminmc.torch.server.entity.MobEntity.HORSE_SUBTYPE_DONKEY));

            // The tame ladder (wheat +3 temper, the same flow the horse test drives).
            client.sendChat("/give minecraft:wheat 64");
            Thread.sleep(200);
            for (int i = 0; i < 100 && server.mobs().byId(horseId).temper() < 100; i++) {
                client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
                Thread.sleep(60);
            }
            awaitCondition(() -> server.mobs().byId(horseId).temper() >= 100,
                    "the wheat ladder filled the temper");
            server.consoleCommand("op PackMule");
            Thread.sleep(150);
            client.sendChat("/clear");
            Thread.sleep(200);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(horseId).tamed(),
                    "the full-temper attempt tamed the donkey");
            client.readAttachEntity(20_000);

            // The chest equips from the hand (the HorseBaseEntity chest arm).
            client.sendChat("/give minecraft:chest 1");
            Thread.sleep(200);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(horseId).chested(),
                    "the chest equipped on the donkey");
            assertTrue((server.mobs().byId(horseId).horseFlagsRaw() & 0x08) != 0,
                    "the flag bit the client renders the chest from");

            // The sneak-open: the 53-slot EntityHorse window. A fresh wheat
            // stack rides along (the /clear emptied the ladder's stack).
            client.sendChat("/give minecraft:wheat 64");
            Thread.sleep(200);
            client.sendEntityAction(0); // start sneaking
            Thread.sleep(150);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            Object[] window = client.readOpenWindow(20_000);
            int windowId = (Integer) window[0];
            assertEquals("EntityHorse", window[1], "the horse window's legacy type string");
            assertEquals(53, window[2], "the Open Window count is the full menu size");
            int[][] table = client.readWindowSlotTable(20_000, windowId);
            assertEquals(53, table.length, "the chested window carries 53 slots");
            assertEquals(-1, table[2][0], "the chest grid starts empty");

            // The grid takes a stack: grab the wheat from the player tail
            // (the /give lands in hotbar engine 0 = wire 44 on the chested
            // window) and drop it into chest slot 2.
            client.sendWindowClick(windowId, 44, 0, 0, 0);
            client.readConfirmTransaction(10_000);
            client.readCursorSlot(10_000);
            client.sendWindowClick(windowId, 2, 0, 0, 1);
            client.readConfirmTransaction(10_000);
            client.readCursorSlot(10_000);
            int[][] after = client.readWindowSlotTable(10_000, windowId);
            assertEquals(53, after.length, "the window stays 53 wide");
            assertEquals(296, after[2][0], "the wheat sits in chest slot 2");
            assertEquals(64, after[2][1], "the whole stack moved");
            assertEquals(-1, after[44][0], "the hotbar tail slot emptied");
            client.sendEntityAction(1); // stop sneaking
        }
    }

    @Test
    void wheatOnACowBurstsHeartsOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("CowWisperer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            client.readWindowItems(5_000);

            client.sendChat("/give minecraft:wheat 8");
            client.sendChat("/spawnmob cow 1");
            awaitCondition(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == net.zaminmc.torch.server.entity.MobType.COW
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0),
                    "the commanded cow registered near the anchor");
            int cowId = server.mobs().all().stream()
                    .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.COW
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0)
                    .findFirst().orElseThrow().entityId();

            client.sendUseEntity(cowId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(cowId).isInLove(),
                    "the wheat started the love window");
            int[] status = client.readEntityStatus(10_000);
            assertEquals(cowId, status[0], "the burst names the loved cow");
            assertEquals(Protocol18.ENTITY_STATUS_LOVE, status[1], "event 18 is the heart burst");
        }
    }
}
