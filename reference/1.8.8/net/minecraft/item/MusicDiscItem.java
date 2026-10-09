package net.minecraft.item;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class MusicDiscItem extends Item {
    private static final Map<String, MusicDiscItem> RECORD_TYPES = Maps.newHashMap();
    public final String record;

    protected MusicDiscItem(String record) {
        this.record = record;
        this.maxStackSize = 1;
        this.setCreativeModeTab(CreativeModeTab.MISC);
        RECORD_TYPES.put("records." + record, this);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() != Blocks.JUKEBOX || blockstate.get(JukeboxBlock.HAS_RECORD)) {
            return false;
        }

        if (world.isClient) {
            return true;
        }

        ((JukeboxBlock)Blocks.JUKEBOX).setRecord(world, pos, blockstate, stack);
        world.doEvent(null, 1005, pos, Item.getId(this));
        stack.size--;
        player.incrementStat(Stats.RECORDS_PLAYED);
        return true;
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        tooltip.add(this.getDescription());
    }

    public String getDescription() {
        return I18n.translate("item.record." + this.record + ".desc");
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.RARE;
    }

    public static MusicDiscItem getByName(String name) {
        return RECORD_TYPES.get(name);
    }
}
