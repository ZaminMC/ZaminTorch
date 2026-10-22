package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.DoublePlantBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * Wave L6 — the double-plant family's per-variant replaceability
 * (DoublePlantBlock.canBeReplaced lines 60-68, 1:1): the tall grass and the
 * large fern (both halves) yield their place to a placement silently, the
 * four FLOWER variants refuse like any normal block — and the family stays
 * walk-through as a whole (replaceability and walk-through are two laws).
 */
class DoublePlantReplaceableAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "l6-world", "it",
                20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        return server;
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

    private BlockPosition surfaceSpot(int offsetX, int offsetZ) {
        var spawn = server.world().spawnPosition().toBlockPosition();
        return new BlockPosition(spawn.x() + offsetX, spawn.y() + 1, spawn.z() + offsetZ);
    }

    private void setBlock(BlockPosition at, net.zaminmc.torch.block.BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    /**
     * Seeds a double-plant pair the way the engine's own placement commits
     * it (useDoublePlantOnTick's pair walk): the lower and the upper in ONE
     * tick-thread submit — the update system's survival arm runs after the
     * pair is whole, exactly like a real placement.
     */
    private void seedPair(BlockPosition soil, int variant) throws InterruptedException {
        var lower = DoublePlantBlocks.lower(variant);
        var upper = DoublePlantBlocks.upper(variant);
        server.ticker().submit(() -> {
            server.world().setBlock(soil.offset(0, 1, 0), lower);
            server.world().setBlock(soil.offset(0, 2, 0), upper);
        });
        await(() -> server.world().getBlock(soil.offset(0, 1, 0)).equals(lower)
                && server.world().getBlock(soil.offset(0, 2, 0)).equals(upper),
                "seeded pair over " + soil);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 3, "engine progress (a 150 wall settle)");
    }

    @Test
    void thePerVariantLawItself() {
        // The four grass/fern states replace; the eight flower states refuse.
        for (int variant = 0; variant < 6; variant++) {
            boolean expect = variant == DoublePlantBlocks.VARIANT_GRASS
                    || variant == DoublePlantBlocks.VARIANT_FERN;
            assertEquals(expect, DoublePlantBlocks.isReplaceableVariant(
                    DoublePlantBlocks.lower(variant).identifier()),
                    "lower variant " + variant);
            assertEquals(expect, DoublePlantBlocks.isReplaceableVariant(
                    DoublePlantBlocks.upper(variant).identifier()),
                    "upper variant " + variant);
            // The replaceable read rides the same law...
            assertEquals(expect, WorldSolidity.isReplaceable(
                    DoublePlantBlocks.lower(variant).identifier()));
            assertEquals(expect, WorldSolidity.isReplaceable(
                    DoublePlantBlocks.upper(variant).identifier()));
            // ...while the walk-through law keeps the WHOLE family
            // (replaceability and walk-through are two different laws:
            // no double-plant state ever blocks a body).
            assertFalse(WorldSolidity.isSolid(DoublePlantBlocks.lower(variant)),
                    "lower variant " + variant + " stays walk-through");
            assertFalse(WorldSolidity.isSolid(DoublePlantBlocks.upper(variant)),
                    "upper variant " + variant + " stays walk-through");
        }
    }

    @Test
    void placingIntoTallGrassReplacesItAndPopsTheUpper() throws Exception {
        boot();
        PlayerSession builder = join("Builder");
        builder.inventory().setSlot(0,
                ItemStack.of(BuiltinItems.lookup(Identifier.parse("minecraft:stone")).orElseThrow(), 2));
        builder.inventory().selectHotbarSlot(0);

        BlockPosition soil = surfaceSpot(3, 3);
        setBlock(soil, BuiltinBlocks.GRASS_BLOCK);
        BlockPosition lower = soil.offset(0, 1, 0);
        BlockPosition upper = soil.offset(0, 2, 0);
        seedPair(soil, DoublePlantBlocks.VARIANT_GRASS);

        // Click the soil's top face: the target is the grass LOWER cell,
        // which the placement REPLACES (canBeReplaced's grass arm).
        useOn(builder, soil, 1);

        assertEquals(BuiltinBlocks.STONE, server.world().getBlock(lower),
                "the tall grass yielded its cell to the stone");
        await(() -> server.world().getBlock(upper).equals(server.world().airType()),
                "the orphaned upper popped through the support arm");
    }

    @Test
    void placingIntoTheLargeFernUpperReplacesItToo() throws Exception {
        boot();
        PlayerSession builder = join("Builder");
        builder.inventory().setSlot(0,
                ItemStack.of(BuiltinItems.lookup(Identifier.parse("minecraft:stone")).orElseThrow(), 2));
        builder.inventory().selectHotbarSlot(0);

        BlockPosition soil = surfaceSpot(2, 1);
        setBlock(soil, BuiltinBlocks.GRASS_BLOCK);
        BlockPosition lower = soil.offset(0, 1, 0);
        BlockPosition upper = soil.offset(0, 2, 0);
        seedPair(soil, DoublePlantBlocks.VARIANT_FERN);
        // A solid block beside the upper: clicking its face toward the upper
        // targets the fern's UPPER cell (the reference resolves the upper's
        // VARIANT virtually through the lower; the engine's flat identifiers
        // carry it).
        BlockPosition besideUpper = soil.offset(1, 2, 0);
        setBlock(besideUpper, BuiltinBlocks.STONE);

        // face 4 = -X: from besideUpper toward the fern's upper cell.
        useOn(builder, besideUpper, 4);

        assertEquals(BuiltinBlocks.STONE, server.world().getBlock(upper),
                "the fern's upper cell yielded its place (the variant rides the identifier)");
    }

    @Test
    void placingIntoTheSunflowerIsRefused() throws Exception {
        boot();
        PlayerSession builder = join("Builder");
        builder.inventory().setSlot(0,
                ItemStack.of(BuiltinItems.lookup(Identifier.parse("minecraft:stone")).orElseThrow(), 2));
        builder.inventory().selectHotbarSlot(0);

        BlockPosition soil = surfaceSpot(7, 3);
        setBlock(soil, BuiltinBlocks.GRASS_BLOCK);
        BlockPosition lower = soil.offset(0, 1, 0);
        BlockPosition upper = soil.offset(0, 2, 0);
        seedPair(soil, 0); // the sunflower

        useOn(builder, soil, 1);

        assertEquals(DoublePlantBlocks.lower(0), server.world().getBlock(lower),
                "the flower refuses the placement: it is not replaceable");
        assertEquals(DoublePlantBlocks.upper(0), server.world().getBlock(upper),
                "the pair stands untouched");
        assertFalse(server.world().getBlock(lower).equals(BuiltinBlocks.STONE));
    }
}
