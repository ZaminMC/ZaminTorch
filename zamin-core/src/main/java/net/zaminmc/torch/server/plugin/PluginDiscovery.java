package net.zaminmc.torch.server.plugin;

import net.zaminmc.torch.plugin.PluginBody;
import net.zaminmc.torch.plugin.PluginDescriptor;
import net.zaminmc.torch.plugin.PluginHost;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The filesystem discovery and classloading wave (§28.5's own
 * {@code DISCOVERED → LOADED} stages over the host's registration):
 * every {@code *.jar} in the plugins directory carries a
 * {@code torch-plugin.properties} descriptor at its root —
 *
 * <pre>{@code
 * id=demo
 * version=1.0.0
 * authors=Alice, Bob
 * entry=demo.DemoPlugin
 * }</pre>
 *
 * <p>One jar's walk: the jar is FOUND ({@code DISCOVERED} — the file on
 * disk), the descriptor is read and validated, the classes load through
 * the jar's OWN {@link URLClassLoader}, and the entry class (a public
 * no-arg {@link PluginBody}) is instantiated ({@code LOADED}) — the
 * host's {@link PluginHost#register} then drives the enable walk. The
 * discovery is per-jar isolated: a broken descriptor, a duplicate id, a
 * missing entry class, or a throwing constructor skips THAT jar with a
 * loud log and never kills the boot (§28.5's failure transitions).</p>
 *
 * <p><b>The classloader's own law (§28.5):</b> the loader closes at
 * UNLOAD — the last lifecycle state, after the disable sweep — through
 * the engine-side unload verb the discovery attaches. Classloader
 * unloading stays best-effort: references a plugin leaked to threads,
 * static registries, or callbacks outlive the close, and that is the
 * documented law, not a claim of isolation.</p>
 */
public final class PluginDiscovery {

    private static final Logger LOGGER = Logger.getLogger(PluginDiscovery.class.getName());

    /** The descriptor entry every discovered jar carries at its root. */
    public static final String DESCRIPTOR_ENTRY = "torch-plugin.properties";

    private PluginDiscovery() {
    }

    /**
     * One discovered jar's loaded form: the descriptor (the
     * {@code DISCOVERED} stage's output), the instantiated body (the
     * {@code LOADED} stage's output), and the jar's own loader (closed at
     * unload).
     */
    public record Candidate(PluginDescriptor descriptor, PluginBody body,
                            URLClassLoader loader, Path jar) {
    }

    /**
     * Scans the plugins directory (an absent directory answers empty —
     * a fresh server runs pluginless) and loads every jar, per-jar
     * isolated: broken jars are skipped with a loud log, never thrown.
     */
    public static List<Candidate> scan(Path pluginsDir) {
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return List.of();
        }
        List<Path> jars;
        try (Stream<Path> entries = Files.list(pluginsDir)) {
            jars = entries.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT)
                                    .endsWith(".jar"))
                    .sorted() // a deterministic boot order (the directory's own name order)
                    .toList();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Plugin directory scan failed for " + pluginsDir, e);
            return List.of();
        }
        List<Candidate> loaded = new ArrayList<>();
        for (Path jar : jars) {
            try {
                loaded.add(loadJar(jar));
            } catch (Exception broken) {
                LOGGER.log(Level.SEVERE, "Plugin jar skipped (the boot continues): "
                        + jar.getFileName(), broken);
            }
        }
        return loaded;
    }

    /**
     * One jar's whole walk: the descriptor read (the {@code DISCOVERED}
     * stage's own parse), the classload, the entry instantiation (the
     * {@code LOADED} stage).
     */
    public static Candidate loadJar(Path jar) throws IOException {
        DescriptorRows rows = readDescriptor(jar);
        URLClassLoader loader = URLClassLoader.newInstance(
                new java.net.URL[]{jar.toUri().toURL()},
                PluginDiscovery.class.getClassLoader());
        PluginBody body;
        try {
            Class<?> entry = Class.forName(rows.entry(), true, loader);
            if (!PluginBody.class.isAssignableFrom(entry)) {
                throw new IllegalStateException("the entry class " + rows.entry()
                        + " does not implement " + PluginBody.class.getName());
            }
            body = (PluginBody) entry.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            try {
                loader.close(); // the failed walk never leaks its loader
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw new IOException("the entry class " + rows.entry()
                    + " could not be instantiated", e);
        }
        return new Candidate(new PluginDescriptor(rows.id(), rows.version(), rows.authors()),
                body, loader, jar);
    }

    /** The descriptor's own rows (the jar's torch-plugin.properties). */
    private record DescriptorRows(String id, String version, List<String> authors, String entry) {
    }

    private static DescriptorRows readDescriptor(Path jar) throws IOException {
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar.toFile())) {
            java.util.jar.JarEntry entry = file.getJarEntry(DESCRIPTOR_ENTRY);
            if (entry == null) {
                throw new IOException("no " + DESCRIPTOR_ENTRY + " at the jar root");
            }
            Properties rows = new Properties();
            try (InputStream in = file.getInputStream(entry)) {
                rows.load(in);
            }
            String id = required(rows, "id", jar);
            String version = required(rows, "version", jar);
            String entryClass = required(rows, "entry", jar);
            List<String> authors = new ArrayList<>();
            String rawAuthors = rows.getProperty("authors");
            if (rawAuthors != null && !rawAuthors.isBlank()) {
                Arrays.stream(rawAuthors.split(","))
                        .map(String::trim)
                        .filter(author -> !author.isEmpty())
                        .forEach(authors::add);
            }
            return new DescriptorRows(id, version, List.copyOf(authors), entryClass);
        }
    }

    private static String required(Properties rows, String key, Path jar) throws IOException {
        String value = rows.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("the descriptor's '" + key + "' row is missing in "
                    + jar.getFileName());
        }
        return value.trim();
    }

    /**
     * The boot's whole wave (§28.5's discovery arm): scan the directory,
     * register every candidate through the host (the enable walk rides
     * the host's own registration), skip the broken and the duplicate
     * per-jar, and attach each loader's close to its plugin's UNLOAD.
     *
     * @return how many plugins registered.
     */
    public static int discoverAndRegister(PluginHost host, Path pluginsDir) {
        if (!(host instanceof PluginManager manager)) {
            // A foreign host: register without the engine-side unload verb
            // (the loader stays open for the process's life — documented).
            int registered = 0;
            for (Candidate candidate : scan(pluginsDir)) {
                try {
                    host.register(candidate.descriptor(), candidate.body());
                    registered++;
                } catch (RuntimeException duplicateOrBroken) {
                    LOGGER.log(Level.SEVERE, "Plugin skipped: "
                            + candidate.descriptor().id(), duplicateOrBroken);
                    closeQuietly(candidate);
                }
            }
            return registered;
        }
        int registered = 0;
        for (Candidate candidate : scan(pluginsDir)) {
            try {
                URLClassLoader loader = candidate.loader();
                manager.registerWithLoader(candidate.descriptor(), candidate.body(),
                        () -> {
                            try {
                                loader.close();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING,
                                        "Plugin classloader close failed", e);
                            }
                        });
                registered++;
            } catch (RuntimeException duplicateOrBroken) {
                LOGGER.log(Level.SEVERE, "Plugin skipped: "
                        + candidate.descriptor().id(), duplicateOrBroken);
                closeQuietly(candidate);
            }
        }
        return registered;
    }

    private static void closeQuietly(Candidate candidate) {
        try {
            candidate.loader().close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed closing a skipped plugin's loader", e);
        }
    }
}
