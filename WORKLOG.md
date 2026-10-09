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

## In progress

- **Slice 6 — mounts completion**: animal breeding (love/breeding-age clocks,
  feed-to-love per species, breed goal with the 60-tick proximity rule, baby
  growth), donkey/mule chests (equip, 15-slot window, spill on death), horse
  feeding table (heal/grow/temper/love bands). Leads deferred.

## Not started

- Slice 7 — enchanting (table + `EnchantmentHelper` math).
- Slice 8 — nether portals.
- Redstone, potions/brewing, leads, structures, natural spawn cycles
  (see `VANILLA_1_8_8_COMPATIBILITY.md` section 10).
- dev.13 release: full-suite run, version bump, dist zip, prerelease.

## Conventions

- Every slice commits with the reference files named in the message.
- The full test suite must be green before each push; the count is recorded
  in the unit's entry (baseline 451 after Slice 5).
- Push after every unit — environment resets must not eat finished work.
