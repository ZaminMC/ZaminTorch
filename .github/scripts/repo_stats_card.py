#!/usr/bin/env python3
"""Generate the ZaminTorch Fluent-style repository statistics card.

The source tree is always read from develop. The generated SVG is written to
main so the public README presents a stable snapshot of development activity.
"""

import json
import os
import subprocess
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

OWNER = "ZaminMC"
REPO = "ZaminTorch"
MODULES = [
    "zamin-api",
    "zamin-core",
    "zamin-launcher",
    "zamin-protocol-v1_8_8",
    "tools",
]
OUTPUT = Path("docs/repository-stats.svg")
API = f"https://api.github.com/repos/{OWNER}/{REPO}"


def github_get(path, params=None):
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN', '')}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "ZaminTorch-repository-stats",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def count_java_loc():
    total = 0
    files = 0
    for module in MODULES:
        root = Path(module)
        if not root.is_dir():
            continue
        for path in root.rglob("*.java"):
            try:
                with path.open("rb") as handle:
                    total += sum(1 for _ in handle)
                files += 1
            except OSError:
                pass
    return total, files


def count_commits():
    result = subprocess.run(
        ["git", "rev-list", "--count", "HEAD"],
        capture_output=True,
        text=True,
        check=True,
    )
    return int(result.stdout.strip())


def format_number(value):
    return f"{value:,}"


def escape_xml(value):
    return (
        str(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def api_count(query):
    data = github_get("/search/issues", {"q": f"repo:{OWNER}/{REPO} {query}", "per_page": 1})
    return int(data.get("total_count", 0))


def latest_release():
    try:
        release = github_get("/releases/latest")
        return release.get("tag_name") or release.get("name") or "No releases"
    except Exception:
        return "No releases"


def text(x, y, value, size, weight="400", cls="body", anchor="start"):
    return (
        f'<text x="{x}" y="{y}" class="{cls}" font-size="{size}px" '
        f'font-weight="{weight}" text-anchor="{anchor}">{escape_xml(value)}</text>'
    )


def metric(x, y, width, label, value, sublabel=""):
    return "\n".join([
        f'<rect x="{x}" y="{y}" width="{width}" height="112" rx="16" class="metric"/>',
        text(x + 20, y + 30, label.upper(), 11, "600", "label"),
        text(x + 20, y + 69, value, 25, "650", "value"),
        text(x + 20, y + 94, sublabel, 11, "400", "muted") if sublabel else "",
    ])


def build_svg(loc, files, release, commits, prs_open, prs_closed, issues_open, issues_closed):
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    release_display = release if len(release) <= 18 else release[:17] + "…"

    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="424" viewBox="0 0 1200 424" role="img" aria-labelledby="title desc">',
        '<title id="title">ZaminTorch repository statistics</title>',
        '<desc id="desc">Repository statistics calculated from the develop branch and displayed from main.</desc>',
        '<defs>',
        '  <linearGradient id="surface" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#f3f3f3"/></linearGradient>',
        '  <linearGradient id="accent" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#0078d4"/><stop offset="1" stop-color="#5b5fc7"/></linearGradient>',
        '  <filter id="shadow" x="-20%" y="-20%" width="140%" height="150%"><feDropShadow dx="0" dy="8" stdDeviation="14" flood-opacity="0.12"/></filter>',
        '  <style>',
        '    .surface{fill:url(#surface);stroke:#dedede}.metric{fill:#ffffff;stroke:#e5e5e5}.title{fill:#1f1f1f}.body{fill:#242424}.label{fill:#5f5f5f}.value{fill:#1f1f1f}.muted{fill:#777}.accent{fill:#0078d4}.rule{stroke:#e1e1e1}',
        '    @media(prefers-color-scheme:dark){.surface{fill:#202020;stroke:#383838}.metric{fill:#292929;stroke:#3b3b3b}.title{fill:#f5f5f5}.body{fill:#eeeeee}.label{fill:#b8b8b8}.value{fill:#ffffff}.muted{fill:#999}.rule{stroke:#3a3a3a}}',
        '  </style>',
        '</defs>',
        '<rect x="12" y="12" width="1176" height="400" rx="28" class="surface" filter="url(#shadow)"/>',
        '<circle cx="56" cy="62" r="18" fill="url(#accent)"/>',
        '<path d="M48 62h16M56 54v16" stroke="#fff" stroke-width="2.4" stroke-linecap="round"/>',
        text(88, 58, "ZaminTorch", 27, "650", "title"),
        text(88, 81, "Repository telemetry", 13, "450", "muted"),
        '<rect x="1010" y="43" width="130" height="34" rx="17" fill="#e8f3fc"/>',
        text(1075, 65, "DEVELOP", 11, "650", "accent", "middle"),
        '<line x1="40" y1="106" x2="1160" y2="106" class="rule"/>',
        metric(40, 126, 266, "Java LOC", format_number(loc), "tracked from develop"),
        metric(318, 126, 266, "Java files", format_number(files), "tracked modules only"),
        metric(596, 126, 266, "Latest release", release_display, "GitHub release"),
        metric(874, 126, 266, "Commits", format_number(commits), "develop history"),
        metric(40, 254, 266, "Pull requests", format_number(prs_open), "open"),
        metric(318, 254, 266, "Pull requests", format_number(prs_closed), "closed"),
        metric(596, 254, 266, "Issues", format_number(issues_open), "open"),
        metric(874, 254, 266, "Issues", format_number(issues_closed), "closed"),
        text(40, 397, f"Generated {now} · source: develop · card: main", 10, "400", "muted"),
        '</svg>',
    ]
    return "\n".join(part for part in parts if part)


def main():
    loc, files = count_java_loc()
    commits = count_commits()
    prs_open = api_count("is:pr is:open")
    prs_closed = api_count("is:pr is:closed")
    issues_open = api_count("is:issue is:open")
    issues_closed = api_count("is:issue is:closed")
    release = latest_release()

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        build_svg(loc, files, release, commits, prs_open, prs_closed, issues_open, issues_closed),
        encoding="utf-8",
    )
    print(f"Generated {OUTPUT}: {loc:,} Java LOC, {files:,} Java files, {commits:,} commits")
    print(f"PRs: {prs_open} open / {prs_closed} closed")
    print(f"Issues: {issues_open} open / {issues_closed} closed")
    print(f"Latest release: {release}")


if __name__ == "__main__":
    main()
