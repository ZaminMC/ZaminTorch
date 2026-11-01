#!/usr/bin/env python3
"""The forty-first pass's third act + the run's close: VT-028's record,
the ledger row, the queue refresh, the closing fetch."""
PATH = "docs/vanilla-conformance-audit.md"
with open(PATH, encoding="utf-8") as f:
    text = f.read()

# 1. The VT-028 ledger row (after the VT-027 row).
vt027_anchor = "the seat 2/0 GREEN (the hardened awaits — no same-tick race), the dig family lanes 16/0 |"
idx = text.index(vt027_anchor) + len(vt027_anchor)
vt028_row = (
    "\n| Falling family — the anvil's land sound's dimension | **VT-028 CONFIRMED AND "
    "CORRECTED — PR #136 OPEN** (the forty-first pass, three passes — the census tail's "
    "first head): the reference's landing places the block SILENTLY (setBlockState flag "
    "3) and calls the block's OWN hook — the BASE FallingBlock's hook is EMPTY (76-77: "
    "the ordinary sand/gravel landing fires NOTHING), the ANVIL's override alone firing "
    "`world.doEvent(1022, pos, 0)` on the falling body's OWN WORLD (AnvilBlock 89-91 — "
    "the per-world listener list; the client answers random.anvil_land). The engine's "
    "falling body carried its dimension since FL4-F9 while the land sound rode the "
    "no-dimension overload — the overworld 0 stamp. Pass 2 THE SEAT "
    "(`FallingAnvilLandDimensionDiscoveryTest`, the full-boot construction over the "
    "REAL gravity chain): RED on develop at the traced pin (the land sound stamped 0, "
    "expected -1); THE SCOPE GUARD green (the ordinary SAND landing plays NOTHING — the "
    "base hook's empty law); pass 3 the walk (the body-stamp contrast, the "
    "onFallingEnded census, the wire's fan gate). **PR #136** "
    "(`fix/anvil-land-dimension-parity-audit` over develop `149eb1f0`): one line — "
    "`entity.dimension()` rides the sound; the seat 2/0 GREEN, the falling/anvil/"
    "gravity families 109/0 |"
)
text = text[:idx] + vt028_row + text[idx:]

# 2. The queue: item 8 — the falling-block head DONE, the tail refined.
old_8 = text[text.index("8. **THE NO-DIMENSION FX CENSUS TAIL**"):]
old_8 = old_8[:old_8.index("\n\n")]
new_8 = (
    "8. **THE NO-DIMENSION FX CENSUS TAIL** (the forty-first pass's wide act): ~17\n"
    "   remaining same-class candidate sites after VT-028 took the falling-block\n"
    "   head (PR #136 — the anvil's land sound, corrected whole this pass). The\n"
    "   named heads, by likelihood: the wither-summon poof (the nether wither fight\n"
    "   the classic case), the anvil use event 1021 (the anvil UI's own sound), the\n"
    "   iron door/trapdoor sounds, the fire placement's portal arm, the\n"
    "   double-plant/farmland/cocoa place sounds, the eye-of-ender frame smoke, the\n"
    "   door world-events. Each needs its own three-pass verification (the acting\n"
    "   flow's dimension availability the per-site question); the redstone-family\n"
    "   sites (noteblock/piston/tripwire) stay behind the FL4 per-dimension redstone\n"
    "   tail. **VT-026, VT-027 and VT-028 corrected whole this pass (PRs #134/#135/"
    "#136)** — the three verified families the pattern's proof."
)
text = text.replace(old_8, new_8)

