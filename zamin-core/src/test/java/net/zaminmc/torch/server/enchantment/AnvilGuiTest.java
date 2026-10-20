package net.zaminmc.torch.server.enchantment;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.player.ContainerOpenEvent;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.AnvilBlocks;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE ANVIL GUI (§29's Wave VIII row VIII5 — the reference
 * {@code inventory/menu/AnvilMenu.java} 1:1): the repair-cost ladder's
 * exact numbers, the combine arms (tool+tool, tool+material, book+book,
 * the enchantment merge with the same-level rule and the conflict
 * penalty), the too-expensive 40 gate with the creative bypass and the
 * pure-rename 39 clamp, the rename surcharge and the strip-on-repair
 * quirk, the XP payment, the 12% anvil-damage roll walking the DAMAGE
 * band, and the live open firing the shared §27.7 container-open
 * observation with the ANVIL kind.
 */
class AnvilGuiTest {

    @TempDir
    Path dataDir;

    // --- the shared fixtures -------------------------------------------------

    private static AnvilMenu menu(BooleanSupplier creative) {
        return new AnvilMenu(new BlockPosition(2, 5, 2), creative);
    }

    /** An iron sword (max durability 250) at the given damage. */
    private static ItemStack ironSword(int damage) {
        return ItemStack.of(BuiltinItems.IRON_SWORD).withDamage(damage);
    }

    private static ItemStack sharpness(ItemStack stack, int level) {
        return stack.withEnchantment(16, level); // Sharpness
    }

    private static ItemStack thorns(ItemStack stack, int level) {
        return stack.withEnchantment(7, level); // Thorns
    }

    private static ItemStack enchantedBook(int id, int level) {
        return ItemStack.of(BuiltinItems.ENCHANTED_BOOK).withEnchantment(id, level);
    }

    /** The forced-roll pin (the UnbreakingAcceptanceTest's own convention). */
    private static final class ForcedRandom extends Random {
        private final float nextFloat;

        ForcedRandom(float nextFloat) {
            this.nextFloat = nextFloat;
        }

        @Override
        public float nextFloat() {
            return nextFloat;
        }
    }

    /** A recording player: the level, the creative flag, the payments. */
    private static final class RecordingRepairer implements AnvilMenu.Repairer {
        private int level;
        private boolean creative;
        private int paidTotal;
        private int paidCalls;

        RecordingRepairer(int level) {
            this.level = level;
        }

        RecordingRepairer asCreative() {
            this.creative = true;
            return this;
        }

        @Override
        public int xpLevel() {
            return level;
        }

        @Override
        public boolean creative() {
            return creative;
        }

        @Override
        public void addXpLevels(int levels) {
            paidTotal += levels;
            paidCalls++;
            int next = level + levels;
            level = Math.max(0, next); // the below-0 zeroing
        }
    }

    /** A recording anvil site: the damage band, the world events. */
    private static final class RecordingSite implements AnvilMenu.AnvilSite {
        private boolean stands = true;
        private int damage;
        private int progressed;
        private int broke;
        private int used;

        @Override
        public boolean anvilStands() {
            return stands;
        }

        @Override
        public int damageState() {
            return damage;
        }

        @Override
        public void progressDamage() {
            progressed++;
            damage++;
        }

        @Override
        public void breakAnvil() {
            broke++;
            stands = false;
        }

        @Override
        public void useSound() {
            used++;
        }
    }

    // --- the repair-cost ladder -----------------------------------------------

    /**
     * The ladder's exact numbers: the prior-work RepairCost sum, the
     * enchantment weight rows (Sharpness's own type 10 answers 1 per
     * level), and the output's RepairCost doubling
     * (max(3, 5) * 2 + 1 = 11).
     */
    @Test
    void theRepairCostLaddersNumbers() {
        AnvilMenu plain = menu(() -> false);
        plain.inputSlotSet(0, sharpness(ironSword(0), 3));
        plain.inputSlotSet(1, sharpness(ironSword(0), 3));
        plain.updateResult();
        assertEquals(4, plain.repairCost(), "two Sharpness III swords: 1 per level at level 4");
        assertEquals(1, plain.output().repairCost(), "the clean inputs double into 1");
        assertEquals(4, plain.output().enchantments().get(16), "the same-level rule answers IV");

        AnvilMenu worked = menu(() -> false);
        worked.inputSlotSet(0, sharpness(ironSword(0), 3).withRepairCost(3));
        worked.inputSlotSet(1, sharpness(ironSword(0), 3).withRepairCost(5));
        worked.updateResult();
        assertEquals(12, worked.repairCost(), "prior 3+5 plus the Sharpness IV row 4");
        assertEquals(11, worked.output().repairCost(), "the higher row doubled plus one");
    }

