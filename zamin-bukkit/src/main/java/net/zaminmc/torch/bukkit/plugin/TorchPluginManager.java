package net.zaminmc.torch.bukkit.plugin;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.plugin.PluginHandle;
import net.zaminmc.torch.plugin.ResourceScope;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The legacy plugin manager over the engine's own §28.5 host and §27.7 bus
 * — the lifecycle bridge's dispatch arm (§28.7's inventory: event
 * registration, priorities, cancellation, ordering, exception behavior).
 *
 * <p><b>The load walk rides the API artifact's own machinery</b> (§27.2:
 * API only, never a server implementation): {@link JavaPluginLoader} (a
 * public class OF the 1.8.8 API artifact) constructs the package-private
 * {@code PluginClassLoader}, instantiates the entry, and drives the
 * init chain — the adapter never reimplements classloading. The
 * enable/disable arms delegate to the same loader (its own
 * {@code setEnabled} route), the event registration scan is the loader's
 * own {@code createRegisteredListeners}, and THIS class adds the §28.5
 * ownership the legacy API never had:</p>
 *
 * <ul>
 *   <li><b>Scope ownership (§28.5):</b> every listener registration and
 *       every scheduled task is admitted into the owning plugin's CURRENT
 *       engine resource scope — the disable sweep unregisters the
 *       listeners and cancels the tasks automatically.</li>
 *   <li><b>Exception isolation (§28.8 case 8):</b> a listener throwing is
 *       caught, attributed to its plugin, logged ("Could not pass event"
 *       — the 1.8.8 wording), and the dispatch continues to the remaining
 *       listeners; the engine's tick never dies.</li>
 *   <li><b>Reentrancy (§28.8 case 7):</b> dispatch runs on the engine's
 *       owner context with NO dispatch lock — the owner context is the
 *       serializer (§27.7's own law); nested dispatches unwind like method
 *       calls and can never deadlock.</li>
 * </ul>
 */
public final class TorchPluginManager implements PluginManager {

    private final BukkitAdapter adapter;
    private final JavaPluginLoader loader;
    /** The event-type → HandlerList cache (the 1.8.8 getHandlerList walk). */
    private final Map<Class<? extends Event>, HandlerList> handlerLists =
            new ConcurrentHashMap<>();
    /** The permission registry (empty by default — the operator gate rules). */
    private final Map<String, Permission> permissions = new HashMap<>();
    private final Map<Boolean, Set<Permissible>> defaultPermSubscriptions
            = new ConcurrentHashMap<>();
    private final Map<String, Set<Permissible>> permSubscriptions
            = new ConcurrentHashMap<>();

    public TorchPluginManager(BukkitAdapter adapter) {
        this.adapter = adapter;
        this.loader = new JavaPluginLoader(adapter.bukkitServer());
    }

    // ------------------------------------------------------------------ queries

    @Override
    public Plugin[] getPlugins() {
        Collection<Plugin> loaded = adapter.pluginBridge().loadedPlugins();
        return loaded.toArray(new Plugin[0]);
    }

    @Override
    public Plugin getPlugin(String name) {
        for (Plugin plugin : adapter.pluginBridge().loadedPlugins()) {
            if (plugin.getName().equalsIgnoreCase(name)) {
                return plugin;
            }
        }
        return null;
    }

    @Override
    public boolean isPluginEnabled(String name) {
        Plugin plugin = getPlugin(name);
        return plugin != null && isPluginEnabled(plugin);
    }

    @Override
    public boolean isPluginEnabled(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    // ------------------------------------------------------------------ loading (the legacy surface over the bridge)

    @Override
    public void registerInterface(Class<? extends PluginLoader> loader)
            throws IllegalArgumentException {
        // One loader family (the API's own JavaPluginLoader) is the slice's
        // whole answer — the loud refusal beats pretending a second one.
        throw adapter.unbridged("PluginManager.registerInterface");
    }

    @Override
    public Plugin loadPlugin(File file) throws org.bukkit.plugin.InvalidPluginException,
            org.bukkit.plugin.InvalidDescriptionException,
            org.bukkit.plugin.UnknownDependencyException {
        return adapter.pluginBridge().loadOne(file.toPath(), loader);
    }

    @Override
    public Plugin[] loadPlugins(File directory) {
        List<Plugin> loaded = adapter.pluginBridge().loadAll(directory, loader);
        return loaded.toArray(new Plugin[0]);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void enablePlugin(Plugin plugin) {
        adapter.pluginBridge().enable(plugin);
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        adapter.pluginBridge().disable(plugin);
    }

    @Override
    public void disablePlugins() {
        for (Plugin plugin : adapter.pluginBridge().loadedPlugins()) {
            if (plugin.isEnabled()) {
                adapter.pluginBridge().disable(plugin);
            }
        }
    }

    @Override
    public void clearPlugins() {
        for (Plugin plugin : adapter.pluginBridge().loadedPlugins()) {
            if (plugin.isEnabled()) {
                adapter.pluginBridge().disable(plugin);
            }
            adapter.pluginBridge().unload(plugin);
        }
    }

    // ------------------------------------------------------------------ events

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        // The loader's own scan (the API artifact's machinery — the
        // @EventHandler walk with its 1.8.8 quirks: bridges and synthetic
        // methods skipped, invalid signatures logged per plugin).
        Map<Class<? extends Event>, Set<RegisteredListener>> registrations =
                loader.createRegisteredListeners(listener, plugin);
        for (Map.Entry<Class<? extends Event>, Set<RegisteredListener>> entry
                : registrations.entrySet()) {
            HandlerList list = getEventListeners(entry.getKey());
            list.registerAll(entry.getValue());
            ownRegistration(plugin, entry.getValue());
        }
    }

    @Override
    public void registerEvent(Class<? extends Event> event, Listener listener,
            org.bukkit.event.EventPriority priority, EventExecutor executor, Plugin plugin) {
        registerEvent(event, listener, priority, executor, plugin, false);
    }

    @Override
    public void registerEvent(Class<? extends Event> event, Listener listener,
            org.bukkit.event.EventPriority priority, EventExecutor executor, Plugin plugin,
            boolean ignoreCancelled) {
        HandlerList list = getEventListeners(event);
        RegisteredListener registration = new RegisteredListener(
                listener, executor, priority, plugin, ignoreCancelled);
        list.register(registration);
        ownRegistration(plugin, Set.of(registration));
    }

    @Override
    public void callEvent(Event event) throws IllegalStateException {
        // §28.4's own law: the events fire on the engine's authoritative
        // context; the legacy async marker is honored honestly — a plugin
        // may not fire an async-marked event FROM the primary thread (the
        // 1.8.8 rule, kept), and no dispatch lock exists (the owner
        // context serializes — reentrancy nests like method calls).
        if (event.isAsynchronous()) {
            if (adapter.engine().ticker().isOwnerThread()) {
                throw new IllegalStateException(event.getEventName()
                        + " cannot be triggered asynchronously from primary server thread.");
            }
        }
        fireEvent(event);
    }

    private void fireEvent(Event event) {
        // The 1.8.8 dispatch: baked handler array, disabled plugins
        // skipped, one listener's exception isolated and attributed, the
        // dispatch continuing (§28.8 case 8).
        HandlerList handlers = event.getHandlers();
        if (handlers == null) {
            return;
        }
        for (RegisteredListener registration : handlers.getRegisteredListeners()) {
            if (!registration.getPlugin().isEnabled()) {
                continue;
            }
            try {
                registration.callEvent(event);
            } catch (Throwable failure) {
                // "Could not pass event" — the 1.8.8 wording, the
                // §28.8 isolation contract.
                adapter.logger().log(Level.SEVERE, "Could not pass event "
                        + event.getEventName() + " to "
                        + registration.getPlugin().getDescription().getFullName(),
                        failure);
            }
        }
    }

    /**
     * The 1.8.8 getHandlerList walk: the registration class's own declared
     * list, or the nearest superclass that declares one.
     */
    private HandlerList getEventListeners(Class<? extends Event> type) {
        HandlerList cached = handlerLists.get(type);
        if (cached != null) {
            return cached;
        }
        HandlerList list = resolveHandlerList(type);
        if (list == null) {
            throw new IllegalPluginAccessException(
                    "Unable to find handler list for event " + type.getName()
                            + ". Static getHandlerList method required!");
        }
        handlerLists.put(type, list);
        return list;
    }

    private HandlerList resolveHandlerList(Class<? extends Event> type) {
        try {
            Method method = getRegistrationClass(type).getDeclaredMethod("getHandlerList");
            method.setAccessible(true);
            return (HandlerList) method.invoke(null);
        } catch (Exception noList) {
            return null;
        }
    }

    private Class<? extends Event> getRegistrationClass(Class<? extends Event> clazz) {
        try {
            clazz.getDeclaredMethod("getHandlerList");
            return clazz;
        } catch (NoSuchMethodException none) {
            Class<?> up = clazz.getSuperclass();
            if (up != null && !up.equals(Event.class) && Event.class.isAssignableFrom(up)) {
                return getRegistrationClass(up.asSubclass(Event.class));
            }
            return clazz; // the caller's resolve reports the refusal
        }
    }

    /**
     * The §28.5 ownership the legacy API never had: the listener
     * registration's close verb (unregister) is admitted into the owning
     * plugin's CURRENT engine scope — the disable sweep unregisters every
     * owned listener automatically, however the plugin ends.
     */
    private void ownRegistration(Plugin plugin, Set<RegisteredListener> registrations) {
        PluginHandle handle = adapter.pluginBridge().nativeHandleOf(plugin);
        if (handle == null) {
            return; // a foreign plugin (not the adapter's): its own lifecycle
        }
        for (RegisteredListener registration : registrations) {
            Listener listener = registration.getListener();
            handle.resources().own(() -> {
                HandlerList.unregisterAll(listener);
            });
        }
    }

    // ------------------------------------------------------------------ permissions (the registry, thin by design)

    @Override
    public Permission getPermission(String name) {
        return permissions.get(name);
    }

    @Override
    public void addPermission(Permission perm) {
        permissions.put(perm.getName(), perm);
    }

    @Override
    public void removePermission(Permission perm) {
        removePermission(perm.getName());
    }

    @Override
    public void removePermission(String name) {
        permissions.remove(name);
    }

    @Override
    public Set<Permission> getDefaultPermissions(boolean op) {
        return Set.of();
    }

    @Override
    public void recalculatePermissionDefaults(Permission perm) {
        // The no-op: the operator gate is the live permission check in this
        // slice (the recorded §27.9 limitation).
    }

    @Override
    public void subscribeToPermission(String permission, Permissible permissible) {
        permSubscriptions.computeIfAbsent(permission, key -> ConcurrentHashMap.newKeySet())
                .add(permissible);
    }

    @Override
    public void unsubscribeFromPermission(String permission, Permissible permissible) {
        Set<Permissible> subs = permSubscriptions.get(permission);
        if (subs != null) {
            subs.remove(permissible);
        }
    }

    @Override
    public Set<Permissible> getPermissionSubscriptions(String permission) {
        return Set.copyOf(permSubscriptions.getOrDefault(permission, Set.of()));
    }

    @Override
    public void subscribeToDefaultPerms(boolean op, Permissible permissible) {
        defaultPermSubscriptions.computeIfAbsent(op, key -> ConcurrentHashMap.newKeySet())
                .add(permissible);
    }

    @Override
    public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) {
        Set<Permissible> subs = defaultPermSubscriptions.get(op);
        if (subs != null) {
            subs.remove(permissible);
        }
    }

    @Override
    public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        return Set.copyOf(defaultPermSubscriptions.getOrDefault(op, Set.of()));
    }

    @Override
    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions.values());
    }

    @Override
    public boolean useTimings() {
        return false;
    }

    /**
     * The handler-list bake the shutdown walk calls (the 1.8.8 verb):
     * every list bakes once, idempotent.
     */
    public void bakeAll() {
        HandlerList.bakeAll();
    }
}
