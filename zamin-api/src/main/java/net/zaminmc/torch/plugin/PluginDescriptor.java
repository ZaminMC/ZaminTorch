package net.zaminmc.torch.plugin;

import java.util.List;

/**
 * One plugin's identity (architecture §28.5's discovery output).
 *
 * <p>Descriptors are immutable values; the id is the host's key (case
 * is preserved, matching is exact). The filesystem discovery and
 * classloading wave is deliberately later (§27.1's order — the native
 * API and engine mature first); the host's programmatic registration
 * carries the same descriptor shape that wave will produce.</p>
 *
 * @param id      the unique plugin id ("economy")
 * @param version the plugin's own version string ("1.2.3")
 * @param authors the author names (display only)
 */
public record PluginDescriptor(String id, String version, List<String> authors) {

    public PluginDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("plugin id must not be blank");
        }
        if (version == null) {
            throw new IllegalArgumentException("plugin version must not be null");
        }
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
