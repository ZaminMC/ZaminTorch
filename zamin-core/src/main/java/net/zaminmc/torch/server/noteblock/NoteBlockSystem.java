package net.zaminmc.torch.server.noteblock;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.redstone.RedstoneSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Position;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The note block family 1:1 (the reference's NoteBlock +
 * NoteBlockBlockEntity, register line `noteblock` id 25, strength 0.8,
 * Material.WOOD): one note tile per placed note block carrying the pitch
 * byte (0-24, the reference's own clamp on read) and the runtime powered
 * flag, the three play arms (the use's tune-and-play, the survival
 * attack's play, the power-edge's play), and the two-packet broadcast the
 * reference's server emits — the Block Action (0x24, block 25, the
 * instrument, the note) through the observers plus the server-side
 * doEvent sound ("note." + instrument, volume 3.0F, the pitch walk
 * 2^((note-12)/12), the ServerWorldEventListener broadcast).
 *
 * <p><b>The reference walk (NoteBlock.java):</b> {@code neighborChanged}
 * reads {@code hasNeighborSignal} — only the RISING edge plays (the
 * falling edge just clears the flag, no replay); {@code use} tunes the
 * pitch ({@code (note + 1) % 25}) and plays; {@code startMining} (the
 * survival dig's start — creative never fires it, the manager's own
 * creative arm breaks instantly) plays. {@code playNote} (the BE's own)
 * plays ONLY when the block above is air-material, selects the instrument
 * from the block BELOW's material (STONE→1 bass drum, SAND→2 snare,
 * GLASS→3 hat, WOOD→4 bass guitar, else 0 harp), and queues the block
 * event; the server's {@code doBlockEvents} relays it as the Block Action
 * packet within 64 blocks after calling {@code doEvent} server-side.</p>
 *
 * <p><b>The material table</b> (the reference's own registry assignments,
 * Block.java/Blocks.java): the engine's registered set classified per the
 * reference — the quirks preserved: gravel and soul sand are SAND
 * material (snare), the infested family is CLAY (harp — never bass drum),
 * glowstone and the sea lantern are GLASS (hat), the nether brick fence
 * is STONE (bass drum, the line-1236 registration), wool/beds are WOOL
 * and torches/ladders are DECORATION (all harp), and a note block below a
 * note block is WOOD (bass guitar).</p>
 */
public final class NoteBlockSystem implements WorldChangeListener {

    private static final Logger LOGGER = Logger.getLogger(NoteBlockSystem.class.getName());

    /** The reference's instrument order (NoteBlock.INSTRUMENTS): harp,
     *  bass drum, snare, hat, bass guitar — the wire ids. */
    static final int INSTRUMENT_HARP = 0;
    static final int INSTRUMENT_BASS_DRUM = 1;
    static final int INSTRUMENT_SNARE = 2;
    static final int INSTRUMENT_HAT = 3;
    static final int INSTRUMENT_BASS_GUITAR = 4;

    /** The note's band (NoteBlockBlockEntity.readNbt's clamp + tunePitch's
     *  modulo 25). */
    static final int NOTE_BAND = 25;

    /**
     * One note tile (the reference's NoteBlockBlockEntity): the pitch byte
     * 0-24 and the runtime powered flag. Only the note byte persists (the
     * reference's writeNbt writes "note" alone).
     */
    public static final class NoteTile {
        private byte note;
        private boolean powered;

        NoteTile(byte note, boolean powered) {
            this.note = note;
            this.powered = powered;
        }

        public byte note() {
            return note;
        }

        boolean isPowered() {
            return powered;
        }

        void tune() {
            note = (byte) ((note + 1) % NOTE_BAND);
        }

        void setPowered(boolean powered) {
            this.powered = powered;
        }
    }

    /** The note tiles by position (the placed-block set). */
    private final Map<BlockPosition, NoteTile> tiles = new ConcurrentHashMap<>();

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final FxManager fx;

    /** The note-action observers (the protocol adapter's Block Action). */
    public interface NoteActionListener {
        void onNoteAction(BlockPosition position, int instrument, int note);
    }

    private final List<NoteActionListener> noteActionListeners = new CopyOnWriteArrayList<>();

