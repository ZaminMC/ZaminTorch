#!/usr/bin/env python3
"""Folia 26.2.x region-threading patch inventory extractor.

Parses every patch in the archive, enumerates every modified Java source
path (per patch, with hunk counts), and emits:
  - folia_inventory.json : structured data (paths, hunk counts, subjects)
  - console summary      : per-patch path counts for the audit doc
"""
import json
import os
import re
import sys

ROOT = "/home/z/my-project/folia-extract/Folia-ver-26.2.x"
PATCH_DIRS = [
    ("minecraft", os.path.join(ROOT, "folia-server/minecraft-patches/features")),
    ("paper-server", os.path.join(ROOT, "folia-server/paper-patches/features")),
    ("paper-api", os.path.join(ROOT, "folia-api/paper-patches/features")),
]

DIFF_RE = re.compile(r"^diff --git a/(\S+) b/(\S+)$")
HUNK_RE = re.compile(r"^@@")

def parse_patch(path):
    """Return (subject, [(path, hunks, added, deleted)], is_new_file_count)."""
    subject = ""
    files = {}  # path -> [hunks, added, deleted]
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("Subject: [PATCH"):
                subject = re.sub(r"^Subject: \[PATCH \d+/\d+\] ", "", line)
                continue
            m = DIFF_RE.match(line)
            if m:
                p = m.group(2)
                if p not in files:
                    files[p] = [0, 0, 0]
                continue
            if HUNK_RE.match(line):
                # attribute to the most recent file
                if files:
                    last = list(files)[-1]
                    files[last][0] += 1
                continue
            # count +/- lines (approximate churn) attributed to last file
            if files:
                last = list(files)[-1]
                if line.startswith("+") and not line.startswith("+++"):
                    files[last][1] += 1
                elif line.startswith("-") and not line.startswith("---"):
                    files[last][2] += 1
    return subject, [(p, v[0], v[1], v[2]) for p, v in files.items()]

def main():
    inventory = {}
    for side, d in PATCH_DIRS:
        for name in sorted(os.listdir(d)):
            if not name.endswith(".patch"):
                continue
            full = os.path.join(d, name)
            subject, files = parse_patch(full)
            key = f"{side}/{name}"
            java_files = [x for x in files if x[0].endswith(".java")]
            inventory[key] = {
                "subject": subject,
                "total_paths": len(files),
                "java_paths": len(java_files),
                "files": [
                    {"path": p, "hunks": h, "added": a, "deleted": dl}
                    for p, h, a, dl in files
                ],
            }
            print(f"{key}: subject='{subject}' total={len(files)} java={len(java_files)}")

    out = "/home/z/my-project/scripts/folia_inventory.json"
    with open(out, "w") as f:
        json.dump(inventory, f, indent=1)
    print(f"\nWrote {out}")

    # summary counts
    total_java = sum(v["java_paths"] for v in inventory.values())
    print(f"TOTAL java path entries across all patches: {total_java}")

if __name__ == "__main__":
    main()
