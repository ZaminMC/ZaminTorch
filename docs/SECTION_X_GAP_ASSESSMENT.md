# Section X Architecture Gap Assessment

**Status date:** 2026-10-31
**Auditor:** Abasing
**Scope:** the engine's compliance with Torch-Arcetecture.txt §X (the
forward-compatible engine architecture), inspected against the actual
`develop` tree at 735737c1.
**Method:** source inspection of every area §X names, classified under
§X.13's version-fidelity gate (A shared infrastructure / B
forward-compatible representation / C 1.8.8 gameplay / D 1.8.8
compatibility adapter / E future-version implementation). No code was
changed by this audit.

---

## 1. What already satisfies the forward-compatible requirement

These are verified compliant; they are listed so future waves do not
re-litigate them.

### Canonical identifiers (B — compliant by construction)

`net.zaminmc.torch.util.Identifier` (zamin-api) is the engine's stable
namespaced identity, and its javadoc already carries §X's own law:
"Numeric or protocol-specific representations must never replace them
inside the engine; those belong to version adapters as translation
data." Every registry, block type, item type, and persistence key is
Identifier-keyed.

### Block/state representation (B — the flattened-family form)

`EngineBlockType` is an immutable Identifier-keyed per-state type; the
optional `legacyMetadata` field is documented as boundary translation
data ("it never leaves the boundary"). The "flattening" direction §X.5
mandates — namespaced, extensible, not `int blockId`/`byte metadata` —
is the engine's existing model: state families are per-state types
(`rail_ew`, `oak_stairs_east_top`, crop stages), and the wire's numeric
form exists only in the adapters. No new engine system may introduce an
`int blockId` API; none was found in a sweep of `zamin-core`'s main
sources.

### Registry architecture (A — frozen-at-boot, extensible pre-freeze)

`ItemRegistry` (zamin-api) is frozen before gameplay begins and keyed
by Identifier; `BuiltinBlocks`/the block registry follow the same
shape (the registry-count integrity pins exist to catch drift, not to
freeze growth). A later version adds entries before the freeze rather
than replacing the registry system — §X.7's requirement.

### Protocol isolation (D — clean)

The dependency direction is verified clean:
`zamin-core → zamin-api` only; `zamin-protocol-v1_8_8 → core+api+transport`;
`zamin-launcher → all`. `zamin-core`'s main sources import nothing from
the 1.8.8 protocol module (the one `net.zaminmc.torch.protocol.*`
import is the API module's own packet-listener surface). The entire
legacy id/metadata table (`LegacyBlockIds`) lives in the protocol
module as translation data at the boundary — exactly §X.10's
preference.

### World-storage translation (D — compliant, one coupling noted)

The Anvil import's numeric-state table (`AnvilBlockIds`, in core's
anvil package) is reverse-derived from the protocol module's table and
pinned by `AnvilBlockIdsIntegrityTest` in the protocol module. Unknown
numeric states import as air with a counted isolation — never a silent
substitute. NOTE: storage and protocol are distinct boundaries in later
versions (a 1.13+ storage adapter would not share the protocol-47
table); today's derivation is correct for the only existing version but
the two tables should be understood as independently-owned when the
second version arrives.

### Ownership, scheduling, concurrency (A — the strongest area)

`OwnershipDomain`, `SimulationScheduler`, the `ComputeSubsystem`, the
`RegionPartition` with boundary scopes, and the tick pipeline
(`docs/CONCURRENCY_ARCHITECTURE.md`, phases 1-4) are version-independent
infrastructure by design. Nothing in them assumes 1.8.8 protocol or
content.

### Module layering (X.8's version layers — the shape exists)

`zamin-transport` (wire-agnostic movement), `zamin-protocol-v1_8_8`
(the 1.8.8 adapter layer), `zamin-bukkit` (a second adapter family),
`zamin-api` (the stable face) — the layer boundaries §X.8 describes are
already the module boundaries. A future 1.9 layer would be a new module
beside `zamin-protocol-v1_8_8`, not a rewrite.

---

## 2. Genuine architectural gaps

