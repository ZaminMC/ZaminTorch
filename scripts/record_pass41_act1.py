#!/usr/bin/env python3
"""The forty-first pass's report updates: the VT-018 ledger row, the queue
items, the numbering-collision bookkeeping, and the pass section appended."""
import re

PATH = "docs/vanilla-conformance-audit.md"
with open(PATH, encoding="utf-8") as f:
    lines = f.readlines()

text = "".join(lines)

# 1. The VT-018 ledger row -> CORRECTED (both arms, PR #124 widened).
old_row_start = "| Projectile family — the stick's bowhit sound |"
idx = next(i for i, l in enumerate(lines) if l.startswith(old_row_start))
new_row = (
    "| Projectile family — the stick's bowhit sound | **VT-018 CONFIRMED AND "
    "CORRECTED — PR #124 OPEN, BOTH ARMS, three passes each** (the pass-35 "
    "lane's source walk + the thirty-ninth's stick seat and independent "
    "challenge + the forty-first's entity-hit seat and walk): the reference "
    "plays `random.bowhit` at BOTH impact arms — the BLOCK stick (ArrowEntity "
    "line 303, at the rest position, the pitch `1.2F/(rand·0.2+0.9)` band "
    "[1.0909, 1.3333], one draw per stick) and the ENTITY hit (line 276, "
    "INSIDE the takeDamage-true branch alone, before the remove, at the "
    "arrow's pre-integration position; the failed-damage branch the SILENT "
    "bounce); the engine played NEITHER — the stick arm fired the no-op "
    "`onProjectileLanded` fan alone, `hitMob`/`hitPlayer`/`hitVehicle` "
    "soundless, `random.bowhit` at the tripwire alone. The stick seat RED on "
    "develop (COMPLETE SILENCE); the entity-hit seat RED on develop "
    "`149eb1f0` (the captured `[]` at a landed mob hit, the controls green); "
    "the HURT-SOUND CONTRAST the entity arm's decisive pass-3 evidence (the "
    "mob's own hurt sound IS wired — `onMobHurt` → `sendMobSound` — the "
    "hit's audio pair exists in halves, the bowhit alone missing: the pop "
    "contrast's twin). **PR #124** (`fix/arrow-stick-bowhit-sound-parity-audit` "
    "over develop `777b7c86`, widened at `01783eeb`): the stick arm's sound "
    "+ the shared `playEntityHitBowhit` (the ARROW kind gate, the `from` "
    "position, the landed-verdict gate) at ALL FIVE entity sites (mob, "
    "player, vehicle, crystal, dragon part), the `CombatSink`'s "
    "projectile-carrying `playerHit` evolved to boolean through the "
    "default-bridge pattern; the seats GREEN (the stick 1/0 + the entity-hit "
    "3/0 with the gate and kind guards); the regression 96/0. The named "
    "adjacent tails: the failed-damage BOUNCE (the reference bounces, the "
    "engine removes) and the ENDERMAN arrow-survives exception (line 277) |\n"
)
lines[idx] = new_row

# 2. Queue item 3a -> the entity-hit follow-up DONE by the forty-first.
old_3a = text[text.index("3a. ~~VT-018's passes 2-3~~"):text.index("4. The merge watch:")]
new_3a = (
    "3a. ~~VT-018's passes 2-3~~ — **DONE (the thirty-ninth pass)**: the\n"
    "   stick's FX-assert seat RED in complete silence + the pass-3 walk\n"
    "   that found the SECOND SITE — and **the entity-hit follow-up DONE\n"
    "   WHOLE (the forty-first pass, this lane)**: the seat RED on develop\n"
    "   `149eb1f0` (`ArrowEntityHitBowhitSoundDiscoveryTest` — the landed mob\n"
    "   hit's captured stream `[]`, the gate guard + the kind guard), the\n"
    "   pass-3 walk (the hurt-sound contrast, the position law, the verdict\n"
    "   mapping, the enderman nuance), and **PR #124 WIDENED** (`01783eeb`:\n"
    "   the entity-hit arm at all five sites, the landed-verdict gate, the\n"
    "   `playerHit` default-bridge evolution; the seats 4/0 GREEN on the\n"
    "   tree, the regression 96/0). **VT-018 CORRECTED WHOLE** — the two\n"
    "   adjacent tails ledgered (the failed-damage bounce, the enderman\n"
    "   arrow-survives exception), never the PR's scope.\n"
)
text = text.replace(old_3a, new_3a)

