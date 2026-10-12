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

- Slice 7 remainder — the leftover effect hooks: thorns (the
  protection/damage wildcards), the loot family (looting/fortune/silk
  touch), the bow family (power/punch/flame/infinity), unbreaking,
  respiration/depth strider — each lands with its gameplay slice.
- Slice 8 — nether portals.
- Redstone, potions/brewing, leads, structures, natural spawn cycles
  (see `VANILLA_1_8_8_COMPATIBILITY.md` section 10).
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
