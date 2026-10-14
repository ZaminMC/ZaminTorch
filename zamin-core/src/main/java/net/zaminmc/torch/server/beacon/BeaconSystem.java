package net.zaminmc.torch.server.beacon;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockLightTable;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The beacon system (Slice 29b — the reference port of block/BeaconBlock +
 * block/entity/BeaconBlockEntity + inventory/menu/BeaconMenu): the 80-tick
 * update walk ({@code world.getTime() % 80 == 0} — BeaconBlockEntity.tick
 * line 46), the pyramid computation (updateLevels lines 82-167), the effect
 * spread (spreadEffects lines 56-80), the neighbor-changed recompute
 * (BeaconBlock.neighborChanged lines 75-81), and the per-position state
 * map the GUI + persistence ride.
 *
 * <p>The effect ids are the reference's own ({@code BeaconBlockEntity.EFFECTS}
 * = speed 1 / haste 3, resistance 11 / jump 8, strength 5, regen 10 — the
 * engine's StatusEffects constants match). The spread applies a 180-tick
 * instance (the reference's own duration) with the amplifier-1 primary at
 * level 4's same-effect pair, and the second effect at level 4's
 * different-pair arm.</p>
 */
public final class BeaconSystem implements WorldChangeListener {

    /** The beacon block (legacy 138 — a single state, no properties). */
    public static final EngineBlockType BEACON = new EngineBlockType(
            Identifier.parse("minecraft:beacon"), "Beacon");

    /** The pyramid's four minerals (updateLevels line 145). */
    private static final String[] PYRAMID_MINERALS = {
            "minecraft:emerald_block", "minecraft:gold_block",
            "minecraft:diamond_block", "minecraft:iron_block"};

    /** The spread's duration (spreadEffects line 71: 180 ticks). */
    private static final int SPREAD_DURATION_TICKS = 180;

    private final EngineWorld world;
    private final ItemEntityManager itemEntities;
    private final Map<BlockPosition, BeaconBlockEntity> beacons = new LinkedHashMap<>();
    /** The effect application (the engine's own potion family). Wired at boot. */
    private PotionSystem potions;
    /** The live player scan (the boot's session registry supplier). */
    private java.util.function.Supplier<List<? extends PlayerSession>> players = List::of;

    /** The state-change broadcast (the 0x35 republish — the adapter wires it). */
    private final List<Listener> listeners = new ArrayList<>();

    /** The beacon update broadcast (the adapter's 0x35 action-3 republish). */
    public interface Listener {
        void onBeaconUpdated(BlockPosition position, BeaconBlockEntity beacon);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public BeaconSystem(EngineWorld world, ItemEntityManager itemEntities) {
        this.world = world;
        this.itemEntities = itemEntities;
    }

    /** Wires the effect application (the engine's boot order). */
    public void setPotions(PotionSystem potions) {
        this.potions = potions;
    }

    /** Wires the live player scan (the boot's session registry). */
    public void setPlayers(java.util.function.Supplier<
            List<? extends PlayerSession>> players) {
        this.players = java.util.Objects.requireNonNull(players, "players");
    }

    /** @return the block type the family registers at boot. */
    public static BlockRegistryBuilder registerBlocks(BlockRegistryBuilder builder) {
        return builder.register(BEACON);
    }

    /** @return whether the type is the beacon. */
    public static boolean isBeacon(BlockType type) {
        return type != null && type.identifier().value().equals("beacon")
                && type.identifier().namespace().equals("minecraft");
    }

    /** @return the beacon state at the position, creating it on first use. */
    public BeaconBlockEntity getOrCreate(BlockPosition position) {
        return beacons.computeIfAbsent(position, at -> new BeaconBlockEntity());
    }

    /** @return the live state at the position, or null when none. */
    public BeaconBlockEntity peek(BlockPosition position) {
        return beacons.get(position);
    }

    /** @return the whole beacon map (the save snapshot). */
    public Map<BlockPosition, BeaconBlockEntity> snapshot() {
        return new LinkedHashMap<>(beacons);
    }

    /** The ZBT restore (boot). */
    public void restoreAll(Map<BlockPosition, BeaconBlockEntity> restored) {
        beacons.putAll(restored);
    }

    /** The beacons in one chunk (the chunk-send tile replay). */
    public List<Map.Entry<BlockPosition, BeaconBlockEntity>> inChunk(
            net.zaminmc.torch.block.ChunkPosition chunk) {
        List<Map.Entry<BlockPosition, BeaconBlockEntity>> inChunk = new ArrayList<>();
        for (Map.Entry<BlockPosition, BeaconBlockEntity> entry : beacons.entrySet()) {
            if (entry.getKey().chunkPosition().packed() == chunk.packed()) {
                inChunk.add(entry);
            }
        }
        return inChunk;
    }

    /** The placement listener: the block's arrival creates the state. */
    @Override
    public void onBlockChanged(EngineWorld changed, BlockPosition position, BlockType now) {
        if (isBeacon(now)) {
            getOrCreate(position);
        }
    }

    /**
     * The per-tick walk: each beacon updates on the global 80-tick phase
     * (BeaconBlockEntity.tick lines 45-49), and the block-gone sweep runs
     * beside it (only on a LOADED chunk — the restart rule; the beacon's
     * state freezes in an unloaded chunk like every vanilla block entity).
     */
    public void tick(long time) {
        boolean sweep = false;
        var iterator = beacons.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            BlockPosition position = entry.getKey();
            if (world.isChunkLoadedAt(position)) {
                if (!isBeacon(world.getBlock(position))) {
                    iterator.remove(); // the block left (creative break)
                    sweep = true;
                    continue;
                }
                if (time % 80L == 0L) {
                    update(position, entry.getValue());
                }
            }
        }
        if (sweep) {
            // The state left with the block: every remaining beacon
            // republishes (the adapter's 0x35 refresh).
            for (var entry : beacons.entrySet()) {
                for (Listener listener : listeners) {
                    listener.onBeaconUpdated(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /** The neighbor-changed recompute (BeaconBlock.neighborChanged lines 75-81). */
    public void neighborHeard(BlockPosition position) {
        for (net.zaminmc.torch.util.Position offset : NEIGHBORS) {
            BlockPosition at = position.offset((int) offset.x(), (int) offset.y(),
                    (int) offset.z());
            BeaconBlockEntity beacon = beacons.get(at);
            if (beacon != null) {
                update(at, beacon);
            }
        }
        BeaconBlockEntity self = beacons.get(position);
        if (self != null) {
            update(position, self);
        }
    }

    private static final Position[] NEIGHBORS = {
            new Position(0, -1, 0), new Position(0, 1, 0), new Position(0, 0, -1),
            new Position(0, 0, 1), new Position(-1, 0, 0), new Position(1, 0, 0)};

    /** update() lines 51-54: the levels recompute + the effect spread. */
    public void update(BlockPosition position, BeaconBlockEntity beacon) {
        updateLevels(position, beacon);
        spreadEffects(position, beacon);
        for (Listener listener : listeners) {
            listener.onBeaconUpdated(position, beacon);
        }
    }

    /**
     * updateLevels lines 82-167: the beam walk (an opacity-15 block kills
     * the beam — bedrock excepted; the stained-glass color arms ride the
     * stained-glass family's own slice), then the pyramid: level l1 needs
     * the FULL (2*l1+1) square ring at y-l1 of the four minerals, one level
     * at a time from the inside out.
     */
    void updateLevels(BlockPosition position, BeaconBlockEntity beacon) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
        boolean beamActive = true;
        // The beam walk (lines 95-131): the first opaque block above kills
        // the beam (getOpacity() >= 15, bedrock excepted).
        for (int i = y + 1; i < 256; i++) {
            BlockType above = world.getBlock(new BlockPosition(x, i, z));
            if (above.identifier().value().equals("bedrock")) {
                continue;
            }
            if (opacityOf(above) >= 15) {
                beamActive = false;
                break;
            }
            // The stained-glass color arms (the section split + the blend)
            // ride the stained-glass family's slice — the engine registers
            // no stained glass yet, so the walk only passes through.
        }
        int levels = 0;
        if (beamActive) {
            // The pyramid (lines 133-155): each level l1 (1..4) needs the
            // full square at y-l1 — the reference's own two-break walk.
            for (int l1 = 1; l1 <= 4; l1++) {
                int ringY = y - l1;
                if (ringY < 0) {
                    break;
                }
                boolean full = true;
                outer:
                for (int j = x - l1; j <= x + l1; j++) {
                    for (int k = z - l1; k <= z + l1; k++) {
                        if (!isPyramidMineral(world.getBlock(new BlockPosition(j, ringY, k)))) {
                            full = false;
                            break outer;
                        }
                    }
                }
                if (!full) {
                    break;
                }
                levels = l1;
            }
        }
        beacon.setLevels(levels);
    }

    /** The block's opacity through the engine's light table (getOpacity). */
    private static int opacityOf(BlockType type) {
        return BlockLightTable.lookup(type.identifier())
                .map(BlockLightTable.LightData::filter)
                .orElse(0);
    }

    /** The pyramid's four minerals (line 145). */
    private static boolean isPyramidMineral(BlockType type) {
        if (!type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.equals("emerald_block") || value.equals("gold_block")
                || value.equals("diamond_block") || value.equals("iron_block");
    }

    /**
     * spreadEffects lines 56-80: the range box (levels*10+10, grown, then
     * expanded to the full world height), every player inside receives the
     * 180-tick primary (amplifier 1 at the level-4 same-effect pair), and
     * the level-4 different-pair arm adds the secondary at amplifier 0.
     */
    void spreadEffects(BlockPosition position, BeaconBlockEntity beacon) {
        int levels = beacon.levels();
        if (levels <= 0 || beacon.primary() <= 0 || potions == null) {
            return;
        }
        double range = levels * 10 + 10;
        int amplifier = 0;
        if (levels >= 4 && beacon.primary() == beacon.secondary()) {
            amplifier = 1;
        }
        List<PlayerSession> inRange = playersInRange(position, range);
        for (PlayerSession player : inRange) {
            potions.add(player,
                    new StatusEffectInstance(beacon.primary(), SPREAD_DURATION_TICKS, amplifier));
        }
        if (levels >= 4 && beacon.primary() != beacon.secondary()
                && beacon.secondary() > 0) {
            for (PlayerSession player : inRange) {
                potions.add(player, new StatusEffectInstance(beacon.secondary(),
                        SPREAD_DURATION_TICKS, 0));
            }
        }
    }

    /** The box walk (line 67): grown by the range, expanded to full height. */
    private List<PlayerSession> playersInRange(BlockPosition position, double range) {
        List<PlayerSession> inRange = new ArrayList<>();
        for (PlayerSession player : players.get()) {
            Position at = player.position();
            if (Math.abs(at.x() - (position.x() + 0.5)) <= range
                    && Math.abs(at.z() - (position.z() + 0.5)) <= range) {
                inRange.add(player);
            }
        }
        return inRange;
    }

    /**
     * The MC|Beacon application (ServerPlayNetworkHandler lines 1062-1080):
     * only with a payment in the slot — one unit consumes, then the two
     * choices land through the reference's own effect gate.
     * @return whether the selection applied (a payment was present).
     */
    public boolean applySelection(BlockPosition position, int primary, int secondary) {
        BeaconBlockEntity beacon = beacons.get(position);
        if (beacon == null || !beacon.consumePaymentUnit()) {
            return false; // no payment: the handler's silent no-op
        }
        beacon.setPrimary(primary);
        beacon.setSecondary(secondary);
        update(position, beacon);
        return true;
    }

    /** A survival-broken beacon spills its payment (the break hook). */
    public void onBlockBroken(BlockPosition position) {
        BeaconBlockEntity beacon = beacons.remove(position);
        if (beacon == null) {
            return;
        }
        ItemStack payment = beacon.payment();
        if (!payment.isEmpty()) {
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    payment, net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }
    }
}
