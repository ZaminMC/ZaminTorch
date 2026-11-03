package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.AnvilBlocks;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-035 PASS-2 SEAT — the anvil window family's dimension
 * ownership (docs/vanilla-conformance-audit.md, the fifty-second pass —
 * the block-consult window tail Slice B left on the 0-stamp open form).
 *
 * <p>THE REFERENCE LAW (AnvilMenu lines 40-46 + 81-93 + 308-311,
 * re-walked for this seat): the anvil menu is CONSTRUCTED over the world
 * the right-click happened in ({@code new AnvilMenu(playerInventory,
 * world, pos, player)} — the acting world rides in at the open), and every
 * later consult reads THAT world: the walk-away gate's
 * {@code world.getBlockState(this.pos).getBlock() == Blocks.ANVIL}
 * (isValid line 309), the take's damage walk
 * ({@code world.getBlockState(pos)} line 81 — the 0.12 roll's anvil read,
 * the DAMAGE progression write, the remove) and the 1020/1021 events
 * ({@code world.doEvent} lines 86/89/92 — that world's listener list
 * alone, World.doEvent 2613-2626). A nether anvil's window stays open over
 * the NETHER's cell, its repairs damage the NETHER's anvil, and its use
 * sound plays IN THE NETHER.</p>
 *
 * <p>THE ENGINE'S ROW (the pre-fix tree): the anvil open rides the 3-arg
 * {@code openContainerWindow} (the 0 dimension stamp — the four tile
 * families got the 4-arg form in Slice B, the block-consult windows did
 * not), the viewer tick's validity consult reads the OVERWORLD FIELD
 * ({@code menu.stillValid(world::getBlock, ...)} — EngineServer's
 * tickAnvilViewers), and the take's site consults ride the same field
 * (anvilSiteAt — anvilStands/damageState/progressDamage/breakAnvil) with
 * the 1020/1021 sounds on the no-dimension fx overload. A nether anvil:
 * the window SLAMS SHUT on the first viewer tick (the overworld cell is
 * not an anvil), a mirrored overworld anvil at the same coordinates keeps
 * it alive and the repair take CORRUPTS THE OVERWORLD'S ANVIL
 * (cross-dimension block writes), and the sounds stamp the overworld 0.</p>
 *
 * <p>THE SEAT: the full-boot construction — every step through the REAL
 * wires (the use wire opens the window, the window-click wire carries the
 * inputs and the take, the player random SEEDED for the deterministic
 * 0.12 roll — seed 1's first nextFloat is 0.1005 &lt; 0.12, the roll
 * HITS). ARM A pins the nether window's life and its take walking the
 * NETHER's anvil. ARM B pins the mirrored corruption (an overworld anvil
 * at the SAME coordinates must stay untouched by a nether repair). The
 * enchanting twin (the same 3-arg open + the overworld validity consult)
 * rides the third test.</p>
 */
class AnvilWindowDimensionDiscoveryTest {

    /** setSeed(8491153766255516575L) then nextFloat() == 0.098667204 < 0.12 — the roll HITS. */
    private static final long ROLL_HIT_SEED = 8491153766255516575L;

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> captured = java.util.Collections.synchronizedList(
            new ArrayList<>());

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "anvilwin" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                captured.add(sound);
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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

