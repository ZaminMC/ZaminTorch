package net.minecraft.world.gen.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

public class FlatWorldGeneratorSettings {
    private final List<FlatWorldLayer> layers = Lists.newArrayList();
    private final Map<String, Map<String, String>> features = Maps.newHashMap();
    private int biome;

    public int getBiome() {
        return this.biome;
    }

    public void setBiome(int biome) {
        this.biome = biome;
    }

    public Map<String, Map<String, String>> getFeatures() {
        return this.features;
    }

    public List<FlatWorldLayer> getLayers() {
        return this.layers;
    }

    public void processLayers() {
        int i = 0;

        for (FlatWorldLayer flatworldlayer : this.layers) {
            flatworldlayer.setY(i);
            i += flatworldlayer.getSize();
        }
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append(3);
        stringbuilder.append(";");

        for (int i = 0; i < this.layers.size(); i++) {
            if (i > 0) {
                stringbuilder.append(",");
            }

            stringbuilder.append(this.layers.get(i).toString());
        }

        stringbuilder.append(";");
        stringbuilder.append(this.biome);
        if (!this.features.isEmpty()) {
            stringbuilder.append(";");
            int k = 0;

            for (Entry<String, Map<String, String>> entry : this.features.entrySet()) {
                if (k++ > 0) {
                    stringbuilder.append(",");
                }

                stringbuilder.append(entry.getKey().toLowerCase());
                Map<String, String> map = entry.getValue();
                if (!map.isEmpty()) {
                    stringbuilder.append("(");
                    int j = 0;

                    for (Entry<String, String> entry1 : map.entrySet()) {
                        if (j++ > 0) {
                            stringbuilder.append(" ");
                        }

                        stringbuilder.append(entry1.getKey());
                        stringbuilder.append("=");
                        stringbuilder.append(entry1.getValue());
                    }

                    stringbuilder.append(")");
                }
            }
        } else {
            stringbuilder.append(";");
        }

        return stringbuilder.toString();
    }

    private static FlatWorldLayer parseLayer(int biomeId, String preset, int y) {
        String[] astring = biomeId >= 3 ? preset.split("\\*", 2) : preset.split("x", 2);
        int i = 1;
        int j = 0;
        if (astring.length == 2) {
            try {
                i = Integer.parseInt(astring[0]);
                if (y + i >= 256) {
                    i = 256 - y;
                }

                if (i < 0) {
                    i = 0;
                }
            } catch (Throwable throwable) {
                return null;
            }
        }

        Block block = null;

        try {
            String s = astring[astring.length - 1];
            if (biomeId < 3) {
                astring = s.split(":", 2);
                if (astring.length > 1) {
                    j = Integer.parseInt(astring[1]);
                }

                block = Block.byId(Integer.parseInt(astring[0]));
            } else {
                astring = s.split(":", 3);
                block = astring.length > 1 ? Block.byKey(astring[0] + ":" + astring[1]) : null;
                if (block != null) {
                    j = astring.length > 2 ? Integer.parseInt(astring[2]) : 0;
                } else {
                    block = Block.byKey(astring[0]);
                    if (block != null) {
                        j = astring.length > 1 ? Integer.parseInt(astring[1]) : 0;
                    }
                }

                if (block == null) {
                    return null;
                }
            }

            if (block == Blocks.AIR) {
                j = 0;
            }

            if (j < 0 || j > 15) {
                j = 0;
            }
        } catch (Throwable throwable1) {
            return null;
        }

        FlatWorldLayer flatworldlayer = new FlatWorldLayer(biomeId, i, block, j);
        flatworldlayer.setY(y);
        return flatworldlayer;
    }

    private static List<FlatWorldLayer> getLayers(int biomeId, String preset) {
        if (preset != null && preset.length() >= 1) {
            List<FlatWorldLayer> list = Lists.newArrayList();
            String[] astring = preset.split(",");
            int i = 0;

            for (String s : astring) {
                FlatWorldLayer flatworldlayer = parseLayer(biomeId, s, i);
                if (flatworldlayer == null) {
                    return null;
                }

                list.add(flatworldlayer);
                i += flatworldlayer.getSize();
            }

            return list;
        } else {
            return null;
        }
    }

    public static FlatWorldGeneratorSettings of(String s) {
        if (s == null) {
            return ofDefault();
        }

        String[] astring = s.split(";", -1);
        int i = astring.length == 1 ? 0 : MathHelper.parseInt(astring[0], 0);
        if (i >= 0 && i <= 3) {
            FlatWorldGeneratorSettings flatworldgeneratorsettings = new FlatWorldGeneratorSettings();
            int j = astring.length == 1 ? 0 : 1;
            List<FlatWorldLayer> list = getLayers(i, astring[j++]);
            if (list != null && !list.isEmpty()) {
                flatworldgeneratorsettings.getLayers().addAll(list);
                flatworldgeneratorsettings.processLayers();
                int k = Biome.PLAINS.id;
                if (i > 0 && astring.length > j) {
                    k = MathHelper.parseInt(astring[j++], k);
                }

                flatworldgeneratorsettings.setBiome(k);
                if (i > 0 && astring.length > j) {
                    String[] astring1 = astring[j++].toLowerCase().split(",");

                    for (String sx : astring1) {
                        String[] astring2 = sx.split("\\(", 2);
                        Map<String, String> map = Maps.newHashMap();
                        if (astring2[0].length() > 0) {
                            flatworldgeneratorsettings.getFeatures().put(astring2[0], map);
                            if (astring2.length > 1 && astring2[1].endsWith(")") && astring2[1].length() > 1) {
                                String[] astring3 = astring2[1].substring(0, astring2[1].length() - 1).split(" ");

                                for (int l = 0; l < astring3.length; l++) {
                                    String[] astring4 = astring3[l].split("=", 2);
                                    if (astring4.length == 2) {
                                        map.put(astring4[0], astring4[1]);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    flatworldgeneratorsettings.getFeatures().put("village", Maps.newHashMap());
                }

                return flatworldgeneratorsettings;
            } else {
                return ofDefault();
            }
        } else {
            return ofDefault();
        }
    }

    public static FlatWorldGeneratorSettings ofDefault() {
        FlatWorldGeneratorSettings flatworldgeneratorsettings = new FlatWorldGeneratorSettings();
        flatworldgeneratorsettings.setBiome(Biome.PLAINS.id);
        flatworldgeneratorsettings.getLayers().add(new FlatWorldLayer(1, Blocks.BEDROCK));
        flatworldgeneratorsettings.getLayers().add(new FlatWorldLayer(2, Blocks.DIRT));
        flatworldgeneratorsettings.getLayers().add(new FlatWorldLayer(1, Blocks.GRASS));
        flatworldgeneratorsettings.processLayers();
        flatworldgeneratorsettings.getFeatures().put("village", Maps.newHashMap());
        return flatworldgeneratorsettings;
    }
}
