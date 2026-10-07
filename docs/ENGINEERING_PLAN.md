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
| **PrismarineJS/minecraft-data** (MIT, 948★, active) | **ADOPTED**: block behavior data (hardness/drops/materials for pc/1.8) embedded as a generated table with attribution; protocol 47 packet layouts and the position bitfield verified against it — it caught a real wire bug (packed position x:26|y:12|z:26) | Every new data need first |
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

Explicitly NOT yet: inventory UI click handling (window clicks are
accepted-and-ignored with authoritative re-sync), crafting, item NBT beyond
durability damage, player-data persistence, item merging, physics beyond
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

## 11. Known risks

- 1.8.8 client quirks not obvious from protocol docs (e.g. exact chunk/lighting expectations) — mitigated by scripted-client tests + real client validation.
- Single ordered command queue may become a bottleneck at scale — accepted; revisit at multiplayer slice with measurements.
- Palette-of-shorts chunk storage is not memory-optimal — accepted until profiled (§296).

## 12. Unresolved questions (owners: next experiments)

- Q1: Can the 1.8.8 chunk serializer be produced incrementally (per-section) to avoid full-chunk copies? → measure at multiplayer slice.
- Q2: Where exactly does the engine tick boundary sit vs. immediate world mutation (§534)? → decide at block-update slice.
- Q3: Minestom re-evaluation triggers: which milestone first *needs* its instance/registry infrastructure? → modern-protocol milestone.
