package net.minecraft.client.render.model.block;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.Direction;
import org.lwjgl.util.vector.Vector3f;

public class ItemModelGenerator {
    public static final List<String> LAYERS = Lists.newArrayList("layer0", "layer1", "layer2", "layer3", "layer4");

    public BlockModel generate(TextureAtlas blockAtlas, BlockModel model) {
        Map<String, String> map = Maps.newHashMap();
        List<BlockElement> list = Lists.newArrayList();

        for (int i = 0; i < LAYERS.size(); i++) {
            String s = LAYERS.get(i);
            if (!model.hasTexture(s)) {
                break;
            }

            String s1 = model.getTexture(s);
            map.put(s, s1);
            TextureAtlasSprite textureatlassprite = blockAtlas.getSprite(new Identifier(s1).toString());
            list.addAll(this.processFrames(i, s, textureatlassprite));
        }

        if (list.isEmpty()) {
            return null;
        }

        map.put("particle", model.hasTexture("particle") ? model.getTexture("particle") : map.get("layer0"));
        return new BlockModel(list, map, false, false, model.getTransformations());
    }

    private List<BlockElement> processFrames(int layer, String key, TextureAtlasSprite blockSprite) {
        Map<Direction, BlockElementFace> map = Maps.newHashMap();
        map.put(Direction.SOUTH, new BlockElementFace(null, layer, key, new BlockElementTexture(new float[]{0.0F, 0.0F, 16.0F, 16.0F}, 0)));
        map.put(Direction.NORTH, new BlockElementFace(null, layer, key, new BlockElementTexture(new float[]{16.0F, 0.0F, 0.0F, 16.0F}, 0)));
        List<BlockElement> list = Lists.newArrayList();
        list.add(new BlockElement(new Vector3f(0.0F, 0.0F, 7.5F), new Vector3f(16.0F, 16.0F, 8.5F), map, null, true));
        list.addAll(this.addSideElements(blockSprite, key, layer));
        return list;
    }

    private List<BlockElement> addSideElements(TextureAtlasSprite blockSprite, String key, int layer) {
        float f = blockSprite.getWidth();
        float f1 = blockSprite.getHeight();
        List<BlockElement> list = Lists.newArrayList();

        for (ItemModelGenerator.Span itemmodelgenerator$span : this.getSpans(blockSprite)) {
            float f2 = 0.0F;
            float f3 = 0.0F;
            float f4 = 0.0F;
            float f5 = 0.0F;
            float f6 = 0.0F;
            float f7 = 0.0F;
            float f8 = 0.0F;
            float f9 = 0.0F;
            float f10 = 0.0F;
            float f11 = 0.0F;
            float f12 = itemmodelgenerator$span.getMin();
            float f13 = itemmodelgenerator$span.getMax();
            float f14 = itemmodelgenerator$span.getAnchor();
            ItemModelGenerator.Facing itemmodelgenerator$facing = itemmodelgenerator$span.getFacing();
            switch (itemmodelgenerator$facing) {
                case UP:
                    f6 = f12;
                    f2 = f12;
                    f4 = f7 = f13 + 1.0F;
                    f8 = f14;
                    f3 = f14;
                    f9 = f14;
                    f5 = f14;
                    f10 = 16.0F / f;
                    f11 = 16.0F / (f1 - 1.0F);
                    break;
                case DOWN:
                    f9 = f14;
                    f8 = f14;
                    f6 = f12;
                    f2 = f12;
                    f4 = f7 = f13 + 1.0F;
                    f3 = f14 + 1.0F;
                    f5 = f14 + 1.0F;
                    f10 = 16.0F / f;
                    f11 = 16.0F / (f1 - 1.0F);
                    break;
                case LEFT:
                    f6 = f14;
                    f2 = f14;
                    f7 = f14;
                    f4 = f14;
                    f9 = f12;
                    f3 = f12;
                    f5 = f8 = f13 + 1.0F;
                    f10 = 16.0F / (f - 1.0F);
                    f11 = 16.0F / f1;
                    break;
                case RIGHT:
                    f7 = f14;
                    f6 = f14;
                    f2 = f14 + 1.0F;
                    f4 = f14 + 1.0F;
                    f9 = f12;
                    f3 = f12;
                    f5 = f8 = f13 + 1.0F;
                    f10 = 16.0F / (f - 1.0F);
                    f11 = 16.0F / f1;
            }

            float f15 = 16.0F / f;
            float f16 = 16.0F / f1;
            f2 *= f15;
            f4 *= f15;
            f3 *= f16;
            f5 *= f16;
            f3 = 16.0F - f3;
            f5 = 16.0F - f5;
            f6 *= f10;
            f7 *= f10;
            f8 *= f11;
            f9 *= f11;
            Map<Direction, BlockElementFace> map = Maps.newHashMap();
            map.put(itemmodelgenerator$facing.asDirection(), new BlockElementFace(null, layer, key, new BlockElementTexture(new float[]{f6, f8, f7, f9}, 0)));
            switch (itemmodelgenerator$facing) {
                case UP:
                    list.add(new BlockElement(new Vector3f(f2, f3, 7.5F), new Vector3f(f4, f3, 8.5F), map, null, true));
                    break;
                case DOWN:
                    list.add(new BlockElement(new Vector3f(f2, f5, 7.5F), new Vector3f(f4, f5, 8.5F), map, null, true));
                    break;
                case LEFT:
                    list.add(new BlockElement(new Vector3f(f2, f3, 7.5F), new Vector3f(f2, f5, 8.5F), map, null, true));
                    break;
                case RIGHT:
                    list.add(new BlockElement(new Vector3f(f4, f3, 7.5F), new Vector3f(f4, f5, 8.5F), map, null, true));
            }
        }

        return list;
    }

