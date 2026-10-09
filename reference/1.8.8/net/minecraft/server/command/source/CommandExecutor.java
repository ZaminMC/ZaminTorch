package net.minecraft.server.command.source;

import io.netty.buffer.ByteBuf;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.handler.CommandHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.world.World;

public abstract class CommandExecutor implements CommandSource {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private int successCount;
    private boolean trackOutput = true;
    private Text lastOutput = null;
    private String command = "";
    private String name = "@";
    private final CommandResults results = new CommandResults();

    public int getSuccessCount() {
        return this.successCount;
    }

    public Text getLastOutput() {
        return this.lastOutput;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putString("Command", this.command);
        nbt.putInt("SuccessCount", this.successCount);
        nbt.putString("CustomName", this.name);
        nbt.putBoolean("TrackOutput", this.trackOutput);
        if (this.lastOutput != null && this.trackOutput) {
            nbt.putString("LastOutput", Text.Serializer.toJson(this.lastOutput));
        }

        this.results.writeNbt(nbt);
    }

    public void readNbt(NbtCompound nbt) {
        this.command = nbt.getString("Command");
        this.successCount = nbt.getInt("SuccessCount");
        if (nbt.contains("CustomName", 8)) {
            this.name = nbt.getString("CustomName");
        }

        if (nbt.contains("TrackOutput", 1)) {
            this.trackOutput = nbt.getBoolean("TrackOutput");
        }

        if (nbt.contains("LastOutput", 8) && this.trackOutput) {
            this.lastOutput = Text.Serializer.fromJson(nbt.getString("LastOutput"));
        }

        this.results.readNbt(nbt);
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        return permissionLevel <= 2;
    }

    public void setCommand(String command) {
        this.command = command;
        this.successCount = 0;
    }

    public String getCommand() {
        return this.command;
    }

    public void run(World world) {
        if (world.isClient) {
            this.successCount = 0;
        }

        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        if (minecraftserver != null && minecraftserver.hasGameDirectory() && minecraftserver.areCommandBlocksEnabled()) {
            CommandHandler commandhandler = minecraftserver.getCommandHandler();

            try {
                this.lastOutput = null;
                this.successCount = commandhandler.run(this, this.command);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Executing command block");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Command to be executed");
                crashreportcategory.add("Command", new Callable<String>() {
                    public String call() throws Exception {
                        return CommandExecutor.this.getCommand();
                    }
                });
                crashreportcategory.add("Name", new Callable<String>() {
                    public String call() throws Exception {
                        return CommandExecutor.this.getName();
                    }
                });
                throw new CrashException(crashreport);
            }
        } else {
            this.successCount = 0;
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Text getDisplayName() {
        return new LiteralText(this.getName());
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void sendMessage(Text message) {
        if (this.trackOutput && this.getCommandSourceWorld() != null && !this.getCommandSourceWorld().isClient) {
            this.lastOutput = new LiteralText("[" + DATE_FORMAT.format(new Date()) + "] ").append(message);
            this.markDirty();
        }
    }

    @Override
    public boolean sendCommandSuccessToOps() {
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        return minecraftserver == null || !minecraftserver.hasGameDirectory() || minecraftserver.worlds[0].getGameRules().getBoolean("commandBlockOutput");
    }

    @Override
    public void addResult(CommandResults.Type type, int result) {
        this.results.add(this, type, result);
    }

    public abstract void markDirty();

    public abstract int getType();

    public abstract void writeInfo(ByteBuf buffer);

    public void setLastOutput(Text output) {
        this.lastOutput = output;
    }

    public void setTrackOutput(boolean trackOutput) {
        this.trackOutput = trackOutput;
    }

    public boolean trackOutput() {
        return this.trackOutput;
    }

    public boolean openScreen(PlayerEntity player) {
        if (!player.abilities.creativeMode) {
            return false;
        }

        if (player.getCommandSourceWorld().isClient) {
            player.openCommandBlockMenu(this);
        }

        return true;
    }

    public CommandResults getResults() {
        return this.results;
    }
}
