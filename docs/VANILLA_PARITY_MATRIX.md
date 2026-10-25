# Vanilla 1.8.8 Parity Matrix — reference-derived coverage by subsystem

Cycle-4 exhaustive-audit deliverable. Coverage states:
`VERIFIED-EQ` (census/count match + tests recorded in the compat ledger) ·
`PARTIAL` (present with named gaps) · `GAP` (absent, ledger row exists) ·
`UNVERIFIED` (not yet checked end-to-end this audit) ·
`INTENTIONAL` (documented divergence). Family-level behavioral depth stays
in `VANILLA_1_8_8_COMPATIBILITY.md`; this matrix reconciles the
reference-derived INVENTORIES against the engine and names what is missing.

Reference inventories derived mechanically from `reference/1.8.8/` this
cycle (entity/block/item/packet/effect class censuses); engine side from
`zamin-core`/`zamin-protocol-v1_8_8` source.

## A. Protocol (domain A)

| Family | Reference census | Engine state | Coverage |
|---|---|---|---|
| C2S packets | 28 classes (1 handshake, 2 query, 2 login, 23 play) | 30 constants; 27/28 mapped 1:1 (all 23 play + handshake + query pair + LOGIN_START) | PARTIAL — `KeyC2SPacket` (encryption response) absent = offline-mode only → ML-020/DEC-001 |
| S2C packets | 77 classes (3 login, 2 query, 72 play) | 67 constants, all IDs verified against protocol 47 | PARTIAL — absent: 0x20 Entity Properties (ML-011), 0x36 Sign Editor Open (ML-003), 0x0A Use Bed (ML-010), 0x41 Server Difficulty (ML-012), 0x43 Camera (ML-018), 0x48 Resource Pack Send (ML-017), 0x22 Multi Block Change + 0x26 Chunk Bulk (ML-019, batching only); login CompressionThreshold handled by FrameCodec (wire form remainder check R-3) |
| Handshake/login/status | handshake → status/login state machine | `V18Connection` state switch verified | VERIFIED-EQ (offline-mode shape) |
| Keepalive/latency | KeepAlive both ways | C2S/S2C KEEP_ALIVE wired | VERIFIED-EQ (timeout behavior remainder R-4) |
| Movement sync | Entity 0x14 + rel/look/teleport/head | all five constants + writers | VERIFIED-EQ |
| Metadata/equipment/effects | 0x1C/0x04/0x1D/0x1E | all wired | VERIFIED-EQ (0x0C metadata encoding suspect — ML-003 cycle-3 lead, wire bytes never captured) |
| Chunk/block sync | 0x21 chunk data, 0x23 block change, 0x24 action, 0x25 break anim, 0x35 BE update | all wired (single-update form) | VERIFIED-EQ minus batching (ML-019) |
| Inventory windows | open/close/set-slot/items/property/confirm | all wired | VERIFIED-EQ (slot-27 rejection unverified — ML-004) |
| Player list/chat/tab | 0x30 PlayerInfo (via PLAYER_INFO), 0x3A tab-complete, header/footer | wired | VERIFIED-EQ |
| Plugin messages | 0x3F/0x17 | wired (MC|TrList rides it) | VERIFIED-EQ |
| Scoreboard | objective/score/display/teams | all wired | VERIFIED-EQ |

## B-D. World, blocks, entities (domains B, C, D)

