package net.minecraft.world.map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.MapDataS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.saveddata.SavedData;

public class SavedMapData extends SavedData {
    public int centerX;
    public int centerZ;
    public byte dimension;
    public byte scale;
    public byte[] colors = new byte[16384];
    public List<SavedMapData.Holder> holders = Lists.newArrayList();
    private Map<PlayerEntity, SavedMapData.Holder> holdersByPlayer = Maps.newHashMap();
    public Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();

    public SavedMapData(String string) {
        super(string);
    }

    public void updateCenter(double x, double z, int scale) {
        int i = 128 * (1 << scale);
        int j = MathHelper.floor((x + 64.0) / i);
        int k = MathHelper.floor((z + 64.0) / i);
        this.centerX = j * i + i / 2 - 64;
        this.centerZ = k * i + i / 2 - 64;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        this.dimension = nbt.getByte("dimension");
        this.centerX = nbt.getInt("xCenter");
        this.centerZ = nbt.getInt("zCenter");
        this.scale = nbt.getByte("scale");
        this.scale = (byte)MathHelper.clamp(this.scale, 0, 4);
        int i = nbt.getShort("width");
        int j = nbt.getShort("height");
        if (i == 128 && j == 128) {
            this.colors = nbt.getByteArray("colors");
        } else {
            byte[] abyte = nbt.getByteArray("colors");
            this.colors = new byte[16384];
            int k = (128 - i) / 2;
            int l = (128 - j) / 2;

            for (int i1 = 0; i1 < j; i1++) {
                int j1 = i1 + l;
                if (j1 >= 0 || j1 < 128) {
                    for (int k1 = 0; k1 < i; k1++) {
                        int l1 = k1 + k;
                        if (l1 >= 0 || l1 < 128) {
                            this.colors[l1 + j1 * 128] = abyte[k1 + i1 * i];
                        }
                    }
                }
            }
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.putByte("dimension", this.dimension);
        nbt.putInt("xCenter", this.centerX);
        nbt.putInt("zCenter", this.centerZ);
        nbt.putByte("scale", this.scale);
        nbt.putShort("width", (short)128);
        nbt.putShort("height", (short)128);
        nbt.putByteArray("colors", this.colors);
    }

    public void tickHolder(PlayerEntity player, ItemStack item) {
        if (!this.holdersByPlayer.containsKey(player)) {
            SavedMapData.Holder savedmapdata$holder = new SavedMapData.Holder(player);
            this.holdersByPlayer.put(player, savedmapdata$holder);
            this.holders.add(savedmapdata$holder);
        }

        if (!player.inventory.contains(item)) {
            this.decorations.remove(player.getName());
        }

        for (int i = 0; i < this.holders.size(); i++) {
            SavedMapData.Holder savedmapdata$holder1 = this.holders.get(i);
            if (!savedmapdata$holder1.player.removed && (savedmapdata$holder1.player.inventory.contains(item) || item.isInItemFrame())) {
                if (!item.isInItemFrame() && savedmapdata$holder1.player.dimension == this.dimension) {
                    this.addDecoration(
                        0,
                        savedmapdata$holder1.player.world,
                        savedmapdata$holder1.player.getName(),
                        savedmapdata$holder1.player.x,
                        savedmapdata$holder1.player.z,
                        savedmapdata$holder1.player.yaw
                    );
                }
            } else {
                this.holdersByPlayer.remove(savedmapdata$holder1.player);
                this.holders.remove(savedmapdata$holder1);
            }
        }

        if (item.isInItemFrame()) {
            ItemFrameEntity itemframeentity = item.getItemFrame();
            BlockPos blockpos = itemframeentity.getBlockPos();
            this.addDecoration(
                1, player.world, "frame-" + itemframeentity.getNetworkId(), blockpos.getX(), blockpos.getZ(), itemframeentity.dir.getIdHorizontal() * 90
            );
        }

        if (item.hasNbt() && item.getNbt().contains("Decorations", 9)) {
            NbtList nbtlist = item.getNbt().getList("Decorations", 10);

            for (int j = 0; j < nbtlist.size(); j++) {
                NbtCompound nbtcompound = nbtlist.getCompound(j);
                if (!this.decorations.containsKey(nbtcompound.getString("id"))) {
                    this.addDecoration(
                        nbtcompound.getByte("type"),
                        player.world,
                        nbtcompound.getString("id"),
                        nbtcompound.getDouble("x"),
                        nbtcompound.getDouble("z"),
                        nbtcompound.getDouble("rot")
                    );
                }
            }
        }
    }

    private void addDecoration(int type, World world, String id, double x, double z, double rotation) {
        int i = 1 << this.scale;
        float f = (float)(x - this.centerX) / i;
        float f1 = (float)(z - this.centerZ) / i;
        byte b0 = (byte)(f * 2.0F + 0.5);
        byte b1 = (byte)(f1 * 2.0F + 0.5);
        int j = 63;
        byte b2;
        if (f >= -j && f1 >= -j && f <= j && f1 <= j) {
            rotation += rotation < 0.0 ? -8.0 : 8.0;
            b2 = (byte)(rotation * 16.0 / 360.0);
            if (this.dimension < 0) {
                int k = (int)(world.getData().getTimeOfDay() / 10L);
                b2 = (byte)(k * k * 34187121 + k * 121 >> 15 & 15);
            }
        } else {
            if (!(Math.abs(f) < 320.0F) || !(Math.abs(f1) < 320.0F)) {
                this.decorations.remove(id);
                return;
            }

            type = 6;
            b2 = 0;
            if (f <= -j) {
                b0 = (byte)(j * 2 + 2.5);
            }

            if (f1 <= -j) {
                b1 = (byte)(j * 2 + 2.5);
            }

            if (f >= j) {
                b0 = (byte)(j * 2 + 1);
            }

            if (f1 >= j) {
                b1 = (byte)(j * 2 + 1);
            }
        }

        this.decorations.put(id, new MapDecoration((byte)type, b0, b1, b2));
    }

    public Packet createUpdatePacket(ItemStack item, World world, PlayerEntity player) {
        SavedMapData.Holder savedmapdata$holder = this.holdersByPlayer.get(player);
        return savedmapdata$holder == null ? null : savedmapdata$holder.createUpdatePacket(item);
    }

    public void markDirty(int x, int y) {
        super.markDirty();

        for (SavedMapData.Holder savedmapdata$holder : this.holders) {
            savedmapdata$holder.markDirty(x, y);
        }
    }

    public SavedMapData.Holder addHolder(PlayerEntity player) {
        SavedMapData.Holder savedmapdata$holder = this.holdersByPlayer.get(player);
        if (savedmapdata$holder == null) {
            savedmapdata$holder = new SavedMapData.Holder(player);
            this.holdersByPlayer.put(player, savedmapdata$holder);
            this.holders.add(savedmapdata$holder);
        }

        return savedmapdata$holder;
    }

    public class Holder {
        public final PlayerEntity player;
        private boolean dirty = true;
        private int dirtyMinX = 0;
        private int dirtyMinY = 0;
        private int dirtyMaxX = 127;
        private int dirtyMaxY = 127;
        private int ticks;
        public int step;

        public Holder(PlayerEntity player) {
            this.player = player;
        }

        public Packet createUpdatePacket(ItemStack item) {
            if (this.dirty) {
                this.dirty = false;
                return new MapDataS2CPacket(
                    item.getMetadata(),
                    SavedMapData.this.scale,
                    SavedMapData.this.decorations.values(),
                    SavedMapData.this.colors,
                    this.dirtyMinX,
                    this.dirtyMinY,
                    this.dirtyMaxX + 1 - this.dirtyMinX,
                    this.dirtyMaxY + 1 - this.dirtyMinY
                );
            } else {
                return this.ticks++ % 5 == 0
                    ? new MapDataS2CPacket(
                        item.getMetadata(), SavedMapData.this.scale, SavedMapData.this.decorations.values(), SavedMapData.this.colors, 0, 0, 0, 0
                    )
                    : null;
            }
        }

        public void markDirty(int x, int y) {
            if (this.dirty) {
                this.dirtyMinX = Math.min(this.dirtyMinX, x);
                this.dirtyMinY = Math.min(this.dirtyMinY, y);
                this.dirtyMaxX = Math.max(this.dirtyMaxX, x);
                this.dirtyMaxY = Math.max(this.dirtyMaxY, y);
            } else {
                this.dirty = true;
                this.dirtyMinX = x;
                this.dirtyMinY = y;
                this.dirtyMaxX = x;
                this.dirtyMaxY = y;
            }
        }
    }
}
