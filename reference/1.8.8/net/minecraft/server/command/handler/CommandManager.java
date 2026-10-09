package net.minecraft.server.command.handler;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.server.Console;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.AchievementCommand;
import net.minecraft.server.command.BlockDataCommand;
import net.minecraft.server.command.ClearCommand;
import net.minecraft.server.command.CloneCommand;
import net.minecraft.server.command.Command;
import net.minecraft.server.command.DebugCommand;
import net.minecraft.server.command.DefaultGameModeCommand;
import net.minecraft.server.command.DifficultyCommand;
import net.minecraft.server.command.EffectCommand;
import net.minecraft.server.command.EnchantCommand;
import net.minecraft.server.command.EntityDataCommand;
import net.minecraft.server.command.ExecuteCommand;
import net.minecraft.server.command.ExperienceCommand;
import net.minecraft.server.command.FillCommand;
import net.minecraft.server.command.GameModeCommand;
import net.minecraft.server.command.GameRuleCommand;
import net.minecraft.server.command.GiveCommand;
import net.minecraft.server.command.HelpCommand;
import net.minecraft.server.command.KillCommand;
import net.minecraft.server.command.MeCommand;
import net.minecraft.server.command.ParticleCommand;
import net.minecraft.server.command.PlaySoundCommand;
import net.minecraft.server.command.ReplaceItemCommand;
import net.minecraft.server.command.SayCommand;
import net.minecraft.server.command.ScoreboardCommand;
import net.minecraft.server.command.SeedCommand;
import net.minecraft.server.command.SetBlockCommand;
import net.minecraft.server.command.SetWorldSpawnCommand;
import net.minecraft.server.command.SpawnPointCommand;
import net.minecraft.server.command.SpreadPlayersCommand;
import net.minecraft.server.command.StatsCommand;
import net.minecraft.server.command.SummonCommand;
import net.minecraft.server.command.TellCommand;
import net.minecraft.server.command.TellRawCommand;
import net.minecraft.server.command.TestForBlockCommand;
import net.minecraft.server.command.TestForBlocksCommand;
import net.minecraft.server.command.TestForCommand;
import net.minecraft.server.command.TimeCommand;
import net.minecraft.server.command.TitleCommand;
import net.minecraft.server.command.ToggleDownfallCommand;
import net.minecraft.server.command.TpCommand;
import net.minecraft.server.command.TriggerCommand;
import net.minecraft.server.command.WeatherCommand;
import net.minecraft.server.command.WorldBorderCommand;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.dedicated.command.BanCommand;
import net.minecraft.server.dedicated.command.BanIpCommand;
import net.minecraft.server.dedicated.command.BanListCommand;
import net.minecraft.server.dedicated.command.DeOpCommand;
import net.minecraft.server.dedicated.command.KickCommand;
import net.minecraft.server.dedicated.command.ListCommand;
import net.minecraft.server.dedicated.command.OpCommand;
import net.minecraft.server.dedicated.command.PardonCommand;
import net.minecraft.server.dedicated.command.PardonIpCommand;
import net.minecraft.server.dedicated.command.SaveAllCommand;
import net.minecraft.server.dedicated.command.SaveOffCommand;
import net.minecraft.server.dedicated.command.SaveOnCommand;
import net.minecraft.server.dedicated.command.SetIdleTimeoutCommand;
import net.minecraft.server.dedicated.command.StopCommand;
import net.minecraft.server.dedicated.command.WhitelistCommand;
import net.minecraft.server.integrated.command.PublishCommand;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class CommandManager extends CommandRegistry implements CommandListener {
    public CommandManager() {
        this.register(new TimeCommand());
        this.register(new GameModeCommand());
        this.register(new DifficultyCommand());
        this.register(new DefaultGameModeCommand());
        this.register(new KillCommand());
        this.register(new ToggleDownfallCommand());
        this.register(new WeatherCommand());
        this.register(new ExperienceCommand());
        this.register(new TpCommand());
        this.register(new GiveCommand());
        this.register(new ReplaceItemCommand());
        this.register(new StatsCommand());
        this.register(new EffectCommand());
        this.register(new EnchantCommand());
        this.register(new ParticleCommand());
        this.register(new MeCommand());
        this.register(new SeedCommand());
        this.register(new HelpCommand());
        this.register(new DebugCommand());
        this.register(new TellCommand());
        this.register(new SayCommand());
        this.register(new SpawnPointCommand());
        this.register(new SetWorldSpawnCommand());
        this.register(new GameRuleCommand());
        this.register(new ClearCommand());
        this.register(new TestForCommand());
        this.register(new SpreadPlayersCommand());
        this.register(new PlaySoundCommand());
        this.register(new ScoreboardCommand());
        this.register(new ExecuteCommand());
        this.register(new TriggerCommand());
        this.register(new AchievementCommand());
        this.register(new SummonCommand());
        this.register(new SetBlockCommand());
        this.register(new FillCommand());
        this.register(new CloneCommand());
        this.register(new TestForBlocksCommand());
        this.register(new BlockDataCommand());
        this.register(new TestForBlockCommand());
        this.register(new TellRawCommand());
        this.register(new WorldBorderCommand());
        this.register(new TitleCommand());
        this.register(new EntityDataCommand());
        if (MinecraftServer.getInstance().isDedicated()) {
            this.register(new OpCommand());
            this.register(new DeOpCommand());
            this.register(new StopCommand());
            this.register(new SaveAllCommand());
            this.register(new SaveOffCommand());
            this.register(new SaveOnCommand());
            this.register(new BanIpCommand());
            this.register(new PardonIpCommand());
            this.register(new BanCommand());
            this.register(new BanListCommand());
            this.register(new PardonCommand());
            this.register(new KickCommand());
            this.register(new ListCommand());
            this.register(new WhitelistCommand());
            this.register(new SetIdleTimeoutCommand());
        } else {
            this.register(new PublishCommand());
        }

        AbstractCommand.setListener(this);
    }

    @Override
    public void sendSuccess(CommandSource source, Command command, int flags, String message, Object... args) {
        boolean flag = true;
        MinecraftServer minecraftserver = MinecraftServer.getInstance();
        if (!source.sendCommandSuccessToOps()) {
            flag = false;
        }

        Text text = new TranslatableText("chat.type.admin", source.getName(), new TranslatableText(message, args));
        text.getStyle().setColor(Formatting.GRAY);
        text.getStyle().setItalic(true);
        if (flag) {
            for (PlayerEntity playerentity : minecraftserver.getPlayerManager().getAll()) {
                if (playerentity != source && minecraftserver.getPlayerManager().isOp(playerentity.getGameProfile()) && command.canUse(source)) {
                    boolean flag1 = source instanceof MinecraftServer && MinecraftServer.getInstance().broacastConsoleToOps();
                    boolean flag2 = source instanceof Console && MinecraftServer.getInstance().broadcastRconToOps();
                    if (flag1 || flag2 || !(source instanceof Console) && !(source instanceof MinecraftServer)) {
                        playerentity.sendMessage(text);
                    }
                }
            }
        }

        if (source != minecraftserver && minecraftserver.worlds[0].getGameRules().getBoolean("logAdminCommands")) {
            minecraftserver.sendMessage(text);
        }

        boolean flag3 = minecraftserver.worlds[0].getGameRules().getBoolean("sendCommandFeedback");
        if (source instanceof CommandExecutor) {
            flag3 = ((CommandExecutor)source).trackOutput();
        }

        if ((flags & 1) != 1 && flag3 || source instanceof MinecraftServer) {
            source.sendMessage(new TranslatableText(message, args));
        }
    }
}
