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

Early development — the engine targets **1.8.8 vanilla-grade parity** first.
See [docs/ENGINEERING_PLAN.md](docs/ENGINEERING_PLAN.md) for the current
architecture, decisions and milestones.

[![1.8.8 vanilla parity](https://img.shields.io/badge/1.8.8_vanilla_parity-%E2%89%8895%25-brightgreen)](#status)
[![dimension ownership](https://img.shields.io/badge/dimension_ownership-%E2%89%8897%25-brightgreen)](#status)
[![CI pipeline](https://img.shields.io/badge/CI_pipeline-%E2%89%8897%25-brightgreen)](#status)
[![automated seats](https://img.shields.io/badge/automated_seats-3%2C770-brightgreen)](#status)

| Workstream | Done | State |
|------------|------|-------|
| 1.8.8 vanilla parity (the mechanics ledger, §22/§29) | ≈95% | 3,300+ engine seats green; the roll-law family closed whole (the drag, the rider scale, the kick) and the standing audit is down to single-digit named rows (each fixed within a pass or two of its discovery) |
| Dimension ownership (the FL4 waves — every system ranges its own world) | ≈97% | chests, containers, tiles, pistons, decorations, leads, beacons, the wither — each dimension's own family, redstone and wire |
| CI pipeline (Phase A + publishing) | ≈97% | parallelised with selective retry and failure forensics; every known failing member root-caused and retired at the source |
| Engine infrastructure (§22/§35: ownership, scheduling, transport) | ≈90% | the region model, the cross-owner protocol, the encode-once transport landed and pinned; the B-gaps wait for their triggers per §X.8 |
| Concurrency Phase 5 — parallel pre-gen lighting | 90% (gated) | landed, measured 1.4–1.6×, OFF by default (`torch.parallel-pregen-light`) |
| Concurrency Phase 6 — further parallel gameplay lanes | ≈20% | the random-tick lane landed; each further lane gated on its own evidence |
| Concurrency Phase 7 — heavy-owner optimization lanes | ≈10% | the profiling opener landed; the optimization lanes wait on its evidence |
| Concurrency Phase 8 — adaptive region balancing | 0% | not started (deliberately last) |
| Bukkit/Spigot/Paper adapter (§28.7) | ≈10% | first slice (plugin lifecycle + event + scheduler bridges) exists as `zamin-bukkit`, unwired; deferred by §27.1's order until 1.8.8 is polished |
| Phase 0 standing practice — per-path mutable-access audit | continuous | runs alongside every mechanics slice |

[Progress history](WORKLOG.md)

<sub>Every row's estimate is measured against its own ledger — the wave/section named in the row — not a feeling; percentages move as work lands and a row leaves the table at 100%. The landed record behind these numbers lives in [`Torch-Arcetecture.txt`](Torch-Arcetecture.txt) §22/§29, [`VANILLA_1_8_8_COMPATIBILITY.md`](VANILLA_1_8_8_COMPATIBILITY.md), [`NATIVE_API_CAPABILITIES.md`](NATIVE_API_CAPABILITIES.md) and [`WORKLOG.md`](WORKLOG.md). Last full re-verification of the table: 2026-10-31 (the FL4-F10 redstone world-binding wave + the parity PR wave #101/#102/#104); latest landing: 2026-11-08 (Waves RD + RK — the minecart's whole roll law: the ridden drag triple, the rider's 0.75 move scale, the wall launch, the W-kick with the brake cancel, the activator-rail gate).</sub>

## Downloading a dev build (no build tools needed)

Grab the latest zip from
[Releases](https://github.com/ZaminMC/ZaminTorch/releases) (dev builds land
on the `develop` branch's releases; prereleases included). Builds are
numbered `torch-1.8.8-N` — the numbering continues the old `v0.2.0-dev.N`
ledger — and are cut automatically from every `develop` push by
[`.github/workflows/release.yml`](.github/workflows/release.yml),
with the fat jar `torch-1.8.8-N.jar` and the bundle
`ZaminTorch-server-1.8.8-N.zip` attached to each:

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

## Automated releases

Every push to `develop` publishes the next numbered dev build automatically:
the workflow computes `N` from the tag ledger, stamps the version into
`distVersion` and `UpdateChecker.BUILD_VERSION`, runs the full test suite
(one retry for the three documented load flakes), tags `torch-1.8.8-N`, and
ships the jar + zip as a prerelease with auto-generated notes (commits since
the previous tag). To title a release, either add a `Release-Title: <title>`
trailer to a commit message, or cut an off-cycle release with
`scripts/release.sh "<title>"`. A head commit carrying `[skip ci]` postpones
its push's release to the next push.

## Modules

- `zamin-api` — the stable public API for plugins. No implementation types.
- `zamin-core` — the engine: lifecycle, world model, players, simulation.
- `zamin-transport` — the outbound transport layer (architecture §35): the
  encode-once payload primitive, the per-connection outbound queue with its
  coalesced drain, and transport metrics.
- `zamin-protocol-v1_8_8` — the 1.8.8 protocol adapter (wire handling,
  compression, encryption, session authentication).
- `zamin-bukkit` — the §28.7 Bukkit/Spigot compatibility adapter's first
  slice (plugin lifecycle + event + scheduler bridges over the native
  surfaces). Deliberately not wired into the launcher or the fat jar; the
  adapter era stays deferred per §27.1 and DEC-002.
- `zamin-launcher` — bootstrap entry point.

The module list derives from `settings.gradle.kts` (six modules at the
2026-10-28 verification).

## License

MIT — see [LICENSE](LICENSE).
