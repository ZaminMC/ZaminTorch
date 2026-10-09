package net.minecraft.client.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.play.AddEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddExperienceOrbS2CPacket;
import net.minecraft.network.packet.s2c.play.AddGlobalEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddMobS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPaintingS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import net.minecraft.network.packet.s2c.play.AttachEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockMiningProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.CameraS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.CompressionThresholdS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityHeadAnglesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPickupS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityRemoveStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuConfirmS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuContentS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuDataS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.network.packet.s2c.play.MapDataS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenSignEditorS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerCombatS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSleepS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerXpS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreS2CPacket;
import net.minecraft.network.packet.s2c.play.SelectSlotS2CPacket;
import net.minecraft.network.packet.s2c.play.SignUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.network.packet.s2c.play.SpawnPointS2CPacket;
import net.minecraft.network.packet.s2c.play.StatsS2CPacket;
import net.minecraft.network.packet.s2c.play.TabListS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.TitlesS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldBorderS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunksS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeS2CPacket;

public interface ClientPlayPacketHandler extends PacketHandler {
    void handleAddEntity(AddEntityS2CPacket packet);

    void handleAddExperienceOrb(AddExperienceOrbS2CPacket packet);

    void handleAddGlobalEntity(AddGlobalEntityS2CPacket packet);

    void handleAddMob(AddMobS2CPacket packet);

    void handleScoreboardObjective(ScoreboardObjectiveS2CPacket packet);

    void handleAddPainting(AddPaintingS2CPacket packet);

    void handleAddPlayer(AddPlayerS2CPacket packet);

    void handleEntityAnimation(EntityAnimationS2CPacket packet);

    void handleStats(StatsS2CPacket packet);

    void handleBlockMiningProgress(BlockMiningProgressS2CPacket packet);

    void handleOpenSignEditor(OpenSignEditorS2CPacket packet);

    void handleBlockEntityUpdate(BlockEntityUpdateS2CPacket packet);

    void handleBlockEvent(BlockEventS2CPacket packet);

    void handleBlockUpdate(BlockUpdateS2CPacket packet);

    void handleChatMessage(ChatMessageS2CPacket packet);

    void handleCommandSuggestions(CommandSuggestionsS2CPacket packet);

    void handleBlocksUpdate(BlocksUpdateS2CPacket packet);

    void handleMapData(MapDataS2CPacket packet);

    void handleInventoryMenuConfirm(InventoryMenuConfirmS2CPacket packet);

    void handleCloseInventoryMenu(CloseInventoryMenuS2CPacket packet);

    void handleInventoryMenuContent(InventoryMenuContentS2CPacket packet);

    void handleOpenInventoryMenu(OpenInventoryMenuS2CPacket packet);

    void handleInventoryMenuData(InventoryMenuDataS2CPacket packet);

    void handleInventoryMenuSlotContent(InventoryMenuSlotContentS2CPacket packet);

    void handleCustomPayload(CustomPayloadS2CPacket packet);

    void handleDisconnect(DisconnectS2CPacket packet);

    void handlePlayerSleep(PlayerSleepS2CPacket packet);

    void handleEntityEvent(EntityEventS2CPacket packet);

    void handleAttachEntity(AttachEntityS2CPacket packet);

    void handleExplosion(ExplosionS2CPacket packet);

    void handleGameEvent(GameEventS2CPacket packet);

    void handleKeepAlive(KeepAliveS2CPacket packet);

    void handleWorldChunk(WorldChunkS2CPacket packet);

    void handleWorldChunks(WorldChunksS2CPacket packet);

    void handleWorldEvent(WorldEventS2CPacket packet);

    void handleLogin(LoginS2CPacket packet);

    void handleEntityMove(EntityMoveS2CPacket packet);

    void handlePlayerMove(PlayerMoveS2CPacket packet);

    void handleParticle(ParticleS2CPacket packet);

    void handlePlayerAbilities(PlayerAbilitiesS2CPacket packet);

    void handlePlayerInfo(PlayerInfoS2CPacket packet);

    void handleRemoveEntities(RemoveEntitiesS2CPacket packet);

    void handleEntityRemoveStatusEffect(EntityRemoveStatusEffectS2CPacket packet);

    void handlePlayerRespawn(PlayerRespawnS2CPacket packet);

    void handleEntityHeadAngles(EntityHeadAnglesS2CPacket packet);

    void handleSelectSlot(SelectSlotS2CPacket packet);

    void handleScoreboardDisplay(ScoreboardDisplayS2CPacket packet);

    void handleEntityData(EntityDataS2CPacket packet);

    void handleEntityVelocity(EntityVelocityS2CPacket packet);

    void handleEntityEquipment(EntityEquipmentS2CPacket packet);

    void handlePlayerXp(PlayerXpS2CPacket packet);

    void handlePlayerHealth(PlayerHealthS2CPacket packet);

    void handleTeam(TeamS2CPacket packet);

    void handleScoreboardScore(ScoreboardScoreS2CPacket packet);

    void handleSpawnPoint(SpawnPointS2CPacket packet);

    void handleWorldTime(WorldTimeS2CPacket packet);

    void handleSignBlockEntityUpdate(SignUpdateS2CPacket packet);

    void handleSoundEvent(SoundEventS2CPacket packet);

    void handleEntityPickup(EntityPickupS2CPacket packet);

    void handleEntityTeleport(EntityTeleportS2CPacket packet);

    void handleEntityAttributes(EntityAttributesS2CPacket packet);

    void handleEntityStatusEffect(EntityStatusEffectS2CPacket packet);

    void handlePlayerCombat(PlayerCombatS2CPacket packet);

    void handleDifficulty(DifficultyS2CPacket packet);

    void handleCamera(CameraS2CPacket packet);

    void handleWorldBorder(WorldBorderS2CPacket packet);

    void handleTitles(TitlesS2CPacket packet);

    void handleCompressionThreshold(CompressionThresholdS2CPacket packet);

    void handleTabList(TabListS2CPacket packet);

    void handleResourcePack(ResourcePackS2CPacket packet);

    void handleEntitySync(EntitySyncS2CPacket packet);
}
