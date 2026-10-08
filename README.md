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
| Day/night cycle sync + /time + /spawnmob | implemented |
| Real-client validation (mineflayer 1.8.8) | implemented |
| Item NBT (display names, enchantments) | planned |
| Mob persistence across restarts | planned (temporary decision, §146 pattern) |
| Anvil world format import | planned (compatibility adapter) |
| Minestom integration | deferred (see ADR in plan) |

This table reflects tested behavior only; nothing is marked implemented without an
automated test proving it.

## Building & running

Requirements: Java 21.

```bash
./gradlew build        # compile + tests
./gradlew test         # run unit + integration tests
./gradlew :zamin-launcher:runServer   # start a development server
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
