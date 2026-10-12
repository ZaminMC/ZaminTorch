package net.minecraft.client.render.texture;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.MathHelper;

public class Stitcher {
    private final int mipLevel;
    private final Set<Stitcher.Holder> sprites = Sets.newHashSetWithExpectedSize(256);
    private final List<Stitcher.Region> regions = Lists.newArrayListWithCapacity(256);
    private int width;
    private int height;
    private final int maxWidth;
    private final int maxHeight;
    private final boolean updateSize;
    private final int holderSize;

    public Stitcher(int maxWidth, int maxHeight, boolean updateSize, int holderSize, int mipLevel) {
        this.mipLevel = mipLevel;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.updateSize = updateSize;
        this.holderSize = holderSize;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void registerSprite(TextureAtlasSprite sprite) {
        Stitcher.Holder stitcher$holder = new Stitcher.Holder(sprite, this.mipLevel);
        if (this.holderSize > 0) {
            stitcher$holder.setSize(this.holderSize);
        }

        this.sprites.add(stitcher$holder);
    }

    public void stitch() {
        Stitcher.Holder[] astitcher$holder = this.sprites.toArray(new Stitcher.Holder[this.sprites.size()]);
        Arrays.sort(astitcher$holder);

        for (Stitcher.Holder stitcher$holder : astitcher$holder) {
            if (!this.addSprite(stitcher$holder)) {
                String s = String.format(
                    "Unable to fit: %s - size: %dx%d - Maybe try a lowerresolution resourcepack?",
                    stitcher$holder.getSprite().getName(),
                    stitcher$holder.getSprite().getWidth(),
                    stitcher$holder.getSprite().getHeight()
                );
                throw new StitcherException(stitcher$holder, s);
            }
        }

        if (this.updateSize) {
            this.width = MathHelper.smallestEncompassingPowerOfTwo(this.width);
            this.height = MathHelper.smallestEncompassingPowerOfTwo(this.height);
        }
    }

    public List<TextureAtlasSprite> collectSprites() {
        List<Stitcher.Region> list = Lists.newArrayList();

        for (Stitcher.Region stitcher$region : this.regions) {
            stitcher$region.collectRegions(list);
        }

        List<TextureAtlasSprite> list1 = Lists.newArrayList();

        for (Stitcher.Region stitcher$region1 : list) {
            Stitcher.Holder stitcher$holder = stitcher$region1.getSprite();
            TextureAtlasSprite textureatlassprite = stitcher$holder.getSprite();
            textureatlassprite.init(this.width, this.height, stitcher$region1.getX(), stitcher$region1.getY(), stitcher$holder.isRotated());
            list1.add(textureatlassprite);
        }

        return list1;
    }

    private static int smallestFittingMinTexel(int size, int mipLevel) {
        return (size >> mipLevel) + ((size & (1 << mipLevel) - 1) == 0 ? 0 : 1) << mipLevel;
    }

    private boolean addSprite(Stitcher.Holder sprite) {
        for (int i = 0; i < this.regions.size(); i++) {
            if (this.regions.get(i).add(sprite)) {
                return true;
            }

            sprite.toggleRotated();
            if (this.regions.get(i).add(sprite)) {
                return true;
            }

            sprite.toggleRotated();
        }

        return this.expand(sprite);
    }

    private boolean expand(Stitcher.Holder sprite) {
        int i = Math.min(sprite.getWidth(), sprite.getHeight());
        boolean flag = this.width == 0 && this.height == 0;
        boolean flag1;
        if (this.updateSize) {
            int j = MathHelper.smallestEncompassingPowerOfTwo(this.width);
            int k = MathHelper.smallestEncompassingPowerOfTwo(this.height);
            int l = MathHelper.smallestEncompassingPowerOfTwo(this.width + i);
            int i1 = MathHelper.smallestEncompassingPowerOfTwo(this.height + i);
            boolean flag2 = l <= this.maxWidth;
            boolean flag3 = i1 <= this.maxHeight;
            if (!flag2 && !flag3) {
                return false;
            }

            boolean flag4 = j != l;
            boolean flag5 = k != i1;
            if (flag4 ^ flag5) {
                flag1 = !flag4;
            } else {
                flag1 = flag2 && j <= k;
            }
        } else {
            boolean flag6 = this.width + i <= this.maxWidth;
            boolean flag7 = this.height + i <= this.maxHeight;
            if (!flag6 && !flag7) {
                return false;
            }

            flag1 = flag6 && (flag || this.width <= this.height);
        }

        int j1 = Math.max(sprite.getWidth(), sprite.getHeight());
        if (MathHelper.smallestEncompassingPowerOfTwo((flag1 ? this.height : this.width) + j1) > (flag1 ? this.maxHeight : this.maxWidth)) {
            return false;
        }

        Stitcher.Region stitcher$region;
        if (flag1) {
            if (sprite.getWidth() > sprite.getHeight()) {
                sprite.toggleRotated();
            }

            if (this.height == 0) {
                this.height = sprite.getHeight();
            }

            stitcher$region = new Stitcher.Region(this.width, 0, sprite.getWidth(), this.height);
            this.width = this.width + sprite.getWidth();
        } else {
            stitcher$region = new Stitcher.Region(0, this.height, this.width, sprite.getHeight());
            this.height = this.height + sprite.getHeight();
        }

        stitcher$region.add(sprite);
        this.regions.add(stitcher$region);
        return true;
    }

    public static class Holder implements Comparable<Stitcher.Holder> {
        private final TextureAtlasSprite sprite;
        private final int width;
        private final int height;
        private final int mipLevel;
        private boolean rotated;
        private float size = 1.0F;

        public Holder(TextureAtlasSprite sprite, int mipLevel) {
            this.sprite = sprite;
            this.width = sprite.getWidth();
            this.height = sprite.getHeight();
            this.mipLevel = mipLevel;
            this.rotated = Stitcher.smallestFittingMinTexel(this.height, mipLevel) > Stitcher.smallestFittingMinTexel(this.width, mipLevel);
        }

        public TextureAtlasSprite getSprite() {
            return this.sprite;
        }

        public int getWidth() {
            return this.rotated
                ? Stitcher.smallestFittingMinTexel((int)(this.height * this.size), this.mipLevel)
                : Stitcher.smallestFittingMinTexel((int)(this.width * this.size), this.mipLevel);
        }

        public int getHeight() {
            return this.rotated
                ? Stitcher.smallestFittingMinTexel((int)(this.width * this.size), this.mipLevel)
                : Stitcher.smallestFittingMinTexel((int)(this.height * this.size), this.mipLevel);
        }

        public void toggleRotated() {
            this.rotated = !this.rotated;
        }

        public boolean isRotated() {
            return this.rotated;
        }

        public void setSize(int size) {
            if (this.width > size && this.height > size) {
                this.size = (float)size / Math.min(this.width, this.height);
            }
        }

        @Override
        public String toString() {
            return "Holder{width=" + this.width + ", height=" + this.height + '}';
        }

        public int compareTo(Stitcher.Holder holder) {
            int i;
            if (this.getHeight() == holder.getHeight()) {
                if (this.getWidth() == holder.getWidth()) {
                    if (this.sprite.getName() == null) {
                        return holder.sprite.getName() == null ? 0 : -1;
                    }

                    return this.sprite.getName().compareTo(holder.sprite.getName());
                }

                i = this.getWidth() < holder.getWidth() ? 1 : -1;
            } else {
                i = this.getHeight() < holder.getHeight() ? 1 : -1;
            }

            return i;
        }
    }

    public static class Region {
        private final int originX;
        private final int originY;
        private final int width;
        private final int height;
        private List<Stitcher.Region> subSlots;
        private Stitcher.Holder sprite;

        public Region(int originX, int originY, int width, int height) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }

        public Stitcher.Holder getSprite() {
            return this.sprite;
        }

        public int getX() {
            return this.originX;
        }

        public int getY() {
            return this.originY;
        }

        public boolean add(Stitcher.Holder sprite) {
            if (this.sprite != null) {
                return false;
            }

            int i = sprite.getWidth();
            int j = sprite.getHeight();
            if (i <= this.width && j <= this.height) {
                if (i == this.width && j == this.height) {
                    this.sprite = sprite;
                    return true;
                }

                if (this.subSlots == null) {
                    this.subSlots = Lists.newArrayListWithCapacity(1);
                    this.subSlots.add(new Stitcher.Region(this.originX, this.originY, i, j));
                    int k = this.width - i;
                    int l = this.height - j;
                    if (l > 0 && k > 0) {
                        int i1 = Math.max(this.height, k);
                        int j1 = Math.max(this.width, l);
                        if (i1 >= j1) {
                            this.subSlots.add(new Stitcher.Region(this.originX, this.originY + j, i, l));
                            this.subSlots.add(new Stitcher.Region(this.originX + i, this.originY, k, this.height));
                        } else {
                            this.subSlots.add(new Stitcher.Region(this.originX + i, this.originY, k, j));
                            this.subSlots.add(new Stitcher.Region(this.originX, this.originY + j, this.width, l));
                        }
                    } else if (k == 0) {
                        this.subSlots.add(new Stitcher.Region(this.originX, this.originY + j, i, l));
                    } else if (l == 0) {
                        this.subSlots.add(new Stitcher.Region(this.originX + i, this.originY, k, j));
                    }
                }

                for (Stitcher.Region stitcher$region : this.subSlots) {
                    if (stitcher$region.add(sprite)) {
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        }

        public void collectRegions(List<Stitcher.Region> regions) {
            if (this.sprite != null) {
                regions.add(this);
            } else if (this.subSlots != null) {
                for (Stitcher.Region stitcher$region : this.subSlots) {
                    stitcher$region.collectRegions(regions);
                }
            }
        }

        @Override
        public String toString() {
            return "Slot{originX="
                + this.originX
                + ", originY="
                + this.originY
                + ", width="
                + this.width
                + ", height="
                + this.height
                + ", texture="
                + this.sprite
                + ", subSlots="
                + this.subSlots
                + '}';
        }
    }
}
