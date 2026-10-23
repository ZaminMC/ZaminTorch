#!/usr/bin/env python3
"""KigPaper patch census: parse every .patch in the supplied tree.
Outputs JSON (path, subject, files, stats) + per-directory summaries."""
import json, os, re, sys

BASE = __import__("os").environ.get("KIGPAPER_TREE", "/home/z/my-project/audit/kigpaper/KigPaper-ver-1.8.8")
OUT = __import__("os").environ.get("KIGPAPER_CENSUS", "docs/kigpaper-census.json")
DIRS = ["Bukkit-Patches", "CraftBukkit-Patches", "Spigot-API-Patches",
        "Spigot-Server-Patches", "nms-patches"]

def parse_patch(path):
    """Extract subject + target files + insert/delete counts from a git patch."""
    subject, files, ins, dele = None, [], 0, 0
    cur_file = None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if line.startswith("Subject: ") and subject is None:
                subject = line[len("Subject: "):].strip()
                m = re.match(r"\[PATCH\]?\s*(.*)", subject)
                if m:
                    subject = m.group(1).strip()
            elif line.startswith("diff --git "):
                m = re.match(r'diff --git a/(.+?) b/(.+)$', line.rstrip())
                if m:
                    cur_file = m.group(2)
                    files.append(cur_file)
            elif line.startswith("+++ b/"):
                t = line[6:].rstrip()
                if t and t != "/dev/null" and (not files or files[-1] != t):
                    if t not in files:
                        files.append(t)
                        cur_file = t
            elif line.startswith("+") and not line.startswith("+++"):
                ins += 1
            elif line.startswith("-") and not line.startswith("---"):
                dele += 1
    return {"subject": subject or "(no subject)", "files": files,
            "insertions": ins, "deletions": dele}

census = {}
total = 0
for d in DIRS:
    root = os.path.join(BASE, d)
    entries = []
    for fn in sorted(os.listdir(root)):
        if not fn.endswith(".patch"):
            continue
        p = os.path.join(root, fn)
        info = parse_patch(p)
        info["patch"] = fn
        info["path"] = f"{d}/{fn}"
        entries.append(info)
        total += 1
    census[d] = entries

with open(OUT, "w") as f:
    json.dump(census, f, indent=1)

print(f"total patches: {total}")
for d, entries in census.items():
    print(f"{d}: {len(entries)}")
    no_subject = [e for e in entries if e["subject"] == "(no subject)"]
    if no_subject:
        print(f"  WARNING {len(no_subject)} without subject")
