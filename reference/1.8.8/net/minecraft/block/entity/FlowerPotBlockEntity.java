package net.minecraft.block.entity;

import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.resource.Identifier;

public class FlowerPotBlockEntity extends BlockEntity {
    private Item plant;
    private int metadata;

    public FlowerPotBlockEntity() {
    }

    public FlowerPotBlockEntity(Item plant, int metadata) {
        this.plant = plant;
        this.metadata = metadata;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Identifier identifier = Item.REGISTRY.getKey(this.plant);
        nbt.putString("Item", identifier == null ? "" : identifier.toString());
        nbt.putInt("Data", this.metadata);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Item", 8)) {
            this.plant = Item.byKey(nbt.getString("Item"));
        } else {
            this.plant = Item.byId(nbt.getInt("Item"));
        }

        this.metadata = nbt.getInt("Data");
    }

    @Override
    public Packet createUpdatePacket() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound);
        nbtcompound.remove("Item");
        nbtcompound.putInt("Item", Item.getId(this.plant));
        return new BlockEntityUpdateS2CPacket(this.pos, 5, nbtcompound);
    }

    public void setPlant(Item plant, int metadata) {
        this.plant = plant;
        this.metadata = metadata;
    }

    public Item getPlant() {
        return this.plant;
    }

    public int getMetadata() {
        return this.metadata;
    }
}
