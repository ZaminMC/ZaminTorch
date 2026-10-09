package net.minecraft;

import com.mojang.authlib.GameProfile;
import java.io.PrintStream;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.dispenser.DispenseBehavior;
import net.minecraft.block.dispenser.DispenseItemBehavior;
import net.minecraft.block.dispenser.DispenseProjectileBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Dispensable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FireworksEntity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.DyeColor;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.stat.Stats;
import net.minecraft.text.StringUtils;
import net.minecraft.util.PrintStreamLogger;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.IPosition;
import net.minecraft.world.IBlockSource;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Bootstrap {
    private static final PrintStream SYSOUT = System.out;
    private static boolean initialized = false;
    private static final Logger LOGGER = LogManager.getLogger();

    public static boolean isInitialized() {
        return initialized;
    }

    static void registerDispenseBehaviors() {
        DispenserBlock.BEHAVIORS.put(Items.ARROW, new DispenseProjectileBehavior() {
            @Override
            protected Dispensable createProjectile(World world, IPosition pos) {
                ArrowEntity arrowentity = new ArrowEntity(world, pos.getX(), pos.getY(), pos.getZ());
                arrowentity.pickup = 1;
                return arrowentity;
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.EGG, new DispenseProjectileBehavior() {
            @Override
            protected Dispensable createProjectile(World world, IPosition pos) {
                return new EggEntity(world, pos.getX(), pos.getY(), pos.getZ());
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.SNOWBALL, new DispenseProjectileBehavior() {
            @Override
            protected Dispensable createProjectile(World world, IPosition pos) {
                return new SnowballEntity(world, pos.getX(), pos.getY(), pos.getZ());
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.EXPERIENCE_BOTTLE, new DispenseProjectileBehavior() {
            @Override
            protected Dispensable createProjectile(World world, IPosition pos) {
                return new ExperienceBottleEntity(world, pos.getX(), pos.getY(), pos.getZ());
            }

            @Override
            protected float getVariation() {
                return super.getVariation() * 0.5F;
            }

            @Override
            protected float getForce() {
                return super.getForce() * 1.25F;
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.POTION, new DispenseBehavior() {
            private final DispenseItemBehavior fallback = new DispenseItemBehavior();

            @Override
            public ItemStack dispense(IBlockSource source, ItemStack item) {
                return PotionItem.isSplashPotion(item.getMetadata()) ? (new DispenseProjectileBehavior() {
                    @Override
                    protected Dispensable createProjectile(World world, IPosition pos) {
                        return new PotionEntity(world, pos.getX(), pos.getY(), pos.getZ(), item.copy());
                    }

                    @Override
                    protected float getVariation() {
                        return super.getVariation() * 0.5F;
                    }

                    @Override
                    protected float getForce() {
                        return super.getForce() * 1.25F;
                    }
                }).dispense(source, item) : this.fallback.dispense(source, item);
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.SPAWN_EGG, new DispenseItemBehavior() {
            @Override
            public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
                double d0 = source.getX() + direction.getOffsetX();
                double d1 = source.getPos().getY() + 0.2F;
                double d2 = source.getZ() + direction.getOffsetZ();
                Entity entity = SpawnEggItem.spawnEntity(source.getWorld(), item.getMetadata(), d0, d1, d2);
                if (entity instanceof LivingEntity && item.hasCustomHoverName()) {
                    ((MobEntity)entity).setCustomName(item.getHoverName());
                }

                item.split(1);
                return item;
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.FIREWORKS, new DispenseItemBehavior() {
            @Override
            public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
                double d0 = source.getX() + direction.getOffsetX();
                double d1 = source.getPos().getY() + 0.2F;
                double d2 = source.getZ() + direction.getOffsetZ();
                FireworksEntity fireworksentity = new FireworksEntity(source.getWorld(), d0, d1, d2, item);
                source.getWorld().addEntity(fireworksentity);
                item.split(1);
                return item;
            }

            @Override
            protected void playSound(IBlockSource source) {
                source.getWorld().doEvent(1002, source.getPos(), 0);
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.FIRE_CHARGE, new DispenseItemBehavior() {
            @Override
            public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
                IPosition iposition = DispenserBlock.getDispensePos(source);
                double d0 = iposition.getX() + direction.getOffsetX() * 0.3F;
                double d1 = iposition.getY() + direction.getOffsetY() * 0.3F;
                double d2 = iposition.getZ() + direction.getOffsetZ() * 0.3F;
                World world = source.getWorld();
                Random random = world.random;
                double d3 = random.nextGaussian() * 0.05 + direction.getOffsetX();
                double d4 = random.nextGaussian() * 0.05 + direction.getOffsetY();
                double d5 = random.nextGaussian() * 0.05 + direction.getOffsetZ();
                world.addEntity(new SmallFireballEntity(world, d0, d1, d2, d3, d4, d5));
                item.split(1);
                return item;
            }

            @Override
            protected void playSound(IBlockSource source) {
                source.getWorld().doEvent(1009, source.getPos(), 0);
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.BOAT, new DispenseItemBehavior() {
            private final DispenseItemBehavior fallback = new DispenseItemBehavior();

            @Override
            public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
                World world = source.getWorld();
                double d0 = source.getX() + direction.getOffsetX() * 1.125F;
                double d1 = source.getY() + direction.getOffsetY() * 1.125F;
                double d2 = source.getZ() + direction.getOffsetZ() * 1.125F;
                BlockPos blockpos = source.getPos().offset(direction);
                Material material = world.getBlockState(blockpos).getBlock().getMaterial();
                double d3;
                if (Material.WATER.equals(material)) {
                    d3 = 1.0;
                } else {
                    if (!Material.AIR.equals(material) || !Material.WATER.equals(world.getBlockState(blockpos.down()).getBlock().getMaterial())) {
                        return this.fallback.dispense(source, item);
                    }

                    d3 = 0.0;
                }

                BoatEntity boatentity = new BoatEntity(world, d0, d1 + d3, d2);
                world.addEntity(boatentity);
                item.split(1);
                return item;
            }

            @Override
            protected void playSound(IBlockSource source) {
                source.getWorld().doEvent(1000, source.getPos(), 0);
            }
        });
        DispenseBehavior dispensebehavior = new DispenseItemBehavior() {
            private final DispenseItemBehavior fallback = new DispenseItemBehavior();

            @Override
            public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                BucketItem bucketitem = (BucketItem)item.getItem();
                BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
                if (bucketitem.place(source.getWorld(), blockpos)) {
                    item.setItem(Items.BUCKET);
                    item.size = 1;
                    return item;
                } else {
                    return this.fallback.dispense(source, item);
                }
            }
        };
        DispenserBlock.BEHAVIORS.put(Items.LAVA_BUCKET, dispensebehavior);
        DispenserBlock.BEHAVIORS.put(Items.WATER_BUCKET, dispensebehavior);
        DispenserBlock.BEHAVIORS.put(Items.BUCKET, new DispenseItemBehavior() {
            private final DispenseItemBehavior fallback = new DispenseItemBehavior();

            @Override
            public ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                World world = source.getWorld();
                BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
                BlockState blockstate = world.getBlockState(blockpos);
                Block block = blockstate.getBlock();
                Material material = block.getMaterial();
                Item itemx;
                if (Material.WATER.equals(material) && block instanceof LiquidBlock && blockstate.get(LiquidBlock.LEVEL) == 0) {
                    itemx = Items.WATER_BUCKET;
                } else {
                    if (!Material.LAVA.equals(material) || !(block instanceof LiquidBlock) || blockstate.get(LiquidBlock.LEVEL) != 0) {
                        return super.dispenseItem(source, item);
                    }

                    itemx = Items.LAVA_BUCKET;
                }

                world.removeBlock(blockpos);
                if (--item.size == 0) {
                    item.setItem(itemx);
                    item.size = 1;
                } else if (source.<DispenserBlockEntity>getBlockEntity().insertItem(new ItemStack(itemx)) < 0) {
                    this.fallback.dispense(source, new ItemStack(itemx));
                }

                return item;
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.FLINT_AND_STEEL, new DispenseItemBehavior() {
            private boolean ignited = true;

            @Override
            protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                World world = source.getWorld();
                BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
                if (world.isAir(blockpos)) {
                    world.setBlockState(blockpos, Blocks.FIRE.defaultState());
                    if (item.takeDamage(1, world.random)) {
                        item.size = 0;
                    }
                } else if (world.getBlockState(blockpos).getBlock() == Blocks.TNT) {
                    Blocks.TNT.onBroken(world, blockpos, Blocks.TNT.defaultState().set(TntBlock.EXPLODE, true));
                    world.removeBlock(blockpos);
                } else {
                    this.ignited = false;
                }

                return item;
            }

            @Override
            protected void playSound(IBlockSource source) {
                if (this.ignited) {
                    source.getWorld().doEvent(1000, source.getPos(), 0);
                } else {
                    source.getWorld().doEvent(1001, source.getPos(), 0);
                }
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.DYE, new DispenseItemBehavior() {
            private boolean dyed = true;

            @Override
            protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                if (DyeColor.WHITE == DyeColor.byMetadata(item.getMetadata())) {
                    World world = source.getWorld();
                    BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
                    if (DyeItem.fertilize(item, world, blockpos)) {
                        if (!world.isClient) {
                            world.doEvent(2005, blockpos, 0);
                        }
                    } else {
                        this.dyed = false;
                    }

                    return item;
                } else {
                    return super.dispenseItem(source, item);
                }
            }

            @Override
            protected void playSound(IBlockSource source) {
                if (this.dyed) {
                    source.getWorld().doEvent(1000, source.getPos(), 0);
                } else {
                    source.getWorld().doEvent(1001, source.getPos(), 0);
                }
            }
        });
        DispenserBlock.BEHAVIORS.put(Item.byBlock(Blocks.TNT), new DispenseItemBehavior() {
            @Override
            protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                World world = source.getWorld();
                BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
                PrimedTntEntity primedtntentity = new PrimedTntEntity(world, blockpos.getX() + 0.5, blockpos.getY(), blockpos.getZ() + 0.5, null);
                world.addEntity(primedtntentity);
                world.playSound(primedtntentity, "game.tnt.primed", 1.0F, 1.0F);
                item.size--;
                return item;
            }
        });
        DispenserBlock.BEHAVIORS.put(Items.SKULL, new DispenseItemBehavior() {
            private boolean spawnedWither = true;

            @Override
            protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                World world = source.getWorld();
                Direction direction = DispenserBlock.getDirection(source.getBlockMetadata());
                BlockPos blockpos = source.getPos().offset(direction);
                SkullBlock skullblock = Blocks.SKULL;
                if (!world.isAir(blockpos) || !skullblock.canSpawn(world, blockpos, item)) {
                    this.spawnedWither = false;
                } else if (!world.isClient) {
                    world.setBlockState(blockpos, skullblock.defaultState().set(SkullBlock.FACING, Direction.UP), 3);
                    BlockEntity blockentity = world.getBlockEntity(blockpos);
                    if (blockentity instanceof SkullBlockEntity) {
                        if (item.getMetadata() == 3) {
                            GameProfile gameprofile = null;
                            if (item.hasNbt()) {
                                NbtCompound nbtcompound = item.getNbt();
                                if (nbtcompound.contains("SkullOwner", 10)) {
                                    gameprofile = NbtUtils.readProfile(nbtcompound.getCompound("SkullOwner"));
                                } else if (nbtcompound.contains("SkullOwner", 8)) {
                                    String s = nbtcompound.getString("SkullOwner");
                                    if (!StringUtils.isStringEmpty(s)) {
                                        gameprofile = new GameProfile(null, s);
                                    }
                                }
                            }

                            ((SkullBlockEntity)blockentity).setProfile(gameprofile);
                        } else {
                            ((SkullBlockEntity)blockentity).setSkullType(item.getMetadata());
                        }

                        ((SkullBlockEntity)blockentity).setRotation(direction.getOpposite().getIdHorizontal() * 4);
                        Blocks.SKULL.trySpawn(world, blockpos, (SkullBlockEntity)blockentity);
                    }

                    item.size--;
                }

                return item;
            }

            @Override
            protected void playSound(IBlockSource source) {
                if (this.spawnedWither) {
                    source.getWorld().doEvent(1000, source.getPos(), 0);
                } else {
                    source.getWorld().doEvent(1001, source.getPos(), 0);
                }
            }
        });
        DispenserBlock.BEHAVIORS.put(Item.byBlock(Blocks.PUMPKIN), new DispenseItemBehavior() {
            private boolean spawnedGolem = true;

            @Override
            protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
                World world = source.getWorld();
                BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
                PumpkinBlock pumpkinblock = (PumpkinBlock)Blocks.PUMPKIN;
                if (world.isAir(blockpos) && pumpkinblock.canSpawnGolem(world, blockpos)) {
                    if (!world.isClient) {
                        world.setBlockState(blockpos, pumpkinblock.defaultState(), 3);
                    }

                    item.size--;
                } else {
                    this.spawnedGolem = false;
                }

                return item;
            }

            @Override
            protected void playSound(IBlockSource source) {
                if (this.spawnedGolem) {
                    source.getWorld().doEvent(1000, source.getPos(), 0);
                } else {
                    source.getWorld().doEvent(1001, source.getPos(), 0);
                }
            }
        });
    }

    public static void init() {
        if (!initialized) {
            initialized = true;
            if (LOGGER.isDebugEnabled()) {
                wrapPrintStreams();
            }

            Block.init();
            FireBlock.init();
            Item.init();
            Stats.init();
            registerDispenseBehaviors();
        }
    }

    private static void wrapPrintStreams() {
        System.setErr(new PrintStreamLogger("STDERR", System.err));
        System.setOut(new PrintStreamLogger("STDOUT", SYSOUT));
    }

    public static void sysout(String x) {
        SYSOUT.println(x);
    }
}
