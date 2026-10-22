#!/usr/bin/env python3
"""Extract the 1.8.8 blast-resistance table from the reference decompile.

Parses reference/1.8.8/net/minecraft/block/Block.java's init() registration
chains (setStrength / setBlastResistance / setUnbreakable, in call order)
plus, for chains that carry no setters of their own, the block CLASS
constructors up the hierarchy (reference/1.8.8/net/minecraft/block/*.java),
and computes each block's final blast-resistance READ value exactly like
the reference: the stored field is setBlastResistance(r) -> r*3 (or floored
by setStrength(s) -> max(br, s*5)); the read is the field / 5
(Block.getBlastResistance, lines 542-543).

Chain evaluation order (the reference's Java semantics):
  1. the class hierarchy's constructors, base first (super chain);
  2. the register call's inline chain, in source order;
  3. local-variable registrations resolve through their assignment chain.

Output: JSON on stdout, one row per registered block:
  {id, name, hardness, br_read, br_field}.

Usage: python3 scripts/extract-blast-resistance.py > blast.json
"""
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BLOCK_DIR = REPO / "reference" / "1.8.8" / "net" / "minecraft" / "block"
BLOCK_JAVA = BLOCK_DIR / "Block.java"

CALL_RE = re.compile(
    r"(?:this\.)?\.(?:setStrength\(\s*(-?[0-9.]+)F?\s*\)"
    r"|setBlastResistance\(\s*(-?[0-9.]+)F?\s*\)"
    r"|setUnbreakable\(\))"
)
# A chain segment: either a constructor chain `new Xxx(...)...` or a bare var.
REGISTER_RE = re.compile(
    r"register\(\s*(\d+)\s*,\s*(AIR_KEY|\"[a-z0-9_]+\")\s*,\s*(.+?)\);",
    re.DOTALL,
)
LOCAL_VAR_RE = re.compile(
    r"Block\s+(\w+)\s*=\s*(new\s+\w+[^;]*);",
    re.DOTALL,
)
CLASS_RE = re.compile(r"public\s+(?:abstract\s+)?class\s+(\w+)(?: extends (\w+))?[^\{]*\{")
FILE_CACHE = {}


def file_text(path):
    if path not in FILE_CACHE:
        FILE_CACHE[path] = path.read_text(encoding="utf-8")
    return FILE_CACHE[path]


def class_ctor_chain(class_name):
    """The setter calls in a class's constructors, base hierarchy first.

    Scans the class file for (this.)setStrength / setBlastResistance /
    setUnbreakable calls in source order (the decompile's ctors are the
    only home of these calls; a class rarely has more than one).
    """
    path = BLOCK_DIR / f"{class_name}.java"
    if not path.exists():
        return []
    text = file_text(path)
    chain = []
    m = CLASS_RE.search(text)
    if m and m.group(2):
        parent = m.group(2)
        if parent != "Block" and (BLOCK_DIR / f"{parent}.java").exists():
            chain.extend(class_ctor_chain(parent))
    for cm in CALL_RE.finditer(text):
        if cm.group(1) is not None:
            chain.append(("setStrength", float(cm.group(1))))
        elif cm.group(2) is not None:
            chain.append(("setBlastResistance", float(cm.group(2))))
        else:
            chain.append(("setUnbreakable", None))
    return chain


def chain_of(new_expr):
    """Evaluate one `new Xxx(...)...chain` expression's setter calls."""
    m = re.match(r"new\s+(\w+)\s*\(", new_expr)
    calls = []
    if m:
        calls.extend(class_ctor_chain(m.group(1)))
    for cm in CALL_RE.finditer(new_expr):
        if cm.group(1) is not None:
            calls.append(("setStrength", float(cm.group(1))))
        elif cm.group(2) is not None:
            calls.append(("setBlastResistance", float(cm.group(2))))
        else:
            calls.append(("setUnbreakable", None))
    return calls


def evaluate(calls):
    br = 0.0
    hardness = None
    for name, arg in calls:
        if name == "setStrength":
            hardness = arg
            if br < arg * 5.0:
                br = arg * 5.0
        elif name == "setBlastResistance":
            br = arg * 3.0
        else:  # setUnbreakable -> setStrength(-1.0F)
            hardness = -1.0
            if br < -5.0:
                br = -5.0
    return br, hardness


def parse():
    src = BLOCK_JAVA.read_text(encoding="utf-8")
    start = src.index("public static void init()")
    body = src[start:]
    local_vars = {}
    for m in LOCAL_VAR_RE.finditer(body):
        local_vars[m.group(1)] = m.group(2)
    rows = []
    seen = set()
    for m in REGISTER_RE.finditer(body):
        block_id = int(m.group(1))
        name = "air" if m.group(2) == "AIR_KEY" else m.group(2).strip('"')
        expr = m.group(3).strip()
        if name in seen:
            continue
        seen.add(name)
        if re.fullmatch(r"\w+", expr) and expr in local_vars:
            calls = chain_of(local_vars[expr])
        else:
            calls = chain_of(expr)
        br, hardness = evaluate(calls)
        rows.append({
            "id": block_id,
            "name": name,
            "hardness": hardness,
            "br_read": br / 5.0,
            "br_field": br,
        })
    return rows


def main():
    rows = parse()
    json.dump(rows, sys.stdout, indent=1)
    print()


if __name__ == "__main__":
    main()
