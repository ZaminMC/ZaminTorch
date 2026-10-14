package net.zaminmc.torch.server.jukebox;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;

/**
 * The jukebox family's block helpers (the reference's Block.java line-1181
 * registration, {@code register(84, "jukebox", ...)}): the plain jukebox and
 * its HAS_RECORD variant share legacy id 84 — the metadata nibble's bit 0 is
 * the reference's own {@code JukeboxBlock.HAS_RECORD} property
 * ({@code getMetadataFromState}: has_record ? 1 : 0).
 */
public final class JukeboxBlocks {

    private JukeboxBlocks() {
    }

    /** @return whether the type is either jukebox variant. */
    public static boolean isFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("jukebox")
                        || type.identifier().value().equals("jukebox_record"));
    }

    /** @return whether the type is the HAS_RECORD variant (metadata 1). */
    public static boolean isRecordVariant(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().equals("jukebox_record");
    }

    /** The HAS_RECORD variant of the plain jukebox (and vice versa). */
    public static BlockType variantOf(boolean hasRecord) {
        return hasRecord ? BuiltinBlocks.JUKEBOX_RECORD : BuiltinBlocks.JUKEBOX;
    }

    /** @return whether the identifier is one of the twelve discs. */
    public static boolean isMusicDisc(Identifier identifier) {
        if (!identifier.namespace().equals("minecraft")) {
            return false;
        }
        return switch (identifier.value()) {
            case "record_13", "record_cat", "record_blocks", "record_chirp",
                    "record_far", "record_mall", "record_mellohi", "record_stal",
                    "record_strad", "record_ward", "record_11", "record_wait" -> true;
            default -> false;
        };
    }
}
