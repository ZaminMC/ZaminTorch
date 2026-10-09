package net.minecraft.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.UnbreakingEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.Stats;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class ItemStack {
    public static final DecimalFormat MODIFIER_FORMAT = new DecimalFormat("#.###");
    public int size;
    public int popAnimationTime;
    private Item item;
    private NbtCompound nbt;
    private int metadata;
    private ItemFrameEntity itemFrame;
    private Block cachedMineBlockOverride = null;
    private boolean cachedMineBlockOverrideResult = false;
    private Block cachedPlaceOnBlockOverride = null;
    private boolean cachedPlaceOnBlockOverrideResult = false;

    public ItemStack(Block block) {
        this(block, 1);
    }

    public ItemStack(Block block, int size) {
        this(block, size, 0);
    }

    public ItemStack(Block block, int size, int metadata) {
        this(Item.byBlock(block), size, metadata);
    }

    public ItemStack(Item item) {
        this(item, 1);
    }

    public ItemStack(Item item, int size) {
        this(item, size, 0);
    }

    public ItemStack(Item item, int size, int metadata) {
        this.item = item;
        this.size = size;
        this.metadata = metadata;
        if (this.metadata < 0) {
            this.metadata = 0;
        }
    }

    public static ItemStack fromNbt(NbtCompound nbt) {
        ItemStack itemstack = new ItemStack();
        itemstack.readNbt(nbt);
        return itemstack.getItem() != null ? itemstack : null;
    }

    private ItemStack() {
    }

    public ItemStack split(int amount) {
        ItemStack itemstack = new ItemStack(this.item, amount, this.metadata);
        if (this.nbt != null) {
            itemstack.nbt = (NbtCompound)this.nbt.copy();
        }

        this.size -= amount;
        return itemstack;
    }

    public Item getItem() {
        return this.item;
    }

    public boolean useOn(PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        boolean flag = this.getItem().useOn(this, player, world, pos, face, faceX, faceY, faceZ);
        if (flag) {
            player.incrementStat(Stats.ITEMS_USED[Item.getId(this.item)]);
        }

        return flag;
    }

    public float getMiningSpeed(Block block) {
        return this.getItem().getMiningSpeed(this, block);
    }

    public ItemStack startUsing(World world, PlayerEntity player) {
        return this.getItem().startUsing(this, world, player);
    }

    public ItemStack finishUsing(World world, PlayerEntity player) {
        return this.getItem().finishUsing(this, world, player);
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        Identifier identifier = Item.REGISTRY.getKey(this.item);
        nbt.putString("id", identifier == null ? "minecraft:air" : identifier.toString());
        nbt.putByte("Count", (byte)this.size);
        nbt.putShort("Damage", (short)this.metadata);
        if (this.nbt != null) {
            nbt.put("tag", this.nbt);
        }

        return nbt;
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains("id", 8)) {
            this.item = Item.byKey(nbt.getString("id"));
        } else {
            this.item = Item.byId(nbt.getShort("id"));
        }

        this.size = nbt.getByte("Count");
        this.metadata = nbt.getShort("Damage");
        if (this.metadata < 0) {
            this.metadata = 0;
        }

        if (nbt.contains("tag", 10)) {
            this.nbt = nbt.getCompound("tag");
            if (this.item != null) {
                this.item.validateNbt(this.nbt);
            }
        }
    }

    public int getMaxSize() {
        return this.getItem().getMaxStackSize();
    }

    public boolean isStackable() {
        return this.getMaxSize() > 1 && (!this.isDamageable() || !this.isDamaged());
    }

    public boolean isDamageable() {
        return this.item != null && this.item.getMaxDamage() > 0 && (!this.hasNbt() || !this.getNbt().getBoolean("Unbreakable"));
    }

    public boolean hasCustomData() {
        return this.item.hasCustomData();
    }

    public boolean isDamaged() {
        return this.isDamageable() && this.metadata > 0;
    }

    public int getDamage() {
        return this.metadata;
    }

    public int getMetadata() {
        return this.metadata;
    }

    public void setDamage(int damage) {
        this.metadata = damage;
        if (this.metadata < 0) {
            this.metadata = 0;
        }
    }

    public int getMaxDamage() {
        return this.item.getMaxDamage();
    }

    /**
     * Applies damage to this item stack and returns whether it is past the maximum damage value.
     */
    public boolean takeDamage(int amount, Random random) {
        if (!this.isDamageable()) {
            return false;
        }

        if (amount > 0) {
            int i = EnchantmentHelper.getLevel(Enchantment.UNBREAKING.id, this);
            int j = 0;

            for (int k = 0; i > 0 && k < amount; k++) {
                if (UnbreakingEnchantment.shouldReduceDamage(this, i, random)) {
                    j++;
                }
            }

            amount -= j;
            if (amount <= 0) {
                return false;
            }
        }

        this.metadata += amount;
        return this.metadata > this.getMaxDamage();
    }

    public void takeDamageAndBreak(int amount, LivingEntity holder) {
        if (!(holder instanceof PlayerEntity) || !((PlayerEntity)holder).abilities.creativeMode) {
            if (this.isDamageable()) {
                if (this.takeDamage(amount, holder.getRandom())) {
                    holder.onBrokenItem(this);
                    this.size--;
                    if (holder instanceof PlayerEntity) {
                        PlayerEntity playerentity = (PlayerEntity)holder;
                        playerentity.incrementStat(Stats.ITEMS_BROKEN[Item.getId(this.item)]);
                        if (this.size == 0 && this.getItem() instanceof BowItem) {
                            playerentity.clearItemInHand();
                        }
                    }

                    if (this.size < 0) {
                        this.size = 0;
                    }

                    this.metadata = 0;
                }
            }
        }
    }

    public void attack(LivingEntity target, PlayerEntity attacker) {
        boolean flag = this.item.attack(this, target, attacker);
        if (flag) {
            attacker.incrementStat(Stats.ITEMS_USED[Item.getId(this.item)]);
        }
    }

    public void mineBlock(World world, Block block, BlockPos pos, PlayerEntity player) {
        boolean flag = this.item.mineBlock(this, world, block, pos, player);
        if (flag) {
            player.incrementStat(Stats.ITEMS_USED[Item.getId(this.item)]);
        }
    }

    public boolean canMineBlock(Block block) {
        return this.item.canMineBlock(block);
    }

    public boolean interact(PlayerEntity player, LivingEntity entity) {
        return this.item.interact(this, player, entity);
    }

    public ItemStack copy() {
        ItemStack itemstack = new ItemStack(this.item, this.size, this.metadata);
        if (this.nbt != null) {
            itemstack.nbt = (NbtCompound)this.nbt.copy();
        }

        return itemstack;
    }

    public static boolean matchesNbt(ItemStack stack1, ItemStack stack2) {
        return stack1 == null && stack2 == null
            || stack1 != null && stack2 != null && (stack1.nbt != null || stack2.nbt == null) && (stack1.nbt == null || stack1.nbt.equals(stack2.nbt));
    }

    public static boolean matches(ItemStack stack1, ItemStack stack2) {
        return stack1 == null && stack2 == null || stack1 != null && stack2 != null && stack1.matches(stack2);
    }

    private boolean matches(ItemStack other) {
        return this.size == other.size
            && this.item == other.item
            && this.metadata == other.metadata
            && (this.nbt != null || other.nbt == null)
            && (this.nbt == null || this.nbt.equals(other.nbt));
    }

    public static boolean matchesItem(ItemStack stack1, ItemStack stack2) {
        return stack1 == null && stack2 == null || stack1 != null && stack2 != null && stack1.matchesItem(stack2);
    }

    public boolean matchesItem(ItemStack other) {
        return other != null && this.item == other.item && this.metadata == other.metadata;
    }

    public String getTranslationKey() {
        return this.item.getTranslationKey(this);
    }

    public static ItemStack copyOf(ItemStack stack) {
        return stack == null ? null : stack.copy();
    }

    @Override
    public String toString() {
        return this.size + "x" + this.item.getTranslationKey() + "@" + this.metadata;
    }

    public void tick(World world, Entity entity, int slot, boolean selected) {
        if (this.popAnimationTime > 0) {
            this.popAnimationTime--;
        }

        this.item.tick(this, world, entity, slot, selected);
    }

    public void onResult(World world, PlayerEntity player, int amount) {
        player.incrementStat(Stats.ITEMS_CRAFTED[Item.getId(this.item)], amount);
        this.item.onResult(this, world, player);
    }

    public boolean isEqualForHoldAnimation(ItemStack stack) {
        return this.matches(stack);
    }

    public int getUseDuration() {
        return this.getItem().getUseDuration(this);
    }

    public UseAction getUseAction() {
        return this.getItem().getUseAction(this);
    }

    public void stopUsing(World world, PlayerEntity player, int remainingUseTime) {
        this.getItem().stopUsing(this, world, player, remainingUseTime);
    }

    public boolean hasNbt() {
        return this.nbt != null;
    }

    public NbtCompound getNbt() {
        return this.nbt;
    }

    public NbtCompound getNbt(String key, boolean orCreate) {
        if (this.nbt != null && this.nbt.contains(key, 10)) {
            return this.nbt.getCompound(key);
        } else if (orCreate) {
            NbtCompound nbtcompound = new NbtCompound();
            this.addToNbt(key, nbtcompound);
            return nbtcompound;
        } else {
            return null;
        }
    }

    public NbtList getEnchantments() {
        return this.nbt == null ? null : this.nbt.getList("ench", 10);
    }

    public void setNbt(NbtCompound nbt) {
        this.nbt = nbt;
    }

    public String getHoverName() {
        String s = this.getItem().getName(this);
        if (this.nbt != null && this.nbt.contains("display", 10)) {
            NbtCompound nbtcompound = this.nbt.getCompound("display");
            if (nbtcompound.contains("Name", 8)) {
                s = nbtcompound.getString("Name");
            }
        }

        return s;
    }

    public ItemStack setHoverName(String name) {
        if (this.nbt == null) {
            this.nbt = new NbtCompound();
        }

        if (!this.nbt.contains("display", 10)) {
            this.nbt.put("display", new NbtCompound());
        }

        this.nbt.getCompound("display").putString("Name", name);
        return this;
    }

    public void resetHoverName() {
        if (this.nbt != null) {
            if (this.nbt.contains("display", 10)) {
                NbtCompound nbtcompound = this.nbt.getCompound("display");
                nbtcompound.remove("Name");
                if (nbtcompound.isEmpty()) {
                    this.nbt.remove("display");
                    if (this.nbt.isEmpty()) {
                        this.setNbt(null);
                    }
                }
            }
        }
    }

    public boolean hasCustomHoverName() {
        return this.nbt != null && this.nbt.contains("display", 10) && this.nbt.getCompound("display").contains("Name", 8);
    }

    public List<String> getTooltip(PlayerEntity player, boolean advanced) {
        List<String> list = Lists.newArrayList();
        String s = this.getHoverName();
        if (this.hasCustomHoverName()) {
            s = Formatting.ITALIC + s;
        }

        s = s + Formatting.RESET;
        if (advanced) {
            String s1 = "";
            if (s.length() > 0) {
                s = s + " (";
                s1 = ")";
            }

            int i = Item.getId(this.item);
            if (this.hasCustomData()) {
                s = s + String.format("#%04d/%d%s", i, this.metadata, s1);
            } else {
                s = s + String.format("#%04d%s", i, s1);
            }
        } else if (!this.hasCustomHoverName() && this.item == Items.FILLED_MAP) {
            s = s + " #" + this.metadata;
        }

        list.add(s);
        int i1 = 0;
        if (this.hasNbt() && this.nbt.contains("HideFlags", 99)) {
            i1 = this.nbt.getInt("HideFlags");
        }

        if ((i1 & 32) == 0) {
            this.item.addHoverText(this, player, list, advanced);
        }

        if (this.hasNbt()) {
            if ((i1 & 1) == 0) {
                NbtList nbtlist = this.getEnchantments();
                if (nbtlist != null) {
                    for (int j = 0; j < nbtlist.size(); j++) {
                        int k = nbtlist.getCompound(j).getShort("id");
                        int l = nbtlist.getCompound(j).getShort("lvl");
                        if (Enchantment.byId(k) != null) {
                            list.add(Enchantment.byId(k).getName(l));
                        }
                    }
                }
            }

            if (this.nbt.contains("display", 10)) {
                NbtCompound nbtcompound = this.nbt.getCompound("display");
                if (nbtcompound.contains("color", 3)) {
                    if (advanced) {
                        list.add("Color: #" + Integer.toHexString(nbtcompound.getInt("color")).toUpperCase());
                    } else {
                        list.add(Formatting.ITALIC + I18n.translate("item.dyed"));
                    }
                }

                if (nbtcompound.getType("Lore") == 9) {
                    NbtList nbtlist1 = nbtcompound.getList("Lore", 8);
                    if (nbtlist1.size() > 0) {
                        for (int j1 = 0; j1 < nbtlist1.size(); j1++) {
                            list.add(Formatting.DARK_PURPLE + "" + Formatting.ITALIC + nbtlist1.getString(j1));
                        }
                    }
                }
            }
        }

        Multimap<String, AttributeModifier> multimap = this.getAttributeModifiers();
        if (!multimap.isEmpty() && (i1 & 2) == 0) {
            list.add("");

            for (Entry<String, AttributeModifier> entry : multimap.entries()) {
                AttributeModifier attributemodifier = entry.getValue();
                double d0 = attributemodifier.get();
                if (attributemodifier.getId() == Item.ATTACK_DAMAGE_MODIFIER_UUID) {
                    d0 += EnchantmentHelper.modifyDamage(this, MobType.UNDEFINED);
                }

                double d1;
                if (attributemodifier.getOperation() != 1 && attributemodifier.getOperation() != 2) {
                    d1 = d0;
                } else {
                    d1 = d0 * 100.0;
                }

                if (d0 > 0.0) {
                    list.add(
                        Formatting.BLUE
                            + I18n.translate(
                                "attribute.modifier.plus." + attributemodifier.getOperation(),
                                MODIFIER_FORMAT.format(d1),
                                I18n.translate("attribute.name." + entry.getKey())
                            )
                    );
                } else if (d0 < 0.0) {
                    d1 *= -1.0;
                    list.add(
                        Formatting.RED
                            + I18n.translate(
                                "attribute.modifier.take." + attributemodifier.getOperation(),
                                MODIFIER_FORMAT.format(d1),
                                I18n.translate("attribute.name." + entry.getKey())
                            )
                    );
                }
            }
        }

        if (this.hasNbt() && this.getNbt().getBoolean("Unbreakable") && (i1 & 4) == 0) {
            list.add(Formatting.BLUE + I18n.translate("item.unbreakable"));
        }

        if (this.hasNbt() && this.nbt.contains("CanDestroy", 9) && (i1 & 8) == 0) {
            NbtList nbtlist2 = this.nbt.getList("CanDestroy", 8);
            if (nbtlist2.size() > 0) {
                list.add("");
                list.add(Formatting.GRAY + I18n.translate("item.canBreak"));

                for (int k1 = 0; k1 < nbtlist2.size(); k1++) {
                    Block block = Block.byKey(nbtlist2.getString(k1));
                    if (block != null) {
                        list.add(Formatting.DARK_GRAY + block.getName());
                    } else {
                        list.add(Formatting.DARK_GRAY + "missingno");
                    }
                }
            }
        }

        if (this.hasNbt() && this.nbt.contains("CanPlaceOn", 9) && (i1 & 16) == 0) {
            NbtList nbtlist3 = this.nbt.getList("CanPlaceOn", 8);
            if (nbtlist3.size() > 0) {
                list.add("");
                list.add(Formatting.GRAY + I18n.translate("item.canPlace"));

                for (int l1 = 0; l1 < nbtlist3.size(); l1++) {
                    Block block1 = Block.byKey(nbtlist3.getString(l1));
                    if (block1 != null) {
                        list.add(Formatting.DARK_GRAY + block1.getName());
                    } else {
                        list.add(Formatting.DARK_GRAY + "missingno");
                    }
                }
            }
        }

        if (advanced) {
            if (this.isDamaged()) {
                list.add("Durability: " + (this.getMaxDamage() - this.getDamage()) + " / " + this.getMaxDamage());
            }

            list.add(Formatting.DARK_GRAY + Item.REGISTRY.getKey(this.item).toString());
            if (this.hasNbt()) {
                list.add(Formatting.DARK_GRAY + "NBT: " + this.getNbt().getKeys().size() + " tag(s)");
            }
        }

        return list;
    }

    public boolean hasEnchantmentGlint() {
        return this.getItem().hasEnchantmentGlint(this);
    }

    public Rarity getRarity() {
        return this.getItem().getRarity(this);
    }

    public boolean isEnchantable() {
        return this.getItem().isEnchantable(this) && !this.hasEnchantments();
    }

    public void addEnchantment(Enchantment enchantment, int level) {
        if (this.nbt == null) {
            this.setNbt(new NbtCompound());
        }

        if (!this.nbt.contains("ench", 9)) {
            this.nbt.put("ench", new NbtList());
        }

        NbtList nbtlist = this.nbt.getList("ench", 10);
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.putShort("id", (short)enchantment.id);
        nbtcompound.putShort("lvl", (byte)level);
        nbtlist.addElement(nbtcompound);
    }

    public boolean hasEnchantments() {
        return this.nbt != null && this.nbt.contains("ench", 9);
    }

    public void addToNbt(String key, NbtElement nbt) {
        if (this.nbt == null) {
            this.setNbt(new NbtCompound());
        }

        this.nbt.put(key, nbt);
    }

    public boolean canUseOnBlockInAdventureMode() {
        return this.getItem().canUseOnBlockInAdventureMode();
    }

    public boolean isInItemFrame() {
        return this.itemFrame != null;
    }

    public void setItemFrame(ItemFrameEntity itemFrame) {
        this.itemFrame = itemFrame;
    }

    public ItemFrameEntity getItemFrame() {
        return this.itemFrame;
    }

    public int getRepairCost() {
        return this.hasNbt() && this.nbt.contains("RepairCost", 3) ? this.nbt.getInt("RepairCost") : 0;
    }

    public void setRepairCost(int cost) {
        if (!this.hasNbt()) {
            this.nbt = new NbtCompound();
        }

        this.nbt.putInt("RepairCost", cost);
    }

    public Multimap<String, AttributeModifier> getAttributeModifiers() {
        Multimap<String, AttributeModifier> multimap;
        if (this.hasNbt() && this.nbt.contains("AttributeModifiers", 9)) {
            multimap = HashMultimap.create();
            NbtList nbtlist = this.nbt.getList("AttributeModifiers", 10);

            for (int i = 0; i < nbtlist.size(); i++) {
                NbtCompound nbtcompound = nbtlist.getCompound(i);
                AttributeModifier attributemodifier = EntityAttributes.fromNbt(nbtcompound);
                if (attributemodifier != null
                    && attributemodifier.getId().getLeastSignificantBits() != 0L
                    && attributemodifier.getId().getMostSignificantBits() != 0L) {
                    multimap.put(nbtcompound.getString("AttributeName"), attributemodifier);
                }
            }
        } else {
            multimap = this.getItem().getDefaultAttributeModifiers();
        }

        return multimap;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Text getDisplayName() {
        LiteralText literaltext = new LiteralText(this.getHoverName());
        if (this.hasCustomHoverName()) {
            literaltext.getStyle().setItalic(true);
        }

        Text text = new LiteralText("[").append(literaltext).append("]");
        if (this.item != null) {
            NbtCompound nbtcompound = new NbtCompound();
            this.writeNbt(nbtcompound);
            text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new LiteralText(nbtcompound.toString())));
            text.getStyle().setColor(this.getRarity().formatting);
        }

        return text;
    }

    public boolean hasMineBlockOverride(Block block) {
        if (block == this.cachedMineBlockOverride) {
            return this.cachedMineBlockOverrideResult;
        }

        this.cachedMineBlockOverride = block;
        if (this.hasNbt() && this.nbt.contains("CanDestroy", 9)) {
            NbtList nbtlist = this.nbt.getList("CanDestroy", 8);

            for (int i = 0; i < nbtlist.size(); i++) {
                Block blockx = Block.byKey(nbtlist.getString(i));
                if (blockx == block) {
                    this.cachedMineBlockOverrideResult = true;
                    return true;
                }
            }
        }

        this.cachedMineBlockOverrideResult = false;
        return false;
    }

    public boolean hasPlaceOnBlockOverride(Block block) {
        if (block == this.cachedPlaceOnBlockOverride) {
            return this.cachedPlaceOnBlockOverrideResult;
        }

        this.cachedPlaceOnBlockOverride = block;
        if (this.hasNbt() && this.nbt.contains("CanPlaceOn", 9)) {
            NbtList nbtlist = this.nbt.getList("CanPlaceOn", 8);

            for (int i = 0; i < nbtlist.size(); i++) {
                Block blockx = Block.byKey(nbtlist.getString(i));
                if (blockx == block) {
                    this.cachedPlaceOnBlockOverrideResult = true;
                    return true;
                }
            }
        }

        this.cachedPlaceOnBlockOverrideResult = false;
        return false;
    }
}
