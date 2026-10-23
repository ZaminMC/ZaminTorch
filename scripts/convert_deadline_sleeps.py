#!/usr/bin/env python3
"""ML-002's mechanical conversion: every standalone fixed sleep in the
engine-backed test trees becomes EngineAwaits.awaitTicks on the engine's own
tick clock (docs/TEST_DEADLINE_CENSUS.md, the C-B family).

Rules (the census doc's conversion ledger):
  * ticks: a trailing comment stating the tick intent ("~12 ticks",
    "18 ticks nominal", "26 ticks at 20 Hz") wins; otherwise
    ceil(milliseconds / 50) at the nominal cadence;
  * the in-scope clock: the file's UNIQUE EngineServer/EngineWorld variable
    (server -> server.world(), world -> world), BukkitTestHarness fields
    (harness -> harness.server.world()); files with several distinct names
    are skipped for manual conversion (the compiler-in-the-loop stragglers);
  * the purpose string: the site's own comment, else "engine progress".

Line numbers come from the census TSV (the trees are unmodified since it
ran). Replacements apply bottom-up so the numbers stay valid.
"""

from __future__ import annotations

import csv
import math
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RAW = REPO / "scripts" / "census_raw.tsv"

SKIP_FILES = {
    # the do-while FIN-WAIT drain: the bound itself converts (manual)
    "zamin-core/src/test/java/net/zaminmc/torch/server/rcon/RconProtocolTest.java",
}
SKIP_SITES = {
    # RandomTickLaneTest's pacing for-loop: manual (submit + awaitTicks(1))
    ("zamin-core/src/test/java/net/zaminmc/torch/server/concurrent/RandomTickLaneTest.java", 194),
}

SLEEP_LINE = re.compile(r"^(\s*)Thread\.sleep\(([0-9][0-9_]*)\)\s*(?:;\s*(//.*)?)?$")
TICK_INTENT = re.compile(r"(\d+)[\s-]*ticks?")
DECL = re.compile(r"\b(EngineServer|EngineWorld)\s+(\w+)\s*[=;)]")
HARNESS_DECL = re.compile(r"\bBukkitTestHarness\s+(\w+)\s*[=;)]")
IMPORT_LINE = "import net.zaminmc.torch.testing.EngineAwaits;"


def ticks_from(ms_value: str, comment: str | None) -> int:
    """The converted wait must cover AT LEAST the original's nominal
    engine progress: ceil(ms/50) at the nominal cadence. A comment stating
    the tick cadence can only RAISE the floor, never shorten the window."""
    n = int(ms_value.replace("_", "").replace("ms", ""))
    ticks = max(1, math.ceil(n / 50))
    if comment:
        m = TICK_INTENT.search(comment)
        # a stated tick cadence is this sleep's own window only within a
        # sane slack of its wall time; a comment citing a different (much
        # larger) cadence never inflates the wait
        if m and int(m.group(1)) <= ticks * 3:
            ticks = max(ticks, int(m.group(1)))
    return ticks


def purpose(comment: str | None, ms_value: str) -> str:
    if comment:
        text = comment.lstrip("/").strip()
        if text:
            return text.replace('"', "'")
    return f"engine progress (a {ms_value} wall settle)"


FIELD_DECL = re.compile(
    r"^\s*(?:private|protected|public)?\s*(?:static\s+)?(?:final\s+)?"
    r"(EngineServer|EngineWorld)\s+(\w+)\s*;", re.MULTILINE)


