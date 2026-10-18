#!/usr/bin/env python3
"""Development Statistics generator for ZaminTorch (run by GitHub Actions).

Usage:
    python3 loc_stats.py count    # run on a develop checkout (full history)
    python3 loc_stats.py render   # run on a main checkout (README + history)

count mode:
    - Counts Java LOC per tracked root module (zamin-api, zamin-core,
      zamin-launcher, zamin-protocol-v1_8_8, tools) in the checked-out tree.
    - Resolves the commits from 1 / 7 / 30 days ago and counts module LOC
      there using temporary git worktrees (root commit if history is younger).
    - Snapshots the last 30 calendar days (PKT) for history backfill.
    - Writes a JSON snapshot to $LOC_SNAPSHOT (default /tmp/loc-snapshot.json).

render mode:
    - Reads the snapshot, upserts today's entry into .github/loc-history.json
      and backfills missing past days from the snapshot.
    - Rewrites the block between the LOC_STATS markers in README.md as a
      <details> "Development Statistics" dropdown: today's stats table with
      per-module 24h/7d/30d deltas, a diff-colored trend block (GitHub
      addition-green for lines added, deletion-red for lines removed) and a
      nested "Past stats" dropdown holding one collapsible card per recorded
      day.
"""

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone

MODULES = [
    "zamin-api",
    "zamin-core",
    "zamin-launcher",
    "zamin-protocol-v1_8_8",
    "tools",
]

PKT = timezone(timedelta(hours=5))  # Asia/Karachi (UTC+5, no DST)

SNAPSHOT_ENV = "LOC_SNAPSHOT"
DEFAULT_SNAPSHOT = "/tmp/loc-snapshot.json"
HISTORY_PATH = ".github/loc-history.json"
README = "README.md"
START = "<!-- LOC_STATS:START -->"
END = "<!-- LOC_STATS:END -->"

BACKFILL_DAYS = 30      # how many past days to backfill from git history
MAX_PAST_CARDS = 60     # how many per-day cards to render in README


# ---------------------------------------------------------------- helpers

def run_git(args, check=True):
    return subprocess.run(
        ["git"] + args, capture_output=True, text=True, check=check
    )


def resolve_src_ref():
    for ref in ("origin/develop", "develop", "HEAD"):
        result = run_git(["rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}"])
        if result.returncode == 0 and result.stdout.strip():
            return ref
    raise SystemExit("error: could not resolve the develop ref")


def count_tree(root):
    """Count Java lines per module under a directory (checked-out tree)."""
    modules = {}
    files = 0
    total = 0
    for module in MODULES:
        module_total = 0
        module_files = 0
        module_dir = os.path.join(root, module)
        if os.path.isdir(module_dir):
            for dirpath, _dirnames, filenames in os.walk(module_dir):
                for name in filenames:
                    if name.endswith(".java"):
                        module_files += 1
                        try:
                            with open(os.path.join(dirpath, name), "rb") as fh:
                                module_total += sum(1 for _ in fh)
                        except OSError:
                            pass
        modules[module] = module_total
        files += module_files
        total += module_total
    return {"total": total, "files": files, "modules": modules}


_worktree_cache = {}


def count_at_commit(sha):
    """Count Java lines per module at an old commit via a temp worktree."""
    if sha in _worktree_cache:
        return _worktree_cache[sha]
    tmp = tempfile.mkdtemp(prefix="loc-stats-")
    try:
        run_git(["worktree", "add", "--quiet", "--detach", tmp, sha])
        counts = count_tree(tmp)
    finally:
        run_git(["worktree", "remove", "--force", tmp], check=False)
        shutil.rmtree(tmp, ignore_errors=True)
    _worktree_cache[sha] = counts
    return counts


def last_commit_before(when, src_ref):
    result = run_git(["rev-list", "-1", f"--before={when}", src_ref], check=False)
    if result.returncode != 0:
        return ""
    return result.stdout.strip()


def root_commit(src_ref):
    result = run_git(["rev-list", "--max-parents=0", src_ref], check=False)
    shas = [line for line in result.stdout.split() if line]
    return shas[-1] if shas else ""


def fmt_num(number):
    return f"{number:,}"


def fmt_delta(number):
    return f"{number:+,}"


def fmt_cell_delta(now, base):
    if now == 0 and base == 0:
        return "—"
    return f"{now - base:+,}"


# ---------------------------------------------------------------- count

