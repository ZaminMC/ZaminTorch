package net.zaminmc.torch.server.decoration;

/**
 * One painting body (Slice 28b — the reference port of
 * entity/decoration/PaintingEntity): the wall cell it hangs in, the
 * horizontal facing, and the motive — one of the reference's own
 * twenty-six art entries.
 *
 * <p>The painting's own placement walk (the constructor lines 21-38)
 * collects every motive that fits the wall and rolls one at random;
 * the system owns that walk because it needs the world reads.</p>
 */
public final class Painting {

    private final int entityId;
    private final net.zaminmc.torch.block.BlockPosition pos;
    /** The facing the painting points away from its wall (0-3, byIdHorizontal). */
    private final int facing;
    /** The art entry (never null — the read walk's own KEBAB fallback). */
    private final Motive motive;

    Painting(int entityId, net.zaminmc.torch.block.BlockPosition pos, int facing, Motive motive) {
        this.entityId = entityId;
        this.pos = pos;
        this.facing = facing & 3;
        this.motive = motive == null ? Motive.KEBAB : motive;
    }

    /** The painting's engine entity id (the Spawn Painting wire id). */
    public int entityId() {
        return entityId;
    }

    /** The wall cell the painting hangs in (the reference's own {@code pos}). */
    public net.zaminmc.torch.block.BlockPosition pos() {
        return pos;
    }

    /** The facing code the painting points away from its wall (0-3). */
    public int facing() {
        return facing;
    }

    /** The art entry. */
    public Motive motive() {
        return motive;
    }

    /**
     * The reference's own art registry (PaintingEntity.Motive lines
     * 112-138 — the exact twenty-six, their pixel sizes, and their atlas
     * offsets, in the reference's declaration order).
     */
    public enum Motive {
        KEBAB("Kebab", 16, 16, 0, 0),
        AZTEC("Aztec", 16, 16, 16, 0),
        ALBAN("Alban", 16, 16, 32, 0),
        AZTEC2("Aztec2", 16, 16, 48, 0),
        BOMB("Bomb", 16, 16, 64, 0),
        PLANT("Plant", 16, 16, 80, 0),
        WASTELAND("Wasteland", 16, 16, 96, 0),
        POOL("Pool", 32, 16, 0, 32),
        COURBET("Courbet", 32, 16, 32, 32),
        SEA("Sea", 32, 16, 64, 32),
        SUNSET("Sunset", 32, 16, 96, 32),
        CREEBET("Creebet", 32, 16, 128, 32),
        WANDERER("Wanderer", 16, 32, 0, 64),
        GRAHAM("Graham", 16, 32, 16, 64),
        MATCH("Match", 32, 32, 0, 128),
        BUST("Bust", 32, 32, 32, 128),
        STAGE("Stage", 32, 32, 64, 128),
        VOID("Void", 32, 32, 96, 128),
        SKULL_AND_ROSES("SkullAndRoses", 32, 32, 128, 128),
        WTIHER("Wither", 32, 32, 160, 128),
        FIGHTERS("Fighters", 64, 32, 0, 96),
        POINTER("Pointer", 64, 64, 0, 192),
        PIGSCENE("Pigscene", 64, 64, 64, 192),
        BURNING_SKULL("BurningSkull", 64, 64, 128, 192),
        SKELETON("Skeleton", 64, 48, 192, 64),
        DONKEY_KONG("DonkeyKong", 64, 48, 192, 112);

        /** The reference's own name-length constant (the wire read's cap). */
        public static final int SKULL_AND_ROSES_LENGTH = "SkullAndRoses".length();

        /** The motive's registry name (the wire's Spawn Painting title). */
        public final String name;
        /** The art's pixel width (a 16-unit block per 16 pixels). */
        public final int width;
        /** The art's pixel height. */
        public final int height;
        /** The atlas u offset (the renderer's own concern, carried for parity). */
        public final int u;
        /** The atlas v offset. */
        public final int v;

        Motive(String name, int width, int height, int u, int v) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.u = u;
            this.v = v;
        }

        /** The registry-name lookup (readCustomNbt's own walk, null when absent). */
        public static Motive byName(String name) {
            for (Motive motive : values()) {
                if (motive.name.equals(name)) {
                    return motive;
                }
            }
            return null;
        }
    }
}
