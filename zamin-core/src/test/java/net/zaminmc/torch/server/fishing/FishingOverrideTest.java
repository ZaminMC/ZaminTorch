package net.zaminmc.torch.server.fishing;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §27.7 fishing extensibility (Slice 69 — the audit's named gap):
 * the loot override (the replaceable strategy over the three bands) and
 * the catch intercept (the observe/replace/cancel hook between the roll
 * and the drop) — with the vanilla law's own semantics preserved (the
 * cancel answers the bare retrieve; the fall-through composes).
 */
class FishingOverrideTest {

    /** The all-water world (the FishingSystemTest harness's own). */
    private static final class WaterWorld implements FishingSystem.WorldQuery {
        @Override public Position blockHit(Position from, Position to) {
            return null;
        }

        @Override public double waterFraction(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
            return 1.0;
        }

        @Override public boolean hasSkyAccess(int x, int y, int z) {
            return true;
        }

        @Override public boolean raining() {
            return false;
        }
    }

    /** The recording sinks (drops + XP). */
    private static final class Recording implements FishingSystem.HookResolver,
            FishingSystem.CatchSink {
        final List<String> catches = new ArrayList<>();
        final List<Integer> xps = new ArrayList<>();

        @Override public Object hookAlong(Position from, Position to, int throwerId,
                                          int airTicks) {
            return null;
        }

        @Override public boolean hookedAlive(Object hooked) {
            return true;
        }

        @Override public Position hookedAnchor(Object hooked) {
            return new Position(50.0, 64.0, 50.0);
        }

        @Override public void pullToward(Object hooked, double dx, double dy, double dz) {
        }

        @Override public void dropCatch(Position at, ItemStack stack,
                                        double anglerX, double anglerY, double anglerZ) {
            catches.add(stack.type().identifier() + ":" + stack.damage());
        }

        @Override public void dropXp(Position at, int amount) {
            xps.add(amount);
        }

        @Override public void sound(Position at, String key, float volume, float pitch) {
        }
    }

