# ZaminTorch

ZaminTorch is a from-first-principles Minecraft server engine. Its first compatibility
target is **Minecraft Java Edition 1.8.8** — as a *compatibility target*, not an
architectural one. ZaminTorch reproduces the observable behavior of Minecraft while
being architecturally unlike the historical Bukkit/CraftBukkit/Spigot/Paper lineage.

```
Minecraft client
       ↓
1.8.8 protocol adapter
       ↓
Zamin engine (world, entities, simulation, registries)
       ↓
stable Zamin API  ← plugins / future compatibility layers
```

## Status

Early development. See [docs/ENGINEERING_PLAN.md](docs/ENGINEERING_PLAN.md) for the
current architecture, decisions and milestones, and `Torch-Arcetecture.txt` §29 +
`VANILLA_1_8_8_COMPATIBILITY.md` for the per-mechanic records. The engine's
mechanics ledger runs deep (survival loop, mobs/AI, combat, physics, fluids,
villages, biomes, maps, remote admin — see the table below and the "Remaining
work" ledger for the pending tail).

| Feature        | Status      |
|----------------|-------------|
| Server lifecycle | implemented |
| 1.8.8 handshake/status/login | implemented |
| Flat world + chunks | implemented |
| Player movement tracking | implemented |
| Block placement/breaking (creative) | implemented |
| World persistence (delta store) | implemented |
| Chat + basic commands | implemented |
| Two-player visibility sync | implemented |
| Survival mining (server-validated timing) | implemented |
| Block drops + item entities | implemented |
| Player inventory (hotbar/main) + pickup | implemented |
| Inventory UI clicks + quick-move/number keys | implemented |
| Tools & harvest classes (+ durability wear) | implemented |
| Player data persistence (ZPD v1) | implemented |
| Crafting (2x2 + 3x3 table) | implemented |
| Furnace smelting (ZFD persistence) | implemented |
| Chest storage container (ZCD persistence) | implemented |
| Item metadata variants (charcoal, red sand) | implemented |
| Survival body: health/food/fall/death/respawn | implemented |
| Living mobs (pig, cow, chicken, zombie) + population | implemented |
| Melee combat, knockback, mob loot | implemented |
| PvP (player melee, hurt window, knockback, death/respawn) | implemented |
| Vanilla-DataWatcher metadata parity (real-client crash fix) | implemented |
| Day/night cycle sync + /time + /spawnmob | implemented |
| Scheduled block updates: falling sand/gravel, torch pop, grass spread/decay | implemented |
| Mob persistence across restarts (ZMD v1) | implemented |
| Gravel + flint (first-hit-wins chance drops) | implemented |
| Real-client validation (mineflayer 1.8.8) | implemented |
| Sounds + particles (FX bus: dig/eat/bow/shatter feedback) | implemented |
| Ranged combat: bow (charge), snowball, egg (chick roll) | implemented |
| Posture sync: sneak/sprint flags to observers | implemented |
| Fluids: pour/spread/dry, falling columns, water+lava contact, buckets | implemented |
| Mob AI expansion: skeleton archers, creepers (fuse + explosions), night spiders, shearable sheep | implemented |
| Real-client wire conformance (packet-id table pinned in tests) | implemented |
| Experience: XP orbs (0x11), Set Experience (0x1F), mining/kill/death XP, ZPD v6 | implemented |
| Fire: flint & steel, fire spread/burnout, burning bodies, undead dawn burn | implemented |
| Sugar cane + cactus: worldgen, growth, support rules, prick damage | implemented |
| Reed crafting chain (sugar/paper/book), gold ingot smelt | implemented |
| Lit furnace visual (block 62 + glow), chest lid (Block Action 0x24) | implemented |
| Collision shapes: slabs/stairs/fences, shape-aware physics | implemented |
| Vehicles: boats + minecarts (mount/steer/dismount wire) | implemented |
| A*-light mob pathfinding + furnace take XP + wall signs | implemented |
| Bed spawn persistence (ZPD v7) | implemented |
| Horses: temper taming, saddle/armor, EntityHorse inventory, ridden physics | implemented |
| Pig saddles + carrot-on-a-stick steering | implemented |
| Villager trading: careers, MC|TrList/TrSel wire, merchant window | implemented |
| Anti-cheat: reach check, nuker budget, fastplace guard, violation ladders | implemented |
| Interactive terminal console: JLine line editing, history, tab completion, colored log bridge, plain fallback | implemented |
| Item NBT (display names, enchantments, lore, attribute modifiers, SkullOwner, BlockEntityTag — the full ItemStack tag envelope) | implemented |
| Anvil world format import (region reader, level.dat, entity/tile import, §31.6 refusal census) | implemented |
| Map item family (filled_map 358, MapData + ZMP store, 0x34 writer, clone/upscale/empty recipes) | implemented |
| Remote admin: RCON (TCP) + GS4 Query (UDP), vanilla 1.8.8 1:1 | implemented |
| Biome family: 61-row registration, GenLayer layout, surface builders, decorators, spawn tables | implemented |
| Local difficulty (the reference's calculate whole, raw day clock, inhabited time, ZWD v2) | implemented |
| Minestom integration | deferred (see ADR in plan) |

This table reflects tested behavior only; nothing is marked implemented without an
automated test proving it.

## Remaining work — the live TODO ledger (2026-10-22)

Everything below is NOT done yet. Done work is tracked in
`Torch-Arcetecture.txt` §22/§29, `VANILLA_1_8_8_COMPATIBILITY.md`,
`NATIVE_API_CAPABILITIES.md` and `WORKLOG.md`; this list carries only the
pending tail, verified against the code on 2026-10-22. Architecture-file rows
(`Torch-Arcetecture.txt`) come first, then vanilla-grade 1:1 gaps, then API
exposure, then concurrency phases, then standing deferrals.

### A. Architecture ledger (Torch-Arcetecture.txt §29 Wave VIII14+ — the last open row)

1. The ambient-sound cadence, 1:1 (the ONLY remaining Wave VIII row): replace
   the engine's named-adaptation idle clock (`160 + nextInt(320)`) with the
   reference's exact law — `MobEntity.baseTick` lines 151-159
   (`random.nextInt(1000) < ambientSoundDelay++`, reset to
   `-getAmbientSoundInterval()`), the per-family interval band (default 80,
   AnimalEntity 120, WaterMobEntity 120, GolemEntity 120, GuardianEntity 160,
   HorseBaseEntity 400), the play pair through `getSoundVolume()`/`getSoundPitch()`
   (ghast 10.0, horse 0.8, cow/ocelot/wolf/squid 0.4, bat 0.1, slime 0.4*size;
   baby `(r-r)*0.2+1.5` vs adult `(r-r)*0.2+1.0`), the volume/pitch-carrying
   fan (`onMobSoundAt`), and the villager's trade arms
   (`VillagerEntity.trade`'s delay reset + `updateOffer`'s 20-tick yes/no gate).

### B. Vanilla-grade 1:1 gaps (named in the ledgers, verified unported in the code)

2. The living family's in-wall suffocation (`LivingEntity.baseTick` lines
   195-197: `isInWall()` → `IN_WALL` 1.0 damage; `Entity.isInWall`'s 8-point
   eye-band view-blocking consult) — mobs AND players; also closes the
   piston-crush tail named in Wave VIII13.
3. The climb-out assist (the lava/water pool-exit:
   `collidingHorizontally && canMove(+0.6)` → `velocityY = 0.3`, the step
   model's own slice named in the lava-drag row).
4. The water branch's own travel model (the reference's `travel` water arm vs
   the engine's established clamp — the named standing envelope).
5. The explosion resistance table (the charged skull's 0.8 blast-resistance
   cap family — "not yet modeled" in the wither row).
6. The explosion × decoration interplay (explosions do not yet ring the
   decoration sweep — the frame/painting instant-drop arms of
   `DecorationSystem`).
7. The ender dragon's respawn ritual (the four-end-crystal fountain —
   `DragonDataStore`'s own javadoc names it "its own future slice").
8. The thorns wildcard's mob-victim arm (an armored mob's retaliation — the
   walk reads only the player rows today; verify against VIII4's equipment
   family and wire the mob arm).
9. The wither's conditional exemption arms (spawner/cobweb/fall-damage/
   light-level/armor-protection/no-riding — each rides its own family's
   slice; track each as that family lands).