| Family | Reference census | Engine state | Coverage |
|---|---|---|---|
| Mob families | 32 families (16 monster, 2 boss, 2 golem, 1 ambient, 1 passive, 7 animal, 2 tameable, 1 water) | `MobType` enum: exactly 32 kinds, name-by-name match | VERIFIED-EQ at census level; per-family depth per compat ledger (+ ML-100 tails) |
| Projectiles | 9 kinds (arrow, egg, ender pearl, xp bottle, fireball, potion, small fireball, snowball, wither skull) | `ProjectileEntity.Kind`: exactly 9 | VERIFIED-EQ |
| Special entities | EnderCrystal, EnderEye, ExperienceOrb, FallingBlock, Fireworks, FishingBobber, Item, PrimedTnt | dedicated packages: endcrystal, endereye, experience, FallingBlockEntity, fireworks, fishing, ItemEntity, TNT (fx/explosion) | VERIFIED-EQ (TNT entity presence verified by package census R-1) |
| Vehicles | Boat + 8 minecart variants (base, chest, cmd-block, furnace, hopper, rideable, spawner, tnt) | BoatEntity + all 8 minecart classes | VERIFIED-EQ |
| Decorations | Painting, ItemFrame, LeadKnot, ArmorStand | decoration package + armorstand package + LeadSystem | VERIFIED-EQ |
| Global | LightningBolt | LightningSystem (weather record) | VERIFIED-EQ |
| Attributes | 9 classes; wire 0x20 | engine attributes (Wave J) full; wire absent | PARTIAL → ML-011 |
| AI goals | 49 goal classes + 4 controls + 9 pathing | `MobGoals`/`GoalSelector`/ai.pathing (family-by-family per ledger) | VERIFIED-EQ at family level (per-goal depth = ledger rows; wither exemptions = README pending row) |
| Block states | 153 block classes; full 1.8.8 state space | `BlockRegistryTest` census pin 896 states (full family history: doors/slabs/stairs/wool/glass/carpet/clay/monuments/fortresses/strongholds…) | VERIFIED-EQ at registry level; per-block behavior = ledger rows |
| Worldgen | biomes, genlayers, structures (village/fortress/stronghold/monument), surfaces, spawn tables | landed slice record (VIII14 family; §29 waves) | VERIFIED-EQ per ledger (deterministic-seed remainder R-5) |
| Neighbor updates at unloaded boundaries | vanilla drops into unloaded chunks | not verified | UNVERIFIED → ML-015 |
| Relog remount | vehicle restore on load | `ridingVehicleId` persisted; remount walk unverified | UNVERIFIED → ML-016 |

## E-F. Items, inventory, gameplay (domains E, F)

