package net.zaminmc.torch.server.banner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BannerBlocks;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE BANNER FAMILY'S CORE LAWS (BannerBlock/BannerItem/BannerBlockEntity
 * 1:1): the placement's own facing rules (the DOWN refusal, the standing
 * rotation formula, the wall FACING band), the tile's base read (the
 * BlockEntityTag's Base row over the stack's damage fallback), the tagged
 * drop (the patterns surviving the break through the BlockEntityTag), the
 * neighbor-drop walks (the standing floor consult, the wall's
 * opposite-facing consult), and the survival-only drop law (the creative
 * break pays nothing).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BannerFamilyTest {

    private EngineServer server;

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "bannerfam", "it", 20, 4, 20,
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

    private void hold(PlayerSession player, ItemStack stack) {
        player.inventory().setSlot(0, stack);
        player.inventory().selectHotbarSlot(0);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
    }

    /** The red banner's damage (the dye item domain: 14 = red). */
    private static final int RED = 14;

    /** A banner stack carrying one applied pattern row. */
    private static ItemStack patternedBanner(int base, String hash, int color) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("Pattern", hash);
        row.put("Color", color);
        Map<String, Object> tag = new java.util.LinkedHashMap<>();
        tag.put("Base", base);
        tag.put("Patterns", List.of(row));
        return new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.BANNER, 1, base)
                .withBlockEntityTag(tag);
    }

    @Test
    void thePlacementLawsOwnTheirFaces() throws Exception {
        server = boot();
        PlayerSession placer = join("Placer");
        var world = server.world();
        int gx = (int) Math.floor(placer.position().x()) + 2;
        int gy = (int) Math.floor(placer.position().y()) - 1;
        int gz = (int) Math.floor(placer.position().z());
        BlockPosition floor = new BlockPosition(gx, gy, gz);
        BlockPosition postTop = new BlockPosition(gx, gy + 1, gz + 4);
        server.ticker().submit(() -> {
            world.setBlock(floor, BuiltinBlocks.STONE);
            world.setBlock(new BlockPosition(gx, gy, gz + 4), BuiltinBlocks.STONE);
            world.setBlock(postTop, BuiltinBlocks.STONE);
        });
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE)
                && world.getBlock(postTop).equals(BuiltinBlocks.STONE), "floor and post seeded");

        // The DOWN-face refusal (BannerItem.useOn line 29): nothing plants,
        // nothing consumes.
        hold(placer, new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.BANNER, 2, RED));
        useOn(placer, floor, 0);
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        assertTrue(!BannerBlocks.isBanner(
                        world.getBlock(floor.offset(0, -1, 0)).identifier()),
                "the DOWN face never plants a banner");
        assertEquals(2, placer.inventory().held().count(), "the refusal consumed nothing");

        // The top-face standing placement: yaw 0 (looking south) plants the
        // banner facing NORTH — the formula floor((0 + 180) * 16 / 360 +
        // 0.5) & 15 = 8, the north cardinal.
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, gy + 1,
                gz - 1.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        useOn(placer, floor, 1);
        BlockPosition standingCell = floor.offset(0, 1, 0);
        await(() -> world.getBlock(standingCell).equals(BuiltinBlocks.STANDING_BANNER_NORTH),
                "the standing banner lands on its north cardinal (the yaw-0 formula)");
        assertEquals(1, placer.inventory().held().count(),
                "the survival placement consumed one item");
        var tile = server.banners().tileAt(standingCell);
        assertEquals(RED, tile.base(), "the tile takes the stack's damage as its base");

        // The side-face wall placement: the north face of the post hangs a
        // wall banner FACING north (metadata 2, away from the wall). The
        // placer steps up to the post first (the use's own 4.5 reach band).
        placer.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, gy + 1,
                gz + 2.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        hold(placer, new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.BANNER, 1, RED));
        useOn(placer, postTop, 2);
        BlockPosition hungCell = postTop.offset(0, 0, -1);
        await(() -> world.getBlock(hungCell).equals(BuiltinBlocks.WALL_BANNER_NORTH),
                "the wall banner hangs facing away from its wall");
        assertEquals(0, placer.inventory().held().count(), "the wall placement consumed the last");
        server.shutdown(null);
    }

    @Test
    void theTaggedDropSurvivesTheSurvivalBreakAlone() throws Exception {
        server = boot();
        PlayerSession miner = join("Miner");
        var world = server.world();
        int gx = (int) Math.floor(miner.position().x()) + 2;
        int gy = (int) Math.floor(miner.position().y()) - 1;
        int gz = (int) Math.floor(miner.position().z());
        BlockPosition floor = new BlockPosition(gx, gy, gz);
        BlockPosition bannerCell = floor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");

        hold(miner, patternedBanner(RED, "ms", 0));
        useOn(miner, floor, 1);
        await(() -> BannerBlocks.isBanner(world.getBlock(bannerCell).identifier()),
                "the patterned banner placed");

        // The survival break: the drop carries the base damage AND the
        // BlockEntityTag (Base + the one pattern row) — the patterns
        // survive the break (dropItems' own walk).
        server.blockInteraction().submitMiningStart(miner, bannerCell);
        EngineAwaits.awaitTicks(world, 8, "the 1.0-hardness wood dig window");
        server.blockInteraction().submitMiningFinished(miner, bannerCell);
        await(() -> world.getBlock(bannerCell).equals(BuiltinBlocks.AIR), "the banner broke");
        await(() -> server.itemEntities().all().stream().anyMatch(
                        item -> item.stack().type().identifier().value().equals("banner")),
                "the tagged drop landed");
        ItemStack drop = server.itemEntities().all().stream()
                .filter(item -> item.stack().type().identifier().value().equals("banner"))
                .findFirst().orElseThrow().stack();
        assertEquals(RED, drop.damage(), "the drop carries the tile's base damage");
        assertEquals(RED, ((Number) drop.blockEntityTag().get("Base")).intValue(),
                "the tag's Base row");
        List<?> rows = (List<?>) drop.blockEntityTag().get("Patterns");
        assertEquals(1, rows.size(), "the pattern row survived the break");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("ms", row.get("Pattern"), "the stripe_middle hash");
        assertEquals(0, ((Number) row.get("Color")).intValue(), "the pattern's color");
        assertNull(server.banners().tileAt(bannerCell), "the tile left with the block");

        // The banner's afterMinedByPlayer override skips super: no 0.025
        // fatigue, no mined-block stat (the registration's disableStats).
        assertEquals(0.0f, miner.exhaustion(), 1.0e-6f,
                "the banner break pays no mining fatigue (the override skips super)");
        server.shutdown(null);
    }

    @Test
    void theCreativeBreakDropsNothing() throws Exception {
        server = boot();
        PlayerSession builder = join("Creator");
        server.ticker().submit(() -> builder.setGamemode(net.zaminmc.torch.GameMode.CREATIVE));
        var world = server.world();
        int gx = (int) Math.floor(builder.position().x()) + 2;
        int gy = (int) Math.floor(builder.position().y()) - 1;
        int gz = (int) Math.floor(builder.position().z());
        BlockPosition floor = new BlockPosition(gx, gy, gz);
        BlockPosition bannerCell = floor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");

        hold(builder, patternedBanner(RED, "bs", 0));
        useOn(builder, floor, 1);
        await(() -> BannerBlocks.isBanner(world.getBlock(bannerCell).identifier()),
                "the banner placed");

        int dropsBefore = server.itemEntities().all().size();
        server.blockInteraction().submitCreativeBreak(builder, bannerCell);
        await(() -> world.getBlock(bannerCell).equals(BuiltinBlocks.AIR),
                "the creative break landed");
        EngineAwaits.awaitTicks(world, 4, "engine settle");
        assertEquals(dropsBefore, server.itemEntities().all().size(),
                "the creative break pays nothing (afterMinedByPlayer never runs)");
        assertNull(server.banners().tileAt(bannerCell), "the tile still left with the block");
        server.shutdown(null);
    }

    @Test
    void theSupportDeparturesDropTheTaggedItem() throws Exception {
        server = boot();
        List<Object[]> publishes = new CopyOnWriteArrayList<>();
        server.banners().addListener(new BannerManager.Listener() {
            @Override
            public void onBannerTileChanged(BlockPosition position, BannerManager.BannerTile tile) {
                publishes.add(new Object[]{position, tile});
            }
        });
        PlayerSession seeder = join("Seeder");
        var world = server.world();
        int gx = (int) Math.floor(seeder.position().x()) + 2;
        int gy = (int) Math.floor(seeder.position().y()) - 1;
        int gz = (int) Math.floor(seeder.position().z());
        BlockPosition floor = new BlockPosition(gx, gy, gz);
        BlockPosition bannerCell = floor.offset(0, 1, 0);
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.STONE));
        await(() -> world.getBlock(floor).equals(BuiltinBlocks.STONE), "floor seeded");

        // The standing banner's floor departure (Standing.neighborChanged
        // lines 128-135): the floor leaving pops the banner with its
        // tagged item.
        hold(seeder, patternedBanner(RED, "bs", 0));
        useOn(seeder, floor, 1);
        await(() -> BannerBlocks.isBanner(world.getBlock(bannerCell).identifier()),
                "the banner placed");
        server.ticker().submit(() -> world.setBlock(floor, BuiltinBlocks.AIR));
        await(() -> world.getBlock(bannerCell).equals(BuiltinBlocks.AIR),
                "the standing banner left with its floor");
        await(() -> server.itemEntities().all().stream().anyMatch(
                        item -> item.stack().type().identifier().value().equals("banner")),
                "the floor departure dropped the banner");
        ItemStack standingDrop = server.itemEntities().all().stream()
                .filter(item -> item.stack().type().identifier().value().equals("banner"))
                .findFirst().orElseThrow().stack();
        assertEquals(RED, standingDrop.damage(), "the support drop carries the base");
        assertEquals(1, ((List<?>) standingDrop.blockEntityTag().get("Patterns")).size(),
                "the support drop carries the patterns");

        // The wall banner's wall departure (Wall.neighborChanged lines
        // 184-192): the block BEHIND the facing leaving pops it; a block
        // leaving anywhere else never does.
        BlockPosition wallBase = new BlockPosition(gx, gy, gz + 6);
        BlockPosition wallPost = wallBase.offset(0, 1, 0);
        server.ticker().submit(() -> {
            world.setBlock(wallBase, BuiltinBlocks.STONE);
            world.setBlock(wallPost, BuiltinBlocks.STONE);
        });
        await(() -> world.getBlock(wallPost).equals(BuiltinBlocks.STONE), "the wall post seeded");
        seeder.applyMovement(new net.zaminmc.torch.util.Position(gx + 0.5, gy + 1,
                gz + 3.5), new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        hold(seeder, new ItemStack(
                net.zaminmc.torch.server.item.BuiltinItems.BANNER, 1, RED));
        useOn(seeder, wallPost, 2); // the north face: the banner faces north
        BlockPosition hungCell = wallPost.offset(0, 0, -1);
        await(() -> world.getBlock(hungCell).equals(BuiltinBlocks.WALL_BANNER_NORTH),
                "the wall banner hung");
        // A far cell leaving never disturbs it.
        server.ticker().submit(() -> world.setBlock(wallBase, BuiltinBlocks.AIR));
        EngineAwaits.awaitTicks(world, 4, "engine settle");
        assertEquals(BuiltinBlocks.WALL_BANNER_NORTH, world.getBlock(hungCell),
                "the base cell's departure never disturbs the hung banner");
        // The wall itself leaving (the cell BEHIND the facing, the south
        // neighbor of the hung cell) pops it.
        server.ticker().submit(() -> world.setBlock(wallPost, BuiltinBlocks.AIR));
        await(() -> world.getBlock(hungCell).equals(BuiltinBlocks.AIR),
                "the wall's departure popped the hung banner");
        server.shutdown(null);
    }
}
