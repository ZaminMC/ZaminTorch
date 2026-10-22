package net.zaminmc.torch.server.map;

import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE MAP ITEM FAMILY (the reference's EmptyMapItem/FilledMapItem/
 * SavedMapData/MapDataS2CPacket/CloneMapRecipe/UpscaleMapRecipe whole):
 * the use's filled-map creation with the count-law id allocation, the
 * damage-row reassign quirk, the terrain scan's hand-checked column pins
 * (the multiset + the height shading + the water-depth special + the
 * nether's noise arm), the decoration law (the canvas mapping, the
 * off-map arrows, the nether spin), the holder cadence and the dirty
 * region, the 0x34 payload's shape, the clone and scale-up laws through
 * the REAL window-click paths, and the ZMP persistence round-trip.
 */
class MapItemTest {

    private final String worldPrefix = "mi" + Math.abs(UUID.randomUUID().hashCode() % 100000);

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The model laws (the reference's own walks, hand-checked)
    // ------------------------------------------------------------------

    /** The flat fake column: one uniform terrain across every column. */
    private static final class FakeSurface implements MapSystem.Surface {

        private final java.util.function.BiFunction<Integer, Integer, BlockType> columnBlocks;

        FakeSurface(java.util.function.BiFunction<Integer, Integer, BlockType> columnBlocks) {
            this.columnBlocks = columnBlocks;
        }

        @Override
        public Column columnAt(int dimension, int x, int z) {
            return new Column() {
                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public int height(int localX, int localZ) {
                    // The light-filter walk: the highest filtering block + 1
                    // (the heightmap's own law).
                    for (int yy = 255; yy >= 0; yy--) {
                        if (isFiltering(block(0, yy, 0))) {
                            return yy + 1;
                        }
                    }
                    return 0;
                }

                @Override
                public BlockType block(int localX, int y, int localZ) {
                    return columnBlocks.apply(x, y);
                }
            };
        }

        @Override
        public long timeOfDay(int dimension) {
            return 0;
        }

        @Override
        public Position spawnPosition() {
            return new Position(0, 64, 0);
        }

        private static boolean isFiltering(BlockType type) {
            return net.zaminmc.torch.server.block.BlockLightTable.filter(type) > 0;
        }
    }

    /**
     * The one registry the whole suite reads (built once — the walk
     * consults blocks tens of thousands of times, and a per-call rebuild
     * would spend the whole budget re-freezing the table): the built-ins
     * with the fluids on top (the engine's own boot chain's fluid leg —
     * the water depth walk's own blocks).
     */
    private static final net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry BLOCKS =
            net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                    net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(
                            new net.zaminmc.torch.server.block.BlockRegistryBuilder())).freeze();

    /** The memoized lookups over the one registry. */
    private static final java.util.Map<String, BlockType> BLOCK_CACHE = new java.util.HashMap<>();

    /** The block lookup over the test registry. */
    private static BlockType block(String name) {
        return BLOCK_CACHE.computeIfAbsent(name,
                n -> BLOCKS.lookup(Identifier.parse("minecraft:" + n)).orElseThrow());
    }

