# ZaminTorch — Engineering Plan (Slice #1)

Status: living document. Updated when reality changes; never documents planned work as done.

## 1. Repository state (investigated 2026-10-07)

- Blank foundation: `main` contains only `LICENSE` (MIT, 2026 Zamin Bhutto).
- No build system, no source, no CI. Architecture starts at zero, as stated in the vision.
- Environment: Java 21 (OpenJDK 21.0.x), Linux, Gradle 8.14, Maven Central reachable.

## 2. Build & language recommendation

- **Java 21** (LTS). The 1.8.8 compatibility target does not constrain the JVM baseline.
- **Gradle (Kotlin DSL)**, single build, small multi-module setup. Modules are added only when a real dependency boundary exists.
- Dependencies (justified per rule §31): **Netty 4.2** (proven async transport; writing our own NIO layer would be a replacement of proven infrastructure with no benefit), **JUnit 5** (tests). Nothing else for Slice #1.

## 3. Minestom boundary (ADR-0001 — deferred, with evidence)

The vision expects Minestom as foundational infrastructure. Current finding:

- Minestom's current release implements networking for **one modern protocol version** (1.21.x). Its packet codec, connection states and registries are hard-wired to that version; a 1.8.8 client (protocol 47) cannot be served by it without forking its internals.
- Using Minestom's instance/chunk model as Slice #1's world container would introduce a second authoritative world representation (modern flattened block IDs → 1.8.8 legacy IDs translation) and violate the one-source-of-truth rule (§834) and the compatibility-is-a-boundary rules (§52x).

**Decision:** Slice #1 implements a dedicated 1.8.8 *protocol adapter* (its own module, on Netty) and a minimal Zamin world model. The engine keeps clean boundaries (no protocol types leak into gameplay; no gameplay knows the wire). Minestom is introduced at the moment its capabilities are genuinely needed — first candidate: the modern-protocol milestone — through a dedicated integration module (`zamin-minestom`), so the engine core does not change. This is a documented, evidence-based deviation (§34/§35/§184), not an abandonment of the Minestom boundary. Re-evaluate at the modern-protocol milestone.

## 3b. Community reuse (ADR-0002 — evaluated per the reuse rule)

Standing rule: before building anything, search for community solutions and
make them fit instead of writing replacements. Evaluation of the Minestom
ecosystem index and related candidates, against the current slices:

| Candidate | Verdict | Trigger to revisit |
| --- | --- | --- |
| Minestom | Deferred (ADR-0001 stands; networking single-version 1.21.x, second world model) | Modern-protocol milestone |
| hollow-cube/polar | Not now: ZWD v1 persistence implemented + test-proven; Polar stores modern flattened data needing 1.8 conversion | Multi-world / format milestone |
| mworzala/canvas | Exact fit at Anvil import (§541) | Anvil milestone |
| BlueDragonMC/SteelWorldGen | Real terrain gen is a later slice; flat gen is the deterministic fixture | Terrain-gen slice |
| TogAr2/MinestomPvP, VanillaReimplementation | Minestom-tied; combat out of scope; VRI is behavioral reference reading only | Combat slice |
| Incendo/cloud-minestom | Two commands exist; a command framework is machinery without a workload (§31) | When commands grow |
| Shynixn/MCCoroutine, KotStom | Kotlin — engine is pure Java 21 | Stack change only |
| stomui, hephaestus-engine, WorldSeedEntityEngine | Chest GUIs / custom entity visuals, out of scope | UI/custom-entity slices |
| **PrismarineJS/minecraft-data** (MIT, 948★, active) | **ADOPTED**: block behavior data (hardness/drops/materials for pc/1.8) embedded as a generated table with attribution; protocol 47 packet layouts and the position bitfield verified against it — it caught a real wire bug (packed position x:26|y:12|z:26); entities.json (pc/1.8) carries the mob wire type ids and body sizes for the living-entities slice | Every new data need first |
| Querz/NBT (MIT) | Logged for Anvil import | Anvil milestone |

No Java binding of minecraft-data exists; embedding a generated, attributed
subset is the established JVM-engine pattern (Glowstone/Nukkit tables).
Dependency additions still require the §31 justification rule.

## 4. Module structure & dependency direction

```
zamin-launcher  →  zamin-core  →  zamin-api
                       ↑
        zamin-protocol-v1_8_8 (implements engine-facing protocol interfaces)
```

- `zamin-api` — public, stable developer API. No implementation types, no protocol types, no Netty. Holds: `Server`, `World`, `WorldView`, `Player`, value types (`BlockPosition`, `ChunkPosition`, `Rotation`, `PlayerState`), registry identifiers (`Identifier`, `BlockType`), event interfaces. Deliberately minimal (§369).
- `zamin-core` — the engine. Owns lifecycle, configuration, logging boundaries, the world model (chunks, block state, flat generation), the player registry, the simulation tick loop, and the port through which protocol adapters attach. Minestom/Netty-free.
- `zamin-protocol-v1_8_8` — version adapter. Owns the 1.8.8 wire: transport, framing, encryption-free offline login, packet codecs, legacy ID translation. Depends on engine-facing interfaces only.
- `zamin-launcher` — `main()`. Composes configuration → engine → protocol adapter → start. Contains no logic.

Dependency rules enforced: API never depends on core/adapters; core never depends on protocol; adapters depend on core+API. No cycles (§43/§44).

## 5. 1.8.8 protocol strategy

