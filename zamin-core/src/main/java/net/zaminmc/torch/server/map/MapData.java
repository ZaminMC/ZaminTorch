package net.zaminmc.torch.server.map;

import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One map's data (the reference's {@code world/map/SavedMapData.java} 1:1):
 * the 128x128 color canvas, the scale byte, the center pair, the dimension
 * byte, the named decorations (the player markers, the frame markers, the
 * off-map arrows), and the per-player trackers ({@link Holder}) that own
 * each observer's dirty region and the decorations-only resend cadence.
 *
 * <p>The engine's player handle is the {@link PlayerSession}; the laws are
 * the reference's own — {@link #updateCenter} (the 128*2^scale grid
 * snap), {@link #addDecoration} (the canvas mapping with the 63-unit
 * border, the off-map type-6 arrows, the nether's spinning-marker
 * quirk), and the holder walk's dirty-region accumulation. The terrain
 * scan ({@code FilledMapItem.update}) lives in {@link MapSystem} beside
 * its world consults; this class carries the state and the per-map
 * laws.</p>
 */
public final class MapData {

    /** The canvas size (the reference's 16384-byte colors array). */
    public static final int CANVAS_SIZE = 128;

    /** The scale ceiling (SavedMapData.readNbt's clamp). */
    public static final int MAX_SCALE = 4;

    public int centerX;
    public int centerZ;
    public byte dimension;
    public byte scale;
    public final byte[] colors = new byte[CANVAS_SIZE * CANVAS_SIZE];
    /** The per-player trackers, in join order (the holders list). */
    public final List<Holder> holders = new ArrayList<>();
    private final Map<PlayerSession, Holder> holdersByPlayer = new LinkedHashMap<>();
    /** The named decorations, insertion-ordered (the linked map's own law). */
    public final Map<String, MapDecoration> decorations = new LinkedHashMap<>();

    /**
     * The centering snap (SavedMapData.updateCenter lines 33-39): the
     * 128*2^scale grid, the +64 offset, the center landing on the grid
     * cell's middle minus 64 — the map's own coordinate anchor.
     */
    public void updateCenter(double x, double z, int scale) {
        int i = 128 * (1 << scale);
        int j = (int) Math.floor((x + 64.0) / i);
        int k = (int) Math.floor((z + 64.0) / i);
        this.centerX = j * i + i / 2 - 64;
        this.centerZ = k * i + i / 2 - 64;
    }

