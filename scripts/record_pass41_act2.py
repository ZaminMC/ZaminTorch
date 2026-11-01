#!/usr/bin/env python3
"""The forty-first pass's second act: the no-dimension fx census record,
the VT-026/VT-027 ledger rows, the FX ledger row refresh, the queue."""
PATH = "docs/vanilla-conformance-audit.md"
with open(PATH, encoding="utf-8") as f:
    text = f.read()

# 1. The FX ledger row -> the census state.
old_row = "| FX broadcasts — the dimension awareness | NOT INSPECTED (the FxManager position-only API; the PR #90 ledger) |"
new_row = (
    "| FX broadcasts — the dimension awareness | **CENSUSED (the forty-first pass) + TWO "
    "FAMILIES CORRECTED**: the bus itself carries the dimension on every event (Wave "
    "TF-12 — the javadoc's own law, `FxDimensionReadsTest` the forms' record) and the "
    "wire's fan refuses cross-dimension observers (`V18Connection.fxEligible`, verified "
    "in the walk) — the UNCOVERED surface was the CALLERS' overload choice: **32 "
    "no-dimension emit sites** (the overworld-0 stamp), censused engine-wide. **VT-026 "
    "CONFIRMED AND CORRECTED — PR #134 OPEN** (the TNT prime hiss: `TntSystem.ignite` "
    "+ `spawnPrimedAt` — the body carried the acting dimension since FL4-F7 while its "
    "own hiss stamped the overworld; the nether prime silent to the players beside it, "
    "the phantom hiss in the overworld; the seat RED on develop at both arms, the fix "
    "the acting dimension at both sites). **VT-027 CONFIRMED AND CORRECTED — PR #135 "
    "OPEN** (the dig/place commit feedback: the `Commit` record carried NO dimension — "
    "the nether dig's break sound stamped 0, the digger breaking blocks in COMPLETE "
    "SILENCE while the overworld heard the phantom; the fix the record's dimension "
    "field + the closure's three stamps). The remaining census: the redstone-family "
    "sites (noteblock/piston/tripwire) ride the FL4 per-dimension redstone tail (their "
    "systems are overworld-bound today, the stamp consistent with the binding); the "
    "thunder pair CORRECT-BY-BINDING (the lightning strikes over the overworld's "
    "chunks alone — vanilla's no-nether-weather law, the bolt fan already gated 0); "
    "**~20 same-class candidate sites ledgered as the census tail** (the wither-summon "
    "poof, the anvil use/land events, the iron door/trapdoor, the eye-of-ender frame "
    "smoke, the fire placement's portal arm, the double-plant/farmland/cocoa place "
    "sounds, the door world-events, the falling-block land 1022) — each needing its "
    "own three-pass verification of the acting flow's dimension |"
)
assert old_row in text
text = text.replace(old_row, new_row)