10. The global world-event radius (1012/1014 ride the chunk-gated stand-in
    today; the reference's 64-block radius is the 1:1 form).

### C. Native API exposure (the Wave IX demand-named tail — NATIVE_API_CAPABILITIES.md §9)

11. The mob equipment read/set on `entity.LivingEntity` (the five-slot family,
    VIII4's engine surface has no api handle).
12. The achievement reads: `Server.achievements()` (the 34-row table) + the
    per-player unlocked set (VIII6's surface).
13. `Server.anvilImport()` — the plugin-facing import verb over the same I/O
    lanes (the ledger report as the return).
14. The generic-mount seat rows (the exotic pairs' api read beside
    `Vehicle.riderEntityId()`).
15. `world.decorations()` — the hanging bodies + armor-stand read surface.
16. `block.BiomeData` — the registration datums (temperature/downfall/height/
    isRainy law) beside `World.biomeAt`'s display name.
17. `Server.fishing()` — the fishing system's api surface (the engine accessor
    is diagnostics-only today).
18. The door-break arming read (VIII9's builtin-goal arming + progress, the
    §27.10 row).

### D. Concurrency phases (Torch-Arcetecture.txt §22 — the gated lanes)

19. Phase 5: the lighting batch's parallel wiring (the gate is answered with
    measured wins; the wiring itself is demand-named).
20. Phase 6: the further parallel per-region gameplay lanes beyond the
    random-tick lane (each gated on its own evidence; full parallel execution
    is not complete).
21. Phase 7: the heavy-owner optimization lanes (the profiling opener landed;
    each optimization stays gated on the §22 evidence bar).
22. Phase 8: adaptive balancing, not started (dynamic split/merge,
    load-driven migration with in-flight tasks, telemetry, anti-oscillation
    rules).
23. Phase 0's standing practice: continue the per-path mutable-access audit
    alongside every mechanics slice.

### E. Standing deferrals

24. The §28.7 Bukkit/Spigot/Paper compatibility adapter (deferred by §27.1's
    order: after the 1.8.8 implementation is polished into a playable,
    maintainable server).
25. Minestom integration (deferred — see the ADR in the plan).

## Downloading a dev build (no build tools needed)

Grab the latest zip from
[Releases](https://github.com/ZaminMC/ZaminTorch/releases) (dev builds land
on the `develop` branch's releases; prereleases included). Builds are
numbered `torch-1.8.8-N` — the numbering continues the old `v0.2.0-dev.N`
ledger — and are cut automatically from every `develop` push by
[`.github/workflows/release.yml`](.github/workflows/release.yml),
with the fat jar `torch-1.8.8-N.jar` and the bundle
`ZaminTorch-server-1.8.8-N.zip` attached to each:

1. Unzip. Requires **Java 21+** on the PATH.
2. `./start.sh` (Linux/macOS), `start.bat` (cmd) or `start.ps1` (PowerShell).
3. Connect a vanilla 1.8.8 client to port 25565. First boot writes
   `zamin.properties` and generates the world next to the jar.

The server checks GitHub on boot and **prints an update prompt** when a newer
release exists — updating means stop, replace the jar, start again. Nothing
is downloaded or replaced automatically.

## Building & running

Requirements: Java 21.

```bash
./gradlew build        # compile + tests
./gradlew test         # run unit + integration tests
./gradlew :zamin-launcher:runServer   # start a development server
./gradlew :zamin-launcher:serverDist  # build the release zip
```

The development server reads `zamin.properties` from the working directory
(a default is created on first start) and listens for 1.8.8 clients on the
configured port. Console commands: `help`, `state`, `save`, `stop`. In-game:
`/help`, `/ping`. World changes are persisted to `worlds/<world>/zamin-delta.bin`
and survive restarts.

## Automated releases

Every push to `develop` publishes the next numbered dev build automatically:
the workflow computes `N` from the tag ledger, stamps the version into
`distVersion` and `UpdateChecker.BUILD_VERSION`, runs the full test suite
(one retry for the three documented load flakes), tags `torch-1.8.8-N`, and
ships the jar + zip as a prerelease with auto-generated notes (commits since
the previous tag). To title a release, either add a `Release-Title: <title>`
trailer to a commit message, or cut an off-cycle release with
`scripts/release.sh "<title>"`. A head commit carrying `[skip ci]` postpones
its push's release to the next push.

## Modules

- `zamin-api` — the stable public API for plugins. No implementation types.
- `zamin-core` — the engine: lifecycle, world model, players, simulation.
- `zamin-protocol-v1_8_8` — the 1.8.8 protocol adapter (wire handling only).
- `zamin-launcher` — bootstrap entry point.

## License

MIT — see [LICENSE](LICENSE).
