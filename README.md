# ZaminTorch

ZaminTorch is a from-first-principles Minecraft server engine. Its first compatibility
target is **Minecraft Java Edition 1.8.8** — as a *compatibility target*, not an
architectural one. ZaminTorch reproduces the observable behavior of Minecraft while
being architecturally unlike the historical Bukkit/CraftBukkit/Spigot/Paper lineage.

```
Minecraft client
       ↓
1.8.8 protocol adapter
       ↓
Zamin engine (world, entities, simulation, registries)
       ↓
stable Zamin API  ← plugins / future compatibility layers
```

## Status

Early development. See [docs/ENGINEERING_PLAN.md](docs/ENGINEERING_PLAN.md) for the
current architecture, decisions and milestones. The current milestone is
**Slice #4 — survival loop**: a real 1.8.8 client can mine with server-validated
timing, chase and collect drops, and place from its inventory; the full loop is
proven over the wire and against a live process.

| Feature        | Status      |
|----------------|-------------|
| Server lifecycle | implemented |
| 1.8.8 handshake/status/login | implemented |
| Flat world + chunks | implemented |
| Player movement tracking | implemented |
| Block placement/breaking (creative) | implemented |
| World persistence (delta store) | implemented |
| Chat + basic commands | implemented |
| Two-player visibility sync | implemented |
| Survival mining (server-validated timing) | implemented |
| Block drops + item entities | implemented |
| Player inventory (hotbar/main) + pickup | implemented |
| Inventory UI clicks + quick-move/number keys | implemented |
| Tools & harvest classes (+ durability wear) | implemented |
| Player data persistence (ZPD v1) | implemented |
| Crafting (2x2 + 3x3 table) | implemented |
| Furnace smelting (ZFD persistence) | implemented |
| Chest storage container (ZCD persistence) | implemented |
| Item metadata variants (charcoal, red sand) | implemented |
| Survival body: health/food/fall/death/respawn | implemented |
| Living mobs (pig, cow, chicken, zombie) + population | implemented |
| Melee combat, knockback, mob loot | implemented |
| PvP (player melee, hurt window, knockback, death/respawn) | implemented |
| Vanilla-DataWatcher metadata parity (real-client crash fix) | implemented |
| Day/night cycle sync + /time + /spawnmob | implemented |
| Scheduled block updates: falling sand/gravel, torch pop, grass spread/decay | implemented |
| Mob persistence across restarts (ZMD v1) | implemented |
| Gravel + flint (first-hit-wins chance drops) | implemented |
| Real-client validation (mineflayer 1.8.8) | implemented |
| Sounds + particles (FX bus: dig/eat/bow/shatter feedback) | implemented |
| Ranged combat: bow (charge), snowball, egg (chick roll) | implemented |
| Posture sync: sneak/sprint flags to observers | implemented |
| Item NBT (display names, enchantments) | planned |
| Anvil world format import | planned (compatibility adapter) |
| Minestom integration | deferred (see ADR in plan) |

This table reflects tested behavior only; nothing is marked implemented without an
automated test proving it.

## Downloading a dev build (no build tools needed)

Grab the latest zip from
[Releases](https://github.com/ZaminMC/ZaminTorch/releases) (dev builds land
on the `develop` branch's releases; prereleases included):

1. Unzip. Requires **Java 21+** on the PATH.
2. `./start.sh` (Linux/macOS), `start.bat` (cmd) or `start.ps1` (PowerShell).
3. Connect a vanilla 1.8.8 client to port 25565. First boot writes
   `zamin.properties` and generates the world next to the jar.

The server checks GitHub on boot and **prints an update prompt** when a newer
release exists — updating means stop, replace the jar, start again. Nothing
is downloaded or replaced automatically.

## Building & running

Requirements: Java 21.

```bash
./gradlew build        # compile + tests
./gradlew test         # run unit + integration tests
./gradlew :zamin-launcher:runServer   # start a development server
./gradlew :zamin-launcher:serverDist  # build the release zip
```

The development server reads `zamin.properties` from the working directory
(a default is created on first start) and listens for 1.8.8 clients on the
configured port. Console commands: `help`, `state`, `save`, `stop`. In-game:
`/help`, `/ping`. World changes are persisted to `worlds/<world>/zamin-delta.bin`
and survive restarts.

## Modules

- `zamin-api` — the stable public API for plugins. No implementation types.
- `zamin-core` — the engine: lifecycle, world model, players, simulation.
- `zamin-protocol-v1_8_8` — the 1.8.8 protocol adapter (wire handling only).
- `zamin-launcher` — bootstrap entry point.

## License

MIT — see [LICENSE](LICENSE).