    /**
     * The material arm (lines 141-157): one iron ingot repairs a quarter
     * of the maximum (62 of 250); the exact unit walk 200 -> 138 -> 76 ->
     * 14 -> 0 charges one per unit, and the take consumes exactly the
     * units spent out of the material stack.
     */
    @Test
    void theMaterialArmRepairsAQuarterPerUnit() {
        AnvilMenu repair = menu(() -> false);
        repair.inputSlotSet(0, ironSword(200));
        repair.inputSlotSet(1, ItemStack.of(BuiltinItems.IRON_INGOT, 5));
        repair.updateResult();
        assertEquals(4, repair.repairCost(), "four units spent: 200 -> 0");
        assertEquals(0, repair.output().damage(), "the full walk repairs to pristine");
        assertEquals(1, repair.output().count(), "the output carries the sword's own count");

        RecordingRepairer smith = new RecordingRepairer(10);
        repair.canTakeOutput(smith);
        RecordingSite site = new RecordingSite();
        ItemStack taken = repair.takeOutput(smith, new ForcedRandom(0.5F), site);
        assertEquals(5 - 4, repair.ingredient().count(), "the take consumes the four units spent");
        assertTrue(repair.input().isEmpty(), "the first input consumes wholesale");
        assertEquals(-4, smith.paidTotal, "four levels paid");
        assertEquals(1, site.used, "the missed roll answers the 1021 use sound");
        assertEquals(taken.damage(), 0);
    }

    // --- the tool+tool durability merge ---------------------------------------

    /**
     * The combine arm (lines 165-179): the remainders plus 12% of the
     * maximum, clamped at zero — 100+100 answers a pristine sword at cost
     * 2, 240+240 answers 200.
     */
    @Test
    void theToolPlusToolDurabilityMerge() {
        AnvilMenu light = menu(() -> false);
        light.inputSlotSet(0, ironSword(100));
        light.inputSlotSet(1, ironSword(100));
        light.updateResult();
        assertEquals(2, light.repairCost(), "the combine surcharge alone");
        assertEquals(0, light.output().damage(), "(150 + 150 + 30) exceeds the maximum: clamped 0");

        AnvilMenu heavy = menu(() -> false);
        heavy.inputSlotSet(0, ironSword(240));
        heavy.inputSlotSet(1, ironSword(240));
        heavy.updateResult();
        assertEquals(200, heavy.output().damage(), "(10 + 10 + 30) answers 200 damage");
        assertEquals(2, heavy.repairCost());

        AnvilMenu pristine = menu(() -> false);
        pristine.inputSlotSet(0, ironSword(0));
        pristine.inputSlotSet(1, ironSword(0));
        pristine.updateResult();
        assertTrue(pristine.output().isEmpty(), "no work: no output at all");
        assertEquals(0, pristine.repairCost());
    }

    // --- the enchantment merge ------------------------------------------------

    /**
     * The merge arms (lines 181-236): the same-level ++ rule, the
     * different-level max, the incompatibility penalty (Smite against
     * Sharpness — refused, +1), the book's halved weight (Thorns's type 1
     * answers 8, the book halves to 4), and book+book merging.
     */
    @Test
    void theEnchantmentMergeConflictPenaltyAndTheBookHalving() {
        AnvilMenu levels = menu(() -> false);
        levels.inputSlotSet(0, sharpness(ironSword(0), 3));
        levels.inputSlotSet(1, sharpness(ironSword(0), 1));
        levels.updateResult();
        assertEquals(3, levels.output().enchantments().get(16),
                "different levels answer the max, never the ++");

        AnvilMenu conflict = menu(() -> false);
        conflict.inputSlotSet(0, sharpness(ironSword(0), 3));
        conflict.inputSlotSet(1, ItemStack.of(BuiltinItems.IRON_SWORD)
                .withDamage(0).withEnchantment(17, 3)); // Smite III
        conflict.updateResult();
        assertFalse(conflict.output().enchantments().containsKey(17),
                "the incompatible enchantment never lands");
        assertEquals(1, conflict.repairCost(), "the conflict penalty row alone");
        assertEquals(3, conflict.output().enchantments().get(16), "the carried enchantment stays");

        AnvilMenu book = menu(() -> false);
        book.inputSlotSet(0, thorns(ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE), 2));
        book.inputSlotSet(1, enchantedBook(7, 3));
        book.updateResult();
        assertEquals(12, book.repairCost(), "Thorns III over II: the halved weight 4 * level 3");
        assertEquals(3, book.output().enchantments().get(7), "the book's level lands clamped at the cap");

