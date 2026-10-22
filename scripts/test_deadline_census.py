#!/usr/bin/env python3
"""The wall-clock-deadline census over every module test tree (ML-001).

Enumerates every wall-clock dependency in the five test trees:

  * deadline-bounded poll loops   — System.currentTimeMillis() + N
  * standalone fixed sleeps       — Thread.sleep(N) outside a poll loop
  * poll intervals                — Thread.sleep(N) inside a poll loop
  * latch / future timeouts       — .await(N, TimeUnit...), .get(N, ...)
  * nanoTime budgets              — System.nanoTime() + N / elapsed forms
  * timeout annotations/params    — @Timeout, read...(deadline - now)

Output: one TSV row per site — module, file, line, kind, value, snippet —
written to stdout. The census doc (docs/TEST_DEADLINE_CENSUS.md) is the
classified, human-audited rendering of this enumeration.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

MODULES = [
    "zamin-api",
    "zamin-core",
    "zamin-protocol-v1_8_8",
    "zamin-launcher",
    "zamin-bukkit",
]

DEADLINE_RE = re.compile(r"System\.currentTimeMillis\(\)\s*[+]\s*([0-9][0-9_]*)")
NANO_DELTA_RE = re.compile(r"System\.nanoTime\(\)\s*[+]\s*([0-9][0-9_]*)")
SLEEP_RE = re.compile(r"Thread\.sleep\(\s*([0-9][0-9_]*)\s*\)")
LATCH_RE = re.compile(r"\.await\(\s*([0-9][0-9_]*)\s*,\s*(TimeUnit\.\w+|ChronoUnit\.\w+)")
FUTURE_GET_RE = re.compile(r"\.get\(\s*([0-9][0-9_]*)\s*,\s*(TimeUnit\.\w+)")
TIMEOUT_ANNOT_RE = re.compile(r"@Timeout\(([^)]*)\)")
TIMEOUT_PARAM_RE = re.compile(
    r"(?:read|poll|connect|readRel\w*|readKeepAlive\w*)\w*\("
    r"[^()]*?(?:deadline\s*-\s*System\.currentTimeMillis\(\)|[0-9]{3,})"
)
WHILE_RE = re.compile(r"\bwhile\s*\(")
FOR_RE = re.compile(r"\bfor\s*\(")
CURRENT_MILLIS_RE = re.compile(r"System\.currentTimeMillis\(\)")
NANO_RE = re.compile(r"System\.nanoTime\(\)")


def fmt(value: str) -> str:
    try:
        n = int(value.replace("_", ""))
        return f"{n}ms" if n < 1000 or n % 1000 else f"{n // 1000}s"
    except ValueError:
        return value


def scan_file(path: Path, module: str, rows: list[dict]) -> None:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    rel = str(path.relative_to(REPO)).replace("\\", "/")

    def in_guard(i: int) -> bool:
        """A sleep at line i sits inside a deadline-bounded poll loop when a
        while/for header and a wall-clock read appear within the 8 lines
        above it (the repo's settle-poll idiom)."""
        window = lines[max(0, i - 8):i]
        return any(WHILE_RE.search(w) or FOR_RE.search(w) for w in window) and any(
            CURRENT_MILLIS_RE.search(w) or NANO_RE.search(w) for w in window
        )

    for i, line in enumerate(lines):
        for m in DEADLINE_RE.finditer(line):
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind="deadline-poll", value=fmt(m.group(1)),
                             snippet=clean(line)))
        for m in NANO_DELTA_RE.finditer(line):
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind="nano-budget", value=fmt(m.group(1)),
                             snippet=clean(line)))
        for m in SLEEP_RE.finditer(line):
            kind = "poll-interval" if in_guard(i) else "fixed-sleep"
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind=kind, value=fmt(m.group(1)),
                             snippet=clean(line)))
        for m in LATCH_RE.finditer(line):
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind="latch-await", value=m.group(2),
                             snippet=clean(line)))
        for m in FUTURE_GET_RE.finditer(line):
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind="future-get", value=f"{m.group(1)} {m.group(2)}",
                             snippet=clean(line)))
        for m in TIMEOUT_ANNOT_RE.finditer(line):
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind="timeout-annotation", value=m.group(1)[:40],
                             snippet=clean(line)))
        if (CURRENT_MILLIS_RE.search(line) and not DEADLINE_RE.search(line)) or (
            NANO_RE.search(line) and not NANO_DELTA_RE.search(line)
        ):
            rows.append(dict(module=module, file=rel, line=i + 1,
                             kind="wallclock-read", value="-",
                             snippet=clean(line)))


def clean(line: str) -> str:
    s = line.strip()
    return (s[:110] + "…") if len(s) > 110 else s


def main() -> None:
    rows: list[dict] = []
    for module in MODULES:
        tree = REPO / module / "src" / "test"
        if not tree.is_dir():
            continue
        for path in sorted(tree.rglob("*.java")):
            scan_file(path, module, rows)

    counts: dict[str, int] = {}
    for r in rows:
        counts[r["kind"]] = counts.get(r["kind"], 0) + 1
    print("# census site counts by kind:", file=sys.stderr)
    for kind, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"#   {kind}: {n}", file=sys.stderr)

    print("module\tfile\tline\tkind\tvalue\tsnippet")
    for r in rows:
        print(f"{r['module']}\t{r['file']}\t{r['line']}\t{r['kind']}\t{r['value']}\t{r['snippet']}")


if __name__ == "__main__":
    main()