Ordered by severity. The first two are **correctness defects today**
(the FL4 ledger's named tails), not merely forward-compat debts; the
rest are named gaps with explicit trigger conditions, per §X.8's "do
not create artificial abstractions" and the owner's "build foundations
when there is a real architectural requirement".

### Gap 1 — the redstone system is overworld-bound (severity: gameplay
correctness in the nether/End; the ledger's FIRST remaining tail)

`RedstoneSystem` is constructed over the overworld alone
(`new RedstoneSystem(world, itemEntities)` at the boot wiring) and
registered as the OVERWORLD's change listener only. The nether and the
End — which since FL4-F8 carry their own `BlockUpdateSystem` instances
— carry NO signal machinery: a nether lever's flip, a nether
comparator's read, a nether repeater's delay have no system behind
them. The FL4-F7 wave already routed the TNT family's WRITE arms per
dimension while leaving "the SIGNAL consult gated to the system's own
redstone binding until that family's per-dimension pass" — this gap.
Additionally the plate probes close over the global entity walks
(`mobs.all()`, `players.all()`, `itemEntities.all()`) without a
dimension filter, so a nether pressure plate consults overworld bodies
(the pre-FL4 defect class), and the comparator/lever/button USE
consults are named in the FL4-F3 ledger as overworld-closing.

**Classification:** C (1.8.8 gameplay) over an ownership defect.
**Disposition:** the next wave — trace-first, per §5 of the governing
brief. The reference's own law is per-world signal machinery
(MinecraftServer's per-world tick loop carries each world's
updateBlocks/redstone walk).

### Gap 2 — the container/block-entity stores are position-keyed without
dimension (severity: cross-dimension state collision; the ledger's
SECOND remaining tail)

`ChestManager` keys `Map<BlockPosition, ChestBlockEntity>`; the
dispenser system's container map, and the other block-entity families'
stores, follow the same shape. Two dimensions' containers at the same
coordinates are THE SAME MAP ENTRY — a nether chest's contents
overwrite an overworld chest's (and the persistence layer
`ChestDataStore.save(Map<BlockPosition, ...>)` serializes the same
collided shape). The FL4/TF waves already fixed this class of defect
for entities (dimension stamps riding bodies, per-dimension filtered
walks); the container families never received their pass.

**Classification:** C over an ownership defect (the persistence seam
additionally D).
**Disposition:** the wave after redstone, per the ledger's order.
Investigate first (the §4.2 list: inventories, viewers, item movement,
persistence, protocol adapters) — the fix shape is likely the FL4
dimension-stamped-key form, not "a dimension field everywhere".

### Gap 3 — ItemStack's fixed record fields (severity: forward-compat
debt with a named trigger)

`ItemStack` models each historical NBT slice as another record field:
`displayName`, `enchantments`, `unbreakable`, `repairCost`, `hideFlags`,
`lore`, `blockEntityTag`. §X.6 warns against exactly this pattern ("an
ItemStack should not require every future item property to be added as
another special field"). Every field is 1.8.8-reference-faithful TODAY
(classification C/D per field), so nothing is wrong for 1.8.8 — but the
first later-version item property that is not a 1.8.8 NBT slice would
require widening the record and every one of its construction sites.

**Classification:** B-gap (the forward-compatible component seam does
not exist yet).
**Disposition: DO NOT BUILD NOW.** The trigger is concrete: when a
second Minecraft version's item property cannot be represented as a
1.8.8 NBT slice, introduce the component-map seam AT THAT POINT, with
the existing fields becoming the 1.8.8 adapter's components. Building
it now would be the "artificial abstraction" §X.8 forbids — the current
record is honest, tested, and exactly as extensible as 1.8.8 requires.

### Gap 4 — the fixed three-dimension model (severity: forward-compat
debt with a named trigger)

`EngineServer.worldFor(int)` hardcodes `-1`/`0`/`1`; the worlds are
three named fields; the dimension set is fixed at compile time. §X.7
lists dimensions among the registries that must be extensible. 1.8.8
has exactly three vanilla dimensions, so the gameplay (C) is correct;
but a later version with custom dimensions would need the world-set
redesigned.

**Classification:** B-gap (a dimension registry does not exist).
**Disposition: DO NOT BUILD NOW.** Same trigger discipline: the first
requirement for a non-vanilla dimension (or a version whose dimension
set differs) introduces the registry and migrates the three fields. The
FL4 waves' `worldFor` router seam is the natural migration point — all
per-dimension work already routes through it.

### Non-gaps, explicitly recorded (so they are not re-raised)

- **Per-state block types without a property-table abstraction** —
  §X.5 permits the flattened-family form; typed property tables would
  be an alternative B-representation, not a required one. The current
  model already satisfies "do not build new systems around
  `int blockId`/`byte metadata`".
- **The 20 Hz single-lane tick with region ownership** — A-class
  infrastructure; the parallel per-region execution lane is gated on
  measurement per the concurrency ADRs, which is the §X-correct posture
  (no concurrency without demonstrated justification).
- **The bukkit adapter's absence from the runtime distribution** — a
  deliberate owner decision (DEC-002), recorded in its build script.

---

## 3. The audit's effect on the wave queue

No wave order changes. The two correctness gaps (1: redstone, 2:
containers) were already the ledger's named remaining tails; this audit
confirms they are the ONLY foundational defects in the §X sense, and
that the representation layer (identifiers, registries, blocks,
protocol, storage) is already the forward-compatible shape §X mandates.
The two named B-gaps (3: item components, 4: dimension registry) carry
their triggers and must not be built before those triggers fire.
