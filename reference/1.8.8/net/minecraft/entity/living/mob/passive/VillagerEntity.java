package net.minecraft.entity.living.mob.passive;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentEntry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.FollowGolemGoal;
import net.minecraft.entity.ai.goal.FormCaravanGoal;
import net.minecraft.entity.ai.goal.GoToEntityGoal;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.LookAtCustomerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RestrictOpenDoorGoal;
import net.minecraft.entity.ai.goal.StayIndoorsGoal;
import net.minecraft.entity.ai.goal.StopFollowingCustomerGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TradeWithVillagerGoal;
import net.minecraft.entity.ai.goal.VillagerFarmGoal;
import net.minecraft.entity.ai.goal.VillagerMatingGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.stat.Stats;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.village.Village;
import net.minecraft.world.village.trade.TradeOffer;
import net.minecraft.world.village.trade.TradeOffers;
import net.minecraft.world.village.trade.Trader;

public class VillagerEntity extends PassiveEntity implements Trader, Npc {
    private int profession;
    private boolean mating;
    private boolean inCaravan;
    Village village;
    private PlayerEntity customer;
    private TradeOffers traderOffers;
    private int levelUpCountdown;
    private boolean levelUp;
    private boolean willing;
    private int riches;
    private String lastSuccessfulTradeCustomer;
    private int career;
    private int careerLevel;
    /**
     * This field will be set to true when a villager gets created by the means of converting a zombie.
     * In next mobTick() it will be set to false and awards all the players in the village with a reputation bonus.
     */
    private boolean convertedZombie;
    private boolean hasAgeDependentGoals;
    private SimpleInventory inventory = new SimpleInventory("Items", false, 8);
    private static final VillagerEntity.TradeSource[][][][] TRADE_SOURCES = new VillagerEntity.TradeSource[][][][]{
        {
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.WHEAT, new VillagerEntity.RandomAmount(18, 22)),
                                new VillagerEntity.EmeraldForItems(Items.POTATO, new VillagerEntity.RandomAmount(15, 19)),
                                new VillagerEntity.EmeraldForItems(Items.CARROT, new VillagerEntity.RandomAmount(15, 19)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.BREAD, new VillagerEntity.RandomAmount(-4, -2))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Item.byBlock(Blocks.PUMPKIN), new VillagerEntity.RandomAmount(8, 13)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.PUMPKIN_PIE, new VillagerEntity.RandomAmount(-3, -2))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Item.byBlock(Blocks.MELON_BLOCK), new VillagerEntity.RandomAmount(7, 12)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.APPLE, new VillagerEntity.RandomAmount(-5, -7))
                        },
                        {
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.COOKIE, new VillagerEntity.RandomAmount(-6, -10)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.CAKE, new VillagerEntity.RandomAmount(1, 1))
                        }
                },
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.STRING, new VillagerEntity.RandomAmount(15, 20)),
                                new VillagerEntity.EmeraldForItems(Items.COAL, new VillagerEntity.RandomAmount(16, 24)),
                                new VillagerEntity.ItemsForItemsAndEmeralds(
                                    Items.FISH, new VillagerEntity.RandomAmount(6, 6), Items.COOKED_FISH, new VillagerEntity.RandomAmount(6, 6)
                                )
                        },
                        {new VillagerEntity.ItemsForEmeralds(Items.FISHING_ROD, new VillagerEntity.RandomAmount(7, 8))}
                },
                {
                        {
                                new VillagerEntity.EmeraldForItems(Item.byBlock(Blocks.WOOL), new VillagerEntity.RandomAmount(16, 22)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.SHEARS, new VillagerEntity.RandomAmount(3, 4))
                        },
                        {
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 0), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 1), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 2), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 3), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 4), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 5), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 6), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 7), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 8), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 9), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 10), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 11), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 12), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 13), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 14), new VillagerEntity.RandomAmount(1, 2)
                                ),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Item.byBlock(Blocks.WOOL), 1, 15), new VillagerEntity.RandomAmount(1, 2)
                                )
                        }
                },
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.STRING, new VillagerEntity.RandomAmount(15, 20)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.ARROW, new VillagerEntity.RandomAmount(-12, -8))
                        },
                        {
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.BOW, new VillagerEntity.RandomAmount(2, 3)),
                                new VillagerEntity.ItemsForItemsAndEmeralds(
                                    Item.byBlock(Blocks.GRAVEL), new VillagerEntity.RandomAmount(10, 10), Items.FLINT, new VillagerEntity.RandomAmount(6, 10)
                                )
                        }
                }
        },
        {
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.PAPER, new VillagerEntity.RandomAmount(24, 36)),
                                new VillagerEntity.EnchantedItemForEmeralds()
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.BOOK, new VillagerEntity.RandomAmount(8, 10)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.COMPASS, new VillagerEntity.RandomAmount(10, 12)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Item.byBlock(Blocks.BOOKSHELF), new VillagerEntity.RandomAmount(3, 4))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.WRITTEN_BOOK, new VillagerEntity.RandomAmount(2, 2)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.CLOCK, new VillagerEntity.RandomAmount(10, 12)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Item.byBlock(Blocks.GLASS), new VillagerEntity.RandomAmount(-5, -3))
                        },
                        {new VillagerEntity.EnchantedItemForEmeralds()},
                        {new VillagerEntity.EnchantedItemForEmeralds()},
                        {new VillagerEntity.EmeraldsForVillagerTypeItem(Items.NAME_TAG, new VillagerEntity.RandomAmount(20, 22))}
                }
        },
        {
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.ROTTEN_FLESH, new VillagerEntity.RandomAmount(36, 40)),
                                new VillagerEntity.EmeraldForItems(Items.GOLD_INGOT, new VillagerEntity.RandomAmount(8, 10))
                        },
                        {
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.REDSTONE, new VillagerEntity.RandomAmount(-4, -1)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(
                                    new ItemStack(Items.DYE, 1, DyeColor.BLUE.getMetadata()), new VillagerEntity.RandomAmount(-2, -1)
                                )
                        },
                        {
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.ENDER_EYE, new VillagerEntity.RandomAmount(7, 11)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Item.byBlock(Blocks.GLOWSTONE), new VillagerEntity.RandomAmount(-3, -1))
                        },
                        {new VillagerEntity.EmeraldsForVillagerTypeItem(Items.EXPERIENCE_BOTTLE, new VillagerEntity.RandomAmount(3, 11))}
                }
        },
        {
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.COAL, new VillagerEntity.RandomAmount(16, 24)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.IRON_HELMET, new VillagerEntity.RandomAmount(4, 6))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.IRON_INGOT, new VillagerEntity.RandomAmount(7, 9)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.IRON_CHESTPLATE, new VillagerEntity.RandomAmount(10, 14))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.DIAMOND, new VillagerEntity.RandomAmount(3, 4)),
                                new VillagerEntity.ItemsForEmeralds(Items.DIAMOND_CHESTPLATE, new VillagerEntity.RandomAmount(16, 19))
                        },
                        {
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.CHAINMAIL_BOOTS, new VillagerEntity.RandomAmount(5, 7)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.CHAINMAIL_LEGGINGS, new VillagerEntity.RandomAmount(9, 11)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.CHAINMAIL_HELMET, new VillagerEntity.RandomAmount(5, 7)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.CHAINMAIL_CHESTPLATE, new VillagerEntity.RandomAmount(11, 15))
                        }
                },
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.COAL, new VillagerEntity.RandomAmount(16, 24)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.IRON_AXE, new VillagerEntity.RandomAmount(6, 8))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.IRON_INGOT, new VillagerEntity.RandomAmount(7, 9)),
                                new VillagerEntity.ItemsForEmeralds(Items.IRON_SWORD, new VillagerEntity.RandomAmount(9, 10))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.DIAMOND, new VillagerEntity.RandomAmount(3, 4)),
                                new VillagerEntity.ItemsForEmeralds(Items.DIAMOND_SWORD, new VillagerEntity.RandomAmount(12, 15)),
                                new VillagerEntity.ItemsForEmeralds(Items.DIAMOND_AXE, new VillagerEntity.RandomAmount(9, 12))
                        }
                },
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.COAL, new VillagerEntity.RandomAmount(16, 24)),
                                new VillagerEntity.ItemsForEmeralds(Items.IRON_SHOVEL, new VillagerEntity.RandomAmount(5, 7))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.IRON_INGOT, new VillagerEntity.RandomAmount(7, 9)),
                                new VillagerEntity.ItemsForEmeralds(Items.IRON_PICKAXE, new VillagerEntity.RandomAmount(9, 11))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.DIAMOND, new VillagerEntity.RandomAmount(3, 4)),
                                new VillagerEntity.ItemsForEmeralds(Items.DIAMOND_PICKAXE, new VillagerEntity.RandomAmount(12, 15))
                        }
                }
        },
        {
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.PORKCHOP, new VillagerEntity.RandomAmount(14, 18)),
                                new VillagerEntity.EmeraldForItems(Items.CHICKEN, new VillagerEntity.RandomAmount(14, 18))
                        },
                        {
                                new VillagerEntity.EmeraldForItems(Items.COAL, new VillagerEntity.RandomAmount(16, 24)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.COOKED_PORKCHOP, new VillagerEntity.RandomAmount(-7, -5)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.COOKED_CHICKEN, new VillagerEntity.RandomAmount(-8, -6))
                        }
                },
                {
                        {
                                new VillagerEntity.EmeraldForItems(Items.LEATHER, new VillagerEntity.RandomAmount(9, 12)),
                                new VillagerEntity.EmeraldsForVillagerTypeItem(Items.LEATHER_LEGGINGS, new VillagerEntity.RandomAmount(2, 4))
                        },
                        {new VillagerEntity.ItemsForEmeralds(Items.LEATHER_CHESTPLATE, new VillagerEntity.RandomAmount(7, 12))},
                        {new VillagerEntity.EmeraldsForVillagerTypeItem(Items.SADDLE, new VillagerEntity.RandomAmount(8, 10))}
                }
        }
    };

    public VillagerEntity(World world) {
        this(world, 0);
    }

    public VillagerEntity(World world, int profession) {
        super(world);
        this.setProfession(profession);
        this.setSize(0.6F, 1.8F);
        ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new FleeEntityGoal<>(this, ZombieEntity.class, 8.0F, 0.6, 0.6));
        this.goalSelector.addGoal(1, new StopFollowingCustomerGoal(this));
        this.goalSelector.addGoal(1, new LookAtCustomerGoal(this));
        this.goalSelector.addGoal(2, new StayIndoorsGoal(this));
        this.goalSelector.addGoal(3, new RestrictOpenDoorGoal(this));
        this.goalSelector.addGoal(4, new LongDoorInteractGoal(this, true));
        this.goalSelector.addGoal(5, new WanderThroughVillageGoal(this, 0.6));
        this.goalSelector.addGoal(6, new VillagerMatingGoal(this));
        this.goalSelector.addGoal(7, new FollowGolemGoal(this));
        this.goalSelector.addGoal(9, new GoToEntityGoal(this, PlayerEntity.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(9, new TradeWithVillagerGoal(this));
        this.goalSelector.addGoal(9, new WanderAroundGoal(this, 0.6));
        this.goalSelector.addGoal(10, new LookAtEntityGoal(this, MobEntity.class, 8.0F));
        this.setCanPickupLoot(true);
    }

    private void registerAgeDependentGoals() {
        if (!this.hasAgeDependentGoals) {
            this.hasAgeDependentGoals = true;
            if (this.isBaby()) {
                this.goalSelector.addGoal(8, new FormCaravanGoal(this, 0.32));
            } else if (this.getProfession() == 0) {
                this.goalSelector.addGoal(6, new VillagerFarmGoal(this, 0.6));
            }
        }
    }

    @Override
    protected void onGrowUp() {
        if (this.getProfession() == 0) {
            this.goalSelector.addGoal(8, new VillagerFarmGoal(this, 0.6));
        }

        super.onGrowUp();
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.5);
    }

    @Override
    protected void mobAiTick() {
        if (--this.profession <= 0) {
            BlockPos blockpos = new BlockPos(this);
            this.world.getVillages().markForUpdate(blockpos);
            this.profession = 70 + this.random.nextInt(50);
            this.village = this.world.getVillages().getNearestVillage(blockpos, 32);
            if (this.village == null) {
                this.resetVillageRadius();
            } else {
                BlockPos blockpos1 = this.village.getCenter();
                this.setVillagePosAndRadius(blockpos1, (int)(this.village.getRadius() * 1.0F));
                if (this.convertedZombie) {
                    this.convertedZombie = false;
                    this.village.updateAllReputations(5);
                }
            }
        }

        if (!this.hasCustomer() && this.levelUpCountdown > 0) {
            this.levelUpCountdown--;
            if (this.levelUpCountdown <= 0) {
                if (this.levelUp) {
                    for (TradeOffer tradeoffer : this.traderOffers) {
                        if (tradeoffer.isDisabled()) {
                            tradeoffer.increaseMaxUses(this.random.nextInt(6) + this.random.nextInt(6) + 2);
                        }
                    }

                    this.levelUp();
                    this.levelUp = false;
                    if (this.village != null && this.lastSuccessfulTradeCustomer != null) {
                        this.world.doEntityEvent(this, (byte)14);
                        this.village.updateReputation(this.lastSuccessfulTradeCustomer, 1);
                    }
                }

                this.addStatusEffect(new StatusEffectInstance(StatusEffect.REGENERATION.id, 200, 0));
            }
        }

        super.mobAiTick();
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        boolean flag = itemstack != null && itemstack.getItem() == Items.SPAWN_EGG;
        if (!flag && this.isAlive() && !this.hasCustomer() && !this.isBaby()) {
            if (!this.world.isClient && (this.traderOffers == null || this.traderOffers.size() > 0)) {
                this.setCustomer(player);
                player.openTraderMenu(this);
            }

            player.incrementStat(Stats.TALKED_TO_VILLAGER);
            return true;
        } else {
            return super.interactMob(player);
        }
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, 0);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("Profession", this.getProfession());
        nbt.putInt("Riches", this.riches);
        nbt.putInt("Career", this.career);
        nbt.putInt("CareerLevel", this.careerLevel);
        nbt.putBoolean("Willing", this.willing);
        if (this.traderOffers != null) {
            nbt.put("Offers", this.traderOffers.toNbt());
        }

        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.inventory.getSize(); i++) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (itemstack != null) {
                nbtlist.addElement(itemstack.writeNbt(new NbtCompound()));
            }
        }

        nbt.put("Inventory", nbtlist);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setProfession(nbt.getInt("Profession"));
        this.riches = nbt.getInt("Riches");
        this.career = nbt.getInt("Career");
        this.careerLevel = nbt.getInt("CareerLevel");
        this.willing = nbt.getBoolean("Willing");
        if (nbt.contains("Offers", 10)) {
            NbtCompound nbtcompound = nbt.getCompound("Offers");
            this.traderOffers = new TradeOffers(nbtcompound);
        }

        NbtList nbtlist = nbt.getList("Inventory", 10);

        for (int i = 0; i < nbtlist.size(); i++) {
            ItemStack itemstack = ItemStack.fromNbt(nbtlist.getCompound(i));
            if (itemstack != null) {
                this.inventory.addItem(itemstack);
            }
        }

        this.setCanPickupLoot(true);
        this.registerAgeDependentGoals();
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected String getAmbientSound() {
        return this.hasCustomer() ? "mob.villager.haggle" : "mob.villager.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.villager.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.villager.death";
    }

    public void setProfession(int profession) {
        this.syncedData.update(16, profession);
    }

    public int getProfession() {
        return Math.max(this.syncedData.getInt(16) % 5, 0);
    }

    public boolean getMating() {
        return this.mating;
    }

    public void setMating(boolean value) {
        this.mating = value;
    }

    public void setInCaravan(boolean value) {
        this.inCaravan = value;
    }

    public boolean getInCaravan() {
        return this.inCaravan;
    }

    @Override
    public void setAttacker(LivingEntity attacker) {
        super.setAttacker(attacker);
        if (this.village != null && attacker != null) {
            this.village.addOrUpdateAttacker(attacker);
            if (attacker instanceof PlayerEntity) {
                int i = -1;
                if (this.isBaby()) {
                    i = -3;
                }

                this.village.updateReputation(attacker.getName(), i);
                if (this.isAlive()) {
                    this.world.doEntityEvent(this, (byte)13);
                }
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        if (this.village != null) {
            Entity entity = source.getAttacker();
            if (entity != null) {
                if (entity instanceof PlayerEntity) {
                    this.village.updateReputation(entity.getName(), -2);
                } else if (entity instanceof Monster) {
                    this.village.stopMating();
                }
            } else {
                PlayerEntity playerentity = this.world.getNearestPlayer(this, 16.0);
                if (playerentity != null) {
                    this.village.stopMating();
                }
            }
        }

        super.die(source);
    }

    @Override
    public void setCustomer(PlayerEntity player) {
        this.customer = player;
    }

    @Override
    public PlayerEntity getCustomer() {
        return this.customer;
    }

    public boolean hasCustomer() {
        return this.customer != null;
    }

    public boolean consumeAvailableFood(boolean updateFirst) {
        if (!this.willing && updateFirst && this.hasExcessFood()) {
            boolean flag = false;

            for (int i = 0; i < this.inventory.getSize(); i++) {
                ItemStack itemstack = this.inventory.getItem(i);
                if (itemstack != null) {
                    if (itemstack.getItem() == Items.BREAD && itemstack.size >= 3) {
                        flag = true;
                        this.inventory.removeItem(i, 3);
                    } else if ((itemstack.getItem() == Items.POTATO || itemstack.getItem() == Items.CARROT) && itemstack.size >= 12) {
                        flag = true;
                        this.inventory.removeItem(i, 12);
                    }
                }

                if (flag) {
                    this.world.doEntityEvent(this, (byte)18);
                    this.willing = true;
                    break;
                }
            }
        }

        return this.willing;
    }

    public void setWilling(boolean willing) {
        this.willing = willing;
    }

    @Override
    public void trade(TradeOffer offer) {
        offer.use();
        this.ambientSoundDelay = -this.getAmbientSoundInterval();
        this.playSound("mob.villager.yes", this.getSoundVolume(), this.getSoundPitch());
        int i = 3 + this.random.nextInt(4);
        if (offer.getUses() == 1 || this.random.nextInt(5) == 0) {
            this.levelUpCountdown = 40;
            this.levelUp = true;
            this.willing = true;
            if (this.customer != null) {
                this.lastSuccessfulTradeCustomer = this.customer.getName();
            } else {
                this.lastSuccessfulTradeCustomer = null;
            }

            i += 5;
        }

        if (offer.getPrimaryPayment().getItem() == Items.EMERALD) {
            this.riches = this.riches + offer.getPrimaryPayment().size;
        }

        if (offer.rewardXp()) {
            this.world.addEntity(new ExperienceOrbEntity(this.world, this.x, this.y + 0.5, this.z, i));
        }
    }

    @Override
    public void updateOffer(ItemStack item) {
        if (!this.world.isClient && this.ambientSoundDelay > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundDelay = -this.getAmbientSoundInterval();
            if (item != null) {
                this.playSound("mob.villager.yes", this.getSoundVolume(), this.getSoundPitch());
            } else {
                this.playSound("mob.villager.no", this.getSoundVolume(), this.getSoundPitch());
            }
        }
    }

    @Override
    public TradeOffers getOffers(PlayerEntity player) {
        if (this.traderOffers == null) {
            this.levelUp();
        }

        return this.traderOffers;
    }

    private void levelUp() {
        VillagerEntity.TradeSource[][][] avillagerentity$tradesource = TRADE_SOURCES[this.getProfession()];
        if (this.career != 0 && this.careerLevel != 0) {
            this.careerLevel++;
        } else {
            this.career = this.random.nextInt(avillagerentity$tradesource.length) + 1;
            this.careerLevel = 1;
        }

        if (this.traderOffers == null) {
            this.traderOffers = new TradeOffers();
        }

        int i = this.career - 1;
        int j = this.careerLevel - 1;
        VillagerEntity.TradeSource[][] avillagerentity$tradesource1 = avillagerentity$tradesource[i];
        if (j >= 0 && j < avillagerentity$tradesource1.length) {
            VillagerEntity.TradeSource[] avillagerentity$tradesource2 = avillagerentity$tradesource1[j];

            for (VillagerEntity.TradeSource villagerentity$tradesource : avillagerentity$tradesource2) {
                villagerentity$tradesource.provide(this.traderOffers, this.random);
            }
        }
    }

    @Override
    public void setOffers(TradeOffers offers) {
    }

    @Override
    public Text getDisplayName() {
        String s = this.getCustomName();
        if (s != null && s.length() > 0) {
            LiteralText literaltext = new LiteralText(s);
            literaltext.getStyle().setHoverEvent(this.getHoverEvent());
            literaltext.getStyle().setInsertion(this.getUuid().toString());
            return literaltext;
        }

        if (this.traderOffers == null) {
            this.levelUp();
        }

        String s1 = null;
        switch (this.getProfession()) {
            case 0:
                if (this.career == 1) {
                    s1 = "farmer";
                } else if (this.career == 2) {
                    s1 = "fisherman";
                } else if (this.career == 3) {
                    s1 = "shepherd";
                } else if (this.career == 4) {
                    s1 = "fletcher";
                }
                break;
            case 1:
                s1 = "librarian";
                break;
            case 2:
                s1 = "cleric";
                break;
            case 3:
                if (this.career == 1) {
                    s1 = "armor";
                } else if (this.career == 2) {
                    s1 = "weapon";
                } else if (this.career == 3) {
                    s1 = "tool";
                }
                break;
            case 4:
                if (this.career == 1) {
                    s1 = "butcher";
                } else if (this.career == 2) {
                    s1 = "leather";
                }
        }

        if (s1 != null) {
            TranslatableText translatabletext = new TranslatableText("entity.Villager." + s1);
            translatabletext.getStyle().setHoverEvent(this.getHoverEvent());
            translatabletext.getStyle().setInsertion(this.getUuid().toString());
            return translatabletext;
        } else {
            return super.getDisplayName();
        }
    }

    @Override
    public float getEyeHeight() {
        float f = 1.62F;
        if (this.isBaby()) {
            f = (float)(f - 0.81);
        }

        return f;
    }

    @Override
    public void doEvent(byte event) {
        if (event == 12) {
            this.addParticles(ParticleType.HEART);
        } else if (event == 13) {
            this.addParticles(ParticleType.VILLAGER_ANGRY);
        } else if (event == 14) {
            this.addParticles(ParticleType.VILLAGER_HAPPY);
        } else {
            super.doEvent(event);
        }
    }

    private void addParticles(ParticleType type) {
        for (int i = 0; i < 5; i++) {
            double d0 = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.world
                .addParticle(
                    type,
                    this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                    this.y + 1.0 + this.random.nextFloat() * this.height,
                    this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                    d0,
                    d1,
                    d2
                );
        }
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        this.setProfession(this.world.random.nextInt(5));
        this.registerAgeDependentGoals();
        return data;
    }

    public void setConvertedZombie() {
        this.convertedZombie = true;
    }

    public VillagerEntity makeChild(PassiveEntity passiveEntity) {
        VillagerEntity villagerentity = new VillagerEntity(this.world);
        villagerentity.initialize(this.world.getLocalDifficulty(new BlockPos(villagerentity)), null);
        return villagerentity;
    }

    @Override
    public boolean isTameable() {
        return false;
    }

    @Override
    public void struckByLightning(LightningBoltEntity lightning) {
        if (!this.world.isClient && !this.removed) {
            WitchEntity witchentity = new WitchEntity(this.world);
            witchentity.setPositionAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
            witchentity.initialize(this.world.getLocalDifficulty(new BlockPos(witchentity)), null);
            witchentity.setNoAi(this.isNoAi());
            if (this.hasCustomName()) {
                witchentity.setCustomName(this.getCustomName());
                witchentity.setCustomNameVisible(this.isCustomNameVisible());
            }

            this.world.addEntity(witchentity);
            this.remove();
        }
    }

    public SimpleInventory getVillagerInventory() {
        return this.inventory;
    }

    @Override
    protected void updateInventory(ItemEntity item) {
        ItemStack itemstack = item.getItem();
        Item itemx = itemstack.getItem();
        if (this.isFood(itemx)) {
            ItemStack itemstack1 = this.inventory.addItem(itemstack);
            if (itemstack1 == null) {
                item.remove();
            } else {
                itemstack.size = itemstack1.size;
            }
        }
    }

    private boolean isFood(Item item) {
        return item == Items.BREAD || item == Items.POTATO || item == Items.CARROT || item == Items.WHEAT || item == Items.WHEAT_SEEDS;
    }

    public boolean hasExcessFood() {
        return this.hasFoodPoints(1);
    }

    public boolean wantsMoreFood() {
        return this.hasFoodPoints(2);
    }

    public boolean hasRoomForHarvest() {
        boolean flag = this.getProfession() == 0;
        return flag ? !this.hasFoodPoints(5) : !this.hasFoodPoints(1);
    }

    private boolean hasFoodPoints(int min) {
        boolean flag = this.getProfession() == 0;

        for (int i = 0; i < this.inventory.getSize(); i++) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (itemstack != null) {
                if (itemstack.getItem() == Items.BREAD && itemstack.size >= 3 * min
                    || itemstack.getItem() == Items.POTATO && itemstack.size >= 12 * min
                    || itemstack.getItem() == Items.CARROT && itemstack.size >= 12 * min) {
                    return true;
                }

                if (flag && itemstack.getItem() == Items.WHEAT && itemstack.size >= 9 * min) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean hasPlantableItems() {
        for (int i = 0; i < this.inventory.getSize(); i++) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (itemstack != null && (itemstack.getItem() == Items.WHEAT_SEEDS || itemstack.getItem() == Items.POTATO || itemstack.getItem() == Items.CARROT)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean replaceItem(int slot, ItemStack item) {
        if (super.replaceItem(slot, item)) {
            return true;
        } else {
            int i = slot - 300;
            if (i >= 0 && i < this.inventory.getSize()) {
                this.inventory.setItem(i, item);
                return true;
            } else {
                return false;
            }
        }
    }

    static class EmeraldForItems implements VillagerEntity.TradeSource {
        public Item item;
        public VillagerEntity.RandomAmount cost;

        public EmeraldForItems(Item item, VillagerEntity.RandomAmount cost) {
            this.item = item;
            this.cost = cost;
        }

        @Override
        public void provide(TradeOffers trades, Random random) {
            int i = 1;
            if (this.cost != null) {
                i = this.cost.pick(random);
            }

            trades.add(new TradeOffer(new ItemStack(this.item, i, 0), Items.EMERALD));
        }
    }

    static class EmeraldsForVillagerTypeItem implements VillagerEntity.TradeSource {
        public ItemStack item;
        public VillagerEntity.RandomAmount cost;

        public EmeraldsForVillagerTypeItem(Item item, VillagerEntity.RandomAmount cost) {
            this.item = new ItemStack(item);
            this.cost = cost;
        }

        public EmeraldsForVillagerTypeItem(ItemStack item, VillagerEntity.RandomAmount cost) {
            this.item = item;
            this.cost = cost;
        }

        @Override
        public void provide(TradeOffers trades, Random random) {
            int i = 1;
            if (this.cost != null) {
                i = this.cost.pick(random);
            }

            ItemStack itemstack;
            ItemStack itemstack1;
            if (i < 0) {
                itemstack = new ItemStack(Items.EMERALD, 1, 0);
                itemstack1 = new ItemStack(this.item.getItem(), -i, this.item.getMetadata());
            } else {
                itemstack = new ItemStack(Items.EMERALD, i, 0);
                itemstack1 = new ItemStack(this.item.getItem(), 1, this.item.getMetadata());
            }

            trades.add(new TradeOffer(itemstack, itemstack1));
        }
    }

    static class EnchantedItemForEmeralds implements VillagerEntity.TradeSource {
        public EnchantedItemForEmeralds() {
        }

        @Override
        public void provide(TradeOffers trades, Random random) {
            Enchantment enchantment = Enchantment.ALL[random.nextInt(Enchantment.ALL.length)];
            int i = MathHelper.nextInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel());
            ItemStack itemstack = Items.ENCHANTED_BOOK.withEnchantment(new EnchantmentEntry(enchantment, i));
            int j = 2 + random.nextInt(5 + i * 10) + 3 * i;
            if (j > 64) {
                j = 64;
            }

            trades.add(new TradeOffer(new ItemStack(Items.BOOK), new ItemStack(Items.EMERALD, j), itemstack));
        }
    }

    static class ItemsForEmeralds implements VillagerEntity.TradeSource {
        public ItemStack item;
        public VillagerEntity.RandomAmount emeralds;

        public ItemsForEmeralds(Item item, VillagerEntity.RandomAmount emeralds) {
            this.item = new ItemStack(item);
            this.emeralds = emeralds;
        }

        @Override
        public void provide(TradeOffers trades, Random random) {
            int i = 1;
            if (this.emeralds != null) {
                i = this.emeralds.pick(random);
            }

            ItemStack itemstack = new ItemStack(Items.EMERALD, i, 0);
            ItemStack itemstack1 = new ItemStack(this.item.getItem(), 1, this.item.getMetadata());
            itemstack1 = EnchantmentHelper.addRandomEnchantment(random, itemstack1, 5 + random.nextInt(15));
            trades.add(new TradeOffer(itemstack, itemstack1));
        }
    }

    static class ItemsForItemsAndEmeralds implements VillagerEntity.TradeSource {
        public ItemStack item;
        public VillagerEntity.RandomAmount cost;
        public ItemStack result;
        public VillagerEntity.RandomAmount amount;

        public ItemsForItemsAndEmeralds(Item item, VillagerEntity.RandomAmount cost, Item result, VillagerEntity.RandomAmount amount) {
            this.item = new ItemStack(item);
            this.cost = cost;
            this.result = new ItemStack(result);
            this.amount = amount;
        }

        @Override
        public void provide(TradeOffers trades, Random random) {
            int i = 1;
            if (this.cost != null) {
                i = this.cost.pick(random);
            }

            int j = 1;
            if (this.amount != null) {
                j = this.amount.pick(random);
            }

            trades.add(
                new TradeOffer(
                    new ItemStack(this.item.getItem(), i, this.item.getMetadata()),
                    new ItemStack(Items.EMERALD),
                    new ItemStack(this.result.getItem(), j, this.result.getMetadata())
                )
            );
        }
    }

    static class RandomAmount extends Pair<Integer, Integer> {
        public RandomAmount(int min, int max) {
            super(min, max);
        }

        public int pick(Random random) {
            return this.getLeft() >= this.getRight() ? this.getLeft() : this.getLeft() + random.nextInt(this.getRight() - this.getLeft() + 1);
        }
    }

    interface TradeSource {
        void provide(TradeOffers trades, Random random);
    }
}
