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
[![automated seats](https://img.shields.io/badge/automated_seats-3%2C910-brightgreen)](#status)

| Workstream | Done | State |
|------------|------|-------|
| 1.8.8 vanilla parity (the mechanics ledger, §22/§29) | ≈95% | 3,300+ engine seats green; the flank steal's own cadence closed to the selector's 3-tick setup-pass law; the farm animals' food-tempt walk + the ctor swim census + the chicken's egg layer and slow fall landed; the leash's leeched-navigation arm (the holder-centered village pin, the swim park, the band-2 wander) and the chicken's jockey family (the 5% baby-on-chicken spawn, the riderless despawn, the 10-XP kill, the jockey seat) landed |
| Dimension ownership (the FL4 waves — every system ranges its own world) | ≈97% | chests, containers, tiles, pistons, decorations, leads, beacons, the wither — each dimension's own family, redstone and wire |
| CI pipeline (Phase A + publishing) | ≈97% | parallelised with selective retry and failure forensics; **torch-1.8.8-161 published** (run #698 green whole carrying the exhaustion census + the hunt seat; #697's two one-run reds — DonkeyChest's retry-allowlisted open-window timeout and the RiderSpareFall seat's parallel-load flake — both green in #698, the flake family documented); runs #678–#682's red seats all root-caused same-day from the logs and artifacts — the engine 1:1 at every one |
| Engine infrastructure (§22/§35: ownership, scheduling, transport) | ≈90% | the region model, the cross-owner protocol, the encode-once transport landed and pinned; the B-gaps wait for their triggers per §X.8 |
| Concurrency Phase 5 — parallel pre-gen lighting | 90% (gated) | landed, measured 1.4–1.6×, OFF by default (`torch.parallel-pregen-light`) |
| Concurrency Phase 6 — further parallel gameplay lanes | ≈20% | the random-tick lane landed; each further lane gated on its own evidence |
| Concurrency Phase 7 — heavy-owner optimization lanes | ≈10% | the profiling opener landed; the optimization lanes wait on its evidence |
| Concurrency Phase 8 — adaptive region balancing | 0% | not started (deliberately last) |
| Bukkit/Spigot/Paper adapter (§28.7) | ≈10% | first slice (plugin lifecycle + event + scheduler bridges) exists as `zamin-bukkit`, unwired; deferred by §27.1's order until 1.8.8 is polished |
| Phase 0 standing practice — per-path mutable-access audit | continuous | runs alongside every mechanics slice |

[Progress history](WORKLOG.md)

<sub>Every row's estimate is measured against its own ledger — the wave/section named in the row — not a feeling; percentages move as work lands and a row leaves the table at 100%. The landed record behind these numbers lives in [`Torch-Arcetecture.txt`](Torch-Arcetecture.txt) §22/§29, [`VANILLA_1_8_8_COMPATIBILITY.md`](VANILLA_1_8_8_COMPATIBILITY.md), [`NATIVE_API_CAPABILITIES.md`](NATIVE_API_CAPABILITIES.md) and [`WORKLOG.md`](WORKLOG.md). Last full re-verification of the table: 2026-10-31 (the FL4-F10 redstone world-binding wave + the parity PR wave #101/#102/#104); latest landing: 2026-11-10 (WAVE EK — the mob bodies' equipment-enchant consults: the armor/effect/enchant walk over every mob wound (the Protection EPF fold, the Resistance halving, the setBypassesArmor family threaded through MobEntity.hurt's envelope), the fire-resistance refusal, the melee enchant riders at both victim sites (Sharpness/Smite/Bane on the swing, the Knockback impulse along the attacker's yaw with the 60% recoil, the Fire Aspect ignite — the five flat-override kinds excluded), the player's own takeDamage shape at the mob-melee site (the hurt window + THE MELEE KNOCKBACK the walk never carried), the zombie's burning ignite, the skeleton's bow riders (POWER/PUNCH/FLAME + the wither variant's own fire), the attack-type consult keyed on the held bow (updateAttackType's own law), the fall's Feather Falling fold, the blast impulse's Blast Protection shave, and the anvil import table's stone trio (a pre-existing red root-caused from the OV wave); 14 new seats, 12 RED-verified on their no-op patches); the prior: 2026-11-10 (WAVES SA + SA-2 — the structure-placement alignments: every stale Slice-12-era biome envelope retired (the stronghold's valid set is the reference's whole baseHeight > 0 land family, the monument's window the five-row water family, the temple's gate the scattered family whole with the hills pair + the swampland, the village's the savanna row) + THE WITCH HUT PORTED WHOLE (the reference's lines 806-985 verbatim — the spruce shell, the log stilts, the roof ring with the OAK-rolls-SPRUCE-places quirk, the cauldron, the potted mushroom, the witch's clip-gated seat with her whole spawn machinery), and THE NEGATIVE-COORDINATE CACHE COLLAPSE HEALED — the biome and height-map caches' bare-cast keys smeared the sign extension so every negative-z chunk collapsed onto one key per x-band (the owner's all-negative-coordinates world reading a consult-order smear of a few cached arrays — the deepest root of the 'biomes, terrain, structures all sucks' report); the masked packing makes the whole consult surface a pure function of the seed, both standing world-tree reds healed, the coherence seat re-pinned to the true law (ocean 19/20) and the purity seat added (the regression guard); 12 new seats, 8 RED-verified on their no-op patches); the prior: 2026-11-09 (WAVES TN + CV — the terrain's own root: the vanilla 4-noise density stack ported whole (one Random(seed) stream, seven noises in the reference's construction order, the 5x5 biome kernel, the 33-step density walk, the trilinear fill — the overhang-bearing world the retired fbm column model could never place), the reference's own surface column walk with the chunk random (the k roll, the 0..4 bedrock band, the sandstone extension), the [j + i*16] mirrored surface consults, the sea level at the reference's 63, the shared perlinNoise3 depth consult, and the carver pair whole (the 17x17 region walk, the worm-and-room caves with the water suppression + the lava floor + the sandstone/grass fixes, the 1-in-50 ravine with its pinched-waist profile) — the isCave fbm stand-in and the whole value-noise tail retired; 14 new seats, the RED trio (overhang, bedrock band, mirror) + both carver unit seats RED-verified on their no-op patches).</sub>

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
