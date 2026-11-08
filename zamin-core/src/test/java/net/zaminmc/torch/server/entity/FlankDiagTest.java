package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** TEMPORARY diagnostic — delete before the commit. */
class FlankDiagTest {

    private static final class WallWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        int wallX = Integer.MIN_VALUE;

        @Override
        public boolean isSolid(double x, double y, double z) {
            if (Math.floor(y) < 8.0) {
                return true;
            }
            return wallX != Integer.MIN_VALUE
                    && (int) Math.floor(x) == wallX
                    && Math.floor(y) < 13.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }

        @Override
        public List<MobEntity> hostileMobsInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            List<MobEntity> found = new ArrayList<>();
            for (MobEntity mob : mobs) {
                if (mob.dead() || !mob.type().hostile) {
                    continue;
                }
                Position p = mob.position();
                double half = mob.type().width / 2.0;
                if (p.x() + half > minX && p.x() - half < maxX
                        && p.y() + mob.type().height > minY && p.y() < maxY
                        && p.z() + half > minZ && p.z() - half < maxZ) {
                    found.add(mob);
                }
            }
            return found;
        }
    }

    private static MobManager managerIn(WallWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                996_500, (x, z) -> 8);
    }

    @Test
    void diag() {
        WallWorld world = new WallWorld();
        world.wallX = 8;
        MobManager manager = managerIn(world, 93L);
        MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM,
                new Position(7.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE,
                new Position(20.5, 8.0, 0.5));
        world.mobs.add(golem);
        world.mobs.add(zombie);
        zombie.setNoAi(true);
        golem.setMobAttackTarget(zombie);
        for (int tick = 0; tick < 40; tick++) {
            manager.tick(List.of(), 6000L);
            Position p = golem.position();
            System.out.println("DIAG t=" + tick + " pos=(" + String.format("%.4f", p.x())
                    + "," + String.format("%.4f", p.z()) + ") mode=" + golem.mode()
                    + " pathNull=" + (golem.navigationPath() == null)
                    + " pathDone=" + (golem.navigationPath() != null
                            && golem.navigationPath().isDone()));
        }
    }
}