def cmd_count():
    src_ref = resolve_src_ref()
    now = datetime.now(PKT)
    today = now.date()

    current = count_tree(".")
    if current["total"] == 0:
        raise SystemExit("error: no Java lines found in the tracked modules")

    trends = {}
    for key, when in (("24h", "1 day ago"), ("7d", "7 days ago"), ("30d", "30 days ago")):
        sha = last_commit_before(when, src_ref)
        fallback = False
        if not sha:
            sha = root_commit(src_ref)
            fallback = True
        if not sha:
            continue
        base = count_at_commit(sha)
        trends[key] = {
            "sha": sha,
            "fallback": fallback,
            "total": base["total"],
            "modules": base["modules"],
        }

    backfill = {}
    for back in range(1, BACKFILL_DAYS + 1):
        day = today - timedelta(days=back)
        boundary = f"{(day + timedelta(days=1)).isoformat()} 00:00:00 +0500"
        sha = last_commit_before(boundary, src_ref)
        if sha:
            entry = count_at_commit(sha)
            backfill[day.isoformat()] = {
                "total": entry["total"],
                "files": entry["files"],
                "modules": entry["modules"],
            }

    run_git(["worktree", "prune"], check=False)

    snapshot = {
        "generated_at": now.isoformat(timespec="minutes"),
        "date": today.isoformat(),
        "modules": MODULES,
        "current": current,
        "trend": trends,
        "backfill": backfill,
    }
    path = os.environ.get(SNAPSHOT_ENV, DEFAULT_SNAPSHOT)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(snapshot, fh, indent=2)

    print(f"Tracked Java LOC: {fmt_num(current['total'])} lines in {fmt_num(current['files'])} files")
    for key, label in (("24h", "24 hours ago"), ("7d", "7 days ago"), ("30d", "30 days ago")):
        trend = trends.get(key)
        if trend:
            delta = current["total"] - trend["total"]
            extra = " (since start)" if trend["fallback"] else ""
            print(f"  vs {label}{extra}: {fmt_num(trend['total'])} -> {fmt_delta(delta)}")
    print(f"Snapshot written to {path} (backfill days: {len(backfill)})")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write("### Development Statistics (count)\n\n")
            fh.write(f"- Tracked Java LOC: **{fmt_num(current['total'])}** lines "
                     f"in {fmt_num(current['files'])} files\n")
            for key, label in (("24h", "24 hours ago"), ("7d", "7 days ago"),
                               ("30d", "30 days ago")):
                trend = trends.get(key)
                if trend:
                    delta = current["total"] - trend["total"]
                    fh.write(f"- vs {label}: {fmt_delta(delta)} lines\n")
            fh.write(f"- Backfilled days found in git history: {len(backfill)}\n\n")


# ---------------------------------------------------------------- render

def render_today(snap):
    current = snap["current"]
    trends = snap.get("trend", {})

    col_defs = []
    for key, label in (("24h", "24h"), ("7d", "7d"), ("30d", "30d")):
        trend = trends.get(key)
        if trend is None:
            continue
        header = "since start" if trend.get("fallback") else label
        col_defs.append((key, f"± {header}"))

    lines = []
    header_cells = ["Module", "Java LOC"] + [header for _k, header in col_defs]
    lines.append("| " + " | ".join(header_cells) + " |")
    lines.append("|---" * len(header_cells) + "|")
    for module in snap["modules"]:
        now_loc = current["modules"].get(module, 0)
        cells = [module, fmt_num(now_loc)]
        for key, _header in col_defs:
            base = trends[key]["modules"].get(module, 0)
            cells.append(fmt_cell_delta(now_loc, base))
        lines.append("| " + " | ".join(cells) + " |")
    total_cells = ["**Total**", f"**{fmt_num(current['total'])}**"]
    for key, _header in col_defs:
        delta = current["total"] - trends[key]["total"]
        total_cells.append(f"**{fmt_delta(delta)}**")
    lines.append("| " + " | ".join(total_cells) + " |")

    diff_lines = []
    labels = {"24h": "vs 24 hours ago", "7d": "vs 7 days ago", "30d": "vs 30 days ago"}
    for key, _header in col_defs:
        trend = trends[key]
        delta = current["total"] - trend["total"]
        label = labels[key] + (" (since start)" if trend.get("fallback") else "")
        if delta >= 0:
            diff_lines.append(f"+ added {fmt_num(delta)} lines {label}")
        else:
            diff_lines.append(f"- removed {fmt_num(-delta)} lines {label}")
    diff_block = "```diff\n" + "\n".join(diff_lines) + "\n```"

    return lines, diff_block


def render_past_cards(history, today):
    days = [day for day in sorted(history.keys(), reverse=True) if day != today]
    if not days:
        return ["<details>",
                "<summary>📜 Past stats</summary>",
                "",
                "*No past days recorded yet. One entry is kept per day (PKT); "
                "they accumulate automatically and appear here.*",
                "",
                "</details>"], 0

    cards = []
    for index, day in enumerate(days[:MAX_PAST_CARDS]):
        entry = history[day]
        prev_day = days[index + 1] if index + 1 < len(days) else None
        card = [
            "<details>",
            f"<summary>{day} — {fmt_num(entry['total'])} Java lines</summary>",
            "",
            "| Module | Java LOC |",
            "|---|---|",
        ]
        for module in MODULES:
            card.append(f"| {module} | {fmt_num(entry['modules'].get(module, 0))} |")
        card.append(f"| **Total** | **{fmt_num(entry['total'])}** |")
        card.append("")
        if prev_day:
            delta = entry["total"] - history[prev_day]["total"]
            if delta >= 0:
                trend_line = f"+ added {fmt_num(delta)} lines vs previous recorded day ({prev_day})"
            else:
                trend_line = f"- removed {fmt_num(-delta)} lines vs previous recorded day ({prev_day})"
            card.append("```diff")
            card.append(trend_line)
            card.append("```")
        else:
            card.append("*(first recorded day)*")
        card.append("")
        card.append("</details>")
        cards.append("\n".join(card))

    day_word = "day" if len(days) == 1 else "days"
    parts = [
        "<details>",
        f"<summary>📜 Past stats — {len(days)} recorded {day_word}</summary>",
        "",
    ]
    parts.append("\n\n".join(cards))
    if len(days) > MAX_PAST_CARDS:
        parts.append("")
        parts.append(f"*Showing the {MAX_PAST_CARDS} most recent days; "
                     f"{len(days) - MAX_PAST_CARDS} older entries are kept in "
                     f"`.github/loc-history.json`.*")
    parts.append("")
    parts.append("</details>")
    return parts, len(days)