    private List<ItemModelGenerator.Span> getSpans(TextureAtlasSprite blockSprite) {
        int i = blockSprite.getWidth();
        int j = blockSprite.getHeight();
        List<ItemModelGenerator.Span> list = Lists.newArrayList();

        for (int k = 0; k < blockSprite.getFrameCount(); k++) {
            int[] aint = blockSprite.getFrame(k)[0];

            for (int l = 0; l < j; l++) {
                for (int i1 = 0; i1 < i; i1++) {
                    boolean flag = !this.isTransparent(aint, i1, l, i, j);
                    this.checkTransition(ItemModelGenerator.Facing.UP, list, aint, i1, l, i, j, flag);
                    this.checkTransition(ItemModelGenerator.Facing.DOWN, list, aint, i1, l, i, j, flag);
                    this.checkTransition(ItemModelGenerator.Facing.LEFT, list, aint, i1, l, i, j, flag);
                    this.checkTransition(ItemModelGenerator.Facing.RIGHT, list, aint, i1, l, i, j, flag);
                }
            }
        }

        return list;
    }

    private void checkTransition(
        ItemModelGenerator.Facing facing, List<ItemModelGenerator.Span> spans, int[] frame, int x, int y, int width, int height, boolean expandWhenTransparent
    ) {
        boolean flag = this.isTransparent(frame, x + facing.getOffsetX(), y + facing.getOffsetY(), width, height) && expandWhenTransparent;
        if (flag) {
            this.expandSpan(spans, facing, x, y);
        }
    }

    private void expandSpan(List<ItemModelGenerator.Span> spans, ItemModelGenerator.Facing facing, int x, int y) {
        ItemModelGenerator.Span itemmodelgenerator$span = null;

        for (ItemModelGenerator.Span itemmodelgenerator$span1 : spans) {
            if (itemmodelgenerator$span1.getFacing() == facing) {
                int i = facing.isVertical() ? y : x;
                if (itemmodelgenerator$span1.getAnchor() == i) {
                    itemmodelgenerator$span = itemmodelgenerator$span1;
                    break;
                }
            }
        }

        int j = facing.isVertical() ? y : x;
        int k = facing.isVertical() ? x : y;
        if (itemmodelgenerator$span == null) {
            spans.add(new ItemModelGenerator.Span(facing, k, j));
        } else {
            itemmodelgenerator$span.expand(k);
        }
    }

    private boolean isTransparent(int[] frame, int x, int y, int width, int height) {
        return x < 0 || y < 0 || x >= width || y >= height || (frame[y * width + x] >> 24 & 0xFF) == 0;
    }

    enum Facing {
        UP(Direction.UP, 0, -1),
        DOWN(Direction.DOWN, 0, 1),
        LEFT(Direction.EAST, -1, 0),
        RIGHT(Direction.WEST, 1, 0);

        private final Direction dir;
        private final int offsetX;
        private final int offsetY;

        Facing(Direction dir, int offsetX, int offsetY) {
            this.dir = dir;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public Direction asDirection() {
            return this.dir;
        }

        public int getOffsetX() {
            return this.offsetX;
        }

        public int getOffsetY() {
            return this.offsetY;
        }

        private boolean isVertical() {
            return this == DOWN || this == UP;
        }
    }

    static class Span {
        private final ItemModelGenerator.Facing facing;
        private int min;
        private int max;
        private final int anchor;

        public Span(ItemModelGenerator.Facing facing, int value, int anchor) {
            this.facing = facing;
            this.min = value;
            this.max = value;
            this.anchor = anchor;
        }

        public void expand(int amount) {
            if (amount < this.min) {
                this.min = amount;
            } else if (amount > this.max) {
                this.max = amount;
            }
        }

        public ItemModelGenerator.Facing getFacing() {
            return this.facing;
        }

        public int getMin() {
            return this.min;
        }

        public int getMax() {
            return this.max;
        }

        public int getAnchor() {
            return this.anchor;
        }
    }
}
