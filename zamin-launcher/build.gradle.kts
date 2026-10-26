import java.util.zip.ZipFile

dependencies {
    api(project(":zamin-api"))
    implementation(project(":zamin-core"))
    implementation(project(":zamin-protocol-v1_8_8"))
    // The interactive terminal console (architecture §30): JLine 3 behind the
    // Torch adapter, JUL retained as the logging source of truth. Versions
    // are pinned centrally in gradle/libs.versions.toml (ADR-21).
    implementation(libs.jline.terminal)
    implementation(libs.jline.reader)
    implementation(libs.jline.terminal.jansi)
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Main-Class" to "net.zaminmc.torch.launcher.ZaminLauncher")
    }
}

tasks.register<JavaExec>("runServer") {
    group = "zamin"
    description = "Starts a ZaminTorch development server."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "net.zaminmc.torch.launcher.ZaminLauncher"
    workingDir = file("${rootProject.projectDir}/run")
    standardInput = System.`in`
}

/**
 * The distributable dev build: one fat jar (every module + the netty
 * runtime), OS start scripts, the quickstart and a default configuration —
 * zipped for the GitHub release. The update checker inside the launcher
 * compares its build version against the released tag, so this artifact's
 * version constant and the release tag must move together.
 *
 * The torch-1.8.8-N scheme (from build 26 on): the release tag is
 * torch-1.8.8-N, the fat jar torch-1.8.8-N.jar, the zip
 * ZaminTorch-server-1.8.8-N.zip, and the numbering continues the legacy
 * v0.2.0-dev.N ledger (dev.25 -> torch-1.8.8-26). The release workflow
 * (.github/workflows/release.yml) stamps this constant and
 * UpdateChecker.BUILD_VERSION on every cut.
 */
val distVersion = "1.8.8-147"

