package net.minecraft.block.entity;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;

public class BannerBlockEntity extends BlockEntity {
    private int baseColor;
    private NbtList patternsNbt;
    private boolean hasPatternData;
    private List<BannerBlockEntity.Pattern> patterns;
    private List<DyeColor> colors;
    private String texture;

    public void set(ItemStack item) {
        this.patternsNbt = null;
        if (item.hasNbt() && item.getNbt().contains("BlockEntityTag", 10)) {
            NbtCompound nbtcompound = item.getNbt().getCompound("BlockEntityTag");
            if (nbtcompound.contains("Patterns")) {
                this.patternsNbt = (NbtList)nbtcompound.getList("Patterns", 10).copy();
            }

            if (nbtcompound.contains("Base", 99)) {
                this.baseColor = nbtcompound.getInt("Base");
            } else {
                this.baseColor = item.getMetadata() & 15;
            }
        } else {
            this.baseColor = item.getMetadata() & 15;
        }

        this.patterns = null;
        this.colors = null;
        this.texture = "";
        this.hasPatternData = true;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        writeNbt(nbt, this.baseColor, this.patternsNbt);
    }

    public static void writeNbt(NbtCompound nbt, int baseColor, NbtList patternsNbt) {
        nbt.putInt("Base", baseColor);
        if (patternsNbt != null) {
            nbt.put("Patterns", patternsNbt);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.baseColor = nbt.getInt("Base");
        this.patternsNbt = nbt.getList("Patterns", 10);
        this.patterns = null;
        this.colors = null;
        this.texture = null;
        this.hasPatternData = true;
    }

    @Override
    public Packet createUpdatePacket() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound);
        return new BlockEntityUpdateS2CPacket(this.pos, 6, nbtcompound);
    }

    public int getBase() {
        return this.baseColor;
    }

    public static int getBaseColor(ItemStack item) {
        NbtCompound nbtcompound = item.getNbt("BlockEntityTag", false);
        return nbtcompound != null && nbtcompound.contains("Base") ? nbtcompound.getInt("Base") : item.getMetadata();
    }

    public static int getPatternCount(ItemStack item) {
        NbtCompound nbtcompound = item.getNbt("BlockEntityTag", false);
        return nbtcompound != null && nbtcompound.contains("Patterns") ? nbtcompound.getList("Patterns", 10).size() : 0;
    }

    public List<BannerBlockEntity.Pattern> getPatterns() {
        this.setPatterns();
        return this.patterns;
    }

    public NbtList getPatternsNbt() {
        return this.patternsNbt;
    }

    public List<DyeColor> getColors() {
        this.setPatterns();
        return this.colors;
    }

    public String getTexture() {
        this.setPatterns();
        return this.texture;
    }

    private void setPatterns() {
        if (this.patterns == null || this.colors == null || this.texture == null) {
            if (!this.hasPatternData) {
                this.texture = "";
            } else {
                this.patterns = Lists.newArrayList();
                this.colors = Lists.newArrayList();
                this.patterns.add(BannerBlockEntity.Pattern.BASE);
                this.colors.add(DyeColor.byMetadata(this.baseColor));
                this.texture = "b" + this.baseColor;
                if (this.patternsNbt != null) {
                    for (int i = 0; i < this.patternsNbt.size(); i++) {
                        NbtCompound nbtcompound = this.patternsNbt.getCompound(i);
                        BannerBlockEntity.Pattern bannerblockentity$pattern = BannerBlockEntity.Pattern.byHash(nbtcompound.getString("Pattern"));
                        if (bannerblockentity$pattern != null) {
                            this.patterns.add(bannerblockentity$pattern);
                            int j = nbtcompound.getInt("Color");
                            this.colors.add(DyeColor.byMetadata(j));
                            this.texture = this.texture + bannerblockentity$pattern.getHash() + j;
                        }
                    }
                }
            }
        }
    }

    public static void removeLastPattern(ItemStack item) {
        NbtCompound nbtcompound = item.getNbt("BlockEntityTag", false);
        if (nbtcompound != null && nbtcompound.contains("Patterns", 9)) {
            NbtList nbtlist = nbtcompound.getList("Patterns", 10);
            if (nbtlist.size() > 0) {
                nbtlist.removeElement(nbtlist.size() - 1);
                if (nbtlist.isEmpty()) {
                    item.getNbt().remove("BlockEntityTag");
                    if (item.getNbt().isEmpty()) {
                        item.setNbt(null);
                    }
                }
            }
        }
    }

    public enum Pattern {
        BASE("base", "b"),
        SQUARE_BOTTOM_LEFT("square_bottom_left", "bl", "   ", "   ", "#  "),
        SQUARE_BOTTOM_RIGHT("square_bottom_right", "br", "   ", "   ", "  #"),
        SQUARE_TOP_LEFT("square_top_left", "tl", "#  ", "   ", "   "),
        SQUARE_TOP_RIGHT("square_top_right", "tr", "  #", "   ", "   "),
        STRIPE_BOTTOM("stripe_bottom", "bs", "   ", "   ", "###"),
        STRIPE_TOP("stripe_top", "ts", "###", "   ", "   "),
        STRIPE_LEFT("stripe_left", "ls", "#  ", "#  ", "#  "),
        STRIPE_RIGHT("stripe_right", "rs", "  #", "  #", "  #"),
        STRIPE_CENTER("stripe_center", "cs", " # ", " # ", " # "),
        STRIPE_MIDDLE("stripe_middle", "ms", "   ", "###", "   "),
        STRIPE_DOWNRIGHT("stripe_downright", "drs", "#  ", " # ", "  #"),
        STRIPE_DOWNLEFT("stripe_downleft", "dls", "  #", " # ", "#  "),
        STRIPE_SMALL("small_stripes", "ss", "# #", "# #", "   "),
        CROSS("cross", "cr", "# #", " # ", "# #"),
        STRAIGHT_CROSS("straight_cross", "sc", " # ", "###", " # "),
        TRIANGLE_BOTTOM("triangle_bottom", "bt", "   ", " # ", "# #"),
        TRIANGLE_TOP("triangle_top", "tt", "# #", " # ", "   "),
        TRIANGLES_BOTTOM("triangles_bottom", "bts", "   ", "# #", " # "),
        TRIANGLES_TOP("triangles_top", "tts", " # ", "# #", "   "),
        DIAGONAL_LEFT("diagonal_left", "ld", "## ", "#  ", "   "),
        DIAGONAL_RIGHT("diagonal_up_right", "rd", "   ", "  #", " ##"),
        DIAGONAL_LEFT_MIRROR("diagonal_up_left", "lud", "   ", "#  ", "## "),
        DIAGONAL_RIGHT_MIRROR("diagonal_right", "rud", " ##", "  #", "   "),
        CIRCLE_MIDDLE("circle", "mc", "   ", " # ", "   "),
        RHOMBUS_MIDDLE("rhombus", "mr", " # ", "# #", " # "),
        HALF_VERTICAL("half_vertical", "vh", "## ", "## ", "## "),
        HALF_HORIZONTAL("half_horizontal", "hh", "###", "###", "   "),
        HALF_VERTICAL_MIRROR("half_vertical_right", "vhr", " ##", " ##", " ##"),
        HALF_HORIZONTAL_MIRROR("half_horizontal_bottom", "hhb", "   ", "###", "###"),
        BORDER("border", "bo", "###", "# #", "###"),
        CURLY_BORDER("curly_border", "cbo", new ItemStack(Blocks.VINE)),
        CREEPER("creeper", "cre", new ItemStack(Items.SKULL, 1, 4)),
        GRADIENT("gradient", "gra", "# #", " # ", " # "),
        GRADIENT_UP("gradient_up", "gru", " # ", " # ", "# #"),
        BRICKS("bricks", "bri", new ItemStack(Blocks.BRICKS)),
        SKULL("skull", "sku", new ItemStack(Items.SKULL, 1, 1)),
        FLOWER("flower", "flo", new ItemStack(Blocks.RED_FLOWER, 1, FlowerBlock.Type.OXEY_DAISY.getId())),
        MOJANG("mojang", "moj", new ItemStack(Items.GOLDEN_APPLE, 1, 1));

        private String key;
        private String hash;
        private String[] patterns = new String[3];
        private ItemStack item;

        Pattern(String key, String hash) {
            this.key = key;
            this.hash = hash;
        }

        Pattern(String key, String hash, ItemStack item) {
            this(key, hash);
            this.item = item;
        }

        Pattern(String key, String hash, String topPattern, String middlePattern, String bottomPattern) {
            this(key, hash);
            this.patterns[0] = topPattern;
            this.patterns[1] = middlePattern;
            this.patterns[2] = bottomPattern;
        }

        public String getKey() {
            return this.key;
        }

        public String getHash() {
            return this.hash;
        }

        public String[] getPatterns() {
            return this.patterns;
        }

        public boolean hasPattern() {
            return this.item != null || this.patterns[0] != null;
        }

        public boolean hasItem() {
            return this.item != null;
        }

        public ItemStack getItem() {
            return this.item;
        }

        public static BannerBlockEntity.Pattern byHash(String hash) {
            for (BannerBlockEntity.Pattern bannerblockentity$pattern : values()) {
                if (bannerblockentity$pattern.hash.equals(hash)) {
                    return bannerblockentity$pattern;
                }
            }

            return null;
        }
    }
}
