# The terrain worldgen family audit — the fresh-boot experience (the owner's demand-named pass)

The owner's report (2026-10-30): a freshly started server "uses the same seed
everytime"; "the player spawns under terrain for some reason like under
blocks"; "the bat also spawn in air at surface"; "the terrain does not look
vanilla 1:1, the ores generation the caves, its all messed up"; "the spawn
of entity including player and mobs and animals all look wrong"; "the
terrain looks highly sphiscicated". This document is the terrain-family
discovery record: every row verified against `reference/1.8.8` before any
fix, three independent passes per confirmed finding, one fix branch per
independently actionable issue.

The probe evidence lives in `TerrainSpawnProbeTest` (the scratch census kept
for the record; its `system-out` is the behavioral pass). The hash-bias
proof lives in the T-ORE row's verification record.

---

## T-SEED — the world seed is name-derived, never random, `level-seed` unread — CONFIRMED (FIXED this wave)

**Status:** confirmed (three passes complete) → **FIXED — PR #83** (Wave TERR-A:
`WorldSeedStore` + the `EngineConfig.levelSeed` row + the boot resolution +
`WorldSeedStoreParityTest` 8/0; the stash verification: the pre-fix tree
refuses to compile the seat files — the seams absent in no partial shape).

**Engine sites:** `NormalWorldGenerator.<init>` (the seed line:
`seed = 0x5A4D494E5C00L ^ worldName.hashCode() * 0x9E3779B97F4L`),
`EngineServer` boot (the generator construction + `worldSeed =
generator.seed()`), `ConfigLoader.fromProperties` (no `level-seed` row),
`ServerLayout`'s properties template (writes `level-seed=` — cosmetic only).

**Reference:** `DedicatedServer` lines 170-183 (the seed law):
`long k = new Random().nextLong();` then, if the `level-seed` property is
non-empty: `Long.parseLong` (kept when `!= 0L`), else the string's
`hashCode()`. The chosen seed persists in the level data — every later boot
of the SAME world reuses it; a FRESH world (no stored data) gets the random
(or level-seed) derivation.

**Expected (vanilla 1.8.8):** fresh world creation rolls a random seed
unless the operator pinned `level-seed`; the seed is stored per world and
reused; two fresh installs never share terrain.

**Observed (engine):** the seed is a pure function of the world NAME. The
`level-seed` property is written into server.properties and never read. Two
fresh boots named `world` produce byte-identical terrain; a different name
produces different terrain. The probe pinned it: `seed(world) =
-3865450252547311320`, re-derived identically by a second fresh generator
instance (the probe asserts it).

**Verification passes:**
1. *Source:* the two derivation sites read side by side (the engine's
   name-hash XOR law vs. the reference's random/parse/hashCode chain). The
   engine's ConfigLoader key inventory contains no `level-seed` row.
2. *Behavioral:* the probe's seed-determinism assertion PASSES (the
   assertion documents the divergence: the same name re-seeds identically
   across fresh boots — the opposite of the reference's fresh-world law).
3. *Re-verification:* the full seed-consumer walk — `worldSeed` feeds the
   nether generator, the two portal forcers, the slime-chunk derivation,
   the natural spawner's `worldSeed()` — confirming the fix must persist
   the seed or every stored delta/slime/portal fact breaks on reboot.

**The fix (this branch):** `WorldSeedStore.resolve(worldDataDir,
worldName, levelSeed)` — the stored `world/data/seed.json` wins (the
spawn.json persistence pattern); a pre-fix world with no record adopts the
HISTORICAL name law and persists it (the delta-overlay migration: a
name-seeded world keeps its continuity); a genuinely fresh world takes the
reference's derivation (`level-seed` numeric ≠ 0 / string hash / random).
`EngineConfig` grows the `levelSeed` row (a compatibility constructor keeps
the existing test constructors whole); the boot resolves the seed BEFORE
`StorageManifest.open` (the freshness check must not see the boot's own
manifest write) and feeds it to the generator's new seed constructor.

**Regression risks:** engine-boot tests that pin terrain-dependent facts
get fresh random seeds (the suites' re-pins recorded in the wave entry if
any surfaced).

---

## T-SPAWN — the spawn point never consults the generated blocks — CONFIRMED (FIXED this wave)

**Status:** confirmed (three passes complete) → **FIXED — PR #84** (Wave TERR-A:
`WorldSpawnResolver` + the boot-time resolution after pregen (the anchor
gated) + `WorldSpawnResolverParityTest` 5/0; the stash verification: the
pre-fix tree refuses to compile the seat files — the seams absent in no
partial shape).

