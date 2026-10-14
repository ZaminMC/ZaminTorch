#!/usr/bin/env sh
# ZaminTorch release helper — the local GitHub-CLI companion to
# .github/workflows/release.yml.
#
# Every push to develop already cuts the next numbered dev build
# (torch-1.8.8-N) automatically. This script covers the rest:
#
#   scripts/release.sh                      watch the latest run to completion
#   scripts/release.sh "a release title"    dispatch an off-cycle release with
#                                           a title, then watch it
#   scripts/release.sh --status             recent runs + the latest release
#   scripts/release.sh --notes <tag>        print a published release's notes
#
# Titles can also ride regular pushes: add a "Release-Title: <title>" trailer
# to any commit message on develop and the pipeline picks it up.
#
# Requires: gh, authenticated (gh auth login, or GH_TOKEN exported).
set -eu

REPO="${GH_REPO:-ZaminMC/ZaminTorch}"
WORKFLOW="release.yml"
BRANCH="develop"

die() { echo "release.sh: $*" >&2; exit 1; }

command -v gh >/dev/null 2>&1 || die "gh is not installed — see https://cli.github.com/"
gh auth status >/dev/null 2>&1 || die "gh is not authenticated — run: gh auth login"

latest_run_id() {
    gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" \
        --limit 1 --json databaseId --jq '.[0].databaseId // empty'
}

watch_run() {
    id=$(latest_run_id) || true
    [ -n "${id:-}" ] || die "no run found for $WORKFLOW@$BRANCH yet"
    echo "==> watching run $id ($REPO/actions/runs/$id)"
    gh run watch "$id" --repo "$REPO" --exit-status
    echo "==> run finished"
}

show_release() {
    if ! gh release view --repo "$REPO" --json name,tagName,isPrerelease,author,publishedAt,assets \
        -q '"Latest release: " + .name + "  (tag " + .tagName + ", prerelease " + (.isPrerelease|tostring) + ", by " + .author.login + " at " + .publishedAt + ")\nAssets:\n" + ([.assets[].name] | join("\n"))' 2>/dev/null; then
        echo "No releases found."
    fi
}

case "${1:-}" in
    --status)
        echo "==> recent runs ($WORKFLOW@$BRANCH)"
        gh run list --repo "$REPO" --workflow "$WORKFLOW" --branch "$BRANCH" --limit 5 \
            --json databaseId,status,conclusion,displayTitle,createdAt \
            -q '.[] | "  run \(.databaseId): \(.status)\(if .conclusion then " (" + .conclusion + ")" else "" end) — \(.displayTitle) (\(.createdAt))"'
        echo
        show_release
        ;;
    --notes)
        [ $# -ge 2 ] || die "usage: scripts/release.sh --notes <tag>"
        gh release view "$2" --repo "$REPO" --json body -q .body
        ;;
    --help|-h)
        sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
        ;;
    "")
        watch_run
        show_release
        ;;
    *)
        TITLE=$1
        echo "==> dispatching $WORKFLOW with title: $TITLE"
        gh workflow run "$WORKFLOW" --repo "$REPO" --ref "$BRANCH" -f title="$TITLE"
        # Give the dispatch a moment to register, then watch it.
        sleep 5
        watch_run
        show_release
        ;;
esac
