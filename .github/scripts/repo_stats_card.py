#!/usr/bin/env python3
"""Generate the ZaminTorch repository statistics card from develop."""

import json
import os
import subprocess
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

OWNER = "ZaminMC"
REPO = "ZaminTorch"
MODULES = ["zamin-api", "zamin-core", "zamin-launcher", "zamin-protocol-v1_8_8", "tools"]
OUTPUT = Path("docs/repository-stats.svg")
API = f"https://api.github.com/repos/{OWNER}/{REPO}"


def github_get(path, params=None):
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {os.environ.get('GITHUB_TOKEN', '')}",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "ZaminTorch-repository-stats",
    })
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
    result = subprocess.run(["git", "rev-list", "--count", "HEAD"], capture_output=True, text=True, check=True)
    return int(result.stdout.strip())


def format_number(value):
    return f"{value:,}"


def escape_xml(value):
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def api_count(query):
    data = github_get("/search/issues", {"q": f"repo:{OWNER}/{REPO} {query}", "per_page": 1})
    return int(data.get("total_count", 0))


def latest_release():
    try:
        release = github_get("/releases/latest")
        return release.get("tag_name") or release.get("name") or "No releases"
    except Exception:
        return "No releases"


def text(x, y, value, size, weight="400", cls="body", anchor="start", letter_spacing=None):
    spacing = f' letter-spacing="{letter_spacing}px"' if letter_spacing is not None else ""
    return f'<text x="{x}" y="{y}" class="{cls}" font-size="{size}px" font-weight="{weight}" text-anchor="{anchor}"{spacing}>{escape_xml(value)}</text>'


def stat_pill(x, y, width, label, value, icon):
    return "\n".join([
        f'<rect x="{x}" y="{y}" width="{width}" height="76" rx="22" class="glass"/>',
        f'<circle cx="{x + 29}" cy="{y + 38}" r="15" class="icon-bg"/>',
        icon.replace("{X}", str(x + 29)).replace("{Y}", str(y + 38)),
        text(x + 55, y + 32, label.upper(), 9, "650", "label", letter_spacing=1.2),
        text(x + 55, y + 55, value, 18, "650", "value"),
    ])


