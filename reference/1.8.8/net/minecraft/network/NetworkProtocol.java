package net.minecraft.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.HelloC2SPacket;
import net.minecraft.network.packet.c2s.login.KeyC2SPacket;
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
import net.minecraft.network.packet.c2s.query.PingC2SPacket;
import net.minecraft.network.packet.c2s.query.ServerStatusC2SPacket;
import net.minecraft.network.packet.s2c.login.HelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginFailS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
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
import net.minecraft.network.packet.s2c.query.PingS2CPacket;
import net.minecraft.network.packet.s2c.query.ServerStatusS2CPacket;
import org.apache.logging.log4j.LogManager;

public enum NetworkProtocol {
    HANDSHAKE(-1) {
        {
            this.register(PacketFlow.SERVERBOUND, HandshakeC2SPacket.class);
        }
    },
    PLAY(0) {
        {
            this.register(PacketFlow.CLIENTBOUND, KeepAliveS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, LoginS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ChatMessageS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, WorldTimeS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityEquipmentS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, SpawnPointS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerHealthS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerRespawnS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerMoveS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, SelectSlotS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerSleepS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityAnimationS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AddPlayerS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityPickupS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AddEntityS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AddMobS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AddPaintingS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AddExperienceOrbS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityVelocityS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, RemoveEntitiesS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityMoveS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityMoveS2CPacket.Position.class);
            this.register(PacketFlow.CLIENTBOUND, EntityMoveS2CPacket.Angles.class);
            this.register(PacketFlow.CLIENTBOUND, EntityMoveS2CPacket.PositionAndAngles.class);
            this.register(PacketFlow.CLIENTBOUND, EntityTeleportS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityHeadAnglesS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityEventS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AttachEntityS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityDataS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityStatusEffectS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityRemoveStatusEffectS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerXpS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntityAttributesS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, WorldChunkS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, BlocksUpdateS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, BlockUpdateS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, BlockEventS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, BlockMiningProgressS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, WorldChunksS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ExplosionS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, WorldEventS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, SoundEventS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ParticleS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, GameEventS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, AddGlobalEntityS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, OpenInventoryMenuS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, CloseInventoryMenuS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, InventoryMenuSlotContentS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, InventoryMenuContentS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, InventoryMenuDataS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, InventoryMenuConfirmS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, SignUpdateS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, MapDataS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, BlockEntityUpdateS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, OpenSignEditorS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, StatsS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerInfoS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerAbilitiesS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, CommandSuggestionsS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ScoreboardObjectiveS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ScoreboardScoreS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ScoreboardDisplayS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, TeamS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, CustomPayloadS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, DisconnectS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, DifficultyS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PlayerCombatS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, CameraS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, WorldBorderS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, TitlesS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, CompressionThresholdS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, TabListS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ResourcePackS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, EntitySyncS2CPacket.class);
            this.register(PacketFlow.SERVERBOUND, KeepAliveC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, ChatMessageC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerInteractEntityC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerMoveC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerMoveC2SPacket.Position.class);
            this.register(PacketFlow.SERVERBOUND, PlayerMoveC2SPacket.Angles.class);
            this.register(PacketFlow.SERVERBOUND, PlayerMoveC2SPacket.PositionAndAngles.class);
            this.register(PacketFlow.SERVERBOUND, PlayerHandActionC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerUseC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, SelectSlotC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, ArmSwingC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerMovementActionC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerInputC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, CloseInventoryMenuC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, InventoryMenuClickSlotC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, InventoryMenuConfirmC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, CreativeMenuSlotC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, MenuClickButtonC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, SignUpdateC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerAbilitiesC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, CommandSuggestionsC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, ClientSettingsC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, ClientStatusC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, CustomPayloadC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, PlayerSpectateC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, ResourcePackC2SPacket.class);
        }
    },
    STATUS(1) {
        {
            this.register(PacketFlow.SERVERBOUND, ServerStatusC2SPacket.class);
            this.register(PacketFlow.CLIENTBOUND, ServerStatusS2CPacket.class);
            this.register(PacketFlow.SERVERBOUND, PingC2SPacket.class);
            this.register(PacketFlow.CLIENTBOUND, PingS2CPacket.class);
        }
    },
    LOGIN(2) {
        {
            this.register(PacketFlow.CLIENTBOUND, LoginFailS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, HelloS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, LoginSuccessS2CPacket.class);
            this.register(PacketFlow.CLIENTBOUND, net.minecraft.network.packet.s2c.login.CompressionThresholdS2CPacket.class);
            this.register(PacketFlow.SERVERBOUND, HelloC2SPacket.class);
            this.register(PacketFlow.SERVERBOUND, KeyC2SPacket.class);
        }
    };

    private static int MIN_ID = -1;
    private static int MAX_ID = 2;
    private static final NetworkProtocol[] BY_ID = new NetworkProtocol[MAX_ID - MIN_ID + 1];
    private static final Map<Class<? extends Packet>, NetworkProtocol> BY_PACKET = Maps.newHashMap();
    private final int id;
    private final Map<PacketFlow, BiMap<Integer, Class<? extends Packet>>> packets = Maps.newEnumMap(PacketFlow.class);

    NetworkProtocol(int id) {
        this.id = id;
    }

    protected NetworkProtocol register(PacketFlow flow, Class<? extends Packet> type) {
        BiMap<Integer, Class<? extends Packet>> bimap = this.packets.get(flow);
        if (bimap == null) {
            bimap = HashBiMap.create();
            this.packets.put(flow, bimap);
        }

        if (bimap.containsValue(type)) {
            String s = flow + " packet " + type + " is already known to ID " + bimap.inverse().get(type);
            LogManager.getLogger().fatal(s);
            throw new IllegalArgumentException(s);
        } else {
            bimap.put(bimap.size(), type);
            return this;
        }
    }

    public Integer getPacketId(PacketFlow flow, Packet packet) {
        return this.packets.get(flow).inverse().get(packet.getClass());
    }

    public Packet createPacket(PacketFlow flow, int id) throws IllegalAccessException, InstantiationException {
        Class<? extends Packet> oclass = this.packets.get(flow).get(id);
        return oclass == null ? null : oclass.newInstance();
    }

    public int getId() {
        return this.id;
    }

    public static NetworkProtocol byId(int id) {
        return id >= MIN_ID && id <= MAX_ID ? BY_ID[id - MIN_ID] : null;
    }

    public static NetworkProtocol byPacket(Packet packet) {
        return BY_PACKET.get(packet.getClass());
    }

    static {
        for (NetworkProtocol networkprotocol : values()) {
            int i = networkprotocol.getId();
            if (i < MIN_ID || i > MAX_ID) {
                throw new Error("Invalid protocol ID " + Integer.toString(i));
            }

            BY_ID[i - MIN_ID] = networkprotocol;

            for (PacketFlow packetflow : networkprotocol.packets.keySet()) {
                for (Class<? extends Packet> oclass : networkprotocol.packets.get(packetflow).values()) {
                    if (BY_PACKET.containsKey(oclass) && BY_PACKET.get(oclass) != networkprotocol) {
                        throw new Error(
                            "Packet " + oclass + " is already assigned to protocol " + BY_PACKET.get(oclass) + " - can't reassign to " + networkprotocol
                        );
                    }

                    try {
                        oclass.newInstance();
                    } catch (Throwable throwable) {
                        throw new Error("Packet " + oclass + " fails instantiation checks! " + oclass);
                    }

                    BY_PACKET.put(oclass, networkprotocol);
                }
            }
        }
    }
}
