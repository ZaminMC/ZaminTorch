package net.minecraft.block.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BlockEntity {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Map<String, Class<? extends BlockEntity>> ID_TO_TYPE = Maps.newHashMap();
    private static Map<Class<? extends BlockEntity>, String> TYPE_TO_ID = Maps.newHashMap();
    protected World world;
    protected BlockPos pos = BlockPos.ORIGIN;
    protected boolean removed;
    private int metadata = -1;
    protected Block block;

    private static void register(Class<? extends BlockEntity> type, String id) {
        if (ID_TO_TYPE.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate id: " + id);
        }

        ID_TO_TYPE.put(id, type);
        TYPE_TO_ID.put(type, id);
    }

    public World getWorld() {
        return this.world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public boolean hasWorld() {
        return this.world != null;
    }

    public void readNbt(NbtCompound nbt) {
        this.pos = new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
    }

    public void writeNbt(NbtCompound nbt) {
        String s = TYPE_TO_ID.get(this.getClass());
        if (s == null) {
            throw new RuntimeException(this.getClass() + " is missing a mapping! This is a bug!");
        }

        nbt.putString("id", s);
        nbt.putInt("x", this.pos.getX());
        nbt.putInt("y", this.pos.getY());
        nbt.putInt("z", this.pos.getZ());
    }

    public static BlockEntity fromNbt(NbtCompound nbt) {
        BlockEntity blockentity = null;

        try {
            Class<? extends BlockEntity> oclass = ID_TO_TYPE.get(nbt.getString("id"));
            if (oclass != null) {
                blockentity = oclass.newInstance();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        if (blockentity != null) {
            blockentity.readNbt(nbt);
        } else {
            LOGGER.warn("Skipping BlockEntity with id " + nbt.getString("id"));
        }

        return blockentity;
    }

    public int getBlockMetadata() {
        if (this.metadata == -1) {
            BlockState blockstate = this.world.getBlockState(this.pos);
            this.metadata = blockstate.getBlock().getMetadataFromState(blockstate);
        }

        return this.metadata;
    }

    public void markDirty() {
        if (this.world != null) {
            BlockState blockstate = this.world.getBlockState(this.pos);
            this.metadata = blockstate.getBlock().getMetadataFromState(blockstate);
            this.world.notifyBlockEntityChanged(this.pos, this);
            if (this.getBlock() != Blocks.AIR) {
                this.world.updateNeighborComparators(this.pos, this.getBlock());
            }
        }
    }

    public double squaredDistanceTo(double x, double y, double z) {
        double d0 = this.pos.getX() + 0.5 - x;
        double d1 = this.pos.getY() + 0.5 - y;
        double d2 = this.pos.getZ() + 0.5 - z;
        return d0 * d0 + d1 * d1 + d2 * d2;
    }

    public double getSquaredViewDistance() {
        return 4096.0;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Block getBlock() {
        if (this.block == null) {
            this.block = this.world.getBlockState(this.pos).getBlock();
        }

        return this.block;
    }

    public Packet createUpdatePacket() {
        return null;
    }

    public boolean isRemoved() {
        return this.removed;
    }

    public void markRemoved() {
        this.removed = true;
    }

    public void cancelRemoval() {
        this.removed = false;
    }

    public boolean doEvent(int type, int data) {
        return false;
    }

    public void clearBlockCache() {
        this.block = null;
        this.metadata = -1;
    }

    public void populateCrashReport(CrashReportCategory category) {
        category.add("Name", new Callable<String>() {
            public String call() throws Exception {
                return BlockEntity.TYPE_TO_ID.get(BlockEntity.this.getClass()) + " // " + BlockEntity.this.getClass().getCanonicalName();
            }
        });
        if (this.world != null) {
            CrashReportCategory.addBlockDetails(category, this.pos, this.getBlock(), this.getBlockMetadata());
            category.add("Actual block type", new Callable<String>() {
                public String call() throws Exception {
                    int i = Block.getId(BlockEntity.this.world.getBlockState(BlockEntity.this.pos).getBlock());

                    try {
                        return String.format("ID #%d (%s // %s)", i, Block.byId(i).getTranslationKey(), Block.byId(i).getClass().getCanonicalName());
                    } catch (Throwable throwable) {
                        return "ID #" + i;
                    }
                }
            });
            category.add("Actual block data value", new Callable<String>() {
                public String call() throws Exception {
                    BlockState blockstate = BlockEntity.this.world.getBlockState(BlockEntity.this.pos);
                    int i = blockstate.getBlock().getMetadataFromState(blockstate);
                    if (i < 0) {
                        return "Unknown? (Got " + i + ")";
                    }

                    String s = String.format("%4s", Integer.toBinaryString(i)).replace(" ", "0");
                    return String.format("%1$d / 0x%1$X / 0b%2$s", i, s);
                }
            });
        }
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public boolean requireOpForPlacingWithNbt() {
        return false;
    }

    static {
        register(FurnaceBlockEntity.class, "Furnace");
        register(ChestBlockEntity.class, "Chest");
        register(EnderChestBlockEntity.class, "EnderChest");
        register(JukeboxBlock.JukeboxBlockEntity.class, "RecordPlayer");
        register(DispenserBlockEntity.class, "Trap");
        register(DropperBlockEntity.class, "Dropper");
        register(SignBlockEntity.class, "Sign");
        register(MobSpawnerBlockEntity.class, "MobSpawner");
        register(NoteBlockBlockEntity.class, "Music");
        register(MovingBlockEntity.class, "Piston");
        register(BrewingStandBlockEntity.class, "Cauldron");
        register(EnchantingTableBlockEntity.class, "EnchantTable");
        register(EndPortalBlockEntity.class, "Airportal");
        register(CommandBlockBlockEntity.class, "Control");
        register(BeaconBlockEntity.class, "Beacon");
        register(SkullBlockEntity.class, "Skull");
        register(DaylightDetectorBlockEntity.class, "DLDetector");
        register(HopperBlockEntity.class, "Hopper");
        register(ComparatorBlockEntity.class, "Comparator");
        register(FlowerPotBlockEntity.class, "FlowerPot");
        register(BannerBlockEntity.class, "Banner");
    }
}
