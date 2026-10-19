package net.zaminmc.torch.bukkit.block;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The legacy state view over one {@link TorchBlock} cell (§28.7's
 * block-state inventory): the reads resolve live to the engine position,
 * and the {@code update()} arms ride the same §27.6 routing the block
 * handle itself carries (owner-context direct, otherwise a cross-owner
 * intent).
 *
 * <p><b>The honest gap:</b> the engine's block state model carries no
 * 1.8.8 metadata nibble and no distinct state-object family yet —
 * {@code getData()} answers the constant 0 and mutation arms fail loudly
 * rather than pretending (§27.9: record the limitation, never fake the
 * compatibility).</p>
 */
public final class TorchBlockState implements BlockState {

    private final TorchBlock block;

    public TorchBlockState(TorchBlock block) {
        this.block = block;
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public Material getType() {
        return block.getType();
    }

    @Override
    public int getTypeId() {
        return block.getTypeId();
    }

    @Override
    public byte getLightLevel() {
        return block.getLightLevel();
    }

    @Override
    public World getWorld() {
        return block.getWorld();
    }

    @Override
    public int getX() {
        return block.getX();
    }

    @Override
    public int getY() {
        return block.getY();
    }

    @Override
    public int getZ() {
        return block.getZ();
    }

    @Override
    public Location getLocation() {
        return block.getLocation();
    }

    @Override
    public Location getLocation(Location loc) {
        return block.getLocation(loc);
    }

    @Override
    public Chunk getChunk() {
        throw block.adapter().unbridged("BlockState.getChunk");
    }

    @Override
    public MaterialData getData() {
        Material type = getType();
        return type == null ? null : new MaterialData(type, (byte) 0);
    }

    @Override
    public byte getRawData() {
        return 0;
    }

    @Override
    public void setData(MaterialData data) {
        throw block.adapter().unbridged("BlockState.setData");
    }

    @Override
    public void setRawData(byte data) {
        throw block.adapter().unbridged("BlockState.setRawData");
    }

    @Override
    public void setType(Material type) {
        block.setType(type);
    }

    @Override
    public boolean setTypeId(int type) {
        block.setTypeId(type);
        return true;
    }

    @Override
    public boolean update() {
        // The state view writes through the block handle immediately (the
        // engine has no pending-state buffer); the legacy three-step
        // contract collapses to one, documented.
        return true;
    }

    @Override
    public boolean update(boolean force) {
        return update();
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        return update();
    }

    @Override
    public boolean isPlaced() {
        return true;
    }

    // ------------------------------------------------------------------ metadata

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        throw block.adapter().unbridged("BlockState.setMetadata");
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
        throw block.adapter().unbridged("BlockState.removeMetadata");
    }
}
