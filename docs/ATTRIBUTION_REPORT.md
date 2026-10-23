# ML-007 — the performance attribution report

The §22 evidence event this report exists for: cycle 3 measured the TPS
observations (idle 20.0, +4 walking bots 20.0, +100-zombie horde 18.0,
+respawn-kill storm composite 12.0) with NO per-subsystem attribution —
"the bottleneck attribution (entity AI vs chunk re-stream vs respawn walk)
is unknown until B-7's section table exists" (cycle-3 finding B-7 / the
master ledger's ML-006). ML-006 landed the section table this cycle; this
report is the attribution pass ML-007 owed over it. The gate verdict at the
end is a human decision over the printed evidence, per §22's discipline —
never a CI assertion.

## The capture methodology

- **Harness**: `AttributionLoadTest` (the zamin-protocol-v1_8_8 test tree —
  the real-wire boot; a headless session cannot see the re-stream because
  the wire writers short-circuit on a dead channel). Two scripted 1.8.8
  clients (TestClient18) on loopback with socket-drain threads (a real
  client reads its socket; without the drain the OS buffers fill and the
  write path stops being honest). Java 21, the Gradle test worker JVM,
  `--max-workers=1`.
- **The load shapes are cycle-3's, reproduced in-process**: the
  100-zombie horde around the standing players; the respawn-kill storm at
  the observed ~3 Hz (one `damage`+`performRespawn` cycle every ~333 ms —
  the horde killing the standing player). The storm's re-stream is by
  construction, not by a distance trick: `V18Connection`'s `respawnAt`
  does `sent.clear()` + `sendInitial(center)` — EVERY respawn re-sends the
  FULL view-distance chunk set to the respawned player, wherever they
  stand (code-level ground for cycle-3's "full chunk re-stream per cycle",
  finding B-6).
