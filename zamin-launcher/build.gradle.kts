dependencies {
    api(project(":zamin-api"))
    implementation(project(":zamin-core"))
    implementation(project(":zamin-protocol-v1_8_8"))
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
 */
val distVersion = "0.2.0-dev.12"

val fatJar = tasks.register<Jar>("fatServerJar") {
    group = "zamin"
    description = "Merges every runtime classpath jar into one launchable server jar."
    archiveFileName.set("zamin-server-$distVersion.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.jar) // the launcher jar must exist before it is expanded
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
exec java -Xms256m -Xmx1g -jar zamin-server-$distVersion.jar
""")
        startShFile.setExecutable(true)

        val startBatFile = dir.resolve("start.bat")
        startBatFile.writeText("""@echo off
rem ZaminTorch server launcher (Windows). Requires Java 21+ on the PATH.
cd /d "%~dp0"
java -Xms256m -Xmx1g -jar zamin-server-$distVersion.jar
pause
""")

        val startPs1File = dir.resolve("start.ps1")
        startPs1File.writeText("""# ZaminTorch server launcher (PowerShell). Requires Java 21+ on the PATH.
Set-Location ${'$'}PSScriptRoot
java -Xms256m -Xmx1g -jar zamin-server-$distVersion.jar
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