    /**
     * The map-center read for the store (the loaded rows restore the
     * center directly — the reference's readNbt rows).
     */
    public void restoreCenter(int centerX, int centerZ, int scale, int dimension) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = (byte) Math.max(0, Math.min(MAX_SCALE, scale));
        this.dimension = (byte) dimension;
    }

    /**
     * The dirty-cell fan-out (SavedMapData.markDirty(int, int) lines
     * 188-194): every holder's region grows to include the cell.
     */
    public void markDirty(int x, int y) {
        for (Holder holder : holders) {
            holder.markDirty(x, y);
        }
    }

    /**
     * The holder consult (SavedMapData.addHolder lines 196-205): the
     * existing tracker or a fresh one joined to both maps.
     */
    public Holder addHolder(PlayerSession player) {
        Holder holder = holdersByPlayer.get(player);
        if (holder == null) {
            holder = new Holder(player);
            holdersByPlayer.put(player, holder);
            holders.add(holder);
        }
        return holder;
    }

    /** The holder lookup for the packet consult (createUpdatePacket lines 183-186). */
    public Holder holderOf(PlayerSession player) {
        return holdersByPlayer.get(player);
    }

    /**
     * The holder removal (tickHolder's else arm, lines 107-110): the
     * player left the map's audience.
     */
    public void removeHolder(PlayerSession player) {
        Holder holder = holdersByPlayer.remove(player);
        if (holder != null) {
            holders.remove(holder);
        }
    }

    /**
     * The decoration's canvas mapping (SavedMapData.addDecoration lines
     * 140-181): the (x - center) / 2^scale fractions doubled onto the
     * -128..127 canvas, the 63-unit border (the on-map band), the
     * sixteen-step rotation byte, the off-map arrows (type 6, the border
     * pin at 127), the >320 removal, and the nether's spinning-marker
     * quirk ({@code k*k*34187121 + k*121 >> 15 & 15} over the world's
     * own time-of-day clock).
     */
    public void addDecoration(int type, long timeOfDay, String id,
                              double x, double z, double rotation) {
        int i = 1 << this.scale;
        float f = (float) (x - this.centerX) / i;
        float f1 = (float) (z - this.centerZ) / i;
        byte b0 = (byte) (f * 2.0F + 0.5F);
        byte b1 = (byte) (f1 * 2.0F + 0.5F);
        int j = 63;
        byte b2;
        if (f >= -j && f1 >= -j && f <= j && f1 <= j) {
            rotation += rotation < 0.0 ? -8.0 : 8.0;
            b2 = (byte) (rotation * 16.0 / 360.0);
            if (this.dimension < 0) {
                // The nether quirk: the marker spins with the world clock.
                int k = (int) (timeOfDay / 10L);
                b2 = (byte) (k * k * 34187121 + k * 121 >> 15 & 15);
            }
        } else {
            if (!(Math.abs(f) < 320.0F) || !(Math.abs(f1) < 320.0F)) {
                this.decorations.remove(id);
                return;
            }
            type = 6;
            b2 = 0;
            if (f <= -j) {
                b0 = (byte) (j * 2 + 2.5);
            }
            if (f1 <= -j) {
                b1 = (byte) (j * 2 + 2.5);
            }
            if (f >= j) {
                b0 = (byte) (j * 2 + 1);
            }
            if (f1 >= j) {
                b1 = (byte) (j * 2 + 1);
            }
        }
        this.decorations.put(id, new MapDecoration((byte) type, b0, b1, b2));
    }

    /**
     * One per-player tracker (SavedMapData.Holder 1:1): the dirty-region
     * accumulation (the first dirty cell anchors the region, later cells
     * grow it), the resend cadence (every 5th consult re-sends the
     * decorations alone), and the terrain scan's column step (the 16-band
     * rotation that spreads the walk over the ticks).
     */
    public static final class Holder {

        public final PlayerSession player;
        private boolean dirty = true;
        private int dirtyMinX = 0;
        private int dirtyMinY = 0;
        private int dirtyMaxX = 127;
        private int dirtyMaxY = 127;
        private int ticks;
        public int step;

        Holder(PlayerSession player) {
            this.player = player;
        }

        /**
         * The update-packet consult (Holder.createUpdatePacket lines
         * 221-241): a dirty region answers the colors slice immediately
         * (the region reset with it), a clean tracker answers the
         * decorations alone every 5th consult — null otherwise.
         */
        public MapUpdatePayload createUpdatePayload(int mapId, byte scale,
                                                    Map<String, MapDecoration> decorations,
                                                    byte[] colors) {
            if (this.dirty) {
                this.dirty = false;
                return new MapUpdatePayload(mapId, scale,
                        new ArrayList<>(decorations.values()),
                        this.dirtyMinX, this.dirtyMinY,
                        this.dirtyMaxX + 1 - this.dirtyMinX,
                        this.dirtyMaxY + 1 - this.dirtyMinY,
                        sliceColors(colors, this.dirtyMinX, this.dirtyMinY,
                                this.dirtyMaxX + 1 - this.dirtyMinX,
                                this.dirtyMaxY + 1 - this.dirtyMinY));
            } else {
                return this.ticks++ % 5 == 0
                        ? new MapUpdatePayload(mapId, scale,
                        new ArrayList<>(decorations.values()), 0, 0, 0, 0, new byte[0])
                        : null;
            }
        }

        /** The dirty-cell grow (Holder.markDirty lines 243-256). */
        public void markDirty(int x, int y) {
            if (this.dirty) {
                this.dirtyMinX = Math.min(this.dirtyMinX, x);
                this.dirtyMinY = Math.min(this.dirtyMinY, y);
                this.dirtyMaxX = Math.max(this.dirtyMaxX, x);
                this.dirtyMaxY = Math.max(this.dirtyMaxY, y);
            } else {
                this.dirty = true;
                this.dirtyMinX = x;
                this.dirtyMinY = y;
                this.dirtyMaxX = x;
                this.dirtyMaxY = y;
            }
        }
    }

    /**
     * The colors slice (MapDataS2CPacket ctor lines 34-40): the dirty
     * rectangle's rows pulled out of the 128-wide canvas, row-major over
     * the region's own width.
     */
    private static byte[] sliceColors(byte[] colors, int minX, int minY,
                                      int width, int height) {
        byte[] slice = new byte[width * height];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                slice[i + j * width] = colors[minX + i + (minY + j) * CANVAS_SIZE];
            }
        }
        return slice;
    }

    /**
     * The 0x34 wire payload (the reference's MapDataS2CPacket fields): the
     * map id (the stack's damage), the scale byte, the icon rows, and the
     * dirty rectangle (width 0 = the decorations-only resend).
     */
    public record MapUpdatePayload(int mapId, byte scale, List<MapDecoration> icons,
                                   int minX, int minY, int width, int height,
                                   byte[] colors) {
    }
}
