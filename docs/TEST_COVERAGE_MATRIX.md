# Test Coverage and Verification Matrix

Cycle-4 exhaustive-audit deliverable. Maps requirement families to the
tests that establish them, distinguishes coverage states, and names the
missing evidence. Test census (mechanical, this cycle): **440 test classes
/ 2533 @Test methods** (core 370, protocol 47, launcher 9, bukkit 10,
api 4).

## The suite's shape

| Module | Classes | Established by (representative, per compat ledger + §29) |
|---|---|---|
| zamin-core | 370 | the per-slice behavioral tests every §29 wave cites (reference-shadow sweeps, formula pins, round trips) |
| zamin-protocol-v1_8_8 | 47 | wire conformance (Protocol18ConformanceTest, VanillaParityIntegrationTest, TestClient18) |
| zamin-launcher | 47 tests / 9 classes | console contract (ADR-21 list) |
| zamin-bukkit | 10 (52 tests) | the honest-shim event bridges (deferred adapter's test-first core) |
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
| Chest slot-27 rejection | **unverified** | ML-004: needs rejection-reason instrumentation first (no FINE log exists) |
| RCON drain under load | **flaky member** | RconProtocolTest:333-340 5s FIN-WAIT loop — ML-002 member |
| Fishing bite windows under load | **flaky members** | two methods — hardened on PR #37; population audit pending |
| Item registry id-by-id | **unverified** | R-2: no census test pinning the full 1.8.8 item table |
| Worldgen end-to-end determinism | **unverified** | R-5: slices are 1:1 but no same-seed region-level byte-compare exists |
| Malformed-packet fuzz | **missing** | R-7: no fuzz/boundary suite in the tree |
| Login flood | **missing** | ML-014: no test bounding concurrent login cost |
| Per-command semantics | **partial** | R-6: individual commands tested (op/tp/give…), no systematic diff vs the vanilla set |
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
nether/end portal travel (untested in cycle 3), world-border edges,
malformed-input resilience, or the R-1..R-8 remainder. Those limits are
inherited from cycle 3's playtest record and this cycle's census.