**Engine sites:** `NormalWorldGenerator.scanSpawnColumn` (the +x diagonal
biome scan, a named stand-in for the reference's biome search),
`NormalWorldGenerator.spawnPosition` (`heightAt + 1` — the pure height
function, never the chunk), `EngineWorld` line 166 (the position fixed at
construction, before any chunk exists), `EngineServer.joinRequest` (a
fresh player's position `orElseGet(world::spawnPosition)`), the two
respawn paths (`bedSpawn or world.spawnPosition()`).

**Reference:** `ServerWorld.initSpawnPoint` lines 648-682 (the stored
spawn's X/Z walk: `while (!this.dimension.isValidSpawnPoint(i, k))` —
`Dimension.isValidSpawnPoint` = `world.getSurfaceBlock(new BlockPos(x, 0,
z)) == Blocks.GRASS`, the drift `+= random.nextInt(64) -
random.nextInt(64)` capped at 1000, the `Random(seed)` stream);
`ServerPlayerEntity`'s constructor lines 130-160 (the actual placement: the
spawn-protection scatter, `world.getSurfaceHeight(...)` — the real-chunk
surface consult — then the UNSTUCK WALK `while (!world.getCollisions(this,
this.getShape()).isEmpty() && this.y < 255.0) { y += 1 }`); `World
.getSurfaceHeight` lines 1132-1148 (scan down from the section top, skip
leaves — `blocksMovement() && material != LEAVES` — return the cell above
the first solid); `World.getSurfaceBlock` lines 158-168 (climb from sea
level while the cell above is not air, return the block there — a tree
column answers LEAVES, an ocean column answers WATER, a grass column
answers GRASS).

**Expected (vanilla 1.8.8):** the world spawn sits on a GRASS column
(never a tree, never open water); every fresh-player/respawn placement
resolves the landing Y through the live blocks and walks UP while the body
collides. A decoration at the spawn column can never bury a player.

**Observed (engine):** the spawn position is the pure height function's
answer at one column, fixed before generation; nothing ever consults the
generated chunk. When the decorate stage plants a tree (or a structure
piece lands) at that column, the fresh player's feet materialize inside
the trunk/canopy — the owner's report. The probe census: 0/12 world names
buried at the pure height (the census's own finding: burial is the
decoration coincidence, not the height math), but TWO of the twelve names
(New World's ocean fallback class — `map1` over `ocean`, `terra` over
`deep_ocean`) spawned OVER OPEN WATER — a column class the reference's
grass gate never allows. The fallback law (`max(heightAt(0,0), 63)`)
bypasses the biome allowlist entirely.

**Verification passes:**
1. *Source:* the two chains traced end to end — the engine's
   constructor-time pure answer (join, respawn, credits all consume it)
   against the reference's grass walk + per-placement surface consult +
   unstuck walk.
2. *Behavioral:* the probe census — the water-column spawns (impossible
   vanilla), plus the engine's own decorate stage writing trees into the
   spawn chunk through the same populate window the spawn never re-reads.
3. *Re-verification:* the full `setSpawnPosition` call-site inventory —
   the `/setspawn` anchor, the anvil import, `EngineWorld`'s construction
   — no compensating re-resolution exists anywhere on the join or respawn
   paths; the spawn area IS pregenerated at boot (the fix's opportunity:
   the blocks exist before the first join).

**The fix (this branch):** `WorldSpawnResolver` — the reference's laws
over a narrow `Ground` seam: the stored-X/Z grass walk (`Random(seed)`,
the ±64 drift, the 1000 cap), the `getSurfaceHeight` law (leaves skipped,
the cell above the top solid), and the unstuck walk (+1 while the landing
cells are solid, capped at 255). `EngineServer` resolves it right after
`pregenerateSpawnArea` (the anchor case skipped — `/setspawn` keeps its
precedence) and rewrites the world spawn. The scatter band (the reference
places each fresh player within `max(5, protection-6)` of the stored point)
stays a ledgered adaptation — the engine's single-spawn model places every
fresh body at the one resolved cell.

---

## T-ORE — per-block hash scatter instead of veins, and the scatter is catastrophically biased — CONFIRMED (queued for the next wave)

**Status:** confirmed (three passes complete). The design row was a
ledgered adaptation (the FeatureDecorator's "THE LEDGERED STAGE"); the
owner's demand retires the ledger. The implementation row (the bias) is a
plain defect on top of it.

**Engine sites:** `NormalWorldGenerator.oreOrStone` (the depth-banded
ladder over `hash3(x, y, z, 0x51ED)`: diamond `y<=13 && roll%10000 < 12`,
redstone `y<=16 && < 22`, gold `y<=30 && < 20`, iron `y<=54 && < 60`,
coal `y<=60 && < 80`); `FeatureDecorator`'s javadoc "THE LEDGERED STAGE"
(the veins stage deliberately retired with the pure-terrain-contract
rationale).

**Reference:** `FeatureDecorator.placeVeins` (the walk's FIRST stage) —
`placeVeinLinear` per family (dirt 10@0-256/33, gravel 8@0-256/33,
granite 10@0-80/33, diorite 10@0-80/33, andesite 10@0-80/33, coal
20@0-128/17, iron 20@0-64/9, gold 2@0-32/9, redstone 8@0-16/8, diamond
1@0-16/8) + `placeVeinTriangular` (lapis 1@16-16/7 — the triangular y
distribution); `BlockVeinFeature.place` (the vein itself: the drop-to-
floor walk, the 3-segment ellipse walk over `size`-scaled radii). Plus the
per-biome extreme-hills emerald + silverfish post-pass.

**Expected (vanilla 1.8.8):** veins of contiguous same-kind blocks —
~8-17 ore cells per vein at the per-family depths and per-chunk counts;
lapis exists; granite/diorite/andesite/dirt/gravel blobs make up the
stone-family composition.

**Observed (engine):** the probe census over 16 chunks:
`coal=6400, diamond=27651, gold=26034, iron=43716, redstone=5083` —
per-chunk averages of **diamond ≈ 1,728** (vanilla ~8), **gold ≈
1,627** (vanilla ~18), iron ≈ 2,732 (vanilla ~180), redstone ≈ 318
(vanilla ~64), coal ≈ 400 (vanilla ~340, roughly right BY COUNT but
scattered singles, never veins). No lapis, no granite/diorite/andesite,
no dirt/gravel blobs. No two adjacent ore cells anywhere (a hash scatter
cannot form veins).

**Verification passes:**
1. *Source:* the ladder read against `placeVeins`/`BlockVeinFeature` —
   different mechanism (per-cell independent rolls vs. seeded contiguous
   features), different distributions (flat depth bands vs. the reference's
   per-family linear/triangular bands), different families (the ladder
   carries no lapis/stone-family rows at all).
2. *Behavioral:* the census above — diamond 216× the reference's density;
   plus the independent hash check (Python, exact 64-bit Java arithmetic):
   `hash3(seed, x, y, z, 0x51ED) % 10_000 < 12` holds for **38.88%** of
   the y≤13 cells in a sampled chunk (1,294/3,328) — 324× the ladder's own
   intended 0.12%. The multiply-XOR-shift chain's low-order residues
   cluster catastrophically modulo 10,000; the ladder is broken as a
   SCATTER, before the vein question even starts.
3. *Re-verification:* the FeatureDecorator's own ledger re-read — the
   retirement rationale ("the pure terrain function could never see a
   decorate-time vein write") holds for the GENERATION-side consults, but
   the populate window's region re-derivation (`{self,-x} x {self,-z}`)
   already solves exactly this class for the trees/lakes/dungeons; the
   veins stage re-enters through the same window, and the generation-side
   pure consults answer the reference's own unpopulated-neighbor truth
   (stone) once the ladder retires. The adaptation's stated premise no
   longer binds.

**The fix (queued, the next wave):** reinstate `placeVeins` as the walk's
first stage in the populate window (the reference's exact order, counts,
depth bands, and `BlockVeinFeature` 1:1 over the FeatureWorld view),
retire `oreOrStone`'s ore arms (the pure answer becomes stone, the
reference's unpopulated-neighbor truth), and add the extreme-hills
emerald pass if the profile lacks it. This is a self-contained wave (the
decorator stage + the ladder retirement + the seats), separate from the
carver/noise rows below.

---

## T-CAVE — noise-blob caves sealed under the surface; no ravines — CONFIRMED (queued)

**Status:** confirmed (three passes complete). A ledgered adaptation
(the generator javadoc's "noise caves"); the owner's demand retires it.

**Engine sites:** `NormalWorldGenerator.isCave` (`fbm3(x*0.045, y*0.09,
z*0.045) > 0.74`) and its call site (`y > 4 && y < h - 3` — "the surface
stays intact"); no ravine anywhere.

**Reference:** `CaveWorldCarver` whole (the `Generator` region discipline —
`range = 8`, the per-region-chunk `setSeed(l*j ^ i1*k ^ seed)` walk; the
per-chunk count `nextInt(nextInt(nextInt(15)+1)+1)` with the 7-in-8
zero-gate; the recursive `carveTunnel` worm — the sin-scaled radius, the
yaw/pitch drift, the branch fork at the half-tunnel, the water-touch
abort, the `canReplace` family consult, the y<10 lava floor, the
grass→biome-surface repair, the sand→sandstone ceiling repair) and
`RavineWorldCarver` (the 1-in-50 chance, the 256-cell height-variance
buffer, the wide flat-bottomed carve). The carvers run at the GEN stage,
writing the chunk storage; caves OPEN THE SURFACE (the carve band runs to
y=248 and carves grass).

**Expected:** worm tunnel systems crossing chunk borders (roots up to ±128
blocks away through the region walk), surface openings, ravines, lava
floors below y=10.

**Observed:** the probe census: the carved volume ≈ 2-3% of the
underground (solid 276,349 vs the ~286,700 a flat column model implies),
and every carve strictly below `h-3` — the surface NEVER opens, so no
cave entrance exists anywhere in the world; no ravines; no lava floors
from carving; blob shapes (a 3D noise threshold), never worm topology.

**Verification passes:**
1. *Source:* `isCave`'s position-pure threshold against the region-walked
   seeded worm carver — no shared mechanism, ordering, or shape.
2. *Behavioral:* the census (the sealed-surface law is directly readable
   from the carve gate `y < h - 3`; the probe's cave-cell count confirms
   the sparse blob volume).
3. *Re-verification:* the same window re-read as T-ORE's pass 3 — the
   carver runs at the GEN stage (before populate), writing the chunk
   storage directly; the engine's generate() writes the same chunk array,
   so the reference's carver port fits the per-chunk model through the
   region re-derivation window (each region sequence writes only its
   intersection with this chunk — the same discipline the structures
   already use). The position-pure `blockAt` consults below y=h-3 would
   need the carver fold or the consult stays the pre-carve truth (the
   reference's own neighbor-window truth).

**The fix (queued):** port `CaveWorldCarver` + `RavineWorldCarver` into
the generate stage over the region window; retire `isCave`. A wave of its
own (the carver pair + the canReplace family table + the lava/repair arms
+ seats).

---

## T-NOISE — the terrain shape itself is a custom heightmap, not the reference's density stack — CONFIRMED (queued; the largest row)

**Status:** confirmed (three passes complete). The generator javadoc
names it a standing adaptation ("the noise core is our own", "the
engine's column model answers the same ordering and magnitudes, its fbm
standing in for the perlin stack").

**Engine sites:** `NormalWorldGenerator.heightAt` (the column law: `64 +
baseHeight*17` over two fbm2 bands + a ridge term, clamped 8..120) and
`generate`'s per-column fill (the whole terrain is a heightmap — no 3D
density, no overhangs).

**Reference:** `OverworldChunkGenerator` whole — the
`PerlinNoise`/`PerlinSimplexNoise` stack constructed from `new
Random(seed)` (minLimit 16 octaves, maxLimit 16, main 8, surface-depth
simplex 4, scale 10, depth 16), `generateHeightMap` (the 5×33×5 height
buffer: the 5×5 biome kernel with the biomeWeights `10/sqrt(i²+j²+0.2)`
table and the depth-halving rule, the depthNoise
`|x|/8000`-folding, the `baseSize + d8*4` pivot, the clampedLerp
`(d2, d3, d4) - d1` density, the y>29 → -10 blend), and `buildTerrain`'s
tri-linear 5×33×5 → 16×256×16 interpolation (stone where density > 0,
liquid below sea level). The `OverworldGeneratorOptions` defaults
(coordinateScale/heightScale 684.412, mainNoiseScale 80/160/80, baseSize
8.5, stretchY 12, lower/upperLimitScale 512, depthNoiseScale 200/200,
biome weights 1.0).

**Expected:** terrain with 3D features (overhangs, floating islands' rare
cousins, natural cliff faces), the reference's exact column heights for a
given seed.

**Observed:** rolling 8-120 heightmap terrain — every column a single
surface height, no overhangs anywhere, heights that do not match the
reference's noise for ANY seed (a different generator family). The
engine's own nether generator proves the port is feasible:
`NetherWorldGenerator` IS the full 1:1 `NetherChunkGenerator` port over
the same `PerlinNoise` stack (`zamin-core/.../world/noise/`), while the
overworld kept the stand-in.

**Verification passes:**
1. *Source:* the two terrain algorithms read side by side (above).
2. *Behavioral:* the probe's height traces (the clamped column law:
   `heightAt@spawn = 72` with `topSolid = 72` — a column model's exact
   equality, impossible under the density stack where the surface
   consults the 3D field).
3. *Re-verification:* the nether precedent re-read — the noise classes are
   ported and proven; the overworld's `buildSurfaces`/biome-layer/
   decorator stages are already ported (VIII14); the missing piece is
   `generateHeightMap`+`buildTerrain` over the existing noise package.

**The fix (queued, its own wave):** port `generateHeightMap` +
`buildTerrain` over the existing `noise` package (the biome consult
switches from `gridBiomeAt`'s 1:4 sample to the reference's
`getNoiseBiomes(..., chunkX*4-2, chunkZ*4-2, 10, 10)` 10×10 array), keep
the engine's per-chunk fill adapter, and re-pin the worldgen test seats to
the new law. This is the ROOT ROW of the owner's "terrain does not look
vanilla 1:1" report — the largest single correction in the family, landed
after the bounded rows.

---

## T-ANIMALS — the populate-time animal seeding reads the pre-decoration height — CONFIRMED (queued, second-order)

**Engine sites:** `NormalWorldGenerator.seedAnimals` + `surfaceYFor`
(`heights[localX][localZ] + 1` — the heights array captured BEFORE the
decorate stage wrote its trees).

**Reference:** `NaturalSpawner.populateChunk` line 185 — `world
.getSurfaceHeight(new BlockPos(j, 0, k))` per placement attempt, against
the LIVE chunk (post-decorate).

**Observed vs expected:** the engine's animals attempt placement at the
pre-decoration height; the trunk/canopy cells refuse them through the
chunk consult (a near-miss drift), while the reference's animals stand on
the grass beneath a canopy (the leaves-skipping surface law) or atop a
trunk where its cap leaves a gap. A bounded correction riding the
T-SPAWN resolver's `getSurfaceHeight` seam. Three passes: the two height
sources (source), the decorate-order trace in `generate()` (behavioral —
the heights array predates `plantDecorations`), and the refusal-drift
walk re-read (re-verification — the engine's four-attempt drift masks the
divergence most of the time, which is why it survived the worldgen
consumer lanes).

---

## T-BAT — the natural spawn cycle is a faithful port; two ledgered drifts stand, no defect found in the cycle — REJECTED as a standalone defect row

The owner's "bats spawn in air at surface" report against
`NaturalSpawnSystem` (the 1:1 `NaturalSpawner.tick` port: the
`getRandomPosInChunk` y-ceiling law — `roundUp(topSolid+2, 16)` with the
engine's top-solid convention, the same bound as the reference's
`roundUp(getHeight()+1, 16)`; the four-attempt drift with the
`nextInt(1)-nextInt(1)` y-quirk; the bat's own gates — `y < 63`, the
1-in-2 coin, `rawBrightness <= nextInt(4)` — verified against
`BatEntity.canSpawn` lines 188-203 line by line).

**The two ledgered drifts (recorded, not actionable as the reported
symptom):**
1. The Halloween window (`isTodayAroundHalloween`: Oct 20 - Nov 3 widens
   the brightness band to `nextInt(7)`) rides no engine clock — the
   engine keeps `j = 4` year-round. (Notably ACTIVE on this audit date.)
2. The reported surface-bat symptom is TERRAIN-DRIVEN: with T-CAVE's
   sealed-surface law the underground blob caves are the only sub-63
   dark air pockets, and with T-NOISE's 8-120 clamped columns the
   sea-adjacent bands are thin — the spawn cycle faithfully picks its
   cells; the CELLS are the terrain family's findings. No independent
   cycle defect survived verification (three passes: the reference
   re-read, the wiring walk of `SpawnWorldQuery.surfaceHeight`/
   `skyLightAt`/`blockLightAt`, and the census correlation).

---

## The queue after this wave

- **Wave TERR-B (next):** T-ORE — the veins stage reinstated, the
  per-block ladder retired.
- **Wave TERR-C:** T-CAVE — the carver pair ported, `isCave` retired.
- **Wave TERR-D:** T-NOISE — the density stack ported over the existing
  noise package; the worldgen seats re-pinned.
- **Riding rows:** T-ANIMALS (the populate surface consult), the
  Halloween calendar window, the fresh-player scatter band (T-SPAWN's
  ledgered adaptation), T-SPAWN's X/Z scan answer (the named stand-in
  for the reference's `findBiome` — retired by TERR-D's port if the biome
  source consult lands with it).

## The wave's regression record (Wave TERR-A)

- The config lanes (ConfigLoaderTest + the config package + RconQueryConfigTest)
  GREEN.
- The worldgen family lane (`net.zaminmc.torch.server.world.*`, the probe +
  the two new parity classes + Biome/GenLayer/Nether/TheEnd/Portal/anvil
  suites) GREEN.
- The entity package lane (`net.zaminmc.torch.server.entity.*`) GREEN —
  the engine-booting tests tolerate the fresh-world random seeds (the
  fresh-boot random derivation changes TERRAIN per boot; no seat in these
  lanes pins terrain-dependent facts — the boot-time spawn resolution
  settles onto a grass column every boot, whatever the seed rolled).
- The remaining lanes run in the wave's PR branches; the run record rides
  the PRs' own evidence sections.