    /**
     * THE TERRAIN SCAN'S HAND-CHECKED COLUMNS (FilledMapItem.update lines
     * 56-172): the scale-0 map centered at (128, 128) with the player at
     * (128.5, 128.5) — the holder's first walk takes column k1=1 (the
     * step-1 band), whose cells read the world columns x = 64+k1, z =
     * 64+l1.
     *
     * <p>Stone at y=64 everywhere: every cell's descend lands on the stone
     * (k4 = 64, d1 = 64.0); the first READ cell (l1=-1, allowed by the
     * read gate but refused by the write gate) sets d0 = 64, so every
     * WRITTEN cell's d2 is the parity term alone (+-0.2 — inside band 1)
     * and the byte lands at STONE.id * 4 + 1 = 45.</p>
     *
     * <p>The flag re-arm (the reference's own {@code b0 != b1 -> flag =
     * true}, line 166): the first walk's every-cell change keeps the flag
     * armed, so EVERY column after the band's first write walks too — the
     * whole canvas fills on the first update (the vanilla map's instant
     * first draw; the 16-column rotation only paces LATER updates).
     * Scale 0's circle (j1 = 128) covers every cell of the canvas.</p>
     */
    @Test
    void theColorWalkPinsTheFlatStoneColumn() {
        MapSystem maps = new MapSystem(new FakeSurface((x, y) ->
                y == 64 ? block("stone") : block("air")));
        int id = maps.createMapAt(0, 128.5, 128.5);
        MapData data = maps.mapDataOf(0, id);
        PlayerSession walker = walker(0, 128.5, 128.5, 0.0f);
        maps.update(walker, data);
        // Column k1=1 (the step-1 band): every cell reads 45 (STONE * 4 +
        // band 1).
        for (int l1 = 0; l1 < 128; l1++) {
            assertEquals(45, data.colors[1 + l1 * 128],
                    "cell (1, " + l1 + ")");
        }
        // The flag re-arm cascade: the columns after the band's first
        // write walked too — the whole canvas filled (45 at the far
        // corner, 45 right beside the band).
        assertEquals(45, data.colors[2 + 5 * 128],
                "the re-arm cascade walked column k1=2");
        assertEquals(45, data.colors[127 + 127 * 128],
                "the cascade reached the far corner");
    }

    /**
     * The water column (the reference's depth special): water sources at
     * y=61..64 over stone at y=60 — the descend lands on the water top
     * (k4 = 64, WATER wins the multiset), the depth walk counts k3 = 4
     * (the four water cells + the terminating stone read... the walk
     * counts four reads: 63, 62, 61, 60 — three water + the stone), so
     * d2 = 0.4 + parity * 0.2: the EVEN cells land band 2 (12*4+2 = 50)
     * and the ODD cells band 1 (12*4+1 = 49) — the famous water ripple.
     */
    @Test
    void theColorWalkPinsTheWaterDepthSpecial() {
        MapSystem maps = new MapSystem(new FakeSurface((x, y) -> {
            if (y >= 61 && y <= 64) {
                return block("water");
            }
            return y == 60 ? block("stone") : block("air");
        }));
        int id = maps.createMapAt(0, 128.5, 128.5);
        MapData data = maps.mapDataOf(0, id);
        maps.update(walker(0, 128.5, 128.5, 0.0f), data);
        for (int l1 = 0; l1 < 128; l1++) {
            int expected = ((1 + l1) & 1) == 0 ? 50 : 49;
            assertEquals(expected, data.colors[1 + l1 * 128],
                    "water cell (1, " + l1 + ")");
        }
    }

    /**
     * The nether's noise arm (lines 91-102): the deterministic dirt/stone
     * pattern over the column's own coordinates — l3 = k2 + l2 * 231871,
     * l3 = l3*l3*31287121 + l3*11, the (l3 >> 20) & 1 gate choosing ten
     * dirt (color DIRT) or a hundred stone (color STONE). The pinned
     * columns (hand-computed): (65, 63), (65, 64), (65, 65) read DIRT and
     * (65, 66) reads STONE; d1 = 100.0 flat so every written cell lands
     * band 1 — the dirt cells 10*4+1 = 41, the stone cell 11*4+1 = 45.
     */
    @Test
    void theColorWalkPinsTheNetherNoiseArm() {
        MapSystem maps = new MapSystem(new FakeSurface((x, y) -> block("netherrack")));
        int id = maps.createMapAt(-1, 128.5, 128.5);
        MapData data = maps.mapDataOf(-1, id);
        maps.update(walker(-1, 128.5, 128.5, 0.0f), data);
        // The nether's halved band (j1 = 64) with the OUTER-RING parity
        // gate (flag1: i2^2 + j2^2 > (j1-2)^2 — column k1=1 sits at
        // i2 = -63 whose square alone exceeds 62^2, so the whole column
        // writes only the ODD (k1 + l1) cells — the vanilla ring): the
        // pinned even-l1 cells inside the circle, their noise bits
        // hand-computed over the int32 walk: l1=60 -> (65, 124): DIRT ->
        // 41; l1=62 -> (65, 126): STONE -> 45; l1=64 -> (65, 128): STONE
        // -> 45.
        assertEquals(41, data.colors[1 + 60 * 128], "the (65,124) dirt cell");
        assertEquals(45, data.colors[1 + 62 * 128], "the (65,126) stone cell");
        assertEquals(45, data.colors[1 + 64 * 128], "the (65,128) stone cell");
        // The ring's even-parity refusal + the outside cell: never written.
        assertEquals(0, data.colors[1 + 61 * 128], "the odd cell refused (the ring)");
        assertEquals(0, data.colors[1 + 0], "the outside cell untouched");
    }

