# Compatibility Matrix — targets, scope, and implementation status

Cycle-4 exhaustive-audit deliverable. One row per target from the Phase-1
contract (see `CYCLE4_EXHAUSTIVE_AUDIT.md`); "owed?" is the applicability
verdict derived from the architecture decisions, not from upstream
marketing.

| Target | Owed? | Scope commitment | Torch implementation status | Ledger |
|---|---|---|---|---|
| Vanilla 1.8.8 | YES — primary | §1-B + §17 coverage list; observable behavior, quirks preserved, no modern mechanics | Deep: 2771-declared-test suite (505 classes, the 2026-10-28 census), §29 Waves A-X + ON-1 + T-1..T-4, census-level VERIFIED-EQ on entity/block/effect/packet inventories (see parity matrix — S2C now 77/77 with every writer wired); the former open wire gaps (0x36/0x20/0x0A) and the sleep/spectator partials are ALL RESOLVED (PRs #39-#44, #50); remaining: R-8's 25 family tails, the ML-004 seven-arm confirm-transaction divergence, the wither exemption arms (README pending row) | ML-003/005/008/010/011/012/018/100, ML-004 (dispositioned) |
| Bukkit/CraftBukkit | DEFERRED | §27.1 order (after 1.8.8 polish); §28.7 adapter contract; §27.9 inventory is the future checklist | `zamin-bukkit` exists: honest shims + event bridges, 10 classes/52 tests, NOT runtime-activated (owner's row 24 order); prerequisites inventoried (§27.9's list = the adapter-era backlog); activation is an owner decision | DEC-002 |
| Spigot | DEFERRED (with Bukkit) | §27.9 treats Bukkit/Spigot/Paper as one later milestone | No Spigot-specific surface owed today; none implemented | — |
| Paper | DEFERRED (with Bukkit) | §27.9 | Paper-informed OWNED subsystems already landed by decision: §30 console (ADR-21), §31 storage-boundary items; these are Torch-native design informed by Paper, not Paper-compat commitments | — |
| Folia 26.2.x (supplied tree #1) | NO — lessons only | §0 rule 9 + §5 forensic mandate | Forensic audit complete (path level, prior cycle) + per-patch accounting complete (24/24 files, mechanically re-verified 2026-10-28: 460/460 hunk paths): 10 different-by-design, 9 N/A, 0 missing (the watchdog landed ML-013), 0 unverified (ML-014/015/016 all resolved); no Folia behavior owed | ML-013..016 (all RESOLVED); FOLIA_PATCH_ACCOUNTING.md |
| KigPaper 1.8.8 (supplied fork #2 — the patched 1.8.8 server) | NO by default — reference-1.8.8 wins; fork knobs = DEC-003 | §0.7/§17: reference is the authority; KigPaper's gameplay alterations are config-gated (its README); bug-fix patches = recorded divergences | Audit COMPLETE (increment 3): identity+lineage+layer model from source; 533 enumerated (census committed); nms 8/8 full-read; API stacks closed as deferred layer (107, DEC-002); 418 server-stack patches dispositioned (130 knob/171 bugfix-div/70 api/24 perf/23 infra) with the two-sided fluids spot-check passed; ML-022 closed (registry inert). No Torch debt created: knobs=DEC-003, bugfix-divs=reference-wins, api=DEC-002, perf=ML-007-gated | DEC-003; ML-022/023; KIGPAPER_PATCH_ACCOUNTING.md |
| Torch-native API | YES — co-equal | §27.3 complete exposure, §28 plugin contract, Special APIs | `zamin-api` families + NATIVE_API_CAPABILITIES.md ledger; §29 Wave IX exposure audit landed; the audit's fresh native rows: watchdog (ML-013), /debug table (ML-006), resource pack (ML-017), login-bound verification (ML-014) | ML-006/007/013/017 |
| Intentional differences | recorded, verified | ADR-0001 (Minestom closed); §28.7 deferral (DEC-002 decided 2026-10-26: stay native indefinitely); Phase 5-8 gates; ledger "known differences" fields; offline-mode scope (DEC-001 decided 2026-10-26 and LANDED as Wave ON-1: online-mode whole — offline stays as the online-mode=false branch); /give form (ML-008 decision); 0x22/0x26 batching (ML-019) | Each documented with its decision; none classified as defects | DEC-001/002 |

Cross-layer note (addendum §7): the former fresh wire gaps
(0x36/0x20/0x0A/0x41) were single-root-cause items whose symptoms span V
(vanilla behavior) and P (protocol) layers — one ledger row each, all now
RESOLVED (2026-10-23/24, PRs #39-#44). The release blocker (ML-001) spans
I (infra) and test-infra (ML-002). No item was split into per-layer
duplicates. This note is retained as the historical cycle-4 record; the
live gap register is the master ledger.
