package net.zaminmc.torch.bukkit.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.util.Identifier;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;

/**
 * One legacy block cell over one engine position (§28.7's world/block
 * inventory). The §27.5 handle law: this object resolves to the CURRENT
 * authoritative engine state on every read — it is never a copied cell.
 *
 * <p><b>The ownership law (§27.6, the adapter's own headroom):</b> reads
 * are best-effort live reads (the engine's own contract for unloaded
 * cells is the {@code minecraft:air} answer); MUTATIONS route — a call
 * from the simulation owner's own context applies directly (the event
 * handlers' context), and a call from anywhere else travels as a
 * cross-owner intent to the position's owner, applied at a tick boundary
 * (§28.2's nonblocking default). A reference is never authority
 * (§28.3) — the legacy plugin holding a Block off the owner thread can
 * express the intent, and the owner validates and applies it.</p>
 */
public final class TorchBlock implements org.bukkit.block.Block {

    private final BukkitAdapter adapter;
    private final TorchWorld world;
    private final BlockPosition position;

    public TorchBlock(BukkitAdapter adapter, TorchWorld world, int x, int y, int z) {
        this.adapter = adapter;
        this.world = world;
        this.position = new BlockPosition(x, y, z);
    }

    /** The engine position this handle resolves to. */
    public BlockPosition enginePosition() {
        return position;
    }

    /** The adapter this handle resolves through (the state view's route). */
    public BukkitAdapter adapter() {
        return adapter;
    }

    // ------------------------------------------------------------------ reads (best-effort live)

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public int getX() {
        return position.x();
    }

    @Override
    public int getY() {
        return position.y();
    }

    @Override
    public int getZ() {
        return position.z();
    }

    @Override
    public Location getLocation() {
        return new Location(world, position.x(), position.y(), position.z());
    }

    @Override
    public Location getLocation(Location loc) {
        if (loc != null) {
            loc.setWorld(world);
            loc.setX(position.x());
            loc.setY(position.y());
            loc.setZ(position.z());
        }
        return loc;
    }

    @Override
    public Chunk getChunk() {
        throw adapter.unbridged("Block.getChunk");
    }

    @Override
    public Material getType() {
        return adapter.materialMap().toLegacy(world.engineWorld().getBlock(position));
    }

    @Override
    public int getTypeId() {
        Material legacy = getType();
        return legacy == null ? 0 : legacy.getId();
    }

    @Override
    public byte getData() {
        return 0; // the engine's block state model carries no 1.8.8 nibble yet
    }

    @Override
    public boolean isEmpty() {
        return isType("air");
    }

    @Override
    public boolean isLiquid() {
        return isType("water") || isType("lava") || isType("flowing_water") || isType("flowing_lava");
    }

    private boolean isType(String value) {
        Identifier id = world.engineWorld().getBlock(position).identifier();
        return id.value().equals(value);
    }

    // ------------------------------------------------------------------ the power/physics reads (the honest constants)

    @Override
    public boolean isBlockPowered() {
        return false;
    }

    @Override
    public boolean isBlockIndirectlyPowered() {
        return false;
    }

    @Override
    public boolean isBlockFacePowered(BlockFace face) {
        return false;
    }

    @Override
    public boolean isBlockFaceIndirectlyPowered(BlockFace face) {
        return false;
    }

    @Override
    public int getBlockPower(BlockFace face) {
        return 0;
    }

    @Override
    public int getBlockPower() {
        return 0;
    }

    @Override
    public byte getLightLevel() {
        return 0;
    }

    @Override
    public byte getLightFromSky() {
        return 0;
    }

    @Override
    public byte getLightFromBlocks() {
        return 0;
    }

    // ------------------------------------------------------------------ mutations (§27.6 routing)

    @Override
    public void setType(Material type) {
        setTypeId0(type == null ? 0 : type.getId());
    }

    @Override
    public boolean setTypeId(int type) {
        setTypeId0(type);
        return true;
    }

    @Override
    public void setData(byte data) {
        // The engine's block state model carries no 1.8.8 nibble; the loud
        // refusal beats silently pretending it applied.
        throw adapter.unbridged("Block.setData");
    }

    @Override
    public void setType(Material type, boolean applyPhysics) {
        setType(type);
    }

