package net.minecraft.item;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.map.SavedMapData;

public class FilledMapItem extends NetworkSyncedItem {
    protected FilledMapItem() {
        this.setHasCustomData(true);
    }

    public static SavedMapData getMapData(int id, World world) {
        String s = "map_" + id;
        SavedMapData savedmapdata = (SavedMapData)world.loadSavedData(SavedMapData.class, s);
        if (savedmapdata == null) {
            savedmapdata = new SavedMapData(s);
            world.setSavedData(s, savedmapdata);
        }

        return savedmapdata;
    }

    public SavedMapData getSavedMapData(ItemStack item, World world) {
        String s = "map_" + item.getMetadata();
        SavedMapData savedmapdata = (SavedMapData)world.loadSavedData(SavedMapData.class, s);
        if (savedmapdata == null && !world.isClient) {
            item.setDamage(world.getSavedDataCount("map"));
            s = "map_" + item.getMetadata();
            savedmapdata = new SavedMapData(s);
            savedmapdata.scale = 3;
            savedmapdata.updateCenter(world.getData().getSpawnX(), world.getData().getSpawnZ(), savedmapdata.scale);
            savedmapdata.dimension = (byte)world.dimension.getId();
            savedmapdata.markDirty();
            world.setSavedData(s, savedmapdata);
        }

        return savedmapdata;
    }

