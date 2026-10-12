package net.minecraft.server.dedicated.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.storage.exception.SessionLockException;

public class SaveAllCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "save-all";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.save.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        source.sendMessage(new TranslatableText("commands.save.start"));
        if (minecraftserver.getPlayerManager() != null) {
            minecraftserver.getPlayerManager().saveAll();
        }

        try {
            for (int i = 0; i < minecraftserver.worlds.length; i++) {
                if (minecraftserver.worlds[i] != null) {
                    ServerWorld serverworld = minecraftserver.worlds[i];
                    boolean flag = serverworld.savingDisabled;
                    serverworld.savingDisabled = false;
                    serverworld.save(true, null);
                    serverworld.savingDisabled = flag;
                }
            }

            if (args.length > 0 && "flush".equals(args[0])) {
                source.sendMessage(new TranslatableText("commands.save.flushStart"));

                for (int j = 0; j < minecraftserver.worlds.length; j++) {
                    if (minecraftserver.worlds[j] != null) {
                        ServerWorld serverworld1 = minecraftserver.worlds[j];
                        boolean flag1 = serverworld1.savingDisabled;
                        serverworld1.savingDisabled = false;
                        serverworld1.flushChunks();
                        serverworld1.savingDisabled = flag1;
                    }
                }

                source.sendMessage(new TranslatableText("commands.save.flushEnd"));
            }
        } catch (SessionLockException sessionlockexception) {
            sendSuccess(source, this, "commands.save.failed", sessionlockexception.getMessage());
            return;
        }

        sendSuccess(source, this, "commands.save.success");
    }
}
