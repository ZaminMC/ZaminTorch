package net.minecraft.block.entity;

import com.google.gson.JsonParseException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.SignUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SignBlockEntity extends BlockEntity {
    public final Text[] lines = new Text[]{new LiteralText(""), new LiteralText(""), new LiteralText(""), new LiteralText("")};
    public int currentRow = -1;
    private boolean editable = true;
    private PlayerEntity player;
    private final CommandResults commandResults = new CommandResults();

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        for (int i = 0; i < 4; i++) {
            String s = Text.Serializer.toJson(this.lines[i]);
            nbt.putString("Text" + (i + 1), s);
        }

        this.commandResults.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        this.editable = false;
        super.readNbt(nbt);
        CommandSource commandsource = new CommandSource() {
            @Override
            public String getName() {
                return "Sign";
            }

            @Override
            public Text getDisplayName() {
                return new LiteralText(this.getName());
            }

            @Override
            public void sendMessage(Text message) {
            }

            @Override
            public boolean canUseCommand(int permissionLevel, String command) {
                return true;
            }

            @Override
            public BlockPos getCommandSourceBlockPos() {
                return SignBlockEntity.this.pos;
            }

            @Override
            public Vec3d getCommandSourcePos() {
                return new Vec3d(SignBlockEntity.this.pos.getX() + 0.5, SignBlockEntity.this.pos.getY() + 0.5, SignBlockEntity.this.pos.getZ() + 0.5);
            }

            @Override
            public World getCommandSourceWorld() {
                return SignBlockEntity.this.world;
            }

            @Override
            public Entity asEntity() {
                return null;
            }

            @Override
            public boolean sendCommandSuccessToOps() {
                return false;
            }

            @Override
            public void addResult(CommandResults.Type type, int result) {
            }
        };

        for (int i = 0; i < 4; i++) {
            String s = nbt.getString("Text" + (i + 1));

            try {
                Text text = Text.Serializer.fromJson(s);

                try {
                    this.lines[i] = TextUtils.updateForEntity(commandsource, text, null);
                } catch (CommandException commandexception) {
                    this.lines[i] = text;
                }
            } catch (JsonParseException jsonparseexception) {
                this.lines[i] = new LiteralText(s);
            }
        }

        this.commandResults.readNbt(nbt);
    }

    @Override
    public Packet createUpdatePacket() {
        Text[] atext = new Text[4];
        System.arraycopy(this.lines, 0, atext, 0, 4);
        return new SignUpdateS2CPacket(this.world, this.pos, atext);
    }

    @Override
    public boolean requireOpForPlacingWithNbt() {
        return true;
    }

    public boolean isEditable() {
        return this.editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        if (!editable) {
            this.player = null;
        }
    }

    public void setPlayer(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity getPlayer() {
        return this.player;
    }

    public boolean onUse(PlayerEntity player) {
        CommandSource commandsource = new CommandSource() {
            @Override
            public String getName() {
                return player.getName();
            }

            @Override
            public Text getDisplayName() {
                return player.getDisplayName();
            }

            @Override
            public void sendMessage(Text message) {
            }

            @Override
            public boolean canUseCommand(int permissionLevel, String command) {
                return permissionLevel <= 2;
            }

            @Override
            public BlockPos getCommandSourceBlockPos() {
                return SignBlockEntity.this.pos;
            }

            @Override
            public Vec3d getCommandSourcePos() {
                return new Vec3d(SignBlockEntity.this.pos.getX() + 0.5, SignBlockEntity.this.pos.getY() + 0.5, SignBlockEntity.this.pos.getZ() + 0.5);
            }

            @Override
            public World getCommandSourceWorld() {
                return player.getCommandSourceWorld();
            }

            @Override
            public Entity asEntity() {
                return player;
            }

            @Override
            public boolean sendCommandSuccessToOps() {
                return false;
            }

            @Override
            public void addResult(CommandResults.Type type, int result) {
                SignBlockEntity.this.commandResults.add(this, type, result);
            }
        };

        for (int i = 0; i < this.lines.length; i++) {
            Style style = this.lines[i] == null ? null : this.lines[i].getStyle();
            if (style != null && style.getClickEvent() != null) {
                ClickEvent clickevent = style.getClickEvent();
                if (clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                    MinecraftServer.getInstance().getCommandHandler().run(commandsource, clickevent.getValue());
                }
            }
        }

        return true;
    }

    public CommandResults getCommandResults() {
        return this.commandResults;
    }
}
