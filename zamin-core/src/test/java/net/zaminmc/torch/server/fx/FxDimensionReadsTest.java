package net.zaminmc.torch.server.fx;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wave TF-12 — the FX family's dimension stamps (the reference's "the
 * World the sound is played on"): every event on the bus carries the
 * emitting world's dimension — the reference's
 * ServerWorldEventListener resolves {@code this.world.dimension.getId()}
 * and PlayerManager.sendPacket (lines 630-640) refuses every observer
 * standing in another dimension ahead of the range test. The wire's
 * observer gate lives in the adapter's deliverFx; this test pins the
 * ENGINE half — the stamps themselves, every emit form.
 */
class FxDimensionReadsTest {

    /** Captures every event's dimension, one row per event. */
    private static final class Recording implements FxManager.Listener {
        final List<String> kinds = new ArrayList<>();
        final List<Integer> dimensions = new ArrayList<>();

        @Override
        public void onFx(FxManager.FxEvent event) {
            kinds.add(event.getClass().getSimpleName());
            dimensions.add(event.dimension());
        }
    }

    private static Position at(double x, double y, double z) {
        return new Position(x, y, z);
    }

    @Test
    void everySoundFormStampsItsDimension() {
        FxManager fx = new FxManager();
        Recording recorder = new Recording();
        fx.addListener(recorder);

        fx.sound(at(0, 64, 0), "random.bow", 1.0f, 1.0f, -1);
        fx.sound(at(1, 64, 1), "random.bow", 1.0f, 1.0f, 1);
        fx.sound(at(2, 64, 2), "random.bow", 0.5f, 1.0f);
        fx.sound(at(3, 64, 3), "random.explode");
        assertEquals(List.of("Sound", "Sound", "Sound", "Sound"), recorder.kinds);
        assertEquals(List.of(-1, 1, 0, 0), recorder.dimensions,
                "the dimension-carrying forms stamp their own world; the "
                        + "no-dimension overloads stamp the overworld 0 (the "
                        + "overworld-bound systems' own ledged binding)");
    }

    @Test
    void everyParticleAndWorldEventFormStampsItsDimension() {
        FxManager fx = new FxManager();
        Recording recorder = new Recording();
        fx.addListener(recorder);

        net.zaminmc.torch.server.block.EngineBlockType stone =
                net.zaminmc.torch.server.block.BuiltinBlocks.STONE;
        ItemStack stack = ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.STICK, 1);

        fx.blockShatter(at(0, 64, 0), stone, -1);
        fx.blockShatter(at(1, 64, 1), stone);
        fx.standShatter(at(2, 64, 2), 1);
        fx.standShatter(at(3, 64, 3));
        fx.itemShatter(at(4, 64, 4), stack, -1);
        fx.itemShatter(at(5, 64, 5), stack);
        fx.poof(at(6, 64, 6), 1);
        fx.poof(at(7, 64, 7));
        fx.smoke(at(8, 64, 8), -1);
        fx.smoke(at(9, 64, 9));
        fx.potionSplash(at(10, 64, 10), 32732, 1);
        fx.potionSplash(at(11, 64, 11), 32732);
        fx.worldEvent(at(12, 64, 12), 2005, 0, -1);
        fx.worldEvent(at(13, 64, 13), 2000, 1);

        assertEquals(List.of(-1, 0, 1, 0, -1, 0, 1, 0, -1, 0, 1, 0, -1, 0),
                recorder.dimensions,
                "every particle and world-event form carries its own world; "
                        + "the no-dimension overloads keep the overworld stamp");
        assertEquals(List.of(
                        "BlockShatter", "BlockShatter",
                        "StandShatter", "StandShatter",
                        "ItemShatter", "ItemShatter",
                        "Poof", "Poof",
                        "Smoke", "Smoke",
                        "PotionSplash", "PotionSplash",
                        "WorldEvent", "WorldEvent"),
                recorder.kinds);
    }

    @Test
    void theProjectileShatterCarriesTheProjectilesDimension() {
        // The egg's shard break (the splat puff + the glassy click) stamps
        // the projectile's own dimension — the family's TF-9 field riding
        // the FX bus. (The fishing splash's own stamp — the bobber's
        // dimension through the CatchSink — is pinned beside its family in
        // FishingSystemTest.theSplashSoundCarriesTheBobbersDimension.)
        net.zaminmc.torch.server.entity.projectile.ProjectileManager.SolidQuery solid =
                new net.zaminmc.torch.server.entity.projectile.ProjectileManager.SolidQuery() {
                    @Override
                    public void setActiveDimension(int dimension) {
                    }

                    @Override
                    public boolean solid(double x, double y, double z) {
                        return y <= 1.0; // the flat floor both worlds share
                    }
                };
        net.zaminmc.torch.server.entity.projectile.ProjectileManager.HitResolver noHits =
                new net.zaminmc.torch.server.entity.projectile.ProjectileManager.HitResolver() {
                    @Override
                    public net.zaminmc.torch.server.entity.MobEntity mobAt(
                            Position point, int dimension) {
                        return null;
                    }

                    @Override
                    public net.zaminmc.torch.server.player.PlayerSession playerAt(
                            Position point, int dimension) {
                        return null;
                    }
                };
        net.zaminmc.torch.server.entity.projectile.ProjectileManager.CombatSink combat =
                new net.zaminmc.torch.server.entity.projectile.ProjectileManager.CombatSink() {
                    @Override
                    public boolean mobHit(net.zaminmc.torch.server.entity.MobEntity mob,
                            float damage, double kbYaw, int shooterId) {
                        return true;
                    }

                    @Override
                    public void playerHit(net.zaminmc.torch.server.player.PlayerSession player,
                            float damage, double kbYaw) {
                    }

                    @Override
                    public void chickenHatch(Position position, int dimension) {
                    }
                };
        FxManager fx = new FxManager();
        List<String> kinds = new ArrayList<>();
        List<Integer> dimensions = new ArrayList<>();
        fx.addListener(event -> {
            kinds.add(event.getClass().getSimpleName());
            dimensions.add(event.dimension());
        });
        net.zaminmc.torch.server.entity.projectile.ProjectileManager manager =
                new net.zaminmc.torch.server.entity.projectile.ProjectileManager(
                        solid, noHits, combat, fx, new java.util.Random(41L), 1);

        manager.launchVector(
                net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.EGG,
                -1, at(0, 40, 0), 0.0f, -90.0f, 0.0f, 1.0, 6.0, -1);
        for (int i = 0; i < 60 && kinds.isEmpty(); i++) {
            manager.tick(List.of());
        }
        assertEquals(List.of("Poof", "Sound"), kinds,
                "the egg's landing burst and glass sound ride the bus");
        assertEquals(List.of(-1, -1), dimensions,
                "both halves of the shatter play in the projectile's own world");
    }
}
