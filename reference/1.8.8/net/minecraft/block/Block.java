package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.Stats;
import net.minecraft.util.CrudeIncrementalIntIdentityHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.DefaultedIdRegistry;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.explosion.Explosion;

public class Block {
    private static final Identifier AIR_KEY = new Identifier("air");
    public static final DefaultedIdRegistry<Identifier, Block> REGISTRY = new DefaultedIdRegistry<>(AIR_KEY);
    public static final CrudeIncrementalIntIdentityHashMap<BlockState> STATE_REGISTRY = new CrudeIncrementalIntIdentityHashMap<>();
    private CreativeModeTab creativeModeTab;
    public static final Block.Sounds DEFAULT_SOUNDS = new Block.Sounds("stone", 1.0F, 1.0F);
    public static final Block.Sounds WOOD_SOUNDS = new Block.Sounds("wood", 1.0F, 1.0F);
    public static final Block.Sounds GRAVEL_SOUNDS = new Block.Sounds("gravel", 1.0F, 1.0F);
    public static final Block.Sounds GRASS_SOUNDS = new Block.Sounds("grass", 1.0F, 1.0F);
    public static final Block.Sounds STONE_SOUNDS = new Block.Sounds("stone", 1.0F, 1.0F);
    public static final Block.Sounds METAL_SOUNDS = new Block.Sounds("stone", 1.0F, 1.5F);
    public static final Block.Sounds GLASS_SOUNDS = new Block.Sounds("stone", 1.0F, 1.0F) {
        @Override
        public String getBreaking() {
            return "dig.glass";
        }

        @Override
        public String getPlacing() {
            return "step.stone";
        }
    };
    public static final Block.Sounds CLOTH_SOUNDS = new Block.Sounds("cloth", 1.0F, 1.0F);
    public static final Block.Sounds SAND_SOUNDS = new Block.Sounds("sand", 1.0F, 1.0F);
    public static final Block.Sounds SNOW_SOUNDS = new Block.Sounds("snow", 1.0F, 1.0F);
    public static final Block.Sounds LADDER_SOUNDS = new Block.Sounds("ladder", 1.0F, 1.0F) {
        @Override
        public String getBreaking() {
            return "dig.wood";
        }
    };
    public static final Block.Sounds ANVIL_SOUNDS = new Block.Sounds("anvil", 0.3F, 1.0F) {
        @Override
        public String getBreaking() {
            return "dig.stone";
        }

        @Override
        public String getPlacing() {
            return "random.anvil_land";
        }
    };
    public static final Block.Sounds SLIME_SOUNDS = new Block.Sounds("slime", 1.0F, 1.0F) {
        @Override
        public String getBreaking() {
            return "mob.slime.big";
        }

        @Override
        public String getPlacing() {
            return "mob.slime.big";
        }

        @Override
        public String getStepping() {
            return "mob.slime.small";
        }
    };
    protected boolean opaqueCube;
    protected int opacity;
    protected boolean isTranslucent;
    protected int light;
    protected boolean useNeighborLight;
    protected float miningTime;
    protected float blastResistance;
    protected boolean stats = true;
    /**
     * Specifies whether this block accepts random ticks.
     * Note that while this field might be set to true the block might not do anything with the random tick.
     */
    protected boolean ticksRandomly;
    protected boolean hasBlockEntity;
    protected double minX;
    protected double minY;
    protected double minZ;
    protected double maxX;
    protected double maxY;
    protected double maxZ;
    public Block.Sounds sounds = DEFAULT_SOUNDS;
    public float gravity = 1.0F;
    protected final Material material;
    protected final MapColor mapColor;
    public float slipperiness = 0.6F;
    protected final StateDefinition stateDefinition;
    private BlockState defaultState;
    private String key;

    public static int getId(Block block) {
        return REGISTRY.getId(block);
    }

    /**
     * Converts the given block state into legacy block data.
     */
    public static int serialize(BlockState state) {
        Block block = state.getBlock();
        return getId(block) + (block.getMetadataFromState(state) << 12);
    }

    public static Block byId(int id) {
        return REGISTRY.get(id);
    }

    /**
     * Converts the given legacy block data into a block state.
     */
    public static BlockState deserialize(int blockdata) {
        int i = blockdata & 4095;
        int j = blockdata >> 12 & 15;
        return byId(i).getStateFromMetadata(j);
    }

    public static Block byItem(Item item) {
        return item instanceof BlockItem ? ((BlockItem)item).getBlock() : null;
    }

    public static Block byKey(String key) {
        Identifier identifier = new Identifier(key);
        if (REGISTRY.containsKey(identifier)) {
            return REGISTRY.get(identifier);
        }

        try {
            return REGISTRY.get(Integer.parseInt(key));
        } catch (NumberFormatException numberformatexception) {
            return null;
        }
    }

    /**
     * An opaque block is a full cube with no transparency.
     */
    public boolean isOpaque() {
        return this.opaqueCube;
    }

    public int getOpacity() {
        return this.opacity;
    }

    public boolean isTranslucent() {
        return this.isTranslucent;
    }

    public int getLight() {
        return this.light;
    }

    public boolean usesNeighborLight() {
        return this.useNeighborLight;
    }

    public Material getMaterial() {
        return this.material;
    }

