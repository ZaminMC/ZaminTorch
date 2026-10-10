# Torch Engineering Ledger

**Status date:** 2026-10-10  
**Branch inspected:** `develop`  
**Purpose:** record verified repository state and separate implemented behavior from proposals and unverified work.

## Current repository

- Build: Gradle Kotlin DSL, Java 21 toolchain, JUnit 5.
- Modules: `:zamin-api`, `:zamin-core`, `:zamin-protocol-v1_8_8`, `:zamin-launcher`.
- Current build distribution version in `zamin-launcher/build.gradle.kts`: `0.2.0-dev.16`.
- Latest inspected release: [v0.2.0-dev.16](https://github.com/ZaminMC/ZaminTorch/releases/tag/v0.2.0-dev.16), with a server JAR and distribution ZIP. Release notes report 508 passing tests for that build; this inspection did not rerun them.
- The committed 1.8.8 reference tree is `reference/1.8.8/`, described by the repository as 1,634 decompiled classes.
- `Folia-ver-26.2.x.zip` is committed at the repository root.
- The requested `1.8.8 - mechanics.zip` is not present in the inspected `develop` tree. Recover it before claiming it was inspected or using it as the required implementation source.
- No Minestom dependency is declared in the inspected Gradle build. `docs/ENGINEERING_PLAN.md` records Minestom as deferred under ADR-0001.
- This pass used GitHub repository access, not the developer's local checkout. Local uncommitted changes, local build status, and local environment state are therefore unknown.

## Architecture that exists today

- `EngineTicker` is a single simulation loop. It drains a cross-thread deferred-work queue at tick start, advances world time, and calls the configured tick handler.
- Missed ticks are skipped instead of burst-caught-up.
- `EngineWorld` is the authoritative world state. Mutations check the recorded owner thread; chunks are published through concurrent map operations, but mutable chunk values do not become thread-safe through that map.
- Chunk generation and chunk-load preparation currently execute synchronously before publication.
- The 1.8.8 protocol adapter uses Netty; network event-loop execution is distinct from world mutation ownership.
- Native API, engine, protocol, and launcher are separate Gradle modules. A Bukkit/Spigot/Paper binary compatibility runtime is not present in the inspected module list.

## Engineering status

Statuses: `NOT INVESTIGATED`, `INVESTIGATED`, `IN PROGRESS`, `IMPLEMENTED`, `VERIFIED`, `BLOCKED`.

| Area | Status | Evidence / next work |
|---|---|---|
| Build and module structure | VERIFIED (repository metadata) | Gradle settings and module build files inspected; actual build not rerun in this environment |
| Current world owner checks | INVESTIGATED | `EngineWorld` owner checks and `EngineTicker` context inspected; audit every mutable access path |
| Multithreaded simulation | NOT INVESTIGATED / not implemented | Current runtime is single-owner; proposed design is in `docs/CONCURRENCY_ARCHITECTURE.md` |
| Minestom integration | BLOCKED / deferred | No dependency currently present; see `docs/ENGINEERING_PLAN.md` ADR-0001 |
| Folia source audit | IN PROGRESS | Archive committed; full extraction and per-Java-path patch inventory still required; see `docs/FOLIA_FORENSIC_AUDIT.md` |
| Vanilla 1.8.8 reference source | VERIFIED (present) | `reference/1.8.8/`; source must be inspected per mechanic before porting |
| `1.8.8 - mechanics.zip` | BLOCKED | Not found in inspected `develop` tree |
| Vanilla compatibility coverage | IN PROGRESS | Detailed per-mechanic state is in `VANILLA_1_8_8_COMPATIBILITY.md` |
| Native API | IN PROGRESS | `zamin-api`; inspect each public contract for ownership, nullability, lifecycle, and mutation semantics |
| Bukkit/Spigot/Paper compatibility | NOT INVESTIGATED | No compatibility modules in current settings; do not claim plugin compatibility |
| Release pipeline | IN PROGRESS | Manual dev.16 release exists; no verified automated build-and-release workflow was found in the inspected tree |
| Runtime performance baseline | NOT INVESTIGATED | Need reproducible workload definitions and fresh measurements |

## Current concurrency proposal

See:

- `docs/CONCURRENCY_ARCHITECTURE.md`
- `docs/MINESTOM_CONCURRENCY_BASELINE.md`
- `docs/FOLIA_FORENSIC_AUDIT.md`

The proposal is not implementation. Its core recommendation is single-writer ownership domains, a shared bounded simulation pool, immutable compute inputs/results, validated result application, and explicit cross-owner intents. It explicitly keeps dynamic migration and conflict-aware multi-writer simulation disabled until correctness tests justify them.

## Next steps, ordered

1. Recover `1.8.8 - mechanics.zip`; if the intended file is only on a local machine, add it to the repository in a deliberate commit after checking its contents and licensing.
2. Extract the committed Folia archive outside the repository and enumerate every modified Java source path in both base patches. Do not substitute a README summary for this inventory.
3. Complete source call-path tracing for the current network-to-tick handoff, block listeners, chunk generation, save snapshots, and shutdown.
4. Review the concurrency proposal against the complete Folia evidence, then freeze the initial ownership and cross-owner contracts before runtime concurrency changes.
5. Run `./gradlew build` in a real checkout and preserve the actual output. Do not report test counts from an old release as current results.
6. Resume the next vanilla mechanics slice from the original 1.8.8 reference and the recovered mechanics archive, adding behavior-level tests and updating the compatibility ledger.
7. Produce a new dev release only from a tested build, with a version/tag that matches the launcher update checker and a changelog describing the actual code changes.

## Security and repository safety

- Never put GitHub credentials in source, commits, issues, or worklogs. A credential pasted into chat should be revoked and replaced.
- No runtime source was changed by the 2026-10-10 documentation pass.
- All changes in this pass were committed directly to `develop`; no destructive Git operations or branch switches were performed.
