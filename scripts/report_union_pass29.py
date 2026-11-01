#!/usr/bin/env python3
"""The audit report union: merge the parallel stream's pass-27..29 lineage
into this stream's report (the house doc-union precedent).

Their lineage: origin/audit/verifier-pass-29-discovery (c7c374b8), forked
from the pass-26-era report. Unique content: VT-009/VT-010/VT-011 findings,
their pass-27/28/29 records, four coverage-ledger rows, their queue.
Our lineage: this stream's report — the VT-006 five-family landing (#108)
and VT-012 (this pass).
"""
import re

OURS = 'docs/vanilla-conformance-audit.md'
THEIRS = '/tmp/their-report.md'

ours = open(OURS).read()
theirs = open(THEIRS).read()

# ---- 1. extract their unique sections (pass 28, pass 29, VT-009/010/011) ----
def section(text, start_heading, end_heading):
    s = text.index(start_heading)
    e = text.index(end_heading)
    return text[s:e]

their_pass28 = section(theirs,
    '## The twenty-eighth pass — VT-008 re-verified GREEN on the MERGED law',
    '## VT-009 —')
their_vt009 = section(theirs, '## VT-009 —', '## VT-010 —')
their_vt010 = section(theirs, '## VT-010 —', '## VT-011 —')
their_vt011 = section(theirs, '## VT-011 —',
    '## The twenty-ninth pass — VT-010\'s third pass landed')
their_pass29 = section(theirs,
    "## The twenty-ninth pass — VT-010's third pass landed",
    '## Coverage ledger')

# their pass-27 section: the independent VT-008 seats record (their unique
# construction) — take it too, labeled as theirs
their_pass27 = section(theirs,
    '## The twenty-seventh pass — VT-008\'s verification completed',
    '## VT-008 —')

# ---- 2. build the union block ----
union_block = """## The parallel stream's records (the report union — this pass's second act)

The fetch after this pass's first push surfaced a **parallel stream's
twenty-ninth pass** on the same audit-report lineage: the branch
`audit/verifier-pass-29-discovery` (head `c7c374b8`, based on the
pre-vine develop `a23011fa`) — a second worker carrying the same
pass-26-era report forward through its own passes 27-29 while this
stream carried it through the VT-006 landing and VT-012. The two
lineages forked at the pass-26 record; neither saw the other's pushes
until now. This section is the union (the F-EX6/F-EX9/F-EX13
doc-union precedent): the parallel stream's records preserved verbatim
below, the coverage ledger's rows merged in place, the forks reconciled
by evidence rather than by date.

**The fork's one material staleness:** the parallel stream's VT-006
items-family pass 3 concluded "THE FIX IS THE NEXT RUN'S HEAD" — a
conclusion its own evidence supports (the independent walk, the
compensation hunt negative, the design-choice challenge negative), but
which post-dates this stream's landing of the same fix: **PR #108
OPEN** carries the mobs/items/orbs/vehicles/projectiles families whole
(the pass-27/28 records above; the five seats green, the
stash-discrimination RED/GREEN pairs). The parallel stream's pass 3 is
therefore recorded here as an INDEPENDENT CONFIRMATION of VT-006 —
its walk and hunts ran on `a23011fa` without this stream's branch in
view, and reached the same conclusion the census reached. The orbs,
boats, carts, and arrows pass-3 walks its queue names are the same
finding's remaining members, already landed behind #108; the End-origin
arm remains the named tail on both lineages.

**The parallel stream's pass records** (verbatim; its pass 27's VT-008
coverage seats and its passes 28-29's VT-009/VT-010/VT-011 discovery
and correction records are its unique evidence — the VT-008 seats'
independent constructions stand beside this stream's own):

"""

union_block += their_pass27.rstrip() + '\n\n'
union_block += their_pass28.rstrip() + '\n\n'
union_block += their_vt009.rstrip() + '\n\n'
union_block += their_vt010.rstrip() + '\n\n'
union_block += their_vt011.rstrip() + '\n\n'
union_block += their_pass29.rstrip() + '\n\n'

# ---- 3. insert the union block before my queue section ----
anchor = '## The queue for the next pass'
idx = ours.index(anchor)
merged = ours[:idx] + union_block + '\n' + ours[idx:]