- Connection state machine: `HANDSHAKE → (STATUS | LOGIN) → PLAY` with explicit per-state accepted packets (§67, §514). Invalid packets in a state disconnect the client; gameplay never sees raw packets.
- Offline-mode login: `Login Start` → `Login Success` (UUID v3 of `"OfflinePlayer:" + name`, matching historical behavior). Encryption not implemented (offline); compression disabled at first, threshold introduced when measured necessary.
- All wire handling stays in the adapter: VarInt framing, packet IDs, chunk binary format (1.8 layout: 16 sections × 4096 legacy block IDs + full skylight), legacy numeric block IDs as *translation data* (§579), not engine identifiers.
- Engine-facing intents: the adapter translates packets into engine operations (`join`, `move`, `look`, `keep-alive response`, later `place/break`). Engine results translate back through a `ProtocolAdapter` into version-specific frames.

## 6. World / chunk strategy (Slice #1 scale)

- `Identifier` (`minecraft:stone`) is the canonical identity; a small built-in `BlockType` registry with a freeze lifecycle (§55/§56). Legacy IDs live only in the 1.8.8 adapter as a mapping table.
- Chunk = 16×256×16, sections of 16³, block storage as palette→`short` type ids per section (simple, correct, measurable — §207). No custom compression yet.
- World owns chunk lifecycle (`UNLOADED/LOADING/LOADED`) and the single block-access entry point (`getBlock/setBlock` through the world, §208). Deterministic flat-world generator (deterministic fixture, §241); real generation is a later slice.
- Persistence: **not in Slice #1** (explicit temporary decision, §146). Slice #2 introduces world storage behind a `WorldStorage` interface.

## 7. Player / entity strategy

- `Player` = gameplay identity (UUID, name) + session state machine (`CONNECTING→AUTHENTICATING→JOINING→PLAYING→DISCONNECTING→DISCONNECTED`, §437) + entity state (position/rotation). Connection/socket ownership stays in the protocol adapter; the engine owns gameplay state.
- Server authoritative: client movement packets are *proposals*; obvious invalid input (NaN/∞/absurd deltas) is rejected safely (§441). No anti-cheat yet.
- Entities beyond players: not in Slice #1 (§330).

## 8. Concurrency strategy (Slice #1 scale)

Implemented ownership model (amended from the original "single command queue" draft, per §34/§184 — the queue was machinery without a current workload):

- The **simulation tick thread owns world state**; the world enforces this (mutation from a foreign thread throws) and the tick loop runs fixed-rate with a no-burst catch-up policy (overruns are logged, never executed in bulk).
- **Per-channel ordering**: each connection's packets are processed on its Netty event loop, so per-player state updates are naturally ordered without locks. Identity-critical operations (join/reject) are atomic through the player registry; per-player position is volatile engine state written only by the owning channel loop.
- **World chunk generation** is ownership-respecting: network threads request loads through `EngineServer.requestChunkLoad`, which executes generation on the world owner and calls back there.
- Chunk reads for network serialization are safe because published chunks are immutable at rest in Slice #1; this constraint must be revisited (snapshot-per-tick) when block-mutating gameplay lands (tracked TODO in EngineWorld).
- A general engine work queue exists on the ticker for deferred engine work; a full command-queue model arrives with cross-entity gameplay, not before.

## 9. Testing strategy

- Unit tests per module (JUnit 5): identifiers, registry freeze, block storage, coordinate math, VarInt, framing, packet codecs (golden bytes as protocol fixtures, §95), config loading.
- Integration test: in-JVM engine + adapter, scripted fake 1.8.8 client (real socket) performs handshake → login → receives Join Game/chunks → moves → keep-alive → disconnect → clean shutdown (acceptance test of §319).
- Behavior tests expressed as scenarios (§93). No mocking of internals.

## 10. First vertical slice (acceptance criteria, §318/§319/§133)

1. `./gradlew run` starts the server; typed config loaded; lifecycle states visible in logs.
2. A real 1.8.8 client (or scripted test client) can: handshake → status ping → login → spawn in a flat world → receive chunks → move with server-tracked position → respond to keep-alive → disconnect cleanly.
3. Failure paths (invalid handshake, disconnect mid-login, malformed packet) leave the server healthy (§320).
4. `./gradlew test` proves the above without a human clicking.
5. Shutdown is clean: network stops, no leaked threads, process exits (§121).

## 10b. Slice #1 — implemented status

All acceptance criteria of §10 are implemented and covered by automated tests
(`JoinFlowIntegrationTest`, `ShutdownAcceptanceTest`, unit suites): typed config,
lifecycle state machine, 1.8.8 handshake/status/login, flat world, spawn chunk
sync (full view, synchronous), movement tracking with validation, keep-alive with
timeout kick, clean disconnect, clean shutdown under load, rejection paths
(outdated client, invalid/duplicate name) leave the server healthy.

## 10c. Slices #2/#3 — implemented status

- **Block interaction**: creative placement/breaking through the single semantic entry point (`BlockInteractionService`), reach + collision validation, tick-thread commit, Block Change sync to clients with the chunk visible. (§215/§216/§227)
- **Persistence**: versioned world delta store (`ZWD` v1), atomic writes, corruption quarantine, deltas applied over regenerated terrain; restart survival proven by test (§407). Anvil import remains future compatibility work (§541) — this format never claims to be it.
- **Chat & commands**: validated chat, semantic command parsing, private command feedback (§351/§521).
- **Multiplayer visibility**: mutual spawn exchange, movement teleports within view distance, destroy on departure (§322 core loop).

## 10d. Slice #4 — survival loop (implemented status)

The vertical survival loop is implemented and test-proven end to end
(`SurvivalFlowIntegrationTest` drives it over a real socket; a live process
smoke repeats it against the launcher):