# 3. The numbering-collision bookkeeping: item 7b after 7a.
seven_a = text[text.index("7a. **THE #131 VT-023 COLLISION**"):]
seven_a = seven_a[:seven_a.index("\n\n")]
new_7b = seven_a + (
    "\n\n7b. **THE VT-024 SECOND COLLISION** (the forty-first pass's\n"
    "   bookkeeping): the pass-38 lane's close (`1b42d140`) claims VT-024 for\n"
    "   the ARROW PICKUP ENVELOPE (the horizontal 1.25 cap vs the reference's\n"
    "   grown-box 1.55, the shifted vertical band — three passes complete,\n"
    "   Phase E their named next head), but the public-first protocol gives\n"
    "   VT-024 to **#131** (the piston toBreak drop consult, PR-opened\n"
    "   06:48 before any arrow-pickup PR exists — item 7a's resolution).\n"
    "   **The arrow pickup envelope renumbers to VT-025** (the next free\n"
    "   number, provenance-marked: the pass-38 lane's record `92fe4440` the\n"
    "   evidence base; their Phase E branch will carry the number). No\n"
    "   public artifact renamed — their claim lives on their audit branch\n"
    "   alone."
)
text = text.replace(seven_a, new_7b)

# 4. Append the forty-first pass's section.
pass_section = """

## The forty-first pass, first act — VT-018's named follow-up completed whole: the ENTITY-HIT bowhit seated, walked, and PR #124 widened to both impact arms

The pass-37 close named this head; the pass-39 walk discovered the site and
the review note on #124 carried it. This lane took it up (the concurrent
lanes' claims checked first: the pass-38 lane holds the arrow-pickup
envelope's Phase E, the pass-38b lane holds F-SP1's behavioral seats, the
pass-40 lane holds the piston family — no collision).

**THE LAW (ArrowEntity lines 230-287, re-walked for this act)**: the
entity-hit arm plays `random.bowhit` at line 276 — INSIDE the
`takeDamage(...) == true` branch ALONE (a landed hit), BEFORE the
`remove()`, at the arrow's PRE-INTEGRATION position (the entity-hit arm
never moves the arrow; the position integration at 328 runs after). The
FAILED-damage branch (line 280's else) is SILENT — the arrow bounces
(`velocity *= -0.1`, the yaw flip, `inAirTicks = 0`). The bowhit is the
ARROW family's alone (SnowballEntity/EggEntity/the fireballs play no such
name on their hits).

**THE SEAT (pass 2, `ArrowEntityHitBowhitSoundDiscoveryTest`, three
tests)** — the mob-hit construction (the pass-37 close's named shape):
an arrow dropped onto a zombie through the rigged box resolver, the
combat sink's landed verdict the gate:
- **THE PIN, RED on develop `149eb1f0`** at the traced assert: the
  captured FX stream at a LANDED mob hit is **`[]`** — the entity hit in
  COMPLETE SILENCE, the controls green (the strike consulted through the
  sink, the arrow left the world on the hit);
- **THE GATE GUARD**: the failed-damage construction (the sink's
  `landed=false`) pins the reference's silent bounce branch — the fix must
  gate on the verdict, never play unconditionally;
- **THE KIND GUARD**: the snowball construction through the same box — the
  engine's `hitMob` serves EVERY projectile kind; the name is
  ArrowEntity's alone.

**THE PASS-3 WALK (the independent angles)**:
- **The consumers' census**: `onProjectileRemoved`'s only implementations
  are destroy fans (the protocol's + EngineServer's re-fan);
  `ProjectileManager`'s single `fx.sound` is the shatter family's
  `random.glass` (1449) — nothing can carry the entity-hit sound.
- **THE HURT-SOUND CONTRAST** (the entity arm's decisive evidence, the
  stick arm's pop-contrast twin): the mob's OWN hurt sound IS wired
  (`onMobHurt` → `sendMobSound(mob.hurtSound(), 1.0, 1.0)`) — the hit's
  audio pair exists in halves in the engine, the bowhit alone missing.
  The one-by-one family pattern again.
- **THE POSITION LAW**: the reference plays at the arrow's pre-move
  position; the engine's `from` (the per-tick origin `resolveCollisions`
  receives) is the exact match — `projectile.position()` at the consult
  is one step past (post-integration, `after`).
- **THE VERDICT MAPPING** (takeDamage's boolean ↔ the engine): the mob
  arm's `combat.mobHit` landed; the player arm's evolved `playerHit`
  (the sink was void — the `CombatSink`'s projectile-carrying default
  bridge evolved from `void` to `boolean` through the interface's own
  bridge pattern, the legacy form presumed landed, source-compatible);
  the vehicle arm's already-`boolean` `vehicleHit` (previously IGNORED by
  `hitVehicle`); the crystal and dragon arms' `hitCrystal`/`hitDragonPart`
  booleans.
- **THE ENDERMAN NUANCE**: the engine's enderman hurt (the 9-in-10
  teleport BEFORE the hurt, the hurt lands, returns true) keeps
  `landed=true` — matching vanilla's takeDamage-true: the bowhit plays
  on enderman hits. The enderman ARROW-SURVIVES exception (line 277 —
  the reference keeps the arrow alive) is the adjacent tail.

**THE NAMED ADJACENT TAILS** (ledgered, never the PR's scope): the
failed-damage BOUNCE (the reference bounces the arrow — the engine
removes it unconditionally on every arm) and the ENDERMAN
arrow-survives exception.

**THE CORRECTION — PR #124 WIDENED** (`fix/arrow-stick-bowhit-sound-parity-audit`,
`01783eeb` over the branch's `6a4811b4`): the shared
`playEntityHitBowhit` (the ARROW kind gate + the `from` position + the
dimension + the reference's fresh pitch draw over [~1.09, ~1.33]) called
at ALL FIVE entity sites (mob, player, vehicle, crystal, dragon part),
each gated on its landed verdict; `hitMob`/`hitPlayer`/`hitVehicle` now
return the verdict; `EngineServer`'s sink returns
`projectileHitPlayerOnTick`'s evolved verdict (the failed-damage early
returns — pvp off, the dead/idle body, the cancelled event, the
armor-eaten amount, the hurt-window absorption — the reference's
takeDamage-false rows). **THE SEATS GREEN on the fix tree**: the
entity-hit seat 3/0 (the pin lifted, the guards holding), the stick seat
unregressed; **THE REGRESSION 96/0** (the projectile package 52, the
entity families 27, the vehicle 3, the fx 3, the nbt 11 — every lane
touching the evolved signatures). The PR body rewritten for the widened
scope (the verification table now carries both arms' three passes).

**The bookkeeping beside the act**: the VT-024 second collision resolved
(queue item 7b — the pass-38 lane's arrow pickup envelope renumbers to
VT-025 per the public-first protocol, #131 owning VT-024; provenance
marked, nothing public renamed); the lane's base carries develop
`149eb1f0` merged in (the seats' baseline — FL4-GAP2 Slice B landed).
"""
text = text.rstrip("\n") + pass_section

with open(PATH, "w", encoding="utf-8") as f:
    f.write(text)
print("report updated:", len(text.splitlines()), "lines")
