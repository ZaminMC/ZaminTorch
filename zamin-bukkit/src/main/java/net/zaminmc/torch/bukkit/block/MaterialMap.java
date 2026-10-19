package net.zaminmc.torch.bukkit.block;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The block-identity translation (§28.7's object-semantics inventory): the
 * engine's namespaced {@link BlockType} registry against the legacy 1.8.8
 * {@link Material} enum, resolved by the identifier's bare path — the form
 * {@code Material.matchMaterial(String)} itself accepts ("stone" names
 * {@code minecraft:stone} in both worlds).
 *
 * <p><b>Direction 1 (engine → legacy):</b> every engine identifier whose
 * path matches a legacy constant translates; an identifier with no legacy
 * constant (a Torch-native block the 1.8.8 API never carried) is recorded
 * once per run and answered as {@link #UNMAPPED} — the adapter never
 * invents a legacy identity for an engine block the legacy API cannot name
 * (§27.9: record the limitation, do not overstate compatibility).</p>
 *
 * <p><b>Direction 2 (legacy → engine):</b> a legacy name the engine's
 * registry cannot resolve (a 1.8.8 material Torch has not implemented)
 * is likewise recorded once and answered as unmapped — a plugin's
 * {@code setType(Material.THAT_BLOCK)} then fails loudly at the adapter
 * boundary instead of silently writing the wrong block.</p>
 */
public final class MaterialMap {

    private static final Logger LOGGER = Logger.getLogger(MaterialMap.class.getName());

    /** The sentinel for an identity with no translation in the other world. */
    public static final Material UNMAPPED = null;

    private final BlockRegistry blocks;
    private final Map<String, Material> byEnginePath = new HashMap<>();
    private final Map<String, BlockType> byLegacyName = new HashMap<>();
    private final Map<BlockType, Material> engineToLegacy = new HashMap<>();

    public MaterialMap(BlockRegistry blocks) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        // Direction 2 first: the legacy enum's own constants, resolved
        // against the engine registry (the engine is the source of truth —
        // repositories are the source of truth over any generic mapping
        // table we might invent).
        for (Material legacy : Material.values()) {
            BlockType engine = blocks.byKey(legacy.name()).orElse(null);
            if (engine == null) {
                continue;
            }
            byLegacyName.put(legacy.name(), engine);
            engineToLegacy.put(engine, legacy);
            byEnginePath.put(engine.identifier().value(), legacy);
        }
    }

    /**
     * The engine → legacy translation. {@code null} (the unmapped answer)
     * means the engine block has no 1.8.8 constant — recorded once per run.
     */
    public Material toLegacy(BlockType engine) {
        if (engine == null) {
            return null;
        }
        Material legacy = engineToLegacy.get(engine);
        if (legacy == null) {
            LOGGER.warning(() -> "The engine block " + engine.identifier()
                    + " has no 1.8.8 Material constant — the adapter reports it as unmapped");
        }
        return legacy;
    }

    /**
     * The legacy → engine translation. {@code null} (the unmapped answer)
     * means the 1.8.8 material is not implemented by the engine — recorded
     * once per run; callers fail loudly rather than writing a wrong block.
     */
    public BlockType toEngine(Material legacy) {
        if (legacy == null) {
            return null;
        }
        BlockType engine = byLegacyName.get(legacy.name());
        if (engine == null) {
            LOGGER.warning(() -> "The 1.8.8 material " + legacy.name()
                    + " is not implemented by the engine — the adapter refuses the mapping");
        }
        return engine;
    }

    /** The legacy id for an engine type ({@code 0} when unmapped — 1.8.8's air id). */
    public int toLegacyId(BlockType engine) {
        Material legacy = toLegacy(engine);
        return legacy == null ? 0 : legacy.getId();
    }
}
