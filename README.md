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
current architecture, decisions and milestones, and `Torch-Arcetecture.txt` §29 +
`VANILLA_1_8_8_COMPATIBILITY.md` for the per-mechanic records. The engine's
mechanics ledger runs deep (survival loop, mobs/AI, combat, physics, fluids,
villages, biomes, maps, remote admin — the landed record lives in
`Torch-Arcetecture.txt` §22/§29, `VANILLA_1_8_8_COMPATIBILITY.md`,
`NATIVE_API_CAPABILITIES.md` and `WORKLOG.md`). The table below is the live
TODO list: only what is NOT done yet — implemented work is never listed
here; a row leaves the table the moment its work lands.

| Feature | Status |
|---------|--------|
| Concurrency Phase 5 — the lighting batch's parallel wiring | landed but gated OFF by default (`torch.parallel-pregen-light`) — the measured win stands (1.4-1.6x at the boot shape after the storm fix); the gate retires on the lane's own evidence (§22) |
| Concurrency Phase 6 — further parallel per-region gameplay lanes beyond the random-tick lane | pending — each lane gated on its own evidence (§22) |
| Concurrency Phase 7 — the heavy-owner optimization lanes | pending — the profiling opener landed; each optimization stays gated on the §22 evidence bar |
| Concurrency Phase 8 — adaptive balancing (dynamic split/merge, load-driven migration with in-flight tasks, telemetry, anti-oscillation rules) | not started |
| Phase 0 standing practice — the per-path mutable-access audit | continuous — runs alongside every mechanics slice |
| §28.7 Bukkit/Spigot/Paper compatibility adapter | deferred by §27.1's order — after 1.8.8 is polished into a playable, maintainable server |

This table is the complete pending list — kept current as work lands, and
nothing leaves it without an automated test proving the landing. Verified
against the code on 2026-10-28 (the wither's conditional exemption arms —
the last pending parity row — landed with the exemption-arms slice: the
armor hull and the never-despawn law fixed, the fall/summon arms already
landed, the light-level/cobweb/no-riding arms retired with evidence; the
transport waves T-1..T-4, the five parity-audit PRs #54-#59, the
mining-speed parity audit PR #60, the eye/firework/armor-stand
dimension carries — Waves EYFW + AS, the LAST overworld-0 family closed,
plus the EYFW join-view repairs the AS scoping surfaced — the
§35.12 codec measurements (Wave T-5, the compression/encryption rows),
the serialized-column memoization (Wave SC — the perf family's head;
the warm 81-column re-stream cycle drops from the measured ~32 ms to
0.765 ms), and the flight families' contact arms (Wave FC — the
ML-100 tail: bats, squids and guardians now wound and burn in lava
like every body in the reference, the squid/guardian swim gates split
to water alone, the ghast's fluid drag bands), the slime pad trio +
the redstone ore's step glow + the farmland trample (Waves TR + SG),
and the item/orb landing physics + the friction family whole (Wave
IO-F 2026-10-29: the item's pre-move gravity, the vertical-clip
beforeCollision consult — the slime flip, the post-move drag reading
the support cell's slipperiness, the grounded -0.5/-0.9 flips; the
mob's damping at all three sites reading the block's own slipperiness
* 0.91 with the reference's 0.91 air base — the ice slide), and the
rider-spare fall (Wave RS 2026-10-29: F-RIDER-SPARE closed — the
mount's landing forwards the same (distance, multiplier) pair UP to
the seated player through the delegate at takeFallDamage's head; the
ridden body's own fall walk the pre-fix engine swallowed whole; the
vehicles' whole fall walk with the base-alone payment — a vehicle
pays nothing itself, the seated player pays) also landed since the
prior verification.

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
