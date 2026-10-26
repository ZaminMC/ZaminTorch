# Master Implementation Ledger — the authoritative next-queue backlog

Cycle-4 exhaustive-audit deliverable. Every row below is evidence-backed:
the evidence column names the exact files/lines/patches examined. Rows are
ordered by the implementation sequence (§Phase 10 of the audit doc), not by
discovery order. Statuses: `confirmed gap` / `suspected gap` / `unverified` /
`intentional difference` / `deferred` / `not applicable`. Historical
completed work is NOT duplicated here — it lives in `Torch-Arcetecture.txt`
§29, `VANILLA_1_8_8_COMPATIBILITY.md`, `NATIVE_API_CAPABILITIES.md`; only
the 25 standing family tails are referenced (ML-100), never re-enumerated.

Severity scale: R (release-blocking) > H (high) > M (medium) > L (low).
Layer tags: V=vanilla-parity, P=protocol/wire, T=Torch-native,
I=infra/CI/release, S=security/boundary.

---

## Release-blocking

### ML-001 — the release pipeline is red; torch-1.8.8-128 unpublished
- Category: build/CI/release defect. Layers: I.
- Severity: R. Impact: no release can publish; every future wave ships only
  as commits.
- Evidence: GH Actions run #362 (a074fef6) failed at "Build + full test
  suite (one retry…)" (job API verified this session); #361 same. Latest
  published release torch-1.8.8-127 (2026-10-22T16:37Z). The 128 bump
  commit exists on develop.
- Reference/requirement: §23 — "For releaseable milestones… never claim a
  release that does not exist"; the release workflow gates on the suite.
- Actual: the suite is load-flaky under CI (see ML-002); the two observed
  failure members are already hardened on PR #37 (89892637) but that PR has
  no CI run (release workflow = develop-push only) and is unmerged.
- Gap: fix staged but unverified; pipeline red.
- Verification: merge PR #37 (owner call) or run the suite on its branch;
  expect green; then the release workflow publishes 128+.
- Acceptance: a green develop-head release run + a published 128+ release.
- Dependencies: ML-002 (the population — a different member may trip next).
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the release pipeline: runs #364-#366+ green through the wave merges, torch-1.8.8-129/130/131 published (129 was green in-flight at the wave start); the 128-bump debt cleared by the PR #37 merge push. The suite-population hardening landed as ML-002.
- Status: RESOLVED (pipeline green through the wave).
- Confidence: high.

### ML-002 — the wall-clock-deadline test population (systemic)
- Category: test-infrastructure defect. Layers: I.
- Severity: R (blocks ML-001). Impact: every loaded full-suite run can trip
  a different timing test; whack-a-mole red pipelines.
- Evidence: runs #361/#362 failed three DISTINCT tests across two runs:
  `FishingApiAcceptanceTest.theInterceptorSeesTheContextAndReplacesTheCatch`
  + `.theLootOverrideAnswersTheBandAndTheRestore` (missed bite-window race,
  two methods), `BukkitEventBridgeGroupThreeTest.theMobDeathBridgeObserves…`
  (racy MONITOR assert), `RconProtocolTest.theEmptyOutputAnswersOneEmptyPacket`
  (5s FIN-WAIT wall-clock drain). The two OBSERVED members are hardened on
  PR #37; the POPULATION is not audited.
- Requirement: §19 "Never loosen a test merely to make it pass" — the
  remediation must harden synchronization, not delete detection.
- Gap: no census of `System.currentTimeMillis`/`Thread.sleep`/`await(…N…)`
  deadlines across the test tree; no tick-rate-scaled await helper.
- Direction: enumerate every wall-clock deadline in tests; classify each
  (unjustified deadline / missing engine-visible condition / genuine
  product timeout); convert to engine-condition awaits or tick-scaled
  deadlines; keep failure detection meaningful.
- Acceptance: a census doc (test file → deadline → classification), the
  conversions landed, and ≥3 consecutive green full-suite CI runs.
