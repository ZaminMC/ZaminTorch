package net.zaminmc.torch.bukkit.plugin;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.plugin.PluginBody;
import net.zaminmc.torch.plugin.PluginDescriptor;
import net.zaminmc.torch.plugin.PluginHandle;
import net.zaminmc.torch.plugin.PluginHost;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The lifecycle bridge (§28.7's plugin-discovery/lifecycle inventory): the
 * legacy load wave rides the API artifact's OWN loader machinery (§27.2:
 * API only — {@link JavaPluginLoader} constructs the package-private
 * {@code PluginClassLoader}, instantiates the entry, and drives the init
 * chain; the adapter never reimplements classloading), and every bridged
 * plugin registers through the ENGINE's native §28.5 host as one native
 * plugin — bridging it, never duplicating it:
 *
 * <pre>
 * jar (plugins/, plugin.yml)          the legacy discovery's own form
 *   → loader.loadPlugin(file)         the API artifact's machinery
 *   → plugin.onLoad()                 the bridge's own call (the legacy load wave)
 *   → engine host registerWithLoader  the native §28.5 machine owns it from here:
 *       body.onEnable  → loader.enablePlugin  (the legacy enable wave)
 *       body.onDisable → loader.disablePlugin (the legacy disable wave)
 *       scope sweep    → listeners unregistered, tasks cancelled, late
 *                        results invalidated (§28.5's law, unchanged)
 *       unload verb    → the plugin's classloader closed (best-effort, §28.5)
 * </pre>
 *
 * <p><b>Per-jar isolation (§28.5's failure transitions):</b> a broken
 * descriptor, a missing main class, or a throwing constructor skips THAT
 * jar with a loud log — the boot (and the rest of the wave) continues.</p>
 *
 * <p><b>The 1.8.8 enable-failure flaw, preserved as documented</b> (§28.7:
 * if the legacy semantics cannot be improved without inventing them, record
 * them): the API's own {@code setEnabled(true)} flips the enabled flag
 * BEFORE the callback, so a throwing {@code onEnable} is caught and logged
 * by the loader and the legacy flag stays enabled — the adapter does not
 * invent a different failure state; the native scope still owns the
 * cleanup whenever the plugin is disabled.</p>
 */
public final class BukkitPluginBridge {

    private static final Logger LOGGER = Logger.getLogger(BukkitPluginBridge.class.getName());

    private final BukkitAdapter adapter;
    /** One bridged plugin's record: the legacy plugin + its native handle. */
    private final Map<Plugin, Record> byPlugin = new ConcurrentHashMap<>();
    private final Map<String, Record> byName = new ConcurrentHashMap<>();

    /**
     * The walk-scoped handle (§28.5's own law): the native host passes the
     * handle TO the enable/disable callback, and the plugin's own lifecycle
     * code runs INSIDE that callback — scheduling tasks and registering
     * listeners before the registration call ever returns. The bridge
     * publishes the handle for the walk's duration so the scheduler and
     * the manager can own those resources in the CURRENT scope; the walks
     * run single-threaded under the engine host's lifecycle lock.
     */
    private volatile PluginHandle walkHandle;

    private record Record(Plugin plugin, PluginHandle handle) {
    }

    public BukkitPluginBridge(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    // ------------------------------------------------------------------ the load wave

    /**
     * One jar's whole load walk: the loader's machinery (description,
     * data folder, the package-private classloader, instantiation, init),
     * the bridge's own {@code onLoad} call, and the registration through
     * the engine's native host — which immediately drives the enable walk.
     */
    public Plugin loadOne(Path jar, JavaPluginLoader loader) throws
            org.bukkit.plugin.InvalidPluginException,
            org.bukkit.plugin.InvalidDescriptionException,
            org.bukkit.plugin.UnknownDependencyException {
        Plugin plugin = loader.loadPlugin(jar.toFile());
        plugin.onLoad(); // the bridge's own call — the legacy load wave
        return plugin;
    }

    /**
     * The discovery wave over the plugins directory: every {@code *.jar}
     * with a {@code plugin.yml}, per-jar isolated (a broken jar is skipped
     * with a loud log, never thrown — the wave continues), deterministic
     * order (the directory's own name order, the native discovery's law).
     */
    public List<Plugin> loadAll(Path pluginsDir, JavaPluginLoader loader) {
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return List.of(); // a fresh server runs pluginless (the native law)
        }
        List<Path> jars;
        try (Stream<Path> entries = Files.list(pluginsDir)) {
            jars = entries.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT)
                                    .endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException scanFailure) {
            LOGGER.log(Level.SEVERE, "Plugin directory scan failed for " + pluginsDir,
                    scanFailure);
            return List.of();
        }
        List<Plugin> loaded = new ArrayList<>();
        for (Path jar : jars) {
            try {
                Plugin plugin = loadOne(jar, loader);
                register(plugin, jar);
                loaded.add(plugin);
            } catch (Exception | LinkageError broken) {
                LOGGER.log(Level.SEVERE, "Bukkit plugin jar skipped (the wave continues): "
                        + jar.getFileName(), broken);
            }
        }
        return List.copyOf(loaded);
    }

    /** The legacy overload the PluginManager surface carries. */
    public List<Plugin> loadAll(java.io.File directory, JavaPluginLoader loader) {
        return loadAll(directory.toPath(), loader);
    }

    // ------------------------------------------------------------------ the native registration

    /**
     * Registers one loaded plugin through the engine's native §28.5 host:
     * the native lifecycle machine owns it from here — the enable walk
     * (the legacy onEnable wave), the disable walk (the legacy onDisable
     * wave + the scope sweep), and the unload verb (the classloader close).
     */
    private void register(Plugin plugin, Path jar) {
        if (byPlugin.containsKey(plugin)) {
            throw new IllegalStateException("plugin already bridged: " + plugin.getName());
        }
        PluginHost host = adapter.engine().plugins();
        PluginDescriptor descriptor = new PluginDescriptor(
                plugin.getName(),
                plugin.getDescription().getVersion(),
                plugin.getDescription().getAuthors());
        PluginBody body = new PluginBody() {
            @Override
            public void onEnable(PluginHandle handle) {
                walkHandle = handle; // the walk-scoped publication (see the field's law)
                try {
                    loaderFor(plugin).enablePlugin(plugin);
                } finally {
                    walkHandle = null;
                }
            }

            @Override
            public void onDisable(PluginHandle handle) {
                walkHandle = handle;
                try {
                    loaderFor(plugin).disablePlugin(plugin);
                } finally {
                    walkHandle = null;
                }
            }
        };
        Runnable closeLoader = () -> {
            try {
                ((URLClassLoader) plugin.getClass().getClassLoader()).close();
            } catch (IOException closeFailure) {
                LOGGER.log(Level.WARNING, "Plugin classloader close failed for "
                        + plugin.getName(), closeFailure);
            }
        };
        PluginHandle handle;
        if (host instanceof net.zaminmc.torch.server.plugin.PluginManager engineManager) {
            // The engine-side registration: the unload verb closes the
            // plugin's own classloader at UNLOAD (the last lifecycle state,
            // after the disable sweep — §28.5's classloading law).
            handle = engineManager.registerWithLoader(descriptor, body, closeLoader);
        } else {
            // A foreign host: register without the engine-side unload verb
            // (the loader stays open for the process's life — documented,
            // the same law the native discovery carries).
            handle = host.register(descriptor, body);
        }
        Record record = new Record(plugin, handle);
        byPlugin.put(plugin, record);
        byName.put(plugin.getName(), record);
        LOGGER.info(() -> "Bridged " + plugin.getDescription().getFullName()
                + " onto the engine's plugin host (" + jar.getFileName() + ")");
    }

    private PluginLoader loaderFor(Plugin plugin) {
        return plugin.getPluginLoader();
    }

    // ------------------------------------------------------------------ the lifecycle verbs (the legacy surface)

    /** The legacy enable verb: the native host's enable walk drives it. */
    public void enable(Plugin plugin) {
        Record record = byPlugin.get(plugin);
        if (record == null) {
            throw new IllegalStateException("not a bridged plugin: " + plugin.getName());
        }
        if (plugin.isEnabled()) {
            return; // the documented no-op
        }
        adapter.engine().plugins().reload(record.handle()); // reload's own law:
        // "a plugin not currently enabled is simply enabled" — the native
        // enable walk with a FRESH scope (§28.5's reload semantics).
    }

    /** The legacy disable verb: the native host's disable walk (§28.5). */
    public void disable(Plugin plugin) {
        Record record = byPlugin.get(plugin);
        if (record == null) {
            throw new IllegalStateException("not a bridged plugin: " + plugin.getName());
        }
        if (!plugin.isEnabled()) {
            return; // the documented no-op
        }
        adapter.engine().plugins().disable(record.handle());
    }

    /** The unload verb: the native host's UNLOAD (the classloader closes). */
    public void unload(Plugin plugin) {
        Record record = byPlugin.remove(plugin);
        if (record == null) {
            return;
        }
        byName.remove(plugin.getName());
        adapter.engine().plugins().unload(record.handle());
    }

    // ------------------------------------------------------------------ queries

    public Collection<Plugin> loadedPlugins() {
        return List.copyOf(byPlugin.keySet());
    }

    public PluginHandle nativeHandleOf(Plugin plugin) {
        Record record = byPlugin.get(plugin);
        if (record != null && record.handle() != null) {
            return record.handle();
        }
        return walkHandle; // the walk-scoped answer (the field's own law)
    }

    public Plugin pluginByName(String name) {
        Record record = byName.get(name);
        return record == null ? null : record.plugin();
    }
}
