package net.zaminmc.torch.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Prepares the server home with the historical Paper layout before boot:
 *
 * <pre>
 * cache/  config/  libraries/  logs/  plugins/  versions/  world/
 * banned-ips.json  banned-players.json  bukkit.yml  commands.yml
 * eula.txt  ops.json  server.properties  spigot.yml  usercache.json
 * whitelist.json
 * </pre>
 *
 * <p>First boot writes every missing piece with defaults; nothing operator
 * owned is ever overwritten. Boot refuses to continue while
 * {@code eula.txt} still says {@code eula=false} — the historical EULA gate.</p>
 */
public final class ServerLayout {

    private ServerLayout() {
    }

    /** @return true when the EULA is accepted (or was just written as false). */
    public static boolean ensureEula(Path root) throws IOException {
        Path eula = root.resolve("eula.txt");
        if (!Files.exists(eula)) {
            String text = """
                    #By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).
                    #$(date)s
                    eula=false
                    """.replace("$(date)s", LocalDate.now().toString());
            Files.writeString(eula, text, StandardCharsets.UTF_8);
            return false;
        }
        return Files.readString(eula, StandardCharsets.UTF_8)
                .lines()
                .filter(l -> l.startsWith("eula="))
                .map(l -> l.substring("eula=".length()).trim())
                .findFirst()
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    /** Creates the historical directory set (idempotent). */
    public static void ensureDirectories(Path root, String worldName) throws IOException {
        for (String dir : new String[]{
                "cache", "config", "libraries", "logs", "plugins", "versions",
                worldName, worldName + "/data", worldName + "/playerdata"}) {
            Files.createDirectories(root.resolve(dir));
        }
    }

    /** Writes empty JSON stores when missing (ops, whitelist, bans, cache). */
    public static void ensureJsonStores(Path root) throws IOException {
        for (String file : new String[]{
                "ops.json", "whitelist.json", "banned-players.json",
                "banned-ips.json", "usercache.json"}) {
            Path path = root.resolve(file);
            if (!Files.exists(path)) {
                Files.writeString(path, "[\n]\n", StandardCharsets.UTF_8);
            }
        }
    }

    /** Writes the Paper-style YAML skeletons when missing (never overwrites). */
    public static void ensureYamlStores(Path root) throws IOException {
        writeIfMissing(root, "bukkit.yml", """
                settings:
                  allow-end: false
                  warn-on-overload: false
                  permissions-file: permissions.yml
                  update-folder: update
                  plugin-profiling: false
                  connection-throttle: 4000
                  query-plugins: true
                  deprecated-verbose: default
                  shutdown-message: Server closed
                  minimum-api: none
                spawn-limits:
                  monsters: 70
                  animals: 10
                  water-animals: 15
                  ambient: 15
                chunk-gc:
                  period-in-ticks: 600
                ticks-per:
                  animal-spawns: 400
                  monster-spawns: 1
                  autosave: 6000
                """);
        writeIfMissing(root, "spigot.yml", """
                # This is the main configuration file for Spigot.
                settings:
                  log-filters:
                    - authlib
                  netty-threads: 4
                  bungeecord: false
                  save-empty-scoreboard-teams: false
                  attribute:
                    maxHealth:
                      max: 2048.0
                    movementSpeed:
                      max: 2048.0
                    attackDamage:
                      max: 2048.0
                messages:
                  whitelist: You are not whitelisted on this server!
                  unknown-command: Unknown command. Type "/help" for help.
                  server-full: The server is full!
                  outdated-client: Outdated client! Please use {0}
                  outdated-server: Outdated server! I'm still on {0}
                  restart: Server is restarting
                commands:
                  tab-complete: 0
                  log: true
                  spam-exclusions:
                    - /skill
                world-settings:
                  default:
                    verbose: true
                    merge-radius:
                      item: 2.5
                      exp: 3.0
                    item-despawn-rate: 6000
                    ticks-per:
                      hopper-transfer: 8
                      hopper-check: 8
                """);
        writeIfMissing(root, "commands.yml", """
                command-block-overrides: []
                ignore-vanilla-permissions: false
                aliases:
                  icanhasbukkit:
                    - version $1-
                """);
        writeIfMissing(root, "permissions.yml", "[]\n");
        writeIfMissing(root, "config/paper-global.yml", """
                # This is the global configuration file for Paper.
                _version: 28
                misc:
                  region-file-cache-size: 256
                chunk-loading-advanced:
                  auto-config-send-distance: true
                """);
    }

    private static void writeIfMissing(Path root, String name, String content) throws IOException {
        Path path = root.resolve(name);
        if (!Files.exists(path)) {
            if (name.contains("/")) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }
    }

    /** The default {@code server.properties} (the historical vanilla key set). */
    public static void ensureServerProperties(Path root) throws IOException {
        Path file = root.resolve("server.properties");
        if (Files.exists(file)) {
            return;
        }
        EngineConfig d = EngineConfig.defaults();
        String text = """
                #Minecraft server properties
                #%s
                spawn-protection=16
                server-name=Unknown Server
                force-gamemode=false
                allow-nether=true
                gamemode=%d
                broadcast-console-to-ops=true
                enable-query=false
                player-idle-timeout=0
                difficulty=1
                spawn-monsters=true
                op-permission-level=4
                resource-pack-sha1=
                announce-player-achievements=true
                pvp=%s
                snooper-enabled=true
                level-type=%s
                hardcore=false
                enable-command-block=false
                max-players=%d
                network-compression-threshold=256
                max-world-size=29999984
                server-port=%d
                server-ip=%s
                spawn-npcs=true
                allow-flight=false
                level-name=%s
                view-distance=%d
                white-list=%s
                generate-structures=true
                online-mode=false
                max-build-height=256
                level-seed=
                motd=%s
                enable-rcon=false
                """.formatted(LocalDate.now().toString(),
                d.gamemode().legacyId(), d.pvp(), d.levelType(), d.maxPlayers(),
                d.port(), d.host().equals("0.0.0.0") ? "" : d.host(),
                d.worldName(), d.viewDistance(), d.whiteList(), d.motd());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
