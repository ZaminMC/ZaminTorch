package net.zaminmc.torch.server.banner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE BANNER FAMILY'S CRAFTING + WASH LAWS (BannerRecipes + CauldronBlock's
 * banner arm, 1:1): the sixteen wool base recipes (the inverted result
 * domain — the white wool's banner carries damage 15), the pattern-add
 * recipe (the dye-shape consult and the item-catalyst consult, the
 * six-pattern ceiling), the duplicate recipe (the same-base pair, the
 * carrier surviving as the mold), and the cauldron wash (the topmost
 * pattern leaving, the stat and the level spend).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BannerCraftingTest {

    private EngineServer server;

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "bannercraft", "it", 20, 4, 20,
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

    private static ItemStack plainBanner(int base) {
        return new ItemStack(BuiltinItems.BANNER, 1, base);
    }

    private static ItemStack patternedBanner(int base, String... hashes) {
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("Base", base);
        List<Object> rows = new java.util.ArrayList<>();
        for (String hash : hashes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Pattern", hash);
            row.put("Color", 0);
            rows.add(row);
        }
        tag.put("Patterns", rows);
        return new ItemStack(BuiltinItems.BANNER, 1, base).withBlockEntityTag(tag);
    }

    @Test
    void theWoolBaseRecipesCarryTheInvertedDomain() throws Exception {
        server = boot();
        PlayerSession crafter = join("Weaver");
        server.ticker().submit(() -> crafter.openContainerWindow(1,
                PlayerSession.ContainerKind.CRAFTING_TABLE, null));
        EngineAwaits.awaitTicks(server.world(), 2, "engine settle");
        // The white banner (color 0's wool, the result damage 15 — the dye
        // item's domain) over the reference's "###"/"###"/" | " shape.
        server.ticker().submit(() -> {
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) {
                    crafter.tableCrafting().setCell(row * 3 + col,
                            new ItemStack(BuiltinItems.WOOL, 1, 0));
                }
            }
            crafter.tableCrafting().setCell(7, ItemStack.of(BuiltinItems.STICK, 1));
        });
        await(() -> {
            ItemStack preview = server.containerResult(crafter);
            return !preview.isEmpty() && preview.type().equals(BuiltinItems.BANNER)
                    && preview.damage() == 15;
        }, "the white wool ring previews the damage-15 white banner");
        server.windowClick(crafter, 1, 0, 0, 0, accepted -> { });
        await(() -> !crafter.inventory().cursor().isEmpty()
                        && crafter.inventory().cursor().type().equals(BuiltinItems.BANNER)
                        && crafter.inventory().cursor().damage() == 15,
                "the take lands the white banner (damage 15)");
        server.shutdown(null);
    }

    @Test
    void thePatternAddShapesAndCatalysts() throws Exception {
        server = boot();
        PlayerSession crafter = join("Painter");
        server.ticker().submit(() -> crafter.openContainerWindow(1,
                PlayerSession.ContainerKind.CRAFTING_TABLE, null));
        EngineAwaits.awaitTicks(server.world(), 2, "engine settle");

        // The dye-shape consult: STRIPE_MIDDLE ("   ", "###", "   ") — the
        // banner on a blank cell, three same-color dyes on the middle row.
        server.ticker().submit(() -> {
            crafter.tableCrafting().setCell(0, plainBanner(15));
            for (int col = 0; col < 3; col++) {
                crafter.tableCrafting().setCell(3 + col,
                        new ItemStack(BuiltinItems.INK_SAC, 1, 0));
            }
        });
        await(() -> {
            ItemStack preview = server.containerResult(crafter);
            return !preview.isEmpty() && preview.blockEntityTag() != null
                    && ((List<?>) preview.blockEntityTag().get("Patterns")).size() == 1;
        }, "the stripe-middle shape previews the one-row banner");
        ItemStack preview = server.containerResult(crafter);
        Map<?, ?> row = (Map<?, ?>) ((List<?>) preview.blockEntityTag().get("Patterns")).get(0);
        assertEquals("ms", row.get("Pattern"), "the stripe_middle hash");
        assertEquals(0, ((Number) row.get("Color")).intValue(),
                "the pattern's color rides the FIRST dye (ink sac damage 0)");
        server.windowClick(crafter, 1, 0, 0, 0, accepted -> { });
        await(() -> {
            ItemStack cursor = crafter.inventory().cursor();
            return !cursor.isEmpty() && cursor.type().equals(BuiltinItems.BANNER)
                    && ((List<?>) cursor.blockEntityTag().get("Patterns")).size() == 1;
        }, "the take lands the patterned banner");

        // The item-catalyst consult: the banner + a dye + the vine names
        // CURLY_BORDER anywhere in the grid (the 2x2 player window too).
        server.ticker().submit(() -> {
            crafter.inventory().setSlot(0, ItemStack.EMPTY);
            crafter.crafting().setCell(0, patternedBanner(15, "ms"));
            crafter.crafting().setCell(1, new ItemStack(BuiltinItems.INK_SAC, 1, 0));
            crafter.crafting().setCell(2, ItemStack.of(BuiltinItems.VINE, 1));
        });
        await(() -> {
            ItemStack next = server.craftingResult(crafter);
            return !next.isEmpty()
                    && ((List<?>) next.blockEntityTag().get("Patterns")).size() == 2
                    && "cbo".equals(((Map<?, ?>) ((List<?>) next.blockEntityTag()
                            .get("Patterns")).get(1)).get("Pattern"));
        }, "the vine catalyst previews the curly-border row appended");

        // The six-pattern ceiling (matches' getPatternCount >= 6 refusal).
        server.ticker().submit(() -> {
            crafter.crafting().setCell(0, ItemStack.EMPTY);
            crafter.crafting().setCell(1, ItemStack.EMPTY);
            crafter.crafting().setCell(2, ItemStack.EMPTY);
        });
        EngineAwaits.awaitTicks(server.world(), 2, "engine settle");
        server.ticker().submit(() -> crafter.openContainerWindow(2,
                PlayerSession.ContainerKind.CRAFTING_TABLE, null));
        EngineAwaits.awaitTicks(server.world(), 2, "engine settle");
        ItemStack full = patternedBanner(15, "ms", "bs", "ls", "ts", "rs", "cs");
        server.ticker().submit(() -> {
            crafter.tableCrafting().setCell(0, full);
            for (int col = 0; col < 3; col++) {
                crafter.tableCrafting().setCell(3 + col,
                        new ItemStack(BuiltinItems.INK_SAC, 1, 0));
            }
        });
        await(() -> crafter.tableCrafting().cell(0).equals(full), "the full grid loaded");
        assertEquals(ItemStack.EMPTY, server.containerResult(crafter),
                "a six-pattern banner never takes a seventh");
        server.shutdown(null);
    }

    @Test
    void theDuplicateKeepsTheMold() throws Exception {
        server = boot();
        PlayerSession crafter = join("Copier");
        server.ticker().submit(() -> crafter.openContainerWindow(1,
                PlayerSession.ContainerKind.CRAFTING_TABLE, null));
        EngineAwaits.awaitTicks(server.world(), 2, "engine settle");
        ItemStack carrier = patternedBanner(15, "ms", "bs");
        server.ticker().submit(() -> {
            crafter.tableCrafting().setCell(0, carrier);
            crafter.tableCrafting().setCell(1, plainBanner(15));
        });
        await(() -> {
            ItemStack preview = server.containerResult(crafter);
            return !preview.isEmpty()
                    && ((List<?>) preview.blockEntityTag().get("Patterns")).size() == 2;
        }, "the same-base pair previews the carrier's copy");
        server.windowClick(crafter, 1, 0, 0, 0, accepted -> { });
        await(() -> {
            ItemStack cursor = crafter.inventory().cursor();
            return !cursor.isEmpty() && cursor.type().equals(BuiltinItems.BANNER)
                    && ((List<?>) cursor.blockEntityTag().get("Patterns")).size() == 2;
        }, "the take lands the copy");
        assertEquals(carrier, crafter.tableCrafting().cell(0),
                "the carrier survives the craft (the mold law)");
        assertEquals(ItemStack.EMPTY, crafter.tableCrafting().cell(1),
                "the plain banner paid the copy");

        // A mismatched base pair never matches.
        server.ticker().submit(() -> {
            crafter.tableCrafting().setCell(0, carrier);
            crafter.tableCrafting().setCell(1, plainBanner(0));
        });
        EngineAwaits.awaitTicks(server.world(), 2, "engine settle");
        assertEquals(ItemStack.EMPTY, server.containerResult(crafter),
                "a mismatched base pair never duplicates");
        server.shutdown(null);
    }

    @Test
    void theCauldronWashRemovesTheTopmostPattern() throws Exception {
        server = boot();
        PlayerSession washer = join("Washer");
        var world = server.world();
        int gx = (int) Math.floor(washer.position().x()) + 2;
        int gy = (int) Math.floor(washer.position().y()) - 1;
        int gz = (int) Math.floor(washer.position().z());
        BlockPosition floor = new BlockPosition(gx, gy, gz);
        BlockPosition cauldron = floor.offset(1, 0, 0);
        server.ticker().submit(() -> {
            world.setBlock(floor, BuiltinBlocks.STONE);
            world.setBlock(cauldron, net.zaminmc.torch.server.cauldron.CauldronBlocks.of(3));
        });
        await(() -> net.zaminmc.torch.server.cauldron.CauldronBlocks.isCauldron(
                world.getBlock(cauldron)), "the full cauldron seeded");

        // The wash: the last-held banner replaces the hand slot with the
        // washed copy (one pattern gone), the stat paid, the level spent.
        washer.inventory().setSlot(0, patternedBanner(15, "ms", "bs"));
        washer.inventory().selectHotbarSlot(0);
        washer.applyMovement(new net.zaminmc.torch.util.Position(
                cauldron.x() + 0.5, gy + 1, cauldron.z() + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        server.useItemOnBlock(washer, cauldron, 1, java.util.Optional.empty(),
                windowId -> { });
        await(() -> {
            ItemStack held = washer.inventory().held();
            return !held.isEmpty() && held.type().equals(BuiltinItems.BANNER)
                    && held.blockEntityTag() != null
                    && ((List<?>) held.blockEntityTag().get("Patterns")).size() == 1;
        }, "the washed banner replaced the hand slot (the topmost row gone)");
        assertEquals("ms", ((Map<?, ?>) ((List<?>) washer.inventory().held()
                .blockEntityTag().get("Patterns")).get(0)).get("Pattern"),
                "the LAST pattern leaves (the bottom row stays)");
        // The reference's own quirk: the stat rides the ADD branch alone —
        // washing the LAST banner (the setItem branch) pays NO stat.
        assertEquals(0, washer.stats().get(
                net.zaminmc.torch.server.stat.EngineStats.BANNER_CLEANED),
                "the last-banner wash pays no stat (the reference's branch law)");
        assertEquals(2, net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(
                world.getBlock(cauldron)), "the level spent one");

        // The stack-of-two wash: the ADD branch runs — the stat lands, the
        // held stack pays one unit.
        server.ticker().submit(() -> washer.inventory().setSlot(0,
                patternedBanner(15, "bs").withCount(2)));
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        server.useItemOnBlock(washer, cauldron, 1, java.util.Optional.empty(),
                windowId -> { });
        await(() -> washer.stats().get(
                net.zaminmc.torch.server.stat.EngineStats.BANNER_CLEANED) == 1,
                "the stack wash pays the stat (the ADD branch)");
        assertEquals(1, net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(
                world.getBlock(cauldron)), "the level spent one again");

        // A pattern-less banner never washes (the reference's count > 0 gate):
        // no stat, no level.
        server.ticker().submit(() -> washer.inventory().setSlot(0, plainBanner(15)));
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        int level = net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(
                world.getBlock(cauldron));
        server.useItemOnBlock(washer, cauldron, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(world, 2, "engine settle");
        assertEquals(level, net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(
                world.getBlock(cauldron)), "a plain banner never spends the level");
        server.shutdown(null);
    }
}
