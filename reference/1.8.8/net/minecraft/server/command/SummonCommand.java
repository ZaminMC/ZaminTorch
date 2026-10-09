package net.minecraft.server.command;

import java.util.List;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SummonCommand extends AbstractCommand {
    @Override
    public String getName() {
        return "summon";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(CommandSource source) {
        return "commands.summon.usage";
    }

    @Override
    public void run(CommandSource source, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new IncorrectUsageException("commands.summon.usage");
        }

        String s = args[0];
        BlockPos blockpos = source.getCommandSourceBlockPos();
        Vec3d vec3d = source.getCommandSourcePos();
        double d0 = vec3d.x;
        double d1 = vec3d.y;
        double d2 = vec3d.z;
        if (args.length >= 4) {
            d0 = parseCoordinate(d0, args[1], true);
            d1 = parseCoordinate(d1, args[2], false);
            d2 = parseCoordinate(d2, args[3], true);
            blockpos = new BlockPos(d0, d1, d2);
        }

        World world = source.getCommandSourceWorld();
        if (!world.isChunkLoaded(blockpos)) {
            throw new CommandException("commands.summon.outOfWorld");
        }

        if ("LightningBolt".equals(s)) {
            world.addGlobalEntity(new LightningBoltEntity(world, d0, d1, d2));
            sendSuccess(source, this, "commands.summon.success");
        } else {
            NbtCompound nbtcompound = new NbtCompound();
            boolean flag = false;
            if (args.length >= 5) {
                Text text = parseText(source, args, 4);

                try {
                    nbtcompound = SnbtParser.parse(text.getString());
                    flag = true;
                } catch (NbtException nbtexception) {
                    throw new CommandException("commands.summon.tagError", nbtexception.getMessage());
                }
            }

            nbtcompound.putString("id", s);

            Entity entity2;
            try {
                entity2 = Entities.create(nbtcompound, world);
            } catch (RuntimeException runtimeexception) {
                throw new CommandException("commands.summon.failed");
            }

            if (entity2 == null) {
                throw new CommandException("commands.summon.failed");
            }

            entity2.setPositionAndAngles(d0, d1, d2, entity2.yaw, entity2.pitch);
            if (!flag && entity2 instanceof MobEntity) {
                ((MobEntity)entity2).initialize(world.getLocalDifficulty(new BlockPos(entity2)), null);
            }

            world.addEntity(entity2);
            Entity entity = entity2;

            for (NbtCompound nbtcompound1 = nbtcompound;
                entity != null && nbtcompound1.contains("Riding", 10);
                nbtcompound1 = nbtcompound1.getCompound("Riding")
            ) {
                Entity entity1 = Entities.create(nbtcompound1.getCompound("Riding"), world);
                if (entity1 != null) {
                    entity1.setPositionAndAngles(d0, d1, d2, entity1.yaw, entity1.pitch);
                    world.addEntity(entity1);
                    entity.startRiding(entity1);
                }

                entity = entity1;
            }

            sendSuccess(source, this, "commands.summon.success");
        }
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return suggestMatching(args, Entities.getKeys());
        } else {
            return args.length > 1 && args.length <= 4 ? suggestCoordinate(args, 1, pos) : null;
        }
    }
}
