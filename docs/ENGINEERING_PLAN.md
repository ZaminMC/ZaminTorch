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

Explicitly NOT yet: survival mining/drops, item entities, full inventory model, permissions, lighting, mobs, Anvil import, plugin API.

## 11. Known risks

- 1.8.8 client quirks not obvious from protocol docs (e.g. exact chunk/lighting expectations) — mitigated by scripted-client tests + real client validation.
- Single ordered command queue may become a bottleneck at scale — accepted; revisit at multiplayer slice with measurements.
- Palette-of-shorts chunk storage is not memory-optimal — accepted until profiled (§296).

## 12. Unresolved questions (owners: next experiments)

- Q1: Can the 1.8.8 chunk serializer be produced incrementally (per-section) to avoid full-chunk copies? → measure at multiplayer slice.
- Q2: Where exactly does the engine tick boundary sit vs. immediate world mutation (§534)? → decide at block-update slice.
- Q3: Minestom re-evaluation triggers: which milestone first *needs* its instance/registry infrastructure? → modern-protocol milestone.