    /** A stone shelf with cleared air, one anvil on top, at (64, fy+1, 64). */
    private void seedAnvilPlatform(EngineWorld world, int fy, BlockPosition anvilAt) throws Exception {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 5; y++) {
                        world.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                world.airType());
                    }
                }
            }
            world.setBlock(anvilAt, BuiltinBlocks.ANVIL);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ANVIL.equals(world.getBlock(anvilAt)),
                40, "the anvil landed in its cell");
    }

    /** The smith's kit: XP for the repair cost, a damaged sword, an ingot. */
    private void armSmith(PlayerSession smith) {
        smith.addXpLevels(10);
        smith.inventory().setSlot(0, new net.zaminmc.torch.item.ItemStack(
                BuiltinItems.IRON_SWORD, 1, 100, null, null));
        smith.inventory().selectHotbarSlot(0);
        smith.inventory().setSlot(1, new net.zaminmc.torch.item.ItemStack(
                BuiltinItems.IRON_INGOT, 1, 0, null, null));
    }

    /** Opens the anvil window through the REAL use wire; returns the window id.
     * The await takes the OPEN CONSUMER alone — on the pre-fix tree the window
     * opens and then slams shut on the very first viewer tick, so the open
     * kind is a transient the poll must not require. */
    private int openAnvilWindow(PlayerSession smith, BlockPosition anvilAt) throws Exception {
        AtomicInteger windowId = new AtomicInteger();
        server.useItemOnBlock(smith, anvilAt, 1, Optional.empty(), windowId::set);
        EngineAwaits.awaitCondition(server.world(),
                () -> windowId.get() > 0,
                40, "the anvil window opened through the use wire");
        return windowId.get();
    }

    /** The input dance through the REAL window-click wire: hotbar -> cursor -> menu slots. */
    private void feedInputs(PlayerSession smith, int windowId) throws Exception {
        server.windowClick(smith, windowId, 30, 0, 0, ok -> { }); // cursor takes the sword
        EngineAwaits.awaitCondition(server.world(),
                () -> !smith.inventory().cursor().isEmpty(), 40, "the sword rides the cursor");
        server.windowClick(smith, windowId, 0, 0, 0, ok -> { }); // into input 0
        EngineAwaits.awaitCondition(server.world(),
                () -> smith.anvilMenu() != null
                        && !smith.anvilMenu().input().isEmpty(),
                40, "the sword sits in input 0");
        server.windowClick(smith, windowId, 31, 0, 0, ok -> { }); // cursor takes the ingot
        EngineAwaits.awaitCondition(server.world(),
                () -> !smith.inventory().cursor().isEmpty(), 40, "the ingot rides the cursor");
        server.windowClick(smith, windowId, 1, 0, 0, ok -> { }); // into input 1
        EngineAwaits.awaitCondition(server.world(),
                () -> smith.anvilMenu() != null
                        && !smith.anvilMenu().ingredient().isEmpty()
                        && !smith.anvilMenu().output().isEmpty(),
                60, "the repair projection formed (the output is ready)");
    }

    /** The seeded take through the REAL click wire: the 0.12 roll HITS (seed 1). */
    private void takeSeededOutput(PlayerSession smith, int windowId) throws Exception {
        // The roll's draw is the FIRST on the player random after the seed
        // (the only consumer on the take path — verified: no per-tick draws).
        server.ticker().submit(() -> smith.playerRandom().setSeed(ROLL_HIT_SEED));
        server.windowClick(smith, windowId, 2, 0, 0, ok -> { }); // the take
    }

    @Test
    void theNetherAnvilWindowStaysOpenAndItsTakeWalksTheNetherAnvil() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        PlayerSession smith = join("NetherSmith");
        smith.setDimension(-1);

        int fy = 100;
        BlockPosition anvilAt = new BlockPosition(64, fy + 1, 64);
        seedAnvilPlatform(nether, fy, anvilAt);

        smith.applyMovement(new Position(64.5, fy + 1, 65.5), new Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the smith settles");
        armSmith(smith);

        int windowId = openAnvilWindow(smith, anvilAt);

        // THE SLAM-SHUT PIN — the reference's walk-away gate reads the menu's
        // OWN world (AnvilMenu.isValid line 309 — the world the right-click
        // happened in). The pre-fix viewer tick read the OVERWORLD FIELD's
        // cell: the overworld at (64, 101, 64) is flat-world dirt — not an
        // anvil — and the nether's window closed on the very first viewer
        // tick (a nether anvil UNUSABLE, the GUI slamming shut on open).
        EngineAwaits.awaitTicks(server.world(), 3, "the viewer tick's walk-away gate runs");
        assertEquals(PlayerSession.ContainerKind.ANVIL, smith.openContainerKind(),
                "VT-035: the nether anvil's window must STAY OPEN (the reference's "
                        + "isValid reads the menu's own world — the NETHER's cell at "
                        + "the anvil, AnvilMenu line 309). Observed: the window closed "
                        + "(the validity consult read the overworld's cell instead).");

        // The take walks the NETHER's anvil — the damage roll (seeded to HIT)
        // must progress the NETHER block's damage, and the 1021 use sound
        // must carry the NETHER's dimension.
        feedInputs(smith, windowId);
        takeSeededOutput(smith, windowId);
        EngineAwaits.awaitCondition(server.world(),
                () -> AnvilBlocks.damageOf(nether.getBlock(anvilAt).identifier()) == 1,
                60, "the nether anvil's damage progressed under the seeded take");

        assertEquals(1, AnvilBlocks.damageOf(nether.getBlock(anvilAt).identifier()),
                "VT-035: the nether repair take must progress the NETHER anvil's "
                        + "damage (the reference's damage walk reads and writes the "
                        + "menu's own world — AnvilMenu lines 81-89). Observed damage: "
                        + AnvilBlocks.damageOf(nether.getBlock(anvilAt).identifier()));
        FxManager.Sound use = captured.stream()
                .filter(sound -> sound.name().equals("random.anvil_use"))
                .findFirst().orElse(null);
        assertNotNull(use, "the 1021 use sound fired on the take");
        assertEquals(-1, use.dimension(),
                "VT-035: the nether repair's 1021 use sound must carry the NETHER's "
                        + "dimension (world.doEvent line 89 over the menu's own world — "
                        + "the per-world listener list alone). Observed stamp: "
                        + use.dimension());
    }

    @Test
    void theNetherTakeNeverTouchesTheOverworldMirror() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        PlayerSession smith = join("MirrorSmith");
        smith.setDimension(-1);

        int fy = 100;
        BlockPosition anvilAt = new BlockPosition(64, fy + 1, 64);
        seedAnvilPlatform(nether, fy, anvilAt);
        // THE MIRROR — an overworld anvil at the SAME coordinates. On the
        // pre-fix tree this keeps the nether's window alive (the validity
        // consult reads the overworld's cell) and receives the corruption
        // writes (the take's site consults the overworld field).
        seedAnvilPlatform(overworld, fy, anvilAt);

        smith.applyMovement(new Position(64.5, fy + 1, 65.5), new Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the smith settles");
        armSmith(smith);

        int windowId = openAnvilWindow(smith, anvilAt);
        EngineAwaits.awaitTicks(server.world(), 3, "the viewer tick's walk-away gate runs");
        assertEquals(PlayerSession.ContainerKind.ANVIL, smith.openContainerKind(),
                "VT-035: the nether anvil's window stays open (the nether's own cell "
                        + "is an anvil — the gate must read THAT cell).");

        feedInputs(smith, windowId);
        takeSeededOutput(smith, windowId);
        EngineAwaits.awaitCondition(server.world(),
                () -> AnvilBlocks.damageOf(nether.getBlock(anvilAt).identifier()) == 1
                        || AnvilBlocks.damageOf(overworld.getBlock(anvilAt).identifier()) == 1,
                60, "the seeded take landed (some anvil's damage progressed)");

        // THE CORRUPTION PINS — the nether take must advance the NETHER's
        // anvil alone. On the pre-fix tree the overworld MIRROR advanced
        // instead (the cross-dimension write) and the nether's own block
        // never aged.
        assertEquals(1, AnvilBlocks.damageOf(nether.getBlock(anvilAt).identifier()),
                "VT-035: the nether repair must progress the NETHER anvil (the "
                        + "reference's site consults read the menu's own world). "
                        + "Observed nether damage: "
                        + AnvilBlocks.damageOf(nether.getBlock(anvilAt).identifier()));
        assertEquals(0, AnvilBlocks.damageOf(overworld.getBlock(anvilAt).identifier()),
                "VT-035: the overworld mirror must stay UNTOUCHED by a nether "
                        + "repair (a same-coordinate block in another world is a "
                        + "DIFFERENT world's cell — the cross-dimension write). "
                        + "Observed overworld damage: "
                        + AnvilBlocks.damageOf(overworld.getBlock(anvilAt).identifier()));
        FxManager.Sound use = captured.stream()
                .filter(sound -> sound.name().equals("random.anvil_use"))
                .findFirst().orElse(null);
        assertNotNull(use, "the 1021 use sound fired on the take");
        assertEquals(-1, use.dimension(),
                "VT-035: the nether repair's 1021 sound must carry the NETHER's "
                        + "dimension. Observed stamp: " + use.dimension());
    }

    @Test
    void theNetherEnchantingWindowStaysOpen() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession reader = join("NetherReader");
        reader.setDimension(-1);

        int fy = 100;
        BlockPosition tableAt = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 5; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(tableAt, BuiltinBlocks.ENCHANTING_TABLE);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.ENCHANTING_TABLE.equals(nether.getBlock(tableAt)),
                40, "the nether enchanting table landed");

        reader.applyMovement(new Position(64.5, fy + 1, 65.5), new Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the reader settles");

        AtomicInteger windowId = new AtomicInteger();
        server.useItemOnBlock(reader, tableAt, 1, Optional.empty(), windowId::set);
        EngineAwaits.awaitCondition(server.world(),
                () -> windowId.get() > 0,
                40, "the enchanting window opened through the use wire");

        // THE SLAM-SHUT PIN — the twin's own member (EnchantingTableMenu
        // line 33: the menu's OWN world at construction; the pre-fix viewer
        // tick read the overworld field's cell). A nether enchanting table
        // must keep its window open over the NETHER's cell.
        EngineAwaits.awaitTicks(server.world(), 3, "the viewer tick's walk-away gate runs");
        assertEquals(PlayerSession.ContainerKind.ENCHANTING_TABLE, reader.openContainerKind(),
                "VT-035 (the twin): the nether enchanting table's window must STAY "
                        + "OPEN (the reference's menu carries its own world — the "
                        + "bookshelf scan AND the walk-away gate read the NETHER's "
                        + "cells). Observed: the window closed (the validity consult "
                        + "read the overworld's cell instead).");
    }
}
