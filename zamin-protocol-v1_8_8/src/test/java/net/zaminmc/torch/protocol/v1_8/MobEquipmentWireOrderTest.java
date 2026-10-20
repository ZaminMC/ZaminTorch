package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mob armor family's wire (VIII4): the spawn publication and the
 * equipment-change re-send write ALL FIVE Entity Equipment (0x04) slots —
 * held first, then feet, legs, chest, head (the reference's
 * TrackedEntity.startTracking / LivingEntity.tick order) — verified over
 * the real wire through the scripted client: a summoned armored zombie
 * publishes its dice-shaped spawn batch first, then the /summon Equipment
 * rows' re-send batch, both in the five-slot order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MobEquipmentWireOrderTest extends ProtocolTestBase {

    @Test
    void theSummonedArmorLandsInHeldFirstOrder() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Armorist");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitCondition(() -> server.playerRegistry().byName("Armorist").isPresent(),
                    "the armorist joined");
            client.readWindowItems(10_000);
            // /summon asks for level 2 (the engine's own gate).
            server.playerRegistry().byName("Armorist").ifPresent(p -> p.setOpLevel(2));

            client.sendChat("/summon Zombie ~ ~ ~ {CustomName:\"WireArmor\","
                    + "Equipment:[{},{id:\"minecraft:diamond_boots\",Count:1b},"
                    + "{id:\"minecraft:diamond_leggings\",Count:1b},"
                    + "{id:\"minecraft:diamond_chestplate\",Count:1b},"
                    + "{id:\"minecraft:diamond_helmet\",Count:1b}]}");
            awaitCondition(() -> server.mobs().all().stream()
                            .anyMatch(mob -> "WireArmor".equals(mob.customName())),
                    "the armored zombie summoned");
            var zombie = server.mobs().all().stream()
                    .filter(mob -> "WireArmor".equals(mob.customName()))
                    .findFirst().orElseThrow();
            int entityId = zombie.entityId();
            int diamondBoots = LegacyBlockIds.legacyItemId(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:diamond_boots"))
                    .orElseThrow();
            int diamondLeggings = LegacyBlockIds.legacyItemId(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:diamond_leggings"))
                    .orElseThrow();
            int diamondChestplate = LegacyBlockIds.legacyItemId(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:diamond_chestplate"))
                    .orElseThrow();
            int diamondHelmet = LegacyBlockIds.legacyItemId(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:diamond_helmet"))
                    .orElseThrow();

            // Drain the wire for the body's 0x04 frames — the spawn batch
            // (the dice-shaped state at the spawn publication) then the
            // /summon rows' re-send batch — quiet-window bounded.
            client.setReadTimeout(600);
            List<int[]> frames = new ArrayList<>(); // {slot, itemId}
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                byte[] frame;
                try {
                    frame = client.readPacket();
                } catch (java.net.SocketTimeoutException quietWire) {
                    break;
                }
                if (client.readPacketId(frame) != Protocol18.S2C_ENTITY_EQUIPMENT) {
                    continue;
                }
                io.netty.buffer.ByteBuf body =
                        io.netty.buffer.Unpooled.wrappedBuffer(frame);
                ByteBufOps.readVarInt(body); // the packet id
                if (ByteBufOps.readVarInt(body) != entityId) {
                    continue; // another body's publication
                }
                int slot = body.readShort();
                int itemId = body.readShort();
                frames.add(new int[] {slot, itemId});
                if (frames.size() >= 10) {
                    break; // both five-frame batches collected
                }
            }
            assertTrue(frames.size() >= 10,
                    "both batches published (held " + frames.size() + " frames)");

            // Both batches walk the five slots in the reference's own
            // order — 0 (held), 1 (feet), 2 (legs), 3 (chest), 4 (head).
            for (int batch = 0; batch < 2; batch++) {
                for (int slot = 0; slot < 5; slot++) {
                    assertEquals(slot, frames.get(batch * 5 + slot)[0],
                            "batch " + batch + " frame " + slot + " names its own slot");
                }
            }
            // The re-send batch (the LAST five) carries the summoned rows:
            // the empty [0] row's -1 clear, then the four diamond pieces.
            assertEquals(-1, frames.get(5)[1], "the empty held row clears the wire slot");
            assertEquals(diamondBoots, frames.get(6)[1], "the feet frame");
            assertEquals(diamondLeggings, frames.get(7)[1], "the legs frame");
            assertEquals(diamondChestplate, frames.get(8)[1], "the chest frame");
            assertEquals(diamondHelmet, frames.get(9)[1], "the head frame");
        }
    }
}