- Dependencies: none (ML-001 verifies through it).
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the census landed (docs/TEST_DEADLINE_CENSUS.md + the TSV + the two scripts); 270 fixed sleeps converted over the ONE shared utility (EngineAwaits, java-test-fixtures); the observed members dispositioned. The coordinator review added: the GroupTwo MONITOR sibling hardened (the #361 family member the census missed), and the fishing budget corrected 1500->2400 ticks with the l-scaling derivation (the arming/approach countdowns halve their rate without sky access — the seed-dependent 50/50 flake both machines observed). Two consecutive chunked full-suite passes on the lane; every merge push's release run confirms.
- Status: RESOLVED (census + conversions landed).

## High — protocol / client-visible

### ML-003 — Sign Editor Open (0x36) never sent: real clients cannot type sign text
- Category: protocol defect + vanilla behavior gap. Layers: P, V.
- Severity: H. Impact: a real 1.8.8 client places a sign and the editor
  never opens; the sign stays blank. Bot clients that write 0x12 directly
  mask the bug (why the mineflayer playtest missed it).
- Evidence: reference `ServerPlayerEntity.openSignEditor` (lines 547-550)
  sends `OpenSignEditorS2CPacket` on sign placement — the client's editor
  opens ONLY on this packet. Torch: no 0x36 constant in `Protocol18.java`
  (rg "0x36"/"OpenSign" = none); `SignManager`'s own doc says the editable
  lines "arrive through Update Sign (0x12) right after the historical
  placement editor closes" — but nothing ever opens that editor.
- Gap: the placement path must send 0x36 (packed position) to the placer;
  the editor close then flows 0x12 back as it already does.
- Direction: add the constant + send in the sign-placement arm of
  `BlockInteractionService`/placement dispatch; keep the 64-char server cap.
- Acceptance: a protocol test pinning 0x36's wire form + an engine test
  placing a sign and asserting the packet to the placer; mineflayer probe
  optional.
- Dependencies: none.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the 0x36 writer at the reference SignItem.useOn order (block land -> consume -> open), the per-player fan (the placer alone), the 0x12 close path unchanged. SignEditorOpenWireTest 3/0 + SignEditorOpenAcceptanceTest green.
- Status: RESOLVED (landed, PR #40).

### ML-011 — Entity Properties (0x20) never sent: attribute modifiers invisible to clients
- Category: protocol defect. Layers: P, V.
- Severity: H (medium blast radius). Impact: server-side attribute changes
  (Wave J's `tag.AttributeModifiers` on equipped items, API attribute
  mutations) never reach tracking clients — client-side movement prediction
  and display use defaults.
- Evidence: reference `EntityAttributesS2CPacket` (s2c/play, protocol 47
  0x20) sent by EntityTrackerEntry on track and on attribute change. Torch:
  no `S2C_ENTITY_ATTRIBUTES` constant (constant census this session);
  rg "0x20" in protocol = comments only. The attribute ENGINE exists
  (Wave J; `MobDataStore`, attribute files) — the wire face is absent.
- Gap: the 0x20 writer + the track-time send for entities carrying
  modifiers + the change-time send.
- Direction: implement the packet (varint id, count, per-attribute:
  string id, double value, modifier list) and hook it where equipment
  attribute modifiers are applied and where the API mutates attributes.
- Acceptance: protocol conformance test (wire form vs reference), engine
  test: equip a mob with a modifier item → tracking client receives 0x20
  with the derived value.
- Dependencies: none.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the 0x20 writer (EntityAttributesS2CPacket.write verbatim), the trackable pair only (maxHealth + movementSpeed; attackDamage never — the isTrackable law), the track-time sends at the reference's order (attributes before equipment), the change-time sends. EntityPropertiesWireConformanceTest + MobAttributesWireTest green.
- Status: RESOLVED (landed, PR #42).

### ML-010 — Use Bed (0x0A) never sent: no sleeping pose, no multiplayer sleep walk
- Category: vanilla behavior gap (protocol-delivered). Layers: P, V.
- Severity: M-H. Impact: other players never see the sleep pose; the
  vanilla multiplayer contract (morning only when ALL sleeping players
  sleep) is replaced by a single-player instant jump.
- Evidence: reference `PlayerSleepS2CPacket` (s2c/play 0x0A) broadcasts the
  pose; `ServerWorld`'s sleep walk requires all sleepers. Torch:
  `EngineServer.sleepOnTick` (line ~12383) documents "the single-player
  sleep contract this server makes" — sets bed spawn, jumps to morning,
  clears weather; no 0x0A constant, no per-player sleeping state, no
  all-sleepers walk, no wake/leave-bed packet. `SleepAcceptanceTest` covers
  only the single-player contract (test names verified).
- Gap: per-player sleeping state; 0x0A broadcast on sleep; the
  all-sleeping → morning walk (`areAllPlayersAsleep`); the leave-bed
  EntityStatus on wake; monster-proximity refusal arm.
- Direction: port the reference's sleep walk over the existing night-window
  gate; add the packet.
- Acceptance: engine tests: two players, one sleeps → no morning until both
  sleep; pose packet observed by the other player; the morning walk fires.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the 0x0A broadcast, the all-sleepers walk over the single-player contract (one player = the same instant morning), the monster box (x/z +-8, y +-5), the wake fans, the foot->head redirect; the Entity Action ordinals corrected to the reference enum (2 STOP_SLEEPING / 3 START_SPRINTING / 4 STOP_SPRINTING — a real cross-cutting wire fix the lane surfaced). MultiplayerSleepAcceptanceTest + MultiplayerSleepWireTest green; SleepAcceptanceTest unmodified and green.
- Status: RESOLVED (landed, PR #41).
  single-player contract — a Partial, not a false claim). Confidence: high.

## Medium — protocol / operations

### ML-012 — Server Difficulty (0x41) never sent: /difficulty not live-synced
- Category: protocol defect. Layers: P.
- Severity: M. Impact: `/difficulty` changes the engine's difficulty field
  (EngineServer:21142) but connected clients keep the join-time difficulty
  byte until respawn; hunger/starvation damage behavior diverges
  client-side.
- Evidence: reference `DifficultyS2CPacket` (s2c/play 0x41) sent on
  difficulty change. Torch: difficulty byte only inside JoinGame/Respawn
  (V18Connection:322,768); no 0x41 constant.
- Acceptance: `/difficulty hard` → every connected client receives 0x41=3
  (protocol test + engine test).
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the single-byte write at the reference onLogin slot (after Join Game, before Spawn Position) + the /difficulty change-time fan over every session link. OpsDifficultySyncTest + OpsProtocolWireTest green.
- Status: RESOLVED (landed, PR #39).

### ML-017 — Resource Pack send (0x48) absent end-to-end
- Category: feature gap (ops). Layers: P, T.
- Severity: M. Impact: operators cannot ship a resource pack; the
  `resource-pack`/`resource-pack-hash` server.properties rows of the
  reference dedicated server have no equivalent.
- Evidence: reference `ResourcePackS2CPacket` (0x48) + properties rows;
  Torch: `C2S_RESOURCE_PACK_STATUS` is read-and-discarded
  (V18Connection:567 reads a varint, no-op); no 0x48 writer; no properties
  rows.
- Acceptance: properties rows + join-time 0x48 send + status logging; a
  protocol test pinning the wire form.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the reference DedicatedServer rows (resource-pack / resource-pack-hash), the join-time 0x48 gated on a configured url, the C2S 0x19 hash+result varint parse with the reference Response vocabulary logged. ResourcePackConfigTest + the wire tests green.
- Status: RESOLVED (landed, PR #39).

### ML-018 — Spectator mode is partial: camera/spectate unimplemented
- Category: vanilla behavior gap. Layers: V, P.
- Severity: M. Impact: gamemode 3 gates digging/use correctly
  (BlockInteractionService:987,1035,1236) and grants flight, but the
  spectator's core mechanics — camera teleport to players (C2S Spectate),
  the Camera packet (0x43), spectator no-clip/visibility rules — are
  absent.
- Evidence: `C2S_SPECTATE` handler reads two longs and discards
  (V18Connection:566-569); no 0x43 constant; no no-clip/visibility walk
  found.
- Acceptance: spectate a player → the client's camera follows the target
  (0x43 + position stream); the spectator is invisible to others; the
  leave-spectate walk restores control. Engine + protocol tests.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the C2S Spectate resolution, the 0x43 Camera packet, the no-clip gate (the wire-side sanity band still bounds spectators), the visibility law (the spectating body never spawns in another view); cross-dimension spectate ledgered as the honest bound. SpectatorCameraAcceptanceTest + SpectatorCameraWireAcceptanceTest green (the latter's 0x0C readers corrected to the 16-byte uuid form after the coordinator's ML-021 fix).
- Status: RESOLVED (landed, PR #44).

### ML-005 — /op refuses offline players (vanilla ops via profile cache)
- Category: vanilla parity defect. Layers: V.
- Severity: M (carried from cycle 3, unchanged).
- Evidence: reference `OpCommand` lines 30-39 resolves through
  `GameProfileCache`; Torch answers "No online player named X" (cycle-3
  verified; engine unchanged since).
- Acceptance: `/op Name` for a cached-but-offline profile writes ops.json
  and answers success; unknown name answers the vanilla
  "commands.op.failed" line.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): PlayerProfileCache over the .zpd census + ops.json rows + this run's joins (no new persistence format); /op offline writes ops.json at level 4, unknown names answer commands.op.failed; /deop studied and left alone (its own walk already resolves). OpOfflineProfileAcceptanceTest green.
- Status: RESOLVED (landed, PR #43).

### ML-006 — /debug stop prints no per-section table (blocks perf attribution)
- Category: observability defect. Layers: T.
- Severity: M (gates ML-007 and every §22 concurrency decision).
- Evidence: `EngineProfiler.sectionNanosSnapshot()` exists (§22 Phase 7
  record); the /debug stop report prints a one-line summary only (cycle-3
  B-7; command path re-read this cycle).
- Acceptance: /debug stop prints the reference-shaped section table (name,
  count, avg, total); a test pins the report contents.
- Resolution (2026-10-23, the coordinator's wave-3 lane): LANDED on
  feature/debug-attribution. The profiler gained the visit-count face
  (sectionCountsSnapshot beside the undecayed nanos face); /debug stop
  prints the reference-shaped table to the console (header verbatim from
  DebugCommand.buildReport; rows: name, count, avg/visit, total, % of
  root, total-descending), built from frozen UNDECAYED snapshots taken
  before the file report's decay walk; the file report and the reply stay
  reference-1:1. Tests: DebugSectionTableTest 3/0 + the three existing
  profiler tests green unmodified (12/0 over the family).
- Status: RESOLVED (landed).

### ML-007 — performance attribution pass (baseline retained, attribution missing)
- Category: performance work (evidence-gated). Layers: T.
- Severity: M. Impact: the 20/20/18/12 TPS single-container observations
  (cycle 3) cannot be attributed to subsystems; §22 Phase 5-8 gates cannot
  be evaluated.
- Evidence: cycle-3 Phase 3 methodology + numbers (retained as historical
  observation, not general characterization); no section table (ML-006),
  no jstat/GC capture.
- Direction: land ML-006, re-run the load harness with per-section +
  GC/allocation capture, produce the attribution report. Only then
  evaluate Phase 5/6 lanes against §22.
- Acceptance: an attribution report naming the top subsystems under the
  horde + respawn-storm loads, with the capture methodology recorded.
- Dependencies: ML-006.
- Resolution (2026-10-23, the coordinator's wave-3 lane): LANDED —
  docs/ATTRIBUTION_REPORT.md + docs/ATTRIBUTION_RUN_EVIDENCE.txt over the
  committed harness AttributionLoadTest (real-wire, the cycle-3 shapes
  in-process, whole-fan window deltas + GC beans + heap + JFR
  ObjectAllocationSample). The findings: randomTicks 3.6-3.9ms/tick
  load-INDEPENDENT (the top subsystem); mobs 715us -> 2.4-4.2ms at the
  ~120-body hostile census (~20-34us/body, no superlinear signature); the
  composite 7.2ms/tick whole-fan vs the 50ms budget — 19.9-20.0 TPS
  through every window (the cycle-3 12.0 lived in the wire/bot-side
  envelope the harness excludes, recorded). VERDICT: no concurrency phase
  justified; the §22 discipline holds. GAP FOUND: the re-stream's chunk
  serialization has no dedicated profiler section — NEW ROW ML-024 below.
- Status: RESOLVED (landed).

## Low / papercuts (carried from cycle 3, verified standing)

### ML-008 — /give form divergence (record-or-align decision)
- Category: decision item + parity. Layers: V. Severity: L.
- Evidence: engine `/give <name> [count] [data] [dataTag]` vs vanilla
  `/give <player> <item> …` (cycle-3 B-4; unchanged).
- Decision needed from owner: record as intentional divergence (documented
  in /help) or align to vanilla syntax.
- Resolution (2026-10-26, the owner's vanilla-grade directive — "verify
  what is done and what is not 1:1 vanilla grade, do vanilla grade"):
  ALIGNED. GiveCommand 1:1 — the `<player> <item> [count 1..64] [data]
  [dataTag]` target form over the parsePlayer ladder (selector/uuid/name),
  op-2 like the reference, the pop sound through the playSound(Entity
  except, ...) overload (the new SoundExcept FX bus arm — everyone near
  the target hears random.pop EXCEPT the granted player; the wire arm
  skips the exempt observer), the full-fit arm's makeFakeItem one-tick
  ghost (never-pick-up 32767 + age 5999, withCount(1) keeps the damage
  and every dataTag arm — same-object semantics), the remainder arm's
  drop with noPickUpDelay + the owner window, and the AFFECTED_ITEMS
  result rows; the count band is the command's own 1..64, never the
  stack size. The test fleet migrated with it: 38 core + 56 wire give
  sites, the ops seeding (ops.json pre-seeds for the wire rigs,
  setOpLevel at join for the core rigs), the scripted client's
  ghost-wake drain (the makeFakeItem quirk is real wire behavior —
  spawn, metadata, destroy, the pop — with pushback so no typed read
  loses its packet), and the keep-alive auto-answer in readPacket
  itself (a blocked read was always swallowing keep-alive requests
  unanswered; the drain only widened the hazard the 250ms-interval rigs
  live under). GiveCommandAcceptanceTest 9 green;
  RegisteredCommandSurfaceParityTest re-pinned (give 2, the R-6 row
  count 6 -> 5 divergent, 50 -> 51 matched).
- Status: RESOLVED (aligned to vanilla, landed).

### ML-009 — B-4 papercuts: --port override ignored; unclean-exit stacktrace noise
- Category: operational papercuts. Layers: T. Severity: L.
- Evidence: cycle-3 B-4 (verified standing; `--port` silently loses to
  zamin.properties; abrupt client TCP reset logs a WARNING + netty
  stacktrace where vanilla logs a quiet disconnect line).
- Acceptance: CLI override honored or refused loudly; disconnect path logs
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): (a) --port honored with CLI > properties > default precedence (StartupArguments; malformed fails boot loudly; never written back); (b) the IOException family answers one quiet disconnect line — the ReadTimeout reap rides the same quiet law, first in the hand (netty's own subtype order). DisconnectClassificationTest 2/0, 3x green after the review correction (the reason pin relaxed to the quiet family's either delivery — a reset arrives as either the IOException form or a plain channelInactive).
- Status: RESOLVED (landed, PR #43).

## Torch-native capabilities surfaced by the fork audit

### ML-013 — no tick watchdog / stall detection
- Category: Torch-native capability candidate. Layers: T. Severity: M.
- Evidence: Folia patches P5+M8 implement a watchdog; Torch has none (grep
  this session). Serves §20 diagnostics + §28.9 observability aims; NOT a
  Folia-compat debt (see FOLIA_PATCH_ACCOUNTING.md).
- Direction: a stall detector over the tick lane (thread dump + loud log on
  N-second stalls), owner-approved shape.
- Resolution (2026-10-26, the owner's "continue working towards until done"
  directive): LANDED — not a Folia port but the REFERENCE's own watchdog,
  1.8.8 vanilla behavior carried whole (net.minecraft.server.dedicated.
  ServerWatchdog, reference lines 17-90): the max-tick-time property row
  (one-minute default, 0 disables at the boot gate), the daemon "Server
  Watchdog" thread started at the world-ready moment, the tick lane's
  heartbeat watched, and a tick wedged past the threshold answering with the
  two fatal lines, a full thread dump, a crash report under
  crash-reports/crash-...-server.txt in the reference CrashReport.build
  structure (witty comments carried 1:1), and the reference's force exit
  (System.exit(1) under a 10s halt(1) timer — a wedged tick lane owns every
  world write; a graceful save would itself hang). The lane also found and
  closed an unledgered vanilla difference in the same family: the keep-up
  warn law (MinecraftServer lines 396-401 — >2000ms warns, once per 15s,
  "Can't keep up!" verbatim) replaced the engine's own 250ms-every-time
  line. ServerWatchdogTest 6/0 + MaxTickTimeConfigTest 6/0; the
  ticker/config/console/profiler families green unmodified.
- Status: RESOLVED (landed, the reference-shaped capability).

### ML-014 — login-flood boundary behavior unverified
- Category: security/boundary verification. Layers: S. Severity: M.
- Evidence: Folia M2 bounds pending logins; Torch has no named bound and
  the login path was not audited end-to-end this cycle. A login flood's
  tick-lane cost is unknown.
- Verification: N concurrent logins against a booting world; measure
  tick-lane cost + memory; compare against the reference's own queueing.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the measurement (100 in-progress logins: zero tick-lane cost, 20+ tps; ~1.3-4KiB per pending connection; 12 full logins: 44-55ms each serialized on the event loop) + the reference's own bound (ReadTimeoutHandler(30) first in the pipeline, the quiet reap). LoginFloodBoundaryTest green.
- Status: RESOLVED (measured + bounded, PR #45).

### ML-015 — neighbor updates at unloaded-chunk boundaries unverified
- Category: vanilla parity verification. Layers: V. Severity: M.
- Evidence: Folia M4 guards unloaded/non-owned chunks; vanilla 1.8.8's
  neighbor walk skips unloaded chunks; Torch's guard was not verified.
- Verification: place a redstone/neighbor-triggering update at a
  loaded/unloaded chunk edge; assert the update is dropped (reference
  behavior) and no exception/log noise occurs.
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the update at a loaded/unloaded edge is dropped silently with no chunk generation — exactly the reference's own law (the EmptyChunk dispatch-to-air); verified-as-is and pinned (UnloadedEdgeNeighborUpdateTest 2/0), no engine change owed.
- Status: RESOLVED (verified-as-is + pinned, PR #45).

### ML-016 — relog-while-riding: remount + vehicle position sync unverified
- Category: vanilla parity verification. Layers: V. Severity: M.
- Evidence: Folia M6 fixes vehicle/player position desync on data load;
  Torch persists `ridingVehicleId` (PlayerSession) but the relog remount
  walk was not verified; 1.8.8 restores the mount from player NBT.
- Verification: quit riding a boat/minecart/pig → rejoin → assert remount
  at the vehicle's position (the reference's behavior).
- Resolution (2026-10-23, the coordinator-reviewed wave-3 merge): the remount walk was absent -> landed (the quit captures the ride before the seat opens, the join consumes it after the whole burst, the remount through the live verbs at the reference's startRiding order). RelogRemountAcceptanceTest green.
- Status: RESOLVED (fixed + pinned, PR #45).

### ML-019 — batched block/chunk packets absent (0x22 multi-block change, 0x26 chunk bulk)
- Category: protocol batching difference. Layers: P. Severity: L (perf).
- Evidence: Torch sends individual 0x23 block changes and 0x21 chunk
  datas; the reference batches (0x22, 0x26). Clients accept singles — a
  throughput/bandwidth difference, not a conformance break.
- Direction: optional batching optimization AFTER attribution (ML-007)
  proves it matters. Status: LANDED (PR #50, Wave W6, 2026-10-24): the
  0x22 Multi Block Change at the block-change flush (up to 64 same-chunk
  changes in one record) and the 0x26 Map Chunk Bulk at the view walks
  (the join/region-crossing chunk batches, the 1.8.8 bulk's own
  sky-light column carry) — MultiBlockChangeWireTest 5/0 +
  ChunkBulkWireTest 3/0 over the conformance rig. RETIRED.

### ML-020 — online-mode (Mojang auth + encryption) absent — scope decision
- Category: scope decision. Layers: P, S. Severity: M (deployment-dependent).
- Evidence: reference login flow has `KeyC2SPacket` (encryption response)
  + `HelloS2CPacket` (encryption request) + `CompressionThresholdS2CPacket`
  (login compression). Torch: LOGIN_START only; no encryption/request
  constants; no online-mode property (rg this session). FrameCodec handles
  no compression threshold exchange — need to verify whether zlib
  compression is enabled at all (remainder check, see audit doc §Phase-2-remainder).
- Impact: offline-mode only — fine for local/dev play; blocks
  authenticated public deployment.
- Decision needed from owner: declare offline-mode the 1.8.8-era scope
  (record as intentional difference) or schedule online-mode as a wave.
- **Decision landed 2026-10-26 (DEC-001): online-mode implemented whole
  as Wave ON-1 — the encryption pair, the AES/CFB8 stream layer, the
  session-service hasJoined consult, and the compression exchange this
  row's remainder flagged, with offline-mode kept as the
  online-mode=false branch.**
- **Wave ON-1 LANDED 2026-10-26 (§29's wave rows): ON-1a the login
  compression exchange (f0dbe1dd), ON-1b the whole encryption arm —
  TorchEncryption, the wire pair, the AES/CFB8 stream codec, the login
  stage machine, the authenticator thread, the session-service seam
  with MojangSessionService's real hasJoined consult (5f0d77e1) — and
  ON-1d the profile's signed properties riding the tab list's
  ADD_PLAYER (the textures lane). ML-020 is RESOLVED.**
- Status: RESOLVED (Wave ON-1 landed). Confidence: high.

### ML-021 — 0x0C Named Entity Spawn metadata chokes an independent parser (cycle-3 lead, unresolved)
- Category: suspected wire defect. Layers: P.
- Severity: H (candidate). Impact: strict clients may reject player-spawn
  metadata; observed once with four mutual players (mineflayer protodef
  `PartialReadError` inside `entityMetadataItem`).
- Evidence: cycle-3 B-5 record; no engine change since; the raw 0x0C frames
  were never captured, so localization is open — a Torch encoding defect
  and a mineflayer parser quirk remain both possible (the addendum's own
  rule: an independent parser failure is not proof Torch is wrong).
- Observation/repro/localization split (addendum §3): observation = the
  PartialReadError; reproduction = 4-bot mutual-visibility run (not yet
  re-run); localization = NONE yet (bytes uncaptured); root cause = open.
- Direction: capture the raw 0x0C frames (`bot.on('raw')` hex dump) from a
  4-bot run; pin the engine's DataWatcher encoding against the reference
  `SyncedData` wire form for every watched type (byte/short/int/float/
  string/item/optional-position-specifically); whichever side mismatches
  gets the fix.
- Acceptance: the captured frame + a pinned protocol test over the
  reference form; if mineflayer's parser is at fault, the exoneration is
  recorded with evidence and this row closes as not-a-defect.
- Resolution (2026-10-23, the coordinator's wave-3 lane): CLOSED as a REAL
  ENGINE DEFECT, root-caused with captured bytes. Phase one: the botless
  pin (DataWatcherWirePinTest 4/0 — an independent reference-form parser,
  every watched type pinned to its exact boundary). Phase two: the four-
  bot capture against the release jar REPRODUCED the PartialReadError (24
  stacks, all packet_named_entity_spawn -> entityMetadataItem); a
  socket-level tee captured the failing frames' bytes pre-parse. ROOT
  CAUSE: trackPlayer wrote the 0x0C's uuid as a length-prefixed STRING
  (varint + 36 chars) where 1.8.8's wire form is the reference's
  writeUuid — 16 RAW BYTES (two longs) — 21 extra bytes per 0x0C; every
  strict client entered the metadata walk 21 bytes early; the
  intermittence was the luck of the garbage walk landing on 0x7f. The
  tab list already used writeUuid — the defect was this ONE site. FIXED
  (ByteBufOps.writeUuid, the one-line change) + pinned (the boundary
  assert is the regression guard); mineflayer's parser was RIGHT.
- Status: RESOLVED (defect fixed and pinned).

### ML-022 — EnumProtocol specialized-packets registry: no caller established (KigPaper N4)
- Category: fork-audit bounded verification. Layers: P (candidate).
- Severity: L. Impact: if any shipped KigPaper patch registers a clientbound
  packet class outside the protocol bimap, that is an extra wire surface
  Torch would never emit; if none, the registry is inert infrastructure.
- Evidence: `nms-patches/EnumProtocol.patch` (full read, this increment) adds
  `registerSpecializedPacket` + the `specializedPackets` map consulted on
  CLIENTBOUND id lookup; the caller set was not established (see
  KIGPAPER_PATCH_ACCOUNTING.md N4).
- Verification: grep the four git-format stacks for
  `registerSpecializedPacket`; if no caller, close as NA-infra; if callers
  exist, name the packets and fold them into the wire-gap census.
- Resolution (increment 3): grep across all 533 patches found NO caller —
  the registry is inert infrastructure; no extra clientbound wire surface.
  CLOSED as verified/NA-infra (no defect, no debt).

### ML-024 — the wire-publish walk has no dedicated profiler section
- Category: observability gap (found by the ML-007 pass). Layers: T, P.
- Severity: L.
- Evidence: the attribution pass's storm window (17 full re-streams of the
  view-distance set) shows NO new section and no single-section jump beyond
  the census's variance band — the chunk serialization runs inside the
  publish walk, which no profiled() section covers; the JFR capture
  (byte[] 4.15GB + String 2.72GB sampled) is consistent with the
  serialization buffers plus the profiler's own per-section string churn.
- Direction: a dedicated wire-publish section around the chunk
  serialization/publication path so the next attribution pass is exact.
- Acceptance: the section visible in sectionNanosSnapshot; the harness
  re-run shows the re-stream's cost in its own row.
- Resolution (2026-10-23, the cycle-5 wave): RESOLVED. The section is
  root.wirePublish, recorded through the profiler's new cross-thread
  span arm (EngineProfiler.recordSectionSpan) rather than a nested
  push/pop — the walk runs on three lanes (the netty event loop's
  join/movement streams, the tick thread's pre-handler job drain where
  the respawn re-stream lands OUTSIDE the root section's window — which
  is why the original pass could not see it — and the tick thread's
  relightTransport section), and the push/pop stack's single-caller
  contract excludes the first lane (a torn stack answers garbage rows,
  worse than no data). The gate field is now volatile (the event-loop
  lanes read it with no happens-before edge from start/stop); the arm
  is zero-cost while profiling is off. Acceptance met on both bars:
  the row appears in sectionNanosSnapshot, and the harness re-run reads
  the re-stream's cost in its own row — 4711.2 us/tick on the
  coordinator's independent review run (5344.6 on the author's capture,
  1377 visits, 0.43 ms/visit, 10.44% of root — the storm window's
  second-largest row). The attribution report's gap #4 marked RESOLVED;
  the captured evidence in ATTRIBUTION_RUN_EVIDENCE.txt. Pinned by
  WirePublishSectionTest 2/0 (both real lanes driven — a fresh join on
  the netty loop + a death/respawn re-stream on the pre-handler drain,
  count >= 2x the view columns, the stack-balance pins: the row never
  gains a child, mobs/randomTicks/relightTransport never gain children).
- Status: RESOLVED (landed, the cycle-5 wave).

## KigPaper increment 2 (the supplied patched-1.8.8-server audit)

The fork target is re-anchored on KigPaper (owner-supplied at 77be148c);
Folia remains lessons-only. New rows are fork-derived; the audit's own
increment state lives in KIGPAPER_PATCH_ACCOUNTING.md's coverage statement.

## Family-owned standing tails (referenced, not re-enumerated)

### ML-100 — the compat ledger's 25 "Ledgered:" standing tails
- Category: vanilla parity tails. Layers: V. Severity: per-family (L-M).
- Evidence: `VANILLA_1_8_8_COMPATIBILITY.md`'s 25 "Ledgered:" rows
  (particles, stats folds, armor-model edges, spawner NBT, CustomName
  rows, portal particles, wolf/biome rain gates, cauldron tails, etc.).
- Rule: each tail stays owned by its landed family; a tail is picked up
  only as its family's slice, never as an unbounded "polish" wave. No
  reopening of completed rows without new evidence.
- Status: confirmed tails (tracked at family level).

## Deferred / not-applicable (recorded to prevent re-litigation)

- DEC-001 (=ML-020): online-mode scope — **DECIDED 2026-10-26 (the
  owner's priorities: 1:1, reliability, performance, actualness):
  IMPLEMENT online-mode whole, as Wave ON-1.** Offline-only is not the
  vanilla shape — the dedicated server's own default is
  `getBoolean("online-mode", true)` (DedicatedServer line 103), and the
  Encryption Request/Response pair (S2C/C2S 0x01) plus the AES/CFB8
  stream layer are part of the protocol-47 wire surface every real
  client implements. The wave carries the session-service hasJoined
  consult (the real HTTPS call — no stubs), the reference's exact
  failure strings ("Failed to verify username!", "Authentication
  servers are down. Please try again later, sorry!"), and the
  compression remainder the row flagged (the LoginCompressionWireTest
  divergence pin gets rewritten with this decision as its authority).
  Offline-mode stays as the `online-mode=false` branch — also vanilla.
  **EXECUTED 2026-10-26: Wave ON-1 landed whole (§29's wave rows —
  ON-1a the exchange, ON-1b the encryption arm + consult, ON-1d the
  tab-list textures lane); this entry stays as the decision record.**
- DEC-003: **fork-divergence adoption policy (KigPaper family)** —
  **DECIDED 2026-10-26 (the owner's priorities): NO KigPaper
  configuration family, ever.** Torch owns vanilla's own
  `server.properties` rows only (the two it was missing — online-mode
  and network-compression-threshold — land with Wave ON-1, Dedicated
  lines 103/451 with the MinecraftServer 256 default); KigPaper's ~95
  census-counted "Configurable-*" gameplay knobs stay `Different by
  design / not owed`, each recorded as a fork divergence, never
  auto-owed. The per-patch deep classification's knob listing under
  this ID is now a CLOSED record (the reference-wins law, standing).
- ML-023 (tracking row): CLOSED increment 3 — the 418 server-stack patches
  dispositioned (130 knob/171 bugfix-div/70 api/24 perf/23 infra; scripts +
  deep-pass JSON + 418-row table + bugfix class map committed); the
  material spot-check (SSV0067 fluids) passed two-sided (Torch follows the
  reference bands exactly). Fork-derived waves can now be planned; no
  Torch debt was created by the fork audit (knobs=DEC-003, bugfix-divs
  reference-wins, api=DEC-002, perf=ML-007-gated).
- DEC-002: adapter-era surfaces — **DECIDED 2026-10-26 (the owner's
  priorities: 1:1, reliability, performance, actualness): STAY NATIVE,
  indefinitely.** §28.7 does not activate; the Bukkit Timings API stays
  ABSENT, not stubbed (absent beats stubbed — a stub API is a lie
  plugins compile against); the plugin gating manifest stays unwritten
  until a real plugin host exists; Folia A1/A3 and the KigPaper API
  stacks (107 patches) remain the closed enumeration they are. The
  adapter era reopens only on an explicit owner reversal with a named
  consumer; until then the §27.1 order stands and `zamin-bukkit` stays
  the not-runtime-activated honest-shim record it is.
- §28.7 Bukkit adapter: deferred per §27.1 (README row); prerequisites
  inventoried in COMPATIBILITY_MATRIX.md; NOT activated by this audit.
- Concurrency Phases 5-8: gated on §22 measured evidence (ML-006/ML-007
  provide the evidence path); no wiring started, none recommended yet.
- Minestom: ADR-0001 abandoned — permanently closed.
- Folia 26.2.x: lessons-not-architecture (§0.9) — full accounting in
  FOLIA_PATCH_ACCOUNTING.md; no behavior owed.