# 3. Append the third act + the run's close.
pass_section = """

## The forty-first pass, third act — the census tail's first head: VT-028 confirmed whole and corrected (PR #136), and the run's close

**VT-028 — THE FALLING ANVIL'S LAND SOUND'S DIMENSION** (three passes, the
queue item 8's named first head):
- Pass 1 (source): the reference's landing walk (`FallingBlockEntity.tick`
  90-104) places the block SILENTLY (`setBlockState(blockpos1, this.state, 3)`
  — no event) and calls the block's OWN hook; the BASE `FallingBlock`'s hook
  is EMPTY (lines 76-77 — the ordinary sand/gravel landing fires NOTHING);
  the ANVIL's override alone (`AnvilBlock` 89-91) fires
  `world.doEvent(1022, pos, 0)` on the falling body's OWN world — the
  per-world listener list alone, the client answering `random.anvil_land`.
- Pass 2 (the seat, `FallingAnvilLandDimensionDiscoveryTest`): the full-boot
  construction over the REAL gravity chain — `GravityFalls.tryFall` (the
  engine's verified Slice-470 shape), the manager's physics, the
  FL4-F9-routed landing, the event dispatch. **RED on develop at the traced
  pin**: the land sound stamped **0** (expected -1). THE SCOPE GUARD green:
  an ordinary SAND landing plays NOTHING (the base hook's empty law — the
  fix must never widen the sound to the sand family).
- Pass 3 (the walk): the body-stamp contrast (the FL4-F9 body's physics and
  landing consult the body's world — the sound alone lagged); the
  `onFallingEnded` census (the dispatch re-fans to the falling listeners, no
  other sound); the wire's fan gate (established at `fxEligible`).
- **THE CORRECTION — PR #136 OPEN** (`fix/anvil-land-dimension-parity-audit`
  over develop `149eb1f0`): ONE line — `entity.dimension()` rides the sound,
  the same stamp the body's physics and landing already carry. The seat 2/0
  GREEN; the falling/anvil/gravity families 109/0.

**THE RUN'S TALLY (the forty-first pass, three acts)**:
- **VT-018 completed whole** — the entity-hit bowhit arm (the pass-37 close's
  named follow-up): the seat RED on develop, the pass-3 walk (the hurt-sound
  contrast the decisive evidence), and **PR #124 WIDENED** to both impact
  arms (all five entity sites, the landed-verdict gate, the `playerHit`
  default-bridge evolution; the seats 4/0, the regression 96/0).
- **THE VT-024 SECOND COLLISION RESOLVED** (the public-first protocol): #131
  owns VT-024; the pass-38 lane's arrow pickup envelope renumbers **VT-025**
  (provenance-marked, nothing public renamed).
- **THE WIDE WORK — the engine-wide no-dimension FX census**: 111 emit sites
  parsed, 32 on the overworld-0 overloads; the classes named (the redstone
  family behind the FL4 tail; the thunder pair correct-by-binding; ~17
  same-class candidates the ledgered tail).
- **THREE census families taken from uninspected to corrected whole**:
  VT-026 (the TNT prime hiss — **PR #134**, the seat RED at both arms, the
  fix the acting dimension at both sites), VT-027 (the dig/place commit
  feedback — **PR #135**, the `Commit` record's dimension, the closure's
  three stamps), VT-028 (the anvil's land sound — **PR #136**, one line).
- **THE CLOSING FETCH**: 25 PRs open (the new #134/#135/#136 among them);
  #127 closed unmerged (a concurrent lane's); no carries merged this window;
  develop still `149eb1f0`.

**THE NEXT HEADS** (the forty-second pass's queue): the census tail's
remaining ~17 sites (item 8 — the wither poof the likeliest), F-SP1's
behavioral seats (the pass-38b lane's named first act — theirs unless they
stall), VT-025's Phase E (the pass-38 lane's own named head), and the
standing merge watch (the maintainer's pick among the convergent carriers:
#117/#122 the placement, #120 the un-stick, #123/#128/#131 the TNT/piston
family).
"""
text = text.rstrip("\n") + pass_section

with open(PATH, "w", encoding="utf-8") as f:
    f.write(text)
print("report updated:", len(text.splitlines()), "lines")
