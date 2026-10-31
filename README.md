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
current architecture, decisions and milestones, and `Torch-Arcetecture.txt` §22/§29 +
`VANILLA_1_8_8_COMPATIBILITY.md` for the per-mechanic records. The engine's
mechanics ledger runs deep (survival loop, mobs/AI, combat, physics, fluids,
villages, biomes, maps, remote admin — the landed record lives in
`Torch-Arcetecture.txt` §22/§29, `VANILLA_1_8_8_COMPATIBILITY.md`,
`NATIVE_API_CAPABILITIES.md` and `WORKLOG.md`).

**The live progress table** — every row carries its true completion estimate,
measured against its own ledger (the wave/section cited in the row), not a
feeling. Percentages move as work lands; a row leaves the table at 100%.

| Workstream | Done | The honest state behind the number |
|------------|------|-------------------------------------|
| 1.8.8 vanilla parity (the mechanics ledger) | ~92% | ~2,950 automated seats green; the standing parity audit's queue is down to single-digit named rows (the F-EX/VT series — each fixed within a pass or two of its discovery); the residual is the long tail of the audit, not any known major system |
| Dimension ownership (the FL4 waves — every system ranging its own world) | ~80% | FL4-F2..F10 landed (fire, TNT, block updates, falling bodies, item/orb physics, entity crossings, **the redstone world binding — per-dimension signal systems, routed use consults, dimension-filtered probes, the ZCD v2 store stamp**, 2026-10-31); the container/storage family (Gap 2) is the remaining major wave, then the honest tails each wave ledgered |
| CI pipeline (Phase A + publishing) | ~88% | parallelisation + selective retry + failure forensics landed; the publish path's two blockers (the test freeze, the notes 422) fixed; the residual load-flake lottery continues to shrink at the source — seven members retired across the last two sessions (the pig walk-and-reach seat, five runs, the latest) and runs 562-565's one-off members are queued root-cause-first |
| Engine infrastructure (§22/§35: ownership, scheduling, transport) | ~90% | the region model, the cross-owner protocol, the encode-once transport landed and pinned; the B-gaps (item components, dimension registry) deliberately wait for their triggers per §X.8 |
| Concurrency Phase 5 — parallel pre-gen lighting | 90% (gated) | landed, measured 1.4-1.6x, OFF by default (`torch.parallel-pregen-light`); the gate retires on the lane's own evidence |
| Concurrency Phase 6 — further parallel gameplay lanes | ~20% | the random-tick lane landed; each further lane gated on its own evidence |
| Concurrency Phase 7 — heavy-owner optimization lanes | ~10% | the profiling opener landed; the optimization lanes wait on its evidence |
| Concurrency Phase 8 — adaptive region balancing | 0% | not started (deliberately last) |
| Bukkit/Spigot/Paper adapter (§28.7) | ~10% | first slice (plugin lifecycle + event + scheduler bridges) exists as `zamin-bukkit`, unwired; deferred by §27.1's order until 1.8.8 is polished |
| Phase 0 standing practice — per-path mutable-access audit | continuous | runs alongside every mechanics slice |

The landed record behind these numbers lives in the ledgers named above and in
`WORKLOG.md`; the last full re-verification of the table was 2026-10-31 (the
FL4-F10 redstone world-binding wave, the parity PR wave #101/#102/#104 — the
ghast fireball constants, the wrong-world bed blast, the player wound gate —
and the pig-seat flake retirement).

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
