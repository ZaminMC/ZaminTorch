package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dispenser and dropper on the live engine (Slice 11a — the reference
 * port of DispenserBlock + DropperBlock + Bootstrap's behavior registry): the
 * powered activation (the TRIGGERED pair + the 4-tick reaction), the
 * projectile behaviors, the default item drop with the vanilla velocity
 * family, the dropper's container push (silent) and its item fallback, the
 * bucket pour/scoop, the TNT prime, the fire charge, the flint and the bone
 * meal arms, and the hopper's feed into the nine slots.
 */
class DispenserSystemTest {

    @TempDir
    Path dataDir;

    @Test
    void thePoweredDispenserReleasesItsItemAfterFourTicks() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 10, z = 10, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dispenser facing up, holding one diamond stack.
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(1, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 3));
                // The lever's stone mounts beside the dispenser: a powered
                // lever strongly drives the stone into the dispenser.
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            // Power ON: the TRIGGERED pair rises through the neighbor bus.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> DispenserBlocks.triggered(typeAt(server, x, y, z)),
                    "the powered dispenser raised its TRIGGERED pair");
            // The 4-tick reaction: the item leaves as an item entity (the
            // default drop — nothing but air above the dispenser).
            await(() -> server.dispensers().peek(dispenser) != null
                            && server.dispensers().peek(dispenser).getItem(0).count() == 2,
                    "the dispense consumed one item");
            assertTrue(itemDropNear(server, x, y + 1, z, "diamond"),
                    "the item entity spawned above the dispenser");
            // Power OFF: the pair clears, no re-dispense.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> !DispenserBlocks.triggered(typeAt(server, x, y, z)),
                    "the unpowered dispenser cleared its TRIGGERED pair");
            long deadline = System.currentTimeMillis() + 400;
            while (System.currentTimeMillis() < deadline) {
                if (server.dispensers().peek(dispenser) != null
                        && server.dispensers().peek(dispenser).getItem(0).count() != 2) {
                    throw new AssertionError("the unpowered dispenser re-dispensed");
                }
                sleep(20);
            }
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theUnbreakableFlintInTheDispenserNeverWears() throws Exception {
        // The reference's takeDamage gate (ItemStack.java line 197 — the
        // !isDamageable consult) over the dispenser's own flint arm: the
        // flagged steel lights its fire and keeps its stack whole —
        // wearOne's Unbreakable consult (Slice 94/G1).
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 50, z = 50, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dispenser facing up, holding the flagged steel.
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(1, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.FLINT_AND_STEEL, 1)
                        .withUnbreakable(true));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            // The 4-tick reaction: the fire lands above, the steel stays
            // pristine (never wears, never breaks, never spends).
            await(() -> server.world().getBlock(
                            new BlockPosition(x, y + 1, z)).equals(BuiltinBlocks.FIRE),
                    "the flagged steel still lit its fire");
            await(() -> server.dispensers().peek(dispenser) != null
                            && server.dispensers().peek(dispenser).getItem(0).count() == 1,
                    "the slot kept its steel");
            assertEquals(0, server.dispensers().peek(dispenser).getItem(0).damage(),
                    "the flagged steel never wore");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDispensedArrowBecomesAProjectile() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 30, z = 30, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dispenser facing east, holding arrows.
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(5, false));
                server.dispensers().getOrCreate(dispenser).setItem(1, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.ARROW, 4));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> projectileOfKind(server,
                    net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW)
                            != null,
                    "the arrow launched as a projectile");
            assertEquals(3, server.dispensers().peek(dispenser).getItem(1).count(),
                    "the slot lost one arrow");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFireChargeBecomesASmallFireball() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 50, z = 50, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dispenser faces east into open air; the lever's stone
                // mounts on the WEST side (the push-away arrangement — the
                // fireball needs the clear lane).
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(5, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.FIRE_CHARGE, 2));
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 2, y, z),
                        RedstoneBlocks.leverOf(2, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x - 2, y, z)));
            await(() -> projectileOfKind(server,
                    net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.SMALL_FIREBALL)
                            != null,
                    "the fire charge launched as a small fireball");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDropperPushesIntoTheContainerSilently() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 70, z = 70, y = 40;
            BlockPosition dropper = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dropper facing east into a chest.
                server.world().setBlock(dropper,
                        DispenserBlocks.dropperOf(5, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.CHEST);
                server.dispensers().getOrCreate(dropper).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 5));
                server.world().setBlock(new BlockPosition(x, y, z - 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 2),
                        RedstoneBlocks.leverOf(4, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y, z - 2)));
            await(() -> chestSlotCount(server, x + 1, y, z) >= 1,
                    "the dropper pushed one item into the chest");
            assertEquals(4, server.dispensers().peek(dropper).getItem(0).count(),
                    "the dropper kept the rest");
            // The push is silent: no item entity ever spawns.
            long deadline = System.currentTimeMillis() + 300;
            while (System.currentTimeMillis() < deadline) {
                assertFalse(itemDropNear(server, x + 1, y, z, "diamond"),
                        "the dropper's push spawned no item entity");
                sleep(20);
            }
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDropperDropsTheItemWhenNoContainerFacesIt() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 90, z = 90, y = 40;
            BlockPosition dropper = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dropper facing up: nothing above but air.
                server.world().setBlock(dropper,
                        DispenserBlocks.dropperOf(1, false));
                server.dispensers().getOrCreate(dropper).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.IRON_INGOT, 2));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> itemDropNear(server, x, y + 1, z, "iron_ingot"),
                    "the dropper dropped the item as an entity");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWaterBucketPoursAndBecomesAnEmptyBucket() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 110, z = 110, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dispenser facing up: the pour lands in the open cell.
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(1, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.WATER_BUCKET, 1));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> !typeAt(server, x, y + 1, z).equals(server.world().airType()),
                    "the water source landed in the cell above");
            await(() -> {
                DispenserBlockEntity container = server.dispensers().peek(dispenser);
                return container != null && container.getItem(0).type().identifier().value()
                        .equals("bucket");
            }, "the slot became an empty bucket");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theEmptyBucketScoopsTheSource() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 130, z = 130, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(1, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.BUCKET, 1));
                // A water source above the dispenser: the scoop target.
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                                net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> typeAt(server, x, y + 1, z).equals(server.world().airType()),
                    "the source left the cell");
            await(() -> {
                DispenserBlockEntity container = server.dispensers().peek(dispenser);
                return container != null && container.getItem(0).type().identifier().value()
                        .equals("water_bucket");
            }, "the slot became a water bucket");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDispensedTntPrimesAtTheTargetCell() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 150, z = 150, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The dispenser facing east into open air.
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(5, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.TNT, 2));
                server.world().setBlock(new BlockPosition(x, y, z - 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 2),
                        RedstoneBlocks.leverOf(4, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y, z - 2)));
            await(() -> tntCount(server) >= 1,
                    "the dispensed TNT primed as a body");
            await(() -> server.dispensers().peek(dispenser) != null
                            && server.dispensers().peek(dispenser).getItem(0).count() == 1,
                    "the slot lost one TNT");
            // The blast eventually fires (the fuse) — the primed count drops
            // back after the detonation consumes the bodies.
            await(() -> tntCount(server) == 0,
                    "the primed body detonated at the fuse end");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHopperFeedsTheDispenser() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 170, z = 170, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // The hopper above the dispenser feeds its nine slots.
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(0, false));
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(new BlockPosition(x, y + 1, z))
                        .setItem(0, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.ARROW, 4));
            });
            await(() -> {
                DispenserBlockEntity container = server.dispensers().peek(dispenser);
                return container != null && container.getItem(0).count() >= 1;
            }, "the hopper pushed an arrow into the dispenser's slot");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theEmptyDispenserClicksWithoutLosingAnything() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 190, z = 190, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(1, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> DispenserBlocks.triggered(typeAt(server, x, y, z)),
                    "the empty dispenser still raised its TRIGGERED pair");
            // Nothing spawns, nothing crashes — the empty click.
            long deadline = System.currentTimeMillis() + 300;
            while (System.currentTimeMillis() < deadline) {
                assertFalse(itemDropNear(server, x, y + 1, z, "diamond"),
                        "the empty dispenser dropped nothing");
                sleep(20);
            }
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The helpers
    // ------------------------------------------------------------------

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static int chestSlotCount(EngineServer server, int x, int y, int z) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() -> {
            var chest = server.chestManager().peek(new BlockPosition(x, y, z));
            int count = 0;
            if (chest != null) {
                for (ItemStack stack : chest.snapshotSlots()) {
                    if (!stack.isEmpty()) {
                        count += stack.count();
                    }
                }
            }
            result.set(count);
        });
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? 0 : result.get();
    }

    private static int tntCount(EngineServer server) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() ->
                result.set(server.tnt().primedBodies().size()));
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? 0 : result.get();
    }

    private static net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectileOfKind(
            EngineServer server, net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind kind) {
        AtomicReference<net.zaminmc.torch.server.entity.projectile.ProjectileEntity> found =
                new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var projectile : server.projectiles().all()) {
                    if (projectile.kind() == kind) {
                        found.set(projectile);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        // Return as soon as the check ran — the caller's await re-polls with
        // fresh world reads (a dispensed projectile lives one window).
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static boolean itemDropNear(EngineServer server, int x, int y, int z, String item) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var entity : server.itemEntities().all()) {
                    var at = entity.position();
                    if (Math.abs(at.x() - (x + 0.5)) <= 2.0 && Math.abs(at.y() - y) <= 2.0
                            && Math.abs(at.z() - (z + 0.5)) <= 2.0
                            && entity.stack().type().identifier().value().equals(item)) {
                        found.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        // Return as soon as the check ran (the caller's await re-polls every
        // 50ms — a dispensed entity lives near its spawn for about a second
        // before the pop velocity carries it off).
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot(int regionSizeChunks) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dpt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, regionSizeChunks);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
