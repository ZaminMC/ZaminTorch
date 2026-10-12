package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mount contract over the real wire: the horse spawns with the 1.8
 * metadata block (the index-16 flag Int), the interact ladder tames and
 * seats (Attach Entity riding=1), the reins drive the body (Rel Move), the
 * unmount flag opens the seat, the saddle equips from the hand and the
 * sneak-open reads the EntityHorse inventory with the trailing mount id.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HorseIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    @Test
    void horseTamesSeatsDrivesAndOpensItsInventoryOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("HorseTamer");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // The tame ladder, deterministic: pen the horse within reach
            // (walls two high at Chebyshev 4, sealed below and above), feed
            // wheat to full temper (35 x +3, capped at 100), then one mount
            // attempt tames and seats (full temper always tames).
            var anchor = server.playerRegistry().byName("HorseTamer").orElseThrow().position();
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

            // Resolve the commanded horse server-side (the natural population
            // also rolls horses now — the maintainer's band is 8-20 blocks out,
            // the commanded one lands within 3, so the nearest HORSE wins and
            // the wire id equals the engine id for mobs).
            awaitCondition(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == net.zaminmc.torch.server.entity.MobType.HORSE
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0),
                    "the commanded horse registered near the anchor");
            int horseId = server.mobs().all().stream()
                    .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.HORSE
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0)
                    .findFirst().orElseThrow().entityId();

            // The metadata block: any type-100 spawn on the wire carries the
            // index-16 flag Int (the commanded one and the natural ones ride
            // the same writer).
            Object[] spawnFull = client.readSpawnMobWithWatcher(20_000, 100);
            int[][] watcher = (int[][]) spawnFull[1];
            boolean flagInt = false;
            for (int[] row : watcher) {
                if (row[0] == 16 && row[1] == Protocol18.METADATA_TYPE_INT) {
                    flagInt = true;
                }
            }
            assertTrue(flagInt, "the index-16 flag Int rides the horse spawn");

            // The feeding ladder: wheat in hand, every use +3 temper.
            client.sendChat("/give minecraft:wheat 64");
            Thread.sleep(200);
            for (int i = 0; i < 100 && server.mobs().byId(horseId).temper() < 100; i++) {
                client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
                Thread.sleep(60);
            }
            awaitCondition(() -> server.mobs().byId(horseId).temper() >= 100,
                    "the wheat ladder filled the temper");

            // The mount attempt: the hand empties first (a held temper food
            // keeps feeding — the feed branch owns the interaction while the
            // wheat lasts); the bare hand falls through to the seat. /clear
            // is op level 2, so the console op grants it first.
            server.consoleCommand("op HorseTamer");
            Thread.sleep(150);
            client.sendChat("/clear");
            Thread.sleep(200);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(horseId).tamed(),
                    "the full-temper attempt tamed the horse");
            int[] mount = client.readAttachEntity(20_000);
            assertEquals(horseId, mount[1], "the attach names the horse");
            assertEquals(1, mount[2], "the tame attempt ends in the seat");

            // The saddle goes on from the hand (the rider stays seated; the
            // vanilla right-click ladder equips before it seats).
            client.sendChat("/give minecraft:saddle 1");
            Thread.sleep(200);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(horseId).saddled(),
                    "the saddle equipped from the hand");

            // The reins: forward drives the body (Rel Move on the horse's id).
            boolean moved = false;
            for (int i = 0; i < 15 && !moved; i++) {
                client.sendSteerVehicle(0.0f, 1.0f, 0);
                Thread.sleep(80);
            }
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline && !moved) {
                int[] rel = client.readRelMoveLook(Math.max(1, deadline - System.currentTimeMillis()));
                if (rel[0] == horseId && (rel[1] != 0 || rel[3] != 0)) {
                    moved = true;
                }
            }
            assertTrue(moved, "the steered horse left its spot");

            // The unmount: the jump bit's 0x02 opens the seat.
            client.sendSteerVehicle(0.0f, 0.0f, 2);
            int[] dismount = client.readAttachEntity(20_000);
            assertEquals(0, dismount[2], "the seat cleared over the wire");

            // The sneak-open: the EntityHorse window with the trailing mount
            // id, the saddle sitting in slot 0. The saddle was consumed from
            // the hand above, so the window's slot 0 shows the mounted row.
            client.sendEntityAction(0); // start sneaking
            Thread.sleep(150);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            Object[] window = client.readOpenWindow(20_000);
            int windowId = (Integer) window[0];
            assertEquals("EntityHorse", window[1], "the horse window's legacy type string");
            int[][] table = client.readWindowSlotTable(20_000, windowId);
            assertEquals(38, table.length, "the horse window carries 38 slots");
            assertEquals(329, table[0][0], "the saddle sits in slot 0");
            client.sendEntityAction(1); // stop sneaking
        }
    }
}