    /** Registers the wire observer (the protocol adapter). */
    public void addNoteActionListener(NoteActionListener listener) {
        noteActionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public NoteBlockSystem(EngineWorld world, RedstoneSystem redstone, FxManager fx) {
        this.world = Objects.requireNonNull(world, "world");
        this.redstone = Objects.requireNonNull(redstone, "redstone");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    // ------------------------------------------------------------- the material table

    /**
     * The STONE-material set (instrument 1 — bass drum): the engine's
     * registered blocks whose reference material is STONE. The infested
     * family sits OUT (CLAY material — the reference's own quirk) and so
     * does everything not stone under the reference.
     */
    private static final Set<String> STONE_MATERIAL = Set.of(
            "stone", "bedrock", "cobblestone",
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
            "stone_slab", "stone_slab_top", "double_stone_slab", "stone_brick_slab",
            "cobblestone_slab",
            "double_sandstone_slab", "double_cobblestone_slab", "double_stone_brick_slab",
            "double_stone_slab_seamless", "double_stone_slab2_seamless",
            "furnace", "furnace_lit", "mob_spawner",
            "furnace_north", "furnace_south", "furnace_west", "furnace_east",
            "furnace_lit_north", "furnace_lit_south", "furnace_lit_west", "furnace_lit_east",
            "netherrack", "nether_bricks", "nether_brick_fence",
            "nether_brick_stairs_east", "nether_brick_stairs_north",
            "nether_brick_stairs_south", "nether_brick_stairs_west",
            "nether_brick_stairs_east_top", "nether_brick_stairs_north_top",
            "nether_brick_stairs_south_top", "nether_brick_stairs_west_top",
            "obsidian", "enchanting_table",
            "end_portal_frame_east", "end_portal_frame_north", "end_portal_frame_south",
            "end_portal_frame_west", "end_portal_frame_eye_east", "end_portal_frame_eye_north",
            "end_portal_frame_eye_south", "end_portal_frame_eye_west",
            "coal_ore", "diamond_ore", "iron_ore", "gold_ore", "redstone_ore", "quartz_ore",
            "sandstone", "chiseled_sandstone", "smooth_sandstone", "sandstone_slab",
            "sandstone_stairs_east", "sandstone_stairs_north",
            "sandstone_stairs_south", "sandstone_stairs_west",
            "sandstone_stairs_east_top", "sandstone_stairs_north_top",
            "sandstone_stairs_south_top", "sandstone_stairs_west_top",
            "cobblestone_stairs_east", "cobblestone_stairs_north",
            "cobblestone_stairs_south", "cobblestone_stairs_west",
            "cobblestone_stairs_east_top", "cobblestone_stairs_north_top",
            "cobblestone_stairs_south_top", "cobblestone_stairs_west_top",
            "prismarine_rough", "prismarine_bricks", "prismarine_dark",
            "stained_clay_orange", "stained_clay_yellow",
            "hardened_clay", "stained_clay_white", "stained_clay_magenta",
            "stained_clay_light_blue", "stained_clay_lime", "stained_clay_pink",
            "stained_clay_gray", "stained_clay_silver", "stained_clay_cyan",
            "stained_clay_purple", "stained_clay_blue", "stained_clay_brown",
            "stained_clay_green", "stained_clay_red", "stained_clay_black");

    /** The SAND-material set (instrument 2 — snare): FallingBlock's own
     *  material — gravel and soul sand carry it too (the reference's
     *  Block.java registrations). */
    private static final Set<String> SAND_MATERIAL = Set.of(
            "sand", "gravel", "soul_sand");

    /** The GLASS-material set (instrument 3 — hat): glass, the pane,
     *  glowstone, the sea lantern (the reference's registrations at lines
     *  1029/1191 and the sea lantern's own) + the stained glass family's
     *  sixteen members and their panes (Slice 31 — the same Material.GLASS
     *  registrations at lines 1197/1346). */
    private static final Set<String> GLASS_MATERIAL = Set.of(
            "glass", "glass_pane", "glowstone", "sea_lantern",
            "stained_glass_white", "stained_glass_orange", "stained_glass_magenta",
            "stained_glass_light_blue", "stained_glass_yellow", "stained_glass_lime",
            "stained_glass_pink", "stained_glass_gray", "stained_glass_silver",
            "stained_glass_cyan", "stained_glass_purple", "stained_glass_blue",
            "stained_glass_brown", "stained_glass_green", "stained_glass_red",
            "stained_glass_black",
            "stained_glass_pane_white", "stained_glass_pane_orange",
            "stained_glass_pane_magenta", "stained_glass_pane_light_blue",
            "stained_glass_pane_yellow", "stained_glass_pane_lime",
            "stained_glass_pane_pink", "stained_glass_pane_gray",
            "stained_glass_pane_silver", "stained_glass_pane_cyan",
            "stained_glass_pane_purple", "stained_glass_pane_blue",
            "stained_glass_pane_brown", "stained_glass_pane_green",
            "stained_glass_pane_red", "stained_glass_pane_black");

    /** The WOOD-material set (instrument 4 — bass guitar): the planks/log
     *  family, the containers, the fence, the wooden slab, the signs, and
     *  the note block itself (Material.WOOD — the six wooden door
     *  families' every state ride the isWoodDoor test, not this set; the
     *  six fence-gate families' states ride the isFenceGate test). */
    private static final Set<String> WOOD_MATERIAL = Set.of(
            "oak_planks", "oak_log", "crafting_table", "chest", "bookshelf",
            "oak_fence", "noteblock",
            "spruce_planks", "birch_planks", "jungle_planks",
            "acacia_planks", "dark_oak_planks",
            "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
            "oak_slab", "oak_slab_top",
            "sign", "sign_east", "sign_north", "sign_west",
            "wall_sign", "wall_sign_east", "wall_sign_south", "wall_sign_west");

    /** The reference's instrument selection: the block BELOW's material. */
    public static int instrumentOf(String belowValue) {
        if (STONE_MATERIAL.contains(belowValue)) {
            return INSTRUMENT_BASS_DRUM;
        }
        if (SAND_MATERIAL.contains(belowValue)) {
            return INSTRUMENT_SNARE;
        }
        if (GLASS_MATERIAL.contains(belowValue)) {
            return INSTRUMENT_HAT;
        }
        if (WOOD_MATERIAL.contains(belowValue) || isWoodDoor(belowValue)
                || isFenceGate(belowValue) || isWoodTrapdoor(belowValue)
                || isMushroomBlock(belowValue)) {
            return INSTRUMENT_BASS_GUITAR;
        }
        return INSTRUMENT_HARP;
    }

    /** Whether the value is a wooden trapdoor state (Slice 39 — the wood
     *  family's Material.WOOD; the iron trapdoor is IRON and rides the
     *  default harp like the iron door). */
    private static boolean isWoodTrapdoor(String value) {
        return value.equals("trapdoor") || value.startsWith("trapdoor_");
    }

    /** Whether the value is a huge-mushroom state (Slice 40 — both
     *  families' Material.WOOD). */
    private static boolean isMushroomBlock(String value) {
        return value.equals("brown_mushroom_block") || value.startsWith("brown_mushroom_block_")
                || value.equals("red_mushroom_block") || value.startsWith("red_mushroom_block_");
    }

    /** Whether the value is a wooden door state (the six wood families'
     *  every state — DoorBlock's Material.WOOD; the iron door is IRON and
     *  rides the stone set's rock family instead). */
    private static boolean isWoodDoor(String value) {
        if (value.startsWith("iron_door")) {
            return false;
        }
        return value.equals("oak_door") || value.startsWith("oak_door_")
                || value.equals("spruce_door") || value.startsWith("spruce_door_")
                || value.equals("birch_door") || value.startsWith("birch_door_")
                || value.equals("jungle_door") || value.startsWith("jungle_door_")
                || value.equals("acacia_door") || value.startsWith("acacia_door_")
                || value.equals("dark_oak_door") || value.startsWith("dark_oak_door_");
    }

    /** Whether the value is a fence-gate state (the six families'
     *  sixteen states each — FenceGateBlock's Material.WOOD). */
    private static boolean isFenceGate(String value) {
        int gate = value.indexOf("_fence_gate");
        if (gate < 0) {
            return false;
        }
        String family = value.substring(0, gate);
        return family.equals("oak") || family.equals("spruce") || family.equals("birch")
                || family.equals("jungle") || family.equals("dark_oak") || family.equals("acacia");
    }

    // ------------------------------------------------------------- the tile lifecycle

    /** @return whether the type is the note block. */
    public static boolean isNoteBlock(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().equals("noteblock");
    }

    /** The tile at the position (the public read for the store and tests). */
    public NoteTile tileAt(BlockPosition position) {
        return tiles.get(position);
    }

    /** The full snapshot (the persistence save's own). */
    public Map<BlockPosition, NoteTile> snapshotTiles() {
        return Map.copyOf(tiles);
    }

    /** The placement arm (the reference's createBlockEntity at placement). */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (isNoteBlock(newType)) {
            tiles.computeIfAbsent(position, at -> new NoteTile((byte) 0, false));
        }
    }

    /** The break cleanup (a broken note block's tile dies with it; the
     *  re-place starts at note 0 — the reference's fresh BE). */
    public void onBlockBroken(BlockPosition position) {
        tiles.remove(position);
    }

    /** The restore arm (the ZND load at boot): the note byte lands, the
     *  powered flag starts clear. */
    public void restoreAll(Map<BlockPosition, Byte> stored) {
        for (Map.Entry<BlockPosition, Byte> entry : stored.entrySet()) {
            byte note = entry.getValue();
            if (note < 0) {
                note = 0;
            } else if (note > 24) {
                note = 24;
            }
            tiles.put(entry.getKey(), new NoteTile(note, false));
        }
    }

    // ------------------------------------------------------------- the three play arms

    /**
     * The power-edge arm (NoteBlock.neighborChanged lines 26-39): only the
     * RISING edge plays — the falling edge clears the flag silently. The
     * reference's hasNeighborSignal consult. Tick-thread context.
     */
    public void neighborHeard(BlockPosition pos) {
        if (!isNoteBlock(world.getBlock(pos))) {
            return;
        }
        NoteTile tile = tiles.computeIfAbsent(pos, at -> new NoteTile((byte) 0, false));
        boolean signal = redstone.neighborSignal(pos) > 0;
        if (tile.isPowered() != signal) {
            if (signal) {
                playNote(pos, tile);
            }
            tile.setPowered(signal);
        }
    }

    /**
     * The use arm (NoteBlock.use lines 41-56): the tune ({@code (note + 1) %
     * 25}) then the play. Returns false when the position is not a note
     * block (the caller's dispatch falls through).
     */
    public boolean useFromTick(BlockPosition pos) {
        if (!isNoteBlock(world.getBlock(pos))) {
            return false;
        }
        NoteTile tile = tiles.computeIfAbsent(pos, at -> new NoteTile((byte) 0, false));
        tile.tune();
        playNote(pos, tile);
        return true;
    }

    /**
     * The attack arm (NoteBlock.startMining lines 58-67): the survival dig's
     * start plays. The reference's manager never calls it in creative (the
     * instant-break arm has no note) — the engine's hook fires only on the
     * survival start path.
     */
    public void attackFromTick(BlockPosition pos) {
        if (!isNoteBlock(world.getBlock(pos))) {
            return;
        }
        NoteTile tile = tiles.computeIfAbsent(pos, at -> new NoteTile((byte) 0, false));
        playNote(pos, tile);
    }

    // ------------------------------------------------------------- the play itself

    /**
     * The play (NoteBlockBlockEntity.playNote lines 32-54): the air-above
     * gate, the instrument from the block below's material, then the
     * reference's server walk — the doEvent sound (the
     * ServerWorldEventListener's broadcast) plus the block event relayed
     * as the Block Action packet within 64 blocks.
     */
    private void playNote(BlockPosition pos, NoteTile tile) {
        // The air-material gate: only the air type carries it (the
        // reference's Material.AIR consult over the engine's registry).
        if (!world.getBlock(pos.offset(0, 1, 0)).equals(world.airType())) {
            return;
        }
        net.zaminmc.torch.block.BlockType below = world.getBlock(pos.offset(0, -1, 0));
        int instrument = instrumentOf(below.identifier().value());
        int note = tile.note() & 0xFF;
        if (note > 24) {
            note = 24;
        }
        // The reference's doEvent (NoteBlock lines 82-88): the server-side
        // playSound — "note." + instrument, volume 3.0F, pitch 2^((note-12)/12).
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        fx.sound(new Position(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                "note." + instrumentName(instrument), 3.0f, pitch);
        // The block event relay (ServerWorld.doBlockEvents lines 820-842):
        // the Block Action to the observers after the server doEvent.
        for (NoteActionListener listener : noteActionListeners) {
            try {
                listener.onNoteAction(pos, instrument, note);
            } catch (Throwable t) {
                LOGGER.warning("Note action listener failed: " + t);
            }
        }
    }

    /** The reference's instrument names (INSTRUMENTS list, the sound keys). */
    static String instrumentName(int instrument) {
        return switch (instrument) {
            case INSTRUMENT_BASS_DRUM -> "bd";
            case INSTRUMENT_SNARE -> "snare";
            case INSTRUMENT_HAT -> "hat";
            case INSTRUMENT_BASS_GUITAR -> "bassattack";
            default -> "harp";
        };
    }
}
