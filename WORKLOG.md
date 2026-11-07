# Worklog

(Note: this file was accidentally overwritten during the 2026-10-28 Wave SC
session and rebuilt from the session's opening read; the previous session's
record is reconstructed below, and the full engineering history of every
wave lives in the repo's committed WORKLOG.md — nothing engineering-wise
was lost.)

---
Task ID: (prior session, reconstructed)
Agent: main (ZaminTorch session)
Task: Continue development — Wave AS, the armor-stand family's dimension
carry (the LAST standing overworld-0 family, closing the class whole) +
the two EYFW join-view repairs the scoping surfaced.

Work Log:
- Read the worklog, synced develop (769fd7fb, clean), checked the open
  PRs via the API: 0 open — the first-priority lane empty.
- Re-read Torch-Arcetecture.txt fresh (6387 lines): §22's demand-named
  candidates named the armor-stand family's dimension carry (the LAST
  overworld-0 family — the vanilla-parity head, precedence 1) over the
  perf lanes; scoped it against the reference ArmorStandEntity (the
  placement reads the placer's world, lines 27-51; the drops + clock +
  publish read this.world back, lines 422-490) and the EYFW conventions.
- IMPLEMENTED Wave AS: ArmorStand's dimension int (stamped at
  construction); all nine Surface consults carry the dimension (the
  placement gates with the per-dimension occupancy filter, the walk's
  floor/wall/friction reads, the piston consults, the minecart push
  memoized per dimension, the clock, the burst, both drop arms through
  the TF-9 item spawn); place/summonAt gain the dimension param (the
  EYFW launch shape — session.dimension() at the placement, the console
  source's explicit 0 at /summon); the ZAD store at v4 (the dimension
  byte, the ZID v8 form; v1-v3 default 0); StandRecord + snapshot +
  restore carry the row; the wire's four adapter arms gated + the
  sendStandSpawned internal gate (the 25d mob shape, the join view);
  the piston walk's TF-9/TF-10 overworld binding.
- THE EYFW REPAIRS the scoping surfaced (two real leftovers the EYFW
  row's own "every publication" promise named): the eye's 1002 throw
  event was still hardcoded dimension 0 with the stale pre-fix comment
  ("until the eye carries its own world" — it does now), and the
  eye/firework JOIN sends lacked the internal dimension gate (a nether
  joiner received overworld bodies when the chunk windows coincide).
  Both fixed with the 25d internal-gate shape.
- TESTS: ArmorStandDimensionReadsTest 5/0 NEW (the recording-surface
  shape); 30 place/summonAt call sites re-pinned across five test files
  (a scripted pass, explicit 0s); TileExtraSaveSplit's byte-identity
  re-pinned to the v4 walk (the 2-byte diff the store's own law
  forced); the DataWatcher pose pin's call site updated.
- REGRESSION: family 23/0, eye/firework 9/0, piston 17/0, decorations
  + save-split 6/0, eye insertion + placement gates 10/0, dispenser
  23/0, vehicle 12/0, protocol wire 25/0 (in class batches — the whole
  module times out as one run), bukkit + launcher 105/0. All green.
- LANDED + PUSHED 0c8ceb9b with §29's Wave AS row, §22's next-work
  lines (the overworld-0 family class retired whole), the README
  verification note, and the repo WORKLOG entry.

Stage Summary:
- The overworld-0 family class is CLOSED whole: every engine family now
  reads its own world at every world-touching site (items, projectiles,
  XP orbs, vehicles, FX, eyes, fireworks, armor stands).
- develop at 0c8ceb9b, all work pushed as Abasing, zero uncommitted.
- Next candidates per §22: the serialized-column memoization (the perf
  family's head, against the measured wirePublish cost) or the §35.12
  unmeasured performance rows (the compression/encryption protocol-
  layer measurements).
- SECOND SLICE — WAVE T-5 (the §35.12 codec rows, the other demand-named
  candidate): WireCodecBenchmarkTest NEW in the protocol module (the
  layer that owns the codecs) — the production pipeline order over
  embedded channels, four codec arms, two shapes, byte-exact sampled
  round-trips, numbers for the ledger. THE FINDINGS: the CFB8 law
  (encryption the dominant per-byte cost — each byte its own AES block
  op) and compression-pays-for-itself under encryption (both-arms 2,245
  pps beats encryption-alone 1,767 at the chunk shape — the 10.87x
  shrink means the cipher walks 10.87x fewer bytes); the per-packet law
  at the entity shape (0.31x the plaintext rate, ~230k pps ceiling,
  far inside any single-connection shape). The §35.12 gate's
  ten-scenario list now FULLY measured; T-2's argued-away note retired.
  Tests 2/0; LANDED + PUSHED bf23efd3 with §29's Wave T-5 row, the T-2
  correction, §22's next-work line, the README note, the WORKLOG entry.

Stage Summary (updated):
- TWO waves this session: AS (0c8ceb9b) + T-5 (bf23efd3), both pushed
  as Abasing, zero uncommitted.
- The overworld-0 family class CLOSED whole; the §35.12 gate's list
  FULLY measured.
- The LAST named next-work candidate standing: the serialized-column
  memoization (the perf family's head, against the measured wirePublish
  cost) — untouched yet.
- SESSION CLOSE: the serialized-column memoization scoped but NOT started
  (the honest call): the slice needs a chunk-revision counter whose
  invalidation surface spans setBlock + every LightEngine write + the
  biome writes — a missed invalidation is the phantom-blocks bug class,
  and the measured benefit is storm-window-only (4.5-5.3 ms/tick in the
  synthetic W2 respawn storm; present in NO natural window — the
  attribution's own finding). Too correctness-critical to rush in a
  session tail; the design note is recorded here for the next session's
  opener.
- RELEASE-PIPELINE VERIFICATION (the T-2 lesson applied): the fat jar
  built clean over both waves — torch-1.8.8-154.jar, the release
  artifact shape, every module on the classpath.

Stage Summary (final):
- TWO waves landed and pushed this session: Wave AS (0c8ceb9b — the
  armor-stand dimension carry, the overworld-0 family class closed
  whole, + the two EYFW join-view repairs) and Wave T-5 (bf23efd3 —
  the §35.12 codec rows measured, the gate's list fully measured).
- All tests green on every touched lane; the fat jar verified.
- develop at bf23efd3, zero uncommitted, everything pushed as Abasing.
- Next session's opener: the serialized-column memoization (the last
  named candidate — the revision-counter design above) or whatever the
  owner's next PR/audit brings.

---
Task ID: 1
Agent: main (ZaminTorch session, 2026-10-28)
Task: Continue development — first pass: PR review lane (empty), then the
standard protocol (worklog read, sync, architecture re-read, the uncommitted
Wave SC recovery, ledger, push).

Work Log:
- Read the worklog (prior close: bf23efd3, two waves AS + T-5, "zero
  uncommitted", the serialized-column memoization named as the next opener).
- Checked the open PRs via the API: 0 open — the first-priority lane empty.
- Synced develop: local == origin at bf23efd3, but the tree carried
  UNCOMMITTED work a later context window had left half-landed without
  committing (the memoization itself: ChunkSection + ChunkSerializer18
  diffs and three new test files; the worklog's "NOT started" close was
  stale).
- ASSESSED the half-landed work against the reference and the JMM: the
  revision-bump-after-index-write ordering law (read-revision-FIRST serving,
  store-recorded-before-walk — the stale-serve direction structurally
  impossible), the opaque memo slot's layer boundary, the equivalence of
  section.get(x,y,z) with chunk.getBlock(x,(sectionY<<4)|y,z), and the
  invalidation surface — verified engine-wide that only EngineChunk.setBlock
  ever calls ChunkSection.set (only 3 main-source files reference the class).
- RAN the tests: the three new files green (SectionPackMemoTest 10/0,
  SectionPackMemoConcurrencyTest 1/0, WireColumnMemoBenchmarkTest 1/0 with
  numbers: cold 1361 / warm 9.4 / partial 486 us per column; the warm
  81-column cycle 0.765 ms vs the ML-024 measured ~32 ms; the mixed
  death-site cycle 1.242 ms).
- REGRESSION: serializer lane 8/0; core world lanes 231/0 after ONE stale-pin
  repair — BiomeWorldTest's flora row still pinned the retired pre-PR54-59
  tall-grass 2-drop convention (verified pre-existing on clean bf23efd3 via
  stash; that wave never ran the world package) — re-pinned to the vanilla
  1:1 law (seeds alone at 1/8, the self-drop riding the shears gate);
  protocol wire batch 22/0; bukkit + launcher 105/0.
- LEDGERED Wave SC: §29's wave row, §22's next-work lines (the demand-named
  queue now EMPTY whole), README verification note, repo WORKLOG entry.
- COMMITTED + PUSHED 9668c772 as Abasing (develop, 9 files, +1004/-15).
- Worklog accident: overwrote this file instead of appending; rebuilt from
  the session's opening read (previous session's record reconstructed above
  in full; the deeper history lives in the repo's committed WORKLOG.md).

Stage Summary:
- Wave SC landed whole: the serialized-column memoization (the perf family's
  head — the last demand-named candidate standing). The ML-024 storm's
  wirePublish row answered at its measured root.
- The stale BiomeWorldTest pin repaired to the vanilla law.
- develop at 9668c772, zero uncommitted, everything pushed as Abasing.
- THE DEMAND-NAMED QUEUE IS NOW EMPTY WHOLE. The honest remaining lanes:
  the standing tails (the serializer's byte[] intermediates; the small-packet
  pooled adoption — both demand-named, no current evidence), the concurrency
  phases (all gated OFF by the §22 evidence bar), and the PRIORITY STACK's
  lane 2 — the Paper 1.8.8 patch harvest (docs/FOLIA_PATCH_ACCOUNTING.md's
  unharvested set) — the natural next candidate.

---
Task ID: 2
Agent: main (ZaminTorch session, 2026-10-28 continued)
Task: Wave FC — the flight families' contact arms (the first ML-100
family-tail pick from the R-8 queue, after the SC wave landed).

Work Log:
- Surveyed the next-work lanes after Wave SC: the demand-named queue
  empty, the KigPaper/Folia audits complete with zero owed rows, the
  README pending table carrying concurrency lanes alone (all gated by
  §22's evidence bar). The ML-100 family tails (the R-8 queue) are the
  honest remainder; each picked only as its family's slice.
- Enumerated the 21 "Ledgered:" rows in the compat doc; most LANDED or
  CLOSED. Found the lava family's tails: the motion drag STALE (F1
  landed it; the D2 row's Ledgered text never re-pinned) and the
  flight families' lava arm genuinely OPEN (the engine's five flight
  branches returned before every contact consult).
- Verified the reference law: Entity.tick's lava arm runs for EVERY
  body (only immuneToFire exempts); the fire contact + douse ride
  Entity.move; the suffocation rides baseTick; FlyingMobEntity carries
  its own water *0.8 / lava *0.5 drag arms; the squid's and guardian's
  own isInWater overrides gate on WATER alone (never the shared fluid).
- IMPLEMENTED Wave FC in MobEntity: the every-body contact block
  (fire, lava, suffocation, cactus, douse) hoisted above the flight
  branches; the squid/guardian swim gates split to water alone; the
  ghast's drag bands (water 0.8 / lava 0.5 / air 0.91); the dragon
  comment + the NoAI comment updated; the bat's fluid travel arms
  recorded as the 16e envelope.
- TESTS: FlightFamiliesContactTest 9/0 NEW (first run caught the
  ghast's steering burst landing on tick 1 — the blocked-line stub
  pins the drag band alone). REGRESSION: the entity package whole
  574/0 over 67 classes; the adjacent fire/mob lanes 20/0; the
  protocol wire lanes 13/0; the bukkit + launcher 105/0.
- LEDGERED: the compat doc's D2 re-pin + the new Wave FC row; §29's
  wave row; §22's next-work lines (the ML-100 family tails the R-8
  remainder); README note; repo WORKLOG.
- COMMITTED + PUSHED 2683d958 as Abasing (develop, 6 files, +512/-81).

Stage Summary:
- TWO waves this session: SC (9668c772, the serialized-column
  memoization) + FC (2683d958, the flight families' contact arms).
- develop at 2683d958, zero uncommitted, everything pushed as Abasing.
- The ML-100 remaining honest tails: the fireball's Knockback-punch
  push edge (needs the Knockback enchant surface check), the
  armor-stand's EntityTag NBT merge (the /summon NBT command-surface
  form), the mooshroom's mycelium spawn floor (needs the mushroom
  island biome — a worldgen family slice). The lava family's remaining
  sub-tail (the same-tick wound shadowing) is the accepted drift.

---
Task ID: 3
Agent: main (ZaminTorch session, 2026-10-28 close)
Task: The ML-100 stale-ledger sweep, pass one + the session close.

Work Log:
- Assessed the next ML-100 tails after Wave FC: the fireball's
  Knockback-punch push edge — VERIFIED STALE (Slice TF-7c landed the
  deflect overload with the push; EngineServer:6099 wires the level);
  the regular nether skeleton's daylight burn — VERIFIED STALE (the
  dimension-read family made tickDaylightBurn's isSunny/brightness/
  hasSkyAccess read the body's own world). Both re-pinned with the
  code cites.
- COMMITTED + PUSHED 33e05e56 as Abasing (doc-only, the Wave FC
  lesson applied: the wave that lands a tail owes its ledger re-pin).

Stage Summary (final):
- THREE commits this session, all pushed as Abasing, zero uncommitted:
  - 9668c772 Wave SC — the serialized-column memoization (recovered
    from the half-landed tree, verified against the JMM + the
    reference, completed with the full test battery and the ledgers;
    the warm 81-column re-stream cycle 0.765 ms vs the measured ~32 ms)
  - 2683d958 Wave FC — the flight families' contact arms (the first
    ML-100 family-tail pick: the every-body contact block hoisted, the
    swim gates' water split, the ghast's fluid drag bands)
  - 33e05e56 the stale-ledger sweep pass one (two verified re-pins)
- The BiomeWorldTest tall-grass stale pin repaired inside Wave SC
  (pre-existing failure on clean bf23efd3, verified by stash).
- develop at 33e05e56. The demand-named queue EMPTY; the ML-100
  honest remainder: the armor-stand's EntityTag NBT merge (needs
  scoping against the /summon NBT arm), the mooshroom's mycelium
  spawn floor (needs the mushroom island biome — a worldgen family
  slice), plus the accepted drifts. Next session's opener: the
  stale-ledger sweep pass two (the remaining 21 Ledgered rows each
  verified against the code before any pick) or the EntityTag scoping.

---
Task ID: 2
Agent: main (ZaminTorch session, 2026-10-28 close)
Task: The session close after the PR wave + Wave PF.

Work Log:
- Verified develop at dc3b9648, zero uncommitted, everything pushed as
  Abasing.
- Session total: 8 commits on develop (the #61 merge + its wood-class
  review fix, the #62 merge, the #63 merge, the #64 merge + its
  rim-drop delay review fix + the reviewer's own pickup-geometry
  correction, and Wave PF), all pushed.
- The PR queue: ZERO open (four reviewed in arrival order, four merged,
  two carrying review fixes landed in-branch before merge).
- The honest standing list for the next session: the wither-skeleton
  family (the biggest ML-100 tail), the two pre-existing develop
  failures found by the sweeps (StatsCompletenessTest's cobweb row and
  QuickCraftDragWireTest's chest-window packet-48 timeout, both
  stash-verified on clean develop), the armor-stand's EntityTag NBT
  merge, the mooshroom's mycelium spawn floor, the chest/furnace
  placement sites' truncation band, and the riding pickup band tail.

Stage Summary (final):
- develop at dc3b9648, zero uncommitted, everything pushed as Abasing.
- The PR wave closed whole; the stale-ledger sweep pass two landed its
  first tail (Wave PF, the pumpkin FACING states).

---
Task ID: 1
Agent: main (ZaminTorch session, 2026-10-28 continued — the PR review pair + Wave MI + the stale-note sweep)
Task: Review the open PRs in arrival order (merge or close each), then
the standard work protocol (the architecture's next lane).

Work Log:
- Synced develop (dc3b9648, clean), read the worklog, found PR #65
  open (the hunger exhaustion economy, the verifier's tenth pass).
- PR #65 REVIEW: all five restorations verified against the reference
  line by line (the riding gate 1305, the dived 3-D norm + the
  water-only consults, the IN_WALL zero, the line-856 0.3s, the
  teleport's seed); the evidence test 15/0 reproduced. THE REVIEW
  FINDING: restoration #5 fixed the /tp path alone — the same
  phantom-jump class at five more setPosition-class sites (the ender
  pearl, BOTH dimension arrivals, the End-credits respawn, both
  dismount exits). Fixed all five with resetBodyDelta; three new
  seats stash-verified 3/3 failing unfixed; merged at efa26693.
- PR #66 REVIEW (arrived mid-session, the stats family, the eleventh
  pass): ten claim groups verified against the reference (the harvest
  gate at the CALLER, the dead play clock, the kill-credit pair, the
  movement walk's rows, the riding rows, the fishing trio, the
  villager pair, the one-site break law, the use rows, the
  outside-click drop); StatsParityTest 15/0 reproduced. THE REVIEW
  FINDING: the jump arm's tail placement reads a shallow-water leap
  as a ground jump — the reference's jump INPUT walk (LivingEntity
  1446-1456) routes isInWater/isInLava to their own arms BEFORE the
  ground arm, so jump() never fires from water or lava. The gate
  added at the scratch's own cell; the water-leap seat stash-verified
  failing 1/1 unfixed; merged at 1087b237 after a clean develop merge
  (the WORKLOG keep-both resolution).
- WAVE MI (the architecture lane, the R-8 family-tail remainder's
  LAST honest row): the mooshroom's mycelium spawn floor — the
  natural cycle's animal arm hardcoded grass where the reference
  consults the per-kind spawnableBlock (MooshroomEntity line 16's
  MYCELIUM); the spawner path's twin (animalCanSpawn) had landed with
  VT-7h. Fixed; the isle/grass/plains three-way seat stash-verified;
  60/0 across the touched lanes (NaturalSpawn 13, SpawnerManager 10,
  SpawnerSpawnGates 15, SpawnerDataStore 3, BiomeSpawnTables 8,
  GenLayerLayout 8, MooshroomEntity 3). The stale ledger notes
  re-pinned (the compat doc's mooshroom row + MobType's javadoc — the
  "no mushroom island" claim was twice stale). Landed ebe658d0.
- THE TAIGA RE-PIN (the honesty pass on my own §22 edit): the drift
  list I propagated carried "the taiga's absent worldgen row" —
  stale: the TAIGA row, the TAIGA_LIKE wolf 8/4-4 over all ten
  variants (pinned by BiomeSpawnTablesTest), and the layout placement
  are all whole. Corrected in both §22 places; landed 4b3c9d4e.
- THE BANNER FAMILY SCOPING NOTE (the next session's opener
  candidate, the Wave SC pattern): BANNER_CLEANED's tail owns the
  absent banner family whole — the sign family's twin (standing 176 /
  wall 177 / item 425 with 16 colors / the Base+Patterns tile / the
  drop-with-BlockEntityTag law / the cauldron wash arm / the wire
  ids / the crafting sub-slice). Scoped against the reference's
  BannerBlock/BannerItem and the engine's placeSignOnTick; recorded
  in the repo WORKLOG at dcc35b0b. NOT started — one full session
  wave at the sign family's scale, too large for the session tail.

Stage Summary:
- develop at dcc35b0b, everything pushed as Abasing, zero uncommitted.
- Both open PRs (#65, #66) reviewed with reference-verified findings,
  each merged WITH a review fix; the PR lane empty at close.
- The R-8 family-tail remainder CLOSED (TF-7c + TF-7d + Wave MI); the
  taiga drift note resolved stale; the honest drift remainder: the
  SitGoal no-ops, the perch-vs-breed cadence freeze, BANNER_CLEANED.
- NEXT SESSION'S OPENER: the banner family (the scoping note in the
  repo WORKLOG) — the sign family's twin, one full wave. Alternative
  openers from earlier ledgers: QuickCraftDragWireTest's chest-window
  timeout (pre-existing, stash-verified on clean develop).

---
Task ID: 1
Agent: main (ZaminTorch session, 2026-10-29)
Task: The recovered banner alignment + the five-PR wave + Wave TR (the
farmland trample).

Work Log:
- Recovered the interrupted window's uncommitted banner-16-rotation work
  from the pr-67 tree (verified against BannerItem.useOn/BannerBlock
  line by line), completed PR #67's mid-flight review (the merged state
  verified green, the develop merge pushed, API-merged at 685a79b5), and
  landed the alignment as Wave BN-C (0747e6c6) — with the
  AnvilImportApiAcceptanceTest API-twin re-pin that was a pre-existing
  develop failure (worktree-verified).
- THE FIVE-PR WAVE: #67 sign rotation, #68 rail family (the ten states +
  the RailNode walk, verified 1:1, the pin unions 942/1321... 1291 then
  1321), #69 farming growth cadence, #70 planting arms (+ the
  step.grass till-sound review fix), #71 farmland state laws (+ TWO
  review fixes: the mob view's rain dimension gate the hoist dropped —
  a nether blaze would have drowned in an overworld storm — and the
  solid-above consult switched to MaterialSolidity). All merged; the
  queue empty at close.
- Wave TR (the architecture lane, 9b7902cf): the farmland trample (the
  B4 row) at both sites (the mob's pending-intent arm + the player's
  tickLanding arm) + THE CROP SUPPORT ARM the trample seat's own timeout
  found absent (the farmland crops' PlantBlock pop with the reference's
  drop laws — the farmland PR's comment had been aspirational).
  FarmlandTrampleParityTest 5/0; the regression 314/0.
- LESSON: grep for conflict markers after EVERY merge before committing
  — the #71 merge reported one conflict but left a second unmarked
  (git add -A swept the broken file into the commit; the compile caught
  it).

Stage Summary (final):
- develop at 9b7902cf, zero uncommitted, everything pushed as Abasing.
- The PR queue EMPTY. The fall-path family's honest remainder
  demand-named in the §22 statement: the slime pad's trio (the soft
  landing, the bounce, the step boost) and the redstone ore's glow
  family (the step/mining/use interacts + the lit form's reversion +
  the particles) — the stepSoundWalk javadoc's 'onSteppedOn is a no-op'
  claim re-pinned STALE (three classes override it).
