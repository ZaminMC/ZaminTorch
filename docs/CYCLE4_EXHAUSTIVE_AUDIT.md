# Cycle 4 — The Exhaustive Compatibility and Implementation Gap Audit

Live document for the exhaustive audit cycle (2026-10-23 →). This is an
investigation and planning run only: no production code, tests, patches, or
implementation behavior are changed in this cycle. Deliverables produced by
this audit:

| Artifact | Path | Content |
|---|---|---|
| Audit summary + methodology (this file) | `docs/CYCLE4_EXHAUSTIVE_AUDIT.md` | Ground truth, contract matrix, coverage counts, critical findings, sequence |
| Master implementation ledger | `docs/MASTER_LEDGER.md` | The authoritative ranked backlog, stable IDs, all required fields |
| Vanilla parity matrix | `docs/VANILLA_PARITY_MATRIX.md` | Reference-derived coverage by subsystem and family |
| Patch-accounting index (Folia) | `docs/FOLIA_PATCH_ACCOUNTING.md` | Every supplied-fork patch, lineage, classification, ledger links |
| Patch-accounting index (KigPaper — the supplied patched 1.8.8 server) | `docs/KIGPAPER_PATCH_ACCOUNTING.md` | Increment 2: identity/lineage/layer model + 533-patch census + nms-layer classification + the honest remainder (ML-023) |
| Compatibility matrix | `docs/COMPATIBILITY_MATRIX.md` | Vanilla / Bukkit / Spigot / Paper / fork / Torch-native scope and status |
| Test coverage matrix | `docs/TEST_COVERAGE_MATRIX.md` | Requirements mapped to existing tests, missing evidence |

These artifacts are authoritative for the *next implementation queue*. They do
not replace the historical records: `Torch-Arcetecture.txt` §29 remains the
landed-wave ledger, `VANILLA_1_8_8_COMPATIBILITY.md` the per-mechanic record,
`NATIVE_API_CAPABILITIES.md` the API capability record, and the README Status
table the pending-only TODO list. Nothing already landed is reopened here
without new evidence.

## Phase 0 — Ground truth (verified 2026-10-23, this session)

### Repository state

- Audited baseline: **`develop` @ `48b9157437afa85d5e4a4e81098c08fe53479e39`**
  (working tree clean, synced with `origin/develop`; GitHub develop HEAD
  verified identical via API). Local and remote `Torch-Arcetecture.txt`
  byte-identical at this commit.
- Branch topology at audit start: `develop` (canonical), `main`,
  `cycle3-audit` (PR #37, open), `feature/save-s4-stores` (stale: 87 commits
  behind develop, 0 ahead — fully merged, cleanup is an owner call, not taken
  during this audit run).
- Audit branch: `cycle4-exhaustive-audit`, created from `develop` @
  `48b91574`. All audit artifacts commit here; PR opened against `develop` at
  cycle close. PR #37's branch was NOT modified and NOT merged.

### Module graph (verified from `settings.gradle.kts` + sources)

| Module | Responsibility (from actual code) |
|---|---|
| `zamin-api` | The plugin-facing native API: `io.zamin.torch.api.*` — Server handle families, world/entity/block/inventory views, events, scheduler, Special APIs |
| `zamin-core` | The engine: EngineServer, ownership domains, tick loop, world/chunk/block sim, entities, AI, persistence stores, commands, RCON/Query |
| `zamin-protocol-v1_8_8` | The 1.8.8 (protocol 47) wire codecs, packet registry, compression/encryption framing |
| `zamin-launcher` | Bootstrap, console (JLine 3 / ADR-21), properties loading, main entrypoint |
| `zamin-bukkit` | The §28.7-deferred Bukkit adapter: honest shims + event bridges, 52 tests, NOT activated at runtime (owner-deferred per §27.1 order; row 24) |

### Build / CI / release status (verified via GitHub API this session)