def clock_expr(text: str, module: str) -> str | None:
    harness = HARNESS_DECL.search(text)
    if harness and module == "zamin-bukkit":
        return f"{harness.group(1)}.server.world()"
    # the class-level canonical handle wins (a boot-local `started` beside
    # the `private EngineServer server;` field never confuses the clock)
    fields = FIELD_DECL.findall(text)
    if len(fields) == 1:
        kind, name = fields[0]
        return name if kind == "EngineWorld" else f"{name}.world()"
    # protocol integration tests inherit ProtocolTestBase's protected server
    if not fields and "extends ProtocolTestBase" in text:
        return "server.world()"
    names: dict[str, str] = {}
    for m in DECL.finditer(text):
        names[m.group(2)] = m.group(1)  # last declaration wins
    if len(names) == 1:
        name, kind = next(iter(names.items()))
        return name if kind == "EngineWorld" else f"{name}.world()"
    return None


def convert(module: str, file_rel: str, sites: list[dict]) -> tuple[int, list[int]]:
    path = REPO / file_rel
    lines = path.read_text(encoding="utf-8").splitlines(keepends=False)
    text = "\n".join(lines)
    expr = clock_expr(text, module)
    if expr is None:
        return 0, [int(s["line"]) for s in sites]
    converted = 0
    missed: list[int] = []
    for site in sorted(sites, key=lambda s: -int(s["line"])):
        idx = int(site["line"]) - 1
        if (file_rel, int(site["line"])) in SKIP_SITES:
            continue
        m = SLEEP_LINE.match(lines[idx])
        if not m:
            missed.append(int(site["line"]))
            continue
        indent, ms_value, comment = m.group(1), m.group(2), m.group(3)
        ticks = ticks_from(ms_value, comment)
        what = purpose(comment, ms_value)
        call = f'EngineAwaits.awaitTicks({expr}, {ticks}, "{what}");'
        if len(indent) + len(call) > 110:
            lines[idx] = f"{indent}EngineAwaits.awaitTicks({expr}, {ticks},\n{indent}        \"{what}\");"
        else:
            lines[idx] = f"{indent}{call}"
        converted += 1
    if converted:
        text = "\n".join(lines)
        if IMPORT_LINE not in text:
            imports = [i for i, l in enumerate(lines) if l.startswith("import ")]
            if imports:
                lines.insert(imports[-1] + 1, IMPORT_LINE)
            else:
                pkg = [i for i, l in enumerate(lines) if l.startswith("package ")]
                lines.insert(pkg[-1] + 1 if pkg else 0, IMPORT_LINE)
            text = "\n".join(lines)
        path.write_text(text + ("\n" if not text.endswith("\n") else ""), encoding="utf-8")
    return converted, missed


def main() -> None:
    # a FRESH extraction: already-converted files shifted their line numbers
    # (import insertions, wrapped calls), so the committed census TSV cannot
    # drive the second pass
    import subprocess
    import tempfile

    fresh = Path(tempfile.mkdtemp()) / "census.tsv"
    with fresh.open("w") as fh:
        subprocess.run(
            [sys.executable, str(REPO / "scripts" / "test_deadline_census.py")],
            stdout=fh, check=True)
    rows = [r for r in csv.DictReader(fresh.open(), delimiter="\t")
            if r["kind"] == "fixed-sleep"
            and r["module"] in {"zamin-core", "zamin-protocol-v1_8_8", "zamin-bukkit"}
            and r["file"] not in SKIP_FILES]
    by_file: dict[tuple[str, str], list[dict]] = {}
    for r in rows:
        by_file.setdefault((r["module"], r["file"]), []).append(r)

    total = 0
    missed_total: list[str] = []
    for (module, file_rel), sites in sorted(by_file.items()):
        n, missed = convert(module, file_rel, sites)
        total += n
        for ln in missed:
            missed_total.append(f"{file_rel}:{ln}")
        if n == 0 and not missed:
            print(f"NO-CLOCK {file_rel} ({len(sites)} sites)", file=sys.stderr)

    print(f"CONVERTED {total} sites", file=sys.stderr)
    if missed_total:
        print("MISSED (regex mismatch):", file=sys.stderr)
        for m in missed_total:
            print(f"  {m}", file=sys.stderr)


if __name__ == "__main__":
    main()
