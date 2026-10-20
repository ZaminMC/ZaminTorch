package net.zaminmc.torch.bukkit;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.jar.JarFile;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dependency decision verified through actual parsing (§27.9's
 * evidence-based compatibility): the pinned 1.8.8 API artifact resolved by
 * the build is reflected and its jar entries read — the classes the
 * adapter's whole surface depends on (the loader machinery, the event
 * group, the priority enum's order, the scheduler) must exist with the
 * shapes the adapter compiled against.
 */
@Order(0)
class SpigotApiSurfaceTest {

    @Test
    void thePinnedApiArtifactCarriesTheLoaderMachinery() throws Exception {
        // The artifact behind the compile classpath: JavaPlugin's own code
        // source IS the pinned jar (testImplementation).
        Path apiJar = Path.of(org.bukkit.plugin.java.JavaPlugin.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(apiJar.toString().contains("spigot-api"), "the pinned artifact: " + apiJar);
        try (JarFile jar = new JarFile(apiJar.toFile())) {
            for (String entry : new String[]{
                    "org/bukkit/plugin/java/JavaPlugin.class",
                    "org/bukkit/plugin/java/JavaPluginLoader.class",
                    "org/bukkit/plugin/java/PluginClassLoader.class",
                    "org/bukkit/event/player/PlayerJoinEvent.class",
                    "org/bukkit/event/player/PlayerQuitEvent.class",
                    "org/bukkit/event/player/PlayerChatEvent.class",
                    "org/bukkit/event/player/AsyncPlayerChatEvent.class",
                    "org/bukkit/event/block/BlockBreakEvent.class",
                    "org/bukkit/event/block/BlockPlaceEvent.class",
                    "org/bukkit/event/HandlerList.class",
                    "org/bukkit/scheduler/BukkitScheduler.class",
                    "org/bukkit/Bukkit.class"}) {
                assertNotNull(jar.getJarEntry(entry), "the artifact carries " + entry);
            }
        }
    }

    @Test
    void theLifecycleArmsExistOnJavaPlugin() throws Exception {
        for (String arm : new String[]{"onLoad", "onEnable", "onDisable"}) {
            Method method = org.bukkit.plugin.java.JavaPlugin.class.getMethod(arm);
            assertTrue(Modifier.isPublic(method.getModifiers()), arm + " is public");
        }
    }

    @Test
    void thePriorityOrderIsTheLegacyOne() {
        // LOWEST before LOW before NORMAL before HIGH before HIGHEST before
        // MONITOR — the slot order the bridge's dispatch relies on.
        org.bukkit.event.EventPriority[] values = org.bukkit.event.EventPriority.values();
        assertTrue(values[0].getSlot() < values[1].getSlot(), "ascending slots");
        for (int i = 1; i < values.length; i++) {
            assertTrue(values[i - 1].getSlot() < values[i].getSlot(),
                    values[i - 1] + " before " + values[i]);
        }
        assertTrue(org.bukkit.event.EventPriority.MONITOR.getSlot()
                > org.bukkit.event.EventPriority.HIGHEST.getSlot(), "MONITOR is last");
    }

    @Test
    void theChatEventsCarryTheLegacyShapes() throws Exception {
        // The shapes verified by reflection (the constructors reject null
        // players by design — constructing with nulls is not the point).
        assertTrue(org.bukkit.event.player.PlayerChatEvent.class
                .getConstructor(org.bukkit.entity.Player.class, String.class) != null);
        assertTrue(org.bukkit.event.player.AsyncPlayerChatEvent.class.getConstructor(
                boolean.class, org.bukkit.entity.Player.class, String.class,
                java.util.Set.class) != null);
        assertTrue(org.bukkit.event.block.BlockBreakEvent.class.getConstructor(
                org.bukkit.block.Block.class, org.bukkit.entity.Player.class) != null);
        assertTrue(org.bukkit.event.block.BlockBreakEvent.class
                .getDeclaredMethod("setCancelled", boolean.class) != null);
        assertTrue(org.bukkit.event.Cancellable.class
                .isAssignableFrom(org.bukkit.event.block.BlockBreakEvent.class));
    }
}
