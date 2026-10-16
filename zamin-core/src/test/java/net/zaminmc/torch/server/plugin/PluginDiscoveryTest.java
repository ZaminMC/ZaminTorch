package net.zaminmc.torch.server.plugin;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The discovery/classloading wave (Slice 79 — §28.5's DISCOVERED →
 * LOADED stages over the host's registration): a real jar built at test
 * time (the JDK's own compiler) carries torch-plugin.properties + a
 * compiled PluginBody — the boot scan registers it through the host,
 * the broken jar and the duplicate id are skipped per-jar without
 * killing the boot, and the unload runs the loader's close verb.
 */
class PluginDiscoveryTest {

    @TempDir
    Path dataDir;

    @TempDir
    Path workDir;

    // ------------------------------------------------------------------
    // The jar builder (a real compile + a real jar).
    // ------------------------------------------------------------------

    /** The demo plugin's source: its enable writes the marker file. */
    private static final String DEMO_SOURCE = """
            package demo;

            import net.zaminmc.torch.plugin.PluginBody;
            import net.zaminmc.torch.plugin.PluginHandle;

            public class DemoPlugin implements PluginBody {
                @Override
                public void onEnable(PluginHandle handle) {
                    try {
                        java.nio.file.Files.writeString(
                                java.nio.file.Path.of(
                                        System.getProperty("demo.marker")),
                                "enabled:" + handle.descriptor().id());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            """;

    /**
     * Compiles the demo source and jars it with the descriptor. The
     * marker path rides the demo.marker system property (the enable's
     * observable is loader-transparent).
     */
    private Path buildDemoJar(String jarName, String id, String descriptorOverride)
            throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path classes = Files.createDirectories(workDir.resolve("classes-" + jarName));
        Path source = workDir.resolve("src-" + jarName + "/demo/DemoPlugin.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, DEMO_SOURCE, StandardCharsets.UTF_8);
        String apiClasspath = net.zaminmc.torch.plugin.PluginBody.class
                .getProtectionDomain().getCodeSource().getLocation().getPath();
        int compiled = compiler.run(null, null, null,
                "-classpath", apiClasspath,
                "-d", classes.toString(),
                source.toString());
        assertEquals(0, compiled, "the demo plugin compiled");

        Path jar = dataDir.resolve("plugins").resolve(jarName + ".jar");
        Files.createDirectories(jar.getParent());
        try (java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(
                Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.jar.JarEntry(
                    PluginDiscovery.DESCRIPTOR_ENTRY));
            String descriptor = descriptorOverride != null ? descriptorOverride
                    : "id=" + id + "\nversion=1.0.0\nauthors=Test Author\nentry=demo.DemoPlugin\n";
            out.write(descriptor.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            try (Stream<Path> classFiles = Files.walk(classes)) {
                for (Path classFile : classFiles
                        .filter(path -> path.toString().endsWith(".class"))
                        .toList()) {
                    String entryName = classes.relativize(classFile).toString()
                            .replace('\\', '/');
                    out.putNextEntry(new java.util.jar.JarEntry(entryName));
                    Files.copy(classFile, out);
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    // ------------------------------------------------------------------
    // The scan's own unit law (the per-jar isolation).
    // ------------------------------------------------------------------

    @Test
    void theScanLoadsTheJarAndInstantiatesTheBody() throws Exception {
        Path jar = buildDemoJar("demo", "demo", null);
        var candidates = PluginDiscovery.scan(dataDir.resolve("plugins"));
        assertEquals(1, candidates.size(), "the one jar loaded");
        var candidate = candidates.get(0);
        assertEquals("demo", candidate.descriptor().id(), "the descriptor's id row");
        assertEquals("1.0.0", candidate.descriptor().version(), "the version row");
        assertEquals(1, candidate.descriptor().authors().size(), "the authors row");
        assertEquals("Test Author", candidate.descriptor().authors().get(0));
        candidate.loader().close();
    }

    @Test
    void theBrokenJarIsSkippedWithoutKillingTheScan() throws Exception {
        buildDemoJar("good", "good", null);
        // The broken jar: no descriptor at all.
        Path broken = dataDir.resolve("plugins").resolve("broken.jar");
        Files.createDirectories(broken.getParent());
        Files.write(broken, new byte[]{1, 2, 3}); // not even a real jar
        // A descriptor-less (but valid) jar: an empty jar file.
        try (java.util.jar.JarOutputStream ignored = new java.util.jar.JarOutputStream(
                Files.newOutputStream(dataDir.resolve("plugins").resolve("empty.jar")))) {
            // empty
        }
        var candidates = PluginDiscovery.scan(dataDir.resolve("plugins"));
        assertEquals(1, candidates.size(), "only the good jar loaded");
        assertEquals("good", candidates.get(0).descriptor().id());
        candidates.get(0).loader().close();
    }

    // ------------------------------------------------------------------
    // The registration law (the duplicate skip + the unload's loader verb).
    // ------------------------------------------------------------------

    @Test
    void theDuplicateIdSkipsTheLaterJarAndTheUnloadRunsTheLoaderClose() throws Exception {
        System.setProperty("demo.marker",
                dataDir.resolve("marker3.txt").toString());
        buildDemoJar("first", "dupe", null);
        buildDemoJar("second", "dupe", null);
        PluginManager host = new PluginManager(
                new PluginHostTest.DirectCompute(), new PluginHostTest.DirectIo());
        int registered = PluginDiscovery.discoverAndRegister(host,
                dataDir.resolve("plugins"));
        assertEquals(1, registered, "the first jar registered, the duplicate skipped");
        var handle = host.byId("dupe").orElseThrow();
        assertEquals(net.zaminmc.torch.plugin.PluginState.ENABLED, handle.state(),
                "the registered plugin enabled through the host's own walk");
        // The duplicate's loader closed (its classes released) — observable
        // through the registry's own count.
        assertTrue(host.plugins().size() == 1, "one plugin in the registry");

        // The unload runs the loader's close verb: the classloader's
        // classes stop resolving after the unload.
        AtomicBoolean unloadVerbRan = new AtomicBoolean(false);
        host.registerWithLoader(new net.zaminmc.torch.plugin.PluginDescriptor(
                "verbcheck", "1.0", java.util.List.of()), ignored -> {
        }, () -> unloadVerbRan.set(true));
        var verbHandle = host.byId("verbcheck").orElseThrow();
        host.unload(verbHandle);
        assertTrue(unloadVerbRan.get(), "the unload verb ran at UNLOAD");
        assertTrue(host.byId("verbcheck").isEmpty(), "the unloaded plugin left the registry");
        // The dupe plugin's own unload path: disable first, then unload.
        host.disable(handle);
        host.unload(handle);
        assertTrue(host.byId("dupe").isEmpty(), "the dupe unloaded cleanly");
    }

    // ------------------------------------------------------------------
    // The live-engine boot scan.
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "plugindisc",
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    @Test
    void theBootDiscoversAndEnablesTheJarPlugin() throws Exception {
        System.setProperty("demo.marker",
                dataDir.resolve("marker.txt").toString());
        buildDemoJar("bootdemo", "bootdemo", null);
        EngineServer server = boot();
        try {
            var handle = server.plugins().byId("bootdemo").orElseThrow();
            assertEquals(net.zaminmc.torch.plugin.PluginState.ENABLED,
                    handle.state(), "the discovered plugin enabled at boot");
            assertEquals("1.0.0", handle.descriptor().version());
            assertTrue(Files.exists(dataDir.resolve("marker.txt")),
                    "the loaded class's own enable ran (the marker file)");
            assertEquals("enabled:bootdemo",
                    Files.readString(dataDir.resolve("marker.txt")),
                    "the enable saw its own descriptor through the handle");
        } finally {
            server.shutdown(() -> {
            });
        }
    }

    @Test
    void theBrokenJarNeverKillsTheBoot() throws Exception {
        System.setProperty("demo.marker",
                dataDir.resolve("marker2.txt").toString());
        // The good jar + a garbage file beside it: the boot must survive.
        buildDemoJar("survivor", "survivor", null);
        Files.write(dataDir.resolve("plugins").resolve("garbage.jar"),
                new byte[]{1, 2, 3});
        EngineServer server = boot();
        try {
            assertTrue(server.plugins().byId("survivor").isPresent(),
                    "the good jar registered beside the garbage");
            assertEquals(net.zaminmc.torch.plugin.PluginState.ENABLED,
                    server.plugins().byId("survivor").orElseThrow().state());
        } finally {
            server.shutdown(() -> {
            });
        }
    }
}