    /** One item's stack (the test's own shorthands). */
    private static ItemStack item(String name) {
        return ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:" + name))
                .orElseThrow(), 1);
    }

    /** Casts straight down and ticks until the bite window opens, then retrieves. */
    private static int fishAndRetrieve(FishingSystem fishing, int anglerId) {
        fishing.cast(anglerId, new Position(0.0, 65.0, 0.0), 0.0f, -90.0f);
        FishingSystem.Bobber bobber = fishing.bobberOf(anglerId);
        bobber.catchTimer = 1; // the shortest approach
        for (int i = 0; i < 300 && bobber.selfHitTimer <= 0; i++) {
            fishing.tick();
        }
        return fishing.retrieve(anglerId, new Position(0.0, 65.0, 0.0));
    }

    @Test
    void theLootOverrideReplacesEveryBandItAnswers() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(201L), 11_000);
        // The override answers a diamond for every band.
        fishing.setLootSource((category, luck, lure, random) -> item("diamond"));
        int result = fishAndRetrieve(fishing, 30);
        assertEquals(1, result, "a replaced catch is still a catch");
        assertEquals(1, sink.catches.size(), "one drop");
        assertEquals("minecraft:diamond:0", sink.catches.get(0),
                "the override's stack landed");
        assertEquals(1, sink.xps.size(), "the XP still rides the catch");
        fishing.setLootSource(null); // the restore
    }

    @Test
    void theLootOverrideFallsThroughPerBand() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(202L), 11_100);
        // The override only answers the treasure band; junk and fish fall
        // through to the vanilla tables.
        fishing.setLootSource((category, luck, lure, random) ->
                category == FishingSystem.LootCategory.TREASURE ? item("diamond") : null);
        for (int i = 0; i < 60; i++) {
            int result = fishAndRetrieve(fishing, 31 + i);
            assertEquals(1, result, "the catch landed (angler " + (31 + i) + ")");
        }
        assertTrue(sink.catches.contains("minecraft:diamond:0"),
                "the treasure answer appeared at least once over 60 rolls");
        long vanilla = sink.catches.stream()
                .filter(c -> !c.startsWith("minecraft:diamond")).count();
        assertTrue(vanilla > 0,
                "the fall-through bands answered the vanilla tables (junk/fish)");
        // Every fall-through drop must be one of the reference's own loot
        // items (the override's diamond answers carry the diamond id).
        for (String caught : sink.catches) {
            String id = caught.substring(0, caught.lastIndexOf(':'));
            if (id.equals("minecraft:diamond")) {
                continue; // the treasure override's own answer
            }
            assertTrue(id.endsWith("fish") || id.endsWith("leather") || id.endsWith("bone")
                            || id.endsWith("potion") || id.endsWith("string")
                            || id.endsWith("bowl") || id.endsWith("stick")
                            || id.endsWith("dye") || id.endsWith("tripwire_hook")
                            || id.endsWith("rotten_flesh") || id.endsWith("leather_boots")
                            || id.endsWith("fishing_rod") || id.endsWith("bow")
                            || id.endsWith("book") || id.endsWith("name_tag")
                            || id.endsWith("saddle") || id.endsWith("waterlily"),
                    "a vanilla item landed: " + id);
        }
    }

    @Test
    void theCatchInterceptorReplacesTheStack() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(203L), 11_200);
        fishing.addCatchInterceptor(context -> item("gold_ingot"));
        int result = fishAndRetrieve(fishing, 90);
        assertEquals(1, result, "a replaced catch is still a catch");
        assertEquals("minecraft:gold_ingot:0", sink.catches.get(0),
                "the interceptor's stack landed");
        assertEquals(1, sink.xps.size(), "the XP rides the replacement");
    }

    @Test
    void theCatchInterceptorCancelsTheWholeCatch() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(204L), 11_300);
        fishing.addCatchInterceptor(context -> ItemStack.EMPTY);
        int result = fishAndRetrieve(fishing, 91);
        assertEquals(0, result, "a cancelled catch answers the bare retrieve (no rod wear)");
        assertTrue(sink.catches.isEmpty(), "no drop");
        assertTrue(sink.xps.isEmpty(), "no XP — the catch never happened");
    }

    @Test
    void theInterceptorsRunInRegistrationOrderAndCompose() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(205L), 11_400);
        List<String> observed = new ArrayList<>();
        fishing.addCatchInterceptor(context -> {
            observed.add("first:" + context.caught().type().identifier());
            return item("gold_ingot"); // the replacement
        });
        fishing.addCatchInterceptor(context -> {
            observed.add("second:" + context.caught().type().identifier());
            return context.caught(); // the pass-through
        });
        int result = fishAndRetrieve(fishing, 92);
        assertEquals(1, result, "the chain's outcome is a catch");
        assertEquals(2, observed.size(), "both interceptors ran");
        assertEquals("second:minecraft:gold_ingot", observed.get(1),
                "the second interceptor saw the first's replacement (the chain composes)");
        assertEquals("minecraft:gold_ingot:0", sink.catches.get(0),
                "the pass-through kept the replacement");
    }

    @Test
    void theOverrideAndTheInterceptCompose() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(206L), 11_500);
        List<String> observedCategories = new ArrayList<>();
        fishing.setLootSource((category, luck, lure, random) -> item("diamond"));
        fishing.addCatchInterceptor(context -> {
            observedCategories.add(context.anglerId() + ":" + context.category()
                    + ":" + context.caught().type().identifier());
            return context.caught();
        });
        int result = fishAndRetrieve(fishing, 93);
        assertEquals(1, result, "the composition is a catch");
        assertEquals("93:FISH:minecraft:diamond", observedCategories.get(0),
                "the interceptor saw the override's stack under the rolled band");
    }

    @Test
    void theInterceptorRemovalRestoresTheVanillaWalk() {
        Recording sink = new Recording();
        FishingSystem fishing = new FishingSystem(new WaterWorld(), sink, sink,
                new Random(207L), 11_600);
        FishingSystem.CatchInterceptor canceller = context -> ItemStack.EMPTY;
        fishing.addCatchInterceptor(canceller);
        assertEquals(0, fishAndRetrieve(fishing, 94), "the interceptor cancels while registered");
        assertTrue(fishing.removeCatchInterceptor(canceller), "the removal answered");
        assertEquals(1, fishAndRetrieve(fishing, 95), "the vanilla catch lands again");
        assertEquals(1, sink.catches.size(), "one drop after the removal");
    }
}
