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
