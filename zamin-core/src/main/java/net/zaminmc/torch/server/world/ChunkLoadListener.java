package net.zaminmc.torch.server.world;

/**
 * Engine-internal hook for freshly generated chunks: fired once, after
 * generation and delta replay finished but BEFORE the chunk is published, so
 * the chunk becomes visible only fully generated AND fully lit (§344 — the
 * publication moment stays atomic; no observer ever serializes a half-lit
 * chunk).
 *
 * <p>The light engine is the current subscriber; persistence and others may
 * follow. Implementers must not call back into chunk generation (peek
 * neighbors only) and must not commit block mutations.</p>
 */
public interface ChunkLoadListener {

    void onChunkGenerated(EngineWorld world, EngineChunk chunk);
}
