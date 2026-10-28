package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.cauldron.CauldronBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cauldron's use-arm parity seats (the verifier's ninth pass —
 * CauldronBlock.use lines 89-159, verified against reference/1.8.8): the
 * fill's HAND-SLOT SET law (the empty bucket replaces the held water
 * bucket at the selected slot itself — never an inventory add, so a
 * bucket stack elsewhere absorbs nothing and a full inventory never
 * drops the remainder), the drain's survival-guard law (the stat and
 * the bottle's decrement ride INSIDE the guard — a creative hand drains
 * the level but pays neither), the drain's RIM-DROP law (a full
 * inventory drops the water potion at the cauldron's own rim, pos +
 * 0.5/+1.5/+0.5 — never the player's feet), and the leather wash
 * (ArmorItem.Tier.CLOTH with a color: the tint clears, one level
 * spends, the ARMOR_CLEANED stat lands, the piece is never consumed —
 * and the CLOTH gate spares every colored non-leather piece).
 */
class CauldronWashParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cwp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /** A cauldron at the given level, placed and awaited. */
    private void standCauldron(BlockPosition at, int level) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, CauldronBlocks.of(level)));
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == level,
                "the cauldron stands at level " + level);
    }

    private void hold(PlayerSession player, ItemStack stack)
            throws InterruptedException {
        server.ticker().submit(() -> {
            player.inventory().setSlot(0, stack);
            player.inventory().selectHotbarSlot(0);
        });
        // The whole-stack match (the type alone would race a same-type
        // swap — the plain-cap-then-dyed-cap sequence below).
        await(() -> {
            var held = player.inventory().held();
            return !held.isEmpty() && held.type().equals(stack.type())
                    && held.count() == stack.count()
                    && java.util.Objects.equals(held.displayColor(), stack.displayColor());
        }, "the held item armed");
    }

    @Test
    void theFillSetsTheHandSlotItself() throws Exception {
        BlockPosition at = new BlockPosition(20, 40, 20);
        standCauldron(at, 0);
        PlayerSession farmer = join("Filler", new Position(20.5, 40.0, 21.5));

        // A bucket STACK of 3 sits in slot 9 ahead of the fill: the
        // reference's setItem(selectedSlot, BUCKET) writes the hand slot
        // alone — the stack elsewhere absorbs NOTHING (the engine's old
        // consume-then-add shape merged the new bucket into it).
        server.ticker().submit(() -> player_setSlot(farmer, 9,
                ItemStack.of(BuiltinItems.BUCKET, 3)));
        hold(farmer, ItemStack.of(BuiltinItems.WATER_BUCKET, 1));
        server.useItemOnBlock(farmer, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 3,
                "the fill reached 3");
        await(() -> farmer.inventory().held().type() != null
                        && farmer.inventory().held().type().equals(BuiltinItems.BUCKET),
                "the empty bucket took the hand slot");
        assertEquals(1, farmer.inventory().held().count(),
                "the hand bucket is the fresh single");
        assertEquals(3, farmer.inventory().slot(9).count(),
                "the stack elsewhere absorbed nothing (the hand-slot set law)");
    }

    @Test
    void theFillAtAFullInventoryKeepsTheBucketInTheHand() throws Exception {
        BlockPosition at = new BlockPosition(21, 40, 21);
        standCauldron(at, 0);
        PlayerSession hoarder = join("Hoarder", new Position(21.5, 40.0, 22.5));

        // Every slot full of stone ahead of the fill: the bucket still
        // REPLACES the hand (the water bucket's slot empties by the fill
        // itself) — never a drop.
        server.ticker().submit(() -> {
            for (int slot = 0; slot < 36; slot++) {
                hoarder.inventory().setSlot(slot, ItemStack.of(BuiltinItems.STONE, 64));
            }
        });
        hold(hoarder, ItemStack.of(BuiltinItems.WATER_BUCKET, 1));
        server.useItemOnBlock(hoarder, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 3,
                "the fill reached 3");
        await(() -> hoarder.inventory().held().type() != null
                        && hoarder.inventory().held().type().equals(BuiltinItems.BUCKET),
                "the bucket sits in the hand despite the full inventory");
        assertEquals(0, server.itemEntities().size(),
                "nothing dropped (the hand-slot set law)");
    }

    @Test
    void theCreativeDrainPaysNeitherTheStatNorTheBottle() throws Exception {
        BlockPosition at = new BlockPosition(22, 40, 22);
        standCauldron(at, 2);
        PlayerSession free = join("Freehand", new Position(22.5, 40.0, 23.5));
        free.setGamemode(GameMode.CREATIVE);

        int usedBefore = free.stats().get(
                net.zaminmc.torch.server.stat.EngineStats.CAULDRONS_USED);
        hold(free, ItemStack.of(BuiltinItems.GLASS_BOTTLE, 1));
        server.useItemOnBlock(free, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 1,
                "the creative drain spent the level");
        // Lines 100-120: the stat and the decrement ride INSIDE the
        // survival guard alone — the creative hand keeps the bottle and
        // pays no stat (the level still drains, setLevel's own arm).
        assertEquals(usedBefore, free.stats().get(
                        net.zaminmc.torch.server.stat.EngineStats.CAULDRONS_USED),
                "the creative drain paid no stat");
        assertTrue(free.inventory().held().type().equals(BuiltinItems.GLASS_BOTTLE),
                "the creative hand kept the bottle");
        assertEquals(1, free.inventory().held().count(), "undecremented");
    }

    @Test
    void theFullInventoryDropsThePotionAtTheRim() throws Exception {
        BlockPosition at = new BlockPosition(23, 40, 23);
        standCauldron(at, 1);
        // Three blocks south — OUTSIDE the pickup sweep's reach (the
        // reference's OWN grown box, PlayerEntity lines 483-489: the
        // walking player's collision walk consults getShape().grown(1.0,
        // 0.5, 1.0), so an item at |dx| or |dz| under 1.3 + the item's
        // half 0.125 = 1.425 collects — the engine's withinPickupRange is
        // the 1:1 port, and a delay-0 rim drop inside the band is
        // collected within the first tick, so the seat observes from
        // beyond it; the drop would sit at the rim for ANY player outside
        // the band, vanilla and engine alike).
        PlayerSession packed = join("Packed", new Position(23.5, 40.0, 26.5));

        server.ticker().submit(() -> {
            for (int slot = 0; slot < 36; slot++) {
                packed.inventory().setSlot(slot, ItemStack.of(BuiltinItems.STONE, 64));
            }
        });
        hold(packed, ItemStack.of(BuiltinItems.GLASS_BOTTLE, 1));
        server.useItemOnBlock(packed, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 0,
                "the drain spent the level");
        // Line 105's rim drop: the potion entity lands AT THE CAULDRON
        // (pos + 0.5/+1.5/+0.5) — never the player's feet. The pop motion
        // drifts the body a little; the LAW is the drop sits with the
        // CAULDRON, at the rim's z band around 23.5 (the player stands at
        // z 26.5, three blocks south — a player-feet drop would sit
        // around z 26.5 instead).
        await(() -> server.itemEntities().size() >= 1, "the potion dropped");
        var dropped = server.itemEntities().all().get(0);
        double toRim = Math.hypot(dropped.position().x() - 23.5,
                dropped.position().z() - 23.5);
        double toPlayer = Math.hypot(dropped.position().x() - 23.5,
                dropped.position().z() - 26.5);
        assertTrue(toRim < toPlayer,
                "the drop sits at the cauldron's rim, not the player's feet");
        assertTrue(dropped.stack().type().equals(BuiltinItems.POTION),
                "the dropped stack is the potion");
        assertEquals(0, dropped.stack().damage(), "the water potion (metadata 0)");
        // The review fix's own pin (the reference's delay landscape): the
        // cauldron's rim drop builds the ItemEntity and calls addEntity
        // DIRECTLY — no setDefaultPickUpDelay, so pickUpDelay stands 0 and
        // the dropped potion is pickupable IMMEDIATELY (the 10-tick row
        // belongs to Block.java line 520's block drops and Entity.java
        // line 1255's entity drops alone; the cauldron path never rides
        // either).
        assertTrue(dropped.pickupAllowed(),
                "the rim drop carries no pickup delay (the ctor's own 0)");
    }

    @Test
    void theLeatherWashClearsTheTintAndSpendsTheLevel() throws Exception {
        BlockPosition at = new BlockPosition(24, 40, 24);
        standCauldron(at, 2);
        PlayerSession cleaner = join("Cleaner", new Position(24.5, 40.0, 25.5));

        // The dyed cap (the anvil import's own shape — the displayColor
        // row the NBT surface round-trips): the wash clears the tint,
        // spends one level, lands the ARMOR_CLEANED stat, and NEVER
        // consumes the piece (removeColor mutates the tag alone).
        ItemStack dyedCap = ItemStack.of(BuiltinItems.LEATHER_HELMET, 1)
                .withDisplayColor(0x00AAFF);
        assertNotNull(dyedCap.displayColor(), "the cap carries the tint");
        int cleanedBefore = cleaner.stats().get(
                net.zaminmc.torch.server.stat.EngineStats.ARMOR_CLEANED);
        hold(cleaner, dyedCap);
        server.useItemOnBlock(cleaner, at, 1, Optional.empty(), windowId -> { });
        await(() -> CauldronBlocks.levelOf(server.world().getBlock(at)) == 1,
                "the wash spent one level");
        await(() -> cleaner.inventory().held().displayColor() == null,
                "the tint cleared");
        assertTrue(cleaner.inventory().held().type().equals(BuiltinItems.LEATHER_HELMET),
                "the piece itself stays in the hand");
        assertEquals(1, cleaner.inventory().held().count(), "never consumed");
        assertEquals(cleanedBefore + 1, cleaner.stats().get(
                        net.zaminmc.torch.server.stat.EngineStats.ARMOR_CLEANED),
                "the ARMOR_CLEANED stat landed");
    }

    @Test
    void theUncoloredLeatherAndTheEmptyCauldronNeverWash() throws Exception {
        BlockPosition at = new BlockPosition(25, 40, 25);
        standCauldron(at, 1);
        PlayerSession bearer = join("Bearer", new Position(25.5, 40.0, 26.5));

        // The PLAIN cap (no displayColor row): hasColor answers false —
        // the use falls through, the level stands.
        hold(bearer, ItemStack.of(BuiltinItems.LEATHER_HELMET, 1));
        server.useItemOnBlock(bearer, at, 1, Optional.empty(), windowId -> { });
        net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 6,
                "engine progress (the fall-through settle)");
        assertEquals(1, CauldronBlocks.levelOf(server.world().getBlock(at)),
                "the plain cap never washed");

        // A DYED cap at level 0: the level gate refuses the wash.
        standCauldron(at, 0);
        hold(bearer, ItemStack.of(BuiltinItems.LEATHER_HELMET, 1)
                .withDisplayColor(0x112233));
        server.useItemOnBlock(bearer, at, 1, Optional.empty(), windowId -> { });
        net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 6,
                "engine progress (the empty refusal settle)");
        assertEquals(0, CauldronBlocks.levelOf(server.world().getBlock(at)),
                "the empty cauldron never washes");
        assertNotNull(bearer.inventory().held().displayColor(),
                "the tint stands at level 0");
    }

    @Test
    void theClothGateSparesTheColoredIronPiece() throws Exception {
        BlockPosition at = new BlockPosition(26, 40, 26);
        standCauldron(at, 3);
        PlayerSession knight = join("Knight", new Position(26.5, 40.0, 27.5));

        // A COLORED iron helmet (the anvil import can stamp any piece's
        // displayColor row): ArmorItem.Tier.CLOTH gates the wash to the
        // leather family alone — the iron piece never washes.
        hold(knight, ItemStack.of(BuiltinItems.IRON_HELMET, 1)
                .withDisplayColor(0xAA00AA));
        server.useItemOnBlock(knight, at, 1, Optional.empty(), windowId -> { });
        net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 6,
                "engine progress (the CLOTH gate settle)");
        assertEquals(3, CauldronBlocks.levelOf(server.world().getBlock(at)),
                "the iron piece never washed (the Tier.CLOTH gate)");
        assertNotNull(knight.inventory().held().displayColor(),
                "the iron tint stands");
    }

    /** The test's own slot write (the ticker lane). */
    private static void player_setSlot(PlayerSession player, int slot, ItemStack stack) {
        player.inventory().setSlot(slot, stack);
    }
}
