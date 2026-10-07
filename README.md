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
**Vertical Slice #1**: a real 1.8.8 client can connect, log in, spawn into a flat
world, move, and disconnect cleanly.

| Feature        | Status      |
|----------------|-------------|
| Server lifecycle | implemented |
| 1.8.8 handshake/status/login | implemented |
| Flat world + chunks | implemented |
| Player movement tracking | implemented |
| Block placement/breaking | planned (Slice #2) |
| Persistence | planned (Slice #2) |
| Chat/commands | planned (Slice #3) |
| Mobs/entities | planned |
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
configured port.

## Modules

- `zamin-api` — the stable public API for plugins. No implementation types.
- `zamin-core` — the engine: lifecycle, world model, players, simulation.
- `zamin-protocol-v1_8_8` — the 1.8.8 protocol adapter (wire handling only).
- `zamin-launcher` — bootstrap entry point.

## License

MIT — see [LICENSE](LICENSE).