# 2. The new ledger rows (after the VT-018 row, before the queue header).
vt018_row_end = text.index("## The queue for the next pass")
new_rows = (
    "| Projectile family — the TNT prime hiss's dimension | **VT-026 CONFIRMED AND "
    "CORRECTED — PR #134 OPEN** (the forty-first pass, three passes): the reference "
    "plays `game.tnt.primed` in the IGNITING/DISPENSING world (`TntBlock.ignite` line "
    "65 + `Bootstrap` lines 329-338 over `World.playSound` 769-773 — that world's "
    "listener list alone); the blast-consumed prime plays NOTHING (`onExploded` 47-54 "
    "— `primeFromBlast` already matches). The engine's `PrimedTnt` body carried the "
    "acting dimension (FL4-F7) while the hiss rode the no-dimension overload — the "
    "overworld 0 stamp. Pass 2 THE SEAT (`TntPrimeHissDimensionDiscoveryTest`, the "
    "full-boot construction over the FL4-F7 seat's own wire): RED on develop at both "
    "arms (the nether flint prime's hiss stamped 0, the dispensed arm's stamped 0; "
    "the controls green — the prime landed, the body carrying -1); pass 3 the walk "
    "(the wire's fan gate at `fxEligible`; the `game.tnt.primed` census — the two "
    "sites alone; the FL4-F7 body-stamp contrast). **PR #134** "
    "(`fix/tnt-prime-hiss-dimension-parity-audit` over develop `149eb1f0`): both "
    "sites pass `actingWorld.dimension()`; the seat 2/0 GREEN, the tnt/explosion/"
    "redstone lanes 25/0 |\n"
    "| Interaction family — the dig/place commit feedback's dimension | **VT-027 "
    "CONFIRMED AND CORRECTED — PR #135 OPEN** (the forty-first pass, three passes): "
    "the reference's break feedback (the 2001 event the client plays the dig sound "
    "from, `tryMineBlock` line 211's `this.world.doEvent`) and the place sound "
    "(`BlockItem.place` lines 53-60) ride the DIG WIRE'S OWN WORLD. The engine's "
    "writes routed the acting world since FL4-F5 while the `Commit` record carried NO "
    "dimension — the feedback closure's three stamps all overworld 0: the nether "
    "digger broke blocks in COMPLETE SILENCE (the phantom dig in the overworld), the "
    "place arm the same. Pass 2 THE SEAT (`NetherDigSoundDimensionDiscoveryTest`, the "
    "full-boot construction over the FL4-F5 seat's own wire): RED on develop at both "
    "arms (the dig sound stamped 0, the place's family sound stamped 0); pass 3 the "
    "walk (the compensation hunt NEGATIVE — the block-change packet plays nothing in "
    "1.8, the `blockShatter` encodes to particles, the named sound the ONLY source; "
    "the Commit census — one constructor, one consumer). **PR #135** "
    "(`fix/dig-place-feedback-dimension-parity-audit` over develop `149eb1f0`): the "
    "record gains the dimension (the acting world the commit already receives), the "
    "closure reads it at both arms; the seat 2/0 GREEN (the hardened awaits — no "
    "same-tick race), the dig family lanes 16/0 |\n\n"
)
text = text[:vt018_row_end] + new_rows + text[vt018_row_end:]

# 3. The queue: add the new items after item 7b.
anchor = "their claim lives on their audit branch\n   alone."
idx = text.index(anchor) + len(anchor)
queue_add = (
    "\n\n8. **THE NO-DIMENSION FX CENSUS TAIL** (the forty-first pass's wide act, the\n"
    "   FX ledger row the record): ~20 same-class candidate sites — each an fx emit\n"
    "   riding the no-dimension overload on a flow that CAN act in a foreign\n"
    "   dimension. The named heads, by likelihood: the wither-summon poof (the\n"
    "   nether wither fight the classic case), the falling-block land event 1022\n"
    "   (the nether sand/gravel drops), the anvil use/land events (1021/1022), the\n"
    "   iron door/trapdoor sounds, the fire placement's portal arm, the\n"
    "   double-plant/farmland/cocoa place sounds, the eye-of-ender frame smoke, the\n"
    "   door world-events. Each needs its own three-pass verification (the acting\n"
    "   flow's dimension availability is the per-site question); the redstone-family\n"
    "   sites (noteblock/piston/tripwire) stay behind the FL4 per-dimension redstone\n"
    "   tail. **VT-026 and VT-027 corrected whole this pass (PRs #134/#135)** — the\n"
    "   two verified families the pattern's proof."
)
text = text[:idx] + queue_add + text[idx:]

