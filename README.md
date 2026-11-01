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
| 1.8.8 vanilla parity (the mechanics ledger) | ~93% | ~2,960 automated seats green; the standing parity audit's queue is down to single-digit named rows (the F-EX/VT series — each fixed within a pass or two of its discovery); **2026-11-01 (later): the vine family's wire rows repaired (the production login crash — the anvil import's vine_south/vine_west_east hit the serializer's loud fail; the crash CLASS closed by the whole-registry wire net, every registered block now pinned to resolve a legacy id)**; the bed's OCCUPIED nibble band imported (PR #115, VT-011); the residual is the long tail of the audit, not any known major system  **The floor-consult family landed 2026-11-01 (F-SP2/SP3/SP4 + VT-020, commit 8fe0e233): every floor-mounted family now reads the reference's own law — the wire (hasSolidTop OR glowstone), the diodes (hasSolidTop alone), the plates (hasSolidTop OR the seven-fence census), the rails (floor + ascending headwalls), the door, the fire (the translucent fallout retired), the tripwire SUSPENDED flag, the spawn floor (World.hasSolidTop whole), the plain-torch walk, and the redstone torch's onAdded support-first arm — the six stale-based towers rebased onto the #139 home with their seats salvaged and #121's stronger form carried; the verified merges #116 (the grass shade's two conjuncts), #144 (the repeater's phantom click), #134/#135/#136 (the acting-world sound stamps)** |
| Dimension ownership (the FL4 waves — every system ranging its own world) | ~97% | FL4-F2..F10 landed (fire, TNT, block updates, falling bodies, item/orb physics, entity crossings, the redstone world binding — per-dimension signal systems, routed use consults, dimension-filtered probes); **Gap 2's Slice A landed 2026-11-01: the chest family's per-dimension managers (chestsFor), the ZCD v3 dimension stamp with the v1/v2 migration, the routed window/generation/structure/break/tile-NBT consults, the session's openContainerDimension stamp, the per-family comparator wake — 4 acceptance seats green**; **Slice B landed the same day: the CONTAINER REMAINDER whole (furnace/dispenser/brewing/hopper at the chestsFor form — each dimension's own family, redstone, and cross-family pulls; the ZFD v3 / ZDD v2 / ZBD v2 / ZHD v2 stamps; the dimension-gated item suck, ocelot lock, and cart scans; the dispenser's every emit stamped to its own world; the per-world ContainerViews) — 5 acceptance seats green, and every family's break spill now lands in its own world (Slice A's honest chest tail closed with it)**; **the door/bed pair-death walks joined the block-broken listener's acting-world seat (PR #106, VT-009 — the nether break no longer orphans its sibling or corrupts the overworld cell at the same coordinates)**; **Slice C landed the same day: the TILE FAMILIES whole (sign/jukebox/note-block/banner/beacon/skull/command-block at the chestsFor form — each dimension's own family and world; the ZSD v3 / ZJD v2 / ZND v2 / ZBD v2 / ZBT v2 / ZCB v3 stamps; the beacon's effect spread gated on its own world's players; the protocol's chunk replays per the observer's dimension, the sign/beacon dimension==0 gates retired and the skull/banner/command ungated leaks closed; the command-block redstone run per dimension — the nether's blocks now run on nether power; the restart seat caught the command-block sweep's missing chunk-loaded guard, a real engine defect) — 4 acceptance seats green**; **Slice D landed two of three families the same day (commit ac420ca2): the PISTON family at the chestsFor form (each dimension's own system, world, redstone, and power — a nether piston now runs on nether power where before it never ran at all; the push surface's every body-scan gated on the body's own dimension — the named cross-world push leak closed) — 3 acceptance seats green — and the DECORATION family (the three systems sharing one wire-id allocator, the ZED v2 dimension stamp with the v1 migration, every consult routed by its traced source, the comparator frame readers per dimension — the FL4-F10 named leak paid — the adapter's six wire arms observer-gated, the per-world census views with the stands filtered) — 2 acceptance seats green including the two-tile restart through a real reboot**; **the LEAD family landed the same day (commit 5a53d2fd): every dimension owns its own leash family (leadsFor) — the tie scan gated on the body's own dimension, the survival poll reading the knot's OWN world, the detacher's owner-context assert at the mob's world, the stamped drop, the observer-gated knot wire — 3 acceptance seats green; ROW 4 WHOLE, Slice D complete** |
| CI pipeline (Phase A + publishing) | ~97% | parallelisation + selective retry + failure forensics landed; the publish path's two blockers (the test freeze, the notes 422) fixed; every known failing member root-caused and retired at the source (runs 565/569/571/573/577, then **580/581/582 — thirteen seat defects now, each with the engine verified 1:1**: run 581's fire-arm seat carried TWO stacked hazards (the 1-in-3 lottery tail + the bare-floor flame extinction racing the late census), run 582's spreadplayers bounds were tighter than the command's own contract (the clamp-edge floor+0.5 landing), run 580's horse seat rode the wild horse's reach-drift lottery, and run 582's frozen pig was a BOOT-SEATED DAWN ZOMBIE eating the stationary test player (the probe run caught it red-handed — the eaten player's dead body then froze the pig's stroll window and no-oped every swing)); **runs 583/586's members retired the same day (fifteen in total): the bed seat's rule-off guard now SWEEPS the boot window's own seated hostiles (the mob seat's own guard shape — a boot-seated zombie inside the sleep gate's refusal box starves the sleep-walk await), and the stats seat's dig now names its seed's landed precondition (the committed-await reads "air" — the same answer an unseeded cell gives, a trivial-pass window)**; the vine production crash repaired the same day (see the parity row); **run 588's two members retired (seventeen seat defects in total, both on the baseline): the command-block break seat rode the orphan sweep's missing chunk-loaded guard (a restored tile on a not-yet-loaded chunk read as air and was silently discarded — the sign family's own guard law, now the command-block family's too, caught red-handed by Slice C's restart seat), and the ender-exit health pin was the probe-caught IN_WALL suffocation (a damage-event hook on a replicating probe caught it red-handed: amount=1.0 cause=IN_WALL at the fountain cell — the seat's hand-written fountain never cleared the column above the portal, and the randomized island surface blocks the standing body's eye cell ~1 run in 12; the engine's own dragon-death fountain clears the whole disc's air per the reference's createPortal law — the seat now clears the column too)**; **run 589's bed-guard member retired the same day (eighteen in total): the sleep-walk await starved AGAIN despite the boot sweep — the probe caught it red-handed: the sleeping flag is a ~1-tick TRANSIENT on the single-player contract (the morning jump fires within ticks and wakes the body, the complete cycle landing inside the watch window ~2 rounds in 8; the seat now observes the sleep's own PERSISTENT signature, the bed spawn, instead of the transient flag — the engine 1:1 throughout, the observation was the defect)**; **run 590's give-command member retired (the index-based get(0) race — the drop now picked by its own shape, the SurvivalFlow family's documented law)**; **run 591's attribution NPE root-caused as an ENGINE defect and retired at the source (the nineteenth member): the all() accessors' off-thread snapshot race — List.copyOf over a plain ArrayList handed List.of the nulled tail inside a concurrent removal's window; both entity registries now copy-on-write (the PlayerRegistry precedent), the tick loops' removal arms on the remove(mob, reason) law; TWO MISSED ARMS (the creeper's explosion, the endermite's expiry) caught by the local blast lane the same day and fixed in 688e4f70**; **run 593's dense-owner member retired (the twentieth, a seat defect): the superlinear ceiling pin's shared-runner escalation — the growth is a printed evidence line now (the test's own §22 discipline), the re-measure takes the min of its windows, the floor pins and the equivalence seats hold the walk's law**; run 591's SurvivalFlow drop-settle miss stays the open question (the full static analysis eliminated every removal and id-space candidate — the engine ran at nominal 20 TPS through the whole 400-tick await on a fresh flat world; the instrumented sampler probe is the next session's first act) ; **run 603's five standing failures root-caused and retired (the #139 merge's missed lever-on-piston benches — PistonBaseBlock.isCube() false means a lever never rides a piston base; the four bench files moved to the vanilla-valid stone-above mount) — develop green again, the next CI run is the confirmation; run 603's two other members (the Hunger/Pearl and NetherBed one-offs) pass locally, the loaded-runner environmental class** |
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