    public MapColor getMapColor(BlockState state) {
        return this.mapColor;
    }

    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState();
    }

    public int getMetadataFromState(BlockState state) {
        if (state != null && !state.properties().isEmpty()) {
            throw new IllegalArgumentException("Don't know how to convert " + state + " back into data...");
        } else {
            return 0;
        }
    }

    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state;
    }

    public Block(Material material, MapColor mapColor) {
        this.material = material;
        this.mapColor = mapColor;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        this.opaqueCube = this.isSolidRender();
        this.opacity = this.isSolidRender() ? 255 : 0;
        this.isTranslucent = !material.isOpaque();
        this.stateDefinition = this.createStateDefinition();
        this.setDefaultState(this.stateDefinition.any());
    }

    protected Block(Material material) {
        this(material, material.getColor());
    }

    /**
     * Sets this block's sound set.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setSounds(Block.Sounds sounds) {
        this.sounds = sounds;
        return this;
    }

    /**
     * Sets this block's light opacity.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setOpacity(int opacity) {
        this.opacity = opacity;
        return this;
    }

    /**
     * Sets this block's light level.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setLight(float light) {
        this.light = (int)(15.0F * light);
        return this;
    }

    /**
     * Sets this block's resistance.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setBlastResistance(float blastResistance) {
        this.blastResistance = blastResistance * 3.0F;
        return this;
    }

    public boolean blocksAmbientLight() {
        return this.material.blocksMovement() && this.isCube();
    }

    public boolean isSolid() {
        return this.material.isSolidBlocking() && this.isCube() && !this.isSignalSource();
    }

    public boolean isViewBlocking() {
        return this.material.blocksMovement() && this.isCube();
    }

    public boolean isCube() {
        return true;
    }

    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return !this.material.blocksMovement();
    }

    /**
     * Returns this block's render type. The possible values are as follows:
     * <br>-1: none
     * <br>1: liquid
     * <br>2: animated block entity
     * <br>3: block model
     */
    public int getRenderType() {
        return 3;
    }

    public boolean canBeReplaced(World world, BlockPos pos) {
        return false;
    }

    /**
     * Sets this block's mining speed and updates its blast resistance if needed.
     * A block's blast resistance cannot be less than 5 times its mining speed.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setStrength(float strength) {
        this.miningTime = strength;
        if (this.blastResistance < strength * 5.0F) {
            this.blastResistance = strength * 5.0F;
        }

        return this;
    }

    /**
     * Sets this block as unbreakable by setting its strength to -1. Note that this changes both
     * the block's mining speed and its blast resistance.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setUnbreakable() {
        this.setStrength(-1.0F);
        return this;
    }

    public float getMiningTime(World world, BlockPos pos) {
        return this.miningTime;
    }

    /**
     * Sets whether this block accepts random ticks. Random ticks are mostly used by plants
     * and crops to update growth, but some other blocks are affected by random ticks as well,
     * like e.g. leaves (to update decay).
     * Note that while this field might be set to true the block might not do anything with the random tick.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block setTicksRandomly(boolean ticksRandomly) {
        this.ticksRandomly = ticksRandomly;
        return this;
    }

    /**
     * Returns whether this block accepts random ticks. Random ticks are mostly used by plants
     * and crops to update growth, but some other blocks are affected by random ticks as well,
     * like e.g. leaves (to update decay).
     * Note that while this field might be set to true the block might not do anything with the random tick.
     */
    public boolean ticksRandomly() {
        return this.ticksRandomly;
    }

    /**
     * Returns whether this block has a block entity associated with it.
     */
    public boolean hasBlockEntity() {
        return this.hasBlockEntity;
    }

    protected final void setShape(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public int getLightColor(WorldView world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        int i = world.getLightColor(pos, block.getLight());
        if (i == 0 && block instanceof SlabBlock) {
            pos = pos.down();
            block = world.getBlockState(pos).getBlock();
            return world.getLightColor(pos, block.getLight());
        } else {
            return i;
        }
    }

    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return face == Direction.DOWN && this.minY > 0.0
            || face == Direction.UP && this.maxY < 1.0
            || face == Direction.NORTH && this.minZ > 0.0
            || face == Direction.SOUTH && this.maxZ < 1.0
            || face == Direction.WEST && this.minX > 0.0
            || face == Direction.EAST && this.maxX < 1.0
            || !world.getBlockState(pos).getBlock().isSolidRender();
    }

    public boolean isFaceSolid(WorldView world, BlockPos pos, Direction face) {
        return world.getBlockState(pos).getBlock().getMaterial().isSolid();
    }

    public Box getOutlineShape(World world, BlockPos pos) {
        return new Box(
            pos.getX() + this.minX, pos.getY() + this.minY, pos.getZ() + this.minZ, pos.getX() + this.maxX, pos.getY() + this.maxY, pos.getZ() + this.maxZ
        );
    }

    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        Box box = this.getCollisionShape(world, pos, state);
        if (box != null && shape.intersects(box)) {
            collisions.add(box);
        }
    }

    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return new Box(
            pos.getX() + this.minX, pos.getY() + this.minY, pos.getZ() + this.minZ, pos.getX() + this.maxX, pos.getY() + this.maxY, pos.getZ() + this.maxZ
        );
    }

    public boolean isSolidRender() {
        return true;
    }

    public boolean canRayTrace(BlockState state, boolean allowLiquids) {
        return this.canRayTrace();
    }

    public boolean canRayTrace() {
        return true;
    }

    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
        this.tick(world, pos, state, random);
    }

    /**
     * Performs a scheduled tick. Blocks that want to perform some action after a delay can
     * tell the world to schedule the update, which is then executed by calling this method.
     */
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
    }

    /**
     * Performs a random display tick. Random display ticks are mostly used to spawn particles
     * around a block. The client does 1000 random display tick attempts within a radius of
     * 16 blocks of the player each tick.
     */
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    public void onBroken(World world, BlockPos pos, BlockState state) {
    }

    /**
     * Performs a block update. This method is called when a neighboring block has updated
     * (i.e. placed, broken, or otherwise changed state).
     */
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
    }

    public int getTickRate(World world) {
        return 10;
    }

    public void onAdded(World world, BlockPos pos, BlockState state) {
    }

    public void onRemoved(World world, BlockPos pos, BlockState state) {
    }

    public int getBaseDropCount(Random random) {
        return 1;
    }

    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(this);
    }

    /**
     * Calculates the mining speed of this block, taking into consideration the given
     * player's status effects such as haste and mining fatigue, in progress per tick.
     */
    public float getMiningSpeed(PlayerEntity player, World world, BlockPos pos) {
        float f = this.getMiningTime(world, pos);
        if (f < 0.0F) {
            return 0.0F;
        } else {
            return !player.canMineBlock(this) ? player.getMiningSpeed(this) / f / 100.0F : player.getMiningSpeed(this) / f / 30.0F;
        }
    }

    public final void dropItems(World world, BlockPos pos, BlockState state, int fortuneLevel) {
        this.dropItems(world, pos, state, 1.0F, fortuneLevel);
    }

    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        if (!world.isClient) {
            int i = this.getDropCount(fortuneLevel, world.random);

            for (int j = 0; j < i; j++) {
                if (!(world.random.nextFloat() > luck)) {
                    Item item = this.getDropItem(state, world.random, fortuneLevel);
                    if (item != null) {
                        dropItem(world, pos, new ItemStack(item, 1, this.getDropItemMetadata(state)));
                    }
                }
            }
        }
    }

    public static void dropItem(World world, BlockPos pos, ItemStack item) {
        if (!world.isClient && world.getGameRules().getBoolean("doTileDrops")) {
            float f = 0.5F;
            double d0 = world.random.nextFloat() * f + (1.0F - f) * 0.5;
            double d1 = world.random.nextFloat() * f + (1.0F - f) * 0.5;
            double d2 = world.random.nextFloat() * f + (1.0F - f) * 0.5;
            ItemEntity itementity = new ItemEntity(world, pos.getX() + d0, pos.getY() + d1, pos.getZ() + d2, item);
            itementity.setDefaultPickUpDelay();
            world.addEntity(itementity);
        }
    }

    protected void dropXp(World world, BlockPos pos, int size) {
        if (!world.isClient) {
            while (size > 0) {
                int i = ExperienceOrbEntity.roundSize(size);
                size -= i;
                world.addEntity(new ExperienceOrbEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, i));
            }
        }
    }

    public int getDropItemMetadata(BlockState state) {
        return 0;
    }

    /**
     * Returns this block's blast resistance for the given entity.
     */
    public float getBlastResistance(Entity entity) {
        return this.blastResistance / 5.0F;
    }

    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        this.updateShape(world, pos);
        start = start.add(-pos.getX(), -pos.getY(), -pos.getZ());
        end = end.add(-pos.getX(), -pos.getY(), -pos.getZ());
        Vec3d vec3d = start.intermediateWithX(end, this.minX);
        Vec3d vec3d1 = start.intermediateWithX(end, this.maxX);
        Vec3d vec3d2 = start.intermediateWithY(end, this.minY);
        Vec3d vec3d3 = start.intermediateWithY(end, this.maxY);
        Vec3d vec3d4 = start.intermediateWithZ(end, this.minZ);
        Vec3d vec3d5 = start.intermediateWithZ(end, this.maxZ);
        if (!this.containsX(vec3d)) {
            vec3d = null;
        }

        if (!this.containsX(vec3d1)) {
            vec3d1 = null;
        }

        if (!this.containsY(vec3d2)) {
            vec3d2 = null;
        }

        if (!this.containsY(vec3d3)) {
            vec3d3 = null;
        }

        if (!this.containsZ(vec3d4)) {
            vec3d4 = null;
        }

        if (!this.containsZ(vec3d5)) {
            vec3d5 = null;
        }

        Vec3d vec3d6 = null;
        if (vec3d != null && (vec3d6 == null || start.squaredDistanceTo(vec3d) < start.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d;
        }

        if (vec3d1 != null && (vec3d6 == null || start.squaredDistanceTo(vec3d1) < start.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d1;
        }

        if (vec3d2 != null && (vec3d6 == null || start.squaredDistanceTo(vec3d2) < start.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d2;
        }

        if (vec3d3 != null && (vec3d6 == null || start.squaredDistanceTo(vec3d3) < start.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d3;
        }

        if (vec3d4 != null && (vec3d6 == null || start.squaredDistanceTo(vec3d4) < start.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d4;
        }

        if (vec3d5 != null && (vec3d6 == null || start.squaredDistanceTo(vec3d5) < start.squaredDistanceTo(vec3d6))) {
            vec3d6 = vec3d5;
        }

        if (vec3d6 == null) {
            return null;
        }

        Direction direction = null;
        if (vec3d6 == vec3d) {
            direction = Direction.WEST;
        }

        if (vec3d6 == vec3d1) {
            direction = Direction.EAST;
        }

        if (vec3d6 == vec3d2) {
            direction = Direction.DOWN;
        }

        if (vec3d6 == vec3d3) {
            direction = Direction.UP;
        }

        if (vec3d6 == vec3d4) {
            direction = Direction.NORTH;
        }

        if (vec3d6 == vec3d5) {
            direction = Direction.SOUTH;
        }

        return new HitResult(vec3d6.add(pos.getX(), pos.getY(), pos.getZ()), direction, pos);
    }

    private boolean containsX(Vec3d pos) {
        return pos != null && pos.y >= this.minY && pos.y <= this.maxY && pos.z >= this.minZ && pos.z <= this.maxZ;
    }

    private boolean containsY(Vec3d pos) {
        return pos != null && pos.x >= this.minX && pos.x <= this.maxX && pos.z >= this.minZ && pos.z <= this.maxZ;
    }

    private boolean containsZ(Vec3d pos) {
        return pos != null && pos.x >= this.minX && pos.x <= this.maxX && pos.y >= this.minY && pos.y <= this.maxY;
    }

    public void onExploded(World world, BlockPos pos, Explosion explosion) {
    }

    public BlockLayer getRenderLayer() {
        return BlockLayer.SOLID;
    }

    public boolean canBePlaced(World world, BlockPos pos, Direction face, ItemStack item) {
        return this.canBePlaced(world, pos, face);
    }

    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        return this.canBePlaced(world, pos);
    }

    public boolean canBePlaced(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().material.isReplaceable();
    }

    /**
     * Handles a player interacting with this block, i.e. a player holding the <i>use</i> key
     * while looking at this block.
     * 
     * @return whether the interaction was consumed,
     * i.e. whether further processing of the use action should be blocked.
     */
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        return false;
    }

    public void onSteppedOn(World world, BlockPos pos, Entity entity) {
    }

    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.getStateFromMetadata(metadata);
    }

    public void startMining(World world, BlockPos pos, PlayerEntity player) {
    }

    public Vec3d applyMaterialDrag(World world, BlockPos pos, Entity entity, Vec3d velocity) {
        return velocity;
    }

    public void updateShape(WorldView world, BlockPos pos) {
    }

    public final double getMinX() {
        return this.minX;
    }

    public final double getMaxX() {
        return this.maxX;
    }

    public final double getMinY() {
        return this.minY;
    }

    public final double getMaxY() {
        return this.maxY;
    }

    public final double getMinZ() {
        return this.minZ;
    }

    public final double getMaxZ() {
        return this.maxZ;
    }

    public int getColor() {
        return 16777215;
    }

    public int getColor(BlockState state) {
        return 16777215;
    }

    public int getColor(WorldView world, BlockPos pos, int tint) {
        return 16777215;
    }

    public final int getColor(WorldView world, BlockPos pos) {
        return this.getColor(world, pos, 0);
    }

    /**
     * Returns the redstone signal this block is emitting in the given direction.
     * This roughly equates to what is colloquially known as 'soft' or 'weak' power.
     * 
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so blocks should
     * check for the signal emitted in the direction <i>opposite</i> of the one given.
     */
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return 0;
    }

    /**
     * Returns whether this block is capable of emitting a redstone signal.
     */
    public boolean isSignalSource() {
        return false;
    }

    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
    }

    /**
     * Returns the direct redstone signal this block is emitting in the given direction.
     * This roughly equates to what is colloquially known as 'hard' or 'strong' power.
     * 
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so blocks should
     * check for the signal emitted in the direction <i>opposite</i> of the one given.
     */
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return 0;
    }

    public void resetShape() {
    }

    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        player.incrementStat(Stats.BLOCKS_MINED[getId(this)]);
        player.addFatigue(0.025F);
        if (this.hasSilkTouchDrops() && EnchantmentHelper.hasSilkTouch(player)) {
            ItemStack itemstack = this.getSilkTouchDrop(state);
            if (itemstack != null) {
                dropItem(world, pos, itemstack);
            }
        } else {
            int i = EnchantmentHelper.getFortuneLevel(player);
            this.dropItems(world, pos, state, i);
        }
    }

    protected boolean hasSilkTouchDrops() {
        return this.isCube() && !this.hasBlockEntity;
    }

    protected ItemStack getSilkTouchDrop(BlockState state) {
        int i = 0;
        Item item = Item.byBlock(this);
        if (item != null && item.hasCustomData()) {
            i = this.getMetadataFromState(state);
        }

        return new ItemStack(item, 1, i);
    }

    public int getDropCount(int fortuneLevel, Random random) {
        return this.getBaseDropCount(random);
    }

    /**
     * Called when this block is placed by an entity.
     */
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
    }

    /**
     * Returns whether players can respawn inside this block.
     */
    public boolean canRespawnIn() {
        return !this.material.isSolid() && !this.material.isLiquid();
    }

    /**
     * Sets this block's translation and registry key.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    public Block setKey(String key) {
        this.key = key;
        return this;
    }

    public String getName() {
        return I18n.translate(this.getTranslationKey() + ".name");
    }

    public String getTranslationKey() {
        return "tile." + this.key;
    }

    /**
     * Performs a block event. Block events are queued on the server, and executed once per
     * tick. Successful block events are synced with clients that are within range. Block events
     * are most notably used by pistons to handle extension and retraction, but note blocks
     * also use them to play sounds.
     * 
     * @return whether the block event was successful.
     */
    public boolean doEvent(World world, BlockPos pos, BlockState state, int type, int data) {
        return false;
    }

    public boolean hasStats() {
        return this.stats;
    }

    /**
     * Disables tracking by stats for this block.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    protected Block disableStats() {
        this.stats = false;
        return this;
    }

    /**
     * Returns how this block interacts with pistons. The following values are accepted:
     * <br>- 0: this block can be pushed and pulled by pistons.
     * <br>- 1: this block is broken when pushed.
     * <br>- 2: this block cannot be pushed or pulled by pistons.
     */
    public int getPistonMoveBehavior() {
        return this.material.getPistonMoveBehavior();
    }

    public float getAmbientOcclusionLight() {
        return this.blocksAmbientLight() ? 0.2F : 1.0F;
    }

    public void onFallenOn(World world, BlockPos pos, Entity entity, float fallDistance) {
        entity.takeFallDamage(fallDistance, 1.0F);
    }

    public void beforeCollision(World world, Entity entity) {
        entity.velocityY = 0.0;
    }

    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(this);
    }

    public int getPickItemMetadata(World world, BlockPos pos) {
        return this.getDropItemMetadata(world.getBlockState(pos));
    }

    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, 0));
    }

    public CreativeModeTab getCreativeModeTab() {
        return this.creativeModeTab;
    }

    /**
     * Sets this block's creative inventory tab.
     * 
     * <p>
     * NOTE: this method should only be used during the block's creation before it is registered.
     */
    public Block setCreativeModeTab(CreativeModeTab tab) {
        this.creativeModeTab = tab;
        return this;
    }

    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
    }

    public void randomPrecipitationTick(World world, BlockPos pos) {
    }

    public boolean hasPickItemMetadata() {
        return false;
    }

    /**
     * Returns whether this block accepts ticks when the world is executing them
     * immediately (i.e. without scheduling them first).
     */
    public boolean acceptsImmediateTicks() {
        return true;
    }

    public boolean shouldDropItemsOnExplosion(Explosion explosion) {
        return true;
    }

    public boolean is(Block block) {
        return this == block;
    }

    public static boolean areEqual(Block block1, Block block2) {
        return block1 != null && block2 != null && (block1 == block2 || block1.is(block2));
    }

    /**
     * Returns whether this block is capable of emitting an analog signal,
     * i.e. whether it has contents that can be read by a comparator.
     */
    public boolean isAnalogSignalSource() {
        return false;
    }

    /**
     * Returns the analog signal emitted by this block, i.e. the signal that should be outputted
     * by a comparator reading the contents of this block.
     */
    public int getAnalogSignal(World world, BlockPos pos) {
        return 0;
    }

    public BlockState getStateForRendering(BlockState state) {
        return state;
    }

    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this);
    }

    public StateDefinition stateDefinition() {
        return this.stateDefinition;
    }

    protected final void setDefaultState(BlockState state) {
        this.defaultState = state;
    }

    public final BlockState defaultState() {
        return this.defaultState;
    }

    public Block.OffsetType getOffsetType() {
        return Block.OffsetType.NONE;
    }

    @Override
    public String toString() {
        return "Block{" + REGISTRY.getKey(this) + "}";
    }

    /**
     * Registers all blocks.
     */
    public static void init() {
        register(0, AIR_KEY, new AirBlock().setKey("air"));
        register(1, "stone", new StoneBlock().setStrength(1.5F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("stone"));
        register(2, "grass", new GrassBlock().setStrength(0.6F).setSounds(GRASS_SOUNDS).setKey("grass"));
        register(3, "dirt", new DirtBlock().setStrength(0.5F).setSounds(GRAVEL_SOUNDS).setKey("dirt"));
        Block block = new Block(Material.STONE)
            .setStrength(2.0F)
            .setBlastResistance(10.0F)
            .setSounds(STONE_SOUNDS)
            .setKey("stonebrick")
            .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
        register(4, "cobblestone", block);
        Block block1 = new PlanksBlock().setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("wood");
        register(5, "planks", block1);
        register(6, "sapling", new SaplingBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("sapling"));
        register(
            7,
            "bedrock",
            new Block(Material.STONE)
                .setUnbreakable()
                .setBlastResistance(6000000.0F)
                .setSounds(STONE_SOUNDS)
                .setKey("bedrock")
                .disableStats()
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(8, "flowing_water", new FlowingLiquidBlock(Material.WATER).setStrength(100.0F).setOpacity(3).setKey("water").disableStats());
        register(9, "water", new LiquidSourceBlock(Material.WATER).setStrength(100.0F).setOpacity(3).setKey("water").disableStats());
        register(10, "flowing_lava", new FlowingLiquidBlock(Material.LAVA).setStrength(100.0F).setLight(1.0F).setKey("lava").disableStats());
        register(11, "lava", new LiquidSourceBlock(Material.LAVA).setStrength(100.0F).setLight(1.0F).setKey("lava").disableStats());
        register(12, "sand", new SandBlock().setStrength(0.5F).setSounds(SAND_SOUNDS).setKey("sand"));
        register(13, "gravel", new GravelBlock().setStrength(0.6F).setSounds(GRAVEL_SOUNDS).setKey("gravel"));
        register(14, "gold_ore", new OreBlock().setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreGold"));
        register(15, "iron_ore", new OreBlock().setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreIron"));
        register(16, "coal_ore", new OreBlock().setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreCoal"));
        register(17, "log", new LogBlock().setKey("log"));
        register(18, "leaves", new LeavesBlock().setKey("leaves"));
        register(19, "sponge", new SpongeBlock().setStrength(0.6F).setSounds(GRASS_SOUNDS).setKey("sponge"));
        register(20, "glass", new GlassBlock(Material.GLASS, false).setStrength(0.3F).setSounds(GLASS_SOUNDS).setKey("glass"));
        register(21, "lapis_ore", new OreBlock().setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreLapis"));
        register(
            22,
            "lapis_block",
            new Block(Material.IRON, MapColor.LAPIS)
                .setStrength(3.0F)
                .setBlastResistance(5.0F)
                .setSounds(STONE_SOUNDS)
                .setKey("blockLapis")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(23, "dispenser", new DispenserBlock().setStrength(3.5F).setSounds(STONE_SOUNDS).setKey("dispenser"));
        Block block2 = new SandstoneBlock().setSounds(STONE_SOUNDS).setStrength(0.8F).setKey("sandStone");
        register(24, "sandstone", block2);
        register(25, "noteblock", new NoteBlock().setStrength(0.8F).setKey("musicBlock"));
        register(26, "bed", new BedBlock().setSounds(WOOD_SOUNDS).setStrength(0.2F).setKey("bed").disableStats());
        register(27, "golden_rail", new PoweredRailBlock().setStrength(0.7F).setSounds(METAL_SOUNDS).setKey("goldenRail"));
        register(28, "detector_rail", new DetectorRailBlock().setStrength(0.7F).setSounds(METAL_SOUNDS).setKey("detectorRail"));
        register(29, "sticky_piston", new PistonBaseBlock(true).setKey("pistonStickyBase"));
        register(30, "web", new CobwebBlock().setOpacity(1).setStrength(4.0F).setKey("web"));
        register(31, "tallgrass", new TallPlantBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("tallgrass"));
        register(32, "deadbush", new DeadBushBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("deadbush"));
        register(33, "piston", new PistonBaseBlock(false).setKey("pistonBase"));
        register(34, "piston_head", new PistonHeadBlock().setKey("pistonBase"));
        register(35, "wool", new ColoredBlock(Material.WOOL).setStrength(0.8F).setSounds(CLOTH_SOUNDS).setKey("cloth"));
        register(36, "piston_extension", new MovingBlock());
        register(37, "yellow_flower", new YellowFlowerBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("flower1"));
        register(38, "red_flower", new RedFlowerBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("flower2"));
        Block block3 = new MushroomPlantBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setLight(0.125F).setKey("mushroom");
        register(39, "brown_mushroom", block3);
        Block block4 = new MushroomPlantBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("mushroom");
        register(40, "red_mushroom", block4);
        register(
            41,
            "gold_block",
            new Block(Material.IRON, MapColor.GOLD)
                .setStrength(3.0F)
                .setBlastResistance(10.0F)
                .setSounds(METAL_SOUNDS)
                .setKey("blockGold")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(
            42,
            "iron_block",
            new Block(Material.IRON, MapColor.IRON)
                .setStrength(5.0F)
                .setBlastResistance(10.0F)
                .setSounds(METAL_SOUNDS)
                .setKey("blockIron")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(43, "double_stone_slab", new DoubleStoneSlabBlock().setStrength(2.0F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("stoneSlab"));
        register(44, "stone_slab", new SingleStoneSlabBlock().setStrength(2.0F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("stoneSlab"));
        Block block5 = new Block(Material.STONE, MapColor.RED)
            .setStrength(2.0F)
            .setBlastResistance(10.0F)
            .setSounds(STONE_SOUNDS)
            .setKey("brick")
            .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
        register(45, "brick_block", block5);
        register(46, "tnt", new TntBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("tnt"));
        register(47, "bookshelf", new BookshelfBlock().setStrength(1.5F).setSounds(WOOD_SOUNDS).setKey("bookshelf"));
        register(
            48,
            "mossy_cobblestone",
            new Block(Material.STONE)
                .setStrength(2.0F)
                .setBlastResistance(10.0F)
                .setSounds(STONE_SOUNDS)
                .setKey("stoneMoss")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(49, "obsidian", new ObsidianBlock().setStrength(50.0F).setBlastResistance(2000.0F).setSounds(STONE_SOUNDS).setKey("obsidian"));
        register(50, "torch", new TorchBlock().setStrength(0.0F).setLight(0.9375F).setSounds(WOOD_SOUNDS).setKey("torch"));
        register(51, "fire", new FireBlock().setStrength(0.0F).setLight(1.0F).setSounds(CLOTH_SOUNDS).setKey("fire").disableStats());
        register(52, "mob_spawner", new MobSpawnerBlock().setStrength(5.0F).setSounds(METAL_SOUNDS).setKey("mobSpawner").disableStats());
        register(53, "oak_stairs", new StairsBlock(block1.defaultState().set(PlanksBlock.VARIANT, PlanksBlock.Variant.OAK)).setKey("stairsWood"));
        register(54, "chest", new ChestBlock(0).setStrength(2.5F).setSounds(WOOD_SOUNDS).setKey("chest"));
        register(55, "redstone_wire", new RedstoneWireBlock().setStrength(0.0F).setSounds(DEFAULT_SOUNDS).setKey("redstoneDust").disableStats());
        register(56, "diamond_ore", new OreBlock().setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreDiamond"));
        register(
            57,
            "diamond_block",
            new Block(Material.IRON, MapColor.DIAMOND)
                .setStrength(5.0F)
                .setBlastResistance(10.0F)
                .setSounds(METAL_SOUNDS)
                .setKey("blockDiamond")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(58, "crafting_table", new CraftingTableBlock().setStrength(2.5F).setSounds(WOOD_SOUNDS).setKey("workbench"));
        register(59, "wheat", new WheatBlock().setKey("crops"));
        Block block6 = new FarmlandBlock().setStrength(0.6F).setSounds(GRAVEL_SOUNDS).setKey("farmland");
        register(60, "farmland", block6);
        register(
            61, "furnace", new FurnaceBlock(false).setStrength(3.5F).setSounds(STONE_SOUNDS).setKey("furnace").setCreativeModeTab(CreativeModeTab.DECORATIONS)
        );
        register(62, "lit_furnace", new FurnaceBlock(true).setStrength(3.5F).setSounds(STONE_SOUNDS).setLight(0.875F).setKey("furnace"));
        register(63, "standing_sign", new StandingSignBlock().setStrength(1.0F).setSounds(WOOD_SOUNDS).setKey("sign").disableStats());
        register(64, "wooden_door", new DoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("doorOak").disableStats());
        register(65, "ladder", new LadderBlock().setStrength(0.4F).setSounds(LADDER_SOUNDS).setKey("ladder"));
        register(66, "rail", new RailBlock().setStrength(0.7F).setSounds(METAL_SOUNDS).setKey("rail"));
        register(67, "stone_stairs", new StairsBlock(block.defaultState()).setKey("stairsStone"));
        register(68, "wall_sign", new WallSignBlock().setStrength(1.0F).setSounds(WOOD_SOUNDS).setKey("sign").disableStats());
        register(69, "lever", new LeverBlock().setStrength(0.5F).setSounds(WOOD_SOUNDS).setKey("lever"));
        register(
            70,
            "stone_pressure_plate",
            new PressurePlateBlock(Material.STONE, PressurePlateBlock.ActivationRule.MOBS)
                .setStrength(0.5F)
                .setSounds(STONE_SOUNDS)
                .setKey("pressurePlateStone")
        );
        register(71, "iron_door", new DoorBlock(Material.IRON).setStrength(5.0F).setSounds(METAL_SOUNDS).setKey("doorIron").disableStats());
        register(
            72,
            "wooden_pressure_plate",
            new PressurePlateBlock(Material.WOOD, PressurePlateBlock.ActivationRule.EVERYTHING)
                .setStrength(0.5F)
                .setSounds(WOOD_SOUNDS)
                .setKey("pressurePlateWood")
        );
        register(
            73,
            "redstone_ore",
            new RedstoneOreBlock(false)
                .setStrength(3.0F)
                .setBlastResistance(5.0F)
                .setSounds(STONE_SOUNDS)
                .setKey("oreRedstone")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(
            74,
            "lit_redstone_ore",
            new RedstoneOreBlock(true).setLight(0.625F).setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreRedstone")
        );
        register(75, "unlit_redstone_torch", new RedstoneTorchBlock(false).setStrength(0.0F).setSounds(WOOD_SOUNDS).setKey("notGate"));
        register(
            76,
            "redstone_torch",
            new RedstoneTorchBlock(true).setStrength(0.0F).setLight(0.5F).setSounds(WOOD_SOUNDS).setKey("notGate").setCreativeModeTab(CreativeModeTab.REDSTONE)
        );
        register(77, "stone_button", new StoneButtonBlock().setStrength(0.5F).setSounds(STONE_SOUNDS).setKey("button"));
        register(78, "snow_layer", new SnowLayerBlock().setStrength(0.1F).setSounds(SNOW_SOUNDS).setKey("snow").setOpacity(0));
        register(79, "ice", new IceBlock().setStrength(0.5F).setOpacity(3).setSounds(GLASS_SOUNDS).setKey("ice"));
        register(80, "snow", new SnowBlock().setStrength(0.2F).setSounds(SNOW_SOUNDS).setKey("snow"));
        register(81, "cactus", new CactusBlock().setStrength(0.4F).setSounds(CLOTH_SOUNDS).setKey("cactus"));
        register(82, "clay", new ClayBlock().setStrength(0.6F).setSounds(GRAVEL_SOUNDS).setKey("clay"));
        register(83, "reeds", new SugarCaneBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("reeds").disableStats());
        register(84, "jukebox", new JukeboxBlock().setStrength(2.0F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("jukebox"));
        register(
            85,
            "fence",
            new FenceBlock(Material.WOOD, PlanksBlock.Variant.OAK.getColor()).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("fence")
        );
        Block block7 = new PumpkinBlock().setStrength(1.0F).setSounds(WOOD_SOUNDS).setKey("pumpkin");
        register(86, "pumpkin", block7);
        register(87, "netherrack", new NetherrackBlock().setStrength(0.4F).setSounds(STONE_SOUNDS).setKey("hellrock"));
        register(88, "soul_sand", new SoulSandBlock().setStrength(0.5F).setSounds(SAND_SOUNDS).setKey("hellsand"));
        register(89, "glowstone", new GlowstoneBlock(Material.GLASS).setStrength(0.3F).setSounds(GLASS_SOUNDS).setLight(1.0F).setKey("lightgem"));
        register(90, "portal", new PortalBlock().setStrength(-1.0F).setSounds(GLASS_SOUNDS).setLight(0.75F).setKey("portal"));
        register(91, "lit_pumpkin", new PumpkinBlock().setStrength(1.0F).setSounds(WOOD_SOUNDS).setLight(1.0F).setKey("litpumpkin"));
        register(92, "cake", new CakeBlock().setStrength(0.5F).setSounds(CLOTH_SOUNDS).setKey("cake").disableStats());
        register(93, "unpowered_repeater", new RepeaterBlock(false).setStrength(0.0F).setSounds(WOOD_SOUNDS).setKey("diode").disableStats());
        register(94, "powered_repeater", new RepeaterBlock(true).setStrength(0.0F).setSounds(WOOD_SOUNDS).setKey("diode").disableStats());
        register(95, "stained_glass", new StainedGlassBlock(Material.GLASS).setStrength(0.3F).setSounds(GLASS_SOUNDS).setKey("stainedGlass"));
        register(96, "trapdoor", new TrapdoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("trapdoor").disableStats());
        register(97, "monster_egg", new InfestedBlock().setStrength(0.75F).setKey("monsterStoneEgg"));
        Block block8 = new StonebrickBlock().setStrength(1.5F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("stonebricksmooth");
        register(98, "stonebrick", block8);
        register(
            99, "brown_mushroom_block", new MushroomBlock(Material.WOOD, MapColor.DIRT, block3).setStrength(0.2F).setSounds(WOOD_SOUNDS).setKey("mushroom")
        );
        register(100, "red_mushroom_block", new MushroomBlock(Material.WOOD, MapColor.RED, block4).setStrength(0.2F).setSounds(WOOD_SOUNDS).setKey("mushroom"));
        register(101, "iron_bars", new PaneBlock(Material.IRON, true).setStrength(5.0F).setBlastResistance(10.0F).setSounds(METAL_SOUNDS).setKey("fenceIron"));
        register(102, "glass_pane", new PaneBlock(Material.GLASS, false).setStrength(0.3F).setSounds(GLASS_SOUNDS).setKey("thinGlass"));
        Block block9 = new MelonBlock().setStrength(1.0F).setSounds(WOOD_SOUNDS).setKey("melon");
        register(103, "melon_block", block9);
        register(104, "pumpkin_stem", new StemBlock(block7).setStrength(0.0F).setSounds(WOOD_SOUNDS).setKey("pumpkinStem"));
        register(105, "melon_stem", new StemBlock(block9).setStrength(0.0F).setSounds(WOOD_SOUNDS).setKey("pumpkinStem"));
        register(106, "vine", new VineBlock().setStrength(0.2F).setSounds(GRASS_SOUNDS).setKey("vine"));
        register(
            107,
            "fence_gate",
            new FenceGateBlock(PlanksBlock.Variant.OAK).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("fenceGate")
        );
        register(108, "brick_stairs", new StairsBlock(block5.defaultState()).setKey("stairsBrick"));
        register(
            109,
            "stone_brick_stairs",
            new StairsBlock(block8.defaultState().set(StonebrickBlock.VARIANT, StonebrickBlock.Variant.DEFAULT)).setKey("stairsStoneBrickSmooth")
        );
        register(110, "mycelium", new MyceliumBlock().setStrength(0.6F).setSounds(GRASS_SOUNDS).setKey("mycel"));
        register(111, "waterlily", new LilyPadBlock().setStrength(0.0F).setSounds(GRASS_SOUNDS).setKey("waterlily"));
        Block block10 = new NetherBrickBlock()
            .setStrength(2.0F)
            .setBlastResistance(10.0F)
            .setSounds(STONE_SOUNDS)
            .setKey("netherBrick")
            .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
        register(112, "nether_brick", block10);
        register(
            113,
            "nether_brick_fence",
            new FenceBlock(Material.STONE, MapColor.NETHER).setStrength(2.0F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("netherFence")
        );
        register(114, "nether_brick_stairs", new StairsBlock(block10.defaultState()).setKey("stairsNetherBrick"));
        register(115, "nether_wart", new NetherWartBlock().setKey("netherStalk"));
        register(116, "enchanting_table", new EnchantingTableBlock().setStrength(5.0F).setBlastResistance(2000.0F).setKey("enchantmentTable"));
        register(117, "brewing_stand", new BrewingStandBlock().setStrength(0.5F).setLight(0.125F).setKey("brewingStand"));
        register(118, "cauldron", new CauldronBlock().setStrength(2.0F).setKey("cauldron"));
        register(119, "end_portal", new EndPortalBlock(Material.PORTAL).setStrength(-1.0F).setBlastResistance(6000000.0F));
        register(
            120,
            "end_portal_frame",
            new EndPortalFrameBlock()
                .setSounds(GLASS_SOUNDS)
                .setLight(0.125F)
                .setStrength(-1.0F)
                .setKey("endPortalFrame")
                .setBlastResistance(6000000.0F)
                .setCreativeModeTab(CreativeModeTab.DECORATIONS)
        );
        register(
            121,
            "end_stone",
            new Block(Material.STONE, MapColor.SAND)
                .setStrength(3.0F)
                .setBlastResistance(15.0F)
                .setSounds(STONE_SOUNDS)
                .setKey("whiteStone")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(
            122, "dragon_egg", new DragonEggBlock().setStrength(3.0F).setBlastResistance(15.0F).setSounds(STONE_SOUNDS).setLight(0.125F).setKey("dragonEgg")
        );
        register(
            123,
            "redstone_lamp",
            new RedstoneLampBlock(false).setStrength(0.3F).setSounds(GLASS_SOUNDS).setKey("redstoneLight").setCreativeModeTab(CreativeModeTab.REDSTONE)
        );
        register(124, "lit_redstone_lamp", new RedstoneLampBlock(true).setStrength(0.3F).setSounds(GLASS_SOUNDS).setKey("redstoneLight"));
        register(125, "double_wooden_slab", new DoubleWoodenSlabBlock().setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("woodSlab"));
        register(126, "wooden_slab", new SingleWoodenSlabBlock().setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("woodSlab"));
        register(127, "cocoa", new CocoaBlock().setStrength(0.2F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("cocoa"));
        register(128, "sandstone_stairs", new StairsBlock(block2.defaultState().set(SandstoneBlock.TYPE, SandstoneBlock.Type.SMOOTH)).setKey("stairsSandStone"));
        register(129, "emerald_ore", new OreBlock().setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("oreEmerald"));
        register(
            130,
            "ender_chest",
            new EnderChestBlock().setStrength(22.5F).setBlastResistance(1000.0F).setSounds(STONE_SOUNDS).setKey("enderChest").setLight(0.5F)
        );
        register(131, "tripwire_hook", new TripwireHookBlock().setKey("tripWireSource"));
        register(132, "tripwire", new TripwireBlock().setKey("tripWire"));
        register(
            133,
            "emerald_block",
            new Block(Material.IRON, MapColor.EMERALD)
                .setStrength(5.0F)
                .setBlastResistance(10.0F)
                .setSounds(METAL_SOUNDS)
                .setKey("blockEmerald")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(134, "spruce_stairs", new StairsBlock(block1.defaultState().set(PlanksBlock.VARIANT, PlanksBlock.Variant.SPRUCE)).setKey("stairsWoodSpruce"));
        register(135, "birch_stairs", new StairsBlock(block1.defaultState().set(PlanksBlock.VARIANT, PlanksBlock.Variant.BIRCH)).setKey("stairsWoodBirch"));
        register(136, "jungle_stairs", new StairsBlock(block1.defaultState().set(PlanksBlock.VARIANT, PlanksBlock.Variant.JUNGLE)).setKey("stairsWoodJungle"));
        register(137, "command_block", new CommandBlock().setUnbreakable().setBlastResistance(6000000.0F).setKey("commandBlock"));
        register(138, "beacon", new BeaconBlock().setKey("beacon").setLight(1.0F));
        register(139, "cobblestone_wall", new WallBlock(block).setKey("cobbleWall"));
        register(140, "flower_pot", new FlowerPotBlock().setStrength(0.0F).setSounds(DEFAULT_SOUNDS).setKey("flowerPot"));
        register(141, "carrots", new CarrotsBlock().setKey("carrots"));
        register(142, "potatoes", new PotatoesBlock().setKey("potatoes"));
        register(143, "wooden_button", new WoodenButtonBlock().setStrength(0.5F).setSounds(WOOD_SOUNDS).setKey("button"));
        register(144, "skull", new SkullBlock().setStrength(1.0F).setSounds(STONE_SOUNDS).setKey("skull"));
        register(145, "anvil", new AnvilBlock().setStrength(5.0F).setSounds(ANVIL_SOUNDS).setBlastResistance(2000.0F).setKey("anvil"));
        register(146, "trapped_chest", new ChestBlock(1).setStrength(2.5F).setSounds(WOOD_SOUNDS).setKey("chestTrap"));
        register(
            147,
            "light_weighted_pressure_plate",
            new WeightedPressurePlateBlock(Material.IRON, 15, MapColor.GOLD).setStrength(0.5F).setSounds(WOOD_SOUNDS).setKey("weightedPlate_light")
        );
        register(
            148,
            "heavy_weighted_pressure_plate",
            new WeightedPressurePlateBlock(Material.IRON, 150).setStrength(0.5F).setSounds(WOOD_SOUNDS).setKey("weightedPlate_heavy")
        );
        register(149, "unpowered_comparator", new ComparatorBlock(false).setStrength(0.0F).setSounds(WOOD_SOUNDS).setKey("comparator").disableStats());
        register(
            150, "powered_comparator", new ComparatorBlock(true).setStrength(0.0F).setLight(0.625F).setSounds(WOOD_SOUNDS).setKey("comparator").disableStats()
        );
        register(151, "daylight_detector", new DaylightDetectorBlock(false));
        register(
            152,
            "redstone_block",
            new RedstoneBlock(Material.IRON, MapColor.LAVA)
                .setStrength(5.0F)
                .setBlastResistance(10.0F)
                .setSounds(METAL_SOUNDS)
                .setKey("blockRedstone")
                .setCreativeModeTab(CreativeModeTab.REDSTONE)
        );
        register(153, "quartz_ore", new OreBlock(MapColor.NETHER).setStrength(3.0F).setBlastResistance(5.0F).setSounds(STONE_SOUNDS).setKey("netherquartz"));
        register(154, "hopper", new HopperBlock().setStrength(3.0F).setBlastResistance(8.0F).setSounds(METAL_SOUNDS).setKey("hopper"));
        Block block11 = new QuartzBlock().setSounds(STONE_SOUNDS).setStrength(0.8F).setKey("quartzBlock");
        register(155, "quartz_block", block11);
        register(156, "quartz_stairs", new StairsBlock(block11.defaultState().set(QuartzBlock.VARIANT, QuartzBlock.Variant.DEFAULT)).setKey("stairsQuartz"));
        register(157, "activator_rail", new PoweredRailBlock().setStrength(0.7F).setSounds(METAL_SOUNDS).setKey("activatorRail"));
        register(158, "dropper", new DropperBlock().setStrength(3.5F).setSounds(STONE_SOUNDS).setKey("dropper"));
        register(
            159,
            "stained_hardened_clay",
            new ColoredBlock(Material.STONE).setStrength(1.25F).setBlastResistance(7.0F).setSounds(STONE_SOUNDS).setKey("clayHardenedStained")
        );
        register(160, "stained_glass_pane", new StainedGlassPaneBlock().setStrength(0.3F).setSounds(GLASS_SOUNDS).setKey("thinStainedGlass"));
        register(161, "leaves2", new Leaves2Block().setKey("leaves"));
        register(162, "log2", new Log2Block().setKey("log"));
        register(163, "acacia_stairs", new StairsBlock(block1.defaultState().set(PlanksBlock.VARIANT, PlanksBlock.Variant.ACACIA)).setKey("stairsWoodAcacia"));
        register(
            164, "dark_oak_stairs", new StairsBlock(block1.defaultState().set(PlanksBlock.VARIANT, PlanksBlock.Variant.DARK_OAK)).setKey("stairsWoodDarkOak")
        );
        register(165, "slime", new SlimeBlock().setKey("slime").setSounds(SLIME_SOUNDS));
        register(166, "barrier", new BarrierBlock().setKey("barrier"));
        register(167, "iron_trapdoor", new TrapdoorBlock(Material.IRON).setStrength(5.0F).setSounds(METAL_SOUNDS).setKey("ironTrapdoor").disableStats());
        register(168, "prismarine", new PrismarineBlock().setStrength(1.5F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("prismarine"));
        register(169, "sea_lantern", new SeaLanternBlock(Material.GLASS).setStrength(0.3F).setSounds(GLASS_SOUNDS).setLight(1.0F).setKey("seaLantern"));
        register(
            170, "hay_block", new HayBlock().setStrength(0.5F).setSounds(GRASS_SOUNDS).setKey("hayBlock").setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(171, "carpet", new CarpetBlock().setStrength(0.1F).setSounds(CLOTH_SOUNDS).setKey("woolCarpet").setOpacity(0));
        register(172, "hardened_clay", new HardenedClayBlock().setStrength(1.25F).setBlastResistance(7.0F).setSounds(STONE_SOUNDS).setKey("clayHardened"));
        register(
            173,
            "coal_block",
            new Block(Material.STONE, MapColor.BLACK)
                .setStrength(5.0F)
                .setBlastResistance(10.0F)
                .setSounds(STONE_SOUNDS)
                .setKey("blockCoal")
                .setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS)
        );
        register(174, "packed_ice", new PackedIceBlock().setStrength(0.5F).setSounds(GLASS_SOUNDS).setKey("icePacked"));
        register(175, "double_plant", new DoublePlantBlock());
        register(176, "standing_banner", new BannerBlock.Standing().setStrength(1.0F).setSounds(WOOD_SOUNDS).setKey("banner").disableStats());
        register(177, "wall_banner", new BannerBlock.Wall().setStrength(1.0F).setSounds(WOOD_SOUNDS).setKey("banner").disableStats());
        register(178, "daylight_detector_inverted", new DaylightDetectorBlock(true));
        Block block12 = new RedSandstoneBlock().setSounds(STONE_SOUNDS).setStrength(0.8F).setKey("redSandStone");
        register(179, "red_sandstone", block12);
        register(
            180,
            "red_sandstone_stairs",
            new StairsBlock(block12.defaultState().set(RedSandstoneBlock.TYPE, RedSandstoneBlock.Type.SMOOTH)).setKey("stairsRedSandStone")
        );
        register(
            181,
            "double_stone_slab2",
            new DoubleRedSandstoneSlabBlock().setStrength(2.0F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("stoneSlab2")
        );
        register(182, "stone_slab2", new SingleRedSandstoneSlabBlock().setStrength(2.0F).setBlastResistance(10.0F).setSounds(STONE_SOUNDS).setKey("stoneSlab2"));
        register(
            183,
            "spruce_fence_gate",
            new FenceGateBlock(PlanksBlock.Variant.SPRUCE).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("spruceFenceGate")
        );
        register(
            184,
            "birch_fence_gate",
            new FenceGateBlock(PlanksBlock.Variant.BIRCH).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("birchFenceGate")
        );
        register(
            185,
            "jungle_fence_gate",
            new FenceGateBlock(PlanksBlock.Variant.JUNGLE).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("jungleFenceGate")
        );
        register(
            186,
            "dark_oak_fence_gate",
            new FenceGateBlock(PlanksBlock.Variant.DARK_OAK).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("darkOakFenceGate")
        );
        register(
            187,
            "acacia_fence_gate",
            new FenceGateBlock(PlanksBlock.Variant.ACACIA).setStrength(2.0F).setBlastResistance(5.0F).setSounds(WOOD_SOUNDS).setKey("acaciaFenceGate")
        );
        register(
            188,
            "spruce_fence",
            new FenceBlock(Material.WOOD, PlanksBlock.Variant.SPRUCE.getColor())
                .setStrength(2.0F)
                .setBlastResistance(5.0F)
                .setSounds(WOOD_SOUNDS)
                .setKey("spruceFence")
        );
        register(
            189,
            "birch_fence",
            new FenceBlock(Material.WOOD, PlanksBlock.Variant.BIRCH.getColor())
                .setStrength(2.0F)
                .setBlastResistance(5.0F)
                .setSounds(WOOD_SOUNDS)
                .setKey("birchFence")
        );
        register(
            190,
            "jungle_fence",
            new FenceBlock(Material.WOOD, PlanksBlock.Variant.JUNGLE.getColor())
                .setStrength(2.0F)
                .setBlastResistance(5.0F)
                .setSounds(WOOD_SOUNDS)
                .setKey("jungleFence")
        );
        register(
            191,
            "dark_oak_fence",
            new FenceBlock(Material.WOOD, PlanksBlock.Variant.DARK_OAK.getColor())
                .setStrength(2.0F)
                .setBlastResistance(5.0F)
                .setSounds(WOOD_SOUNDS)
                .setKey("darkOakFence")
        );
        register(
            192,
            "acacia_fence",
            new FenceBlock(Material.WOOD, PlanksBlock.Variant.ACACIA.getColor())
                .setStrength(2.0F)
                .setBlastResistance(5.0F)
                .setSounds(WOOD_SOUNDS)
                .setKey("acaciaFence")
        );
        register(193, "spruce_door", new DoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("doorSpruce").disableStats());
        register(194, "birch_door", new DoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("doorBirch").disableStats());
        register(195, "jungle_door", new DoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("doorJungle").disableStats());
        register(196, "acacia_door", new DoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("doorAcacia").disableStats());
        register(197, "dark_oak_door", new DoorBlock(Material.WOOD).setStrength(3.0F).setSounds(WOOD_SOUNDS).setKey("doorDarkOak").disableStats());
        REGISTRY.validate();

        for (Block block13 : REGISTRY) {
            if (block13.material == Material.AIR) {
                block13.useNeighborLight = false;
            } else {
                boolean flag = false;
                boolean flag1 = block13 instanceof StairsBlock;
                boolean flag2 = block13 instanceof SlabBlock;
                boolean flag3 = block13 == block6;
                boolean flag4 = block13.isTranslucent;
                boolean flag5 = block13.opacity == 0;
                if (flag1 || flag2 || flag3 || flag4 || flag5) {
                    flag = true;
                }

                block13.useNeighborLight = flag;
            }
        }

        for (Block block14 : REGISTRY) {
            for (BlockState blockstate : block14.stateDefinition().all()) {
                int i = REGISTRY.getId(block14) << 4 | block14.getMetadataFromState(blockstate);
                STATE_REGISTRY.put(blockstate, i);
            }
        }
    }

    private static void register(int id, Identifier key, Block block) {
        REGISTRY.register(id, key, block);
    }

    private static void register(int id, String key, Block block) {
        register(id, new Identifier(key), block);
    }

    public enum OffsetType {
        NONE,
        XZ,
        XYZ;
    }

    public static class Sounds {
        public final String key;
        public final float volume;
        public final float pitch;

        public Sounds(String key, float volume, float pitch) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
        }

        public float getVolume() {
            return this.volume;
        }

        public float getPitch() {
            return this.pitch;
        }

        public String getBreaking() {
            return "dig." + this.key;
        }

        public String getStepping() {
            return "step." + this.key;
        }

        public String getPlacing() {
            return this.getBreaking();
        }
    }
}