    /**
     * The decoration law (SavedMapData.addDecoration lines 140-181): the
     * canvas mapping ((x - center) / 2^scale doubled onto the -128..127
     * canvas with the +0.5 round), the sixteen-step rotation byte, the
     * 63-unit border, the off-map type-6 arrows with the border pins, the
     * >320 removal, and the nether's spinning marker.
     */
    @Test
    void theDecorationLawMatchesTheReference() {
        MapData data = new MapData();
        data.restoreCenter(128, 128, 0, 0);
        // The on-map marker: 8.5 blocks off-center -> canvas 17, yaw 0 ->
        // rotation (0 + 8) * 16 / 360 = 0.
        data.addDecoration(0, 0, "Abasing", 136.5, 136.5, 0.0);
        MapDecoration marker = data.decorations.get("Abasing");
        assertNotNull(marker);
        assertEquals(0, marker.getType());
        assertEquals(17, marker.getX());
        assertEquals(17, marker.getY());
        assertEquals(0, marker.getRotation());
        // The yaw 90 -> (90 + 8) * 16 / 360 = 4.
        data.addDecoration(0, 0, "Abasing", 136.5, 136.5, 90.0);
        assertEquals(4, data.decorations.get("Abasing").getRotation());
        // The negative yaw: -10 + -8 = -18 -> -18 * 16 / 360 = (byte) 0xF0
        // (-0.8 truncates to 0).
        data.addDecoration(0, 0, "Abasing", 136.5, 136.5, -10.0);
        assertEquals(0, data.decorations.get("Abasing").getRotation());

        // The off-map arrow: 65 blocks east (f = 65.5 >= 63) -> type 6
        // with the x pinned at 63*2+1 = 127, z dead-center (f1 = 0 ->
        // b1 = 0.5 -> 0).
        data.addDecoration(0, 0, "Abasing", 193.5, 128.0, 0.0);
        MapDecoration arrow = data.decorations.get("Abasing");
        assertEquals(6, arrow.getType());
        assertEquals(127, arrow.getX());
        assertEquals(0, arrow.getY());
        assertEquals(0, arrow.getRotation());

        // The far removal: 320+ blocks off-center drops the marker.
        data.addDecoration(0, 0, "Abasing", 128.5 + 320, 128.5, 0.0);
        assertNull(data.decorations.get("Abasing"));

        // The nether spin (dimension -1): timeOfDay 10 -> k = 1 ->
        // (34187121 + 121) >> 15 & 15 = 3.
        MapData nether = new MapData();
        nether.restoreCenter(128, 128, 0, -1);
        nether.addDecoration(0, 10, "Abasing", 136.5, 136.5, 0.0);
        assertEquals(3, nether.decorations.get("Abasing").getRotation());
    }

