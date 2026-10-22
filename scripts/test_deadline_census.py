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


STRING_RE = re.compile(r'"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'')
COMMENT_RE = re.compile(r'//[^\n]*|/\*.*?\*/', re.DOTALL)


def _blank(match: re.Match) -> str:
    """Length- and line-preserving blank: every char except newlines
    becomes a space, so brace-depth bookkeeping stays aligned."""
    return re.sub(r"[^\n]", " ", match.group(0))


def loop_member_lines(text: str) -> set[int]:
    """The set of line indices INSIDE a while/for body (brace-depth walk).

    String and char literals are stripped first so braces inside them do
    not skew depth. A sleep on one of these lines is loop machinery — the
    interval of a deadline-bounded or condition-bounded poll — never an
    independent deadline."""
    stripped = COMMENT_RE.sub(_blank, STRING_RE.sub(_blank, text))
    lines = stripped.splitlines()
    do_while = re.compile(r"[}\)]\s*while\s*\(|^\s*while\s*\(.*;\s*$")
    do_open = re.compile(r"\bdo\s*\{")
    open_loops: list[tuple[int, int]] = []  # (header index, body depth)
    members: set[int] = set()
    depth = 0
    for i, line in enumerate(lines):
        header = (WHILE_RE.search(line) and not do_while.search(line)) or FOR_RE.search(line)
        if header or do_open.search(line):
            opens = line.count("{")
            # the body sits one level deeper than the header; a same-line
            # `{` (for (...) {) already carries that brace
            open_loops.append((i, depth + (opens if opens else 1)))
        if open_loops:
            members.add(i)
        depth += line.count("{") - line.count("}")
        open_loops = [(h, d) for (h, d) in open_loops if depth >= d]
    return members


def scan_file(path: Path, module: str, rows: list[dict]) -> None:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    rel = str(path.relative_to(REPO)).replace("\\", "/")
    loop_members = loop_member_lines(text)

    def in_guard(i: int) -> bool:
        """A sleep at line i is LOOP MACHINERY when it sits inside a
        while/for body (brace-depth walk) — covering both the repo's
        deadline-bounded settle polls and the condition-bounded polls
        (`while (cond) { ... Thread.sleep(n); }`), whose interval is not a
        deadline at all. Anything else is a standalone wall-clock wait."""
        return i in loop_members

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