- CI workflows: `release` (runs on develop push: build + full test suite with
  one documented-flake retry) and `Development Statistics (Java LOC)` (green
  at head, run #167).
- **Release pipeline RED at develop head**: run #362 (48b91574) failed at
  step "Build + full test suite (one retry for the documented load flakes)"
  (attempt 1 job data verified via API; run #361 on 228019a2 failed the same
  step). The exact failing tests are documented in
  `docs/CYCLE3_AUDIT.md` (#362: `FishingApiAcceptanceTest` +
  `RconProtocolTest.theEmptyOutputAnswersOneEmptyPacket`; #361:
  `BukkitEventBridgeGroupThreeTest` + `FishingApiAcceptanceTest`).
- **Latest published release: `torch-1.8.8-127`** (2026-10-22T16:37Z,
  2 assets). The `release: torch-1.8.8-128` version-bump commit exists on
  develop, but no 128 release was ever published. Releases 125/126/127 are
  the published series; git tags stop at the old `v0.2.0-dev.*` series
  (modern releases publish without tags).
- **PR #37 (`cycle3-audit`): OPEN, unmerged, mergeable (clean), 5 commits.**
  No CI runs exist for the branch (the release workflow triggers on develop
  pushes only) — its two test hardenings (5b20386e) are therefore
  **CI-unverified**. Its diff: WORKLOG entry, `docs/CYCLE3_AUDIT.md` (new),
  `BukkitEventBridgeGroupThreeTest.java` (+8/−8), `FishingApiAcceptanceTest.java`
  (+94/−33 engine-side untouched).

### The supplied patched-server tree — identity and provenance (established from source)

- Location: `Folia-ver-26.2.x.zip` at the repository root (819 KB), also
  present as `/home/z/my-project/repo/Folia-ver-26.2.x.zip` in the working
  environment. Same archive in both places.
- **Actual identity: PaperMC/Folia, branch `ver/26.2.x`** — established from
  the source itself: README ("Fork of Paper which adds regionised
  multithreading"), `gradle.properties` (`group=dev.folia`, `mcVersion=26.2`,
  `apiVersion=26.2`, `channel=BETA`, `paperRef=5d4f9bd0e4f6b1ef70d07feb411580cef7d1a746`),
  paperweight-patcher build (`io.papermc.paperweight.patcher` 2.0.0-beta.21),
  Java 25 toolchain, and the patch layout below.
- **It is NOT a 1.8.8 server.** `mcVersion=26.2` is a modern Minecraft
  version. The audit prompt's belief that the supplied tree was a "patched
  1.8.8 server source tree" is corrected here on evidence. Consequence for
  the target matrix: Folia 26.2.x patches are audited as (a) the regionised
  threading reference the architecture already studies (§5), (b) Paper-line
  ecosystem behavior insofar as Torch's §27.9 compatibility strategy commits
  to it, and (c) NOT as a 1.8.8 vanilla-behavior authority (the 1.8.8
  authority stays `reference/1.8.8/`).
- **Layer structure (from the patcher config)**: the tree contains only
  Folia's delta. Upstream Paper @ `5d4f9bd0` is pulled by reference
  (`paperRef`); the CraftBukkit/Spigot/Bukkit stacks are folded inside
  upstream Paper's own history and are NOT present in this archive. Patch
  layers present: `folia-api/paper-patches` (4 feature patches over
  Paper-API), `folia-server/paper-patches` (6 over Paper-server),
  `folia-server/minecraft-patches` (11 over the Mojang-mapped vanilla
  server), plus 3 build-file patches and `folia-checkstyle`. Total: **24
  patch files (21 feature patches + 3 build patches)** — matching the
  prior inventory's "21 patches" count (docs/FOLIA_PATCH_INVENTORY.md,
  §22 Phase 0) which counted feature patches.