    /**
     * The holder cadence and the dirty region (Holder.createUpdatePacket
     * lines 221-241 + markDirty lines 243-256): the first consult after a
     * dirty mark answers the region (grown by later cells), the clean
     * tracker answers null four times then the decorations-only resend,
     * and a new dirty cell re-anchors the region.
     */
    @Test
    void theHolderCadenceAndDirtyRegionLaw() {
        MapData data = new MapData();
        data.restoreCenter(0, 0, 0, 0);
        MapData.Holder holder = data.addHolder(null);
        assertEquals(1, data.holders.size());
        // The fresh tracker's initial state is DIRTY over the whole canvas
        // (the reference's own first-consult shape — the join's full
        // resend); consume it before growing a small region.
        MapData.MapUpdatePayload join = holder.createUpdatePayload(
                3, (byte) 2, data.decorations, data.colors);
        assertNotNull(join);
        assertEquals(128, join.width(), "the fresh tracker's full canvas");
        data.colors[5 + 7 * 128] = 42;
        data.markDirty(5, 7);
        MapData.MapUpdatePayload first = holder.createUpdatePayload(
                3, (byte) 2, data.decorations, data.colors);
        assertNotNull(first);
        assertEquals(3, first.mapId());
        assertEquals(2, first.scale());
        assertEquals(5, first.minX());
        assertEquals(7, first.minY());
        assertEquals(1, first.width());
        assertEquals(1, first.height());
        assertArrayEquals(new byte[] {42}, first.colors());
        // The clean cadence: the FIRST clean consult re-sends the
        // decorations alone (0 % 5 == 0), then four nulls.
        MapData.MapUpdatePayload resend = holder.createUpdatePayload(
                3, (byte) 2, data.decorations, data.colors);
        assertNotNull(resend);
        assertEquals(0, resend.width());
        assertEquals(0, resend.colors().length);
        assertNull(holder.createUpdatePayload(3, (byte) 2, data.decorations, data.colors));
        assertNull(holder.createUpdatePayload(3, (byte) 2, data.decorations, data.colors));
        assertNull(holder.createUpdatePayload(3, (byte) 2, data.decorations, data.colors));
        assertNull(holder.createUpdatePayload(3, (byte) 2, data.decorations, data.colors));
        assertNotNull(holder.createUpdatePayload(3, (byte) 2, data.decorations, data.colors));
        // A new dirty cell re-anchors the region.
        data.markDirty(100, 120);
        MapData.MapUpdatePayload region = holder.createUpdatePayload(
                3, (byte) 2, data.decorations, data.colors);
        assertEquals(100, region.minX());
        assertEquals(120, region.minY());
        assertEquals(1, region.width());
        assertEquals(1, region.height());
    }

