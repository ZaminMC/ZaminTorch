package net.zaminmc.torch.block;

/**
 * One biome registration row (§27.3's world-generation family — the
 * VIII14 slice-1 exposure): the reference 1.8.8's own registration
 * datums for one of the 61 census rows ({@code Biome.java}'s static
 * registration lines 70-234 + the static block's 21 mutations — 40 base
 * ids 0-39 + 21 mutated ids 129-167, the reference's own census),
 * reached through {@link net.zaminmc.torch.World#biomes()} (the whole
 * census) and {@link net.zaminmc.torch.World#biomeDataAt(BlockPosition)}
 * (the column's row — the same resolution {@code biomeAt} rides).
 *
 * <p>The engine behind it is the server's biome registration table,
 * every row carrying the reference's ctor-chain datums: the
 * temperature/downfall pair ({@code setTemperatureAndDownfall}, the
 * class default 0.5/0.5), the height pair ({@code setHeight}'s
 * base/variation — the standard mutation bumps the pair +0.1/+0.2; the
 * four custom shapes carry their own laws), the {@code setSnowy} flag,
 * and the rain READ law (the reference's {@code isRainy()} lines
 * 393-395: {@code !isCold() && rainy} — a snowy biome NEVER reports
 * rain: the frozen family's own field stays true but the read refuses,
 * and the {@code disableRain} family — the deserts, hell, the end, the
 * savanna pair, the mesa family — refuses everywhere; the cauldron's
 * rain-fill gate reads this form). The datum set is the §9 audit row's
 * own demand: the worldgen consults (the GenLayer layout, the spawn
 * tables, the depth-dependent surface builders, the per-row water fog
 * and surface wire ids) stay worldgen-side — §27.2's layer separation,
 * the DO-NOT-TOUCH lane's own reads.</p>
 *
 * <p><b>Threading (§27.6):</b> the registration table behind every row
 * is immutable (resolved once at engine class init, never mutated at
 * runtime), so the census and every row are freely consumable from any
 * thread — no owner context, no snapshot discipline; the read is
 * trivially thread-safe and the same rows answer every call.</p>
 *
 * <p>Instances are pure value rows (§27.5): no engine reference rides
 * the record, and the datums mirror the authoritative engine
 * registration exactly — never re-derived view-side ({@code rainy} is
 * the engine row's own read-law consult, not a re-computed
 * expression).</p>
 *
 * @param legacyId        the 1.8 wire id (the chunk packet's biome array
 *                        value; the mutated rows carry their own ids in
 *                        the 129-167 band)
 * @param name            the stable API name ({@code "plains"}, {@code
 *                        "ice_plains_spikes"}) — the exact string
 *                        {@link net.zaminmc.torch.World#biomeAt} answers
 *                        at the column
 * @param displayName     the reference's display name ({@code setName}'s
 *                        own string, byte-exact: "FrozenOcean" without
 *                        the space, "Extreme Hills+" with the plus,
 *                        "Sunflower Plains" not "Plains M") — the key
 *                        the all-biomes progress set carries
 * @param temperature     the registration temperature (the class default
 *                        0.5; the precipitation gate's band input)
 * @param downfall        the registration downfall (the class default
 *                        0.5 — the reference's {@code getDownfall})
 * @param baseHeight      the registration height pair's base ({@code
 *                        setHeight}'s first member)
 * @param heightVariation the registration height pair's variation (the
 *                        second member)
 * @param snowy           the {@code setSnowy} flag — the frozen family's
 *                        marker (the rain read law's own left operand,
 *                        the reference's {@code isSnowy}/{@code isCold})
 * @param rainy           the rain READ law ({@code !isCold() && rainy}):
 *                        whether the column's weather can rain at all —
 *                        false on every snowy row and every
 *                        {@code disableRain} row
 */
public record BiomeData(
        int legacyId,
        String name,
        String displayName,
        float temperature,
        float downfall,
        float baseHeight,
        float heightVariation,
        boolean snowy,
        boolean rainy) {
}