    public void update(World world, Entity entity, SavedMapData data) {
        if (world.dimension.getId() == data.dimension && entity instanceof PlayerEntity) {
            int i = 1 << data.scale;
            int j = data.centerX;
            int k = data.centerZ;
            int l = MathHelper.floor(entity.x - j) / i + 64;
            int i1 = MathHelper.floor(entity.z - k) / i + 64;
            int j1 = 128 / i;
            if (world.dimension.hasNoSky()) {
                j1 /= 2;
            }

            SavedMapData.Holder savedmapdata$holder = data.addHolder((PlayerEntity)entity);
            savedmapdata$holder.step++;
            boolean flag = false;

            for (int k1 = l - j1 + 1; k1 < l + j1; k1++) {
                if ((k1 & 15) == (savedmapdata$holder.step & 15) || flag) {
                    flag = false;
                    double d0 = 0.0;

                    for (int l1 = i1 - j1 - 1; l1 < i1 + j1; l1++) {
                        if (k1 >= 0 && l1 >= -1 && k1 < 128 && l1 < 128) {
                            int i2 = k1 - l;
                            int j2 = l1 - i1;
                            boolean flag1 = i2 * i2 + j2 * j2 > (j1 - 2) * (j1 - 2);
                            int k2 = (j / i + k1 - 64) * i;
                            int l2 = (k / i + l1 - 64) * i;
                            Multiset<MapColor> multiset = HashMultiset.create();
                            WorldChunk worldchunk = world.getChunk(new BlockPos(k2, 0, l2));
                            if (!worldchunk.isEmpty()) {
                                int i3 = k2 & 15;
                                int j3 = l2 & 15;
                                int k3 = 0;
                                double d1 = 0.0;
                                if (world.dimension.hasNoSky()) {
                                    int l3 = k2 + l2 * 231871;
                                    l3 = l3 * l3 * 31287121 + l3 * 11;
                                    if ((l3 >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.getMapColor(Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.DIRT)), 10);
                                    } else {
                                        multiset.add(
                                            Blocks.STONE.getMapColor(Blocks.STONE.defaultState().set(StoneBlock.VARIANT, StoneBlock.Variant.STONE)), 100
                                        );
                                    }

                                    d1 = 100.0;
                                } else {
                                    BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                                    for (int i4 = 0; i4 < i; i4++) {
                                        for (int j4 = 0; j4 < i; j4++) {
                                            int k4 = worldchunk.getHeight(i4 + i3, j4 + j3) + 1;
                                            BlockState blockstate = Blocks.AIR.defaultState();
                                            if (k4 > 1) {
                                                do {
                                                    blockstate = worldchunk.getBlockState(blockpos$mutable.set(i4 + i3, --k4, j4 + j3));
                                                } while (blockstate.getBlock().getMapColor(blockstate) == MapColor.AIR && k4 > 0);

                                                if (k4 > 0 && blockstate.getBlock().getMaterial().isLiquid()) {
                                                    int l4 = k4 - 1;

                                                    Block block;
                                                    do {
                                                        block = worldchunk.getBlock(i4 + i3, l4--, j4 + j3);
                                                        k3++;
                                                    } while (l4 > 0 && block.getMaterial().isLiquid());
                                                }
                                            }

                                            d1 += (double)k4 / (i * i);
                                            multiset.add(blockstate.getBlock().getMapColor(blockstate));
                                        }
                                    }
                                }

                                k3 /= i * i;
                                double d2 = (d1 - d0) * 4.0 / (i + 4) + ((k1 + l1 & 1) - 0.5) * 0.4;
                                int i5 = 1;
                                if (d2 > 0.6) {
                                    i5 = 2;
                                }

                                if (d2 < -0.6) {
                                    i5 = 0;
                                }

                                MapColor mapcolor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.AIR);
                                if (mapcolor == MapColor.WATER) {
                                    d2 = k3 * 0.1 + (k1 + l1 & 1) * 0.2;
                                    i5 = 1;
                                    if (d2 < 0.5) {
                                        i5 = 2;
                                    }

                                    if (d2 > 0.9) {
                                        i5 = 0;
                                    }
                                }

                                d0 = d1;
                                if (l1 >= 0 && i2 * i2 + j2 * j2 < j1 * j1 && (!flag1 || (k1 + l1 & 1) != 0)) {
                                    byte b0 = data.colors[k1 + l1 * 128];
                                    byte b1 = (byte)(mapcolor.id * 4 + i5);
                                    if (b0 != b1) {
                                        data.colors[k1 + l1 * 128] = b1;
                                        data.markDirty(k1, l1);
                                        flag = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient) {
            SavedMapData savedmapdata = this.getSavedMapData(stack, world);
            if (entity instanceof PlayerEntity) {
                PlayerEntity playerentity = (PlayerEntity)entity;
                savedmapdata.tickHolder(playerentity, stack);
            }

            if (selected) {
                this.update(world, entity, savedmapdata);
            }
        }
    }

    @Override
    public Packet getUpdatePacket(ItemStack stack, World world, PlayerEntity player) {
        return this.getSavedMapData(stack, world).createUpdatePacket(stack, world, player);
    }

    @Override
    public void onResult(ItemStack stack, World world, PlayerEntity player) {
        if (stack.hasNbt() && stack.getNbt().getBoolean("map_is_scaling")) {
            SavedMapData savedmapdata = Items.FILLED_MAP.getSavedMapData(stack, world);
            stack.setDamage(world.getSavedDataCount("map"));
            SavedMapData savedmapdata1 = new SavedMapData("map_" + stack.getMetadata());
            savedmapdata1.scale = (byte)(savedmapdata.scale + 1);
            if (savedmapdata1.scale > 4) {
                savedmapdata1.scale = 4;
            }

            savedmapdata1.updateCenter(savedmapdata.centerX, savedmapdata.centerZ, savedmapdata1.scale);
            savedmapdata1.dimension = savedmapdata.dimension;
            savedmapdata1.markDirty();
            world.setSavedData("map_" + stack.getMetadata(), savedmapdata1);
        }
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        SavedMapData savedmapdata = this.getSavedMapData(stack, player.world);
        if (advanced) {
            if (savedmapdata == null) {
                tooltip.add("Unknown map");
            } else {
                tooltip.add("Scaling at 1:" + (1 << savedmapdata.scale));
                tooltip.add("(Level " + savedmapdata.scale + "/" + 4 + ")");
            }
        }
    }
}
