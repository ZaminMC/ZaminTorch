package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The piston's entity displacement (the VIII13 slice — the reference's
 * MovingBlockEntity.moveEntities, lines 80-115): the interpolated
 * collision box over the moving carrier's flight, the push of the bodies
 * standing in it (the mobs, the items, the vehicles — the three solver
 * families), the amounts (the extending steps' delta + 0.0625 catch-up,
 * the completion's flat 0.25), the wall clip (a pushed body stops at the
 * block — the crush setup), and the slime-block fling (the velocity set
 * along the facing axis).
 */
class PistonEntityPushTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void theExtendingPistonPushesTheMobAlongTheFlightPath() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                // The piston facing east with the stone it pushes; the mob
                // stands in the stone's landing cell (the flight path).
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                mob.set(server.mobs().spawnAt(MobType.PIG,
                        new Position(x + 2.5, y, z + 0.5)));
            });
            await(() -> mob.get() != null, "the mob spawned");
            double before = mob.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            // The mob rode the flight: the extending steps' amounts (0.5625
            // at the half-step + 0.5625 at the arrival + the completion's
            // flat 0.25 over the landing cell) — the total displacement at
            // least the completion's own 0.25, clamped only by a wall (none
            // here — the flat world's open air).
            assertTrue(mob.get().position().x() > before + 0.24,
                    "the mob rode the piston's flight east (before " + before
                            + ", after " + mob.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePushClampsAtTheWall() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // The wall one cell past the landing: the pushed mob clamps
                // against it (the solver's clip — the crush setup).
                server.world().setBlock(new BlockPosition(x + 3, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 3, y + 1, z), BuiltinBlocks.STONE);
                mob.set(server.mobs().spawnAt(MobType.PIG,
                        new Position(x + 2.5, y, z + 0.5)));
            });
            await(() -> mob.get() != null, "the mob spawned");
            double before = mob.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            // The wall at x+3: the mob's box (0.9 wide) clamps at
            // (x+3) - 0.45 — the push never moves it INTO the wall.
            assertTrue(mob.get().position().x() <= x + 3 - 0.44,
                    "the mob clamped at the wall (x " + mob.get().position().x() + ")");
            assertTrue(mob.get().position().x() > before,
                    "the mob still rode the free part of the flight");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDroppedItemRidesThePush() throws Exception {
        EngineServer server = boot();
        try {
            int x = 50, z = 50, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> item =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                item.set(server.itemEntities().spawnDropAtBlock(
                        new Position(x + 2, y, z),
                        net.zaminmc.torch.item.ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 1),
                        0));
            });
            await(() -> item.get() != null, "the item spawned");
            double before = item.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(item.get().position().x() > before + 0.24,
                    "the item rode the piston's flight east (before " + before
                            + ", after " + item.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSlimeBlockFlingsTheBody() throws Exception {
        EngineServer server = boot();
        try {
            int x = 70, z = 70, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.MobEntity> mob =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                // The slime block the piston pushes (the engine's slime pad).
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.SLIME_BLOCK);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                mob.set(server.mobs().spawnAt(MobType.PIG,
                        new Position(x + 2.5, y, z + 0.5)));
            });
            await(() -> mob.get() != null, "the mob spawned");
            double before = mob.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            // The fling (MovingBlockEntity lines 94-107): the velocity SET
            // along the facing axis to the facing offset — the east fling's
            // +1.0 per tick decaying over the flight. A plain push moves a
            // body at most 0.8125; the fling flies it SEVERAL blocks east
            // (the velocity's own integration, the friction decay).
            await(() -> mob.get().position().x() > before + 1.5,
                    "the slime block flung the mob east");
            assertTrue(mob.get().position().x() > before + 1.5,
                    "the slime block flung the mob east (before " + before
                            + ", after " + mob.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The additive families (the VIII13 record's own follow-up rows: the
    // orbs, the stands, the primed TNT, the falling blocks, the
    // projectiles) — each family's own box test, displacement, and
    // slime-fling axis over the reference's uniform moveEntities law
    // (MovingBlockEntity lines 80-115: world.getEntities(null, box) pushes
    // EVERY body family, none exempted).
    // ------------------------------------------------------------------

    @Test
    void theExperienceOrbRidesThePushAndTheBoxMissesTheFarOrb() throws Exception {
        EngineServer server = boot();
        try {
            int x = 110, z = 110, y = 40;
            AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> orb =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> far =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // The in-path orb (the exact-position restored form — no pop
                // velocity) and a control two cells past the landing.
                orb.set(server.experienceOrbs().spawnRestored(
                        new Position(x + 2.5, y + 0.125, z + 0.5), 5, 0));
                far.set(server.experienceOrbs().spawnRestored(
                        new Position(x + 4.5, y + 0.125, z + 0.5), 5, 0));
            });
            await(() -> orb.get() != null && far.get() != null, "the orbs spawned");
            double before = orb.get().position().x();
            double farBefore = far.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(orb.get().position().x() > before + 0.24,
                    "the orb rode the piston's flight east (before " + before
                            + ", after " + orb.get().position().x() + ")");
            assertTrue(Math.abs(far.get().position().x() - farBefore) < 0.01,
                    "the orb outside the carrier's box never moved (before "
                            + farBefore + ", after " + far.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theArmorStandRidesThePushAndTheBoxMissesTheFarStand() throws Exception {
        EngineServer server = boot();
        try {
            int x = 130, z = 130, y = 40;
            AtomicReference<net.zaminmc.torch.server.armorstand.ArmorStand> stand =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // The stands' own floors: the in-path feet cell and the
                // control's (two past the landing).
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 4, y - 1, z), BuiltinBlocks.STONE);
                server.armorStands().place(new BlockPosition(x + 2, y - 1, z), 0, 1, 0.0f,
                        new java.util.Random(1));
                server.armorStands().place(new BlockPosition(x + 4, y - 1, z), 0, 1, 0.0f,
                        new java.util.Random(1));
                for (net.zaminmc.torch.server.armorstand.ArmorStand placed
                        : server.armorStands().allStands()) {
                    if ((int) Math.floor(placed.x()) == x + 2) {
                        stand.set(placed);
                    }
                }
            });
            await(() -> stand.get() != null, "the stands placed");
            double before = stand.get().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(stand.get().x() > before + 0.24,
                    "the armor stand rode the piston's flight east (before "
                            + before + ", after " + stand.get().x() + ")");
            for (net.zaminmc.torch.server.armorstand.ArmorStand placed
                    : server.armorStands().allStands()) {
                if ((int) Math.floor(placed.x()) == x + 4) {
                    assertTrue(Math.abs(placed.x() - (x + 4.5)) < 0.01,
                            "the stand outside the carrier's box never moved (x "
                                    + placed.x() + ")");
                }
            }
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePrimedTntRidesThePushAndTheBoxMissesTheFarBody() throws Exception {
        EngineServer server = boot();
        try {
            int x = 150, z = 150, y = 40;
            AtomicReference<net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt> tnt =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt> far =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                server.tnt().spawnPrimedAt(server.world(), x + 2.5, y, z + 0.5);
                server.tnt().spawnPrimedAt(server.world(), x + 4.5, y, z + 0.5);
                for (net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt primed
                        : server.tnt().allPrimed()) {
                    if (primed.x() < x + 3.5) {
                        tnt.set(primed);
                    } else {
                        far.set(primed);
                    }
                }
            });
            await(() -> tnt.get() != null && far.get() != null, "the primed bodies spawned");
            double before = tnt.get().x();
            double farBefore = far.get().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(tnt.get().x() > before + 0.24,
                    "the primed TNT rode the piston's flight east (before "
                            + before + ", after " + tnt.get().x() + ")");
            assertTrue(Math.abs(far.get().x() - farBefore) < 0.1,
                    "the primed TNT outside the carrier's box never moved (before "
                            + farBefore + ", after " + far.get().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFallingBlockRidesThePushAndTheBoxMissesTheFarBody() throws Exception {
        EngineServer server = boot();
        try {
            int x = 170, z = 170, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.FallingBlockEntity> falling =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.entity.FallingBlockEntity> far =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                falling.set(server.fallingEntities().startFall(
                        new BlockPosition(x + 2, y, z), BuiltinBlocks.SAND));
                far.set(server.fallingEntities().startFall(
                        new BlockPosition(x + 4, y, z), BuiltinBlocks.SAND));
            });
            await(() -> falling.get() != null && far.get() != null, "the falls started");
            double before = falling.get().position().x();
            double farBefore = far.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(falling.get().position().x() > before + 0.24,
                    "the falling block rode the piston's flight east (before "
                            + before + ", after " + falling.get().position().x() + ")");
            assertTrue(Math.abs(far.get().position().x() - farBefore) < 0.01,
                    "the falling block outside the carrier's box never moved (before "
                            + farBefore + ", after " + far.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theProjectileRidesThePushAndTheBoxMissesTheFarArrow() throws Exception {
        EngineServer server = boot();
        try {
            int x = 190, z = 190, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.projectile.ProjectileEntity> arrow =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.entity.projectile.ProjectileEntity> far =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // The zero-force arrows hover (the family's own gravity
                // pulls them through the band over the extension's ticks).
                arrow.set(server.projectiles().launchVector(
                        net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                        -1, new Position(x + 2.5, y + 0.5, z + 0.5), 1.0, 0.0, 0.0, 0.0, 0.0, 0));
                far.set(server.projectiles().launchVector(
                        net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                        -1, new Position(x + 4.5, y + 0.5, z + 0.5), 1.0, 0.0, 0.0, 0.0, 0.0, 0));
            });
            await(() -> arrow.get() != null && far.get() != null, "the arrows launched");
            double before = arrow.get().position().x();
            double farBefore = far.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");
            assertTrue(arrow.get().position().x() > before + 0.24,
                    "the arrow rode the piston's flight east (before "
                            + before + ", after " + arrow.get().position().x() + ")");
            assertTrue(Math.abs(far.get().position().x() - farBefore) < 0.01,
                    "the arrow outside the carrier's box never moved (before "
                            + farBefore + ", after " + far.get().position().x() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSlimeBlockFlingsTheOrbTheStandTheTntAndTheArrow() throws Exception {
        EngineServer server = boot();
        try {
            int x = 210, z = 210, y = 40;
            AtomicReference<net.zaminmc.torch.server.experience.ExperienceOrbEntity> orb =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.armorstand.ArmorStand> stand =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt> tnt =
                    new AtomicReference<>();
            AtomicReference<net.zaminmc.torch.server.entity.projectile.ProjectileEntity> arrow =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.SLIME_BLOCK);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
                orb.set(server.experienceOrbs().spawnRestored(
                        new Position(x + 2.5, y + 0.125, z + 0.4), 5, 0));
                server.armorStands().place(new BlockPosition(x + 2, y - 1, z), 0, 1, 0.0f,
                        new java.util.Random(1));
                server.tnt().spawnPrimedAt(server.world(), x + 2.5, y, z + 0.6);
                arrow.set(server.projectiles().launchVector(
                        net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                        -1, new Position(x + 2.5, y + 0.5, z + 0.2), 1.0, 0.0, 0.0, 0.0, 0.0, 0));
                for (net.zaminmc.torch.server.armorstand.ArmorStand placed
                        : server.armorStands().allStands()) {
                    stand.set(placed);
                }
                for (net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt primed
                        : server.tnt().allPrimed()) {
                    tnt.set(primed);
                }
            });
            await(() -> orb.get() != null && stand.get() != null && tnt.get() != null
                    && arrow.get() != null, "the four bodies in place");
            double orbBefore = orb.get().position().x();
            double standBefore = stand.get().x();
            double tntBefore = tnt.get().x();
            double arrowBefore = arrow.get().position().x();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            // The fling (lines 94-107): each family's velocity SET along the
            // facing axis, the family's own integration carrying it out — a
            // plain push moves a body at most 0.8125, the fling flies them.
            await(() -> orb.get().position().x() > orbBefore + 1.5
                    && stand.get().x() > standBefore + 1.0
                    && tnt.get().x() > tntBefore + 1.5
                    && arrow.get().position().x() > arrowBefore + 1.5,
                    "the four bodies flung east by the slime carrier");
            assertTrue(stand.get().x() > standBefore + 1.0,
                    "the stand slid east (the walk's friction decay): before "
                            + standBefore + ", after " + stand.get().x());
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theUpwardSlimeCarrierFlingsTheFallingBlock() throws Exception {
        EngineServer server = boot();
        try {
            int x = 290, z = 290, y = 40;
            AtomicReference<net.zaminmc.torch.server.entity.FallingBlockEntity> falling =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                // The UP-facing piston (the falling family's fling carrier is
                // the VERTICAL axis alone): the slime block one cell above,
                // the lever behind the base (the facing side is immune) on
                // its own support block (the up-mounted lever pops without
                // the block below — the core suite's own lesson).
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(1 /* UP */, false, false));
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.SLIME_BLOCK);
                server.world().setBlock(new BlockPosition(x - 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        RedstoneBlocks.leverOf(5, false));
                // The fall starts ONE cell above the slime's landing cell
                // (a body at the landing cell itself rests on the slime's
                // top and lands through the fall's own first tick — the
                // flight must still be airborne when the carrier arrives).
                falling.set(server.fallingEntities().startFall(
                        new BlockPosition(x, y + 3, z), BuiltinBlocks.SAND));
            });
            await(() -> falling.get() != null, "the fall started above the slime");
            double before = falling.get().position().y();
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x - 1, y, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston flipped to its extended pair");
            // The fling: the falling family's own carrier is the vertical
            // one — the UP axis's set component rides (velocityY = +1.0),
            // the fall's own integration carrying the body up before the
            // gravity reclaims it.
            await(() -> falling.get().position().y() > before + 0.4,
                    "the falling block flung upward by the slime carrier");
            assertTrue(falling.get().position().y() > before + 0.4,
                    "the falling block flung upward (before " + before
                            + ", after " + falling.get().position().y() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    private BlockType typeAt(EngineServer server, int x, int y, int z) {
        return server.world().getBlock(new BlockPosition(x, y, z));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ppt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
