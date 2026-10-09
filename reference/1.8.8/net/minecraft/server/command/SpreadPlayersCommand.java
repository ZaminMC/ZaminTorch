package net.minecraft.server.command;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.EntityNotFoundException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.exception.PlayerNotFoundException;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SpreadPlayersCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "spreadplayers";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.spreadplayers.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 6) {
            throw new IncorrectUsageException("commands.spreadplayers.usage");
        }

        int i = 0;
        BlockPos blockpos = source.getCommandSourceBlockPos();
        double d0 = parseCoordinate(blockpos.getX(), args[i++], true);
        double d1 = parseCoordinate(blockpos.getZ(), args[i++], true);
        double d2 = parseDouble(args[i++], 0.0);
        double d3 = parseDouble(args[i++], d2 + 1.0);
        boolean flag = parseBoolean(args[i++]);
        List<Entity> list = Lists.newArrayList();

        while (i < args.length) {
            String s = args[i++];
            if (TargetSelector.isValid(s)) {
                List<Entity> list1 = TargetSelector.select(source, s, Entity.class);
                if (list1.size() == 0) {
                    throw new EntityNotFoundException();
                }

                list.addAll(list1);
            } else {
                PlayerEntity playerentity = MinecraftServer.getInstance().getPlayerManager().get(s);
                if (playerentity == null) {
                    throw new PlayerNotFoundException();
                }

                list.add(playerentity);
            }
        }

        source.addResult(CommandResults.Type.AFFECTED_ENTITIES, list.size());
        if (list.isEmpty()) {
            throw new EntityNotFoundException();
        }

        source.sendMessage(new TranslatableText("commands.spreadplayers.spreading." + (flag ? "teams" : "players"), list.size(), d3, d0, d1, d2));
        this.spreadPlayers(source, list, new SpreadPlayersCommand.Pile(d0, d1), d2, d3, list.get(0).world, flag);
    }

    private void spreadPlayers(
        CommandSource source, List<Entity> players, SpreadPlayersCommand.Pile pile, double spreadDistance, double maxRange, World world, boolean teams
    ) throws CommandException {
        Random random = new Random();
        double d0 = pile.x - maxRange;
        double d1 = pile.z - maxRange;
        double d2 = pile.x + maxRange;
        double d3 = pile.z + maxRange;
        SpreadPlayersCommand.Pile[] aspreadplayerscommand$pile = this.makePiles(random, teams ? this.getTeamCount(players) : players.size(), d0, d1, d2, d3);
        int i = this.spreadPlayers(pile, spreadDistance, world, random, d0, d1, d2, d3, aspreadplayerscommand$pile, teams);
        double d4 = this.teleportPlayers(players, world, aspreadplayerscommand$pile, teams);
        sendSuccess(source, this, "commands.spreadplayers.success." + (teams ? "teams" : "players"), aspreadplayerscommand$pile.length, pile.x, pile.z);
        if (aspreadplayerscommand$pile.length > 1) {
            source.sendMessage(new TranslatableText("commands.spreadplayers.info." + (teams ? "teams" : "players"), String.format("%.2f", d4), i));
        }
    }

    private int getTeamCount(List<Entity> entities) {
        Set<AbstractTeam> set = Sets.newHashSet();

        for (Entity entity : entities) {
            if (entity instanceof PlayerEntity) {
                set.add(((PlayerEntity)entity).getScoreboardTeam());
            } else {
                set.add(null);
            }
        }

        return set.size();
    }

    private int spreadPlayers(
        SpreadPlayersCommand.Pile pile,
        double spreadDistance,
        World world,
        Random rand,
        double minX,
        double minZ,
        double maxX,
        double maxZ,
        SpreadPlayersCommand.Pile[] piles,
        boolean teams
    ) throws CommandException {
        boolean flag = true;
        double d0 = Float.MAX_VALUE;

        int i;
        for (i = 0; i < 10000 && flag; i++) {
            flag = false;
            d0 = Float.MAX_VALUE;

            for (int j = 0; j < piles.length; j++) {
                SpreadPlayersCommand.Pile spreadplayerscommand$pile = piles[j];
                int k = 0;
                SpreadPlayersCommand.Pile spreadplayerscommand$pile1 = new SpreadPlayersCommand.Pile();

                for (int l = 0; l < piles.length; l++) {
                    if (j != l) {
                        SpreadPlayersCommand.Pile spreadplayerscommand$pile2 = piles[l];
                        double d1 = spreadplayerscommand$pile.distanceTo(spreadplayerscommand$pile2);
                        d0 = Math.min(d1, d0);
                        if (d1 < spreadDistance) {
                            k++;
                            spreadplayerscommand$pile1.x = spreadplayerscommand$pile1.x + (spreadplayerscommand$pile2.x - spreadplayerscommand$pile.x);
                            spreadplayerscommand$pile1.z = spreadplayerscommand$pile1.z + (spreadplayerscommand$pile2.z - spreadplayerscommand$pile.z);
                        }
                    }
                }

                if (k > 0) {
                    spreadplayerscommand$pile1.x /= k;
                    spreadplayerscommand$pile1.z /= k;
                    double d2 = spreadplayerscommand$pile1.absolute();
                    if (d2 > 0.0) {
                        spreadplayerscommand$pile1.normalize();
                        spreadplayerscommand$pile.subtract(spreadplayerscommand$pile1);
                    } else {
                        spreadplayerscommand$pile.setPileLocation(rand, minX, minZ, maxX, maxZ);
                    }

                    flag = true;
                }

                if (spreadplayerscommand$pile.clamp(minX, minZ, maxX, maxZ)) {
                    flag = true;
                }
            }

            if (!flag) {
                for (SpreadPlayersCommand.Pile spreadplayerscommand$pile3 : piles) {
                    if (!spreadplayerscommand$pile3.isNotFireOrLiquidAtTopOfWorld(world)) {
                        spreadplayerscommand$pile3.setPileLocation(rand, minX, minZ, maxX, maxZ);
                        flag = true;
                    }
                }
            }
        }

        if (i >= 10000) {
            throw new CommandException(
                "commands.spreadplayers.failure." + (teams ? "teams" : "players"), piles.length, pile.x, pile.z, String.format("%.2f", d0)
            );
        } else {
            return i;
        }
    }

    private double teleportPlayers(List<Entity> players, World world, SpreadPlayersCommand.Pile[] piles, boolean teams) {
        double d0 = 0.0;
        int i = 0;
        Map<AbstractTeam, SpreadPlayersCommand.Pile> map = Maps.newHashMap();

        for (int j = 0; j < players.size(); j++) {
            Entity entity = players.get(j);
            SpreadPlayersCommand.Pile spreadplayerscommand$pile;
            if (teams) {
                AbstractTeam abstractteam = entity instanceof PlayerEntity ? ((PlayerEntity)entity).getScoreboardTeam() : null;
                if (!map.containsKey(abstractteam)) {
                    map.put(abstractteam, piles[i++]);
                }

                spreadplayerscommand$pile = map.get(abstractteam);
            } else {
                spreadplayerscommand$pile = piles[i++];
            }

            entity.teleport(
                MathHelper.floor(spreadplayerscommand$pile.x) + 0.5F,
                spreadplayerscommand$pile.getAboveHighestNonAir(world),
                MathHelper.floor(spreadplayerscommand$pile.z) + 0.5
            );
            double d2 = Double.MAX_VALUE;

            for (int k = 0; k < piles.length; k++) {
                if (spreadplayerscommand$pile != piles[k]) {
                    double d1 = spreadplayerscommand$pile.distanceTo(piles[k]);
                    d2 = Math.min(d1, d2);
                }
            }

            d0 += d2;
        }

        return d0 / players.size();
    }

    private SpreadPlayersCommand.Pile[] makePiles(Random random, int count, double minX, double minZ, double maxX, double maxZ) {
        SpreadPlayersCommand.Pile[] aspreadplayerscommand$pile = new SpreadPlayersCommand.Pile[count];

        for (int i = 0; i < aspreadplayerscommand$pile.length; i++) {
            SpreadPlayersCommand.Pile spreadplayerscommand$pile = new SpreadPlayersCommand.Pile();
            spreadplayerscommand$pile.setPileLocation(random, minX, minZ, maxX, maxZ);
            aspreadplayerscommand$pile[i] = spreadplayerscommand$pile;
        }

        return aspreadplayerscommand$pile;
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        return args.length >= 1 && args.length <= 2 ? suggestHorizontalCoordinate(args, 0, pos) : null;
    }

    static class Pile {
        double x;
        double z;

        Pile() {
        }

        Pile(double x, double z) {
            this.x = x;
            this.z = z;
        }

        double distanceTo(SpreadPlayersCommand.Pile other) {
            double d0 = this.x - other.x;
            double d1 = this.z - other.z;
            return Math.sqrt(d0 * d0 + d1 * d1);
        }

        void normalize() {
            double d0 = this.absolute();
            this.x /= d0;
            this.z /= d0;
        }

        float absolute() {
            return MathHelper.sqrt(this.x * this.x + this.z * this.z);
        }

        public void subtract(SpreadPlayersCommand.Pile other) {
            this.x = this.x - other.x;
            this.z = this.z - other.z;
        }

        public boolean clamp(double minX, double minZ, double maxX, double maxZ) {
            boolean flag = false;
            if (this.x < minX) {
                this.x = minX;
                flag = true;
            } else if (this.x > maxX) {
                this.x = maxX;
                flag = true;
            }

            if (this.z < minZ) {
                this.z = minZ;
                flag = true;
            } else if (this.z > maxZ) {
                this.z = maxZ;
                flag = true;
            }

            return flag;
        }

        public int getAboveHighestNonAir(World world) {
            BlockPos blockpos = new BlockPos(this.x, 256.0, this.z);

            while (blockpos.getY() > 0) {
                blockpos = blockpos.down();
                if (world.getBlockState(blockpos).getBlock().getMaterial() != Material.AIR) {
                    return blockpos.getY() + 1;
                }
            }

            return 257;
        }

        public boolean isNotFireOrLiquidAtTopOfWorld(World world) {
            BlockPos blockpos = new BlockPos(this.x, 256.0, this.z);

            while (blockpos.getY() > 0) {
                blockpos = blockpos.down();
                Material material = world.getBlockState(blockpos).getBlock().getMaterial();
                if (material != Material.AIR) {
                    return !material.isLiquid() && material != Material.FIRE;
                }
            }

            return false;
        }

        public void setPileLocation(Random random, double minX, double minZ, double maxX, double maxZ) {
            this.x = MathHelper.nextDouble(random, minX, maxX);
            this.z = MathHelper.nextDouble(random, minZ, maxZ);
        }
    }
}
