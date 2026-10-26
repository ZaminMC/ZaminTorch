# Test Coverage and Verification Matrix

Cycle-4 exhaustive-audit deliverable. Maps requirement families to the
tests that establish them, distinguishes coverage states, and names the
missing evidence. Test census (mechanical recount 2026-10-26, post
Waves ML13/TF/TF-7/ON-1 — classes containing @Test methods): **495
test classes / 2718 @Test methods** (core 402/2381, protocol 70/213,
launcher 10/53, bukkit 9/52, api 4/19).

## The suite's shape

| Module | Classes | Established by (representative, per compat ledger + §29) |
|---|---|---|
| zamin-core | 402 | the per-slice behavioral tests every §29 wave cites (reference-shadow sweeps, formula pins, round trips) |
| zamin-protocol-v1_8_8 | 70 | wire conformance (Protocol18ConformanceTest, VanillaParityIntegrationTest, TestClient18) + the Wave ON-1 login families (LoginCompressionWireTest 5, CompressionCodecTest 8, TorchEncryptionTest 8, EncryptionCodecTest 3, SessionProfileParserTest 11, MojangSessionServiceTest 5, OnlineLoginWireTest 6) |
| zamin-launcher | 10 (53 tests) | console contract (ADR-21 list) |
| zamin-bukkit | 9 (52 tests) | the honest-shim event bridges (deferred adapter's test-first core) |
| zamin-api | 4 | the §27.10 capability tests (no-cast law) |

CI: `release.yml` runs build + full suite with ONE retry whose documented
flake list names LifecycleEvent/HopperSystem/HopperWindow — the 2026-10-22
runs (#361/#362) tripped members OUTSIDE that list (fishing ×2 methods,
bukkit bridge, rcon FIN-WAIT), which is the evidence behind ML-002.

## Requirement → evidence map (gaps only; established rows live in the ledgers)

| Requirement family | Coverage state | Evidence / missing |
|---|---|---|
| Release gate reliability | **tested-but-flaky** | ML-002: the wall-clock-deadline population; census not yet run; two members hardened on PR #37 (CI-unverified — no branch runs) |
| 0x0C Named Spawn metadata encoding | **untested** (no pinned wire test) | ML-003 (cycle-3 lead): the DataWatcher form for every watched type needs a pinned protocol test; suspected wire defect, bytes never captured |
| 0x36 sign editor open | **gap + untested** | ML-003 row: no writer, no test |
| 0x20 entity properties | **gap + untested** | ML-011: no writer, no test |
| 0x0A use bed / multiplayer sleep | **partial + partial test** | SleepAcceptanceTest covers the single-player contract only (test methods verified this cycle) |
| 0x41 difficulty live-sync | **gap + untested** | ML-012 |
| Chest slot-27 rejection | **unverified** (repro open) | ML-004's instrumentation LANDED: every refused window click logs its wire context + the named gate at FINE (unknown-window / creative-only-gesture / slot-out-of-range / stale-chest / mode-out-of-range / outside-click-no-cursor / chest-slot-mode-gate / chest-click-refused / unclassified), pinned by WindowClickRejectionInstrumentationTest 4/0 — the next state-dependent repro diagnoses itself |
| RCON drain under load | **flaky member** | RconProtocolTest:333-340 5s FIN-WAIT loop — ML-002 member |
| Fishing bite windows under load | **flaky members** | two methods — hardened on PR #37; population audit pending |
| Item registry id-by-id | **pinned this cycle** | R-2 resolved: ItemRegistryParityTest 4/0 — the full table, the 17-id no-engine-item set, the dual spellings, the stack sizes; 11 wire-id defects + 16 missing rows + the bucket stack repaired first |
| Worldgen end-to-end determinism | **pinned this cycle** | R-5 resolved: WorldgenDeterminismRunTest 1/0 (4 runs) — two engines, same derived seed, 25 columns byte-identical (serialized columns); region-level inapplicable by design (region files import-read-only; native save = delta pipeline) |
| Malformed-packet fuzz | **pinned this cycle** | R-7 resolved: MalformedPacketFuzzWireTest 3/0 — 24-case deterministic corpus + seeded tail (Random(474747L)) + the 21-bit frame boundary (close vs wait) |
| Login flood | **pinned (wave 3)** | ML-014 resolved: LoginFloodBoundaryTest — 100 pending at zero tick-lane cost, ReadTimeoutHandler(30) reap |
| Online-mode login arm (encryption, consult, exchange, textures) | **pinned (Wave ON-1)** | ML-020/DEC-001 executed: TorchEncryptionTest 8, EncryptionCodecTest 3, SessionProfileParserTest 11, MojangSessionServiceTest 5 (the stub HTTP server's three arms), LoginCompressionWireTest 5, CompressionCodecTest 8, OnlineLoginWireTest 6 (the full encrypted login over real sockets + the tab-list textures lane), TabListServiceTest 6 / TabListIntegrationTest 3 (the join seam + the offline count-0 law) |
| Per-command semantics | **surface-pinned this cycle** | R-6 resolved at the surface level: RegisteredCommandSurfaceParityTest 2/0 — the exact 74-verb set + every level vs the reference walk's 62 names; effect-level semantics stay family-owned |
| Ownership/concurrency invariants | **tested** | the §22 Phase 1-2 records (eight ownership tests + lane gates) — standing green |
| The §28.8 adversarial plugin set | **deferred tests** | required before any plugin-safety claim; blocked on §28.7-era work (not started by design) |

## Cycle-3 lead disposition in test terms (addendum §4 compliance)

- Bukkit MONITOR assert: **defective synchronization strategy** (asserted a
  flag instead of awaiting the dispatch) — hardened on PR #37 to await the
  observer. Isolated-green verified locally; CI proof pending (ML-001).
- Fishing awaits: **wall-clock deadlines vs tick cadence** — hardened with
  bite-window retries; the population census remains open (ML-002).
- RCON FIN-WAIT: **unjustified wall-clock deadline** — not yet hardened;
  ML-002 member.
- None of the three has been shown to be a PRODUCT defect; equally, no
  retry-passing is accepted here as correctness proof — the census must
  classify each deadline (test-side vs product-side) on evidence.

## What a green suite establishes (honest scope)

A green full suite establishes the 2533 pinned behaviors under unloaded
conditions plus CI-load robustness of the hardened members. It does NOT
establish: real-Mojang-client UX, multiplayer latency beyond loopback,
nether/end portal travel (untested in cycle 3), world-border edges.
The R-2..R-7 remainder items closed this cycle (malformed-input resilience
included, R-7; R-1's lifecycle walk landed at 0ec68c18 — the boundary
fan, the two-phase order, the density-attenuated wound law, the fx law);
what remains open is R-8 (the
25 family-owned tails), inherited from this cycle's census.
