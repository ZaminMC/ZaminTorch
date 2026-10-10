# Folia 26.2.x Forensic Audit

**Status: incomplete — inventory not yet produced. Do not treat this document as a completed Folia audit.**

## Source availability

The repository's `develop` tree contains `Folia-ver-26.2.x.zip` at the root (837,676 bytes; Git blob `fb817f8be9fea6532fff9fe901a526c2ab40dbd5`). This confirms the archive is committed and versioned. The GitHub inspection interface used in this pass can read UTF-8 repository files but does not provide the archive bytes as a usable local file, and the attempted direct binary retrieval was unavailable. Therefore, the patch paths, hunk-level behavior, and modified Java source paths have **not** been enumerated in this pass.

No path counts, class names, or patch findings should be inferred from approximate counts in the assignment. The archive itself must be extracted and enumerated before any inventory can be marked complete.

## Required audit procedure

1. Extract the exact committed archive without modifying the repository.
2. Read `README.md`, `update.txt`, `REGION_LOGIC.md`, and `PROJECT_DESCRIPTION.md`.
3. Identify the exact Minecraft-side and Paper-side `0001-Region-Threading-Base.patch` files and all region-threading-related follow-up patches.
4. Enumerate every Java source path modified by each base patch; preserve duplicate paths if the same source path is modified in both patches, while identifying the patch/hunk separately.
5. For each path, record source path, patch/hunk, original behavior, changed behavior, inferred ownership boundary, change category, race/regression addressed, Torch relevance, recommendation, evidence, and uncertainty.
6. Trace high-risk operations through all related patches: block/neighbor updates, redstone, chunk access and generation, entity movement, passengers/vehicles, teleport, pending logins, player/chunk throughput, watchdog, profiler, and plugin scheduler APIs.
7. Separate explicit explanations present in patch comments/docs from architectural inference.
8. Link each Torch decision in `CONCURRENCY_ARCHITECTURE.md` to the exact audited path/hunk and to a test that will validate the chosen adaptation.

## Inventory format

Use one row per modified Java source path per relevant patch:

| Patch | Java source path | Hunk/method | Original behavior | Folia change | Ownership/context | Change category | Intended safety property | Torch recommendation | Evidence / uncertainty |
|---|---|---|---|---|---|---|---|---|---|

Do not collapse multiple paths into one summary row. Repetitive entries may share a rationale, but each path must remain traceable.

## Audit status

| Area | Status |
|---|---|
| Archive presence and Git identity | Verified |
| README and metadata files read from archive | Blocked pending archive extraction |
| Exact base-patch Java path counts | Not investigated |
| Minecraft-side base patch inventory | Not investigated |
| Paper-side base patch inventory | Not investigated |
| Scheduler / regionizer method-level trace | Not investigated |
| Chunk and world-state boundary changes | Not investigated |
| Block updates, redstone, pistons, fluids | Not investigated |
| Entity transfer, vehicles, passengers, teleports | Not investigated |
| Pending login / chunk throughput / worldgen read patches | Not investigated |
| Plugin scheduler and Folia support API patches | Not investigated |
| Profiler, watchdog, diagnostics, shutdown | Not investigated |
| Decision-to-patch-to-test traceability | Not investigated |

## Hard rule

Until the archive is extracted and the inventory is complete, describe the proposed Torch architecture as a conservative design direction informed by the current Torch baseline, **not** as a fully validated synthesis of Folia's implementation. Do not claim that Torch has adopted, improved upon, or exceeded any specific Folia mechanism without evidence.
