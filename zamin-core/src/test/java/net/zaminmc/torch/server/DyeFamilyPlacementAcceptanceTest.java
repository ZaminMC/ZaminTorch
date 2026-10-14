package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dye-coloring families' live placement walk (Slice 31): the damage-aware
 * survival placement (the held stack's damage naming the family's color
 * member), the carpet's support gates (the placement refusal over air, the
 * survival over water, the neighbor-changed pop when the floor breaks), and
 * the colored break's drop round trip on a real engine.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DyeFamilyPlacementAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "dyes", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private ItemType item(String id) {
        return BuiltinItems.lookup(Identifier.parse(id)).orElseThrow();
    }

    private BlockType block(String id) {
        return server.blockRegistry().lookup(Identifier.parse(id)).orElseThrow();
    }

    /** The world's own surface beside spawn (the test worlds' terrain height
     *  varies with the seed — the positions stay spawn-relative). */
    private BlockPosition surfaceSpot(int offsetX, int offsetZ) {
        var spawn = server.world().spawnPosition().toBlockPosition();
        return new BlockPosition(spawn.x() + offsetX, spawn.y() + 1, spawn.z() + offsetZ);
    }

    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(), windowId -> { });
        Thread.sleep(150);
    }

    @Test
    void theColoredWoolPlacesItsColorMemberAndBreaksBackToTheDamageField()
            throws Exception {
        server = boot();
        PlayerSession builder = join("Builder");

        // a lime wool stack (damage 5 — the sheep's own fleece domain)
        builder.inventory().setSlot(0, ItemStack.of(item("minecraft:wool"), 2).withDamage(5));
        builder.inventory().selectHotbarSlot(0);

        BlockPosition floor = surfaceSpot(0, 0);
        setBlock(floor, BuiltinBlocks.STONE);
        useOn(builder, floor, 1);

        BlockType placed = server.world().getBlock(floor.offset(0, 1, 0));
        assertEquals(Identifier.parse("minecraft:lime_wool"), placed.identifier(),
                "the damage 5 stack places the lime member");
        assertEquals(1, builder.inventory().held().count(), "the placement spent one wool");

        // the survival break walks the drop with the color on the damage
        // field (the shears are wool's own tool class — the 5x speed makes
        // the dig's vanilla finish window a quarter second)
        BlockPosition wool = floor.offset(0, 1, 0);
        builder.inventory().setSlot(1, ItemStack.of(item("minecraft:shears"), 1));
        builder.inventory().selectHotbarSlot(1);
        server.blockInteraction().submitMiningStart(builder, wool);
        Thread.sleep(600);
        server.blockInteraction().submitMiningFinished(builder, wool);
        await(() -> server.world().getBlock(wool).equals(BuiltinBlocks.AIR), "the wool broke");
        await(() -> !server.itemEntities().all().isEmpty(), "the drop landed");
        ItemStack dropped = server.itemEntities().all().iterator().next().stack();
        assertEquals(Identifier.parse("minecraft:wool"), dropped.type().identifier());
        assertEquals(5, dropped.damage(), "the lime member breaks back to damage 5");
        server.shutdown(null);
    }

    @Test
    void theCarpetSupportGatesMatchTheReference() throws Exception {
        server = boot();
        PlayerSession builder = join("Weaver");

        builder.inventory().setSlot(0, ItemStack.of(item("minecraft:carpet"), 4).withDamage(14));
        builder.inventory().selectHotbarSlot(0);

        // over AIR: the placement refuses (canSurvive lines 95-97)
        BlockPosition floating = surfaceSpot(0, 0).offset(0, 3, 0);
        useOn(builder, floating.offset(0, -1, 0), 1);
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(floating),
                "the carpet never places over air");
        assertEquals(4, builder.inventory().held().count(), "the refused use spends nothing");

        // over STONE: the red carpet (damage 14) places
        BlockPosition floor = surfaceSpot(2, 0);
        setBlock(floor, BuiltinBlocks.STONE);
        useOn(builder, floor, 1);
        BlockType placed = server.world().getBlock(floor.offset(0, 1, 0));
        assertEquals(Identifier.parse("minecraft:carpet_red"), placed.identifier(),
                "the damage 14 stack places the red carpet");
        assertEquals(3, builder.inventory().held().count());

        // over WATER: the carpet survives (isAir false — the reference's
        // own softer-than-the-plate rule)
        BlockPosition pond = surfaceSpot(-2, 0);
        setBlock(pond, BuiltinBlocks.STONE);
        setBlock(pond.offset(0, 1, 0),
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER));
        useOn(builder, pond.offset(0, 1, 0), 1);
        BlockType floatingCarpet = server.world().getBlock(pond.offset(0, 2, 0));
        assertEquals(Identifier.parse("minecraft:carpet_red"), floatingCarpet.identifier(),
                "the carpet stands over water");

        // the neighbor-changed pop: breaking the floor drops the carpet
        // with its color and clears the cell
        server.ticker().submit(() -> server.blockInteraction()
                .submitCreativeBreak(builder, floor));
        await(() -> server.world().getBlock(floor).equals(BuiltinBlocks.AIR), "the floor broke");
        await(() -> server.world().getBlock(floor.offset(0, 1, 0)).equals(BuiltinBlocks.AIR),
                "the unsupported carpet popped");
        boolean carpetDrop = server.itemEntities().all().stream()
                .map(entity -> entity.stack())
                .anyMatch(stack -> stack.type().identifier().value().equals("carpet")
                        && stack.damage() == 14);
        assertTrue(carpetDrop, "the pop's drop carries the family item with the color damage");
        server.shutdown(null);
    }

}
