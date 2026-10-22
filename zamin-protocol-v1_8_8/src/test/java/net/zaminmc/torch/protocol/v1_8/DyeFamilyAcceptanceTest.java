package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The dye family over the real wire (Slice 30b): the sheep's index-16
 * nibble (the fleece's DyeColor id — the spawn byte, the dye arm's update,
 * and the sheared bit's carry), the dye arm's consume (ItemDye.interact
 * lines 124-138 — an unsheared coat of a different color takes the dye),
 and the shear's colored wool drops (the wool stack's damage IS the
 * body's color, the lamb never shears).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DyeFamilyAcceptanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    @Test
    void theSheepTakesTheDyeAndTheShearDropsTheColor() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Shepherd");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // Pen the sheep within the feeding reach (the horse pattern).
            var anchor = server.playerRegistry().byName("Shepherd").orElseThrow().position();
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
            client.sendChat("/spawnmob sheep 1");
            awaitCondition(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == net.zaminmc.torch.server.entity.MobType.SHEEP
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0),
                    "the commanded sheep registered near the anchor");
            var sheep = server.mobs().all().stream()
                    .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.SHEEP
                            && m.position().x() * m.position().x()
                            + m.position().z() * m.position().z() < 20.0)
                    .findFirst().orElseThrow();
            int sheepId = sheep.entityId();

            // The dye arm: hold lapis (minecraft:dye damage 4 — the blue
            // row of the ink-sac family) and use it on the sheep. The fleece
            // becomes BLUE — DyeColor id 11 (byMetadata(4) = BLUE).
            client.sendChat("/give minecraft:dye 8 4");
            EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
            client.sendUseEntity(sheepId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(sheepId).sheepFleeceColor() == 11,
                    "the lapis dyed the fleece blue (id 11)");
            // One dye consumed from the stack of 8 (ItemDye.interact's
            // size--, no creative guard) — the server-side hand is the
            // honest read (the wire's Set Slot rides the same state).
            assertEquals(7, server.playerRegistry().byName("Shepherd").orElseThrow()
                            .inventory().held().count(),
                    "one dye unit consumed by the arm");

            // The shear: the drops are wool of damage 11 — the blue row.
            // The shears land in the first empty hotbar slot (1 — the dye
            // holds slot 0); select it, then use it on the coat.
            client.sendChat("/give minecraft:shears 1");
            EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
            client.sendHeldItemChange(1);
            EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");
            client.sendUseEntity(sheepId, Protocol18.USE_ENTITY_INTERACT);
            awaitCondition(() -> server.mobs().byId(sheepId).sheared(),
                    "the shears stripped the coat");
            awaitCondition(() -> server.itemEntities().all().stream().anyMatch(item ->
                            item.stack().type() == net.zaminmc.torch.server.item.BuiltinItems.WOOL
                                    && item.stack().damage() == 11),
                    "blue wool dropped at the coat");
            int woolBlue = (int) server.itemEntities().all().stream()
                    .filter(item -> item.stack().type() == net.zaminmc.torch.server.item.BuiltinItems.WOOL
                            && item.stack().damage() == 11)
                    .count();
            assertTrue(woolBlue >= 1 && woolBlue <= 3,
                    "1-3 wool units per shear (the reference's roll): " + woolBlue);
        }
    }
}
