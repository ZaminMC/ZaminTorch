#!/usr/bin/env python3
"""Census every FxManager emit call site: explicit-dimension vs the
no-dimension overloads (which stamp overworld 0), with the caller's file."""
import re
from pathlib import Path

ROOT = Path("zamin-core/src/main/java")
# The no-dimension overloads' arities: sound(pos, name, vol, pitch) = 4;
# sound(pos, name) = 2; soundExcept(pos, name, vol, pitch, except) = 5;
# blockShatter(pos, block) = 2; potionSplash(pos, data) = 2;
# worldEvent(pos, eventId, data) = 3; poof(pos) = 1; smoke(pos) = 1.
NO_DIM = {
    ("sound", 4), ("sound", 2),
    ("soundExcept", 5),
    ("blockShatter", 2),
    ("potionSplash", 2),
    ("worldEvent", 3),
    ("poof", 1), ("smoke", 1),
}
CALL = re.compile(
    r"\b(?:fxManager|fx|this\.fx|projectilesFx|blockFx)\."
    r"(sound|soundExcept|blockShatter|potionSplash|worldEvent|poof|smoke)\s*\(")

def split_args(s):
    args, depth, cur = [], 0, []
    for ch in s:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            args.append("".join(cur)); cur = []
        else:
            cur.append(ch)
    if cur:
        args.append("".join(cur))
    return [a.strip() for a in args]

rows = []
for path in sorted(ROOT.rglob("*.java")):
    src = path.read_text(encoding="utf-8")
    for m in CALL.finditer(src):
        # find the balanced close paren
        i = m.end(); depth = 1; start = i
        while i < len(src) and depth:
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
            i += 1
        args = split_args(src[start:i-1])
        method = m.group(1)
        line = src[:m.start()].count("\n") + 1
        nodim = (method, len(args)) in NO_DIM
        rows.append((str(path), line, method, len(args), nodim, args[-1][:60]))

print(f"total fx emit sites: {len(rows)}")
print(f"no-dimension (stamp overworld 0): {sum(1 for r in rows if r[4])}")
print()
for path, line, method, n, nodim, last in rows:
    if nodim:
        print(f"  NODIM {path}:{line} {method}({n}) last={last}")