# 4. Append the pass section.
pass_section = """

## The forty-first pass, second act — the wide work: the engine-wide no-dimension FX census, VT-026 and VT-027 confirmed whole and corrected (PRs #134/#135)

The pass-37 close named the dimension tails the wide head; the FX row was NOT
INSPECTED. The entry point: the FxManager's own javadoc names the risk verbatim
— "The no-dimension emit overloads stamp the overworld 0" — so the uncovered
surface is exactly the CALLERS' overload choice.

**THE CENSUS** (`scripts/fx_census.py`, the engine-wide parse): **111 fx emit
sites, 32 on the no-dimension overloads**. The classification:
- **VT-026 — the TNT prime hiss** (2 sites: `TntSystem.ignite` +
  `spawnPrimedAt`): CONFIRMED and corrected below. The dimension was AVAILABLE
  at both sites (the acting world parameter) — the purest form of the defect.
- **VT-027 — the dig/place commit feedback** (EngineServer's closure, 3 emits):
  CONFIRMED and corrected below. The `Commit` record carried no dimension at
  all — the fix threads the acting world's own.
- **The redstone family** (noteblock 403, piston 883, tripwire 307-313/343):
  their systems are overworld-bound today (the FL4 ledger's named per-dimension
  redstone tail — the TntSystem javadoc's own citation); the 0 stamp is
  consistent with the binding until that pass lands.
- **The thunder pair** (1679/1681): CORRECT-BY-BINDING — the lightning strikes
  over the overworld's loaded chunks alone (vanilla's no-nether-weather law),
  the bolt fan already gated `dimension() == 0`.
- **~20 same-class candidates** ledgered as the census tail (queue item 8):
  the wither-summon poof, the falling-block land 1022, the anvil events, the
  iron door/trapdoor, the fire placement, the place-family direct sounds
  (double-plant/farmland/cocoa), the eye-of-ender frame smoke, the door
  world-events.

**VT-026 — THE TNT PRIME HISS'S DIMENSION** (three passes):
- Pass 1 (source): `TntBlock.ignite` line 65 plays the hiss on the IGNITING
  world's `playSound` — which iterates THAT world's listener list alone
  (`World.playSound` 769-773); the Bootstrap dispenser arm the same (line 335,
  `source.getWorld()`); the blast prime plays NOTHING (`onExploded` 47-54 — the
  engine's `primeFromBlast` matches, a verified negative).
- Pass 2 (the seat, `TntPrimeHissDimensionDiscoveryTest`): the full-boot
  construction over the FL4-F7 seat's own wire — a nether flint-and-steel use
  primes the NETHER's TNT. **RED on develop at both arms**: the hiss stamped
  **0** (expected -1), the dispenser arm the same; the controls green (the
  prime landed, the body carrying -1 — the FL4-F7 law, the very contrast the
  hiss's stamp breaks).
- Pass 3 (the walk): the wire's fan gate verified (`fxEligible` refuses the
  cross-dimension observer — the stamp's observable consequence: the nether
  players beside the TNT hear NOTHING, the overworld hears the phantom); the
  `game.tnt.primed` census (the two sites alone); the body-stamp contrast.
- **THE CORRECTION — PR #134 OPEN** (`fix/tnt-prime-hiss-dimension-parity-audit`
  over develop `149eb1f0`): both sites pass `actingWorld.dimension()`. The
  seat 2/0 GREEN (the flint arm hardened — the hiss's landing awaited with the
  body's, no same-tick race); the tnt package + NetherTntRouting + the
  explosion/redstone lanes 25/0.

**VT-027 — THE DIG/PLACE COMMIT FEEDBACK'S DIMENSION** (three passes):
- Pass 1 (source): the reference's break feedback — `tryMineBlock` line 211:
  `this.world.doEvent(this.player, 2001, pos, ...)` — rides the interaction
  manager's OWN world (the dig wire's), the client playing the dig sound from
  that packet; the place sound (`BlockItem.place` 53-60) the same world. The
  engine's `Commit` record census: ONE constructor site, ONE consumer closure.
- Pass 2 (the seat, `NetherDigSoundDimensionDiscoveryTest`): the full-boot
  construction over the FL4-F5 seat's own wire — `submitMiningStart`, the dig
  clock, `submitMiningFinished`; a nether place through the real use wire.
  **RED on develop at both arms**: the dig sound stamped **0** (expected -1),
  the place's family sound stamped 0.
- Pass 3 (the walk): the compensation hunt NEGATIVE — the block-change packet
  plays nothing in 1.8 (the client's break sound comes from the 2001 event
  alone), the engine's `blockShatter` encodes to PARTICLES (verified in
  `encodeFx`), the named sound the ONLY sound source; the wire's fan gate; the
  sibling finding's boundary (VT-026 the TNT hiss, its own PR).
- **THE CORRECTION — PR #135 OPEN** (`fix/dig-place-feedback-dimension-parity-audit`
  over develop `149eb1f0`): the `Commit` record gains the dimension (the one
  constructor site passes `world.dimension()` — the acting world the commit
  already receives); the closure reads it at both arms (the shatter + the dig
  sound; the placing sound). The seat 2/0 GREEN; the dig family lanes 16/0
  (NetherDigRouting's FL4-F5 law, BlockInteractionAcceptance, DigHandlerGates).

**The act's tally**: one wide census (111 sites parsed, 32 no-dimension, the
classes named); TWO findings taken from uninspected to corrected whole (the
seats RED on develop at the traced stamps, the fixes minimal, the PRs #134 and
#135); the seats riding this lane beside their fix branches; the census tail
the next pass's named head.
"""
text = text.rstrip("\n") + pass_section

with open(PATH, "w", encoding="utf-8") as f:
    f.write(text)
print("report updated:", len(text.splitlines()), "lines")
