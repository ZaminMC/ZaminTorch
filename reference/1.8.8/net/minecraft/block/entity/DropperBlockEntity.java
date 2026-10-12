package net.minecraft.block.entity;

public class DropperBlockEntity extends DispenserBlockEntity {
    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.dropper";
    }

    @Override
    public String getMenuType() {
        return "minecraft:dropper";
    }
}
