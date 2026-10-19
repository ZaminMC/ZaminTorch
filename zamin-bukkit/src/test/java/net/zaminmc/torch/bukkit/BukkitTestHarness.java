package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The module's shared test harness: ONE engine server + ONE adapter for the
 * whole test JVM (the legacy {@code Bukkit} singleton is set once — the API
 * forbids redefining it), booted on the loopback with port 0 exactly like
 * the engine's own acceptance suites (no real network: the adapter tests
 * never connect anywhere — the existing suites' own precedent).
 *
 * <p>The probe plugin jar is built at test time (the JDK's own compiler,
 * the pinned spigot-api artifact as the probe's whole compile classpath —
 * the same real-jar pattern the engine's PluginDiscoveryTest rides), placed
 * in the engine data dir's {@code plugins/} BEFORE the boot, and bridged by
 * the adapter's attach wave. The probe's observable is its log file (the
 * cross-classloader boundary — loader-transparent, the engine suite's own
 * marker-file law).</p>
 */
public final class BukkitTestHarness {

    private static volatile BukkitTestHarness instance;

    public final EngineServer server;
    public final BukkitAdapter adapter;
    public final Path dataDir;
    public final Path probeLog;

    /** The cell coordinates the probe's scripts are pinned to. */
    public static final int CELL_X = 2;
    public static final int CELL_Y = 6;
    public static final int CELL_Z = 2;

    private BukkitTestHarness(EngineServer server, BukkitAdapter adapter, Path dataDir,
            Path probeLog) {
        this.server = server;
        this.adapter = adapter;
        this.dataDir = dataDir;
        this.probeLog = probeLog;
    }

    /** The one harness for the JVM (tests run serially — max-workers=1). */
    public static synchronized BukkitTestHarness harness() throws Exception {
        if (instance != null) {
            return instance;
        }
        Path dataDir = Files.createTempDirectory("torch-bukkit-it");
        Path probeLog = dataDir.resolve("probe.log");
        // The probe's cross-classloader observable + script inputs ride
        // system properties (loader-transparent, set before the boot).
        System.setProperty("probe.log", probeLog.toString());
        buildProbeJar(dataDir.resolve("plugins"));
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), GameMode.CREATIVE);
        EngineServer server = new EngineServer(config);
        server.start();
        BukkitAdapter adapter = new BukkitAdapter(server);
        adapter.attach();
        instance = new BukkitTestHarness(server, adapter, dataDir, probeLog);
        // The JVM-exit walk: the engine's own shutdown (the plugins' disable
        // sweep + the lanes + the pool) — belt and braces with the platform
        // listener; both idempotent.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownNow(),
                "torch-bukkit-harness-shutdown"));
        return instance;
    }

    /** The idempotent end-of-JVM walk (the listener and the hook both call). */
    public static synchronized void shutdownNow() {
        BukkitTestHarness live = instance;
        if (live == null) {
            return;
        }
        instance = null;
        try {
            live.server.shutdown(null);
        } catch (RuntimeException alreadyStopped) {
            // idempotent in practice; never a test failure
        }
    }

    // ------------------------------------------------------------------ engine-driving helpers (the existing suites' own pattern)

    /** Joins a player through the engine's own join walk (fires the join event). */
    public PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    /**
     * The order-independence law: every class's entry state, ensured —
     * the probe live and listening (idempotent; the reload's own law
     * re-enables it with a FRESH scope when a previous class's drill left
     * it disabled).
     */
    public void ensureProbeEnabled() {
        org.bukkit.plugin.Plugin probe = org.bukkit.Bukkit.getPluginManager().getPlugin("Probe");
        if (probe != null && !probe.isEnabled()) {
            org.bukkit.Bukkit.getPluginManager().enablePlugin(probe);
        }
    }

    /** The engine's own disconnect walk (fires the quit event). */
    public void disconnect(PlayerSession session) {
        server.clientDisconnected(session, "test");
    }

    /** The bounded await (the engine suite's own helper). */
    public void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** The probe's log lines, as written (the cross-classloader observable). */
    public List<String> probeLogLines() throws IOException {
        if (!Files.exists(probeLog)) {
            return List.of();
        }
        return Files.readAllLines(probeLog, StandardCharsets.UTF_8);
    }

    /**
     * The probe-log containment read, safe inside await-suppliers (an IO
     * hiccup reads as "not there yet" — the await's own retry handles it).
     */
    public boolean probeLogContains(String needle) {
        try {
            for (String line : probeLogLines()) {
                if (line.contains(needle)) {
                    return true;
                }
            }
            return false;
        } catch (IOException unreadable) {
            return false;
        }
    }

    // ------------------------------------------------------------------ the probe jar (a real compile + a real jar)

    /** The probe plugin's source: the whole scripted surface (one probe, every test's arm). */
    static final String PROBE_SOURCE = """
            package probe;

            import org.bukkit.Bukkit;
            import org.bukkit.Material;
            import org.bukkit.event.EventHandler;
            import org.bukkit.event.EventPriority;
            import org.bukkit.event.Listener;
            import org.bukkit.event.block.BlockBreakEvent;
            import org.bukkit.event.block.BlockPlaceEvent;
            import org.bukkit.event.player.AsyncPlayerChatEvent;
            import org.bukkit.event.player.PlayerChatEvent;
            import org.bukkit.event.player.PlayerJoinEvent;
            import org.bukkit.event.player.PlayerQuitEvent;
            import org.bukkit.plugin.java.JavaPlugin;

            public class ProbePlugin extends JavaPlugin implements Listener {

                static synchronized void log(String line) {
                    try {
                        java.nio.file.Files.writeString(
                                java.nio.file.Path.of(System.getProperty("probe.log")),
                                line + System.lineSeparator(),
                                java.nio.file.StandardOpenOption.CREATE,
                                java.nio.file.StandardOpenOption.APPEND);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void onLoad() {
                    log("lifecycle:onLoad");
                }

                @Override
                public void onEnable() {
                    log("lifecycle:onEnable");
                    Bukkit.getPluginManager().registerEvents(this, this);

                    // The scheduler arms.
                    Bukkit.getScheduler().runTask(this, () ->
                            log("sched:sync-run primary=" + Bukkit.isPrimaryThread()));
                    Bukkit.getScheduler().runTask(this, () -> log("sched:sync-a"));
                    Bukkit.getScheduler().runTask(this, () -> log("sched:sync-b"));
                    Bukkit.getScheduler().runTaskLater(this, () -> log("sched:later-2"), 2);
                    Bukkit.getScheduler().runTaskTimer(this, () -> {
                        org.bukkit.World world = Bukkit.getWorlds().get(0);
                        long tick = world.getFullTime();
                        log("sched:timer-beat tick=" + tick);
                    }, 2, 2);
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        log("sched:async-run primary=" + Bukkit.isPrimaryThread()
                                + " thread=" + Thread.currentThread().getName());
                        // The adversarial arm: an off-owner block write THROUGH
                        // the legacy API — the adapter must route it by the
                        // owner, never a silent off-owner race.
                        org.bukkit.block.Block target = Bukkit.getWorlds().get(0)
                                .getBlockAt(2, 6, 2);
                        log("adversarial:async-submitted type=" + target.getType());
                        target.setType(Material.GLOWSTONE);
                        log("adversarial:async-body-done (the lane's own law: running work completes)");
                    });

                    // A later sync verifier for the adversarial write.
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        org.bukkit.World world = Bukkit.getWorlds().get(0);
                        log("adversarial:applied=" + (world.getBlockAt(2, 6, 2).getType()
                                == Material.GLOWSTONE)
                                + " primary=" + Bukkit.isPrimaryThread());
                    }, 6);
                }

                @Override
                public void onDisable() {
                    log("lifecycle:onDisable");
                }

                // ---------------- join/quit (the observation arms)

                @EventHandler
                public void onJoin(PlayerJoinEvent event) {
                    log("event:join message=" + event.getJoinMessage()
                            + " primary=" + Bukkit.isPrimaryThread());
                }

                @EventHandler
                public void onQuit(PlayerQuitEvent event) {
                    log("event:quit message=" + event.getQuitMessage()
                            + " primary=" + Bukkit.isPrimaryThread());
                }

                // ---------------- chat (both legacy forms + the reentrancy arm)

                @EventHandler(priority = EventPriority.NORMAL)
                public void onChat(AsyncPlayerChatEvent event) {
                    log("event:chat-async message=" + event.getMessage()
                            + " asyncFlag=" + event.isAsynchronous()
                            + " primary=" + Bukkit.isPrimaryThread());
                    if (event.getMessage().contains("censored")) {
                        event.setCancelled(true);
                        log("event:chat-async cancelled");
                    }
                    if (event.getMessage().equals("reentrant")) {
                        // The §28.8 case-7 arm: a listener firing a further
                        // event from inside the dispatch — nested, same thread.
                        log("reentrancy:chat-begin");
                        Bukkit.getPluginManager().callEvent(
                                new PlayerJoinEvent(event.getPlayer(), "nested"));
                        log("reentrancy:chat-end");
                    }
                }

                @SuppressWarnings("deprecation")
                @EventHandler(priority = EventPriority.MONITOR)
                public void onLegacyChat(PlayerChatEvent event) {
                    log("event:chat-legacy message=" + event.getMessage()
                            + " cancelled=" + event.isCancelled());
                }

                // ---------------- break/place (the order + cancellation + isolation arms)

                @EventHandler(priority = EventPriority.LOWEST)
                public void onBreakLowest(BlockBreakEvent event) {
                    log("prio:LOWEST type=" + event.getBlock().getType());
                    if (event.getBlock().getType() == Material.OBSIDIAN) {
                        event.setCancelled(true);
                        log("cancel:break-obsidian");
                    }
                }

                @EventHandler(priority = EventPriority.LOW)
                public void onBreakLow(BlockBreakEvent event) {
                    log("prio:LOW type=" + event.getBlock().getType());
                }

                @EventHandler(priority = EventPriority.NORMAL)
                public void onBreakNormal(BlockBreakEvent event) {
                    log("prio:NORMAL type=" + event.getBlock().getType());
                    if (event.getBlock().getType() == Material.GOLD_BLOCK) {
                        // The §28.8 case-8 arm: a throwing listener.
                        throw new IllegalStateException("probe's deliberate failure");
                    }
                }

                @EventHandler(priority = EventPriority.HIGH)
                public void onBreakHigh(BlockBreakEvent event) {
                    log("prio:HIGH type=" + event.getBlock().getType());
                }

                @EventHandler(priority = EventPriority.HIGHEST)
                public void onBreakHighest(BlockBreakEvent event) {
                    log("prio:HIGHEST type=" + event.getBlock().getType());
                }

                @EventHandler(priority = EventPriority.MONITOR)
                public void onBreakMonitor(BlockBreakEvent event) {
                    log("prio:MONITOR type=" + event.getBlock().getType());
                }

                @EventHandler(priority = EventPriority.MONITOR)
                public void onPlaceMonitor(BlockPlaceEvent event) {
                    log("event:place type=" + event.getBlockPlaced().getType()
                            + " primary=" + Bukkit.isPrimaryThread());
                }
            }
            """;

    /** The probe's plugin.yml — the legacy discovery's own descriptor form. */
    static final String PROBE_YML = """
            name: Probe
            version: 1.0.0
            main: probe.ProbePlugin
            author: Torch
            """;

    /**
     * Compiles the probe and jars it with plugin.yml — the real-jar pattern
     * (the engine's own PluginDiscoveryTest), the probe's compile classpath
     * being the pinned API artifact ALONE (plugins never see the engine).
     */
    static void buildProbeJar(Path pluginsDir) throws IOException {
        Files.createDirectories(pluginsDir);
        Path work = pluginsDir.resolve(".probe-build");
        Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(work.resolve("src/probe"));
        Files.writeString(work.resolve("src/probe/ProbePlugin.java"), PROBE_SOURCE,
                StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String apiClasspath = org.bukkit.plugin.java.JavaPlugin.class
                .getProtectionDomain().getCodeSource().getLocation().getPath();
        int compiled = compiler.run(null, null, null,
                "-classpath", apiClasspath,
                "-d", work.resolve("classes").toString(),
                work.resolve("src/probe/ProbePlugin.java").toString());
        if (compiled != 0) {
            throw new IllegalStateException("probe compilation failed");
        }
        Path jar = pluginsDir.resolve("Probe.jar");
        try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(
                Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.jar.JarEntry("plugin.yml"));
            out.write(PROBE_YML.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            try (var walk = Files.walk(work.resolve("classes"))) {
                for (Path classFile : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
                    String entry = work.resolve("classes").relativize(classFile)
                            .toString().replace('\\', '/');
                    out.putNextEntry(new java.util.jar.JarEntry(entry));
                    out.write(Files.readAllBytes(classFile));
                    out.closeEntry();
                }
            }
        }
        // The build work directory must not stay in the discovery path.
        deleteRecursively(work);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
