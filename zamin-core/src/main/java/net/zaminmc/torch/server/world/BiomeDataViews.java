package net.zaminmc.torch.server.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The public biome-registration view (§27.3's family — the VIII14
 * slice-1 exposure): the engine's {@link Biome} census dressed as the
 * api's {@code net.zaminmc.torch.block.BiomeData} rows, reached through
 * {@code EngineWorld.biomes()}/{@code EngineWorld.biomeDataAt}. The
 * registration table is immutable (enum state, resolved once at class
 * init, never mutated at runtime), so the view builds ONCE and answers
 * the same rows forever — trivially thread-safe (§27.6: any thread, no
 * owner context, no snapshot copying, the villages()/VillageViews
 * snapshot discipline not needed here because there is nothing to copy
 * against).
 *
 * <p>Every datum is the engine row's OWN consult — never re-derived
 * view-side: {@code rainy} is {@link Biome#rainy()}'s read law (the
 * reference's {@code isRainy()} lines 393-395), the name is the
 * engine's own stable-name {@code toString} surface ({@code biomeAt}'s
 * own answer form). The worldgen consults (the GenLayer layout, the
 * spawn tables, the surface builders, the water fog and surface wire
 * ids) stay worldgen-side — §27.2's layer separation, the VIII14
 * audit's own boundary; this view carries the §9 audit row's demanded
 * datums alone (the display name, the temperature/downfall pair, the
 * height pair, the snowy flag, the rain read law) plus the two keys
 * that make them addressable ({@code legacyId}, the stable name).</p>
 */
public final class BiomeDataViews {

    /** The whole census as api rows, id order (the reference's own table). */
    private static final List<net.zaminmc.torch.block.BiomeData> ALL;
    /** The api row per engine row (immutable, built once at class init). */
    private static final Map<Biome, net.zaminmc.torch.block.BiomeData> BY_ROW;

    static {
        Biome[] rows = Biome.all();
        List<net.zaminmc.torch.block.BiomeData> all = new ArrayList<>(rows.length);
        Map<Biome, net.zaminmc.torch.block.BiomeData> byRow = new EnumMap<>(Biome.class);
        for (Biome row : rows) {
            net.zaminmc.torch.block.BiomeData view =
                    new net.zaminmc.torch.block.BiomeData(
                            row.legacyId(),
                            row.toString(), // the engine's own stable-name surface
                            row.displayName(),
                            row.temperature(),
                            row.downfall(),
                            row.baseHeight(),
                            row.heightVariation(),
                            row.snowy(),
                            row.rainy());
            all.add(view);
            byRow.put(row, view);
        }
        ALL = List.copyOf(all);
        BY_ROW = Collections.unmodifiableMap(byRow);
    }

    private BiomeDataViews() {
    }

    /**
     * @return the whole registration census as api rows (61, id order —
     *         the immutable list, the same instance every call).
     */
    public static List<net.zaminmc.torch.block.BiomeData> all() {
        return ALL;
    }

    /**
     * @param row the engine row (never null — {@code
     *            EngineWorld.biomeValueAt} answers the plains default,
     *            never a gap)
     * @return the row's api view (the SAME instance every call — the
     *         table is immutable)
     */
    public static net.zaminmc.torch.block.BiomeData of(Biome row) {
        return BY_ROW.get(Objects.requireNonNull(row, "row"));
    }
}