def build_svg(loc, release, commits, prs_open, prs_closed, issues_open, issues_closed):
    generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    release_display = release if len(release) <= 20 else release[:19] + "…"
    pr_icon = '<path d="M{X} {Y}-6 0M{X} {Y}l4-4M{X} {Y}l4 4M{X} {Y}-1-10" class="icon"/>'
    issue_icon = '<circle cx="{X}" cy="{Y}" r="7" class="icon"/><path d="M{X} {Y}-3 0M{X} {Y}+3 0" class="icon"/>'
    check_icon = '<path d="M{X} {Y}-6 0 4 4 8-8" class="icon"/>'

    parts = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="420" viewBox="0 0 1200 420" role="img" aria-labelledby="title desc">',
        '<title id="title">ZaminTorch engineering statistics</title>',
        '<desc id="desc">Live repository statistics from the develop branch.</desc>',
        '<defs>',
        '  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f8fafc"/><stop offset="0.52" stop-color="#eef2f7"/><stop offset="1" stop-color="#e6ebf2"/></linearGradient>',
        '  <radialGradient id="orb1"><stop offset="0" stop-color="#8bbcff" stop-opacity="0.72"/><stop offset="1" stop-color="#8bbcff" stop-opacity="0"/></radialGradient>',
        '  <radialGradient id="orb2"><stop offset="0" stop-color="#c6a7ff" stop-opacity="0.48"/><stop offset="1" stop-color="#c6a7ff" stop-opacity="0"/></radialGradient>',
        '  <linearGradient id="glass" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity="0.78"/><stop offset="1" stop-color="#ffffff" stop-opacity="0.38"/></linearGradient>',
        '  <linearGradient id="number" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#111827"/><stop offset="1" stop-color="#334155"/></linearGradient>',
        '  <filter id="shadow" x="-20%" y="-30%" width="140%" height="170%"><feDropShadow dx="0" dy="18" stdDeviation="24" flood-color="#64748b" flood-opacity="0.16"/></filter>',
        '  <filter id="soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="34"/></filter>',
        '  <style>',
        '    .bg{fill:url(#bg);stroke:#ffffff;stroke-opacity:.72}.glass{fill:url(#glass);stroke:#ffffff;stroke-opacity:.72}.title{fill:#0f172a}.label{fill:#64748b}.value{fill:#111827}.number{fill:url(#number)}.muted{fill:#94a3b8}.icon-bg{fill:#ffffff;fill-opacity:.72}.icon{fill:none;stroke:#64748b;stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}',
        '    @media(prefers-color-scheme:dark){.bg{fill:#111318;stroke:#30343c}.glass{fill:#1b1f27;fill-opacity:.78;stroke:#3a404b}.title{fill:#f8fafc}.label{fill:#9aa4b2}.value{fill:#f8fafc}.number{fill:#ffffff}.muted{fill:#687384}.icon-bg{fill:#262c36}.icon{stroke:#aab4c2}}',
        '  </style>',
        '</defs>',
        '<rect x="10" y="10" width="1180" height="400" rx="34" class="bg" filter="url(#shadow)"/>',
        '<circle cx="1030" cy="-20" r="270" fill="url(#orb1)" filter="url(#soft)"/>',
        '<circle cx="210" cy="430" r="230" fill="url(#orb2)" filter="url(#soft)"/>',
        '<circle cx="58" cy="59" r="6" fill="#0f172a" opacity=".88"/>',
        text(78, 64, "ZAMINTORCH", 13, "700", "title", letter_spacing=2.5),
        text(78, 88, "ENGINEERING METRICS", 9, "650", "label", letter_spacing=1.7),
        '<rect x="1010" y="43" width="118" height="30" rx="15" class="glass"/>',
        text(1069, 62, "DEVELOP", 9, "700", "label", "middle", 1.4),
        text(56, 190, format_number(loc), 72, "700", "number"),
        text(58, 218, "JAVA LINES", 10, "700", "label", letter_spacing=2.1),
        text(58, 244, "tracked codebase", 11, "400", "muted"),
        '<rect x="690" y="116" width="438" height="112" rx="30" class="glass"/>',
        text(720, 151, "LATEST RELEASE", 9, "700", "label", letter_spacing=1.5),
        text(720, 190, release_display, 27, "650", "value"),
        text(720, 211, "GitHub", 10, "400", "muted"),
        '<line x1="56" y1="275" x2="1144" y2="275" stroke="#94a3b8" stroke-opacity=".18"/>',
        stat_pill(56, 298, 258, "PRs open", format_number(prs_open), pr_icon),
        stat_pill(330, 298, 258, "PRs closed", format_number(prs_closed), check_icon),
        stat_pill(604, 298, 258, "Issues open", format_number(issues_open), issue_icon),
        stat_pill(878, 298, 266, "Issues closed", format_number(issues_closed), check_icon),
        text(1144, 392, generated, 9, "400", "muted", "end"),
        '</svg>',
    ]
    return "\n".join(part for part in parts if part)


def main():
    loc, _ = count_java_loc()
    commits = count_commits()
    prs_open = api_count("is:pr is:open")
    prs_closed = api_count("is:pr is:closed")
    issues_open = api_count("is:issue is:open")
    issues_closed = api_count("is:issue is:closed")
    release = latest_release()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(build_svg(loc, release, commits, prs_open, prs_closed, issues_open, issues_closed), encoding="utf-8")
    print(f"Generated {OUTPUT}: {loc:,} Java LOC, {commits:,} commits")
    print(f"PRs: {prs_open} open / {prs_closed} closed")
    print(f"Issues: {issues_open} open / {issues_closed} closed")
    print(f"Latest release: {release}")


if __name__ == "__main__":
    main()