| Family | Reference census | Engine state | Coverage |
|---|---|---|---|
| Potion effects | 23 effect ids | all 23 names present in PotionSystem | VERIFIED-EQ at census level (per-effect depth = ledger) |
| Enchantments | 21 classes / legacy-id registry | `Enchantments` with legacy ids (PROTECTION=0, SHARPNESS=16, …) | VERIFIED-EQ at registry level (per-ench depth = ledger) |
| Crafting/smelting/brewing/anvil/enchanting tables | recipe families | landed (compat ledger §1; anvil import #31) | VERIFIED-EQ per ledger |
| Items registry | 74 item classes (registry ~440 ids incl. block-items) | item families present (census R-2 remainder: full id-by-id diff not run this cycle) | UNVERIFIED at id level (family coverage per ledger) |
| Commands | ~41 vanilla commands | 74 registered (superset incl. custom); per-command semantic diff = remainder R-6; known divergences: /give (ML-008), /op-offline (ML-005) | PARTIAL |
| Sleep | pose broadcast + all-sleepers walk | single-player contract only (declared) | PARTIAL → ML-010 |
| Spectator | gamemode 3 mechanics | gates + flight only | PARTIAL → ML-018 |
| Combat/hunger/XP | 1.8.8 pipeline | deep record (§29 VIII waves) | VERIFIED-EQ per ledger |
| Scoreboards/teams/stats/achievements | full families | landed (#28 achievements; stats folds) | VERIFIED-EQ per ledger |
| Weather/day cycle/lightning | full | landed (Slice 20b/20c/21c records) | VERIFIED-EQ |

## G-H. Persistence, operations (domains G, H)

| Family | Engine state | Coverage |
|---|---|---|
| Player persistence (ZPD v11), world stores (ZWD/ZSD/ZMP), anvil import, session lock, storage manifest | landed (§31 boundary + wave records) | VERIFIED-EQ per ledger |
| RCON + GS4 Query | landed 1:1 (§32) | VERIFIED-EQ (FIN-WAIT test = ML-002 member) |
| Console (JLine 3, ADR-21) | landed (§30) | VERIFIED-EQ |
| server.properties surface | partial (zamin.properties; --port override issue ML-009; resource-pack rows absent ML-017) | PARTIAL |

## I-J. Security, micro-behavior (domains I, J)

| Check | State | Coverage |
|---|---|---|
| Unknown-packet handling | FINE log + ignore (V18Connection default case); unknown C2S ids CLOSE the connection (observed in R-7's tail) | VERIFIED-EQ (historical form) + R-7 pinned |
| Malformed-packet fuzzing | R-7 run this cycle: 24-case deterministic corpus (one fresh client per case) + seeded tail (Random(474747L), 40 frames) — 4 lawful closes, engine ticked through all, clean join intact | VERIFIED-EQ (MalformedPacketFuzzWireTest 3/0) |
| Login flood | bounded + measured (wave 3, ML-014: zero tick-lane cost at 100 pending; ReadTimeoutHandler(30) first in the pipeline) | RESOLVED (ML-014, wave 3) |
| Packet-size limits / rate limits | the 21-bit frame ceiling (MAX_FRAME 2,097,151 = the 3-byte varint maximum) — the reference's own splitter law (SplitterHandler.java: wider-than-21-bit → CorruptedFrameException); pinned: 4-byte continuation varint closes, ceiling-length partial frame WAITS | VERIFIED-EQ (R-7, MalformedPacketFuzzWireTest) |
| Compression threshold exchange | the engine never performs the 0x03 exchange — its permanent mode is the reference's threshold=-1 shape (the ENGINEERING_PLAN slice decision); the byte-exact no-exchange form pinned at the wire | INTENTIONAL DIVERGENCE, RECORDED + PINNED (R-3, LoginCompressionWireTest 3/0) |
| Micro-level boundary/rounding laws | per-mechanic records in ledger (formulas pinned test-by-test) | VERIFIED-EQ where ledgered (the standing method) |

## Coverage summary and honest remainder

Verified by census this cycle: mob 32/32, projectiles 9/9, vehicles 9/9,
specials 8/8, decorations 4/4, potion effects 23/23, block-state registry
896 (test-pinned), enchantment registry (legacy-id scheme), packet
constants 27/28 C2S + 67/77 S2C with every absence named and ledgered.

Remainder (quantified, NOT claimed complete — R-1..R-7 resolved this cycle, R-8 left):
- R-1: RESOLVED (the lifecycle walk, 1:1 — 0ec68c18): the boundary ray fan
  (the shell's 1352 rays, the retired 4096-cell interior fan gone), the
  two-phase collect-then-commit order (every ray reads the pre-blast world;
  the wounds and their density rays read it too), the wound law
  (feet-distance gate, eye-height direction, the World.getBlockDensity
  attenuation with the reference's own end-cell-terminal DDA, the
  int-truncated quadratic (d10^2+d10)/2*8*f3+1, the Blast Protection 0.15
  shave on the KNOCKBACK scale alone — the retired engine shaved the
  damage), the packet's own dir*d10 motion carry (the retired double
  velocity-send gone), the random.explode 4.0F pair with the reference's
  pitch walk, the fx range law (sounds 16*volume past 1.0, particles 16),
  the detonation origin y + height/16 = 0.06125, the priming hiss at the
  body's feet, the burning-arrow ignition arm (the arrow keeps its flight
  through the opened cell), the creeper's feet-position blast, and the
  nextFloat() <= 1/power drop roll. The smoke trail is NOT owed — the
  server's particle listener is the empty override
  (ServerWorldEventListener lines 23-29); the client renders its own from
  the 0x27. BlastSourceTest re-pinned over the boundary fan (the dirt
  crater 21 -> 7, the creeper-stone 50 -> 1).
- R-2: RESOLVED (defects fixed + pinned): the id-by-id diff vs Item.java's
  init() walk (337 registrations) found 11 wrong wire ids (hopper at the
  prismarine crystals' 410 — the reference registers a BlockItem at 154;
  compass at the saddle's 329 → 345; the seed pair at the hoe ids 292/294
  → 361/362) and 16 registered items with no wire row at all (the food
  family, the lever/button/piston item forms, the oak sapling alias),
  plus the empty bucket's stack size 1 → the reference's 16 — all repaired
  with the reference's own registration lines cited; ItemRegistryParityTest
  4/0 pins the full table, the no-engine-item set (17 ids), the dual
  spellings, and the stack sizes.
- R-3: RECORDED as the standing intentional divergence + pinned: the engine
  never performs the login compression exchange (permanent threshold=-1
  shape, the ENGINEERING_PLAN's own slice decision); the reference's own
  exchange law (ServerLoginNetworkHandler lines 92-101) extracted and the
  byte-exact no-exchange form pinned (LoginCompressionWireTest 3/0).
- R-4: RESOLVED (payload defect fixed + the engine's own stricter kick
  recorded): the payload now the reference's (int) getTimeMillis() form
  (ServerPlayNetworkHandler.tick lines 131-137 — it was a from-scratch
  counter); the reference's own law extracted (no application-level kick,
  ReadTimeoutHandler(30) as the only silent-client drop, no Disconnect
  packet on that path); Torch keeps its §10b design (3× interval, WITH a
  "Timed out" Disconnect packet) — pinned KeepAliveTimeoutWireTest 2/0.
- R-5: RESOLVED (verified + pinned): the same-seed run executed — two
  independent engines over the same derived seed (world-name law,
  NormalWorldGenerator line 113), 25 columns, byte-identical serialized
  columns (bitmask AND bytes). Region-level inapplicable BY DESIGN: region
  files are the import side only (read-only); the native save path is the
  delta pipeline, vacuous for untouched generated chunks — recorded, not
  forced. Known in-process limitation: Layer.init's static (same-seed
  idempotent; different-seed isolation needs a cross-process run).
  WorldgenDeterminismRunTest 1/0 (4 runs green).
- R-6: RESOLVED (surface-verified + pinned): 74 engine verbs vs the
  reference walk's 62 names (59 registrations + the w/msg tell aliases +
  the ? help alias) — intersection 56 (50 level-matched), 6 level
  divergences RECORDED (give 0v2, time 0v2, seed 0v2, kick 2v3, say 2v1,
  defaultgamemode 2v4), 18 extras (the collapsed save, the gamemode
  shortcuts, msg/w, the admin QoL family, spawnmob), 6 missing (the ?
  help alias, publish, save-all/on/off as separate verbs, setidletimeout).
  Form divergences stay ledgered: /give ML-008 (owner decision pending),
  /summon + /spawnmob player-only (cycle-3 papercut), /save collapsed,
  /setspawn the standing-form verb. /op ML-005 resolved (wave 3).
  RegisteredCommandSurfaceParityTest 2/0 pins the exact 74-name set and
  every level. Depth limitation recorded: name/alias/level/grammar
  surface; per-command effect-level semantics stay family-owned.
- R-7: RESOLVED (verified + pinned): the deterministic corpus (24 cases:
  oversized string prefixes, NaN positions, truncated bodies, out-of-range
  enums/slots, unknown ids, a mismatched keep-alive echo) + the seeded tail
  (Random(474747L), 40 frames: 4 lawful closes, 5 connections, engine
  ticked through all, clean join after) + the 21-bit frame boundary
  (4-byte continuation varint closes; the 2,097,151-ceiling partial frame
  WAITS — the reference's own SplitterHandler law).
  MalformedPacketFuzzWireTest 3/0.
- R-8: the compat ledger's per-family "Remaining (ledgered)" tails —
  25 rows (ML-100), family-owned.

These remainders are the next audit increment's queue; none of them is
claimed verified by this matrix.
