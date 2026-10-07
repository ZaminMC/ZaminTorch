package net.zamin.api;

/**
 * The identity and behavior description of a block, independent of any position.
 *
 * <p>A {@code BlockType} is "what stone is"; the block state at a position is
 * "the stone currently occupying that position". Block properties/state variants
 * will extend this model in a later slice without changing identity semantics.</p>
 */
public interface BlockType {

    /** The canonical identifier, for example {@code minecraft:stone}. */
    Identifier identifier();

    /** Human-readable display name for diagnostics and future client use. */
    default String displayName() {
        return identifier().value();
    }
}
