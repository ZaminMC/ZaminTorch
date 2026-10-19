package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.concurrent.DomainWorkerPool;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * THE LIGHTING BATCH SHAPE (§22's last named Phase-5 item): the pregen's
 * initial light computes leave the owning lane for the domain worker pool,
 * in WAVES — the staleness protocol the gate's own verdict demanded ("the
 * pipelined capture would see pre-apply neighbor states for the overlapping
 * 3x3 — the staleness protocol is that step's gate").
 *
 * <p>THE WAVE DECOMPOSITION (the staleness protocol, and the whole of it):
 * the pregen area partitions into four waves by {@code (x & 1, z & 1)}; any
 * two same-wave chunks differ by at least 2 in x or in z, so no same-wave
 * chunk sits inside another's 3x3 capture neighborhood. A wave's protocol:
 * generate every chunk of the wave on the lane, apply the persisted delta
 * overlay (the same rows {@code installGenerated} re-applies — the capture
 * must see the delta-corrected materials), capture every 3x3 read set on the
 * lane, dispatch every pure compute to the pool, join, then apply-and-install
 * in the wave's fixed order. Because a wave's captures ALL precede its first
 * install, no capture can see a same-wave sibling's half-applied state — the
 * pipelined staleness the gate refused is structurally impossible inside a
 * wave, and across waves the earlier wave is fully installed before the next
 * wave's first capture, so every capture sees a consistent, fully-applied
 * world.</p>
 *
 * <p>THE VISIBILITY THEOREM (what makes each capture correct): at any
 * capture in wave W, the visible set is exactly the pregen area's waves
 * before W (plus any pre-existing published chunks — the restore-then-pregen
 * shape), and NOTHING else. Same-wave siblings are invisible by
 * construction; later waves are not yet generated. The sequential walk's
 * visibility at a chunk is the column-major prefix — a DIFFERENT set in
 * general (the wave order and the column-major order disagree on many
 * neighbor pairs), which is why the batch is not claimed sequential-
 * equivalent by fiat: the equivalence/divergence is MEASURED by the batch
 * twin-world test, and the install-order argument that recovers most of the
 * difference is the initial compute's own step 4 — the LATER-installed chunk
 * of a cross-wave pair reconciles the shared border against the earlier
 * chunk's published state (the neighbor strip re-derive darkens phantom-fed
 * cells; the strip's seeds and the compute's BFS restore the missed inflow
 * in both directions — the recorded neighbor writes land because the earlier
 * chunk is loaded).</p>
 *
 * <p>THE ORDER: waves fixed as (0,0), (0,1), (1,0), (1,1); within a wave,
 * column-major (dx outer, dz inner) — the sequential walk's own local order,
 * so the two walks agree on every pair whose wave order happens to match the
 * column-major order. Deterministic by construction: same area, same seed,
 * same state — the same writes in the same order (the determinism test's
 * own claim).</p>
 *
 * <p>THE HANDOFF: each result reaches its chunk through
 * {@link LightEngine#stashInitial} + {@code installGenerated} — the hook
 * replays the precomputed writes inside the ONE publication path (§344: the
 * chunk appears fully generated AND fully lit; publication stays atomic).
 * The capture and the apply never leave the owning lane; only the pure
 * compute over the immutable {@link LightRegion} does (§6.5's explicit
 * inputs, §6.4's no live references).</p>
 */
public final class LightBatchPregen {

    private LightBatchPregen() {
    }

    /**
     * Pregenerates the square area the way the server's boot walk does —
     * every chunk generated, lit and published — with the initial light
     * computes dispatched to the pool in waves.
     *
     * @param world   the live world (owning-lane context; generation,
     *                capture, apply and install all run on the caller)
     * @param light   the world's light engine (the stash consumer)
     * @param center  the area's center chunk
     * @param radius  the area's radius in chunks (the full view: radius r
     *                covers (2r+1)^2 chunks)
     * @param pool    the domain worker pool for the pure computes
     */
    public static void pregenerate(EngineWorld world, LightEngine light,
                                   ChunkPosition center, int radius, DomainWorkerPool pool) {
        for (int wave = 0; wave < 4; wave++) {
            int wx = wave & 1;
            int wz = wave >> 1;
            // The wave's positions, column-major (the sequential walk's own
            // local order).
            List<ChunkPosition> positions = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.x() + dx;
                    int z = center.z() + dz;
                    if ((x & 1) == wx && (z & 1) == wz) {
                        positions.add(new ChunkPosition(x, z));
                    }
                }
            }
            if (positions.isEmpty()) {
                continue;
            }
            // 1. Generate the whole wave on the lane (the delta overlay
            //    included — the capture must see the delta-corrected
            //    materials; installGenerated's re-application is the same
            //    writes again, changing nothing).
            List<EngineChunk> fresh = new ArrayList<>(positions.size());
            for (ChunkPosition position : positions) {
                if (world.peek(position) != null) {
                    continue; // already published (the restored world): nothing to pregenerate
                }
                EngineChunk chunk = new EngineChunk(position, world.airType());
                world.generator().generate(chunk);
                world.applyPersistedDeltas(chunk);
                fresh.add(chunk);
            }
            if (fresh.isEmpty()) {
                continue;
            }
            // 2. Capture every 3x3 read set on the lane — before any of the
            //    wave's installs (the staleness protocol: no capture can see
            //    a same-wave sibling's half-applied state).
            List<LightRegion> regions = new ArrayList<>(fresh.size());
            for (EngineChunk chunk : fresh) {
                regions.add(LightRegion.capture(world, chunk));
            }
            // 3. The pure computes leave the lane (the pool's own join is
            //    part of the protocol — the applies start only when every
            //    compute of the wave finished). Each job writes its result
            //    into its slot; the join's happens-before edge publishes
            //    every slot to this lane.
            List<InitialLightResult[]> slots = new ArrayList<>(fresh.size());
            List<Runnable> jobs = new ArrayList<>(fresh.size());
            for (int i = 0; i < fresh.size(); i++) {
                LightRegion region = regions.get(i);
                InitialLightResult[] slot = new InitialLightResult[1];
                slots.add(slot);
                jobs.add(() -> slot[0] = InitialLightCompute.compute(region));
            }
            pool.dispatchAndJoin(jobs);
            // 4. Apply + install in the wave's fixed order — each stash is
            //    consumed by its own install's hook, on this lane.
            for (int i = 0; i < fresh.size(); i++) {
                EngineChunk chunk = fresh.get(i);
                light.stashInitial(chunk.position(), slots.get(i)[0]);
                world.installGenerated(chunk.position(), chunk);
            }
        }
    }
}
