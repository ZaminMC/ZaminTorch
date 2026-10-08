package net.zaminmc.torch.server.world.light;

/**
 * Light storage for one 16x16x16 section: two nibble arrays, block light and
 * skylight (§475 "lighting is world state").
 *
 * <p>Encoding is the historical one the wire itself uses (protocol 47 chunk
 * payload): one byte holds two cells, the <b>low</b> nibble for the even
 * index, the high nibble for the odd index, indices packed
 * {@code (y << 8) | (z << 4) | x} — the same order the block arrays use. The
 * serializer can therefore copy the arrays verbatim.</p>
 *
 * <p>Absent (null) sections carry the implicit defaults — block light 0,
 * skylight 15 everywhere (open sky over air) — which is also exactly what the
 * 1.8 client assumes for sections omitted from a chunk packet's bitmask (the
 * client's null-storage default). This slice only materializes a section when
 * a value diverges from those defaults, so the flat surface world stores
 * almost nothing.</p>
 *
 * <p>Threading: reads and writes happen on the world's owning context (the
 * tick thread), like every other world mutation. The chunk serializer reads
 * published chunks from network threads — same relaxed contract the block
 * arrays already rely on (values are primitive arrays written once before
 * publication or mutated under the tick thread's single-writer discipline).</p>
 */
public final class LightSection {

    public static final int SIZE = 16;
    public static final int CELLS = SIZE * SIZE * SIZE;
    public static final int ARRAY_BYTES = CELLS / 2;

    /** Implicit defaults of every cell in an absent section. */
    public static final int DEFAULT_SKY = 15;
    public static final int DEFAULT_BLOCK = 0;

    private final byte[] block = new byte[ARRAY_BYTES];
    private final byte[] sky = filledSky();

    private static byte[] filledSky() {
        byte[] sky = new byte[ARRAY_BYTES];
        // A materialized section starts at the implicit defaults cell-by-cell
        // (block 0 / sky 15) — the same defaults an absent section carries and
        // the 1.8 client assumes. Without the pre-fill, the first block-light
        // write would materialize a section whose whole sky channel read 0.
        java.util.Arrays.fill(sky, (byte) 0xFF);
        return sky;
    }

    public int blockLight(int localX, int localY, int localZ) {
        return nibble(block, index(localX, localY, localZ));
    }

    public int skyLight(int localX, int localY, int localZ) {
        return nibble(sky, index(localX, localY, localZ));
    }

    /** @return the raw block-light nibble array (wire-ready: identical packing). */
    public byte[] blockLightArray() {
        return block;
    }

    /** @return the raw skylight nibble array (wire-ready: identical packing). */
    public byte[] skyLightArray() {
        return sky;
    }

    public void setBlockLight(int localX, int localY, int localZ, int level) {
        setNibble(block, index(localX, localY, localZ), level);
    }

    public void setSkyLight(int localX, int localY, int localZ, int level) {
        setNibble(sky, index(localX, localY, localZ), level);
    }

    /** @return whether any cell of this section diverges from the implicit defaults. */
    public boolean hasNonDefaultLight() {
        for (int i = 0; i < ARRAY_BYTES; i++) {
            // Bytes sign-extend on shift: mask each nibble explicitly.
            if ((block[i] & 0x0F) != DEFAULT_BLOCK || ((block[i] >> 4) & 0x0F) != DEFAULT_BLOCK) {
                return true;
            }
            if ((sky[i] & 0x0F) != DEFAULT_SKY || ((sky[i] >> 4) & 0x0F) != DEFAULT_SKY) {
                return true;
            }
        }
        return false;
    }

    private static int index(int localX, int localY, int localZ) {
        if ((localX | localY | localZ) < 0 || localX >= SIZE || localY >= SIZE || localZ >= SIZE) {
            throw new IllegalArgumentException(
                    "Local coordinates out of section bounds: " + localX + "," + localY + "," + localZ);
        }
        return (localY << 8) | (localZ << 4) | localX;
    }

    private static int nibble(byte[] array, int index) {
        byte packed = array[index >> 1];
        return (index & 1) == 0 ? packed & 0x0F : (packed >> 4) & 0x0F;
    }

    private static void setNibble(byte[] array, int index, int level) {
        if (level < 0 || level > 15) {
            throw new IllegalArgumentException("Light level must be 0..15: " + level);
        }
        int byteIndex = index >> 1;
        if ((index & 1) == 0) {
            array[byteIndex] = (byte) ((array[byteIndex] & 0xF0) | level);
        } else {
            array[byteIndex] = (byte) ((array[byteIndex] & 0x0F) | (level << 4));
        }
    }
}