    /**
     * The 0x34 payload's color slice (MapDataS2CPacket ctor lines 34-40):
     * a 3x2 region pulled row-major out of the 128-wide canvas.
     */
    @Test
    void theColorsSliceIsRowMajorOverTheDirtyRectangle() {
        MapData data = new MapData();
        data.restoreCenter(0, 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                data.colors[10 + i + (20 + j) * 128] = (byte) (i + j * 3);
            }
        }
        MapData.Holder holder = data.addHolder(null);
        // Consume the fresh tracker's initial full-canvas dirty state (the
        // reference's own first-consult shape), then grow the region.
        assertNotNull(holder.createUpdatePayload(0, (byte) 0, data.decorations, data.colors));
        data.markDirty(10, 20);
        data.markDirty(12, 21);
        MapData.MapUpdatePayload payload = holder.createUpdatePayload(
                0, (byte) 0, data.decorations, data.colors);
        assertEquals(3, payload.width());
        assertEquals(2, payload.height());
        assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 5}, payload.colors());
    }

    /**
     * The clone and scale-up match laws (CloneMapRecipe.matches +
     * UpscaleMapRecipe.matches): the clone takes ONE filled map plus any
     * number of empty maps and refuses everything else; the upscale takes
     * the paper ring around the map and refuses a scale-4 source.
     */
    @Test
    void theCloneAndUpscaleMatchLaws() {
        MapSystem maps = new MapSystem(new FakeSurface((x, y) -> block("air")));
        ItemStack map0 = ItemStack.of(BuiltinItems.FILLED_MAP).withDamage(0);
        // The clone's positive + negative shapes over a 2x2 grid.
        ItemStack[] cloneGrid = {map0, ItemStack.of(BuiltinItems.EMPTY_MAP),
                ItemStack.EMPTY, ItemStack.EMPTY};
        assertTrue(maps.cloneMatches(cloneGrid, 2));
        assertEquals(2, maps.cloneResult(cloneGrid, 2).count());
        assertEquals(0, maps.cloneResult(cloneGrid, 2).damage());
        // Two filled maps refuse.
        ItemStack[] twoMaps = {map0, map0, ItemStack.EMPTY, ItemStack.EMPTY};
        assertFalse(maps.cloneMatches(twoMaps, 2));
        // A filled map alone refuses (no empties).
        ItemStack[] alone = {map0, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        assertFalse(maps.cloneMatches(alone, 2));
        // A foreign item refuses.
        ItemStack[] foreign = {map0, ItemStack.of(BuiltinItems.PAPER),
                ItemStack.EMPTY, ItemStack.EMPTY};
        assertFalse(maps.cloneMatches(foreign, 2));
        // The name carries.
        ItemStack named = map0.withName("Home");
        ItemStack[] namedGrid = {named, ItemStack.of(BuiltinItems.EMPTY_MAP),
                ItemStack.EMPTY, ItemStack.EMPTY};
        assertEquals("Home", maps.cloneResult(namedGrid, 2).displayName());

        // The upscale's ring over a 3x3 grid: paper around the map.
        maps.createMapAt(0, 128.5, 128.5); // map 0 at scale 0
        ItemStack[] ring = new ItemStack[9];
        java.util.Arrays.fill(ring, ItemStack.of(BuiltinItems.PAPER));
        ring[4] = map0;
        assertTrue(maps.upscaleMatches(ring, 3, 0));
        ItemStack upscale = maps.upscaleResult(ring, 3);
        assertEquals(1, upscale.count());
        assertEquals(0, upscale.damage());
        assertTrue(upscale.mapScaling(), "the upscale result carries the marker");
        // A scale-4 source refuses (the gate).
        maps.mapDataOf(0, 0).scale = 4;
        assertFalse(maps.upscaleMatches(ring, 3, 0));
        maps.mapDataOf(0, 0).scale = 0;
        // A paper missing refuses.
        ring[0] = ItemStack.EMPTY;
        assertFalse(maps.upscaleMatches(ring, 3, 0));
        // A 2x2 grid never matches the ring.
        assertFalse(maps.upscaleMatches(cloneGrid, 2, 0));

        // The onResult walk (applyScaleUp): the new id + the scale+1 data
        // centered on the source's center.
        MapData source = maps.mapDataOf(0, 0);
        source.colors[3 + 4 * 128] = 45;
        ItemStack crafted = maps.upscaleResult(ring, 3);
        ItemStack scaled = maps.applyScaleUp(0, crafted);
        assertEquals(1, scaled.damage(), "the taken stack carries the new id");
        MapData grown = maps.mapDataOf(0, 1);
        assertNotNull(grown);
        assertEquals(1, grown.scale);
        // The center re-snaps at the finer grid (updateCenter at scale 1:
        // the 256-block grid over the source's center — floor((128+64)/256)
        // = 0 -> 0*256 + 128 - 64 = 64; the vanilla move the formula owns).
        assertEquals(64, grown.centerX);
        assertEquals(64, grown.centerZ);
        assertEquals(0, grown.colors[3 + 4 * 128],
                "the scale-up starts a fresh canvas (the reference's new data)");
        assertTrue(scaled.mapScaling(), "the flag stays (the reference's own quirk)");
    }

    // ------------------------------------------------------------------
    // The live flow (the engine boot)
    // ------------------------------------------------------------------

    /**
     * THE USE (EmptyMapItem.startUsing): the held empty map becomes a
     * filled map with the id the dimension's count law allocated, the map
     * data centered on the player at scale 0, and the use stat landing on
     * the empty map's own row (the 1.8.8 registry name "map"). The second
     * and third uses walk the id sequence 1, 2.
     */
    @Test
    void theUseCreatesTheFilledMapWithTheAllocatedId() throws Exception {
        EngineServer server = boot("use");
        try {
            PlayerSession player = join(server, "Cartographer");
            Position at = player.position();
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP)));
            await(() -> !player.inventory().held().isEmpty(), "the empty map held");

            // The single-stack use: the held slot is REPLACED by the
            // filled map (EmptyMapItem's own return form).
            server.useItem(player);
            await(() -> !player.inventory().held().isEmpty()
                            && player.inventory().held().type().equals(BuiltinItems.FILLED_MAP),
                    "the held slot became the filled map");
            assertEquals(0, player.inventory().held().damage(), "the first map's id");
            assertEquals(1, player.inventory().held().count());
            MapData first = server.maps().mapDataOf(0, 0);
            assertNotNull(first, "the map data registered");
            assertEquals(0, first.scale);
            assertEquals(0, first.dimension);
            // The center snap at scale 0 (updateCenter over the player's
            // own position: the 128-block grid's middle minus 64).
            int grid = 128;
            int expectedX = (int) Math.floor((at.x() + 64.0) / grid) * grid + grid / 2 - 64;
            int expectedZ = (int) Math.floor((at.z() + 64.0) / grid) * grid + grid / 2 - 64;
            assertEquals(expectedX, first.centerX);
            assertEquals(expectedZ, first.centerZ);

            // The id sequence: two more uses walk ids 1 and 2.
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP)));
            await(() -> !player.inventory().held().isEmpty()
                            && player.inventory().held().type().equals(BuiltinItems.EMPTY_MAP),
                    "the second empty held");
            server.useItem(player);
            await(() -> server.maps().mapDataOf(0, 1) != null, "map 1 allocated");
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP)));
            await(() -> !player.inventory().held().isEmpty()
                            && player.inventory().held().type().equals(BuiltinItems.EMPTY_MAP),
                    "the third empty held");
            server.useItem(player);
            await(() -> server.maps().mapDataOf(0, 2) != null, "map 2 allocated");

            // The many-stack use: the held stack keeps its spares, the
            // filled map ADDS to the inventory (the addItem arm).
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP, 3)));
            await(() -> player.inventory().held().count() == 3, "three empties held");
            server.useItem(player);
            await(() -> player.inventory().countOf(BuiltinItems.FILLED_MAP) >= 1
                    && player.inventory().held().type().equals(BuiltinItems.EMPTY_MAP)
                    && player.inventory().held().count() == 2,
                    "the many-stack use added the copy beside the spares");

            // The use stat (the empty map's own row — the "map" name).
            assertTrue(player.stats().get(
                    net.zaminmc.torch.server.stat.EngineStats.useItemOf(
                            BuiltinItems.EMPTY_MAP.identifier())) >= 4,
                    "the uses paid the stat");
        } finally {
            stop(server);
        }
    }

    /**
     * THE REASSIGN QUIRK (FilledMapItem.getSavedMapData lines 42-51): a
     * filled map naming an unknown id (the /give form) walks the holder
     * tick until the stack's damage is REASSIGNED to a fresh id with the
     * data at scale 3 centered on the world spawn.
     */
    @Test
    void theDamageRowReassignQuirk() throws Exception {
        EngineServer server = boot("reassign");
        try {
            PlayerSession player = join(server, "Explorer");
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.FILLED_MAP).withDamage(999)));
            await(() -> !player.inventory().held().isEmpty()
                    && player.inventory().held().type().equals(BuiltinItems.FILLED_MAP),
                    "the foreign map held");
            await(() -> player.inventory().held().damage() == 0,
                    "the holder tick reassigned the fresh id 0");
            MapData reassigned = server.maps().mapDataOf(0, 0);
            assertNotNull(reassigned);
            assertEquals(3, reassigned.scale, "the reassign's scale-3 overview");
            Position spawn = server.world().spawnPosition();
            // updateCenter(spawn) at scale 3: the 1024-block grid snap.
            int i = 128 * 8;
            int j = (int) Math.floor((spawn.x() + 64.0) / i);
            int k = (int) Math.floor((spawn.z() + 64.0) / i);
            assertEquals(j * i + i / 2 - 64, reassigned.centerX);
            assertEquals(k * i + i / 2 - 64, reassigned.centerZ);
        } finally {
            stop(server);
        }
    }

    /**
     * THE CLONE through the real window-click path: the 2x2 player grid
     * with the source map and an empty map previews a two-copy stack
     * sharing the id, and the take lands the copies on the cursor.
     */
    @Test
    void theCloneCraftThroughTheWindow() throws Exception {
        EngineServer server = boot("clone");
        try {
            PlayerSession player = join(server, "Copier");
            // Make map 0 through the use.
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP)));
            await(() -> !player.inventory().held().isEmpty(), "the empty map held");
            server.useItem(player);
            await(() -> player.inventory().held().type().equals(BuiltinItems.FILLED_MAP),
                    "map 0 created");
            await(() -> player.inventory().held().damage() == 0, "the id 0");

            // Grid: the map + an empty map (the 2x2 window's cells 1 and 2).
            server.ticker().submit(() -> {
                player.inventory().setSlot(1, ItemStack.of(BuiltinItems.EMPTY_MAP));
                player.crafting().setCell(0, player.inventory().held());
                player.crafting().setCell(1, ItemStack.of(BuiltinItems.EMPTY_MAP));
                player.inventory().setSlot(0, ItemStack.EMPTY);
            });
            await(() -> !player.crafting().cell(0).isEmpty(), "the grid loaded");
            // The preview: two copies sharing id 0.
            await(() -> {
                ItemStack preview = server.craftingResult(player);
                return !preview.isEmpty() && preview.count() == 2
                        && preview.damage() == 0
                        && preview.type().equals(BuiltinItems.FILLED_MAP);
            }, "the preview shows the two copies");
            // The take: the cursor holds the copies.
            server.windowClick(player, 0, 0, 0, 0, accepted -> { });
            await(() -> !player.inventory().cursor().isEmpty()
                    && player.inventory().cursor().count() == 2
                    && player.inventory().cursor().type().equals(BuiltinItems.FILLED_MAP)
                    && player.inventory().cursor().damage() == 0,
                    "the cursor holds the two copies of map 0");
        } finally {
            stop(server);
        }
    }

    /**
     * THE SCALE-UP through the real table window: the paper ring around
     * the source map previews the flagged stack, and the take allocates
     * the new id (the onResult walk) — the cursor's map carries it and
     * the new data sits at scale 1 on the same center.
     */
    @Test
    void theUpscaleCraftThroughTheTableWindow() throws Exception {
        EngineServer server = boot("upscale");
        try {
            PlayerSession player = join(server, "Zoomer");
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP)));
            await(() -> !player.inventory().held().isEmpty(), "the empty map held");
            server.useItem(player);
            await(() -> !player.inventory().held().isEmpty()
                    && player.inventory().held().type().equals(BuiltinItems.FILLED_MAP)
                    && player.inventory().held().damage() == 0, "map 0 created");
            int sourceCenterX = server.maps().mapDataOf(0, 0).centerX;
            int sourceCenterZ = server.maps().mapDataOf(0, 0).centerZ;

            // Open the table window and load the ring.
            server.ticker().submit(() -> {
                player.openContainerWindow(1,
                        PlayerSession.ContainerKind.CRAFTING_TABLE, null);
                ItemStack map = player.inventory().held();
                player.inventory().setSlot(0, ItemStack.EMPTY);
                player.tableCrafting().setCell(4, map);
                for (int cell = 0; cell < 9; cell++) {
                    if (cell != 4) {
                        player.tableCrafting().setCell(cell,
                                ItemStack.of(BuiltinItems.PAPER));
                    }
                }
            });
            await(() -> !player.tableCrafting().cell(4).isEmpty(), "the ring loaded");
            // The preview: the flagged stack (the same id).
            await(() -> {
                ItemStack preview = server.containerResult(player);
                return !preview.isEmpty() && preview.mapScaling()
                        && preview.damage() == 0
                        && preview.type().equals(BuiltinItems.FILLED_MAP);
            }, "the preview shows the flagged stack");
            // The take: the cursor's map carries the NEW id.
            server.windowClick(player, 1, 0, 0, 0, accepted -> { });
            await(() -> !player.inventory().cursor().isEmpty()
                            && player.inventory().cursor().type().equals(BuiltinItems.FILLED_MAP)
                            && player.inventory().cursor().damage() == 1,
                    "the cursor's map carries the new id 1");
            MapData grown = server.maps().mapDataOf(0, 1);
            assertNotNull(grown, "the scale+1 data registered");
            assertEquals(1, grown.scale);
            // The center re-snaps at the finer grid (updateCenter at the
            // new scale over the source's center — the vanilla move).
            int i = 128 * 2;
            int expectedX = (int) Math.floor((sourceCenterX + 64.0) / i) * i + i / 2 - 64;
            int expectedZ = (int) Math.floor((sourceCenterZ + 64.0) / i) * i + i / 2 - 64;
            assertEquals(expectedX, grown.centerX, "the center re-snapped at scale 1");
            assertEquals(expectedZ, grown.centerZ);
        } finally {
            stop(server);
        }
    }

    /**
     * THE PERSISTENCE ROUND-TRIP: the maps survive the restart — the ids,
     * the scales, the centers, the colors, and the id allocator's counter
     * (the next use after the reboot allocates the NEXT id, never a
     * collision).
     */
    @Test
    void thePersistenceRoundTrip() throws Exception {
        Path dir = dataDir.resolve("persist");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, worldPrefix + "-persist",
                "it", 20, 2, 20, dir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        await(() -> server.state() == ServerState.RUNNING, "the server up");
        try {
            PlayerSession player = join(server, "Archivist");
            server.ticker().submit(() -> player.inventory().setSlot(0,
                    ItemStack.of(BuiltinItems.EMPTY_MAP, 2)));
            await(() -> !player.inventory().held().isEmpty(), "the empties held");
            server.useItem(player);
            await(() -> server.maps().mapDataOf(0, 0) != null, "map 0 created");
            server.useItem(player);
            await(() -> server.maps().mapDataOf(0, 1) != null, "map 1 created");
            // Paint a color into map 0's canvas.
            server.ticker().submit(() ->
                    server.maps().mapDataOf(0, 0).colors[3 + 4 * 128] = 45);
            await(() -> server.maps().mapDataOf(0, 0).colors[3 + 4 * 128] == 45,
                    "the color painted");
        } finally {
            stop(server);
        }
        // The reboot over the same data dir.
        EngineServer reboot = new EngineServer(new EngineConfig("127.0.0.1", 0,
                worldPrefix + "-persist", "it", 20, 2, 20, dir.toString()));
        reboot.start();
        try {
            await(() -> reboot.state() == ServerState.RUNNING, "the reboot up");
            assertNotNull(reboot.maps(), "the map system booted");
            MapData zero = reboot.maps().mapDataOf(0, 0);
            MapData one = reboot.maps().mapDataOf(0, 1);
            assertNotNull(zero, "map 0 restored");
            assertNotNull(one, "map 1 restored");
            assertEquals(0, zero.scale);
            assertEquals(0, zero.centerX);
            assertEquals(0, zero.centerZ);
            assertEquals(45, zero.colors[3 + 4 * 128], "the painted color survived");
            // The allocator: the next id is 2 (no collision).
            assertEquals(2, reboot.maps().allocateId(0));
        } finally {
            stop(reboot);
        }
    }

    // ------------------------------------------------------------------
    // The fixtures
    // ------------------------------------------------------------------

    /**
     * The bare walker the model-level scans take (a real session over a
     * recording link — the scan reads the dimension, the position and the
     * holder alone).
     */
    private static PlayerSession walker(int dimension, double x, double z, float yaw) {
        PlayerSession session = new PlayerSession(UUID.randomUUID(),
                "Walker" + Math.abs(UUID.randomUUID().hashCode() % 1000), new RecordingLink());
        session.setDimension(dimension);
        session.applyMovement(new Position(x, 64, z),
                new net.zaminmc.torch.util.Rotation(yaw, 0.0f), true);
        return session;
    }

    private EngineServer boot(String worldName) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, worldPrefix + "-" + worldName,
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        await(() -> server.state() == ServerState.RUNNING, "the server up");
        return server;
    }

    private static PlayerSession join(EngineServer server, String name) {
        RecordingLink link = new RecordingLink();
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    /** The 0x34 recorder (the wire-shape pin's sink). */
    static final class RecordingLink implements ClientLink {
        final List<int[]> mapPackets = new ArrayList<>();
        final List<byte[]> mapColors = new ArrayList<>();
        final List<List<MapIcon>> mapIcons = new ArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void sendMapData(int mapId, byte scale, List<MapIcon> icons,
                                int minX, int minY, int width, int height, byte[] colors,
                                long gateChunk) {
            mapPackets.add(new int[] {mapId, scale, minX, minY, width, height});
            mapColors.add(colors.clone());
            mapIcons.add(new ArrayList<>(icons));
        }
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void stop(EngineServer server) {
        server.shutdown(null);
        try {
            long deadline = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < deadline) {
                if (server.state() == ServerState.STOPPED) {
                    return;
                }
                Thread.sleep(25);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