- **Survival mining** (§441 spirit): start/abort/finish proposed by the client,
  validated server-side — reach (4.5), diggability, and elapsed time against
  the historical break duration (hardness × 30 ticks harvestable / × 100
  otherwise, 70% network leniency). Too-fast finishes are rejected and the
  authoritative state is re-synced so clients never keep ghost blocks. Stone
  by hand takes 7.5s and yields nothing, as historically.
- **Block behavior data** (§424/§434): `BlockBehaviorTable` generated from the
  community dataset (see ADR-0002) — hardness, material, harvest gating and
  drops per registered block. `DropService` turns committed breaks into item
  entities; placement consumes from the authoritative inventory (§430/§431/§432).
- **Item entities** (§445/§449/§450): engine-global ids, simulation-owned
  lifecycle — gravity with epsilon-correct ground snap, pickup delay (10
  ticks block drops / 40 player-thrown), despawn at 6000 ticks, validated
  pickup (§433) with partial pickup keeping the remainder in the world.
- **Player inventory** (§428–§432): 36 slots with explicit hotbar/main
  semantics, semantic operations only (pickUp / consumeHeld / dropHeld /
  selectHotbarSlot), historical fill order (stacks, then empty slots, hotbar
  first). Window Items (0x30) full sync of the 45-slot window on change;
  Set Slot available for targeted updates.
- **Wire additions** (all community-verified against protocol 47 data): Spawn
  Entity (0x0E object 1) with objectData item id + pop velocity and item
  metadata (slot type, index 10, 0x7F terminator — which also fixed the
  named-spawn terminator that sent 0xFF), Collect Item (0x0D) + Destroy
  Entities, Entity Teleport for moving items, Held Item Change tracking,
  digging statuses 3/4 as semantic Q/Ctrl+Q drops.
- **Wire bug fixed by live smoke**: the packed block position layout was
  x|z|y; the verified protocol 47 layout is x:26 | y:12 | z:26. Self-
  consistent scripted tests could not catch this — the live process could.

Explicitly NOT yet: crafting (window clicks in craft slots are rejected),
item NBT beyond durability damage, item merging, physics beyond
gravity+drag, permissions, lighting, mobs, Anvil import, plugin API.

## 10e. Slice #5 — tools & harvest (implemented status)

The tool system completes the survival mining loop; every value is the
community dataset's (ADR-0002) and pinned by tests:

- **Tool identity** (§426): 21 tools registered (5 pickaxes / axes / shovels /
  swords, shears) with `ToolSpec` (class + material). Materials carry the
  dataset's speed multipliers (wood 2x, stone 4x, iron 6x, diamond 8x, gold
  12x), harvest tiers (1-4; gold stays tier 1) and durability limits (59 /
  131 / 250 / 1561 / 32, shears 238). Tool items stack to one; the 1.8
  adapter maps their dataset-verified legacy ids (270, 274, 257, ... 359).
- **Harvest gating** (§424/§434): blocks gain `harvestLevel`; the ore ladder
  coal (tier 1) -> iron (tier 2, drops itself) -> diamond (tier 3, drops the
  gem) is gated exactly as the dataset's `harvestTools` maps describe. The
  wrong tier still breaks the block, historically yielding nothing.
- **Mining speed**: a matching tool class accelerates the dig by its material
  multiplier (tier-independent, as historically — a wooden pickaxe digs
  diamond ore fast but yields nothing). Break ticks are now
  `ceil(hardness * (harvest ? 30 : 100) / speed)`.
- **Durability**: stacks carry damage (wire: the historical per-slot damage
  field; value semantics: a worn tool stays worn through split/drop/pickup).
  One successful dig of a hardness>0 block wears the held tool by one; at the
  limit the tool leaves the hand (slot empty + authoritative re-sync).
  Creative players never wear tools.
- **/give** (§521): administrative item source (`/give <name> [count]`) —
  the way to obtain tools until crafting and inventory clicks exist.
- Wire order is pinned by the integration test: commit -> drop spawn ->
  durability re-sync; the live smoke proved a shovel dig commits at 350 ms
  where a hand needs 525 ms, with wear accumulating across digs.

## 10f. Slice #6 — player-data persistence (implemented status)

A returning player continues survival exactly where they left it; the
restart proof of §407 now covers personal state, pinned by test and by a
live restart smoke:

- **ZPD v1** (`PlayerDataStore`, one file per identity under
  `<dataDir>/players/<uuid>.zpd`): name, position, rotation, held slot and
  the non-empty inventory slots (identifier + count + damage — tool wear
  travels with the stack). Same durability rules as the world store: atomic
  temp-file writes, corrupt files quarantined beside the storage path and
  loaded as absent, loud save failures.
- **When**: on disconnect (tick-thread, ordered after any queued inventory
  work) and for still-connected players during shutdown.
- **Restore on join**: the saved spot replaces the spawn anchor, look
  angles, inventory and hotbar selection are applied before the join
  sequence, so the client's first Window Items already shows the restored
  tools. Unresolvable saved items are dropped loudly, not fatally. The
  initial chunk view is centered on the player's actual position (spawn for
  fresh players), not unconditionally on the world spawn.

## 10g. Slice #7 — inventory window clicks (implemented status)

The player's own 36-slot inventory is now clickable; the engine decides and
the client reverts on rejection (§429 semantics, community-verified wire
layouts for Click Window 0x0E and Confirm Transaction 0x32):

