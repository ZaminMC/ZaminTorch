package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.CraftingTableMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CraftingTableBlock extends Block {
    protected CraftingTableBlock() {
        super(Material.WOOD);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        player.openMenu(new CraftingTableBlock.MenuProvider(world, pos));
        player.incrementStat(Stats.CRAFTING_TABLE_INTERACTIONS);
        return true;
    }

    public static class MenuProvider implements net.minecraft.inventory.menu.MenuProvider {
        private final World world;
        private final BlockPos pos;

        public MenuProvider(World world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public boolean hasCustomName() {
            return false;
        }

        @Override
        public Text getDisplayName() {
            return new TranslatableText(Blocks.CRAFTING_TABLE.getTranslationKey() + ".name");
        }

        @Override
        public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
            return new CraftingTableMenu(playerInventory, this.world, this.pos);
        }

        @Override
        public String getMenuType() {
            return "minecraft:crafting_table";
        }
    }
}
