package net.minecraft.item;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentCategory;
import net.minecraft.enchantment.EnchantmentEntry;

public abstract class CreativeModeTab {
    public static final CreativeModeTab[] ALL = new CreativeModeTab[12];
    public static final CreativeModeTab BUILDING_BLOCKS = new CreativeModeTab(0, "buildingBlocks") {
        @Override
        public Item getIconItem() {
            return Item.byBlock(Blocks.BRICKS);
        }
    };
    public static final CreativeModeTab DECORATIONS = new CreativeModeTab(1, "decorations") {
        @Override
        public Item getIconItem() {
            return Item.byBlock(Blocks.DOUBLE_PLANT);
        }

        @Override
        public int getIconMetadata() {
            return DoublePlantBlock.Variant.PAEONIA.getId();
        }
    };
    public static final CreativeModeTab REDSTONE = new CreativeModeTab(2, "redstone") {
        @Override
        public Item getIconItem() {
            return Items.REDSTONE;
        }
    };
    public static final CreativeModeTab TRANSPORTATION = new CreativeModeTab(3, "transportation") {
        @Override
        public Item getIconItem() {
            return Item.byBlock(Blocks.POWERED_RAIL);
        }
    };
    public static final CreativeModeTab MISC = (new CreativeModeTab(4, "misc") {
        @Override
        public Item getIconItem() {
            return Items.LAVA_BUCKET;
        }
    }).setEnchantmentCategories(EnchantmentCategory.ALL);
    public static final CreativeModeTab SEARCH = (new CreativeModeTab(5, "search") {
        @Override
        public Item getIconItem() {
            return Items.COMPASS;
        }
    }).setTexture("item_search.png");
    public static final CreativeModeTab FOOD = new CreativeModeTab(6, "food") {
        @Override
        public Item getIconItem() {
            return Items.APPLE;
        }
    };
    public static final CreativeModeTab TOOLS = (new CreativeModeTab(7, "tools") {
        @Override
        public Item getIconItem() {
            return Items.IRON_AXE;
        }
    }).setEnchantmentCategories(EnchantmentCategory.DIGGER, EnchantmentCategory.FISHING_ROD, EnchantmentCategory.BREAKABLE);
    public static final CreativeModeTab COMBAT = (new CreativeModeTab(8, "combat") {
            @Override
            public Item getIconItem() {
                return Items.GOLDEN_SWORD;
            }
        })
        .setEnchantmentCategories(
            EnchantmentCategory.ARMOR,
            EnchantmentCategory.ARMOR_FEET,
            EnchantmentCategory.ARMOR_HEAD,
            EnchantmentCategory.ARMOR_LEGS,
            EnchantmentCategory.ARMOR_TORSO,
            EnchantmentCategory.BOW,
            EnchantmentCategory.WEAPON
        );
    public static final CreativeModeTab BREWING = new CreativeModeTab(9, "brewing") {
        @Override
        public Item getIconItem() {
            return Items.POTION;
        }
    };
    public static final CreativeModeTab MATERIALS = new CreativeModeTab(10, "materials") {
        @Override
        public Item getIconItem() {
            return Items.STICK;
        }
    };
    public static final CreativeModeTab INVENTORY = (new CreativeModeTab(11, "inventory") {
        @Override
        public Item getIconItem() {
            return Item.byBlock(Blocks.CHEST);
        }
    }).setTexture("inventory.png").hideScrollbar().hideTooltips();
    private final int id;
    private final String name;
    private String texture = "items.png";
    private boolean scrollbar = true;
    private boolean tooltips = true;
    private EnchantmentCategory[] categories;
    private ItemStack item;

    public CreativeModeTab(int id, String name) {
        this.id = id;
        this.name = name;
        ALL[id] = this;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return "itemGroup." + this.getName();
    }

    public ItemStack getIcon() {
        if (this.item == null) {
            this.item = new ItemStack(this.getIconItem(), 1, this.getIconMetadata());
        }

        return this.item;
    }

    public abstract Item getIconItem();

    public int getIconMetadata() {
        return 0;
    }

    public String getTexture() {
        return this.texture;
    }

    public CreativeModeTab setTexture(String texture) {
        this.texture = texture;
        return this;
    }

    public boolean hasTooltips() {
        return this.tooltips;
    }

    public CreativeModeTab hideTooltips() {
        this.tooltips = false;
        return this;
    }

    public boolean hasScrollbar() {
        return this.scrollbar;
    }

    public CreativeModeTab hideScrollbar() {
        this.scrollbar = false;
        return this;
    }

    public int getColumn() {
        return this.id % 6;
    }

    public boolean isTopRow() {
        return this.id < 6;
    }

    public EnchantmentCategory[] getEnchantmentCategories() {
        return this.categories;
    }

    public CreativeModeTab setEnchantmentCategories(EnchantmentCategory... categories) {
        this.categories = categories;
        return this;
    }

    public boolean hasEchantmentCategory(EnchantmentCategory category) {
        if (this.categories == null) {
            return false;
        }

        for (EnchantmentCategory enchantmentcategory : this.categories) {
            if (enchantmentcategory == category) {
                return true;
            }
        }

        return false;
    }

    public void addItems(List<ItemStack> inventory) {
        for (Item item : Item.REGISTRY) {
            if (item != null && item.getCreativeModeTab() == this) {
                item.addToCreativeMenu(item, this, inventory);
            }
        }

        if (this.getEnchantmentCategories() != null) {
            this.addEnchantedBooks(inventory, this.getEnchantmentCategories());
        }
    }

    public void addEnchantedBooks(List<ItemStack> inventory, EnchantmentCategory... categories) {
        for (Enchantment enchantment : Enchantment.ALL) {
            if (enchantment != null && enchantment.category != null) {
                boolean flag = false;

                for (int i = 0; i < categories.length && !flag; i++) {
                    if (enchantment.category == categories[i]) {
                        flag = true;
                    }
                }

                if (flag) {
                    inventory.add(Items.ENCHANTED_BOOK.withEnchantment(new EnchantmentEntry(enchantment, enchantment.getMaxLevel())));
                }
            }
        }
    }
}
