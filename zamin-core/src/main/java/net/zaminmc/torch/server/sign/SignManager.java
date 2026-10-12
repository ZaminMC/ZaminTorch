package net.zaminmc.torch.server.sign;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns every placed sign's text of one world, keyed by block position —
 * the same block-entity shape as the chest manager (simulation-thread
 * confined, tick sweep discards orphaned state).
 *
 * <p>The four editable lines arrive through Update Sign (0x12) right after
 * the historical placement editor closes; the wire replay (Update Sign 0x33)
 * re-sends them on chunk load so signs survive relogs. Text is plain — the
 * historical 1.8 signs carry no formatting here.</p>
 */
public final class SignManager {

    /** The historical editable line count of a sign. */
    public static final int LINES = 4;
    /** A generous cap; the 1.8 client UI enforces its own shorter limits. */
    public static final int MAX_LINE_LENGTH = 64;

    private final Map<BlockPosition, String[]> signs = new LinkedHashMap<>();

    /** Stores (replaces) the text of the sign at the position. */
    public void set(BlockPosition position, String[] lines) {
        Objects.requireNonNull(position, "position");
        signs.put(position, normalized(lines));
    }

    /** @return the live text at the position, or null when the sign has none. */
    public String[] peek(BlockPosition position) {
        return signs.get(position);
    }

    /** @return a defensive copy of the whole map (consistent save snapshot). */
    public Map<BlockPosition, String[]> snapshot() {
        return new LinkedHashMap<>(signs);
    }

    /** Restores persisted state after boot (world deltas already applied). */
    public void restoreAll(Map<BlockPosition, String[]> restored) {
        signs.putAll(restored);
    }

    /** @return every sign text within one chunk column (the chunk-send replay). */
    public List<Map.Entry<BlockPosition, String[]>> inChunk(ChunkPosition chunk) {
        List<Map.Entry<BlockPosition, String[]>> found = new ArrayList<>();
        for (Map.Entry<BlockPosition, String[]> entry : signs.entrySet()) {
            if (entry.getKey().chunkPosition().equals(chunk)) {
                found.add(entry);
            }
        }
        return found;
    }

    /**
     * Discards state whose block is no longer a sign (the break path removes
     * the block; text is not spilled). Runs once per tick on the world owner;
     * a sign has no advancing state of its own.
     */
    public void tick(EngineWorld world) {
        signs.entrySet().removeIf(entry -> !isSignBlock(world, entry.getKey()));
    }

    private static boolean isSignBlock(EngineWorld world, BlockPosition position) {
        return isSignType(world.getBlock(position));
    }

    /** Whether the block type is one of the standing sign states. */
    public static boolean isSignType(BlockType type) {
        return type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("sign")
                    || type.identifier().value().startsWith("sign_")
                    || type.identifier().value().equals("wall_sign")
                    || type.identifier().value().startsWith("wall_sign_"));
    }

    /** Trims and pads the lines to the historical four-line shape. */
    private static String[] normalized(String[] lines) {
        String[] result = new String[LINES];
        for (int i = 0; i < LINES; i++) {
            if (lines != null && i < lines.length && lines[i] != null) {
                result[i] = lines[i].substring(0, Math.min(MAX_LINE_LENGTH, lines[i].length()));
            } else {
                result[i] = "";
            }
        }
        return result;
    }
}