- The Bukkit/CraftBukkit/Spigot/Paper patch layers below Folia therefore
  **cannot be audited from this archive** — that limitation is recorded
  precisely instead of silently substituting an upstream checkout (the
  audit's own evidence rule). Bukkit-line compatibility scope for Torch
  derives from §27.9's strategy and the deferred §28.7 adapter, not from
  this tree.

### Prior-cycle artifacts verified present

- `docs/CYCLE3_AUDIT.md` (337 lines, on PR #37's branch — read via
  `git show cycle3-audit:docs/CYCLE3_AUDIT.md` during this audit).
- `docs/FOLIA_PATCH_INVENTORY.md` (477 lines: all 21 feature patches read,
  460 modified Java paths inventoried) and `docs/FOLIA_FORENSIC_AUDIT.md`
  (158 lines) — preserved as the path-level record; this cycle adds the
  per-patch Torch classification on top (see `docs/FOLIA_PATCH_ACCOUNTING.md`).
- `VANILLA_1_8_8_COMPATIBILITY.md` (292 lines, 10 sections + ledger policy),
  `NATIVE_API_CAPABILITIES.md` (191 lines), `WORKLOG.md` (13.5k lines,
  newest at bottom), README Status table (7 pending rows — the pending-only
  contract).
- Reference inputs: `reference/1.8.8/` decompile tree (1634 `.java` files,
  zip-identical to the committed tree per §22 Phase 0's earlier
  verification) and `reference/1.8.8/1.8.8 - mechanics.zip`.

### Ground-truth defects recorded (not repaired in this run)

1. Release pipeline RED at develop head (#362), 128 unpublished —
   continuity lead A-1, see §Continuity.
2. `feature/save-s4-stores` stale branch remains on the remote — cosmetic
   debt only (fully merged).
3. The mineflayer playtest harness `scripts/validate-1.8.8.js` assumes an
   opped bot (cycle-3 B-1) — the validator cannot exercise permission-gated
   flows unattended until fixed; preserved as-is this run.

## Continuity — every unresolved cycle-3 finding carried forward

Statuses use the addendum's taxonomy: confirmed defect / confirmed
test-infrastructure defect / reproducible intermittent failure / suspected
defect / unverified behavior / resolved with evidence / no longer
applicable. Each links to its master-ledger ID (ML-*) in
`docs/MASTER_LEDGER.md`.

| Cycle-3 lead | Current state (reverified this session) | Classification | Ledger ID |
|---|---|---|---|
| A-1: full-suite load flakes (bukkit MONITOR assert + fishing bite-window race) — fix landed on PR #37 (5b20386e) | Fix commits present on `cycle3-audit`; PR open, **no CI run for the branch** (release workflow = develop-push only); run #362 at develop head still red with the same step | Confirmed test-infrastructure defect; fix staged but CI-unverified | ML-001 |
| #362 verdict: the wall-clock-deadline test *population* (fishing awaits, RCON FIN-WAIT drain, bukkit harness awaits) trips a different member per loaded run | Generalization stands (three distinct timing tests across runs #361/#362); no further runs since; population audit not yet done | Confirmed test-infrastructure defect (systemic) | ML-002 |
| B-5 (HIGH): 0x0C Named Entity Spawn metadata `PartialReadError` in mineflayer's protodef with four mutual players | No engine change since; not reproduced beyond the cycle-3 observation; wire bytes never captured | Suspected defect (wire-conformance candidate; evidence insufficient to localize — could be a Torch encoding defect OR a mineflayer parser quirk) | ML-021 |
| B-2: chest window slot-27 (wire) transaction rejection, state-dependent, once | Not reproduced deterministically; cycle-3 record preserves the sequence; no rejection-reason instrumentation exists yet | Unverified behavior (bounded diagnostic item) | ML-004 |
| B-3: `/op` refuses offline players (reference ops via GameProfile cache) | Reference walk verified in cycle 3 (`OpCommand.java` lines 30-39); engine unchanged since | Confirmed defect (parity, low-medium) | ML-005 |
| B-7: `/debug stop` prints no per-section table | `EngineProfiler.sectionNanosSnapshot()` exists; the report arm still doesn't print sections (verified by reading the command path this cycle — see ML-006) | Confirmed defect (observability; blocks perf attribution) | ML-006 |
| Release pipeline: version 128 never published | Run #362 red at the full-suite step; 128 bump committed; latest release 127 | Confirmed release-infrastructure defect (blocked on ML-001/ML-002) | ML-001 |
| README/architecture vs actual `zamin-bukkit` mismatch question | README Status table shows §28.7 deferred; module exists with 52 tests, not runtime-activated — the docs and reality AGREE (pending-only table + §27.1 order); no mismatch found | No longer applicable (verified consistent) | — |
| Perf baseline (20.0 / 20.0 / 18.0 / 12.0 TPS at idle/bots/horde/respawn-storm) | Retained as historical observation with its methodology (cycle-3 Phase 3: release jar, loopback, /tps + /debug counts, no per-section attribution). Not re-run this cycle (see Phase 6 below) | Unverified as general characterization; valid as single-container observation | ML-007 |

Reverified and standing: B-4 papercuts (--port ignored; unclean-exit
stacktrace noise; /give form divergence — record-or-align decision),
B-1 harness op-flow, and the 25 compat-ledger "Ledgered:" standing tails.
None were disproved; none were fixed in this run. B-6 (respawn-kill storm
survivability) remains a positive stress-pass observation, not a defect.

## Phase 1 — The compatibility contract (target matrix)

Derived from `Torch-Arcetecture.txt` (§0 rules, §1 mission, §5, §17-§22,
§27-§29, §31-§34) as of `48b91574` — the owner's §33/§34 additions included.

| Target | Audit requirement (established scope) |
|---|---|
| Vanilla 1.8.8 | Reproduce observable behavior within §17's declared coverage list (blocks/world state, placement/breaking/drops/neighbor updates, scheduled ticks/fluids, entities/movement/gravity/friction/collision, damage/death/knockback/environmental, player movement/hunger/health/XP/inventory/equipment, 1.8.8 combat pipeline, redstone/pistons, hoppers/containers, item durability/use/crafting/furnaces, potion/food/enchantment, mob AI/navigation/pathfinding, spawning/despawning, world generation, entity metadata/tracking, 1.8.8 protocol). Authority: `reference/1.8.8/`. No modern mechanics (no attack cooldown). |
| Bukkit/CraftBukkit | NOT a current target: §27.1 order = native API first, compatibility adapter after 1.8.8 polish; §28.7 deferred (README row: "after 1.8.8 is polished into a playable, maintainable server"). The audit inventories prerequisites and the existing `zamin-bukkit` state (§27.9's compatibility inventory is the future checklist) but activates nothing. |
| Spigot | Same deferred status as Bukkit (§27.9 covers "Bukkit/Spigot/Paper" as one later milestone). No Spigot-specific behavior is currently owed. |
| Paper | Same deferred status (§27.9). Two Paper-informed subsystems are ALREADY landed by explicit decision: the §30 console (ADR-21, "Paper-informed, Torch-owned") and §31 storage layout references (boundary items landed; modern backend future-gated by §31.4's own decision). These are recorded as intentional Torch-native design informed by Paper, not Paper-compatibility commitments. |
| Supplied downstream fork (Folia 26.2.x) | §0 rule 9: "Folia is evidence and a source of engineering lessons, not Torch's architecture." §5 mandates the forensic audit (done at path level; this cycle adds per-patch classification). No Folia behavior is owed; the regionised-threading lessons feed the §6-§16 concurrency model Torch already owns. |
| Torch-native | §27.3's complete-exposure goal over the engine's actual capabilities; §28's plugin concurrency contract; §29 Waves A-X landed surface; Special APIs per `NATIVE_API_CAPABILITIES.md`. Audited independently of upstream compatibility (Phase 4). |
| Intentional differences | ADR-0001 (Minestom abandoned — never reopen); §28.7 deferral; concurrency Phases 5-8 evidence gates (§22); the documented intentional divergences in the compat ledger's "known differences" fields; the /give custom form (record-or-align decision pending — ML-008); no-attack-cooldown (1.8.8 has none — modern cooldown is forbidden, not missing). |

Applicability rule used throughout this audit: a behavior is owed only if
(a) it is 1.8.8-observable within §17's scope, or (b) it is a Torch-native
commitment (§27/§28/§29/§30/§31/§32), or (c) a documented decision says
Torch reproduces it. Everything else is recorded as a decision item or
not-applicable, never silently mandatory. Ambiguities become ledger
decision-items (see MASTER_LEDGER DEC-* rows).

## Phase 2 — vanilla audit method and result (this cycle's increment)

Method: mechanical reference-derived censuses (entity/block/item/packet/
effect class enumerations from `reference/1.8.8/`) reconciled against the
engine source by targeted reads, plus protocol-47 ID verification of every
packet constant. Result: `VANILLA_PARITY_MATRIX.md` — census-level
VERIFIED-EQ on the entity/mob/projectile/vehicle/effect/block-state
inventories (32/32 mobs, 9/9 projectiles, 9/9 vehicles, 23/23 effects,
896 block states), S2C 67/77 with every absence named, and three fresh
confirmed wire gaps the census surfaced:

1. **0x36 Sign Editor Open never sent** (ML-003, H) — `SignManager`'s own
   doc expects the client editor to close; the reference's
   `ServerPlayerEntity.openSignEditor` (lines 547-550) proves the server
   must OPEN it. Real clients cannot type sign text; bot clients masked it.
2. **0x20 Entity Properties never sent** (ML-011, H) — the attribute
   engine (Wave J) has no wire face; equipment attribute modifiers are
   invisible to tracking clients.
3. **0x0A Use Bed never sent + no multiplayer sleep walk** (ML-010) — the
   engine honestly declares "the single-player sleep contract"; the
   vanilla all-sleepers walk and pose broadcast are absent.

Plus the medium rows (0x41 difficulty live-sync ML-012; resource-pack
end-to-end ML-017; spectator camera ML-018) and the quantified remainder
R-1..R-8 (item id diff, compression exchange, keepalive timeout, worldgen
determinism run, per-command diff, fuzz pass, family tails) — the next
increment's queue, honestly scoped.

## Phase 3 — fork audit

Complete for the supplied archive: 24/24 patch files examined and
classified, layer model verified from the paperweight config, two-layer
feature pairs reconstructed. See `FOLIA_PATCH_ACCOUNTING.md`. The
upstream Paper/Spigot/CraftBukkit/Bukkit stacks are not in the archive —
recorded as a scope boundary, never silently audited.

## Phase 4 — Torch-native audit (this cycle's increment)

- API-vs-engine wiring: the §29 Wave IX exposure audit + the API
  capability ledger stand as the record; this cycle's fresh native rows
  are the watchdog (ML-013), the /debug section table (ML-006), the
  resource-pack surface (ML-017), and the login-bound verification
  (ML-014).
- `zamin-bukkit`: 10 classes / 52 tests, honest shims + event bridges,
  not runtime-activated — consistent with the README's deferred row and
  §27.1's order (verified consistent this cycle; the cycle-3 "mismatch"
  question is disproved — see Continuity).
- Read-and-discarded packet handlers found: C2S Spectate (reads two
  longs, no-op) and C2S Resource Pack Status (reads varint, no-op) — both
  ledgered (ML-018/ML-017).
- Deeper stub/TODO sweeps across zamin-core were NOT run this cycle
  (quantified remainder R-9); the prior slices' honesty records + the
  census method stand in.

## Phase 5 — test audit

`TEST_COVERAGE_MATRIX.md`: 440 classes / 2533 tests censused; the
release-workflow retry list vs the actual 2026-10-22 failure members;
the cycle-3 leads dispositioned in test terms; the honest scope of a
green suite. No tests were modified, weakened, or added in this run.

## Phase 6 — performance, concurrency, reliability

- `EngineProfiler` exists with the Phase-7 `sectionNanosSnapshot()` face;
  the /debug stop report does not print the section table (ML-006,
  re-verified by reading the command path) — the attribution blocker.
- The cycle-3 baseline (20.0 idle/bots, 18.0 horde, 12.0 respawn-storm,
  single container, loopback, /tps + /debug methodology) is retained as a
  historical observation only. No new measurements were taken this cycle
  (audit-only run); ML-007 defines the attribution pass.
- Concurrency ownership enforcement stands as implemented (§22 Phase 1-2
  records, ownership tests green); no violations found this cycle, no
  changes made. Phases 5-8 stay gated per §22 — no wiring recommended
  until ML-006+ML-007 deliver attribution.

## Phase 10 — the recommended implementation sequence

Waves, dependency-ordered, sized for parallel agents without unsafe
overlap (shared-file risks named):

1. **Wave R (release)**: ML-001 + ML-002 — merge PR #37 (owner), run the
   deadline census + conversions, land the green pipeline + 128 release.
   Everything else ships behind it. Shared-file risk: test-tree-wide but
   mechanical; no engine files.
2. **Wave W1 (wire conformance, three files' blast radius)**: ML-003
   (0x36 — Protocol18 + the sign-placement arm), ML-011 (0x20 — Protocol18
   + equipment/attribute publish sites), ML-012 (0x41 — Protocol18 + the
   /difficulty arm). Independent rows, same module: sequential or
   three-agent with protocol-module coordination.
3. **Wave W2 (gameplay gaps)**: ML-010 (sleep walk + 0x0A) and ML-018
   (spectator camera) — independent of W1, both touch PlayerSession +
   protocol writers; two agents, disjoint features.
4. **Wave D (diagnostics first, per the evidence rules)**: ML-004 (slot-27
   rejection-reason instrumentation then repro), ML-006 (/debug table),
   ML-013 (watchdog decision + implementation), ML-014/015/016 (the three
   bounded verifications). All independent; instrumentation precedes any
   behavior claim.
5. **Wave V (parity verifications)**: R-1..R-7 remainder items + ML-005
   (/op offline) + ML-008/009 (decisions + papercuts). Each is a bounded
   read/test task; parallelizable as written.
6. **Wave P (attribution)**: ML-007 after ML-006 — the §22 gate evaluation
   on real numbers. Explicitly NOT implementation of any concurrency
   phase.
7. Standing: ML-100 family tails ride their families' slices; §28.7
   adapter stays deferred (owner); DEC-001/002 are owner decisions.

## Final report (this cycle)

- Repository audited: develop @ 48b91574 (clean, synced); branch
  `cycle4-exhaustive-audit` carries the artifacts.
- Supplied fork: PaperMC/Folia ver/26.2.x (mcVersion 26.2, paperRef
  5d4f9bd0, BETA) — NOT a 1.8.8 server; 24/24 patches classified.
- Vanilla coverage: census-verified families listed in the parity matrix
  (32 mobs, 9 projectiles, 9 vehicles, 8 specials, 4 decorations, 23
  effects, 896 block states, 27/28 C2S + 67/77 S2C constants); remainder
  R-1..R-9 quantified.
- Counts: confirmed gaps 14 (ML-001..006, 009..013, 017, 018 + the three
  fresh wire rows counted within), unverified 5 (ML-004, 014, 015, 016,
  008-decision), suspected 1 (ML-021 — the 0x0C metadata lead), intentional differences 3
  (offline-mode DEC-001, batching ML-019, /give pending decision),
  deferred/not-applicable per the compatibility matrix.
- CI/release: pipeline red at #362 (suite step), 127 published, 128
  committed-not-published, PR #37 open/CI-less.
- Rechecked completed items: sleep family (found Partial — single-player
  contract only, honestly declared), spectator (Partial), sign family
  (found the editor-open gap), attributes (engine yes / wire no). No
  regressed rows found; the four Partials above are incompleteness
  findings, not regression findings.
- Could not be conclusively audited (and why): the upstream Paper→Bukkit
  stacks (not in the archive), real-Mojang-client behavior (no client in
  the container), item-registry id-level diff / per-command diff / fuzz /
  determinism run (time-scoped to the next increment, R-1..R-9).
- **No production code, tests, patches, or implementation behavior were
  changed during this run.** Artifacts added: this file + 5 matrix/ledger
  docs + WORKLOG entry, all on `cycle4-exhaustive-audit`.

Audit completeness statement: the exhaustive audit is COMPLETE for the
supplied-fork tree (24/24) and for the contract/continuity/CI dimensions;
it is INCREMENT-COMPLETE for the vanilla dimension (census-level
verification + the named gaps + the quantified R-1..R-9 remainder). The
remainder list is the honest boundary of this cycle's claims.

## Increment 2 (2026-10-23, post-reset session) — ground-truth delta and the KigPaper re-anchor

- **PR #37 handled per owner instruction** ("handle the PR yourself"):
  squash-merged into develop as b7a6917c (5 commits → 1, author Abasing).
  The branch itself was NOT modified; it remains an owner call whether to
  delete it (not done during the audit).
- Develop moved 48b91574 → 94f1fed3 (**owner supplies
  `KigPaper-ver-1.8.8.zip`** at repo root) → b7a6917c (PR #37). Release
  runs: #361/#362 red at the suite step (recorded, ML-001/ML-002); **#363
  in_progress at increment time** (triggered by 94f1fed3); outcome to be
  recorded when it finishes — no pipeline fixes made during the audit.
- **The fork target is re-anchored**: the audit's Phase-3 primary is now
  KigPaper (a real patched 1.8.8 server, owner-supplied), per the mission
  statement's target #2. Folia stays as tree #1 (lessons-only, accounting
  complete and unchanged). Identity established from source, not the name:
  ProjectKig/KigPaper, fork of 1.8.8 Paper; lineage Bukkit→CraftBukkit→
  Spigot→Paper→KigPaper; 533 patches across 5 stacks; submodules
  (upstream Bukkit/CraftBukkit sources) empty in the archive — scope
  boundary recorded. Full detail: `KIGPAPER_PATCH_ACCOUNTING.md` +
  `docs/kigpaper-census.json` (census script `scripts/kigpaper_census.py`).
- Audit-remainder state: ML-023 tracks the per-patch deep classification of
  the two server stacks (418 patches; nms layer done 8/8; API stacks closed
  as a DEC-002-deferred layer with full enumeration). The audit is NOT
  end-to-end done until ML-023 closes; this increment reports exact
  coverage, never a partial scan as complete.
- **No production code, tests, patches, or implementation behavior were
  changed in this increment.** Artifacts: the KigPaper accounting doc, the
  census JSON + script, ledger rows ML-022/023 + DEC-003, compatibility
  matrix fork row, this section, WORKLOG entry.
