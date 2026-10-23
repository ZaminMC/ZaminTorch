package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
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
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The stone-slab family's doubles + the SEAMLESS skins (Slice L5 —
 * DoubleStoneSlabBlock/RedSandstoneSlab 1:1): the three variant doubles
 * the engine never had (the pairs merged into the PARENT BLOCKS, the
 * pre-52 stand-in), the two register-only seamless skins (no gameplay
 * path sets the bit), the drop-law repairs (the 43 double dropped
 * ITSELF, the 181 double dropped ONE — vanilla drops TWO singles), and
 * the dead-arm repair (the slab2 family arm sat below the stone_slab
 * startsWith, so every red-sandstone pair merged into the 43 double).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DoubleStoneSlabFamilyTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "slabs", "it", 20, 4, 20,
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
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theVariantDoublesCarryTheReferenceBand() {
        // DoubleStoneSlabBlock's VARIANT nibble: sandstone 1, cobblestone
        // 3, stone-brick 5 — and the SEAMLESS bit 8 on both doubles.
        assertEquals(1, BuiltinBlocks.DOUBLE_SANDSTONE_SLAB.legacyMetadata());
        assertEquals(3, BuiltinBlocks.DOUBLE_COBBLESTONE_SLAB.legacyMetadata());
        assertEquals(5, BuiltinBlocks.DOUBLE_STONE_BRICK_SLAB.legacyMetadata());
        assertEquals(8, BuiltinBlocks.DOUBLE_STONE_SLAB_SEAMLESS.legacyMetadata());
        assertEquals(8, BuiltinBlocks.DOUBLE_STONE_SLAB2_SEAMLESS.legacyMetadata());
        // The drop laws (the repairs): 43 yields TWO stone slabs (never
        // itself), 181 yields TWO of its own — and the variant doubles
        // two of their singles (getDropItem's single + getBaseDropCount's
        // isDouble ? 2 : 1).
        BlockBehavior plain = BlockBehaviorTable.of(
                Identifier.parse("minecraft:double_stone_slab")).orElseThrow();
        assertEquals("minecraft:stone_slab", plain.drops().get(0).item().toString());
        assertEquals(2, plain.drops().get(0).count());
        for (String[] pair : new String[][] {
                {"minecraft:double_sandstone_slab", "minecraft:sandstone_slab"},
                {"minecraft:double_cobblestone_slab", "minecraft:cobblestone_slab"},
                {"minecraft:double_stone_brick_slab", "minecraft:stone_brick_slab"}}) {
            BlockBehavior behavior = BlockBehaviorTable.of(Identifier.parse(pair[0])).orElseThrow();
            assertEquals(2.0, behavior.hardness());
            assertEquals(1, behavior.drops().size());
            assertEquals(pair[1], behavior.drops().get(0).item().toString());
            assertEquals(2, behavior.drops().get(0).count());
        }
        BlockBehavior slab2 = BlockBehaviorTable.of(
                Identifier.parse("minecraft:double_stone_slab2")).orElseThrow();
        assertEquals("minecraft:stone_slab2", slab2.drops().get(0).item().toString());
        assertEquals(2, slab2.drops().get(0).count(),
                "the 181 double yields TWO singles — the count repair");
        // The seamless skins drop the same pairs.
        BlockBehavior seamless = BlockBehaviorTable.of(
                Identifier.parse("minecraft:double_stone_slab_seamless")).orElseThrow();
        assertEquals(2, seamless.drops().get(0).count());
        // The stats folds: everything on the 43/181 rows.
        assertEquals("stat.mineBlock.minecraft.double_stone_slab",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:double_sandstone_slab")));
        assertEquals("stat.mineBlock.minecraft.double_stone_slab",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:double_stone_slab_seamless")));
        assertEquals("stat.mineBlock.minecraft.double_stone_slab2",
                net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                        Identifier.parse("minecraft:double_stone_slab2_seamless")));
        // The note block's STONE material band carries the five.
        assertTrue(net.zaminmc.torch.server.block.WorldSolidity.isFurnaceBlock(
                BuiltinBlocks.DOUBLE_SANDSTONE_SLAB.identifier()) == false);
    }

    @Test
    void thePairsDoubleIntoTheirOwnVariantDoubles() throws Exception {
        server = boot();
        PlayerSession builder = join("SlabBuilder");
        var world = server.world();

        BlockPosition floor = new BlockPosition(
                (int) Math.floor(builder.position().x()) + 2,
                (int) Math.floor(builder.position().y()) - 1,
                (int) Math.floor(builder.position().z()));
        BlockPosition atop = floor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "the floor seeded");

        // The sandstone pair: two singles merge into the SANDSTONE double
        // (DoubleStoneSlabBlock's variant nibble) — no longer the parent
        // sandstone block's pre-52 stand-in.
        ItemType sandstoneSlab = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:sandstone_slab")).orElseThrow();
        builder.inventory().setSlot(0, new ItemStack(sandstoneSlab, 4, 0));
        builder.inventory().selectHotbarSlot(0);
        server.useItemOnBlock(builder, floor, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 120 wall settle)");
        assertEquals("sandstone_slab", world.getBlock(atop).identifier().value(),
                "the first single lays the bottom half");
        server.useItemOnBlock(builder, atop, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 120 wall settle)");
        assertEquals("double_sandstone_slab", world.getBlock(atop).identifier().value(),
                "the pair doubles into the family's OWN double");

        // The red sandstone pair (the dead-arm repair): the 182 family
        // merges into the 181 double — never the 43 stone double the
        // dead arm fed it.
        BlockPosition floor2 = floor.offset(0, 0, -2);
        BlockPosition atop2 = floor2.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor2, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor2).equals(BuiltinBlocks.STONE), "the second floor");
        ItemType slab2 = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:stone_slab2")).orElseThrow();
        builder.inventory().setSlot(0, new ItemStack(slab2, 4, 0));
        builder.inventory().selectHotbarSlot(0);
        server.useItemOnBlock(builder, floor2, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 120 wall settle)");
        server.useItemOnBlock(builder, atop2, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 120 wall settle)");
        assertEquals("double_stone_slab2", world.getBlock(atop2).identifier().value(),
                "the slab2 pair merges into the 181 double (the dead-arm repair)");
        server.shutdown(null);
    }
}