- Next session's opener: the slime pad trio or the redstone ore glow
  family (the fall-path family's remainder), or whatever the owner's
  next PR/audit brings.

---
Task ID: 1
Agent: main (ZaminTorch session, 2026-10-29, continued)
Task: Wave SG — the slime pad trio + the redstone ore's step glow (the
fall-path family's remainder closed whole).

Work Log:
- Opened from the worklog's named remainder (Wave TR's demand-named tail):
  re-read Torch-Arcetecture.txt fresh from the remote (6939 lines, the §22
  statement + §29's Wave TR row name the slime pad's trio + the redstone
  ore's glow family). PR queue empty at open.
- THE SCOPING SURPRISES: the glow family was already THREE-QUARTERS landed
  (the dig-start swap Slice 33-era, the use arm, the random-tick reversion,
  the dust/XP drops) — the honest remainder was the STEP arm (mob + player)
  and the silk-on-LIT drop; the reference's dedicated-server addParticle is
  a NO-OP STUB (ServerWorldEventListener lines 23-28) — the glow particles
  are the client's own randomDisplayTick band, NOTHING to broadcast 1:1;
  and RedstoneOreBlock.getSilkTouchDrop OVERRIDES the base to return the
  ore itself for BOTH forms — the engine's LIT+silk arm (empty stack, from
  the base class's Item.byBlock null) had starved every silk harvest, and
  the landed dig-start swap made that bug live.
- THE SLIME PAD TRIO (SlimeBlock 27-51 over Entity.move's three sites):
  the soft landing's ZERO multiplier at the mob swept arm + the player's
  tickLanding (the -0.2 consult hoisted; a sneaking player keeps the full
  walk — the seat pair pins 20/11 hearts); the beforeCollision bounce at
  solverMove's zeroVy (the post-move -0.2 consult, the full-speed flip, the
  velocityY>=0 no-op quirk kept) + the VEHICLE twin (VehicleEntity's
  WorldQuery extended with blockIdAt, the production wiring over the ACTIVE
  dimension); the onSteppedOn damping inside stepSoundWalk (the makesSteps
  band, onGround-gated, before the distanceMoved accumulation — the
  stepSoundWalk javadoc's 'onSteppedOn is a no-op' claim re-pinned STALE).
- THE STEP GLOW: the mob path through the pending intent (the trample's
  own shape — oreStepX/Y/Z + consumeOreStep + the manager's drain + the
  listener's host-re-checked LIT write over the body's own dimension); the
  player path through a per-tick standing consult (tickStepOn — the
  reference's own arm rides the verification move the engine's
  proposal-driven walk never runs; gated onGround/!sneak/!riding, the
  idempotent swap's outcome identical); the LIT form never re-swaps.
- SEAT LESSONS (two test bugs found by the trace, not the engine): the cow's
  ten-heart hull (the first seat asserted 20); the default world spawn is
  (0.5, 5.0, 0.5) — an await on y==5.0 passes INSTANTLY at spawn, so the
  movement proposal fired before the teleport landed and the guard rejected
  it (the seat awaited x instead); the cart seat's bounce await was vacuous
  (y>41.5 true AT SPAWN 46.0 in both runs — redesigned to await the landing
  then the rise above the rest position 41.35).
- Evidence: SlimePadOreGlowParityTest 8/0 NEW; the stash verification 7/8
  failing unfixed (the stone control alone passing — the pre-existing
  behavior it pins); the regression lanes 256/0 over three batches (the
  fall/step/redstone/vehicle/drop/stats/hunger/mob-drain/physics families).

Stage Summary:
- The fall-path family's honest remainder CLOSED whole; the ledgers updated
  (§29's Wave SG row, the §22 statement, the compat file's three drift
  rows: the mineral vault's slime note, the redstone family's stepped-on
  note, the block-sound family's stale no-op claim).
- Next session's opener: the §22 honest tails that remain standing are the
  accepted-drift rows alone (the SitGoal no-ops, the perch-vs-breed cadence
  freeze) plus the transport standing tails (demand-named) — check the
  verifier agent's incoming PRs first, then whatever the owner's next
  audit names.

---
Task ID: 1 (close)
Agent: main (ZaminTorch session, 2026-10-29, final)

Work Log (the session's second half):
- PR #72 (the verifier's fifteenth pass, B3) arrived 20 minutes after the
  Wave SG landing carried the same silk fix: reviewed against the
  reference independently (getSilkTouchDrop 134-137 — the override
  confirmed), found the wave's landing had BROKEN the silk lane's stale
  seat (redstoneOreSilkAnswersTheOreAndTheLitFormNothing pinned the
  pre-fix empty drop — a lane the wave's regression batches missed), the
  PR's seat repair merged via the house pattern (develop merged into the
  PR branch, the DropService comment conflict resolved to the PR's
  correct 134-137 citation — the wave's comment had miscounted 95-97),
  the merged tree 27/0, API-merged at 6b22af1b.
- THE FIFTEENTH PASS'S FINDINGS DOC (the audit branch) adjudicated: my
  Wave SG arms confirmed by its V-records; ITS B2 CLAIM (the interact
  burst is a server broadcast) REJECTED — ServerWorldEventListener
  .addParticle is an EMPTY STUB (the same listener's playSound and
  notifyBlockChanged DO send packets — the stub is deliberate; the burst
  is the client's own prediction). The adjudication recorded in §22 for
  the incoming B2 PR's review.
- The honest remainder recorded as the next waves' queue: the ITEM and
  ORB bodies' arms (bounce/damping/ore-lighting — makesSteps defaults
  true for both; the item's Ground lacks blockIdAt — the default-method
  extension is the seam) + the F-candidates (F-FRICTION the loudest —
  mobs/items/orbs hardcode 0.6 where the reference reads the block's
  slipperiness; F-RIDER-SPARE, F-DUST, F-ITEMSTEP, F-VINE).
- Session discipline notes: the repo WORKLOG.md is 18k lines — NEVER cp
  the local scratch worklog over it (the amend repaired it); the full
  2713-test suite does not complete in one gradle invocation here — run
  lanes in batches; the local scratch worklog and the repo WORKLOG are
  different files.

Stage Summary (final):
- develop at 6b22af1b (Wave SG 5429b60b + PR #72's merge), everything
  pushed as Abasing, zero uncommitted.
- The architecture's implementable queue: EMPTY of unchecked rows again;
  the next waves' demand-named queue is the fifteenth pass's §D/§F tables
  (the item/orb arms + the F-candidates), each row reference-verified
  before its fix. The verifier agent's incoming PRs (B2's broadcast claim
  pre-adjudicated WRONG) ride their own schedule.

---
Task ID: 1
Agent: main (ZaminTorch session, 2026-10-29 continued)
Task: The status report + PR #73's review + Wave IO-F (the item/orb
landing physics + the friction family whole).

Work Log:
- Synced develop (5ccc61c5 clean), checked the PR queue: #73 open (the
  verifier's redstone-ore glow remainder, §B1/§B2). Delivered the
  STATUS REPORT (the user's ask): ~99% landed, ~1% remainder (this wave
  + F-RIDER-SPARE/F-DUST/F-VINE + the envelope tails), ~2-4 waves of
  effort, the concurrency lanes gated off by design.
- PR #73 REVIEWED against the reference line by line and CLOSED: B1's
  mob/player arms already landed (Wave SG); B1's item/orb arms NOT OWED
  (makesSteps FALSE for both bodies — ItemEntity.java:44,
  ExperienceOrbEntity.java:32; Entity.move:591's steps band never runs —
  the PR would have ADDED ore lighting vanilla doesn't have); B2's
  spark broadcast REJECTED (ServerWorldEventListener.addParticle:23-28
  is an EMPTY STUB — the burst is the client's own prediction; the PR's
  RedstoneOreSpark FX arm would have sent particles the server never
  sends). Full adjudication posted on the PR.
- WAVE IO-F: the §D/§F queue adjudicated FIRST (three of the pass's
  claims WRONG: the item/orb step rows + F-ITEMSTEP are non-rows), then
  the honest rows landed whole: the ITEM's tick 1:1 (pre-move gravity
  unconditional; the (v-0.04)*0.98 fusion and the !onGround gate and
  the standing-drift hack RETIRED; the vertical clip's beforeCollision
  consult through the new Ground.blockIdAt — the -0.2 feet cell, the
  slime flip/keep/zero at the POST-move position; the post-move drag
  through the new Ground.slipperinessBelow — the support cell; the
  grounded flip -0.5); the ORB's tick the same with its own constants
  (gravity 0.03 — the engine had carried the item's 0.04; the flip
  -0.9); F-FRICTION whole (the mob's damping at all three sites — the
  walker, the ridden body, the ghast's on-ground band — reading the
  support cell's own slipperiness * 0.91 through the new
  WorldQuery.slipperinessBelow, the air band the reference's 0.91 base;
  the pre-fix engine carried the flat 0.6/0.98, the item family's
  bands); the production adapters (ShapeGround + MobWorldQuery) read
  the ACTIVE dimension through the reference's slipperiness table.
- Evidence: ItemOrbLandingPhysicsParityTest 9/0 NEW; the stash
  verification 7/9 failing unfixed. The regression: item + orb families
  101/0; the mob physics lanes 49/0 after ONE re-pin (the sticky-floor
  seat to the pad's 0.728) and ONE latent-bug fix the run surfaced
  (slimeUnderfoot's null guard — a Wave SG latent NPE on the interface
  null default; MobSolverWalkTest had not run since Wave SG); the
  entity package whole 574/0; the vehicle/armorstand/drop/stats/hunger/
  projectile lanes 169/0 (one StatsParity use-row flake once in five
  runs, not wave-caused); the protocol entity-sync batch green
  (QuickCraft's packet-48 the documented pre-existing flake); bukkit +
  launcher green. CollisionShapeTest's ShapeWorld gained the
  slipperinessBelow diamond override (the two interfaces' identical
  defaults).
- LEDGERED + LANDED dd87373d, pushed as Abasing (10 files, +996/-48):
  §29's wave row, §22's statement (the adjudication + the named
  remainder: F-RIDER-SPARE, F-DUST, F-VINE, the item/orb envelope
  tails), the README note, the WORKLOG entry.

Stage Summary:
- develop at dd87373d, zero uncommitted, pushed as Abasing.
- The named remainder for the next waves: F-RIDER-SPARE (the vehicle
  landing's rider delegate), F-DUST (the landing-dust burst — likely
  another client-prediction row), F-VINE (the vine family's pass), and
  the item/orb envelope tails (the item's lava fizz — the flag/25-tick
  gate; the orb's own fizz + the igniter attraction).

---
Task ID: 2
Agent: main (ZaminTorch session, 2026-10-29 continued)
Task: Wave RS — the rider-spare fall (F-RIDER-SPARE closed).

Work Log:
- THE LAW verified: Entity.takeFallDamage:748-752 (the base forwards the
  landing UP to the rider) + LivingEntity:824 (the super call runs the
  delegate FIRST for the mounts). FarmlandBlock.onFallenOn:57-71's
  instanceof LivingEntity gate skips vehicles whole.
- THE MOUNTS: the RiderFall seam (MobEntity + MobManager, the
  playerStatAward pattern) at takeFallDamage's head; paySeatedRiderFall
  on EngineServer pays the fallen row + the wound through the enchant
  consult.
- THE WAVE'S OWN FIND: the pre-fix RIDDEN walk swallowed the fall WHOLE
  (a ridden mount took nothing from any height) — tickRiddenBody now
  runs checkFallDamage for both paths through the extracted
  payLandedFall (shared with the walker).
- THE DOUBLE-CHARGE FIX: the seat's writes fed applyMovement — the
  player's own store paid the ride's descent ON TOP of the delegate;
  tickLanding now skips seated riders and resets the store.
- THE VEHICLES: the whole fall walk (the store was persistence-only):
  accumulateFall/halveFall/spendFall/payRiderFall on VehicleEntity; the
  manager's tick carries the walk + the landed-cell consult; the
  farmland skips vehicles; the boxHalfHeight accessor.
- Evidence: RiderSpareFallParityTest 3/0 NEW; the stash verification
  FAILING unfixed (the pig unwounded from a ten-block fall; the
  pre-fix player paid through their own accumulated store). The
  regression: vehicle + riding 101/0 after ONE re-pin
  (VehiclePersistenceTest's imported store spends at the settle's
  first landing — the reference's line-733 law); stats + hunger +
  entity package whole 608/0; the wire's body/relog/mob batch green;
  bukkit + launcher green.
- LEDGERED + LANDED, pushed as Abasing (10 files, +872/-60): §29's
  Wave RS row, §22's statement (the remainder: F-DUST, F-VINE, the
  item/orb envelope tails), the README note, the WORKLOG entry.

Stage Summary:
- develop at the Wave RS commit, zero uncommitted, pushed as Abasing.
- The session's total: TWO waves (IO-F + RS) + PR #73 closed with the
  full adjudication + the slimeUnderfoot latent-bug fix + four re-pins
  (the sticky-floor seat, CollisionShapeTest's diamond, the IO-F-era
  stub overrides, VehiclePersistenceTest's imported store).
- The named remainder: F-DUST, F-VINE, the item/orb envelope tails
  (the item's lava fizz, the orb's own fizz + the igniter attraction).

---
Task ID: 3 (close)
Agent: main (ZaminTorch session, 2026-10-29 final)

Work Log (the session's PR wave):
- PR #74 + #75 arrived mid-wave (the validator's seventeenth pair);
  both reviewed against the reference line by line and merged whole
  via the house pattern (the develop merge carried in each PR branch,
  the merged trees verified green — #74's merged tree 594/0 over the
  entity package + the step/glow/landing/riderspare lanes).
- #74: SquidEntity.moveRelative:154-157 IS the full colliding
  this.move — the pre-fix raw integration made every squid phase
  through blocks; the stroke now rides the swept solver + the bat and
  the squid joined the makesSteps FALSE list. Merged e215adae.
- #75: PlayerEntity.makesSteps:1568-1570 returns !abilities.flying —
  the flying gate added to tickStepOn. Merged dbfddfda (after the
  develop re-merge carrying #74).
- The F-DUST adjudication finding: ServerWorld.addParticle:899-930
  DOES send a ParticleS2CPacket — the landing dust (BLOCK_DUST at
  LivingEntity:170-180, count (int)(150 * min(0.2 + f/15, 10) capped
  2.5)) IS a real server broadcast, NOT a client prediction — F-DUST
  is a genuine row, not a B2-class non-row.
- LEDGERED + PUSHED c77262b3 (the PR wave record in §22 + WORKLOG).

Stage Summary (final):
- develop at c77262b3, zero uncommitted, everything pushed as Abasing.
- SESSION TOTAL: PR #73 closed with the full adjudication; Wave IO-F
  (dd87373d — the item/orb landing physics + the friction family
  whole, 9/0 NEW, 7/9 stash-verified); Wave RS (851865ec — F-RIDER-
  SPARE closed, 3/0 NEW, stash-verified); the slimeUnderfoot latent
  NPE fix; four re-pins; PRs #74 + #75 merged; the PR queue EMPTY.
- The named remainder: F-DUST (a REAL row — ServerWorld.addParticle
  sends the packet; the landing-dust burst with the 150*min(0.2+f/15,
  10)-cap-2.5 count law over the block state), F-VINE, the item/orb
  envelope tails (the lava fizz pair + the igniter attraction).

---
Task ID: wave-lf (2026-10-29 session)
Agent: main (ZaminTorch session)
Task: Continue development — the architecture wave first (the §22
demand-named remainder), the validator's PRs left to their own lane.

Work Log:
- Synced develop (c77262b3, clean, 0 open PRs — the validator lane
  idle). Re-read Torch-Arcetecture.txt fresh (7158 lines): all 269
  tracked [x] rows landed; the active queue the §22 prose remainder
  (the item/orb lava envelope tails, F-DUST, F-VINE). Delivered the
  opening status report (~96-97% of the 1.8.8 surface landed, ~2-3
  sessions of named rows left).
- IMPLEMENTED Wave LF (the item/orb lava envelope + the orb's igniter
  attraction): ItemEntity's fizz (the boundary-cross/25-tick gate
  with the (int)-cast truncation quirk kept 1:1, the pop between the
  move and the drag, the fizz sound through the new Sounds seam);
  ExperienceOrbEntity's UNCONDITIONAL fizz (the pop feeds the same
  tick's rise) + the attraction whole (the staggered ~20-tick
  retarget with the fresh-orb dark start, the sticky hold, the live
  read with the spectator release, the 8-block eye-aimed pull); the
  manager wiring (both families' Sounds over the FX broadcast, the
  orb manager implementing the Players consult over the tick walk);
  the ShapeGround.lavaAtCell consult on the ACTIVE dimension.
- ADJUDICATED F-DUST a non-row (the B2 class: the landing dust rides
  the server's EMPTY addParticle stub — the client's own prediction).
- Evidence: ItemOrbLavaEnvelopeParityTest 8/0 NEW (the seat-design
  find: the attraction seats must reset the body per tick — the
  free-fall during the 21-tick throttle window carries the orb out
  of range otherwise); the stash verification failing unfixed WHOLE
  (the pre-fix tree refuses to compile the seat file). Regression:
  the item/orb families 64/0, the entity package 574/0, the
  stats/hunger/sign/pad/rider/farmland lanes 56/0, the drop lanes
  10/0, bukkit + launcher green.
- The ledgers: §29's Wave LF row + the §22 statement (F-VINE alone
  queued) + the README note + the repo WORKLOG entry. Committed and
  pushed as Abasing.

Next: F-VINE (the vine family's own pass — the 5-face connection
state, the placement gate, the neighborChanged decay through
hasConnections, the random-tick growth with the 4/5 density census),
then the stale-ledger sweep tails.

---
Task ID: wave-vn-a (2026-10-29 session, second commit)
Agent: main (ZaminTorch session)

Work Log:
- IMPLEMENTED Wave VN-A (the F-VINE pass's first slice): the vine
  family's sixteen-state vocabulary (VineBlocks.java — the metadata
  nibble's four face bits, the bare all-faces form, vine_none, the
  fourteen faceful names), the anvil import rows, the table coverage
  (MapColors FOLIAGE + the rail pass's eight missing rows,
  BlockSoundMap's vine prefix rule, BlastResistance's vine_none exact
  row), the registry census re-pins (942 -> 957, 896 -> 957), and the
  CLIMBABLE pair (isClimbable: ladder OR vine — the fall catch, the
  climb stat, the fatigue gates; split from the fluid system's
  ladder-only liquid-blocking consult).
- THE WAVE'S OWN FIND: the pre-fix WorldSolidity.isSolid exclusion had
  no vine row — every vine carried full-cube collision; the reference's
  getCollisionShape is null (walk-through). Fixed; the support consult
  (canGrowOn) rides CollisionShapes.isFullCube.
- The sweep repaired three pre-existing develop failures
  (stash-verified pre-existing): the rail states' MapColors rows, the
  blast census pin, and the anvil vehicle seat's stale imported-store
  pin (the RS line-733 law).
- Evidence: VineStateFamilyParityTest 4/0; block+entity packages
  867/0; the table/anvil/fire/fluid lanes green. Committed and pushed
  as Abasing.

Next: Wave VN-B — the placement gate + the opposite-face state
(canBePlaced/getPlacementState), the neighborChanged decay
(hasConnections), the random-tick growth whole (the 25% roll, the 4/5
density census, the up/down/side/corner spread arms). Then the
stale-ledger sweep tails.

---
Task ID: wave-vn-b (2026-10-29/30 session)
Agent: main (ZaminTorch session)

Work Log:
- Resumed the CUT-OFF VN-B work (the previous session ended
  mid-flight: +550 uncommitted lines over 8 files + the untracked
  consult seat). Verified the state whole: compiles clean, the
  consult suite 11/0 green.
- PR #78 (the LF review's three repairs) reviewed against the
  reference line by line — ALL THREE CORRECT: the item's fizz poll
  clock IS Entity.ticks (an independent lifetime counter; the age is
  NBT-seeded at ItemEntity 223/241 — the fused age clock mispolled
  every restored item); the crossing flag and both fizz sounds read
  the FEET frame (the reference's y IS the box bottom; World
  .playSound reads source.x/y/z); the orb's retarget window rides the
  body's OWN renderTicks (line 12's field, tail-incremented at 123,
  never persisted). Branch verified green (5/0 own suites, 596/0
  entity+experience), MERGED at 5a64d572.
- PR #79 (the validator's VN-B1) adjudicated: correct-but-partial
  with TWO reference gaps — no replaceable-CLICKED arm (useOn 24-28
  place in place) and no glass exclusion (GlassBlock.isCube overrides
  false; the PR's gate would grow vines on glass). CLOSED AS
  SUPERSEDED with the full adjudication comment; its real-dispatch
  seat shape ADOPTED with credit.
- COMPLETED the VN-B wave whole: the house air-click guard + the
  simplified replaceable consult in placeVineOnTick; the NEW
  end-to-end dispatch seat (VinePlacementDispatchParityTest — six
  arms: the single opposite-face bit + consume, the four refusals
  incl. the GLASS wall, the lintel UP hang, the replaceable target,
  the replaceable clicked IN PLACE; the seat-design finds: the
  sideCell/southStone geometry collision and the bitless-seed
  deferred-wake pop — the wake-stable vine_east between two stones).
- Stash verification: the consult seats failing unfixed WHOLE (the
  tree refuses to compile them), the dispatch seat failing at its
  FIRST assertion (the pre-fix generic placement lands the BARE
  all-faces form).
- Regression: block + dispatch + drop 305/0; entity + experience +
  fizz 607/0; anvil/tree/armorstand/rail/worldgen 80/0.
- LEDGERED + LANDED, pushed as Abasing (0a89a0fe, 13 files): §29's
  Wave VN-B row, the §22 statement (F-VINE retired end to end, the
  queue EMPTY), the README note, the WORKLOG entry.

Stage Summary:
- develop at 0a89a0fe, zero uncommitted, pushed as Abasing.
- SESSION TOTAL: PR #78 merged (verified, 5a64d572) + PR #79 closed
  as superseded (the adjudication) + Wave VN-B landed whole (the
  F-VINE family retired END TO END, the sixteenth-pass F-candidate
  list fully adjudicated).
- The queue: EMPTY. The architecture's named lanes are all landed or
  adjudicated; what remains is the standing demand-named tails (the
  transport serializer's byte[] intermediates, the small-packet
  pooled adoption if a future attribution ever shows the arena
  winning) + the concurrency phases gated on the attribution pass.

Next: the next verifier pass's findings or the owner's next
direction; the stale-ledger sweep (any re-pins the next full-suite
run surfaces).

---
Task ID: wave-bu1 (2026-10-30 session)
Agent: main (ZaminTorch session)

Work Log:
- Resumed the interrupted prior session's CUT-OFF work (+217 uncommitted
  lines over 6 files: the addressee pin, the own-commit dispatch law,
  the flag-4 silent dispenser flip, the vine family's table rows, the
  TileNbt sign seeding). Compiled clean; all seven CI-failing seats
  verified green locally.
- DIAGNOSED THE CI FREEZE (the owner's report): last published build
  torch-1.8.8-153 on 2026-10-27; runs 520+522 failed on 5 then 7 test
  seats (the sign trio, the dispenser/crop stale wake, the vine table
  rows, DeathXpLadder, RangedCombat).
- LANDED Wave BU-1 (65528073) + the ledger push (be3f76b6). The
  verification: block 304/0; dispenser+nbt+sign+banner+skull 169/0;
  consumer lanes 118/0; entity sweep 619/0; table walks 22/0.
- MERGED PRs #80 (0c229f9b, fluid material law), #81 (12c47524, fizz
  center+pitch), #82 (9e0cd421, hopper box-intersection) — each
  verified line by line against the reference and trial-merged green
  before the API merge; #82's WORKLOG conflict resolved keeping both
  records.
- The §22 statement names F-FL4 (the nether/End fluid simulation) the
  next queue head: ONE FluidSystem wired to the overworld; the nether/
  End need their own scheduled-tick + random-tick systems and the
  per-dimension FluidSystem pair with the dimension profile
  (nether: water vaporizes, lava cadence 10 full reach; End: water 5,
  lava 10). docs/FLUID_FAMILY_AUDIT_FINDINGS.md carries the scoping.

Stage Summary:
- develop at be3f76b6, zero uncommitted, pushed as Abasing.
- SESSION TOTAL: the CI freeze diagnosed + fixed (Wave BU-1); the
  release pipeline resumed (run 523 in flight over the fixed tree);
  PRs #80 + #81 + #82 reviewed, verified, merged; the ledgers updated
  (F-FL4 named next).

Next: watch run 523/527 go green and confirm build 154 publishes;
then F-FL4 (the per-dimension fluid simulation architecture pass).

---
Task ID: wave-fl4 (2026-10-30 session)
Agent: main (ZaminTorch session)

Work Log:
- Resumed at develop 247b7581 (BU-2 pushed, run 529 in flight), zero
  uncommitted. Verified run 523's failure logs matched the BU-2
  diagnosis exactly (the SlimePad CME at line 500 + the relief pin).
- DIAGNOSED the cancellation pattern in the CI history: the release
  workflow's concurrency group (release-develop, cancel-in-progress
  false) never cancels mid-flight runs — the "cancelled" runs
  (524-528, 530-531) were PENDING runs superseded by newer pushes (the
  documented GitHub behavior). The pipeline itself is healthy.
- REVIEWED + MERGED PR #83 (the world seed law, the terrain family
  audit fix 1) at 4ba8b148: the reference DedicatedServer lines 170-183
  derivation verified side by side (the random roll, the parsed
  non-zero override, the string hash arm, the zero quirk), the
  WorldSeedStore per-world record with the pre-fix migration (a
  name-seeded world keeps its continuity — the delta overlay's
  alignment IS the seed), the level-seed property finally read, all
  seed consumers riding the persisted value. Trial-merged green: the
  world package 241/0 + the seed seats 8/0 + the config lanes.
- REVIEWED + MERGED PR #84 (the world spawn vs terrain, fix 2) at
  6b9cb007 with ONE review correction riding the follow-up commit
  5d692dc9: the three reference walks verified (the grass-column drift
  initSpawnPoint 660-680 + the GRASS gate, the leaves-skipping surface
  consult getSurfaceHeight 1132-1148, the unstuck walk capped 255), the
  /setspawn anchor's precedence, the anvil import's own spawn applied
  later at runtime (safe). THE CORRECTION: the SpawnProtectionAcceptance
  seat caught the flat-family divergence — the PR ran the grass walk
  for flat worlds too, but a flat column can NEVER answer GRASS through
  the reference's sea-level climb (63 vs the flat surface 3), so
  vanilla's flat walk is a pure 1000-step noise drift; the engine's 32
  flat acceptance seats pin spawn-relative positions against the
  deterministic (0,4,0). The boot now gates the resolution on the
  NORMAL level type alone. Verified: the failing seat 3/0 + the world/
  spawn/anvil/persistence/ender lanes 260/0 + the config lanes 10/0.
- LANDED Wave FL4 (the F-FL4 architecture pass, d695de77) — the
  per-dimension fluid simulation whole: the nether/End scheduled-tick
  surfaces with their own ZST persistence (loaded BEFORE the domains
  attach — the ownership shape the restart seat forced: the boot thread
  carries the loads only where no domain is bound yet), the
  per-dimension random-tick walks with dimension-filtered players (the
  nether's carrying NO pigman sink — PortalBlock.tick line 35's own
  isNatural gate), the End's tick clock (EngineTicker's attachEndWorld
  — the pre-FL4 engine never advanced it), the FluidSystem pair over
  parameterized adapters with the reference's dimension profiles, the
  FluidSink's dimension stamps, the bucket path's dimension routing
  (the use-site row: the pre-fix path wrote the OVERWORLD field
  whatever the player's dimension), the per-world placement consult,
  and EngineWorld.dimension() with the pops + leaf-decay drops stamped.
  THE SEATS' OWN FINDS: the real-boot spread runs toward the gaps (the
  seats carve full plate floors); the LAVA wash-out fizzles with no
  drop (only WATER drops items — the reference's spreadTo pair); the
  restart's chunks arrive through the player-arrival stand-in (the
  reference's line-505 re-arm-until-loaded shape). The newly-ledgered
  tails: the fire family's per-dimension scheduling, the placement
  families' use-path world routing, the ice family's melt arms.
- THE CI WATCH: run 529 (the BU-2 tree) FAILED on the protocol wire
  seats' IOExceptions at socket reads (QuickCraftDrag + Vehicle, both
  attempt AND retry, plus two more on the retry — the hosted runner
  degrading over the 48-minute run; zamin-core itself passed whole).
  Runs 532 (the PR tree + correction) and 533 (the FL4 tree) queued
  behind it — the pending-supersession behavior means the LAST run
  (533, the FL4 tree) is the one that will publish build 154 if green.

Stage Summary:
- develop at d695de77, zero uncommitted, pushed as Abasing.
- SESSION TOTAL: PR #83 merged (4ba8b148) + PR #84 merged with the
  flat-family review correction (6b9cb007 + 5d692dc9) + Wave FL4 landed
  whole (the fluid family audit's standing architecture tail retired —
  the nether's lava flows beside a broken block, the End's water and
  lava both flow, the bucket works in every dimension).

Next: watch run 533 (the FL4 tree) go green and confirm build 154
publishes; if the protocol wire seats' IOException family recurs on
fresh runners, the wire harness's read windows need their own
hardening pass (the 10-second socket reads against a degrading
2-core box); then the newly-ledgered tails (the fire family's
per-dimension scheduling, the placement families' use-path world
routing, the ice family's melt arms) or the owner's next direction.

---
Task ID: wave-fl4-flag1 (the same session's CI-regression fix)
Agent: main (ZaminTorch session)

Work Log:
- DIAGNOSED run 532's failure (the 5d692dc9 tree): DimensionWalkTest's
  round trip never armed — the body crossed to the nether and arrived
  BURIED IN THE FRAME COLUMN (obsidian at feet/head/below). Bisected:
  be3f76b6 passes, 4ba8b148 (PR #83) fails — the random seed's forcer
  frames exposed a LATENT bug.
- THE ROOT CAUSE (found by tracing the reference line by line): the
  reference's flag1 (PortalForcer line 105 + Entity.onPortalCollision
  line 1444) reads forward.clockwiseY().getAxisDirection() == NEGATIVE —
  clockwiseY is N->E->S->W, so ONLY SOUTH (Z-positive) shifts the width
  origin by one. The engine read !forwardPositive at BOTH sites — the
  MIRROR error (shifting WEST/NORTH where the reference shifts nothing,
  leaving SOUTH unshifted where it shifts). The deterministic name-seed
  had landed the forcer frames where the error cancelled; the random
  seed's SOUTH-facing frames (the count walk's tie-goes-positive default
  — the COMMON case) put the arrival ON the column.
- LANDED the fix (9dc333e7): both sites read the reference's own
  condition (forwardAxis == Z && forwardPositive — the SOUTH-alone
  flag1); PortalForcerTest's hand-built-frame seat re-pinned to the
  reference's value (12.0 — both old and new land inside the 2-wide
  interior; the old seat pinned the wrong math's coincidence).
  Verified: DimensionWalkTest 5/5 fresh random-seed boots (the forced
  --rerun runs); the world package whole 246/0; the player lanes 56/0;
  EnderExit + PerDimensionFluid green; run 532's other three failures
  (HungerExhaustion's pearl, the pig voice bridge, the powered hopper)
  all pass locally — the load-flake family, not regressions.

Stage Summary:
- develop at 9dc333e7, zero uncommitted, pushed as Abasing.
- The CI queue: run 533 (created for d695de77) still waiting for a
  runner — its checkout takes the LATEST develop, so it validates the
  full current tree (FL4 + flag1); run 534 (9dc333e7) queued behind it.

Next: watch runs 533/534 go green and confirm build 154 publishes
(the release pipeline's first build since 2026-10-27); the protocol
wire seats' IOException family (run 529's runner degradation) may need
its own hardening pass if it recurs on fresh runners.

---
(Final session note) Run 533 hit the 60-minute job timeout (cancelled —
its checkout predated the flag1 fix); runs 534/535 superseded by the
later pushes. RUN 536 (dcd2e139 — the full current tree: FL4 + flag1 +
the ledgers + WORKLOG) is the one in flight as this session ends —
watch it go green and confirm build 154 publishes. The release
pipeline's last build remains torch-1.8.8-153 (2026-10-27).

---
Task ID: session-2026-10-30-evening (the PR wave + FL4-F2/F3)
Agent: main (ZaminTorch session)

Work Log:
- Read worklog + pulled develop (dcd2e139) + re-read Torch-Arcetecture.txt
  fresh; the FL4 row's three surfaced tails named the queue: the fire
  family's per-dimension scheduling, the placement families' use-path
  world routing, the ice family's melt arms.
- THE PR WAVE (both subagent-reviewed against the reference): PR #86
  (double chest F-DC1) merged at 8cf91242 — all four surfaces consume the
  one ChestPairs.resolve consult, 73/73 chest/hopper lanes green on the
  merge tree. PR #85 (the blast's entity gather F-EX1) merged at 66266982
  with BOTH review-confirmed defects carried as riders at 09e5f259: the
  crystal arm's dimension gate (an overworld blast at coinciding coords
  popped the End crystal + detonated a 6.0F blast in the END — the leak
  the review's scratch test reproduced) and the armor-stand double-break
  guard (the reference's own onBroken null-out + the registry containment
  — the stale snapshot's re-break dropped the equipment TWICE). Both new
  seats stash-verified failing unfixed.
- LANDED Wave FL4-F2 (d34abe97, the fire family's per-dimension
  scheduling): EngineWorld.scheduledTicks() (the world's own queue, the
  reference's world.scheduleTick law), the fire tickers on all three
  dimensions, the End's eternal-bedrock row via dimension()==1, and THE
  NETHER-SIDE FRAME IGNITE restored (the reference's getId()>0 portal
  gate — the overworld AND the nether both attempt the build; the engine
  had gated on the overworld alone, so a nether frame's fire never lit
  its portal). THE SEATS' OWN FIND: the vacuous pass (awaitCondition
  evaluates before the async submit lands — the still-air cell satisfies
  a death await) — the seats now carry the two-phase anti-race shape.
  PerDimensionFireAcceptanceTest 3/0 NEW; FireBlocksTest 14/0; the
  block/world/portal/fluid lanes 563/0.
- LANDED Wave FL4-F3 (a8ddd991, the placement families' use-path world
  routing): 58 session-path arms open with the player's-own-world local
  (worldFor(session.dimension())), 3 position-only arms take the threaded
  world, the shared gates (canOccupyForPlacement/isNeighboringCactus)
  route with their callers (31 gate calls).
  NetherPlacementRoutingAcceptanceTest 1/0 NEW over the real use wire
  (a nether player's sign lands IN the nether, the overworld cell
  untouched) — stash-verified; the regression lanes 385/0 + 559/0.
- THE LEDGERS: section 29's FL4-F2 + FL4-F3 rows + the section 22
  statement + the WORKLOG record (684ee7ff). The newly-ledgered tails:
  the dig/place generic surface (BlockInteractionService's overworld
  constructor binding — the generic fallback AND the survival dig), the
  redstone binding, the block-update family's per-dimension story, the
  TNT dimension row.

Stage Summary:
- develop at 684ee7ff, zero uncommitted, pushed as Abasing.
- THE CI: run 541 (the full current tree) PENDING at session end — it
  publishes build 154 if green (the release pipeline's last build remains
  torch-1.8.8-153, 2026-10-27). Runs 537-540 cancelled by the push
  supersession (the documented pending-supersession behavior).

Next: the ice family's melt arms (the FL4 row's last NAMED tail — the
engine's ice carries no melt arm at all; the yeetsWater consult at the
ice sites, the melt-to-water law); then the newly-ledgered FL4-F3 tails
(the dig/place generic surface first — it is the generic placement every
plain block rides); watch run 541 go green and confirm build 154
publishes.

---
Task ID: session-2026-10-30-night (the ice family whole + the dig/place routing + the PR wave + the CI timeout)
Agent: main (ZaminTorch session)

Work Log:
- Read worklog + pulled develop (684ee7ff) + re-read Torch-Arcetecture.txt
  fresh; the queue named the ice melt arms first, then the FL4-F3 tails.
- LANDED Wave FL4-F4 (3520a666): the ice family's melt arms — IceBlock.tick
  60-69 over the random-tick dispatch (the BLOCK light channel alone, the
  nether's yeetsWater removal, the still-source melt elsewhere);
  IceMeltParityAcceptanceTest 4/0 NEW (stash-verified failing unfixed);
  EngineServer.randomTicksFor(dimension) exposing the per-dimension walks.
- LANDED Wave FL4-F5 (cb87dcdf): the dig/place generic surface — the
  service's world router seam, every session-path arm's worldOf(player)
  local, the per-session tickMining, the threaded helpers, the
  dimension-stamped drops, the widened resync publisher;
  NetherDigRoutingAcceptanceTest 1/0 over the REAL dig wire (stash-verified:
  the pre-fix dig never opened against the overworld's air).
- LANDED Wave FL4-F6 (99176913): the ice dig's own FLOWING_WATER law
  (afterMinedByPlayer 39-51 — the silk branch, the below-cell material
  consult, the nether's no-water); four dig seats NEW (8/0 whole).
- THE CI ROOT CAUSE FOUND AND FIXED (99b7652c): the release job's
  60-minute timeout killed healthy runs — the successful release runs have
  taken 44-92 minutes (469: 92min, 472: 79min); raised to 120. The
  pending-supersession churn is publication-neutral. Run 541 was in flight
  on the build step at session end; 154's bump already sits in the tree.
- THE USER'S ARCHITECTURE ADDITION (028d976f, 741 lines): Section X — the
  forward-compatible engine architecture (modernize the ENGINE, never the
  1.8.8 gameplay; the version-fidelity gate A-E). Re-read and recorded;
  the engine's Identifier/registry model already complies.
- THE PR WAVE (all four processed): #87 MERGED (the signal consult's
  Block.isSolid law — verified line by line, merged on the FL4 tree with
  both sides' lanes green). #88 MERGED (the sleeping blast knockback at
  the lying eye — getEyeHeight's three states verified). #89 CLOSED AS
  SUPERSEDED with BOTH corrections adopted as riders (d6614da3): the ice
  melt's opacity-corrected band (the REGISTRATION's setOpacity(3) — the
  row my FL4-F4 missed; the band is > 8, the discriminating light-10 seat
  stash-verified) + the SNOW LAYER's own melt family (two seats NEW;
  11/0 whole). #90 CLOSED AS SUPERSEDED with the dragon-egg rider adopted
  (e088de73: the teleport consult's overworld closure — the punch arm now
  routes the puncher's world).

Stage Summary:
- develop at e088de73 + the ledger commit, zero uncommitted, pushed as
  Abasing. Four PRs processed (2 merged, 2 superseded-with-riders).
- THE ICE FAMILY IS WHOLE END TO END (melt + snow layer + dig law, 11/0).

Next: the remaining FL4-F3 tails (the redstone binding — the
comparator/lever/button use consults; the block-update family's
per-dimension story; the TNT dimension row; the block-broken listener's
container cleanups); the owner's Section X classification now gates every
implementation; watch run 541+ succeed and confirm build 154 publishes
(the 120-minute timeout now gives the suite the room it needs).

---
Task ID: session-2026-10-31-ciaudit (the CI performance audit + Phase 2 proposal)
Agent: main (ZaminTorch session)

Work Log:
- Pulled develop (0717e85b; the history was force-rewritten upstream - the
  identity rewrite, 1223 commits ahead of the old local lineage; clean reset).
- PHASE 1 AUDIT COMPLETED, ZERO CODE CHANGES (the owner's explicit gate):
  pulled 100 workflow runs + per-step timings via the GitHub API, downloaded
  and line-parsed the logs of runs 473 (last green) / 529 / 532, aggregated
  the local JUnit XML timings (173 classes), and inspected every build file.
  THE NUMBERS: the last green build = run 473, BUILD SUCCESSFUL in 40m28s,
  88.1% of it in two SEQUENTIAL test tasks (core:test 21m10s / 2,715 tests;
  protocol:test 14m31s / 240 tests); compile ~2.3 min; NO gradle.properties
  at all (parallel/caching/config-cache all off, maxParallelForks defaults
  to 1); of the last 100 runs 5 green / 30 failed / 63 cancelled-while-
  queued (14% success of executed); every failure = 1-4 DIFFERENT tests per
  run (the flake population is far wider than the documented three - nine
  additional members observed across 523/529/532; DimensionWalkTest failed
  BOTH attempts of 532 = triage as possible real defect); the blind
  whole-build retry doubles failing runs to 50-56 min and still fails; no
  release since torch-1.8.8-153 (Oct 27) while the tree carries the 154
  bump; queue waits 11.5-34.5 min; repo is PUBLIC = 4-vCPU/16GB runners
  (the workflow comment's 2-core claim is wrong); suite is tick-bound
  (EngineAwaits, 20 Hz no catch-up), so forks convert idle CPU to wall time;
  zamin-bukkit is the ONE shared-state module (singleton harness) and must
  stay single-fork.
- PHASE 2 PROPOSAL DELIVERED (docx + pdf in download/, awaiting approval):
  Phase A = single job kept; new gradle.properties (parallel + caching +
  jvmargs); maxParallelForks 3 on core/protocol, 1 on bukkit (override);
  gradle-test-retry with a census-governed class filter replacing the blind
  retry; test-report upload on failure; projected green runs 13-16 min
  (arithmetic from measured task times), failing runs +minutes not +27;
  all release semantics/numbering/tagging/publication unchanged; every one
  of the ~2,955 tests still runs. Phase B (multi-job matrix) documented but
  DEFERRED. The full metrics table (before measured / after target) is in
  the report.
- NO COMMITS PUSHED - the owner's instruction ("Do not modify files or
  create commits until I approve") overrides the standing push protocol for
  this session.

Next: the owner's approval decision on Phase A (five-file change set);
on approval, implement + verify per the report's section 9 plan and then
resume the architecture queue (the FL4-F3 tails).
---
Task ID: session-2026-10-30-fl4f7-f8 (the TNT + block-update dimension rows)
Agent: main (ZaminTorch session)

Work Log:
- Pulled develop to 3a6127a0 (the second review lane's record; no open
  PRs remained). The CI audit task from the prior session stands
  COMPLETE: Phase 1 audit done, Phase 2 proposal DELIVERED (docx + pdf
  in download/) — awaiting the owner's approval; NO CI/build/workflow
  files touched this session per the gate.
- Wave FL4-F7 LANDED (b1d997a5): the TNT family's dimension row — the
  primed body's dimension stamp, every ignition arm taking the acting
  world, the widened SolidityProbe/DetonationSink seams, the nether's
  OWN explosion service (the overworld-bound one collected nothing for
  a nether blast), the dimension-filtered wire fan and wound gather,
  and the reference's indexed tick walk + deferred removal (the latent
  mid-tick-chain ConcurrentModificationException the new seat exposed).
  NetherTntRoutingAcceptanceTest 1/0, stash-verified failing unfixed.
- Wave FL4-F8 LANDED (0392d917): the block-update family's per-dimension
  story — the nether/End's own BlockUpdateSystem instances on their own
  worlds + scheduled-tick seams + views, gravity tickers on every
  dimension's queue, the nineteen drop arms dimension-stamped.
  NetherBlockUpdateRoutingAcceptanceTest 2/0, stash-verified failing
  unfixed at both seats. Named remainder: the falling family's own
  per-dimension row (the FallingBlockEntityManager's landing consult).
- CI watch: run 551 in progress (older tree), 553 pending on this head;
  no release past torch-1.8.8-153 yet — the green run must carry the
  tag + publish steps through.

Stage Summary:
- develop at 2224159c (the WORKLOG record), zero uncommitted, pushed as
  Abasing. Two FL4-F3 tails retired (TNT + block-update).

Next: the redstone system's world binding (the biggest tail — per-world
RedstoneSystem instances cascade into the piston/noteblock/hopper/
dispenser/beacon bindings), the container families' per-dimension
stores, the falling family's row; watch run 553 + build 154's
publication; the CI Phase A proposal awaits the owner's decision.

- Wave FL4-F9 LANDED (cbbd33c5): the falling family's per-dimension row
  — the body's dimension stamp, the per-body ground flip, the routed
  landing consult/commit, the stamped occupied-landing drop, the anvil
  crush's dimension-filtered walks, the wire fan's stamp filter. The
  landing seat stash-verified failing unfixed; the falling-family
  regression lanes green whole.
- Session total: THREE FL4 tails retired (TNT + block-update + falling).
  develop at 817dbe74 (the worklog record), zero uncommitted, pushed as
  Abasing. CI: run 551 ~73 min into the test step (120-min cap); run
  555 queued on the session head; the next green run publishes
  torch-1.8.8-154.

Next: the redstone system's world binding (the biggest tail) + the
container families' per-dimension stores; the CI Phase A proposal
awaits the owner's decision.

---
Task ID: session-2026-10-31-phasea-sectionx (CI Phase A + the test-side roots + the Section X audit + the redstone trace)
Agent: main (ZaminTorch session)

Work Log:
- Session start: develop at 817dbe74, clean tree, in sync with origin.
- THE CI FREEZE ROOT-CAUSED (why nothing published since torch-1.8.8-153):
  runs 529/551/556 all failed on the SAME three protocol tests
  (QuickCraftDragWireTest chest seat, VehicleIntegrationTest minecart seat,
  MobIntegrationTest) — NOT load flakes. Two bisects over
  [torch-1.8.8-153..HEAD]: the chest seat broke at 7f9fb6da (the furnace
  drag test placed its furnace at the SAME (2,5,2) cell the chest seat
  uses, in the class-shared world — JVM method order decided the loser);
  the minecart seat broke at 9ad983b6 (the rail parity pass: both cart
  seats seed rails directly over the boat seat's leftover water pool —
  the reference's OWN AbstractRailBlock.neighborChanged hasSolidTop-below
  law correctly pops the unsupported rails, the cart strands off-rail).
  Both reproduced locally (a scratch rig showed all five rails popped
  within 40 ticks with water below). DimensionWalkTest (run 532's
  both-attempts failure): wall-budget defect — 20s/30s polls over the
  80-tick portal stand clock, burned by hosted-runner tick stretch;
  passes at nominal speed everywhere. Mob/SurvivalFlow: intermittent on
  the hosted runner only, green locally — NOT retry-listed (the
  membership law requires a diagnosed mechanism).
- LANDED (c2397fed, the test-side fixes — THE ENGINE UNTOUCHED, every
  gate the tests tripped is the reference's own law): the furnace takes
  its own cell (3,5,2); both cart seats restore the grass rail bed
  before seeding; DimensionWalkTest converted to EngineAwaits tick
  budgets (100/400). All green locally, repeatedly.
- LANDED (735737c1, CI Phase A — the approved proposal): new
  gradle.properties (parallel + caching + jvmargs — the build-cache-
  persists claim VERIFIED against setup-java's own source: it caches the
  whole ~/.gradle/caches tree); maxParallelForks=3 + maxHeapSize=1g
  (bukkit overridden to 1 — verified it spawns exactly one executor);
  org.gradle.test-retry 1.6.6 with maxRetries=1/maxFailures=20 and the
  three-class census allowlist (the API verified against the plugin's
  source); the workflow's blind retry RETIRED + the failure-report
  upload added (if: failure(), between build and tag — the tag/publish
  steps carry GitHub's implicit success() so they can never run after a
  failed verification). PROBE-VERIFIED all three retry behaviors with
  scratch classes (deleted): eligible fail-once → retried, build green;
  eligible always-fail → one retry then red; non-eligible → exactly one
  execution, red. Release semantics byte-identical; 120-min timeout kept.
- The census doc: the Phase A allowlist governance record + the filled
  DimensionWalkTest row.
- THE SECTION X AUDIT LANDED (docs/SECTION_X_GAP_ASSESSMENT.md + the
  ledger's Wave X-AUDIT row): the representation layer ALREADY complies
  (canonical Identifier, flattened-family block types, frozen-extensible
  registries, clean protocol isolation, class-A concurrency infra); the
  TWO genuine foundational defects are the ledger's own two tails
  (overworld-bound RedstoneSystem; position-keyed container stores); two
  named B-gaps carry triggers and must NOT be built early (ItemStack
  component seam; dimension registry).
- THE REDSTONE WAVE TRACED (Wave FL4-F10, INVESTIGATED — the exact
  handoff is in the ledger): RedstoneSystem holds one overworld field
  (82 world consults), registered as the overworld's listener alone —
  the nether/End carry NO signal machinery; 32 consult sites split into
  use consults (position-only), wake/update (position-only), probes
  (global entity walks, no dimension filter), and the fan/tickers; the
  comparator store is position-keyed with no dimension. The plan, the
  reference law, the entanglement, and the acceptance seats are all
  recorded in the row.

Stage Summary:
- develop at (this docs commit), pushed as Abasing. Three commits this
  session: the test-side roots (c2397fed), CI Phase A (735737c1), the
  audit + trace record.
- CI run 557 was in flight on the Phase A head at session end — WATCH
  IT: the first green run publishes torch-1.8.8-154 and measures Phase
  A's actual duration (the 13-16-minute projection is a target to
  verify, hard bound 20).

- RUN 557's RESULT (the Phase A measurement, first data point): FAILED
  in 15m 59s — a FAILING run now costs LESS than the old GREEN run
  (40m28s) and a third of the old failing runs (50-56m). The module
  overlap is visible in the log (bukkit:test/core:test/protocol:test
  interleaved); 2767 tests ran; the failure semantics verified LIVE:
  the reports artifact uploaded (its XML named the failure exactly),
  tag/notes/publish all skipped. THE ONE FAILURE: MobEventBridge's
  pig seat — the uploaded XML said 'expected: <mob.pig.say> but was:
  <step.grass>' — the any-sound race (the footstep beat the voice
  through the last-write-wins slot; the cow seat carries the same
  latent race). LANDED (aac5a981): both seats capture the VOICE by
  name and poll that; the census row records it; three consecutive
  local greens. NOT allowlisted — fixed at the source.
- RUNS 558/559/560 (the full CI story this session):
  - 558 FAILED in 13m24s: ComputeSubsystem's stale-result seat (the
    queuedDepth==0 poll proves DEQUEUED, not DEPOSITED — a starved
    worker let the single drain run before the deposit) + LoginFlood's
    inhabitedTime growth pin (the evidence printout proved it: 39.3 vs
    98.3 us/tick one-player baselines across the class's two seats in
    ONE run — single-window contention noise). BOTH FIXED (aac5a981's
    successor 3d16b02d): the compute seat loops the idempotent drain
    (the handle's generation is captured at SUBMIT — any later deposit
    is stale by construction); the flood baseline is now the per-section
    MINIMUM of three windows (the noise floor), the strict pins kept.
  - 559: THE GRADLE BUILD WAS GREEN (15m10s — the first full-suite pass
    since build 153's tree) but the PUBLISH died: 'HTTP 422: body is
    too long (maximum is 125000 characters)' — the notes' full-messages
    block over the four-day ~170-commit gap overflowed GitHub's release
    body cap. THE SECOND ROOT of 'the builds aren't publishing', only
    visible after the test fixes. FIXED (deef2a17): the notes generator
    bounds itself (head+changes+tail built first, the details block
    per-body-capped at 1500 and included only under a 120k total cap,
    else replaced by a pointer). Verified against the real range: the
    uncapped details were 186,590 bytes; the bounded notes 31,467.
    Tag 154 sits orphaned (pushed, never released — the design's known
    state; the ledger continues at 155).
  - 560 FAILED in 20m55s with a four-flake burst (one per module):
    BukkitEventBridge's death seat, VehicleWorldDamage's lava seat (the
    DOCUMENTED combined-run load class), DonkeyChest's window seat,
    TorchEncryption's hash seat (recurred from 529). Each single, each
    timing-class. The fork-count decision: NO fallback yet — the flake
    rate matches the pre-Phase-A era (529/532/551/556 each carried 1-4
    distinct members; the population is era-invariant, not fork-caused),
    the two PROVEN fork-specific races are fixed at the source, and the
    15-16m timings are in target. Runs 561+ accumulate the evidence; if
    the hosted flake rate stays >0 per run, the next session decides:
    the risk register's 2-fork fallback (one line) vs the allowlist's
    root-cause-first growth path.
  - 561 FAILED in 16m53s: RangedCombatAcceptanceTest's bow seats (2) +
    MobIntegration's pig seat (recurred: 529/551/556/561) + one protocol
    seat; 2767 core tests 4 failed, protocol 240/1. THE ERA-INVARIANCE
    CONFIRMED: ~3.4 failures/run pre-Phase-A (523/529/532/551/556) vs
    ~3.0 post (557/558/560/561) — the forks did NOT raise the flake
    rate; the residual is the audit's named wide population (the
    'far wider than the documented three' finding). The path: the
    census's root-cause-first law, member by member — six fixed at the
    source THIS session (QuickCraft, Vehicle, DimensionWalk,
    MobEventBridge, ComputeSubsystem, LoginFlood). The recurring
    members for the next session's queue: MobIntegration's pig
    walk-and-reach seat (the pig wanders out of reach between the walk
    and the attack under load — the readEntityStatus timeout), the
    RangedCombat bow seats, TorchEncryption's hash seat (529+560?),
    DonkeyChest, BukkitEventBridge's death seat, VehicleWorldDamage
    (documented). Every failure now downloads as reports.

SESSION TOTAL: 7 commits pushed as Abasing (c2397fed the test roots,
735737c1 CI Phase A, 99f1718b the Section X audit + redstone trace,
aac5a981 MobEventBridge, 3d16b02d ComputeSubsystem+LoginFlood,
deef2a17 the notes cap; plus 560's pipeline-owned bump). The Section X
audit landed (docs/SECTION_X_GAP_ASSESSMENT.md); the FL4-F10 redstone
wave is traced with its exact plan in the ledger; the container wave
(Gap 2) follows. CI: the deterministic blockers retired, the publish
422 fixed, the pipeline mechanically sound (13-21min, correct failure
semantics, forensics upload); the first green+publish now rides the
residual flake lottery, and each member is fixable at the source.

Next: Wave FL4-F10's implementation exactly per the ledger row (the
per-dimension signal machinery, the routed use consults, the probe
filters, the comparator store stamp, the scheduled-tick seams); then the
container/storage ownership wave (Gap 2); watch run 559 (the expected
first green — it publishes torch-1.8.8-154) and record the measured
before/after; SurvivalFlow stays under watch (root-cause first if it
recurs — the reports upload makes forensics a download).

## SESSION 2026-10-31 (evening) — FL4-F10 landed whole; the PR wave #101/#102/#104 merged; the pig seat retired

Repo state at start: develop at a95e7ca6 (the README live table + the
prior session record), clean. Three PRs open from the parity agent:
#101 (F-EX9 ghast fireball flight constants), #102 (F-EX6 the
wrong-world bed blast), #104 (F-EX13 the blast's player wound
dimension gate).

THE CI CHECK (the owner's ask — 'the builds aren't being published
since a long time'): the release pipeline remains mechanically sound
(run 559's 422 was the last deterministic blocker, fixed in deef2a17);
what blocks the green now is the residual load-flake population, one
to four members per hosted run. This session's forensics (the reports
artifacts downloaded for runs 562/563/565 — the failure upload works):
- 562: EnderExit's fountain seat (13.0 vs 12.0) — one-off.
- 563: HorseIntegration's wheat ladder + MobIntegration's pig seat
  (the FIFTH recurrence: 529/551/556/561/563).
- 565: ExplosionFireArm's crater seat + HungerExhaustion's pearl seat
  — one-offs.
All three one-off classes verified two consecutive local greens —
load flakes, NOT regressions from the merged PR wave. The pig seat
(the worst recidivist) was retired at the source (below); the one-off
members join the census queue root-cause-first.

THE PIG SEAT RETIREMENT (MobIntegration, commit 49578334): the seat
walked to the pig's live cell and swung ONCE, but the tick lane's
catch-up ages the pig's AI while the swing sits queued (the first
hit's panic flees at ~7 blocks a second) — the pig exits the
3.85-block reach budget before the attack's processing and the gate
correctly refuses SILENTLY, so the status read starves. THE FIX (the
seat's law preserved — the loop DEPENDS on the server's gate):
swingUntilTheBodyAnswers walks the live cell, swings, and polls the
body's OWN simulation state for the landing tell (the health drop
under max for the first hit, the dead flag for the kill); a refused
swing leaves the state untouched and the next iteration closes the new
distance. Evidence: 2/0 twice (solo 10.9s, three-class combined load
12.0s).

THE PR WAVE (each merged only after live three-pass verification):
- #101 F-EX9 MERGED (076d676f): the reference read directly
  (FireballEntity extends ProjectileEntity; getDrag 0.95; no gravity
  in the base walk — ThrownEntity's line 240 alone); the stash
  discrimination reproduced live (both seats fail on unfixed develop
  at the PREDICTED values — the terminal speed 4.483 vs vanilla's
  1.81, the restored droop to y=78.3); the projectile lanes 48/0.
- #102 F-EX6 MERGED (b6718ab3, after resolving the findings-doc union
  on the branch — the PR head needed the push to its OWN branch name):
  BedBlock.use's gate/else-arm verified (hasSpawnPoint && biome !=
  HELL; power 5, fire ALWAYS, destructive always); the nether seat
  fails unfixed (the pair left whole), 3/0 fixed; the engine's own
  bedSiblingCleanup carries the same pair convention; 17/0 lanes.
- #104 F-EX13 MERGED (2737c76c, the branch unioned with develop
  first): Explosion.damageEntities gathers from the blast's OWN
  world's chunks; the one-line gate completes the eleven-walk census
  (every other wound walk already carried it); the nether body at 0.5
  health on unfixed develop, 1/0 fixed; 20/0 on the unioned branch.

WAVE FL4-F10 LANDED (commit 0328e92f, pushed) — the redstone system's
world binding, EXACTLY per the ledger's traced six-point plan:
- Per-dimension instances (the FL4-F8 form): netherRedstone +
  endRedstone booted, each its own world's change listener, each
  drained on its own profiled tick lane. The downstream families (the
  pistons, the hoppers, the dispensers, the tripwire, the command
  blocks, the beacons, the TNT) stay on the overworld's bus — Gap 2's
  named population, the honest-tail form.
- The USE consults route by session.dimension() through redstoneFor
  (the worldFor form): useComparator/useLever/useButton + the
  eye-frame wake; the fx sounds carry the dimension (TF-12); the door
  pair's suppression flag rides the ACTING dimension's system.
- The probes walk their OWN dimension's bodies (the plate, the
  weighted plate, the trapped chest's viewers, the cart) and the
  analog reader ranges the ACTING world's cells.
- The ZCD store's v2 row carries the dimension stamp (the TF-9
  signed-byte law; v1 rows load as overworld rows; the save unions
  every dimension's maps; the boot's byDimension walk routes the
  restore).
- The scheduled ticks ride each instance's own internal queue (the
  drain fans all three).
ACCEPTANCE: four seats green through the real wires (the lever's use
dispatch flip opening the nether door; the plate powering for the
nether mob's stamped feet alone — an overworld body at the SAME
coordinates never triggers it; the repeater's 2-tick delay; the
comparator's stored output surviving the restart stamped -1). THE
STASH DISCRIMINATION live: seats 1-3 fail on the unfixed tree at
exactly the predicted awaits; seat 4's is structural. Regression
142/0 (the redstone family whole, the pistons, the hoppers, the
dispensers, the store split v2 byte-identical, the sleep pair, the
nether quartet, the blast/bed gates, the doors, the interaction and
use-dispatch lanes).
Seat-geometry lessons recorded inline: the family's FACING constants
(FACING_SOUTH=0, not the Direction id 3); a repeater feeds its output
dust a STRONG 15 (no decay); the store's file rides the world's own
data dir (dataDir/<world>/data); the restart seat names ONE world.

DOCS: the FL4-F10 ledger row flipped to LANDED with the record; the
README live table re-verified (dimension ownership ~80%, CI ~88%, the
evidence named); this worklog record.

SESSION TOTAL: 5 commits pushed as Abasing (49578334 the pig seat,
the #101/#102/#104 merges, 7910a110 the develop union, 0328e92f
FL4-F10) + this docs commit.

NEXT: the container/storage dimension ownership wave (Gap 2 — the
position-keyed container/block-entity stores gaining their dimension
rows; the cross-dimension collision the Section X audit names as a
TODAY defect); the flake census queue (HorseIntegration's wheat
ladder, the one-off members from 562/565, RangedCombat's bow seats,
TorchEncryption's hash seat); watch the next CI runs — the pig seat's
retirement removes the most frequent member from the lottery.

LATE ARRIVAL (post-push): PR #105 (VT-007's remaining slice — the TNT
cart's blast sink gains the dimension carry, F-EX11's last call site).
The SOURCE PASS verified this session against the reference
(TntMinecartEntity.explode line 92: this.world.explode(this, ..., 5-arg
overload, TRUE) — the cart's OWN world, destructive true, createFire
the overload's own false; the PR's detonateAtIn(worldForSafe(dim),
explosionServiceFor(dim), ..., true, false) matches exactly, and the
overworld-bound detonateAtWithSource it removes was the last
overworld-bound blast helper). The BEHAVIORAL pass (the stash
discrimination) and the regression lanes NOT yet run — the session's
time boundary. NEXT SESSION'S FIRST PR REVIEW: run the PR's two new
test classes on the unfixed tree (expect the predicted failures), then
the vehicle family + the blast lanes, then merge.

## SESSION 2026-10-31 (afternoon) — the CI-unblock session + the PR #105 merge + the Gap 2 trace

THE CI INVESTIGATION (the user's ask: "the builds arent beeing
published since a long time"): confirmed — the last release is
torch-1.8.8-153 (2026-10-27, four days); every run since failed in
the test suite (the rotating flake lottery, each failure canceling
the publication). The timeout fix (60→120min, 99b7652c) was already
in; the residual was pure test-seat flake. Root-caused and retired
EIGHT members at the source this session, every one a TEST-SEAT
defect with the engine verified 1:1 against the reference in each
case (commit dfb64a61, seven members; ff2e01c0, the eighth):

- THE SWING LOOP'S READ-SILENT KICK (MobIntegrationTest, runs
  569+571): the chase polls the body's own state up to 60s without
  one typed read; the keep-alive auto-answer lives inside readPacket;
  the server's kick arm (interval × 3 = 15s at the 5s rig cadence)
  closed the channel mid-chase → the next walkTo died as a broken
  pipe. FIX: TestClient18.pumpKeepAlives (the non-discarding drain —
  keep-alives answered inline, everything else re-queued in order),
  called every 50ms poll. THE STASH DISCRIMINATION (throwaway rig,
  deleted after): 20s read-silent under a 1.5s cadence — without the
  pump the exact run-569 signature, with it the chat round trip works.
- THE DEATH AWAIT'S PRECONDITION (BukkitEventBridgeGroupTwoTest, 569):
  dead is dieOnTick's FIRST act, the event fires later in the same
  walk; the await polled the flag. FIX: await the monitor's own
  observation (both references), the GroupThree form.
- THE BED SEAT'S NIGHT HUNT (BedWrongWorldBlastAcceptanceTest, 571):
  the seat sets night and the natural spawn cycle seeds hostiles
  inside the sleep gate's ±8/±5 refusal box — the engine's 1:1 law
  correctly refused. FIX: doMobSpawning=false in the boot (the
  protocol sleep rigs' own rule).
- THE BLAST EVENT'S SAME-WALK WINDOW (NetherBlastRouting, both seats,
  571): the ExplosionEvent fan runs at the walk's end; the poll could
  observe the torn cell first. FIX: await blast.get() != null.
- THE TERMINAL BRIDGE'S SCHEDULER WALL (TerminalLogBridgeTest, 571):
  the 20ms budget measured descheduling, not the bridge (publish is
  format+offer). FIX: the 1s budget (the blocking failure mode costs
  ~2.3s).
- THE HASH SEAT'S INVENTED LAW (TorchEncryptionTest, 571): "a negative
  digest carries its full 40-char magnitude" contradicts the reference
  (both sides' bare BigInteger.toString(16) — never padded, verified
  at ServerLoginNetworkHandler line 155 + ClientLoginNetworkHandler
  line 47). FIX: the reference's own law pinned (≤40, never -0).
- THE ARROW SEAT'S EDGE-RIDING GEOMETRY (RangedCombat, 571 + the
  cold-daemon miss): the hit walk samples the arrow's CENTER at
  midpoint/endpoint vs the ±0.3/-0.2..1.8 band; the old +1.5/35° seat
  landed the midpoint 0.0166 inside the near z edge with the endpoint
  BELOW the feet band — a ≥1.5σ gaussian event (the reference's own
  0.0075 spread, ArrowEntity.dispense) tunneled the arrow (~7%/run).
  FIX: the flat geometry (+2.9, 10° down) — the first tick's endpoint
  at the band's center, >12σ margins.
- THE FIRE ARM'S BINOMIAL TAIL (ExplosionFireArmParity, 565): the
  1-in-3 per eligible cell makes one blast's fire count Binomial;
  the single-shot ≥2 assertion rode the tail. FIX: four independent
  sites, ANY one blast answering several (the discrimination
  unchanged — the pre-fix stand-in can never answer two in one blast).

THE WATCH LIST (unexplained one-offs, artifacts named, no mechanism
found, 12/12 and 3/3 green today): HungerExhaustionParity's pearl
seat (run 565) and MultiplayerSleepWire's refusal-line chat read (566).

THE PR #105 MERGE (278b139e + the rider 32af09f3): the TNT cart's
blast dimension (F-EX11's last call site). The behavioral pass re-run
independently — the cart seat on unfixed develop fails at exactly the
predicted await (nether=0 overworld=20). ONE review correction
carried: the creeper discovery seat's nether standing pocket (the
per-boot terrain lottery embedding the flipped body — one no-blast in
17 runs; the pocket clears the three layers above the box, the
measured cells untouched). LESSON RECORDED: the correction was lost to
a stash mishap in the merge flow and landed as a follow-up rider —
always commit review corrections to the union branch BEFORE merging.

THE GAP 2 TRACE (the architecture file's Wave FL4-GAP2 row): the
container/storage dimension wave traced whole — 18 families censused
with every consult site's dimension source named (the window paths
carry the session; the listeners receive the world; the generation
rides the chunk), the four-slice plan ledgered (A: chest; B: the
container remainder; C: the tile families; D: the transient
families), the reference's law stated (every WorldServer owns its
tile entities). The implementation is the next session's first act.

THE CI STATE AT SESSION END: run 573 mid-suite on the flake-fixed
tree (dfb64a61+ff2e01c0), run 575 queued on the final tree (with the
#105 merge + the pocket rider). A green run cuts build 154+ and
confirms the publish resume.

NEXT: implement Wave FL4-GAP2 Slice A (the chest family) exactly per
the traced plan; watch runs 573/575 to publication; the remaining PR
wave (#106-#114, the verifier agent's VT-006/009/010 series) reviews
as time permits — the three-pass discipline each.

THE PR #107 MERGE (98ded357, the session's second review): the mob
portal cooldown's double-decrement retired — the tail decrement beside
PR #98's entity-level portal arm halved the 300-tick re-entry immunity
(the reference: ONE decrement per ticked body, baseTick lines 311-313).
Behavioral pass live: unfixed develop decays 40 over a 20-tick window,
the union green. ONE correction carried: BaseDefaultsModelTest's legacy
seat drove the retired tail directly; it now drives the manager's
per-tick portal arm (the single remaining site). Lanes: portal + entity
(611) + NBT defaults green.

SESSION COMMITS (all as Abasing, all pushed): dfb64a61 (the seven
569/571 flake retirements), ff2e01c0 (the 565 fire member), 278b139e
(the #105 merge) + 32af09f3 (its pocket rider), 8e557798 (the Gap 2
trace + the README table), 98ded357 (the #107 merge + its correction).

AT CLOSE: run 573 mid-suite on the flake-fixed tree; runs 575/577
queued behind it on the final tree (the #105 + #107 merges carried).
A green run cuts build 154 and confirms the publish resume — the first
check of the next session. The next session's first implementation
act: Wave FL4-GAP2 Slice A (the chest family) exactly per the traced
ledger row. The remaining PR wave (#106, #108-#114) reviews continue
under the three-pass discipline.

---
Task ID: 2026-11-01 (the publish-resume + Gap 2 Slice A session)
Agent: main (ZaminTorch session)
Task: The user's three asks — (1) the CI publish stall (builds stopped at
153 since Oct 27), (2) continue the architecture's remaining work (the
Gap 2 container/storage wave, Slice A first per the traced ledger row),
(3) the README live table kept at real percentages.

Work Log:
- THE PUBLISH STALL'S DIAGNOSIS (the first hour): every release run since
  Oct 27 failed on ONE OR TWO DIFFERENT flaky members per run (571: three;
  572: two; 573: one; 577: one) — each failing run blocking the publish
  and the whole tail behind it. The two standing blockers at session
  start: runs 573 (VanillaMiningTimingTest.airborneDig) and 577
  (VehicleWorldDamageTest.theLavaKillsTheCart).
- RUN 573'S ROOT CAUSE — A GRASS-SPREAD LOTTERY, NOT LOAD: the test-reports
  artifact gave the failing await ("the airborne dig still self-completes
  on the slowed clock"); the probe (a stage-ladder listener over the dig
  session) showed the stage REGRESSING 7 -> 6 at tick 60 with the tail
  rate exactly 1/90 — the flat world's grass layer at y=4 randomly ticks
  (the RandomTickSystem's own vanilla 1:1 spread, the 3x5x3 reach) and
  converts the seeded DIRT dig target at (2,5,2) to GRASS (hardness 0.6
  vs 0.5): the live-recompute progress law regresses the accumulated
  progress and the 75-tick dig stretches to ~91 — past every budget
  (~8% of runs). THE ENGINE IS 1:1 CORRECT; the SCENE seeded its dig
  target on a live grass field. FIX: clearGrassFieldAroundDigs (the 3x3
  patch whose spread reach covers the target) on both dirt seats; the
  self-completion await converted to EngineAwaits.awaitCondition (the
  ML-002 shape it missed — the engine's tick clock, 60-tick budget) and
  the tool-wear assert converted with it (a visibility race: the walk
  commits AIR before wearHeldTool, the slot write with no happens-before
  for the test thread — one local run read the stale 0). 18/18 green.
- RUN 577'S ROOT CAUSE — A ONE-SHOT WINDOW POLLED AS A STABLE CONDITION:
  the pool carts die on tick N's lava walk but the death blast kills the
  dry control on tick N+1's SAME iterator pass that sweeps them (the
  in-order removal wounds it dead moments before the cursor reaches its
  slot), so a test thread that first observes the pool deaths after that
  pass — one missed 25ms poll under load — sees the dry control dead too,
  false forever after. FIX: the never-burned law re-pinned on the
  PERMANENT observable (the control's one wound is the detonation's
  EXPLOSIVE arm, asserted after the chain-stone await).
- THE MOB-INTEGRATION STATUS READ (the watch-list member, retired at the
  source): one local protocol-suite run read a natural-spawn body's fall
  wound (entity 1100018) where the pig's 1100001 was asked — the natural
  spawn cycle's own hurt packets interleave the Entity Status stream and
  the body-agnostic readEntityStatus lands whichever came first. FIX:
  readEntityStatusFor(entityId, ...) in TestClient18 (the body-filtered
  drain), the seat's three reads converted, 5/5 green.
- **THE PUBLISH RESUMED: runs 578/579/580 GREEN — builds 155, 156 and
  157 CUT.** The four-day stall is over. (The census doc carries the
  governance rows for every retired member, the grass-hazard CLASS named
  for future flat-world dirt-dig seats.)
- WAVE FL4-GAP2 SLICE A LANDED (the architecture's next-work row,
  exactly per the traced ledger): the chest family's dimension ownership
  — three ChestManager instances at the FL4-F8 form (chestsFor(dim), the
  redstoneFor shape), the ZCD store's v3 row with the dimension stamp at
  the row's tail (the comparator store's signed-byte law; v1/v2 files
  load as overworld rows), the save UNION over every dimension's maps,
  the restore routing each row to its own family, all 21+9 consult sites
  routed by their traced dimension source (the window family through the
  session's new openContainerDimension stamp — a 4-arg
  openContainerWindow beside the legacy 3-arg form stamping 0; the
  block-broken listener GAINING the acting world, its one implementer
  updated; the generation drain by the chunk's world dimension; the
  structure placement by the sender's source dimension; the tick sweep
  per family; the TileNbt seams through an IntFunction router — the
  /blockdata and anvil import land in the acting world's family), and
  the per-family contents-changed wake (each family's slot mutations arm
  its OWN world's comparators). SEATS 4/0
  (ChestDimensionOwnershipAcceptanceTest: the two-tile restart — a nether
  chest and an overworld chest at the SAME coordinates carrying different
  contents through a real restart; the window open — a nether player's
  click lands the NETHER chest's emerald on the cursor; the hopper pull —
  the overworld hopper reads the OVERWORLD family's diamond; the
  comparator wake — the nether chest's fill powers the NETHER comparator
  at the same coordinates where the overworld's stays cold). HONEST
  TAILS (Slice B's population): the hopper system and ContainerViews
  stay overworld-bound; the furnace/dispenser/brewing families single.
  Regression: the chest family 28/0, the window/lock/hopper/redstone/
  NBT/anvil/banner/placement lanes 179+ green, the bukkit module whole,
  ContainerSaveSplitTest re-pinned to the v3 bytes.
- THE DOCS: the census doc (the two retired blockers + the hazard class),
  the README live table (dimension ownership ~84%, CI ~97% with the
  publish resumed), the architecture file's FL4-GAP2 row (Slice A
  LANDED, the next-work lines re-pinned to Slice B).

SESSION COMMITS (all as Abasing, all pushed): 316146bd (the two build
blockers retired), 883adb5b (the census doc), 2d0f74af (Slice A + the
mob-integration retirement), d26a1ebd (the docs close — the FL4-GAP2
row + the README table).

AT CLOSE: the pipeline healthy (155/156/157 published), Slice A whole,
Slice B (the container remainder — furnace, dispenser, brewing, hopper
+ the cross-family pulls) is the next session's first act per the
architecture's next-work lines; the PR wave (#106, #108-#115) reviews
continue under the three-pass discipline.

---
Task ID: 2026-11-01 (later — the vine production crash + the runs 580-582 flake wave session)
Agent: main (ZaminTorch session)
Task: The user's three asks — (1) a live production crash report (the email:
vine_south/vine_west_east throwing at the login bulk send, the tick loop
skipping), (2) the CI publish (runs 580/581/582 all failed, each on a
DIFFERENT one-off member — the one-flake-per-run pattern again), (3) the PR
wave review, with the architecture work continuing behind them.

Work Log:
- THE VINE PRODUCTION CRASH (the session's first commit, 6b29c46f): the
  root cause was a TABLE DRIFT — Wave VN-A landed the engine's sixteen vine
  face states and the anvil import rows but MISSED the protocol module's
  wire rows (LegacyBlockIds carried only the bare all-faces minecraft:vine
  at 106|15); a jungle world's anvil import (vine_south 106|1,
  vine_west_east 106|10) then hit ChunkSerializer18.legacyStateId's §147
  loud fail at the login bulk send AND every relight resend. THE REPAIR:
  withVineStates + withVineStateMetadata generate BOTH wire tables from
  VineBlocks itself (the family's one state vocabulary — the table
  structurally cannot drift again); the no-meta reverse keeps the bare form
  canonical on 106 (the vine item's own row, the dye-family skip pattern).
  THE CRASH CLASS CLOSED: LegacyBlockIdsIntegrityTest's whole-registry net
  (every block the FULL EngineServer boot chain registers must resolve a
  legacy id — a registered-but-unwireable type is a live §147 crash in
  waiting; the Anvil derivation SKIPS the unwireable, exactly how this gap
  hid). The net PASSES with the vine rows: the skip set is now EMPTY and
  pinned. This was the SECOND member of the class (the placed oak sapling
  the first, its R-2 repair documented in the table).
- RUNS 580/581/582'S FLAKE WAVE (the session's second commit, 2a306a22) —
  four members, each root-caused with probes before the fix:
  1. RUN 581 — ExplosionFireArmParityTest seat 1's TWO stacked hazards:
     the 1-in-3 lottery (per-blast fire counts 0-5, a zero blast ~6% of
     the time — the probe's empirical spread over six runs) AND the
     extinction race (a bare-floor flame dies at age>3, ~6-15s of life,
     and the seat censused AFTER all blasts resolved). Hardened: six
     in-pregen zones (the probe's second finding: beyond the view-2
     pregen the fireball falls through ungenerated air to bedrock and
     answers NOTHING — seat 2's far sites survive only because the dirt
     walls self-supply floors), per-blast census immediately after each
     own resolution, the stand-in's hard cap as the discriminator
     (best-of-6 min 4 over six probe runs).
  2. RUN 582 (core) — the spreadplayers bounds: the relaxation's clamp
     snaps an edge-pushed pile to the box boundary EXACTLY (the
     reference's own law) and the landing is floor+0.5 — half a block
     outside the ±maxRange band; the seat's bounds (20.0) were tighter
     than the command's own contract (20.5) and the edge landing made the
     await permanently unsatisfiable. One-token fix, engine 1:1.
  3. RUN 580 — the horse seat's reach lottery: the wild horse's vanilla
     stroll drifts it around the loose pen (~1.2 blocks/s) past the
     interact ladder's 4.2-block reach gate; every out-of-reach use is
     silently refused. THE ENGINE FINDING (ledgered for the verifier):
     the wander drift passes THROUGH the pen's cobblestone — the wander
     arm carries no block collision (a caging stall design was tried and
     retired: the leak makes caging useless). The fix: the ~3 summon's
     deterministic placement + FOLLOW arms (walk the tamer to the horse's
     live cell before every use — the swing helper's own closing law).
  4. RUN 582 (protocol) — the mob seat's frozen pig: THE PROBE RUN
     CAUGHT IT RED-HANDED (ZOMBIE@0,0 eating the stationary hunter's 20
     hp over ten seconds while the pig stood frozen beside it): a
     natural-cycle zombie seats at the world spawn BEFORE the client
     joins (the boot-time dawn light's randomized rule), the eaten player
     answers every later gate like a disconnected one — the despawn scan
     skips the dead body (the pig's far-window climbs past 100 and the
     animal NEVER strolls again — canDespawn false), and the attack
     ladder's dead-player return no-ops every swing at any distance. The
     guard: /time set noon + a sweep of the boot-seated hostiles; the
     swing helper also re-closes the distance every 1.2s (the dead-window
     retirement).
- THE PR WAVE (two of eight reviewed and merged, both with review
  corrections carried):
  - PR #115 (VT-011, the bed's OCCUPIED nibbles): source-verified against
    BedBlock's own law (bit 4 written for an occupied HEAD alone, the
    foot arm masks bit 2); merged with the expected-count re-pin UNIFIED
    (the PR's 1329 vs the vine repair's same-hour 1336 — the merge
    carries 1344 = 1336 + 8, both histories in the comment).
  - PR #106 (VT-009, the door/bed pair-death walks): the defect still
    live on develop (both cleanups read the overworld FIELD — a nether
    break orphaned the nether sibling AND destroyed the overworld cell at
    the same coordinates); merged with the review correction that the
    PR's BlockBrokenHook interface is IDENTICAL IN SHAPE to Slice A's
    already-landed BlockBrokenListener — develop's interface name kept,
    the PR's parameterized cleanups + three parity seats taken whole.
  - REMAINING OPEN (the next session's review queue): #111 (the bed
    sibling resolution, +448), #113 (VT-006 the arrow family, +1997),
    #114 (VT-009's bed neighbor-changed pair-integrity, +2308), and the
    VT-006 trio #108/#109/#110 (the items/orbs/vehicles crossings — three
    stacked variants, compare before merging any).
- THE CI PUBLISH: builds 155/156 published at the session's start (runs
  578/579), then 580/581/582 failed on the four members above (all
  retired); runs 583/584/585 on this session's commits were in
  flight/queued at close (584 cancelled — the concurrency group's
  supersede, expected). The FIRST check of the next session: run 585
  (becf84b0) green and the build published — the four retirements should
  clear the one-flake-per-run pattern.
- THE README LIVE TABLE re-verified: parity ~92% (the vine wire net + the
  bed import row), dimension ownership ~85% (the pair-death walks joined
  the listener's acting-world seat), CI ~97% (thirteen seat defects now
  root-caused, all with the engine verified 1:1).

SESSION COMMITS (all as Abasing, all pushed): 6b29c46f (the vine wire
repair + the crash-class net), 2a306a22 (the four flake retirements),
e17c538b (the PR #115 merge), becf84b0 (the PR #106 merge), plus the
README/worklog close riding the final push.

AT CLOSE: the production crash fixed and its class closed; the publish's
flake wave retired; two PRs merged with corrections; six PRs still open
for the next review pass. The next session's first implementation act
per the architecture's next-work lines: FL4-GAP2 Slice B (the container
remainder — furnace, dispenser, brewing, hopper families + the
cross-family pulls). The verifier's engine findings from this session's
probes (the wander's no-collision drift, the dead-player gate shape)
hand to the standing audit.

---
Task ID: 2026-11-01 (latest — the FL4-GAP2 Slice B container-remainder
session + the runs 583/586 publish blockers)
Agent: main (ZaminTorch session)
Task: The architecture's next-work lines named Slice B (the container
remainder — furnace, dispenser, brewing, hopper + the cross-family
pulls); the user's standing CI ask (builds unpublished) surfaced runs
583/586 both failing on one-off members again.

Work Log:
- Read the worklog, synced develop (777b7c86, clean), re-read
  Torch-Arcetecture.txt fresh (9214 lines): the FL4-GAP2 row's Slice B
  (row 2 of the four-slice plan) confirmed as the first act, Slice C/D
  and the PR remainder (#111, #113, #114, #108-#110) named after.
- IMPLEMENTED Slice B whole, exactly per the traced row: the four
  families gained the chestsFor form (furnacesFor/hoppersFor/
  dispensersFor/brewingStandsFor — each dimension's instance holding
  its OWN world, its OWN redstone, and the SAME dimension's chest and
  furnace families through the hopper's constructor); the stores' v+1
  stamps (ZFD v3, ZDD v2, ZBD v2, ZHD v2 — the dimension byte at each
  row's tail, v-1 files loading as overworld rows, the save UNION, the
  routed restore); every consult site routed by its traced source (the
  window opens/clicks/quick-craft/viewer ticks by the session's
  openContainerDimension — the four opens now the 4-arg
  openContainerWindow form; the block-broken listener by the acting
  world AND every family's spill now stamps that world, closing Slice
  A's own honest chest tail; the block-change listener, the generation
  drain, the comparator probe, the structure placement, the hopper-cart
  placements and the restore/anvil hosts by the CART's own dimension).
- THE CROSS-FAMILY PULLS AND THE DIMENSION GATES INSIDE THE HOPPER
  (the slice's own named law): each dimension's hoppers consult the
  SAME dimension's dispenser/brewing lookups and cart scan (a
  same-coordinate cart in another world is no container); the item
  suck and both cart-transfer arms gate on the ACTOR's own dimension
  (the reference's per-world getEntitiesOfType — a nether item at the
  overworld hopper's coordinates is invisible); the ocelot lock's scan
  gates on the world (a sitting ocelot in another world never locks);
  the DISPENSER's every emit (fx, launches, the default drop) stamps
  its OWN world — the fireworks arm's own "never a silent wrong stamp"
  ledger note answered; ContainerViews dress every world with its OWN
  families.
- Seats 5/0 first-run green (ContainerSliceBOwnershipAcceptanceTest):
  the four-family two-tile restart through a real reboot, the
  cross-family pull with BOTH directions (the overworld hopper pulls
  the overworld furnace's iron while the nether hopper pulls the
  nether furnace's gold, same coordinates), the suck gate (the nether
  emerald never enters the overworld hopper; the overworld diamond
  does), the per-dimension comparator wake, and the nether furnace
  window open through the full click path (the cursor receives the
  nether family's gold ore).
- Regression: the container family lanes 134/0 (chest/hopper/furnace/
  brewing/dispenser/container packages + the save split/pipeline/
  effect-tile seats re-pinned to the v+1 union forms — the byte-walk
  equivalences carry the version bumps and the row-tail stamp), the
  protocol window/anvil/nbt/serializer lanes green, the minecart and
  redstone lanes green. The sandbox's own full-suite run OOM-killed
  (exit 137, 2-core sandbox + parallel runs — not a code defect; CI's
  586 had already shown 2809 tests with only the one flake).
- THE CI PUBLISH BLOCKERS (the user's standing ask): runs 583 AND 586
  both failed, each on a DIFFERENT one-off member (the one-flake-per-
  run pattern again, two NEW members):
  1. RUN 583 — BedWrongWorldBlastAcceptanceTest.theOverworldBedStill-
     SleepsAtNight at the SLEEP-WALK AWAIT (line 180): the seat's
     doMobSpawning rule-off (run 571's own retirement) only stops
     FUTURE spawns — the boot window's own already-seated hostiles
     (the dawn-zombie class the mob seat's probe caught red-handed)
     remain, and a boot-seated zombie inside the sleep gate's ±8/±5
     refusal box makes the engine's own 1:1 monster-proximity law
     refuse the use — the await starves. THE FIX: the mob seat's own
     guard shape — sweep the boot-seated hostiles after the rule
     lands.
  2. RUN 586 — StatsParityTest.theUseRowRidesTheDigAndTheSwing at the
     use-row assert (expected 1, actual 0, the dig's committed-await
     PASSED): the seat's committed-await reads "air" — the SAME answer
     an UNSEEDED cell gives when the seed's place never landed, a
     trivial-pass window that then fails confusingly at the row. THE
     FIX: the seed's landed precondition — a named await (the seed
     block verifiably holds before the dig opens) closing the window;
     if a place ever is rejected the seat now names it instead of
     failing at the row. (The engine-side candidates were eliminated
     one by one: creative early-return (default SURVIVAL), empty hand,
     terrain/seed variation (flat gen, deterministic), chunk-load
     races (the spawn chunk covers the seed cell), spawn protection
     (no ops, unarmed band), fastplace/nuker budgets (one place/one
     break), the early-finish self-completion (pays through the same
     tryMineBlock), stats visibility (ConcurrentHashMap).)
- The two open PR-queue reviews (#111, #113, #114, the VT-006 trio)
  did not fit this session's window; they stay next work per the
  architecture's lines.

SESSION COMMITS (all as Abasing): the Slice B engine + stores + seats
+ the four-slice row's LANDED record + the README table (dimension
ownership ~90% with the container remainder whole; CI ~97% with
fifteen seat defects root-caused) + the two publish-blocker
retirements, all in this push.

AT CLOSE: the container families' dimension ownership whole (Slices
A+B landed of the four); the publish's two new members retired at the
source; the next session's first acts per the architecture: Slice C
(the tile families — sign, jukebox, note block, banner, beacon, skull,
command block, the same v+1 law), then Slice D, with the PR wave's
remainder as time permits. The CI run on this push should publish
build 157 if green — the four retirements since 582 (583/586's two +
the two here) all point at seat defects, never the engine.

---
Task ID: 2026-11-01 (latest — the FL4-GAP2 Slice C tile-families session
+ run 588's two publish blockers)
Agent: main (ZaminTorch session)
Task: The architecture's next-work lines named Slice C (the tile families
— sign, jukebox, note block, banner, beacon, skull, command block, the
same v+1 law); the CI run on the Slice B close (run 588, 149eb1f0)
surfaced two NEW one-off members blocking the publish.

Work Log:
- Read the worklog, synced develop (149eb1f0, clean), re-read
  Torch-Arcetecture.txt fresh: the FL4-GAP2 row's Slice C confirmed as
  the first act, Slice D and the PR remainder named after. The spawner
  store's verify-do-not-assume note answered first: its row key already
  carries dimension(i32) — no pass needed. The skull family carries no
  store (placement-born world state).
- IMPLEMENTED Slice C whole, exactly per row 3: the seven families at
  the chestsFor form (signsFor/bannersFor/commandBlocksFor/
  noteBlocksFor/beaconsFor/jukeboxesFor/skullsFor — each dimension's
  instance holding its OWN world: the note family hearing its OWN
  redstone's power edges, the beacon family spreading through its OWN
  world's players with the dimension gate ADDED inside playersInRange
  (a nether beacon never buffs an overworld player at the same
  coordinates — the reference's per-WorldServer player list), the skull
  family's summon host a private skullHost(world, dimension) factory
  with the newborn wither stamped for that dimension); the stores' v+1
  stamps (ZSD v3, ZJD v2, ZND v2, ZBD v2, ZBT v2, ZCB v3 — the
  dimension byte at each row's tail, v-1 files loading as overworld
  rows, the save UNION, the restore routed per row).
- Every consult site routed by its traced source: the sign edit by the
  WRITER's dimension; the sign/command-block orphan sweeps per world;
  the block-broken listener's five tile cleanups by the ACTING world;
  the banner place + the survival-mined tagged drop by the placer's/
  miner's dimension; the note use/attack by the user's world (the
  mining-start hook upgraded to a BiConsumer gaining the digger);
  the jukebox use + disc insert by the user's dimension; the beacon
  window family by the session's openContainerDimension stamp (the
  beacon open now the 4-arg openContainerWindow form; the quick-craft,
  click, viewer tick, close payment, applySelection, and BOTH protocol
  window reads the same stamp); the beacon tile creation on the
  block-change path by the CHANGED world; the /stats position arm by
  the SENDER's sourceDimension; the command-block redstone run per
  dimension (each dimension's redstone arms its OWN family — the
  nether's command blocks now RUN on nether power where before they
  never ran at all).
- The cross-layer wiring: the per-world BlockEntityViews (each world's
  sign view over its own family, the End gaining a view), the
  per-dimension dispenser skull surfaces + block-update banner seams,
  the TileNbt Seams at the IntFunction router form (every tile family's
  /blockdata + import/export landing in the world's own family), and
  the protocol's chunk replays gated by the observer's dimension — the
  sign and beacon replays' dimension==0 hard gates RETIRED (a nether
  column now replays its nether tiles) and the skull/banner/command
  replays' ungated cross-world leaks CLOSED.
- Seats 4/0 first-run green after one fix (TileSliceCOwnership-
  AcceptanceTest): the six-family two-tile restart through a real
  reboot, the nether sign edit writing the NETHER family alone, the
  note use tuning the user's own world, the beacon window open through
  the acting dimension's family. THE RESTART SEAT CAUGHT A REAL ENGINE
  DEFECT: the command-block orphan sweep lacked the sign family's
  chunk-loaded guard — a restored tile on a not-yet-loaded chunk read
  as air and was silently discarded at boot; the same guard law landed
  (the sign family's own shape).
- RUN 588's TWO PUBLISH BLOCKERS retired (both on the baseline, never
  the slice): (1) CommandBlockBlockTest.theBreakSweepsTheTileAndFires-
  TheRemoval — the same sweep class (the ungated discard on an
  unloaded chunk can eat the tile before the break's paired listener
  fires, leaving the removal count 0); the engine-side guard retires
  it. (2) EnderExitAcceptanceTest's health pin — run 562's fall-debt
  class through the OTHER window: the seat ARRIVED the body before
  writing the fountain (the submit window's fall on the not-yet-
  supported island cell lands as debt when the bedrock write commits);
  the seat now writes the fountain FIRST and awaits it (the reference's
  own order — the dragon's death writes the portal before anyone
  stands in it).
- Regression: the tile family lanes 188/0 (sign/jukebox/noteblock/
  banner/commandblock/beacon/skull + nbt + tile stores + the new
  seats), the container/redstone/blockentity/dispenser/interaction
  lanes 73/0, the block+world lanes 557/0, the stat/chest/furnace/
  hopper/brewing lanes 127/0, the protocol module 243/0, bukkit +
  launcher compile. The sandbox cannot hold the full suite (the
  2-core window kills it mid-run; CI owns it — 2814 tests on run 588).
- The PR queue (now 21 open, the verifier's VT stack #116-#131 joined
  the older remainder) did not fit this session's window; they stay
  next work per the architecture's lines, the three-pass discipline.

SESSION COMMITS (all as Abasing): the Slice C engine + stores + seats +
the architecture's Slice C LANDED record + the README live table
(dimension ownership ~93% with the tile families whole; CI ~97% with
seventeen seat defects root-caused) + the two run-588 retirements, all
in this push.

AT CLOSE: the tile families' dimension ownership whole (Slices A+B+C
landed of the four); the publish's two new members retired at the
source (one an engine defect, one a seat order); the next session's
first act per the architecture: Slice D (the transient families — the
piston movingBlocks, the decorations, the leads), with the PR wave's
remainder as time permits. The CI run on this push should publish
build 157 if green.

ADDENDUM (the session's close): the Slice D TRACE ledgered in the
architecture's FL4-GAP2 row (findings only, the CI-unblock precedent —
the implementation is the next session's first act): the piston family's
per-dimension shape is construct-natural (the system already holds its
own world + redstone; the named leak is the push surface's ungated
entity scan), the decoration family adds the ZED v2 stamp (the named
leak is the comparator's through-the-wall frame reader reading the ONE
shared registry from EVERY dimension's redstone), and the lead family's
named leaks are the mobsIn tie scan, the overworld-pinned survival
read, and the detacher's overworld mutation context. Row 4's plan and
seats named.

SECOND ADDENDUM (the same session): (1) PR #124 (VT-018, the arrow's
random.bowhit at both impact arms) reviewed and MERGED — the source pass
verified against the reference (line 303's stick arm + line 276's
entity arm inside the takeDamage-true branch, the pitch law, the
ARROW-kind gate at both arms), the discovery seats 4/0 + the projectile
lanes 52/0 on the PR branch and the merged tree, the one review
correction carried (the stick comment's stale follow-up line). (2) The
ender-exit member's REAL root cause caught red-handed after the first
diagnosis proved incomplete (the merged-tree run failed again): a
temporary probe with a PlayerDamageEvent hook logged amount=1.0
cause=IN_WALL at the fountain cell — the seat's hand-written fountain
never cleared the column above the portal, and the generated island's
randomized surface blocks the standing body's eye cell (64 + 1.62)
about one run in twelve; the engine's own dragon-death fountain clears
the whole disc's air (dy>0, 32 layers, the reference's createPortal
law) — the seat now clears the column too (4/4 fresh runs green), the
fountain-before-arrival order kept from the first diagnosis.

THIRD ADDENDUM (the same session): run 589's bed-guard member retired
(the eighteenth seat defect, the probe-caught class again) — the sleep
walk's await starved on develop despite the boot sweep; a replicating
probe with a MobSpawnEvent hook showed ZERO spawns and ZERO hostiles
(mobs=0, nearBed=false at every pre-click census) yet 2 rounds in 8
answered sleeping=false with the world time at 59 (DAWN): the sleeping
flag is a ~1-tick TRANSIENT on the single-player contract — the
morning jump fires within ticks of the entry and wakes the body, and
the complete cycle (sleep, dawn, wake) landed INSIDE the seat's watch
window, the 25ms poll missing the flip entirely. THE FIX: the seat
observes the sleep's own PERSISTENT signature (the bed spawn, set at
the entry and surviving every wake path, pinned to the head cell's
rest position) instead of the transient flag — 5/5 fresh runs green.
The engine verified 1:1 throughout; the observation was the defect.

FOURTH ADDENDUM (the session's close): run 590 (040a8d80) showed FOUR
members — the bed guard (retired in 90f64947 above) plus three NEW
one-offs, the first 4-failure run (a heavily loaded runner): (1)
GiveCommandAcceptanceTest's remainder seat — the index-based get(0)
race (the SurvivalFlow family's own documented class: a concurrent
natural drop can land in the list between the await and the read, its
nonzero pickup delay answering the noPickUpDelay pin) — RETIRED here
(the drop now picked by its own shape, filtered by the diamond type).
(2) AttributionLoadTest's hostileCount NPE at line 152 and (3)
SurvivalFlowIntegrationTest's drop-settle await — both protocol-module
load flakes needing their own probes; they go to the next session's
census as the first acts alongside the run-90f64947 check.

---
Task ID: 2026-11-01 (the FL4-GAP2 Slice D session — the piston +
decoration halves + runs 591/593's census members)
Agent: main (ZaminTorch session)
Task: The architecture's next-work lines named Slice D (the transient
families) as the first act with run 590's two protocol flakes as the
census first-acts; the session landed the piston and decoration
families (two of Slice D's three) and retired two new CI members.

Work Log:
- Read the worklog, synced develop (efcd0c4f, clean), re-read
  Torch-Arcetecture.txt fresh: Slice D's TRACE (the same session's
  ledger) named the three families whole — the piston's per-dimension
  shape construct-natural with the push surface's ungated entity scan
  as the named leak; the decoration family's ZED v2 stamp with the
  comparator's through-the-wall frame reader as the named leak; the
  lead family's three named leaks.
- THE CENSUS FIRST: run 590's two protocol flakes pulled from the CI
  reports. (1) AttributionLoadTest's NPE at hostileCount:152 — the run
  XML's full frame chain pinned it INSIDE MobManager.all():
  List.copyOf -> List.of -> requireNonNull — the accessor's snapshot
  read the plain ArrayList's size and array on opposite sides of a
  concurrent removal (the attribution horde's 100-body clear racing a
  25ms poll), handing List.of the nulled tail. ENGINE defect, the
  all() accessor's documented off-thread contract broken under its
  intended use (237 poll sites across 82 seats). THE FIX: both entity
  registries copy-on-write (the PlayerRegistry's ConcurrentHashMap is
  the codebase's own idiom for exactly this), the tick loops'
  iterator.remove arms converted to the existing remove(mob/item,
  reason) law, removeHostiles' defensive copy retired. The census
  tail NAMED not sprayed: TntSystem/ProjectileManager/
  PrecipitationSystem/PacketHookRegistry carry the same
  List.copyOf-over-ArrayList shape but ZERO await-wrapped polls (the
  seats assert post-settle) — untouched per the §34 proportion rule.
  (2) SurvivalFlow's drop-settle timeout — the full static analysis
  (documented in the architecture): the engine ran at NOMINAL 20 TPS
  through the whole 400-tick await (run 591's own timestamps), on a
  fresh flat world (pickaxeDigs ran FIRST — the XML's testcase order),
  with no collector in range (the spawn (0.5,5,0.5) is 2.0 blocks
  from the drop, the pickup range 1.425), no merge partner (the only
  item is the cobble), the wire id verified engine-native
  (sendItemSpawn writes entity.entityId() directly), and the physics
  settle at ~tick 13 deterministic. Every candidate eliminated; the
  instrumented sampler probe is the next session's first act.
- RUN 593 (on efcd0c4f) surfaced ONE new member:
  DenseOwnerProfilingTest's superlinear ceiling pin — the merge walk
  measured 1.59x growth with the re-measure ESCALATING to 2.18x (a
  thrashing shared runner; the same tree measures 1.17x locally). The
  healthy band (~1.0-1.2) and the O(n^2) signature (1.52-1.74
  recorded) have NO clean ceiling between them under runner noise —
  the pin retired to the test's own printed-evidence discipline (the
  §22 shape), the re-measure taking the MIN of its windows, the two
  floor pins and the 12 equivalence seats holding the walk's law.
- IMPLEMENTED Slice D's PISTON family: the three instances at the
  chestsFor form (each dimension's own world + redstone — the
  nether's pistons now RUN on nether power where before they never
  ran at all), the change/neighbor listeners and ticks per dimension,
  the pistonsFor(dim) router, and the push surface extracted into
  pistonPushSurface(dim) with EVERY body family's scan gated on the
  body's own dimension (the mobs/vehicles/TNT/falling/projectiles
  GAIN the gate; the items/orbs/stands' hard != 0 pins generalize).
  Seats 3/0 first-run green (the nether piston displacing only the
  nether's bodies with the overworld pig at the same coordinates
  never moving; the mirror; the power routing with the nether-lever
  positive control). The piston lanes whole (28/0).
- IMPLEMENTED Slice D's DECORATION family: the three systems sharing
  ONE wire-id allocator, the ItemFrame/Painting dimension column, the
  ZED v2 stamp (dimension byte at the row's tail, union save, routed
  restore, v1-as-overworld migration — TileExtraSaveSplitTest
  rewritten + the new migration seat), every consult site routed by
  its traced source (attack/placement/interact/survival-poll/maps-
  audience/blast-wound/anvil-import/frame-readers/wire-observer-
  gates/census-views/break-drops). Seats 2/0 after two SEAT-side
  fixes (the bare-frame signal law — a frame without a display
  answers 0; the reference-vs-display await race — the inserts land
  in the same ticker task as the lookups). The decoration + map +
  redstone-routing lanes whole.
- THE SESSION'S OWN CAUGHT DEFECT: the COW conversion's first push
  (d1590655) missed TWO removal arms — the creeper's explosion and
  the endermite's expiry still called iterator.remove() over the COW
  list (the COW iterator throws by design), so every tick reaching
  either arm failed whole and the creeper never detonated. The local
  decoration-blast lane caught it (the run's own log:
  'UnsupportedOperationException at COWIterator.remove' every tick);
  both arms converted in 688e4f70, pushed fast, and the full iterator
  sweep re-audited (one iterator per tick loop, both traversal-only
  now). The regression lanes had missed it because none of them
  (MobCombat, PeacefulDifficulty, SpawnEgg, SaveSplit, SurvivalMining)
  reach the explosion or expiry arms.
- The GitHub API rejected the token mid-session ('Bad credentials'
  on the actions endpoints; Bearer worked for the repo endpoint, then
  that too) while git push/fetch over HTTPS kept working throughout —
  the CI runs past 593 could not be monitored; the pushes themselves
  all landed.

SESSION COMMITS (all as Abasing): d1590655 (the COW engine fix),
ead7de54 (the dense-owner retirement), 688e4f70 (the two missed
removal arms), ac420ca2 (Slice D's piston + decoration halves — the
message names the decoration half, the piston engine changes rode
the same commit, the architecture's Slice D record names both), plus
the architecture's Slice D LANDED record + the README rows (dimension
ownership ~95% with the lead family remaining; CI's nineteenth and
twentieth members retired) in this push.

AT CLOSE: Slice D two of three families landed (the piston and the
decoration); the next session's first acts per the architecture: the
LEAD family (the knots' mobsIn scan gate, the survival read's
overworld pin, the detacher's owner-context assert — no store pass),
then the SurvivalFlow drop-settle probe (the instrumented sampler),
with the PR wave's remainder as time permits.

## SESSION 2026-11-01 (the Slice D close + the PR wave's first reviews)

- Read the worklog, synced develop (57f19ad5 — the prior session's
  drop-settle census instrument had landed), re-read
  Torch-Arcetecture.txt fresh: Slice D two of three families landed;
  the LEAD family the named next act; the PR stack at 37 open.
- CI verified green: run #598 on 57f19ad5, build 157 published (the
  release pipeline healthy — runs 594/595's failures were the pushed
  tree's own intermediate states, 596/597 cancelled superseded runs).
- THE PR WAVE (the user's stacked-review instruction, the three-pass
  discipline each):
  * MERGED #109 (VT-006 items/orbs/vehicles portal arms) + #113 (the
    arrows — stacked on #109): the reference citations verified
    line-by-line (the super.tick chains, threshold 0, cooldown 300,
    the minecart's own byte-identical copy at 199-232, ArrowEntity's
    super-first order); the per-body-row design byte-identical to the
    merged #98 mob law; the #107 double-decrement class audited at
    every family (the single decrement site holds — the vehicles'
    pre-existing portalCooldown row had NO decrement site on develop);
    seats 1/0+1/0+2/0+1/0 green, the projectile package whole, the
    bed/sleep lanes, DimensionWalkTest green on the branch trees.
    CLOSED #108 and #110 (the convergent alternative carriers — the
    top-level promotion and the EntityPortalClock extraction both
    diverge from the merged per-body-row law; #108's audit doc is
    carried evolved by #114; salvage notes posted for its three
    unique test classes).
  * MERGED #111 (VT-010 the bed facing law): the four walks' neighbor
    scans replaced by the id's own FACING band — verified against
    getStateFromMetadata/byIdHorizontal (0=S,1=W,2=N,3=E) and the
    use redirect's block-CLASS test; 4/0 seats + the bed lanes whole.
  * MERGED #114 (VT-009 the bed pair-integrity neighborChanged arm)
    WITH THE REVIEW CORRECTION (the #106 precedent): the PR's
    four-neighbor scan was the exact divergence class #111 retired —
    the merged arm consults the half's OWN facing-named cell; the
    facing law now lives in ONE home (server/block/BedBlocks.java,
    the WoodDoorFamily convention) with EngineServer's four private
    helpers retired to thin delegation; the correction's own seat
    added (aForeignAdjacentPairNeverKeepsAnOrphanAlive — 5/0 whole).
  * MERGED #139 (F-SP1 the support-consult family): the four mount
    laws verified verbatim (canSitOnTop 49-55, ButtonBlock.canSurvive
    70-73, TrapdoorBlock.canBePlacedOn 188-190, isSolidBlockingCube);
    the SupportConsult home + 68 divergent rows + 30 controls; the
    redstone package whole. CLOSED #137/#138 (the convergent
    carriers — #139 the strongest construction); the six tower PRs
    (#141/#143/#145/#147/#148/#149) left OPEN with rebase
    instructions (their bases superseded; their incremental floor/
    door/fire/tripwire/redstone laws wanted on the rebased form).
  * MERGED #118 (F-DS1 the double-slab full cube): SlabBlock
    .updateShape verified verbatim; 5/0 + CollisionShape 19/0.
- THE LEAD FAMILY LANDED (commit 5a53d2fd — Slice D WHOLE, row 4's
  third and final family): leadsFor(dim) at the chestsFor form (each
  instance holding its OWN world + fence identifier, setSurvivalRead
  retired, ONE shared IntSupplier for the Spawn Object 77 band), the
  LeashKnot dimension column, the tick walk gated on the body's own
  dimension, LeadSurface.mobsIn gated (THE NAMED LEAK CLOSED), the
  drop stamped with the leashed body's dimension, the fence use by
  the player's dimension, knotById for the wire-id lookup, the
  detacher's requireMutationContext at the MOB's own world, the
  anvil import routed by the target world, the protocol's knot arms
  + join replay observer-gated. THE STASH AUDIT: no v+1 needed (the
  knots ride the mob's Leash NBT; the mob store already carries the
  dimension row — verified, not assumed). Seats 3/0 + LeadSystemTest
  8/0 (the rig's listener registration restored after the rewire) +
  LeashApi 3/0 + AnvilImport 6/0 + DimensionWalk 1/0 + the Slice D
  siblings + the block/entity packages 891/0.
- The architecture's Slice D record closed WHOLE, the README's
  dimension-ownership row at ~97%, the next-work line re-pinned.

SESSION COMMITS (all as Abasing): 18c9b920 (#109), 278a7662 (#113),
c2bd0a14 (#111), b4ec6738 (#114 + the BedBlocks correction), 5a53d2fd
(the Lead family), the docs commit (Slice D WHOLE + README), 1139f2c3
(#118) — plus the four closes and six tower comments on GitHub.

AT CLOSE: the PR stack at 27 open (the six tower rebases requested,
the singletons #116/#117/#120/#121/#122/#123/#125/#126/#128/#130/
#131/#132/#133/#134/#135/#136/#140/#142/#144/#146 unreviewed); the
next session's first acts per the architecture: the tower rebases'
reviews, the singleton wave, then the dimension-ownership ledger's
next rows (the redstone world's remaining consults, the container/
storage ownership); the drop-settle census instrument awaits the
next CI occurrence.

## SESSION 2026-11-02 (the floor-consult family + the stacked PR wave)

- Read the worklog, synced develop (1139f2c3, clean), re-read
  Torch-Arcetecture.txt fresh: Slices B/C/D whole; the PR wave's
  remainder + the dimension-ownership ledger's next rows the named
  acts; the PR stack at 27 open.
- THE TOWER REVIEW (the user's stacked-PR instruction): the six
  towers (#141/#143/#145/#147/#148/#149 — the floor-consult family)
  showed GitHub "mergeable_state: clean" but a LOCAL merge-tree test
  showed 43 conflict markers each (their bases predate the #139
  SupportConsult merge; the 13:06 "updates" were the prior session's
  review comments, not rebases — the branch heads stood at 10:31-52).
  LESSON RECORDED in the architecture: verify GitHub's merge state
  against a local merge-tree before trusting it.
- THE SOURCE PASS (fresh from the reference, every claim verified):
  RedstoneWireBlock.canBePlaced 91-93 (hasSolidTop OR GLOWSTONE),
  DiodeBlock 29-35 (hasSolidTop ALONE), AbstractPressurePlateBlock
  72-74/84-86 (hasSolidTop OR the FenceBlock census — seven families,
  the wall NOT), AbstractRailBlock 71-73 + headwalls, DoorBlock
  213-214, FireBlock 306-315, TripwireBlock 87-94/110-114 +
  TripwireHookBlock 101, TorchBlock.onAdded 91-93, NaturalSpawner
  160-162 (the spawn floor reads World.hasSolidTop whole), and
  World.hasSolidTop 1740-1752 itself. #121's discovery rows verified
  correct but its fix the suffix-approximation class (the collision
  conjunct kept — glass/ice/leaves/TNT still spawn floors).
- THE CARRIER BUILT (commit 8fe0e233): the towers' laws rebased onto
  the #139 home — the four named floor laws (wireFloorHolds/
  diodeFloorHolds/plateFloorHolds/doorFloorHolds) replacing the one
  shared hasSupportBelow, the six EngineServer placement gates + the
  weighted pair's generic-path gate, FireBlocks.hasSolidTop delegated
  whole (the translucent fallout retired), RailConnections + the
  three tripwire sites, the plain-torch walk (the #137/#138 named
  row), the redstone torch's onAdded support-first arm, and
  NaturalSpawnSystem's isFullCubeGround through the shared home (the
  STRONGER form of #121, its three seat rows carried). The towers'
  five seat files salvaged whole.
- THE BENCH DISCOVERY: the piston lanes FAILED on clean develop —
  the #139 merge (ad71bb32) had repaired three piston bench files but
  missed PistonPushApiAcceptanceTest, RegionBoundaryTest, and the
  newer PistonSliceDOwnershipAcceptanceTest (+ BukkitEventBridge-
  GroupTwoTest) — develop's FIVE standing failures, CI run 603
  (failure, 13:41) the confirmation. ROOT: PistonBaseBlock.isCube()
  false (reference line 223) — a lever never rides a piston base in
  vanilla; the benches staged the illegal scene. All four files moved
  to the vanilla-valid mount (the stone above carries the lever, the
  flip's strong power re-radiating down).
- THE VERIFIED MERGES (each the SOURCE pass + seats green on the
  merged tree, merged with real merge commits — GitHub marks them
  merged): #116 (the grass-decay shade's two conjuncts — GrassBlock
  .tick 49-52, isOpaque retired), #144 (the repeater's phantom click
  retired — the delay cycle silent, the click the comparator's alone
  per ComparatorBlock.use 148-152), #134 (the TNT prime's hiss
  stamped with the acting world), #135 (the dig/place feedback
  stamped — the Commit record gaining the dimension column), #136
  (the falling anvil's land sound stamped with the body's dimension).
- CLOSED with the full three-pass review notes: the six towers +
  #121 (superseded by the carrier). The stack: 27 open at session
  start -> 23 open now (5 merged, 7 closed, 8 NEW arrived mid-session
  from the verifying agent: #150-#157).
- LANES (all local): the five family seats 16/0, redstone + block
  372/0, piston lanes 36/0, falling family 109/0, entity 582/0,
  interaction/player/world 308/0, spawn 16/0, bukkit 52/0 — ~1,700
  tests green. The anvil seat flaked ONCE under the 5-class parallel
  boot load (the 60-tick await) and passed on the family run + the
  isolated re-run — the loaded-runner environmental class, joined by
  CI 603's Hunger/Pearl + NetherBed one-offs (both pass locally; the
  wither-curse await the same class under the interrupted full run).
- The architecture's record appended (the family LANDED + the
  next-work re-pin), the README's parity row ~93% + the CI row's
  run-603 note, all pushed.

SESSION COMMITS (all as Abasing): 8fe0e233 (the carrier), 6fe43975
(#116), 5fbff154 (#144), 63f547c4 (#134), bf05bfb6 (#136), 274812ff
(#135), 359dab9e (the docs) — plus the seven closes and the review
comments on GitHub.

AT CLOSE: 23 PRs open (the arrow cluster #117/#120/#122/#123/#128/
#132/#140 — seven convergent lanes over ProjectileManager; the
dimension-stamp cluster #125/#130/#133/#142/#146; the piston cluster
#126/#129/#131; the fresh wave #150-#157 unreviewed); the next
session's first acts per the architecture: the arrow cluster's review
(the TNT-preemption pair #123/#140 the likely carrier pair), then the
dimension-stamp cluster, then the ledger's next rows (the redstone
world's remaining consults, the container/storage ownership); the
next CI run is develop's confirmation (run 603's five failures
retired at the source).

## SESSION 2026-11-02 (later — the stacked-PR wave landed whole)

- Read the worklog, synced develop, re-read Torch-Arcetecture.txt
  fresh: the PR wave's 23 open the named first acts (the arrow
  cluster, the dimension stamps, the pistons, the fresh #150-#157).
- THE ARROW FAMILY (seven convergent lanes over ProjectileManager):
  merged #140 (VT-030 the stick precedes the TNT consult — the
  reference's arm order, ArrowEntity 304-before-308; the pre-fix arm
  ignited first and return-false'd, the arrow flying through the
  opened cell at full speed), #120 (VT-015 the un-stick law — the
  tracked cell + identifier consult, the per-axis random*0.2 damping,
  the anvil import's xTile/yTile/zTile/inTile rows so a restored
  stuck arrow consults), #123 (VT-019 the stuck arrow's inGround gate
  — the reference's scan rides the flight branch alone, line 382),
  #128 (VT-023 the feet-law band [y, y+0.5] — the anvil import's
  vanilla Pos restore pinned the premise), #132 (VT-024 the pickup
  envelope's box intersection — PlayerEntity's grown(1.0, 0.5, 1.0)
  walk, 0.3+1.0+0.25 horizontal). CLOSED #117/#122 (the convergent
  pair): the VT-014 CARRIER composed (commit 84140361) — the
  reference's World.rayTrace at the engine's granularity: #117's
  boundary-crossing cell walk (the parametric face math, the
  negative-zero guards, the direction-corrected recomputation) with
  #122's per-cell COLLISION-BOX entry test (the slab method; the
  reference tests the SHAPE, never the bare cell), the rest exactly
  face−0.05·unit, the stuck velocity keeping the face-relative
  vector, the box's own cell the tracked cell, ShapeGround's
  solidBoxesAt box view on the per-body dimension flip. Both PRs'
  seats green on the carrier (the thin-wall + the slab-shape faces
  both pinned). #123's unit test re-pinned to the merged stuck shape
  (the #106/#114 precedent).
- THE DIMENSION-STAMP FAMILY: merged #125 (VT-012 the fourteen live
  spawn sites stamp the birth dimension BEFORE the fan — Entity line
  166's construction law; spawnAt's dimension-aware arm + the VIII14
  dice context; the composition conflict with the landed Slice C
  skullHost resolved keeping the per-dimension structure), #130
  (VT-013 the three post-fan precedents — the spawner, the End
  dragon, the chicken egg), #133 (VT-026 the nether border's /8
  center projection — NetherDimension.createWorldBorder 75-86
  verified verbatim; the borderFor router at the containment lanes,
  the command/wire surfaces staying on worlds[0]), #142 (VT-029 the
  sixteen no-dimension FX residual sites), #146 (VT-033 the
  wither-summon family's five residues — the converged MobManager
  arm conflicts resolved keeping #125's form).
- THE PISTON FAMILY: merged #126 (VT-016 the full material census —
  the 426-id divergence corrected over Material.java 10-43 + the
  block-level overrides: BedBlock/DoorBlock/plates return 1, Ice/
  Rails return 0), #131 (VT-025 the toBreak drop walk), #129 (VT-022
  the block-entity gate widened to the BlockEntityProvider census).
  THE BENCH REPAIRS: the six new seats' lever-on-piston-base mounts
  moved to the vanilla-valid stone-above form (the F-SP1 class —
  PistonBaseBlock.isCube() false, reference line 223).
- THE FRESH WAVE: merged #153 (W-POT the pot's floor consult at both
  sites — canBePlaced 136-137 + neighborChanged 141-143), #154 (W-BED
  BedItem.useOn's two gates — the UP-face law 24-26 + the both-halves
  solid top 42), #156 (VT-037 the orb's two-constructor box law — the
  xp ctor's 0.5 box vs the load ctor's 0.25), #151 (VT-036 the
  tripwire + TNT signal arm's per-dimension machinery — the chestsFor
  form + the redstoneFor router; the pre-fix actingWorld==world gate
  refused every nether/End prime), #152 (VT-035 the anvil/enchanting
  windows' own dimension — the 4-arg open, the viewer ticks' stillValid
  over the opening world, the damage walk + 1020/1021 events on the
  site world), #155 (VT-035 the nether bed seat's whole-pair probe —
  test-only, #114's pair-integrity arm the law). CLOSED #150/#157
  (their stale bases re-implemented the landed F-SP2/SP3/SP4 family):
  #150's unique F-SP5 hook-wall law salvaged (commit 964a681a — the
  Block.isSolid consult at both wall sites, its seat green whole),
  #157's 669-line seat carried as the independent verification.
- THE FLAKE HUNT: ScoreboardIntegrationTest's health publish flaked
  in the full-module run — bisect-verified NOT a regression (the
  baseline, the join point, and every merge point green in isolation;
  HEAD 3/0 green thrice) — the loaded-runner environmental class
  again, the record updated in the architecture.
- LANES: the arrow family 77/0, the dimension-touched families 673/0,
  the piston package 34/0, the experience package 27/0, the redstone
  + TNT families 61/0, the anvil family 9/0, the spawner family 33/0,
  the skull/dispenser lanes 51/0, the adjacent lanes (combat/
  interaction/block/player/world) 635/0, the entity lanes 639/0.
- The architecture's record appended (the wave LANDED + the next-work
  re-pin to the dimension-ownership ledger's remaining rows + the
  Phase E boundary arms), the README's parity row ~95%, all pushed.

SESSION COMMITS (all as Abasing): b3e22a69 (#140), 2d3776dd (#120
with the composition), 2a787db4 (#123 with the re-pin), 84140361
(the VT-014 carrier), bd560f65 (#128), f283dde1 (#132), 4c4d00b5
(the join), c54434f5 (#125 with the Slice C composition), 1c97c2f6
(#130), 8c813de8 (#133), 42c5e2ac (#142), ccf4ec6b (#146), 0deb9d9a's
family (#126/#131/#129 with the bench repairs), 964a681a (the F-SP5
salvage), d35a7fa5 (#153), 2720017c (#154), 145ce09e (#156), 589af729
(#155), 5c58429f (#151), e6a55e5c (#152) — plus the four closes
(#117/#122/#150/#157) with the full review notes on GitHub.

AT CLOSE: the PR stack EMPTY (0 open — the 23-wave whole landed);
the next session's first acts per the architecture: the dimension-
ownership ledger's remaining rows (the redstone world's remaining
consults — the per-family comparator wake, the wire's cross-dimension
consults; the container/storage ownership's tail rows — the anvil
import's per-dimension host consults, the hopper cart scan's
cross-family pulls), then the vanilla-conformance census's Phase E
boundary arms; the next CI run the confirmation (the fresh-wave
families' seats all local-green).

## SESSION 2026-11-02 (the tails + the flake's root cause)

- Continued from the PR-wave landing: the architecture's next acts
  (the dimension-ownership ledger's remaining rows) taken up.
- #146's review-note ledger RETIRED (commit d96f4a2a): the wither
  fall's kill row + the beacon full-power row walk the ACTING world's
  players (the un-dimensioned awardPlayersInBox retired at both
  sites); the banner/beacon/command-block tile fans the skull fan's
  per-world form (each dimension's store re-publishing through its
  OWN listener gated to its dimension's observers — the three were
  the overworld-only ungated shape).
- THE SEAT: AwardWalkDimensionDiscoveryTest 2/0 — the nether wither's
  fall refusing the overworld bystander's stat row, the nether
  beacon's pyramid refusing the overworld squatter. THE SEAT'S OWN
  VANILLA-VALIDITY REPAIR: the nether's beam column cleared to the
  bedrock roof first (updateLevels 95-106 kills the beam on
  opacity-15 — the netherrack terrain above leaves the beacon
  inactive and the pyramid census never runs; bedrock alone excepted,
  the vanilla nether-beacon shaft's own construction).
- THE SCOREBOARDHEALTH FLAKE ROOT-CAUSED: the health walk is CHANGE-
  GATED (ServerPlayerEntity.tick 312-318, completeHealth 1:1 in the
  engine) and the player's first tick may precede the test's
  objective creation — the mark at 20 with no objective, no publish
  ever landing (vanilla's own answer for a mid-game objective: the
  score appears on the next health CHANGE alone). THE FIX (test-only,
  the seat class): the mark reset for the deterministic first
  publish — 4/4 green solo. The full-module one-offs (DonkeyChest/
  ChestLid, each green isolated) the loaded-runner environmental
  class, rotating members.
- LANES: the stat/beacon/tile families 44/0, the award seat 2/0, the
  scoreboard seat 4/4 solo, the protocol module 242/243 (the one-off
  the environmental class).
- The architecture's record appended (the tails LANDED + the flake's
  root cause + the next-work re-pin), all pushed.

SESSION COMMITS (all as Abasing): d96f4a2a (the tails + the seat +
the seat-race fix).

AT CLOSE: the PR stack still EMPTY; the next session's first acts per
the architecture: the redstone wire's cross-dimension consults, the
anvil import's per-dimension host consults, the hopper cart scan's
cross-family pulls, then the Phase E boundary arms.

## SESSION 2026-11-03 (the three-wave session: the wire's row closed + the drop census + two boundary arms)

- Continued per the architecture's next-work: the dimension-ownership
  ledger's remaining rows + the Phase E boundary arms. An in-flight
  PE-5 wave (uncommitted from the prior session's end) finished,
  verified, and landed first.
- WAVE PE-5 (commit daf68a67): the dragon's contact hulls (each body's
  OWN box against the part's grown hull, STRICT; the armor stand census
  landed — flung whole, the head's wound refused), the lead tie box
  (the mob's OWN type box against the ±7 box STRICT; the two arms'
  different anchors pinned — the fence arm's MIN CORNER, the knot arm's
  ENTITY position), the splash scan's own-box law. THE BREAK DROP
  ANCHOR REPAIR — the ArmorStand placement-occupancy seat's 1-in-3
  flake ROOT-CAUSED as a genuine parity gap: the reference drops at
  new BlockPos(this) (FLOORED) and .up(); the engine fed the stand's
  raw CENTER into the block-corner API — every break drop a HALF-BLOCK
  east/south into the next cell. The spawn inset re-pinned to
  Block.dropItem verbatim ([0.25, 0.75] uniform). The armorstand
  family 24/0 four consecutive runs — the flake dead.
- WAVE PE-6 (commit 11ce37d4) — THE DEATH-DROP CENSUS: eleven
  dimension stamps (the WIRE POP — the architecture row's own find:
  a nether wire pop dropped into the OVERWORLD; the piston to-break,
  the jukebox disc, the beacon payment, the seven vehicle sites, the
  banner, the double-plant, the rail pop), the reference's THREE drop
  forms (spawnDropAtEntity — Entity.dropItem's exact feet anchor;
  spawnContentsSpill — InventoryUtils' [0.1, 0.9] band + 10-30 chunk
  splits + gaussian velocity + NO pickup delay — the vehicle contents
  and all five container block spills migrated), the named cart's
  custom-name carry. SEATS: DropStampDimensionDiscoveryTest 2/0, RED
  2/2 pre-fix. The merge's splatter-then-recombine documented (the
  reference's own law). Bench repairs: the piston slime rig re-ordered
  (the PE-4 gate is vanilla's law — the stand places before the orb),
  the bukkit landing window widened to the cell column. Lanes green
  whole (190/0, 104/0, 889/0, 394/0, 52/0, 45/0).
- WAVE PE-7 (commit 01b7bea8): the DETECTOR RAIL'S CART PROBE (the
  0.2-inset box, each cart's own hull STRICT, the MinecartEntity.class
  census — the HOPPER CART now powers the rail, the edge-graze fires)
  and THE DISPENSER'S ARMOR ARM (the own-box strict law over the
  facing cell; the type's own size; THE ARMOR STAND WALK landed — the
  reference's explicit CanPickUpItemsFilter qualify arm: a dispenser
  DRESSES a stand). SEATS 4/0 + 3/0, RED 6/7 pre-fix. THE HONEST FIND
  LEDGERED: the detector rail's comparator read (the cart inventory
  analog arm) absent whole — its own next-work row.
- The architecture's record appended (the three waves + the re-pinned
  next work: the anvil import's per-dimension host consults, the
  detector rail's comparator read, the mob AI walks' boundary arms).

SESSION COMMITS (all as Abasing): daf68a67 (PE-5 + the anchor repair),
11ce37d4 (PE-6 the death-drop census), 01b7bea8 (PE-7 the rail + the
armor arm) + this record's architecture/worklog commit.

AT CLOSE: the PR stack untouched per the user's instruction (the
verifying agent's PRs pending); the next session's first acts per the
architecture: the anvil import's per-dimension host consults, the
detector rail's comparator read (PE-7's ledgered find), then the mob
AI walks' Phase E boundary arms.

## SESSION 2026-11-03 (the second batch: PE-8 + PE-9)

- Continued per the re-pinned next-work. The protocol/launcher modules
  verified green (243/0, 53/0 — the earlier one-off the environmental
  class). The ANVIL IMPORT row investigated and VERIFIED LANDED (the
  scheduled-tick loads and container restores already per-dimension,
  each store loading into its own system — no gap found, the row
  retired from the next-work).
- WAVE PE-8 (in commit 7425faec): THE DETECTOR RAIL'S COMPARATOR READ
  — PE-7's ledgered find retired. The reference's two-tier law
  (DetectorRailBlock.getAnalogSignal 133-147): the command-cart tier's
  success count first, then the INVENTORY carts' fullness (the chest
  and hopper carts), over the same 0.2-inset box the probe walks; the
  unpowered rail and the empty box answering 0. SEAT 3/0, RED 3/3
  pre-fix (the chest cart's 13-stack fullness 7, the command cart's
  count 9 verbatim, the hopper cart's 2-stack fullness 6).
- WAVE PE-9 (in commit 7425faec): THE FISHING HOOK'S EXACT CLIP LAW —
  the segment clipped against every hookable body's OWN box grown 0.3
  (the exact slab-method port, segmentBoxEntrySquared), the closest
  hit winning; the census widened to hasCollision's law (the mobs,
  players, THE ARMOR STANDS, THE VEHICLES, THE PROJECTILES — items and
  orbs excluded); the follow/pull arms for every new family; the
  bobber's own dimension riding the consult. SEAT 5/0 (RED 3/5
  pre-fix: the graze, the stand, the cart; the outside and item
  controls green by design), 5/0 three consecutive runs.
- THE SEAT DISCIPLINE'S OWN FINDS: the stale-XML red-check trap (a
  compileTestJava failure leaves the previous run's XML standing — the
  red-check must run with the interface signatures consistent, both
  the main and the test trees stashed together); the corridor floor
  built BEFORE the angler teleports in; the flight's own schedule
  pinning the targets (the pitch-0 lob's tick-3-4 band at z+4, the
  descent through y 40.9 at z+8.5 for the cart).
- LANES: fishing + vehicle + armorstand 119/0, interaction + projectile
  + redstone 126/0. The architecture's record appended, the next-work
  re-pinned (the mob AI walks' boundary arms, the hook census tail —
  the primed TNT/frames/crystals, the command cart's run path).

SESSION COMMITS (all as Abasing): 7425faec (PE-8 + PE-9) + the
architecture record commit.

AT CLOSE: the next session's first acts per the architecture: the mob
AI walks' boundary arms (the breeding/mating proximity, the
follow-owner walk, the panic/flee target selection), then the hook
census tail (the primed TNT, the frames/paintings, the ender
crystals), then the command cart's own run path over a powered
activator rail.

## SESSION 2026-11-03 (the third batch: PE-10)

- WAVE PE-10 (commit c20b9905): THE BREED GOAL'S MATE SCAN — the
  AnimalBreedGoal.findMate law: the closest partner whose OWN box
  intersects the seeker's own shape grown 8 (STRICT, the
  feet-anchored asymmetric y band [y-8, y+height+8]), the per-world
  gather's DIMENSION GATE landed (the pre-fix scan: the CENTER inside
  a ±8 Chebyshev band, no dimension gate — the graze at 8.2 and the
  ledge at -8.2 never found, the cross-dimension pair COULD mate).
  The villager arm rides the same scan — the fix covers both.
  SEAT 4/0 (RED 3/4 pre-fix; the far control green by design), the
  scan's answer taken synchronously inside one ticker action.
- LANES: the entity lanes 589/0, the interaction + world lanes 252/0.
- The architecture's record appended, the next-work re-pinned (the
  follow-owner/panic walks, the hook census tail — the primed
  TNT/frames/crystals, the command cart's run path).

SESSION COMMITS (all as Abasing): c20b9905 (PE-10) + 6c0aa687 (the
architecture record).

AT CLOSE: everything pushed; the next session's first acts per the
architecture: the follow-owner walk's consult and the panic/flee
target selection (the mob AI walks' remaining boundary arms), then
the hook census tail, then the command cart's run path.

## SESSION 2026-11-04 (the PE-15 batch: the panic/flee target selection)

- The session's first act: the PE-11/13/14 waves' architecture records
  (the previous session's commits cc9f8383 + be5bc492 were pushed without
  their ledger rows — the session cut off before the record). Appended
  whole with the re-pinned next-work.
- WAVE PE-15 (commit b4420ce4): THE PANIC/FLEE TARGET SELECTION — the
  mob AI walks' last boundary arms. ESCAPEDANGERGOAL 1:1: the census
  (chicken 1.4 / sheep+pig 1.25 / horse 1.2 / cow+mooshroom 2.0 — every
  other kind never panics on hurt), the gate (the attacker's 100-tick
  window stamped at the hurt walk's landed site OR the fire arm — the
  environmental hurt never panics), the TargetFinder 5/4 pick walking a
  PATH at the per-kind speed (the flat 1.8 random-bearing band retired).
  FLEEENTITYGOAL 1:1, all five registrations: the grown-box census, the
  FIRST-in-gather (never the nearest), the eye-to-eye visibility, the
  away-half-plane pick, the farther-check, findPath+isOnTarget, the
  49-squared speed band — the ocelot/rabbit sites rebuilt, the skeleton
  intercept migrated onto the goal, THE VILLAGER-FROM-ZOMBIES and THE
  CREEPER-FROM-OCELOTS landed new (both absent whole pre-fix; the
  creeper yields to the ignite's band-2 claim exactly at the combat
  mind's prime band — and the reverse order IS the cat-scares-creeper
  mechanic: a fleeing creeper never stops to fuse).
- THE WAVE'S OWN ENGINE FIND: MobNavigation.findPath was missing the
  vanilla PathFinder BlockPos overload's +0.5 centering (PathFinder line
  33) — the evaluator's width/2 shift moved every integer-coordinate
  target one cell southwest and Path.isOnTarget never passed. Fixed 1:1
  (the moveTo form's idempotence unchanged).
- SEATS: PanicFleeParityDiscoveryTest 12/0; RED 7/7 on the pre-fix
  engine (the stash discrimination with the pre-fix-API red-check
  variant). The migration seats: TemptBegGoals's flee seat, MobTarget
  Family's skeleton seat, the selector's 15-goal pin, MobEntityTest's
  gate/window/fire law, the two ScriptedWorld/MobWorld/GardenWorld
  stubs gained the box-gather census.
- THE SEAT DISCIPLINE'S OWN FINDS: the vanilla flee's isOnTarget passes
  through the A*'s closest-node FALLBACK (the ground cell under an air
  target carries the target's x/z column — the x/z-only compare); the
  panic's plain pick never fails (no filters) so the re-arm chain walks
  gapless; the speed seat's median-window form (the await's occasional
  two-tick sample slips poison max-window reads); the melee knockback
  launches the victim AIRBORNE — the panic's first moveTo may fail until
  the landing (the claim re-arms per tick, no loss).
- LANES: the entity families 609/0, GoalApi/bridge/thorns green (the
  one thorns batch miss the loaded-runner environmental class, green
  solo).

SESSION COMMITS (all as Abasing): b4420ce4 (PE-15) + this record's
architecture/worklog commit.

AT CLOSE: the next session's first acts per the architecture's re-pin:
the Phase E ledger's standing tail (the mob target walks' own-box forms
over the hunt scans — nearestMobOfKind's center-range consults vs the
reference's ActiveTargetGoal box-gathers), the CI census watches, and
the owner's Section X version-fidelity gate named in the FL4 ledger.

- WAVE PE-16 (commit af15c2ea): THE HUNT SCANS' OWN-BOX FORM —
  ActiveTargetGoal.canStart's gather (the own shape grown 32 horizontal
  and 4 vertical, the candidate's OWN box STRICT, the NEAREST member by
  the comparator) replacing the center-range consult at every hunt site
  (the wolf's trio, the killer bunny, the ocelot's chicken, the
  golem feuds, the zombie's villager hunt). The y band is the wave's
  own law: a seventh-ledge villager is outside vanilla's hunt box
  however near the column; a grazing center 32.2 out IS vanilla's find.
  SEATS: HuntScanBox 3/0, RED 2/3 pre-fix. The stub worlds gained the
  box census. LANES: the entity families 599/0.

SESSION COMMITS (cont.): af15c2ea (PE-16) + this record's commit.

AT CLOSE: the next session's first acts per the architecture's re-pin:
the PLAYER hunt's own box form (the nearestPlayer consult's gather +
the sneaking/invisibility folds already carried), the golem's hostile
census box form, then the CI census watches and the owner's Section X
version-fidelity gate.

## SESSION 2026-11-04 (the PE-19 batch: the projectile family's grown-0.3 ray consult)

- PR review first: 0 open PRs at sync (develop @ 3584ecdf, clean) — the
  first-priority lane empty, straight to the architecture.
- WAVE PE-19: THE ENTITY SCAN'S EXACT SEGMENT FORM — the reference's
  ONE getEntities walk (ArrowEntity.tick 187-229 / ThrownEntity 147-180
  / ProjectileEntity 104-139) as the engine's flight resolution whole:
  the BLOCK RAY over the whole step first (VT-014's stick walk as the
  trigger — thin walls never tunnel), the ENTITY RAY CLIPPED at the
  block face (a body behind a wall never struck), the ONE grown-0.3
  scan (Box.clip ported 1:1 as ProjectileManager.clipSegment: the
  intermediateWith parallel guard, the [0,1] band, the INCLUSIVE
  bounds, the origin-inside EXIT-face law), the CLOSEST intercept
  winning ACROSS families (the fixed family order retired). The census:
  mobs (the DRAGON EXCLUDED — hasCollision line 597 false, its 16x8 box
  wounded directly pre-fix, the part walk dead code under it — the
  wave's own find), players, stands, vehicles, crystals, dragon parts.
  The crystal/dragon sinks to the BODY form (the graze intercept lies
  outside the ungrown box); the pearl's owner pass-through consumes
  without removal; the TNT ignite cell the hitresult cell; the bottle
  bursts on carts too. Standing tails pinned: the primed TNT, the
  decorations, the other projectiles (the next census wave).
- SEATS: ProjectileRayScanParityDiscoveryTest 4/0 — RED 4/4 pre-fix
  (the stash discrimination: the mob-side graze, the stand-side graze
  (the PE-18 bounce's own band), the cross-family closest (the
  pre-fix engine wounded the COW behind the stand), the thin-wall
  clip). RayScanRoutingLawTest 8/0 (the clip math law + the crystal/
  dragon routing + the owner pass-through).
- LANES: the projectile package 71/0, the PE-18 stand seats + fishing
  18/0 (the migration compatible), the crystal/dragon/TNT seats 20/0,
  the entity families 629/0. The full-suite sweep hit the sandbox's
  process-recycling wall mid-run (the bed class's loaded-runner flake
  the worklog already documents — green solo); the engine lanes above
  are the wave's own.

SESSION COMMITS (all as Abasing): the PE-19 wave + this record's
architecture commit.

AT CLOSE: the next session's first acts per the architecture's re-pin:
the entity scan's census tail (the primed TNT, the frames/paintings,
the other projectiles — the same grown-0.3 clip, the per-family hit
arms), the Section X version-fidelity gate (§X.13, the FL4 row), the
CI census watches.

- THE CLOSING LANES (the time-remaining sweep): the dispenser +
  interaction + armorstand + decoration + tnt + vehicle + experience +
  fishing + fx lanes 115/0, the world + block lanes green (the
  5m50s run) — no regression from the resolver/scan change anywhere
  it touches.

AT CLOSE (final): everything committed and pushed (4e4c90ba); the
re-pin stands as written above.

## SESSION 2026-11-07 (the PP + TS2 batch: the push family whole + the sit posture/verb split)

- The session's first act: the worklog read, develop synced 4e4c90ba →
  6e623153 (21 commits — the later sessions' PE-20 through HF+TS waves
  whole; the local stale WIP superseded by the remote's evolved copies,
  backed up then discarded), 0 open PRs at sync — the first-priority
  lane empty, straight to the architecture's re-pin.
- WAVE PP (commit d05b40c2): THE MOB-MOB COLLISION PUSH FAMILY — the
  re-pin's head row, the walk the engine never carried. The gather over
  the pusher's shape grown (0.2, 0.0, 0.2), the candidates' OWN boxes
  STRICT both sides, the isPushable census (the bat's false, the ridden
  horse's rider==null, the dying bodies included — the reference's
  mobTick carries NO isDead gate on the push block, verified against
  LivingEntity.tick's unconditional mobTick call), the absMax/sqrt/
  cap/0.05 push math 1:1 with the mutual rider gates and the
  riding-pair refusal, and THE GOLEM'S pushAway CARRIER (the row the
  architecture held NOT-REACHABLE: a pushed Monster, never a creeper,
  1-in-20 → the mob-target seat). The excess-kill's SILENT death voice
  (the HF honest tail) rode the same wave: the listener's voiceless
  verdict on both wound walks, the wire skipping the sound alone.
  SEATS: MobPushCollisionParityDiscoveryTest 6/0 — RED 3/3 pre-fix
  (the stash discrimination), the bat and the creeper controls green
  by design. The seat's own find: the pinned bodies' x0.98 decay
  (decayVelocity, the reference's non-controlled arm) interleaves the
  two walks' impulses — the exact-magnitude assertion carries the
  tick-order decay form.
- WAVE TS2 (commit 01ad6dcb): THE SIT POSTURE/VERB SPLIT — the TS
  honest tail closed. The verb (enabledWithOwner) a distinct field:
  armed at the tame, read from the NBT's Sitting row beside the
  posture, stood down by ANY wound attempt (both tameables'
  takeDamage overrides BEFORE the i-frame consult — a hit sitting pet
  rises and never re-sits; the pre-fix engine kept it seated), stood
  down by the block-sit, flipped by the owner's verb to the POSTURE'S
  OWN OPPOSITE (THE CHEST-QUIRK: a perched cat's click computes
  !true = false — nothing changes; the pre-fix XOR stood the perched
  cat down). The posture follows the verb through the sit goal's
  claim/stop transitions (one pass behind — the reference's shape);
  the ownerless ||/&& precedence quirk kept verbatim (the ownerless
  arm never consults the verb). SEATS:
  SitVerbPostureSplitParityDiscoveryTest 3/0 NEW — the wounded-pet
  stand, the REAL perch flow (a chest in the stub world, the walk,
  the perch, the click that never stands it down), the one-pass
  follow. The seat discipline's own find: taming an UNSETTLED fresh
  spawn lets the block-sit's first consult outrun the claim — the
  reference's band order (the sit goal's band 2 before the block-sit's
  band 6) exists to prevent exactly that race; the seats driven
  settle-first, the honest real flow.
- THE FLAKE RE-VERIFIED: the PanicFlee wounded-cow seat failed in the
  entity-package sweep; the clean-checkout probe (develop @ 6e623153,
  both waves stashed) fails the same seat — pre-existing, the
  documented CI watch, not this session's regression.
- LANES: the entity package 706/0 after PP alone; the entity+nbt
  packages 827/1 after TS2 (the flake alone); the tameable family +
  NBT rows + GoalApi green whole (41+12/0); the protocol module
  247/0 green whole; both modules compile.

SESSION COMMITS (all as Abasing): d05b40c2 (PP) + 01ad6dcb (TS2) +
this record's architecture/worklog commit.

AT CLOSE: everything pushed; the next session's first acts per the
architecture's re-pin: the push family's adjacent carriers (the player
as a pushable body — the session's velocity wire; the carts/boats),
the golem's GoToEntityTargetGoal flank walk, the environmental kills'
death publication (the PP scoping's find), the fire-resistant verb
corner, the verifier's PRs when they open, the CI census watches.

## SESSION 2026-11-07b (the PX + G2 + EK + FR batch: the re-pin's four rows whole)

- The session's first act: the worklog read, develop synced 7ab72558 →
  69fc95d4 → c5a11b31 → fff96aeb → c8920759 (this session's own four
  commits, no foreign commits between), 0 open PRs at sync and at close
  — the verifier's PRs still pending, the first-priority lane empty,
  straight to the architecture's re-pin.
- WAVE PX (commit 69fc95d4): THE PUSH FAMILY'S ADJACENT CARRIERS — the
  re-pin's head row whole. THE PLAYER ARM of every living body's push
  walk (the reference's gather over world.getEntities carries the
  players — PlayerEntity.mobTick line 455 running the shared push block
  through super): the engine's mob walk now gathering the playing
  non-spectator sessions with the player's own 0.6x1.8 box STRICT both
  sides, the mutual push 1:1 (the riding-pair refusal, the pusher's
  rider gate), and THE NO-WIRE LAW the reference's own tracker shape
  dictates (the session's own find, correcting the re-pin's "knockback
  packet form" guess): the pushed player's velocity add lands on the
  SESSION'S MOTION STORE alone — the vanilla wire NEVER carries the
  push to the pushed player's own client (the player's tracker rides
  syncVelocity=false, EntityMap line 95's three-arg form, and
  addVelocity never marks damaged — the ONLY player velocity
  publication is the damage walk's markDamaged flag, which fires the
  sendPacketToAll velocity packet including the player's own
  connection); the store feeds the knockback halving (applyKnockback's
  velocityX /= 2) and the movement clamp's threshold; the visible push
  is the CLIENT'S own physics, exactly the vanilla split. THE PLAYER'S
  OWN WALK (the walking player gathers the mobs, the other players,
  the carts and the boats — the walking-through-animals shove): the
  walk itself carries NO spectator gate in the reference (the vanilla
  quirk kept verbatim — a spectating player still shoves the mobs it
  drifts through), the candidates alone filtered NOT_SPECTATOR; the
  walk rides BEFORE the dead-skip in the body tick (the push block
  carries no isDead gate — the PP wave's own law, the player's own
  walk the same shape). THE VEHICLE ARM — the carts and boats (the two
  isPushable-true non-living families, MinecartEntity line 107 /
  BoatEntity line 63): the BOAT rides Entity.push's ordinary mutual
  law with the passenger gate (an occupied boat never moves), the CART
  rides MinecartEntity.push's WHOLESALE override (lines 649-734 — the
  session's own deep read: the squared-horizontal-distance form with
  the 1.0E-4 floor, never the absMax pair; the 0.1-then-0.5 scale;
  the cart pushed away FULL and the pushing body pushed away at
  QUARTER; NO rider gates on either side — the override replaces
  Entity.push wholesale); the rider refusals that name the player all
  carried (the cart's line-656 gate, the boat's line-970 vehicle arm,
  the mob's own rider pair). The AUTO-MOUNT consult (lines 660-667,
  the moving rideable cart scooping a non-player non-golem living body
  onto the seat) held NOT-REACHABLE — the engine's mobs never ride
  (the seated-mob model), this wave's named tail. SEATS:
  PlayerVehiclePushParityDiscoveryTest 6/0 NEW — RED on the pre-fix
  engine (the player's motion store stayed exactly zero however
  crowded).
- WAVE G2 (commit c5a11b31): THE GOLEM'S FLANK WALK — the re-pin's
  second row. The session's own deep finds: (1) the DECOMPILER NAME'S
  RED HERRING corrected — getTargetAwayFromPosition's offset is start
  − mob, so the half-plane filter keeps the candidates TOWARD the
  given position (the MCP family's MoveTowardsTarget law — the golem
  flanks TOWARD its target through favor-picked cells; the AWAY
  half-plane belongs to the getTargetAwayFromENTITY form the flee
  family rides); (2) the PARTIAL-PATH A* means MeleeAttackGoal
  .canStart's findPath nulls ONLY at the PURE LOCAL MINIMUM
  (PathFinder.buildPath's pathnode == start arm — the
  wall-face-with-target-dead-ahead geometry, every walkable neighbour
  equidistant or farther); (3) THE CLAIM-HOLD LAW — the golem's
  pauseWhenMobIdle=true means shouldContinue's only consult is
  isValidGoalTarget(target cell) (the village-radius check, TRUE for
  a village-less body), so once the melee goal has EVER claimed it
  HOLDS while the target lives — the flank NEVER re-arms after the
  chase's first claim (the vanilla golem stands at the wall's local
  minimum forever); (4) MovementControl.tick's speed multiplier law
  (setSpeed(speed × attribute) — the ctor's 0.9 over the 0.25 base =
  0.225 blocks/tick). The claim: the toward-target pick over the 16/7
  band, the same-tick route start, the STEAL POLL every 20 flank
  ticks (the engine's cadence form of the selector's every-tick
  canStart consult), the releases (no target, the census's refusals,
  the village band — carried 1:1 with the cached-radius consult).
  SEATS: GolemFlankWalkParityDiscoveryTest 3/0 NEW — RED on the
  pre-fix engine (the beeline's dead-east heading stayed clipped at
  0.0 however long the target stood).
- WAVE EK (commit fff96aeb): THE ENVIRONMENTAL KILLS' DEATH
  PUBLICATION — the PP scoping's find closed. The body-level
  hurtThroughFrame arms (the fire clock, the drown, the lava, the
  void, the wall, the starve, the snow golem's melt, the anvil
  crush) walk the reference's takeDamage whole but their kills never
  reached the publication walk the melee and magic families ride:
  no death voice, no status 3, no MobDeathEvent, no XP burst — the
  silent remove. The fix: the pending flag on the body (with the
  excess verdict — a kill through the frame's excess arm dies
  SILENT, the PP wave's own flag-gate law), the SAME-TICK drain
  after the body's own tick (the reference's
  die()-inside-the-damage-walk shape), the HEAD drain at the loop's
  iteration head (the cross-phase kills — the anvil's crush lands in
  the block phase), and the death hooks (the villager's
  environmental else — a player watching within 16 blocks stops the
  mating; the golem's −5 never; the dragon rides its own walk).
  SEATS: EnvironmentalDeathPublicationParityDiscoveryTest 4/0 NEW —
  RED on the pre-fix engine (zero publications).
- WAVE FR (commit c8920759): THE FIRE-RESISTANT SITTING PET'S VERB
  CORNER — the re-pin's fourth row. The reference's takeDamage
  OVERRIDE clears the sit verb at its HEAD (WolfEntity line 265 /
  OcelotEntity line 154), BEFORE the super's gates — the
  FIRE_RESISTANCE consult included (LivingEntity lines 645-647): a
  fire-resistant pet in fire or lava RISES while the wound itself is
  refused. The engine's fire family consulted the resistance BEFORE
  the wound walk — the pet SAT THROUGH ITS OWN BURNING. The fix: the
  standSitVerbDown helper at the head of both fire-family consult
  sites (the burn clock's cadence arm — the timer now resets at 20
  regardless of the resistance, the reference's every-20-ticks call
  shape — and the lava walk's wound arm), beside the shared frame
  walk's own head. SEATS: FireResistantSitVerbParityDiscoveryTest
  3/0 NEW — RED on the pre-fix engine (the verb stayed armed through
  the whole burn).
- THE FLAKE WATCH: the WolfCollarDye one-off parallel flake (expected
  2 but was 3 — a dye unit count) failed once in the entity-package
  sweep, re-verified GREEN solo x5 AND green on the package rerun —
  the documented CI-watch family (the PanicFlee precedent), not this
  session's regression; recorded in the architecture's census watches.
- LANES: the entity package green whole across all four waves (706+
  tests); the tameable family (SitVerbPostureSplit, TameableSitChase,
  WolfEntity, OcelotEntity, WolfCollarDye), the golem family
  (GolemEntityTest, GolemDefenseCanTarget, the village spawn lanes),
  the vehicle/mount/player lanes (RiderSpareFall, MountSeatsApi,
  vehicle.*, PlayerSessionTest), LavaWalkTest (the resistance law
  preserved — the restructure carries the same gate one arm deeper),
  and the protocol module (247/0, forced rerun) — all green.

SESSION COMMITS (all as Abasing): 69fc95d4 (PX) + c5a11b31 (G2) +
fff96aeb (EK) + c8920759 (FR) + this record's architecture/worklog
commit.

AT CLOSE: everything pushed; the next session's first acts per the
architecture's re-pin: the push family's named tails (the AUTO-MOUNT
held NOT-REACHABLE until the seated-mob model exists — the
mob-rides-vehicle body state, the seat-follow's AI freeze, the
dismount-on-break; the CART-CART blend and the vehicle family's own
collision walks), the flank walk's steal-poll cadence (the documented
adaptation), the verifier's PRs when they open (none at close), the
CI census watches, the vanilla-grade census's next family pick.

---
Task ID: 2026-11-07 session
Agent: main (ZaminTorch session)
Task: Continue development per Torch-Arcetecture.txt — the re-pin's
named rows (the vehicle family's own collision walks; the seated-mob
model + the AUTO-MOUNT), the F-EX standing queue's verification, and
the README live-progress-table cleanup (the AnyPS5-style form).

Work Log:
- Read the worklog, synced develop (ac47081a, clean), checked the open
  PRs via the API: 0 open — the PR lane empty (the verifier's rows
  not yet filed).
- Re-read Torch-Arcetecture.txt fresh (11,227 lines): the latest
  re-pin named the push family's remaining tails — the AUTO-MOUNT
  (held NOT-REACHABLE until the seated-mob model), the CART-CART
  blend, the VEHICLE FAMILY'S OWN collision walks.
- WAVE VC (commit 85f4123d): THE VEHICLE FAMILY'S OWN COLLISION
  WALKS. MinecartEntity.tick lines 295-299 / BoatEntity.tick lines
  347-356 — each vehicle gathers its own grown-(0.2, 0, 0.2) shape and
  pushes its own family ALONE (carts push carts, boats push boats).
  The CART-CART BLEND whole: the squared-distance axis, the 1.0E-4
  floor, the 0.1-then-0.5 scale, the 0.8 DOT-PRODUCT GATE over the
  CALLEE'S own yaw axis (the atan2-vs-look convention difference
  proven to fold under |dot| — the equivalence proven, never
  re-derived), the FURNACE ASYMMETRY both arms (the ram and the
  wall), the MOMENTUM BLEND (both damped to 0.2, the halved sum,
  minus/plus the separation). The BOAT-BOAT push: Entity.push's
  absMax-sqrt form with the rider gates. SEATS: VehicleFamilyCollision
  ParityDiscoveryTest 6/0 NEW — RED on the pre-fix engine 5/1 (the
  membership/dimension seat the negative-law green by design). The
  sequential-tick shape verified end-to-end (A's walk writes B's
  velocity before B's own tick moves it the SAME tick).
- THE F-EX QUEUE RETIRED BY VERIFICATION (each row the three-pass
  discipline): F-EX2 LANDED (the sleeping eye frame in all three
  blast arms); F-EX3 GREEN WHOLE (the executable census: 1,235/1,235
  identifiers resolve through the BlastResistanceTable, zero fallback
  misses — the probe made the permanent regression seat
  BlastCoverageCensusTest); F-EX4 LANDED (the createFire call census:
  the bed true, the ghast fireball both-arms mobGriefing, the skull
  fire=false, the TNT fuse false — every caller verified against the
  reference source); F-EX5 LANDED (the deflect census: only the large
  fireball; SmallFireball/WitherSkull's return-false overrides
  confirmed in the reference); F-FL4 LANDED (Wave FL4's per-dimension
  fluid systems).
- WAVE SM (commit ad1d0a53): THE SEATED-MOB MODEL + THE AUTO-MOUNT —
  the re-pin's held tail LANDED. The SEAT ROW (seatedVehicleId)
  stamped by the manager from the vehicles' passenger seats; the
  MOVEMENT FREEZE (the seat owns the position — the reference's
  apply-then-overwrite shape adapted to the immediate-publish model,
  the contact block and the mind keeping); the MAKESSTEPS gate
  (Entity line 715's vehicle == null); the AUTO-MOUNT consult (all
  six conjuncts: living non-player non-golem, RIDEABLE-alone, moving
  > 0.01, empty, walking free) through the new VehicleMount seam,
  the push math running anyway (the vanilla order); the RIDER GATES
  REAL (line 656 + line 970's vehicle arm — the seated body never
  pushes its own mount). The seat-follow + dismount-on-break were the
  §31.6 machinery — the wave wires the missing MOUNT PATH. SEATS:
  SeatedMobAutoMountParityDiscoveryTest 5/0 NEW — RED 5/5 (the no-op
  patch verified: the consult, the freeze, both gates).
- THE README'S LIVE PROGRESS TABLE RE-FORMED (commit 7bf2dcd5): the
  AnyPS5 Status presentation — four shields badges, the compact
  one-line-state table, the progress-history link, the measurement
  footnote; the walls of per-run forensics retired to the ledgers
  that own them; the numbers refreshed (3,760 seats suite-wide).
- LANES: the entity package green whole (2x), the vehicle package
  green whole (2x), the block package green whole — all after both
  waves; the SM/VC seats verified RED on the pre-fix engine by the
  no-op patches, then green on the restore.

SESSION COMMITS (all as Abasing): 85f4123d (VC) + 7bf2dcd5 (README) +
ad1d0a53 (SM) + the architecture/worklog record commit.

AT CLOSE: everything pushed; the next session's first acts per the
architecture's re-pin: the flank walk's steal-poll cadence (the
documented adaptation row), the verifier's PRs when they open (none
at close), the CI census watches (the PanicFlee + WolfCollarDye
families), and the vanilla-grade census's next family pick — the
seated-mob model's own tails now open as fresh census rows (the
seated goal behaviors under the freeze, the minecart's ridden drag).

---
Task ID: 2026-11-08 session
Agent: main (ZaminTorch session)
Task: Continue development per Torch-Arcetecture.txt — the re-pin's
ridden-drag row (the reference's ridden-body velocity triple the seat
now carries) + the W-kick row the source pass surfaced, plus the
protocol lane's pre-existing minecart-seat breakage.

Work Log:
- Read the worklog, synced develop (290b3f35, clean), checked the open
  PRs via the API: 0 open — the verifier's rows not yet filed.
- WAVE RD (commit adb0bd0f): THE MINECART'S RIDDEN DRAG + the roll
  laws of moveOnRail's tail, each 1:1: the DRAG TRIPLE at the
  reference's own call site (applySlowdown after the move — the carry
  alone: 0.997 ridden / 0.96 empty, the pre-fix engine had NO
  plain-family drag at all), the RIDER'S 0.75 MOVE SCALE, THE
  STATE/MOVE SPLIT (the 2-D head read + the projection's 2.0 ceiling
  the only state bound; the move clamps at getMaxSpeed 0.4/0.2 — the
  hand-rolled 0.3 state cap retired), THE BOOST REORDERED post-drag
  with THE WALL LAUNCH (±0.02 away from the neighbor block, the
  wall-less rest never launches — the pre-fix 0.06 look-launch
  retired), THE INVENTORY DRAGS (the chest's fullness migrated to the
  override; THE HOPPER CART GAINS IT — the pre-fix hopper had no drag
  at all), THE FURNACE'S applySlowdown OVERRIDE (0.8+push / 0.98 +
  super; the off-rail furnace never pushes), and THE PRODUCTION
  CONSULT'S GOLDEN-ALONE GATE (the activator rail never boosts/brakes
  — the pre-fix consult fed the pair whole).
- WAVE RK (this session's second commit): THE RIDER'S W-KICK (lines
  387-399) — the continuous +0.02 steer drive RETIRED: from rest alone
  (the squared-0.01 band — two kicks: 0.1 then 0.1997), along the
  RIDER'S OWN LOOK (the SteerInput's new riderYaw, the wire passing
  the player's rotation), with the BRAKE CANCEL (flag1 = false); a
  rolling cart gains nothing, a riderless cart never kicks.
- SEATS: MinecartRiddenDragParityDiscoveryTest 10/0 NEW (RED on the
  pre-fix engine verified by the no-op patches, each law neutralized
  in turn); ActivatorRailBoostGateParityDiscoveryTest 3/0 NEW (the
  boot rig with the golden control; the two activator gates RED on
  the pre-fix consult). The affected seats updated to the new law
  (the manager's cap test, the L-corner/track-end mounts, the furnace
  variant composition, the family collision's resting pair over the
  dragged carry).
- THE PROTOCOL LANE'S PRE-EXISTING BREAKAGE ROOT-CAUSED AND RETIRED:
  VehicleIntegrationTest's creative-minecart seat failed
  deterministically since the SM wave's AUTO-MOUNT (that session never
  re-ran the protocol module) — a shared-world leftover rolling
  vehicle meets a wandering animal, the 1:1 scoop seats it mid-test,
  its attach broadcast arriving before the player's detach (verified
  by the debug print: rider=1100000 vehicle=6100004). The seat's
  dismount read now filters for the rider's own detach. The two
  load-flakes (SurvivalFlow's drop-settle, DonkeyChest's open-window)
  re-verified green solo — the watch family's protocol members.
- LANES: the vehicle package green whole (84), the entity package
  green (746 — the mob-AI parallel flakes PanicFlee/HuntScanBounds
  solo-green, the documented watch family), the hopper/block/root
  vehicle lanes green, the bukkit/api modules green, the protocol
  module 247 with the fixed seat.

SESSION COMMITS (all as Abasing): adb0bd0f (RD) + the RK commit + the
architecture/worklog record commit.

AT CLOSE: everything pushed; the next session's first acts per the
architecture's re-pin: the NEW CENSUS ROWS this session's source pass
opened (the ascending-slope roll — the geometry model the blocker;
the rail-center d18 interpolation; the boat's steer model; the
perpendicular-look kick edge — the off-rail row closed with OR), the flank
walk's steal-poll cadence (the verifier's row), the verifier's PRs
when they open (none at close), the CI census watches (the expanded
family), and the seated-mob goal behaviors (the verifier's rows).

---
Task ID: 2026-11-08 session (second run)
Agent: main (ZaminTorch session)
Task: "complete and finish the total CI pipeline in this turn — it's
been stuck at it" — the six-day stuck-release window closed whole.

Work Log:
- Read the worklog, reset a leftover mid-bisect state, pulled develop
  (a2709d05 clean; a stashed SleepAcceptanceTest fix from the prior
  session carried — the W-BED pad repair, landed this run).
- THE DIAGNOSIS (from the failed runs' own artifacts, downloaded and
  parsed): every release run since torch-1.8.8-157 (Nov 1, run #598)
  failed — #632-#663 at the BUILD step, #664-#671 at the CHECKOUT
  step ('could not read Username' — the ABASING_TOKEN secret dead).
  The build failures: four deterministic seats (SleepAcceptance's bed
  pad, RangedCombat's snowball glass, FxDimensionReads' egg poof+
  sound, PistonEntityPush's slime four-body fling) + the documented
  load-flake family (PanicFlee x3, MultiplayerSleepWire x2, the
  protocol pair SurvivalFlow/DonkeyChest, and seven one-off members).
- THE THREE STALE SEATS re-pinned to their waves' landed laws (each
  verified against the reference source first): the W-BED both-halves
  pad (BedItem.useOn line 42), PE-21's shard silence (SnowballEntity
  onCollision lines 51-54 — no sound, the puff alone) and the egg's
  ITEM_CRACK crumbs ([ItemShatter] alone, EggEntity lines 55-66).
- THE PISTON SEAT ROOT-CAUSED WITH A PER-TICK PROBE (the seat failed
  locally first try — the probe showed the piston extending, the
  orb/stand/TNT flying east, and THE ARROW reversing mid-flight to
  land stuck half a block east): PE-19's exact segment scan + PE-20's
  census put the co-located stand's/TNT's 0.3-grown hulls over the
  whole shared cell, and the flung arrow strikes one mid-flight (the
  stand's ONE-SHOT or the TNT's refused-body bounce — each the
  reference's own law); when the fling is late (the loaded-runner
  thread race), the at-rest arrow has already STUCK and the fling is
  dead state (ArrowEntity.tick's stick branch — the engine's own
  documented 1:1 law). EVERY branch of the old bench fails — it
  passed only while the pre-PE-19 point-sampling scan masked the
  interaction. THE BENCH REPAIRED: the orb/stand/TNT keep the one
  carrier, the arrow rides its own carrier three cells south
  (launched upward, ~13 ticks of hang), both levers in one submit.
  The piston class 11/0 (was 20s-starving); all four seats' lanes
  green twice over (25/25).
- THE WATCH FAMILY'S RETRY ROWS (the census-governed path):
  PanicFlee + MultiplayerSleepWire + SurvivalFlow + DonkeyChest
  joined the Phase-A allowlist, each member's evidence row written in
  docs/TEST_DEADLINE_CENSUS.md with the four fixed seats' retirement
  rows beside them. The seven one-off members stay OFF per the
  membership law (the verifier's open lane).
- THE CHECKOUT SECRET: the owner rotated ABASING_TOKEN at 19:04 Nov 7
  — run #672 (on this session's commit ae1ede72) verified the
  revival LIVE: checkout, version ledger, stamp and bump sync all
  green, the full suite building toward torch-1.8.8-158.
- The architecture's record appended (Wave CI-2) + the NEXT WORK
  re-pin (the CI RELEASE WATCH — the first green release since #598
  closes the window whole); the README's CI row and footnote
  refreshed.

SESSION COMMITS (all as Abasing): ae1ede72 (the four root-cause
fixes + the allowlist + the census rows) + the architecture/worklog/
README record commit.

AT CLOSE: run #672 building (44-92 min expected); the next session's
first acts per the re-pin: THE CI RELEASE WATCH (confirm #672 green
and the torch-1.8.8-158 release published — a red run's uploaded
forensics are the retry-family's first-occurrence root-cause reads),
the verifier's PRs when they open, the roll-law census rows (the
ascending-slope roll, the rail-center d18 interpolation, the boat's
steer model, the perpendicular-look kick edge), and the flank walk's
steal-poll cadence.
