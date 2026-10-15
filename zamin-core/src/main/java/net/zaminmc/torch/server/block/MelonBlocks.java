package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The melon family (Slice 41 — MelonBlock/StemBlock 1:1 over the
 * reference's registrations): the melon block 103 (Material.PUMPKIN, the
 * 3-7 slice drop walk), the pumpkin stem 104 and the melon stem 105 (both
 * {@code setStrength(0.0F)} WOOD_SOUNDS over farmland — the AGE band
 * 0-7 with the fruit arm at 7).
 *
 * <p>The stem states follow the engine's crop naming convention
 * ({@code <family>_stage0..7}, the wheat/carrots/potatoes precedent); the
 * stems have NO items (CreativeModeTab null, the pick yields the seeds —
 * the planting rides the seeds' own use arms).</p>
 */
public final class MelonBlocks {

    private MelonBlocks() {
    }

    /** The stem families: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> STEMS = Map.of(
            "pumpkin_stem", 104,
            "melon_stem", 105);

    /** The fruit each stem spawns at AGE 7 (StemBlock.tick's plant field). */
    public static BlockType fruitOf(String stemFamily) {
        return stemFamily.equals("pumpkin_stem")
                ? BuiltinBlocks.PUMPKIN : BuiltinBlocks.MELON_BLOCK;
    }

    /** The seeds item each stem's drop walk yields (getSeedsItem). */
    public static Identifier seedsOf(String stemFamily) {
        return Identifier.parse(stemFamily.equals("pumpkin_stem")
                ? "minecraft:pumpkin_seeds" : "minecraft:melon_seeds");
    }

    /** The stem's stage block (the AGE band 0..7). */
    public static EngineBlockType stemStage(String family, int stage) {
        String display = (family.equals("pumpkin_stem") ? "Pumpkin" : "Melon") + " Stem";
        return new EngineBlockType(
                Identifier.parse("minecraft:" + family + "_stage" + stage), display, stage);
    }

    /** Every registration this family owns (the 16 stem states — the melon
     *  block's own constant rides the plain registration rows). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (String family : STEMS.keySet()) {
            for (int stage = 0; stage <= 7; stage++) {
                blocks.add(stemStage(family, stage));
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return the stem family of an identifier, or null. */
    public static String stemFamilyOf(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return null;
        }
        String value = id.value();
        if (value.startsWith("pumpkin_stem_stage")) {
            return "pumpkin_stem";
        }
        if (value.startsWith("melon_stem_stage")) {
            return "melon_stem";
        }
        return null;
    }

    /** The fruit spawn walk (StemBlock.tick lines 74-84, 1:1): at AGE 7 a
     *  random horizontal neighbor whose floor is farmland, dirt, or grass
     *  takes the fruit — unless a fruit already sits beside the stem. */
    public static boolean trySpawnFruit(net.zaminmc.torch.server.world.EngineWorld world,
            BlockPosition stem, String family, java.util.Random random) {
        int[][] steps = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        // The already-fruiting check (lines 70-73): any horizontal fruit
        // beside the stem ends the walk.
        BlockType fruit = fruitOf(family);
        for (int[] step : steps) {
            if (world.getBlock(stem.offset(step[0], 0, step[1])).equals(fruit)) {
                return false;
            }
        }
        int[] step = steps[random.nextInt(steps.length)];
        BlockPosition target = stem.offset(step[0], 0, step[1]);
        BlockType at = world.getBlock(target);
        if (at.equals(world.airType())) {
            BlockType floor = world.getBlock(target.offset(0, -1, 0));
            String floorId = floor.identifier().value();
            if (floorId.equals("farmland") || floorId.equals("farmland_wet")
                    || floorId.equals("dirt") || floorId.equals("grass_block")) {
                world.setBlock(target, fruit);
                return true;
            }
        }
        return false;
    }
}
