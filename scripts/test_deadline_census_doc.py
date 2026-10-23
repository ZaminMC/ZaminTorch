#!/usr/bin/env python3
"""Renders docs/TEST_DEADLINE_CENSUS.md from the raw census TSV (ML-001).

Classification buckets (the audit's own vocabulary, CYCLE3_AUDIT.md Phase 4):
  C-A unjustified deadline          — the bound cannot cover the awaited
                                      engine cadence; must be converted.
  C-B missing engine-visible cond.  — a fixed wall-clock sleep where the
                                      engine's own tick clock (or a polled
                                      condition) is the honest clock.
  C-C genuine product timeout /     — the deadline IS the product's own
     justified liveness bound           wall-clock behavior, or a generous
                                      liveness backstop on an already
                                      engine-visible condition poll.

The output is the human-audited skeleton: heuristics pre-sort, the named
audit lists carry the hand-checked rows.
"""

from __future__ import annotations

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RAW = REPO / "scripts" / "census_raw.tsv"  # regenerate: test_deadline_census.py > census_raw.tsv

TICK_MENTION = re.compile(
    r"tick|nominal|floor|hardness|charge|expiry|decay|i-frame|iframe|regen|"
    r"cadence|starvation|window|durational|second of engine")
DRAIN_MENTION = re.compile(r"drain|settle|submit|dispatch|arrive|land|echo|application")
NEGATIVE_MENTION = re.compile(
    r"not arrive|nothing may|refused|cancelled|canceled|wrongly|would have|"
    r"never land|no debt|parked|far below|past the")


def ms(value: str) -> int:
    if value.endswith("ms"):
        return int(value[:-2])
    if value.endswith("s"):
        return int(value[:-1]) * 1000
    return -1


def ticks_of(value: str) -> int:
    """The engine tick count a wall duration represents at the nominal 20 Hz."""
    v = ms(value)
    if v < 0:
        return -1
    return max(1, (v + 49) // 50)


def family_of(snippet: str) -> str:
    s = snippet.lower()
    if NEGATIVE_MENTION.search(s):
        return "negative-proof window"
    if TICK_MENTION.search(s):
        return "tick-cadence"
    if DRAIN_MENTION.search(s):
        return "submit-drain settle"
    return "unannotated settle"


def main() -> None:
    rows = list(csv.DictReader(RAW.open(), delimiter="\t"))

    # ---- group machinery rows into their parent poll loop -----------------
    # poll-interval / wallclock-read rows belong to the deadline-poll loop
    # of the same file (the helper body); keep one representative row.
    files = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for r in rows:
        files[r["module"]][r["file"]][r["kind"]].append(r)

    out = []
    w = out.append

    counts = defaultdict(int)
    conv_targets = defaultdict(int)
    audit_short_deadlines = []
    audit_latches = []
    audit_misc = []

    for module in ["zamin-api", "zamin-core", "zamin-protocol-v1_8_8",
                   "zamin-launcher", "zamin-bukkit"]:
        mfiles = files.get(module)
        if not mfiles:
            continue
        w(f"\n## {module}\n")
        for f in sorted(mfiles):
            kinds = mfiles[f]
            short = f.split("/")[-1]
            w(f"### `{short}`\n")
            w("| line | form | value | classification | action |")
            w("| --- | --- | --- | --- | --- |")

            def emit(line, form, value, cls, action):
                w(f"| {line} | {form} | {value} | {cls} | {action} |")

            # deadline-poll loops: one row per site (most files have one
            # helper; sites with inline loops list each).
            for r in kinds.get("deadline-poll", []):
                v = ms(r["value"])
                if v < 3000:
                    cls = "C-A candidate — audit list"
                    counts["C-A-candidate"] += 1
                    audit_short_deadlines.append(r)
                    action = "audited below"
                else:
                    cls = "C-C liveness bound (engine-visible condition polled)"
                    counts["C-C-deadline-poll"] += 1
                    action = "keep"
                emit(r["line"], "poll loop deadline", r["value"], cls, action)
                counts["machinery-folded"] += len(kinds.get("poll-interval", [])) + len(
                    kinds.get("wallclock-read", []))

            for r in kinds.get("fixed-sleep", []):
                t = ticks_of(r["value"])
                fam = family_of(r["snippet"])
                if module == "zamin-launcher":
                    cls = "C-C — no engine in scope (terminal pipe)"
                    action = "keep (documented)"
                    counts["C-C-no-engine"] += 1
                else:
                    cls = f"C-B missing engine-visible condition — {fam}"
                    action = f"convert → `awaitTicks({t})`"
                    counts["C-B-" + fam] += 1
                    conv_targets[f] = conv_targets[f] + 1
                emit(r["line"], "fixed sleep", f"{r['value']} (~{t}t)", cls, action)

            for r in kinds.get("latch-await", []):
                counts["C-C-latch"] += 1
                audit_latches.append(r)
                emit(r["line"], "latch await", r["value"],
                     "C-C candidate — audit list", "audited below")

            for r in kinds.get("nano-budget", []):
                counts["C-C-nano"] += 1
                audit_misc.append(r)
                emit(r["line"], "nanoTime budget", r["value"],
                     "audit list", "audited below")

            for r in kinds.get("timeout-annotation", []):
                counts["C-C-annot"] += 1
                audit_misc.append(r)
                emit(r["line"], "@Timeout", r["value"], "audit list", "audited below")

            for r in kinds.get("future-get", []):
                counts["C-C-future"] += 1
                audit_misc.append(r)
                emit(r["line"], "future get", r["value"], "audit list", "audited below")
            w("")

    body = "\n".join(out)
    summary = dict(counts)
    print(f"SUMMARY: {dict(summary)}", file=sys.stderr)
    print(f"CONV FILES: {len(conv_targets)}", file=sys.stderr)
    Path("/home/z/my-project/scripts/census_body.md").write_text(body, encoding="utf-8")
    with open("/home/z/my-project/scripts/census_audit.tsv", "w", encoding="utf-8") as fh:
        for r in audit_short_deadlines + audit_latches + audit_misc:
            fh.write("\t".join([r["kind"], r["file"], r["line"],
                                r["value"], r["snippet"]]) + "\n")


if __name__ == "__main__":
    main()
