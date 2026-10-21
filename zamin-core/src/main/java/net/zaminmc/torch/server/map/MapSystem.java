package net.zaminmc.torch.server.map;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The map system (the map item family — the reference's
 * {@code FilledMapItem} + {@code SavedMapData} walks over the engine's
 * worlds): the per-dimension map data with the id allocator (the
 * {@code getSavedDataCount("map")} count law), the terrain scan
 * ({@code FilledMapItem.update} lines 56-172 — the holder's 16-band
 * column rotation, the per-column multiset over the 2^scale block sample,
 * the height shading with the water-depth special, the circular write
 * region, the nether's dirt/stone noise arm), the holder walk
 * ({@code SavedMapData.tickHolder} lines 83-138 — the player markers,
 * the frame markers, the stack's Decorations rows, the audience's
 * keep/remove law), and the packet consult ({@code Holder
 * .createUpdatePacket} — the dirty-region send + the every-5th
 * decorations-only resend).
 *
 * <p>Thread ownership: every mutation runs on the owning tick lane (the
 * engine's single simulation context — §15's "world reads on the owning
 * lane"); the persistence rides the store pattern (the capture on the
 * tick lane, the serialization and the file half off it).</p>
 */
public final class MapSystem {

    /** The engine's world consults (the surfaces the reference reads off World). */
    public interface Surface {

        /**
         * The chunk-column accessor at a block position: null when the
         * chunk is not loaded (the reference's getChunk returning an empty
         * chunk — the isEmpty gate then skips the column).
         */
        Column columnAt(int dimension, int x, int z);

        /** The dimension's time-of-day clock (the nether spin's own read). */
        long timeOfDay(int dimension);

        /** The dimension's world spawn (the reassign path's scale-3 center). */
        Position spawnPosition();

        /** One loaded chunk column (the reference's WorldChunk handle). */
        interface Column {

            /** The reference's {@code worldchunk.isEmpty()} gate. */
            boolean isEmpty();

            /**
             * The heightmap consult (the reference's
             * {@code worldchunk.getHeight(x, z)}): the highest
             * light-filtering block + 1 — the engine derives it from the
             * light-opacity model (the same law the heightmap arrays
             * maintain: any block filtering light raises the floor).
             */
            int height(int localX, int localZ);

            /** The block at a cell of this column. */
            BlockType block(int localX, int y, int localZ);
        }
    }

    private final Surface surface;

    /** The per-dimension map tables (the reference's per-world saved data). */
    private final Map<Integer, Map<Integer, MapData>> mapsByDimension = new HashMap<>();

    public MapSystem(Surface surface) {
        this.surface = Objects.requireNonNull(surface, "surface");
    }

    // ------------------------------------------------------------- the data

    /** The map with the id, or null (the reference's loadSavedData consult). */
    public MapData mapDataOf(int dimension, int id) {
        Map<Integer, MapData> maps = mapsByDimension.get(dimension);
        return maps == null ? null : maps.get(id);
    }

    /** Every map of a dimension (the persistence capture's walk). */
    public List<MapData> allMapsOf(int dimension) {
        Map<Integer, MapData> maps = mapsByDimension.get(dimension);
        return maps == null ? List.of() : new ArrayList<>(maps.values());
    }

    /**
     * The id allocator (the reference's {@code world.getSavedDataCount(
     * "map")} — EmptyMapItem.startUsing line 15 + FilledMapItem.onResult
     * line 198): the count of the dimension's maps, the never-shrinking
     * set's own next-free id.
     */
    public int allocateId(int dimension) {
        Map<Integer, MapData> maps = mapsByDimension.get(dimension);
        return maps == null ? 0 : maps.size();
    }

    /**
     * The use path's creation (EmptyMapItem.startUsing lines 15-22): the
     * allocated id, the scale 0, the centering on the player, the
     * dimension row — registered in the dimension's table (the reference's
     * setSavedData).
     */
    public MapData createMap(int dimension, double x, double z) {
        int id = allocateId(dimension);
        MapData data = new MapData();
        data.scale = 0;
        data.updateCenter(x, z, 0);
        data.dimension = (byte) dimension;
        dataOf(dimension).put(id, data);
        return data;
    }

    /**
     * The scale-up path's creation (FilledMapItem.onResult lines 199-208):
     * the newly allocated id, the scale +1 clamped at 4, the center
     * re-snapped at the finer grid over the SAME center, the dimension
     * carried.
     */
    public MapData createScaledCopy(int dimension, MapData source) {
        int id = allocateId(dimension);
        MapData data = new MapData();
        data.scale = (byte) Math.min(MapData.MAX_SCALE, source.scale + 1);
        data.updateCenter(source.centerX, source.centerZ, data.scale);
        data.dimension = source.dimension;
        dataOf(dimension).put(id, data);
        return data;
    }

    /**
     * The damage-row consult (FilledMapItem.getSavedMapData lines 39-54):
     * the map the stack's damage names; when the world holds none, the
     * server REASSIGNS the stack a fresh id and creates the data at scale
     * 3 centered on the world spawn (the reference's own quirk — the
     * /give'd filled map with an unknown id becomes a spawn-centered
     * overview). The caller applies the returned stack.
     */
    public ItemStack resolveStackMapData(ItemStack stack, int dimension) {
        MapData data = mapDataOf(dimension, stack.damage());
        if (data != null) {
            return stack;
        }
        int freshId = allocateId(dimension);
        data = new MapData();
        data.scale = 3;
        Position spawn = surface.spawnPosition();
        data.updateCenter(spawn.x(), spawn.z(), 3);
        data.dimension = (byte) dimension;
        dataOf(dimension).put(freshId, data);
        return stack.withDamage(freshId);
    }

    /** The loaded rows' restore (the store's load walk). */
    public void restore(int dimension, List<MapDataStore.MapRow> rows) {
        Map<Integer, MapData> maps = dataOf(dimension);
        for (MapDataStore.MapRow row : rows) {
            MapData data = new MapData();
            data.restoreCenter(row.xCenter(), row.zCenter(), row.scale(), row.dimension());
            System.arraycopy(row.colors(), 0, data.colors, 0, data.colors.length);
            maps.put(row.id(), data);
        }
    }

    /** The persistence capture (the fresh rows on the tick lane). */
    public MapDataStore.SaveInput snapshot(int dimension) {
        Map<Integer, MapData> maps = mapsByDimension.get(dimension);
        List<MapDataStore.MapRow> rows = new ArrayList<>();
        int maxId = -1;
        if (maps != null) {
            for (Map.Entry<Integer, MapData> entry : maps.entrySet()) {
                MapData data = entry.getValue();
                rows.add(new MapDataStore.MapRow(entry.getKey(), data.dimension,
                        data.centerX, data.centerZ, data.scale, data.colors.clone()));
                maxId = Math.max(maxId, entry.getKey());
            }
        }
        // The counter over the count law (the never-shrinking set's own
        // next-free id — max(count, highest+1) guards a hand-edited gap).
        return new MapDataStore.SaveInput(Math.max(maxId + 1, rows.size()), rows);
    }

    private Map<Integer, MapData> dataOf(int dimension) {
        return mapsByDimension.computeIfAbsent(dimension, k -> new LinkedHashMap<>());
    }

    // ----------------------------------------------------- the holder walk

    /**
     * The held-map tick (FilledMapItem.tick lines 175-187 over
     * PlayerInventory.tick's 36-slot walk): the damage-row consult (the
     * reassign path's stack replacement — the caller writes it back), the
     * holder walk, and the selected slot's terrain scan.
     */
    public ItemStack tickHeldStack(PlayerSession player, ItemStack stack, boolean selected) {
        int dimension = player.dimension();
        ItemStack resolved = resolveStackMapData(stack, dimension);
        MapData data = mapDataOf(dimension, resolved.damage());
        if (data != null) {
            tickHolder(player, resolved, null);
            if (selected) {
                update(player, data);
            }
        }
        return resolved;
    }

    /**
     * The audience walk (SavedMapData.tickHolder lines 83-138): the
     * first-consult join, the no-longer-holding marker removal, the
     * keep/remove law over the holders (a framed map keeps every
     * consulted holder — the wall-of-maps render), the per-holder player
     * marker with the dimension gate, the frame's own marker (type 1 at
     * the frame cell, the facing*90 rotation), and the stack's
     * Decorations rows materialized once (the not-yet-known ids).
     */
    public void tickHolder(PlayerSession player, ItemStack item, FrameInfo frame) {
        int dimension = player.dimension();
        MapData data = mapDataOf(dimension, item.damage());
        if (data == null) {
            return;
        }
        data.addHolder(player);
        if (!inventoryContains(player, item)) {
            data.decorations.remove(player.name());
        }
        for (int i = 0; i < data.holders.size(); i++) {
            MapData.Holder holder = data.holders.get(i);
            boolean removed = holder.player.state()
                    != net.zaminmc.torch.entity.PlayerState.PLAYING;
            if (!removed && (inventoryContains(holder.player, item) || frame != null)) {
                if (frame == null && holder.player.dimension() == data.dimension) {
                    Position at = holder.player.position();
                    data.addDecoration(0, surface.timeOfDay(holder.player.dimension()),
                            holder.player.name(), at.x(), at.z(),
                            holder.player.rotation().yaw());
                }
            } else {
                data.removeHolder(holder.player);
                i--;
            }
        }
        if (frame != null) {
            // The frame's own marker (tickHolder lines 113-119): the
            // "frame-<id>" name, the cell's corner, the horizontal
            // facing's *90 rotation.
            data.addDecoration(1, surface.timeOfDay(dimension),
                    "frame-" + frame.entityId(), frame.x(), frame.z(),
                    frame.facing() * 90);
        }
        if (item.mapDecorations() != null) {
            for (Map<String, Object> row : item.mapDecorations()) {
                String id = stringValue(row.get("id"));
                if (id == null || data.decorations.containsKey(id)) {
                    continue;
                }
                data.addDecoration(intValue(row.get("type")),
                        surface.timeOfDay(dimension), id,
                        doubleValue(row.get("x")), doubleValue(row.get("z")),
                        doubleValue(row.get("rot")));
            }
        }
    }

    /**
     * The update-packet consult (ServerPlayerEntity.tickPlayer lines
     * 288-297 over the whole 40-slot inventory): every filled map the
     * player holds answers its holder's payload — the dirty region or the
     * every-5th decorations-only resend — or nothing.
     */
    public List<MapData.MapUpdatePayload> heldMapPackets(PlayerSession player) {
        List<MapData.MapUpdatePayload> packets = new ArrayList<>();
        int dimension = player.dimension();
        for (int slot = 0; slot < 40; slot++) {
            ItemStack at = slot < 36 ? player.inventory().slotCopy(slot)
                    : player.inventory().armorCopy(slot - 36);
            if (at.isEmpty() || !at.type().equals(BuiltinItems.FILLED_MAP)) {
                continue;
            }
            MapData data = mapDataOf(dimension, at.damage());
            if (data == null) {
                continue;
            }
            MapData.Holder holder = data.holderOf(player);
            if (holder == null) {
                continue;
            }
            MapData.MapUpdatePayload payload =
                    holder.createUpdatePayload(at.damage(), data.scale,
                            data.decorations, data.colors);
            if (payload != null) {
                packets.add(payload);
            }
        }
        return packets;
    }

    /**
     * The framed maps' audience walk (TrackedEntity lines 134-151 — the
     * ten-tick cadence): every frame displaying a map ticks the holder
     * for every player of the dimension and answers each holder's
     * payload. The frame gate rides the wire (the adapter's own
     * chunk-window check — the reference's tracking-player set).
     */
    public void frameTick(Iterable<PlayerSession> players, List<FrameInfo> framesWithMaps) {
        for (FrameInfo frame : framesWithMaps) {
            for (PlayerSession player : players) {
                if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                        || player.dimension() != frame.dimension()) {
                    continue;
                }
                tickHolder(player, frame.display(), frame);
                MapData data = mapDataOf(player.dimension(), frame.display().damage());
                if (data == null) {
                    continue;
                }
                MapData.Holder holder = data.holderOf(player);
                if (holder == null) {
                    continue;
                }
                MapData.MapUpdatePayload payload = holder.createUpdatePayload(
                        frame.display().damage(), data.scale, data.decorations, data.colors);
                if (payload != null) {
                    player.link().sendMapData(payload.mapId(), payload.scale(),
                            iconsOf(payload), payload.minX(), payload.minY(),
                            payload.width(), payload.height(), payload.colors(),
                            frame.chunkKey());
                }
            }
        }
    }

    /** The payload's icon rows in the link's own record form. */
    public static List<net.zaminmc.torch.server.net.ClientLink.MapIcon> iconsOf(
            MapData.MapUpdatePayload payload) {
        List<net.zaminmc.torch.server.net.ClientLink.MapIcon> icons = new ArrayList<>(
                payload.icons().size());
        for (MapDecoration icon : payload.icons()) {
            icons.add(new net.zaminmc.torch.server.net.ClientLink.MapIcon(
                    icon.getType(), icon.getX(), icon.getY(), icon.getRotation()));
        }
        return icons;
    }

    // ------------------------------------------------------ the terrain scan

    /**
     * The terrain scan (FilledMapItem.update lines 56-172, 1:1): the
     * selected holder's column walk — one 16-band column per tick (the
     * step's & 15 rotation, the flag re-arm when a column changed), the
     * per-column color multiset over the 2^scale block sample with the
     * height shading, the water-depth special, the nether's noise arm,
     * and the circular write region's color byte landing.
     */
    public void update(PlayerSession player, MapData data) {
        int dimension = player.dimension();
        if (dimension != data.dimension) {
            return;
        }
        int i = 1 << data.scale;
        int j = data.centerX;
        int k = data.centerZ;
        int l = (int) Math.floor(player.position().x() - j) / i + 64;
        int i1 = (int) Math.floor(player.position().z() - k) / i + 64;
        int j1 = 128 / i;
        if (dimension == -1) {
            j1 /= 2; // hasNoSky: the nether's halved band
        }

        MapData.Holder holder = data.addHolder(player);
        holder.step++;
        boolean flag = false;

        for (int k1 = l - j1 + 1; k1 < l + j1; k1++) {
            if ((k1 & 15) == (holder.step & 15) || flag) {
                flag = false;
                double d0 = 0.0;

                for (int l1 = i1 - j1 - 1; l1 < i1 + j1; l1++) {
                    if (k1 >= 0 && l1 >= -1 && k1 < 128 && l1 < 128) {
                        int i2 = k1 - l;
                        int j2 = l1 - i1;
                        boolean flag1 = i2 * i2 + j2 * j2 > (j1 - 2) * (j1 - 2);
                        int k2 = (j / i + k1 - 64) * i;
                        int l2 = (k / i + l1 - 64) * i;
                        Map<MapColors.Color, Integer> multiset = new HashMap<>();
                        Surface.Column column = surface.columnAt(dimension, k2, l2);
                        if (column != null && !column.isEmpty()) {
                            int i3 = k2 & 15;
                            int j3 = l2 & 15;
                            int k3 = 0;
                            double d1 = 0.0;
                            if (dimension == -1) {
                                // The nether's noise arm (lines 91-102): the
                                // deterministic dirt/stone pattern over the
                                // column's own coordinates.
                                int l3 = k2 + l2 * 231871;
                                l3 = l3 * l3 * 31287121 + l3 * 11;
                                if ((l3 >> 20 & 1) == 0) {
                                    multiset.merge(MapColors.DIRT, 10, Integer::sum);
                                } else {
                                    multiset.merge(MapColors.STONE, 100, Integer::sum);
                                }
                                d1 = 100.0;
                            } else {
                                for (int i4 = 0; i4 < i; i4++) {
                                    for (int j4 = 0; j4 < i; j4++) {
                                        int k4 = column.height(i4 + i3, j4 + j3) + 1;
                                        BlockType blockstate = null;
                                        if (k4 > 1) {
                                            do {
                                                blockstate = column.block(i4 + i3, --k4, j4 + j3);
                                            } while (colorOf(blockstate).id() == 0 && k4 > 0);
                                            if (k4 > 0 && isLiquid(blockstate)) {
                                                int l4 = k4 - 1;
                                                BlockType block;
                                                do {
                                                    block = column.block(i4 + i3, l4--, j4 + j3);
                                                    k3++;
                                                } while (l4 > 0 && isLiquid(block));
                                            }
                                        }
                                        d1 += (double) k4 / (i * i);
                                        multiset.merge(colorOf(blockstate), 1, Integer::sum);
                                    }
                                }
                            }

                            k3 /= i * i;
                            double d2 = (d1 - d0) * 4.0 / (i + 4)
                                    + ((k1 + l1 & 1) - 0.5) * 0.4;
                            int i5 = 1;
                            if (d2 > 0.6) {
                                i5 = 2;
                            }
                            if (d2 < -0.6) {
                                i5 = 0;
                            }

                            MapColors.Color mapcolor = highestCount(multiset);
                            if (mapcolor == MapColors.WATER) {
                                d2 = k3 * 0.1 + (k1 + l1 & 1) * 0.2;
                                i5 = 1;
                                if (d2 < 0.5) {
                                    i5 = 2;
                                }
                                if (d2 > 0.9) {
                                    i5 = 0;
                                }
                            }

                            d0 = d1;
                            if (l1 >= 0 && i2 * i2 + j2 * j2 < j1 * j1
                                    && (!flag1 || (k1 + l1 & 1) != 0)) {
                                byte b0 = data.colors[k1 + l1 * 128];
                                byte b1 = (byte) (mapcolor.id() * 4 + i5);
                                if (b0 != b1) {
                                    data.colors[k1 + l1 * 128] = b1;
                                    data.markDirty(k1, l1);
                                    flag = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- helpers

    /**
     * The inventory consult (the reference's {@code player.inventory
     * .contains(item)} — Inventory.contains' isItemEqual over the 40
     * slots): the same map identity — the filled-map type, the same map
     * id, the same scaling flag and decorations rows (the stack's whole
     * tag, the reference's own comparison).
     */
    private static boolean inventoryContains(PlayerSession player, ItemStack item) {
        for (int slot = 0; slot < 40; slot++) {
            ItemStack at = slot < 36 ? player.inventory().slotCopy(slot)
                    : player.inventory().armorCopy(slot - 36);
            if (at.isEmpty()) {
                continue;
            }
            if (ItemStack.mergeable(at, item)) {
                return true;
            }
        }
        return false;
    }

    /** The multiset's highest-count color (copyHighestCountFirst's head). */
    private static MapColors.Color highestCount(Map<MapColors.Color, Integer> multiset) {
        MapColors.Color best = MapColors.AIR;
        int bestCount = -1;
        for (Map.Entry<MapColors.Color, Integer> entry : multiset.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private static MapColors.Color colorOf(BlockType type) {
        return MapColors.of(type);
    }

    private static boolean isLiquid(BlockType type) {
        return type != null && net.zaminmc.torch.server.block.FluidBlocks.kindOf(
                type.identifier()) != null;
    }

    private static String stringValue(Object boxed) {
        return boxed instanceof String s ? s : null;
    }

    private static int intValue(Object boxed) {
        return boxed instanceof Number n ? n.intValue() : 0;
    }

    private static double doubleValue(Object boxed) {
        return boxed instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** One framed map's context (the engine's frame handle stand-in). */
    public interface FrameInfo {

        /** The frame's engine entity id (the "frame-<id>" marker name). */
        int entityId();

        /** The frame's wall cell x. */
        int x();

        /** The frame's wall cell z. */
        int z();

        /** The horizontal facing the frame points away from its wall (0-3). */
        int facing();

        /** The dimension the frame hangs in. */
        int dimension();

        /** The displayed map stack (a copy at size 1 — the frame's own rule). */
        ItemStack display();

        /** The frame's chunk key (the wire's tracking gate). */
        long chunkKey();
    }
}
