# Torch Worklog

Working record of what is done and what is not. Append-only; newest units at
the bottom. The behavior-level status of every mechanic lives in
`VANILLA_1_8_8_COMPATIBILITY.md` — this file tracks the engineering stream.

## Completed

- **dev.6 → dev.12 baseline** (pre-ledger): terrain generation, block world,
  survival mining, chests, furnaces, farming, fire, beds/sleep, commands,
  Paper command set (`/me /tell /ban-ip /xp /difficulty /seed /tps ...`),
  protocol 47 stack, anti-cheat, item entities, experience orbs, projectiles,
  vehicles (boat/minecart), horses (temper taming, saddle, armor, bucking),
  pigs, villager trading with 7-use stock, package rename to
  `net.zaminmc.torch.*`, mounts slice, released through dev.12.
- **Vanilla 1.8.8 reference in-repo** (`0a279e5`): `reference/1.8.8/`
  (1634 decompiled classes) — the porting source for every mechanic since.
- **Protocol batch two** (`7f6f761`): teleport relative-flag byte fixed,
  respawn/tp chunk re-anchoring, collision-less placement rules, ByteBuf
  leak fix, villager trade test budget.
- **Slice 1 — crafting** (`40bd083`): vanilla matcher (offset scan, mirror,
  virtual 3x3, recipe order), workbench size byte 0, half-take, craft-all
  limit, close-drop.
- **Slice 2 — mining** (`8ecfa55`): vanilla per-tick progress math, tick-clock
  accumulation, f >= 0.7 finish, wasMining self-complete, crack broadcasts.
- **Slice 3 — falling blocks** (`77dd8d8`): exact tick order, two-tick fuse,
  landing gate, slab quirk, lifetime rule, bounds safety.
- **Slice 4 — pathfinding** (`fca6ef1`): A* + BinaryHeap + WalkNodeEvaluator +
  GroundPathNavigation ports; the old A*-light finder deleted.
- **Slice 5 — knockback + hunger** (`f3dec31`): applyKnockback recipe with the
  server-motion residual, FoodStats semantics, per-meter exhaustion ledger,
  damage-source exhaustion charges.

- **Slice 7 part 2 — enchanting-table menu state** (`63632d7`): the
  two-slot transient menu (item max 1, lapis), the seeded cost ladder with
  the `< slot+1` zeroing, the id|level<<8 clue picks, the bookshelf power
  scan's exact geometry, the lapis-slot dye-blue gate through the new
  WindowClicks SlotFilter/per-slot-max semantics (the reference's PICKUP
  walk: empty-slot split, merge clamp, gated swap, not-allowed reverse
  merge), the enchant button's gate ladder + the null-offer still-pays
  quirk, book-to-enchanted-book conversion, the old-seed/rerolled-seed
  recompute ordering, the close-drop, wireSeed &-16; lapis +
  enchanted-book items, bookshelf + enchanting-table blocks,
  ItemStack.enchantments() accessor. Tests: api+core suites green;
  dev.13 artifacts cut and smoke-booted (`67a1ef9`).

- **Slice 7 part 3 — the table's live window** (`09ac43b`): the per-open
  menu on the session (seed read at open, the open-time recompute with the
  attached world view), right-click opens the vanilla GUI (Open Window
  minecraft:enchanting_table size byte 0, the 38-slot Window Items, the
  seven initial Window Properties), the per-tick view fan-out diffing the
  properties + resyncing on the menu revision, the gated click routing,
  shift-click quick-move both directions, the 0x11 Enchant Item walk paying
  slot+1 levels (the ported applyEnchantmentCosts on the points model) and
  slot+1 lapis with the XP bar resync, the isValid stale-window close with
  the two-slot close-drop; the WindowClicks gate placement fixed to the
  reference arm order, the ItemStack enchantment-id floor fixed to 0
  (protection is id 0), the bookshelf/table light entries, the missing
  items + legacy wire ids. Tests: full suite 491 green (14 new).

## In progress

- **Concurrency Phase 4** — the protocol layer LANDED (worker pool +
  parallel walk + region partition + migration + the router on the live
  tick, `3d49dbc`/`3d77172`, dev.20); remaining: the spatial activation
  (chunks bind their region domain) gated on the per-mechanic boundary
  protocols, which land with their vanilla mechanics (hopper/piston/
  redstone are still unported — the §11 list grows with them).
- **Slice 8b — the nether dimension**: the second world (the nether), the
  changeDimension walk (the 8:1 coordinate scaling, the PortalForcer's
  find/generate portal walk), the teleport arm of the stand clock. Doubles
  as the concurrency Phase 4 spatial activation (the second ownership
  domain live).

## Landed since the ledger opened

- **Slice 7g — Unbreaking** (`7dc252b`): the takeDamage per-unit reduction
  walk (the armor's 60% early-false gate eating the roll without consuming
  the int, the level read once per walk) on every wear surface; the
  wearArmor walk now carries the reference damageArmor /4 min-1 scaling
  (heavy hits wear proportionally). Tests: UnbreakingAcceptanceTest (8) —
  suite 603 green.
- **Slice 7h — Respiration** (`d4fc984`): the breath roll
  (updateBreathUnderwater's keep arm) on advanceBreath; Depth Strider's
  read surfaced with the client-authoritative physics boundary recorded.
  Tests: RespirationAcceptanceTest (5) — suite 608 green.
- **Slice 8a — the nether portal's frame, ignition, block, stand clock**
  (`652077c`): the PortalFrameBuilder (the PortalBuilder port — the exact
  bounds, the X-then-Z axis order, the neighbor-break re-validation), the
  nether portal block in both axis planes (90/1, 90/2 on the wire),
  the flint-and-steel ignition (the FireBlock.onAdded arm), the player's
  stand clock (the 80-tick survival stand, the instant creative crossing,
  the 10-tick cooldown with the entry-edge re-arm, the 4-per-tick decay).
  The teleport arm (changeDimension + the 8:1 walk + the PortalForcer) is
  BLOCKED on the second dimension (8b — which doubles as the Phase 4
  spatial activation). Tests: PortalFrameBuilderTest (12) — suite 620
  green.
- **Permanent architecture Phase 4 second + third units** (`3d49dbc`,
  `3d77172`, dev.20, suite 595 green):
  - **The physical worker pool** (`server/concurrent/DomainWorkerPool`):
    max(1, cores/2) capped 4, parked workers, the `dispatchAndJoin` batch
    as the ENFORCED tick-edge barrier, failure isolation, total shutdown.
  - **The parallel domain walk** (`SimulationScheduler.runDueParallel`):
    caller-bound domains drain inline (the live run byte-identical),
    foreign domains dispatch one drain job each and the join waits; FIFO
    and the single-writer gate survive the dispatch. `EngineTicker.
    attachDomainPool` + the EngineServer boot/shutdown wiring.
  - **The spatial partition model** (`RegionPartition`): the fixed R×R-chunk
    grid, deterministic floor-division (seamless across the origin), one
    stable domain per region, no auto-balancing.
  - **The migration protocol** (`OwnershipMigration`): the §9 phase machine
    (safe-point begin refusing mid-task, the scheduler's admission hold —
    the new holdAdmission/releaseAdmission hooks with drainDomain — the
    old-generation drain, the transfer with abort-on-failure bumping the
    generation, the atomic publish, the run-once contract). Fault-injected.
  - **The router on the live tick** (`EngineTicker.
    attachCrossOwnerRouter`): the §7 target-side apply rides the tick
    after the scheduler walk, before the compute drain; the engine boots
    and shuts the router.
  - Tests: DomainWorkerPoolTest (7) + SimulationSchedulerParallelTest (7)
    + RegionPartitionTest (8) + OwnershipMigrationTest (9) +
    EngineTickerRouterTest (3).
- **Release v0.2.0-dev.20** published with the Phase 4 protocol layer
  (jar + zip + the full changelog).
- **Permanent architecture Phases 1-3 implemented** (the runtime, not the
  docs — commits `52f059b`, `e9667aa`, `6bcec30`, `df66064`):
  - **Phase 1 — ownership primitives** (`server/concurrent`):
    `OwnershipDomain` (dynamic executor binding with counted re-entrancy,
    monotonic generations, the single-writer tick gate, STRICT/DIAGNOSTIC
    enforcement), `OwnershipViolationException` (domain + operation
    context, still an IllegalStateException), `ComputeTicket`/
    `StaleResultException` (the versioned result envelope), `DomainTask`
    (cancel never resurrects). `EngineWorld.attachDomain` +
    `EngineServer` binds `simulation:<world>` for the loop's whole run.
  - **Phase 2 — the scheduler substrate**: `SimulationScheduler` (per-domain
    ready FIFO, the delayed store keyed (dueTick, stable sequence), the
    stable domain-name walk, bounded admission with loud refusal,
    permanent cancellation, total shutdown, the telemetry floor);
    `EngineTicker.attachScheduler` routes submit through it and tickOnce
    runs due tasks before the time advance.
  - **Phase 3 — the compute subsystem**: `ComputeSubsystem` (bounded
    admission + the conservative worker pool, zero mutation authority on
    the workers, typed handles closing the apply/stale arms, the owner's
    drainResults at the tick boundary) + **the first live offload**:
    on-demand chunk generation left the tick (the detached generation on
    the pool, the owner-side `installGenerated` with the never-overwrite
    guard, the request dedupe, the callbacks served in the drain context).
  - Tests: OwnershipDomainTest (6) + EngineWorldDomainTest (2) +
    SimulationSchedulerTest (11) + ComputeSubsystemTest (8) +
    ChunkGenerationOffloadTest (4); the suite rides 554 green with the
    enforcement live and identical gameplay.
- **Phase 4 first unit — the CrossOwnerRouter** (`7c73b2a`): the §7
  protocol primitive — the immutable Intent envelope, exactly-once
  admission within the in-flight window (the op id as the idempotency
  key, the window clearing at drain), the bounded per-target pending
  with the loud refusal, the target-side drain running intents in its
  own bound context, the poisoned-intent isolation, the total shutdown,
  the telemetry. Tests: CrossOwnerRouterTest (7) — suite 561 green. The
  router's first full run exposed the bow-charge pin racing the tick
  loop's own per-tick advance; PlayerSession gained the
  freezeBowChargeForTest seam (the setVelocityForTest shape) and the
  flick/mid-draw pins hold exactly under any load.
- **Release v0.2.0-dev.19** published with the architecture Phases 1-3
  (jar + zip + the full changelog).
- **Slice 7f — the loot family** (`a82c3d9`): silk touch (the
  hasSilkTouchDrops gate as the SILK_TOUCHABLE set), fortune (the
  foreign-drop multiplier + gravel's nextInt(10-fortune*3) flint walk),
  looting (count = base + nextInt(1+looting) at the three kill sites).

- **Slice 7e — thorns** (`e152dad`, dev.18): the reference wildcard walk
  (ThornsEnchantment lines 38-61 + EnchantmentHelper
  applyProtectionWildcard lines 152-161 + getEquipmentWithEnchantment
  lines 220-228) fired at all three sites — the PvP melee landing
  (PlayerEntity.attack line 1016), the mob-melee landing
  (Entity.damageEntity lines 1943-1947, the victim's armor row captured
  BEFORE the damage walk so a killing blow still retaliates before death
  scatters it), and the arrow hit (ArrowEntity line 264, the shooter
  resolved through the player then mob band — the skeleton shooter takes
  its share). Each piece rolls 15%/level independently; a proc deals 1-4
  (level-10 flat above the elbow) through the attacker's own
  armor-and-protection walk with the damage.thorns 0.5F/1.0F sound; the
  wear (3/1, accumulated per visited piece) rides the FIRST thorns stack
  (the helmet wears for a proc the chest rolled — pinned by the test's
  helmet-vs-chest wear split). The targeted wear rides the new
  PlayerInventory.wearArmorStack (the unbreaking hook point ledgered
  with its slice). Tests: `ThornsAcceptanceTest` (5) — suite 523 green.
  Release v0.2.0-dev.18 published with jar + zip + changelog.

- **Concurrency architecture assignment completed** (`09eeaed`, `430ed18`,
  `36e7e97`): the Folia 26.2.x archive extracted outside the repo, all 21
  patches read, and the mandatory class-by-class inventory produced — 460
  modified Java path entries (200 Minecraft-side + 206 Paper-side base-patch
  paths, hunk counts + churn, keyword-categorized;
  `docs/FOLIA_PATCH_INVENTORY.md`); the subsystem deep-dives A–G traced at
  hunk level (`docs/FOLIA_FORENSIC_AUDIT.md`: the regionizer's merge/split
  under the structure lock, `TickRegionScheduler.runTick`'s
  `tryMarkTicking` single-writer acquisition + `getPeriodsAhead` catch-up
  + `TickTime` scheduled-vs-actual recording, `EntityScheduler` run-time
  re-validation retiring stale entity tasks, `RegionizedWorldData` state
  extraction, the hopper's redstone-time rebasing + worldData/ThreadLocal
  flag migration reviewed hunk-by-hunk, the piston ownership guards, and
  follow-up patch 0004's boundary-update drops); the four mandatory hard
  cases answered from the patches with Adopt/Adapt verdicts; the category
  distribution extracted (62% of Folia's mass is entity bookkeeping, not
  the scheduler); decision-to-patch-to-test traceability. The §20
  comparison table (17 dimensions), 20 ADRs, and the 8-phase rollout
  landed in `docs/CONCURRENCY_DECISIONS_AND_ROLLOUT.md`; the component
  graph + six lifecycle sequence diagrams in `docs/CONCURRENCY_DIAGRAMS.md`;
  `docs/CONCURRENCY_ARCHITECTURE.md` evidence gaps closed.
- **Slice 7d — the bow family** (`d8f1e6d`, dev.17): the release charge
  curve riding the reference's `(f*f + f*2)/3` shape — the 0.1 flick gate
  aborts at charge 2 (0.07) and fires at 3 (0.1075), the 1.0 clamp marks
  the full draw and sets the arrow's crit flag; Infinity (creative OR the
  enchantment leaves the quiver untouched; the pickup=2 retrieval surface
  ledgered with its slice); Power feeding the damage multiplier
  (level * 0.5 + 0.5), Punch riding the landed hit's knockback, Flame
  igniting the arrow 2000 ticks (the reference `setOnFireFor(100)`
  whole-flight) with the 100-tick target ignite on hit — the burning flag
  broadcast at spawn through Entity Metadata 0x0C index 0; the launch
  sounds moved to the caller recipes (the bow's pitch riding the shaped
  charge, the shard throw's 0.4/(rand*0.4+0.8)). Tests: `BowFamilyTest` +
  ranged-acceptance walks; suite 518 green.
- **Release v0.2.0-dev.17** published on GitHub Releases with the jar,
  the distribution ZIP, and the changelog (the dev.13 loss class closed —
  releases now ride every dev build).
- **Slice 7c — attack crits + the thrown spawn pull-back** (dev.16): the
  vanilla critical hit over both attack walks (reference/1.8.8
  PlayerEntity.attack lines 958-1008): the flag gate (fallDistance > 0,
  airborne, not climbing, not in water, not blind, unmounted, living
  target — the blindness arm reads false until the potion slice exists,
  structurally the no-effect read), the 1.5x multiply riding the BASE
  damage before the enchantment family joins (the reference's `f *= 1.5F`
  then `f += f1` order), and the landed-hit bursts — Animation 0x0B code
  4 (the crit burst) on the crit flag and code 5 (the magic-crit burst)
  whenever the damage family added anything, falling or not — broadcast
  over the new EntityAnimationObserver (the reference's
  ServerPlayerEntity.addCritParticles audience: the attacker's tracking
  set plus the attacker; the player target resolves through each
  observer's id space with the self arm). The thrown spawn pull-back
  landed as its ledgered unit: the living-thrower constructors' swapped-
  trig legacy (ThrownEntity lines 58-62, ArrowEntity lines 89-93: x -=
  cos(yaw)*0.16, z -= sin(yaw)*0.16, y -= 0.1 — NOT the look vector, the
  offset is exactly perpendicular to the throw direction), so thrown
  bodies no longer lean on the thrower-immunity tick window (the
  snowball-shatter race class closed at the root); the skeleton's
  target-aimed constructor spawns without the pull-back and stays on the
  eye-aimed shape until the bow-family slice. Tests:
  `CritParityAcceptanceTest` (the standing plain-7 control with no burst,
  the falling 10.5 one-shot vs the 10-hp pig with the Animation 4 burst
  and no magic spark, the grounded Sharpness V burst riding Animation 5,
  the PvP bare-fist crit landing exactly 1.5 on the victim's wire body) +
  `RangedCombatAcceptanceTest.theThrownShardSpawnsBehindTheEyeLikeTheReference`
  (the exact 0.16 lateral read — the offset is invariant under flight
  because it is perpendicular to the travel line, a race-free probe); the
  mining seed helper got the 45s commit budget (the same load-starvation
  flake class the fire test's latch hardened). Full suite 508 green.

- **Slice 7 part 4 — the enchantment effect hooks wired over the live
  engine**: the `DamageKind` vocabulary (`entity/damage`, the reference
  DamageSource predicates the protection branches read: fire/fall/explosive/
  projectile/out-of-world/unblockable), the EnchantmentHelper consumption
  half (level/highestLevel readers, the getExtraProtection per-piece curve
  with its per-kind scalers, modifyProtection with the 0..25 clamp and the
  legacy half-to-full roll `(p+1>>1)+nextInt((p>>1)+1)`, the
  modifyOnFireTimer and modifyExplosionDamage shaves), the protection step
  on every player damage entry (melee PvP + mob melee + arrow + explosion
  + in-fire/on-fire/fall/drown/cactus; starve skips as unblockable, the
  void's per-piece zero), the blast shave before the explosion pipeline,
  the efficiency + aqua-affinity reads in the dig (replacing the hardcoded
  0/false), the damage family riding the mob's damage category
  (`MobType.damageCategory`: zombie/skeleton undead, spider arthropods),
  the Knockback extra on the attacker's look yaw with the 60% motion decay
  + the sprint wipe, the Fire Aspect pre-set (1s) / level*4 re-arm /
  extinguish-on-refusal quirk, the exhaustion + durability now gated on
  the landed verdict (`MobManager.hurt` returns boolean, the reference's
  takeDamage verdict). Tests: `EnchantmentEffectMathTest` (the exact
  reference tables incl. the roll band + clamp) +
  `EnchantmentEffectAcceptanceTest` (sharpness one-shot vs the plain
  blade, smite's undead read vs the pig's 7, the fire-aspect re-arm
  outliving the pre-set + the burn kill, the protection band vs the exact
  2.38 unenchanted envelope, the Efficiency V dig); full suite green. Two
  flaky seeds hardened on the way: the fire test's setBlock now observes
  the tick-thread commit with a latch (a floating flame can burn out
  before a loaded-box poll sees it), and the snowball shatter race was
  investigated to root cause — the thrower-immunity window vs tick
  skipping under load (the reference spawns thrown projectiles 0.16
  behind the eye; ours spawn inside the thrower box and lean on the
  immunity window — ledgered as its own unit).

- **Slice 7a — enchantment registry + math + storage**: the 25-id registry
  with the reference weights/curves/categories/enchantability (shears = 0,
  the reference's silence honored), the seeded ladder/window/offer math
  (EnchantmentHelper port), `ItemStack.enchantments` (the tag.ench slice,
  merge identity extends), and the ench wire encoding in SlotNbt. 11 tests;
  suite 477 green. Known gaps: table UI, effect hooks beyond the damage
  family, seed-exact second-pick mapping (HashMap bucket order — ledgered).

- **Slice 6 — breeding + donkey chest** (`be37243`, follow-ups): the love
  window (600 ticks, event-18 burst, damage clears, off-age clears), the
  EntityAgeable age walk with the wire's index-12 byte and grew-up delta,
  the baby-feed tenth-growth, the AnimalBreedGoal landing (grown-8 scan,
  60-tick proximity, squared-9 gate, 6000 cooldown, -24000 childhood, the
  1-7 XP burst), the horse family rules (tamed + FULL health + unmounted,
  mule barren, 0x1 makes the mule), the HorseBaseEntity feed table
  (heal/grow/temper/love arms with the untamed temper band), the pig's
  carrot and the per-kind breeding items, the donkey chest (15-slot grid
  through the shared cursor semantics, the faithful 38/53 Open Window
  counts, the death spill order), the missing food items + Foods rows, and
  the lost `ENTITY_STATUS_HURT/DEAD` constants restored. Tests: 13 unit +
  2 wire tests; suite 466 green (was 451). Breed spawns defer past the
  mob-iteration loop (CME); ambient heart particles pending a verified
  heart id (documented in the ledger).

## Not started

- Slice 7 remainder — RESOLVED (survey, Slice 26): thorns, looting,
  fortune, silk touch, power/punch/flame/infinity, unbreaking,
  respiration, and depth strider all landed with their gameplay slices
  (registered in `Enchantments` with their families, wired at the
  reference's own consult sites). The row was stale; kept for the record.
- Slice 8 — nether portals — RESOLVED: Slices 8a/8b/8c complete (the
  frame scan, the ignition, the stand clock, the changeDimension walk,
  the per-dimension TimeUpdate sync); see the ledger's §169 row.
- The real remaining vanilla gaps (survey, Slice 26 — none present in the
  engine's source tree): the note block family (block 25: the instrument
  selection from the block below, the redstone trigger, the pitch walk) —
  RESOLVED Slice 27a; the jukebox (block 84: the record insert/play/eject)
  — RESOLVED Slice 27b (the disc table, the four arms, the 1005 event, the
  ZJD store); the daylight
  sensor (block 151: the sky-light power band) — RESOLVED Slice 27c; the
  item frame (entity family: placement, the held stack, the rotation,
  the drop) — RESOLVED Slice 28a; the painting (entity family: the
  placement walk, the art registry) — RESOLVED Slice 28b; the armor stand
  (entity 30: the placement, the armor slots, the pose) — RESOLVED Slice
  28c; the ender chest (block 130: the per-player ender storage) —
  RESOLVED Slice 29a (the private-grid door model, the ZPD v8 identity
  tail, the obsidian/silk split); and the beacon (block 138: the beam,
  the area effect, the payment) — RESOLVED Slice 29b (the pyramid walk,
  the spread, the payment GUI + the MC|Beacon confirm, the ZBT store).
  The survey's list is now empty — every named gap has landed.
- Releases now ride GitHub Releases with changelogs per dev build; the
  dev.13 jar was lost to an environment reset before a release existed
  (tags v0.2.0-dev.13 was never cut; dev.14/dev.15 were back-filled with
  the surviving artifacts).

## Conventions

- Every slice commits with the reference files named in the message.
- The full test suite must be green before each push; the count is recorded
  in the unit's entry (baseline 451 after Slice 5).
- Push after every unit — environment resets must not eat finished work.


## 2026-10-10 — Concurrency architecture reconnaissance

- Confirmed the current `develop` tree has 2,176 entries and four Gradle modules: `zamin-api`, `zamin-core`, `zamin-protocol-v1_8_8`, and `zamin-launcher`. Java toolchain is 21; tests use JUnit 5.
- Verified no Minestom dependency is declared in the inspected build files. `docs/ENGINEERING_PLAN.md` records Minestom integration as deferred under ADR-0001. The current runtime is Torch's own world model and Netty protocol-47 server, not a Minestom runtime.
- Read `EngineTicker`: one simulation loop, deferred work drained at tick start, world time and handler executed serially, missed ticks skipped rather than burst-caught-up. Existing metrics include a rolling TPS estimate and last overrun, but not per-owner latency (there are no independent owners yet).
- Read `EngineWorld`: mutation checks use a recorded owner thread; chunk publication uses a concurrent map, but that does not grant concurrent mutation rights over `EngineChunk`. Chunk generation currently runs synchronously under the world owner before publication. Persistence snapshots copy delta maps under owner context.
- Confirmed the committed `reference/1.8.8/` source tree exists. The requested `1.8.8 - mechanics.zip` is absent from the inspected `develop` tree; do not claim to have inspected it or base work on its contents until recovered.
- Confirmed GitHub release `v0.2.0-dev.16` already contains `zamin-server-0.2.0-dev.16.jar` and the distribution ZIP. Its release notes report 508 green tests at that build; those tests were not rerun in this inspection environment.
- Added `docs/CONCURRENCY_ARCHITECTURE.md` as a proposal, not an implementation. It records the ownership model, initial scheduler recommendation, compute-result validation, migration lifecycle, and explicit handling of hopper transfers, pistons, redstone, and AI results after entity transfer.
- Limitations this pass: the Folia ZIP was confirmed present (837,676 bytes), but its archive contents have not yet been exhaustively extracted or inventoried; the mandatory class-by-class patch audit remains open. No local `git status`, build, or test execution was possible through the GitHub-only environment. No runtime source was changed in this pass.

### Next concrete steps

1. Recover or upload `1.8.8 - mechanics.zip` so its actual implementations can be inspected before porting related mechanics.
2. Complete the Folia patch inventory, beginning with the two region-threading base patches and recording every modified Java path, then follow critical cross-boundary mechanics through their diffs.
3. Inspect current source and tests for the next vanilla implementation slice before changing behavior; compare against `reference/1.8.8/` and update the compatibility ledger only with evidence.
4. Run `./gradlew build` in a real checkout and record the actual result before cutting a new dev release. Do not relabel the existing dev.16 artifact as a newer build.


- Follow-up documentation commits: `docs/MINESTOM_CONCURRENCY_BASELINE.md` records the actual current module and ownership baseline, including the limits of concurrent maps and live chunk references; `docs/FOLIA_FORENSIC_AUDIT.md` records the exact outstanding extraction/inventory work and explicitly prevents the incomplete audit from being represented as finished.
- Documentation commits in this pass: architecture proposal `52cee0c3d7cb6239397c3ad5096fcd8f2bd1cce1`; worklog update `29087a1021259ff675f338b93d365a67fcd2eff4`; Minestom baseline `b8a3141cbbda2d466672c3dbd8d876189a9e89eb`; Folia audit status `523f3f8f254a98cc39da628f8cb6f7b7de620d46`. These commits are on `develop`; no runtime source changed.

- Added root `TORCH_ENGINEERING.md` (commit `208ab911b66abaab1b60cd01486937945122a33a`) as the current-state ledger: modules, verified ownership baseline, honest status table, missing mechanics archive blocker, release state, and ordered next steps. The ledger explicitly says local working-tree status is unknown because this pass did not access the local checkout.

## 2026-10-10 — Slice 8b-i / 8b-ii / 8b-iii-a land; dev.22 released

- **Slice 8b-i — the nether generator** (commit `139fcd6`): ported from
  `reference/1.8.8 world/gen/chunk/NetherChunkGenerator.java` (415 lines) +
  `NetherCaveCarver.java` + `noise/PerlinNoise.java` + `noise/ImprovedNoise.java`.
  The seven-PerlinNoise stack (16/16/8/4/4/10/16 octaves over one shared
  Random), the 5x17x5 trilinear terrain shape (684.412/2053.236/684.412), the
  lava sea below 63/2+1=32, bedrock banded both walls, the soul-sand/gravel
  skin, the eight-neighbor cave carve, and the decorate pass (lava pockets,
  fire patches, two glowstone forms, quartz veins, the mushrooms). The new
  nether materials register across the tables (netherrack 87, soul sand 88,
  glowstone 89 light 15, quartz ore 153, mushrooms 39/40; the HELL biome,
  wire id 8). Determinism adaptations documented: the decorate pass re-seeds
  per chunk (order-independent rebuilds), feature writes clip to their own
  chunk, the fortress draw skipped. NetherWorldTest (7). Suite 628 green.
- **Slice 8b-ii — the second EngineWorld** (commit `5753df4`): vanilla's
  DIM-1 — the nether world boots alongside the overworld on the same seed,
  its own chunk map, its own delta persistence (`DIM-1/zamin-delta.bin`),
  its own ownership domain `simulation:nether` (the boot binds both domains
  around the runLoop), and the ticker's time walk advancing both clocks.
  The nether surfaced a real engine bug, fixed in the same commit: the
  chunk-local delta index masked y to 4 bits, truncating every persisted
  edit at y >= 16 — the index now takes its full byte. NetherDimensionTest
  (5: the dual-clock tick, the null-nether contract, the double-attach
  refusal, the `simulation:nether` ownership assertion, the DIM-1
  persistence round-trip at y=40). Suite 633 green.
- **Slice 8b-iii-a — the PortalForcer** (commit `03237aa`): ported from
  `reference/1.8.8 net/minecraft/server/world/PortalForcer.java` (351 lines)
  + `PortalBlock.findPortalShape`. The shape match (the four front-layer
  counts pick the forward — the emptier side is the front, POSITIVE wins
  ties), findNetherPortal (the block-granular cache with the 300-second
  eviction, the 257x257 column scan to each column's lowest portal cell,
  the placement math: the width/height inverse-lerp shares, the clockwise-y
  width shift, the facing-traded yaw, the velocity quarter-turn matrix),
  generateNetherPortal (the two-scan hunt riding the reference's label296
  column-level semantics, the y-clamped platform fallback, the 4x5 frame
  build). PortalForcerTest (5). Suite 638 green.
- **Dev build 0.2.0-dev.22** (commit `774dc08`): released as
  `v0.2.0-dev.22` on GitHub Releases with the changelog; the fat jar and
  the distribution ZIP both attached. The changeDimension walk (8b-iii-b:
  the 8:1 scaling, the Respawn sequence at dimension -1, the per-dimension
  chunk visibility, the PlayerSession portal memory) is next.

## 2026-10-10 — Slice 8 completes: 8b-iii-b (the changeDimension walk); dev.23

- **Slice 8b-iii-b** (commit `64cef12`): the PlayerManager.changeDimension
  port — the stand clock's teleport arm fires the walk, the 8:1 coordinate
  scaling (÷8 in, ×8 back, the ±29999872 clamp), the 17x17 destination ring
  load before the search, the PortalForcer's find-or-generate riding the
  body's onPortalCollision memory (the raw inverse-lerp frame offsets +
  the entered facing, PlayerSession.notePortalEntry), the arrival, and the
  wire: the Respawn packet's dimension int, the ChunkTracker re-pointed
  (the per-dimension chunk reads through engine.worldFor), the
  dimension-tagged detached chunk loads. DimensionWalkTest (1: the live
  round trip). Suite 639 green. The per-dimension TimeUpdate sync remains
  ledgered as the follow-up wire arm.
- **Slice 8 (nether portals) is COMPLETE.**

## 2026-10-11 — Slices 8c, 9a, 9b land; the redstone core opens

- **Slice 8c — the per-dimension Time Update sync** (commit `8c89e06`): the
  reference's MinecraftServer lines 547-558 walk — every world's clock
  publishes to the players standing in that dimension only (the
  TimeListener carries the dimension, the wire fan-out filters per
  connection exactly like the reference's sendPacket(packet, dimension)),
  the 20-tick cadence (was 100), /time set and the new /time add walking
  EVERY world server with the forward wrap (floorMod), and the sleep jump
  staying the overworld's canSkipNight arm. DimensionTimeSyncTest (3).
  Suite 642 green.
- **Slice 9a — the redstone core** (commits `bab950b` + `16b5244`): the
  reference port of the signal model (World.java lines 2293-2390 — the weak
  read's solid re-radiation, the strong reads, the 15 short-circuit), the
  wire cascade (doUpdatePower's exact arithmetic, the shouldSignal
  re-entrancy guard, the same-tick synchronous propagation through the
  engine's change-listener dispatch — vanilla's flag-1 walk), the two-hop
  notification rings (onAdded/onRemoved updateNeighbors — the wire's change
  reaches the torch hanging on the block under it), the scheduled-tick
  queue (the (position, family) coalescing, the time/priority/sequence
  TreeSet ordering, the 1000-per-tick drain, the willTickThisTick guard),
  the torch (the 2-tick reaction, the 8-toggle/60-tick burnout ledger with
  the 160-tick recovery), the repeater (the DELAY*2 scheduled reaction,
  the lock, the -3/-2/-1 priority arms, shouldPrioritize's
  facing-a-facing-back test). The flattened family registry (wire p0-15
  legacy 55, torch lit 76/unlit 75 x 5 facings, repeater 93/94 x
  facingH|delay-1<<2), the light rows (lit torch emits 7), the behavior
  rows, the solidity exclusions, the dust 331/repeater 356 items.
  WorldChangeListener gained the onBlockRemoved companion (the old type
  fanned out after the commit — the onRemoved port surface). Two parse
  bugs found and fixed on the wire (indexOf vs lastIndexOf on the power
  suffix; the bare redstone_wire metadata-0 — the second broke every
  protocol test through the reverse map's static init). RedstoneSystemTest
  (5: the torch-fed block's dust reading 15, the 16-cell decay dying
  exactly at the boundary, the NOT gate, the relight, the repeater's
  turn-on/turn-off). Suite 647 green.
- **Slice 9b — the redstone interaction layer** (commit `0595407`): the
  dust placement (the solid-bed gate, the cascade filling the power on the
  SAME commit), the redstone torch placement (the clicked face's FACING
  with the attachment gates and the first-wall fallback, the ceiling arm
  never attaching), the repeater placement (FACING = the look OPPOSITE,
  the signal flowing away from the player), and the repeater's right-click
  delay cycle (1->2->3->4->1, the use firing before the held-item
  placement like the reference's use() precedence, the click sound).
  RedstoneInteractionTest (4: the same-commit power fill, the bedless
  refusal, the wall torch's clicked-face landing, the four-step cycle).
  Suite 651 green (one environmental wire-test flake observed across
  full-suite runs, each passing alone — the pattern predates the slice).
- Next: the levers/buttons/pressure plates (the player-driven sources),
  the comparator, then the pistons (the Phase 4 spatial-activation gate).

## 2026-10-11 — Slice 9c: the player-driven sources

- **Slice 9c — the lever, the buttons, the pressure plates** (commit
  `a335b5b`): the reference ports of LeverBlock (the eight-facing model
  with the attachment directions, the POWERED swap, the two-ring
  notification), ButtonBlock (the stone 20 / wood 30-tick release, the
  FACING-points-away-from-the-wall geometry with the mount-side strong
  arm), and the pressure plates (the occupancy cycle through the
  engine's per-tick scan over the 0.125-inset box — the
  onEntityCollision equivalent, the mobs+players probe with the wooden
  plates adding items, the 20-tick re-compute debounce, the strong UP arm
  to the mount below). The placements ride the clicked-face mount walks.
  Three geometry bugs caught by the tests before landing: the button's
  support and notification must look at the facing's OPPOSITE (the mount
  side), the lever's onRemoved offset walks the attachment opposite, and
  the plate's press needed the per-tick scan arm (nothing else fired it).
  RedstoneSourcesTest (3). Suite 654 green.
- Next: the comparator, then the pistons (the Phase 4 spatial-activation
  gate), then potions/leads/structures.

---
## Slice 9d — the comparator (the analog diode)

**Reference:** `block/ComparatorBlock.java`, `block/DiodeBlock.java`,
`block/entity/ComparatorBlockEntity.java`,
`inventory/menu/InventoryMenu.java` (getAnalogSignal), `world/World.java`
(updateNeighborComparators lines 2701-2716), `block/entity/BlockEntity.java`
(markDirty lines 96-103), `block/RepeaterBlock.java` (isValidSideInput).

**Landed:**
- The flattened family: `comparator_<facing>_<compare|subtract>` /
  `powered_comparator_...` (legacy 149/150, the nibble
  `facingH | subtract<<2 | powered<<3`), the comparator item (404), the
  behavior rows, the solidity exclusion, the serializer nibble + the
  damage-aware reverse.
- The signal model: the powered pair emits its STORED ANALOG VALUE toward
  the output side (dir == FACING), weak and strong arms equal.
- The input walk: the diode base read, the direct container override, the
  through-solid read (i < 15 && solid at the input -> one further back).
- The container fullness (InventoryMenu.getAnalogSignal): the per-slot
  fraction over min(inventoryMax, itemMax), the whole-size average,
  floor(f * 14) + (any non-empty ? 1 : 0).
- The compare gate (input >= 15 on / input == 0 off / sides == 0 ||
  input >= sides), the subtract arithmetic (max(input - sides, 0)), the
  2-tick reaction with the -1/0 priority pair, the immediate use-cycle
  re-evaluation (the mode toggle + the 0.55/0.50 click), the
  quiet-subtract ring suppression (j != i || COMPARE).
- The contents wake: `ChestBlockEntity`/`FurnaceBlockEntity` bump their
  serials through a contents listener wired per-position by their
  managers -> `RedstoneSystem.wakeComparators` (the
  updateNeighborComparators port: the four horizontals, the direct
  comparator + the through-solid second hop).
- The stored output survives the family's internal pair swaps
  (putIfAbsent on arrival, removed only when the family departs — the
  engine's removal arm fires after the change arm).
- The reference corrections that landed with it: the repeater's side
  input and shouldPrioritize now count comparators (the reference's
  isDiode test), while the comparator's own side read keeps the
  UNRESTRICTED isValidSideInput (any signal source — ComparatorBlock
  does not override it).

**Tests:** RedstoneComparatorTest (5) — the fullness ladder pins (0/1/1/2/7/
15 + the min-stack arm over 16-sign stacks), the live analog emission with
the wire decay (7 then 6), the content-change wake (both arms), the compare
gate + subtract + the use cycle + the emptied-input drop, the through-solid
read. Suite 654 -> 659 green.

**Known differences (ledgered):** the stored output is in-memory (the
reference persists it in BE NBT); the item-frame arm rides the item-frame
slice; the 150 block id never appears in live play (the flattened powered
pair IS the converged 149-POWERED-true state).

**Next:** Slice 9e — the pistons (the Phase 4 spatial-activation gate).

---
## Slice 9e — the pistons (the Phase 4 spatial-activation gate's mechanic)

**Reference:** `block/PistonBaseBlock.java`, `block/piston/
PistonMoveStructureResolver.java`, `block/PistonHeadBlock.java`,
`block/MovingBlock.java`, `block/entity/MovingBlockEntity.java`,
`server/world/ServerWorld.java` (the block-event queues, lines 96/207/808-840),
`block/material/Material.java` (the piston behaviors).

**Landed:**
- The flattened family: `piston_<facing>[_extended]` (33),
  `sticky_piston_<facing>[_extended]` (29), `piston_head_<facing>[_sticky]`
  (34), `moving_piston_<facing>[_sticky]` (36) — the nibbles mirror the
  reference's metadata (facing | extended<<3 / facing | sticky<<3), the
  items, the behavior rows (the base 0.5 + drops; the head dropless; the
  carrier unbreakable), the solidity (the base + head solid, the carrier
  non-solid), the serializer nibbles + the reverse.
- `PistonSystem` — the listener + the tick:
  - shouldExtend: the six neighbors except the FACING side (the front-face
    immunity), the piston's own position (inert — the reference's
    isCube-false piston neither re-radiates nor emits; the family is
    excluded from the signal-solid set), and the quasi-connectivity walk
    around the position above.
  - The two-queue block-event swap (the duplicate scan on the current
    queue, the drain-after-processing shape, the drain after the redstone
    queue within the same tick — the reference's own order).
  - doEvent 0/1: the extend (the move + the EXTENDED flip + the "out"
    sound), the retract (the mid-flight finish, the body carrier, the
    sticky pull with the moving-block finish + the canMove gate + the
    piston-behavior gate, the plain head removal, the "in" sound), the
    cancel arms (the re-armed retract, the unpowered extend).
  - move(): the resolver, the toBreak drops through the behavior table,
    the shifted carriers (the reverse order, the movedType captured before
    the removal), the head's carrier, the neighbor rings in the
    reference's order.
  - The two-tick carriers: the 0.5/tick progress, the landing write, the
    record cleanup on replacement.
- `PistonStructureResolver` — the 1:1 column walk (the 12-block budget,
  the toBreak DESTROY rule, the slime back-walk + neighbor columns +
  insertColumn splice — dormant until slime lands).
- canMoveBlock: obsidian, the y-bounds with the DOWN/UP clamps, the
  extended-piston refusal, the unbreakables (the -1 hardness arm), the
  BLOCK family (the portal, the head, the carrier), the DESTROY family
  (the whole redstone decoration set incl. the comparator, the plants,
  the leaves, the liquids, the fragile gourds) behind the allowBreaking
  gate, the BE providers (the chest, the furnace, the sign).
- The placement: the eye-height walk (within 2 blocks horizontally — up
  above two, down below the feet) else the look-opposite, the piston and
  sticky piston items.
- The engine-side fix: the piston family excluded from isSignalSolid (the
  reference's isCube-false piston is a signal dead zone).
- The test-side race fix: PersistenceAcceptanceTest's delta-count await
  now covers the grass-decay commit (a pre-existing load race).

**Tests:** PistonSystemTest (6) — the extend + the pushed landing, the
plain retract leaving the block, the sticky pull, the 12-block budget
(the refusal + the fit), the break-on-push with the item drop, the quasi
power with the update gate (the vanilla rule: the quasi region alone does
not wake the piston — an adjacent change must ring it). Suite 659 -> 665
green (the full pass on 2-core hosts: `--max-workers=1` — the real-wire
tests are load-sensitive).

**Known differences (ledgered):** the entity-displacement arm (the push of
bodies in the flight path) rides the entity-collision slice; the client
animation (the Block Action fan-out + the moving BE sync) rides the
protocol slice; the world-border arm is absent; the slime columns are
dormant.

**Next:** the Phase 4 spatial activation — the boundary protocol work
over piston pushes across region seams (the §11 boundary tests) — then
TNT ignition, dispensers, hoppers.

---
## Slice 9f — the TNT + the cross-system neighbor bus

**Reference:** `block/TntBlock.java`, `entity/PrimedTntEntity.java`,
`world/explosion/Explosion.java` (the entity damage walk's numbers).

**Landed:**
- The block: `minecraft:tnt` (legacy 46, the block item shares the id),
  the behavior row (hardness 0, drops the item), the registry (102
  built-ins), the serializer id.
- `TntSystem` (`server/tnt/`):
  - The redstone ignition: a TNT that arrives into — or hears — a
    neighbor signal primes (the onAdded + neighborChanged arms).
  - The flint-and-steel use arm (TntBlock.use): the engine's flint path
    carries the ignite + the durability.
  - The blast chain-reaction (TntBlock.onExploded): a TNT consumed by an
    explosion primes a short-fuse body (next(fuse/4) + fuse/8) instead of
    dropping — the generalized detonation walk carries the arm.
  - The primed bodies: the 80-tick fuse, the spawn hop (0.02 horizontal /
    0.2 rise), the physics (gravity -0.04, drag 0.98, the feet-relative
    ground test with the collision clamp + the bounce, the 0.7/0.5
    friction), the "game.tnt.primed" sound.
  - The detonation: the 4.0F blast at the body's own position (y+0.06)
    through the generalized `detonateAt` — the creeper's explosion walk
    parameterized (the per-power drop roll, the wound falloff to double
    the power, the easy cap: the creeper keeps 24, the TNT 32).
- The wire: Spawn Entity object 50 (objectData 1 + the velocity triple),
  the Entity Teleport ride, Destroy — the adapter's TNT listener.
- **The cross-system neighbor bus**: the RedstoneSystem's internal
  neighborChanged now fans to registered consumers (the pistons, the
  TNT) — the reference's world-level `neighborChanged`. This closes the
  lever-through-a-block gap: a source's two-ring walk (the lever's own +
  its mount's) now reaches every family, not just the redstone blocks.
- The ExplosionService bounds guard: the ray fan stops at the vertical
  world bounds (a latent crash — a blast marching below y=0 built an
  out-of-bounds position).
- The quasi-power piston test corrected to the vanilla semantics: the
  lever's mount-ring DOES wake the piston (the toggle extends it through
  the quasi walk) — the earlier test asserted the engine's gap.

**Tests:** TntSystemTest (2) — the redstone ignition through a lever
mounted beside (the mount's re-radiation + the two-ring wake), the fuse
flight, the blast consuming the neighborhood; the flint-and-steel prime +
the detonation. Suite 665 -> 667 green (the full pass on 2-core hosts:
`--max-workers=1`).

**Known differences (ledgered):** the burning-arrow ignite needs per-block
entity collision callbacks; the smoke trail rides the particle slice; the
primed body's physics is the simplified hop model (no entity-entity
collision).

**Next:** the Phase 4 spatial-activation boundary work over piston pushes
across region seams, then dispensers/droppers, hoppers, tripwire.

---
## Release v0.2.0-dev.25

- Slices 9d + 9e + 9f ride this artifact: the comparator, the pistons
  (the Phase 4 gate's mechanic), the TNT + the cross-system neighbor bus.
- 667 tests green at the build's commit.
- The server JAR + the distribution ZIP attached; the changelog published.
- Next: the Phase 4 spatial-activation boundary work (piston pushes
  across region seams with the §11 boundary tests), then
  dispensers/droppers, hoppers, tripwire.

---
## Slice 10a — the Phase 4 spatial activation (the regionized ownership lane)

**Goal:** the rollout's Phase 4 spatial activation — the per-position
ownership assertion, the boundary scope for the seam-crossing mechanics,
and the §11 boundary tests over piston pushes across region seams.

**Reference:** `docs/CONCURRENCY_ARCHITECTURE.md` §4/§10.3 (the larger
exclusive ownership scope), `docs/CONCURRENCY_DECISIONS_AND_ROLLOUT.md`
ADR-6 (the default-huge granularity) + ADR-9 (never drop, never diverge)
+ ADR-13 (the ambient/global credential), the master spec's §18 boundary
test list.

**Landed:**

- `EngineWorld.attachRegionPartition` + the position-aware
  `requireOwnership(op, position/chunk)`: every block/chunk mutation
  resolves the owning region's domain; the credential passes through the
  region itself (the parallel lane's writer) OR the ambient simulation
  domain (the single tick lane); every other context fails through the
  region's own STRICT/DIAGNOSTIC enforcement (the violation names the
  region). The boot-lane construction window (no ambient yet) keeps the
  historical owner-thread check.
- `EngineConfig.regionSizeChunks` — the `region-size-chunks` property,
  default `1<<30` (the whole world one region — Folia's regionShift=31
  posture). The boot creates each dimension's partition and attaches it.
- The boundary scope `EngineWorld.executeInRegions(box, body)` — the
  design §10.3 larger exclusive ownership: every region the box touches
  entered in the ONE global packed-region-id acquisition order, released
  in reverse (concurrent scopes deadlock-free by construction); a held
  region refuses the enter and the scope releases its acquisitions.
  `RegionPartition.domainsBetween` is the sorted-id box walk.
- The seam-crossing mechanics under scopes: the piston's whole event (the
  14-block reach wrap around doEvent — the extend's move writes, the
  retract's sticky pull, the head) + the landing cells' own region (the
  carrier finish); the detonation blast walk (the generalized `detonateAt`
  destruction under the power*2+margin box — the §11 "explosions
  spanning multiple owners" row).
- The per-region intent drain at the tick head:
  `EngineWorld.drainRegionIntents(router)` — every live region entered,
  its inbound §7 intents applied in the target's own context (the
  target-side apply), held regions skipped with their intents staying
  queued (ordered, never dropped). The tick handler drains both
  dimensions before the gameplay systems.
- `RegionBoundaryTest` (8): the scope carries the region credential with
  the ambient never entered (a writer holding only one region writes its
  own side, fails the far side, and the spanning scope carries both
  writes); the held region's exclusivity (the scope refuses, the body
  never runs, the acquisitions release); the drain's held-region skip +
  the after-release apply; the live foreign write failing against the
  position's own region (the refused write never lands); the piston push
  AND the sticky pull across the one-chunk seam (vanilla-exact,
  same-tick); the redstone decay ladder walking the seam (15-7 at x=15,
  15-8 at x=16 — the cascade neither restarts nor drops); the explosion
  consuming both sides of the seam; the region-targeted router intent
  applying at the target's boundary through the tick-head walk.

**Ledgered limitation:** the parallel multi-domain execution lane (the
gameplay systems dispatched per region through the worker pool) remains
future work gated on the rollout's measurement rule; the redstone cascade
and the fluid walks ride the ambient authority on the single tick lane.

**Tests:** 667 -> 675 green (the full pass on 2-core hosts with
`--max-workers=1`).

**Next:** dispensers/droppers, hoppers (the §11 hopper intent arm — the
cross-owner transfer rides the router), tripwire, then potions, leads,
structures, natural spawn cycles.

---
## Slice 10b — the hoppers (the §11 boundary hard case)

**Goal:** the reference port of the hopper — the 8-tick transfer cadence,
the push/pull/suck arms, the powered disable, and the cross-seam transfer
pinned under the Phase 4 boundary scope.

**Reference:** `reference/1.8.8/block/HopperBlock.java` +
`block/entity/HopperBlockEntity.java` (transferItems/pushItem/pullItem/
pickUpItem/getInventoryAt, the cooldown semantics, the sided slot walks) +
`block/entity/FurnaceBlockEntity.java` (the sided slot arrays).

**Landed:**

- `HopperBlocks`: the flattened family (legacy 154, five FACING values —
  everything but UP, the reference's own predicate — × the ENABLED pair,
  the nibble facing | disabled<<3) + the behavior rows (hardness 3.0, the
  hopper item drop) + the item (410, the comparator-style split) + the
  wire serializer (the nibble encode + the damage-aware reverse fold).
- `HopperBlockEntity`: the 5 slots, the transferCooldown (the reference's
  -1 initial — a fresh hopper transfers on its first tick), the 8-tick
  reset, the receiving-hopper cooldown arm (canTransferNextTick), the
  contents-changed wake (markDirty -> the comparators).
- `HopperSystem`: the per-tick cadence (cooldown--, transfer when off
  cooldown, the success reset to 8), ONE item per transfer with the
  whole-stack restore on failure (the reference's removeItem(i, 1) +
  copy), the push toward the FACING container, the pull from the container
  above, the item-entity suck from the box above (the whole stack), the
  furnace's sided slots (top input / bottom output-then-fuel / sides
  fuel, the bucket-only fuel pull gate, the fuel-only slot filter), the
  powered disable through the cross-system neighbor bus (the ENABLED pair
  flip on hasNeighborSignal), the placement's clicked-face-opposite
  facing (UP clamps DOWN), the survival break spill + the comparator
  wake, and the placement-time BE creation (the world commit listener —
  the reference's createBlockEntity at placement).
- The comparator's container-fullness read gains the hopper (5 slots).
- Every transfer is one same-tick unit under the boundary scope spanning
  the hopper, its push target, and the pull source (the Phase 4 spatial
  activation): the cross-seam transfer is vanilla-exact — the §11
  "hoppers transferring items between containers with different owners"
  row.

**Ledgered:** the 5-slot hopper GUI window and the hopper-contents ZCD
persistence ride the protocol/persistence slices; the minecart-with-chest
entity arm of the container resolution is absent (no chest minecarts
yet); the one-tick-latency intent arm is the parallel-lane adaptation.

**Tests:** 675 -> 682 green (HopperSystemTest 7: the push cadence
one-by-one, the pull with the exact release, the furnace's side-face fuel
routing, the powered disable + the resume, the item-entity suck, the
cross-seam transfer under the boundary scope, the break spill).

**Next:** dispensers/droppers, tripwire, then potions, leads,
structures, natural spawn cycles.

---
## Slice 10c — the tripwire (the wire + the hook)

**Goal:** the reference port of the tripwire family — the chain
attachment walk, the occupancy press, and the mounted-source arms.

**Reference:** `reference/1.8.8/block/TripwireBlock.java` +
`block/TripwireHookBlock.java` (updateState's 41-cell walk,
updateOutputState's occupancy, getDirectSignal's mount arm, the click
family, the neighbor support arms).

**Landed:**

- `TripwireBlocks`: the flattened families — the wire (legacy 132, the
  four stored bits powered/suspended/attached/disarmed riding the nibble;
  the N/E/S/W connection flags are virtual, computed client-side) and
  the hook (legacy 131, facingH | attached<<2 | powered<<3), + the
  behavior rows (the wire drops string unless disarmed — the shears arm;
  the hook drops its item) + the tripwire_hook item + the wire
  serializer (both nibble encodes + the damage-aware reverse folds).
- `TripwireSystem`: the occupancy scan (the per-tick probe over the wire
  set — the wire's slim 5/32 box, mobs + players + items; the 10-tick
  re-scan while occupied), the hook's updateState walk (the 41-cell
  scan, the suspended matching, the powered aggregation over the chain,
  the far-hook state with its own two-position ring, the ATTACHED
  propagation over the chain, the click family), the neighbor support
  arms (the wire's suspended flip break, the hook's mount break), the
  wire's onRemoved powered quirk (a removed powered wire counts as on
  for the hook update — the trap signal), the placements (the string
  places the wire; the hook needs a horizontal clicked face with a solid
  wall behind), and the re-entrancy guard (the flag-3 adaptation: the
  hook walk's own writes never re-enter the walk — without it the two
  hooks ping-pong their ATTACHED pairs to a StackOverflowError).
- `RedstoneSystem`: the hook's weak arm (15 all directions when powered)
  + the strong mount arm (15 toward the wall behind, the engine's
  mounted-source convention shared with the lever/button), the
  FAMILY_TRIPWIRE scheduled tick (the re-scan), the family dispatch.
- `WorldSolidity`: the tripwire family joins the non-solid set (the
  reference's isCube-false, null collision — without it the hook read as
  a solid block and the dust read the strong arm instead of the weak).
- EngineServer: the system wiring (the listener, the neighbor bus, the
  occupancy probe, the tick), the registry chain, the placements.

**Tests:** TripwireSystemTest (2): the chain attaches + the occupancy
press powers both hooks + the weak arm reaches the dust + the release
after the body leaves; the strong mount arm re-radiates through the
wall to the dust on its top. Suite 684 green in isolation (the full
pass had one projectile-test load flake — the RangedCombat acceptance
timing under --max-workers=1 load; 3/3 green in isolation).

**Next:** dispensers/droppers, then potions, leads, structures,
natural spawn cycles.

---

## Slice 11a — the dispensers and the droppers (2026-10-11)

**Reference:** `block/DispenserBlock.java` + `block/DropperBlock.java` +
`block/entity/DispenserBlockEntity.java` + `block/dispenser/*` +
`Bootstrap.registerDispenseBehaviors` (the behavior registrations) +
`SmallFireballEntity` (the fire charge's flight).

**Files:**

- `server/dispenser/DispenserBlocks` — the flattened families: the
  dispenser 23, the dropper 125, six FACING values x the TRIGGERED
  pair, the nibble `facing | triggered<<3` (DispenserBlock lines
  202-215).
- `server/dispenser/DispenserBlockEntity` — nine slots (the 3x3 window
  layout), the reservoir-sampled pickNonEmptySlot (the reference's own
  1/j walk, lines 61-72), the insertItem (the bucket scoop's give-back),
  and the chest's click/quick-move/drop semantics over the nine.
- `server/dispenser/DispenserSystem` — the activation (the TRIGGERED
  pair through the neighbor bus with the defensive 7-position walk the
  hopper established, the reference's through-the-top
  `hasNeighborSignal(pos.up())` read, the 4-tick reaction through
  FAMILY_DISPENSER), the dispense walk, the dropper's container push
  (ONE item, silent, falling through to the default drop), the
  behavior registry (arrow/egg/snowball projectiles force 1.1
  variation 6, the fire charge, the TNT prime, the bucket pour/scoop
  with the empty-bucket swap + the insert-else-drop, the flint and
  steel fire + the one-use wear, the bone meal crop grow
  `stage + nextInt(2..5)`, the default item drop with the vanilla
  0.7-out position + the 0.2-and-gaussian velocity family), the
  placement re-orientation quirk (onAdded's facing-into-a-wall flip,
  run at placement only per the flag-1 semantics), the break spill +
  the comparator wake, the stale-state sweep.
- `ProjectileEntity`/`ProjectileManager`: the SMALL_FIREBALL kind
  (object type 63) — the accelerating physics (the velocity picks up
  the normalized*0.1 acceleration BEFORE the 0.95 drag, no gravity),
  the launchVector API (the dispenser's projectile family), the fire
  placement at the crossed air cell on landing, the flat 5.0 fire
  damage + the five-second burn.
- `TntSystem.spawnPrimedAt` (the dispensed TNT primes at the facing
  cell's center, no block removal) + the public isTntBlock +
  primedBodies read.
- `HopperSystem`: the containerAt query + the pushOneIntoContainer (the
  dropper's push) + the dispenser family joins the container adapter
  (hoppers feed and pull the nine slots) + the late-wired
  dispenserLookup (the construction cycle resolved through a setter).
- `RedstoneSystem`: FAMILY_DISPENSER + the scheduleDispense entry + the
  dispenserTicker dispatch.
- EngineServer: the system wiring (the listener, the neighbor bus, the
  ticker, the fire sink), the registry chain, the 9-slot GUI window
  (ContainerKind.DISPENSER + the click/quick-move/drop semantics +
  the wire items sync + the open dispatch), the placement (the
  piston-style eye/look facing + the re-orientation quirk), the
  comparator fullness, the break spill, the bonemeal player-use roll
  fix (`2 + nextInt(4)` — the reference's MathHelper.nextInt(random,
  2, 5)).
- `BlockLightTable`: the light rows for EVERY late-registered
  mechanical family (the dispenser/dropper/piston bases/heads filter
  15; the hopper/moving carrier/tripwire wire+hook pass) — the gap the
  world's warning swallow hid since Slice 9e.
- `detonateAt`: the boundary-scope box clamped to the world's vertical
  bounds — a pre-existing crash any ground-level blast would hit (the
  scope walked below y=0 and the whole tick died).
- The wire: the families' ids + the nibbles + the reverse folds, the
  items 23/125/385, the window constants.

**Bugs found through live debugging:**

1. The item-split copy-out — the engine's `ItemStack.split` is a
   copy-out (the record model), so the default drop's slot never lost
   its unit; the reference's in-place split decomposed into the
   explicit one + consumeOne.
2. The ground-level detonation crash — the boundary-scope box for any
   blast near the floor walked below y=0 and killed the tick; clamped
   to the world bounds.
3. The missing light rows — every mechanical family since the pistons
   (Slice 9e) ran on the world's warning swallow; the rows registered.
4. The player-use bone meal roll — `2 + nextInt(3)` corrected to the
   reference's `2 + nextInt(4)`.

**Tests:** DispenserSystemTest (10): the powered release after the four
ticks + the TRIGGERED pair + the empty click + the no-re-dispense
guard, the arrow as a projectile, the fire charge as a small fireball,
the dropper's silent chest push, the dropper's item fallback, the water
bucket pour + the empty-bucket swap, the empty bucket's scoop, the
dispensed TNT prime + the detonation at the fuse end, the hopper feed,
the empty dispenser's click. The fresh full-suite method count: 604
green in zamin-core + 19 in zamin-api + 71 in the protocol module
(zero failures; the full pass with --max-workers=1).

**Ledgered (not in this slice):** the spawn-egg/potion/fireworks/skull/
pumpkin/minecart/armor behaviors (their items or their entities ride
other slices — the potion is next), the world-event 2000 smoke + 2005
bonemeal particles (the particle slice), the ZCD persistence of the
family's nine slots, the arrow pickup flag.

**Next:** potions (the splash entity + the effect application), leads,
structures, natural spawn cycles.

## Slice 11b — the potions (the reference port of entity/living/effect/{PotionHelper, StatusEffect, StatusEffectInstance, InstantStatusEffect} + item/PotionItem + item/GlassBottleItem + entity/projectile/PotionEntity + LivingEntity's effect arms + ServerPlayerEntity's packet overrides)

**What landed:**

- **The metadata engine** (`potion/PotionHelper`, 1:1): the 1.8.8
  damage-value model — the brewing flags (0-3 the effect pair, 5 the
  amplifier-II, 6 the extended, 13 the drinkable carrier, 14 the splash),
  the reference's own flag-expression machine (`parseRecipe` + `evaluate`
  ported statement-for-statement: '|' takes the left when positive, '&'
  zeroes on either zero else the max, the sub-expression sums), the two
  recipe tables (14 duration rows + 9 amplifier rows, the exact strings),
  the duration math `1200 * (i*3 + (i-1)*2) >> amplifier` with the
  per-effect multiplier and the splash's `round(*0.75 + 0.5)` shave, the
  color average, `applyIngredient` (the brewing stand's future surface —
  the '+', '-', '!', '&' operators, the 32767 mask), and `getPrefixIndex`.
- **The effect registry** (`potion/StatusEffects`): all twenty-three
  1.8.8 effects in id order — the harmful flags, the potion colors, the
  duration multipliers (the harmful 0.5, regen/poison/wither/nausea/
  blindness 0.25, haste 1.5), the instant family (health/damage/
  saturation), and the periodic cadences (regen `50 >> amp`, poison
  `25 >> amp`, wither `40 >> amp`, hunger every tick) read off the
  countdown clock exactly as the reference's modulo.
- **The instance model** (`potion/StatusEffectInstance`): the combine
  rule (the stronger amplifier replaces with the duration reset, the
  equal keeps the longer, the ambient clears), the tick walk (apply on
  the cadence, then the countdown, expiry when the duration hits zero).
- **The system** (`potion/PotionSystem`): the per-body effect stores
  keyed by engine entity id, the vanilla merge on re-application, the
  undead refusal (canHaveStatusEffect: regeneration and poison), the
  per-tick walk with the cadenced application — regeneration heals one
  under the max, **poison stops one short of dead (the `health > 1.0`
  gate — the reference's own rule, poison never kills)**, wither hurts
  freely, hunger exhausts `0.025 * (amp+1)`, saturation feeds — the
  instant family's smite-aware swing (`4 << amp` heals the living,
  `6 << amp` magic on the undead, mirrored for the harm brew, each
  proximity-scaled and rounded), the drink (the effect set at full
  strength, instants apply the same tick — the reference's
  PlayerEntity.tick order: finishUsing before super.tick's effect walk),
  and the splash (the `0.25`-cube-grown-`(4, 2, 4)` scan box, the
  `1 - sqrt(d2)/4` proximity, the direct-hit 1.0, the timed set at
  `d1 * duration + 0.5` with the 20-tick floor, the instant family
  through the same swing).
- **The throw** (EngineServer): the splash press (one unit, the 0.5
  power with the -20° pitch offset, the 0.5-volume bow, the metadata
  payload on the POTION projectile kind 68), the 32-tick drink clock
  (the separate timer — no hunger gate), the completion (the effect set,
  the glass-bottle swap — drop on a full inventory), **the reference's
  drink feedback (the "random.drink" every four ticks through the last
  25 and at completion — not the food's burp)**, and the hand-change
  cancel.
- **The bottle scoop** (EngineServer): **any water block fills the
  bottle (the material gate — flowing water counts, unlike the bucket's
  source-only rule), and the reference plays no sound for the fill.**
- **The wire**: the Entity Effect 0x1D (varint entity, byte effect,
  byte amplifier, varint duration with the 32767 permanent cap, byte
  flags 1=show-particles) + the Remove Entity Effect 0x1E (varint
  entity, byte effect) — both the reference packet classes' own write
  order; the fan-out to the affected player's own client only (the
  reference's ServerPlayerEntity overrides), the 600-tick re-sync arm
  (tickStatusEffects' `% 600 == 0` re-send), the POTION Spawn Object
  type 68, and the conformance table's two new rows with the
  0x1D-guard repurposed (only the entity effect constant may claim the
  slot).
- **The dispenser arm** (DispenserSystem): the splash potion flies
  (force `1.1 * 1.25`, variation `6 * 0.5`, the metadata payload, the
  1.2-pitch bow); the drinkable falls through to the default item drop.
- **The fire-resistance gate** (EngineServer): the burn family skips a
  protected body entirely (no ignite, no fire damage, the reference's
  isOnFireResistant).

**Bugs found and fixed through the reference read:**

1. **The poison gate** — the port hurt unconditionally; the reference
   stops one short of dead (`health > 1.0`), the death cause now the
   magic message.
2. **The drink's burp** — the port played random.burp at completion;
   the reference plays random.drink (the burp is FoodItem's own) plus
   the every-four-ticks drink feedback.
3. **The bottle's source gate** — the port required a source block;
   the reference's material check accepts flowing water too, and plays
   no fill sound.
4. **The splash's scan box** — the reference grows the potion's own
   0.25 cube, not a point box: the 0.125 half-width now rides every face.
5. **The missing Entity Effect packets** — the effects never reached
   the client HUD; the 0x1D/0x1E pair now lands with the 600-tick
   re-sync.

**Tests:** PotionSystemTest (22): the metadata math (water 0 empty;
regen 8193 → 900 ticks the historical 0:45, the amplifier-II right
shift 450, the extended 2400, the splash shave 676 + the flag; speed
3600, weakness 1800 the harmful half; the instant unit duration; the
color average), the store (the vanilla merge, the undead refusal, the
expiry + the listener), the periodic (the regen cadence two pulses over
51 ticks, **the poison floor**, wither across the floor + the hunger
exhaustion 0.05 at amplifier I, the 600-tick resync), the instant
family (the smite pair both directions, the half-proximity rounding),
the drink (the II heals 8, the timed install, the water nothing), the
splash (the proximity falloff 0.025 → zero heal at 3.9, the direct
1.0, the 4.2 outside, the scaled timed install 450, the 20-tick
floor), and the brewing walk (sugar on water → the mundane 8192, on
the awkward → speed 8194, redstone → extended, glowstone → II,
gunpowder → the splash 16386, the fermented eye → 8202 the corruption
through the flags). The fresh full-suite count: **626 green in
zamin-core** (+19 api +73 protocol) with --max-workers=1.

**Ledgered (not in this slice):** the brewing stand's block entity +
GUI (the ingredient walk exists — `applyIngredient` — the stand
itself rides the container slice), the golden apple's Regeneration II
+ Absorption (row 64 upgrade), the beacon/pyramid effect feed, the
mob-effect metadata color (index 7/8) for the observers' clients, the
world event 2002 splash particles, the witch, CustomPotionEffects NBT.

**Next:** leads (the leash entity + the knot), structures, natural
spawn cycles.

## Slice 11c — the leads (the reference port of item/LeadItem + entity/decoration/LeadKnotEntity + MobEntity's leash arms + PathFinderMobEntity.updateLeashStatus)

**What landed:**

- **The knot model** (`leash/LeashKnot`): the fence-post entity — the
  +0.5-center position, the 100-tick survival poll (DecorationEntity's
  own cadence — a broken fence leaves the knot hanging up to five
  seconds, the vanilla quirk), the global engine entity id in its own
  band (KNOT_ID_BASE above the TNT bodies).
- **The system** (`leash/LeadSystem`): the knot registry, the player's
  hold (the right-click attach + the consume — creative pays nothing),
  the hostile refusal (isTameable's gate: the monster family never
  leashes), the held-mob re-click release (the drop unless creative),
  the fence tie (LeadItem.useOn: every mob the player holds within the
  7-cube ties to the knot — created on the first taker, reused after —
  and the lead itself is NEVER consumed on the tie, the mob already paid
  it), the knot's own interact (the tie with a lead in hand; the removal
  without one — creative releasing the tied without the drop, the
  non-creative leaving them to the holder-gone arm), the per-tick walk
  (the holder-gone release with the drop for dead players and fallen
  knots), and the leash physics — the follow beyond 4.0 through the
  navigation port (moveTo at the mob's own walk speed), the pull beyond
  6.0 (`v += d * |d| * 0.4` per component), the snap at 10.0 with the
  lead's drop.
- **The mob state** (MobEntity): the leashed flag + the two holder forms
  (a player session or a knot), the attach/detach/clear arms, and the
  navigateToward seam (the reference's getNavigation().moveTo(entity,
  1.0)).
- **The engine wiring** (EngineServer): the LeadSurface (the box scan +
  the lead drop through the item entities), the per-tick tickLeashes
  after the mob tick, the interact dispatch's new arms (the knot lookup
  with the reach gate BEFORE the mob lookup; the leash arms FIRST in
  the mob's right-click order per the reference's MobEntity.interact),
  the lead-on-fence use arm (before the repeater/comparator uses), and
  the leads() accessor.
- **The wire**: the Spawn Object type 77 (the knot at the FENCE BLOCK's
  raw corner — blockPos * 32, no +0.5 — TrackedEntity's own
  createSpawnPacket quirk), the Destroy Entities, and the Attach Entity
  0x1B pair for the mob's leash (the holder's id observer-local: own id
  when the observer holds the lead, the remote id for others, the
  knot's global id for fence ties), the join view re-sending both the
  knots and the leashed mobs' attach state, and the OBJECT_LEASH_KNOT
  conformance row.

**Tests:** LeadSystemTest (8): the right-click attach + consume + the
re-click release + the drop, the hostile refusal + the creative
no-consume + the creative no-drop, the fence tie (no consume, the knot
reused at the same fence), the knot's interact (the empty-hand removal +
the holder-gone release with the drop), the physics (the slack, the
8-block follow band, the past-10 snap + the drop), the dead holder, the
100-tick survival fall, and the live-engine tie (joinRequest's real
session, the fence block, the knot in the registry). The fresh full-suite
count: **634 green in zamin-core** (+19 api +71 protocol), zero
failures, --max-workers=1.

**Ledgered (not in this slice):** the leash NBT persistence (the UUID
and the X/Y/Z forms ride the mob persistence slice), the tamed-horse
ownership gate (the reference refuses the stranger's lead on a tamed
horse; the engine's horses have no ownership yet), the leash wrap
animation's client side, the sitting-tameable's early snap.

**Next:** the structures / the natural spawn cycles, then the brewing
stand BE + GUI.

## Slice 11d — the natural spawn cycle (the reference port of world/NaturalSpawner + MobEntity.checkDespawn + MonsterEntity's light rule + AnimalEntity's grass gate + the dimension's time walk)

**What landed:**

- **The cycle** (`spawn/NaturalSpawnSystem`): the spawning-chunk set
  rebuilt every tick (the 17x17 window around every non-spectator
  playing player, the outer ring counted toward the cap but never
  spawning), the per-category caps scaled by the counted chunks
  (`cap * chunks / 289`), the per-chunk walk (the random anchor with
  the `roundUp(height+1, 16)` y ceiling, the non-solid anchor gate),
  the three packs of four drifting attempts (`nextInt(6) -
  nextInt(6)` horizontal, the y's `nextInt(1) - nextInt(1)` never
  moves — the reference's own quirk), the 24-block player and
  world-spawn keep-out rings, the once-per-pack weighted entry pick,
  the 4-per-chunk limit, and the 400-tick animal throttle
  (`timeOfDay % 400 == 0` — CREATURE is the only rare category).
- **The spawn cell**: the full-cube solid floor (no bedrock, no
  partial shapes), the air cell with the air above, the monster's
  randomized pair (the raw sky light vs `nextInt(32)` — a permanent
  probabilistic filter over open sky; the brightness `max(sky -
  darkness, block)` under `nextInt(8)`), and the animal's grass floor
  + the RAW light `max(sky, block) > 8` — `getActualLight` =
  `chunk.getLight(pos, 0)`, NO ambient-darkness shave: animals spawn
  on the midnight surface exactly like the reference.
- **The baby roll**: the horse's own `nextInt(5) == 0` per mob
  (HorseBaseEntity.initialize lines 1284-1286) — the ONLY animal that
  spawns young; the cows, pigs, sheep and chickens land grown (the
  first draft's universal 1/7 corrected against the reference).
- **The despawn** (`checkDespawn` 1:1): the `farFromPlayerTicks`
  counter now increments EVERY tick (serverTickAi line 442) with the
  persistent reset (line 419) and the 32-block reset (lines 433-434)
  — with nobody online the window still counts, so a distant joiner
  meets a warm idle roll; the monsters' 16384-squared instant removal
  and the 600-tick/1/800 idle roll beyond 1024 squared; **the animals
  NEVER despawn** (AnimalEntity.canDespawn lines 104-106 — a penned
  cow stays penned); the canDespawn split rides the hostile flag.
- **The old simplified population maintainer removed**: the 5-second
  top-up rolls, the 96-block range despawn, and the night-raid pick
  are gone — the manager now only owns the per-mob walk, the counts
  (`countByCategory`), and the exact-position spawn
  (`spawnNatural`, the -24000 childhood for the baby horses).
- **The wiring**: the per-tick walk after the mob tick
  (ServerWorld.tick line 180), the SpawnSurface/SpawnWorldQuery
  adapters, and `MobEntity`'s persistent flag (the leashed family
  rides `persistent()`).

**The fix that broke the fire test first**: the initial draft gated
set membership on nothing — the ±8 spawning window at the test's
view-distance 4 forced `getOrGenerate` over ~200 unloaded chunks on
the first rare-gated tick (timeOfDay 0), stalling the tick thread
1.4s past the test's 150ms use window. The chunk gate now intersects
the window with the loaded box (the reference's own border-gate
position; at view distance 9+ the two are identical) — the walk never
generates.

**Tests:** NaturalSpawnSystemTest (7: the darkness walk's
noon/midnight plateaus, the night-surface vs noon gates, the keep-out
rings, the animal rare+grass+light gates with the dirt refusal, the
cap arithmetic at the 70 boundary, the horse-only baby roll with the
not-a-horse assertion, the despawn rules with the animals'
never-leave) + MobManagerTest.theNaturalCycleIsExternalAndTheDespawnRuleHolds
(rewritten: the far zombie goes, the far cow stays forever). The
fresh full-suite count: **641 green in zamin-core** (+19 api +71
protocol) with --max-workers=1, zero failures.

**Ledgered (not in this slice):** the slime/enderman/witch monster
rows and the ambient/water categories (their entities ride their own
slices), the per-biome tables (the single-biome world), the
chunk-populate pass (`populateChunk` — the worldgen animal seeding),
the `spawnAnimals/spawnMonsters` difficulty flags (PEACEFUL holds the
monsters; the engine defaults both on), the mob yaw + the horse
variant/type dice at spawn, the sheep color pick, the thunder arm of
the light gate.

**Next:** the brewing stand BE + GUI (the 11b ledger row), the
structures, the mob equipment/JC queues.

## Slice 11e — the brewing stand (the reference port of block/BrewingStandBlock + block/entity/BrewingStandBlockEntity + inventory/menu/BrewingStandMenu + Item.java's ingredient registrations)

**What landed:**

- **The block family** (`brewing/BrewingStandBlocks`): legacy 117 with
  the three HAS_BOTTLE bits riding the nibble (bit i = has_bottle_i, the
  reference's own getStateFromMetadata lines 145-153) — eight flattened
  states; the strength 0.5 behavior row with the stand-item drop; the
  light-table rows (the reference's setLight(0.125F) = filter 1, which
  the engine's max(1, filter) step absorbs — the stand registers like
  the chest, light passes).
- **The block entity** (`BrewingStandBlockEntity`): the 1:1 port of the
  400-tick cycle (the arming through canBrew with the remembered
  ingredient identity, the countdown with the reference's exact
  cancellation order — the not-brewing gate, then the item-changed gate),
  the canBrew change rule (the splash conversion always counts; the
  effect-list walk with the reference's `(j <= 0 || differ) && (empty ||
  differ-and-nonempty) && j != k` shape — the engine's element equality
  over id/amplifier/duration/splash replacing the reference's cached-list
  identity), the brew's careful write gate (the same-effect brew only
  rewrites when the splash bit itself moved — the reference's inverted
  condition), the ingredient's one-unit payment (no 1.8.8 ingredient
  carries a recipe remainder — verified), the bottle-bits change flag
  (findHasPotion over the cached pair), and the slot surface (the
  potion slots' one-stack limit with the potion/bottle filter, the
  ingredient slot's ingredient gate).
- **The system** (`BrewingStandSystem`): the per-tick walk (the state
  whose block is gone discards — the creative-break path), the
  bottle-bits world write through the ordinary commit path (the
  reference's flag-2 setBlockState), the survival-break spill, the
  viewer fan-out seam, and the hopper's sided surface (getSlots: the UP
  face feeds the ingredient slot alone, every other face the three
  potion slots; canPushItem = the slot filters).
- **The ingredients** (`BrewIngredients`): the item → recipe-string
  table — the reference's Item.potionIngredient registrations item for
  item (gunpowder, redstone, glowstone, sugar, ghast tear, nether wart's
  "+4", spider eye, fermented spider eye, blaze powder, magma cream,
  glistering melon, golden carrot, rabbit foot; the pufferfish rides
  the fish slice). The nine new items with their reference ids (370,
  372, 375-378, 382, 414) + the stand item 379 through the protocol's
  ITEM_OVERRIDES split (the block keeps 117).
- **The GUI + wire**: ContainerKind.BREWING_STAND, the open arm (the
  use dispatch), the click routing (the four filtered slots on wire 0-3
  with the historical click semantics, the number-key/drop arms, the
  shift-click's content-based routing — the ingredient into the empty
  slot 3, the bottles one per potion slot, the non-brewable never
  leaves), the Open Window `minecraft:brewing_stand` with the 4 own
  slots, the 40-slot Window Items (the wire 4-30 main / 31-39 hotbar
  layout of the reference's menu), the brew-timer Window Property 0 on
  change (the reference's updateListeners — the changed value only),
  and the per-tick view fan-out to the open viewer.
- **The cross-system wiring**: the comparator's container fullness over
  the four slots (the analog source gate), the contents-changed wake
  through the markDirty fan-out, the hopper's container adapter (the
  dropper's push and the hopper's feed both reach the stand's slots),
  and the block registry chain.

**Tests:** BrewingStandSystemTest (6: the water→awkward 400-tick walk
with the three bottle bits + the ingredient's one-unit payment, the
cancellation gates — the ingredient leaves, a different item appears
under the running timer — and the no-op's never-arming + the splash's
always-brewing (the gunpowder's `&13-13` clears the carrier: the mundane
8192 turns the plain splash 16384), the survival-break spill + the
container fullness, the hopper's UP-face ingredient feed, and the
shift-click arms with the one-bottle-per-slot walk). The fresh full-suite
count: **647 green in zamin-core** (+19 api +71 protocol) with
--max-workers=1, zero failures.

**Ledgered (not in this slice):** the stand's ZCD persistence (the four
slots + the timer ride the container persistence slice), the pufferfish
ingredient (the fish item's metadata-3 arm), the BREW_POTION
achievement, the custom-name carry from the placed item's hover name,
the smoke particles of randomDisplayTick, the 1.8 client's brewing-bar
inversion (the client maps the raw timer itself — the wire sends the
reference's own value).

**Next:** the container ZCD persistence family (the brewing stand's four
slots join it), the structures, the golden apple's effect set.

## Slice 11f — the container persistence family (the ZDD + the ZBD)

**What landed:**

- **The ZDD** (`dispenser/DispenserDataStore`): the dispenser/dropper
  family's nine slots per position, the same layout and durability rules
  as the chest's ZCD (atomic writes, the corrupt-file quarantine, the
  registry-miss drop with a warning) — the ledgered slot persistence the
  11a slice deferred.
- **The ZBD** (`brewing/BrewingStandDataStore`): the brewing stand's
  four slots + the timer (the reference's own Items + BrewTime NBT
  pair). The restored cycle carries the reference's own quirk exactly:
  the armed ingredient identity is NOT persisted, so the first
  post-load tick's item-changed gate cancels the carried BrewTime and
  the cycle re-arms fresh at the full 400 — the restored timer is
  decorative, exactly like the reference.
- **The wiring**: both stores boot-restore after their systems'
  construction (dispensers.bin, brewing_stands.bin under the world
  data dir) and join `saveAllNow` beside the furnace/chest/sign/mob
  stores.
- **The restores**: `DispenserBlockEntity.restoreSlots` (the nine-slot
  fill) and `BrewingStandBlockEntity.restoreState` (the slots + the
  timer + the null ingredient).

**Tests:** ContainerPersistenceTest (1, the full two-life round trip:
the dispenser's arrow + fire-charge stacks and the brewing stand's
water bottle + nether wart survive the shutdown-restart cycle; the
restored brew cycle re-arms fresh (the null-ingredient quirk); the
bottle bits republish on the first post-load tick; both blocks ride
the world deltas through). The fresh full-suite count: **648 green in
zamin-core** (+19 api +71 protocol) with --max-workers=1, zero
failures.

**Ledgered (not in this slice):** the hopper's five-slot persistence
(the hopper's own save rides the hopper slice's store when it lands),
the leash NBT persistence (the 11c ledger row), CustomPotionEffects
NBT.

**Next:** the golden apple's effect set (the 11b ledger row — the
effect store exists, the apple's own set needs the food wiring), the
structures.

## Slice 11g — the golden apple's effect set (the 11b ledger row)

**What landed:** the eat completion's golden apple arm (tickEating, after
the burp — the reference's own finishUsing order): the plain apple
(metadata 0) installs Absorption I for 2400 ticks (the FoodItem's own
super arm adds nothing — the apple's registration sets no effect pair);
the notch pair (damage 1) adds Regeneration V (amplifier 4) for 600,
Resistance I for 6000 and Fire Resistance I for 6000. The installs ride
the PotionSystem's store (the combine rule + the 0x1D Entity Effect
fan-out to the player's own client — the HUD icons arrive through the
Slice 11b plumbing).

**Tests:** BodySurvivalAcceptanceTest.theGoldenAppleInstallsItsEffectSet
(the plain apple's absorption install + the no-regen gate; the notch
pair's full set with the exact durations and the amplifier). The fresh
full-suite count: **649 green in zamin-core** (+19 api +71 protocol)
with --max-workers=1, zero failures.

**Ledgered (not in this slice):** the FoodItem's own setStatusEffect
pair (the poison chance family — the rotten flesh, the spider eye's
own eat), the absorption hearts' server-side health surface, the notch
apple's glint/rarity (client-side), the golden apple's horse-love arm
(already wired through the horse food table).

**Next:** the structures (the villages/mineshafts/strongholds beyond
terrain decoration), the fish item family (the pufferfish ingredient).

## Slice 11h — the FoodItem effect family + the golden apple correction

**What landed:** the FoodItem's own effect pair table (`Foods.effectOf`
— the reference's setStatusEffect registrations): the raw chicken's
30% Hunger 30 s, the rotten flesh's 80%, the spider eye's certain
Poison 5 s, the poisonous potato's 60%, and the golden apple's certain
Regeneration II 5 s. The eat completion applies the pair exactly like
the reference's FoodItem.addEatEffects (the chance roll, then the
duration × 20 install).

**The correction (the 11g reading was wrong):** the reference's
GoldenAppleItem.addEatEffects calls `super.addEatEffects` for the PLAIN
apple — and the apple's own registration carries
`setStatusEffect(REGENERATION, 5, 1, 1.0F)` — so the plain golden apple
installs Absorption I 2400 **plus Regeneration II 100** (the famous
brief regen). The notch pair's branch skips the super call (its own
Regeneration V replaces). The engine's eat now models the branch
structure exactly: the absorption on both apples, the notch's three,
and the FoodItem pair for everything else (the plain apple included).

**Also:** the spider eye's nutrition row (FoodItem(2, 0.8) — 2 food,
3.2 saturation; the item itself arrived with Slice 11e but the eat gate
had no nutrition for it).

**Tests:** BodySurvivalAcceptanceTest (the corrected
theGoldenAppleInstallsItsEffectSet — the plain apple's absorption +
Regeneration II 100/amplifier 1, the notch pair's full set; the new
theSpiderEyeAlwaysPoisonsItsEater — Poison I 100, amplifier 0). The
fresh full-suite count: **650 green in zamin-core** (+19 api +71
protocol) with --max-workers=1, one projectile-test load flake (the
documented one, 3/3 green in isolation).

**Ledgered (not in this slice):** the fish items (the raw/cooked pairs
with the pufferfish ingredient arm), the poisonous potato's item
registration, the poison chance's interaction with milk (the item
itself is not registered).

**Next:** the structures (the villages/mineshafts/strongholds), the
fish item family.

## Slice 11i — the fish item family

**What landed:** the reference's `FishItem` (reference/1.8.8
item/FishItem.java) ported 1:1: the two items (the raw fish 349, the
cooked fish 350) whose four variants ride the damage field — 0 cod,
1 salmon, 2 clownfish, 3 pufferfish — through a new
`server/item/FishItems` (the reference's own Type table: the raw pair
2/0.1 and 1/0.1, the cooked pair 5/0.6 and 6/0.8, the clownfish and
the pufferfish non-cookable).

**The nutrition dispatch:** `Foods.nutritionOf(type, damage)` — the
damage-aware overload the eat gate, the eat-start gate and the
completion all call — answers the fish family by the metadata variant
exactly like the reference's getHungerPoints/getSaturation overrides
(`cooked && canBeCooked()` gates the cooked arm). The reference's own
quirks kept: `byId` answers COD for an unknown metadata, and a cooked
clownfish/pufferfish answers the RAW values.

**The pufferfish's eat triple** (FishItem.addEatEffects lines 38-47,
BEFORE the super call — the reference's own order): Poison IV 1200
(60 s), Hunger III 300 (15 s), Nausea II 300 (15 s), all certain, no
chance roll. The dispatch reads the metadata of EITHER fish item —
a cooked fish with pufferfish metadata eats (and poisons) like a raw
pufferfish, the reference's own Type.byItem behavior.

**The brewing arm:** `BrewIngredients.recipeOf`'s pufferfish branch —
the metadata-3 of either fish item answers
`PotionHelper.PUFFERFISH` ("+0-1+2+3+13&4-4") exactly like the
reference's asPotionIngredient. The awkward potion (16) brews into
the water breathing (8205, the 3:00 base), the ingredient paying its
single unit. The arm flows through every brewing surface: canBrew,
the applyIngredient walk, the ingredient slot's isItemAllowed filter,
the shift-click routing, the hopper's push gate.

**The smelting pair:** `FurnaceRecipes.resultOf(type, damage)` — the
damage-aware overload (the four furnace call sites updated: canSmelt,
smeltOne, quickMoveIn, the manager's shift-click gate): the raw cod →
the cooked cod (damage 0), the raw salmon → the cooked salmon (damage
1); the clownfish and the pufferfish never cook (the canBeCooked gate).
The cooked fish joins the cooked-food band's 0.35 take XP. Also the
data-gap fix: the mutton → cooked mutton row (the item's own
registration comment claimed the smelt since the living-night slice,
but the row never landed — the community table's own entry).

**Tests:** BodySurvivalAcceptanceTest (the new
thePufferfishPoisonsItsEaterWithTheFullTriple — the exact three
durations + amplifiers, all certain; theFishFamilyNourishesByItsVariant
— the full Type table, the live cooked-salmon eat, the two quirks: the
cooked clownfish eats raw, the unknown metadata answers cod, the live
cooked-pufferfish-eats-raw-and-poisons quirk).
BrewingStandSystemTest.thePufferfishBrewsTheWaterBreathing (the 16 →
8205 walk with the unit payment + the 3:00 effect set).
MetadataSmeltingTest (rawFishSmeltsIntoItsCookedVariant — the pair +
the two never-cook gates; theFurnaceSmeltsFishCarryingTheVariant — the
live smelt carrying the damage, the stacking, the take XP). The fresh
full-suite count: **655 green in zamin-core** (+19 api +71 protocol)
with --max-workers=1, zero failures (one hopper-test load flake in a
first pass, 7/7 green in isolation and in the second full run — the
same environmental class as the documented projectile flake).

**Ledgered (not in this slice):** the fishing bobber (the rod's cast,
the hook entity, the loot table), the guardian/squid mobs with their
fish drops, the ocelot's fish taming, the cooked fish's saturation
serving on the client HUD (rides the body sync).

**Next:** the structures (the villages/mineshafts/strongholds).

## Slice 12a — the structure foundation + the mineshaft

**What landed:** the reference's structure system foundation
(reference/1.8.8 gen/structure/) ported into
`server/world/structure/`: the `StructureBox` (1:1 with the 4-field
chunk-clip quirk minY 1 / maxY 512), the `StructurePiece` base (the
exact transformX/Y/Z quadrant math, the fill family — fillWithOutline,
fillRandomlyWithOutline with the reference's inclusive
`!(nextFloat() > threshold)` gate, setBlockStateWithThreshold,
placeUpperHemisphere with the 1.05 ellipsoid test, the
bordersOnLiquids clipped-shell probe, getIntersectingPiece), the
`StructureStart` (the union bounds, the moveBelowSeaLevel re-base with
the spread roll), and the `StructureWorld` view (in-chunk reads from
the chunk, out-of-chunk reads from the pure terrain — the
deterministic equivalent of the reference's populate-time world
reads).

**The placement driver:** `MineshaftStructure.populate` — the
reference's Generator.place ±8 region walk with the exact seed math
(two longs drawn once from the world seed; every region seeds
`regionX * first ^ regionZ * second ^ worldSeed`, burns one draw,
then rolls the feature gate), the per-chunk postProcess over the
4-field clip, and the reference's own populate-random seed
(`chunkX * 341873128712L + chunkZ * 132897987541L`, no world-seed
fold — the reference's quirk).

**The mineshaft 1:1** (MineshaftStructure + MineshaftStart +
MineshaftPieces): the 0.004 chance AND the
`nextInt(80) < max(|x|,|z|)` distance gate; the Room at the region's
+2 corner (the 8-13 wide hall, the dirt floor, the dome carve, the
entrance cutouts); the recursive walk (the 80-block leash, the depth-8
cap, the piece rolls corridor <70 / stairs 70-79 / crossing 80+); the
Corridor (the 1-in-3 rails, the 1-in-23 cobwebs, the fence frames
with the plank caps and the 1-in-4 gap, the cobweb/torch chance
writes, the floor patch, the 0.7 rail roll over solid floors with the
west/east metadata flip); the Crossing (the 5x5x3 junction, the
1-in-4 second floor with the four boolean-gated upper arms, the plank
pillars, the floor patch); the Stairs (the five-step shaft).

**The architectural decision (the ledger's envelope note):** the
reference caches starts across chunks and removes a piece the first
time its clipped shell touches a liquid — a load-order-dependent
removal the engine's deterministic contract cannot reproduce. The
engine recomputes starts per chunk from pure (seed, region) inputs: no
shared cache, no locks, the parallel chunk pipeline stays stateless —
a piece refuses only in the chunks whose own clipped shell is wet and
stays dry elsewhere (a superset of the reference's
interleaving-dependent outcomes).

**Also:** the cobweb block registered (legacy 30 — the corridor's
1-in-23 resident, walk-through like the vanilla web; the slow effect
ledgers with its own slice) through BuiltinBlocks + LegacyBlockIds +
the WorldSolidity exclusion; the NormalWorldGenerator's populate hook
(the structure pass after the flora); the BlockRegistryTest count
102 → 103.

**Ledgered (not in this slice):** the chest minecart with the
corridor's loot rolls (the minecart entity family), the cave-spider
mob spawner (the block entity + the spawner logic), the villages, the
strongholds, the fortresses (the reference's remaining structure
families over the same foundation), the cobweb's entity slow effect.

**Tests:** MineshaftStructureTest (2: the hit-region walk — a start
found by the region probe generates material under the sea floor in
its own bounds with the re-base below SEA_LEVEL - 8; the same seed
regenerates identical chunks cell-for-cell). The fresh full-suite
count: **657 green in zamin-core** (+19 api +71 protocol) with
--max-workers=1, zero failures.

**Next:** the villages (VillageStructure + VillagePieces over the
foundation), the strongholds.

## Slice 12b (investigation) — the village dependency map

**Scope mapped, not yet implemented** (the reference survey for the
next session):

- **The placement (VillageStructure.java):** the 32-chunk distance /
  8-chunk min-distance grid (the negative-coordinate floor walk), the
  region random through
  `world.setRandomSeed(k, l, 10387312)` (the k/l cell pair with the
  10387312 salt — needs the engine-side setRandomSeed derivation:
  `k * 1289931791L ^ l * 797977377L ^ seed`-shape, to be read from
  World.java), the random offset pair, and the biome gate
  `isBiomeWithin(chunkX*16+8, chunkZ*16+8, 0, [PLAINS, DESERT,
  SAVANNA])` — the engine's three-biome climate answers plains/desert
  directly (forest is not a village biome, the engine's FOREST maps
  out).
- **The Start (VillageStructure.Start lines 1083-1121):** the
  piece-weight table (getPieceWeights: nine building families with
  the weight + the MathHelper.nextInt count rolls), the Well start
  piece at the region's +2 corner, the road/building queue walk (the
  random-pick drain until both lists empty), the isValid gate (more
  than two non-road pieces).
- **The missing base helpers the pieces need (StructurePiece.java):**
  `updateHeight` (the terrain-height probe averaging
  `world.getSurfaceHeight` over the piece's clipped box — the engine
  answer: the generator's pure `heightAt`), `fillAirColumnUp` /
  `fillColumnDown` (the column helpers), `StructureBox.orient` (the
  facing-oriented box builder), `postProcessBlockMetadata`'s
  ladder/stairs/button door families (the facing-dependent metadata
  flips), `spawnVillagers` (the engine has the villager mob + the
  professions), `placeWoodenDoor`, the chest-with-loot pair (the
  blacksmith's two chests), the mob-spawner (the priest house never
  places one in 1.8.8 — only the mineshaft does).
- **The piece families:** Well/Start (the 4x4x4 cobblestone well),
  Road/RoadPiece (the 3-wide gravel paths with the torch lamp posts),
  SmallHouse (the 5x6x5 plains house with the terrace roll + the
  ladder), PriestHouse, LibrarianHouse, FarmerHouse, ButcherHouse,
  DoubleFarm/Farm (the wheat/carrot/potato fields), BlacksmithHouse
  (the lava + the two chests), LargeHouse, LampPost.
- **The engine surface already present:** cobweb/fence/planks/torch/
  cobblestone/log/rail (the mineshaft palette), the villager mob with
  professions + trading, the heightAt pure function, the structure
  foundation's transform/fill family (Slice 12a).

**Next session's order:** the base helpers (updateHeight over
heightAt, the column pair, orient, the metadata flips) → the Well +
the road walk → the SmallHouse + the Farm (the two most common
families) → the rest of the building set.

The repository state: Slice 12a pushed (21b36c5), 657 green in
zamin-core (+19 api +71 protocol), the ledger and this worklog
current.

## Slice 12b — the villages

**The reference port** (`gen/structure/VillageStructure.java` +
`VillagePieces.java` + `StructurePiece.java`'s helper family, 1:1 over
the Slice 12a foundation):

- **The placement** (`VillageStructure`): the 32-chunk distance /
  8-chunk min-distance grid — the reference's own negative-coordinate
  floor walk (`(chunk - 31) / 32`), the second random seeded
  `cellX * 341873128712L + cellZ * 132897987541L + worldSeed +
  10387312` (the `World.setRandomSeed(k, l, 10387312)` derivation), the
  `nextInt(24)` offset pair picking one chunk per cell, and the biome
  gate at the chunk's +8 center (every sampled 4-block cell must be
  plains or desert — the engine carries no savanna, the envelope note).
- **The start**: the piece-weight table from the region random
  (post-burn — the reference's own order), the well at the region's +2
  corner, the road/building queue drain until both lists empty, the
  bounds union, and the valid gate (more than two non-road pieces or
  the village never writes).
- **The thirteen families**: the Well (the 4x4 cobblestone shaft with
  the water column, the four fence posts, the cobble roof, the gravel
  ring), the Road (the surface-probing gravel path with the side
  buildings + the two road-split arms), the SmallHouse (the terrace +
  ladder rolls), the FarmerHouse (the low-ceiling + table rolls), the
  ButcherHouse + the LibrarianHouse + the LargeHouse (the gable-roof
  loop with the per-facing stair metadata), the PriestHouse (the
  12-tall tower with the internal ladder), the BlacksmithHouse (the
  lava pools behind iron bars, the double furnace, the double stone
  slab table, the 17-row loot chest), the two farms (the farmland rows
  over the water channels with the three crop families' random
  stages 2..7), and the LampPost.
- **The base helpers** (on `StructurePiece`/`StructureBox`):
  `postProcessBlockMetadata` ported in full (the seven metadata
  families: rail, door, stairs, ladder, stone button, horizontal,
  piston — each with the reference's own per-facing remaps),
  `fillAirColumnUp`/`fillColumnDown` (the terrain flatteners),
  `placeWoodenDoor` (the metadata flip + the counter-clockwise turn),
  and `StructureBox.orient` (the facing-oriented box builder).

**The new engine surface:**

- **The village block set**: the glass pane (102), the iron bars
  (101), the double stone slab (43), the smooth sandstone (24/2), the
  sandstone stairs (128, E/W/S/N), and the two new crop families —
  carrots (141) and potatoes (142) with the age 0..7 band (all with
  the legacy wire rows + the behavior/light/sound tables).
- **The populate-time attachments** (`EngineChunk.pendingVillagers` +
  `pendingGenChests`): the reference's structure `addEntity` +
  `placeChestWithLoot` rides the engine's ownership model — the
  generator thread writes the records while the chunk is detached,
  the publication is the safe-publication barrier, and the tick thread
  drains them exactly once (the villagers materialize through
  `MobManager.spawnAt` with the building's profession; the chest
  contents roll at generate time and land in the ChestManager). The
  restart idempotence guards: a villager already standing at the
  position (the mob store's restore) skips the re-spawn; a chest with
  contents (the ZCD restore) skips the re-roll — the deterministic
  regeneration never duplicates.
- **The crop growth generalization**: the wheat, the carrots and the
  potatoes all extend the reference's BlockCrops rule (the light-9
  gate, the hydrated 1/3 / dry 1/6 chance) — the bone meal arms in
  both the player path and the dispenser now grow any of the three
  families.
- **The potato item family** (392/393): the raw/baked pair with the
  food rows (1/0.6, 5/6.0) and the smelting row (0.35 XP); plus the
  blacksmith's loot pair (the obsidian item 49, the oak sapling 6).

**The envelope notes** (all in the ledger row): the standing-torch
stand-in for the wall torches; the hinge-less door upper; the pure
full-box `updateHeight` (chunk-independent, identical to the reference
for single-chunk pieces); the per-chunk villager count reset; the
-1 profession convention; the drained-chest refill edge.

The fresh full-suite count **665 green in zamin-core** (+19 api +71
protocol), `--max-workers=1`, one projectile-test load flake (the
documented one — green in isolation and in a second pass).

**Next:** the strongholds (StrongholdStructure + StrongholdPieces over
the foundation), the fortresses, the ocean monuments, the temples.

## Slice 12c — the strongholds

**The reference port** (`gen/structure/StrongholdStructure.java` +
`StrongholdPieces.java`, 1:1 over the Slice 12a foundation):

- **The placement** (`StrongholdStructure`): the three-position table
  drawn once from the world seed — the spiral walk from a random angle
  (`(1.25 * ring + nextDouble()) * distance * ring` per ring), the
  findBiome reservoir snap (the ±112-block window at 4-block steps,
  the `nextInt(k+1) == 0` uniform keep), the `2π * i / spread` angle
  advance and the ring growth at the spread boundary
  (`i += 2 + nextInt(5)`, `spread += 1 + nextInt(2)`).
- **The start**: the weights reset, the source spiral staircase at the
  position chunk's +2/+2 corner (the random facing, the y-64 anchor
  box), the forced FiveWayCrossing after the stairs down, the children
  queue drained in random order, the bounds union, the
  moveBelowSeaLevel(10) re-base — regenerated until the walk leaves an
  end portal room (the retry gate).
- **The weight-table walk**: the reference's exact quirks preserved —
  the exhausted rows still fold the total (a nextInt roll can land on
  an exhausted row and burn a try), the no-repeat previous row, the
  depth-gated Library (>4) and EndPortalRoom (>5), the PlainCorridor
  fallback with the findSize shrink, the ±112 leash + the depth-50 cap.
- **The eleven families**: the StraightCorridor (the torch threshold
  rolls, the side openings), the Prison (the iron bar cells, the iron
  door pair with the metadata flip), the LeftTurn/RightTurn elbows,
  the RoomCrossing (the fountain pillar, the lava brazier, the storage
  loft with the ladder + the chest), the StraightStairs (the six-step
  flight), the SpiralStaircase (the winding steps, the Start's source
  shape), the FiveWayCrossing (the two floors, the four side arms), the
  ChestCorridor (the smooth-slab pedestal + the loot chest), the
  Library (the shelf walls, the gallery balcony with the fence/torch
  pedestal, the cobwebs, the two chests), the EndPortalRoom (the lava
  moat + the pools, the iron bar walls, the stair platform, the twelve
  frames with the per-frame eye rolls + the per-facing remaps, the
  silverfish spawner).
- **The entrance family**: the four shapes over the stone-brick frames
  (OPENING / WOOD_DOOR / GATES / IRON_DOOR with the stone button pair).

**The new engine surface:**

- **The stronghold block set**: the stone-brick trio (98: plain/mossy/
  cracked), the infested stone-brick egg (97/4), the stone-brick slab
  (44/5), the mob spawner (52), the iron door's five states (71), the
  end portal frame's eight (120: the facing band | the eye bit) — all
  with the legacy wire rows, the metadata nibbles, the behavior rows
  (the frames undiggable, the spawner dropless, the variants dropping
  the plain brick) and the light rows.
- **The loot item family**: the ender pearl (368), the empty map (395),
  the compass (329), the iron door item (330).
- **The fillPicked picker fill** on `StructurePiece` (the reference's
  fill + BlockPicker, 1:1): the per-cell roll walk behind the
  reference's own air gate — the StoneBrickPicker's band (0.2 cracked /
  0.5 mossy / 0.55 infested).
- **The GenLootSlot enchantment extension**: the chest drain
  materializes the enchanted book with its rolled level-30 enchantment
  (the reference's own roll order — createLoot inside addAll BEFORE the
  amount roll).
- **The SlotNbt StoredEnchantments arm**: the enchanted book's own NBT
  shape on the wire (never the item "ench").

**The architectural decision** (the same envelope as the village's
piece-weight walk): the reference's STATIC weight state
(`currentWeights`, `forcedPiece`, `totalWeight`, `Start.previous`) is
per-start instance state in the engine — held by StrongholdStart — so
the parallel chunk pipeline's per-chunk recomputation never shares
mutable statics. The positions derive from the world seed alone
(computed once at construction); the starts recompute per chunk from
pure (seed, region) inputs — no locks, identical piece sets.

**The envelope notes** (all in the ledger row): the spawner's block
entity + the silverfish logic ledger with the mob-spawner slice; the
silverfish burst from the infested egg ledgers with the mob slice; the
chest's updateFacing re-orientation absent; the frames' eye rolls are
per-chunk (the engine's per-chunk determinism envelope); the count-0
roll writes nothing.

The fresh full-suite count **675 green in zamin-core** (one registry
count row updated for the nineteen new blocks), `--max-workers=1`,
zero failures beyond the documented load flakes.

**Next:** the fortresses (FortressStructure + FortressPieces over the
foundation — the nether bridge family), the ocean monuments, the
temples.

## Slice 12d — the nether fortresses

**The reference port** (`gen/structure/FortressStructure.java` +
`FortressPieces.java`, 1:1 over the Slice 12a foundation):

- **The placement** (`FortressStructure`): the cell = chunk >> 4 (16
  chunks per cell), the seed `cellX ^ (cellZ << 4) ^ worldSeed`, the
  burned draw, the nextInt(3) == 0 gate AND the pick identity
  `(cellX << 4) + 4 + nextInt(8)` on both axes (the z roll
  short-circuits behind the x check — the reference's own && order).
- **The start**: the 19x19 BridgeCrossing shape at the pick chunk's +2
  corner (the random facing, the y-64 anchor), the children queue
  drained in random order, the bounds union, the
  moveBetweenYCoords(48, 70) band re-base (the fortress's own re-base,
  now on StructureStart).
- **The two weight tables**: the bridge walk (StraightBridge 30/0
  connector, BridgeCrossing 10/4, LargeCrossing 10/4, BridgeStairs
  10/3, BlazeSpawner 5/2, CastleEntrance 5/1) and the castle walk
  (SmallCorridor 25/0 connector, SmallCorridorCrossing 15/5, the turns
  5/10, StairsCorridor 10/3 connector, CorridorBalcony 7/2,
  NetherwartFarm 5/2) — the getTotalWeight -1 sentinel, the five-try
  walk, the connector-repeat rule (a non-connector row may not follow
  itself), the depth-30 cap, the ±112 leash, and the BridgeEnd
  fallback (its own constructor-drawn seed with the wall-length rolls
  from it; the out-of-leash fallback does NOT join the lists).
- **The fourteen families**: the StraightBridge causeway with the rail
  fences, the BridgeCrossing hub, the BridgeEnd terminator, the
  BridgeStairs tower, the BlazeSpawner's stair platform with the
  spawner, the CastleEntrance gatehouse with the lava well, the
  CorridorBalcony with its two nextInt(8) corridor rolls, the
  LargeCrossing roofed hub, the SmallCorridor family with the chest
  rolls (nextInt(3) == 0), the StairsCorridor's ten-step flight, and
  the NetherwartFarm (the stair cascade, the soul sand beds with the
  age-0 wart, the two-level gallery).

**The new engine surface:**

- **The fortress block set**: the nether bricks (40), the nether-brick
  fence (113), the nether-brick stairs (114, the ascending band), the
  nether wart (115, the age 0..3) — with the legacy wire rows, the
  metadata nibbles, the behavior rows and the light rows.
- **The block item trio**: 405/113/114.
- **The moveBetweenYCoords band re-base** on StructureStart (the
  reference's lines 106-121).
- **The nether generator's populate hook**: the fortress pass rides
  the NetherWorldGenerator with the StructureWorld view (the
  out-of-chunk answer is the bulk stone shape — the fortress pieces
  pass avoidAir=false and never probe the liquid border, so no
  out-of-chunk read gates any write; the envelope note).

**The envelope notes** (all in the ledger row): the blaze spawner's
block entity + the fortress's own monster spawn table ledger with the
mob-spawner/natural-spawn slices; the castle entrance's
populate-time lava tick ledgers with the fluid-update slice; the wart's
2-4 maturity drop band ledgers with the farming slice.

The fresh full-suite count **682 green in zamin-core**, `--max-workers=1`,
zero failures (the NetherWorldTest's registry updated to the engine's
full boot chain — the fortress pass pulls the structure palette).

**Next:** the ocean monuments, the temples.

## Slice 12e — the ocean monuments

**The reference port** (`gen/structure/OceanMonumentStructure.java` +
`OceanMonumentPieces.java`, 1:1 over the Slice 12a foundation):

- **The placement**: the spacing 32/separation 5 grid (the
  negative-coordinate floor walk), the setRandomSeed(10387313)
  derivation, the reference's own two-roll averaged pick
  `(nextInt(27)+nextInt(27))/2` per axis, the DEEP_OCEAN center gate
  and the ±29 all-water window (BiomeSource.isBiomeWithin 1:1).
- **The start**: the reference's own seed walk (two longs from the
  world seed, the `chunkX*first ^ chunkZ*second ^ seed` start
  random), the -29 anchor, the random horizontal facing, the fixed
  y 39..61 band (no re-base — the reference's own shape).
- **The room-graph layout**: the 75-cell lattice (the y0 floor ring,
  the y1 full ring, the y2 core ring), the six-direction connection
  walk with the reference's z-plane opposite-flip quirk, the three
  appended specials, the core pick with the eight claim marks, the
  shuffle, and the findSource entrance-removal pass.
- **The eleven families**: the master monument (the water fill, the
  wing pair, the entrance arch + wall, the roof pyramid, the three
  wall tiers, the base grid, the corner fillets), the CoreRoom (the
  gold block), the five double rooms, the MonumentEntry, the
  Penthouse, the SimpleRoom's three designs, the SimpleTopRoom (the
  wet sponge), the WingRoom's two designs.

**The new engine surface:**

- **The ocean biomes**: OCEAN (id 0) + DEEP_OCEAN (id 24) over the
  pure heightAt bands (<sea-4 the deep, <sea the ocean — measured
  57%/1.2% of the terrain).
- **The monument block set**: the prismarine trio (168), the sea
  lantern (169), the sponge pair (19), the gold block (41).
- **The item family**: the prismarine, the crystals, the shard, the
  lantern, the sponge, the gold block.
- **The StructureBox.of normalizer + the 4-field horizontal
  intersects** (the monument's local-frame wall probes).

**The envelope notes**: the elder guardian spawns + the monument's
guardian spawn table ledger with the guardian-mob slice; the frozen
ocean/river absent from the valid-biome family; the placedMonuments
dedup subsumed by the one-populate-per-chunk model.

The fresh full-suite count **688 green in zamin-core**, `--max-workers=1`,
zero failures (the registry count row updated to 163; the NetherWorldTest
chain rides the full boot).

**Next:** the temples (the desert pyramid + the jungle temple), the last
of the structure families.

## Slice 12f — the temples

**The reference port** (`gen/structure/TempleStructure.java` +
`TemplePieces.java`, 1:1 over the Slice 12a foundation):

- **The placement**: the distance 32/min-distance 8 grid (the
  negative-coordinate floor walk), the setRandomSeed(14357617)
  derivation, the one-roll-per-axis pick pair, the biome gate at the
  chunk's +8 center.
- **The start**: the biome dispatch (the jungle temple / the witch hut
  / the desert pyramid) at the chunk's own origin with the
  StructureFeature's region random (the burned draw after the seed
  fold).
- **The desert pyramid**: the stepped shell, the four corner towers
  with the per-facing stair metadata, the blueprint floor (the orange
  clay + the yellow center — the reference's own ~DyeColor pair), the
  hidden TNT chamber (the -14 shaft, the 9-cell TNT floor, the stone
  pressure plate), and the four direction chests with the treasure
  table + the enchanted-book row.
- **The jungle temple**: the mossy cobblestone picker (40/60), the
  multi-storey shell with the stair roof, the two tripwire dispenser
  traps (the hook pairs with the attached wire + the redstone trail +
  the facing-flipped dispensers with the arrow loot), the vine
  camouflages, the lever-piston puzzle (the lever trio, the UP sticky
  piston + the WEST pair, the repeater), and the main + hidden chests.

**The new engine surface:**

- **The JUNGLE biome** (id 21 — the hot-wet climate band).
- **The temple block set**: the chiseled sandstone (24/3), the stained
  clay pair (159/1 + 159/4), the mossy cobblestone (48), the vine
  (106/15), the chiseled stone bricks (98/3).
- **The placeGenDispenser pending record**: the chunk carries the
  rolled arrow stacks; the EngineServer drain materializes them into
  the dispenser system with the contents guard (the same model as the
  chest).

**The envelope notes**: the witch hut arm never fires (no swampland
biome — the piece ledgers with the swamp slice); the desert/jungle
hills absent; the updateHPos average reads the pure heightAt; the
jungle's traps are live systems (the tripwire + the dispenser + the
redstone run at tick time).

The fresh full-suite count **691 green in zamin-core** (one registry
count row updated; one hopper-test load flake — the documented
environmental class, green in isolation).

**The structure family is complete**: mineshafts, villages,
strongholds, fortresses, ocean monuments, temples — all six of the
1.8.8 overworld/nether structure families ported 1:1 over the
deterministic per-chunk foundation.

## Slice 12g — the mob spawners

**The reference port** (`block/spawner/MobSpawner.java`, 1:1 over the
engine's ownership model):

- **The SpawnerManager** (the chest manager's own pattern): the
  per-position state (the typed record + the delay), the tick walk —
  the 16-block player gate, the -1 first-tick delay reset, the 200..800
  band on fire, the four-attempt spawn cycle with the 6-mob nearby cap
  over the spawn-range-grown box, the position rolls in the reference's
  own distribution shapes (`nextDouble() - nextDouble()` per axis,
  `nextInt(3) - 1` for y), and the solid-gate stand-in (the body cell
  + the head cell clear).
- **The typed pending record**: `placeMobSpawner` on the
  StructureWorld + EngineChunk.pendingMobSpawners + the EngineServer
  drain with the contents guard.
- **The three structures' arms wired**: the mineshaft's cave-spider
  spawner (the 1:1 arm — the section's random cell, the cobweb-pair
  gate), the stronghold's silverfish spawner, the fortress's blaze
  spawner.
- **The three mob types**: CAVE_SPIDER (59), SILVERFISH (60), BLAZE
  (61 — the ranged trait, the fireball ledgers with the projectile
  slice).

**The envelope notes**: the event 2004 + the spin display + the cage
entity's wire sync ledger with the particle/metadata slices; the
spawner state is in-memory (the persistence ledger row lands with the
block-entity store slice); the fortress's own monster spawn table
(Blaze 10/2/3, ZombiePigman 5/4/4, Skeleton 10/4/4, MagmaCube 3/4/4)
ledgers with the natural-spawn override slice.

The fresh full-suite count **694 green in zamin-core** (one hopper-test
load flake — the documented environmental class, green in isolation).

## Slice 13a — the native API foundation: the event bus + the first wired events

**The architecture response** (§27, the new API chapter the user added):
the engine is rich (290 core classes, the whole mechanics family landed)
while the public API carried 19 files of identity types. The order §27.1
sets — mechanics and API maturing together — starts here.

- **NATIVE_API_CAPABILITIES.md** (new): the §27.10 capability ledger —
  the full audit of every engine subsystem against its public exposure
  (8 sections, ~60 rows, every `NOT AUDITED` row naming what stands
  between the implementation and the plugin-facing surface).
- **`net.zaminmc.torch.event`** (zamin-api): `Event`, `Cancellable`,
  `EventPriority` (FIRST→MONITOR), `EventHandler`, `EventSubscription`,
  `EventBus` — the §27.7 observe/cancel contract with the threading
  semantics documented at every level: registration safe from any
  thread, dispatch on the firing owner, handlers must not block, MONITOR
  is convention (a POJO's setter cannot be policed by the bus).
- **`EngineEventBus`** (zamin-core): concurrent registration structures,
  per-dispatch snapshot (mid-dispatch registry changes affect the next
  fire, not the in-flight one), class-hierarchy dispatch (base handlers
  receive subtypes), handler exceptions caught+reported with the
  dispatch continuing.
- **The four first-wave events**, wired to real engine paths:
  - `BlockBreakEvent` — pre-commit in `tryMineBlock` (survival) and
    `creativeBreakOnTick`; cancel prevents mutation/drops/wear/XP and
    re-syncs the cell so client prediction drops.
  - `BlockPlaceEvent` — pre-commit in both place paths; cancel prevents
    the mutation AND the survival item consumption.
  - `MobSpawnEvent` — `MobManager.spawnAt` (the single chokepoint every
    spawn source uses); cancel returns null, consumes no entity id,
    fires no engine listeners. `spawnGroupAt` and the villager drain
    null-safe.
  - `PlayerChatEvent` — `ChatService.handleOnTick` post-sanitize,
    pre-broadcast; cancel drops the message entirely.
- **`Server.events()`** — the public bus accessor; the engine creates it
  eagerly and wires it into the three services at boot.
- **The worlds() gap fixed**: the Server view returned only the
  overworld — both booted dimensions (overworld + nether) are public now.
- **Tests**: `EventBusUnitTest` (9: the priority/registration order, the
  cancellation read, exception continuation, idempotent unsubscribe,
  self-unsubscribe mid-dispatch, subtype dispatch, cross-thread
  registration, type isolation, reentrant nesting) +
  `EventApiAcceptanceTest` (6: the bus/worlds exposure, the cancelled
  break with the unsubscribe arm, the cancelled place with the land arm,
  the cancelled spawn, the cancelled chat through a counting publisher,
  the MONITOR chat observation with the sanitized payload).

The fresh full-suite count **709 green in zamin-core** (+15),
`--max-workers=1`, one timing-test load flake (the documented
environmental class — `VanillaMiningTimingTest`, green in 3 consecutive
isolated runs).

**Next:** the entity/snapshot wave (Slice 13b) — the `Entity`/`LivingEntity`
handle family over `MobEntity`, the snapshot reads, the damage events;
then the player wave (13c).

## Slice 13b — the entity handle wave: LivingEntity + World.execute

**The §27.5/§27.6 surface** (the engine object IS the handle — no wrapper
class, no state copy):

- **`Entity`** (api): entityId + typeIdentifier + snapshot.
- **`EntitySnapshot`** (api record): the immutable any-thread view —
  identity, position, yaw, velocity, health/maxHealth, onGround, dead,
  plus horizontalSpeed().
- **`LivingEntity extends Entity`** (api): the live surface with the
  threading contract on the interface itself — position/rotation/
  velocity/health reads, heal/hurt/addVelocity/navigateToward/
  persistent/setPersistent/despawn mutations, all documented as
  simulation-context (the owner path is World.execute).
- **`World.entities()`** — the live population view (the manager's
  captured copy, point-in-time per call); **`World.execute(Runnable)`** —
  the §27.6 owner-context primitive routing onto the engine ticker's
  between-ticks lane. Both worlds carry them (the nether's entity view is
  empty until its own population lands — the engine's mobs live in the
  overworld; documented).
- **`MobEntity implements LivingEntity`**: typeIdentifier, rotation
  (yaw-only — mobs carry no pitch), snapshot, despawn (the removal hook
  the manager installs at spawn: `remove(mob, "api")`).
- **The seven test fakes** implementing World anonymously grew the two
  new methods (the API stays abstract — implementors must think).

**Tests**: `EntityApiAcceptanceTest` (4: the live view + the identity
contract, the foreign-thread owner-context mutation ladder — hurt lands,
heal restores, the impulse rides; the off-owner snapshot consumption; the
handle despawn with the baseline-count isolation from the boot
population). The fresh full-suite count **713 green in zamin-core** (+4),
`--max-workers=1`, ZERO failures.

**Next:** the player wave (Slice 13c) — the health/food/XP/inventory
surface over PlayerSession, then the scheduler/task controls.

## Slice 13c — the player wave: the body/progression/inventory surface

**The §27.3 player-state list over `Player`** (the session IS the handle,
the same §27.5 contract):

- **The body family**: health/food/saturation/dead reads, hurt/heal
  (the new clamped restore)/addExhaustion mutations.
- **The progression family**: experienceLevel/totalExperience
  (delegating the engine's totalXp)/addExperience.
- **The mode + fire family**: gamemode/setGamemode (the authoritative
  switch), burning/ignite/extinguish.
- **The inventory family**: heldItem/heldSlot/countOf/give — give rides
  the engine's own vanilla pickup rules (stack merge first, empty slots
  next) and re-syncs the wire through the engine's inventory fan-out
  (the sync hook installed at session creation; the API give behaves
  exactly like the engine's ground-pickup path).
- **The threading contract on the interface**: reads consistent on the
  owner, mutations owner-context (the documented path World.execute).

**Tests**: `PlayerApiAcceptanceTest` (5: the hurt/heal ladder with the
cap, the XP award crossing levels, the mode switch + the fire
ignite/extinguish cycle, the give/overflow ladder — 5 + 61 diamonds
splitting across two stacks, the sync-hook firing on the API give). The
fresh full-suite count **718 green in zamin-core** (+5),
`--max-workers=1`, ZERO failures.

**Next:** the §27.3 checklist continues — the scheduler/task controls
(delayed/repeating), then back to the vanilla rows: the worldgen animal
seeding, the remaining spawn categories.

## Slice 12h — the worldgen animal seeding (populateChunk 1:1)

**The reference port** (`NaturalSpawner.populateChunk` lines 164-215, called
from `OverworldChunkGenerator.populateChunk` line 393 — the populate step's
LAST pass):

- **The group roll**: the `while (nextFloat() < 0.1)` chance
  (Biome.getAnimalSpawnChance), the weighted pick (the cumulative walk),
  the group size `min + nextInt(1 + max - min)`.
- **The per-biome creature tables 1:1**: the default family (sheep 12/4-4,
  pig 10, chicken 10, cow 8 — the rabbit row ledgers with the rabbit
  slice), the desert's `passiveEntries.clear()`, the ocean's clear, the
  plains' horse row (5/2-6), the jungle's own SECOND chicken (the
  reference's own duplicate), the forest's wolf (ledgers with the wolf
  slice). The biome read at the chunk's +16 corner (the reference's own
  position).
- **The placement walk**: the 4 attempts per member, the surface height
  (the terrain's heights +1 in-chunk, the pure heightAt out-of-chunk —
  the Slice 12b deterministic contract), the ON_GROUND gate (solid
  ground, air cell, air above), the +0.5 centering, the yaw roll
  `nextFloat() * 360`.
- **The drift + the leash**: the reference's own `nextInt(5) -
  nextInt(5)` walk with the re-anchor loop (the k bound's rangeX quirk
  included — identical at 16 == 16).
- **The horse group's shared entitydata**
  (HorseBaseEntity.initialize lines 1262-1280): the first member rolls
  the subtype (1-in-10 the donkey) + the coat (`nextInt(7) | nextInt(5)
  << 8`), the pack shares the type + the color byte and re-rolls the
  marking, the baby is the per-member 1-in-5.
- **The pending-animal records** (`EngineChunk.PendingAnimal`): the
  generator records while the chunk is detached; the tick drain
  materializes exactly once (the villagers' own pattern) with the
  pack-cohesion idempotence guard (`mobOfKindNear`, radius 8 — the
  widened villagerNear).
- **The tick cycle's yaw roll** (the reference's setPositionAndAngles at
  line 109): the NaturalSpawnSystem's walk now rolls `nextFloat() * 360`
  with the spawn.
- **The horse constructor dice corrected to the reference's exact
  shapes** (the 1-in-10 donkey, the `nextInt(7) | nextInt(5) << 8` coat
  — replacing the approximate 1-in-5/60%-plain split).
- **The synthetic `populateInitial` REMOVED** (the fresh world's
  animals arrive through the vanilla path — the pregenerated spawn
  chunks' pending animals drain on the first tick).

**Tests**: `WorldgenAnimalSeedingTest` (5: the deterministic regeneration,
the spread across a 144-chunk land area with only passive kinds, the
placement invariants — the +0.5 centering, the yaw band, the world
bounds —, the horse group's shared dice (a 3+ horse chunk's consecutive
pair), the per-chunk stream independence). The natural-spawn fake's
signature updated (the yaw param). The fresh full-suite count **723
green in zamin-core** (+5), `--max-workers=1`, one projectile-test load
flake (the documented class, green in isolation).

**Next:** the remaining vanilla rows — the slime/enderman/witch spawn
rows (their entity slices), the dispenser's spawn eggs, or the §27 API
waves (the scheduler/task controls, the container/inventory surface).

## Slice 12i — the enderman (the natural-spawn row + the teleport family)

**The reference port** (`entity/living/mob/monster/EndermanEntity.java`):

- **The kind**: id 58, the 0.6x2.9 body, 40 health, the 0-1 ender pearl
  drop, the reference's own sound names. The per-kind attack damage
  landed with it (the flat 2.0 became the MobType field — the enderman's
  7.0, every other kind at the engine's easy-tier 2.0).
- **The three teleport walks**:
  - `teleportRandomly` (lines 167-174): the +-32-block cube.
  - `teleportTo` (lines 186-238): the drop-to-solid-ground walk, the
    body-clearance gate (the 2.9-tall body's own cells clear of solids
    and fluids), the position committed only on a clean landing.
  - `teleportToward` (lines 176-184): 16 blocks along the
    toward-target direction with the reference's own jitter bands (the
    `(random-0.5)*8` x/z and the `nextInt(16)-8` y).
- **The far-target gate** (the ChasePlayerGoal, line 425): a target
  beyond 16 blocks for 30 ticks blinks the body closer.
- **The projectile dodge** (takeDamage lines 299-309): the projectile
  NEVER lands — 64 teleport attempts instead, wired into the engine's
  ProjectileCombatSink.
- **The armor-bypass response** (lines 311-316): the magic/environment
  family (hurtWithoutKnockback) teleports 9 times in 10.
- **The water clock** (mobAiTick line 145): standing in fluid costs 1.0
  on the 10-tick cadence.
- **The daylight blink** (mobAiTick lines 153-159): the reference's own
  rate `nextFloat() * 30 < (brightness - 0.4) * 2` over the sky light
  (the WorldQuery grew the `skyLight` read; the stub worlds answer full
  bright).
- **The spawn row**: the monster table's own enderman row (10/1-4 —
  Biome line 289).

**The envelope notes** (all in the ledger row): the block-carry goal and
the stare-anger (the crosshair + scream arm) ride their own slices; the
teleport's portal particles + the sound ride the FX slice; the wire
carries the spawn through the existing MobEventDispatch (id 58).

**Tests**: `EndermanEntityTest` (6: the kind identity, the
drop-to-ground landing, the blocked-landing refusal keeping the body,
the random band, the toward-blink closing the distance with the reach,
the water clock's ~10-tick cadence with the exact 1.0 hits). The fresh
full-suite count **729 green in zamin-core** (+6), `--max-workers=1`,
one projectile-test load flake (the documented class, green in
isolation).

**Next:** the witch (the potion-thrower arm over the landed potion
system) and the slime (the size family + the split), then back to the
API waves.

## Slice 12j — the witch (the natural-spawn monster row)

**The audit**: the session resumed over an uncommitted Witch stub (the
kind row, the drink ladder, the ranged attack, the loot walk, an
uncompilable test). The reference re-read (`WitchEntity.java` 1:1) and
seven gaps found — including a real wire-level bug family in the Spawn
Object writer.

**The drink** (mobTick lines 87-141): the ladder now carries the
reference's own hasStatusEffect gates (water breathing / fire
resistance / speed — the rolls stay first, the short-circuit order
kept), the timer uses the reference's POST-decrement (the window spans
33 full ticks, the effects land on the 34th), and the completed drink
applies through `finishDrinking` (the same PotionItem math as the
player's — instants apply, the previous stub only installed them into
the store and the heal never landed). The gates read the LIVE effect
stores through a per-witch query the manager wires at spawn. The
7.5E-4 spell-swirl roll runs every tick (Entity Event 15).

**The movement stop**: the drinking speed penalty (the -0.25 modifier
on the 0.25 base — witches stand still while drinking) lands as the
walk gate.

**The throw** (doRangedAttack lines 194-215): the trajectory now
measures the aim triple from the witch's FEET (the old eye-measured d2
threw the arc 1.66 blocks low — the potions landed short), the spawn
seat is the reference's own ThrownEntity constructor (the eye 1.62,
the 0.1 dip, the 0.16 perpendicular hand offset), the weakness roll
draws from the mob's shared stream, and the bow sound is GONE (the
reference's witch throws silently).

**The combat rules** (getDamageAfterEffectsAndEnchantments lines
163-175): the witch's own splash never lands (attacker == self → 0)
and every magic source pays the 85% shave — the PotionSystem's hurt
arm grew the DamageSource shape (attacker + magic flag), the splash
carries the thrower end-to-end (ProjectileSink → the engine's resolver
→ the instant arm), and the witch's rules land in the engine's
PotionBodies. The poison's periodic tick pays the shave too (1.0 →
0.15) — exactly the reference's magic source.

**The drops** (dropItems lines 178-192): the reference's roll order
(count → item → looting bonus) and the per-unit single-item stacks
(the old walk merged them into one counted stack and skipped the item
roll on a zero count — both RNG-stream divergences).

**The wire family** (the audit's finds, all fixed):
- The potion's Spawn Object type was **68** — the guardian's MOB id;
  the wire truth is **73**, and the objectData carries the POTION
  VALUE (the client builds the bottle's color from it), not the
  thrower's id.
- The velocity shorts are now conditional on objectData > 0 (the
  packet's own read shape) — the dispensed fire charge's data-0 spawn
  previously wrote six trailing bytes the client would never read (a
  frame-corruption class).
- The snowball/egg pair sends bare (no data, no velocity — vanilla's
  own shape).
- **The block break animation sat at 0x28** — the Effect packet's
  slot. The reference's own NetworkProtocol registration order pins it
  at 0x25; the conformance table's row carried the same drift (its
  "community-verified" comment masked it). Both corrected.
- The world Effect packet (0x28) landed for the 2002 potion splash
  (the FxManager's PotionSplash event, fired at every splash
  resolution — the color-carrying burst, 64-block broadcast).
- The witch's drinking flag rides the index-21 metadata (the join
  view + the flip events).

**The spawn row**: the monster table's witch entry (5/1/1 — Biome
line 290, the default list).

**Tests**: `WitchEntityTest` (6: the kind identity, the heal through
the manager + potion system, the 33-tick window with the 34th-tick
completion, the stand-still, the live-store gate refusal, the
single-item drops) + `WitchAcceptanceTest` (3: the own-splash immunity
on a live booted server, the 0.9 shave on the 6.0 harming splash, the
0.15 poison tick — all through the tick-lane execute primitive). The
fresh full-suite count **738 green in zamin-core** (+9), plus the
protocol module's 71 (one documented load flake, green in isolation).

**Next:** the slime (the size family + the split-on-kill) — the last
default-list monster without an entity — then back to the API waves.

## Slice 12k — the slime (the natural-spawn monster row)

**The size family** (setSize 1:1): the per-instance size byte (1/2/4 —
the initialize's `1 << nextInt(3)`; the local-difficulty bump arm rides
the difficulty slice), the body recomputes riding it (the dimension
0.51000005*size — the reference's own float, the health size², the
movement speed 0.2+0.1*size feeding the hop, the XP and the contact
damage the size), the dynamic hurt/death sound (mob.slime.big over
size 1), and the eye height 0.625*height (the kind-specific share over
the generic 0.85).

**The hop movement** (the MovementControl model): the movement input is
ZERO between hops (the body coasts on the residual only), the launch at
velocityY 0.42 with the facing impulse at the speed attribute, the
cadence `nextInt(20) + 10` divided by three on the aggressive chase,
the swim arm's 80% hop at the 1.2 speed, and the direction re-roll
every 40+nextInt(60) ticks (nextInt(360)). The landing edge queues the
thud for size > 2 (the reference's makesLandSound); the jump squish
rides every hop at volume 0.4*size with the reference's own pitch
bands (×0.8 the jump, ÷0.8 the landing).

**The split** (remove 1:1): a death at size > 1 spawns 2-4 children at
half the size on the reference's (k%2-0.5)*size/4 grid half a block up,
each with its own yaw roll — DEFERRED to after the tick iteration (the
pending-breeds pattern; a mid-iteration add is the
ConcurrentModification trap).

**The contact damage** (onPlayerCollision + damageTargetEntity): the
big body within 0.6*size blocks with a clear line arms the melee
pipeline with the size damage on the player's own 10-tick i-frame
cadence; the mob.attack squish rides the same landing.

**The drops**: only the size-1 body carries the slime balls (the new
SLIME_BALL item, wire id 341); the split family drops nothing.

**The spawn row** (the reference's FULL default monster list is now
live): SLIME 100/4-4 + the entity-level canSpawn gate — the
slime-chunk arm (the shared nextInt(10), then the chunk's own
getRandomForSlime(987234911L) nextInt(10) — the seed derivation
`seed + cx*cx*4987142 + cx*5947611 + cz*cz*4392871 + cz*389711 ^
987234911L` 1:1, then the y < 40 depth gate) and the swampland band
(short-circuited at the biome check — the engine carries no swamp, so
the arm's rolls never consume RNG, exactly like a plains spawn in the
reference). The gate sits after the yaw roll, before the initialize —
the reference's own consult order.

**The wire**: the size byte rides the index-16 metadata on the join
view (the size never changes mid-life — the split spawns new bodies);
the hurt/death sounds route through the new per-body accessors.

**Tests**: `SlimeEntityTest` (8: the kind identity with the reference's
own float, the size-family recompute, the initialize roll, the death
split with the cascade down to size 1, the size-gated drops, the hop
cadence with the grounded rest, the contact damage's three arms — the
armed big body, the small refusal, the out-of-reach refusal — and the
seed derivation). The fresh full-suite count **746 green in zamin-core**
(+8) + the protocol module's 71, `--max-workers=1`, zero failures.

**Next:** back to the API waves (the §27.3 audit's next family), or the
rabbit/wolf/ocelot rows (their entity slices), or the brewing stand's
block entity + GUI.

## Slice 14a — the world & block API wave (§27.3)

**The family** (the §27.3 checklist's own first bullet — "world, chunk,
block, block-state, and world-generation operations"): five capabilities
through the public interfaces, no engine internals.

- **`World.setTimeOfDay(long)`**: the owner-context clock set (the
  floorMod wrap on the 24000-tick day; the per-world independence — the
  engine's `/time` walks every dimension, a plugin loops `worlds()`).
- **`World.blockLightAt` / `skyLightAt`**: the any-thread best-effort
  reads over the light engine's committed state (0 block / 15 sky
  outside loaded chunks — the engine's own documented totals).
- **`World.biomeAt`**: the column's stable family name (the read over
  the chunk's generation-derived biome row; "plains" outside loaded
  chunks — the implicit default).
- **`Server.difficulty()`**: the 1.8.8 difficulty id read (the set arm
  rides the difficulty slice).
- **`BlockChangedEvent`** (§27.7's post-commit observation): every
  committed block change — player, explosion, piston, fluid, growth —
  fires through the public bus, carrying the world name, the position
  and the complete old/new type pair. NOT cancellable by design (the
  commit already happened). The engine bridge: the `WorldChangeListener`
  grew the combined `onBlockReplaced(world, pos, old, new)` default
  (fired first inside the same commit, ignored by every existing
  listener), and the EngineServer registers its bridge on BOTH booted
  dimensions.

**The test fakes**: the seven anonymous World test fakes grew the four
new methods (the established pattern — the API stays abstract).

**Tests**: `WorldApiAcceptanceTest` (5: the noon landing + the 25000
wrap + the per-world independence, the glowstone source cell at 15,
the biome families + the unloaded default, the difficulty band, the
change event's pair both ways + the unsubscribe silence). The fresh
full-suite count **770 green across zamin-core + zamin-api** (746 + 5 new
+ the api module's own 19), `--max-workers=1`, zero failures.

## Slice 14c — the lifecycle & damage event wave (§27.7's second family)

The six new public events + the vanilla join/quit broadcasts:

- **`PlayerJoinEvent` / `PlayerQuitEvent`** (the §27.7 post-commit
  observations): the join fires on the joining connection's context after
  the session is fully constructed (registered, restored, positioned);
  the quit fires on the disconnect walk right before the registry drops
  the handle — handlers still see live state. Both carry the mutable
  vanilla broadcast line; `null` suppresses the delivery.
- **The vanilla yellow broadcasts themselves** (a fidelity gap the wave
  closed): `multiplayer.player.joined` / `multiplayer.player.left` in
  yellow, 1:1 with `PlayerManager.onLogin` lines 136-145 and
  `ServerPlayNetworkHandler.onDisconnect` lines 573-576. The reference
  broadcasts BEFORE `add(player)` — the joiner never sees their own
  line; the engine's early register skips the joiner the same way.
- **`PlayerDamageEvent`** (cancellable, mutable amount): gates EVERY
  player damage entry — `damageOnTick` grew the `DamageCause` parameter
  threaded through its whole overload family (void, in-fire, on-fire,
  cactus, starve, fall, drown, thorns, mob melee, the potion magic arm),
  `projectileHitPlayerOnTick` (PROJECTILE), and the explosion's own
  player walk (EXPLOSION, zero-motion on cancel). A cancelled hit lands
  nothing: no health loss, no exhaustion, no bruise window, no death.
  The amount is the post-armor, post-enchantment value.
- **`MobDamageEvent`** (cancellable, mutable amount, the live
  `LivingEntity` handle): `MobManager.hurt` grew the four-arg cause
  overload (melee callers default MELEE; the projectile site passes
  PROJECTILE; the explosion site EXPLOSION) and `hurtWithoutKnockback`
  reads MAGIC. A cancelled hit answers the refused-takeDamage verdict —
  no health, no knockback, no listeners. The enderman's teleport dodge
  now sits behind the gate too.
- **`PlayerDeathEvent`** (cancellable = keepInventory): fires in
  `dieOnTick` after the body dies (effects cleared, seat open) but
  BEFORE the scatter — a cancelled event keeps the experience and the
  whole inventory on the body; the death itself still resolves. The
  death line is mutable (the broadcast and the log read it).
- **`MobDeathEvent`** (observation): fires at `MobEventDispatch.onMobDied`
  before the XP release — the kill-counter point, not cancellable.
- **`event.DamageCause`**: the public damage-source classification —
  the engine's own `DamageKind` constants verbatim (the reference's
  `DamageSource` predicates) plus `MAGIC` (the potion family's arm).

Protocol-suite adaptation: the shared-server chat reads now meet the
join broadcasts, so `TestClient18` grew
`readChatLineSkippingBroadcasts` (drains the yellow join/left lines)
and the two-recipient command test reads through it.

Fresh full-suite count: **764 core + 19 api + 71 protocol = 854 green**,
`--max-workers=1`, zero failures.

## Slice 14d — the scheduler & diagnostics wave (§27.3's task controls)

- **`Server.scheduler()`** (`net.zaminmc.torch.scheduler.Scheduler`): the
  time dimension over the owner-context execution — `runNextTick`,
  `runLater(delayTicks)`, `runRepeating(delay, interval)`, each returning
  the cancellable `Task` handle. The ticker grew the due-time queue
  (a `ConcurrentSkipListMap<due tick, tasks>` + the head-of-tick drain):
  submissions from any thread compute the due tick against the
  completed-tick view (a volatile written when `logicalTick` advances),
  so a delay of N lands at the Nth-next drain; due bodies run in
  due-then-submission order on the owner's context BEFORE the pending
  work drain; one throwing body is logged and skipped (the tick's own
  policy), a repeating body still re-arms past the in-flight tick.
  The immediate lane keeps its own admission (`World.execute` /
  `ticker.submit` — the §27.6 primitive stays untouched).
- **`Server.tps(int windowSeconds)`**: the public TPS read over the
  ticker's rolling second-window (the Paper 1m/5m/15m windows), any
  thread, 20.0 before enough data.
- Hopper cadence test hardened (a pre-existing flake on a loaded box,
  reproduced WITHOUT the change): the frozen-sample window became the
  observed-transfer-gap assertion (>= 300ms observed between the 2nd
  and 3rd transfers proves the 8-tick cooldown while tolerating
  observation latency).

Fresh suite: **769 core + 19 api green** (788 total), protocol 71
(checked in the same session), `--max-workers=1`.

## Slice 12l — the rabbit (the natural-spawn animal + the entity family)

- **The items** (Item.java lines 715-719): rabbit 411 FoodItem(3, 0.3F),
  cooked_rabbit 412 FoodItem(5, 0.6F), rabbit_stew 413 FoodItem(10, 0.6F)
  with the stack-1 bowl rule, rabbit_hide 415 the plain material (the
  foot 414 already lived for brewing). The Foods table carries the
  nutrition (3/1.8, 5/6.0, 10/12.0 — the dataset saturation fold).
- **The kind**: MobType.RABBIT (101, the 0.6x0.7 body, 10 health, the
  animal walk band; the reference's own mob.rabbit.* sound trio).
- **The hop movement** (RabbitEntity's Action model over the engine's
  zeroed-input lane, the slime's pattern): between hops the body is
  still; on the HOP cadence (jumpDelay 10) the body launches — the
  facing impulse at walkSpeed x 1.2 (the reference's setSpeed(1.5 x
  actionSpeed)) and the 0.2 strength rise — with the hop sound at the
  reference's 0.8-centered pitch band; the idle rabbit sits (the NONE
  action); in fluid the 80% swim arm launches.
- **The breeding family** (isTempting): the carrot, the golden carrot
  and the dandelion — the breedingItemOf single-item table became the
  isBreedingItem(kind, held) multi-item gate (the rabbit is the first
  multi-food animal).
- **The spawn rows**: the base Biome passive list's rabbit 10/3-3 landed
  in the worldgen creature tables (jungle/plains/forest-default — the
  reference's own base-list order, sheep first) and the natural CREATURE
  table.
- Ledgered: the killer-bunny variant family (skin 99: the 8 damage, the
  8 armor, the player-hunting goals — the index-18 variant byte rides a
  future spawn-egg/command surface), the RaidFarmGoal (the carrot-crop
  eating — no carrot crop block yet), the flee-wolf goal (no wolves
  yet), the 2.5% rabbit-foot rare drop (the rare-loot-table slice), the
  natural variant roll nextInt(6) (every engine rabbit renders brown
  until the variant byte carries).

Fresh suite: **792 green (773 core + 19 api)**, `--max-workers=1`, zero
failures; the protocol module carries no wire change this slice (71 from
the same session's earlier run).

## Slice 15a — the AI-goal registration surface (§27.3's mob-AI family)

**Scope**: the first of the three remaining §27.3 waves the last session
named — the goal mind's public registration surface. A plugin-facing
`Goal` contract and `GoalRegistration` handle now live in `zamin-api`
(`net.zaminmc.torch.entity.ai`), carried by `LivingEntity.goals()`.

**The public shape**:
- `Goal` — priority, exclusive, `canUse(LivingEntity, night)`,
  `tick(LivingEntity, night)`: stateless decisions over the live handle
  (the engine object IS the handle, §27.5 — the adapter casts, never
  copies). Runs on the owner lane beside the built-ins; a throwing body
  isolates per the mind's own policy.
- `GoalRegistration` — `register` (the goal's own priority band, stable
  insertion order within a band), `goals()` (the identity-preserving
  snapshot: customs come back BY IDENTITY; built-ins dress as per-call
  views that also PROBE willingness — §27.3's "querying intermediate
  decisions"), `remove(Goal)` (identity match over customs and built-in
  views alike), `removeBuiltIn(id)` / `restoreBuiltIn(id)` (the stable
  ids "float", "panic", "breed", "stroll" — the compose/replace verb and
  its way back).

**The ownership protocol (§27.6, the honest part)**: every mutation
admits onto the owner lane — `MobManager` wires the world's `execute` into
each spawned body (`setGoalAdmission`), so registrations land between
ticks from ANY thread, never inside the selector's walk (a mutation
requested from inside a goal body re-queues at the next admission
window). The verbs deliberately carry NO synchronous verdict — the
mutation has not run when they return — and `goals()` is the
confirmation channel. No blocking handshakes, no thread-name checks, no
deadlock window. Bare harnesses (no admission wired) mutate inline under
the caller's own context, matching the entity family's documented
contract.

**Engine shape**: `GoalSelector` became the registry — the built-in family
plus registered customs in ONE priority-ordered list, the built-ins
parking restorable on removal (both the stable-id path AND the view
path), the tick walk unchanged (same-order scan, nothing mutates
mid-iteration). `MobGoal` grew `goalId()` for the stable addressing; the
built-ins carry their ids. Two thin adapters bridge the type gap
(`PluginGoalAdapter` public→engine, `BuiltinGoalView` engine→public).

**Tests**: `GoalApiAcceptanceTest` (4) — the registered goal ticks on the
owner lane (`zamin-tick` captured from a foreign thread's registration);
the stroll's exclusive claim blocks lower bands until removed by id and
re-blocks after restore; the gated exclusive custom claims the band below
it (open gate freezes the counter, close resumes, remove-by-identity ends
it); the snapshot addresses built-ins by view (probe + remove + the
stable-id restore reaching the view-removal path too — the restorable
parking fixed for BOTH removal roads).

Fresh suite: **867 green (777 core + 19 api + 71 protocol)**,
`--max-workers=1`, zero failures.

## Slice 15b — the dispenser behavior registry (§27.3's dispenser family)

**Scope**: the second of the three §27.3 waves — the per-item dispense
behavior table's public registration surface, the stand-in for the
reference's `Bootstrap.registerDispenseBehaviors`.

**The public shape** (`zamin-api`):
- `block.Direction` — the six facings in the historical 0-5 id order with
  offset accessors, `opposite`, and `byId` (a genuinely public facing
  type; the engine tables address by id).
- `dispenser.DispenseBehavior` — one use of the picked stack; answers the
  slot's remainder (empty = consumed). Runs on the owner lane inside the
  dispense walk; a throwing behavior is logged and the stack stays.
- `dispenser.DispenseContext` — the live dispense: `world()` (the public
  World handle — placing a block through it is exactly what the built-in
  fire arm does), `origin()`, `facing()` (the Direction), `stack()`, and
  the derived `facingCell()`.
- `dispenser.DispenseBehaviors` — `register(ItemType, DispenseBehavior)`
  (a custom overrides the built-in switch for that item, or replaces the
  default drop where none answered), `reset(ItemType)` (the way back to
  the built-in answer), `behaviorOf` (the custom-confirmation read;
  empty when the built-in/default owns the item).
- `Server.dispenserBehaviors()` — the mount.

**The ownership protocol**: register/reset admit onto the world's owner
lane (the `execute` admission the system already holds) — safe from any
thread, landing between ticks, never inside a dispense walk; the verbs
carry no synchronous verdict and `behaviorOf` confirms (the same
command-protocol shape as the goal surface, Slice 15a).

**Engine**: `DispenserSystem.behaviorOf` consults the customs table
before the built-in switch; the customs ride a concurrent registry whose
mutation authority is the owner lane (the admission — the map carries the
point-in-time read contract, not the ownership); the public surface
(`DispenserBehaviorsSurface`) builds the live context per dispense.

**Tests**: `DispenseBehaviorApiAcceptanceTest` (2) — the custom behavior
receives the live context on the owner lane (origin/facing/facing
cell/stack/world name, the empty remainder consumed the slot, registered
from a foreign thread), and the reset returns the built-in arrow arm
(the projectile launches again, behaviorOf empty). One load flake
(RangedCombatAcceptanceTest's arrow-wound await) reproduced once with the
changes and once cleanly on HEAD — green forced and in isolation both
ways; the documented load-sensitivity class, not a regression.

Fresh suite: **798 green (779 core + 19 api)**, `--max-workers=1
--rerun-tasks`, zero failures; protocol unchanged this slice (71).

## Slice 15c — the packet interception hooks (§27.3's protocol family)

**Scope**: the third of the §27.3 waves — the outbound packet hooks:
observe, cancel, and re-payload the active protocol version's frames,
verified against a real wire.

**The public shape** (`zamin-api`, `net.zaminmc.torch.protocol`):
- `OutboundPacket` — the callback-scoped handle: the recipient `Player`,
  the `packetId` in the ACTIVE version's table (protocol 47 here — the
  deliberate version-shaped surface of §27.8), a payload COPY (the wire's
  own bytes never leak), `cancel()`, and `payload(byte[])` — the
  re-payload verb keeps the id (a listener never rewrites a frame's
  identity; a malformed replacement is the replacer's own protocol error).
- `PacketListener` — runs on the recipient's WIRE thread (registration
  order, exception-isolated; no blocking, no live-state writes —
  simulation work goes through `World.execute`).
- `ProtocolHooks` — addListener/removeListener (any thread) + the live
  `listeners()` snapshot; mounted at `Server.protocolHooks()`.

**The interception point**: one Netty `PacketHookOutboundHandler` per
connection, installed `addAfter` the frame encoder — between the
connection's writers and the length-prefixing, so the walk sees the RAW
wire form (varint id + payload). The id varint is parsed non-destructively
(its byte length drives both the payload slice and the rebuild); a
cancelled frame is released and never reaches the socket; a replaced
payload is rebuilt with the SAME id varint verbatim. Pre-login frames
(status/login) ride untouched — the documented scope: gameplay traffic;
connection lifecycle observes through the join/quit events.

**Engine**: `PacketHookRegistry` (the listener registry — COW list, the
honest carrier for a rare-mutation any-thread table iterated on wire
threads; the ownership stays with the listeners' own §27.6 contracts),
mounted on EngineServer; `engine.protocolHooks()` casts to the registry
for the handler's fire.

**Debug war stories**: the first placement (`addBefore` the encoder) saw
the LENGTH-PREFIXED frames — the wire's own ids read as garbage (a
12KB chunk read as "packet 12558"); `addAfter` is the tail-side seat.
The scripted test clients also cannot survive the shared base's 250ms
keep-alive (the ×3 factor kicks silent connections at 750ms) — this
suite overrides `keepAliveIntervalMs()` per the base's own law.

**Tests**: `PacketHookApiTest` (3, over the real wire) — the listener
observes the chat frame (the id, the recipient, the payload) from a
foreign-thread registration; the cancelled frame never reaches the wire
(1.5s silent window on the watcher's stream); the replaced payload rides
(the original is gone, the rewritten JSON arrives). The full-suite pass
carried one SurvivalFlowIntegrationTest flake — green in isolation and
on re-run, the protocol module's documented load class.

Fresh suite: **872 green (779 core + 19 api + 74 protocol)**,
`--max-workers=1`, the one flake re-verified green.

## Slice 15d — the wolf (WolfEntity 1:1, the tameable family)

**Scope**: the tamer's companion — the kind, the tame interaction with
its full state flip, the wire block, the breed gates, the despawn rules,
and the forest's worldgen row. The combat-goal family (the untamed
hunts-sheep/rabbits, the attack-with-owner, the skeleton feud, the beg
goal's held-item gaze) ledgers below with its own wave — the mob-vs-mob
target selection doesn't exist in the engine yet.

**The kind**: WOLF 95 (the community dataset), 0.6x0.8 (setSize), 8
health wild / 20 tamed (initAttributes' own branch + setTamed's
attribute recompute), movement 0.3 (the chaser band — the enderman's
twin reference attribute), hostile=false, no loot (Item.byId(-1)), the
mob.wolf.* sound trio (the idle's dynamic bark/whine/growl walk rides
the ambient-sound slice).

**The state family** (MobEntity): the index-16 state byte (sitting 0x1 |
angry 0x2 | tamed 0x4 — TameableEntity's bits + the wolf's own), the
collar dye byte (index 20, RED at spawn — the reference's default), the
begging flag (index 19), the per-instance attack damage (4.0 tamed over
the 2.0 wild default — the melee walk's read), the tamed max-health
override (heal clamps at 20 not 8), and the wolf's own tick clock with
mobTick's angry-clear (a targetless angry wolf stands down).

**The tame** (interactWolfOnTick, the reference's interactMob ladder):
the untamed body takes the bone (the creative hand spares the stack) and
rolls the 1-in-3 — the win flips the whole body (the flag, the owner, the
20 health, the 4.0 damage, the sitting start, the Entity Status 7
hearts) or smokes (6); the tamed body takes the meat heal under 20
(the canBeCooked family: the porkchop/beef/rabbit 3, the cooked pair 8/5,
the chicken pair 2/6), then the owner's empty hand toggles the sit (a
held breeding food feeds instead — the reference's own gate); the
breeding falls through to the shared animal feed — the wolf's
isBreedingItem is the meat family, and canBreedWith gates BOTH bodies
tamed and NEITHER sitting.

**The wire**: the join view's wolf block (state byte 16, owner string
17, health float 18 — the tail-wag read, begging 19, collar 20) + the
Set Entity Metadata writer (sendWolfState) riding the new
onWolfStateChanged listener, and the taming verdict's Entity Status
(7/6) through the new onMobStatusEvent listener — the same dispatch
shape the love burst rides.

**The rules**: persistent() covers the tamed wolf (never leaves);
canDespawn() — the new per-mob despawn gate — answers !tamed && ticks >
2400 (the wolf's own canDespawn override; the shared call site moved
off type().hostile). The worldgen: FOREST split out of the default
family with the ForestBiome variant-0 wolf row 5/4-4 (the taiga's
8/4-4 row has no taiga biome yet — ledgered).

**Ledgered for their own waves**: the untamed hunts-sheep/rabbits +
attack-with-owner + revenge + skeleton feud (the mob-combat-target
wave), the WolfBegGoal (the held-item gaze needs a player-inventory
query on the mind's WorldQuery), the water-shake cycle (the particle
system), the collar dye arm (the dye item family — only bone meal
registers today), the non-player non-arrow (amount+1)/2 damage shave
(the attacker identity doesn't reach the hurt walk yet), the sheep's
color pick + the ocelot's creep-flee (their slices), and the tamed
state's persistence (the ZMD v2 wave with the horse's flags).

**Tests**: WolfEntityTest (6: the kind identity, the tame's full flip,
the tamed ceiling heal, the sit toggle + the angry clear, the breed
gates' tamed-and-standing law, the despawn pair). The interact ladder
rides a protocol acceptance follow-up with the ocelot's (the tame roll
needs the live window; the unit seam is the mob's own state).

Fresh suite: **878 green (785 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 15e — the ocelot (OcelotEntity 1:1, the jungle cat)

**Scope**: the second tameable — the fish tame with the coat roll, the
shared tameable state family (the wolf's bits generalized), the wire
block, the breed gates, the jungle worldgen row, the despawn pair.

**The kind**: OCELOT 98, 0.6x0.7, 10 health (NO tame flip — the wolf's
20 is its own; the cat keeps whatever it had), movement 0.3 (the chaser
band), the 3.0 melee (tryDamage's own override), silent untamed (""), the
mob.cat.hitt pair, no drops (dropItems' empty override).

**The tame** (interactOcelotOnTick, the reference's interactMob): the raw
fish inside the 3-block reach (squaredDistanceTo < 9 — the reference's
own gate), the 1-in-3 roll flipping the flag + the coat byte (1 +
nextInt(3): the tamed coats) + the owner + the sitting start, with the
Entity Status 7/6 verdicts; the tamed body takes the owner's empty-hand
sit toggle; the raw-fish breeding falls through to the shared feed. The
tempt-gate (the slow approach) and the creep-flee family ledger with the
player-velocity wave — the held-item gaze's own ledger (the wolf's beg).

**The shared family**: the wolf's index-16 bits + owner + the 2400-tick
clock generalized to isTameableKind() (persistent when tamed; canDespawn
= !tamed && ticks > 2400; canBreedWith's both-tamed-and-standing gates).

**The wire**: the ocelot's own block (state byte 16, owner string 17,
coat byte 18 — the wolf's 18 is health, the ocelot's is the coat) in both
the join view and the shared sendWolfState (the kind picks the writer).

**Tests**: OcelotEntityTest (4: the kind identity, the fish tame's
no-health-flip state law, the breed gates + the wolf cross-kind refusal,
the despawn pair).

Fresh suite: **882 green (789 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 16a — the mind's awareness (PlayerView + the ocelot's tempt/creep-flee + the wolf's beg)

**Scope**: the player-velocity/held-item awareness wave the 15d/15e
ledgers named: the WorldQuery's `PlayerView` (the nearest live player's
position, rotation, and held item), the TEMPT goal, the FLEE_PLAYER
goal, the BEG goal, the shared-flags postures on the wire, and the
tame's own tempt gate.

**The view**: `MobEntity.PlayerView` — position/yaw/pitch/heldItem —
with `WorldQuery.nearestPlayerView(x, y, z, range)` defaulting to null
(the stub worlds keep their no-player semantics). The engine's
`MobWorldQuery` answers it from the session registry (the same nearest
walk `nearestPlayer` runs).

**TEMPT** ("tempt", band 3, exclusive): TemptGoal 1:1 — the fresh start
takes the NEAREST player within 10 holding the raw fish (not the nearest
fish-holder), the walk at 0.6 over the 0.3 base (the new goal-walk-speed
multiplier the body's walk reads), the 2.5-block hold with the cleared
path, the scared check (within 6 blocks: no move beyond 0.01, no turn
beyond 5 degrees — the position sample updates only beyond that band,
the rotation sample every tick), the 100-tick stop penalty.

**FLEE_PLAYER** ("flee_player", band 3, exclusive): FleeEntityGoal 1:1
— any live player within 16 sends the UNTAMED cat the other way: 0.8 far,
1.33 inside 7 blocks, the heading directly away, the PANIC walk.

**BEG** ("beg", band 3, ADDITIVE — the LOOK control only): WolfBegGoal
1:1 — the nearest player within 8 holding a bone (untamed) or the meat
family holds the gaze over its own 40+nextInt(40) window; the index-19
flag rides the manager's wolf-state flip detection (the whole block
republishes); the stroll's glance yields while begging.

**The postures**: the tempt walk's 0.6 sets the shared-flags SNEAK bit
(0x02), the flee close's 1.33 the SPRINT bit (0x10) — both through the
living-flags byte at index 0 (the extended spawn/living-flags writers +
the manager's posture flip event → `sendMobLivingFlags`).

**The tame gate**: the ocelot's fish arm now requires the live tempt
state (`interactMob`'s own gate — a cat that never crept takes no fish).

**Tests**: TemptBegGoalsTest (7: the follow + the creep posture, the
scared check with the 100-tick penalty, the nearest-only gate, the flee
with the close sprint + the tamed refusal, the beg window + the family
gates, the selector's band-3 law, the kind gates). GoalApiAcceptanceTest
updated for the seven-built-in snapshot.

Fresh suite: **889 green (796 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 16b — the mob-combat-target family (the wolf's hunts, the feud, the revenge and owner arms)

**Scope**: the targetSelector's single-target state over the wolf and the
zombie — the mob-vs-mob hunts, the skeleton feud with the skeleton's own
flee, RevengeGoal with the call-others, AttackWithOwnerGoal +
OwnerHurtGoal, and the wolf's takeDamage shave.

**The state**: `mobAttackTarget` on the body — the reference's
setAttackTarget. The hunt scan (ActiveTargetGoal's 1-in-10 cadence, the
32-block follow range) updates BEFORE the goal walk so the stroll's
yielding gate (goalHasTarget) sees the live target; the chase mind runs
against its position after the goal walk; the melee consume damages IT
(hurtByMob) instead of the nearest player. The wolf's angry posture
follows the write; the target's death stands the anger down.

**The hunts**: the wolf's sheep/rabbits (UNTAMED —
UntamedActiveTargetGoal) and the always-on skeleton feud; the zombie's
villagers. The skeleton's flee-from-wolves: a wolf within 6 blocks owns
the movement (the 1.0/1.2 speeds over the 0.25 base), computed before
the goal walk.

**The arms**: RevengeGoal — a hostile kind or a wolf hit by a mob targets
its attacker, the wolf and the zombie calling their nearby targetless
kin (the call-others family); the owner arms as event-site joins — the
player's STANDING tamed wolves (the sitting pet keeps its seat) target
the mob their owner attacked (the player-melee site) and the mob that
attacked their owner (the onMobAttackedPlayer site).

**The shave**: the manager's hurtByMob applies the wolf's
(amount + 1) / 2 on a mob attacker before the walk; the sheep takes the
whole hit; the revenge fires inside.

**Tests**: MobTargetFamilyTest (8: the untamed hunt with the tame gate,
the feud's precedence + the death clear, the skeleton flee, the zombie's
villager hunt, the posture law, the shave + the revenge through the real
manager, the owner-arm gates, the follow-range bound).

Fresh suite: **897 green (804 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 16c — the nether's monster pair (MagmaCubeEntity + GhastEntity)

**Scope**: the magma cube over the generalized slime family, the ghast's
flight + the charged fireball cycle, the large-fireball projectile with
its detonation arm, the fire-immune family, and the fix for the wire
rows.

**The magma cube**: the slime family's type checks generalized to
`slimeFamily()` (the size system, the split with own-kind children, the
contact damage, the initialize roll, the index-16 size byte) with the
magma's own overrides — the 4x hop cadence, the 0.42 + size*0.1 jump,
the +2 damage, the always-on landing sound, the mob.magmacube pair, the
`nextInt(4) - 2` magma-cream drop (the size-1 gate).

**The ghast**: the flight body (no gravity, the ±16 targets, the 0.1
bursts on the 2-7 cadence with the LOS gate, the 0.91 drag, the per-axis
collision integration), the LookAroundGoal, the ShootFireballGoal cycle
(the 20-tick charge, the 1007 at 10, the launch at 20 with the -40
cooldown, the index-16 charging byte), the fireball's kind (Spawn Object
64, the 0.4-sigma jitter, the flat 6.0, the ExplosionSink wired to the
boundary-scoped detonateAt at 8*power+1, the fire placement).

**The fix**: the magma-cream/ghast-tear wire rows already existed (the
brewing slice) — the duplicates threw at LegacyBlockIds' static init and
killed every join flow (the failure mode is a join timeout, not a
compile error). Removed; the protocol suite is green again.

**Tests**: NetherMonsterTest (4).

Fresh suite: **901 green (808 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 16d — the zombie pigman + the nether's natural spawn cycle

**Scope**: the anger model (the group aggro), the neutral hunt gate, the
drop pair, and the nether's own spawn cycle with the HellBiome rows.

**The anger**: `becomeAngryTo(player)` — the 400+nextInt(400) window, the
delayed angry sound (volume*2, the 1.8 band), the attacker's name; the
manager's `pigmenBecomeAngryAt` — the hurt body + every calm pigman
within 32 blocks anger together (the already-angry keep their own
windows); the landing site — the player's melee on the pigman. The hunt
gate: the pigman's mind hunts players only while angry; the +0.05 boost
as the 1.34x chase multiplier.

**The nether's cycle**: the second NaturalSpawnSystem with the nether
flag (the HellBiome monster rows, everything else empty), the
SpawnWorldQuery parameterized per dimension (the nether's sky light 0,
the floor scan below the bedrock roof), and the tick feeding it the
nether players only.

**Tests**: ZombiePigmanTest (4: the identity, the window's roll + decay,
the delayed sound, the group anger through the manager).

Fresh suite: **905 green (812 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 16e — the squid and the bat (the ambient/water categories)

**Scope**: the two remaining spawn categories — SquidEntity's swim and
BatEntity's roost cycle — plus the category buckets.

**The squid**: the tentacle-cycle swim (the 2π stroke phasing the drive,
the 1-in-50 direction target, the direct velocity set), the stranded
sink, the ink-sac drop (the dye family's first wire member), the
y 45-seaLevel in-water spawn gate.

**The bat**: the roosting flag (the index-16 byte), the ceiling hold
with the player-within-4 wake, the hangPos wander flight with the
signum-band lerp, the 1-in-100 re-roost, the ceiling snap.

**The categories**: AMBIENT (bat 10/8-8) + WATER_CREATURE (squid
10/4-4) with their caps (15/5); countByCategory's four buckets; the
custom minds (ghast/squid/bat) now preempt the goal layer — their
reference goal sets carry no shared goals. The NetherMonsterTest's
gravity check hardened (the flight's random target can point down —
the assertion now pins the no-gravity bound, |vy| < 1.5).

**Tests**: SquidAndBatTest (3); the spawn tests updated for the ambient
bucket (the bats spawn on the always-on cadence — MobCategory.AMBIENT
is not rare).

Fresh suite: **908 green (815 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 17a — the guardian (the ocean monument's hunter)

**Scope**: GuardianEntity 1:1 — the laser's charge cycle with the wire's
beam pair, the still-body spike reflect, the elder variant with the
mining-fatigue aura, the monument spawn rows, and the monument's own
three elders.

**The kind** (68, the 0.85x0.85/30-health body, movement 0.5, the 6.0
melee, xpDrop 10): the wire's guardian block — the index-16 flag Int
(the swim's moving bit 0x02, the elder bit 0x04) and the index-17
beam-target Int (the client's laser draw), both in the spawn metadata
and the Set Entity Metadata writer the manager's flip detection drives.

**The laser** (GuardianAttackEntityGoal 1:1): the -10 start offset, the
beam arming at charge 0 (the target's id on the wire + the Entity Event
21), the hit at the 80-tick charge time — the magic half (1.0 + the
elder's 2.0 + the hard difficulty's 2.0) piercing armor with no
knockback, then the melee half through the full armor/enchantment
ladder; the look held on the target every charge tick; the hunt's break
(target lost, no line, inside the 3-block flop band) clearing the beam.

**The thorns** (takeDamage's spike reflect): a still body hit by the
melee family wounds the attacker 2.0 with the `damage.thorns` sound at
0.5 — a swimming body never spikes (the moving bit gates it).

**The body**: the sinusoidal swim (the movement control's own
`sin((ticks+id)*0.5)*0.05` drift + the `*0.75` vertical arm, the
0.1-scale steering with the 0.9 decay, the idle -0.005 sink, the
seabed rest, the water-favor steer re-roll on a dry cell —
WaterPathNavigation's stand-in) and the land flop (the grounded +0.5
rise with the ±0.4 horizontal and the random yaw between the gravity
falls, the ground snap).

**The elder** (setElder): the 1.9975 hull, the 80-health ceiling
healed at the flip, the 8.0 melee, the 0.3 speed, the persistent
never-despawn rule, the 60-tick charge. The aura (mobAiTick's
`(ticks+id)%1200==0` cadence): the survival players within 50 blocks
take the Fatigue III / 6000-tick curse (the weaker rows only —
amplifier < 2 or duration < 1200), each preceded by the Change Game
State 10 ghost-curse overlay (the ClientLink's new playGameState
seam).

**The monument integration**: the spawn entries' monument arm (the
guardian 1/2-4 inside the bounds — the entry pick resolving per
position through the new WorldQuery.insideMonument, the deterministic
nine-cell pick-chunk derivation with the horizontal box filter before
the biome gates; the IN_WATER cell rule — the liquid cell with the
liquid below and the non-solid above, no light gate; the guardian's own
1-in-20 open-sky canSpawn roll) and the monument's three elders (the
Penthouse at (6,1,6) + the two WingRoom designs at (11,2,16)/(11,5,13)
through the pending-record drain with the restart idempotence guard).

**The drops** (dropItems 1:1): the prismarine shards'
`nextInt(3)+nextInt(1+looting)` single stack, the fish-else-crystals
`nextInt(3+looting)>1` pair, the elder's wet sponge (the stack damage
field carries the variant) on the player-kill arm.

**Tests**: GuardianEntityTest (7: the identity + the mid-body eye, the
charge cycle with the beam verdicts, the flop-band gate, the elder
flip, the spike verdict, the swim + the flop bodies, the hunt-break
clear).

Fresh suite: **915 green (822 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 17b — the mooshroom (the mushroom cow)

**Scope**: MooshroomEntity over the cow — the bowl-to-stew interaction,
the shear-into-a-cow conversion, the wire rows for the bowl pair.

**The kind** (96, the cow's 0.9x1.3/10-health body, the wheat breeding,
the leather + raw beef loot, the cow's sound family): the child breeds
its own kind (makeChild), the mycelium spawn floor ledgered with the
mushroom-biome slice (the engine's biome family carries no mushroom
island).

**The interactions** (interactMob's own ladder before the wheat arm):
the bowl fills with the mushroom stew (the adult only — a single bowl
swaps in the hand, a stacked one picks up the stew and consumes one
bowl, the creative hand keeps the stack), and the shears shear it INTO
a plain cow — the body replaces itself at the same position and health
(the new setHealthCarried clamp), five red mushrooms pop, the tool
wears one use, and the `mob.sheep.shear` sound lands.

**The wire**: the bowl 281 and the mushroom stew 282 (the stack-1
FoodItem(6, 0.6F) with the 7.2 saturation fold).

**Tests**: MooshroomEntityTest (3: the identity, the conversion's health
carry with the clamp, the baby's own kind).

Fresh suite: **918 green (825 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 17c — the endermite + the ender pearl (the pearl's passenger)

**Scope**: EndermiteEntity + the EnderPearlEntity's whole throw/teleport
cycle — the projectile kind, the 5% passenger roll, and the 5.0 fall toll.

**The endermite** (67, the 0.4x0.3/8-health body, movement 0.25, the 2.0
melee, xpDrop 3, the silverfish sound family, the flat 0.1 eye): the
ARTHROPOD damage category (Bane of Arthropods lands on it — the fix
carried the cave spider and the silverfish into the category too, the
reference's own MobType.ARTHROPOD), no drops, no natural spawn rows (the
reference agrees: the pearl and the spawn egg are the only sources), and
the lifetime despawn — the non-persistent body removes itself at the
2400-tick wall through the manager's expiry walk (no death animation, no
loot, the reference's own remove()).

**The pearl**: the ENDER_PEARL projectile kind (Spawn Object 65 with the
thrower's id as objectData), the press-time throw (the stack-16 unit
leaves the hand, the shard's own whoosh recipe), and the landing — the
thrower's teleport to the last crossed air cell with the grace window +
the fall-distance reset + the flat 5.0 fall toll (the enchantment walk
only, the vanilla FALL source's bypass-armor exemption), the 5%
player-spawned endermite at the thrower's PRE-teleport feet, the owner
pass-through on the entity hit (the pearl keeps flying — the
pearl-boost trick's own rule), and the struck-body 0.0 bruise.

**Tests**: EndermiteEntityTest (3: the identity + the category family,
the flat eye, the lifetime wall's persistent gate).

Fresh suite: **921 green (828 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures (one documented HorseIntegration load
flake, green on the isolation re-run).

## Slice 17d — the golem pair (the pumpkin's builders)

**Scope**: IronGolemEntity + SnowGolemEntity + the five build blocks +
the pumpkin's build patterns.

**The blocks**: the snow pair (the full block 80 with the 4-snowball
drop, the thin layer 78 — a walk-through ground cover), the iron block
(42, the stone-tier rock), and the pumpkin pair (86/91 — the jack
o'lantern's famous 15 emission), all with their behavior rows, light
rows, wire ids, and item forms.

**The iron golem** (99, the 1.4x2.9/100-health body, movement 0.25):
the reverse hunt — the nearest hostile mob within 32 (GoToEntityTarget
Goal's range) owns the walk and the melee, the body never hunting
players (the canAttack gate); the heavy hit ({@code 7 + nextInt(15)},
tryDamage line 202) with the landed-launch uppercut (velocityY += 0.4)
through the manager's hurtByMob pipeline, the Entity Event 4 swing-up
with the {@code mob.irongolem.throw} sound; the drops (the 0-2 poppies
+ the 3-5 iron ingots, per-unit stacks).

**The snow golem** (97, the 0.7x1.9/4-health body, the 1.7 flat eye):
the 20-tick snowball volley at the hostile kinds within 10 blocks (the
reference's own dispense aim — the eye band pulled to the 1.1 mark with
the horizontal-distance rise, the 1.6 speed), the melts (the water
contact's 1.0 per tick, the hot biome's — the engine's desert — the
same), and the 0-15 snowball drops.

**The build**: the pumpkin placement (the canBePlaced solid-top gate)
runs trySpawnGolem — the snow column (two snow blocks under the head)
builds the snow golem at the bottom cell, the iron cross (the 3-wide
chest row + the base with the ~ air guards, BOTH horizontal axes —
BlockPattern.find's rotations) builds the player-created iron golem
(persistent, never despawns) at the base's center, the body blocks
clearing to air on the match, both bodies landing at the +0.05 hop.

**Tests**: GolemEntityTest (4: the identities, the heavy band + the
launch, the hostile hunt, the volley + the melts) + the registry count
pin (174).

Fresh suite: **925 green (832 core + 19 api + 74 protocol)**,
`--max-workers=1` — two documented load flakes (the hopper cadence and
the mob-integration clock, both green on the isolation re-runs).

## Slice 18a — the blaze (the fortress's fire)

**Scope**: BlazeEntity 1:1 + the fortress's own spawn overlay rows.

**The kind** (61, the 0.6x1.8/20-health fire-immune hull): the 6.0
ATTACK_DAMAGE, the xpDrop 10, and the drop correction — the
`nextInt(2 + looting)` single BLAZE RODs (the item 369 + its wire row;
the pre-18a row carried BLAZE_POWDER, the reference drops the rod).

**The AttackGoal 1:1**: the step cycle's exact shape — the 60-tick
charge (the fire-active flag rises on the wire's index-16 byte), the
three fireball volleys six ticks apart (the `step > 1` gate — the
reset writes 0 first, so the 4th never fires), the 100-tick cooldown
with the fire off; the distance bands (d0 < 4 the 20-tick melee with
the movement follow; d0 < 256 the stand-and-volley — only the look
moves; beyond the follow walk).

**The mobAiTick arms**: the water contact's DROWN 1.0 per tick, the
eye-offset re-roll every 100 ticks (`0.5 + gaussian*3`), the hover rise
(`velocityY += (0.3 - velocityY) * 0.3` toward a higher target's eye
band), and the fall dampening (`velocityY *= 0.6` airborne and
falling).

**The fireball**: SmallFireballEntity's shooter ctor — the mid-band
aim with the HORIZONTAL-only gaussian spread
`sqrt(sqrt(d0)) * 0.5` + the ctor's own 0.4 per-axis tilt, the spawn at
`y + height/2 + 0.5`, the world event 1009 at volume 2.0 (the clients
play mob.ghast.fireball). The snowball's flat 3.0 against the blaze
(SnowballEntity.onHit lines 26-29).

**The fortress spawn overlay**: FORTRESS_MONSTER_ENTRIES (the blaze
10/2-3, the pigman 5/4-4, the skeleton 10/4-4, the magma cube 3/4-4)
consulted inside a fortress PIECE's bounds, or the START's envelope
over nether bricks (NetherChunkGenerator.getSpawnEntries 1:1 — the
new FortressStructure.insideBounds consult over the deterministic
(seed, region) recompute, both arms).

**The engine fix** the hover exposed: the grounded vertical zeroing
now gates on `velocityY <= 0` — a positive write (the hover, an
explosion's launch) lifts a grounded body (the reference's own move
semantics: the ground only re-engages on a fall).

**Tests**: BlazeEntityTest (7: the identity + the rod item, the charge
cycle's exact tick family 1/61/67/73 + the fire flip + the 100-tick
cooldown, the 20-tick melee cadence, the hover rise, the DROWN arm,
the fall dampening, the sigma formula).

Fresh suite: **932 green (839 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 18b — the cave spider (the mineshaft's venom)

**Scope**: CaveSpiderEntity over the spider family + the spider family's
drop correction.

**The kind** (59, the 0.7x0.5 hull, the 12 health over the spider's 16):
the flat 0.45 eye (getEyeHeight line 53 — not the generic 0.85 share's
0.425), the ARTHROPOD category, the spider's sounds and traits.

**The venomous bite** (tryDamage lines 25-45): every landed melee hit
poisons by the difficulty — NORMAL the 7-second curse, HARD the
15-second, the weaker rows nothing — through the EngineServer's melee
consume site into the potion system's player store.

**The spider family's drop correction**: the SPIDER_EYE conditional
landed (a 1-in-3 base roll OR any looting luck, one eye) beside the
string walk, both the spider and the cave spider — the pre-18b row
carried only the string.

**Tests**: CaveSpiderEntityTest (4: the identity + the family traits,
the flat 0.45 eye, the venomous bite through the live melee pipeline at
hard (the 300-tick curse) then normal (the 140-tick), the easy tier's
no-venom).

Fresh suite: **843 green in zamin-core**, `--max-workers=1`, zero
failures.

## Slice 18c — the silverfish + the infested blocks

**Scope**: SilverfishEntity + InfestedBlock + the stronghold's spawner
type + the stone-brick item.

**The kind** (60, the 0.4x0.3/8-health body): the 1.0 ATTACK_DAMAGE (the
pre-18c default 2.0 corrected), the flat 0.1 eye, the ARTHROPOD category,
no drops, no natural rows (the eggs and the spawner are the only
sources).

**The egg-hosting life**: the WAKE-UP (the entity-sourced-or-magic hurt
arms the 20-tick cooldown through the manager's DamageCause gate, the
expiry's scan walking the reference's own interleaved 10/5/10 box, each
egg broken with its silverfish spawned, the 1-in-2 early stop) and the
INFEST (the targetless body's 1-in-10 roll over the six adjacent cells,
the canBeInfested read through the new WorldQuery.blockIdAt, the egg
written for the host and the body removed).

**The infested block family**: the SIX flattened eggs (stone 0,
cobblestone 1, stonebrick 2 — THE WIRE FIX: the pre-18c row carried
metadata 4, the CRACKED variant; the stronghold's pick is the reference's
STONEBRICK 2 — mossy 3, cracked 4, chiseled 5, all wire id 97) with the
reference's own rows (setStrength(0.0F) — the instant hand break, the
clay material, getBaseDropCount 0) and the silk drop (the stone/
cobblestone eggs drop their host items, all four brick eggs drop the
STONE BRICKS item with the damage field 0/1/2/3 — the STONE_BRICKS item
registered with it, closing a latent crash where the stone-brick
family's drop rows referenced an unregistered item).

**The burst**: the SURVIVAL break spawns the silverfish at the block's
center — the creative break never bursts (the reference's
afterMinedByPlayer arm is survival-only). The stronghold's spawner
finally carries its SILVERFISH type.

**Tests**: SilverfishEntityTest (6: the identity + the flat eye, the egg
family's mappings, the hand/silk drop arms, the wake-up clock, the
infest roll's adjacent-cell law, the egg burst through the live survival
break with the creative never-burst).

Fresh suite: **942 green (849 core + 19 api + 74 protocol)**,
`--max-workers=1`, zero failures.

## Slice 18d — the wither skeleton

**Scope**: SkeletonEntity.setType(1) 1:1 — the variant body, the nether
initialize, the cursed blade, the reference's own drop law, the held
equipment surface, and the static-init fix the protocol module needed.

**The variant** (setType lines 277-285): the taller 0.72x2.535 hull, the
fire immunity (`immuneToFire = type == 1`), the 4.0 ATTACK_DAMAGE (the
initialize line 221 — the stone-sword arm), and the generic eye share of
the TALLER hull (getEyeHeight line 314's super arm: 2.535 * 0.85 — the
in-progress code's `type.height * 0.85` corrected: super reads the live
size, not the kind's own 1.95).

**The nether initialize** (initialize lines 215-226): the 4-in-5 roll
(`random.nextInt(5) > 0` over `world.dimension instanceof NetherDimension`)
— the nether's own natural-spawn arm (`MobManager.spawnNaturalNether`
through the dimension-aware `SpawnSurface`), the melee goal swap riding
the wither flag in the mind router (`traits.ranged() && !skeletonWither`),
and the stone sword replacing the bow. Every other spawn path keeps the
archer's own bow (addRandomEquipment line 211 — the mob held-equipment
surface's first member).

**The cursed blade** (tryDamage lines 96-106): every landed melee hit
applies the flat 200-tick WITHER (tier 0, no difficulty gate) through the
EngineServer's melee consume site — the cave spider's venom pattern, the
wither's own shape.

**The player-kill window** (LivingEntity lines 92/254/679): the
`markPlayerHit` model — 100 ticks from the player's last landed wound
(melee, the player shooter's projectile, the thorns retaliation), decaying
through the death animation — the gate `dropRareItem` reads.

**The drop law** (dropItems lines 178-198 + dropEquipment + dropRareItem):
the wither's coal arm (`nextInt(3 + looting) - 1` — the negative-friendly
count, most bodies drop nothing), both variants' bones
(`nextInt(3 + looting)`), the archer's arrows — each unit its own
single-item stack (the generic ItemRoll path's `nextInt(3) +
nextInt(1+looting)` replaced with the reference's own bound); the held
item's 0.085 spill (`nextFloat() - looting * 0.01F < 0.085F` on the
player-kill window); the wither skull's rare roll (`0.025 + looting *
0.01`, the SKULL item 397 with the damage field 1) — the engine's first
rare-drop arm.

**The fire-immunity fix the slice exposed**: the fire clock never gated
on immunity — a Fire Aspect blaze took tick damage. The reference's own
model landed (Entity.update lines 322-327 + takeFireDamage 739-743): the
immune body burns FOUR times as fast and never wounds.

**The static-init fix**: Slice 18c's stone-brick rows doubled
`minecraft:chiseled_stone_bricks` (12f's temple row already carried it) —
the `Map.ofEntries` duplicate threw at LegacyBlockIds' clinit, killing
every join flow's chunk serialization. The duplicate removed and the
`LegacyBlockIdsIntegrityTest` guard added (the failure now names itself
in milliseconds, not twenty seconds inside a Netty callback).

**The wire**: the skeleton's type byte at the synced slot 13 (the
SKELETON_TYPE_METADATA_INDEX — 0 the archer, 1 the wither) and the held
item's Entity Equipment packet right after the spawn (the player body's
own wire order).

**The daylight gate**: the wither never joins the dimension-blind
daylight sweep (it only exists in the nether, where the reference's own
hasSkyAccess reads false).

**Tests**: WitherSkeletonTest (6: the variant identity + the eye share
over the live hull, the archer's own body, the 4-in-5 conversion with
the sword swap + the overworld never converting, the death walk through
the always-rare rig — the coal/bone/sword/skull law with the archer's
never-coal, the wither curse through the live melee pipeline with the
archer's no-curse) + LegacyBlockIdsIntegrityTest (1).

Fresh suite: **949 green (855 core + 19 api + 75 protocol)**,
`--max-workers=1`, one documented chest load flake green in isolation.

---

## Slice 18e — the spawner's dimension model + wire surface

**The dimension fix**: every spawner now carries the dimension it lives
in — the spawn cell gate and the broken-block cleanup read the spawner's
OWN world through the dimension-aware resolver (the EngineServer passes
the overworld/nether pair; `drainGenerationAttachments` types each
pending spawner under its own world, with the wire snapshot refresh on
the typed path). This closes the pre-18e blind sweep that removed a
nether fortress's blaze spawner on its first tick: the overworld read at
the same coordinates never answers a spawner block, so the cleanup
misread every nether spawner as broken and dropped it.

**The tile wire (0x35)**: the Update Block Entity packet finally carries
the spinning body. The immutable per-chunk `SpawnerTile` snapshot
(rebuilt on every mutation, published through a volatile, read
cross-thread by the chunk-send path) feeds the packet on every chunk
send and every resend — gated to the tile's own dimension (a chunk (x,
z) exists in both worlds; only the observer's dimension's tiles replay).
The payload is the reference's own `writeNbt` family: EntityId (the wire
entity registration names — `PigZombie`, `LavaSlime`, `MushroomCow`,
`SnowMan`, `Ozelot`, `EntityHorse`, `VillagerGolem` spellings included),
x/y/z, the Delay short + the six timing shorts, and the SpawnData
compound with the id member (SpawnPotentials removed, the reference's
own createUpdatePacket shape).

**The two broadcasts**: the fired cycle's flame burst
(MobSpawner.tick line 108's `doEvent(2004, blockpos, 0)` — the Effect
packet, chunk-gated per connection) and the delay reroll's spin reset
(`broadcastEvent(1)` → the Block Action packet, event byte 1 over the
legacy block id 52, chunk-gated). Both fan out through the manager's
listener list into the protocol adapter.

**The conformance row**: `S2C_UPDATE_BLOCK_ENTITY = 0x35` added to the
table — the NetworkProtocol registration order's own #54 (after MapData
0x34, before OpenSignEditor 0x36).

**The test hardening**: MobIntegrationTest's `/time set night` read
walked into the periodic 20-tick Time Update race — the first packet
after the chat may still carry the pre-command day. The read now walks
Time Updates until the post-command night lands (the wire guarantees no
ordering between the in-flight periodic packet and the command's
application — the same interleaving contract the chat reader already
handles for join/quit broadcasts).

**Tests**: SpawnerManagerTest grew to 5 (the nether spawner's
own-dimension survival + the mirror broken-block removal, the wire
surface — the per-chunk tile snapshot + both broadcasts). Protocol
conformance carries the 0x35 row.

Fresh suite: **951 green (857 core + 19 api + 75 protocol)**,
`--max-workers=1`, zero failures.

---

## Slice 19a — the §27.3 entity-family API wave (items, projectiles,
falling blocks, vehicles, XP orbs)

**The public handles**: five new zamin-api interfaces over the engine's
non-living populations — `DroppedItem`, `Projectile` (+ the public
`ProjectileKind`: arrow/snowball/egg/small+large fireball/splash
potion/ender pearl), `FallingBlock`, `Vehicle` (+ `VehicleKind`), and
`ExperienceOrb`. Each follows the `MobEntity`/`LivingEntity` pattern
(§27.5's zero-allocation shape: the engine class IS the handle — no
wrapper per access): the engine bodies implement the interfaces with
`typeIdentifier()` (the canonical kind ids — minecraft:item, per-kind
projectile ids, minecraft:falling_block, minecraft:boat/minecart,
minecraft:xp_orb) and `snapshot()` (the any-thread-consumable view;
health/maxHealth answer 1/1 on the non-living bodies except the
vehicle's real 4.0 hull).

**The World surface**: `itemEntities()`, `projectiles()`,
`fallingBlocks()`, `vehicles()`, `experienceOrbs()` — point-in-time
population views (the managers' `all()` defensive copies) captured
through volatile suppliers attached at boot; the interface's default
empty answers keep the stub worlds' contract. The nether keeps the
empty views (its drops/projectiles route through the overworld
managers — the same envelope as its mob view).

**The engine wiring**: `EngineWorld.attachEntityFamilyViews` (the five
suppliers), the EngineServer boot attaching them after the last family
manager exists.

**Tests**: `EntityFamilyApiAcceptanceTest` (4: the drop's identity +
contents + the pickup-delay band through the World alone, the arrow's
kind + thrower, the falling sand's block identity, the boat + orb pair
with the snapshot captured on the owner context and consumed on a
foreign thread — §27.6's documented off-owner contract).

Ledgers: the five capability rows NOT AUDITED → IMPLEMENTED
(Experimental).

---

## Slice 19d — the enderman's provocation + carry (the stare + the blocks)

**The hunt-gate correction**: the pre-19d enderman hunted any player in
the shared aggro band — the reference's body is NEUTRAL until provoked.
The engine's canHunt now carries the enderman's own gate: only the stare
or the wound arms the hunt.

**The stare (LookAtPlayerGoal 1:1)**: the nearest player within the
reference's own FOLLOW_RANGE 64; the look-vector dot band
`1 - 0.025 / distance` aimed at the body's mid-height; the carved
pumpkin's veil (the PlayerView helmet read — the engine's armor head
slot); the clearLine sight gate; the 5-tick creepy window ending in the
mob.endermen.stare scream + the anger; the hunting body's close-stare
escape (a staring target inside 4 blocks teleports the body away).

**The takeDamage ladder**: every wound angers (the reference's own
no-attacker clause reaches the environmental family); the player's melee
hand marks the permanent anger at the engine's own melee site (the
creative hand calms instead — the reference's own branch); the
projectile's unknown origin clears the marker; the endermite's exemption
rides the endermite-aware hurt overload; the 1-in-100 calm decay; the
daylight blink clears the whole provocation.

**The carry (PickUpBlockGoal + PlaceBlockGoal 1:1)**: the 1-in-20 pickup
roll over the ±2/±3/±2 box against the reference's own fourteen-block
HOLDABLE set (grass, dirt, sand, gravel, the two flowers, the two
mushrooms, tnt, cactus, clay, pumpkin, melon, mycelium) — the world's
air write + the carry completing through the manager's intent drain; the
1-in-2000 place roll over the ±1/±2/±1 box with the air-and-support
gate.

**The wire**: the carried block's Short at the synced slot 16
(Block.serialize's combined id<<4|meta through the protocol's legacy
table) and the screaming byte at 18 — the join view carries both, the
flip republishes them through the manager's drain.

**Tests**: EndermanCarryStareTest (7). Fresh suite: 872 core green (the
documented lifecycle load flake green in isolation) + 19 api + 75
protocol, `--max-workers=1`.

### Slice 19e — the difficulty family (the peaceful law)

`MonsterEntity.tick` 33-38 + `canSpawn` 131 + `PlayerEntity.tick` 443-451 +
`takeDamage` 786-798: the peaceful sweep (a hostile body leaves the very next
tick whatever path brought it — the spawner, the egg, the command, the
conversion; `MobManager`'s per-tick `removeHostiles()` behind the engine's
difficulty read), the spawn gate (the MONSTER category never spawns on
peaceful — both dimensions' `NaturalSpawnSystem` consult), the regen pair (a
heart a second on the body's own % 20 clock, a food point a half-second on
% 10, both arms running beside each other exactly as the reference runs them —
`tickFoodEconomy`'s peaceful arm over the session's `peacefulTick` clock), and
the blast ladder (the scaled-source family at the explosion player-walk:
peaceful never wounds, easy halves plus one, hard lifts a half — before the
Blast Protection shave). PeacefulDifficultyTest (3). Fresh suite: 871 core +
19 api green, `--max-workers=1`, zero failures.

### Slice 20a — the spawn eggs

`SpawnEggItem` 1:1 over the engine's item/use surfaces: the item (383, the
damage field naming the entity registration id), the mapping (the reference's
registerWithSpawnEgg set — 27 kinds; the golems 97/99, the giant 53 and the
enderer dragon 63 carry no eggs, faithful to the reference's own gaps), the
block use (the spawner re-type arm through the manager's getOrCreate + the
wire-tile republish — an unknown id clears the type exactly as the reference's
null key does; the face-offset spawn with the fence UP-face's +0.5 quirk; the
survival consume / the creative keep), the in-air use (the engine's FIRST
block-grid raycast — the Amanatides-Woo walk over the reference's canRayTrace
semantics: the liquid SOURCE counts only with liquids allowed, the flowing
cells pass, air never counts, the 200-step cap, the 5.0 reach; the spawn at
the liquid cell's center), and the spawn itself (`MobManager.spawnFromEgg`:
the wrapped random facing + the ambient sound at the spawn, the per-kind
initialize arms already riding spawnAt). SpawnEggTest (6). Fresh suite 877
core + 19 api + 75 protocol green, `--max-workers=1`, zero failures.

### Slice 20b — the weather cycle (the 1.8.8 model)

`World.tickWeather` + `WorldData` + `WeatherCommand` + `ServerWorld`'s publishes
1:1 over the engine's new `WeatherSystem` (the pre-20b boolean + auto-clear
countdown replaced): the five WorldData fields (the clear-weather hold with
its 1/2 pin oscillation — the terminal quirk where the first unlocked tick
flips the rain back on kept), the countdowns with the decrement-to-zero flip
tick and the four reroll bands, the two gradients walked 0.01 with the
clamp, the publishes (game state 7/8 per change, the 1/2 flip pair + the
re-push, the overworld's players only), the `/weather` three arms (the
default (300+nextInt(600))*20, the 1..1000000-seconds gate), the join view
(the live gradient pair, PlayerManager's own shape), the sleep reset
(clearWeather — both flags, zeroed clocks, no hold), and the
ambient-darkness shaves (the fully-stormed noon reads 5 over the clear 0,
wired into the natural spawn's light walk). WeatherSystemTest (7). Fresh
suite 884 core green, `--max-workers=1`, zero failures. Ledgered: the
lightning family (the strike roll, the bolt entity, the global-entity wire,
the fire attempts), the ice/snow precipitation arm (a plains no-op by the
temperature gate — faithful), the weather fields' persistence.

### Slice 20c — the lightning family

`ServerWorld.tickChunks` lines 313-321 + `LightningBoltEntity` 1:1 over the
engine's new `LightningSystem`: the per-chunk strike roll (the 1-in-100000
consumed BEFORE the flag gates — the reference's own stream discipline), the
LCG-derived in-chunk pick, findLightningTarget (the ±3-grown box over the
sky-visible bodies, the random pick, else the precipitation cell), the bolt's
life (the constructor's fire walk with the air + support gate and the
NORMAL/HARD difficulty gate, the stage-2 thunder pair at the global 10000
volume, the stage >= 0 damage box — 5.0 + the 8-second ignite through the
engine's own player/mob arms, the fire attempts' re-arm quirk where the
reset to stage 1 re-opens the damage window — the reference's own shape),
and the death. The wire: the Spawn Global Entity packet (0x2C, registration
order #42 — the type byte 1, the fixed-point 1/32 coordinates) + the
LIGHTNING damage cause (both enums, additive). LightningSystemTest (4).
Fresh suite 888 core (one documented compute-drain load flake, green in
isolation) + 75 protocol green, `--max-workers=1`.

### Slice 21a — the chest minecart

`ChestMinecartEntity` + `InventoryMinecartEntity` + the corridor's cart arms
1:1: the cart body (`ChestMinecartEntity` extends the now-open minecart
family — the kind-aware constructor, the `minecartTypeIndex()` wire surface,
the `railDragFactor()` hook) carries a `ChestBlockEntity` (the composition
gives the 27-slot container the chest's own click semantics — the reference's
`ChestMenu` over the cart), the fullness drag (`applySlowown`'s
`0.98 + (15 - signal) * 0.001` — an empty cart rolls 0.995, a full one 0.98),
the interact's chest menu (`InventoryMinecartEntity.interact` ->
`openChestMenu` — never the seat; the window's `isValid` reach gate:
squaredDistanceTo > 64.0 refuses the clicks of a player the cart rolled away
from), and the death's three-drop chain (`MinecartEntity.dropItems`' MINECART
item, `InventoryMinecart`'s contents spill, `ChestMinecart`'s CHEST block —
never the chest-minecart item). The void's correction: `outOfWorld` ->
`setDead` never runs `dropItems` — the whole vehicle family now consumes
without a drop (the pre-21a always-drop arm fixed for the boat and the plain
cart too). The wire: the Spawn Object's objectData carries the minecart type
index (`TrackedEntity` lines 413-415 — 1 for the chest) and a nonzero index
arms the velocity triple (`AddEntityS2CPacket`'s `data > 0` shape — the
±3.9 clamp, the ×8000 fixed-point, three shorts); the window opens as
"minecraft:chest" with the "container.minecart" title over the 63-slot items.
The mineshaft: the corridor's two 1-in-100 cart arms (the air-gated rail cell
with the boolean orientation, the cart at the cell's +0.5 center) with the
loot walk (`LOOT_ENTRIES`' thirteen rows — the lapis band rides the dye's
damage 4, the enchanted book rides `addAll`'s own tail with the reference's
argument-evaluation order (the book's enchantment rolls before the amount's
nextInt(4)) — the weighted pick, the count band, the stack-split arm, the
random slot at 27), the populate-time pending record materializing into the
vehicle manager at chunk publish (the idempotence guard: a cart already
standing on the record's cell skips the re-spawn). The items: the chest
minecart 342 (the rail-place arm), the melon/pumpkin seeds 292/294 (the loot
rows). Tests: `ChestMinecartTest` (6: the type index, the fullness drag
bands, the death's three-drop chain, the void's no-drop, the interact's menu
+ the click routing + the reach gate, the item's rail placement) +
`MineshaftStructureTest.theCorridorRecordsItsLootCarryingChestMinecart` (the
scan over the regions, the rail under the cart, the table bands + the lapis
damage + the book's enchantment, the deterministic twin) +
`VehicleIntegrationTest.chestMinecartPlacesOpensAndServesItsMenuOverTheWire`
(the objectData 1 + the zero velocity triple + the Open Window + the 63-slot
items over the real wire). Fresh suite: 895 core (144 classes) + 19 api +
76 protocol (32 classes), `--max-workers=1`, zero failures. Ledgered: the
vehicle family's persistence (the chest cart's contents included — the
vehicles carry no store), the hopper's cart arm (a hopper pulling from a
chest minecart above it), the furnace/tnt/hopper/command-block/spawner
minecart variants, the chest cart's 27 slots as a public API handle (the
container/inventory wave).

### Slice 21b — the weather persistence

`WeatherDataStore` ("ZWD" v1, beside the other block-entity stores):
`WorldData`'s own five save fields (lines 249-253 — clearWeatherTime,
rainTime, raining, thunderTime, thundering) survive restarts through the
engine's `saveAllNow` (shutdown, console save) and the boot restore; the
gradients never persist (the reference's fresh `World.rain` fields walk from
0 on load); a corrupt store boots the fresh defaults (clear — the first
tick's reroll band draws the clocks) with the quarantine copy beside the
file, the established store behavior. `WeatherSystem.snapshot()/restore()`
carry the five fields (tick-thread snapshot, boot-time restore).
WeatherSystemTest.theWeatherStateSurvivesTheRestart (the three lives: the
thunder command's flags + clocks restore inside the command window, the
gradient restarts its walk from 0, the corrupt quarantine with the .corrupt
copy). Fresh suite: the weather family + the neighbor chunk green,
`--max-workers=1` (one SpawnEggTest natural-spawn load flake in the chunk
run — the documented environmental class, green in isolation).

### Slice 21c — the ice/snow precipitation arm

`PrecipitationSystem` (tickChunks lines 325-341 + World.canFreeze lines
1927-1954 + canSnowFall lines 1960-1978): the 1-in-16 roll per ticking chunk
(running before the rain gates — the reference's own stream order), the
randomTickLCG-derived in-chunk pick, the precipitation height, then the gate
chain — the freeze (NO rain gate: the cold freezes dry; temperature ≤ 0.15,
block light < 10, the water SOURCE, and the surrounded-by-water shore rule
refusing the lake's heart) and the snow (the rain gate alone; temperature,
light, air, and SNOW_LAYER.canBePlaced's solid support). The Biome enum
carries the reference's registration temperatures (the ocean pair 0.5, the
plains 0.8, the desert/hell 2.0, the forest 0.7, the jungle 0.95) — every
generated column refuses both arms (the vanilla-plains envelope, faithfully);
the y>64 noise shave ledgers with the noise slice (on the warm set even the
extreme shave cannot cross the gate). The ICE block registers (legacy 79, the
translucent filter-3 light row). The stream keeps the 20c envelope (each
weather system owns its Random + LCG). The per-chunk walk runs the lightning
arm then the ice/snow arm — the reference's own order. The third arm
(randomPrecipitationTick — the cauldron's rain fill) stays ledgered.
PrecipitationSystemTest (4: the LCG pick derivation — the first advance's
own (7, 12), the freeze's dry run + the shore rule, the warm/light/dry-rain/
support gates, the biome temperature table). Fresh suite: the weather family
+ the block/world/entity/package chunks green, `--max-workers=1`.

## Slice 21d — the rabbit 1:1 (the Action model, the killer bunny, the garden raid)

`RabbitEntity` completed 1:1 over the engine's rabbit family:

**The Action model** (the pre-21d hop's 10-tick HOP cadence corrected to the
reference's own 20): the five rows NONE/HOP/STEP/SPRINT/ATTACK with their
speed/strength/delay/duration quadruples; the launch at `1.5 x the action
speed` over the walk band with the action's own rise (getJumpStrength's
moving-up arm: a target above `y + 0.5` answers the flat 0.5); the landing
edge's delay re-roll; the ATTACK's hold-until-landing; the SPRINT band from
the panic/chase modes (mobAiTick's `movementControl.getSpeed() > 0.8`); the
Entity Status 1 at each hop start (the client's hop bounce).

**The variant family**: the spawn's `nextInt(6)` coat, `setRabbitVariant`'s
killer 99 (only ever by the explicit set — the NBT /summon arm), makeChild's
`nextBoolean` coin through the breed landing, and the wire's slot-18 byte in
the spawn view (the client's skin swap).

**The killer bunny**: the 8.0/3.0 tryDamage pair, the mob.attack strike
sound, the EvilAttackGoal's `4.0 + 0.6` squared reach at the melee site, the
player hunt through the engine's canHunt gate, the wolf hunt through the
1-in-10 mob-target scan, the RevengeGoal arm through applyRevenge, and the
flee-wolf's own removal. The lunge arm: grounded + delay spent + the target
within 16 squared → the look, the move, the ATTACK hop.

**The garden raid** (RaidFarmGoal over GoToBlockGoal 1:1): the goal layer's
"rabbit_raid_farm" band-4 goal — the reference's own ring-walk spiral over
the ±16 box at the y-1/y+0/y-2 bands, the farmland-with-age-7-carrots
target, the `200 + nextInt(200)` scan cooldown (10 after an eat), the hungry
snapshot gate, the approach band `nextInt(nextInt(1200)+1200)+1200`, the 0.7
walk with the 40-tick re-path, and the eat — the pending intent through the
manager's drain, the air write + the blockShatter burst + the hunger-100
refill (onEatFromGarden).

**The awareness arms**: the carrot/golden-carrot/dandelion tempt (the "tempt"
goal's rabbit arm — the 1.0 walk, the no-scared arm, the 2.5-block hold) and
the flee-wolf (the "flee_player" goal's rabbit arm — the 16-block scan, 1.33
both bands).

**The drops** (the dedicated arm): the hide `nextInt(2) + nextInt(1+looting)`
walk, the meat `nextInt(2)` with the burning cooked swap, and the rare foot
on the player-kill window's `0.025 + looting * 0.01` roll.

Tests: RabbitEntityTest (9) + the GoalApiAcceptanceTest band-4 update (the
eighth built-in). Fresh suite: 906 core green (the 4 goal-API assertions
updated for the new built-in) + 19 api + 76 protocol, `--max-workers=1`.
Ledgered: the killer's 8 armor (the engine's missing mob armor model), the
"Killer Bunny" name (the name-tag wire family), the variant's persistence
(the MobDataStore's minimal snapshot), the mobGriefing gate (default-on
stand-in), the STEP JumpControl wiring (the walk model's step arm).

## Slice 21e — the pigman refinements (the attacker re-acquisition + the gold ingot)

ZombiePigmanEntity.mobAiTick lines 79-83: while the anger window burns and
the named attacker is live, the credit window (playerHitTimer — the
reference's recentlyHit bookkeeping) re-arms every tick through the
manager's players walk — the pigman killed mid-chase still credits the
attacker for the drops, the looting level, and the XP. The named player
gone, the window lapses at 100 like any other body.

dropRareItem line 189: the single gold ingot on the player-kill window's
`0.025 + looting * 0.01` roll, beside the flesh/nugget pair in the
manager's dedicated drop arm.

Tests: ZombiePigmanTest +2 (the rare ingot's window gate, the chase-kill
credit at 150 ticks with the lapse-on-gone negative). The nether
neighbor suites green. Ledgered: the re-acquisition's
stamp-vs-countdown deobfuscation ambiguity (the engine models the
never-lapsing credit window); the ghast's own-fireball reflection (needs
the player-attacks-fireball deflection family), the magma cube's size*3
armor (the missing mob armor model), the ghast's 1-in-20 canSpawn +
1-per-chunk limit (the natural spawn cycle's own gates — Slice 21f).

## Slice 21f — the ghast's spawn gates (the 1-in-20 roll + the 1-per-chunk cap)

GhastEntity.canSpawn line 126 (`random.nextInt(20) == 0 && super.canSpawn()
&& difficulty != PEACEFUL`): the 1-in-20 entity-level roll in the natural
spawn system's canSpawnAt walk — the giant body stays rare whatever the
HellBiome weight rows say (the peaceful arm rides the MONSTER category's own
peaceful gate, Slice 19e). GhastEntity.getLimitPerChunk line 131 (1): the
kind's own per-chunk cap in the pack loop — the ghast's pack stops at one,
every other kind keeps the shared 4.

Tests: the spawn/nether suites green (no existing ghast-spawn assertions —
the gate is new coverage). Ledgered: the fireball-kill achievement arm
(no achievement surface), the own-fireball reflection (the missing
player-attacks-fireball deflection family), the magma cube's size*3 armor
(the missing mob armor model).

## Slice 21g — the villager trade system 1:1 (the career model over the fixed stock)

The pre-21g stand-in (the explicitly-simplified three fixed offers per
profession) replaced with the reference's own model:

**The TRADE_SOURCES tables 1:1** (VillagerTrades rewritten): the five
professions' career/level generator tables exactly as the reference
declares them — the farmer's four careers (the farmer/fisherman/shepherd/
fletcher with the shepherd's sixteen wool-color rows), the librarian's
six levels, the cleric's four, the blacksmith's three careers, the
butcher's two. The five generator shapes: EmeraldForItems, ItemsForEmeralds
(with the addRandomEnchantment polish at `5 + nextInt(15)`),
EmeraldsForVillagerTypeItem's negative-amount direction flip,
ItemsForItemsAndEmeralds (the compound buy), and EnchantedItemForEmeralds
(the random enchantment at its own level range, the
`2 + nextInt(5 + level*10) + 3*level` price capped 64).

**The lazy career state** (MobEntity): the first `offers()` consult rolls
the career `nextInt(careers) + 1` at level 1 (the reference's
traderOffers == null gate); levelUp appends the career's next level;
the per-offer live maxUses array carries the re-green extensions.

**The trade bookkeeping** (villagerTrade): the use charge, the 40-tick
level-up arm on the first use or the 1-in-5 (the willing flag + the +5
XP), the emerald riches, the `3 + nextInt(4)` XP band — the execution
spawns the orb beside the body and plays mob.villager.yes.

**The level-up walk** (tickVillagerLevelUp, customer-gated by the
manager's open-window registry): the spent-out re-greens
`nextInt(6) + nextInt(6) + 2`, the next level's appends.

**The wire**: the MC|TrList carries the live uses + the extended
maxUses (the pre-21g `spentOut ? maxUses : 0` stand-in corrected). The
ten new trade items (fishing rod 346, clock 347, cake 354, cookie 357,
melon 360, ender eye 381, experience bottle 384, written book 387,
pumpkin pie 400, name tag 421) + their wire ids.

Tests: VillagerTradeTest (7, rewritten) + VillagerTradeIntegrationTest
(the rolled career's rows on the wire, the post-open currency stocking).
Fresh suite: 913 core + 19 api + 77 protocol green, --max-workers=1.
Ledgered: the village system (the reputation, the mating gates, the
farm goal), the Regeneration window (the mob-side effect family), the
haggle customer ambient, the Offers NBT persistence.

## Slice 22a — the registry-family API wave (Recipes + Enchantments)

§27.3's read surfaces over the frozen registries (the first two of the 28
NOT AUDITED rows):

**`Server.recipes()`** → `crafting.Recipes`: the recipe set in the vanilla
match order (shaped first, larger footprints first), the 2x2 and 3x3 match
verbs over public ItemStacks with the reference's own semantics (the offset
scan with the mirror-first rule, the multiset rule), and the immutable
RecipeView (the result identity/count/damage, shaped, the ingredient count).
Any thread — the registry is frozen at boot and every read is pure.

**`Server.enchantments()`** → `enchantment.EnchantmentRegistry`: the id-
ordered views with `byId`/`byKey` lookups (id/key/weight/maxLevel). Any
thread.

Tests: RegistryApiAcceptanceTest (2: the order invariant + the
foreign-thread log-to-planks match + the 3x3 chest ring + the empty-grid
miss; the known enchantment rows + the id order + the off-lane read).
The ledger rows graduate to IMPLEMENTED.

## Slice 22b — the container-family API wave (chests, furnaces, hoppers)

§27.3's block-entity read surface over `World.containers()`: the
position-keyed `container.Containers` family — `chestAt`/`furnaceAt`/
`hopperAt` lookups (best-effort from any thread, empty on a miss) over
the three managers' own block entities (§27.5: the engine objects behind
the live views). The chest's 27-slot view (slots()/slot(i) copies), the
furnace's burn/cook state (input/fuel/output, burnTimeRemaining/
burnTimeTotal, cookTime), the hopper's 5-slot view + the transfer
cooldown. The EngineWorld's containerView wiring at boot; the ContainerViews
adapter in the server's own package.

Tests: ContainerApiAcceptanceTest (2: the chest's public reads + the
off-lane lookup, the furnace/hopper views + the miss arms). The three
ledger rows graduate NOT AUDITED → IMPLEMENTED.

## Slice 22c — the block-entity API wave beyond the containers (spawners + signs)

§27.3's read surface over `World.blockEntities()`: the position-keyed
`blockentity.BlockEntities` family — `spawnerAt(pos)` and `signAt(pos)`
(both best-effort from any thread, empty on a miss) over the engine's
own state (§27.5 — the engine objects behind the live views). The
spawner view carries the spawned kind's canonical identifier + the live
delay walk, and — beyond the container family's shape — answers only on
the dimension the spawner lives in (the manager is global; the
BlockEntityViews constructor takes the world's dimension and filters,
the overworld view never answering a fortress spawner and the nether
view never answering a dungeon's). The sign view exposes the four
editable lines (`lines()` copies, `line(i)` with the empty arms). The
EngineWorld wiring (attachBlockEntityView) at boot on BOTH dimensions;
`MobSpawnerBlockEntity.delay()` opened public for the read surface.

Tests: BlockEntityApiAcceptanceTest (2: the spawner kind + delay + the
dimension filter both ways + the un-typed null + the off-lane lookup;
the sign lines + the copy isolation + the misses). The spawner + sign
ledger rows graduate NOT AUDITED → IMPLEMENTED.

## Slice 22d — the scheduled-tick API wave (the neighbor-update queue + the gravity fuses)

§27.3's scheduled-block-tick family over `World.scheduledUpdates()`:
`block.ScheduledUpdates` — the query arms (`pending()`, `pendingFallChecks()`
copies of the two tick-confined queues, `pendingAt(pos)`) answer
best-effort from any thread; the mutation arms (`cancelAt(pos)`,
`cancelFallCheckAt(pos)`, `requestAt(pos)`) assert the world's
authoritative context through the new public
`EngineWorld.requireMutationContext` (§27.6 — an off-lane call fails
with the operation-named OwnershipViolation, the §28.10 contract). The
engine side: `BlockUpdateSystem`'s own public surface
(pendingUpdates/pendingFallChecksView/pendingUpdateAt/cancelUpdate/
cancelFallCheck/requestUpdate + the ScheduledTickView record) over its
unchanged private queues — the historical one-update-per-position
coalescing rule untouched (the cancel arms remove both the queue entry
and the pending key together; the request arm rides the earliest-wins
schedule).

Tests: ScheduledUpdatesApiAcceptanceTest (2: the grass-under-stone decay
observable through the queue's drain + the sand fuse deterministically
cancelled inside its two-tick window through the scheduler's runLater(1)
— the sand hovers forever, no falling body; the off-lane cancel/request
violations naming their operations + the on-lane World.execute path).
The scheduled-ticks ledger row graduates NOT AUDITED → IMPLEMENTED.

## Slice 22e — the registry-completion API wave (the item registry + the tool verdicts)

§27.3's registry family completed over the frozen tables:

**`Server.items()`** → `item.ItemRegistry`: the frozen BuiltinItems set
dressed as the public registry — `all()` (the 221-type enumeration),
`byId(Identifier)`, `byKey(String)` (the short minecraft-namespace
form). Any thread (the frozen map). The ItemRegistryView adapter in the
server's own package.

**`BlockRegistry.canHarvest(type, held)` / `miningSpeedMultiplier(type, held)`**:
the engine's own tool-interaction verdicts opened on the public block
registry — the class/tier matching (BlockBehaviorTable.canHarvest: the
empty hand and the mismatched class never harvest a tool-gated block;
the wooden pickaxe satisfies stone's level 1) and the material speed
table (wood 2.0, diamond 8.0, the hand 1.0). The API interface carries
dataOf-derived defaults; the FrozenBlockRegistry overrides with the
exact engine tables. Any thread (frozen data).

Tests: ItemAndToolDataApiAcceptanceTest (2: the enumeration + the
lookups + the off-lane reads; the harvest ladder + the speed ladder).
The block-behavior-tables ledger row graduates NOT AUDITED →
IMPLEMENTED (the dataOf reads were already there since 14b — the row
was stale); the item-types row's enumeration omission cleared.

## Slice 22f — the inventory API wave (the player slots + armor, the container writes, the cart cargo)

§27.3's player-inventory + container-write families:

**`Player.inventory()`** → `Player.Inventory`: the slot-level surface over
the engine's own PlayerInventory — resolved through a COVARIANT RETURN
(PlayerInventory implements Player.Inventory; the engine's own accessor
`session.inventory()` now satisfies the API method — the engine object IS
the handle, §27.5, zero call-site churn across the engine's 144 uses).
The reads: slotCount (36), slot(i)/slots() (the forgiving copies),
heldSlot, armorSlotCount (4), armorAt(i)/armorStacks() (the EMPTY-filled
row — the engine's effect hooks read both shapes through the null/
isEmpty-guarded helpers, and ItemStack's record immutability keeps the
live-vs-copy distinction behavior-free). The writes: setSlot(i, stack)
and setArmor(i, stack) (the vanilla kind gate) fire the new change
callback — the same fan-out the give() path rides (a redundant full
window push for the three engine paths that already publish, benign and
idempotent); selectHotbarSlot deliberately does NOT — the client predicts
its own held-slot swap (the historical rule the engine's
held-item-change path keeps, documented in the API javadoc). armorAt's
out-of-range answer moved from the engine's throw to the API's EMPTY
(only in-range engine callers ever hit it — verified by grep).

**The container writes**: Containers.Chest/Hopper `setSlot(i, stack)` +
Containers.Furnace `setInput/setFuel/setOutput` — the §27.6
owner-context arms over the engine's own block entities, asserting
through the world's requireMutationContext (an off-lane write fails with
the operation-named violation). The boot wiring now passes the world to
ContainerViews; the legacy three-arg constructor keeps the bare-tests'
shape.

**The cart cargo**: `Vehicle.cargo()` → `Vehicle.Cargo` — the chest
minecart's rolling 27-slot grid over the engine's own cart chest (the
engine object behind the handle, §27.5); the boat and the plain minecart
answer empty.

Tests: InventoryApiAcceptanceTest (3: the slot round trip + the copy
isolation + the hotbar select; the armor kind gate (the refused sword)
+ the helmet landing + the clear; the chest off-lane violation + the
on-lane chest/furnace/hopper writes; the cart cargo through
World.vehicles()). The neighboring suites re-run green (Inventory/
Armor/Player/WindowClick/Death/Enchant batches + the full protocol
suite 76).

## Slice 22g — the explosion/TNT/redstone API wave (the §27.7 detonation gate + the TNT family + the power reads)

**`event.world.ExplosionEvent`**: the pre-commit cancellable gate over
the `detonateAt` walk (every detonation family rides it — the creeper
blast, the primed TNT, the chain reaction). Fired at the walk's head,
before any destruction: a cancelled blast destroys nothing, wounds
nobody, sends no Explosion packet (the priming body itself already left
at the fuse's end — only its consequence is undone). The event carries
the world, the center, the power (the vanilla creeper 3.0 / TNT 4.0).

**`World.primedTnt()`** → `entity.PrimedTnt`: the TNT family view — the
engine's own `TntSystem.PrimedTnt` bodies ARE the handles (§27.5 — the
class now implements the public interface: position, velocity,
fuseTicks, onGround, typeIdentifier minecraft:primed_tnt, the any-thread
snapshot). The EngineWorld supplier wiring at boot.

**`World.redstone()`** → `block.RedstoneSignals`: the power reads —
`powerAt(pos)` (the strong-power neighbor walk over the engine's own
consult: sources, wire decay, the whole `neighborSignal` semantics) and
`poweredAt(pos)` (the boolean form). Owner-context consistent reads.

**The piston/fluid rows audited honestly**: their states are block
states (World.getBlock reads the extension/orientation/level cells) —
no redundant public surface; the observe hooks ride the
interaction-events wave.

Tests: ExplosionTntRedstoneApiAcceptanceTest (2: the cancelled twin —
the gate fires exactly once with the vanilla TNT power and the stone
cluster stands — plus the uncancelled twin that tears it; the primed
body's family view + snapshot + the lever's 15 feed through both read
forms + the far cell's zero). The explosion/redstone/TNT/explosion-event
ledger rows graduate NOT AUDITED → IMPLEMENTED; the piston/fluid rows →
AUDITED with the block-state rationale.

## Slice 22h — the async-compute API wave (the §27.3 compute family)

**`Server.compute()`** → `concurrent.AsyncCompute`: the engine's own
ComputeSubsystem dressed as the public safe-computation surface —
`submit(input, compute, onFresh, onStale)` binds to the server's
simulation owner domain; the pure function runs on the bounded pool
over the immutable input; the result (or the staleness verdict)
delivers at the head of a near tick on the owner's validated context
(the ticker's own drain — the §27.3/§27.6 contract: fresh results apply
inside the domain, stale results never apply, admission is bounded with
an explicit refusal, the handle is never a blocking promise, §28.2).
The lifecycle handle: generation/isDone/isCancelled/isFailed/failure/
tryCancel (a queued task cancels; a running one completes and validates
at the drain). The §27.9 diagnostics: submittedCount/rejectedCount/
failedCount/queuedCount over the subsystem's own counters (the three
existing + the new queuedCount).

The view (AsyncComputeView) is wired at boot over the real subsystem +
simulation domain; the §5 ledger rows updated honestly: the
owner-context-execution row rides the already-implemented World.execute
(the §27.6 owner path); the snapshots row rides the already-implemented
EntitySnapshot family; the cross-owner row ledgered as the
parallel-lane milestone's own (the router is engine-internal region
traffic today).

Tests: AsyncComputeApiAcceptanceTest (2: the pure result delivered on
the tick thread with its generation + the counters; the queued-cancel
arm + the throwing computation's isolation + the survivor submission).

## Slice 22i — the hopper GUI window + the contents persistence (vanilla 1:1)

**The window (the reference's HopperBlock.use lines 135-146 + HopperMenu):**
right-clicking a placed hopper opens the 41-slot menu — ContainerKind.HOPPER,
openHopperOnTick (the lazy entity create, the carried-window close, the wire
id), the use-dispatch arm, and the click routing (clickHopperWindowOnTick —
mode 0 the cursor walk over the five slots through the shared WindowClicks,
mode 1 the quick-move pair (any stack enters — the reference's plain
InventorySlots, no filters), mode 2 the number-key exchange on the player's
own slots, mode 4 the drop-click). The wire layout exactly the reference's:
5 container slots, 5-31 the main inventory (engine 9-35), 32-40 the hotbar.

**The wire**: Open Window 0x2D "minecraft:hopper" (5 GUI slots) + the
authoritative 41-slot Window Items (sendHopperWindow/sendHopperWindowItems
in the adapter, the chest family's own shape).

**The entity arms**: HopperBlockEntity gains the chest's own click family
(clickSlot over WindowClicks, dropFromSlot, quickMoveToInventory,
quickMoveIn — matching partials first, then the first empty slot).

**The persistence**: HopperDataStore ("ZHD" v1 — the chest store's own
durability rules: atomic writes, the corrupt quarantine, the loud failures)
wired at boot (restoreAll) and saveAllNow.

Tests: HopperWindowAcceptanceTest (2: the open + the cursor-dance click
landing the stack + the quick-move back; the contents surviving a restart
over the same data dir — polling waits, robust under load). The neighboring
suites (Hopper/WindowClick/Chest/Dispenser batches) green. The vanilla
ledger's hopper row: the GUI + persistence gaps closed.

## Slice 23a — the brewing stand API wave (§27.3's container family completes)

**The surface:** `World.containers().brewingStandAt(pos)` →
`container.Containers.BrewingStand` — the live 4-slot view over the
engine's own `BrewingStandBlockEntity` (§27.5: the engine object IS the
handle): slotCount (the vanilla 4), slots() (the isolated copy), slot(i),
ingredient() (slot 3), brewTicksRemaining() (the 400-tick cycle read),
and setSlot(i, stack) — the §27.6 owner-context write that carries the
reference's own isItemAllowed gates (the ingredient slot takes brewing
ingredients, the potion slots take potions and glass bottles; an illegal
non-empty stack fails with IllegalArgumentException so a plugin learns
the vanilla rule instead of a silent no-op — the engine's setItem
already clamps, the view diagnoses).

**The wiring:** ContainerViews gains the BrewingStandSystem (the fourth
position-keyed manager, the full-shape constructor; the legacy
constructors keep their shape) + the BrewingStandView adapter;
EngineServer passes brewingStands at the same attachContainerView site
(the boot order safe: the system constructs at the hopper-lookup wiring,
the view at the world attach — later in the same walk).

Tests: BrewingStandApiAcceptanceTest (3: the lookup + the live view +
the fresh-stand reads + the miss arms + the off-lane best-effort lookup;
the off-lane write violation + the on-lane bottle/ingredient writes + the
slots-copy isolation + the reference slot gates both directions + the
empty-stack clear; the brew cycle armed through the public write + the
timer read + the missing-ingredient cancellation). The neighboring
suites green (Container/Inventory/HopperWindow/brewing batch; the
HopperWindow restart arm is load-sensitive under parallel workers —
green alone and under --max-workers=1).

The vanilla ledger's hopper-era container row already covered the stand's
mechanics (Slice 11e); the API ledger's Brewing row graduates
NOT AUDITED → IMPLEMENTED.

## Slice 23b — the enchanting data API wave (§27.3's registry predicates)

**The surface:** `Server.enchantments()`'s data wave — five predicates
over the frozen tables, all pure any-thread reads:
`canEnchant(id, stack)` (the reference's Enchantment.canEnchant — the
damage family's axe arm from DamageEnchantment line 71-72 and
efficiency's shears arm from EfficiencyEnchantment line 24; the book's
take-everything stays the table's own flag, the predicate answers the
category walk's false), `isCompatible(id, otherId)` (the family
conflicts — the protections' subtype exclusion with feather falling's
pairing, the damage trio's mutual exclusion; unknown ids answer true),
`enchantabilityOf(stack)` (the material/armor tier tables — wood 15,
stone 5, iron 14, diamond 10, gold 22; leather 15, chain 12, iron 9,
gold 25, diamond 10; the book/bow/rod 1; the shears' historical 0),
`minXp(id, level)` / `maxXp(id, level)` (the per-family curves as
OptionalInt — unknown ids empty).

**The wiring:** EnchantmentRegistry gains the five defaults (the
tolerant stand-ins); the EngineServer anonymous impl overrides them over
Enchantments.byId / Entry.canEnchant(stack, true) / Entry.isCompatible /
Enchantments.enchantabilityOf / Entry.minXP/maxXP.

**The reference quirks pinned:** efficiency's max curve rides the BASE
min (super.getMinXpRequirement — 1 + level*10 + 50, NOT its own min):
level 1 the window 1..61, level 5 the window 41..101.

Tests: EnchantmentDataApiAcceptanceTest (3: the category walk + the
axe/shears arms + the book's false + the unknown/empty tolerance + the
off-lane read; the family conflicts both directions + the FF pairing +
the unknown tolerance + the off-lane read; the tier ladder over five
tool materials and four armor tiers + the three quirks + four families'
XP curves + the BASE-curve max quirk + the off-lane tier read). The
RegistryApiAcceptanceTest still green (the view shape unchanged).

The API ledger's Enchanting row graduates NOT AUDITED → IMPLEMENTED
(the omissions column now names the offer math, the menu window state,
and the random-enchant verb as the remaining engine-side families).

## Slice 23c — the leash API wave (§27.3's entity family)

**The surface:** `LivingEntity`'s leash family — `leashed()`,
`leashHolderKind()` (the new public `entity.LeashHolderKind`:
NONE/PLAYER/KNOT), `leashHolderPlayerUuid()` (Optional<UUID> — the
holder's own uuid, no PlayerSession leak), `leashKnotPosition()`
(Optional<BlockPosition> — the fence the knot ties to), and
`unleash(boolean dropLead)` — the §27.6 owner-context mutation routed
through the LeadSystem's own detach walk (clearLeash + the wire's
onMobDetached + the optional drop at the mob's feet).

**The wiring:** MobEntity carries the public reads over its own leash
state + a `leashDetacher` hook (the removalHook pattern); MobManager
gains `setLeashDetacher(BiConsumer)` — spawnAt wires fresh spawns, the
setter retro-wires the already-spawned (the boot order restores mobs at
line 564, the LeadSystem attaches at 1137 — both orders covered);
EngineServer wires the hook with the §27.6 assert:
`world.requireMutationContext("unleash")` before the system's walk (the
bare-wired rigs keep their tolerant shape).

**The naming:** the API's holder read is `leashHolderPlayerUuid()` —
the engine's own `leashHolderPlayer()` (PlayerSession) keeps its name;
the uuid form avoids the return-type-only clash.

Tests: LeashApiAcceptanceTest (3: the player-hold arm through the
reference's interactMobWithLead path + the holder-kind/uuid reads + the
unleash-with-drop landing the lead item at the mob; the fence tie
through useLeadOnFence + the knot reads + the creative unleash's
no-drop; the off-lane unleash failing diagnosably + the leash surviving
the rejected write + the handle-identity walk through World.entities()).
The neighboring suites green (EntityApi, LeadSystem batch, GoalApi,
EntityFamilyApi).

The API ledger's Leashes row graduates NOT AUDITED → IMPLEMENTED.

## Slice 23d — the configuration API wave (§27.3's server-lifecycle read)

**The surface:** `Server.config()` → the new public
`config.ServerConfig` — an immutable record carrying exactly what the
operator's file validated at boot: host, port, worldName, motd,
maxPlayers, viewDistance, tickRateHz, dataDir, gamemode, pvp, levelType,
whiteList, regionSizeChunks (the ADR-6 spatial granularity — the
WHOLE_WORLD_REGION_SIZE sentinel public on the API type). Frozen at
boot: no live setter surface exists by design (the historical restart
rule). Any thread.

**The wiring:** EngineServer maps its EngineConfig into the public
record per call. The engine's own accessor renamed `config()` →
`engineConfig()` (the return-type clash with the API surface — three
protocol-module call sites updated: V18Connection, V18ProtocolServer,
StatusResponse).

Tests: ConfigApiAcceptanceTest (2: the full thirteen-field read-back
through the public surface + the frozen stability + the off-lane
read + the sentinel; the defaults' historical shape — survival, pvp on,
normal terrain, no whitelist, the whole world one region). The protocol
suite green after the accessor rename (77 tests).

The API ledger's Configuration row graduates NOT AUDITED → IMPLEMENTED.

## Slice 23e — the world-deltas audit decision (the last NOT AUDITED row)

**The decision:** `WorldDeltaSnapshot`'s row graduates NOT AUDITED →
AUDITED with the recorded rationale — the delta state's every
observable value already reads through the public World surface
(`World.getBlock` the authoritative cell, `World.totalTicks`/
`timeOfDay` the clocks); the delta-set enumeration itself stays internal
because its meaning is bound to the delta persistence format (the flat
generator's determinism contract, §541), not to a stable gameplay
contract. A public "modified-since-generation" enumeration would be a
NEW capability, not an exposure of an existing one; it rides its own
wave if plugin demand names it (§27.3's rule: record the inaccessible
capability with the concrete reason).

**The milestone:** the capability ledger now carries zero NOT AUDITED
rows — every engine subsystem behind §27.3's audit list has either a
public entry point with a plugin-facing test (IMPLEMENTED) or a
recorded decision naming exactly what stands between the engine and the
surface (AUDITED with rationale).

## Slice 23f — the interaction-events wave (§27.7's named omissions close)

**The four events** (every handler registered through the public bus,
firing on the walk's own owner context):

1. **`event.block.DispenseEvent`** — the cancellable pre-commit gate
inside the dispense walk's boundary scope, BEFORE the behavior runs:
carries the world/origin/facing (Direction)/stack/facingCell/dropper
reads. A cancelled dispense keeps the stack in its slot untouched (no
item leaves, no projectile spawns, not even the empty click sounds).
Wraps the whole family — built-in and custom behaviors alike (the
§27.3 registration surface is unaffected). DispenserSystem gains the
setEventBus wiring.

2. **`event.block.ChestOpenEvent`** — the post-commit observation in
openChestOnTick, after the window id assigns and the lid swings: the
worldName/position/openerId/openerName reads. Not cancellable (the
window already opened).

3. **`event.block.SignChangeEvent`** — the cancellable + rewritable gate
in editSignOnTick, BEFORE signManager.set: the sign still carries its
old lines. Cancel keeps the old text (no viewer replay);
`setLines(String[])` replaces the submitted lines wholesale (a null or
wrong-length array is ignored — the submitted lines stand) and the walk
applies the handler's answer.

4. **`event.world.ExplosionDestroyEvent`** — the per-block post-commit
observation in detonateAt, after the destruction and the drop rolls
resolve, before the wound falloff: the world/center/power reads + the
immutable cells() list (position + previousType per destroyed cell, in
the detonation walk's own order). Not cancellable — the pre-commit
ExplosionEvent above it is the veto point.

Tests: InteractionEventAcceptanceTest (4: the dispense cancel — the
field reads, the stack staying, no projectile — plus the uncancelled
twin with the one-unit payment; the chest-open identity reads after the
use-dispatch; the sign cancel arm + the rewrite arm + the identity
reads; the explosion destruction list with the post-commit air truth —
the TNT's own seat correctly absent, the prime consumed it). The
neighboring suites green (DispenseBehavior, EventApi, ExplosionTnt-
Redstone, BlockEntity, HopperWindow, the chest + dispenser batches).

The ledger's four long-named omissions close: the dispense observe/
cancel, the chest-open observe, the sign-change observe, the per-block
explosion destroy. The piston/fluid observe hooks and the furnace
burn/cook hooks stay ledgered for their own waves (the §27.7 rule:
hooks land with the mechanics that need them, never speculatively).

## Slice 23g — the inbound packet-inspection wave (§27.3's C2S mirror)

**The surface:** `ProtocolHooks`'s inbound family —
`addInboundListener(InboundPacketListener)` / `removeInboundListener` /
`inboundListeners()` (any thread, registration-order,
exception-isolated — the outbound registry's own law) + the live
`InboundPacket` handle: `sender()` (the Player), `packetId()` (the
ACTIVE version's table — protocol 47 here), `payload()` (the fresh
copy), `cancel()` / `cancelled()`. A dropped frame never decodes: no
authoritative mutation, no semantic event — the sender's client believes
it sent, the engine simply did not listen.

**The deliberate boundary:** NO payload-replacement verb on the inbound
side. The engine's parsers own the decode (a hand-replaced payload
could corrupt authoritative state mid-walk); a plugin that wants to
change a player's intent implements it at the semantic layer — the
command/event surfaces — not by rewriting bytes. Observe-or-drop is the
whole contract, documented on the handle.

**The wiring:** `PacketHookRegistry` gains the second registry +
`fireInbound` (the mirror of the outbound fire); the protocol side
gains `PacketHookInboundHandler` — one Netty handler per connection
between the length-prefix decoder and the connection's own dispatch
(placed with addLast BEFORE the connection's addLast: the inbound
traversal order is Decoder → hook → connection; the first attempt's
addBefore("connection", ...) failed at initChannel — the name does not
exist until the next line, the NoSuchElementException killed every
channel at birth, diagnosed from the test's captured stderr).

Tests: PacketHookInboundApiTest (2: the observation — the chat frame
seen pre-decode with the sender/payload copy/the registry verbs; the
drop — the cancelled chat never broadcasts, the uncancelled twin lands
once the listener leaves). The outbound PacketHookApiTest still green
(the pipeline change is additive).

The protocol ledger row's inbound omission closes.

## Slice 23h — the entity-spawn event family (§27.7's spawn gates complete)

**The two events** (the cancellable pre-commit gates over the engine's
two spawn chokepoints, both firing on the walk's own tick context):

1. **`event.entity.ItemSpawnEvent`** — over ItemEntityManager's two
spawn bodies (spawnDropAtBlock + spawnThrown): the block-break loot,
the mob deaths, the player throws, the container spills, the
dispenser's plain egress all ride the same chokepoint. Carries the
stack + the position. A cancelled drop never enters the world: no id
consumed, nothing lies on the ground — the clean-drops plugin's own
verb. The cancelled spawn returns null (the MobManager.spawnAt
contract's own shape); the two callers that touched the return now
null-check (the player-throw velocity + the dispenser egress
velocity).

2. **`event.entity.ProjectileSpawnEvent`** — over ProjectileManager's
five launch variants (launch, launchVector, launchFireball,
launchGhastFireball, launchBlazeFireball) through the shared
admitLaunch gate: the bow shot, the snowball/egg/potion throws, the
dispenser's projectile arms, the blaze and ghast volleys all ride the
same chokepoint. Carries the PUBLIC ProjectileKind (the apiKind
mapping) + the thrower's entity id (-1 for the anonymous fireball) +
the origin. A cancelled shot consumes no id, spawns no body, sends no
wire frame — the launch returns null (most callers already
null-checked; the one that didn't now does).

Tests: EntitySpawnEventAcceptanceTest (2: the item gate — the stack
reads, the null return, the empty ground, the uncancelled twin with
the entity carrying the stack; the projectile gate — the public kind
read, the null return, the empty sky, the uncancelled twin). The
neighboring suites green (EntityFamilyApi, EventApi, InteractionEvent,
RangedCombat, the dispenser batch, ItemEntityManagerTest).

The §6 mob-spawn row's omission closes for the projectile and
item-entity families (the vehicle spawn event rides the vehicle
family's own wave).

## Slice 23i — the cauldron family (vanilla 1:1 — the weather row's third arm closes)

**The block:** `server/cauldron/CauldronBlocks` — the four flattened
LEVEL states (`minecraft:cauldron` + `cauldron_level1..3`, legacy 118
with the level on the nibble, `of(level)`/`levelOf`), registered in the
boot chain; the behavior row (Block.java line 1242's strength 2.0, the
cauldron-item drop whatever the level) + the light row (opacity 0 —
CauldronBlock line 57's isSolidRender false, Block line 216's rule) +
the CAULDRON item (380 — Item.java line 676) with the wire's ITEM_OVERRIDES
split (the block keeps 118, the item 380).

**The use arms** (CauldronBlock.use lines 77-160, through
useItemOnBlockOnTick's dispatch): the water bucket fills any non-full
cauldron to 3 (survival swaps for the empty bucket), the glass bottle
drains one level into the water potion (metadata 0, the bottle pays one
unit, a full inventory drops the potion at the rim), both arms consume
the use whatever the level, and anything else falls through to the
placement proposal — the armor/banner wash arms omit CLEANLY (the
engine's armor carries no color model: hasColor answers false exactly
like the reference's uncolored leather; no banner item exists).

**The rain fill** (the weather row's ledgered third arm,
randomPrecipitationTick lines 168-175): the same 1-in-16 walk dispatches
the block's own tick on the surface cell while it rains in a rainy biome
— the 1-in-20 `nextInt(20) == 1` roll, the level+1 write under the 3
cap. The Surface interface gains isRainyBiomeAt + randomPrecipitationTick;
the Biome enum gains the rain flag (disableRain's model: the desert +
hell false, reference lines 76/97).

**The off-by-one the third arm exposed:** the EngineServer's
precipitationHeight answered the topmost BLOCK instead of the
reference's getPrecipitationHeight (the first FREE cell above) — both
frozen arms stayed no-ops whatever the cell (the warm-biome temperature
gate masked it), but the cauldron dispatch read height-1 and found air.
Both surfaces (the lightning + the precipitation) now answer
`surfaceY + 1` 1:1; the lightning suite still green.

**The rest:** the comparator's analog row (the level IS the signal,
lines 188-195) + the burning player's douse in the fire walk
(onEntityCollision lines 67-74: the feet dipping to
`y + (6+3*level)/16` extinguish and spend one level). The burning MOB's
douse ledgers with the mob block-query extension (the WorldQuery
carries no block-type read today).

**The latent landmine fixed:** throwFromPlayer never guarded the empty
stack — pickUp's empty leftover (everything fit) reached the ItemEntity
ctor's "cannot be empty" throw and killed the whole tick task. The
cauldron's bucket arm exposed it; the guard now answers the obvious
contract (an empty overflow throws nothing).

Tests: CauldronAcceptanceTest (4) + the precipitation stub's two new
Surface methods. The neighboring suites green (weather batch, lightning,
fire, block behavior/data, world model, block interaction; the protocol
module compiles).

## Slice 24a — the gamerule family (the fifteen-rule table 1:1 + every engine gate)

**The table:** `server/gamerule/GameRules` — the reference port of
`world/GameRules.java`: the TreeMap table with the fifteen 1.8.8 rules
and their exact defaults/types, the string-value model with the parsed
boolean/int views (the int parse falls back to the boolean's 1/0, the
NumberFormatException swallows silently — the reference's own parse),
the ANY_VALUE extension arm (`set` on an unknown name ADDS it), the
sorted enumeration, and `isType`. One shared instance per server — the
reference's own shape, where every dimension's
`ReadOnlyWorldData.getGameRules()` delegates to the overworld's table.

**The threading contract (§27.6/§28.3):** mutations run ONLY on the
simulation context — the `/gamerule` command's dispatch already rides
the tick lane, and the public `set` route asserts the world's own
mutation context (the off-lane call throws the loud refusal, never a
silent race). Reads are any-thread safe through an immutable published
snapshot (the AtomicReference swap — each read sees one whole parsed
table state; Value is immutable so the snapshot is honestly shareable).

**The persistence:** `GameRuleDataStore` — the "ZGR" format (magic +
count + name/value UTF pairs), the weather store's own durability rules
(atomic move, corrupt = preserved as .corrupt beside the path, loud
failures), saved at saveAllNow, loaded at boot. The reference's own
placement is WorldData's `GameRules` NBT compound — the engine's
world/data/gamerules.bin.

**The gameplay gates (each the reference's own consult site):**

- **doDaylightCycle** — EngineTicker.tickTime: the total clock walks
  unconditionally, the day clock gates (ServerWorld.tick 190-192).
  EngineWorld.tickTime grew the `advanceDaylight` parameter.
- **naturalRegeneration** — BOTH regen arms: the peaceful pair
  (PlayerEntity.tick 443 — the rule AND the difficulty) and the food>=18
  walk (HungerManager.tick 41). Starvation still bites with the rule off.
- **keepInventory** — dieOnTick: the rule composes with the §27.7
  PlayerDeathEvent's cancel arm (either keeps the inventory + XP; the
  death still resolves).
- **showDeathMessages** — the death broadcast gates; the event's own
  null-suppress (a plugin's silent death) composes with it.
- **doMobLoot** — MobManager.dropLoot's whole body + the kill-reward XP
  burst + the breed-reward burst (LivingEntity.die, AnimalBreedGoal).
- **doTileDrops** — the break's publishDrops (Block.java's dropBlock
  gate): the break commits, nothing lands.
- **doEntityDrops** — the vehicle drop sink (the cart item + the chest
  extras): the body still breaks.
- **doMobSpawning** — BOTH natural cycles (overworld + nether share the
  one table) + the pearl's endermite arm (EnderPearlEntity 52); the
  teleport itself always runs.
- **doFireTick** — BlockUpdateSystem.tickFire's whole body INCLUDING the
  re-schedule (FireBlock.tick 145): a false rule freezes the flame
  inert — no spread, no douse, no burn-away — until a neighbor commit
  re-arms it.
- **randomTickSpeed** — RandomTickSystem's per-section pick band, read
  once per chunk walk; 0 stops the walk (tickChunks 343's `l2 > 0`).
- **mobGriefing** — the creeper + ghast-fireball destructive flag
  (CreeperEntity 208, FireballEntity 33: detonateAt grew the
  `destructive` parameter — the wounds, knockback, and packet still
  land; TNT passes true unconditionally, PrimedTntEntity 75), the
  enderman carry pair (PickUpBlockGoal 446 + PlaceBlockGoal 476 — the
  carried body keeps what it holds), the rabbit's raid-farm canStart
  (RaidFarmGoal 534), and the silverfish wake's silent host swap
  (WakeUpFriendsGoal 222-227: a no-grief world converts the egg back to
  its host stone — no break, no fish — the walk may still stop on the
  coin). The MobEntity WorldQuery gained the `mobGriefing()` default
  (the default-true stand-in; the engine's query overrides with the
  live read).

**The command:** `/gamerule` (level 2) — GameRuleCommand 1:1: the bare
form's listArgs shape (`a, b, c and d`), the query line
`rule = value`, the unknown-rule miss, the boolean validation
(`'x' is not true or false` — the refused value never lands), the set
success (`Game rule %s has been updated to: %s`), and the reference's
extension arm (an unknown name adds a custom ANY_VALUE rule).

**The public surface (§27.3/§27.5):** `Server.gameRules()` →
`gamerule.GameRules` — the engine object IS the handle: `get/
getBoolean/getInt/names/contains` (any thread, the snapshot contract)
+ `set` (owner context, the loud refusal).

**The cauldron race fixed:** CauldronAcceptanceTest's douse arm joined
the walker straight INTO the cauldron cell — the ignite and the same-
tick douse collapsed the burn window to a sub-tick sliver the 50 ms
poll could never see (the suite-order flake the baseline exposed). The
join now lands beside the cauldron (the test's own documented intent:
"ignite, then place inside"); the assertions unchanged.

Tests: GameRuleAcceptanceTest (11) — the table 1:1, the command forms +
the extension arm, keepInventory's death keep, showDeathMessages'
suppressed broadcast + the loud twin, doMobLoot's withheld loot/XP +
the dropping control, doTileDrops' withheld drop, doDaylightCycle's
frozen day clock over a walking total clock + the resume, mobGriefing's
intact blocks in the wounded blast + the destructive control,
naturalRegeneration's held wounds + the resumed control, the ZGR
restart round-trip (three typed pairs over the same world dir), the
any-thread reads + the off-lane set refusal + the on-lane set. The
neighboring suites green (block update, weather, lifecycle, mining XP,
body survival, silverfish, enderman carry, rabbit, peaceful
difficulty, TNT, explosion API, cauldron, fire, command API, spawn
egg, event API, vehicles, dispenser, hopper window).

## Slice 24b — the vehicle spawn gate (§27.7's spawn family completes)

**The event:** `event.entity.VehicleSpawnEvent` — the cancellable
pre-commit gate over the mount family's spawn chokepoints: the public
VehicleKind + the position. A cancelled spawn consumes no id, spawns no
body, sends no wire frame — and the placing item is NOT consumed (the
same withholding semantics the BlockPlaceEvent family carries).

**The wiring:** VehicleManager's three spawn verbs (boat, minecart,
chest minecart) each fire the gate BEFORE the id consumption and the
construction — the manager gained the event-bus field + setter, the
engine wires it beside the other §27.7 gates. The item-placement path
(EngineServer's spawnVehicleUseOnTick) now withholds the item when the
gate refuses (the null return skips consumePlaced); the
structure-seeded chest carts' loot fill was already null-safe (the
instanceof arm skips a cancelled spawn's empty hand).

Tests: VehicleSpawnEventAcceptanceTest (2: the boat placement — the
kind/position reads, the empty world, the item withheld, the
uncancelled twin with the item paid; the manager verb — the null
return, the no-id contract, the uncancelled twin carrying the chest
kind). The neighboring suites green (vehicle manager, entity family,
the spawn-event batch, the mineshaft structure seeding).

## Slice 25a — the zombie villager 1:1 (the infect/cure/convert family)

**The reference:** `ZombieEntity.java`'s own conversion family — the whole
cure loop vanilla runs.

**The infection (onKill lines 352-378):** a zombie's killing blow on a
villager converts the victim — NORMAL carries the reference's own early
coin (`nextBoolean()`), HARD always infects, EASY and below never. The
staging lands deep inside the melee walk (`hurtByMob`'s die-moment arm),
so the replacement defers to the tick loop's close: the villager leaves
immediately (`removeEntity` — no death-animation window), a villager-skinned
zombie takes its place at the same position and rotation (baby for a
baby — `setBaby(true)`), and the **1016 world event** sounds (the client
plays `mob.zombie.infect` at volume 2.0 from the Effect packet itself —
the engine's new generic `sendWorldEvent`, chunk-gated like the spawner's
2004).

**The cure (interactMob lines 457-480 + setConversionTime 482-488):** the
plain golden apple on a WEAKENED villager zombie (the splash potion's
famous recipe) arms the conversion — `nextInt(2401) + 3600` ticks —
clears the Weakness, installs the Strength for the whole window (the
reference's own `Math.min(difficultyId - 1, 0)` amplifier — the observed
0 on every difficulty that can carry the arm), and fires the entity
event 16 (the client plays `mob.zombie.remedy` at the body). The
interact dispatch consumes the apple through the shared `eatFromHand`
(the creative hand spares the stack); a refused cure (no weakness, no
apple, the plain zombie, a dead body) never consumes and never blocks
the arm's return.

**The countdown (tick 236-243 + getTicksUntilConversionDecrement
540-563):** one decrement per tick, and a 1%-per-tick roll scans the
8-cube around the body for **iron bars and beds** — every found cell (up
to 14 counted) adds a 30% chance of one more decrement, the famous
iron-bars cure accelerator. The walk rides the WorldQuery's new
`isIronBarsOrBed` (the engine's query maps the live block; the stub
worlds answer false). The completing tick hands the conversion to the
manager.

**The completion (convertToVillager 519-537):** a fresh villager takes
the zombie's place (the construction's own `nextInt(5)` profession roll —
the reference's `initialize`), a baby zombie converts into a baby
villager (`setBreedingAge(-24000)`), the newcomer carries the 200-tick
NAUSEA through the PotionSystem, and the **1017 world event** sounds
(`mob.zombie.unfect`).

**The despawn exemption (canDespawn line 511):** the converting body
never leaves — the cure's countdown outlives every despawn window.

**The natural-spawn arm (initialize line 401):** the 5% villager-skin
Data roll rides `spawnNatural`'s zombie arm.

**The wire:** the zombie's own metadata block — the slot-12 baby byte
(the zombie's own 1, not the passive family's -1), the slot-13 villager
byte, the slot-14 converting byte — in the spawn view, and the Set
Entity Metadata pair republished when the cure arms
(`onZombieStateChanged` → `sendZombieState`).

Ledgered (their own slices): the chicken jockey (the baby's 5% ride —
the mob-riding-mob family), the zombie's reinforcements/leader/
door-breaking initialize rolls (the zombie-attributes slice), and the
converted villager's `setConvertedZombie` reputation arm (the
village-detection slice).

## Slice 25c — the MobDataStore extension (ZMD v2 — the state block)

The reference's own NBT fields now survive restarts: the format gains a
version-2 state block per record — a flags byte then, per bit, the
kind's own family. Bit 0 the zombie's IsVillager + ConversionTime; bit 1
the villager's Profession/Career/CareerLevel/Riches quartet; bit 2 the
rabbit's coat byte (the killer's 99 survives restarts); bit 3 the
breeding-age int (babies of every kind survive restarts — the signed
int, negatives ride writeInt); bit 4 the skeleton's wither variant (with
its stone sword); bit 5 the slime family's size. Version 1 files still
load (every record answers the bare no-extras shape); the v1 round-trip
equality holds (a zero-flags block reads back as the null extras).

The capture walks `snapshot()`'s new `stateExtrasOf` (only the fields
the kind reads land in the record — the reference's own
writeCustomNbt dispatch); the restore lands each field on the kind that
reads it (`restoreStateExtras` — the zombie's `ConversionTime > -1`
gate included). The villager's trade state restores through the new
package-private `restoreVillagerTradeState` (the stock re-rolls lazily
on the first open, the reference's own `traderOffers == null` shape).

Tests: ZombieVillagerConversionTest (10) + MobDataStoreTest grew to 6
(the v2 state-block round-trip through the file + the restored state
landing on the engine bodies). Fresh suite: 954 core tests, the two
documented compute-drain load flakes (RangedCombat/Hopper) green in
isolation; the protocol suite 77 green.

## Slice 25b — the fireball deflection family (the punch that turns the blast around)

**The reference:** `ProjectileEntity.takeDamage` lines 248-273 (the fireball
parent's own arm — both the ghast's large fireball and the dispensed fire
charge carry it) + the collision scan's shooter window (line 119's
`inAirTicks >= 25` against `ThrownEntity`'s `ticksInAir >= 5`).

**The deflect walk:** the player's punch on a fireball redirects it — the
velocity becomes the attacker's normalized look vector, the acceleration
follows at `look * 0.1` (the reference's own velocity*0.1), and the puncher
becomes the NEW SHOOTER: the deflected blast credits the player, and the
shooter-immunity window flips with the reassignment — a fireball past its
25 air ticks now ignores the player and can strike the ghast that loosed
it, the famous deflect-kill.

**The landing sites:** `ProjectileManager.deflect` (the fireball kinds
only — the thrown family and arrows answer false exactly the reference's
takeDamage surface), `ProjectileEntity.deflectBy` (the velocity +
acceleration + shooter writes; the thrower field grew mutable for the
re-credit), and the melee dispatch's fireball branch (the vehicle arm's
own shape: the reach window over the fireball's pick radius, the look
vector through the same swapped-trig walk the launches carry, and the
landed attack's 0.3 exhaustion). The wire re-publishes the velocity (Set
Entity Velocity 0x12 — the reference's velocityChanged flag → the
tracker's packet) so the client's own fireball model turns around with
the punch; the per-tick teleports carry the trajectory from there.

**The immunity window split:** the engine's single 5-tick
THROWER_IMMUNITY grew kind-dependent — the fireball family's own 25
(`ProjectileEntity` line 119), the thrown family's 5
(`ThrownEntity` line 165), selected at both collision sites through
`throwerImmunityTicksOf`.

Tests: FireballDeflectionTest (4: the punch walk — the look velocity, the
0.1 acceleration, the shooter re-credit, the wire event; the
thrown-family refusals + the fire charge's family arm; the 25-tick
window — the shooter immune inside, the deflected strike landing at tick
26; the thrown family's 5-tick control). The neighboring suites green
(projectile manager, bow family).

## Slice 25d — the dimension-aware MobWorldQuery (every body reads its own world)

**The reference's model:** `Entity.world` — every body carries its own
world; the nether's pigmen, ghasts, magma cubes, and blazes read the
NETHER's blocks, not the overworld's at the same coordinates.

**The engine's pre-25d limitation closed:** the shared `MobWorldQuery`
read the overworld for everyone (the documented engine-wide gap since
Slice 16d's nether spawn cycle).

**The design:** `MobEntity.dimension` (0/-1, the reference's
Entity.dimension) + the WorldQuery's new `setActiveDimension` default
(the stub worlds keep the single-dimension no-op) + the manager's
per-body flip before every walk (`world.setActiveDimension(mob.dimension())`
in both tick arms — the tick-lane-only mutable context). The engine's
query resolves every read through `activeWorld()` — the solidity pair,
the support, the fluids/lava/fire, the block identity, the pathing
material, the iron-bars/bed scan — and the scans filter by dimension:
`nearestPlayer`, `nearestPlayerView`, `nearestMobOfKind`, and
`nearestHostileMob` answer their own dimension's bodies only.

**The player walks:** the melee target (`nearestPlayingPlayer`) and the
despawn window's nearest-player scan filter by the body's dimension — a
nether body never measures an overworld player standing at the same
coordinates.

**The stamps:** `spawnNaturalNether` stamps -1; every spawner body
carries its spawner's own dimension (the 18e dimension model's spawn
arm); the ZMD v2 state block gained the dimension byte (bit 6 — a nether
pigman restores into the nether, never the overworld; the -1 lands as
the u8 255 and reads back through the signed byte).

**The wire:** the mob spawn and move packets gate on the observer's own
dimension (`connection.dimensionOfSession() != mob.dimension()` — the
same coords exist in both worlds; a nether body never spawns for an
overworld observer, whatever the chunk view holds).

Tests: DimensionAwareMobQueryTest (4: the per-body flip through the
recording stub + the nether stamp; the dimension round-trip through the
store + the restore; the bare body's default; the LIVE ENGINE walk — a
netherrack platform in the nether holds a nether-spawned pigman whose
support read answers from the nether's blocks). The neighboring suites
green (nether monsters, the conversion family, the store, the manager,
the dimension walk, the spawner, the integration).

## Slice 25e — the hopper's cart arm (the rolling container resolves)

**The reference:** `HopperBlockEntity.getInventoryAt` lines 466-472 — the
entity fallback: when no block container sits at the cell, the 1×1×1 box
around the cell's center scans the INVENTORY entities and picks a RANDOM
one — the chest minecart parked on the hopper (or at its facing side) IS
the container.

**The engine's arm:** `containerSlotsAt`'s new fallback (after every
block-container branch) — the wired `cartLookup` scans the chest
minecarts inside the cell's box (the engine's own 1×1×1 mirror), the
random pick matches the reference's own `list.get(random.nextInt(...))`,
and the 27-slot view rides the SAME `chestSlot` adapter the block chest
uses (the cart's `ChestBlockEntity` IS the engine's own 27-slot
container since Slice 21a — zero new click semantics). Both arms inherit
the fallback through the shared resolution: the pull-above and the
push-facing.

Tests: HopperSystemTest's cart arm (the live engine: a chest minecart
standing on the hopper drains into it one item per transfer — the whole
stack and the cart's own slot emptying). The neighboring hopper suites
green (the window, the persistence).

## Slice 25f — the mineshaft's cave-spider spawner verified (the stale ledger row corrected)

The corridor's cobweb-and-spawner pair (MineshaftPieces lines 327-341) was
ALREADY live in the engine — the section walk places the mob-spawner block
and the typed CAVE_SPIDER record through the pending-spawner drain — but
the ledger still carried it as "ledgered with the mob-spawner block entity
slice" and the class javadoc said "rides the mob-spawner slice." The
corrections: the stale javadoc fixed, the ledger row graduated, and the
missing COVERAGE landed — MineshaftStructureTest's new walk probes seeds
until a start's generated chunks carry a pending spawner record, asserting
the CAVE_SPIDER type and the block cell (every cobweb corridor rolls one —
~3% of corridors are cobweb corridors and a start's recursive walk carries
many, so a few seeds always answer).

## Slice 26 — the §28.8 adversarial contract family + the §28.1 storage lane

The master spec's new §28 (plugin concurrency, deadlocks, and lifecycle —
`74ef313`, strengthened by `08a1aff`) demanded its twelve adversarial
proofs and the fourth execution category. This slice lands the engine-side
half: the §28.8 list over the engine's own contracts, and the blocking-I/O
lane §28.1's category 4 requires before any plugin ever runs on them.

**The same-pool self-wait detector (§28.2/§28.8 case 2):** the one real
implementation gap. `DomainWorkerPool.dispatchAndJoin` called from one of
the pool's OWN workers parked on the completion latch while the
waited-for jobs needed the very capacity the waiter occupies — a
guaranteed dependency cycle on a small pool or a wide batch. The fix: the
pool tracks its live worker identity (a concurrent set, registered at
construction, removed in the worker loop's finally) and refuses the call
loudly — `IllegalStateException` naming the cycle and the §28.2 rule —
with the refusal counted in its own telemetry (`refusedSelfWaitCount`).
The refusing worker stays live: one mis-built reentrant dispatch degrades
into a diagnosable refusal instead of a silent hang, and the pool serves
the next dispatch.

**The blocking-I/O lane (§28.1 category 4 — the storage lane):**
`BlockingIoLane` — the compute subsystem's own mailbox pattern for the
blocking world: database/filesystem/HTTP work runs on its own bounded pool
(`torch-io-N`, sized max(1, cores/4) capped 4), never a simulation worker,
never the compute pool; the submission captures the owning domain and its
generation; the completion lands in the per-domain mailbox and the owner's
`drainCompletions` applies fresh ones inside its validated context and
discards stale ones through the registered fallback (the §6.3 rule). The
§28.9 observability read: `pendingDeliveries(domain)`. Admission is
bounded and loud (capacity 128 default, explicit refusal); `tryCancel`
works while queued; a failing task's result is discarded and never kills
the worker; shutdown cancels everything queued and interrupts the
in-flight calls (the lane asks blocking work to be interruptible by
contract; a task that swallows interruption simply completes and flows
through the drain).

**The public surface (§28.3's strengthened rule — exposure is mandatory):**
`concurrent.BlockingIo` (the AsyncCompute-shaped interface:
`submit(operation, work, onFresh, onStale)`, the counters, the
cancellable `Task` handle) + `BlockingIoView` over the real lane +
`Server.blockingIo()` + `EngineServer.blockingIo()` — the lane constructed
at boot, its per-tick `drainCompletions` wired into the ticker AFTER the
compute drain (the same owner boundary), and its shutdown ordered after
the compute shutdown. The capability ledger row rides §5's compute row.

**The §28.8 adversarial list — `AdversarialContractTest`:**
- case 1 (reciprocal owner requests): two domains' concurrent drains each
  submit an intent to the OTHER through the router; both apply at the next
  drain exactly-once, in the TARGET's own context, neither blocks — the
  one-way intent model is the proof;
- case 2 (same-pool self-wait): the mis-built reentrant dispatch refused
  loudly in bounded time, the unrelated batch-mate still ran, the pool
  stays healthy, the telemetry counted the refusal;
- case 9 (the noisy owner): a domain floods its own admission past the
  bound (four loud refusals + `rejectedSubmissions` telemetry) while the
  quiet domain's work completes inside the noisy drain's own window on a
  different worker — no shared fate;
- case 10 (blocking I/O pressure): three parked blocking tasks on
  `torch-io-*` threads while the simulation runs its ticks on
  `zamin-domain-*` threads — the disjoint thread families are the direct
  proof; after the release, the completions land at the owner's drain;
- case 12 (shutdown): the full stack — scheduler, router, compute, lane,
  pool — with work in-flight and queued everywhere, shut down in order;
  every cancellation policy asserted (scheduler cancels queued, router
  cancels queued, lane cancels queued AND interrupts both in-flight
  calls), no cycle (the test's own timeout is the proof);
- case 6 (disable during execution): recorded as the concrete blocker the
  spec allows — no plugin lifecycle exists yet (the §28.5 state machine
  lands with the plugin loader slice); the engine-side equivalents
  (cancel-is-permanent, tryCancel-while-queued, shutdown-cancels-queued)
  are covered by their own suites. Cases 3/4/5/7/8/11 were already
  covered by OwnershipDomainTest, OwnershipMigrationTest,
  EventBusUnitTest, RegionBoundaryTest, and DomainWorkerPoolTest.

Tests: BlockingIoLaneTest (8: the owner's-drain delivery + the
never-on-the-worker proof, the stale discard through the fallback, the
drain context demand, the loud admission bound, the failure isolation +
the survivor, the shutdown cancel/interrupt pair, tryCancel while queued)
+ BlockingIoApiAcceptanceTest (2: the tick-thread delivery with the
lane-thread proof + the generation; the queued-cancel + the failure
isolation + the survivor) + AdversarialContractTest (5).
Fresh suite: 999 core tests (the two load flakes — the hopper restart
walk and the airborne mining timing — green in isolation under the
2-core full-suite load), 19 api + 78 protocol green, `--max-workers=1`.

## Slice 27a — the note block family 1:1 (the music block)

**The reference:** `NoteBlock` (register line `noteblock` id 25,
`setStrength(0.8F)`, Material.WOOD) + `NoteBlockBlockEntity` (the pitch
byte 0-24 with the reference's own read clamp, the runtime powered flag,
writeNbt persisting "note" alone) + the master registry's material
assignments (Block.java's registrations — the evidence for the instrument
table).

**The three play arms 1:1:** the USE (NoteBlock.use lines 41-56 — the
tune `(note + 1) % 25` then the play, wired into the engine's use
dispatch before the placement proposal; the tune persists through the
ZND store at saveAllNow); the survival ATTACK (NoteBlock.startMining
lines 58-67 through ServerPlayerInteractionManager line 133 — creative
never fires it; the engine's new mining-start listener rides after the
reach gate, so the note plays exactly when the reference's manager would
call it); and the POWER EDGE (NoteBlock.neighborChanged lines 26-39 over
the cross-system neighbor bus: only the RISING edge plays, the falling
edge clears the flag silently, the re-rise replays — the lever walk the
TNT test uses proves the bus).

**The play itself:** the air-above gate (Material.AIR over the engine's
registry — only the air type carries it); the instrument from the block
BELOW's material over the reference's OWN assignments — the engine's
registered set classified (the quirks preserved: gravel and soul sand
are SAND material — snare; the infested family is CLAY — harp; glowstone
and the sea lantern are GLASS — hat; the nether brick fence is STONE at
line 1236 — bass drum; wool/beds CLOTH and torches/ladders DECORATION —
harp; the note block itself WOOD — bass guitar); and the reference's
server walk — the doEvent sound ("note." + instrument, volume 3.0F,
pitch 2^((note-12)/12)) through the FX bus plus the Block Action 0x24
relay (block id 25, the instrument as the action byte, the note as the
param byte, chunk-gated per connection — the doBlockEvents walk the
chest lid's writer already rode).

**The landing sites:** `server/noteblock/NoteBlockSystem` (the tiles +
the arms + the material table + the note-action listeners) +
`NoteDataStore` (the ZND format — noteblocks.bin, the hopper store's own
durability rules) + the block/item registrations (NOTE_BLOCK both forms
sharing legacy id 25, the behavior row hardness 0.8/drops itself, the
light row a full opaque cube filter 15 — the registry pin moved to 181)
+ `BlockInteractionService.setMiningStartListener` (the dig-start hook)
+ the EngineServer wiring (the world-change listener, the neighbor-bus
registration, the break cleanup, the use arm, the saveAllNow arm) +
`V18Connection.sendNoteAction` over the chest lid's own layout.

Tests: NoteBlockSystemTest (6: the material table's instruments + the
reference's sound keys; the use arm's tune-and-play with the exact pitch
walk + the 25-band wrap; the attack arm's no-tune play; the power edge's
rising-only replay with the re-rise; the air gate with the
still-tunes-while-covered rule; the tile lifecycle + the ZND round trip
+ the corrupt-file preservation). BlockRegistryTest's pin moved to 181.

## Slice 25g — the vehicle persistence (the ZVD store)

**The gap:** the carts never carried a store — a restart lost every
minecart and boat (and the chest minecart's cargo), while the mobs, the
chests, and the furnaces all survived.

**The store:** `VehicleDataStore` (vehicles.bin, the ZVD format) — the
kind, position, yaw, remaining health, and the chest minecart's 27-slot
cargo in the ZCD item form; the atomic-write/quarantine durability
rules of every store, saved at saveAllNow.

**The arms:** the manager's `snapshot()` (the live population's
persistable view, broken bodies excluded) and `restoreAll` (the spawn
verbs ride the §27.7 gate like every arrival, unknown kinds drop, the
health lands back clamped inside the family's own 4.0 ceiling, the
cargo refills the cart's chest slot-by-slot).

Tests: VehiclePersistenceTest (1: the whole family — the chest
minecart's position and cargo, the wounded minecart's health carry, and
the boat — survives the shutdown-restart cycle on the live engine).

## Slice 27b — the jukebox 1:1 (the record player)

**The reference:** `JukeboxBlock` + its `JukeboxBlockEntity` +
`MusicDiscItem` (Block.java line 1181's `register(84, "jukebox", ...)`
— strength 2.0, blast resistance 10.0, STONE sounds; Item.java lines
735-746's twelve `MusicDiscItem`s, ids 2256-2267, maxStackSize 1).

**The blocks:** `jukebox` (legacy 84, metadata 0) + its HAS_RECORD
variant `jukebox_record` (metadata 1 — the reference's own
`getMetadataFromState`: has_record ? 1 : 0) over the shared id; the
behavior rows (hardness 2.0, drops itself — the record ejects through
the break hook, not the drop table), the light rows (opaque), and the
twelve disc items with their legacy ids in the wire's table.

**The four arms:** the INSERT (MusicDiscItem.useOn lines 28-43 — a disc
against a record-less jukebox stores the count-1 copy, flips the
variant, broadcasts the world event 1005 carrying the disc's item id,
and consumes one from the hand through the engine's creative-free
convention); the EJECT (JukeboxBlock.use lines 30-38 over removeRecord
lines 51-72 — the stop event, the drop, the flip back; a record-less
jukebox answers false and the click falls through to the item arms, the
reference's own order: block.use first, then item.useOn); the BREAK
(onRemoved lines 74-78 -> the same removeRecord — the record ejects
with the stop event before the block's own drop); and the COMPARATOR
READ (getAnalogSignal lines 97-108 — `Item.getId(record) + 1 -
Item.getId(RECORD_13)`: the disc's table index + 1, 13 -> 1 through
wait -> 12, 0 with no record).

**The wire:** the same Effect packet (0x28) carries both directions of
the reference's world event 1005 — the disc's item id plays (the
client's own "records." + name walk, WorldRenderer lines 1923-1929), 0
stops — chunk-gated like every world event, through the record
listener family (the adapter resolves the legacy item id at the
boundary).

**The persistence:** the ZJD store (jukeboxes.bin — the position plus
the disc in the ZCD item form, the reference's writeNbt "RecordItem"),
saved at saveAllNow; the restore lands the record back in the slot and
flips the HAS_RECORD variant when the block sits there.

Tests: JukeboxSystemTest (6: the disc table's reference order + the
family predicates; the insert arm's copy/flip/play + the useOn gates —
a playing jukebox never takes a second disc, a disc against anything
else never inserts; the eject arm's stop/drop/flip-back + the
record-less fall-through; the break arm's stop/drop/slot-death; the
comparator's disc-index math over three discs and the empty read; the
ZJD round trip + the restore flip + the corrupt file's quarantine).


## Slice 27c — the daylight detector 1:1 (the sky-light power band)

**The reference:** `DaylightDetectorBlock` +
`DaylightDetectorBlockEntity` (Block.java lines 1323/1377 —
`register(151, "daylight_detector", ...)` and
`register(178, "daylight_detector_inverted", ...)`: strength 0.2, WOOD
sounds, Material.WOOD, the 0.375-high non-cube shape).

**The blocks:** the flattened family — `daylight_detector_p0..p15`
(legacy 151) and `daylight_detector_inverted_p0..p15` (legacy 178), the
POWER property 0-15 riding the WHOLE metadata nibble
(getStateFromMetadata: POWER = metadata — the dial the client renders);
the item form 151 (the inverted form has none — the use arm toggles);
the behavior rows (hardness 0.2, both forms drop the NORMAL detector
item — getDropItem's own rule); the light rows (the sensor band passes
light untouched, isSolidRender false).

**The math chain (the reference's own walks, verbatim):** the time
fraction (Dimension.getTimeOfDay: the -0.25 shift, the wrap, the cosine
easing `f1 + (f - f1)/3`); the sun angle (the fraction times 2*PI); the
ambient darkness (calculateAmbientDarkness: the clamped cosine band, the
rain and thunder gradient scalings `1 - g*5/16`, times 11); the power
(`round((skyLight - darkness) * cos(angle))` with the detector's own
night-wrap `f += (f1 - f) * 0.2` pulling the night angle toward the
horizon), clamped 0-15, the inverted form's 15 - i.

**The arms:** the 20-tick BE gate (the Tickable's own
`world.getTime() % 20 == 0` over the engine's timeOfDay clock — a frozen
doDaylightCycle freezes the detector with it, the reference's own
behavior); the variant flip ONLY when the level changed (the
setBlockState-if gate); the use arm's invert toggle keeping the POWER
level with the immediate re-update; the weak emission (getSignal's POWER
in EVERY direction — no dir gate) through the redstone system's own
consult, with NO strong arm (the reference overrides getSignal alone)
and the family excluded from the signal-solid re-radiation set.

Tests: DaylightDetectorSystemTest (3: the math chain's anchors — the
noon/midnight fractions, the clear/storm darkness bands (the saturated
midnight never moves), the power walk's extremes + the inverted mirror +
the dawn mid-band; the live 20-tick walk — the noon lift to p15, the
weak-emission consult, the midnight drop, the use arm's inverted-midnight
flip to inverted_p15, the break cleanup + the re-place; the family
predicates + the band wrap).

## Release automation — the torch-1.8.8-N era

**The pipeline** (`.github/workflows/release.yml`): every push to `develop`
now cuts the next numbered dev build automatically. The run computes N over
BOTH tag ledgers — the legacy `v0.2.0-dev.N` family and the new
`torch-1.8.8-N` family, one continuous numbering (dev.25 →
torch-1.8.8-26) — stamps the version into `distVersion`
(zamin-launcher/build.gradle.kts) and `UpdateChecker.BUILD_VERSION` in the
workspace, syncs the bump back to develop as a `[skip ci]` commit BEFORE
building (so the jar is always built from the exact tree that gets tagged,
even when a rebase pulls newer commits mid-run), runs the full suite with
one retry for the three documented load flakes
(LifecycleEvent/HopperSystem/HopperWindow), tags `torch-1.8.8-N`
(lightweight, matching the v0.2.0-dev.* family), and publishes the fat jar
+ server zip as a prerelease through gh. Release notes are generated from
the commits since the previous tag; the title comes from a workflow_dispatch
input or a `Release-Title:` trailer on any commit in the range.

**The rename bridge:** the artifact scheme moves from
`zamin-server-0.2.0-dev.N.jar` to `torch-1.8.8-N.jar` (tag
`torch-1.8.8-N`, fat jar `torch-1.8.8-N.jar`, zip
`ZaminTorch-server-1.8.8-N.zip`, manifest `Implementation-Version`
`1.8.8-N`). UpdateChecker now reads both ledgers — `torch-1.8.8-N` tags
against `1.8.8-N` builds, the legacy `v0.2.0-dev.N` tags still parsing so
any torch build orders above every legacy one — and consults the releases
LIST endpoint (`/releases?per_page=1`) instead of `/releases/latest`,
because /latest cannot see prereleases and every automated release is one;
the atom feed remains the rate-limit-free fallback. Fielded dev.2–dev.25
builds parse the new tags as noise and stay silent (the accepted cost of
the rename). UpdateCheckerTest pins the ordering bridge (9 cases: prefix
non-bumps, numeric comparison, the torch-above-legacy rule, the legacy
internal order, garbage/null silence).

**The identities and the safety rails:** every pipeline commit, tag push
and release is authored as Abasing through the encrypted `ABASING_TOKEN`
repo secret — never `github-actions[bot]`. The bump commit's `[skip ci]`
breaks the self-retrigger loop; the concurrency group serializes releases;
a build/test failure pushes no tag and cuts no release (the next run reuses
the number — the already-pushed bump commit becomes the next release head);
a run that dies between tag and release is resumed by the orphan-tag repair
path; re-running a published run is a no-op. `scripts/release.sh` is the
local gh companion: watch the latest run, dispatch a titled off-cycle
release, print run/release status.

**Fresh suite at this commit:** 10 launcher tests (UpdateCheckerTest),
green.
## Slice 28a + 28b — the decoration family 1:1 (the item frame + the painting)

**The gap:** the worklog's own survey (Slice 26) named the item frame and
the painting among the real remaining vanilla gaps — neither existed in
the engine's source tree.

**The system:** `server/decoration/DecorationSystem` — the shared
registry + the reference port of entity/decoration/{DecorationEntity,
ItemFrameEntity, PaintingEntity} + item/DecorationItem:

- **The placement** (DecorationItem.useOn lines 21-42): the DOWN/UP
  faces refuse outright; the body lands at the clicked face's offset
  cell; the canSurvive gate (the solid wall + the clear box + no
  overlapping decoration) refuses WITHOUT consuming the stack; a landed
  body consumes one unit (the engine's creative-free convention). The
  frame's facing = the clicked face's own direction (the art hangs ON
  that face, pointing away).
- **The survival walk** (DecorationEntity.canSurvive lines 93-120): the
  wall scan walks counter-clockwise by width/16 and up by height/16
  behind the body (the solid-or-diode gate — repeaters and comparators
  mount art); the hanging box carries updateShape's own math (the
  0.46875 wall inset, the even-pixel centering `length % 32 == 0 ? 0.5
  : 0.0` with the height's own lift, the half-extents width/32 along
  the wall + 1/32 into the room — the reference's d6/d8 axis swap);
  solid blocks inside the box refuse; another decoration's box inside
  refuses.
- **The 100-tick survival poll** (DecorationEntity.tick lines 80-91): a
  broken wall leaves the body hanging up to five seconds — the
  reference's own cadence — then the sweep runs the break's drops
  through the null-attacker arm.
- **The frame's interact** (ItemFrameEntity.interact lines 180-194):
  the insert takes the SIZE-1 COPY of the held stack
  (setDisplayItem's own rule), a loaded frame turns one step through
  the rotation % 8 byte wrap, both arms wake the comparators
  (updateNeighborComparators over the frame's cell).
- **The frame's attack** (takeDamage lines 40-55 +
  dropItemOrItemFrame lines 79-100): a non-explosive hit on a loaded
  frame EJECTS the item and keeps the frame on the wall (the creative
  attacker removes it silently); anything else breaks the frame off the
  wall with the frame item + the loaded item both dropping (the
  creative hand sparing both, the doEntityDrops rule gating all).
- **The painting's fit roll** (PaintingEntity's ctor lines 21-38): every
  motive that survives the wall collects, one lands at random — over
  the reference's own twenty-six-entry Motive registry verbatim (KEBAB
  through DONKEY_KONG, the pixel sizes, the atlas offsets, the
  SKULL_AND_ROSES_LENGTH constant, the readCustomNbt KEBAB fallback).
- **The painting's attack** (onAttack lines 87-98): the body falls, the
  painting item drops, the creative hand spares it.
- **The comparator's through-the-wall read** (ComparatorBlock.
  getInputSignal lines 108-128): the cell one beyond the comparator's
  input block, when it holds air and a frame facing the comparator's
  own direction, answers the frame's getAnalogSignal (rotation % 8 + 1,
  0 when bare) — wired into the engine's comparator input walk through
  the new `setFrameReader` hook beside the deep container read.

**The wire:** the frame's Spawn Object 71 with the horizontal facing
riding the objectData int and the RAW wall-cell corner (TrackedEntity
lines 493-502's own override — blockPos * 32, no +0.5), the yaw byte
the reference's facing*90 walk, the metadata pair (the displayed stack
at slot 8 type 5, the rotation byte at slot 9) riding the spawn + the
two Set Entity Metadata change arms; the painting's own Spawn Painting
packet 0x10 (AddPaintingS2CPacket's write: varint id, the motive's
registry string, the packed block long, the facing byte); both
families' Destroy + the join-view fan-out (chunk-gated, the knot's own
shape). The items: ITEM_FRAME (legacy 389) + PAINTING (legacy 321).

**The persistence:** the ZED store (decorations.bin, magic Z/E/D/1 —
the frames' position/facing/stack-in-the-ZCD-form/rotation + the
paintings' position/facing/motive; the jukebox store's own durability
rules), saved at saveAllNow, restored at boot.

Tests: DecorationSystemTest (7: the motive registry's reference table +
the lookup; the placement gates — the vertical faces, the wall, the
solid cell, the overlap refusal; the interact ladder — the bare click,
the size-1 copy insert, the rotation walk through the wrap, the
comparator consult both facings; the attack ladder — the eject keeping
the frame + the diamond's drop, the break dropping both, the creative
silence; the painting's fit roll over the two-tall one-wide wall + the
break; the survival poll's grace + sweep on the broken wall; the ZED
round trip — the stack + rotation + facing + motive).

## Slice 28c — the armor stand 1:1

**The gap:** the worklog's own survey (Slice 26) named the armor stand
among the real remaining vanilla gaps — the entity, its equipment
surface, and its item did not exist in the engine's source tree.

**The system:** `server/armorstand/ArmorStandSystem` — the reference
port of entity/living/ArmorStandEntity + item/ArmorStandItem:

- **The placement** (ArmorStandItem.useOn lines 22-68): the DOWN face
  refuses; a replaceable clicked cell stands there (the face offset
  otherwise); the cell AND the cell above must be free; no entity may
  occupy the 1x2x1 box; the yaw snaps through the reference's own
  `floor((wrapDegrees(yaw - 180) + 22.5) / 45) * 45`; the pose rolls
  (the head's 0-5 pitch + the ±10 yaw, the body's ±5 yaw).
- **The equipment swap ladder** (interactAt lines 273-342 + swapItem
  lines 344-365): the held armor claims its own slot (boots 1, legs 2,
  chest 3, head 4; the skull and the pumpkin claim the head), the empty
  hand takes the worn piece the click's own height names (the feet band
  [0.1, 0.55), the legs [0.4, 1.2), the chest [0.9, 1.6), the head
  1.6+, the small stand's offsets halved), the hand-slot needs the arms
  shown, and the swap rules carry the reference's own shape — the
  creative copy without paying, the single unit's full swap, the
  big-stack carve, the occupied refusal. **The wire's INTERACT_AT
  offsets now route into the engine** (the first consumer — every
  previous packet consumed them blind).
- **The melee break ladder** (takeDamage lines 412-429): the creative
  hand removes outright; the first hit stamps the five-tick clock (the
  wobble); the second hit inside the window breaks — the stand item at
  the feet plus every worn piece at the head cell.
- **The fire burn-down** (lines 388-400): the ignite (100 ticks), the
  4.0 on-fire toll per burning tick, the 0.5 floor breaking with ONLY
  the equipment dropping — the stand item never drops to fire.
- **The wire**: Spawn Object 78 at the stand's real feet position, the
  metadata pair (the status byte at slot 10 + the six Rotation triples
  at slots 11-16, the wire's type 7), the five Entity Equipment packets
  riding the spawn + the per-slot change arm, the Destroy, the join
  view. The item: legacy 416, the reference's own maxStackSize 16.
- **The persistence**: the ZAD store (armorstands.bin, magic Z/A/D/1 —
  position, yaw, the five slots, the status byte, the six pose
  triples), saved at saveAllNow.

Tests: ArmorStandSystemTest (5, all green — the placement gates + the
snap + the pose bands, the swap ladder's six arms, the melee ladder
with the aged-world wobble + the double-hit break + the creative
silence, the fire burn's equipment-only drop, the ZAD round trip). The
protocol suite 78 green (the conformance row for the interact-at
routing unchanged). Ledgered: the arrow one-shot (the projectile scan
walks mobs + players only), the EntityTag NBT, the NBT-only flags,
the gravity walk, the minecart push, the breaking particles.

## The unloaded-chunk state eviction repair (2026-10-14)

The long-ledgered `HopperWindowAcceptanceTest::theHopperContentsSurviveTheRestart`
failure (marked "verified failing on the clean 2fa30fb baseline" at Slice 28a)
rooted: the ZHD round trip was never broken — the store saved the hopper with
its emeralds and the second boot's `load()` read `count=1` back. The first
`tick()` then threw it away. Every block-entity family's per-tick sweep
(`hoppers.entrySet().removeIf(entry -> !isHopper(world.getBlock(entry.getKey())))`
and the same shape in the chest, furnace, dispenser, brewing-stand, sign,
spawner and daylight-detector systems) treats an UNLOADED chunk's air read as
"block gone" — and a fresh boot only loads the spawn area, so any restored
container outside it died on tick one.

- The fix: `EngineWorld.isChunkLoadedAt(BlockPosition)` + the eight sweeps now
  decide the block-gone discard only on a LOADED chunk. An unloaded chunk's
  state is FROZEN — the vanilla block-entity contract (a BE in an unloaded
  chunk neither ticks nor discards; it returns with the chunk).
- The frozen semantics ride the activity arms too: the furnace's smelt cycle,
  the brewing stand's brew cycle, and the hopper's cooldown + transfer all
  skip while the chunk is away (vanilla unloaded-BE behavior — the hopper's
  cooldown resumes where it froze, not reset).
- The spawner unit test's gone-block scenario now loads its chunk first (the
  creative-break shape — `setBlock` force-loads, the sweep decides on real
  air); the frozen-path spawners survive restarts outside the spawn area.
- Tests: HopperWindowAcceptanceTest (the restart test green for the first
  time since it landed), ContainerPersistenceTest, and the eight families'
  suites green.

## Slice 29a — the ender chest 1:1 (the per-player storage family)

The reference port of `block/EnderChestBlock` + `block/entity/
EnderChestBlockEntity` + `inventory/EnderChestInventory` — the storage model
vanilla actually uses: the placed block is only the DOOR, and every use opens
the player's OWN 27-slot grid riding their identity.

- The block family: `EnderChestBlocks` — legacy 130, four horizontal FACING
  states, the metadata nibble IS the reference Direction id (2/3/4/5,
  `getStateFromMetadata` lines 114-127, a Y axis clamping NORTH); the wire's
  `LegacyBlockIds` family + bare-item 130 with the damage-aware reverse fold.
- The placement verb: `getPlacementState` lines 63-65 — the placing player's
  horizontal facing OPPOSITE (the yaw-band walk), the chest family's own rule.
- The use arm: `use` lines 73-92 — the solid-above refusal at line 80, then
  the player's OWN grid opens (`ContainerKind.ENDER_CHEST`); the block is
  never the storage. The click routing is the chest layout over the session's
  private `ChestBlockEntity` grid with the door-presence liveness gate (a
  broken door rejects every click until the close arrives).
- The lid: the BE's own onOpen/onClose broadcast — Block Action 1 over legacy
  130 (the lid writer resolves the carrier from the world block, so the plain
  chest's 54 and the ender chest's 130 share one listener family).
- The wire: Open Window `minecraft:chest` + the Ender Chest title over the
  63-slot chest layout; the container-items and re-sync branches read the
  session grid.
- The drops: `getBaseDropCount` 8 obsidian under the diamond-tier harvest
  gate (the DropService family branch — silk takes the chest itself,
  `hasSilkTouchDrops`); the obsidian block's own behavior row
  (`setStrength(50F)` line 1103, diamond tier, drops itself) landed with the
  family.
- The light rows: the reference's `setLight(0.5F)` = emission 7 (the blue
  glow), the chest's filter-0 shape.
- The persistence: ZPD v8 — the reference's `EnderItems`, the 27 slots
  appended per identity at the file tail (older files load an empty grid);
  the join restore and the leaving save both walk the grid.
- Tests: `EnderChestAcceptanceTest` (3: the private-grid open + the
  click-through + the solid-above refusal + the world-store isolation, the
  identity-carried restart round trip, the obsidian/silk/no-tier drop walk).

Ledgered: the BE's 80-tick viewer-count re-broadcast (the client animates its
own lid from the open/close pair), the `randomDisplayTick` portal particles
(the particle slice), the open's `ENDER_CHESTS_OPENED` stat (the stats slice),
and the walk-away `isValid` close (the chest family's click-rejection
precedent).

## Slice 29b — the beacon 1:1 (the pyramid + the area effects)

The last open Not-started vanilla row: the reference port of
`block/BeaconBlock` + `block/entity/BeaconBlockEntity` +
`inventory/menu/BeaconMenu`.

- The state: `BeaconBlockEntity` — levels/primary/secondary + the 1-slot
  payment inventory (the reference's `isItemAllowed` filter + max-stack-1)
  and the `getBeaconEffect` six-effect gate.
- The walk: `BeaconSystem` — the 80-tick global-phase update
  (`world.getTime() % 80 == 0`), the neighbor-changed recompute, and the
  two arms 1:1: `updateLevels` (the beam walk's opacity-15 kill with the
  bedrock exception, through the engine's light table; the pyramid's
  inside-out full-square rings of the four minerals) and `spreadEffects`
  (the `levels*10+10` range box, the 180-tick instances over the engine's
  own `PotionSystem`, the level-4 same-effect amplifier-1 pair and the
  different-pair secondary arm).
- The pyramid's two missing minerals registered with the family: the
  diamond block (57) and the emerald block (133) — the blocks, the items,
  the iron-tier behavior rows, the light rows, the legacy ids.
- The GUI: `ContainerKind.BEACON` — the payment click routing over the
  shared `WindowClicks` with the filter + max-1, the close's payment drop
  INTO THE WORLD (BeaconMenu.close lines 46-54 — never back into the
  slot), and the three window properties (levels/primary/secondary)
  riding the open plus a per-tick viewer diff walk (the brewing pattern).
- The confirm: the MC|Beacon Custom Payload (the BeaconScreen Done
  button's two i32s) — the server's own handler: one payment unit
  consumes, the choices land through the gate, the unpaid confirm is the
  silent no-op.
- The wire: Open Window `minecraft:beacon` over the 37-slot layout, the
  0x35 action-3 tile (the reference's Primary/Secondary/Levels NBT) on
  every state change and on the chunk-send replay.
- The persistence: the ZBT store (levels/primary/secondary/payment per
  position, the chest store's durability rules).
- Tests: `BeaconSystemTest` (3: the pyramid walk + the beam kill + the
  bedrock exception, the payment + the selection + the 180-tick spread +
  the effect gate, the restart round trip).

Ledgered: the beam's stained-glass color sections are client-side state
(the engine's server model ends at levels), the ACTIVATE_MAX_BEACON
achievement rides the stats slice, the custom-hover-name carry rides the
anvil's slice.

## Slice 30a — the particle display arms (the ambient breeding hearts)

The long-ledgered pendingAmbientHearts stub (the Slice 5 breeding wave
carried it "awaiting a verified heart id") closes.

- The consult (the reference port of AnimalEntity.mobTick lines 42-61):
  one HEART particle every 10th love tick at a random point inside the
  body's footprint (`x + rand*width*2 - width`, `y + 0.5 + rand*height`,
  the same z), drifting on three fresh 0.02-sigma gaussians.
- The path: MobManager's love walk (`tickBreedingFor`) now consumes the
  flag and emits through the new `onMobAmbientHearts(mob, at, vx, vy, vz)`
  listener arm; EngineServer's dispatch forwards it; the adapter renders
  it per connection as World Particles in the **count-0 shape** (one
  particle at the exact position with the offsets as its velocity — the
  ServerWorld.addParticle form), over the new
  `V18Connection.sendDisplayParticle` helper with the 64-block feedback
  cull.
- The display family's wire ids landed with the helper
  (ParticleType.java's own table): splash 5, smoke 11, largeexplode 1,
  angryVillager 20, happyVillager 21, portal 24, heart 34 — the consult
  sites for the wolf's shake splash and the enderman's portal cloud ride
  their own slices.
- Tests: `ParticleDisplayAcceptanceTest` (1: the love feed over the wire,
  the heart's id/footprint/count-0 shape, the clock's repeat).

Ledgered: the block-crack family's damage-variant consults ride the
wool/stained-glass variant rows (Slice 30b grew the wool one); the
client-side randomDisplayTick family (portal particles, brewing bubbles,
mycelium spores) stays client-rendered in 1.8.8 — the server never calls
randomDisplayTick (verified: no server-side caller in the reference).

## Slice 30b — the dye family (the sheep's coat + the wolf's collar)

The reference port of SheepEntity + ItemDye's interact arm + WolfEntity's
dye arm, 1:1.

- The sheep's color state: `sheepFleeceColor` on MobEntity (the DyeColor
  id domain — the index-16 low nibble, the wool stack's damage, and the
  offspring-mix input), written to the wire as the grown kind byte (the
  color nibble | the sheared bit 0x10) on spawn, on shear, on regrow, and
  on the new dye change (the `onSheepFleeceChanged` listener arm).
- The natural roll (SheepEntity.pickColor lines 225-238): 5/100 black,
  5 gray, 5 silver, 3 brown, 1/500 pink, else white — the spawn init.
- The dye arms: ItemDye.interact on the sheep (an unsheared coat of a
  different color takes the dye, one unit consumed with NO creative
  guard — the reference's own shape; any dye-on-sheep use is consumed by
  the arm) riding the item-after-mob consult order
  (PlayerEntity.interact line 898); WolfEntity.interactMob's DYE arm on
  the tamed body (a different color takes the collar in survival, the
  same color falls through to the sit toggle — the reference's own
  ladder).
- The shear (Slice 30b repairs a real gap): the drops now actually spawn
  — 1-3 wool of the body's own color (every unit its own single stack,
  the damage IS the DyeColor id), the lamb gate added BEFORE the strip
  (a baby never shears — the pre-fix marked the coat without dropping).
- The death drops (SheepEntity.dropItems lines 97-111): the unsheared
  body carries exactly one wool of its color, then the mutton walk
  `nextInt(2) + 1 + nextInt(1 + looting)` with the fire's cooked swap —
  the dedicated arm replaces the generic rows.
- The offspring mixing (getColorForOffspring lines 262-276): both
  parents' colors move into the dye domain (15 - id), the two-dye 2x1
  grid goes through the new `setCraftingConsult(CraftingService)` — the
  nine two-dye mix pairs from DyeRecipes.java's registerShapeless set now
  live in BuiltinRecipes (the player crafting table gets them 1:1), and
  an unmatched pair falls to the parent coin.
- The persistence (ZMD): the kind's dye byte, FLAG_DYE_COLOR 0x80 — the
  sheep's fleece + 1 or the wolf's collar + 1 (the +1 shift keeps 0 the
  honest absent marker so a black collar at metadata 0 never aliases
  absence); old files load without it.
- Tests: `SheepDyeFamilyTest` (6: the pick bands, the spawn colors, the
  mix consult + the parent coin, the shear drops + the lamb gate, the
  death drops' color + the sheared skip, the dye byte round trip for both
  domains), `DyeFamilyAcceptanceTest` (1: the lapis dye over the wire —
  the fleece nibble, the one-unit consume, the blue wool drops),
  `CraftingRecipeTest` grew to 43 recipes / 10 shapeless.

Ledgered: the wool-dyeing crafting recipe (dye + white wool → colored
wool) rides the bone-meal item-model slice (the engine's separate
minecraft:bone_meal item vs the reference's dye-15 row); the carpet and
stained-glass recipes ride their families' slices.

## Slice 30c — the ambient sound families (the wolf's ladder + the cat)

- The wolf (WolfEntity.getAmbientSound lines 138-146): the angry body
  always growls; the 1/3 roll whines on a tamed body under 10 health and
  pants otherwise; the 2/3 barks. `effectiveIdleSound` now carries the
  dynamic ladder per chatter event.
- The ocelot (OcelotEntity.getAmbientSound lines 111-121): the untamed
  body stays silent; the tamed body purrs in love, else a quarter
  purreow over the meow.
- Tests: `WolfEntityTest` +2 arms inside the new ambient-ladder case (the
  weak-tamed whine/bark pair with the never-pants ternary, the
  healthy-tamed panting/bark pair), `OcelotEntityTest` +1 (the wild
  silence, the in-love purr, the meow/purreow family).

## Slice 30d — the wolf's water-shake cycle

The reference port of WolfEntity's own closing block (tick lines 193-222)
+ the mobTick start gate (lines 171-176), 1:1.

- The state machine (`MobEntity.tickWolfShake`, the manager's per-wolf
  consult after the body's tick): the wet re-arm while water-or-rain
  touches (the new `WorldQuery.isRainingAt` read — the rain flag up AND
  the point above the precipitation height at its column; the nether
  never rains), the start gate out of the water (a wet, grounded,
  navigation-at-rest body starts the shake with the event-8 broadcast),
  and the +0.05-per-tick walk from the cycle-start sound
  ("mob.wolf.shake", volume 0.4, the `(rand - rand) * 0.2 + 1` pitch
  band) to the 2.0 clear.
- The splash burst (tick lines 212-221): past the 0.4 bend, `sin((progress
  - 0.4) * PI) * 7` units per tick, each its own particle at a random
  point in the footprint at `minY + 0.8` riding the body's velocity —
  the new `onWolfShakeSplash` listener arm renders each as one World
  Particles unit in the count-0 shape (Slice 30a's helper).
- The event-8 broadcast and the sound ride the existing channels
  (`onMobStatusEvent` → Entity Status, `onMobSoundAt` → the named sound).
- Tests: `WolfEntityTest` +1 (the wet re-arm in water, the start gate out
  with the event/sound pair armed once, the 45-pass walk to the 2.0 clear
  with the splash band observed, the dry body never starting).

Ledgered: the client's own event-8 pose (the shake animation's roll) is
client-rendered from the broadcast — the engine's server model ends at
the cycle's phases.

## Slice 31 — the dye-coloring world families (the wool craft + the stained families)

The reference port of `DyeRecipes.java`'s full registration set + the
Block registrations 35/95/159/160/171/172, closing Slice 30b's ledger
note (the wool-dyeing craft "rides the bone-meal item-model slice" —
the bone_meal item landed with it, the coloring rows land here).

- The family model (`server/block/DyeFamilyBlocks`): every family is ONE
  vanilla item whose damage field carries the `DyeColor.getId()` id (0
  white .. 15 black — the BLOCK-metadata domain, not the dye item's
  inverted one), expanded into flattened per-color blocks whose wire
  nibble IS the color. The white wool keeps the plain `minecraft:wool`
  id (every placed white wool in existing worlds stays valid); the
  temple slice's orange/yellow stained clay pair joins its family as its
  own two members.
- The 78 new blocks: the 15 non-white wool members (legacy 35), the
  stained glass sixteen (95), the stained panes sixteen (160), the
  carpets sixteen (171), the stained clay's 14 new members (159),
  hardened clay (172). The five family items register with their block
  ids (the wool item's own damage model, Slice 30b's fleece domain).
- The behavior rows (the reference's own registrations): wool 0.8 CLOTH
  (shears accelerate), the glass pair 0.3 SILK-ONLY (getBaseDropCount 0
  / PaneBlock's hasDrops false — the plain hand gets nothing, silk takes
  the family item with the color damage), carpet 0.1 its own CARPET
  material (shears accelerate WOOL, never CARPET — the reference's own
  material split), the clays 1.25 STONE (the pickaxe tier-0 harvest
  gate). Every drop carries the family item with the color on the
  damage field (the reference's getDropItemMetadata) — the BlockBehavior
  Drop record grew the damage field, and the temple pair's
  unregistered-item latent crash (the pre-31 row referenced
  `minecraft:stained_clay_orange` as an item) closed with it.
- The 82 recipes (`withDyeRecipes`, DyeRecipes.java 1:1): the 16
  shapeless wool colorings (dye 15-i + white wool -> wool i — the
  inverted dye-item domain), the 16 stained-clay rings (8 hardened clay
  + dye i -> 8 stained clay 15-i), the 16 stained-glass rings (8 glass
  + dye i -> 8 stained glass 15-i), the 16 pane folds (6 stained glass
  i -> 16 panes i), the 16 carpet rows (2 same-color wool -> 3 carpets —
  the exact-damage gate refuses mixed pairs), and the two flower dyes
  the engine's flora carries (the dandelion's TWO yellow, the poppy's
  one red). CraftingRecipeTest 125 recipes / 28 shapeless.
- The damage-aware placement: the survival resolver consult grew from
  `ItemType` to `(ItemType, damage)` — ItemBlock.onItemUse's
  getStateFromMeta walk — resolving the family item to its color member
  (a lime wool stack places `minecraft:lime_wool`); the creative claim
  rides the damage-aware wire reverse (id 35 damage 5 -> the lime
  member's block), and the creative SLOT claim resolves the family ITEM
  with the damage preserved (the flattened member carries no item of its
  own — the no-meta reverse keeps the family items canonical).
- The carpet's support gates (CarpetBlock 1:1): the placement refuses
  only over AIR (water and torches carry it — `!isAir(pos.down())`,
  softer than the plate's solid bed) through the engine's use-dispatch
  arm, and the neighbor-changed pop (canSurviveOrBreak) drops the
  family item with the color and clears the cell (the BlockUpdateSystem
  rule, the torch pop's own shape).
- The world surfaces: the carpets never block a body (the 1/16 shape —
  the snow layer's own envelope, WorldSolidity's non-solid set), the
  note-block instruments (the clays STONE bass drum + hardened clay, the
  stained glass pair GLASS hat, the carpet's own Material.CARPET falls
  to the harp), and the light table (wool/clay opaque 15; the glass
  pair, the panes and the carpets pass light untouched — the carpet
  registration's own explicit `setOpacity(0)`).
- The wire: LegacyBlockIds' family merge (the 78 members over their
  five ids, the color riding the METADATA nibble whole, the five item
  forms beside them), the no-meta reverse EXCLUDING the flattened
  members (the family items stay canonical — the creative slot's
  plain-id fallback), and the chunk/block-change writers ride the
  existing metadataOf surfaces unchanged.
- Tests: `DyeFamilyBlocksTest` (10: the family model + the placement
  resolution + the predicates' guards, every member's behavior/light/
  drop rows over all sixteen colors, the colored drop walks + the
  pickaxe gates + the silk-only glass pair + the temple repair, the
  wool-coloring rows' inverted domain + the off-color refusal, the ring
  recipes' counts and damages, the pane fold + the carpet's same-color
  demand, the flower dyes' counts, the solidity + instrument tables,
  the five family items' damage domain) + `DyeFamilyPlacementAcceptanceTest`
  (2: the lime wool's place/break round trip over the live engine with
  the drop's damage read-back, the carpet's air refusal + the water
  survival + the pop's colored drop) + `DyeFamilyWireAcceptanceTest`
  (2: the creative claim's family-item resolution with the color damage
  + the placement's block change naming the family id, the stained
  glass member's wire round trip). Fresh suite: zamin-core 180 classes
  green (one pre-existing real-time flake — VanillaMiningTimingTest's
  airborne window passes in isolation, the chest test's own class of
  load flake), the protocol + api + launcher modules green,
  `--max-workers=1`.

Ledgered: the remaining flower/double-plant dye rows (the oxeye daisy,
the sunflower/lilac/rose/peony pair) ride their unregistered flora's
own slices; the beacon's beamSection color blend is client-side state
(the beam passes the glass members through the light table's opacity 0
— the client's own BE walks its world); the carpet's thin collision
shape rides the solidity stand-in (walk-through like the snow layer);
the panes' connection states are client-rendered (metadata 0, the
reference's own resolveVirtualProperties).

## Slice 32 — the mineral vault: the missing ores, the storage blocks, and three wire-id repairs

The reference port of `Block.java`'s own registrations for the mineral
family (the lapis/emerald ores, the lapis/redstone/coal/quartz storage
blocks, the brick/clay/hay/slime/end-stone/packed-ice building set) +
`MineralRecipes.java`'s storage crafts + `SmeltingManager`'s missing
smelts + three wire-id repairs found by the block-coverage audit (the
dropper's 125→158, the nether bricks' 40→112, and the gravel-flint
Fortune comparison that never matched).

- The 12 new blocks (`BuiltinBlocks`): lapis_ore 21, emerald_ore 129,
  lapis_block 22, redstone_block 152, coal_block 173, quartz_block 155,
  brick_block 45, clay 82, hay_block 170, slime_block 165, end_stone
  121, packed_ice 174 — every id the reference's own `register` rows.
- The 17 new items (`BuiltinItems`): the materials (clay_ball 337,
  brick 336, nether_brick 405 — Item.java line 709's own rows) + the
  block items sharing their block ids (Item.java's BlockItem walk).
- The behavior rows (`BlockBehaviorTable`): the reference's own
  strengths (lapis ore/block 3.0, redstone/coal 5.0, quartz 0.8, brick
  2.0, clay 0.6, hay 0.5, slime 0.0, end stone 3.0, packed ice 0.5) and
  PickaxeItem.canMineBlock's tier gates (the lapis pair stone-tier band
  2, the emerald ore iron-tier band 3, the plain stone-materials band
  1); clay drops its four balls (ClayBlock), packed ice drops nothing
  bare-handed (PackedIceBlock.getBaseDropCount 0) with the silk set
  extended to carry it and the new ores.
- The 1:1 drop walks (`DropService`): the lapis arm (OreBlock's
  `4 + nextInt(5)` base with the fortune multiplier `nextInt(f+2)-1`
  clamped, times the base) and the redstone arm (RedstoneOreBlock's own
  ADDITIVE `4 + nextInt(2) + nextInt(fortune+1)` — never a multiple).
  The pre-32 redstone_ore row dropped the ORE ITEM — the reference drops
  the DUST; corrected.
- The XP bands (`ExperienceAwards`): lapis 2-5, emerald 3-7, quartz 2-5
  (OreBlock.dropItems' own table).
- The smelts and fuel (`FurnaceRecipes`): the clay pair (ball→brick
  0.3, block→hardened clay 0.35), the ore smelts (coal 0.1, redstone
  0.7, lapis 0.2, diamond 1.0, emerald 1.0, quartz 0.2),
  netherrack→nether_brick 0.1, and the coal block's 16000-tick burn
  (FurnaceBlockEntity.getFuelTime's own row — sixteen smelts).
- The storage crafts (`BuiltinRecipes.withMineralRecipes`): the ten
  nine-of-a-kind pairs (gold/iron/diamond/emerald/lapis/redstone/coal/
  hay/slime + the gold ingot↔nugget tail) and the four 2x2 masonries
  (clay, bricks, quartz, nether bricks) — MineralRecipes' own loop
  shape, 24 shaped rows (CraftingRecipeTest 149 recipes / 28 shapeless).
- The light rows (`BlockLightTable`): the vault opaque 15 except the
  slime pad — the reference's `TransparentBlock(CLAY, false)` whose
  leaves=false arm passes light whole (opacity 0).
- The wire (`LegacyBlockIds`): the 15 new rows. Three repairs: the
  DROPPER's 125→158 (the original was the double wooden slab's id —
  real clients rendered slabs where droppers stood; the reference's
  register(158, "dropper")), the NETHER BRICKS' 40→112 (the original
  was the red mushroom's id — fortress walls rendered as mushrooms), and
  the DropService gravel-flint walk's `value().equals("minecraft:gravel")`
  — the namespace never rides value(), so Fortune never widened the
  flint chance (10 - fortune*3); repaired to the bare comparison.
- Tests: `MineralVaultBlocksTest` (9: the ores' strengths and tier
  gates with the tool-class walk, the storage rows + clay's four balls +
  packed ice's bare-hand nothing, the lapis 4-8 band with the fortune
  multiply over 500 rolls, the redstone dust's 4-5 band with the
  additive fortune topping at 8, the silk arms, the XP bands over 300
  rolls, the smelts/fuel/take-XP rows, the light table, the storage
  pack/unpack round trips over the live matcher) +
  `LegacyBlockIdsIntegrityTest` +2 (the dropper family's 158 + the
  dispenser's untouched 23, the nether bricks' 112 + the material 405 +
  the 15 vault rows) + `CraftingRecipeTest` 149/28 + `BlockRegistryTest`
  275. Fresh runs: the touched-area batch green (furnace, interaction,
  block, crafting, experience, the registry), the protocol module green
  (2m23s), `--max-workers=1`.

Ledgered: the quartz_ore smelt row is data-completeness only (no
quartz_ore item is reachable — the engine's quartz ore drops the quartz
itself; vanilla smelts only silk-touched ore blocks); the slime pad's
bounce/slow movement family (slipperiness 0.8) and the packed ice's
0.98 ride the movement slice; the end stone sits in the registry with
the End dimension's generator on its own slice; the hay bale's AXIS
rotation states ride the metadata-variants slice (the plain Y form is
registered).

## Slice 33 — the redstone power family: the lamp pair + the lit ore

The reference port of `RedstoneLampBlock.java` + `RedstoneOreBlock.java`'s
lit twin (the registrations 123/124/74), the first family the
neighbor-signal bus carries that is not itself a signal source.

- The three new blocks (`BuiltinBlocks`): redstone_lamp 123,
  lit_redstone_lamp 124 (one item — the dark form; the reference's
  getDropItem returns the DARK lamp for both states), lit_redstone_ore
  74 (the plain ore's glowing state).
- The lamp's flip (`RedstoneSystem`, FAMILY_LAMP + LAMP_TICK_RATE 4):
  onAdded evaluates immediately (a lit lamp without a feed goes dark, a
  dark lamp on a feed lights — no tick delay on the brighten),
  neighborChanged schedules the lit form's 4-tick revert when the feed
  departs (getTickRate's own delay) and swaps the dark form immediately
  when one arrives, and the tick dispatch carries the delayed revert.
  The neighbor arm rides the same six-cell ring the torch/repeater
  families use.
- The lit ore's lifecycle: the click arm (`EngineServer.useItemOnBlockOnTick`
  — the use falls through to the placement proposal, the reference's own
  super.use return), the mining-start arm (startMining -> interact, the
  note block's own hook extended), and the revert — the lit form is
  `setTicksRandomly(true)` and the reference's `Block.randomTick`
  DELEGATES to `tick` (Block.java lines 434-436), so the glow's duration
  IS the random-tick band; `RandomTickSystem.randomTick`'s first rule
  carries it back (no scheduled delay exists in the reference — the
  discovery that closed the slice's own research question).
- The drop walks (`DropService`): the redstone arm extended to the lit
  twin (the same 4+nextInt(2) dust walk with the additive fortune both
  states); the lamp pair drops the dark item from both states.
- The light rows: the dark lamp 0/15 (Material.REDSTONE_LAMP is a solid
  opaque cube), the lit lamp 15/15 (setLight(1.0F)), the lit ore 9/15
  (setLight(0.625F) — the (int)(0.625*15) truncation).
- The wire (`LegacyBlockIds`): 123/124/74 — the item shares the dark
  form's 123.
- Tests: `RedstoneLampTest` (2: the lamp's immediate light on a
  torch-fed block + the 4-tick revert after the feed departs over the
  live engine; the lit ore's random-tick revert through the per-cell
  probe + the drop rows of both states + the family predicates) +
  `BlockRegistryTest` 278. Batches: redstone + block + interaction +
  registry green, the protocol module green except the DonkeyChest
  integration flake (the documented 2-core load flake — green in
  isolation, the worklog's own precedent), `--max-workers=1`.

Ledgered: the ore's stepped-on arm (onSteppedOn -> interact) rides the
entity-walk family the engine's plate scan does not expose yet; the
interact arm's spark particles are the client's randomDisplayTick band.

## Slice 34 — the complete door families: the six woods + the iron upgrade

The reference port of `DoorBlock.java` + `DoorItem.java` over the whole
door vocabulary — the oak pair's own shape extended to spruce 193, birch
194, jungle 195, acacia 196, dark_oak 197, and the iron door 71 raised
from its five closed states to the full state grid.

- The state model (`server/block/WoodDoorFamily`): 8 lowers per family
  (the facing band 0-3 with the open bit 4) + the FULL upper grid —
  hinge x powered x open, wire metas 8-15 exactly the reference's own
  encoding (half 8 | hinge 1 | powered 2), with the engine's open flag
  riding bit 4, a bit the 1.8.8 client ignores on upper halves. The
  POWERED and OPEN bits stay INDEPENDENT exactly like the reference
  (use() writes OPEN only; the signal arm writes both) — the manual
  close of a signal-held door persists as closed-POWERED, and a manual
  open never sets POWERED. The oak's `upper_open` byte corrected from
  the historical 9 (a right-hinge claim) to 12 — old worlds' doors were
  manually opened, and 12 is the exact byte a vanilla client decodes
  for that state.
- The placement (`EngineServer.placeDoorOnTick`, 1:1): the UP face only,
  a replaceable clicked cell takes the door in place, the y<255 cap, the
  solid-top floor gate, both cells' headroom, the player's own yaw band
  into the metadata (the shipped band was already byte-exact), and
  DoorItem.place's own hinge rule — the clockwise vs counterclockwise
  solid counts with the same-family adjacency preference. The pair
  commits under `RedstoneSystem.runDoorPairPlacement` (the reference's
  flag-2 writes: both halves stand before any neighbor hears anything).
- The swing (`toggleDoorOnTick`): the open-only flip with the hinge and
  POWERED bits preserved; clicking EITHER half swings the pair (the
  upper redirects down); the iron door's material refusal (consumed,
  never swung).
- The signal arm (`RedstoneSystem.doorNeighborChanged`): hasNeighborSignal
  over BOTH halves; on the edge (flag != POWERED) POWERED flips
  unconditionally and OPEN only when it differs — a held-open door never
  re-toggles on unrelated neighbor changes, and the iron door opens only
  this way. The orphan arm pops a pairless lower with its item drop; the
  support-loss arm kills the pair with exactly one drop.
- The drops: every state's behavior row drops the family item (the
  reference's lower-only getDropItem nets the same one item through the
  engine's broken-cell model — the sibling cleanup removes the survivor
  silently); the iron pair is pickaxe-gated (Material.IRON's own
  setRequiresTool).
- The five new items (Item.java lines 748-751's own rows: 427-431) + the
  wire (LegacyBlockIds: the ids 64/71/193-197 + the metadata nibbles 0-15
  per family) + the light rows (CUTOUT opacity 0) + the noteblock's
  bass-guitar wood family (every door state of the six woods).
- Tests: `WoodDoorFamilyTest` (5, live engine) + `BlockRegistryTest` 375
  + `LegacyBlockIdsIntegrityTest` +1. Batches: the touched-area batch
  green (redstone + block + interaction + noteblock, 80 tests), the
  protocol integrity + chunk serializer green, `--max-workers=1`.

Ledgered: the five new woods' crafting rows (6 planks -> 3 doors) ride
the wood-variant plank family's own slice; the doEvent 1003/1006 pair
rides the engine's fx sound bus; the thin 3/16 collision panel rides the
collision-shape slice (the open pair is walk-through, the closed halves
flat-solid).

## Slice 35 — the wood-variant foundation: the six woods' material vocabulary

The reference port of `PlanksBlock.Variant`'s own domain — the planks,
logs, leaves, and saplings of all six woods over the reference's split
registrations (17/18 the oak..jungle pair, 162/161 the acacia/dark_oak
pair, 5/6 the planks/saplings).

- The state model (`server/block/WoodVariantBlocks`): per-wood planks/
  log/leaves blocks (the five new woods; the shipped oak keeps its own
  ids) + all six saplings, each carrying the reference's exact wire
  encoding — the & 3 band for the log/leaves domains with the Log2/
  Leaves2 pair re-basing acacia/dark_oak to 0/1, the bare variant for
  the planks/saplings. Six damage-domain family items (planks 5, log 17,
  log2 162, leaves 18, leaves2 161, sapling 6) — the dye families' own
  model.
- The placement consult: the chain now reads the wood families first
  (the stack's damage resolving the variant for all four domains), the
  dye families after.
- The leaves' drop walk (`DropService`'s new branch,
  AbstractLeavesBlock.dropItems lines 178-203 1:1): shears or silk takes
  the leaf itself (the family item with the variant damage); the bare
  hand rolls TWO independent walks — the sapling at the wood's own band
  (1/20, the jungle leaf's 1/40 override, the fortune relief -2<<level
  clamped at 10) and the oak-only apple at 1/200 (its -10<<level relief
  clamped at 40). The oak's shipped behavior row corrected (the hand
  rolls now — requiresTool was suppressing the sapling walk).
- The crafting rows (CraftingManager lines 203-226 + 191-198): the
  log->planks pair per new wood (the exact-damage 1x1 shaped form), the
  SIX DOORS' 2x3 crafts (closing Slice 34's ledgered gap), and the
  family-planks stick/bowl/sign rows over any variant (the bare
  Blocks.PLANKS ingredient's own behavior).
- The behavior rows (2.0 wood / 0.2 leaves / 0.0 saplings), the light
  rows (15 / 1,1 / 0), the noteblock's bass-guitar set, the wire rows
  (the block ids + nibbles + the family items), and the solidity rules
  (the saplings walk-through, never replaceable).
- Tests: `WoodVariantBlocksTest` (5) + `BlockRegistryTest` 396 +
  `CraftingRecipeTest` 163. Batches: the touched-area batch green (97
  tests — redstone, block, interaction, crafting), the protocol
  integrity + chunk serializer green, `--max-workers=1`.

Ledgered: the log/leaves axis and decay bits ride the
metadata-direction and tree-decay slices (the upright/fresh forms ship —
the item damage domain carries the bare variant exactly like the
reference's own recipes read); the sapling's tree growth rides the
tree-growth slice; the oak's blocks keep their shipped plain-item drops.

## Slice 36 — the building shapes: the stair families, the wood fences, the wooden slabs

The eight new stair families (spruce 134, birch 135, jungle 136, acacia
163, dark_oak 164, brick 108, stone_brick 109, quartz 156 — every one
over its base material's own strength), the five new wood fences
(188-192), and the wooden-slab pairs over the single-slab id 126 (the
variant nibble, the top half's bit 8).

- The state model (`server/block/WoodShapeBlocks`): the four facing
  states per stair family over the engine's shipped band (east 0 / west
  1 / south 2 / north 3), the fence blocks, the slab pairs.
- The generalized placement: stairsFamilyOf/slabFamilyOf resolve ANY
  family now (the shipped oak/cobblestone/sandstone/nether-brick stairs
  included — they previously fell through to the generic placement
  without their facing rule); the wooden_slab family item resolves its
  variant through the damage consult.
- The behavior rows (wood 2.0; the masonry stairs pickaxe-gated: brick
  2.0, stone brick 1.5, quartz 0.8), the light rows (the opaque partial
  cubes), the wire rows (the ids + the facing/variant nibbles + the
  items), the noteblock's set already covered by the planks family.
- The crafting rows (CraftingManager lines 251-270): the stair ladder
  per family ("#  ", "## ", "###" over six exact-variant planks or the
  plain masonry blocks -> 4 stairs), the slab bands (three planks ->
  six slabs of the same variant), the fence crafts ("S#S", "S#S" over
  two planks + four sticks -> three fences).
- Tests: `WoodShapeBlocksTest` (2) + `BlockRegistryTest` 443 +
  `CraftingRecipeTest` 181. Batches: the touched-area batch green (99),
  the protocol integrity green, `--max-workers=1`.

Ledgered: the stairs' upside-down HALF bit rides the metadata-variants
slice; the double_wooden_slab 125 stays the parent-material stand-in;
the fence gates (107/183-187) are their own slice; the fences'
1.5-block collision rides the collision-shape slice.

## Slice 37 — the fence gates: the six families (FenceGateBlock 1:1)

The reference port of `FenceGateBlock.java` over PlanksBlock.Variant's
whole domain — the oak gate 107 plus the five 1.7 woods' gates (spruce
183, birch 184, jungle 185, dark_oak 186, acacia 187).

- The state model (`server/block/FenceGateBlocks`): sixteen states per
  family over the reference's exact metadata band (the horizontal facing
  S=0 W=1 N=2 E=3 | open 4 | powered 8 — getStateFromMetadata lines
  136-153), the bare meta-0 form sharing the family item's identifier
  (Item.java's own register(Blocks.FENCE_GATE) rows 451-456 — the item
  keeps the block id). The IN_WALL virtual property stays the computed
  render read the reference itself computes at query time (ledgered to
  the collision-shape slice).
- The placement (`EngineServer.placeFenceGateOnTick`, 1:1): the facing
  is the placer's own horizontal facing (getHorizontalFacing — the
  engine's own look band, byte-identical: floor(yaw/90+0.5)&3), OPEN and
  POWERED start clear, and canBePlaced's solid-floor gate refuses flora
  beds.
- The swing (`toggleFenceGateOnTick`): the open arm's far-side rewrite
  (FACING == direction.getOpposite() turns the gate to the opener), the
  close arm keeping the facing, POWERED preserved through the manual
  swing — and the doEvent 1003/1006 pair riding the fx sound bus (the
  door's own open/close sounds, the client event handler's own choice).
- The signal arm (`RedstoneSystem.fenceGateNeighborChanged`, 1:1): the
  rising edge opens with POWERED latching, the falling edge closes an
  open+powered gate (the reference never asks WHO opened it — the bits
  alone decide, so a manually-opened gate that latched POWERED closes
  when the feed departs), the bare POWERED re-latch without touching
  OPEN, and the held-open stability through unrelated neighbor changes.
- The fence-craft REPAIR (a real 1:1 bug the gate work exposed): the
  shipped Slice 36 fence recipes carried the ingredients TRANSPOSED —
  stick-plank-stick is the GATE's own shape ("#W#"), not the fence's
  ("W#W" — CraftingManager lines 59-83: FOUR PLANKS OUTSIDE, two sticks
  in the middle). All five rows corrected and the missing OAK fence row
  added (line 60's own recipe — the oak fence never had a craft).
- The behavior rows (2.0 wood, the family item), the light rows (CUTOUT
  filter 15 like the fences' own), the walk-through rule (the open band
  never blocks a body — getCollisionShape's null; the closed states stay
  solid), the noteblock's bass-guitar family, the crafting rows (four
  sticks + two exact-variant planks -> one gate per family), and the
  wire rows (the ids + every state's facing|open|powered nibble).
- Tests: `FenceGateBlocksTest` (3, live engine — the placement band +
  the flora refusal, the far-side open rewrite, the signal edge walk
  with the manual-close/POWERED independence and the arm2 close, the
  family tables + the crafts) + `WoodShapeBlocksTest` repaired +
  `CraftingRecipeTest` 188 + `LegacyBlockIdsIntegrityTest` +1. Batches:
  block + crafting + redstone + noteblock + building acceptance green
  (110), the protocol integrity + chunk serializer green,
  `--max-workers=1`.

Ledgered: the closed gate's 1.5-tall panel and the IN_WALL flush form
ride the collision-shape slice (the flat-solid stand-in ships — the open
band walks through, the closed blocks like the fence); the gate's
cobblestone-wall consult rides the wall's own slice (139).

## Slice 38 — the rail trio: the golden/detector/activator rails

The reference port of AbstractRailBlock + PoweredRailBlock +
DetectorRailBlock over the reference's registrations (golden_rail 27,
detector_rail 28, activator_rail 157 — the reference's own twin of the
golden rail: register(157, "activator_rail", new PoweredRailBlock()...)).

- The state model (`server/block/RailBlocks`): the six straight shapes
  over metadata 0-5 (Shape.byId: north_south, east_west, the four
  ascents — the curves stay the plain rail's own family) with the
  POWERED bit 8 — twelve states per family, the bare north_south form
  sharing the family item's identifier.
- The placement (`placeRailOnTick` generalized): the look-axis rule (the
  same walk the plain rail owns), the solid-bed gate
  (AbstractRailBlock.canBePlaced's World.hasSolidTop), the trio items
  routing through their own consult.
- The golden family's POWERED walk (`RedstoneSystem.railNeighborChanged`
  + `isPoweredByConnectedRails`/`isPoweredByRail`, 1:1): the direct feed
  OR the connected-rails propagation — the 8-deep walk along the run with
  the reference's own axis math (the ascending shapes' climb/descend
  steps, the below-cell allowance) and its axis-compat gate (an east-west
  walker never reads a north-south run); the SAME-family gate
  (blockstate.getBlock() != this — golden never feeds activator); the
  flip rings the block below (and above when ascending). The support
  walk pops the rail when the bed or the ascending shape's headwall
  departs.
- The detector family (`FAMILY_DETECTOR_RAIL`): the per-tick cart scan
  (the onEntityCollision equivalent over the plate precedent — the
  engine's cart probe), the press/release arms with the two rings and
  the comparator wake, the 20-tick re-check (getTickRate), and the
  emission consults (getSignal's 15 weakly in EVERY direction,
  getDirectSignal's strong UP-only 15, isSignalSolid's exclusion — the
  source never re-radiates).
- The cart's ride (`MinecartEntity.tickKind` over the new
  `WorldQuery.railPowerAt`): the powered golden/activator rail's 0.06
  boost capped at the reference's 0.4 getMaxSpeed (with the from-rest
  kick), the unpowered golden rail's brake (under 0.03 the full stop,
  over it the 0.5 halving) — the reference's own numbers from
  moveAlongTrack lines 345-349, 428-437, 477-494. The cart's axis read
  extends to the trio's shapes (railAxisAt's shape consult).
- The crafting rows (CraftingManager lines 233-237): the golden rail
  (six gold + a stick + redstone), the activator rail (six iron + the
  redstone torch + two sticks), the detector rail (six iron + the stone
  pressure plate + redstone) — six each.
- The behavior rows (0.7 stone-class, drops the family item), the light
  rows (the thin track passes light), the solidity rule (walk-through
  like the plain rail), the items, and the wire rows (the ids + every
  state's shape|powered nibble).
- Tests: `RailBlocksTest` (4, live engine — the placement band + the
  support pop, the three-rail propagation walk with the dark-on-depart,
  the detector's cart press/release cycle, the family tables + the
  crafts) + `CraftingRecipeTest` 191. Batches: block + crafting +
  redstone + vehicle green, the protocol integrity + chunk serializer +
  vehicle integration green, `--max-workers=1`.

Ledgered: the ascending shapes' ramp physics (the slope gravity
0.0078125 and the y-climb) ride the cart's own slope slice — the flat
walk ships; the activator rail's cart-side arms (the TNT cart's prime,
the hopper cart's halt) ride their own cart slices; the trio's
curve-free SHAPE vocabulary is the reference's own (the plain rail's
RailNode auto-shape walk is the plain rail's slice).

## Slice 39 — the trapdoor pair: the wood 96 + the iron 167

The reference port of `TrapdoorBlock.java` over both material rows (the
wood `setStrength(3.0F)` WOOD_SOUNDS, the iron `setStrength(5.0F)`
METAL_SOUNDS with Material.IRON's pickaxe gate).

- The state model (`server/block/TrapdoorBlocks`): the horizontal facing
  over metadata 0-3 (getFacing: N=0 S=1 W=2 E=3) with the OPEN bit 4 and
  the TOP-half bit 8 — sixteen states per family, the bare closed bottom
  north form sharing the family item's identifier (Item.java's rows
  434/503). Both halves carry a collision panel in the reference (the
  closed 3/16 floor/ceiling panel, the open vertical one) — the engine's
  flat-solid stand-in keeps every state solid, the shipped door
  convention (the thin panels ledgered to the collision-shape slice).
- The placement (`EngineServer.placeTrapdoorOnTick`, 1:1): only a
  HORIZONTAL clicked face mounts, the facing is the face's own direction
  (the panel against the wall), the half rides the cursor's upper band
  (`dy > 0.5F` — the slab's own cursor rule), and the wall must pass
  canBePlacedOn (a solid full cube, the glowstone, a slab, or a stairs —
  the engine's isSolid minus the known partial shapes).
- The swing (`use()` lines 107-116): the wood flips OPEN with the doEvent
  1003/1006 pair on the fx bus; the iron's material refusal still
  consumes the use (the reference's `return true`).
- The signal arm (`RedstoneSystem.trapdoorNeighborChanged`, 1:1): the
  mount check first (the wall behind the FACING — a departed mount pops
  the trapdoor with its item), then the signal edge flips OPEN for BOTH
  families (the iron trapdoor's only opening path).
- The crafting rows (CraftingManager lines 197-199): six planks over the
  BARE family ingredient (any variant) -> 2 trapdoors, four iron ingots
  -> 1 iron trapdoor.
- The behavior rows (wood 3.0 / iron 5.0 pickaxe-gated), the light rows
  (CUTOUT filter 15), the noteblock's bass-guitar wood family (the iron
  rides the default harp like the iron door), and the wire rows (the
  ids + every state's facing|open|top nibble).
- Tests: `TrapdoorBlocksTest` (3, live engine — the wall-mount placement
  with the cursor half and the vertical-face refusal, the wood swing +
  the iron refusal, the signal edge over both families + the mount-depart
  pop with its drop, the family tables + the crafts) +
  `CraftingRecipeTest` 193. Batches: block + crafting + redstone +
  noteblock green, the protocol integrity + chunk serializer green,
  `--max-workers=1`.

Ledgered: the open panel's vertical 3/16 shape and the closed panel's
floor/ceiling thickness ride the collision-shape slice (the flat-solid
stand-in ships — every state blocks a body, the reference's own
isPassable pathfinding quirk stays out of the physics model).

## Slice 41 — the melon family: the melon block 103 + the two stems

The reference port of `MelonBlock.java` + `StemBlock.java` over the
reference's registrations (the melon block 103 Material.PUMPKIN
`setStrength(1.0F)` WOOD_SOUNDS, the pumpkin stem 104 and the melon stem
105 `setStrength(0.0F)`).

- The state model (`server/block/MelonBlocks`): the stems' eight AGE
  stages each over the engine's crop naming convention
  (`<family>_stage0..7`, the wheat/carrots/potatoes precedent) with the
  AGE as the wire nibble whole; the melon block rides its own plain
  constant. The stems have NO items (CreativeModeTab null — the pick
  yields the seeds, the planting rides the seeds' use arm).
- The growth (RandomTickSystem's crop walk extended): the stems join the
  stage walk (light >= 9, the hydrated 1/3 vs 1/6 pace) with the FRUIT
  ARM at AGE 7 (StemBlock.tick lines 60-84 1:1 — the already-fruiting
  gate, the random horizontal pick, the farmland/dirt/grass floor gate,
  the air check).
- The planting (`useFarmingOnTick`): the pumpkin/melon seeds join the
  wheat seeds' farmland arm (WheatSeedsItem's own block field — the
  seed names its stem).
- The bone-meal: the stems grow through the same crop gate (stage < 7,
  the +2..+5 walk).
- The drops (DropService): the melon block's slice walk
  (getDropCount's min(9, 3 + nextInt(5) + nextInt(1 + fortune))), the
  stems' seed roll (three independent nextInt(15) <= age rolls — the
  young stem's 1/15 band, the grown stem's ~half). The melon SLICE item
  joins the registry (a pre-existing gap: the constant existed but never
  registered).
- The behavior rows (the melon 1.0 wood / the stems 0.0 plant), the
  light rows (the opaque cube / the walk-through plants), the solidity
  (the stems join the flora walk), and the wire rows (103 + the stems'
  stage nibbles).
- Tests: `MelonBlocksTest` (4 — the AGE band + the tables, the two drop
  walks' bands, the fruit arm over a pure world with the
  already-fruiting gate, the crop-stage resolve) + the item-registry
  repair. Batches: block + interaction + crafting green, the protocol
  integrity + chunk serializer green, `--max-workers=1`.

Ledgered: the stems' FACING property (the attached-fruit render face)
is the client's own render read (resolveVirtualProperties); the melon
block's silk arm takes the default (the slice walk under silk — the
reference has no silk override).

## Slice 42 — the ground-cover pair: the mycelium 110 + the lily pad 111

The reference port of `MyceliumBlock.java` + `LilyPadBlock.java` (both
single meta-0 states — the snowy side and the boat pass-through are the
client's own reads).

- The mycelium: the 0.6 grass row dropping its dirt (getDropItem's own
  consult), the opaque-cube light row, and the random-tick walk 1:1
  (the decay under darkness — brightness < 4 with an opacity > 2 lid —
  and the four-attempt spread to plain dirt at source brightness >= 9
  with the target's >= 4 gate, the 3x5x3 neighborhood).
- The lily pad: the 0.0 plant row dropping itself, the walk-through
  flora rule (the 1/64 surface), the LilyPadItem placement (the aimed
  water SOURCE with air above — the pad takes the surface cell), and
  the support pop (canSurvive's water-source rule — flowing water or a
  departing source pops the pad with its item).
- The items (register(Blocks.MYCELIUM) 110, register(Blocks.LILY_PAD,
  new LilyPadItem(...)) 111 — both sharing their block ids) and the
  wire rows.
- Tests: `GroundCoverBlocksTest` (2 — the mycelium's spread to a lit
  dirt neighbor over a pure world + the behavior/light rows, the lily
  pad's tables + the walk-through rule). Batches: the block batch green,
  the protocol integrity green, `--max-workers=1`.

Ledgered: the pad's boat-breaking arm (the addCollisions boat skip — a
colliding boat destroys the pad) rides the vehicle-collision slice; the
mycelium's spore particles are the client's randomDisplayTick band; the
mob-spawn override (the mycelium's own spawn-list consult) rides the
biome-spawn slice.

## Slice 43 — the cocoa family: the pod 127

The reference port of `CocoaBlock.java` (the pod
`setStrength(0.2F).setBlastResistance(5.0F)` WOOD_SOUNDS over
Material.PLANT, mounted on the jungle log).

- The state model (`server/block/CocoaBlocks`): the horizontal facing
  (S=0 W=1 N=2 E=3) with the AGE over bits 2-3 — twelve states, the bare
  form carrying the south/age-0 placement shape; the pods have NO items
  (the creative pick yields the brown dye — the planting rides the dye's
  own use arm).
- The placement (DyeItem.useOn lines 54-71): the BROWN dye — the cocoa
  beans, the dye family's damage 12 — on a JUNGLE LOG's horizontal face
  plants the age-0 pod in the offset cell, facing back at its log
  (getPlacementState's dir.getOpposite()).
- The support rule (isSupported + neighborChanged's breakCocoa): the
  FACING cell must be the jungle log; a departed log pops the pod with
  the AGE band's dye rolls (1 below age 2, 3 at it — the brown damage
  12) over the neighbor bus.
- The growth (CocoaBlock.tick lines 44-50): the 1-in-5 AGE advance to 2
  on the random tick.
- The drops (DropService): the same 1-or-3 dye rolls on the break walk.
- The behavior rows (0.2 plant), the light rows (CUTOUT), the
  walk-through flora rule, and the wire rows (127 + the facing|age
  nibbles).
- Tests: `CocoaBlocksTest` (2 — the twelve states' band + the placement
  facing, the jungle-log support rule + the drop walks + the growth
  writes). Batches: the block + interaction batches green, the protocol
  integrity green, `--max-workers=1`.

Ledgered: the pod's per-age collision box (updateShape's 4+age*2 faces)
rides the collision-shape slice (the walk-through flora stand-in ships);
onPlaced's look-based FACING override keeps the placement state's own
face-opposite (the support reads the clicked log exactly — the
reference's override can desync the support cell, the engine's shape
cannot).

## Slice 44 — the flower family + the flora support rules: the red flower's nine types

The reference port of `FlowerBlock.java` (the RED group's TYPE band — the
poppy 0, the blue orchid 1, the allium 2, the houstonia 3, the four tulips
4-7, the oxeye daisy 8 — `setStrength(0.0F).setSounds(GRASS_SOUNDS)` over
Material.PLANT, Block.java line 1057).

- The state model (`server/block/FlowerBlocks`): the eight new variant
  states as flattened types sharing the wire id 38 with the TYPE id whole
  in the metadata nibble (FlowerBlock.getMetadataFromState); the poppy
  keeps its shipped plain rows (the melon slice's bare-form model).
- The family item: `minecraft:red_flower` (Item.java's own BlockItem row
  over 38) with the TYPE damage domain — the poppy's own item and the
  dandelion (the YELLOW group's whole family on 37) keep their plain
  forms; the placement consult clamps out-of-band damage to the poppy
  exactly like the reference's own Type.byId.
- The flora support rules (PlantBlock 1:1): the neighbor-bus pops in
  `BlockUpdateSystem` — the flowers pop with the family item carrying the
  TYPE damage (the poppy/dandelion their own items), the tall grass rolls
  the 1/8 seed walk, the dead bush drops nothing; the soils are the
  grass/dirt/farmland band (PlantBlock.canBePlacedOn) and the dead bush's
  own sand/hardened-clay/stained/dirt band (DeadBushBlock's override).
- The placement gate: `BlockInteractionService.setPlacementSupportGate` —
  the canBePlaced consult (PlantBlock lines 36-38) refusing a placement
  whose soil is wrong before any attempt, wired in EngineServer's chain;
  the damage consult chain's third family arm (wood, dye, flower).
- The behavior rows (0.0 plants, the family-item drops with the TYPE
  damage), the light rows (CUTOUT 0), the walk-through solidity rows, and
  the wire rows (the eight variants at 38 with the TYPE nibble; the
  poppy's own 0 metadata row folding the plain (38, 0) reverse to the
  poppy; the family item at 38).
- Two repairs from the audit: the Slice 42 lily pad's support pop never
  ran (`Identifier.equals("waterlily")` — an Identifier never equals a
  String; now reads the value), and the stale BlockRegistryTest re-pin
  (443 from Slice 36 -> the true 664 + this slice's eight = 672; Slices
  37-43 had not re-pinned it).
- Tests: `FlowerBlocksTest` (2 — the nine types' band + the tables + the
  damage consult with the reference's clamp; the live soil matrix over
  the hand-set soils) + `BlockRegistryTest` 672. The full core suite
  green, the protocol module green, `--max-workers=1`.

Ledgered: the blue orchid's swamp-only placement and the flowers'
bone-meal spread ride the bone-meal item-model slice; the sheep's grazing
rides the sheep's own refinement; the fern TYPE 2 and the whole
DOUBLE_PLANT 175 family are Slice 45; the flora's XZ offset rendering
rides the client's model band.

## Slice 45 — the double-plant family: the six two-cell plants 175

The reference port of `DoublePlantBlock.java` (the sunflower 0, the lilac 1
— the reference's own "syringa" key, the double tall grass 2, the large
fern 3, the rose 4, the peony 5 — `setStrength(0.0F).setSounds(GRASS_SOUNDS)`
over Material.REPLACEABLE_PLANT).

- The state model (`server/block/DoublePlantBlocks`): the twelve states —
  the six lowers carry the VARIANT id whole in the metadata nibble, the six
  uppers carry 8 | the default NORTH horizontal (the reference's own write,
  meta 10 — placement never sets FACING); the upper's variant is virtual in
  the reference, flattened into the identifier here.
- The family item: `minecraft:double_plant` (Item.java's own BlockItem row
  over 175) with the VARIANT damage domain — out-of-band damage clamps to
  the sunflower exactly like the reference's Variant.byId.
- The support rules (BlockUpdateSystem): the UPPER pops to air with no drop
  when the cell below stops being the family's lower; the LOWER pops BOTH
  cells with the lower's own drop walk (the flowers the family item with
  the VARIANT damage, the double grass the 1/8 seed roll, the large fern
  nothing) when the soil departs or the upper is gone.
- The placement arm `useDoublePlantOnTick`: the soil AND the headroom
  (DoublePlantBlock.canBePlaced's own pair gate), the pair commit with
  onPlaced's upper write, the creative no-consume.
- The mode-aware break hook (`BlockInteractionService.setBreakReport` —
  fired from both the creative and survival paths): the UPPER's break takes
  its lower — creative silently (removeBlock's own write), survival with
  the lower's walk and the shears arm's tall-grass item over the grass and
  fern variants.
- The behavior rows (0.0 plants), the light rows (CUTOUT 0), the
  walk-through solidity rows, and the wire rows (the twelve states over 175
  with the VARIANT/upper nibbles, the family item at 175).
- Tests: `DoublePlantBlocksTest` (2 — the twelve states' band + the drop
  walks + the tables; the live pair survival over the hand-set soils) +
  `BlockRegistryTest` 684. The full core suite green, the protocol module
  green, `--max-workers=1`.

Ledgered: the bonemeal arms (the flowers' grow-the-item dup and the tall
grass's conversion to the double) ride the bone-meal item-model slice; the
per-variant canBeReplaced refinement rides the metadata-variants slice; the
pair's XZ offset rendering rides the client's model band.

## Slice 46 — the cake family: the block 92's seven BITES states

The reference port of `CakeBlock.java` (Material.CAKE, no setStrength — the
0.0 default; the seven BITES states, the whole cake bites 0, the last
visible crumb bites 6).

- The state model (`server/block/CakeBlocks`): the seven states with the
  BITES id whole in the metadata nibble; the bare bites-0 form is the
  plain `minecraft:cake` the existing CAKE item (354, stack 1) places.
- The eat arms (CakeBlock.use + startMining + the private eat): a right or
  left click eats one slice when the body is not full — canEat(false), the
  add(2, 0.1F) nutrition write (foodLevel min(20, +2), saturation
  min(foodLevel, +0.1)), the BITES advance or the removal at the seventh
  bite. The left click rides the new player-aware
  `setMiningStartReport` hook; the right click the `useCakeOnTick` arm.
- The support pop (neighborChanged): the block below must be SOLID; a
  departing support removes the cake with NO drop (getDropItem's null,
  getBaseDropCount's 0).
- The analog read ((7 - bites) * 2) joins the comparator's source chain;
  the placement gate carries the SOLID-below rule.
- The MILK_BUCKET item (335) + the cake's crafting row (three milk over
  sugar-wheat-sugar) + the recipe remainder consult
  (`CraftingRemainders` — Item.getRecipeRemainder's own model: the
  consumed milk bucket leaves its empty bucket in the grid cell,
  CraftingGrid.consumeOne's consult).
- The behavior rows (0.0, no drops), the light rows (CUTOUT), and the wire
  rows (the seven states at 92 with the BITES nibble, the milk bucket at
  335).
- Tests: `CakeBlocksTest` (2 — the seven states' band + the analog ladder
  + the eat's own writes; the live support read + the recipe remainder) +
  `BlockRegistryTest` 691 + `CraftingRecipeTest` 194. The full core suite
  green, the protocol module green, `--max-workers=1`.

Ledgered: the cake's per-bite 7/16-height collision box rides the
collision-shape slice; the CAKE_SLICES_EATEN stat rides the stats slice;
the milk bucket's drink rides the food slice.

## Slice 47 — the wall family: the cobblestone wall 139's two VARIANT states

The reference port of `WallBlock.java` (the NORMAL cobblestone form 0 and
the MOSSY form 1, over the cobblestone's own strength — 2.0, the stone
sounds, the pickaxe gate).

- The state model (`server/block/WallBlocks`): the two states with the
  VARIANT id whole in the metadata nibble; the UP/N/E/S/W connections are
  VIRTUAL in the reference (resolveVirtualProperties — computed from the
  neighbors, never stored): the wire never carries them, the client
  renders its own connections exactly like the reference.
- The family item: `minecraft:cobblestone_wall` (Item.java's own
  VariantBlockItem over 139) with the VARIANT damage domain — out-of-band
  damage clamps to the NORMAL form like the reference's Variant.byId.
- The crafting pair (CraftingManager lines 89-90): six cobblestone -> six
  walls, six mossy cobblestone -> six mossy (damage 1 in the result).
- The behavior rows (2.0 ROCK pickaxe-gated tier 1, the family item drop
  with the VARIANT damage), the light rows (the non-solid-render partial
  column passes), and the wire rows (the two states at 139 with the mossy
  nibble 1).
- The audit repair: the MOSSY_COBBLESTONE item (48) — the pre-47 behavior
  row dropped it without a registered item type; the survival break of a
  mossy cobblestone threw the DropService's unregistered-item guard. The
  wall's mossy recipe reads it as an ingredient too.
- Tests: `WallBlocksTest` (1) + `BlockRegistryTest` 693 +
  `CraftingRecipeTest` 196. The full core suite green, the protocol module
  green, `--max-workers=1`.

Ledgered: the wall's 1.5-block maxY collision and the per-connection
0.25-0.75 shape ride the collision-shape slice (the flat-solid stand-in:
the wall reads SOLID); the connection rendering is the client's own
virtual read exactly like the reference.

## Slice 48 — the flower-pot family: the pot 140's fourteen states

The reference port of `FlowerPotBlock.java` (Material.DECORATION,
setStrength(0.0F) DEFAULT_SOUNDS — the empty pot 0 and the thirteen potted
contents over the LEGACY_DATA band: the poppy 1, the dandelion 2, the six
saplings 3-6 + 12-13, the red mushroom 7, the brown 8, the cactus 9, the
dead bush 10, the fern 11).

- The state model (`server/block/FlowerPotBlocks`): the fourteen states
  with the LEGACY_DATA nibble — the engine's flattened model carries what
  the reference's block entity holds (the TE's item + metadata).
- The planting arm `useFlowerPotOnTick` (FlowerPotBlock.use): a right click
  on the EMPTY pot with a pottable item writes the potted state and
  consumes unless the hand is creative. The pottable set (isPottablePlant):
  the dandelion, the poppy, the cactus, the two mushrooms, the six
  saplings, the dead bush, the tall grass's FERN damage alone.
- The SOLID-TOP support pop (neighborChanged): a departing support drops
  the pot AND the plant (the plant item with the TE's metadata — the
  saplings over the wood damage, the fern the tall grass's 2); the
  placement gate carries the same SOLID-TOP rule.
- The FLOWER_POT item (390) + the three-brick crafting row ("# #" / " # ").
- The behavior rows (0.0, the pot + the plant drops), the light rows (the
  3/8 pot passes), and the wire rows (the fourteen states at 140 with the
  content nibble, the item at 390).
- Tests: `FlowerPotBlocksTest` (1) + `BlockRegistryTest` 707 +
  `CraftingRecipeTest` 197. The full core suite green, the protocol module
  green, `--max-workers=1`.

Ledgered: the exotic flower types' potted contents (blue orchid..oxeye
daisy — the reference renders them through the TE's CONTENTS; the wire's
LEGACY_DATA has no code for them) and the pot's per-content color tint
ride the block-entity family; the pot's 3/8-height box rides the
collision-shape slice; the FLOWERS_POTTED stat rides the stats slice.

## Slice 49a — the weighted pressure-plate pair: the light 147 + the heavy 148

The reference port of `WeightedPressurePlateBlock.java` (Material.IRON,
setStrength(0.5F) WOOD_SOUNDS — the light (gold) over the weight 15, the
heavy (iron) over the weight 150).

- The state model (`server/redstone/WeightedPlateBlocks`): the
  thirty-two states — sixteen POWER each, the POWER value whole in the
  metadata nibble; the bare form is the POWER-0 state.
- The count walk (calculateOutputSignal): ceil(min(weight, entities) /
  weight * 15) — the light plate reads the entity count itself (capped
  15), the heavy ceil(count * 15 / 150).
- The RedstoneSystem's weighted branches: the scan's press writes the
  POWER state; the scheduled re-check re-writes the changed count while
  held (the reference's every-tick update); the output read carries the
  POWER state; the floor-break pop drops the family item; the
  onAdded/onRemoved joins carry the scan set.
- The counting probe `setPlateCountProbe` (EngineServer): EVERY entity in
  the 0.125-inset box — mobs, players and dropped items alike
  (getEntitiesOfType(Entity.class)'s own read).
- The LIGHT/HEAVY plate items (147/148), the behavior rows (0.5), the
  light rows (the thin plate passes), and the wire rows (the sixteen
  states each with the POWER nibble).
- Tests: `WeightedPlateBlocksTest` (1) + `BlockRegistryTest` 739. The
  full core suite green, the protocol module green, `--max-workers=1`.

Ledgered: the trapped chest 146 is Slice 49b (the chest system's family
extension + the viewer-count weak power); the strong-up-only emission
rides the existing plate branch.

## Slice 49b — the trapped chest: the block 146 (ChestBlock(1) 1:1)

The reference port of the trapped chest — the shared chest model's type-1
arms (setStrength(2.5F) WOOD_SOUNDS).

- The block + item: `minecraft:trapped_chest` (the single meta-0 form; the
  facing nibble rides the metadata-variants slice like the shipped plain
  chest).
- The chest system's family extension: `ChestManager.isChestBlock` accepts
  the trapped chest (the same container, GUI, spill and persistence); the
  open arm routes through the same `openChestOnTick` with the neighbor
  wake on open (the viewer count changed from 0).
- The weak power (ChestBlock.getSignal): the viewer count clamped 0-15 in
  every direction — the indirect-only source, read through the new
  `setTrappedChestViewers` probe (the sessions holding this chest's window
  open).
- The crafting row (BlockRecipes line 21): the chest over the tripwire
  hook.
- The behavior row (2.5 WOOD dropping itself), the light row, and the wire
  row (146).
- Tests: `TrappedChestBlocksTest` (2) + `BlockRegistryTest` 740 +
  `CraftingRecipeTest` 198. The full core suite green, the protocol module
  green, `--max-workers=1`.

Ledgered: the close's neighbor wake rides the container-close family's
own arms; the TRAPPED_CHESTS_TRIGGERED stat rides the stats slice.

## Slice 50 — the collision-shape table: every partial block's own boxes

The reference port of the collision geometry — the ledger's most-deferred
family (Slices 31/34/36/37/39/46/47/48 all rode their shapes here): the
flat-solid stand-ins retire and the physics point queries answer the
reference's exact boxes.

- The new `server/block/CollisionShapes` (the table): a box-list model per
  block type — fractions over the cell, `maxY` past the ceiling for the
  1.5-tall families — with `contains` (the point-in-shape test) and `top`
  (the support surface: the box the point's column hits). The connection
  families resolve their neighbours through a `NeighborProbe` (the
  world-facing callers' own block reads); the probe-less overloads answer
  the isolated form.
- `WorldSolidity.isSolidAt` / `supportY` now delegate to the table (the
  Y-only legacy view keeps the cell's centre column, every shipped
  expectation preserved); the broad `isSolid` gate (the material-style
  read the placement/support/spawn systems consult) is untouched.
- `EngineServer`'s three query providers — the MobWorldQuery (the
  dimension-aware pair), the static shape pair behind ShapeGround
  (items/falling blocks/projectiles) and the VehicleWorldQuery — pass the
  XZ-aware overloads with their own dimension probe (the neighbour reads
  at the offsets from the queried cell, bounds-guarded).

The families (each 1:1 its reference row):

- **The fences** (`FenceBlock.addCollisions`): the 0.375-0.625 post 1.5
  tall, arm 1 the N/S band on a connection, arm 2 the W/E band (or the
  isolated post). The connect rule keeps the reference's own material
  split: every wood fence joins every wood, the nether brick fence
  (Material.STONE) never joins the woods, any gate connects, a full cube
  connects, the pumpkin never does (the Material.PUMPKIN quirk). The
  audit repair: `WorldSolidity.isFence` matched the oak alone — the five
  1.7 woods and the nether brick read as full-cube spawn ground.
- **The wall** (`WallBlock`): the 0.25-0.75 post opening to the cell edge
  on the connected sides, the straight N/S pair's 0.3125-0.6875 band, the
  collision `maxY` 1.5; the mossy variant joins (one block in the
  reference, both VARIANT states connect).
- **The closed fence gates** (`FenceGateBlock.getCollisionShape`): the
  3/8-thick panel across the facing axis (the south/north pair on Z, the
  west/east pair on X), 1.5 tall; the open states carry no box (the
  reference's null — the walk-through band stays).
- **The trapdoor pair** (`TrapdoorBlock.updateShape`): the closed 3/16
  floor panel, the closed ceiling panel, the open vertical panel against
  the hinge wall per facing (the bare form north, the `_south/_west/
  _east` suffixes the reference's own band).
- **The doors** (`DoorBlock.updateShape` over the combined metadata): the
  closed 3/16 panel against the hinge-side edge per facing band (the
  reference's `byIdHorizontal(band).counterClockwiseY()` — band 0 EAST,
  1 SOUTH, 2 WEST, 3 NORTH), the open panel swung against the wall by the
  hinge bit. The facing/open bits live on the lower half, the hinge on
  the upper — the pair resolves through the probe exactly like
  `getCombinedMetadata`; the open halves keep their walk-through
  material read but now carry the open panel box.
- **The chest family** (`ChestBlock`/`EnderChestBlock`): the 1/16-inset
  14/16 box, the double form's joined edge extending to the cell
  boundary (the same-family pair rule: plain joins plain, trapped joins
  trapped, the ender chest its own four states).
- **The panes** (`PaneBlock.addCollisions`): the 7/16-9/16 cross — the
  half arms on one-sided connections, the full bars on both-sided and
  isolated forms (the reference's own else arms), the glass pair and the
  iron bars interconnecting.
- **The thin covers**: the carpet's 1/16 sheet, the snow layer's 2/16
  cover, the lily pad's 1/64 film, the four pressure plates' 1/32 disc
  (the stone/wood pair + the weighted pair) — the shape queries now see
  them (a body rests on the surface) while the material-style `isSolid`
  keeps them non-blocking for the placement/support gates, exactly the
  reference's collision-box/material split.
- **The partial furniture**: the flower pot's 3/8 box, the cake's
  per-bite sliver (the bite shrinks the eaten side alone — `minX`
  `(1 + bites * 2) / 16`, the height a flat 1/2), the cactus' 1/16
  inset, the farmland's 15/16 field, the enchanting table's 3/4, the
  daylight detector's 3/8, the end portal frame's 13/16 base + the eye's
  5/16 centre post, the bed's 9/16 mattress.
- **The columns**: the brewing stand's 1/8 base + 7/8 post pair, the
  cauldron's 5/16 floor + four full-height 1/8 legs, the hopper's 5/8
  funnel + the same legs (the facing never moves the box).
- The slab/stair half rule ships unchanged (the bottom slab and the
  facing stair states their lower half, the top slab the upper, the bare
  stair forms the flat default).

Tests: `CollisionShapeTest` +10 (the fence post/arms with the material
split and the pumpkin exclusion, the wall's post/straight band/1.5, the
gate's two axes and the open vanish, the trapdoor's three panels, the
chest's box + the double's edge, the thin covers' heights, the furniture
boxes, the columns, the pane cross/arms, the doors' panels per
facing/hinge with the pair's combined read) — 19 total. The full core
suite green in seven touched-area batches (block, entity, redstone/
container, crafting/item/spawn, the direct package, the remaining
subpackages, the protocol + api modules), `--max-workers=1`.

Ledgered: the swept-box model (a body straddling the 1.5 bands from the
cell above resolves through the one-cell point query — the legacy
convention, the ground probe entering the shape's own cell) rides the
physics-solver slice; the stairs' true two-box geometry (the ascending
half) and the upside-down HALF bit ride the metadata-variants slice; the
cocoa pod's per-age box and the sign/button/lever models keep their
walk-through stand-ins (the reference's thin mounts, no body ever rests
on them); the dragon egg and the anvil ride their unregistered blocks'
own slices.

## Slice 51 — the stats family (the records repair)

The stats slice itself landed in `abafb26` (author-verified); this entry
repairs the worklog/ledger gap that commit left behind.

- **EngineStats** (`server/stat/EngineStats.java`): the vocabulary over
  the reference's `stat/Stats.java` — the 48 general rows verbatim, the
  item families (USED/MINED) over the dotted registry scheme with the
  vanilla-name translation band (the planks/log/leaves folds, the
  `wooden_door`, the `reeds` and `tallgrass`/`deadbush` spellings, the
  crop/stem stages, the dye families' colored members) and the merge
  table (the flowing fluids onto their stills, the lit/powered
  literals, the doubles onto their singles), the entity kill pair over
  the old capitalized `Entities` keys (PigZombie/Ozelot/MushroomCow/
  EntityHorse/LavaSlime; the golem pair excluded — the spawn-egg-only
  band).
- **PlayerStats** (`server/stat/PlayerStats.java`): the per-session
  counters (increment/set/get, the dirty take, the snapshot/load, the
  since-death clock).
- **The hooks**: the play/since-death tick clocks, the chest family's
  opens, the crafting-table/furnace/beacon/hopper/dispenser/dropper/
  brewing interactions, the cake bite, the pot planting, the noteblock
  tune, the record insert, the enchant, the cauldron fill/drain, the
  damage tenths (taken/dealt), the death, the kill credit through
  `MobEntity`'s new `playerKiller` carrier (melee + arrow), the mined
  blocks, the crafted items, the used items at the placement, the
  broken tools, the drops, the leave.
- **Persistence**: the ZPD v9 tail (the stats map) with the load
  restore (`PlayerDataStore`/`PlayerSnapshot`).
- **Protocol**: 0x37 Client Statistics + the Client Status 1
  REQUEST_STATS arm through `engine.requestStatistics`
  (`ClientLink`, `Protocol18`, `V18Connection`).
- Tests: `StatsTest` (5). The touched-area batches green
  (`--max-workers=1`).

## Slice 52 — the red sandstone family (1.8's own four)

The reference's `RedSandstoneBlock`/`RedSandstoneSlab`/`Block.java` rows
1379-1390, 1:1 — the four blocks 1.8 itself added, plus the red sand
variant they are built from.

- **The red sand** (`minecraft:red_sand`, the sand 12's RED_SAND variant
  with the id whole in the nibble): the gravity band joins it
  (`BlockUpdateSystem` — `SandBlock` falls in both variants), the cactus's
  soil consult accepts it (`BlockCactus`'s `Blocks.SAND` read covers both
  variants in the reference), the behavior row is the sand's own 0.5
  shovel row, the drop the sand item itself at damage 1 (one wire item,
  the damage domain — there is no separate red sand item id in 1.8.8).
- **The three TYPE states over 179** (`RedSandstoneBlock.Type.byId`'s
  band): DEFAULT/CHISELED/SMOOTH — `setStrength(0.8F)` Material.STONE
  with the pickaxe gate; the family item (the reference's
  `VariantBlockItem`) carries the TYPE id in its damage field with the
  out-of-band clamp.
- **The four facing stairs over 180** (the Bukkit band E=0 W=1 S=2 N=3
  over the SMOOTH base — the reference's `new StairsBlock(state)`): the
  look-facing placement, the masonry-stairs light/shape rows.
- **The slab pair over 182 + the double 181** (`SingleRedSandstoneSlab
  Block`: the HALF bit 8, the RED_SANDSTONE variant id 0; `setStrength
  (2.0F).setBlastResistance(10.0F)`): every form's drop the single-slab
  item (the reference's `getDropItem` consult), the family merge into
  the family's own 181 double (the SEAMLESS bit never settable by
  gameplay — the plain form registers alone; the seamless skin rides
  the metadata-variants slice).
- **The crafting five** (the reference's own rows): the 2x2 masonry over
  the RED_SAND sand (BlockRecipes line 26), the SMOOTH fold (2x2 of the
  DEFAULT yields four SMOOTH), the CHISELED column (two slabs stacked),
  the `###` row (three of any red sandstone yield six slabs) and the
  3-step stairs (four from six).
- **The stats merges** (`EngineStats`): the red sand onto the sand row,
  the TYPE states onto the base name (the vanilla per-block-id scheme —
  mining any of 179's states counts on
  `stat.mineBlock.minecraft.red_sandstone`), the stair facings onto the
  stairs' base, the top slab onto the single. The Slice 51 author's
  anticipatory `double_red_sandstone_slab` row corrected to the actual
  registry names (`double_stone_slab2` onto `stone_slab2`).
- **The audit repairs riding the slice**: the 0x37 Client Statistics
  conformance row (the Slice 51 commit declared the constant but left
  the table row out — the "Unload Chunk class of bug" the conformance
  guard exists for); the horse steering flake re-run green in
  isolation (the documented flake family).
- Tests: `RedSandstoneBlocksTest` (5) + the integrity rows (the family's
  own wire table) + `BlockRegistryTest` 751 + `CraftingRecipeTest` 203.
  Full core + protocol + api suites green, `--max-workers=1`.

Ledgered: the double's SEAMLESS skin (the reference's `DoubleRedSand
stoneSlabBlock` seamless bit — no gameplay path sets it; a placed pair
of same-family slabs becomes the plain 181 form) rides the
metadata-variants slice; the mesas (the red sand's source biome — the
engine's biome set has no mesa) ride the world-generation family's own
slice; the wider per-state stats folds for the pre-52 families (the
oak stair facings still counting on their own state names) ride a
stats-completeness slice; the sandstone family's own missing recipe set
(the 2x2 sand masonry, the smooth fold, the chiseled column, the
stairs — the engine carries only the slab row today) rides the
recipe-completeness slice.

## Slice 53 — the anvil family (the block side)

The reference's `AnvilBlock`/`FallingBlock`/`FallingBlockEntity`, 1:1 —
the twelve states, the falling walk, and the landing damage.

- **The twelve states over 145**: the FACING horizontal band (S=0 W=1
  N=2 E=3) in the low pair, the DAMAGE << 2 in the high pair — the
  flattened names `{anvil, anvil_chipped, anvil_damaged}` ×
  `{"", _west, _north, _east}`. `setStrength(5.0F)` Material.ANVIL
  with the pickaxe gate; the drop the family item with the DAMAGE
  damage.
- **The family item** (`minecraft:anvil`, stack 64 — the AnvilItem's
  own domain): the damage field is the DAMAGE id, the creative menu's
  intact/chipped/damaged three.
- **The placement arm**: the facing is the placer's own horizontal
  facing turned clockwise (`getHorizontalFacing().clockwiseY()`), the
  DAMAGE the held stack's damage (the reference's `metadata >> 2`).
- **The gravity join**: every state in the sand band (the two-tick
  schedule — `FallingBlock`'s own `getTickRate` 2).
- **The landing walk** (`takeFallDamage` 1:1): the fall-distance
  accessor on the falling entity (the accumulated displacement —
  exactly the vanilla `fallDistance` the walk receives), the manager's
  anvil landing hook, the hurt (`ceil(distance - 1)` over the 0.98 box,
  every mob and playing non-spectator takes `min(floor(i * 2), 40)`),
  the progression roll (`0.05 + i * 0.05` walks the DAMAGE forward;
  past 2 the brokeAnvil path — nothing placed, nothing dropped), the
  progressed drop carrying the walked damage
  (`getDropItemMetadata`'s own consult), and the land sound on the
  successful placement (world event 1022 — `random.anvil_land`).
- **The tables**: the CollisionShapes axis band (the W/E z-thin box,
  the S/N x-thin box — `updateShape`'s own pair, the DAMAGE never moves
  the box), the CUTOUT light rows (`setOpacity(0)`,
  `isSolidRender` false), the `dig.anvil` band, the stats merges (every
  state folds onto the anvil row — the vanilla per-block-id scheme).
- **The audit repair riding the slice**: the Slice 52 commit had
  silently dropped the weighted pressure-plates' light rows (a failed
  multi-edit had partially applied before reporting failure) — the 49a
  section restored, the redstone batch re-run green. The full-suite
  verification protocol tightened: every slice now re-runs the
  touched-area batches rather than trusting the incremental full run.
- Tests: `AnvilBlocksTest` (4) + the `FallingBlockEntityTest` manager
  pair (the progressed placement over the live world, the brokeAnvil
  path) + `BlockRegistryTest` 763. Full core suite green (the five
  touched-area batches + the full run), protocol + api green,
  `--max-workers=1`.

Ledgered: the AnvilMenu GUI (the repair + rename window — the two
inputs, the level-cost ladder, the enchantment merge, the material
repair, the name-tag cost) rides its own slice (53b); the vanilla
1.8.8 registers NO anvil crafting row (none added); the damage-source
typing (the anvil death message's own source string) rides the
damage-source band slice.

## Slice 54 — the end portal family (the block + the fill walk)

The reference's `EndPortalBlock` + `EnderEyeItem.useOn`, 1:1.

- **The block 119**: the unbreakable film (`setStrength(-1.0F)` —
  never diggable, no drops, no pick item), the 1/16-tall walk-through
  shape (`addCollisions`' empty override — the WorldSolidity blacklist
  joins the nether portal's), the `setLight(1.0F)` emission 15 with
  the CUTOUT film passing whole.
- **The ender eye's frame arm**: an empty frame takes the eye (the
  facing preserved through the flattened pair), the survival hand pays
  the stack, and the sixteen SMOKE_NORMAL particles ride the
  reference's own 5/16..11/16 offset band at the 0.8125 height — the
  new `Smoke` fx event over the wire's PARTICLE_SMOKE_NORMAL.
- **The frame-complete fill walk** (the reference's own geometry): the
  clicked frame's FACING into the interior, the 3-row scan along the
  clockwise perpendicular (k=-2..2 — the first and last eyed frames
  exactly two apart), the far row at FACING × 4 over the same band,
  the two side columns at the row's ends (each FACING × 1..3) — all
  eyed frames or the walk gives up, then the 3×3 interior fills with
  the portal block.
- Tests: `EndPortalBlocksTest` (3 — the rows, the vanilla ring's
  complete fill over the live world, the refused fill on a missing
  far-row eye) + `BlockRegistryTest` 764. The touched-area batches
  green, protocol + api green, `--max-workers=1`.

Ledgered: the contact arm (`onEntityCollision` → `changeDimension(1)`
— the teleport into the End) rides the End dimension's own slice (the
engine has no third world yet); the frame's comparator signal (the
analog 15 with the eye, `updateNeighborComparators`'s own wake) rides
the comparator-source slice; the eye's throw arm (the stronghold
divination entity + the `findNearestStructure` consult) rides its own
slice (the ender-eye entity).

## Slice 55 — the dragon egg (the obelisk + the teleport)

The reference's `DragonEggBlock`, 1:1.

- **The block 122**: `setStrength(3.0F)` blast 15 with the STONE sounds,
  the `setLight(0.125F)` emission 1, the 1/16-inset obelisk box, the
  CUTOUT film passing whole. No item form (`getPickItem` null — the
  landing drop consult finds nothing, the egg never yields a stack).
- **The own fall schedule**: `DragonEggBlock extends Block` (NOT
  FallingBlock) — its own `tryFall` over the same `canFallThrough`
  gate, with `getTickRate` 5 (the hover sits five ticks out, not the
  sand family's two).
- **The click teleport** (both arms — `use` and `startMining`): 1000
  attempts at the reference's own ±16/±8/±16 offsets, the target an
  AIR cell, the move with the source removal. The out-of-band targets
  are the reference's own vanish arm (the set fails below the world,
  the source removes anyway — the egg destroys itself teleporting
  below y 0). The portal particles are the client's own rendering over
  the block change.
- Tests: `DragonEggBlocksTest` (3) + `BlockRegistryTest` 765. The
  touched-area batches green, `--max-workers=1`.

Ledgered: the egg's placement onto the exit portal (the End's
dragon-death spawn) rides the End dimension's slice; the egg's drop on
a torch/piston harvest never exists (no item form — the reference
registers none).

## Slice 56 — the barrier

The reference's `BarrierBlock`, 1:1.

- **The block 166**: `setUnbreakable` (hardness -1, never diggable, no
  drops), the invisible translucent solid (the -1 render, light passes
  whole), the full cube that blocks bodies.
- **The item** over the shared identifier 166 (`register(Blocks.
  BARRIER)` — the /give route; the reference keeps it off every
  creative tab).
- Tests: `BarrierBlockTest` (1) + `BlockRegistryTest` 766. The
  block/interaction batches green, protocol green, `--max-workers=1`.

## Slice 57 — the item-gap pair (the nether star + the writable book)

- **The nether star** (`minecraft:nether_star`, Item.java's 399, stack
  64): the item + the beacon craft (BlockRecipes line 82 — the glass
  cap `GGG` over the star `GSG` on the obsidian base `OOO`). The
  wither's drop source rides the wither boss's own slice.
- **The writable book** (`minecraft:writable_book`, 386, stack 1 — the
  writing stack): the item + its shapeless craft (CraftingManager line
  58 — the book, the ink sac (the dye's damage 0), the feather). The
  sign/write flow rides its own slice.
- **The audit note**: the netherbrick (405) and the speckled_melon
  (382) were alias false-positives in the earlier gap scan — both live
  under their modern names (`nether_brick`/`glistering_melon`) with
  the correct wire rows; the netherrack smelt (the 0.1F row) already
  lands on the right item.
- Tests: `CraftingRecipeTest` 205 + the shapeless band 29. The
  crafting/item/furnace/brewing batches green, `--max-workers=1`.

## Slice 58 — the double wooden slab family

The reference's `DoubleWoodenSlabBlock`/`WoodenSlabBlock`, 1:1.

- **The six variants over 125**: the planks variant id whole in the
  nibble — the oak carries the wire's own `double_wooden_slab` name,
  the other woods their own `double_{wood}_slab`.
  `setStrength(2.0F).setBlastResistance(5.0F)` WOOD_SOUNDS.
- **The two-slab drop law**: `getBaseDropCount`'s `isDouble ? 2 : 1` —
  breaking a double yields TWO single slabs with the variant damage.
- **The slab-merge rewiring**: each wood's pair now doubles into its
  OWN 125 form — the pre-58 planks stand-in retired (placing a slab
  onto a same-family slab makes the proper DoubleWoodenSlabBlock
  state, no longer the full planks cube the client renders with a
  seam-less texture).
- **The stats merges**: every 125 state counts on the 126
  `wooden_slab` row (the vanilla per-block-id scheme).
- Tests: `DoubleWoodenSlabTest` (2) + `BlockRegistryTest` 772. The
  block/interaction/stat/crafting batches green, protocol green,
  `--max-workers=1`.

Ledgered: the seamless skin (the stone-slab 43 family's SEAMLESS bit)
still rides the metadata-variants slice (no gameplay path sets it).

## Slice 59 — the wither boss (WitherEntity + WitherSkullEntity, 1:1)

The boss that closes the 1.8.8 hostile set: the three-head quarry model,
the 220-tick blue window, the skull family, the sweep, and the drop.

- **The body** (already riding Slice 59a's `MobType.WITHER`: 64, the
  0.9x3.5/300-health fire-immune UNDEAD boss, `mob.wither.*`, the
  daylight-burn exemption through `bossFamily`, the never-despawn).
- **The mind** (`tickWitherMind`, dispatched before the goal layer like
  the blaze): the flight shaping on the main head's quarry (the rise
  gate `y < ty || !half && y < ty+5` — the WIP's inverted `half` fixed
  to the reference's `!isAtHalfHealth`, the weakened body hugs lower),
  the 0.6 dampened hover, the 0.5-capped drift beyond 9 squared, the
  yaw follow.
- **The blue window**: `witherSummoned` SETS the health to the third
  (the WIP's `Math.max` fixed — the 22 ten-heals carry 100 back to the
  300 cap exactly at the end), the countdown's end fires the birth
  (the 7.0F explosion at the eye + the GLOBAL 1013 — every player,
  every dimension, the reference's `doGlobalEvent` broadcast), the
  per-tick slot-20 Int dirty.
- **The three heads**: the main head reads the hunt (`nearestVisibleEntityId`),
  the side heads run their own quarries — the 10-20 acquisition band,
  the quarry-validity walk (gone/dead/beyond-30/unseen EYE-TO-EYE/creative),
  the 40-60 volley, the 16-volley charged barrage on normal+ (the
  plus/minus 10/5/10 box), and the reference's own 10-roll box
  acquisition over the living non-undead (the rejected pick leaves the
  list, the mob's own random).
- **The skull family** (`Kind.WITHER_SKULL` object 66, the API kind):
  `launchWitherSkull` rides the shooter ctor (the 0.4-sigma tilt into
  the 0.1 acceleration) from the head's own mount point
  (getHeadX/Y/Z — the center at +3.0, the flanks at plus/minus 1.3 at
  +2.2), the charged blue's own 0.73 drag, the flat 8.0 hit, the kill's
  5.0 heal to the shooting wither, the WITHER II curse (10 s normal,
  40 s hard, amplifier 1), the 1.0F detonation (no fire), and the
  no-deflect surface.
- **The hurt gates** (the manager's full overload, the reference's
  order): the DROWN refusal, another wither's skull never lands, the
  window blocks everything but the void, the half-health ARROW-only
  shield (the fireball family still lands — the source pair rides the
  CombatSink's projectile-carrying overload), the UNDEAD melee family
  never wounds (players exempt), and every passing wound arms the
  20-tick sweep + the charged +3.
- **The sweep**: the 3x3x4 around the feet under mobGriefing, every
  non-air destructible block breaks (bedrock/end-portal/frame/command-
  block/barrier excepted) with its no-tool drops under doTileDrops, the
  1012 when at least one fell.
- **The drop**: the one nether star (the item's only source) under the
  never-despawn flag (`ItemEntity.setNeverDespawn`), the 50-XP band,
  the killWither stat row.
- **The 1:1 audit riding the slice**: `MobType.damageCategory` and the
  potion system's undead consult gain the pigman + the wither (both
  UNDEAD in 1.8.8 — ZombiePigmanEntity extends ZombieEntity, the
  inheritance the earlier band missed); the boss refuses every potion
  effect (the empty `addStatusEffect` override) through the new
  `refusesEffects` consult.
- **The wire**: the spawn's four Ints (17-19 the heads, 20 the
  countdown), `sendWitherMetadata` on the dirty, the global Effect
  flag for 1013, the skull's objectData (the shooter) + the charged
  byte at index 10, `wireEntityNameOf("Wither")`.
- Tests: `WitherBossTest` (7) + `WitherSkullTest` (4). The
  entity/projectile batches green (272), the full suite green at the
  commit, `--max-workers=1`.

## Slice 60 — the skull block family + the wither's soul-sand summon

The build that births the boss: `SkullBlock`/`SkullItem`/`trySpawn` 1:1.

- **The block family**: the six facing states over 144 (the FACING
  nibble the reference's Direction ids, the floor mount at metadata 1
  carrying the base `minecraft:skull` name), the six facing collision
  boxes (updateShape 1:1 — the DOWN case falls to the floor box, the
  reference's own switch-default quirk), the DECORATION pass-whole
  light, the 1.0 stone dig rows with NO table drop.
- **The tiles** (`SkullManager`): every skull's TYPE (skeleton 0 ..
  dragon 5) + the 16-step floor rotation, the wire's 0x35 action-4
  packet (x/y/z + SkullType + Rot, the new writeNbtByte), the
  chunk-send replay, and the listener fan (the beacon's own model).
- **The placement** (`placeSkullOnTick`, the sign's template): the
  block lands at the clicked face's offset with the FACE as its FACING
  (the item's own rule), the tile writes the stack's damage as the
  TYPE, the floor mount takes the placer's yaw as the rotation, and a
  wither skull consults the summon walk.
- **The summon** (trySpawn 1:1): the gates (type 1, y at least 2, not
  peaceful), the 3x3 pattern — three wither-skull TILES (the predicate
  reads the tile, not the block face) over a soul-sand row on an
  air/soul-sand/air base — searched in all four horizontal planes with
  the placed skull at any of the three top cells, the 9-cell clear
  walk (tiles with it), and the birth: the wither at the top-center
  +0.5/+0.55/+0.5 with the axis yaw (the X-run reads 0, the Z-run 90
  — line 191's law), `witherSummoned()`'s 220-tick window + third
  health, and the Poof stand-in for the 120-particle snowball column.
- **The typed drop** (onRemoved 1:1): the break reads the tile and
  drops the skull ITEM with the TYPE as the damage (397 via the
  ITEM_OVERRIDES split) — the survival break pays it, the creative
  break never reaches the listener (the NODROP law).
- **The wire split**: `minecraft:skull` carries block 144 on the chunk
  wire and item 397 in slots — the sign's own 63/323 model. The
  duplicate-key collision this slice caught in testing (the base map
  already carried the item's 397 row from the wither skeleton's drop)
  threw at static init and stalled every join — the split fixes both
  directions.
- Tests: `SkullSummonTest` (5) + `BlockRegistryTest` (778). The
  protocol module green (the crafting integration suites recovered),
  the block/item/skull/wither batches green, `--max-workers=1`.

## Slice 61 — the End dimension, part one (the island world + the crossing)

The third EngineWorld, vanilla's DIM1.

- **The generator** (`TheEndWorldGenerator`): TheEndChunkGenerator 1:1 —
  the five-noise stack (16/16/8/10/16 octaves off one seed-shared
  Random), the 2x2x33 heightmap at stride 1, the trilinear 0.25/0.125
  fill, END_STONE above zero. The island envelope
  `100 - sqrt(x²+z²)*8` clamped [-100,80] — the central island falls to
  void by ~radius 100 — with the -3000 shelf above heightmap midpoint
  and the -30 floor below sample 8. The surfaces pass stays as the
  reference's own zero-write loop (the End carries no STONE).
- **The decorate**: the 1-in-5 EndPillarFeature roll — the surface/base
  ring gates, `nextInt(32)+6` height, `nextInt(4)+1` radius, the
  obsidian cylinder `dx²+dz² <= r²+1`, the bedrock cap. The veins walk
  (nothing places — no stone) skipped whole; the crystal + the dragon
  rides their own slices.
- **The world boot**: `worldName_end`, `DIM1/zamin-delta.bin`, the
  `simulation:end` domain, `worldFor(1)`, and the five dimension-routing
  sites unified through it.
- **The crossing**: the end portal's INSTANT arm (EndPortalBlock
  .onEntityCollision — no stand clock, the rider gate) → the End arrival
  at the forced spawn (100, 50, 0) with the fixed 90-degree yaw and NO
  portal-forcer walk (the reference's `dimension != 1` gate). The
  obsidian platform is the ledgered adaptation (the reference tree
  carries no platform code — the decompilation gap): a 5x5 floor +
  air pocket written with every arrival.
- **The death fix riding the slice**: every respawn now resets the
  dimension to 0 (the reference's `respawn(player, 0, ...)` — dying in
  the nether previously left the session's dimension field stuck at -1
  while the wire already said overworld).
- Tests: `TheEndWorldTest` (6). The generator batch green,
  `--max-workers=1`.

NEXT (in order): (1) the ender crystal entity (EnderCrystalEntity 1:1:
object 200, the render-tick counter, the End-only fire under the body,
the any-damage 6.0F explosion, the beam Int at synced slot 8) + the
pillar-top crystal spawn arm in the decorate (EndPillarFeature lines
49-51) + the attack dispatch; (2) the dragon fight (EnderDragonEntity
620 lines: the phase family, the multi-part body, the flight model, the
crystal heal, the death — 500+12000 XP, the fountain, the egg) — likely
two slices; (3) the exit fountain's End-to-overworld redirect (old 1 +
new 1 → the overworld, the GameEvent-4 credits + the leavingTheEnd
respawn); (4) the smaller entity gaps: the spawnable pair (experience
bottle 75 + fireworks 76), the minecart variants (TNT 45/hopper 46/
furnace 44/spawner 47/command-block 40), the fishing bobber (90), the
giant (53).

## Slice 62 — the ender crystal (the End pillar tops + the any-damage death)

The body that heals the dragon and pops to anything.

- **The system** (`EnderCrystalSystem` + `EnderCrystal`): the End-only
  registry, the 2x2x2 box, the spin (`renderTicks`, born at
  `nextInt(100000)`, never synced), the synced slot-8 damage clock (born
  5 — `registerSyncedData(8, explosionCountdown)` 1:1), the tick's
  feet-cell fire write (the not-fire gate: the generator's BEDROCK cap
  eaten on the first tick — vanilla's own burning pillar tops), and the
  death (any wound: the slot-8 zero broadcast, the removal, the 6.0F
  destructive blast — the six-arg overload's createFire=false,
  destructive=true).
- **The blast ceiling**: 16*power+1 = 97 (the reference Explosion's
  (d10²+d10)/2*8*f3+1 at density 1, f3 = power*2) — the engine's
  detonateAt falloff carries it.
- **The dimension-aware blast walk**: `detonateAtIn(world, service,
  dimension, ...)` — the End's own ExplosionService (BlastWorld
  parameterized), the wounds + the wire carrying the blast's own
  dimension (closes the pre-62 cross-dimension wound/wire leak), the
  adapter's observer gate on the new ExplosionEvent field.
- **THE DRAIN-LOOP DIMENSION FIX riding the slice**: the pre-62 loop
  stamped the nether's records dimension 1 (the End's own id) — every
  fortress spawner's cell gate and tile replay read the OVERWORLD. The
  drain now iterates {0, -1, 1} with the real ids.
- **The generation arm**: `PendingEnderCrystal` (the populate record —
  EndPillarFeature lines 47-49, the nextFloat()*360 yaw) + the drain
  (the chest-minecart idempotence shape) + the ZEC store (the
  destroyed-pillar marks — a popped pillar never re-spawns; the
  crystals themselves rebuild from the deterministic decorate).
- **The combat arms**: the melee dispatch (the armor stand's reach band,
  the End-dimension gate) + the projectile `CrystalSink` (any
  projectile crossing the box pops it — the arrow's pop; the consult
  rides before the block arm).
- **The wire**: Spawn Object 200 + the slot-8 TYPE_INT + the Destroy +
  the wound's dirty re-send + the join snapshot + the End-arrival
  re-send (onDimensionChanged's dimension-1 arm).
- Tests: `EnderCrystalTest` (10) + the spawner/dimension/protocol
  batches green; the full core suite green modulo the documented load
  flakes (GameRule/SpawnEgg/Compute isolation-green), --max-workers=1.

NEXT (in order): (1) the dragon fight (EnderDragonEntity 620 lines: the
phase family, the multi-part body, the flight model, the crystal heal,
the death — 500+12000 XP, the fountain, the egg) — likely two slices;
(2) the exit fountain's End-to-overworld redirect (old 1 + new 1 → the
overworld, the GameEvent-4 credits + the leavingTheEnd respawn); (3) the
smaller entity gaps: the spawnable pair (experience bottle 75 +
fireworks 76), the minecart variants (TNT 45/hopper 46/furnace 44/
spawner 47/command-block 40), the fishing bobber (90), the giant (53).

## Slice 63 — the ender dragon (the seven-part body + the steering flight + the death walk)

The End's own boss, 1:1.

- **The parts**: head 3x3 / body 5x3 / tails 2x2 x3 / wings 4x2+4x3 —
  server-side hulls placed from the 64-slot segment ring; the WIRE ID
  CONVENTION dragonId+1..+7 (the spawn's eight-id block, the client's own
  re-alignment).
- **The flight**: the flap, the steering (the quarry's hover band / the
  point's drift, the 100/22500 band, the clamp, the turn, the nose accel,
  the dampen), the part placement (the segment curve, the f12 pitch, the
  tail reaches), all 1:1. The noClip body skips the engine's whole
  tickBody.
- **The damage gates**: the non-head /4+1, the head-front reset, the
  player-or-explosive source (mob arrows glance off). The melee part
  routing + the projectile DragonSink.
- **The crystal link**: the 1-in-10 nearest scan, the +1 heal cadence,
  the pop's 10.0 explosive wound (the reference's own head hit).
- **The contact arms + the sweep**: the wing fling (d/d4*4 + 0.2), the
  head's 10.0, destroyBlocks' immune five with the restrictMovement
  write-back.
- **The death walk**: the 200-tick rise-and-spin, the 1018, the XP
  batches (9x1000 + the 200th's own 1000 + the final 2000 = 12000), the
  createPortal (the bedrock fountain + the exit portal + the four torches
  + the egg), the ZDR defeated mark, the removal. The standard death
  never runs.
- **The spawn**: the chunk-(0,0) decorate record, the drain's
  alive/defeated guards, the dimension-1 stamp.
- Tests: `EnderDragonTest` (6). The dragon/wither/crystal/End/mob/
  spawner/ranged batches green; the full core suite green modulo the
  documented load flakes (Weather/GameRule/Compute isolation-green),
  --max-workers=1.

NEXT (in order): (1) the exit fountain's End-to-overworld redirect (old 1
+ new 1 → the overworld — Entity.changeDimension lines 1703-1706; the
GameEvent-4 credits + the leavingTheEnd respawn) — the tickPortalStand's
end_portal arm in dimension 1 currently returns early; (2) the smaller
entity gaps: the spawnable pair (experience bottle 75 + fireworks 76),
the minecart variants (TNT 45/hopper 46/furnace 44/spawner 47/
command-block 40), the fishing bobber (90), the giant (53); (3) the
sections 27/28 audit (the native API exposure + the plugin concurrency
contract vs the current code).

## Slice 64 — the exit fountain's walk (the End-to-overworld exit)

The End's own goodbye, 1:1.

- **The credits arm** (ServerPlayerEntity.changeDimension lines 444-457,
  the dimension==1&&dimension==1 arm): `leaveEndOnTick` — the
  `leavingTheEnd` session state (the reference's own removed-from-the-
  world marker), the GameEvent-4 win screen through the link's
  `playGameState` (the elder-guardian precedent's carrier). The
  LEAVE_THE_END achievement rides the achievement family's own slice
  (no achievement rows in the stat store yet); the non-player arm
  (Entity.changeDimension's old-1+new-1 overworld-spawn-surface
  redirect) rides the mob portal-collision simulation (mobs do not yet
  collide with portal cells).
- **The alive respawn** (handleClientStatus lines 740-744):
  `performRespawn`'s credits arm FIRST — `endCreditsRespawnOnTick`
  (PlayerManager.respawn(player, 0, true) + PlayerEntity.copyFrom's
  comesFromTheEnd=true carry): the overworld landing at the bed/world
  spawn with the WHOLE body kept — health, food, saturation, XP,
  inventory — no reset, no drop. The death arm's own walk untouched
  (the flag gate rides ahead of the dead gate, the reference's own
  if/else-if order).
- **The removed body**: `movementProposal` answers nothing while the
  credits play (handlePlayerMove line 198's gate); `tickPlayerBodies`
  skips the whole simulation (no food economy, no breath, no portal
  arms, no stats clock — the reference's removed entity does not
  tick).
- **The removeEntity wire**: `SurvivalListener.onEndCredits` — the
  adapter untracks the mover from every other observer (the Destroy
  Entity the reference's entity map sends).
- **THE CROSS-DIMENSION VISIBILITY GATE** (a real gap the End made
  visible: the End arrival cell x=100 sits inside the overworld
  spawn's view range — the nether's 8:1 coordinates had kept the leak
  below the raw-distance radar): `broadcastMovement` + `trackPlayer`
  gate on the mover's vs the observer's dimension; a body in another
  dimension (or removed for the credits) is invisible — the
  reference's per-world entity map.
- **THE END-DOMAIN BOOT BUG** the new engine-level test exposed: the
  boot loop entered simulationDomain + netherDomain but the End's
  `simulation:end` domain was created inline and NEVER entered — every
  tick-lane End write (enterEndOnTick's arrival platform, the
  crystal's feet-cell fire, the dragon's exit fountain) threw
  OwnershipViolationException on a real booted server; the
  unit-harness slices never noticed. The domain is now a field,
  entered alongside the nether's, exited in the finally.
- Tests: `EnderExitAcceptanceTest` (2) — the full walk (the credits
  armed, the single GameEvent 4, the movement ignored, the alive
  respawn: dimension 0, the world spawn, health/food/saturation/XP
  all carried) + the death respawn's regression. The related batches
  green (survival/End/dragon/crystal/portal/dimension).

NEXT (in order): (1) the smaller entity gaps: the spawnable pair
(experience bottle 75 + fireworks 76), the minecart variants (TNT 45/
hopper 46/furnace 44/spawner 47/command-block 40), the fishing bobber
(90), the giant (53); (2) the sections 27/28 audit (the native API
exposure + the plugin concurrency contract vs the current code).

## Slice 65 — the spawnable pair (the bottle o' enchanting + the firework rocket)

The last two spawnable entities, 1:1.

- **The bottle** (ExperienceBottleEntity): the Kind 75 + the API row, the
  0.07 gravity, the lobbed launch (the ctor's own direction with the
  -20-degree start-pitch offset on the vertical term alone, the dispense
  walk at power 0.7), and the burst arm — the 2002 splash event + the
  3+rand(5)+rand(5) points through the roundSize ladder, NO damage ever
  (the reference's onCollision calls no takeDamage — a struck body never
  loses a heart, a pillar crystal never pops).
- **The roundSize ladder** (ExperienceOrbEntity.roundSize lines 210-227)
  now lives in ExperienceOrbManager: the 2477..1 tiers + spawnSplitBurst
  (the reference's own while-remaining walk).
- **The rocket** (FireworksEntity): the FireworksSystem + entity — the
  launch at the clicked face's own point, the near-zero drift + the 0.05
  lift, the fuse 10 + rand(6) + rand(7) (the plain i=1 band — the Flight
  NBT rides the item-NBT slice), the 1.15x/0.04 flight law, the age-0
  launch sound at volume 3.0, the burst (the Entity Status 17 + the
  removal).
- **THE CURSOR UNIFICATION riding the slice**: the 1.8 C08's own three
  cursor bytes now flow (the adapter decodes all three as the 0..1
  fractions, the engine's new 8-arg overload carries them, the slab +
  trapdoor half reads unified onto the >= 0.5f comparison).
- **The wire**: the rocket's Spawn Object 76 + the slot-8 SLOT item + the
  spawn velocity (EntityMap's (64, 10, syncVelocity) tracker), the
  10-tick velocity re-syncs, the burst's Entity Status 17, the Destroy;
  the bottle's Spawn Object 75 bare with the shard family's teleport
  movement.
- Ledgered: the fireworks' flight-duration + explosion-shape NBT (the
  item-NBT slice), the firework crafting family, the dispensed firework,
  the used-item stat rows.
- Tests: `FireworksAndBottleTest` (5) + `ExperienceOrbManagerTest` (+2).
  The projectile/orb/placement/exit/survival batches green; protocol +
  api suites green.

NEXT (in order): (1) the minecart variants (TNT 45/hopper 46/furnace 44/
spawner 47/command-block 40); (2) the fishing bobber (90) + the giant
(53); (3) the sections 27/28 audit (the native API exposure + the plugin
concurrency contract vs the current code).

## Slice 66 part one — the minecart variants (the TNT cart + the furnace cart)

The variant family opens, 1:1.

- **The Kind family**: the API VehicleKind rows + the engine Kind for the
  five variants (the wire rides the minecart type index the Spawn
  Object's objectData already carries: 2 furnace, 3 TNT, 4 spawner,
  5 hopper, 6 command block).
- **The TNT cart** (TntMinecartEntity): the 80-tick fuse (the powered
  activator rail primes through the manager's tick consult; a re-prime
  never restarts the clock), the burn-down + the detonation at zero
  (4.0 + rand * 1.5 * min(sqrt(speed^2), 5.0) through the engine's
  detonateAt with the 8*power+1 wound + mobGriefing), and the impact arm
  (the fast halt detonates — the engine's track-end halt is the
  collidingHorizontally surface).
- **The furnace cart** (FurnaceMinecartEntity): the 3600-tick coal feed
  + the push aim away from the player, the burn-down + the empty-tank
  zeroing, the push walk (normalize + the anti-reverse gate + the 0.2
  cap), and the fueled acceleration.
- The spawn verbs (the §27.7 gate on each) + the restore arms + the
  place arms + the items (343/407) + the LegacyBlockIds rows.
- Tests: `MinecartVariantsTest` (3). The full vehicle batch green. The
  core-suite run's two failures (PlayerApi/Thorns) were the mid-edit
  compile race — both isolation-green on the completed tree (the
  documented flake class).

NEXT (Slice 66 part two): the TNT death arms + the arrow priming + the
fuse's event-10 wire; the furnace interact + the lit byte + the
cart-vs-cart push; the hopper cart (the 5-slot window + the pickup +
the drain + the activator disable); the spawner cart; the command-block
cart; the remaining items (408/422) + the persistence payload for the
variant states. Then: the fishing bobber (90) + the giant (53); the
sections 27/28 audit.

## Slice 66 part three — the hopper minecart (the 5-slot rolling pickup)

The variant family's third member, 1:1.

- **The cart** (HopperMinecartEntity): the embedded HopperBlockEntity (the
  hopper's own five slots + click semantics, the chest cart's composition
  pattern), the wire's minecart type index 5, and the transfer walk with
  the reference's own NEVER-REASSIGNED `f_0016592` quirk ported verbatim —
  the "last position" stays BlockPos.ORIGIN forever, so away from the
  world-origin cell the cooldown zeroes every tick and the cart attempts
  EVERY tick (the armed 4-tick cooldown only ever holds at cell (0,0,0);
  a successful pull or a FULL item suck arms it).
- **The transfer arms** (HopperSystem.hopperCartTransfer — transferItems
  lines 119-130 through pullItem/pickUpItem): the container-above pull
  first (the mouth at y+0.5, the lookup cell floor(y+1.5), one item per
  attempt through the shared container adapter — the chest, hopper,
  furnace with the bucket-only fuel gate, dispenser family, brewing
  stand, or a chest cart parked above); with no container above, the
  item-entity suck from the 1-box centered on the mouth-plus-one (a full
  consume removes the body and arms the cooldown, a partial keeps the
  remainder); the fallback — the FIRST item entity in the cart's own
  shape grown 0.25 horizontally pushes its whole stack and NEVER arms
  the cooldown (the reference's own return false — the every-tick suck
  the cart is known for).
- **The activator disable** (onActivatorRail lines 63-68): a POWERED
  activator rail DISABLES the cart, an unpowered one re-enables it (the
  inverse sense of the TNT cart's prime) — the manager's tick consults
  the rail power like the TNT cart's; no wire metadata in 1.8.8.
- **The window**: ContainerKind.HOPPER_MINECART + the interact's
  openChestMenu over the cart's five slots (never the seat) + the
  clickHopperMinecartWindowOnTick routing (the hopper menu's own
  semantics, the reference's isValid 8-block gate) + the wire (Open
  Window "minecraft:hopper" with the entity's own display name "Minecart
  with Hopper" — ServerPlayerEntity.openChestMenu's getDisplayName — and
  the 41-slot Window Items through the new engineVehicleHopper lookup).
- **The drops**: the MINECART item + the contents spill + the HOPPER
  block item (never the hopper-minecart item), under doEntityDrops; the
  furnace cart's own missing death drop LANDED too (the FURNACE block
  item — the reference's non-explosive gate ledgered against the
  damage-source surface the vehicle death path does not yet carry).
- **The persistence**: the ZVD store's version 2 — the hopper cart's
  5-slot cargo block (the length names the kind's count) + the variant
  extras (the disable flag + the TransferCooldown row); v1 files load
  with the defaults; the restore re-wires the transfer host through the
  engine's hopper system.
- **THE PART-ONE REPAIR**: FURNACE_MINECART and TNT_MINECART were
  declared but never registered in BuiltinItems' registration list —
  their lookups answered empty (the creative claim path could not
  resolve the held stack). All three variant items (343/407/408) now
  register; the LegacyBlockIds row 408 lands with them.
- Tests: `HopperMinecartTest` (7 — the type index, the quirk + the
  origin-cell contrast, the activator, the live-engine pull from a chest
  above, the interact + the shift-click routing, the persistence
  roundtrip). The vehicle + hopper batches green (34 tests, zero
  failures).

NEXT (in order): (1) the spawner cart (index 4 — the spawner walk over
the cart's own position, the Entity Status broadcasts, the persistence
payload; no item form — Item.java never registers one); (2) the
command-block cart (index 6 — the run-on-rail every 4 ticks, the synced
slots 23/24, the Update Entity NBT 0x49 decode, the item 422); (3) the
fishing bobber (90) + the giant (53); (4) the sections 27/28 audit (the
native API exposure + the plugin concurrency contract vs the code).

## Slice 66 part four — the spawner minecart (the rolling spawner)

The variant family's fourth member, 1:1.

- **The cart** (SpawnerMinecartEntity): the anonymous MobSpawner's walk at
  the cart's own floored cell, with the reference's own "Pig" FIELD
  DEFAULT (MobSpawner line 19 — a summoned cart with no NBT IS a pig
  spawner, ledgered), the 20-tick initial delay, the 200..800 reroll,
  the four-attempt cycle, the 6-mob nearby cap that aborts the WHOLE
  cycle, and the position rolls (nextDouble()-nextDouble() per axis,
  nextInt(3)-1 for y).
- **The broadcasts**: the 2004 flame burst PER SPAWNED MOB (the reference
  fires doEvent inside the loop — the block spawner's engine port fires
  once per cycle; the cart keeps the reference's shape) through the new
  onSpawnerCartFlames listener (the Effect packet at the cart's cell),
  and the delay reroll's broacastEvent(1) over the cart's own
  doEntityEvent (the Entity Status 1, chunk-gated — sendSpawnerCartSpinReset).
- **The host surface** (SpawnHost): the 16-block player gate, the
  same-kind nearby count, and the unobstructed spawn (the SpawnerManager's
  canSpawnAt walk, now public static — shared between the block family
  and the cart).
- **The persistence**: the ZVD v2 variant payload's EntityId/Delay rows
  (MobSpawner.writeNbt's own keys); the SpawnPotentials weighted list
  rides the block family's single-type envelope (ledgered). NO item form
  (Item.java never registers one — the cart is summon/NBT-only).
- Tests: `SpawnerMinecartTest` (6 — the type index + the Pig default, the
  cycle + the per-spawn bursts + the reroll band + the spin reset, the
  nearby-cap abort, the no-player freeze, the persistence roundtrip, the
  live-engine pig spawn beside the rolling cart). The vehicle + spawner
  batches green.

NEXT (in order): (1) the command-block cart (index 6 — the run-on-rail
every 4 ticks, the synced slots 23/24, the Update Entity NBT 0x49 decode,
the item 422); (2) the fishing bobber (90) + the giant (53); (3) the
sections 27/28 audit (the native API exposure + the plugin concurrency
contract vs the code).

## Slice 66 part five — the command-block minecart (the variant family closes)

The variant family's fifth and last member, 1:1.

- **The cart** (CommandBlockMinecartEntity): the CommandExecutor's own
  state (the empty command, the success count, the track flag, the last
  output) with the powered-rail run's own age gate — the reference's
  `ticks - lastExecuted >= 4` with both clocks at 0 (a fresh cart cannot
  run until it has lived 4 ticks).
- **The run surface** (EngineServer.runCommandCart): the CommandService
  dispatch with the executor's own sender — op level 2 (canUseCommand),
  the name "@", the feedback landing in the last output through the
  timestamped recordOutput walk. The reference's
  areCommandBlocksEnabled gate rides the config surface the engine does
  not carry yet (ledgered).
- **The wire**: the synced slots 23 (command) / 24 (last output JSON) as
  METADATA_TYPE_STRING — on spawn (the state-metadata fan rides
  onVehicleSpawned) and on every change (the manager diffs the two
  strings around the tick); the Spawn Object objectData 6.
- **THE 1.8.8 DISCOVERY**: the edit rides the MC|AdvCdm CUSTOM PAYLOAD
  (ServerPlayNetworkHandler lines 1016-1057) — NOT the 1.9+ Update
  Entity NBT 0x49. The minecart arm: type byte 1, the entity id, the
  command string, the track flag; the untracked cart clears its output;
  the advMode.setCommand.success ACK through the new
  ChatService.sendSystem.
- **The item 422** (register's own setCreativeModeTab(null) — the
  engine's registry-based creative menu cannot hide it, ledgered) + the
  BuiltinItems + LegacyBlockIds rows + the rail place arm.
- **The persistence**: the ZVD v2 variant payload's executor rows
  (Command/SuccessCount/TrackOutput/LastOutput).
- Tests: `CommandBlockMinecartTest` (5 — the type index + state, the
  powered-rail cadence with the fresh-cart age gate, the untracked
  output drop, the persistence roundtrip, the live-engine run through
  the real CommandService). The vehicle + persistence + chat batches
  green (35); the protocol + api suites green.

With this, Slice 66's variant family is COMPLETE: the TNT cart, the
furnace cart, the hopper cart, the spawner cart, and the command cart,
all 1:1 with their reference ports.

NEXT (in order): (1) the smaller entity gaps — the fishing bobber (90)
+ the giant (53); (2) the sections 27/28 audit (the native API exposure
+ the plugin concurrency contract vs the current code); (3) the
remaining ledgered gaps (the block command block's MC|AdvCdm arm, the
areCommandBlocksEnabled config gate, the vehicle damage-source surface
for the furnace cart's explosive-death drop skip).

## Slice 67 — the fishing system (the bobber + the rod)

The fishing rod's whole law, 1:1.

- **The system** (server/fishing/FishingSystem): the cast (the eye
  origin pulled back 0.16, the 0.4-power lob scaled 1.5 with the
  gaussian scatter), the flight (the raytrace grounding + the 1200-tick
  stuck clock, the entity clip's zero-damage hook with the angler's own
  5-tick gate, the 0.92 drag, and the water law — the 5-band fraction's
  buoyancy vy += 0.04*(d10*2-1), THE GRAVITY BEING THE d10=0 CASE), the
  catchable cycle (the l modifier's rain/no-sky rolls, the 100..900-minus-
  Lure*100 arming, the 20-80 fish approach, the splash opening THE BITE
  WINDOW of 10-30 ticks), and the retrieve (the hooked drag at 0.1 +
  sqrt(d)*0.08, the bite-window loot + the 1-6 XP, the grounded two, the
  bare zero — the rod wears BY THE RESULT CODE).
- **The loot law** (getResult): junk 0.1 - luck*0.025 - lure*0.01,
  treasure 0.05 + luck*0.01 - lure*0.01, the fish otherwise — the three
  weighted tables verbatim (the junk eleven with the 0.9-damaged boots +
  rod, the treasure six, the fish 60/25/2/13).
- **The use arm** (useItem's FISHING_ROD branch): retrieve-or-cast with
  the reference's random.bow cast sound; the per-tick held-rod gate (the
  rod un-held or the 32-block leash ends the cast); the enchant reads
  through the held rod (Lure cuts the arming, Luck shifts the tables).
- **The wire**: Spawn Object 90 with objectData = THE ANGLER'S id
  (TrackedEntity line 428 — the client draws the line) + the velocity
  triple, the per-tick Entity Teleport, the Destroy — through the new
  FishingSystem.Listener (chunk-gated).
- **THE SLICE-21G/65 REPAIR**: eleven items were declared but never
  registered in BuiltinItems (FISHING_ROD, CLOCK, CAKE, COOKIE,
  ENDER_EYE, EXPERIENCE_BOTTLE, WRITTEN_BOOK, PUMPKIN_PIE, FIREWORKS,
  FIREWORK_CHARGE, NAME_TAG) — their lookups answered empty; all now
  register (the fishing loot's class-init crashed on the missing
  fishing_rod and exposed the whole family).
- Tests: `FishingSystemTest` (7 — the launch conventions, the cycle +
  the bite window + the catch + the XP, the bare zero, the hooked drag +
  the grounded two, the angler gates, the 4000-roll loot distribution,
  the Lure cut). The fishing + items batches green (16); the fireworks/
  trade batches green.

NEXT (in order): (1) the giant (53) — the last of the smaller entity
gaps; (2) the rod's live-engine acceptance (the use path over the wire);
(3) the sections 27/28 audit (the native API exposure + the plugin
concurrency contract vs the current code).

## Slice 67 part two — the giant (the last small entity gap)

- **The row** (MobType.GIANT): Entities.java line 258's "Giant" 53 — the
  6x zombie (3.6x10.8), 100 health, 0.5 walk, 50.0 hit, the flat
  10.440001 eye (getEyeHeight line 18), the BASE monster sounds
  (game.neutral.hurt/die, no idle), NO daylight burn (a plain
  MonsterEntity, not a ZombieEntity), no loot.
- Summon-only in 1.8.8 (no natural spawn, no spawn egg); the engine's
  /summon surface rides its own slice (ledgered).
- Tests: `GiantMobTest` (2). The mob batches green (8).

## The sections 27/28 audit (the native API exposure + the plugin concurrency contract)

- **The doc**: docs/SECTIONS_27_28_AUDIT.md — the evidence-based pass
  over both sections against the Slice 67 tree.
- **§27**: the capability ledger is current (the Slice 66/67 rows
  landed with their slices; the hopper cart's cargo view closed this
  pass); the handle pattern + the threading contracts + the §27.7 hooks
  hold. The honest gaps: the variant carts' internal state (the furnace
  fuel, the TNT fuse, the spawner type/delay, the command string) has
  no read surface yet (the kind identity + the cargo are the current
  surface — the VariantState read view is the next wave); the fishing
  loot override; the packet interception.
- **§28**: the ENGINE-side contract is implemented + adversarially
  tested — ten of the twelve §28.8 cases carry direct tests (the
  reciprocal/self-wait/noisy-owner/I-O-pressure/shutdown five in
  AdversarialContractTest; the wrong-owner/stale/migration trio in the
  ownership suites; the reentrancy/exception pair in EventBusUnitTest;
  the cross-owner transactions in RegionBoundaryTest). The PLUGIN-side
  halves (the lifecycle state machine, the resource scopes, the
  quotas) are the honest blocker — the engine has no plugin host yet
  (the §27.1 order: the native API matures first); cases 6 + the
  plugin-half of 9 are BLOCKED on it.

## Slice 67 part three — the vanilla /summon command

- **The command** (SummonCommand.run 1:1, the engine's envelope): op
  level 2, the vanilla 1.8.8 entity names case-insensitive
  (MobType.byVanillaName — "Zombie", "PigZombie", "LavaSlime", "Giant",
  "WitherBoss", "EntityHorse", "Ozelot", "MushroomCow", "SnowMan",
  "VillagerGolem", "EnderDragon"... — the wire's own spellings, with the
  engine kind names as the fallback), the ~-relative coordinate parse
  (CommandBase.parseCoordinate: bare ~ keeps, ~n offsets, plain
  replaces), the LightningBolt special (the engine's strike verb), the
  chunk-loaded refusal, and the vanilla feedback strings. The NBT arm
  rides the engine's no-item-NBT envelope (ledgered).
- Tests: GiantMobTest (3 — the row, the live spawn, the /summon Giant
  through the chat path with the ~-coordinates + the engine-name
  fallback).

## Slice 68 — the VariantState read view (the §27.3 next wave the audit named)

- **The API** (`zamin-api` `entity.VariantState`): the variant carts'
  own state surfaced through the `Vehicle` handle — four nested views,
  one per kind: `Furnace` (fuelTicks/lit/pushX/pushZ), `Tnt`
  (fuseTicks, -1-unprimed/primed), `Spawner` (spawnType as the public
  Identifier + delayTicks + the five vanilla law constants verbatim —
  200/800, 4, 6, 16, 4), `Command`
  (command/successCount/trackOutput/lastOutput).
  `Vehicle.variantState()` answers empty for the boat/plain/chest carts
  (the chest's container rides cargo()).
- **The pattern** (§27.5, the engine object IS the handle): each engine
  cart class implements its own view directly — no wrapper hierarchy,
  no second game state; the bridge methods are one-name translations
  (fuel() → fuelTicks(), type() → spawnType()) beside the
  already-matching signatures (lit/pushX/pushZ, fuseTicks/primed,
  command/successCount/trackOutput/lastOutput).
- **The contract** (§27.6): live reads on the simulation context (the
  documented owner path `World.execute`), documented on every method;
  scalar reads compose a consistent view inside one owner tick.
- Tests: `VariantStateApiAcceptanceTest` (5 — the four views' reads
  through the public handle alone — §27.10's no-internal-classes rule —
  + the plain kinds' empty answer beside the chest cart's cargo). The
  vehicle batch green (8 classes); the ledger row updated
  (NATIVE_API_CAPABILITIES.md — the "no handle read yet" omission
  closed).

Also this cycle: the HEAD compile defect repaired — the Slice 67
part-two GIANT row missed `V18Connection.wireEntityNameOf`'s switch
(the reference's Entities.java line 258 "Giant", 53); the protocol
module did not compile at HEAD (caught by the fresh-environment build,
masked on CI by an up-to-date compile cache).

NEXT (in order): (1) the fishing loot override + catch intercept
(§27.7's named gap); (2) the packet interception surface (§27.3's
protocol row); (3) the plugin host wave (§28.5/28.6 — the lifecycle
state machine, the resource scopes, the quotas); (4) the ledgered
vanilla gaps (the MC|AdvCdm block arm, the areCommandBlocksEnabled
config gate, the vehicle damage-source surface, the /summon NBT arm).

## Slice 69 — the fishing loot override + the catch intercept (§27.7's named gap)

- **The loot override** (`FishingSystem.LootSource` + `setLootSource`):
  the replaceable strategy over the three reference bands —
  `LootCategory` JUNK/TREASURE/FISH (the getResult lines 487-506 walk's
  own bands), the rod's luck/lure levels passed as the same inputs the
  vanilla roll sees. Answering null falls through PER BAND to the
  vanilla weighted table (the override composes with the reference's
  law instead of replacing the whole walk); setLootSource(null)
  restores the vanilla roll.
- **The catch intercept** (`FishingSystem.CatchInterceptor` +
  `addCatchInterceptor`/`removeCatchInterceptor` + the `CatchContext`
  view): runs on the tick lane between the loot roll and the drop, in
  registration order — each interceptor sees the incoming stack (an
  earlier interceptor's replacement included) and answers the outgoing
  one. Pass-through, replace, or cancel (EMPTY/null). A cancellation
  means the catch never happened: no item drop, no XP, and the retrieve
  answers the bare 0 (the rod's wear gate is the reference's own
  `result > 0`).
- **The roll refactor**: `getResult` returns a `LootRoll` (the stack +
  its band + the enchant levels) — the vanilla probabilities unchanged
  (the same float walk, the same clamp, the same tables verbatim).
- Tests: `FishingOverrideTest` (7 — the band answers, the per-band
  fall-through over 60 rolls with the vanilla-item whitelist, the
  replacement, the cancel (bare 0 + no XP), the chain order/composition,
  the override+intercept composition, the removal restore). The fishing
  batch green (14 tests over the two classes); the ledger row updated
  (the "no public loot-table override" omission closed).

NEXT (in order): (1) the packet interception surface (§27.3's protocol
row — the audit's last §27 gap); (2) the plugin host wave (§28.5/28.6 —
the lifecycle state machine, the resource scopes, the quotas); (3) the
ledgered vanilla gaps (the MC|AdvCdm block arm, the
areCommandBlocksEnabled config gate, the vehicle damage-source surface,
the /summon NBT arm).

## Slice 70 — the packet construction surface (the protocol row's last leg)

- **The audit correction first**: the §27 audit's "protocol stays
  internal" note was STALE — the interception surface has been public
  since Slices 15c (outbound observe/cancel/re-payload) + 23g (the
  inbound observe-or-drop mirror) at `Server.protocolHooks()`, and the
  capability ledger's protocol row was current. The genuinely missing
  leg was §27.3's packet CONSTRUCTION — no way for a plugin to compose
  and send a frame.
- **The verb** (`ProtocolHooks.send(Player, packetId, payload)`): one
  composed PLAY-state frame — the active version's varint id + the
  payload bytes — written through the SAME Netty pipeline as engine
  traffic, so the outbound interception walk sees composed frames like
  any engine frame (observe/cancel/re-payload apply to them too).
- **The wiring** (the boundary held): the engine's ClientLink contract
  ("the engine never sees packets") stays intact — the registry carries
  an adapter-installed `FrameSender` (set at V18ProtocolServer
  construction), the adapter resolves the named player's live
  PLAY-state connection and `V18Connection.sendComposedFrame` builds
  the frame (varint id + payload through `channel.writeAndFlush` — the
  hook handler then the length encoder, the pipeline's own order).
- **The contract**: any thread, non-blocking; the payload copied; the
  sender owns the frame's validity (the engine does not validate
  plugin-composed frames — the raw surface's law, documented); a
  non-PLAY recipient or the headless engine drops the frame (the
  documented result, fine-logged); negative ids and null args refused
  loudly.
- Tests: `PacketSendApiTest` (3, over the real wire: the composed chat
  frame reaches the client, the interception walk cancels it before the
  wire, the contract refusals) + the existing hook suites green. The
  ledger row updated; the audit's §27 gap list now carries all three
  gaps closed (the variant read view, the fishing hooks, the
  construction leg).

## Slice 71 — the plugin host core (§28.5/§28.6's engine side lands)

- **The API** (`zamin-api` `net.zaminmc.torch.plugin`): `PluginState`
  (the §28.5 machine verbatim — DISCOVERED → LOADED → ENABLING →
  ENABLED → DISABLING → DISABLED → UNLOADED, the failure transitions
  documented on the states), `PluginDescriptor` (the immutable
  identity), `PluginBody` (the enable lambda + the ordered-cleanup
  opt-in), `ResourceScope` (the owned-resource ledger: own/own(EventSubscription)/
  own(Task)/own(Computation), ownedCount, generation, close), `PluginTasks`
  (§28.6's quota'd compute/IO lanes — the empty answer IS the refusal),
  `PluginHandle`, `PluginHost` — mounted at `Server.plugins()`.
- **The engine core** (`server/plugin/PluginManager`): the lifecycle
  walks under one control lock (reentrant lifecycle calls from callbacks
  rejected loudly — §28.4's law), the seal→onDisable→sweep disable walk
  (admissions close at DISABLING — the §28.8 case-6 law: disable
  prevents new work the moment it begins, not when it ends), the
  isolated sweep (one throwing close verb never shields the rest), the
  fresh-scope reload (a fresh generation — no duplicates by
  construction), and the quota'd Tasks over the engine's own
  AsyncCompute/BlockingIo lanes with the late-callback generation gate
  (a running computation still completes — the engine's law — but its
  result never reaches the disabled plugin; the drop is counted, §28.9).
- Tests: `PluginHostTest` (9 — the happy walk, the enable-failure
  rollback, the isolated sweep, the DISABLING admissions gate, the
  late-result drop + its counter, the reload no-duplicates + fresh
  generation, the duplicate-id refusal + the freed id, the quota
  flood-vs-neighbor over a gated async facade, the reentrancy
  rejections). The plugin + concurrent + event batches green.
- **The §28 audit updated**: the 28.5/28.6 "NOT STARTED" rows now read
  LANDED with the remaining honest gaps recorded (no filesystem
  discovery/classloading yet — §27.1's order; the registration-time
  scope binding is the later ergonomics wave). §28.8 case 6 is no
  longer BLOCKED.

NEXT (in order): (1) the ledgered vanilla gaps (the MC|AdvCdm block
arm, the areCommandBlocksEnabled config gate, the vehicle
damage-source surface, the /summon NBT arm); (2) the plugin host's
live-engine acceptance (a plugin through the real EngineServer's
plugins() with real event-bus/scope integration); (3) the discovery/
classloading wave when the native API surface settles.

## Slice 72 — the command-block block family + the areCommandBlocksEnabled gate

- **The gate (the audit's ledgered config gap closes)**: EngineConfig
  gains `enableCommandBlocks` (DedicatedServer line 378's
  `getBoolean("enable-command-block", false)` — the vanilla dedicated
  default OFF), ConfigLoader maps the property, and the MC|AdvCdm walk
  is rebuilt 1:1 with the reference's gate order: notEnabled → the
  op-2-creative check → the payload walk → notAllowed. The gate now
  covers BOTH arms (the cart arm was ungated since Slice 66 — the
  ledgered gap). The execution itself stays ungated (the reference's
  own shape: a block that exists runs).
- **The block family (block 137, Block.java line 1299's registration)**:
  COMMAND_BLOCK + COMMAND_BLOCK_TRIGGERED (the TRIGGERED bit, meta 0/1)
  over the unbreakable/6M-blast behavior rows; the tile store
  (`commandblock/CommandBlockManager`) carries the executor's four rows
  (Command/SuccessCount/TrackOutput/LastOutput) in the sign/skull
  block-entity shape; the edit (MC|AdvCdm type 0 — position → the
  executor, setCommand resets the count, the untracked executor clears
  its output); the redstone edge walk in RedstoneSystem
  (neighborChanged: the rising edge flips TRIGGERED + schedules the
  rate-1 tick, the falling edge clears — the run lands on the SCHEDULED
  tick, not the edge; the tick runs the executor + wakes the
  comparators — the success count is the analog signal).
- **The engine verbs**: `areCommandBlocksEnabled()`,
  `updateCommandBlock` (the type-0 landing on the tick lane with the
  no-tile silent skip), `runCommandBlockExecutor` (the CommandService
  dispatch with the executor's own sender — op 2, "@", recordOutput),
  `commandBlocks()`.
- Tests: `CommandBlockBlockTest` (4 — the config default/property flip;
  the executor rows walk through the recording host; the live-engine
  redstone edge walk through the real CommandService (the lever toggle
  → TRIGGERED → the scheduled run → the success count; the falling
  edge clears); the gate's answers) + `BlockRegistryTest` (780 = 778 +
  the two TRIGGERED states). The vehicle/commandblock/redstone/config
  batches green; the LegacyBlockIds integrity green.

NEXT (in order): (1) the placement arm (the item 137's place path
creating the tile with the sendCommandFeedback default) + the tile sync
(0x35 action 2 + the chunk replay) + the ZVD persistence rows; (2) the
vehicle damage-source surface (the furnace cart's explosive-death drop
skip); (3) the /summon NBT arm.

## Slice 73 — the command-block placement arm + the tile sync + the persistence

- **The placement arm (the ledgered follow-up)**: `BuiltinItems.COMMAND_BLOCK`
  (the item 137 — Item.java line 1299's own ItemBlock registration, /give
  only) resolves through the generic block-item resolver (the identifier
  match) and the placement creates the tile through the manager's new
  `WorldChangeListener` arm — onPlaced lines 86-88's law: the
  sendCommandFeedback gamerule is the track-output default, READ AT THE
  PLACEMENT (the flipped gamerule's next placement carries the false
  default — the boot-time constant would have been wrong).
- **THE TRIGGERED-FLIP LAW (the regression the Slice 72 redstone test
  caught)**: the first cut used the single-type `onBlockRemoved` arm — but
  `setBlock` fires it with the OLD type on every state change, so every
  TRIGGERED flip deleted the tile and its command (the scheduled tick then
  found no executor — the Slice 72 test went red). The correct port surface
  is the PAIRED arm `onBlockReplaced`: the removal fires only when the block
  TRULY leaves (old was a command block, new is not); the TRIGGERED flip
  (was && is) is a no-op exactly like the reference's own block entity
  surviving its property flips. The interface's Slice 14a comment predicted
  this exact need.
- **The tile sync**: the `Listener` publish on the edit + the run (the rows
  changed → the 0x35 action 2 fan through the adapter, the same shape as the
  skull/beacon tiles) + the chunk-send replay (`inChunk` walks the freshly
  sent column's command tiles beside the sign/spawner/beacon/skull replays —
  CommandBlockBlockEntity.createUpdatePacket line 75's own rows: Command/
  SuccessCount/TrackOutput/LastOutput, the LastOutput an empty string when
  none).
- **The persistence (the ZCB envelope)**: `CommandBlockDataStore` — the four
  rows per tile, the atomic temp-file swap, the corrupt-file-preserving load
  (the sign store's own envelope), the boot restore, the shutdown save.
- **The orphan sweep**: the per-tick walk discards a restored row whose
  block left between saves (the restore-then-sweep path — the same safety
  as the sign/skull stores).
- Tests: `CommandBlockBlockTest` grew 4→9 (the placement default walk over
  the flipped gamerule + the TRIGGERED-flip no-recreate; the break sweep +
  the removal fire; the edit/run publish + the inChunk walk; the
  persistence round trip + the corrupt read; the orphan sweep). The
  commandblock batch 9 green; the redstone+item batches 31 green; the
  registry tests green; the protocol suite green (one horse load flake,
  green in isolation — the documented one).

NEXT (in order): (1) the vehicle damage-source surface (the furnace cart's
explosive-death drop skip); (2) the /summon NBT arm; (3) the plugin host's
live-engine acceptance; (4) the next vanilla wave per the ledger.

## Slice 74 — the vehicle damage-source surface + the explosion's vehicle wounds

- **The source surface (the ledgered follow-up)**: `VehicleEntity.DamageSource`
  (MELEE/EXPLOSIVE/FIRE — the reference's isExplosive/isFire consults) rides
  `hurt(amount, source)`; the OLD 2-arg shape delegates to MELEE. THE DEATH
  FACTS FREEZE: the reference's dropItems reads the live velocity inside
  damage(); the engine's drop walk runs on the NEXT tick's break sweep —
  `lastDamageSource` + `deathSpeedSquared` freeze at the fatal hit so a cart
  halted between the hit and the sweep still answers the >= 0.01 gate with
  its death-time speed.
- **The death law (the variant carts' dropItems gates)**: the furnace cart's
  FURNACE block and the TNT cart's TNT block both SKIP an explosive death
  (dropItems' own !isExplosive() gates — an exploded cart drops only the cart
  item); the TNT cart's fire/explosive/fast death DETONATES —
  `detonateAtDeath()` with the three-gate refusal INSIDE the method (the
  reference's dropItems wraps the explode call in exactly this gate), the
  power 4.0 + rand * 1.5 * min(sqrt(frozen speed), 5). The hopper/chest
  carts' drops stay ungated (the reference's own shape).
- **The explosion's vehicle wound walk**: `detonateAtIn` now walks the carts
  after the players and the mobs — the same distance falloff
  (1 - dist/(power*2)) * maxDamage with the EXPLOSIVE source, the knockback
  impulse (velocity += delta * 1.6-scaled), the overworld gate. A blast
  beside carts now kills them (the pre-74 engine: a minecart next to a TNT
  blast took NOTHING).
- **The chain reaction**: one TNT cart's blast kills its neighbor with the
  EXPLOSIVE source — the neighbor's break sweep drops no TNT block and
  detonates on the spot (the live test's stone block beside the cart is the
  observable).
- **THE BLAST SINK FACTORY (a pre-74 gap the slice closed)**: only the
  item-use spawn path wired the TNT cart's blast sink — a RESTORED cart
  burst nothing after a restart (its fuse detonation and its death
  detonation both hit the no-op default). `VehicleManager."
  "setBlastSinkFactory` arms EVERY spawn (the item-use path, the restore,
  the test spawns) with the engine's detonation walk.
- Tests: `VehicleDamageSourceTest` (4 — the source lands + the death-facts
  freeze through the i-frame burn-down; the death detonation's gate + power
  bands including the exactly-4.0 rest power; the live-engine blast killing
  both near carts with the source + both block-skip laws + the chain's
  destroyed stone + the far control surviving; the melee contrast dropping
  the furnace block). The vehicle + explosion batches 46 green.

NEXT (in order): (1) the /summon NBT arm; (2) the plugin host's
live-engine acceptance; (3) the next vanilla wave per the ledger (the
vehicle fire/lava walk, the projectile-hits-vehicle family, the
burning-arrow detonation).

## Slice 75 — the /summon NBT arm + the SNBT parser

- **The SNBT parser** (`server/nbt/Snbt`): the reference's SnbtParser language
  over a recursive-descent reader — the suffix families (1.5d/1.5f/1b/1l/1s),
  the bare int/double, the true/false bytes, the all-int PRIMITIVE_PATTERN
  array split, the quoted strings with escapes, the out-of-range
  fall-to-string, the tolerated trailing commas, and the syntax errors' own
  messages. The Tag model carries the typed readers the summon arm consults.
- **The mob rows** (the reference's Entity NBT fields the engine's model
  carries): customName + customNameVisible (the wire's mob-spawn metadata
  slots 2/3 — omitted when unnamed), noAi (the tickMind pin — the body stands
  exactly still, physics/burning/wounds continue), invulnerable (the hurt
  refusal), and the customName-derived persistence (the reference's
  checkDespawn gate: a named body never despawns).
- **The summon arm**: args[4..] joined and parsed as one compound; a parse
  failure answers the tagError line without spawning; the rows land on any
  kind (CustomName/CustomNameVisible/Health/NoAI/PersistenceRequired/
  Invulnerable — every other row parses but lands nowhere, the engine's
  no-item-NBT envelope).
- **The persistence (the ZMD v3 block)**: the presence byte + the rows after
  the v2 extras; v2/v1 files load unchanged. The MobNbtExtras snapshot
  carries the RAW persistence mark (the leashed/tamed/named derivations
  never silently pin a body whose own rows are not persisted).
- **A TEST BUG THE DIAGNOSTIC CAUGHT**: the restart test's boot() generated a
  random world name per call — the second boot read a fresh world directory
  and restored nothing. The fixed name (both boots read the same world)
  proves the rows survive the restart.
- Tests: `SnbtTest` (6) + `SummonNbtTest` (4 — the rows land, the tagError
  refusal, the restart survival, the named-body persistence). The entity
  batch 321 green; the protocol suite green (1m51s, zero failures).

NEXT (in order): (1) the plugin host's live-engine acceptance (a plugin
through the real EngineServer's plugins() with real event-bus/scope
integration); (2) the next vanilla wave per the ledger (the vehicle
fire/lava walk, the projectile-hits-vehicle family, the burning-arrow
detonation, the mob equipment/attribute NBT rows).

## Slice 76 — the plugin host's live-engine acceptance + the shutdown's disable walk

- **The acceptance proof (the §28 wiring)**: `PluginLiveEngineAcceptanceTest` —
  a plugin through the REAL EngineServer's `plugins()`: the registration
  subscribes to PlayerJoinEvent through the PUBLIC `server.events()` bus and
  OWNS the subscription through the scope (the sweep unsubscribes); the
  quota'd compute (21→42) and blocking-IO tasks run over the engine's REAL
  lanes and land their results; a REAL join (joinRequest + markPlaying) fires
  the handler with the player's name; the disable walk sweeps the owned
  resources in registration order, refuses the post-disable submissions (the
  admissions gate), and the swept subscription never fires again.
- **THE SHUTDOWN'S DISABLE WALK (a real gap the acceptance surfaced)**: the
  engine's shutdown had no plugin arm at all — a plugin left ENABLED at stop
  kept its scope open past the lanes' death. The new 3b arm in
  EngineServer.shutdown: every enabled plugin sealed and swept BEFORE the
  simulation stops (the owned subscriptions leave, the close verbs run, no
  admission races the shutdown); a disable failure is isolated per plugin;
  unload stays explicit (the handle remains queryable after the stop).
- Tests: the acceptance (2) + PluginHostTest (9) green; the
  entity+commandblock batches 330 green (every test boots and shuts down the
  real server — the new walk's no-plugin path proves itself across the whole
  suite); the protocol suite green (1m49s).

NEXT (in order): (1) the next vanilla wave per the ledger (the vehicle
fire/lava walk, the projectile-hits-vehicle family, the burning-arrow
detonation, the mob equipment/attribute NBT rows); (2) the plugin
discovery/classloading wave when the native API surface settles; (3) the
periodic full-suite verification (the milestone convention).

## Slice 77 — the vehicle damage family's world arms

- **The projectile's entity scan over the minecarts**: `HitResolver.vehicleAt`
  (the box containment over the cart hull) + the hit walk's vehicle arm —
  an arrow flying through a cart's hull now LANDS there (the pre-77 engine:
  arrows passed straight through carts). The damage rides the speed-scaled
  roll through `CombatSink.vehicleHit` (the engine: vehicleManager.hurt with
  MELEE + the travel-direction knockback impulse).
- **The burning-arrow detonation** (TntMinecartEntity.takeDamage lines
  63-73): a BURNING arrow hit detonates the TNT cart ON THE SPOT — the arm
  runs before the damage walk, the ARROW's own speed-squared feeding the
  power roll (the explodeWith refactor shares the fuse arm's roll). The
  cart annihilates with no drops.
- **The lava walk** (Entity.tick's isInLava arm): the WorldQuery gains the
  inLava consult (the FluidBlocks LAVA kind at the cart's mid-band); the
  tick lands the flat 4.0 FIRE-source wound per i-frame window. A
  lava-killed TNT cart reads the FIRE source through Slice 74's death law:
  the fire death DROPS the TNT block AND detonates (the reference's own
  gates — only the EXPLOSIVE death withholds the drop) — the live test's
  chain stone is the detonation's observable.
- **Two test-expectation corrections the failures taught**: the arrow's
  speed-3 damage roll is 6.0 (the engine's established ceil(speed*2)
  formula); the fire-death TNT cart drops its block (my first draft
  asserted the explosive skip on the fire arm — the reference's gate is
  !isExplosive() ALONE). The dry control also had to move beyond the lava's
  spread radius (the slow batch run let the fluid reach it).
- Tests: `VehicleWorldDamageTest` (3). The vehicle+projectile batches 75
  green; the protocol suite green (1m49s).

NEXT (in order): (1) the next vanilla wave per the ledger (the vehicle
on-fire flags byte + the fire-BLOCK walk, the mob equipment/attribute NBT
rows); (2) the periodic full-suite verification (the milestone
convention); (3) the plugin discovery/classloading wave.

## The full-suite verification (the session's closing pass) + the light fix

- **The light fix the chunk run caught**: `BlockDataApiTest.
  theBreakTimingHelperMatchesTheHistoricalTable` walked every registered
  block's light entry and found the command-block family (Slice 72's
  blocks) carried NO BlockLightTable row — the affected batches of Slice
  72/73 never read the light table, so the gap survived three slices. The
  reference's own registration (Block.java line 1299: no setLight call) —
  the default opaque block: emission 0, filter 15. Both
  command_block/command_block_triggered now carry the rows.
- **The full-suite pass (the milestone convention, chunked)**: the box
  crawled under the marathon session — the full suite cannot finish inside
  one timeout window, so the pass ran per family with per-class salvage
  (gradle wipes the results directory on every invocation — the salvage
  script copies each class's XML aside before the next run): the entity
  family 321 green (the vehicle + projectile batches inside), the world
  family 94 green, the 72 top-level server classes 285 green, the
  subpackage families (explosion/tnt/commandblock/plugin/nbt/redstone/
  item/skull) 64 green, the api suite 19 green, the protocol suite green
  (the final run 1m49s, zero failures) — ~764 core + 19 api + 71 protocol.
- **Two load flakes, both green in isolation** (the documented class):
  SpawnEggTest.theUnknownIdNeverSpawnsOrPays and LifecycleEventAcceptance
  Test.anUncancelledDeathScattersEverything — full-engine boots with
  timing-sensitive assertions on a saturated CPU. Re-verified green.

NEXT (in order): (1) the next vanilla wave per the ledger (the vehicle
on-fire flags byte + the fire-BLOCK walk, the mob equipment/attribute NBT
rows); (2) the plugin discovery/classloading wave; (3) the sections 27/28
audit's remaining rows.

## Slice 78a — the vehicle on-fire family (the burn clock + the fire-source contact walk + the flags byte + the Fire persistence row)

- **The burn clock (Entity.java lines 320-335, onUpdate's fire arm)**:
  `VehicleEntity.onFireTimer` — the reference's signed counter (negative =
  the pre-ignition grace band, positive = ticks of burning left), with
  `FIRE_SAFE_TICKS = 1` (the reference's fireResistance). `tickOnFire()`
  ports the onUpdate arm: the 1.0 ON_FIRE wound at `% 20 == 0` (one per
  full second of burning) + the tick-down regardless of the i-frame's
  verdict (the reference's attempted-damage shape). `setOnFireFor`'s max
  arm (lines 365-371 — a longer burn never shortens; no protection consult,
  the vehicle carries no armor).
- **The fire-source contact walk (lines 635-651, move()'s fire arm)**: the
  manager lands it AFTER the motion (move()'s own order) at the body's
  mid-band — `WorldQuery.inFireSource` (containsFireSource's family: the
  FIRE block + the lava cells, World.java lines 1475-1494): the 1.0
  IN_FIRE wound per i-frame window + `contactFire(wet)`'s grace walk (the
  counter ++ toward zero; the zero crossing arms the 8-second burn), the
  out-of-fire `-safeOnFireTime` re-arm, and the wet extinguish (the fizz
  listener + `extinguish()` — the reference's own order: the sound plays
  first, then the grace re-arms).
- **THE LAVA WALK'S OWN GAP CLOSED (Slice 77's follow-up)**:
  takeLavaDamage's second arm (lines 358-363) — `setOnFireFor(15)`. A
  lava-soaked cart now burns 15 seconds beside its 4.0-per-window wound
  (the pre-78 engine wounded without igniting).
- **The flags byte (line 347's setFlag(0, onFireTimer > 0))**: the wire's
  slot-0 0x01 bit — `sendVehicleStateMetadata` now composes the flags
  byte (every kind) + the furnace lit byte + the command strings into ONE
  Entity Metadata frame; the spawn-time carry (a restored burning cart)
  + the flip re-send through the existing onVehicleStateChanged channel.
  The old sendCommandCartMetadata folded in (the same packet shape).
- **The persistence (the ZVD v3 block)**: the `Fire` short (lines
  1115/1176) — the raw signed clock every kind carries; v2/v1 files load
  with the fresh-body -1 grace. The VehicleSnapshot record grew the fire
  row (the old telescoping constructors keep their defaults).
- **THE FLOATING-FIRE TEST LESSON**: the first wire draft placed the fire
  block at the player's ground level and never ignited — the engine's
  fire burns itself out when floating (no support below, no flammable
  neighbor: tickFire's own law), and the terrain two blocks east is not
  guaranteed at the anchor's level. The stone base under the flame is the
  test's own fix (the diagnostic run caught it: the cart fell past the
  flame cell with the timer still at -1).
- Tests: `VehicleFireTest` (8 — the burn clock's cadence + bottoming, the
  max arm, the fire-block ignition with the flags-flip publish + the
  steady standing-in-fire state, the dry control, the wet no-grace +
  extinguish with the fizz + the flip, the lava's 15-second arm, the
  store round trip, the v2 back-compat) +
  `VehicleIntegrationTest.theBurningCartPublishesItsFlagsByteOverTheWire`
  (the real-wire 0x01 flags byte — TestClient18's readFlagsMetadataOf).
  The vehicle batch green; the full protocol suite green.

NEXT (in order): (1) Slice 78b — the mob equipment/attribute NBT rows
(the ZMD v4 block); (2) the plugin discovery/classloading wave; (3) the
sections 27/28 audit's remaining rows.

## Slice 78b — the /summon Equipment + Attributes rows (the ZMD v4 block)

- **The Equipment row (MobEntity.readCustomNbt lines 301-307)**: the
  five-slot list — the engine's model carries the held slot alone
  (`setHeldEquipment`); the armor slots parse but land nowhere (the
  no-armor envelope). The item parse (`parseItemNbt`) reads the
  engine's identifier form ("minecraft:iron_sword" or the bare name —
  the /give surface), the Count byte (default 1, clamped at the stack
  size), the Damage short (clamped at MAX_DAMAGE).
- **The Attributes row (LivingEntity.readCustomNbt lines 419-421)**:
  the {Name, Base} list over the attribute registry — the engine honors
  `generic.movementSpeed` and `generic.attackDamage`, the two attributes
  its consults read; the other names parse but land nowhere (the
  per-type envelope). A non-positive Base is refused (the attribute
  keeps its base).
- **THE WALK-SPEED CONSULT REFACTOR**: every movement arm read
  `type.walkSpeed` directly (eight sites — the launch, the two
  navigation consults, the walk/panic/chase/goal arms); all eight now
  read the new `walkSpeed()` (the override over the kind's base — the
  reference's getEntityAttribute consult shape). `attackDamage()` grows
  the override consult FIRST (the override replaces the whole variant
  ladder — the reference's attribute base semantics).
- **The equipment's wire re-publish**: the summon row and the restore's
  held stack land AFTER the spawn packet went out — the new
  `MobManager.Listener.onMobEquipmentChanged` (the EngineServer bridge
  arm + the protocol adapter's) re-sends the Entity Equipment 0x04
  (`V18Connection.sendMobHeldEquipment`, chunk-gated). The restore
  fires it too.
- **The persistence (the ZMD v4 block)**: the MobNbtExtras record's new
  rows (heldEquipment + the two NaN-marked overrides); the store's
  high flag bits 0x20/0x40/0x80 (the stack row — identifier + count +
  damage — + the two doubles); v3/v2/v1 files load unchanged (the low
  flags' own shape).
- Tests: `SummonEquipmentTest` (4 — the Equipment row landing the held
  stack + the publication counter + the armor envelope, the Attributes
  rows driving both consults + the unknown-name envelope, the
  non-positive base refused, the restart survival through the v4
  block). The SummonNbtTest + the nbt batch + the FULL entity family
  (the walkSpeed refactor's own proof) + the full protocol suite green.

NEXT (in order): (1) the plugin discovery/classloading wave (§28.5's
DISCOVERED → LOADED states); (2) the sections 27/28 audit's remaining
rows; (3) the next vanilla wave per the ledger.

## Slice 79 — the plugin discovery/classloading wave (§28.5's DISCOVERED → LOADED stages)

- **The discovery (`server/plugin/PluginDiscovery`)**: every `*.jar` in
  `<dataDir>/plugins/` carries a `torch-plugin.properties` at its root
  (id/version/authors/entry) — the engine's own descriptor shape
  (zero-dependency, the stdlib Properties parse; Bukkit's plugin.yml
  shape rides the compatibility adapter, not the native surface). One
  jar's walk: FOUND (the file on disk — the DISCOVERED stage) → the
  descriptor read + validated → the classes load through the jar's OWN
  URLClassLoader → the entry class (a public no-arg PluginBody)
  instantiated (the LOADED stage) → the host's register drives the
  enable walk.
- **The per-jar isolation (§28.5's failure transitions)**: a broken
  jar, a missing/broken descriptor, a duplicate id, or a throwing
  constructor skips THAT jar with a loud log — the boot never dies. The
  failed walk's own loader closes (no leak on the skip path).
- **THE CLASSLOADER'S OWN LAW (§28.5)**: the loader's close attaches to
  UNLOAD — the last lifecycle state, after the disable sweep — through
  the new engine-side `PluginManager.registerWithLoader(descriptor,
  body, closeVerb)` (the programmatic `register` delegates with null).
  The unload walk runs the verbs (each isolated) before the state flip.
  Enable/disable cycles keep the classes loaded; the reload reuses the
  body; only the unload releases them. The best-effort law (leaked
  references outlive the close) is documented, not claimed away.
- **The boot wiring**: the scan runs on the boot thread after the lanes
  are up (the compute/IO views exist before any enable callback), a
  deterministic jar order (the name sort), and the SEVERE-but-continue
  envelope around the whole wave.
- **The test's own compile harness**: `PluginDiscoveryTest` builds a
  REAL jar at test time — the JDK's own JavaCompiler compiles the demo
  DemoPlugin source (the classpath built from PluginBody's code source
  location), the JarOutputStream writes the descriptor + the class
  files, and the enable's observable is a marker file the loaded class
  itself writes (loader-transparent).
- Tests: `PluginDiscoveryTest` (6 — the scan's load + the descriptor
  rows, the broken/empty jar skip, the duplicate skip + the unload verb
  at UNLOAD + the freed id, the live-engine boot scan with the loaded
  class's own enable reading its descriptor through the handle, the
  garbage-jar boot survival). The plugin family (PluginHostTest 9 + the
  live-engine acceptance 2) + the full protocol suite green.

NEXT (in order): (1) the sections 27/28 audit's remaining rows; (2)
the next vanilla wave per the ledger; (3) the periodic full-suite
verification (the milestone convention).

## Slice 79b — the block registry's public row (the §27/28 audit's first closed gap)

- **The `byKey(String)` short form** (the public `BlockRegistry`): the
  canonical form ("minecraft:stone") or the short form ("stone" — the
  minecraft namespace), the item registry's own VERIFIED shape.
- **The acceptance test** (`RegistryApiAcceptanceTest.
  theBlockRegistryCarriesTheFrozenEnumeration`): the frozen
  enumeration's floor over the PUBLIC surface (the engine's own
  register chain — 1079 at this slice, the BlockRegistryTest's 780 is
  its own unit registry's subset), the stone lookups through both
  forms + the miss arms, the dataOf behavior read, and the off-lane
  (foreign thread) enumeration + lookup.
- The ledger row (AUDITED → IMPLEMENTED): the named omissions ("lookup
  by id, the behavior tables") closed — the lookup form family
  completed with byKey, the behavior tables' read proven through the
  public dataOf.

NEXT (in order): (1) the remaining sections 27/28 audit rows (the
cross-owner request surface — the AUDITED row with the router internal
today); (2) the next vanilla wave per the ledger; (3) the periodic
full-suite verification (the milestone convention).

## Slice 80 — the plugin-facing cross-owner request surface (§28.2's default nonblocking API) + the End dimension's missing region drain

- **The public interface (`api concurrent.CrossOwner`)**: the
  `CrossOwnerRouter`'s plugin-facing face over **`Server.crossOwner()`**
  — `submit(world, position, operation, action)` (the one-way command,
  §10.1's default), `submit(..., Supplier, BiConsumer)` (the
  result-carrying form), and the `deadlineTick` overload (the last tick
  the request may still apply). The `Request` handle: `state()`
  (QUEUED/APPLIED/FAILED/CANCELLED/EXPIRED/REFUSED), `cancel()` while
  queued, `resultNow()` (the non-blocking peek), `failure()`; the §28.9
  counters (submitted/refused/applied/failed/cancelled/expired).
- **The contract (§28.2's own rules, now enforced by an adapter)**:
  submission never blocks from any thread; the action AND the completion
  run in the TARGET owner's validated context at a tick boundary (the
  explicit completion context — never an arbitrary owner); admission is
  exactly-once per request (a fresh operation id each — a retry is a new
  request by contract); the per-target pending bound refuses LOUDLY (the
  handle reports REFUSED); a throwing action completes FAILED with the
  throwable and never kills the drain; cancellation skips without
  running; the deadline expires past its tick without running; at
  shutdown the queued requests flip CANCELLED with no completion
  (§28.5's invalidated-not-delivered law).
- **The engine view (`server/concurrent/CrossOwnerView`)**: the routing
  resolves the target position's region domain (the partitioned world's
  own lane — the same domain the tick-head boundary walk drains) or the
  unpartitioned world's own domain; the guard wraps every action (the
  cancel gate, the deadline gate against the target world's own tick
  clock at drain time, the failure isolation — the guard NEVER throws,
  so the router's counters stay its own "drained-without-throwing"
  telemetry and the view's counters carry the semantic outcomes); the
  live-handle registry keeps the shutdown walk bounded.
- **THE END'S MISSING REGION DRAIN (the engine fix)**: the tick head
  drained the overworld's and the nether's region intents but never the
  End's — an intent toward the End's region domain sat queued forever
  (the Slice 61 dimension landed after the drain wiring; nobody added
  its line). `endWorld.drainRegionIntents(crossOwnerRouter)` now rides
  the tick head beside its siblings.
- **The End's public presence (§27's surface fix)**: `Server.worlds()`
  grew the End (the collection stopped hiding the third dimension —
  EventApiAcceptanceTest's size assertion updated with it) and
  `EngineServer.endWorld()` joined `world()`/`netherWorld()`.
- Tests: `CrossOwnerApiAcceptanceTest` (9 — the target-context apply
  with the completion contract + the mutation landing, the failure
  isolation + the survivor, the cancel gate, the expiry gate + the
  on-own-tick apply, the loud backpressure with the REFUSED handle, the
  unpartitioned routing through the world's own domain, the live-engine
  foreign-thread apply through the public accessor, the End's drain
  through the live engine). The concurrent family + RegionBoundaryTest +
  Event/World/BlockEntity API acceptance green.

NEXT (in order): (1) the remaining §27/28 audit rows (the pistons/
fluids observe hooks — the interaction-events wave); (2) the next
vanilla wave per the ledger; (3) the periodic full-suite verification.

## Slice 81 — the piston + fluid observe hooks (§27.7's interaction-events wave) + THE FLUID FLOW FIX

- **THE ENGINE BUG THE WAVE FOUND (the slice's own headline)**:
  `FluidSystem.handleTarget`'s air gate compared an `Identifier`
  against a `BlockType` — `existing.equals(world.airType())` — ALWAYS
  false (the types never equal), so every AIR cell read as solid
  ground and **water/lava NEVER flowed into air on the live engine**
  (the source sat still; no test ever asserted a live spread, so the
  §472 family shipped broken). The one-line fix:
  `existing.equals(world.airType().identifier())`. Found by the
  acceptance harness itself — the flow events never fired, the debug
  walk showed the gate returning "blocked" for air.
- **The public `PistonMoveEvent`** (§27.7's pre-commit gate): fires
  inside the movement's boundary scope after the block-event re-check
  gates, BEFORE any world write — cancelling prevents the whole
  movement (the piston keeps its state, nothing moves or breaks, no
  sound; the vanilla arming's base-state flip is documented — a
  cancelled retract leaves the HEAD in place, the observable). The
  payload carries the resolver's own prospective lists (a fresh pure
  resolution — the push chain + the break list on extend, the pull
  chain on a sticky retract) + the facing (the public `Direction`),
  extending/sticky flags, and the world name.
- **The public `FluidFlowEvent`** (§27.7's pre-commit gate): fires per
  prospective landing cell right before the flow commits — cancelling
  behaves as blocked ground (the fluid never enters; the walk's own
  rules continue around it — a cancelled down-pour lets the source
  spread sideways, exactly as solid ground below would). The payload:
  the from/to cells, the fluid identifier, and the landing level (8
  for the falling column, the decayed level sideways). The contact
  reactions stay the post-commit BlockChangedEvent's own domain.
- **The wiring**: `PistonSystem.setEventBus` (the gate at both arms of
  `doBlockEventBody`) + `FluidSystem.setEventBus` (the gate in
  `handleTarget`, which grew the `from` cell parameter); the
  EngineServer boot wires both beside the other §27.7 gates;
  `FluidSystem.World` grew `name()` (the FluidWorld adapter answers
  the overworld's own name).
- Tests: `PistonFluidEventAcceptanceTest` (5 — the cancelled extend
  leaving the piston retracted + the stone unmoved + the after-cycle,
  the extend observation carrying the resolver lists + the facing +
  the flags, the cancelled retract keeping the head + the pulled
  block, the cancelled down-pour behaving as blocked ground with the
  sideways spread, the flow observation carrying the fluid + its
  level 8 down-pour). The piston family + the precipitation suite
  green (the fluid fix's own neighbors).

NEXT (in order): (1) the full-suite verification pass over the fluid
fix (the §472 family's live flows now actually run — the wider world
family must re-verify); (2) the next vanilla wave per the ledger; (3)
the remaining §27 audit omissions.

## The post-fluid-fix full-suite verification pass (Slice 81's closing gate)

- **The scope**: the air-gate fix changed live behavior (water/lava now
  actually flow) — every family re-verified. The environment's process
  reaper kills long background runs, so the suite ran as short
  foreground batches: the 39 subpackage families (weather, block,
  world, piston, entity, concurrent, redstone, tnt, furnace, hopper,
  chest, container, crafting, fishing, explosion, nbt, plugin, spawn,
  player, noteblock, daylight, endcrystal, skull, ops, brewing,
  spawner, dispenser, stat, event, armorstand, experience, potion,
  jukebox, leash, decoration, item, enchantment, commandblock) + the
  top-level classes in A-E/F-M/N-S/T-Z batches + the api module + the
  FULL protocol suite.
- **The result**: ALL GREEN — zero failures across every batch. The
  fluid fix broke nothing; the two new gates observe beside their
  systems silently when no handler subscribes.

NEXT (in order): (1) the next vanilla wave per the ledger; (2) the
remaining §27 audit omissions (the furnace burn/cook observe hooks,
the hopper transfer hooks — the interaction-events wave's named rows);
(3) the periodic release cadence.

## Slice 82 — the furnace + hopper observe hooks (§27.7's interaction-events wave, the ledger's named rows)

- **The public `FurnaceBurnEvent`** (the ignite gate): fires at the
  ignite decision — cold furnace, smeltable input waiting, one fuel
  unit about to be consumed. Cancel keeps the fuel AND the furnace
  cold (the next tick re-attempts; nothing is lost). The payload: the
  fuel stack + the burn value in ticks.
- **The public `FurnaceSmeltEvent`** (the cook gate): fires at the
  200-tick term — one input about to be consumed, the result about to
  land. Cancel keeps both; the cycle's progress is already spent (the
  reference's own reset happens before the commit — a cancelled smelt
  restarts the cook cycle, exactly like a cycle whose result stopped
  fitting). The payload: the input stack + the would-be result stack.
- **The public `HopperTransferEvent`** (the transfer gate): fires per
  prospective item move inside the transfer's boundary scope —
  PUSH (one item toward the faced container), PULL (one from the
  container above), SUCK (the item entities in the collection box) —
  with the counterpart position + the one-item payload. Cancel keeps
  THIS item; the walk's own rules continue (the next slot's item gets
  its own event; a fully-cancelled cycle arms no cooldown).
- **The wiring**: `FurnaceBlockEntity` grew the `Gates` consult points
  (the ignite + the smelt completion, both before any slot mutation)
  wired by the manager's adapter (the world name captured at the tick
  entry — the gates run on the same tick thread); `HopperSystem` fires
  its own gate (it holds the world) at the three arms, each BEFORE the
  slot/entity mutation.
- **THE KNOWN GAP THE SUCK TEST FOUND (ledgered, not fixed here)**:
  the item-entity physics falls THROUGH the hopper's funnel shape —
  items collide with full cubes only, so an item spawned above a
  hopper passes through the collection band (the in-band suck still
  lands its first cadence; vanilla rests the item in the funnel at
  y+0.625). A physics-shape wave of its own (the §3 collision family).
- Tests: `FurnaceHopperEventAcceptanceTest` (5 — the cancelled ignite
  keeping fuel + input + cold with the after-unsubscribe arm, the
  smelt observation carrying input/result + the cancelled twin holding
  everything, the push observation with the item/counterpart/world +
  the cancel keeping the stack, the pull observation, the suck cancel
  + the fresh-item uncancelled suck). The furnace/hopper/chest/
  container families + LitFurnace + BrewingStand + the piston/fluid
  gates + RegionBoundaryTest green.

NEXT (in order): (1) the item-entity shape-collision wave (the fall-
through gap the suck test found — items must rest in the hopper's
funnel, on slabs, on stairs — the §3 collision family's own slice);
(2) the next vanilla wave per the ledger; (3) the remaining §27
omissions.

## Slice 83 — the item box-footprint physics (the vanilla-shape slice: the fall-through gap the suck gate found)

- **THE FIX**: the item entity's landing test was a CENTER-POINT test —
  a dropped item's historical scatter drifts its center past the cell
  edge while it falls, the point missed the hopper's funnel entirely,
  and the item fell through to the ground (vanilla's EntityItem
  collides as a 0.25-wide BOX; the box's overlapping edge catches the
  funnel's rim). `ItemEntity.Ground` grew the box-footprint forms —
  `isSolidAtBox` + `supportYAtBox` (the bottom face's four corners +
  the center against the collision shapes, the support the HIGHEST
  touched shape top) — and the landing + ceiling arms read them; the
  engine's `ShapeGround` implements the five-point sampling.
- **THE VANILLA LAW THE DEBUG WALKED THROUGH**: an unpowered "disabled"
  hopper RE-ENABLES on the next neighbor change (the reference's own
  updateEnabled — the disabled pair exists only while powered); the
  rest test holds the disabled state with a lever ON above the hopper
  (the honest way to keep a hopper from sucking).
- **THE SUCK'S OWN HOME**: with the box footprint, a dropped item comes
  to rest ON the funnel's 5/8 surface INSIDE the collection band
  (y+0.625+0.125 — the vanilla rest), exactly where the suck finds it:
  the sibling test proves the collection (the whole stack lands on the
  first cadence).
- Tests: `ItemBoxPhysicsAcceptanceTest` (2 — the deterministic
  center-drop rest on the funnel's surface inside the band + the
  one-second hold, the resting stack's collection into the hopper).
  The item-entity family, the hopper family, the furnace/hopper event
  gates, the mining/drop paths, the piston + weather families green.

NEXT (in order): (1) the next vanilla wave per the ledger; (2) the
horizontal item collision (the drift passes through walls — the box
footprint's own follow-up); (3) the remaining §27 omissions.

## Slice 83b — the item horizontal clip (the vanilla-shape slice's follow-up)

- **THE FIX**: the item's drift passed through walls — no horizontal
  collision at all. The box's LEADING FACE now tests against the
  collision shapes per axis (three z-samples x three y-samples on the
  X face, mirrored for Z; the bottom sample sits a hair above the
  resting surface so the ground an item slides on never blocks its own
  slide): a blocked axis zeroes its velocity (the vanilla moveEntity
  response — no horizontal bounce) while the free axis keeps sliding.
- Tests: `ItemBoxPhysicsAcceptanceTest` grew the wall pair — the thrown
  item stops against the wall's face (the CENTER never passes
  wall - 0.125; the pre-clip engine passed straight through) + the
  diagonal throw's slide (the X axis clips, the Z axis keeps moving).
  The full regression sweep green: the entity/item/hopper/furnace
  families, the mining/drop paths, the C-E/F-M/N-S/T-Z top-level
  batches, the world/block/spawn/player/crafting/concurrent/redstone/
  tnt families, the a-z subpackages, and the FULL protocol suite.

NEXT (in order): (1) the next vanilla wave per the ledger; (2) the
remaining §27 omissions (the window-family re-sync rows); (3) the
periodic release cadence.

## Slice 84 — the tab-list family (Player Info 0x38: the join/quit/gamemode/ping walks)

- **THE GAP**: the engine never sent Player Info — the client's tab list was
  EMPTY (no players, no ping column). The full vanilla family is now ported
  1:1: `PlayerInfoS2CPacket`'s five actions over the wire, the four
  `PlayerManager`/`ServerPlayerInteractionManager` send sites, and the
  keep-alive ping smoothing.
- **THE ENGINE SEAM**: `ClientLink` grew the tab-list family (tabListAdd /
  tabListRemove / tabListUpdateGamemode / tabListUpdatePing + the
  TabListEntry record — no properties, no display name: the offline
  profile's own shape, getPlayerListName answers null 1:1);
  `EngineBridge.joinTabList` is the adapter's MID-BURST hook (between the
  pre-position packets and the teleport — exactly where vanilla's
  initializeConnectionToPlayer places the add() walk); `PlayerSession` grew
  `ping`/`updatePing` (the reference's `(ping * 3 + roundTrip) / 4`
  smoothing, the keep-alive handler reporting each round trip).
- **THE WALKS** (`server/player/TabListService`): the JOIN announce
  reproduces the reference's add() wire shape EXACTLY — every other player
  learns the joiner once, the joiner's own wire carries their entry TWICE
  (the broadcast's self copy + the per-player loop's final iteration — the
  reference's own double-add quirk, ported verbatim); the QUIT broadcast
  (remove line 298) rides the tick queue AFTER the yellow line's submits
  (the tick queue's FIFO preserves the reference's line-then-REMOVE wire
  order); the GAMEMODE broadcast (setGameMode line 44 — no self-skip, the
  subject hears it too); the PING refresh (tick lines 504-508 — the
  `++counter > 600` off-by-one fires every 601 ticks, ONE batched packet
  carrying every online player to every wire).
- **THE WIRE** (`V18Connection`): the four writers per the reference's
  write() body (the uuid = two big-endian longs — ByteBufOps.writeUuid);
  Protocol18 grew S2C_PLAYER_INFO = 0x38 + the action constants; the
  conformance table's row added (the test that guards against the
  Unload-Chunk class of id bugs caught the new constant immediately).
- Tests: `TabListServiceTest` (5 — the join wire shape with the double
  self-add + the others-once, the quit REMOVE, the gamemode broadcast incl.
  the subject, the 601-tick cadence as one batched packet, the ping
  smoothing formula) + `TabListIntegrationTest` (3 over the real wire: the
  joiner on every tab list + the own-entry-twice through the
  join-collecting reader, the /gamemode UPDATE on both wires, the
  disconnect REMOVE). The full protocol suite (94) + the core player/event
  families green; one chest-family flake (the random-tick grass block
  change racing readBlockChange) green in isolation — the documented flake
  class.
- Ledgered: the joiner's ADDs flush after the chunk pre-burst (the
  engine's pre-existing chunk-burst-first join shape vs vanilla's
  abilities-then-ADDs); the per-player loop's packet order follows the
  registry's concurrent map (the reference's join-order ArrayList is not
  client-observable — the 1.8 client hashes its tab entries); the yellow
  line + ADD ordering on other wires rides the async chat submit.

NEXT (in order): (1) the scoreboard system (the reference's biggest
missing subsystem — Scoreboard/Objective/Criteria/Score/Team + the 0x3B/
0x3C/0x3D/0x3E wire family + the /scoreboard command + the join sync
walk); (2) the world border (0x44 + the damage/warning/lerp mechanics +
/worldborder); (3) the titles family (0x45 + /title) and the player-list
header/footer (0x47); (4) the missing-commands batch (/effect /particle
/playsound /enchant /spawnpoint /setworldspawn /achievement /testfor
/setblock /entitydata /replaceitem /stats /trigger /spreadplayers /fill
/clone); (5) the §27 window-family re-sync rows.

## Slice 85 — the scoreboard engine (the model + the wire family + the join sync + the drivers)

- **THE GAP**: the reference's biggest missing subsystem — Scoreboard/
  ServerScoreboard/Team + the four wire packets + the gameplay drivers —
  is now ported 1:1 (the /scoreboard command and the persistence ride the
  follow-up slice 85b).
- **THE MODEL** (`server/scoreboard/`): `ScoreboardCriterion` (the 38-entry
  named table: the 6 named criteria + 16 teamkill. + 16 killedByTeam.
  colors; health is read-only + HEARTS render), `ScoreboardObjective` (the
  display name defaults to the name; both setters fire the update
  broadcast), `ScoreboardScore` (the forceUpdate first publish at 0, the
  read-only gate on increase/increment — set() bypasses it exactly like
  the reference's health driver, the COMPARATOR: value ASCENDING + ties by
  REVERSE name — the packet emission order, NOT the client's sidebar
  sort), `ScoreboardTeam` (the friendly-fire pair, the visibility pair,
  prefix/suffix, the color id, the packed flags byte; every setter fires
  action-2 updates — setColor does NOT, the reference's own quiet setter),
  `EngineScoreboard` (the merged Scoreboard+ServerScoreboard: displayed-
  objectives tracking, the start/stop display walks, the slot swap walk
  with the reference's count-after-null branch — a displaced objective
  that still shows elsewhere re-points the slot, one that shows nowhere
  stops displaying; the 19 display slots with sidebar.team.<color> names).
- **THE WIRE**: ClientLink's four verbs (scoreboardObjective/
  scoreboardScore/scoreboardDisplay/scoreboardTeam + the two payload
  records) + V18Connection's writers (0x3B name/byte-action/[display+
  render], 0x3C owner/varint-mode/objective/[value], 0x3D byte-slot/name,
  0x3E the full team payload + the varint-counted members) + the
  conformance table's four rows + `EngineBridge.joinScoreboard` (the
  adapter's second mid-burst hook — the sync rides BEFORE the teleport
  exactly like the reference's updateScoreboard at join step 8).
- **THE DRIVERS**: the health criterion (the body tick's health-move mark —
  ceil(health), the absorption term ledgered: the engine tracks the
  effect but not the hearts), the death count (dieOnTick's post-drops
  walk), the kill walk (takeKillScore 1:1: totalKillCount + playerKillCount
  + the team family — the victim's killedByTeam.<killer color> + the
  killer's teamkill.<victim color>) wired at the melee, projectile, and
  both thorns kill sites.
- Tests: `EngineScoreboardTest` (7 — the undisplayed-objective silence +
  the start walk, the forceUpdate zero publish + the read-only gate + the
  ceil law, the slot swap + stop display + the remove walk's no-cleared-
  pointer shape, the slot-name mapping + the 38-criteria table, the team
  model + join/leave/re-join, the join sync dedup, the comparator law) +
  `ScoreboardIntegrationTest` (3 over the real wire: the display walk +
  score updates, the health criterion's hearts + the ceil re-publish on a
  wound, the team create/update/join + the JOINER's mid-session sync
  through the join collector). The full protocol suite + the core pvp/
  lifecycle/player families green.

## Slice 85b — the /scoreboard command + the persistence

- **THE COMMAND** (`server/scoreboard/ScoreboardCommand`, ScoreboardCommand
  1:1, op-2): the objectives family (list / add with the display name /
  remove / setdisplay over all 19 slot names), the players family (list /
  set / add / remove / reset both shapes / enable with the trigger gate /
  test with the * bounds / operation with all nine operators incl. the
  >< swap), the teams family (list both shapes / add / remove / empty /
  join with the sender-self arm / leave / option), and the reference's own
  `*` wildcard walk (re-run per score owner, the multi-wildcard refusal).
  The option color arm does the reference's own decoration walk (the
  prefix becomes the color's chat code, the suffix the reset). THE BUGS
  the tests caught: the option gate + switch compared the lowercased name
  against mixed-case literals (the visibility options silently fell
  through to the usage gate — the reference's equalsIgnoreCase flattened).
- **THE PERSISTENCE** (`ScoreboardDataStore`, world/scoreboard.json — the
  reference's scoreboard.dat role): objectives (name/criterion/display/
  render), teams (every field + the members), the display slots, and every
  score cell; saved on the shutdown + /save walk, restored at boot before
  the first join can sync it. The dependency-free JSON walk (the OpStore
  convention); a corrupt file degrades to a fresh board.
- **THE QUIRK PRESERVED**: the 1.8 command splitter has NO quote handling
  (Brigadier is 1.13+) — a quoted display name keeps its quote marks,
  exactly like the reference's parseText LiteralText walk (tested).
- Tests: `ScoreboardCommandTest` (4 — the objectives family end-to-end
  incl. the quote quirk + the unknown-criterion gate, the players family
  with the read-only gate + the operation set incl. the swap + the trigger
  enable + the test bounds, the teams family with the color decoration +
  the visibility options + join/leave/empty, the persistence round trip
  through shutdown + reload incl. the display slot + the team color +
  the member index). The command/player families green.

## Slice 86 — the world border (the model + the 0x44 wire family + the damage walk + the join init + the command)

- **THE MODEL** (`server/border/EngineWorldBorder`): WorldBorder 1:1 — the
  center, the size (default 6.0E7), the wall-clock lerp toward a target size
  (the snap at completion), the 29999984 clamp, the containment + distance
  reads, and the damage family (safeZone 5.0, damagePerBlock 0.2) + the
  warning pair (15s / 5 blocks). Every setter broadcasts its own wire verb
  (the reference's WorldBorderListener/PlayerManager pair collapsed into
  the model); the DAMAGE family sends NOTHING (the reference's own empty
  listener arms — the packet enum carries no type for them).
- **THE WIRE**: ClientLink's six border verbs (size/lerp/center/warningTime/
  warningBlocks/init) + V18Connection's writers (0x44 with the varint
  action + the per-action fields incl. the varlong lerp time) + the
  conformance row + `EngineBridge.joinWorldBorder` (the full INITIALIZE
  after the teleport, the reference's sendWorldInfo position).
- **THE DAMAGE WALK**: LivingEntity.baseTick lines 196-202 — a player whose
  0.6-wide box left the border takes max(1, floor(-depth * rate)) per tick
  through the new DamageKind/DamageCause IN_WALL (bypasses armor like the
  reference's setBypassesArmor, exhaustion 0.3, the "suffocated in a wall"
  death line).
- **THE COMMAND** (/worldborder, WorldBorderCommand 1:1, op-2): set (the
  immediate + the lerped forms), add (relative to the current lerp), center
  (with the ~-relative parse), damage buffer/amount, warning time/distance,
  get — the bounded parses, the %-format feedback.
- Tests: `WorldBorderTest` (4 — the geometry + the defaults + the lerp walk
  with the midway read and the snap, the setter broadcasts through a live
  session incl. the join INITIALIZE's full default state, the damage walk
  with the inside-quiet/outside-chew pair, the command arms). The full
  protocol suite + the gamerule/player/pvp families green.
- THE DEBUG WALK: the daemon served a stale test-results XML while
  compileTestJava failed (the missing throws) — the kill-daemon + clean
  pattern exposed the real error.

NEXT (in order): (1) the titles family (0x45 + /title) + the player-list
header/footer (0x47); (2) the missing-commands batch; (3) the §27
window-family re-sync rows; (4) the border's persistence (the reference's
level.dat row — worldborder.json in the engine's store convention); (5)
the periodic release cadence.

## Slice 87 — the titles wave (the text model + the 0x45/0x47 wire + the target selector + /title + /tellraw + the header/footer API)

- **THE TEXT MODEL** (`server/text/`, Text/Style/Formatting 1:1 over the
  dependency-free JSON walk): the GSON tree semantics ported whole — the
  bare-literal short form, the style fields inlined in the reference's own
  order (addStyle before the type dispatch), the with-args flattening (a
  bare literal arg becomes its raw string), the empty-array null quirk
  (fromJson("[]") answers null), and the insertion-vanishing quirk
  (Style.isEmpty omits the insertion field — an insertion-only style
  serializes as the bare form and drops it). ScoreText's content consults
  the live main board through the installed-supplier seam;
  TranslatableText's translation machinery is client-side (the no-locale
  fallback answers the key itself).
- **THE WIRE**: Title 0x45 (TitlesS2CPacket's write walk — the varint enum
  ordinal, the JSON text on TITLE/SUBTITLE, the three i32s on TIMES, the
  empty CLEAR/RESET arms) + Player List Header/Footer 0x47
  (TabListS2CPacket's two-text walk) over ClientLink's six new title verbs
  + the header/footer verb + the raw-JSON chat verb (Chat 0x02 at a
  position byte — the reference's sendMessage(Component)); the conformance
  rows + Protocol18's constants. The vanilla server NEVER sends 0x47 (the
  reference wires only the client handler) — the verb exists for the
  native API surface.
- **THE SELECTOR** (`server/command/TargetSelector` 1:1): the whole
  p/a/e/r walk over the engine's universe seam — the positional short
  form, type/xp/gamemode/team/score_/name/rm-r/ry-rx/c with the
  reference's own quirks (the p/a type-ignored quirk, the !type
  matches-players quirk, the r shuffle, the p/a/e distance sort, the c==1
  self-bias, the wrapDegrees windows, the negative-d box extension). The
  engine install: the live players + the living mobs per the reference's
  dimension ids (-1/0/1), the main board for the score_/team predicates.
  The engine's mobs carry no public uuid (the score owner key rides the
  name) and no tracked pitch (rx reads 0) — ledgered.
- **THE COMMANDS** (op-2): /title (TitleCommand 1:1 — the five arms with
  the exact args gates, the case-insensitive type names with the TITLE
  default, the strict times ints, the joined-string JSON parse with the
  tellraw jsonException root-cause refusal) + /tellraw (TellRawCommand
  1:1 — updateForEntity resolves the component against the receiving
  player, the delivery as the raw-JSON system chat line);
  TextUtils.updateForEntity 1:1 (the score-owner selector walk, the
  selector components into the listText "a, b and c" join, the translate
  args recursion) + CommandParse (parsePlayer's
  selector/uuid/name/not-found ladder, parseString, the strict parseInt).
- **THE 0x47 API**: Server.setTabListHeaderFooter(headerJson, footerJson)
  — the stored pair broadcast to every PLAYING session, later joins get
  the pair at their own burst (joinTabListHeaderFooter, called beside
  joinWorldBorder in the adapter's join walk); null clears to the wire's
  null-text form.
- Tests: TextJsonTest (22), TargetSelectorTest (5 over the live engine),
  TitleTellRawCommandTest (4), TabListHeaderFooterApiTest (1); the
  protocol conformance rows extended.

NEXT (in order): (1) the remaining twenty-one missing vanilla commands —
the batch now unblocked by the selector (execute, testfor, kill @e,
spreadplayers, stats, effect, enchant, achievement, blockdata,
entitydata, replaceitem, setblock, fill, clone, particle, playsound,
setworldspawn, spawnpoint, toggledownfall, trigger, debug — the
/summon-style 1:1 walks); (2) the public Player-surface title/chat
exposure (§27.3 — the verbs are ClientLink-internal this slice); (3) the
real-wire title integration test over the protocol module; (4) the §27
window-family re-sync rows; (5) the border's worldborder.json
persistence; (6) the periodic release cadence.

## Slice 88 — the missing-commands batch, wave one (/kill any-kind + /testfor + /toggledownfall + /spawnpoint + /setworldspawn)

- **/KILL** (KillCommand 1:1): the no-arg self arm, the any-kind ladder
  (selector → player-name → uuid), the player death walk (the reference's
  discard resolves through the death screen) vs the mob's no-drops removal
  (discard bypasses the loot walk).
- **/TESTFOR** (TestForCommand 1:1): the same ladder + "Found %s". The
  dataTag arm is NOT implemented (needs the reference's writeNbtWithoutId
  surface — the engine's persistence is store-shaped); ledgered.
- **/TOGGLEDOWNFALL** (ToggleDownfallCommand 1:1): the rain-flag flip. The
  engine's setWeather carries the /weather default duration (no timer read
  surface; the reference leaves the clocks untouched); ledgered.
- **/SPAWNPOINT** (SpawnPointCommand 1:1): parsePlayer-or-self, the
  ~-relative coords or the target's own block, the ZPD v7 bed-spawn stamp
  (the respawn walk already consults it).
- **/SETWORLDSPAWN** (SetWorldSpawnCommand 1:1): coords-or-source (the
  console's source is the world spawn), the spawn.json persistence, and
  the Spawn Position 0x05 broadcast through the new
  ClientLink.updateSpawnPosition verb.
- The CommandParse.parseEntity ladder (selector, by-name, uuid,
  not-found) + the weather() accessor.
- Tests: MissingCommandsWaveOneTest (5).

NEXT: wave two — /effect + /enchant + /achievement; then /particle +
/playsound; then /execute (+detect); then /setblock /fill /clone; then
/blockdata /entitydata /replaceitem /stats /trigger /debug.

## Slice 88 wave six — /execute + /spreadplayers + /stats (the command-stats engine) + /replaceitem

- **THE COMMAND-SOURCE SURFACE** (CommandSender's new defaults — the
  reference's getCommandSourcePos/getCommandSourceWorld/asEntity 1:1):
  sourcePos/sourceDimension/asSourceEntity carry the player/console
  shapes; the execute proxy overrides all three. The ~-parse sites
  (playsound/particle/setblock/fill/clone/worldborder + spawnpoint's
  coord arm, which the reference parses against the SOURCE, not the
  target) re-anchor through it. TargetSelector.sourceViewOf reads the
  same surface; CommandService.dispatchCounted (CommandRegistry.run's
  j count) answers /execute's failed-run detection — the engine's
  string-feedback convention counts a completed run as one success
  (ledgered against the reference's throwing run).
- **/EXECUTE** (ExecuteCommand 1:1, op-2): the any-kind entity ladder,
  the ~-coords against the ENTITY's body with the ±30000000 bounds
  (CommandParse.parseCoordinate), the detect arm (the second triple
  against the first, the block + the -1..15 meta with -1 any, the
  named-variant state match, the mismatch refusal), and the nested run
  over the proxied source — name/entity/dimension/pos swapped, the
  feedback routed to the original sender, the permission inherited, the
  results routed to the ENTITY's holder. The reference's own quirk
  preserved: the j<1 allInvocationsFailed throw lands inside its own
  catch arm — the failed line always shows, allInvocationsFailed never
  does.
- **/SPREADPLAYERS** (SpreadPlayersCommand 1:1, op-2): the centered x/z
  parse (a bare integer +0.5), the spread/maxRange bounds (maxRange >=
  spread+1), the respect-teams boolean (CommandParse.parseBoolean), the
  selector-or-name entity list, the reference's own relaxation ported
  whole (the Pile class — 10000 iterations, the too-close pushes, the
  clamps, the fire/liquid re-rolls over FluidBlocks.isFluid + fire, the
  random reset, the empty-column 257 landing Y), the floor+0.5 landing
  with the team pile sharing (getTeamCount over the board's
  getTeamOfMember), the count + average-distance feedback, and the
  impossible-space failure line. MobEntity.teleport (Entity.teleport's
  plain position set) carries the mob arm.
- **THE COMMAND-STATS ENGINE** (EngineCommandResults — the reference's
  CommandResults ported whole): the five types by their reference
  names, the per-type (source, objective) bindings, the hasScore-gated
  set on add (an existing score only), the parseEntityName ladder (a
  player answers its name, a mob its name key — the engine's mobs
  carry no public uuid, ledgered; an unmatched selector refuses; a
  plain string answers itself). The holders: PlayerSession + MobEntity
  (the reference's Entity field), the command-block tile's CommandState,
  the sign rows (with the break cleanup). The reporting:
  CommandSender.addResult (the console's empty arm — MinecraftServer's
  own override), the chat player sender, the execute proxy (the
  ENTITY's holder), the command-block executor sender; the
  dispatch-level AFFECTED_ENTITIES/SUCCESS_COUNT on every path; and the
  per-command sites (setblock/fill/clone AFFECTED_BLOCKS 0-upfront +
  the count, give/clear/enchant AFFECTED_ITEMS, the time query's
  QUERY_RESULT).
- **/STATS** (StatsCommand 1:1, op-2): the usage ladder, the entity arm
  (the any-kind ladder — the holder on the player or the mob) and the
  block arm (a command-block tile or a sign), the set pair with the
  empty-string refusal, the clear arm, the unknown-type refusal.
- **/REPLACEITEM** (ReplaceItemCommand 1:1, op-2): the reference's
  SLOTS_BY_ID table verbatim, the entity arm (the player's weapon = the
  held cell, the armor family through the engine's kind-gated setArmor
  (the reference's raw array write — ledgered), the 0-35 hotbar +
  inventory, the 27 enderchest cells; the mob's weapon; the
  villager/horse families and the mob armor answer the failed refusal —
  the engine models those inventories nowhere, ledgered), the block arm
  (the container families by the BLOCK's identity with the lazy
  getOrCreate standing in for the reference's placement-created tile,
  the silent out-of-range skip, the no-container refusal), the literal
  air's clear form, the 1..64 count bounds, the metadata, and the
  AFFECTED_ITEMS stat pair.
- Tests: MissingCommandsWaveOneTest (20 — the execute player/mob arms,
  the spread pair/impossible/team arms, the stats success/zero/count/
  clear arms, the replaceitem entity/block arms); the selector,
  scoreboard, command-block, sign, console, chest/furnace/hopper/
  dispenser/brewing families green.

- **/TESTFORBLOCK** (TestForBlockCommand 1:1, op-2) + **/TESTFORBLOCKS**
  (TestForBlocksCommand 1:1, op-2 — the reference's compare walk): the
  meta gate (-1 any over the type's legacyMetadata), the loaded-chunk
  and loaded-area gates, the 524288 cap, the masked air-skip, the
  per-cell identity compare, the mismatch refusals, the compared-count
  feedback, the AFFECTED_BLOCKS pairs. The dataTag/tile-NBT arms ride
  the readNbt surface (ledgered).
- Tests: MissingCommandsWaveOneTest (22).

NEXT (in order): (1) the NBT command arms — /blockdata + /entitydata +
/testfor's dataTag + /summon's NBT form (the Snbt read/write surface —
Snbt.parseCompound already parses; the per-family tile/mob writeNbt/
readNbt projections are the missing piece); (2) /achievement + the
achievement system (parent chains, toasts); (3) the /debug profiler
ledger; (4) the stats' tile/entity NBT persistence; (5) the §27
window-family re-sync rows + the border's worldborder.json persistence.

## Slice 89 — the NBT surface (the per-family writeNbt/readNbt projections + the NBT command arms)

- **THE NBT SURFACE** (`server/nbt/`): NbtOps (the NbtCompound/NbtUtils/
  NbtElement surface over Snbt.Tag — the deep copy, the deep equality
  with compounds-unordered/lists-ordered, the reference's recursive
  merge, the NbtUtils.matches subset check with the list recursion, the
  SNBT writer with the per-type suffix forms, and the getter family's
  exact coercion contracts: contains(key,99)'s any-numeric widening,
  getBoolean getByte!=0, getList's element-family gate; Tag.asLong's
  64-bit fix — the widened getters truncated the UUID rows), ItemNbt
  (the id/Count/Damage rows), EngineCommandResults.readNbt/writeNbt
  (the CommandStats compound), PotionSystem.putFromNbt (the
  replace-without-gates arm), and the LeadSystem tick's stashed-leash
  resolution (the knot by position, the holder by uuid, the third
  resolver seam).
- **ENTITYNBT** (the reference's Entity.writeNbtWithoutId/readNbt + the
  LivingEntity and MobEntity custom walks + the per-type rows): MobEntity
  grew the base rows (uuid with the LONG-pair read, silent, pitch,
  setRotation's head-yaw follow, the raw fire/hurt/death accessors,
  setVelocity/setOnGround, public rawPersistent, the leashNbt stash +
  applyLeashRows, the per-type setters, setZombieConversionTime's -1
  arm). The per-type rows: sheep Sheared/Color, pig Saddle, horse
  Type/Variant/Temper/Tame with the armor/saddle through the reference's
  own ITEM row forms, villager Profession, wolf
  Tame/Sitting/Angry/CollarColor, zombie
  IsVillager/IsBaby-only-when-true/ConversionTime-with->-1.
- **TILENBT** (the reference's BlockEntity.writeNbt/readNbt): the id
  registry 1:1 with the engine's own block-name spellings (furnace_lit,
  wall_sign*, skull_*, the comparator pairs, hopper_*, the brewing
  stand's bottle variants, jukebox_record), the per-family projections
  (chest/furnace/hopper/dispenser/brewing/beacon/sign/spawner/control/
  jukebox/skull), the Seams record wired in EngineServer with the public
  tileNbt() accessor + EngineWorld.blockRegistry(); CommandState's
  setters public; HopperBlockEntity.cooldown(); JukeboxBlockSystem
  .restoreRecord.
- **THE COMMAND ARMS**: /blockdata (the write-merge-compare-read cycle
  with the forced x/y/z, the unchanged refusal, the notValid/outOfWorld
  refusals, the block re-publish), /entitydata (the noPlayers refusal,
  the UUID strip, the read back whose Pos rows MOVE the body), /testfor's
  dataTag (the subset match against the written rows), /testforblock's
  dataTag (the reference's hasBlockEntity gate on the EXPECTED block),
  and /summon's NBT now reads through EntityNbt.readNbt with the
  reference's own order (the whole entity reads, THEN the command
  coordinates override the Pos rows; the Rotation/Motion stick) — the
  hand-rolled applySummonNbt/parseItemNbt removed.
- **THE NoAI MOVEMENT GATE** (found by the tests): the reference's
  isLocallyControlled over moveRelative — a pinned body integrates NO
  motion (the vanilla midair freeze), the velocity decays *0.98, the
  contact arms and wounds continue, the flight families' own integration
  ledgered.
- Tests: NbtOpsTest (6), EntityNbtTest (11), TileNbtTest (6),
  NbtCommandsTest (3) — the affected batches green (nbt, leash, entity,
  container families, MissingCommandsWaveOne, GiantMob).

NEXT (in order): (1) /achievement + the achievement system (parent
chains, toasts); (2) the /debug profiler; (3) the stats' tile/entity
NBT persistence (the ZCB/ZMD envelopes now carry the rows through
EngineCommandResults.readNbt/writeNbt — the wiring is the piece); (4)
the §27 window-family re-sync rows + the border's worldborder.json
persistence; (5) the older ledgered gaps (the MC|AdvCdm arm type 0, the
areCommandBlocksEnabled gate, the vehicle damage-source surface, the
variant carts' VariantState read view, the public Player-surface
title/chat exposure).

## Slice 90 — the achievement system + the profiler + the stats persistence (the WORKLOG NEXT ledger's own three)

- **p1 — THE ACHIEVEMENT SYSTEM (Achievements + AchievementStat +
  ServerPlayerStats + AchievementCommand 1:1)**: EngineAchievements —
  the 34-row table in the reference's declaration order (the give-all
  walk's own guarantee: every parent strictly before the children that
  gate on it), the parent chains, the challenge and local flags, the
  page bounds, ancestorsOf/dependentsOf (the take walk's own
  dependency tree). PlayerStats grew the reference's semantics —
  hasAchievement/hasParentAchievement, the increment's parent gate,
  the per-session Listener (ServerPlayerStats.set + incrementStat's
  halves), markAllDirty + takeAchievements (the login's
  sendAchievements). StatCriteria — the Stats.init walk over the
  engine's own registries (the general band, the 34 achievements, the
  entity kill pair, the mined family over the block registry through
  the merge fold, the used family, the broken family over
  maxDurability, the crafted family over the recipe results union the
  smelting outputs), each registering its criterion into
  ScoreboardCriterion.BY_NAME (forStat — INTEGER-rendered, not
  read-only). The statListenerFor ride in EngineServer: every write
  follows the stat's criterion objectives (the score follows the
  value), an achievement transition broadcasts the yellow
  chat.type.achievement/.taken line (the bracketed getNameForChat form,
  GREEN / challenges DARK_PURPLE, the SHOW_ACHIEVEMENT hover) as a Chat
  0x02 component to every online player and flushes the dirty set
  through the Client Statistics 0x37 — the toast's server-side
  trigger. /achievement (op-2): the give parent-walk root-first, the
  take's dependents walk (the list1/list2/flag2 shape), the '*' forms
  over the whole table (declaration order for give, reversed for
  take), the already-have/don't-have/unknown refusals, the plain walk
  for general stats, the third-argument player resolution. The Client
  Status 0x16 action 2 (OPEN_INVENTORY_ACHIEVEMENT) through the new
  engine.openInventoryAchievement. The joinCompleted login sync
  (markAllDirty + sendAchievements). Tests: AchievementCommandTest
  (10) green + the scoreboard size row updated for the band (74
  affected green).
- **p2 — THE PROFILER (Profiler + DebugCommand 1:1)**: EngineProfiler
  whole — the section stack with the nano-time table, the delayed
  start/reset, the 100ms warn, the results walk (the self row at
  index 0, the direct children's parent/total shares, the unspecified
  remainder, the *999/1000 decay, the Result ordering). The engine's
  tick walk instrumented at the vanilla section boundaries (root + jobs
  over the drains, swap(levels) + the world's data name + tick over
  the systems fan, the tail's connection/players/tickables swaps —
  ledgered as standing empty, the engine's equivalents live in the
  ticker's submitted-task drain). /debug start/stop real (op-3): the
  delayed activation, the span anchors, the notStarted refusal, the
  report build while the table still collects —
  debug/profile-results-<timestamp>.txt with the witty comment's own
  nanoTime selection, the ticks-per-second line, the BEGIN/END PROFILE
  DUMP and the [NN] spacing rows. DebugProfilerTest (4) green.
- **p3 — THE STATS PERSISTENCE WIRING (the three envelopes)**: the ZCB
  v2 tail (the executor's CommandStats pairs after the four rows,
  v1 files load with empty bindings), the ZMD v5 tail (MobSnapshot's
  ninth field, commandStatsOf/restoreCommandStats, FORMAT_VERSION_4
  split for the v3/v4 boundary), the ZPD v10 tail (PlayerSnapshot's
  sixteenth field, snapshotOf/restorePlayer through the same compound
  form) — all three riding EngineCommandResults.writeNbt/readNbt.
  CommandStatsPersistenceTest (1) green: the /stats-set bindings on a
  player, a named zombie, and a command-block tile survive the
  shutdown + reboot (the VehicleWorldDamage flake green in isolation,
  the 2-core box's own known cadence).

NEXT (in order): (1) the §27 window-family re-sync rows + the border's
worldborder.json persistence; (2) the older ledgered gaps (the MC|AdvCdm
arm type 0, the areCommandBlocksEnabled gate, the vehicle damage-source
surface, the variant carts' VariantState read view, the public
Player-surface title/chat exposure); (3) the sign tiles' command-stats
persistence (the reference's SignBlockEntity carries none — the engine's
own sign store wave); (4) then the next vanilla wave per the ledger.

## Slice 90 closing — the stale-ledger retirement + the action bar (p4-p6)

- **p4 — WORLDBORDER.JSON (c56b5f9)**: WorldBorderDataStore — the
  reference's level.dat border rows (the center pair, the live size, the
  lerp target + the REMAINING lerp time with the resume arm, the safe
  zone, the per-block damage, the warning pair), wired at boot + the
  /save and shutdown walk. WorldBorderPersistenceTest 2 green (the
  stationary family round-trip + the pending 5s shrink's resume).
- **p5 — THE PLAYER TITLE/CHAT SURFACE (f9fcab3)**: Player.sendTitle /
  clearTitle / sendChatLine / sendSystemLine / sendChatComponent over
  Slice 87's wire (the reference's Times-then-Title-then-Subtitle arm
  order; the plain texts through TextJson's bare-string quirk).
  PlayerTitleChatApiTest 3 green.
- **p6 — THE ACTION BAR (c2b37d3)**: Player.sendActionBar (Chat 0x02 @
  position 2).
- **THE STALE-LEDGER RETIREMENT**: verified live in the tree (the Slice
  89 NEXT ledger's "older gaps" rows were stale) — the MC|AdvCdm type-0
  block arm + the areCommandBlocksEnabled gate (V18Connection's payload
  walk), the vehicle damage-source surface (VehicleEntity.DamageSource,
  Slice 74 — the furnace cart's furnace block skips an explosive death),
  and the variant carts' VariantState read view (Slice 68 —
  VariantStateApiAcceptanceTest).

NEXT (in order): (1) the §27 window family — the open-window re-sync to
a viewer (the container rows' own "rides the window family's wave" notes:
a plugin's setSlot on a chest/hopper with an open viewer must re-sync the
viewer's Window Items, the engine's own diff paths generalized) + the
window family's public surface (the audit's named blocker wave); (2) the
sign tiles' command-stats persistence (the reference carries none — the
engine's own convention decision); (3) the next vanilla wave per the
ledger's pending rows (survey VANILLA_1_8_8_COMPATIBILITY.md's open
envelopes first).

## Slice 91 — the §27 window family's re-sync wave + the public open-window surface (p1-p3, the chest arms open)

- **p1 — THE OPEN-WINDOW RE-SYNC (the furnace's own diff path generalized)**:
  EngineServer grew the three per-tick view fans over the furnace idiom —
  tickChestViewers (the world chest, the session's private ender grid, the
  chest minecart's own chest resolved by the mount id), tickHopperViewers
  (the world hopper, the hopper minecart's hopper by the mount id),
  tickDispenserViewers (the dispenser/dropper) — fired in the tick walk
  after the enchanting fan. V18ProtocolServer subscribes the three;
  V18Connection carries the per-window serial diffs
  (chestSlotsSynced/hopperSlotsSynced/dispenserSlotsSynced, captured at
  open by the beacon idiom) and re-syncs the open viewer's Window Items
  only when the container's slotsSerial moved: a plugin's setSlot through
  the public container views, the hopper system's own transfer, any
  engine-side write — all bump the same serial. HopperBlockEntity grew its
  own slotsSerial (the chest's pattern — the BE had none). The
  furnace/brewing/beacon stay on their per-tick view fans (already covered,
  verified not duplicated).
- **p2 — THE PUBLIC OPEN-WINDOW SURFACE (the audit's named blocker)**: the
  api's entity.WindowKind (the fourteen kinds), Player.openWindow() ->
  Player.OpenWindow (kind/windowId/position/mountId — the live view over
  the session's own window state, the engine object IS the handle §27.5);
  PlayerSession.openWindow() + the ContainerKind->WindowKind mapping.
  OpenWindowApiAcceptanceTest 2 green (the quadruple walk through the
  position-keyed/private/mount-keyed families + the close + the live-view
  identity + the off-lane best-effort read).
- **p3 — THE VOID-FALL CHUNK-GATE CRASH (found by the test's fourth join)**:
  a mob mid-fall below y=0 — the reference's OWN Entity.tick void window
  (Entity.java line 342: y < -64.0 -> voidTick — the -64 kill plane,
  verified against the reference source; the engine's VOID_KILL_Y is the
  correct 1:1 value) — crashed every chunk-visibility gate that built a
  BlockPosition from the entity position: sendMobSpawn (the login
  continuation's playerEnteredPlay — the y=-1 IllegalArgumentException
  closing the channel, the client EOF at the read boundaries), the login's
  sendInitial, the movement updateCenter, and the orb/item/falling/
  mob-equipment spawn gates. The fix: chunkKeyOf(Position) — the chunk
  column from x/z alone (floor division); y never changes which 16x256x16
  column a position belongs to, and a body in the void window never
  bounds-crashes the lookup. The remaining toBlockPosition sites are
  packet-payload writes whose positions are engine-validated — the
  follow-up audit rides the next wave.

TEST STATUS (WindowResyncIntegrationTest 4-2): the hopper arm GREEN (a
plugin's setSlot through the public hopper view re-syncs the open viewer's
Window Items over the real wire), the dispenser arm GREEN (an engine-side
write re-syncs — the dispenser has no public container view yet, the
honest gap the row documents). The two chest arms REMAIN: (a) Feeder's
fixture is order-dependent (its chest is placed by a test that runs later
in JUnit's order — the self-containment fix next); (b) Resyncer's plugin
write never re-syncs — the open, the open's items, the capture, the fan,
and the adapter wiring are all verified present in the tree (the hopper
arm proves the whole path works), so the next pass is one diagnostic run
on the chest arm: assert the BE serial after the write, then the fan's
read. Also open: the earlier full-suite runs died silently twice (the
daemon vanishing mid-test-task — the y=-1 crash killed the login
continuations; with p3 landed the suite should hold — re-run to verify).

NEXT (in order): (1) the two chest arms of WindowResyncIntegrationTest
(the self-containment fix + the one diagnostic pass); (2) the full-suite
re-run (854 + the new tests) to verify p3 holds across every login; (3)
the A3 ledger rows (the vanilla ledger's container rows' "rides the window
family's wave" notes retired + the API capability ledger's window-family
row); (4) Wave B — the sign tiles' command-stats persistence; (5) Wave C
per the Torch-Arcetecture.txt §29 ledger.

## Slice 91 closing — the chest arms + the hopper's dead listener + the ledger rows (p4-p6)

- **p4 — THE TWO CHEST ARMS GREEN (863d8a2)**: the WIRE_TRACE diagnosis
  found the real break — the chest was never placed. The client-placed
  chest at column x=6 sat OUTSIDE the survival placement reach:
  InteractionRules.withinSurvivalReach measures the eye to the target
  block's center against 4.5^2, the spawn is (0.5,5,0.5), and column 6
  at z=2 is x^2+z^2=40 against the ~19 budget — the engine rejected the
  placement and resync() published the authoritative AIR (the wire
  trace's own legacy=0 tell at (6,5,2), the BlockInteractionService
  comment's own walk). The duplicate post-open read removed too (one
  Window Items per Open Window — the fan re-sends only on a serial
  move). The fixtures are now fully self-contained: every test owns its
  column inside the spawn chunk with its own player — any execution
  order, any subset, one class-shared world. The transfer test's own
  chest carries a diamond at slot 4 seeded through the proven
  plugin-write arm, so the hopper's transfer asserts the vanilla
  pushItem walk (the FIRST EMPTY slot) on a live fixture.
  WindowResyncIntegrationTest 4-4 over the real wire: the plugin's
  setSlot re-sync (chest + hopper), the hopper system's own transfer
  into the open-viewed chest, and the engine-side dispenser write.
- **p5 — THE HOPPER'S DEAD CONTENTS-LISTENER WIRED (970a42a)**: the
  A1-legacy note's one open sub-item. HopperSystem.getOrCreate created
  the block entity without ever wiring the comparator wake — a hopper
  whose contents changed (a transfer in or out, a plugin's setSlot)
  never re-armed the comparators reading it, while the chest, furnace,
  brewing stand, and dispenser family all carried the wiring. getOrCreate
  now wires () -> redstone.wakeComparators(position) at creation (the
  DispenserSystem line-667 shape) and restoreAll wires the restored
  entities too (the lazily-wired restore contract).
  theHopperContentChangeWakesTheComparator green — the chest-wake
  test's own shape over the 5-slot hopper (the empty silence, one full
  stack's fullness 3 = floor(14*1/5)+1, the emptied 0). Batch
  re-verified: RedstoneComparatorTest 6, HopperSystemTest 8,
  HopperWindowAcceptanceTest 2, FurnaceHopperEventAcceptanceTest 5,
  WindowResyncIntegrationTest 4.
- **p6 — THE LEDGER ROWS (this commit)**: the API capability ledger's
  three container rows (hoppers/chests/furnaces) — the "rides the window
  family's wave" notes retired, replaced by the landed-fact rows naming
  WindowResyncIntegrationTest; the hopper row names the p5 wiring; the
  window-family row added beside the Player rows (Player.openWindow() ->
  Player.OpenWindow, the live view + the re-sync story +
  OpenWindowApiAcceptanceTest); the Torch-Arcetecture.txt §29 Wave A
  checked off COMPLETE (A1/A2/A1-legacy/A3, each with its landing
  commit).

WAVE A COMPLETE. NEXT (in order): (1) Wave B — the sign tiles'
command-stats persistence (the engine's own convention decision: the
SignManager's in-memory statsHolders persist through the sign data
store the way the ZCB v2 / ZMD v5 / ZPD v10 tails ride
EngineCommandResults.writeNbt/readNbt — version-aware, older files
load with empty bindings); (2) Wave C — survey
VANILLA_1_8_8_COMPATIBILITY.md's standing envelopes and land the next
dependency-correct vanilla wave; (3) the standing demand-named items
(the shared container-open event, the §28.7 Bukkit adapter — after the
1.8.8 implementation is polished).

## Slice 91 Wave C (first half) — the spawner's cadence + the creeper's own rows

- **p1 (8245604) — THE SPAWNER'S CADENCE ROWS**: the six NBT rows steer
  each spawner's own walk — MobSpawnerBlockEntity carries the reference's
  fields (the defaults 200/800/4/6/16/4), the tick walk reads the
  spawner's OWN rows (the player gate, the attempt count, the nearby cap,
  the re-arm band, the position roll), the nearby count ports the
  reference's exact grown-box geometry (the [pos, pos+1] box grown by the
  range, intersected with each mob's own bounding box — the old
  center-band stand-in missed the far-edge mobs), the TileNbt read
  carries the PAIRED contains(key,99) gates with the reference's own
  zeroing quirk (MinSpawnDelay alone zeroes maxSpawnDelay AND
  spawnCount), and the wire tile carries the per-spawner rows (the
  block-event-1 spin reset reads the client's minSpawnDelay from them).
  SpawnerManagerTest 9 (5 new), TileNbtTest 7 (1 new), NbtCommandsTest 3,
  SpawnerMinecartTest 6.
- **p2 (bc8f0fb) — THE CREEPER'S OWN ROWS**: powered/ExplosionRadius/
  ignited/Fuse into the model — the charged flag doubles the blast
  (radius * (charged ? 2.0F : 1.0F)), the permanent ignite never cools
  (the swell arm sits ABOVE the goal walk — the stroll goal was consuming
  target-less creepers), the Fuse row steers the swell's duration, the
  NBT rows carry the reference's exact write gates (powered only when
  charged, the one-way ignite) and read gates (the numeric contains
  gates), the wire publishes the index-17 charged byte beside the
  index-16 fuse. CreeperRowsAcceptanceTest 3 over the live engine (the
  event's power 4 = 2 * 2.0F, the no-target ignited swell, the radius-1
  sphere), EntityNbtTest's creeper round-trip, GameRule/MobManager/
  Peaceful re-verified.

NEXT (in order): C2 — the containers' CustomName/Lock rows (the
dependency check done: ItemStack already carries displayName — the Lock
gate's key is available; the reference's openChestMenu walk: locked &&
!canUnlockInventory && !spectator -> the action-bar chat
(container.isLocked) + random.door_close + no window; canUnlockInventory
= the held item's custom hover name equals the lock key); then C3's
remaining envelopes per the §29 ledger.

## Slice 91 Wave C (second half) — the containers' CustomName/Lock rows (01099c8)

- **p3 — THE NAMED-KEY GATE + THE CUSTOM WINDOW TITLE**: the five
  container families (chest/furnace/hopper/dispenser/brewing) carry the
  reference's lockKey + customName; the open gate is
  ServerPlayerEntity.openChestMenu's own walk — a locked container
  without the named key answers the ACTION-BAR chat (the translatable
  container.isLocked component over the display name, Chat 0x02 @ 2
  through TextJson) + random.door_close and never opens; the key is the
  held item's own custom hover name (ItemStack.displayName — the
  dependency was already satisfied); spectators pass every lock. The
  custom window title rides the Open Window's title row (the LiteralText
  over the family default). The TileNbt rows carry the reference's write
  gates (CustomName only when set, Lock ALWAYS — even NONE writes its
  empty key) and the string-typed read gates. ContainerLockAcceptanceTest
  2 (the lock-out chat + no window; the named key opening; the wrong
  name; the empty lock), TileNbtTest 8 (the name-lock round-trip), the
  container sweep 29 (ContainerApi, BrewingStandApi, HopperSystem,
  DispenserSystem, WindowResync + ChestIntegration over the real wire).

NEXT (in order): C3's remaining envelopes per the §29 ledger — the
skeleton's SkeletonType (the wither mapping), the ocelot's CatType, the
base defaults (FallDistance/Air/PortalCooldown/HurtByTimestamp/
AbsorptionAmount/CanPickUpLoot/DropChances), the horse's/wolf's
OwnerUUID + the horse's EatingHaystack/ChestedHorse/HasReproduced/Bred,
the item-tag compound's remaining arms, the flight families' NoAI
interaction, the lazy-tile stand-in, the player-write model.

## Slice 91 Wave C (tail) — the skeleton's SkeletonType + the ocelot's CatType (b745b65)

- **p4 — THE PER-KIND STRAGGLERS**: both models already lived on
  MobEntity (skeletonWither — the wither hull/fire immunity/melee;
  catVariant — the coat, already on the wire's index-18 byte); only the
  NBT rows stood as envelopes. SkeletonType rides the reference's
  contains(key,99) gate (an absent row keeps the variant);
  CatType rides the reference's own UNGATED read (an absent row reads
  its int 0 and the coat resets to wild — captured in the test).
  EntityNbtTest 13, MobManagerTest 8, OcelotEntityTest 5.

NOTE (the environment's own constraint): the full-suite background run
does not survive this box's process reaping (the wrapper scripts and
setsid launches die with the tool window — full-suite-89/90/91.log all
truncate at the same task). The session's verification is the per-batch
runs: every wave's own batch + the adjacent regression batches, all
green (the counts ride each commit's message).

## Slice 93 — Wave D: the living family's lava walk + the item thrower

- **D1 (156a890) — THE GIVE_DIAMOND ARM**: the C3 tail's first standing
  envelope retired. The ItemEntity carries the reference's two private
  strings (thrower/owner); the Q-key drop alone stamps the thrower
  (dropItem's thrownByPlayer arm — the container spills and death drops
  ride the two-arg form, stamping nothing); the pickup walk consults the
  owner-window gate (owner == null || 6000-age <= 200 || owner.equals);
  the onItemPickupEffects callback fires on EVERY successful add (the
  partial pickup included — addItem-returned-true); the achievement
  family (GET_LOG log/log2, KILL_COW leather, GET_DIAMOND, GET_BLAZE_ROD,
  GIVE_DIAMOND-through-the-registry-lookup with the self-pickup exempt)
  and random.pop (0.2F, the ((r-r)*0.7+1)*2 pitch) land in the server's
  dispatch. THE DUPLICATION FIX THE LANDING EXPOSED: the window-click
  outside arm dropped the cursor's stack AND refilled the inventory
  (returnCursor) — one copy leaves the body now (takeCursor). The tests
  taught the parent-chain gate (PlayerStats.increment's own vanilla
  condition — the chains earned root-first like the /achievement give
  walk). ItemThrowerPickupTest 6; the adjacent sweep 10 classes green.
- **D2 (f40f8b6) — THE LIVING FAMILY'S LAVA WALK**: the C3 tail's second
  standing envelope retired. The mob arm (4.0 LAVA-source wound at the
  10-tick i-frame cadence + the 15-second max-arm ignite, fire-immune
  kinds exempt, FIRE_RESISTANCE gating the wound through the per-mob
  amplifier query), the burn clock's own resistance gate (the ON_FIRE
  residual never wounds a resistant body — LivingEntity 645-647's isFire
  consult), the player arm (tickLavaBody, "tried to swim in lava", the
  LAVA protection kind, 0.3 exhaustion), and THE SHARED FLUID MODEL'S
  SPLIT in three consults: the fall store (lava *0.5 halving vs water
  full zero, the reference's tick-then-move order), the burn douse (the
  lava keeps the burn), the blaze's drown (isInWaterOrRain alone — the
  pre-split engine drowned a blaze in its own element). The debugging
  taught: the SwimGoal rides both fluids in the reference too (the
  settled body bobs — the halving's window is the submerged descent),
  tickLanding consumes a grounded body's store (the halving's window is
  the not-grounded descent), and nested ticker submits drain inside the
  SAME task phase (the delayed read rides the public scheduler's
  runLater). LavaWalkTest 8; the adjacent sweep 12 classes green.
- **D3 — the ledger rows**: the NBT-surface row's standing-envelope list
  rewritten (the C3/C3-tail landings named with their commits; the
  remaining envelopes: the no-engine-block-entity tile families, the
  item-entity NBT surface, the model-less item-tag arms), the two Wave D
  rows appended (the evidence, the tests, the standing notes — the lava
  motion drag, the flight families' skip, the wound-shadowing race), the
  §29 ledger checked off, this WORKLOG entry.

NEXT (in order): the §29 standing list — the shared container-open
event (demand-named), the §28.7 Bukkit adapter (after the 1.8.8
implementation is polished); the next vanilla wave from the ledger's
own remaining envelopes (the item-entity NBT surface, the tile families
with no engine block entity, the lava motion drag).

## Slice 93 — Wave E: the item bodies' persistence

- **E1 — THE ZID v1 STORE + THE JOIN REPLAY**: the item bodies survive
  the restart (the ledger's standing envelope — a restart used to erase
  every drop on the ground). The manager's snapshot/restore surface
  (ItemRecord over the reference's writeCustomNbt row set — Health
  excepted, the engine's item body carries no health model); the ZID v1
  store with the ZMD durability rules (atomic, quarantine, loud) and the
  ZMD v6 held-stack tail's own shape for the stack rows (the id/count/
  damage varints + the nullable display name + the {id, lvl} pairs with
  the lvl>=1 gate); the boot restore bypassing the §27.7 spawn gate (the
  persisted bodies are already-committed state, like the mob store's
  restoreAll); the saveAllNow arm. THE JOIN-REPLAY GAP THE LANDING
  EXPOSED: a body dropped while nobody watched (or restored at boot)
  never reached the next joiner — the newcomer's world snapshot now
  spawns the pre-existing items like every other family (the send's own
  chunk-tracker gate applies).
- **E2 — the tests + the rows**: ItemPersistenceTest 4 (the whole-body
  round trip — the named enchanted sword with its thrower/motion/age/
  delay, the pinned star past the wall, the plain count; the corrupt
  quarantine; the unresolvable-stack skip; the live-engine restart
  dance), the regression sweep green (5 core classes + the FULL protocol
  suite 40 + the api suite 4), the ledger row + the §29 check-offs +
  this entry.

NEXT (in order): the §29 standing list (the shared container-open
event, demand-named; the §28.7 Bukkit adapter, after the polish) and
the ledger's remaining envelopes — the tile families with no engine
block entity (EnderChest/daylight detector/enchanting table/end portal/
flower pot/banner/piston moving state/comparator OutputSignal/skull
Owner profile), the lava motion drag (the *0.5 liquid drag vs the
shared *0.8 buoyancy), the model-less item-tag arms.

## Slice 93 — Wave F: the lava motion drag

- **F1 — THE TRAVEL BRANCH'S SPLIT**: LivingEntity.travel's lava arm
  (lines 1202-1214) over the mob walk — the vertical v = v*0.5 - 0.02
  (the geometric transient: a plunging body keeps most of its speed the
  first submerged tick, decelerating over ~5 ticks — not the water
  clamp's instant -0.04), the horizontal *0.5 crawl on the damping
  line, and the MOUNT walk's own split (the ridden body rides the same
  arm, its douse consult split too). THE DOUBLE-DRAG BUG THE TEST
  CAUGHT: the first cut applied the horizontal *0.5 in BOTH the travel
  branch and the damping line (0.125 per tick) — the damping line
  carries it alone now.
- **F2 — the tests + the rows**: LavaMotionDragTest 4 (the plunge's
  first-tick window with the water contrast, the swim-vs-drag
  equilibrium near zero, the crawl, the immune pigman), the regression
  sweep green (7 classes), the ledger row + the §29 check-offs + this
  entry. Ledgered: the climb-out assist (the pool-exit behavior), the
  one-tick drag phase.

NEXT (in order): the §29 standing list (the shared container-open
event, demand-named; the §28.7 Bukkit adapter, after the polish) and
the ledger's remaining envelopes — the tile families with no engine
block entity (EnderChest/daylight detector/enchanting table/end portal/
flower pot/banner/piston moving state/comparator OutputSignal/skull
Owner profile), the model-less item-tag arms.

## Slice 94 — Wave G: the model-less item-tag arms

- **G1 — the four arms' full ride** (0dc74e6 + cc6ca5d): the ItemStack
  record's unbreakable/repairCost/hideFlags/lore components (null-absent
  and zero-absent, the immutable lore with MAX_LORE_LINES 16, the 5-arg
  compatibility constructor), the Unbreakable gate at the reference's
  isDamageable shape over the five wear sites (damageHeld/wearArmor/
  wearArmorStack, MobManager's drop-equipment shave, DispenserSystem's
  wearOne — every other engine wear routes through damageHeld), the
  constructor's past-the-max damage exemption (readNbt 143-145 clamps
  only the negative; the read's Damage row now applies AFTER the tag
  arms so the flag lands first), the ItemNbt rows (each written only
  when carried, each read into the model), the SlotNbt wire (display
  .Lore a TAG_LIST of TAG_STRING, the scalars byte/int/int), the ZID v2
  and ZMD v7 tails (version-aware: v1/v6 files load with defaults).
- **G2 — the tests + THE SPLIT/MERGE FIX** (c7dedb1): the pickup test
  exposed that split built the taken stack over the 5-arg constructor —
  every pickup, container move and drop STRIPPED the arms (a flagged
  pickaxe lost its flag the moment it entered an inventory). split
  threads the four arms now, and mergeable rides the reference's
  areItemStacksEqual (the whole tag compared — a flagged stack never
  merges into an unflagged one, a lore-carrying stack never into a bare
  one). The batches: ItemTagRowsTest 8, PlayerInventoryTest 21,
  SlotNbtTest 10 (byte-exact), ItemPersistenceTest 6 (incl. the
  hand-written v1 file), MobDataStoreTest 7, FireAcceptanceTest 6 (the
  live flagged flint — three fires, zero wear), DispenserSystemTest 11
  (the flagged steel lights and stays whole); the sweep: the full
  protocol suite 104 green + UnbreakingAcceptance 8, ItemEntityManager
  11, LootPickupModel 11, PlayerDataPersistence 5, MobManager 8,
  CommandStatsPersistence 2, the container family 26.

NEXT (in order): the §29 standing list (the shared container-open
event, demand-named; the §28.7 Bukkit adapter, after the polish) and
the ledger's remaining envelopes — the tile families with no engine
block entity (EnderChest/daylight detector/enchanting table/end portal/
flower pot/banner/piston moving state/comparator OutputSignal/skull
Owner profile), the item-tag envelope's remains (CanDestroy/CanPlace,
BlockEntityTag, the attribute modifiers, display.color).

## Slice 94 (cont.) — Wave H1: the comparator's OutputSignal persistence

- **H1 — the ZCD store** (7de9ad4): the reference's
  ComparatorBlockEntity is one int row (writeNbt OutputSignal, readNbt
  reads it) — the engine's model existed (RedstoneSystem's
  comparatorOutputs), the persistence alone was missing (a restart
  answered 0 until the first update). The ComparatorDataStore (the ZND
  pattern — atomic writes, corrupt-quarantine, the per-position i32
  rows), the snapshot/restore surface (the restore at boot lands the
  rows BEFORE any update — the loaded comparator answers its stored
  strength straight into getWeakPower), the EngineServer wiring (the
  boot restore beside the redstoneSystem creation, the saveAllNow arm).
  RedstoneComparatorTest 7 (the new restart round trip: the chest-fed
  comparator's live 7 rides the store through the reboot).

NEXT (in order): Wave H2 — the BlockEntityTag placement arm (BlockItem
lines 76-94: the raw-compound model arm, ItemNbt's row, the placement
consult over the TilePlacementListener-created tile — the merge walk,
the forced x/y/z, the op gate over the command-block family, the
no-change skip) + H3's tests; then the ledger's remaining envelopes
(the other no-BE tile families: EnderChest contents on the player, the
skull's Owner profile; the item-tag envelope's last arms:
CanDestroy/CanPlace, the attribute modifiers, display.color).

## Slice 95 — Wave H2/H3: the BlockEntityTag placement arm (COMPLETE)

The evidence (BlockItem.java lines 70-100 + the 1.8.8 quirks): the
stack's tag compound carries a BlockEntityTag row merged into the
freshly-placed tile's written rows (x/y/z forced back after the merge),
the requireOpForPlacingWithNbt gate (THREE families override true:
Sign/Control/MobSpawner — the planning note's "command-block family
alone" was shorthand, the reference wins), and the no-change refusal is
DEAD CODE in 1.8.8 (the pristine copy precedes the write, lines 83-84 —
x/y/z always land, the read always runs; fixed in 1.9+ by copying after).

- ce2b3a1 (p1): the model arm — the tenth ItemStack component, the raw
  compound as a boxed tree (Map<String,Object>; the api module cannot
  see Snbt.Tag, and the boxed tree's Map/List equals IS the reference's
  NbtCompound.equals — unordered keys, ordered lists), the deep
  immutable sanitizer, mergeable's int[] VALUE-equality walk, the
  NbtOps toBoxed/fromBoxed adapters, ItemNbt's row (the type-10 gate).
- 8085565 (p2): the wire (SlotNbt's generic walk — long/float/double/
  int-array added, the empty list's type-0, lists-of-lists) + the ZID
  v3/ZMD v8 tails + THE VERSION-GATE REPAIR (Slice 94's 6->7 bump had
  left the v6 tameable/horse tail on the bare FORMAT_VERSION — a v6
  file would misparse into quarantine; it now names FORMAT_VERSION_6).
- 8a07cf7 (p3): the consult — TileNbt.applyPlacementTag 1:1, wired at
  both commit sites (the creative arm consults the authoritative held
  stack; the survival arm consults between the commit and the consume,
  the reference's own order) through the PlacementTagConsult seam.
- 9b7f0aa (p4): the /give dataTag arm (ItemNbt.readTagInto shared by
  the row read and the give walk; the usage string's mangled "etadata"
  fragment repaired).
- 684ee5b (H3): BlockEntityTagPlacementTest 8 green — the chest
  Items/Lock/CustomName through the consult, the spawner cadence for an
  op vs the non-op refusal, the command-block op-gate (the placement
  stays committed through a refusal), the 1.8.8 dead-refusal quirk, the
  flagship /give -> stack -> placement -> locked-chest loop.
- 5dd1c99 (p5): THE TAILS' REPAIR — NbtOps.write emits the reference's
  toString list form (index-colon 0:{..}), UNREADABLE by the parser;
  the tails' first cut quarantined their own saves (ItemPersistenceTest
  caught it live: zero bodies loaded). NbtOps.writeParseable added (the
  parseable form: lists [a,b], quoted strings; the empty-list -> empty
  int-array corner is the 1.8.8 grammar's own ambiguity, ledgered).

Sweeps: BlockEntityTagPlacementTest 8, SlotNbtTest 11, ItemPersistence
7, MobDataStore 7, ItemTagRows 8, TileNbt 8, NbtOps 7 — all green.

NEXT: the standing rows (the remaining model-less arms: CanDestroy/
CanPlace, the attribute modifiers, display.color; the skull's Owner
profile; the mob_spawner item registration); then the next vanilla
wave per the compatibility ledger.

## Slice 96 — Wave I: the adventure gates (COMPLETE)

The evidence (ItemStack.java 689-734, PlayerEntity.canUseItemOn
1509-1521, ServerPlayerInteractionManager 112-133/195-211): the
abilities' mayBuild is FALSE in adventure and spectator; the placement
consult names the CLICKED block (pos.offset(face.opposite)); the mine
consults (BOTH arms) name the mined block. The lists are TAG_LIST of
TAG_STRING; canUseOnBlockInAdventureMode is constant-false in the whole
reference set.

- 9355690 (I1): the model arms + rows + wire + the ZID v4/ZMD v9 tails
  (four withX stragglers caught still on the 10-arg form — they would
  have STRIPPED the new arms; the audit pattern stays in the commit).
- 9004a56 (I2+I3): the gates (mayModifyWorld/canUseItemOn/canMineBlock
  helpers in the interaction service; ADVENTURE routed through the
  survival placement path, a passing gate consuming; the spectator use
  guard) + AdventureGatesTest 4 green.

NEXT per the standing rows: the attribute modifiers + display.color
(the item-tag envelope's remains); the skull's Owner profile; the
EnderChest contents-on-player angle.

### The color tail (67102d2)

display.color — the leather tint, ArmorItem.getColor's own row (a
contains(...,3) INT under the display compound; the 10511680 default is
the render model's own). The thirteenth ItemStack component, the rows,
the wire, the ZID v5/ZMD v10 tails (the color read at the TOP level —
an unresolvable id still consumes it, the restructure the compile
caught). ItemTagRowsTest 9. THE VERSION-GATE DISCIPLINE again: the
MobDataStore v9 lists' reads now name their own version (the v10 bump's
audit — the unconditional read inside the v7 block would have misparsed
v7/v8 files; the same pattern as the tame-gate repair).

### Slice 97 — Wave J COMPLETE: the attribute modifiers (the item-tag
envelope's final arm)

- 3ea955e (J1+J2): the api record AttributeModifier (the Validate pair
  as the constructor's law; the whole-row identity — the reference's
  uuid-only equals is the live instance set's own), the FOURTEENTH
  ItemStack component (the per-call audit clean; a present-but-empty
  list stays present — the attribute-wipe, the reference's own
  contains(...,9) branch, never the every-other-arm empty-to-null
  convention), the ItemNbt rows (the fromNbt shape, the
  BOTH-uuid-arms-non-zero keep-gate — the plan's "LSB-zero skip" is
  really the conjunction), the SlotNbt wire, the ZID v6/ZMD v11 tails
  (ONE parseable-SNBT string row, the wrapped compound — the
  BlockEntityTag pattern; the v10 color gate renamed to its own version
  at the bump, the standing audit), Tools.heldDamageOf (computeValue's
  fold verbatim over the player's base 1.0: op-0 adds, the d1 snapshot,
  op-1 scaling the POST-op-0 d0, op-2's 1+amount multiplies, the
  0..2048 clamp; the tagless fall-back to the type table), all four
  damage sites switched. THE AUDIT'S CATCH: dropEquipment's pre-G1
  5-arg reconstruction had silently stripped every tag arm since Slice
  94 — the shave now rides withDamage alone.
- The tests (the same push): AttributeModifierTest 8, the store rows
  (ItemPersistenceTest 8, MobDataStoreTest 8), the LIVE flagship
  AttributeMeleeIntegrationTest (the /give dataTag feeding a 10.0 op-0
  sword — the victim's health re-sync reads 9; the zero-uuid wipe sword
  dealing the bare fist's 1 — 19, never the sword's defaults).
- The sweep: 128 core + 106 protocol green in the adjacent batches.

The reference facts this slice pinned (worth remembering):
- The player's ATTACK_DAMAGE base is 1.0 (PlayerEntity's own
  registration) — the fold's base is NOT the attribute's 2.0 default.
- The keep-gate is a CONJUNCTION: LSB != 0 AND MSB != 0 — either arm's
  zero drops the row.
- op-1 scales the POST-op-0 base (d0 after the adds), not the raw base.
- The vanilla sword compare (MobEntity.updateInventory) reads the TIER
  field alone — the engine's upgrade to the folded damage preserves the
  tagless ordering while making tagged swords comparable.
- The reference's tooltip adds the enchant damage to the
  ATTACK_DAMAGE_MODIFIER_UUID row (display-only — the client's own
  render; the engine models the data alone).

NEXT per the standing rows: (1) the skull's Owner profile + the
EnderChest contents-on-player angle (the no-BE tile families' remains);
(2) the shared container-open event (demand-named); (3) survey the
ledger for the next vanilla wave (the §28.7 adapter stands after the
1.8.8 polish).


### Slice 98 — Wave K COMPLETE: the skull's Owner profile

- 7d90dc5 (K1): the api record SkullProfile + SkullTile's fourth arm +
  TileNbt's rows (THE ROT RENAME — the reference's own key is Rot, the
  engine's "Rotation" never existed in vanilla) + the wire's Owner
  compound.
- 22d8709 (K2): the FIFTEENTH ItemStack component (the SkullOwner item
  row, the two-branch consult), the SlotNbt wire, the ZID v7/ZMD v12
  tails (the v6/v11 gates renamed at the bump), the placement consult
  (the damage-3 arm).
- Evidence: TileNbtTest 8, ItemTagRowsTest 10, ItemPersistenceTest 9,
  MobDataStoreTest 9, SlotNbtTest 11, WitherSkullTest 4,
  SkullSummonTest 5.
- The ledgered corners: the string/compound SkullOwner merge identity
  (vanilla's raw-tag compare never merges them; the engine's parsed
  model does) and the online texture fill (the session-service seam —
  an offline-mode vanilla server's own behavior).

NEXT per the standing rows: (1) the EnderChest angle's VERIFY + retire
(the ender slots ride the player's dat — PlayerNbt's rows already
model them; the tile carries no contents in the reference); (2) the
shared container-open event (demand-named); (3) the next vanilla wave
per the compatibility ledger (the §28.7 adapter stands after the 1.8.8
polish).

- The EnderChest angle VERIFIED + RETIRED live: the reference's
  EnderChestBlockEntity overrides no writeNbt (the tile carries
  id/x/y/z alone — the animation/viewer fields transient), and the
  contents ride the player's EnderItems, which PlayerNbt already
  writes 1:1. No work needed — the standing row retires.


### Slice L1 — the stairs' HALF bit (the metadata-variants slice's first arm)

- 546a90a: the fifty-two upside-down states over the reference's own
  fold ((HALF == TOP ? 4 : 0) | (5 - FACING.getId())) — the
  oak/cobblestone/sandstone/nether-brick/red-sandstone hand rows + the
  eight families' loops; the placement law (getPlacementState lines
  493-500: the underside click hangs the top form, the top face lays
  the bottom, the side faces split on the cursor's upper half — the
  cursor byte now threaded into placeStairsOnTick); the collision law
  (setBaseShapeForCollisions lines 74-81: the top form fills the
  cell's UPPER half — the 1.8.8 law where the base half box alone is
  the collision); the tables (behavior/light/sound — the woods'
  dig.wood rule repairing the five Slice-36 families that defaulted to
  dig.stone — the stats fold for the red sandstone tops).
- THE AUDIT'S CATCH: the sandstone stairs' four facing nibbles never
  registered — every sandstone stair carried meta 0 (the east form),
  so a real client rendered all four facings as east-ascending.
- Also: the slab law's cursorY >= 0.5 tightened to the reference's
  strict > 0.5 (BlockStep's dy > 0.5f gate).
- Evidence: StairsHalfBitTest 3, StairsHalfWireTest 2,
  BlockRegistryTest 832.

### Slice L2 — the chest pair's FACING states (the metadata-variants slice's second arm)

- 0c4d097: the ChestFacingBlocks hub (the plain 54's four + the
  trapped 146's four over the Direction-id nibble band 2-5 — the same
  band the ender chest shipped in Slice 29a), the placement walk
  (getPlacementState's look-opposite + onPlaced's neighbour re-facing
  walk lines 96-125 VERBATIM — the perpendicular same-kind neighbour
  adopts the new state so a pair's fronts match, and THE QUIRK: when
  the facing axis runs along the only neighbour the placement keeps
  the plain look-direction state), the setTileNbt consult threaded
  into the walk (the H2 BlockEntityTag arm), and the family consults
  everywhere (the open dispatch, the manager's spill, the hopper's
  container adapter, the trapped chest's redstone probe + the open's
  neighbor wake + the stats pair, the comparator's analog read, the
  collision box's pair rule, the flammable band, TileNbt's idOf
  family).
- THE AUDIT'S CATCH: the trapped chest's lid Block Action carried
  legacy 54 — the reference's doBlockEvent carries ChestBlock(1)'s
  own 146; the carrier now resolves the family.
- Evidence: ChestFacingTest 2 (the band, the consults, the placement's
  three arms over the live engine), BlockEntityTagPlacementTest's
  three chest arms, BlockRegistryTest 840.

### Slice L3 — the furnace pair's FACING states (the metadata-variants slice's third arm)

- 3049823: the FurnaceFacingBlocks hub (the plain 61's four + the lit
  62's four over the same Direction-id nibble band), the placement law
  (getPlacementState line 133's look-opposite + onPlaced lines 138-146's
  custom-name carry — the held stack's hover name rides the fresh tile,
  the anvil-named furnace's own GUI title), the lit swap keeping the
  FACING whole (updateLitState lines 108-125 — syncLitBlock now
  consults the family's partner state), and the family consults
  everywhere (the open dispatch, the spill, the hopper's container
  adapter, the comparator's analog read, the note block's STONE
  material band, TileNbt's idOf family, the light/behavior/sound
  tables, the stats fold).
- THE AUDIT'S CATCHES: (1) the family's own prefix trap — the lit
  stem shares the furnace_ prefix, so isPlainFamily excludes it
  explicitly (the chest pair's trapped_chest_ prefix never overlapped)
  and partnerOf gained the same-form null guard the bare forms always
  had; (2) the comparator's analog reader matched the bare ids alone —
  every post-L3 furnace (a facing state) read as not-a-source; (3) the
  stats fold was half-done in-flight (the lit states folded, the plain
  states did not) — the whole family folds onto the furnace row, and
  the L2 chest pair's states fold with it (the chest family shipped
  without any fold — mining a facing-state chest counted on a key the
  1.8.8 client drops).
- Evidence: FurnaceFacingTest 2 (the band, the family consults + the
  prefix trap + the same-form guard, the live placement's
  look-opposite + the name carry + the natural ignition's lit swap
  keeping the facing), StatsTest's fold rows, BlockRegistryTest 848.

NEXT per the standing rows: (1) the stats-completeness slice (the L3
audit's catch generalizes: every registered state whose id is not a
1.8.8 registry name counts mining on a key the client drops — the
stairs/doors/slabs/skulls/pots/plates/rails families fold onto their
registry rows; the pinned oak_stairs_east row flips with it); (2) the
SEAMLESS skins (the double_stone_slab2 181 + the stone 43 family's
bit 8 — register-only arms); (3) the double-plant's
canBeReplaced-per-variant refinement; (4) the shared container-open
event (demand-named).


### Slice L4 — the stats-completeness fold (Wave L's fourth arm, BOTH registries)

- 19391d8 (L4a, the BLOCK arm): the 197-entry block registry oracle
  (reference Block.java's registrations, ids 1-197 in order), the
  BLOCK_NAMES divergence table SEPARATE from the item path's
  VANILLA_NAMES (the melon collision — block 103 melon_block vs item
  362 melon), the pure state fold (blockMerged's suffix-strip), and
  the RETIRED merge table (vanilla keeps its own row for every
  registry Block — the grass on grass, the farmland, the doubles, the
  lit/powered forms, the flowing fluids, the redstone torch).
- 157bf4f (L4b, the ITEM arm): the 187-entry item registry oracle
  (reference Item.java, ids 256-2267) over the UNION row set, the item
  completeness walk, and THE STRIP'S TRAPS: 67 is stone_stairs (the
  strip had folded the engine's cobblestone_stairs onto the COBBLESTONE
  row — the wrong family), the 44-family's slab variants carried the
  same trap (sandstone_slab onto sandstone, cobblestone_slab onto
  cobblestone, stone_brick_slab onto STONE), all fixed explicitly onto
  stone_slab.
- The evidence walks: StatsCompletenessTest 5 — every registered
  block's mine key resolves into the 197-name set, every registered
  item's use key into the union; the tests ARE the survey (a new state
  family without its fold fails there rather than counting on a key
  the 1.8.8 client drops).
- The registry spellings the survey pinned: 98 stonebrick (one word),
  144 skull (mob_head is the 1.9 flattening's), 67 stone_stairs,
  165 slime, 395 map / 358 filled_map, 382 speckled_melon, 351 dye
  (the lapis and bone meal ride the damage band), 112 nether_brick
  (singular), the five 1.7 woods' slabs have no registry ids (126/125
  alone), 140 flower_pot's prefix rule, the white stained clay as the
  dye loop's sixteenth member.
- The audit tooling: scripts/l4_survey.py (the resolvability survey),
  scripts/l4_audit.py (the legacy-id cross-check — every engine
  block's resolved row must carry its own true legacy id; the metadata
  nibbles read as false mismatches, the legacy-id column is the
  truth).

NEXT per the standing rows: (1) L5 — the SEAMLESS skins (the
double_stone_slab2 181 + the stone 43 family's bit 8 — register-only
arms); (2) L6 — the double-plant's canBeReplaced-per-variant; (3) the
shared container-open event (demand-named); (4) a broader regression
batch over the stat-adjacent suites (the full suite in the established
seven-batch pattern).


### Slice L5 — the stone-slab family's doubles + the SEAMLESS skins

- a7ddcbd: the three variant doubles (double_sandstone_slab 43|1,
  double_cobblestone_slab 43|3, double_stone_brick_slab 43|5 — the
  pairs had merged into the PARENT BLOCKS, the pre-52 stand-in), the
  two register-only seamless skins (43|8 + 181|8), the missing
  stone_brick_slab_top, and the drop-law repairs (the 43 double
  dropped ITSELF; the 181 double dropped ONE — vanilla yields TWO
  singles through getDropItem + getBaseDropCount's isDouble ? 2 : 1).
- THE AUDIT'S TWO DISPATCH CATCHES: the slab2 family arm sat BELOW the
  stone_slab startsWith in slabFamilyOf (dead code — every
  red-sandstone pair resolved the STONE family), AND the dispatch's
  stone_slab2 check compared the BARE name while heldId carries the
  namespace with endsWith(_slab) missing the slab2 suffix (the family
  never reached the slab path at all — the pairs stacked singles
  forever). BOTH repaired and driven by the live test.
- Evidence: DoubleStoneSlabFamilyTest 2 (the band + the repairs + the
  live merges), SlabStairsAcceptanceTest's flipped pin,
  DoubleWoodenSlabTest's L4-consistent rows, BlockRegistryTest 854.

NEXT per the standing rows: (1) L6 — the double-plant's
canBeReplaced-per-variant (the grass/fern walk-through refinement) +
the upper's per-facing wire band 8..11 (only if a future arm needs
it); (2) the shared container-open event (demand-named); (3) a broader
regression batch (the full suite in the established seven-batch
pattern).

### Slice Arch-30 — the modern interactive terminal console (§30, ADR-21)

- 0afb2fe (the foundation): JLine 3.26.3 pinned centrally with ONE
  provider (Jansi — gradle/libs.versions.toml), the launcher console
  package (TorchConsole, TerminalLogBridge, ConsoleCommands,
  CommandCompleter, FilteredHistory, AnsiSanitizer, ConsoleConfig,
  LogFileFormatter, EngineConsoleTarget), CommandService.commandNames()
  (the synchronized immutable snapshot, zamin-core), and the
  LauncherRuntime rewiring (console.properties loaded, the file sink on
  the source-bearing ESC-free formatter).
- 11731c2 (the 30.4 test list): 47 launcher tests across 9 classes +
  verifyPackagedConsole (the fat jar must carry JLine + the Jansi
  natives — 15 libraries verified, wired into check).
- THE EMPIRICAL AUDIT'S CATCHES (all pinned in ADR-21): (1) JLine's
  DefaultHistory attaches on the FIRST readLine, not at build — the
  explicit attach now loads previous sessions at boot, makes save()
  work before any input, and turns the HISTORY_SIZE bound live (it had
  silently been the 500 default); (2) terminal.close() does NOT unblock
  a pending dumb read — requestShutdown also interrupts the loop thread,
  and the UserInterruptException handler distinguishes its own interrupt
  (flag already set: just end the loop) from a real Ctrl+C (converge on
  the idempotent stop); (3) a throwing engine route is caught in
  ConsoleCommands' default arm (the reading loop previously died with
  the uncaught exception); (4) §c is LIGHT red SGR 91 — the palette
  tests pinned to the true mapping; (5) JLine's completion unit is the
  WHOLE current word regardless of cursor position — kept as JLine's
  native convention, documented in the test.
- The contracts the tests pin: the ONE command path (stop/shutdown/save/
  state built-ins + the existing consoleCommand route — the real-server
  integration persists a console ban end to end), log records print
  ABOVE a partially typed line with the line surviving, EOF ends the
  console but NEVER the server, the terminal threshold hides from the
  screen only (the file sink keeps every record, ANSI-free), the
  bounded queue drops-and-counts loudly without ever blocking a logging
  thread, sensitive commands never enter history (the restart round
  trip, the bound, the unreadable-file degradation), completion answers
  the first word from the immutable snapshot (argument positions answer
  nothing, honestly), and both the JLine and plain loops ride the same
  dispatcher.

NEXT per the standing rows: (1) §31's immediate boundary items (the
format-identity markers, the session lock, the no-implicit-conversion
guard) — the modern backend itself stays future-gated; (2) Wave L6
(the double-plant's canBeReplaced-per-variant); (3) the stairs'
two-box geometry on the physics-solver slice.

### Slice Arch-31-immediate + Wave L6 — the storage boundary + the double-plant's per-variant law

- Arch-31 (the immediate items §31.4/§31.6; the modern 26.1 backend stays
  future-gated by §31.4's own decision): WorldSessionLock — the OS FileLock
  over world/session.lock, held for the run, released at shutdown; the
  crashed-process lock evaporates with the process (no stale-marker story
  AT ALL — the deliberate answer to §31.6's "never rely on presence or
  timestamp"), and a live second writer (cross-process, or the same JVM's
  OverlappingFileLockException) is refused loudly before any store is
  touched. StorageManifest — world/data/storage.properties: the format
  family (zamin-delta) + the layout generation, independent of the game
  version; first boot writes it, every boot verifies, a different family
  (a Vanilla tree pointed at Torch, say) or a NEWER layout refuses to
  touch the world — the no-implicit-conversion guard's spine. Wired into
  EngineServer's boot (before any storage load) and shutdown (the
  release). StorageBoundaryTest 8.
- Wave L6 (the double-plant's canBeReplaced-per-variant,
  DoublePlantBlock lines 60-68 1:1): DoublePlantBlocks.isReplaceableVariant
  (only the GRASS and FERN variants, both halves — the engine's flat
  identifiers carry what the reference resolves virtually through the
  lower), WorldSolidity.isReplaceable's family arm, and THE OCCUPIED-GATE
  SWEEP — the audit's catch: the generic placement had NEVER accepted
  replaceable flora (BlockInteractionService's creative + survival gates
  were air-only, and all 30 of EngineServer's family gates the same), so
  a stone clicked into a tall grass silently failed instead of replacing
  it. The gates now accept air OR replaceable flora
  (World.canBlockBePlaced over canBeReplaced); the replaced flora drops
  nothing and the orphaned upper pops through the update system's own
  support arm. The family stays walk-through as a WHOLE (isFlora is the
  solidity read; replaceability and walk-through are two laws).
  DoublePlantReplaceableAcceptanceTest 3 (the law over all twelve states,
  the live grass replace + upper pop, the fern upper replace through the
  identifier's variant, the sunflower refusal).

NEXT per the standing rows: (1) the stairs' true two-box collision
geometry (the physics-solver slice); (2) the §30 console's per-command
argument completion (demand-named); (3) the §31 modern backend (the
codecs + the migration contract, milestone-gated).

### Slice — the adventure place gate threaded at the dispatch (a pre-existing regression repaired)

- THE CATCH: the L6 sweep's broad rerun surfaced AdventureGatesTest 2/4
  failing — at 26851c4 (the session-start commit), BEFORE any of today's
  work. The bisect: Slice 96 landed the adventure CanPlaceOn consult at
  the generic survival fallback when the CHEST still rode it; Slice L2's
  placeChestFacingOnTick then gave the chest (and every other family
  item) its own arm ABOVE the fallback — silently routing ~30 placement
  arms around the consult. An adventure player holding a chest placed it
  straight through the refusal; the L2 sweep never reran the interaction
  package, so the hole sat undetected.
- THE FIX: the consult threaded ONCE at the dispatch's placement
  transition (after the last USE arm — the container opens, the bucket,
  the bottle, the flint — the reference's own order; before the
  reed/cactus and every placement arm below), through
  BlockInteractionService.canUseItemOnForPlacement (the public
  dispatch-level surface over the Wave-I consult).
- The stale assertion repaired with it: the L2 chest placement lands the
  LOOK-FACING state (chest_north/...), never the bare 54 constant — the
  test now reads the plain family, matching the 1:1 law.
- The full sweep after: every root-package class A-Z green, the
  subpackage batches green, api/protocol/launcher green; the three
  load-flakes (VehicleWorldDamage, GameRule mobGriefing, LifecycleEvent
  death-scatter, ScoreboardIntegration health-hearts) each verified
  green in isolation on this 2-core host.

### Slice — the stairs' true two-box ascending collision geometry (the §29 ledger's last unchecked stair row, LANDED)

- THE LAW (StairsBlock.addCollisions 1:1): the base half slab
  (setBaseShapeForCollisions) ALWAYS, plus the tall back on the FACING
  side (isInnerStair) through the opposite Y band — the bottom form's
  back rising 0.5→1.0, the TOP form's back descending 0→0.5 on the
  facing side — with the reference's own corner resolution: the outer
  corner narrows the back to the quarter cell (the facing neighbour's
  perpendicular same-half stair, the isStairSameHalfSameFacing blocker
  read), the inner corner adds the third step quarter
  (setStepShapeForCollisions, the behind neighbour's perpendicular
  stair).
- THE VIRTUAL SHAPE, resolved engine-side: the corner forms are never
  stored (the wire carries facing+half alone — the reference's own
  resolveVirtualProperties fold); CollisionShapes.stairBoxes resolves
  them at read time through the NeighborProbe, parsing any family's
  flat stair states (StairsBlocks.java: the thirteen families' eight
  states each, the facing segment + the _top HALF bit; the bare item
  forms never states; sameHalfSameFacing the reference's own
  any-family instanceof law).
- THE STALE L1 PIN RETIRED: the "tall half's body carries no collision"
  comment in the old single-slab arm was the deferral's own stand-in
  phrasing, not the reference's law — StairsHalfBitTest's underside
  assertion now reads the descended back on the facing side (the low
  side stays open below a top stair), the pins that survive the
  centre-column scan untouched.
- Evidence: StairsCollisionGeometryTest 9 — the straight two-box law
  over all four facings and both halves (the support ladder 0.5 on the
  low side / 1.0 on the tall), the outer-corner narrowing on two
  mirrored arms (east + south), the same-same blocker, the half
  mismatch, the inner-corner third box, the non-stair neighbours, the
  parse law over all thirteen families, and the LIVE dropped-item arm
  (an emerald resting on the tall back's ceiling 65.125 beside a
  diamond on the base slab's half 64.625, through the engine's own
  shape-aware ground); CollisionShapeTest 19 and SlabStairsAcceptance
  green with no changes; the focused sweep + the full suite rerun
  (full-suite-L7.log).
- The swept-box BODY model (a body straddling the bands from the cell
  above) still rides the physics-solver slice — the point queries keep
  the legacy convention exactly like the fence's 1.5 bands.

NEXT per the standing rows: (1) the §22 vanilla continuation — the
engineering ledger's own order: potions, leads, structures, natural
spawn cycles; (2) the §30 console's per-command argument completion
(demand-named); (3) the §31 modern backend (the codecs + the migration
contract, milestone-gated).

## Slice M1+M2 — the physics-solver foundation (Wave M opens)

- **M1 — the geometry + the gather**: the new `server/physics` package.
  `Aabb` — the 1:1 port of the reference's `util/math/Box.java` in
  ABSOLUTE world coordinates: `expanded` (the swept-motion face law —
  a negative delta grows the min face, a positive the max), `grown`/
  `contract`/`moved`/`union`, `intersects` (the reference's own
  half-open convention — touching faces never intersect), and the
  clipping trio `intersectX/intersectY/intersectZ` (lines 104-162
  verbatim: the box clips the axis limit to its own face distance ONLY
  when the two boxes overlap on the other two axes — a fall that never
  reaches the floor passes untouched, a fall that would pass through
  clips to exactly the ceiling). `EntityCollisionWorld` — the
  block-read surface (blockAt + the chunkLoaded gate + the
  borderContains consult; the caller owns the dimension context, the
  reference's own Entity.world law). `EntityCollisions` — the 1:1 port
  of `World.getCollisions(Entity, Box)` lines 897-940's BLOCK side:
  the cell band `floor(minX)..floor(maxX+1)` with the Y band starting
  ONE cell below (the reference's own `k - 1` — the 1.5-tall families
  poke through their ceiling so the cell below the region still
  contributes), the chunk gate (an unloaded column contributes
  nothing), the border law (a cell outside the world border reads as
  STONE for a body INSIDE the border — the reference's own flag/flag1
  walk — and real for a body already outside), and per cell the
  block's own `addCollisions` through the SAME `CollisionShapes`
  table the point queries read (`cellBoxes` — the new public view;
  the connection families resolve their arms against the cell's own
  neighbour probe, so the swept-box model and the point model read
  ONE law), each box offset into world coordinates and added only
  when it intersects the swept region.
- **M2 — the move itself**: `EntityMoveSolver` — the 1:1 port of
  `Entity.move` lines 390-585, the BODY model the ledger deferred from
  the collision-shape slice. The `Body` (the feet-centre x/y/z + the
  width/height the shape derives from, the reference's `setPosition`
  line 246 law, with `setPositionFromShape` recovering the position
  from the moved shape exactly like line 586's own tail); the
  `MoveLaw` (the per-family fields distilled: stepHeight — 0.5 the
  walkers, 0.0 the item bodies and falling blocks, 1.0 the horses;
  the sneaking edge-guard gate; noClip; inCobweb); the `Result` (the
  clipped deltas + collidingHorizontally/Vertically + onGround — the
  downward clip only, exactly line 563 — + the velocity-zeroing
  signals: the X clip zeroes velocityX, the Z clip velocityZ, the
  vertical clip velocityY through the reference's own
  `beforeCollision` default, + the cobweb zero). THE WALK ITSELF:
  the cobweb drag (0.25/0.05/0.25), the sneaking edge-guard (the
  0.05 shaved walk that never leaves the support — X alone, then Z,
  then the pair, lines 409-446 verbatim), the gather over the shape
  expanded by the motion, the Y clip then X then Z (each box in walk
  order), and THE STEP-HEIGHT RETRY (lines 469-545 verbatim — the
  two-pass up-and-over walk at the full intended motion, the
  squared-distance comparison d20 vs d10, the descending second pass,
  the keep-only-if-better comparison against the clipped walk). Pure
  geometry: no entity state crosses in or out — the caller owns the
  velocities, the fall distance, and the family side effects, exactly
  the reference's own Entity/LivingEntity split.
- THE TEST-SIDE DISCOVERIES the physics drove (all in the tests, the
  solver needed no repair after the first port): (1) the clip's own
  direction — the reference calls `collisionBox.intersectY(moverShape,
  limit)`, the collision box the receiver; (2) the grounded walker's
  convention — `LivingEntity.travel` runs the move BEFORE the gravity,
  so a standing body enters `move()` with the previous tick's small
  negative drift (-0.0784*0.98), and WITHOUT it `onGround` reads false
  on a flat walk (d4 = 0 never satisfies `d4 < 0.0`); (3) the stair
  climb takes TWO ticks at step height 0.5 (the first tick steps onto
  the base slab and clips at the tall back's near face — 12.5 - 0.3
  - 10.7 = 1.5 on the pin — the second clears the back), exactly the
  reference's own per-tick walk, NOT the one-tight smooth climb;
  (4) the sneaking guard shaves in 0.05 steps from the REQUESTED
  motion, landing at 0.95 for a 1.5 walk off a single cell.
- Evidence: PhysicsSolverGeometryTest 13 (the expanded/intersects
  laws, the clip trio over both directions and the no-reach pass, the
  full-cube/slab/fence-band gather in world coordinates, the
  one-below start the tall families need, the unloaded-column skip,
  the border STONE wall + the outside-body real read, the
  non-intersecting box gate) + EntityMoveSolverTest 13 (the
  through-fall clip + onGround + zeroVy, the upward clip never
  grounding, the wall walk's exact face + the diagonal's per-axis
  pair, the step onto the half slab keeping the full 2.0 walk, the
  stepless clip, the retry never climbing a full wall, THE FENCE
  LANDING FROM THE CELL ABOVE — the ledger's own named case, feet
  6.7 falling 1.2 rest at 6.5 exactly — the short body under the top
  slab, the two-tick stair ascent, the cobweb drag, the no-clip
  pass-through, the sneaking shave) — 26 NEW. Regression sweep:
  block 129 + entity 366 + world/structure/spawn 137 + root batch A
  42 — 700 tests, 0 failures (full-suite-M1.log).
- The rewiring (M3: the MobEntity families onto the solver; M4: the
  items, the falling blocks, the vehicles) rides the NEXT units —
  this slice is the verified foundation, the families' own point
  queries untouched (the 366 green prove it).

## Slice M3 — the mob families onto the solver

- **THE CAPABILITY**: `MobEntity.WorldQuery.collisionWorld()` — the
  swept-box surface as an OPTIONAL world capability (the default null:
  a stub world without block reads keeps the legacy point-query walk —
  the 366 existing entity tests untouched BY CONSTRUCTION). The live
  server's `MobWorldQuery` overrides it: one `collisionSurface` built
  once, the block reads resolving the ACTIVE dimension at call time
  (the per-body flip drives them — the reference's own Entity.world
  law), the chunk gate through the world's own loaded-column read, and
  the border consult on the engine's one border (the reference's
  worlds[0] border the other dimensions derive from).
- **THE CONVERSIONS**: the shared `MobEntity.solverMove(...)` — the
  Body from the position + the type's own width/height, the solver's
  outcome applied (position, onGround, the velocity-zeroing signals
  X/Z/Y), returning the Result for the family arms:
  (1) `tickBody`'s walk — the main walker: the solver's clip replaces
  the destination-cell stop, the detour armed on the solver's own
  horizontal-clip signal, the fall law (Slice 92) riding the ACTUAL
  clipped delta;
  (2) the ridden body (the pig 0.5 / the horse's 1.0 step — the
  reference's EntityHorse override);
  (3) `integrateFlight` — the ghast's per-axis stand-in RETIRED (its
  own comment named it): the 4-tall body now clips against every box
  its swept shape touches;
  (4) the guardian's water arm (the seabed rest through the solver's
  downward clip) and the land flop (the rise clips at the ceiling).
- **THE STANDING DRIFT (the slice's own 1:1 repair)**: the reference's
  LivingEntity.travel applies the gravity AFTER the move — a standing
  body ENTERS move() carrying the previous tick's
  `(0 - 0.08) * 0.98 = -0.0784`, the cycle the vertical clip's
  zeroing re-arms every tick. The engine's gravity-BEFORE-move order
  had zeroed the grounded vertical (`velocityY = 0.0`), and a zero dy
  never turns the ground on (`d4 < 0.0` fails) — the drift restores
  the reference's cycle for the solver path (the legacy walk keeps
  its own zeroing, its tests pinned to it).
- **THE GAMERULE FLAKE CLOSED (the diagnosis the sweep forced)**: the
  mobGriefing test's second stage walked the second creeper through
  the first blast's RANDOM crater to a player FLOATING 4 blocks above
  a 3-deep pit — the prime gate's vertical 2.0 never engages, the
  creeper stuck at the crater floor in CHASE forever. BOTH walk
  models susceptible (the baseline trace caught the legacy walk
  freezing a body inside a solid cell with the velocity accumulating
  — the stand-in's own trap); the solver merely raised the rate (the
  box hangs further over edges than the centre point, the descent
  into the deep floor likelier). THE TEST REPAIR: the rule stage now
  plays on INTACT ground (the player moved past the crater, the fresh
  sample beside, the creeper spawned within the prime range) — the
  stage's subject is the BLAST's block preservation, not the approach
  walk. Stable across 6 consecutive runs (the same flake profile
  would have hit the baseline too).
- Evidence: MobSolverWalkTest 7 NEW (the grounded walk keeps the
  floor; the wall stops the body at its FACE — 7.0 - 0.45 exactly,
  never the full stop; the half slab mounts in ONE step at 64.5
  keeping the eastward motion; the full block refuses the 0.5 step;
  THE LEDGER'S NAMED CASE through the live tickBody — the fence's 1.5
  band catches a body from the cell above; the four-block fall wounds
  1.0 through the clipped delta; the guardian's seabed rest) +
  PhysicsSolverGeometryTest 13 + EntityMoveSolverTest 13 green
  unchanged; the sweep entity 373 + physics 26 + root 60 + GameRule
  11 = 470 green (full-suite-M3.log; the VehicleWorldDamageTest
  one-off documented — terrain-random, green 3/3 isolated on both
  trees).
- M4 (the ItemEntity + the FallingBlockEntity + the vehicles) rides
  the next slice.

## Slice M4a — the item + falling-block families onto the solver

- **THE SHARED CAPABILITY**: `physics.HasCollisionWorld` — the
  optional collision-surface interface (the default-null
  `collisionWorld()`) now the ONE declaration the three query
  families extend (`MobEntity.WorldQuery`, `ItemEntity.Ground`,
  `FallingBlockEntity.Ground`) — a class implementing several (the
  live `ShapeGround`, the test stubs) never meets the diamond. The
  live ShapeGround carries one `collisionSurface` (the overworld
  reads + the chunk gate + the border consult — the item/falling
  families' ground reads the overworld alone, the manager's own
  dimension blindness, ledgered).
- **THE ITEM BODIES**: ItemEntity.tick's application — the swept-box
  branch (the 0.25 box through the solver, the engine's centre
  convention translated to the feet convention at the boundary), the
  leading-face sample stand-in RETIRED for the worlds that provide
  the surface, the standing drift carried (the same gravity-after-
  move cycle the walkers took — without it the ground flag would
  flicker every other tick). The stub grounds keep the leading-face
  fold untouched.
- **THE FALLING BLOCKS**: the 0.98 box's own Y clip — the box
  FOOTPRINT catches the slab's edge and the funnel's rim the centre
  point missed (the vanilla FallingBlockEntity's own move) — the
  landing fold retired on the same capability gate.
- **THE WALL-SLIDE TEST'S OWN ARTIFACT CLOSED (the diagnosis)**: the
  ItemBoxPhysics wall-slide test pinned the OLD model's freeze — the
  baseline trace caught the item stopped mid-slide at (4.737, 4.178)
  ON EMPTY GROUND (the leading-face stand-in's own bug, the same
  family as the legacy mob freeze the GameRule diagnosis caught); the
  historical 1-cell pillar was NEVER touched by either model's
  trajectory (the (0.4, 0.3) throw clears its south corner before
  the box reaches the face). THE REPAIR: the wall now spans z 2..10 —
  the diagonal cannot out-run it, the X axis clips at the face
  (4.875) and the slide runs ALONG it, the test's actual intent.
- Evidence: ItemSolverPhysicsTest 7 NEW (the item's box resting on
  the SLAB'S EDGE at 64.5 — the centre over the empty cell, the box's
  overlap carrying the landing; the wall's exact face 6.875 with the
  velocity zeroed and the second tick held; the fence's 1.5 band
  catching the item from above; the falling block's 0.98 box on the
  slab's edge AND its clean miss past the cell; the fence-band
  landing; the legacy point fold pinned on its own convention) —
  plus the sweep 426 green (full-suite-M4a.log).
- M4b (the vehicles: the carts' and boats' own query + the ridden
  bodies) rides the next slice.

## Slice M4b — the vehicles onto the solver

- **THE CAPABILITY SPREAD**: `VehicleEntity.WorldQuery` extends
  `HasCollisionWorld` (the shared optional surface); the live
  `VehicleWorldQuery` carries its own `collisionSurface` (the
  overworld reads, the chunk gate, the border consult — the same
  one-surface shape every family carries now).
- **THE SHARED `solverMove`** on VehicleEntity (the centre
  convention translated at the boundary, the zeroing signals landing
  on the velocities through the setters).
- **THE CONVERSIONS**: (1) the boat's tick — the 1.5-wide hull (the
  reference's own setSize(1.5, 0.6) width; the height keeps the
  engine's 0.4 the resting rows pin) clipped axis by axis, the
  shore's exact face never the point refusal, the steer impulse
  folded into the passed motion; (2) the minecart's OFF-RAIL tail —
  the 0.98 box (the reference's own setSize(0.98, 0.7)) with the
  landing the box's own footprint. THE ON-RAIL WALK STAYS: the rail
  physics is its own cell-snapped law (the reference's own rail
  grid), not a swept-box concern.
- Evidence: the sweep 426 green (entity+vehicle+physics 406 + the
  live vehicle/combat batch 20, full-suite-M4b.log).

## Wave M closed

- The physics-solver wave complete across every family: the
  foundation (M1+M2), the mobs (M3), the items + falling blocks
  (M4a), the vehicles (M4b). The ledger's §29 Wave M section carries
  the full record; the swept-box BODY model is the moving bodies' own
  law, the point queries the standers' and the readers'.

## Slice M4b-fix — the pre-move overlap lift

- **THE REGRESSION the protocol module caught**: the
  VehicleIntegrationTest's rail ride timed out waiting for the cart's
  teleports — the DIAG walk found the FIRECART test's plain cart
  SUNK to y=3.35 inside the dirt, directly under the rail test's new
  cart; the player's interact mounted the sunken body and the steers
  drove a buried cart.
- **THE ROOT CAUSE**: the engine's cart spawns place the CENTRE at
  the rail's ride offset (cellY + 0.06) or a fire test's flame cell
  — the box's feet 0.29 INTO the cell below, an overlap the
  reference never creates (vanilla spawns bodies clear of the
  ground) and the clip law cannot resolve (a box already inside
  another's span clips on neither axis — the Y clip requires the
  mover entirely above). The legacy landing had answered the same
  overlap with its snap-UP ({@code floor(feet) + 1.0 + half}); the
  solver's first port let the body sink forever.
- **THE FIX**: the pre-move overlap lift in
  `VehicleEntity.solverMove` — the boxes overlapping the body's
  CURRENT shape gathered before the move, the feet raised to the
  highest overlapped top (the same raise the legacy landing
  performed), then the solver move. A spawned-in-ground body stands
  where the legacy walk stood it — never sinks — and a properly
  resting body (feet exactly at a top, the half-open convention)
  lifts nothing.
- Evidence: the protocol module FULL 108 green (the rail ride again)
  + the vehicle/physics packages 81 green (full-suite-M4b-fix.log).

## Slice P6-lane — the first parallel gameplay execution lane

- **THE LANE**: the random-tick walk's compute-then-apply split — the
  in-range chunks group by their region, each region's pick
  COMPUTATION rides as one job on the `DomainWorkerPool` (pure,
  parallel), the applies run IN THE STABLE REGION ORDER on the tick
  lane (§6.2's one authoritative writer — the compute side never
  touches world state).
- **THE DERIVED STREAMS** (`RandomTickPicks`): per-(tick, region)
  deterministic — `streamFor(baseSeed, tick*P1, regionId*P2)` — so the
  sequential walk and the pool dispatch produce IDENTICAL picks by
  construction (the equivalence is a property of the stream, not a
  hope about scheduling).
- **THE TIME AXIS DISCOVERY**: the derivation reads the system's OWN
  `laneTick` counter, NOT `world.totalTicks()` — the direct-driven
  tests (RandomTickSystemTest's statistical guard loops) call `tick()`
  without the ticker, and a frozen time axis would freeze the derived
  stream with it: the same picks every call. The live wiring advances
  both counters together (one `tick()` per world tick).
- **THE OBSERVABILITY**: `parallelDispatchTicks()` — the count of
  walks that actually rode the pool; the test asserts it is > 0 armed
  and == 0 unarmed (never a silent sequential fallback).
- **THE GATE**: `torch.parallel-region-ticks` at boot (default OFF) +
  the multi-region gate `regions.size() > 1` (the whole-world default
  region size keeps every walk sequential until an operator configures
  a real split).
- **THE LOCKSTEP TEST DESIGN**: two identically-built worlds (same
  generator/seed, same light engine, same 2-chunk partition, same
  chunk generation order, same seeded apply randomness) driven
  tick-by-tick — one sequential, one on the pool — compared for EXACT
  state equality while BOTH differ from their initial snapshot. The
  earlier two-boot design could never work: two server boots can never
  align their absolute tick numbers, and the streams are per-tick.
- Evidence: `RandomTickLaneTest` 5 green (the lockstep equivalence,
  the live boot wiring through the real tick loop, the stream
  contract, the pick shape, the pool dispatch) +
  `RandomTickSystemTest` 3 green unchanged; the direct sweep
  block/world/concurrent **330 green**; the broad
  entity/physics/piston/scheduler/vehicle sweep green; the protocol
  module 108 (one documented load flake — DonkeyChest, green in
  isolation) (sweep-P6lane-*.log).

**Next:** the §29 ledger's standing rows and the Phase-5 workload
adooptions (each needs its own immutable-input contract and
equivalence proof), the phase-gated items (Bukkit adapter, Anvil
import, modern-protocol module).

## Slice N1 — the two-stage use law + the sneaking use-bypass

- **THE LAW** (ServerPlayerInteractionManager.useBlock lines 259-296,
  1:1): the BLOCK's use arm runs first
  (`if (!player.isSneaking() || getDisplayItemInHand() == null)`) and
  when it consumes the click the held item's useOn NEVER runs; the
  SNEAKING USE-BYPASS skips the whole block stage for a sneaking player
  with an item in hand; an empty hand keeps the block stage for
  everyone and the item stage for no-one.
- **THE REFACTOR**: `useItemOnBlockOnTick` split into the dispatcher +
  `useBlockArmOnTick` (stage 1) + `useItemArmOnTick` (stage 2) — every
  block arm (the containers, the noteblock, the jukebox eject, the
  daylight invert, the cauldron, the repeater/comparator/lever/button,
  the cake, the dragon egg, the flower pot, the bed, the
  door/gate/trapdoor) returns true when it consumes; the item arms
  (bucket, bottle, disc, lead, fireworks, flint, the adventure gate,
  every placement family) are mutually exclusive by held item exactly
  like the reference's one-useOn dispatch.
- **THE ORDERING REPAIRS**: the old single chain ran the bottle and
  fireworks item arms BEFORE the late block arms — a bottle's
  early-return ATE clicks the reference answers with the BLOCK's use:
  a door swung for a bottle in vanilla and stood still in the engine,
  the repeater's delay never cycled for a held bottle, a firework
  launched through a closed door. The block stage now precedes every
  item arm.
- **TEST-SIDE DISCOVERIES** (ledgered): the door pair commits in ONE
  tick submit (a lone lower pops in the redstone neighbor arm — the
  double-plant pair lesson again); the trapdoor's mount wall gate (the
  plain north-facing form's wall at z+1); the ore's fall-through (use
  answers super.use — false — so the item stage still runs after the
  ore lights).
- Evidence: TwoStageUseLawAcceptanceTest 8 green (the bypass holds the
  chest shut + places, the empty-hand exception, the crafting table's
  consumed click, the door swings for the bottle AND the fireworks +
  stands still for the sneaked stone, the repeater cycles for the
  bottle, the ore lights only unsneaked, the noteblock tunes only
  unsneaked, the trapdoor swings for the bottle) + the 32-class
  interaction sweep green — every useItemOnBlock-driving test class
  (sweep-N1-batch1/batch2.log).

**Next:** the §29 ledger's standing rows; the remaining Phase-5
workload adoptions (pathfinding, lighting, serialization); the
useItemOnBlock 64-block reach gate + spawn protection + world-border
consult (the reference's handler-level gates the engine models through
its own reach rules — an audit item).

## Slice N2 — the use handler's own gates

- **THE 8-BLOCK BODY GATE** (handlePlayerUse lines 463-472, 1:1):
  `squaredDistanceTo(x+0.5, y+0.5, z+0.5) < 64.0` measured from the
  BODY's feet (Entity.squaredDistanceTo lines 937-942 — never the
  eyes). A use beyond eight blocks answers NOTHING — both stages sit
  inside the gate exactly like the reference's
  interactionManager.useBlock call (the container stays shut at a
  distance; the old engine opened chests from 100 blocks away).
- **THE WORLD-BORDER CONSULT**: `getWorldBorder().contains(blockpos)` —
  the shared dynamic border the /worldborder commands move; the default
  6.0E7 size is the reference's own unconfigured infinity.
- **THE DEGENERATE CONSULTS ledgered**: the spawn-protection gate
  (DedicatedServer.isSpawnProtected's `getOps().isEmpty() -> false` —
  nothing to wire until the ops model lands) and the `teleported` flag
  (the anti-use-while-teleporting gate rides the teleport slice).
- **THE GATE'S OWN CATCH — the reed/cactus family's real bug**: the old
  tests clicked from 60+ blocks away (the spawn-to-y64 gulf) and PASSED
  FOR THE WRONG REASON — the refused reed arm returned false and fell
  through to the GENERIC placement, whose own distance gate alone
  refused the plant. The moment a player stood near, a reed PLANTED ON
  DRY SOIL through the fall-through. THE FIX
  (SugarCaneBlock.canBePlaced lines 52-68 + CactusBlock.canSurvive
  lines 85-94, 1:1): the family's refusal CONSUMES the use (nothing
  places, nothing spends — the click never reaches the generic path),
  and the cane's STACK case answers TRUE outright (a cane below a cane
  — the old arm wrongly demanded water beside the stack's soil).
- **TEST GEOMETRY REPAIRS** (the tests moved, never the law): six
  acceptance files' players now stand near their click targets
  (CaneAndCactus, EnderChest, Farming, Fire, HopperWindow, LitFurnace).
- Evidence: TwoStageUseLawAcceptanceTest 9 (the new beyond-eight-blocks
  arm: the chest stays shut at 20 blocks, opens at 1) + the six
  geometry-repaired tests + the 24-class interaction sweep green
  (sweep-N2-fixed2/full.log).

**Next:** the anti-desync resync pair (the handler's BlockUpdate
re-sends after every use — a protocol-layer slice), the ops model (the
spawn-protection gate's dependency), the remaining Phase-5 workload
adoptions.

## Slice N3 — the use packet's tail: the too-high arm + the resync pair

- **THE BUILD-TOO-HIGH ARM** (handlePlayerUse lines 466-476, 1:1): the
  ceiling cell with the UP face answers the RED build.tooHigh
  translatable chat (the engine's TranslatableText/Style/TextJson —
  the key + the 256 arg + the inlined color, exactly the reference's
  ChatMessageS2CPacket shape) BEFORE the distance gate (the else-band
  order: any distance still answers) plus the resync pair, and no use
  arm runs. The engine's BlockPosition ctor bounds y to 0..255, so the
  reference's second band (y >= height) never engages.
- **THE ANTI-DESYNC RESYNC PAIR** (lines 494-497, 1:1): after EVERY
  block-face use — accepted, refused by any gate, or consumed by any
  arm — the clicking player's client receives the authoritative state
  of the clicked cell AND the face-offset cell. The client predicts its
  use's outcome locally; a refused click would leave a GHOST block
  without the pair. The accepted use's own writes already broadcast —
  the pair merely repeats them.
- **THE SEAM**: `ClientLink.blockResync` — the per-player unicast verb
  (default no-op for engine-internal links; the V18Connection override
  writes the same 0x23 the committed-change broadcast owns); the
  engine's use walk wrapped in try/finally so the pair fires on every
  exit path. The off-world neighbour (UP at the ceiling, DOWN at the
  floor) is guarded — the reference's dead packet at the void cell is
  skipped (the client drops it either way).
- **THE WIRE-SIDE DISCOVERY**: the JoinFlow echo test's plain
  block-change read caught the pair interleaving ahead of the dig's own
  commit — moved to the POSITIONAL read (`readBlockChangeAt`, the
  helper the grass-decay class already established): the pair's 0x23s
  are by-design traffic now.
- Evidence: TwoStageUseLawAcceptanceTest 11 (the pair arm: the accepted
  open fires clicked+offset in order, the refused too-far use STILL
  fires the pair with the truth; the too-high arm: the red JSON + the
  ceiling resync + no container from 250 blocks below) + the protocol
  module FULL 108 green + the core interaction sweep green
  (sweep-N3-proto2/core.log).

**Next:** the ops model (the spawn-protection gate's dependency), the
teleported-flag gate, the Phase-5 workload adoptions (pathfinding,
lighting, serialization), the §29 standing rows.
