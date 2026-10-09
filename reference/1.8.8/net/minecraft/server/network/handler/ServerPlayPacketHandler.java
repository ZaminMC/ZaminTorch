package net.minecraft.server.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandSuggestionsC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeMenuSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.MenuClickButtonC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerSpectateC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.ResourcePackC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.SignUpdateC2SPacket;

public interface ServerPlayPacketHandler extends PacketHandler {
    void handleArmSwing(ArmSwingC2SPacket packet);

    void handleChatMessage(ChatMessageC2SPacket packet);

    void handleCommandSuggestions(CommandSuggestionsC2SPacket packet);

    void handleClientStatus(ClientStatusC2SPacket packet);

    void handleClientSettings(ClientSettingsC2SPacket packet);

    void handleInventoryMenuConfirm(InventoryMenuConfirmC2SPacket packet);

    void handleMenuClickButton(MenuClickButtonC2SPacket packet);

    void handleInventoryMenuClickSlot(InventoryMenuClickSlotC2SPacket packet);

    void handleCloseInventoryMenu(CloseInventoryMenuC2SPacket packet);

    void handleCustomPayload(CustomPayloadC2SPacket packet);

    void handleInteractEntity(PlayerInteractEntityC2SPacket packet);

    void handleKeepAlive(KeepAliveC2SPacket packet);

    void handlePlayerMove(PlayerMoveC2SPacket packet);

    void handlePlayerAbilities(PlayerAbilitiesC2SPacket packet);

    void handlePlayerHandAction(PlayerHandActionC2SPacket packet);

    void handlePlayerMovementAction(PlayerMovementActionC2SPacket packet);

    void handlePlayerInput(PlayerInputC2SPacket packet);

    void handleSelectSlot(SelectSlotC2SPacket packet);

    void handleCreativeMenuSlot(CreativeMenuSlotC2SPacket packet);

    void handleSignUpdate(SignUpdateC2SPacket packet);

    void handlePlayerUse(PlayerUseC2SPacket packet);

    void handlePlayerSpectate(PlayerSpectateC2SPacket packet);

    void handleResourcePackResponse(ResourcePackC2SPacket packet);
}