# ---- 4. merge their four new ledger rows into my ledger ----
their_rows = [
    ("| Break-path pair walks (door/bed sibling cleanups) — dimension routing |",
     "RESOLVED (the twenty-eighth pass, the parallel stream): **VT-009** CONFIRMED three passes and corrected as **PR #106 OPEN** (`fix/pair-break-dimension-parity-audit`: the `BlockBrokenHook` three-arg listener, the parameterized walks; the parity pin 3/0 + the regression lanes 75/0) |"),
    ("| Bed pair — the sibling resolution model |",
     "RESOLVED by the parallel stream's **PR #111 OPEN** (`fix/bed-pair-facing-resolution-parity-audit`, commit 8b1eefb0: the facing-exact helper shared by the four walks, the block-class membership, the anchor-state detonate removal; the parity pin 4/0 GREEN on the fix, 4/4 RED pre-fix by the stash discrimination; the regression lanes 104/0) — three passes complete on its lineage (the census + the three RED seats + the wire/legacy suffix-consistency walk: the id family IS the reference's metadata space; the class-membership sub-divergence pinned) |"),
    ("| Anvil import — the bed's OCCUPIED nibbles (26: metas 12-15 the reference persists for occupied heads, 4-7 the editor-corrupted feet) |",
     "RESOLVED by the parallel stream's **PR #115 OPEN** (`fix/bed-occupied-anvil-import-parity-audit`, commit 512b0c09: the eight rows + the javadoc band + the integrity test's expected bands, the count re-pinned 1321 → 1329; the parity pin 2/0 GREEN, the anvil family 47/0, the drift guard 2/0, the protocol block-id lanes 15/0) — **VT-011**, three passes complete (the source law, the behavioral seats 2/2 RED with the import's own ledger rows in the failure messages, the lifecycle walk) |"),
    ("| Block-update wake (BlockUpdateSystem.runUpdate, RedstoneSystem.neighborChanged) — world binding |",
     "SURFACED as VT-009's pass-3 non-compensation evidence (the parallel stream): BOTH reads consult the overworld field (the changed-world parameter ignored in the six-neighbor dispatch) — the house stream's FL4-F10 lane LANDED on develop (`0328e92f`), NOT duplicated; the door/bed seats are the evidence cross-reference |"),
]
for prefix, row in their_rows:
    if prefix in merged:
        raise SystemExit(f"row already present: {prefix[:60]}")
    # insert before the "| FX broadcasts" row (a stable anchor near the tail rows)
    anchor_row = '| FX broadcasts — the dimension awareness |'
    i = merged.index(anchor_row)
    merged = merged[:i] + prefix + ' ' + row + '\n' + merged[i:]

# ---- 5. add the cross-stream confirmation note to my VT-006 portal row ----
old_portal_row_tail = ("the projectile slice the twenty-eighth pass's own act, "
    "the stash-discrimination RED/GREEN pair; the same pass's repair restored "
    "the carry-forward clobber and re-pinned the two stale cooldown seats)")
new_portal_row_tail = (old_portal_row_tail + "; the parallel stream's "
    "twenty-ninth-pass items-family walk INDEPENDENTLY CONFIRMED the same "
    "finding on `a23011fa` without this branch in view — the convergence "
    "recorded, its remaining-members queue superseded by #108's landing)")
if old_portal_row_tail in merged:
    merged = merged.replace(old_portal_row_tail, new_portal_row_tail, 1)

# ---- 6. update my queue with their watch items ----
queue_addition = """13. **The parallel stream's watch items (the union's queue merge):**
    **#106** (VT-009's break-path pair walks), **#111** (VT-010's bed
    pair facing resolution), **#115** (VT-011's anvil occupied bed
    states) — all three OPEN against develop, the maintainer's review
    owed; none overlaps this stream's #108. The parallel stream's own
    queue named the crossing families' remaining arms as its next
    head — superseded by this stream's #108 landing (the union note
    above); the End-origin portal arm and the four open PRs are the
    shared watch.
"""
merged = merged.rstrip() + '\n\n' + queue_addition

open(OURS, 'w').write(merged)
print("union written:", len(merged.splitlines()), "lines")