val fatJar = tasks.register<Jar>("fatServerJar") {
    group = "zamin"
    description = "Merges every runtime classpath jar into one launchable server jar."
    archiveFileName.set("torch-$distVersion.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.jar) // the launcher jar must exist before it is expanded
    // The runtime-classpath module jars are expanded below; without these
    // dependsOn edges the fat jar can silently embed stale module jars.
    dependsOn(":zamin-api:jar")
    dependsOn(":zamin-core:jar")
    dependsOn(":zamin-protocol-v1_8_8:jar")
    manifest {
        attributes(
            "Main-Class" to "net.zaminmc.torch.launcher.ZaminLauncher",
            "Implementation-Version" to distVersion,
            "Implementation-Title" to "ZaminTorch",
        )
    }
    val runtimeJars = configurations.runtimeClasspath.get().files +
            tasks.jar.get().archiveFile.get().asFile
    for (jar in runtimeJars) {
        from(zipTree(jar)) { exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class") }
    }
}

/**
 * The packaged-console check (architecture §30.4.8): the interactive console
 * must work from the RELEASE artifact, not only the Gradle test classpath —
 * so the fat jar is verified to carry the JLine classes AND the Jansi
 * provider's native libraries for Windows/Linux/macOS. A missing native
 * would silently degrade every packaged server to the plain console on the
 * platform that needs it most.
 */
val verifyPackagedConsole = tasks.register("verifyPackagedConsole") {
    group = "zamin"
    description = "Verifies the fat jar contains JLine + the Jansi natives (the packaged console)."
    dependsOn(fatJar)
    doLast {
        val jarFile = fatJar.get().archiveFile.get().asFile
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            fun requireEntry(path: String, why: String) {
                check(entries.any { it.startsWith(path) }) {
                    "packaged console broken: $why missing from ${jarFile.name} (expected $path)"
                }
            }
            requireEntry("org/jline/terminal/", "the JLine terminal classes")
            requireEntry("org/jline/reader/", "the JLine reader classes")
            requireEntry("org/jline/utils/", "the JLine support classes")
            requireEntry("org/fusesource/jansi/", "the Jansi provider")
            // The provider's bundled natives (per-OS trees: .so/.dll/.jnilib).
            val natives = entries.count {
                it.startsWith("org/fusesource/jansi/internal/native/") &&
                        (it.endsWith(".so") || it.endsWith(".dll") || it.endsWith(".jnilib"))
            }
            val osFamilies = listOf("Linux", "Windows", "Mac", "FreeBSD")
            for (family in osFamilies) {
                check(entries.any { it.startsWith("org/fusesource/jansi/internal/native/$family/") && !it.endsWith("/") }) {
                    "packaged console broken: the Jansi $family native tree is empty"
                }
            }
            check(natives >= 3) {
                "packaged console broken: expected Jansi native libraries for at least" +
                        " Windows/Linux/Mac, found $natives"
            }
            println("verifyPackagedConsole: JLine + $natives Jansi native libraries present in ${jarFile.name}")
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPackagedConsole)
}

val stageDist = tasks.register<DefaultTask>("stageDist") {
    group = "zamin"
    dependsOn(fatJar)
    val stage = layout.buildDirectory.dir("dist/zamin-server")
    outputs.dir(stage)
    doLast {
        val dir = stage.get().asFile
        dir.mkdirs()

        val startShFile = dir.resolve("start.sh")
        startShFile.writeText("""#!/usr/bin/env sh
# ZaminTorch server launcher (Linux/macOS). Requires Java 21+ on the PATH.
cd "$(dirname "$0")"
exec java -Xms256m -Xmx1g -jar torch-$distVersion.jar
""")
        startShFile.setExecutable(true)

        val startBatFile = dir.resolve("start.bat")
        startBatFile.writeText("""@echo off
rem ZaminTorch server launcher (Windows). Requires Java 21+ on the PATH.
cd /d "%~dp0"
java -Xms256m -Xmx1g -jar torch-$distVersion.jar
pause
""")

        val startPs1File = dir.resolve("start.ps1")
        startPs1File.writeText("""# ZaminTorch server launcher (PowerShell). Requires Java 21+ on the PATH.
Set-Location ${'$'}PSScriptRoot
java -Xms256m -Xmx1g -jar torch-$distVersion.jar
""")

        val quickstartFile = dir.resolve("QUICKSTART.md")
        quickstartFile.writeText("""# ZaminTorch — quickstart (dev build $distVersion)

A custom Minecraft 1.8.8 server engine. This zip boots a playable survival
server with mining, crafting, furnaces, chests, mobs, falling blocks and the
light engine.

## Boot (Java 21 or newer required)

- Linux / macOS: `./start.sh`
- Windows (cmd): `start.bat`
- Windows (PowerShell): `start.ps1`

First boot writes `zamin.properties` (port 25565, survival, flat world) and
generates `worlds/` + `players/` next to the jar. Connect a vanilla 1.8.8
client to port 25565.

## What to know

- Console commands: `help`, `give <item> [count] [metadata]`,
  `rename <name...>`, `time query|set`, `spawnmob <pig|cow|chicken|zombie>`.
- `/rename` stamps the item's display name (rides the slot NBT end to end).
- All world/player/chest/furnace/mob data persist under the data directory.
- The launcher checks GitHub for a newer release on boot and prints a prompt
  when one exists. Update = stop, replace the jar, start again.

## Building from source

./gradlew :zamin-launcher:serverDist
""")

        val propertiesFile = dir.resolve("zamin.properties")
        propertiesFile.writeText("""#ZaminTorch server configuration
data-dir=.
gamemode=survival
host=0.0.0.0
max-players=20
motd=A ZaminTorch server
port=25565
tick-rate=20
view-distance=4
world-name=world
""")
    }
}

tasks.register<Zip>("serverDist") {
    group = "zamin"
    description = "Builds the downloadable dev-build zip (jar + scripts + docs)."
    archiveFileName.set("ZaminTorch-server-$distVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    from(fatJar)
    from(stageDist)
    dependsOn(fatJar, stageDist)
}