def cmd_render():
    path = os.environ.get(SNAPSHOT_ENV, DEFAULT_SNAPSHOT)
    with open(path, encoding="utf-8") as fh:
        snap = json.load(fh)

    today = snap["date"]
    current = snap["current"]

    history = {}
    if os.path.exists(HISTORY_PATH):
        try:
            with open(HISTORY_PATH, encoding="utf-8") as fh:
                history = json.load(fh)
        except (json.JSONDecodeError, OSError):
            history = {}

    for day, entry in snap.get("backfill", {}).items():
        if day not in history:
            history[day] = entry

    history[today] = {
        "total": current["total"],
        "files": current["files"],
        "modules": current["modules"],
    }

    ordered = {day: history[day] for day in sorted(history.keys(), reverse=True)}
    os.makedirs(os.path.dirname(HISTORY_PATH), exist_ok=True)
    with open(HISTORY_PATH, "w", encoding="utf-8") as fh:
        json.dump(ordered, fh, indent=2)
        fh.write("\n")

    # ----- build the dropdown block -----
    today_table, diff_block = render_today(snap)
    past_parts, past_count = render_past_cards(ordered, today)

    trends = snap.get("trend", {})
    headline = f"📊 Development Statistics — {fmt_num(current['total'])} Java lines"
    trend_24h = trends.get("24h")
    if trend_24h:
        delta = current["total"] - trend_24h["total"]
        headline += f" · 24h {fmt_delta(delta)}"

    module_list = ", ".join(f"`{m}`" for m in snap["modules"])
    tools_note = ""
    if current["modules"].get("tools", 0) == 0:
        tools_note = (" `tools` currently contains no Java code; it is counted "
                      "automatically once it does.")

    generated = snap.get("generated_at", "")
    updated = generated[:16].replace("T", " ") if generated else ""

    block_parts = [
        "<details>",
        f"<summary>{headline} (click to expand)</summary>",
        "",
        f"### 📈 Today — {today}",
        "",
    ]
    block_parts.extend(today_table)
    block_parts.append("")
    block_parts.append(diff_block)
    block_parts.append("")
    block_parts.append(
        f"*{fmt_num(current['files'])} Java files · tracked modules: {module_list} · "
        f"dates in PKT (UTC+5) · auto-updated on every push to `develop` and every "
        f"6 hours · last update {updated} PKT.{tools_note}*"
    )
    block_parts.append("")
    block_parts.extend(past_parts)
    block_parts.append("")
    block_parts.append("</details>")
    block = f"{START}\n" + "\n".join(block_parts) + f"\n{END}"

    # ----- write README.md -----
    if not os.path.exists(README):
        with open(README, "w", encoding="utf-8") as fh:
            fh.write("# ZaminTorch\n\n")
        print("README.md did not exist; created it.")

    with open(README, encoding="utf-8") as fh:
        content = fh.read()

    pattern = re.compile(re.escape(START) + r".*?" + re.escape(END), re.DOTALL)
    if pattern.search(content):
        updated_content = pattern.sub(lambda _m: block, content, count=1)
        action = "Updated"
    else:
        section = f"## Development Statistics\n\n{block}\n"
        heading = re.search(r"^#[^\n]*$", content, re.MULTILINE)
        if heading:
            pos = heading.end()
            updated_content = content[:pos] + "\n\n" + section + content[pos:]
        else:
            updated_content = "\n" + section + content
        action = "Inserted"

    if updated_content != content:
        with open(README, "w", encoding="utf-8") as fh:
            fh.write(updated_content)
        print(f"{action} Development Statistics dropdown: "
              f"{fmt_num(current['total'])} Java lines, {past_count} past days.")
    else:
        print(f"README.md already current: {fmt_num(current['total'])} Java lines.")

    env_file = os.environ.get("GITHUB_ENV")
    if env_file:
        with open(env_file, "a", encoding="utf-8") as fh:
            fh.write(f"LOC_TOTAL={current['total']}\n")


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("count", "render"):
        raise SystemExit("usage: loc_stats.py count | render")
    if sys.argv[1] == "count":
        cmd_count()
    else:
        cmd_render()


if __name__ == "__main__":
    main()