        AnvilMenu books = menu(() -> false);
        books.inputSlotSet(0, enchantedBook(16, 3));
        books.inputSlotSet(1, enchantedBook(16, 2));
        books.updateResult();
        assertEquals(3, books.repairCost(), "book+book: Sharpness III over II at the halved weight");
        assertEquals(3, books.output().enchantments().get(16));
        assertEquals(BuiltinItems.ENCHANTED_BOOK.identifier(),
                books.output().type().identifier(), "the book output stays a book");
    }

    // --- the too-expensive gate + the rename -----------------------------------

    /**
     * The 40 gate (lines 261-263) with the creative bypass, and the
     * pure-rename 39 clamp (lines 257-259): a worked sword's rename never
     * hits the gate.
     */
    @Test
    void theTooExpensiveGateTheCreativeBypassAndThePureRenameClamp() {
        AnvilMenu expensive = menu(() -> false);
        expensive.inputSlotSet(0, sharpness(ironSword(0), 3).withRepairCost(19));
        expensive.inputSlotSet(1, sharpness(ironSword(0), 3).withRepairCost(19));
        expensive.updateResult();
        assertEquals(42, expensive.repairCost(), "prior 38 plus the Sharpness IV row 4");
        assertTrue(expensive.output().isEmpty(), "survival: the too-expensive gate answers nothing");

        AnvilMenu creativeMenu = menu(() -> true);
        creativeMenu.inputSlotSet(0, sharpness(ironSword(0), 3).withRepairCost(19));
        creativeMenu.inputSlotSet(1, sharpness(ironSword(0), 3).withRepairCost(19));
        creativeMenu.updateResult();
        assertEquals(42, creativeMenu.repairCost(), "the cost stays — only the output gate bypasses");
        assertFalse(creativeMenu.output().isEmpty(), "creative takes the too-expensive pair");

        AnvilMenu renameOnly = menu(() -> false);
        renameOnly.inputSlotSet(0, ironSword(0).withRepairCost(39));
        renameOnly.updateResult();
        renameOnly.setItemName("Bob");
        assertEquals(39, renameOnly.repairCost(),
                "the pure rename clamps: 40 (39 prior + 1) never blocks");
        assertFalse(renameOnly.output().isEmpty());
        assertEquals("Bob", renameOnly.output().displayName());
    }

    /**
     * The rename arms (lines 240-250): the +1 surcharge, the same-name
     * no-charge, and the strip-on-repair quirk — repairing a custom-named
     * item without sending a name strips the name and charges 1.
     */
    @Test
    void theRenameSurchargeAndTheStripQuirk() {
        AnvilMenu rename = menu(() -> false);
        rename.inputSlotSet(0, ironSword(0));
        rename.updateResult();
        rename.setItemName("Excalibur");
        assertEquals(1, rename.repairCost(), "the rename surcharge alone");
        assertEquals("Excalibur", rename.output().displayName());

        AnvilMenu sameName = menu(() -> false);
        sameName.inputSlotSet(0, ironSword(0));
        sameName.updateResult();
        sameName.setItemName("Iron Sword"); // the item's own hover name
        assertEquals(0, sameName.repairCost(), "the hover name's own text never charges");
        assertTrue(sameName.output().isEmpty(), "no work: no output");

        AnvilMenu stripped = menu(() -> false);
        stripped.inputSlotSet(0, sharpness(ironSword(0).withName("Hero"), 3));
        stripped.inputSlotSet(1, sharpness(ironSword(0), 3));
        stripped.updateResult();
        assertNull(stripped.output().displayName(),
                "the strip-on-repair quirk: the custom name never survives a combine");
        assertEquals(5, stripped.repairCost(), "the Sharpness IV row 4 plus the strip row 1");

        AnvilMenu blankSent = menu(() -> false);
        blankSent.inputSlotSet(0, ironSword(0).withName("Hero"));
        blankSent.updateResult();
        blankSent.setItemName("  ");
        assertEquals(1, blankSent.repairCost(), "a blank send IS the strip form");
        assertNull(blankSent.output().displayName());
    }

    // --- the XP payment ---------------------------------------------------------

    /**
     * The take's payment ({@code onItemRemoved} line 64): the levels
     * consume through {@code addXp}, and a payment past the bar zeroes
     * the whole level model.
     */
    @Test
    void theTakePaysItsLevelsThroughThePlayerArms() {
        AnvilMenu repair = menu(() -> false);
        repair.inputSlotSet(0, ironSword(200));
        repair.inputSlotSet(1, ItemStack.of(BuiltinItems.IRON_INGOT, 5));
        repair.updateResult();
        assertEquals(4, repair.repairCost());

        RecordingRepairer rich = new RecordingRepairer(10);
        assertTrue(repair.canTakeOutput(rich));
        repair.takeOutput(rich, new ForcedRandom(0.5F), new RecordingSite());
        assertEquals(6, rich.level, "ten levels minus the four-unit cost");
        assertFalse(repair.canTakeOutput(new RecordingRepairer(3)),
                "a poorer smith never passes the canPickUp gate");

        AnvilMenu deep = menu(() -> false);
        deep.inputSlotSet(0, ironSword(200));
        deep.inputSlotSet(1, ItemStack.of(BuiltinItems.IRON_INGOT, 5));
        deep.updateResult();
        RecordingRepairer poor = new RecordingRepairer(2);
        deep.takeOutput(poor, new ForcedRandom(0.5F), new RecordingSite());
        assertEquals(0, poor.level, "the below-zero payment zeroes the bar");
    }

    // --- the 12% anvil-damage roll ----------------------------------------------

    /**
     * The take's roll ({@code onItemRemoved} lines 81-93): the 0.12 band
     * walks the DAMAGE row — a miss answers the 1021 use sound, a hit at
     * 0/1 progresses the state, a hit past 2 breaks the anvil with the
     * 1020 event, and a creative take never rolls at all.
     */
    @Test
    void theTwelvePercentRollWalksTheDamageBand() {
        AnvilMenu repair = menu(() -> false);
        repair.inputSlotSet(0, ironSword(200));
        repair.inputSlotSet(1, ItemStack.of(BuiltinItems.IRON_INGOT, 5));
        repair.updateResult();

        RecordingSite missed = new RecordingSite();
        repair.takeOutput(new RecordingRepairer(10), new ForcedRandom(0.5F), missed);
        assertEquals(1, missed.used, "the 88% arm answers the use sound");
        assertEquals(0, missed.progressed + missed.broke);

        RecordingSite progressed = new RecordingSite();
        repair.takeOutput(new RecordingRepairer(10), new ForcedRandom(0.05F), progressed);
        assertEquals(1, progressed.progressed, "the 12% arm walks the DAMAGE row forward");
        assertEquals(0, progressed.broke, "a fresh anvil never breaks on its first roll");

        RecordingSite doomed = new RecordingSite();
        doomed.damage = 2;
        repair.takeOutput(new RecordingRepairer(10), new ForcedRandom(0.05F), doomed);
        assertEquals(1, doomed.broke, "past 2 the anvil breaks (the ++l > 2 arm)");
        assertEquals(0, doomed.used, "the break answers the 1020 event, never the 1021");

        RecordingSite creative = new RecordingSite();
        repair.takeOutput(new RecordingRepairer(10).asCreative(), new ForcedRandom(0.05F), creative);
        assertEquals(1, creative.used, "a creative take never rolls — the plain use sound");
        assertEquals(0, creative.progressed + creative.broke);
    }

    // --- the close walk -----------------------------------------------------------

    /**
     * The reference close (lines 294-305): the two INPUT slots drop; the
     * output is a server-only projection and is never returned.
     */
    @Test
    void theCloseDropsTheInputsAndNeverTheProjection() {
        AnvilMenu closing = menu(() -> false);
        closing.inputSlotSet(0, sharpness(ironSword(100), 3));
        closing.inputSlotSet(1, ItemStack.of(BuiltinItems.IRON_INGOT, 5));
        closing.updateResult();
        assertFalse(closing.output().isEmpty());

        ItemStack[] dropped = closing.closeDrop();
        assertEquals(2, dropped.length);
        assertEquals(BuiltinItems.IRON_SWORD.identifier(), dropped[0].type().identifier());
        assertEquals(BuiltinItems.IRON_INGOT.identifier(), dropped[1].type().identifier());
        assertTrue(closing.input().isEmpty() && closing.ingredient().isEmpty());
        assertEquals(0, closing.repairCost(), "the close resets the cost row");
    }

    // --- the live engine: the open observation + the repair + the rename + the take

    private static EngineServer boot(Path dataDir, String worldName) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, worldName, "it",
                20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(EngineServer server, String name) {
        List<String> sink = new ArrayList<>();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                sink.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void stop(EngineServer server) {
        server.shutdown(null);
        try {
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The use arm opens the anvil through the established machinery and
     * the open fires the shared §27.7 container-open observation with the
     * ANVIL kind (the one-window law closing any carried window first).
     */
    @Test
    void theUseArmFiresTheSharedContainerOpenObservationWithTheAnvilKind() throws Exception {
        EngineServer server = boot(dataDir, "anvilopen");
        try {
            PlayerSession player = join(server, "Smith");
            List<ContainerOpenEvent> seen = new CopyOnWriteArrayList<>();
            var sub = server.events().on(ContainerOpenEvent.class, seen::add);
            try {
                BlockPosition anvil = new BlockPosition(4, 5, 4);
                var anvilType = AnvilBlocks.state(0, 0);
                server.ticker().submit(() -> server.world().setBlock(anvil, anvilType));
                await(() -> server.world().getBlock(anvil).equals(anvilType), "the anvil seeded");

                AtomicInteger windowId = new AtomicInteger();
                server.useItemOnBlock(player, anvil, 1, java.util.Optional.empty(),
                        windowId::set);
                await(() -> !seen.isEmpty(), "the anvil's open fired the shared event");
                await(() -> windowId.get() > 0, "the window opened");
                ContainerOpenEvent event = seen.get(0);
                assertEquals("ANVIL", event.kind(), "the kind names the family");
                assertEquals(anvil, event.position(), "the position carries the anvil's cell");
                assertEquals(player.uuid(), event.openerId(), "the opener rides the event");
                assertTrue(event.windowId() > 0, "the window id is the wire's own counter");
                assertEquals(PlayerSession.ContainerKind.ANVIL, player.openContainerKind());
                assertTrue(player.anvilMenu() != null, "the per-open menu lives on the session");
            } finally {
                sub.unsubscribe();
            }
        } finally {
            stop(server);
        }
    }

    /**
     * The live repair + rename + take: the two damaged Sharpness III swords
     * quick-move into the inputs, the cost climbs the exact ladder (2 the
     * durability merge + 4 the Sharpness IV row + 1 the rename surcharge =
     * 7), the MC|ItemName rename lands, the take pays its levels through
     * the session's own XP model, the inputs consume, and the anvil stands
     * inside the roll's first band whatever the player's own dice said.
     */
    @Test
    void theLiveRepairRenameAndTakeWalkTheWholeLadder() throws Exception {
        EngineServer server = boot(dataDir, "anvilflow");
        try {
            PlayerSession smith = join(server, "Smith");
            assertEquals(GameMode.SURVIVAL, smith.gamemode());
            smith.setTotalXp(net.zaminmc.torch.server.experience.ExperienceMath
                    .totalXpForLevel(10));
            assertEquals(10, smith.experienceLevel());

            BlockPosition anvil = new BlockPosition(4, 5, 4);
            var anvilType = AnvilBlocks.state(0, 0);
            server.ticker().submit(() -> server.world().setBlock(anvil, anvilType));
            await(() -> server.world().getBlock(anvil).equals(anvilType), "the anvil seeded");

            AtomicInteger windowId = new AtomicInteger();
            server.useItemOnBlock(smith, anvil, 1, java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the anvil window opened");

            smith.inventory().setSlot(0, sharpness(ironSword(100), 3));
            smith.inventory().setSlot(1, sharpness(ironSword(100), 3));

            // Quick-move both swords into the two inputs (wire 30/31 = the hotbar).
            server.windowClick(smith, windowId.get(), 30, 0, 1, accepted -> { });
            await(() -> !smith.anvilMenu().input().isEmpty(), "the first sword landed in input 0");
            server.windowClick(smith, windowId.get(), 31, 0, 1, accepted -> { });
            await(() -> !smith.anvilMenu().ingredient().isEmpty(), "the second sword landed in input 1");

            AnvilMenu menu = smith.anvilMenu();
            await(() -> !menu.output().isEmpty(), "the repair projection computed");
            assertEquals(6, menu.repairCost(), "2 the durability merge + 4 the Sharpness IV row");
            assertEquals(0, menu.output().damage(), "100+100 merge to the clamped zero");

            // The rename channel (MC|ItemName's engine arm).
            server.anvilItemName(smith, "Excalibur");
            await(() -> "Excalibur".equals(menu.output().displayName()), "the rename landed");
            await(() -> menu.repairCost() == 7, "the rename surcharge added its row");

            // The take: shift-click the output into the inventory.
            server.windowClick(smith, windowId.get(), 2, 0, 1, accepted -> { });
            await(() -> smith.experienceLevel() == 3, "ten levels minus the seven-level cost");
            await(() -> menu.input().isEmpty() && menu.ingredient().isEmpty(),
                    "both inputs consumed");
            assertEquals(0, menu.repairCost(), "the take resets the cost row");
            assertTrue(smith.inventory().snapshot().stream().anyMatch(stack ->
                            !stack.isEmpty()
                                    && stack.type().identifier()
                                            .equals(BuiltinItems.IRON_SWORD.identifier())
                                    && "Excalibur".equals(stack.displayName())
                                    && stack.enchantments() != null
                                    && stack.enchantments().get(16) == 4),
                    "the renamed Sharpness IV sword came out");

            // The roll's own aftermath: a fresh anvil either kept its state
            // (the 88%) or moved one DAMAGE row (the 12%) — never more.
            int after = AnvilBlocks.damageOf(server.world().getBlock(anvil).identifier());
            assertTrue(after == 0 || after == 1,
                    "the take's roll moved the DAMAGE band at most one row");
        } finally {
            stop(server);
        }
    }

    /**
     * The walk-away gate ({@code isValid} lines 307-311): the per-tick
     * validity walk closes the window the tick the player leaves the
     * eight-block band, dropping the menu's inputs.
     */
    @Test
    void theWalkAwayGateClosesTheWindow() throws Exception {
        EngineServer server = boot(dataDir, "anvilwalk");
        try {
            PlayerSession smith = join(server, "Smith");
            BlockPosition anvil = new BlockPosition(4, 5, 4);
            var anvilType = AnvilBlocks.state(0, 0);
            server.ticker().submit(() -> server.world().setBlock(anvil, anvilType));
            await(() -> server.world().getBlock(anvil).equals(anvilType), "the anvil seeded");

            AtomicInteger windowId = new AtomicInteger();
            server.useItemOnBlock(smith, anvil, 1, java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the anvil window opened");

            smith.inventory().setSlot(0, ironSword(100));
            server.windowClick(smith, windowId.get(), 30, 0, 1, accepted -> { });
            await(() -> smith.anvilMenu() != null && !smith.anvilMenu().input().isEmpty(),
                    "the sword landed in input 0");

            // Twelve blocks away: beyond the squared-64 band.
            server.teleportPlayer(smith, new Position(20, 5, 20));
            await(() -> smith.anvilMenu() == null, "the walk-away gate closed the menu");
            await(() -> smith.openContainerKind() == PlayerSession.ContainerKind.NONE,
                    "the window state cleared");
            await(() -> smith.inventory().snapshot().stream().noneMatch(stack -> !stack.isEmpty()
                            && stack.type().identifier()
                                    .equals(BuiltinItems.IRON_SWORD.identifier())),
                    "the abandoned input dropped to the world, never kept");
        } finally {
            stop(server);
        }
    }

    // --- the anvil-state consults ---------------------------------------------

    /** The stillValid gate reads the twelve-state family, not one spelling. */
    @Test
    void theValidityGateReadsTheWholeTwelveStateFamily() {
        AnvilMenu menu = menu(() -> false);
        List<net.zaminmc.torch.server.block.EngineBlockType> family = AnvilBlocks.familyBlocks();
        for (net.zaminmc.torch.server.block.EngineBlockType state : family) {
            AnvilMenu.WorldView world = position -> state;
            assertTrue(menu.stillValid(world, 2.5, 5.5, 4.5),
                    "the family's every state stands: " + state.identifier());
        }
        AnvilMenu.WorldView gone = position -> BuiltinBlocks.AIR;
        assertFalse(menu.stillValid(gone, 2.5, 5.5, 4.5), "a vanished anvil fails the gate");
        AnvilMenu.WorldView far = position -> AnvilBlocks.state(0, 0);
        assertFalse(menu.stillValid(far, 30, 5, 30), "beyond the eight-block band the gate fails");
    }
}
