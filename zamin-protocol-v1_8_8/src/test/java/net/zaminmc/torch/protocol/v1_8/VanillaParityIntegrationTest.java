package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.entity.Player;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla-parity of the serverbound id table: every play packet the real
 * 1.8.8 client can emit is sent here with its exact wire shape (per the
 * community protocol.json) and the connection must survive with the keep-alive
 * exchange still working afterwards. This is the regression net for the id
 * table: one misdeclared id silently reroutes vanilla traffic into the wrong
 * decoder, which real players experience as an inexplicable "protocol error"
 * disconnect minutes (or seconds) into play.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VanillaParityIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 2_000; // the scripted client answers only when the test drives it
    }

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            PlayerSession session = server.playerRegistry().byName(name).orElse(null);
            if (session != null) {
                return session;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Player " + name + " never registered");
    }

    private static byte[] body(Consumer<ByteBuf> writer) {
        ByteBuf buf = Unpooled.buffer(64);
        writer.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    private static Consumer<ByteBuf> packet(int id, Consumer<ByteBuf> fields) {
        return buf -> {
            ByteBufOps.writeVarInt(buf, id);
            fields.accept(buf);
        };
    }

    @Test
    void fullVanillaServerboundTourKeepsConnectionAlive() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Parity");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            PlayerSession parity = awaitPlayer("Parity");
            awaitCondition(() -> parity.state() == PlayerState.PLAYING, "player reached PLAYING");

            // 0x15 Client Settings — the exact 1.8 shape (no main-hand field).
            client.sendPacket(body(packet(Protocol18.C2S_CLIENT_SETTINGS, buf -> {
                ByteBufOps.writeString(buf, "en_US");
                buf.writeByte(12);
                ByteBufOps.writeVarInt(buf, 0);
                buf.writeBoolean(true);
                buf.writeByte(127);
            })));

            // 0x17 Plugin Message — the MC|Brand announcement.
            client.sendPacket(body(packet(Protocol18.C2S_PLUGIN_MESSAGE, buf -> {
                ByteBufOps.writeString(buf, "MC|Brand");
                ByteBufOps.writeString(buf, "vanilla");
            })));

            // 0x03 flying — the every-tick ground packet.
            client.sendPacket(body(packet(Protocol18.C2S_PLAYER, buf -> buf.writeBoolean(true))));

            // 0x0B Entity Action (sneak, sprint, jump — jump boost field present).
            for (int action : new int[]{0, 1, 4}) {
                client.sendPacket(body(packet(Protocol18.C2S_ENTITY_ACTION, buf -> {
                    ByteBufOps.writeVarInt(buf, 1); // any entity id: this slice consumes the shape
                    ByteBufOps.writeVarInt(buf, action);
                    ByteBufOps.writeVarInt(buf, 0);
                })));
            }

            // 0x0C Steer Vehicle.
            client.sendPacket(body(packet(Protocol18.C2S_STEER_VEHICLE, buf -> {
                buf.writeFloat(0.5f);
                buf.writeFloat(-0.5f);
                buf.writeByte(0);
            })));

            // 0x0F Confirm Transaction — window 1, action 7, accepted.
            client.sendPacket(body(packet(Protocol18.C2S_CONFIRM_TRANSACTION, buf -> {
                buf.writeByte(1);
                buf.writeShort(7);
                buf.writeBoolean(true);
            })));

            // 0x10 Set Creative Slot — empty, plain stack, and a full NBT stack.
            client.sendPacket(body(packet(Protocol18.C2S_SET_CREATIVE_SLOT, buf -> {
                buf.writeShort(36);
                buf.writeShort(-1);
            })));
            client.sendPacket(body(packet(Protocol18.C2S_SET_CREATIVE_SLOT, buf -> {
                buf.writeShort(37);
                buf.writeShort(1);
                buf.writeByte(32);
                buf.writeShort(0);
                buf.writeByte(0);
            })));
            client.sendPacket(body(packet(Protocol18.C2S_SET_CREATIVE_SLOT, buf -> {
                buf.writeShort(38);
                buf.writeShort(1);
                buf.writeByte(1);
                buf.writeShort(0);
                buf.writeByte(0x0A); // TAG_Compound marker; the rest of the
                ByteBufOps.writeString(buf, ""); // compound stays unread: frames
                ByteBufOps.writeString(buf, "Named"); // are length-delimited
                buf.writeByte(0);
            })));

            // 0x11 Enchant Item.
            client.sendPacket(body(packet(Protocol18.C2S_ENCHANT_ITEM, buf -> {
                buf.writeByte(1);
                buf.writeByte(3);
            })));

            // 0x12 Update Sign.
            client.sendPacket(body(packet(Protocol18.C2S_UPDATE_SIGN, buf -> {
                buf.writeLong(0x00_00_00_00_00_00_00_04L);
                for (int line = 0; line < 4; line++) {
                    ByteBufOps.writeString(buf, "line" + line);
                }
            })));

            // 0x13 Player Abilities (client echo: flags + speeds).
            client.sendPacket(body(packet(Protocol18.C2S_PLAYER_ABILITIES, buf -> {
                buf.writeByte(0);
                buf.writeFloat(0.05f);
                buf.writeFloat(0.1f);
            })));

            // 0x14 Tab Complete — with and without the looked-at block option.
            client.sendPacket(body(packet(Protocol18.C2S_TAB_COMPLETE, buf -> {
                ByteBufOps.writeString(buf, "/hel");
            })));
            client.sendPacket(body(packet(Protocol18.C2S_TAB_COMPLETE, buf -> {
                ByteBufOps.writeString(buf, "/hel");
                buf.writeBoolean(true);
                buf.writeLong(0x00_00_00_00_00_00_00_00L);
            })));

            // 0x18 Spectate + 0x19 Resource Pack Status.
            client.sendPacket(body(packet(Protocol18.C2S_SPECTATE, buf -> {
                buf.writeLong(parity.uuid().getMostSignificantBits());
                buf.writeLong(parity.uuid().getLeastSignificantBits());
            })));
            client.sendPacket(body(packet(Protocol18.C2S_RESOURCE_PACK_STATUS, buf -> {
                ByteBufOps.writeVarInt(buf, 3); // accepted
            })));

            // 0x0A Arm Animation — no fields in 1.8.
            client.sendPacket(body(packet(Protocol18.C2S_ARM_ANIMATION, buf -> {
            })));

            // 0x09 Held Item Change.
            client.sendPacket(body(packet(Protocol18.C2S_HELD_ITEM_CHANGE, buf -> buf.writeShort(2))));

            // 0x08 Block Placement — the air-use sentinel, a normal placement,
            // and a placement claiming an NBT-carrying held stack.
            client.sendPacket(body(packet(Protocol18.C2S_PLAYER_BLOCK_PLACEMENT, buf -> {
                buf.writeLong(-1L);
                buf.writeByte(255);
                buf.writeShort(-1);
            })));
            client.sendPacket(body(packet(Protocol18.C2S_PLAYER_BLOCK_PLACEMENT, buf -> {
                buf.writeLong(0x00_00_00_00_00_00_00_02L);
                buf.writeByte(1);
                buf.writeShort(0x0101); // legacy stone
                buf.writeByte(32);
                buf.writeShort(0);
                buf.writeByte(0);
                buf.writeByte(8);
                buf.writeByte(8);
                buf.writeByte(8);
            })));
            client.sendPacket(body(packet(Protocol18.C2S_PLAYER_BLOCK_PLACEMENT, buf -> {
                buf.writeLong(0x00_00_00_00_00_00_00_02L);
                buf.writeByte(1);
                buf.writeShort(0x0101);
                buf.writeByte(1);
                buf.writeShort(0);
                buf.writeByte(0x0A);
                ByteBufOps.writeString(buf, "");
                ByteBufOps.writeString(buf, "Cool");
                buf.writeByte(0);
                buf.writeByte(4);
                buf.writeByte(4);
                buf.writeByte(4);
            })));

            // 0x07 Player Digging — all statuses incl. drop and release.
            for (int status : new int[]{0, 1, 2, 3, 4, 5}) {
                final int st = status;
                client.sendPacket(body(packet(Protocol18.C2S_PLAYER_DIGGING, buf -> {
                    buf.writeByte(st);
                    buf.writeLong(0x00_00_00_00_00_00_00_04L);
                    buf.writeByte(0);
                })));
            }

            // 0x02 Use Entity — attack and interact-at (with the triple).
            client.sendPacket(body(packet(Protocol18.C2S_USE_ENTITY, buf -> {
                ByteBufOps.writeVarInt(buf, 999);
                ByteBufOps.writeVarInt(buf, Protocol18.USE_ENTITY_ATTACK);
            })));
            client.sendPacket(body(packet(Protocol18.C2S_USE_ENTITY, buf -> {
                ByteBufOps.writeVarInt(buf, 999);
                ByteBufOps.writeVarInt(buf, Protocol18.USE_ENTITY_INTERACT_AT);
                buf.writeFloat(0.5f);
                buf.writeFloat(1.0f);
                buf.writeFloat(0.5f);
            })));

            // 0x01 Chat, 0x0E Window Click, 0x0D Close Window.
            client.sendPacket(body(packet(Protocol18.C2S_CHAT_MESSAGE, buf -> {
                ByteBufOps.writeString(buf, "parity probe");
            })));
            client.sendPacket(body(packet(Protocol18.C2S_WINDOW_CLICK, buf -> {
                buf.writeByte(0);
                buf.writeShort(36);
                buf.writeByte(0);
                buf.writeShort(1);
                buf.writeByte(0);
                buf.writeShort(-1);
            })));
            client.sendPacket(body(packet(Protocol18.C2S_CLOSE_WINDOW, buf -> buf.writeByte(0))));

            // A burst of flying packets: the per-tick stream a real client sends.
            for (int i = 0; i < 25; i++) {
                client.sendPacket(body(packet(Protocol18.C2S_PLAYER, buf -> buf.writeBoolean(false))));
            }

            // The connection survived the whole tour: the keep-alive exchange
            // still works end to end (server asks, client answers, twice).
            for (int round = 0; round < 2; round++) {
                byte[] packet = client.readPacketOfType(Protocol18.S2C_KEEP_ALIVE, 5_000);
                ByteBuf keepAlive = Unpooled.wrappedBuffer(packet);
                ByteBufOps.readVarInt(keepAlive);
                client.sendKeepAliveResponse(ByteBufOps.readVarInt(keepAlive));
            }
            client.sendPosition(0.5, 5.0, 0.5, true);
            awaitCondition(() -> parity.position().x() == 0.5 && parity.position().z() == 0.5,
                    "connection alive after the full vanilla tour (movement accepted)");
        }
    }
}
