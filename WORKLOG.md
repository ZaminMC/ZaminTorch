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

- **Slice 7 remainder — the leftover hooks**: thorns (the protection/damage
  wildcards), the loot family (looting/fortune/silk touch), the bow family
  (power/punch/flame/infinity), unbreaking, respiration/depth strider —
  each lands with its gameplay slice.

## Landed since the ledger opened

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