- **Cursor model** (§433 family): the inventory owns a cursor stack.
  Left/right clicks pick up, place, merge into matching (damage-equal)
  stacks, split half (rounding up) and swap mismatches — the historical
  behaviors, pinned by unit tests. A full matching stack is a no-op merge,
  not a swap, as historically.
- **Quick move** (shift-click): whole stacks swap ranges between hotbar and
  main inventory, matching stacks topped up first, then empty slots.
- **Number keys** (mode 2): main slot <-> hotbar exchange.
- **Drop gestures**: mode 4 throws one unit or the whole clicked slot in the
  look direction; clicking outside the window (slot -999) throws the carried
  cursor stack; closing the window (0x0D) returns the cursor to the
  inventory, overflow thrown so nothing is lost. Disconnects do the same
  before the persistence snapshot.
- **Every click answers** Confirm Transaction with the action number
  (accepted) and the authoritative cursor Set Slot (window -1), followed by
  a full Window Items re-sync — the client never keeps predicted state.
- **Rejected** (transaction declined): clicks in craft/armor slots (crafting
  is a later slice), middle-click clone, drag painting (mode 5), unknown
  modes, out-of-range slots. Rejections re-sync too, so the client recovers.

Wire order per click is pinned by the integration test; the live smoke ran
/give -> shift-click -> pickup -> place over a real process (craft-grid clicks
are accepted window state since slice #8).

## 10h. Slice #8 — crafting (implemented status)

**Status: implemented, 141 automated tests green, live smoke green
(CRAFT_SMOKE_DONE), pushed.**

- **Data (hard rule honored):** BuiltinRecipes generated from
  PrismarineJS/minecraft-data `data/pc/1.8/recipes.json` (MIT; attribution in
  the generated file). Filter: 2x2-fit + registered items -> the four vanilla
  recipes (log->planks x4, 2x1 planks->sticks x4, 2x2 planks->crafting table,
  coal+stick->torch x4). Tool-repair recipes (dynamic durability result) and
  variant-result recipes (non-oak planks) are excluded with documented
  reasons; regenerate with scripts/gen-recipes.py when the registry grows.
- **Matching:** trimmed shaped patterns match the grid's non-empty bounding
  box in normal or horizontally mirrored orientation (vanilla never flips
  vertically — pinned by tests); interior null cells demand empty cells;
  shapeless recipes match as multisets; bare-id ingredients accept any
  metadata, (id, metadata) ingredients demand the exact damage-field value.
- **Model:** CraftingGrid (4 cells, row-major) is player window state beside
  PlayerInventory; the one cursor is shared via WindowClicks (click semantics
  extracted so both containers play identical rules). Grid contents are
  transient: window close, disconnect and shutdown return them to the
  inventory, overflow is thrown — nothing is lost, nothing persists.
- **Engine routing:** result-slot clicks craft once into the cursor (empty or
  matching cursor only; refusals decline the transaction and resync); each
  craft consumes one unit per non-empty cell; shift-click craft-all chains
  while the grid still matches (smallest cell stack governs the length; a
  remainder stays in the cell); number-key swaps and middle-click on the
  crafting area are rejected with resync.
- **Wire:** Window Items (0x30) now carries the result preview in wire slot 0
  and the grid in 1-4 (45-slot layout complete); LegacyBlockIds gained stick
  280, crafting_table 58, torch 50; placement/mining work for the two new
  blocks through the existing registries and behavior table.

## 10i. Slice #9 — crafting table container (implemented status)

**Status: implemented, 158 automated tests green, live smoke green
(CRAFT_TABLE_SMOKE_DONE).**

The crafting table is now a real container: right-clicking a placed table
opens the historical 10-slot GUI and unlocks every 3x3 recipe the registry
can serve.

- **Use dispatch** (`EngineBridge.useItemOnBlock`): a right-click on a block
  is decided on the simulation context — a crafting-table target opens the
  container, anything else degrades to the existing placement proposal
  (survival consumes from the authoritative inventory, creative places the
  client-claimed block). The old adapter-side mode branch is gone; both game
  modes route through the one engine decision.
- **Container windows** (`PlayerSession`): one open container window id
  (engine-allocated per-server counter, u8 wire range) with its own 3x3
  `CraftingGrid`. Opening a container closes the player window historically:
  the 2x2 grid returns to the inventory, the cursor carries over (one mouse),
  a stale open container returns its grid first. Close returns cursor +
  container grid, overflow thrown — nothing is lost, nothing persists.
- **Wire** (community-verified protocol 47): Open Window 0x2D
  (`minecraft:crafting_table`, 10 GUI slots) sent before the 46-slot Window
  Items (0 result, 1-9 grid, 10-36 main = engine 9-35, 37-45 hotbar = engine
  0-8) — order matters, the client ignores slot data for unknown window ids.
  Click Window now carries the window id through routing; Confirm Transaction
  echoes it; Close Window closes whichever window was addressed. While a
  container is open, inventory-change syncs target the container window.
- **Recipes**: the generator now keeps patterns up to 3x3 (one list serves
  both grids — the bounding-box matcher keeps 3x3 patterns from matching 2x2
  and lets small patterns match anywhere in the bigger grid). This also fixed
  a real generator bug: pattern cells that must stay empty were treated as
  unregistered ingredients, silently dropping every recipe with interior
  holes. The set grew from 4 to 18 recipes: all pickaxes/axes/shovels/swords
  for wood, stone and diamond, plus furnace and chest (registered blocks with
  dataset behavior: furnace 3.5 rock pickaxe-required, chest 2.5 wood).
  Iron/gold tools stay excluded — their ingot ingredients do not exist in the
  registry yet (smelting slice).

## 10j. Slice #10 — item entity merging (implemented status)

Dropped stacks combine as they did historically: every tick, item entities
whose boxes come within the 1.8 search expansion (`getEntityBoundingBox()
.expand(0.5)` — axis distance <= 0.5 + both half-extents) and carry
mergeable stacks (same item, same damage) combine. The younger entity
(higher engine id, deterministic) empties into the older one, capped at the
stack limit; a partial remainder keeps the younger entity alive with the
rest — no duplication, no loss. Pickup delay does not block combining
(freshly dropped items visibly merge, the historical behavior). Observers see
Destroy Entities for the absorbed entity and an Entity Metadata update for
the surviving stack (partial merges update both). Bounded O(n²) per tick is
accepted at slice scale; the entity-system slice revisits it.

## 10k. Slice #11 — furnace smelting (implemented status)

The furnace is a world-owned block entity (`FurnaceBlockEntity` +
`FurnaceManager`, tick-confined like the item system): three slots in the
community-verified layout (0 input, 1 fuel, 2 output — minecraft-data
`windows.json`, corroborated by mineflayer's furnace plugin) plus the
historical 1.8 burn/cook state machine. One fuel unit ignites only when a
smelt is possible; a 200-tick cook cycle produces one result and interruptions
reset the progress (the historical `furnaceCookTime` reset). Fuel values and
smelting results are the minimal registry-gated subset of the canonical 1.8
tables (coal 1600 = 8 smelts, planks/log 300, stick 100, wooden tools 200;
iron ore -> ingot, cobblestone -> stone) — minecraft-data carries NO
smelting/fuel table for pc/1.8 (full-tree search), so provenance is the
Minecraft Wiki values, embedded with attribution like the behavior table.

Wire: Open Window "minecraft:furnace" (3 own slots) then a 39-slot Window
Items (3 furnace + 27 main + 9 hotbar); the per-tick viewer fan-out diffs the
block entity's slot/property serials and sends only moved values — Window
Property 0x31 (fuel left, fuel max, progress, progress max, community-verified
order). Clicks play the shared cursor rules (`WindowClicks` made public);
shift-click routes smeltables to the input, fuel to the fuel slot, and refuses
everything else without shuffling the inventory. Closing the window leaves the
furnace's contents inside (historical container behavior); survival-breaking
the furnace spills all three slots into the world (post-break hook on
`BlockInteractionService`); creative breaking discards the state silently.

Persistence: ZFD v1 (`FurnaceDataStore`, one file per world) with the same
durability rules as ZWD/ZPD — atomic move writes, corrupt quarantine, unknown
saved items dropped loudly. Save points are the engine's saveAllNow
(shutdown, console save). Charcoal (log -> coal metadata 1) is deliberately
deferred: the ItemStack model carries damage only on durability-bound items;
it lands with the item-metadata slice. Iron ingot registered (legacy 265),
which unlocked the four iron tool recipes + shears in the regenerated recipe
set (18 -> 23, community data). Tests: furnace state machine (8), manager +
ZFD round trip (4), engine restart proof, full wire integration with a real
10-second smelt + live property stream (172 total, 0 failures); live process
smoke: place -> open -> shift-click -> burn/cook deltas on the wire -> ingot
out -> contents survive close + reopen (FURNACE_SMOKE_DONE).

## 10l. Slice #12 — survival body (implemented status)

The player is now a body: health, hunger, saturation and exhaustion live on
the session with the historical 1.8 FoodStats economy. Eating is
server-authoritative — the use-item gesture (block placement with the -1
position sentinel, or face 255) starts a 32-tick server-side timer that
consumes one unit and applies the community foods.json values (beef 3/1.8,
steak 8/12.8, MIT attribution); releasing early (dig status 5), switching
slots, or dying cancels. Exhaustion accrues from regeneration (3.0 per
heart); 4.0 exhaustion points drain one saturation unit, then one hunger
unit. Food >= 18 regenerates one health every 80 ticks; hunger 0 starves one
heart per 80 ticks down to the easy-difficulty floor of 10 — Join Game now
announces difficulty 1 (easy), the right fit for a world without mobs yet.

Falls hurt from movement proposals alone: airborne descent accumulates fall
distance per-player; landing applies ceil(distance - 3) on the tick thread.
Death (health 0) returns carried window state, scatters the whole inventory
and cursor as item entities at the body, and tells the client through Combat
Event 0x42 type 2 (community-verified layout). The respawn gesture (Client
Status 0x16 action 0) resets the body at full health/food/saturation and the
adapter re-anchors the wire: Respawn 0x07, a fresh spawn chunk view, the
authoritative position-and-look (the movement sanity check accepts the
acknowledgment at the teleport anchor), health, and the emptied inventory.

Persistence moved to ZPD v2: health, food and saturation ride the snapshot
(version 1 files load with the historical 20/20/5 defaults). Wire: Update
Health 0x06 (f32 health, varint food, f32 saturation — community-verified)
follows join, damage, eating, regen and respawn. Tests: BodySurvivalAcceptance
(5 on a real ticker — eat/refusal/cancel, exhaustion + regen, starvation
floor, fall damage, death + respawn), BodyIntegrationTest over a real socket
(3 — fall sync, death + combat event + full respawn re-anchor, silent
refusals); 180 total, 0 failures. Live smoke on a real process: baseline
20/20 -> scripted fall to 8.0 -> second fall kills -> Update Health 0 +
Combat Event -> respawn gesture -> Respawn + re-anchor + health 20
(BODY_SMOKE_DONE).

## 10m. Slice #13 — chest storage container (implemented status)

**Status: implemented, 179 automated tests green, live smoke green
(CHEST_META_SMOKE_DONE), real-client validated.**

The chest completes the container trio (crafting table, furnace, chest):
27 world-owned slots (the community-verified 3x9 layout), no tick of its
own, ZCD v1 persistence (magic 'Z','C','D'), survival-break spill, and the
63-slot wire window (0-26 chest, 27-53 main inventory, 54-62 hotbar —
community-verified via prismarine-windows: container slots 0-26, player
range start 27 end 62). Right-click opens; clicks route through the shared
WindowClicks semantics; shift-click moves in any stack (a chest has no slot
filters, unlike the furnace), out to the inventory with the remainder
staying inside on a full inventory; number keys and middle-click on chest
slots are rejected per packet. Tests: ChestBlockEntityTest (7 — clicks,
filter-free quick move, full-chest remainder, drops, serials),
ChestManagerTest (4 — lazy state, ZCD round trip preserving variants,
corrupt quarantine, spill), ChestIntegrationTest over a real socket (2 —
full lifecycle incl. the block's own drop preceding the spill, and the
full-inventory remainder).

## 10n. Slice #14 — item metadata: charcoal and glass (implemented status)

**Status: implemented, covered by unit + integration + real-client tests.**

The ItemStack damage field is now dual-role, exactly like the historical 1.8
`Damage` value it carries: durability wear on tools, variant metadata on
everything else (community items.json: coal metadata 1 = Charcoal, sand
metadata 1 = Red Sand). Variants never merge with their base item
(metadata-aware equality was already total through WindowClicks). The
furnace gained the two metadata unlocks: sand smelts to glass (legacy 20,
placed like any block item; community blocks.json: hardness 0.3, drops
nothing — the historical shatter) and logs smelt to charcoal (coal with
damage 1; charcoal burns like coal through the type-keyed fuel table).
/give takes an optional metadata argument (`/give coal 5 1`); the wire
carries the variant everywhere: window slots, item entity slot metadata
(a thrown charcoal renders as charcoal), ZPD/ZFD/ZCD persistence. Tests:
ItemStackMetadataTest (5), MetadataSmeltingTest (5), MetadataIntegrationTest
over a real socket (2 — variant /give + distinct stacks + item-entity
variant; sand place/drop + glass shatter).

## 10o. Slice #15 — real 1.8.8 client validation (implemented status)

**Status: implemented; 17/17 checks pass (REAL_CLIENT_VALIDATION_PASSED).**

mineflayer — the PrismarineJS community's real Minecraft client
implementation — now drives a live server as an independent protocol peer
(scripts/validate-1.8.8.js; run `npm install mineflayer` beside it). Unlike
the engine's own wire tests (which read what the server wrote), mineflayer
parses every packet with its own stack, so a green run is cross-
implementation proof: login/spawn, chat, dig + pickup, placement, the chest
(63-slot layout, deposit/withdraw preserving the charcoal variant, retain
across reopen), and the furnace (sand -> glass, log -> charcoal through the
metadata field). The first run caught three REAL wire bugs our raw-wire
tests could never see, all fixed and pinned by tests:

1. Chunk payload: blocks must be little-endian u16 `(id << 4) | metadata`
   (we wrote one byte per block), and the three arrays are grouped across
   the chunk — all blocks, then all block light, then all sky light — not
   interleaved per section (ChunkSerializer18Test pins the layout).
2. Block Change (0x23) carries the packed state `(id << 4) | metadata`:
   the real client's registry maps state 54 to dirt and 864 to the chest;
   we sent the raw id (V18Connection, verified empirically via
   prismarine-registry blocksByStateId).
3. Slot NBT: "no NBT" is a single 0x00 TAG_End byte (vanilla
   `PacketBuffer.writeItemStackToBuffer` + protodef optionalNbt), not the
   short -1 we wrote — which desynced every subsequent packet parse
   (readClaimedSlot handles the client's claimed-slot byte the same way;
   the claimed slot is the click packet's final field, so a non-zero
   marker's NBT payload is left unread, exactly and safely).

It also drove one robustness fix: the engine's window-click verdict callback
is now isolated — a throwing adapter callback (found via a debug NPE) can no
longer starve the tick work queue (§54). scripts/SmokeChestMeta.java covers
the same batch over a raw wire on a real process (CHEST_META_SMOKE_DONE);
docs/MANUAL_CLIENT_VALIDATION.md is the manual checklist for the real
Minecraft 1.8.8 client.

## 10p. Slice #16 — living mobs: population, AI and melee combat (implemented status)

**Status: implemented; 228 automated tests green; mineflayer real-client
validation passed (REAL_CLIENT_VALIDATION_PASSED).**

The world now carries living mobs (pig, cow, chicken, zombie) with the same
one-owner discipline as every other simulation system. Data first (ADR-0002):
the wire type ids and body sizes come from the community dataset
(minecraft-data pc/1.8 entities.json — Zombie 54, Pig 90, Cow 92, Chicken 93);
health, loot ranges, damage and the sound names mirror the canonical
historical 1.8 values the dataset does not carry. Loot flows through the item
entity system (porkchop, beef + leather, raw chicken + feathers, rotten
flesh), so pickups, merging and persistence behave exactly like drops.

- **Simulation** (`MobEntity`/`MobManager`): gravity with the epsilon-correct
  ground snap shared with items; a small historical-style goal set — wander,
  panic when hurt (passive kinds), chase-and-melee for the zombie (16 block
  aggro, 20 tick cooldown, easy-difficulty 2 damage); the 20-tick death
  animation before loot + removal; knockback along the attacker-to-mob yaw.
- **Population**: boot-time packs near spawn; a maintainer tops passives up
  to a cap (12), spawns zombies only at night (13 000–23 000) up to 6, and
  despawns anything 96+ blocks from every player (mobs idle when nobody is
  online). Hostiles vaporize at dawn (no fire visuals yet). Mobs are NOT
  persisted across restarts this slice — a documented temporary decision
  (§146 pattern); the population rebuilds on boot.
- **Combat** (`attackEntity`, Use Entity 0x02 mouse=1): server-validated
  reach (3.5 + half width), historical damage per held item (fist 1, sword
  4/5/6/7 by material, axes −1, pickaxes 2-5, shovels/shears 1.5), attack
  exhaustion 0.3, tool durability wear per living-entity hit. The zombie's
  melee lands through the same survival damage path as falls.
- **Wire** (all layouts community-verified against the 1.8 protocol.json):
  Spawn Mob 0x0F with living metadata (flags byte index 0, health float
  index 7); Rel Move Look 0x17 with per-observer 1/32 delta tracking (Entity
  Teleport 0x18 beyond i8 range); Entity Head Look 0x19; Entity Status 0x1A
  (2 hurt, 3 death); Named Sound Effect 0x29 with the historical resource
  names (mob.pig.say, mob.zombie.death, …); Destroy 0x13. C2S Arm Animation
  0x0A broadcasts the swing to other observers through their observer-local
  ids. Time Update 0x03 now rides a periodic cycle sync and the /time
  command, so the client's day follows the server.
- **Commands**: `/time query|set <day|noon|night|midnight|ticks>` and
  `/spawnmob <pig|cow|chicken|zombie> [count]` — the controlled entry
  points for real-client validation.
- **Tests**: MobEntityTest (9 — physics, wander, panic, death timer,
  knockback, chase, melee cooldown, chatter), MobManagerTest (7 — spawn
  bookkeeping, death-to-loot flow, loot bounds, dawn/despawn policies,
  population maintainer, zombie melee), MobCombatAcceptanceTest (5 — sword
  kill + durability wear over the real ticker, reach refusal, night zombie
  hunt, dawn removal, /time), MobIntegrationTest (2 — the full wire flow
  from the client's side, incl. two-observer swing broadcast).

## 10q. Slice #17 — scheduled block updates, falling blocks, grass, mob persistence (implemented status)

**Status: implemented; 246 automated tests green; mineflayer real-client
validation passed (REAL_CLIENT_VALIDATION_PASSED), including the new flows.**

The world now reacts on its own. The §466 scheduled-update queue, the §470
block→entity→block transition and the §471 random-tick pattern all landed in
one batch, plus the mob-persistence debt from slice #16.

- **World-owned change dispatch (§208, the batch's architectural fix):**
  `EngineWorld.setBlock` fires the change listeners itself — every committed
  mutation, player-driven or engine-driven, reaches the neighbor-update
  system and the client syncs exactly once, in world order. Before this fix
  only `BlockInteractionService.commit` published, so engine-driven changes
  (conversions, landings, decays, pops) never cascaded and never synced.
  Listener failures are isolated per listener (§54). The rejected-dig resync
  path survives as the world's `republish` (the §441 spirit).
- **Scheduled block updates (§466):** a tick-thread queue with one pending
  update per position (later requests coalesce; earliest due wins). A commit
  schedules the position and its six neighbors due the same tick, so a
  changed block's rules run within one tick of the commit.
- **Falling blocks (§470):** sand and gravel with air beneath convert into
  `FallingBlockEntity` (the community 0.98 box, the shared gravity/drag
  model). Landing re-materializes the block in the first free cell above the
  hit surface; an occupied landing drops the stack as an item (the historical
  dropItem); the void takes the entity silently. Wire: Spawn Entity **object
  70** with the objectData the 1.8.9 client actually decodes —
  `getStateById(data & 0xFFFF)`, i.e. legacy id in the low 12 bits and
  metadata above them (verified against the MCP-919 client sources after the
  wiki variants conflicted) — plus the three velocity shorts (non-zero
  objectData announces them, exactly like item spawns), Entity Teleports on
  movement, Destroy on landing; the landing's Block Change rides the world
  dispatch.
- **Torch support:** a floor torch over air pops as an item through the
  standard drop path.
- **Grass rules (§471 pattern):** random ticks sample three positions per
  section in every loaded chunk within the historical 128-block player range
  (an idle world ticks everything — the historical gate bounds work, not
  semantics). Randomly-ticked grass decays to dirt under an opaque block and
  makes up to four spread attempts into nearby uncovered dirt (the historical
  ±1/−3..+1 window). Light levels are approximated by "nothing opaque above"
  — the flat world carries full skylight and no block-light propagation
  exists yet (documented simplification).
- **Gravel + flint:** gravel registered (legacy 13) with the behavior
  table's first-hit-wins chance rolls — 10% flint (legacy 318), otherwise
  gravel, the historical `quantityDropped` model; `BlockBehavior.Drop` grew
  the chance component and DropService the roll.
- **Mob persistence (ZMD v1):** the population survives restarts through the
  same durability rules as ZWD/ZFD/ZCD/ZPD (atomic temp-file writes, corrupt
  quarantine, unknown kinds dropped loudly); boot packs roll only for a fresh
  world (a restored world keeps its mobs, the maintainer tops it up). A
  crash-save cannot lose a mid-fall block: `saveAllNow` fast-forwards all
  falling entities to their landings before snapshotting (blocks persist,
  entities do not).
- **Wire checks:** the falling-entity ids live in their own band above the
  mobs; mineflayer observed the fall (object 70 spawn, the landing block
  change, the entity destroy) and the torch pop (block change to air + the
  item entity) with its independent protocol stack.

**Tests:** FallingBlockEntityTest (4 — physics, free landing, occupied
landing→drop, void), BlockUpdateSystemTest (5 — conversion, coalescing,
torch pop, decay, glass/torch exceptions), RandomTickSystemTest (3 — decay,
covered-dirt inertness, spread), MobDataStoreTest (4), plus the wire
integration FallingBlockIntegrationTest (2 — the full fall over the socket
incl. object data and landing syncs; the torch pop) — 246 total, 0 failures.
Two stale assumptions were updated with the new behavior: the registry
count (gravel) and the persistence acceptance (a placed stone legitimately
decays the grass it covers).

## 10r. Slice #18 — light propagation (implemented status)

**Status: implemented; 219 automated tests green at commit; mineflayer
real-client validation of torch block light and skylight shade PASSED.**

The §475/§476 lighting became real world state: block light and skylight per
section, the historical model (emission radiates, max(1, filter) per step,
direct sky columns fall without decrement through filter-0 cells), the
community dataset's emitLight/filterLight values (torch 14, glass/chest/
unlit furnace 0, opaque 15), and the wire copying the nibbles verbatim into
the chunk packets with the bitmask extended by non-default light sections.

- **Correctness first (§476 verbatim):** updates run synchronously inside
  the world mutation on the tick thread — the standard add/remove BFS pair;
  NO concurrency, NO batching. Localized propagation, batched updates and
  parallel chunk work remain the documented follow-up investigations.
- **The removal sweep's ghost-light fix:** re-add seeds are POSITIONS whose
  levels are re-read after the sweep finishes — a cell captured as a
  surviving source early can still be removed by a later branch of the same
  sweep, and re-adding a stale level resurrected light (caught by the
  two-source removal test).
- **Initial compute at generation, before publication (§344):** a chunk
  becomes visible only fully generated AND fully lit — direct sky columns,
  emitters, lateral shade, border inflow and shadow re-derivation against
  already-loaded neighbors.
- **Transport:** protocol 47 has no light-only packet; every chunk column
  the engine touched re-sends once per tick through a deduplicated relight
  queue and the RelightListener path.
- **Grass upgraded to the historical light gates** (decay < 4, spread ≥ 9,
  sampled at pos.up() like BlockGrass.updateTick) — the earlier
  "nothing opaque above" approximation is gone, and cave farms light their
  grass like the historical game.

## 10s. Slice #19 — item NBT: display names (implemented status)

**Status: implemented; 228 automated tests green; the rename, the named
drop/collect and the NBT-over-the-wire flows PASSED the mineflayer
real-client validation.**

- **ItemStack** carries the optional displayName component (sanitized:
  control characters stripped, 64-char bound, blank clears), carried by
  every value transformation, and part of the merge identity via
  ItemStack.mergeable — the historical areItemStacksEqual including NBT —
  now played by window clicks, inventory pickup, item-entity merging and
  the furnace output rule.
- **/rename <name...>** stamps the held stack (the anvil rename without the
  anvil); no arguments clears.
- **SlotNbt** (protocol): {display:{Name}} with short-length MODIFIED UTF-8
  (DataOutput.writeUTF semantics — CESU-8, C0 80 nulls), the exact vanilla
  NBT string encoding, distinct from the protocol's varint strings.
  writeSlot emits it only for named stacks; unnamed keeps the TAG_End
  marker. Structural skip + display-name reader for the test client.
- **Persistence:** ZPD v3 / ZCD v2 / ZFD v2 carry the optional name per
  stack (varint-length UTF-8, 0 = none); readers accept all older versions.
- **Wire bug the real client caught:** Spawn Entity object type 1 is a
  BOAT in protocol 47 — item drops are object 2 (Item) with objectData
  item id | (damage << 16). Only an independent protocol parse could see
  it; the engine's own tests never interpret object types.

## 10t. Slice #20 — the downloadable dev build + update prompt (implemented status)

**Status: implemented; the zip boots standalone (verified) and the update
prompt verified live against the published release.**

- **serverDist:** one fat jar (every module + netty), OS start scripts
  (sh/bat/ps1), QUICKSTART and a default configuration, zipped — published
  as a prerelease on the develop branch's releases.
- **UpdateChecker:** on boot a daemon thread resolves the latest release
  tag — the GitHub API first, then the releases.atom feed as the fallback
  (prereleases included, no API rate limit) — and PROMPTS on a newer tag
  with the download page. Non-blocking, 2s timeouts, every failure
  silent-at-FINE: an offline server boots exactly like an online one
  (the §54 observer rule). Versions compare numerically over vX.Y.Z-dev.N.

## 11. Known risks

- 1.8.8 client quirks not obvious from protocol docs (e.g. exact chunk/lighting expectations) — mitigated by scripted-client tests + real client validation.
- Single ordered command queue may become a bottleneck at scale — accepted; revisit at multiplayer slice with measurements.
- Palette-of-shorts chunk storage is not memory-optimal — accepted until profiled (§296).

## 12. Unresolved questions (owners: next experiments)

- Q1: Can the 1.8.8 chunk serializer be produced incrementally (per-section) to avoid full-chunk copies? → measure at multiplayer slice.
- Q2: Where exactly does the engine tick boundary sit vs. immediate world mutation (§534)? → decide at block-update slice.
- Q3: Minestom re-evaluation triggers: which milestone first *needs* its instance/registry infrastructure? → modern-protocol milestone.