- **The measurement face**: `EngineProfiler.sectionNanosSnapshot()` window
  deltas (the absolute, undecayed face — the same discipline as the §22
  Phase-7 dense-owner harness), whole-root-fan (the storm's cost may land
  outside the world's tick section), per window: the wall clock and true
  TPS, GC counts+time from the MXBeans, heap occupancy, and a whole-run
  JFR `ObjectAllocationSample` capture aggregated by class (the size field
  is introspected — JDK 21 names it `weight`).
- **Windows**: W0 baseline (night, 2 players, no horde) → W1 horde (100
  direct-spawned zombies + the natural census, 40-tick settle excluded
  from the measurement) → W1b relief (post-clear) → W2 storm (17 cycles,
  no direct horde — the natural census rode along, recorded below) → W2b
  post-storm relief → W3 composite (horde + storm, cycle-3's worst case).
- **Keep-alives were disabled for the run** (the adapter's interval set to
  10M ms) — the scripted clients' keep-alive cost is not part of the
  measured loads; recorded here, never silently dropped.
- The full per-window tables are in `ATTRIBUTION_RUN_EVIDENCE.txt` beside
  this report (the raw console capture, every section, every window).

## The run's own findings (recorded honestly, never engineered around)

1. **An above-ground zombie horde in daylight is not a load, it is a
   burnout.** The first run's world stood at dawn; the historical sunlight
   burn consumed all 100 bodies inside the W1 window and the mobs section
   read the animal baseline. The harness runs the whole pass at night
   (`/time set night`); the burn is off and the natural cycle's
   ambient-darkness gate is open.
2. **The join placement is seed-dependent.** The second run anchored the
   grid on world spawn, which stood outside the players' 32-block
   no-despawn ring — the despawn walk removed the whole horde inside the
   settle (zero at window open, no drops anywhere). The harness anchors
   everything on Alice's actual standing spot, with her bed-spawn pinned
   there so every storm cycle respawns her in place.
3. **The natural census re-fills.** At night the natural spawn cycle
   rebuilds its hostile census (~120 bodies around the players) within
   seconds of any clear. W1b and W2 therefore carried ~121 hostile strays
   — the titles record the true census; the W1-vs-W2 "horde marginal"
   comparison is read with that in mind (both windows carry >100 hostiles;
   the W1b relief window, whose census state matches, is the honest
   post-horde reference).
4. **The horde does not chase the standing players.** The engagement
   record (five sampled zombies' drift over the W1 window): 0.9-2.1
   blocks — the shared stroll walk, not a hunt. The cycle-3 "pathfinding
   under load" was the same stroll family. A hunting horde would cost the
   tick more; that state is reachable through provocation, and this
   harness does not claim to have measured it.

## The attribution (per-window, us/tick)

| Section | W0 base | W1 horde (122 hostiles) | W1b relief (121) | W2 storm (121 + 17 cycles) | W2b relief | W3 composite |
|---|---|---|---|---|---|---|
| `levels.world.tick.randomTicks` | 3683 | 3651 | 3921 | 3714 | 3732 | 3638 |
| `levels.world.tick.mobs` | 715 | 2989 | 2425 | 3834 | 4152 | 2919 |
| `levels.world.tick.naturalSpawns` | 902 | 373 | 327 | 349 | 338 | 399 |
| `levels.world.tick.playerBodies` | 83 | 42 | 34 | 28 | 42 | 61 |
| `wirePublish` (ML-024 re-run, 2026-10-23) | — | — | — | **5344.6** | — | — |
| everything else (combined) | ~363 | ~395 | ~300 | ~330 | ~330 | ~360 |
| **whole-fan total** | **5748** | **7434** | **6988** | **8277** | **8531** | **7190** |
| window TPS | 20.2 | 20.1 | 19.9 | 20.0 | 19.9 | 19.9 |
| GC (per window) | +10 col, +14 ms | +18, +27 ms | +12, +23 ms | +37, +54 ms | +12, +18 ms | +37, +59 ms |

The `wirePublish` row is from the ML-024 re-run (below) — the original
capture predates the section, so its W2 column carries the row's cost
inside nothing (the walk ran outside every measured section). The
original W2 row set is kept unchanged above; the re-run's own W2 fan
sits in the evidence file's ML-024 section.

The GC/heap and per-window totals across all six windows are in the
evidence file; the allocation capture (whole run, JFR
`ObjectAllocationSample`): **7.74 GB sampled across 99 classes** —
`byte[]` 4.15 GB and `java.lang.String` 2.72 GB dominate; the wire path
shows directly (`net.zaminmc.torch.protocol.v1_8.ChunkSerializer18`
lambda 19.2 MB, `java.nio.DirectByteBuffer` 13.0 MB,
`io.netty.channel.DefaultChannelPromise` 9.8 MB), as does the physics
family (`Aabb` 25.6 MB) and `BlockPosition` 75.2 MB.

### The findings the numbers carry

1. **`randomTicks` is the top subsystem in every window** — 3.6-3.9
   ms/tick, load-INDEPENDENT (a fresh world with no crops; the walk's cost
   does not vary with the horde, the storm, or the census). At ~65% of
   the W0 total it dwarfs every gameplay system under these loads.
2. **`mobs` scales with the hostile census** — 715 us/tick at the animal
   baseline (W0, ~46 worldgen bodies), 2.4-4.2 ms/tick at the 120-body
   hostile census (W1/W1b/W2/W2b/W3), i.e. ~20-34 us per hostile body per
   tick at these densities (the same per-body band the Phase-7
   dense-owner harness measured for pigs; no superlinear signature at
   120 bodies).
3. **The composite does not reproduce cycle-3's 12 TPS in-process.** The
   whole-fan total under the composite is 7.2 ms/tick against the 50 ms
   tick budget — the engine holds 19.9-20.0 TPS through every window. The
   cycle-3 12.0 reading came from the release jar under real-bot load
   (clients sending positions every tick, keep-alive round-trips, and the
   respawn ACK exchange) — the in-process harness cannot and does not
   claim to reproduce that environment; the honest reading is that the
   single gameplay lane is NOT saturated by the composite's tick-lane
   work, and the cycle-3 saturation lived in the wire-side/bot-side
   envelope this harness deliberately excludes (keep-alives disabled,
   scripted clients).
4. **The re-stream's serialization had no dedicated profiler section.**
   The storm window (17 full re-streams of the view-distance set) shows no
   new section and no single-section jump beyond the census's own variance
   band: the chunk serialization runs inside the publish walk, which no
   `profiled()` section covers, and the JFR byte[]/String dominance is
   consistent with the serialization buffers plus the profiler's own
   per-section string churn (each push/pop concatenates section-name
   strings — the profiler's overhead while profiling is part of the
   measured run, recorded here). A dedicated wire-publish section would
   make the next attribution pass exact; proposed as a master-ledger row.
   **RESOLVED as ML-024 (2026-10-23): the section landed and the re-run
   (next section) reads the re-stream's cost exactly — 5.3 ms/tick over
   the storm window, the window's second-largest row.**
5. **The §22 gate verdict over this evidence: NO concurrency phase is
   justified.** The measured top costs are `randomTicks` (load-independent
   — a §22 Phase-5 lighting/parallel-random-tick candidate at best, and
   ONLY after the attribution above is re-run with the re-stream section
   in place) and `mobs` at ~20-34 us/body — both far inside the 50 ms
   budget at the census densities a single-container server can hold. The
   §22 discipline holds the line: no Phase 5/6/7/8 wiring starts on this
   evidence; the next attribution pass re-runs this harness (it is
   committed, repeatable, and green) whenever a load shape changes.

## The ML-024 re-run (2026-10-23): the re-stream's own row

The wire-publish section landed exactly as the ledger directed: one row
(`root.wirePublish`) around the whole chunk serialization+publication
walk (`ChunkSerializer18.serialize`, the 0x21 build, the flush, the
sign/spawner/beacon/skull/command tile replays), recorded through the
profiler's new cross-thread span arm rather than a nested push/pop —
the walk runs on three lanes (the netty event loop's join/movement
streams, the tick thread's pre-handler job drain where the respawn
re-stream lands — OUTSIDE the root section's window, which is why the
original pass could not see it, and the tick thread's relightTransport
section), and the push/pop stack's single-caller contract excludes the
netty lane. All lanes accumulate into the one row; where the walk runs
inside an open section (the relight re-sends) the row is a cross-cut —
overlap, never a partition.

The re-run's numbers (same harness, same machine, twice):

- **W2 storm window: `wirePublish` 4536.0 then 5344.6 us/tick** — the
  window's second-largest row behind `randomTicks`, present in NO other
  window (the scripted clients stand still; no re-streams occur
  outside the storm).
- **The /debug stop table's accounting is exact**: 1377 visits = 17
  cycles x 81 view-distance columns, 0.50 ms average per column,
  681.65 ms total, 13.49% of root over the whole profiled span — every
  re-streamed column recorded exactly once, no double-count, no miss.
- Per cycle: ~32 ms of serialization+publication for one full
  view re-stream at view-distance 4 (81 columns, ~0.4-0.5 ms each on
  the flat test world).
- The row is invisible to the ORIGINAL capture's W2 column by
  construction (the section did not exist); the original numbers above
  stand unchanged.

Recorded honestly, two run-variance findings from this machine (a base
control — the same harness on the unmodified tree — reproduces BOTH,
so neither is the section's effect): the W3 composite window caught 0
storm cycles in every this-machine run (the storm's stop can leave
Alice mid-death and the next driver's `!victim.dead()` gate then never
fires — a pre-existing harness race; the original release-runner
capture caught 17 cycles there), and this machine's JFR sample totals
run ~27 GB with `[J`/`[Ljava.lang.Object;` dominant versus the original
runner's 9.9 GB with `byte[]` dominant — same harness, different
machine profile. The section's own allocation footprint is
indistinguishable (27.2 GB with vs 27.2 GB without).

## What this closes

- ML-007's acceptance: "an attribution report naming the top subsystems
  under the horde + respawn-storm loads, with the capture methodology
  recorded" — the top subsystems are named with numbers and the
  methodology is fully recorded above.
- ML-006's acceptance was the section table this pass ran on; the two
  rows close together, as the ledger sequenced them.
- ML-024's acceptance (2026-10-23): "the section visible in
  sectionNanosSnapshot; the harness re-run shows the re-stream's cost in
  its own row" — `root.wirePublish` appears in the snapshot face
  (WirePublishSectionTest pins it green, 3/3) and the re-run's W2 storm
  window carries the row at 4.5-5.3 ms/tick with exact visit accounting
  (1377 = 17 x 81).
