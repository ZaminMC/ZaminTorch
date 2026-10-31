// The §28.7 Bukkit/Spigot/Paper compatibility adapter — the first slice:
// plugin lifecycle + event bridge + scheduler bridge, translated onto the
// engine's own §27 native surfaces (architecture §27.2: the adapter sits
// ABOVE the native engine/API boundary; it must not become Torch's internal
// architecture). This module is deliberately NOT wired into zamin-launcher
// or the fat server jar yet — the runtime distribution story (bundling the
// GPL-licensed API artifact) is a later, deliberate decision.
//
// Repository decisions (verified by actual resolution on 2026-10-20):
// - org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT resolves from the SpigotMC
//   snapshot repository (hub.spigotmc.org). The pinned version string is the
//   lineage's only published form; its snapshot metadata is frozen at build
//   1.8.8-R0.1-20160221.082514-43 (build 43, 2016-02-21).
// - The API's transitive net.md-5:bungeecord-chat:1.8-SNAPSHOT no longer
//   resolves from Sonatype; PaperMC's maven-public proxy is the one live
//   resolver for the 1.8 lineage (verified by actual resolution).
//
// License decisions: the spigot-api artifact is the Spigot project's
// GPL-3.0-licensed API; bungeecord-chat is BSD-3-Clause; the compile-scope
// transitives (guava 17.0, gson 2.2.4, snakeyaml 1.15, commons-lang 2.6,
// json-simple 1.1.1, ebean 2.8.1) are Apache-2.0. Every one of them is
// consumed COMPILE/TEST-ONLY here: compileOnly for the adapter's own
// compilation and testImplementation for the fixture plugins — the module's
// published jar contains only Torch adapter code, redistributing none of
// the API artifact (the §27.9 honest-gap rule: never claim more runtime
// compatibility than the evidence supports).

repositories {
    // The 1.8.8 API lineage's home repository (§27.9's inventory).
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    // The bungeecord-chat 1.8-SNAPSHOT transitive's live resolver.
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":zamin-api"))
    // The engine surfaces the bridge rides (§27.6 owner routing, §28.5
    // plugin host, §27.7 bus, §27.3 scheduler): implementation, not api —
    // the adapter's own face is org.bukkit.*, never the engine's.
    implementation(project(":zamin-core"))
    // The shared engine-condition awaits (ML-002) ride zamin-core's test
    // fixtures — see docs/TEST_DEADLINE_CENSUS.md.
    testImplementation(testFixtures(project(":zamin-core")))
    // API only, never a server implementation (§27.2). compileOnly: the
    // adapter's jar must not redistribute the API artifact.
    compileOnly(libs.spigot.api)
    // The tests (and the probe plugin fixtures they compile) need the real
    // API classes at runtime — JavaPlugin, PluginDescriptionFile and the
    // loader machinery come from the artifact itself.
    testImplementation(libs.spigot.api)
    // The harness's end-of-run shutdown listener (the shared engine's clean
    // stop after the last test in the JVM).
    testImplementation("org.junit.platform:junit-platform-launcher")
}

// The suite's own evidence line: the execution order is part of the test
// contract (the classes' @Order walk 0..4, the methods' @Order inside) —
// started events make the order visible in the run log.
tasks.withType<Test>().configureEach {
    testLogging.events("started", "failed", "skipped")
    // CI Phase A: this module stays SINGLE-FORK. Its harness deliberately
    // shares ONE engine across all classes (BukkitTestHarness's singleton
    // + the BukkitHarnessShutdownListener) — the module scripts are
    // evaluated after the root's subprojects block, so this value wins
    // over the root's maxParallelForks = 3.
    maxParallelForks = 1
}
