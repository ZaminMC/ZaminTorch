package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.block.AnvilBlocks;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-040 PASS-2 SEAT — the anvil window's block side is
 * overworld-bound (docs/vanilla-conformance-audit.md, the forty-ninth
 * pass's discovery, this pass's seats; renumbered twice from the lane's
 * draft VT-033 — the wither-summon lane's PR #146 owns 33 publicly
 * first, the pass-52 lane's lever/button gates own 36, the pass-53
 * lane's tripwire surface owns 37/38/39).
 *
 * <p>THE REFERENCE LAW (the dispatch chain, 1:1): the anvil menu is
 * constructed over the ACTING world and consults it alone —
 * {@code AnvilBlock.use} lines 53-59 passes the acting {@code world} into
 * {@code new AnvilBlock.MenuProvider(world, pos)}; {@code createMenu}
 * forwards it into {@code new AnvilMenu(playerInventory, this.world,
 * this.pos, player)} (the ctor's lines 44-47 stamping
 * {@code this.world = world}); the damage roll then reads and writes THAT
 * world — {@code onItemRemoved} lines 81-93:
 * {@code world.getBlockState(pos)}, {@code world.removeBlock(pos)} /
 * {@code world.setBlockState(pos, ..., 2)}, {@code world.doEvent(1020/
 * 1021, pos, 0)} — and the walk-away gate reads it too ({@code isValid}
 * lines 308-311: {@code this.world.getBlockState(this.pos)}).</p>
 *
 * <p>THE ENGINE'S ROW: every one of those consults answers the
 * EngineServer's OVERWORLD {@code world} field —
 * {@code anvilSiteAt} (anvilStands/damageState/progressDamage/breakAnvil
 * all reading {@code world.getBlock/setBlock}), {@code tickAnvilViewers}
 * passing {@code world::getBlock} into {@code stillValid}, the 1020/1021
 * sounds through the no-dimension {@code fxManager.sound} overload, and
 * {@code openAnvilOnTick} stamping dimension 0 through the 3-arg
 * {@code openContainerWindow} — while the furnace/beacon/chest opens
 * already stamp {@code session.dimension()} (the Slice B precedent).</p>
 *
 * <p>RED on develop: a nether anvil's window DIES on the first viewer
 * tick (the overworld's air cell answering the gate), and where the
 * overworld holds a mirrored anvil at the same coordinates the nether
 * repair's damage roll advances the OVERWORLD's anvil — cross-dimension
 * block damage — with the 1020/1021 audio stamped 0.</p>
 */
class AnvilWindowDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Sound> capturedSounds =
            java.util.Collections.synchronizedList(new ArrayList<>());

    /** The shared construction's coordinates (both worlds, same cell). */
    private static final BlockPosition ANVIL_AT = new BlockPosition(64, 101, 64);
    private static final BlockPosition SUPPORT_AT = new BlockPosition(64, 100, 64);

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "anvildim" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                capturedSounds.add(sound);
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
        return ((EngineBridge.Accepted) result).session();
    }

    /** The nether platform: a 5x5 stone floor at fy=100 with four open
     * air layers above it (the FX seat's own shape). */
    private void netherPlatform(EngineWorld nether) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = 101; y <= 104; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(SUPPORT_AT)),
                40, "the nether platform landed");
    }

    private void placeAnvil(EngineWorld world, BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> world.setBlock(at, AnvilBlocks.state(0, 0)));
        EngineAwaits.awaitCondition(server.world(),
                () -> AnvilBlocks.isAnvil(world.getBlock(at).identifier()),
                40, "the anvil landed at " + at);
    }

    private void placeStone(EngineWorld world, BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> world.setBlock(at, BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(world.getBlock(at)),
                40, "the support landed at " + at);
    }

    private void settlePlayerInNether(PlayerSession player) throws InterruptedException {
        player.setDimension(-1);
        player.applyMovement(new Position(64.5, 102.5, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 3, "the player settles in the nether");
    }

    // ------------------------------------------------------------------
    // Seat 1 — the window-usability direction: the nether anvil's window
    // must survive the viewer tick (isValid reads the menu's OWN world)
    // ------------------------------------------------------------------

    @Test
    void theNetherAnvilWindowSurvivesTheViewerTick() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        netherPlatform(nether);
        placeAnvil(nether, ANVIL_AT);

        // The precondition — the overworld's same-coordinate cell holds NO
        // anvil (the common case: the two worlds rarely mirror).
        assertFalse(AnvilBlocks.isAnvil(overworld.getBlock(ANVIL_AT).identifier()),
                "the precondition: the overworld cell at the same coordinates is no anvil");

        PlayerSession smith = join("NetherSmith");
        settlePlayerInNether(smith);

        // The REAL use wire: the right-click on the nether anvil opens the
        // repair window (AnvilBlock.use 53-59 over the acting world).
        AtomicInteger opened = new AtomicInteger(-1);
        server.useItemOnBlock(smith, ANVIL_AT, 1, java.util.Optional.empty(), opened::set);
        EngineAwaits.awaitCondition(server.world(),
                () -> opened.get() > 0, 40, "the nether anvil window opened");

        // Fifteen viewer ticks pass: the reference's isValid gate reads the
        // MENU'S OWN WORLD — the nether anvil stands and the player stands
        // beside it, so the window must survive every one of them.
        EngineAwaits.awaitTicks(server.world(), 15, "the viewer ticks pass");

        // THE PIN — the reference: AnvilMenu.isValid lines 308-311 consults
        // this.world (the ACTING world — the nether). The engine's
        // tickAnvilViewers passes the OVERWORLD's block reader instead
        // (EngineServer: stillValid(world::getBlock, ...)), so on develop the
        // overworld's air cell answers the gate and the window dies on the
        // first viewer tick — the nether anvil is non-interactive.
        assertNotNull(smith.anvilMenu(),
                "VT-040: the nether anvil's window must survive the viewer tick "
                        + "(AnvilMenu.isValid 308-311 reads the menu's OWN world — the acting "
                        + "world's cell, AnvilBlock.use 53-59's MenuProvider(world, pos)). "
                        + "Observed: the window closed (the overworld's cell answered the gate)");
        assertEquals(PlayerSession.ContainerKind.ANVIL, smith.openContainerKind(),
                "VT-040: the window kind must still be ANVIL after the viewer ticks");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the corruption direction: the nether repair's damage roll
    // must land on the NETHER anvil, never the overworld's mirror
    // ------------------------------------------------------------------

    @Test
    void theNetherRepairDamagesTheNetherAnvilNeverTheOverworlds() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        netherPlatform(nether);
        placeAnvil(nether, ANVIL_AT);

        // The mirrored geometry: the overworld holds a damage-0 anvil at the
        // SAME coordinates (stone-supported so the gravity arm never fells
        // it) — the corruption construction's own precondition.
        placeStone(overworld, SUPPORT_AT);
        placeAnvil(overworld, ANVIL_AT);
        assertTrue(AnvilBlocks.isAnvil(overworld.getBlock(ANVIL_AT).identifier()),
                "the precondition: the overworld mirror stands");

        PlayerSession smith = join("NetherSmith");
        settlePlayerInNether(smith);
        smith.addXpLevels(300); // every take costs one level (the material repair's law)

        // The REAL use wire opens the window.
        AtomicInteger opened = new AtomicInteger(-1);
        server.useItemOnBlock(smith, ANVIL_AT, 1, java.util.Optional.empty(), opened::set);
        EngineAwaits.awaitCondition(server.world(),
                () -> opened.get() > 0 && smith.anvilMenu() != null,
                40, "the nether anvil window opened and survived");
        int windowId = opened.get();

        // One full repair through the window's own clicks: a damaged iron
        // sword (damage 5) + one iron ingot (the material repair — the tier
        // table's iron row), the output take rolling the 0.12 damage die.
        // The take repeats until SOME anvil's state changes (the roll is
        // 0.12 per take; 120 takes bound the miss odds below 1e-7).
        for (int take = 0; take < 120; take++) {
            driveOneRepairTake(smith, windowId, take);

            // The roll landed somewhere — stop once an anvil's state changed
            // (or the window itself died: the mirror's break fells the gate
            // that the overworld cell was answering — corruption observed).
            boolean netherChanged = anvilChanged(nether, ANVIL_AT);
            boolean overworldChanged = anvilChanged(overworld, ANVIL_AT);
            if (netherChanged || overworldChanged || smith.anvilMenu() == null) {
                break;
            }
        }

        // THE PIN (the roll's target) — the reference: onItemRemoved lines
        // 81-93 reads and writes the MENU'S OWN WORLD — the nether anvil
        // takes the damage (or breaks past 2), exactly as an overworld
        // repair damages its own anvil.
        assertTrue(anvilChanged(nether, ANVIL_AT),
                "VT-040: the nether repair's 0.12 roll must land on the NETHER anvil "
                        + "(AnvilMenu.onItemRemoved 81-93 over the menu's OWN world — "
                        + "AnvilBlock.use 53-59's MenuProvider(world, pos)). Observed: "
                        + "the nether cell=" + nether.getBlock(ANVIL_AT).identifier().value()
                        + " (untouched) while the overworld cell="
                        + overworld.getBlock(ANVIL_AT).identifier().value()
                        + " — the roll consulted another world's cell");

        // THE PIN (the corruption) — the overworld's mirrored anvil must
        // never hear the nether take: no damage, no break, no phantom write
        // into a world the player never touched.
        assertFalse(anvilChanged(overworld, ANVIL_AT),
                "VT-040: the overworld's mirrored anvil must stay damage 0 — the nether "
                        + "take's progressDamage/breakAnvil wrote the OVERWORLD's cell "
                        + "(EngineServer.anvilSiteAt reads the overworld world field), "
                        + "cross-dimension block corruption. Observed overworld cell: "
                        + overworld.getBlock(ANVIL_AT).identifier().value());
    }

    // ------------------------------------------------------------------
    // Seat 3 — the audio stamp: the take's 1020/1021 sound must carry the
    // acting world's dimension (the VT-029 family's own shape)
    // ------------------------------------------------------------------

    @Test
    void theNetherTakeSoundsInTheNetherDimension() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        netherPlatform(nether);
        placeAnvil(nether, ANVIL_AT);
        placeStone(overworld, SUPPORT_AT);
        placeAnvil(overworld, ANVIL_AT); // the mirror keeps the window alive on the unfixed tree

        PlayerSession smith = join("NetherSmith");
        settlePlayerInNether(smith);
        smith.addXpLevels(10);

        AtomicInteger opened = new AtomicInteger(-1);
        server.useItemOnBlock(smith, ANVIL_AT, 1, java.util.Optional.empty(), opened::set);
        EngineAwaits.awaitCondition(server.world(),
                () -> opened.get() > 0 && smith.anvilMenu() != null,
                40, "the nether anvil window opened and survived");
        int windowId = opened.get();

        // ONE take: every take answers exactly one 1020/1021 sound — the
        // roll branch (progressDamage/breakAnvil) or the else arm's
        // useSound — so the first take pins the stamp deterministically.
        final int baseline = countAnvilSounds();
        driveOneRepairTake(smith, windowId, 0);
        assertTrue(countAnvilSounds() > baseline,
                "the take answered its 1020/1021 sound");

        FxManager.Sound take = latestAnvilSound();
        assertNotNull(take, "the take's 1020/1021 sound fired on the fx bus");
        // THE PIN — the reference: onItemRemoved lines 89/92 —
        // world.doEvent(1021/1020, pos, 0) over the ACTING world's own
        // listener list (World.doEvent -> world.playSound ->
        // PlayerManager 626-640's dimension gate). The engine's soundAt
        // calls the NO-DIMENSION fxManager.sound overload — the
        // unconditional overworld 0.
        assertEquals(-1, take.dimension(),
                "VT-040: the nether take's anvil sound must carry the NETHER's dimension "
                        + "(AnvilMenu.onItemRemoved 89/92: world.doEvent over the ACTING "
                        + "world's listener list — PlayerManager 626-640's dimension gate). "
                        + "Observed stamp: " + take.dimension());
    }

    /**
     * Drives one full repair take through the window's own clicks: the
     * cursor clear (the -999 outside click), the damaged sword onto input
     * 0, the iron ingot onto input 1, and the output take. Every hotbar
     * write rides the ticker — the same queue as the clicks, so the pickup
     * always reads the sword, never the ingot that overwrites the slot a
     * moment later.
     */
    private void driveOneRepairTake(PlayerSession smith, int windowId, int take)
            throws InterruptedException {
        // The previous take's output rides the cursor: the outside click
        // (wire slot -999, the reference InventoryMenu lines 178-192) throws
        // the whole stack into the world — the wire's own way to clear the
        // cursor between takes.
        if (!smith.inventory().cursor().isEmpty()) {
            server.windowClick(smith, windowId, -999, 0, 0, ok -> { });
            EngineAwaits.awaitCondition(server.world(),
                    () -> smith.inventory().cursor().isEmpty(),
                    40, "the cursor cleared (take " + take + ")");
        }
        // The damaged sword onto input 0: hotbar -> cursor -> the input.
        server.ticker().submit(() -> smith.inventory().setSlot(0,
                ItemStack.of(BuiltinItems.IRON_SWORD, 1).withDamage(5)));
        server.windowClick(smith, windowId, 30, 0, 0, ok -> { });
        server.windowClick(smith, windowId, 0, 0, 0, ok -> { });
        // The ingot onto input 1.
        server.ticker().submit(() -> smith.inventory().setSlot(0,
                ItemStack.of(BuiltinItems.IRON_INGOT, 1)));
        server.windowClick(smith, windowId, 30, 0, 0, ok -> { });
        server.windowClick(smith, windowId, 1, 0, 0, ok -> { });
        // The projection must exist before the take (the repair's own
        // output over the two inputs — updateResult rides the input click
        // itself, so this anchor proves both inputs landed).
        EngineAwaits.awaitCondition(server.world(),
                () -> smith.anvilMenu() != null && !smith.anvilMenu().output().isEmpty(),
                40, "the repair projection computed (take " + take + ")");

        final int baseline = countAnvilSounds();
        server.windowClick(smith, windowId, 2, 0, 0, ok -> { });
        boolean took = EngineAwaits.awaitConditionWithin(server.world(),
                () -> countAnvilSounds() > baseline,
                40, "the take answered its 1020/1021 sound (take " + take + ")");
        assertTrue(took, "the take consumed the repair (take " + take + ")");
    }

    /** Whether the cell at {@code at} left the damage-0 anvil state. */
    private static boolean anvilChanged(EngineWorld world, BlockPosition at) {
        var block = world.getBlock(at);
        return !AnvilBlocks.stateId(0, 0).equals(block.identifier());
    }

    private int countAnvilSounds() {
        return (int) capturedSounds.stream()
                .filter(sound -> sound.name().equals("random.anvil_use")
                        || sound.name().equals("random.anvil_break"))
                .count();
    }

    private FxManager.Sound latestAnvilSound() {
        return capturedSounds.stream()
                .filter(sound -> sound.name().equals("random.anvil_use")
                        || sound.name().equals("random.anvil_break"))
                .reduce((first, second) -> second).orElse(null);
    }
}
