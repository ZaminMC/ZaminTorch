package net.zaminmc.torch.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * The storage identity manifest (architecture §31.4/§31.5): a Torch-owned
 * marker beside the world's data that records WHAT format family and layout
 * generation wrote this world — independently of the gameplay/protocol
 * version — so no future boot can mistake one storage format for another.
 *
 * <p>This is the no-implicit-conversion guard's spine: "never read a save
 * as another format because both have a {@code world/} directory". A world
 * whose manifest names an UNKNOWN or NEWER layout is refused loudly (fail
 * safe — this build never rewrites data it does not understand); a manifest
 * naming a different format family (a real Vanilla/Paper tree pointed at
 * Torch, say) is refused outright, because directory names alone are never
 * a compatibility claim.</p>
 *
 * <p>The manifest is the ONLY authority on identity: it is written on first
 * boot, read on every boot, and bumped only by a deliberate, journaled
 * migration (§31.7) — never by ordinary startup.</p>
 */
public final class StorageManifest {

    /** The manifest's file name inside the world's data directory. */
    public static final String FILE_NAME = "storage.properties";

    /** The Torch-owned format family the current engine writes. */
    public static final String FORMAT_FAMILY = "zamin-delta";
    /** The directory-layout generation this build speaks. */
    public static final int STORAGE_LAYOUT_VERSION = 1;

    private final String formatFamily;
    private final int storageLayoutVersion;

    private StorageManifest(String formatFamily, int storageLayoutVersion) {
        this.formatFamily = formatFamily;
        this.storageLayoutVersion = storageLayoutVersion;
    }

    /** The manifest this build writes. */
    public static StorageManifest current() {
        return new StorageManifest(FORMAT_FAMILY, STORAGE_LAYOUT_VERSION);
    }

    public String formatFamily() {
        return formatFamily;
    }

    public int storageLayoutVersion() {
        return storageLayoutVersion;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StorageManifest manifest
                && manifest.formatFamily.equals(formatFamily)
                && manifest.storageLayoutVersion == storageLayoutVersion;
    }

    @Override
    public int hashCode() {
        return formatFamily.hashCode() * 31 + storageLayoutVersion;
    }

    @Override
    public String toString() {
        return formatFamily + "-v" + storageLayoutVersion;
    }

    /**
     * Reads the world's manifest, creating the current one on first boot.
     *
     * @throws IllegalStateException when the world was written by a different
     *         format family or a NEWER layout — the fail-safe refusal (never
     *         a silent conversion, never a rewrite with defaults)
     */
    public static StorageManifest open(Path worldDataDir) {
        Objects.requireNonNull(worldDataDir, "worldDataDir");
        Path file = worldDataDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            write(file, current());
            return current();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("The storage manifest at " + file
                    + " is unreadable; refusing to touch the world rather than guess"
                    + " its format (§31.4)", e);
        }
        String family = properties.getProperty("format-family", "").trim();
        String layoutRaw = properties.getProperty("storage-layout", "").trim();
        if (family.isEmpty() || layoutRaw.isEmpty()) {
            throw new IllegalStateException("The storage manifest at " + file
                    + " names no format family/layout; refusing to touch the world"
                    + " rather than guess its format (§31.4)");
        }
        int layout;
        try {
            layout = Integer.parseInt(layoutRaw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("The storage manifest at " + file
                    + " carries a non-numeric storage-layout '" + layoutRaw
                    + "'; refusing to touch the world (§31.4)", e);
        }
        if (!FORMAT_FAMILY.equals(family)) {
            throw new IllegalStateException("The world at " + worldDataDir
                    + " belongs to the '" + family + "' storage format, not this"
                    + " engine's '" + FORMAT_FAMILY + "'. Point data-dir at a Torch"
                    + " world or use an explicit migration — a directory's shape is"
                    + " never a compatibility claim (§31.4/§31.3).");
        }
        if (layout > STORAGE_LAYOUT_VERSION) {
            throw new IllegalStateException("The world at " + worldDataDir
                    + " was written by storage layout v" + layout + ", NEWER than this"
                    + " build's v" + STORAGE_LAYOUT_VERSION + ". Refusing to write it"
                    + " — upgrade the server rather than letting an older build touch"
                    + " newer data (§31.4: unknown newer versions fail safe).");
        }
        // Same family, same-or-older layout: this build understands it fully
        // (older layouts stay loadable; the binary stores carry their own
        // per-store version gates).
        return new StorageManifest(family, layout);
    }

    private static void write(Path file, StorageManifest manifest) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Properties properties = new Properties();
            properties.setProperty("format-family", manifest.formatFamily);
            properties.setProperty("storage-layout",
                    Integer.toString(manifest.storageLayoutVersion));
            properties.setProperty("game-version", "1.8.8");
            properties.setProperty("note",
                    "Torch-owned storage identity. Directory names are not a Vanilla"
                            + " compatibility claim; format changes ride explicit"
                            + " migrations only (architecture 31).");
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "ZaminTorch storage manifest");
            }
        } catch (IOException e) {
            throw new IllegalStateException("The storage manifest at " + file
                    + " could not be written (§31.4)", e);
        }
    }
}