    @Override
    public boolean setTypeId(int type, boolean applyPhysics) {
        setTypeId0(type);
        return true;
    }

    @Override
    public void setData(byte data, boolean applyPhysics) {
        throw adapter.unbridged("Block.setData");
    }

    @Override
    public boolean setTypeIdAndData(int type, byte data, boolean applyPhysics) {
        setTypeId0(type);
        return true;
    }

    private void setTypeId0(int legacyId) {
        Material legacy = Material.getMaterial(legacyId);
        if (legacy == null) {
            throw new IllegalArgumentException("No 1.8.8 material for id " + legacyId);
        }
        net.zaminmc.torch.block.BlockType engineType = adapter.materialMap().toEngine(legacy);
        if (engineType == null) {
            throw adapter.unbridged("Block.setType(" + legacy.name()
                    + " — the engine implements no such block)");
        }
        adapter.mutatePosition(world.engineWorld(), position, "bukkit-block-set",
                () -> world.engineWorld().setBlock(position, engineType));
    }

    // ------------------------------------------------------------------ state + relatives

    @Override
    public BlockState getState() {
        return adapter.blockState(this);
    }

    @Override
    public org.bukkit.block.Biome getBiome() {
        return org.bukkit.block.Biome.PLAINS;
    }

    @Override
    public void setBiome(org.bukkit.block.Biome bio) {
        throw adapter.unbridged("Block.setBiome");
    }

    @Override
    public org.bukkit.block.PistonMoveReaction getPistonMoveReaction() {
        return org.bukkit.block.PistonMoveReaction.MOVE;
    }

    @Override
    public boolean breakNaturally() {
        return breakNaturally(null);
    }

    @Override
    public boolean breakNaturally(org.bukkit.inventory.ItemStack tool) {
        net.zaminmc.torch.block.BlockType air = adapter.airBlock();
        adapter.mutatePosition(world.engineWorld(), position, "bukkit-break-naturally",
                () -> world.engineWorld().setBlock(position, air));
        return true;
    }

    @Override
    public BlockFace getFace(org.bukkit.block.Block block) {
        if (block == null) {
            return BlockFace.SELF;
        }
        int dx = block.getX() - position.x();
        int dy = block.getY() - position.y();
        int dz = block.getZ() - position.z();
        if (dx == 0 && dy == 1 && dz == 0) return BlockFace.UP;
        if (dx == 0 && dy == -1 && dz == 0) return BlockFace.DOWN;
        if (dx == 0 && dy == 0 && dz == 1) return BlockFace.SOUTH;
        if (dx == 0 && dy == 0 && dz == -1) return BlockFace.NORTH;
        if (dx == 1 && dy == 0 && dz == 0) return BlockFace.EAST;
        if (dx == -1 && dy == 0 && dz == 0) return BlockFace.WEST;
        return BlockFace.SELF;
    }

    @Override
    public double getTemperature() {
        return 0.5;
    }

    @Override
    public double getHumidity() {
        return 0.5;
    }

    @Override
    public TorchBlock getRelative(int modX, int modY, int modZ) {
        return adapter.blockAt(world, position.x() + modX, position.y() + modY, position.z() + modZ);
    }

    @Override
    public TorchBlock getRelative(BlockFace face) {
        return getRelative(face.getModX(), face.getModY(), face.getModZ());
    }

    @Override
    public TorchBlock getRelative(BlockFace face, int distance) {
        return getRelative(face.getModX() * distance, face.getModY() * distance,
                face.getModZ() * distance);
    }

    @Override
    public Collection<org.bukkit.inventory.ItemStack> getDrops() {
        throw adapter.unbridged("Block.getDrops");
    }

    @Override
    public Collection<org.bukkit.inventory.ItemStack> getDrops(
            org.bukkit.inventory.ItemStack item) {
        throw adapter.unbridged("Block.getDrops");
    }

    // ------------------------------------------------------------------ piston/structure arms

    /** The chunk-loaded read for this cell (a convenience over the world view). */
    public boolean isChunkLoaded() {
        return world.isChunkLoaded(position.x() >> 4, position.z() >> 4);
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        throw adapter.unbridged("Block.setMetadata");
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return List.of();
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return false;
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        throw adapter.unbridged("Block.removeMetadata");
    }
}
