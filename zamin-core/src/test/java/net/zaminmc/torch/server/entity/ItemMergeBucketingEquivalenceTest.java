package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The merge walk's spatial bucketing, pinned EQUIVALENT to the old law (§22
 * Phase 7 — the demand-named heavy-owner optimization's own evidence bar).
 *
 * <p><b>The bar:</b> the bucketed walk must produce results IDENTICAL to the
 * old O(n²) walk — same absorbed entities, same final stacks, same partial
 * remainders, AND the same listener publication sequence (the wire-visible
 * event order). THE METHOD: {@link #referenceWalk} below is a VERBATIM copy
 * of the old {@code mergeNearbyItems} inner law (the double loop as it sat
 * before the bucketing, checks in the old order, merge arms and publication
 * points unchanged); every scene is spawned twice — once into the real
 * manager (the bucketed walk), once into the test's own entity list (the
 * reference walk) — and compared after every walk round on the surviving
 * population (ids AND list order), each body's whole state (stack, position,
 * age, the expired flag), and the full publication sequence captured
 * eagerly at publish time. The bucketed walk consults only its 27 nearby
 * cells and sorts the candidates back into list order, so agreement is by
 * construction — this test is the per-seed proof that the construction
 * holds, including the deterministic edges (the inclusive 0.75 window
 * boundary, cell-boundary straddles, the packed key's coordinate clamps,
 * the expired-skip semantics, the dense-neighborhood plain-scan fallback
 * and its bail boundary, cascading partial remainders over multiple
 * walks).</p>
 */
class ItemMergeBucketingEquivalenceTest {

    /** The open-air ground stub — the walk consults no world at all. */
    private static final ItemEntity.Ground OPEN_AIR = (x, y, z) -> false;

    /** One spawned body's whole input state (the scene's row). */
    private record SpawnSpec(Position position, ItemStack stack, int age,
                             int pickupDelay, boolean neverDespawn) {
    }

    /** One wire-visible publication, captured EAGERLY (the payload the wire saw at publish time). */
    private record Publication(String kind, int entityId, int count) {
    }

    /** Records the walk's own publications exactly as the wire dispatch receives them. */
    private static final class RecordingListener implements ItemEntityManager.Listener {
        final List<Publication> events = new ArrayList<>();

        @Override
        public void onItemSpawned(ItemEntity entity) {
            // spawn noise — the walk's comparison starts after the scene is built
        }

        @Override
        public void onItemMoved(ItemEntity entity) {
        }

        @Override
        public void onItemCollected(ItemEntity entity,
                net.zaminmc.torch.server.player.PlayerSession collector, int collectedCount) {
        }

        @Override
        public void onItemStackChanged(ItemEntity entity) {
            events.add(new Publication("stack", entity.entityId(), entity.stack().count()));
        }

        @Override
        public void onItemRemoved(ItemEntity entity, String reason) {
            events.add(new Publication("removed:" + reason, entity.entityId(),
                    entity.stack().count()));
        }
    }

    /**
     * THE OLD LAW, verbatim — the {@code mergeNearbyItems} double loop as it
     * sat before the spatial bucketing (checks in the old order, the merge
     * arms, the publication points). Do not "improve" this copy: it is the
     * equivalence bar itself. Runs over the test's own entity list, with the
     * publications captured at the same points the old listener dispatches
     * fired.
     */
    private static void referenceWalk(List<ItemEntity> entities, List<Publication> events) {
        List<ItemEntity> absorbed = null;
        for (int i = 0; i < entities.size(); i++) {
            ItemEntity keeper = entities.get(i);
            if (keeper.expired() || (absorbed != null && absorbed.contains(keeper))) {
                continue;
            }
            for (int j = 0; j < entities.size(); j++) {
                if (j == i) {
                    continue;
                }
                ItemEntity other = entities.get(j);
                if (other.expired() || (absorbed != null && absorbed.contains(other))) {
                    continue;
                }
                // Wave TF-9: the same-dimension gate the live walk carries
                // (the reference's same-world getEntitiesOfType scan) — a
                // no-op over this test's single-dimension populations, kept
                // so the copy stays the bar for the CURRENT law.
                if (other.entityId() < keeper.entityId()
                        || other.dimension() != keeper.dimension()
                        || !referenceWithinMergeRange(keeper, other)
                        || !ItemStack.mergeable(keeper.stack(), other.stack())) {
                    continue;
                }
                // "other" (younger) empties into "keeper" (older), capped.
                int capacity = keeper.stack().type().maxStackSize() - keeper.stack().count();
                int moved = Math.min(capacity, other.stack().count());
                if (moved <= 0) {
                    continue; // keeper's stack is full: nothing combines
                }
                keeper.setStack(keeper.stack().withCount(keeper.stack().count() + moved));
                if (absorbed == null) {
                    absorbed = new ArrayList<>();
                }
                if (moved == other.stack().count()) {
                    absorbed.add(other);
                    events.add(new Publication("stack", keeper.entityId(), keeper.stack().count()));
                    events.add(new Publication("removed:merged", other.entityId(),
                            other.stack().count()));
                } else {
                    other.setStack(other.stack().withCount(other.stack().count() - moved));
                    events.add(new Publication("stack", keeper.entityId(), keeper.stack().count()));
                    events.add(new Publication("stack", other.entityId(), other.stack().count()));
                }
            }
        }
        if (absorbed != null) {
            entities.removeAll(absorbed);
        }
    }

    /** The old {@code withinMergeRange}, verbatim (over the same constants the manager reads). */
    private static boolean referenceWithinMergeRange(ItemEntity a, ItemEntity b) {
        double dx = Math.abs(a.position().x() - b.position().x());
        double dy = Math.abs(a.position().y() - b.position().y());
        double dz = Math.abs(a.position().z() - b.position().z());
        return dx <= ItemEntityManager.MERGE_BOX_EXPANSION + 2 * ItemEntity.HALF_WIDTH
                && dy <= ItemEntityManager.MERGE_BOX_EXPANSION + 2 * ItemEntity.HALF_HEIGHT
                && dz <= ItemEntityManager.MERGE_BOX_EXPANSION + 2 * ItemEntity.HALF_WIDTH;
    }

    /**
     * Spawns the scene into BOTH worlds (the manager through its own
     * {@code spawnRestored} surface — exact positions, sequential ids from
     * 1 — and the reference list over the same specs), then drives
     * {@code walks} merge-walk rounds on each and compares after EVERY
     * round: the surviving population, every body's whole state, and the
     * accumulated publication sequence.
     */
    private void assertSceneAgrees(String context, List<SpawnSpec> specs, int walks) {
        RecordingListener recorder = new RecordingListener();
        ItemEntityManager manager = new ItemEntityManager(OPEN_AIR, new Random(1), 1);
        manager.addListener(recorder);
        List<ItemEntity> reference = new ArrayList<>(specs.size());
        for (int i = 0; i < specs.size(); i++) {
            SpawnSpec spec = specs.get(i);
            ItemEntity entity = manager.spawnRestored(spec.position(), spec.stack());
            entity.setAge(spec.age());
            entity.setPickupDelayTicks(spec.pickupDelay());
            if (spec.neverDespawn()) {
                entity.setNeverDespawn();
            }
            ItemEntity twin = new ItemEntity(i + 1, spec.position(), spec.stack(),
                    spec.pickupDelay());
            twin.setAge(spec.age());
            if (spec.neverDespawn()) {
                twin.setNeverDespawn();
            }
            reference.add(twin);
        }
        List<Publication> referenceEvents = new ArrayList<>();
        for (int walk = 0; walk < walks; walk++) {
            manager.mergeNearbyItems();
            referenceWalk(reference, referenceEvents);
            assertWalksAgree(context + ", walk " + walk, manager, reference,
                    recorder, referenceEvents);
        }
    }

    private void assertWalksAgree(String context, ItemEntityManager manager,
                                  List<ItemEntity> reference, RecordingListener recorder,
                                  List<Publication> referenceEvents) {
        List<ItemEntity> actual = manager.all();
        List<Integer> expectedIds = new ArrayList<>();
        List<Integer> actualIds = new ArrayList<>();
        for (ItemEntity entity : reference) {
            expectedIds.add(entity.entityId());
        }
        for (ItemEntity entity : actual) {
            actualIds.add(entity.entityId());
        }
        assertEquals(expectedIds, actualIds,
                context + ": the surviving population (ids and list order)");
        for (int i = 0; i < reference.size(); i++) {
            ItemEntity expected = reference.get(i);
            ItemEntity got = actual.get(i);
            assertEquals(expected.stack(), got.stack(),
                    context + ": entity " + expected.entityId() + "'s stack");
            assertEquals(expected.position(), got.position(),
                    context + ": entity " + expected.entityId() + "'s position");
            assertEquals(expected.age(), got.age(),
                    context + ": entity " + expected.entityId() + "'s age");
            assertEquals(expected.expired(), got.expired(),
                    context + ": entity " + expected.entityId() + "'s expired flag");
        }
        assertEquals(referenceEvents, recorder.events,
                context + ": the wire-visible publication sequence");
    }

    // ------------------------------------------------------------------
    // The randomized sweep: 400 seeds over pile/tight/roomy/sparse boxes,
    // mixed stack shapes (full stacks, capacity-1 stacks, worn tools,
    // named/enchanted tagged stacks, mid-cap stacks), expired and pinned
    // bodies, stamped pickup delays, 1-3 walk rounds each.
    // ------------------------------------------------------------------

    @Test
    void randomizedScenesMatchTheOldLawExactly() {
        for (int seed = 0; seed < 400; seed++) {
            Random random = new Random(seed);
            List<SpawnSpec> specs = randomScene(random);
            int walks = 1 + random.nextInt(3);
            assertSceneAgrees("seed " + seed, specs, walks);
        }
    }

    @Test
    void largeRandomizedScenesMatchTheOldLawExactly() {
        // The heavy-merge shape: hundreds of bodies in a tight box — cascading
        // full/partial absorptions across chains of keepers, the exact regime
        // where an order divergence would compound.
        for (long seed : new long[]{101L, 202L, 303L}) {
            Random random = new Random(seed);
            int count = 420 + random.nextInt(240);
            double side = 6.0;
            double originX = (random.nextInt(5) - 2) * 32.0;
            double originZ = (random.nextInt(5) - 2) * 32.0;
            List<SpawnSpec> specs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                specs.add(new SpawnSpec(
                        new Position(originX + random.nextDouble() * side,
                                40.0 + random.nextDouble() * side,
                                originZ + random.nextDouble() * side),
                        randomStack(random),
                        random.nextInt(ItemEntity.DESPAWN_TICKS),
                        random.nextInt(64),
                        false));
            }
            assertSceneAgrees("large seed " + seed, specs, 2);
        }
    }

    /** The scene generator: positions, stack shapes, ages, delays — the whole law's input space. */
    private static List<SpawnSpec> randomScene(Random random) {
        int count = 1 + random.nextInt(90);
        // The scene's spread: a pile (everything in window), a tight box, a
        // roomy box, a sparse field — and origins that straddle cell, chunk
        // and coordinate-sign boundaries.
        double side = switch (random.nextInt(4)) {
            case 0 -> 0.6;
            case 1 -> 3.0;
            case 2 -> 14.0;
            default -> 60.0;
        };
        double originX = (random.nextInt(7) - 3) * 16.0;
        double originY = 3.0 + random.nextInt(3) * 8.0;
        double originZ = (random.nextInt(7) - 3) * 16.0;
        List<SpawnSpec> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Position position = new Position(
                    originX + random.nextDouble() * side,
                    originY + random.nextDouble() * side,
                    originZ + random.nextDouble() * side);
            // ~10% expired (skipped by the walk but still present in the
            // list — the walk's own removal discipline is absorb-only);
            // expired-and-pinned bodies (never despawn) DO participate.
            int age = random.nextInt(11) == 0
                    ? ItemEntity.DESPAWN_TICKS + random.nextInt(500)
                    : random.nextInt(ItemEntity.DESPAWN_TICKS);
            boolean neverDespawn = age >= ItemEntity.DESPAWN_TICKS && random.nextBoolean();
            specs.add(new SpawnSpec(position, randomStack(random), age,
                    random.nextInt(64), neverDespawn));
        }
        return specs;
    }

    /** The stack-shape mixer: every mergeability arm the law has. */
    private static ItemStack randomStack(Random random) {
        return switch (random.nextInt(10)) {
            // max-stack-1 bodies (capacity 0: the full-keeper skip arm)
            case 0 -> ItemStack.of(BuiltinItems.BOW, 1);
            // worn bows: two different damages never merge, same damage hits the capacity-0 arm
            case 1 -> ItemStack.of(BuiltinItems.BOW, 1).withDamage(1 + random.nextInt(50));
            // mid-cap stacks (16): the partial-remainder factory
            case 2 -> ItemStack.of(BuiltinItems.ENDER_PEARL, 1 + random.nextInt(16));
            case 3 -> ItemStack.of(BuiltinItems.SNOWBALL, 1 + random.nextInt(16));
            case 4 -> ItemStack.of(BuiltinItems.EGG, 1 + random.nextInt(16));
            // always-full stacks (capacity 0 against every neighbor)
            case 5 -> ItemStack.of(BuiltinItems.DIAMOND, 64);
            // plain 64-cap stacks, partial and full-ish
            case 6 -> ItemStack.of(BuiltinItems.STICK, 1 + random.nextInt(64));
            // named stacks: only identically-named ones merge
            case 7 -> ItemStack.of(BuiltinItems.DIRT, 1 + random.nextInt(64))
                    .withName("Named " + random.nextInt(3));
            // enchanted stacks: only identically-enchanted ones merge
            case 8 -> ItemStack.of(BuiltinItems.OAK_LOG, 1 + random.nextInt(64))
                    .withEnchantments(Map.of(1 + random.nextInt(3), 1));
            default -> ItemStack.of(BuiltinItems.DIRT, 1 + random.nextInt(64));
        };
    }

    // ------------------------------------------------------------------
    // The deterministic edges.
    // ------------------------------------------------------------------

    @Test
    void theInclusiveWindowBoundaryStaysExact() {
        // |delta| 0.75 exactly merges on every axis (the window is
        // inclusive); 0.751 does not. All coordinates exact binary fractions.
        assertSceneAgrees("window edges", List.of(
                // merges: exactly 0.75 on x
                new SpawnSpec(new Position(10.0, 64.0, 10.0),
                        ItemStack.of(BuiltinItems.DIRT, 3), 0, 40, false),
                new SpawnSpec(new Position(10.75, 64.0, 10.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 40, false),
                // no merge: 0.751 on x
                new SpawnSpec(new Position(20.0, 64.0, 20.0),
                        ItemStack.of(BuiltinItems.STICK, 7), 0, 0, false),
                new SpawnSpec(new Position(20.751, 64.0, 20.0),
                        ItemStack.of(BuiltinItems.STICK, 9), 0, 0, false),
                // merges: exactly 0.75 on all three axes at once
                new SpawnSpec(new Position(30.0, 64.0, 30.0),
                        ItemStack.of(BuiltinItems.DIRT, 2), 0, 0, false),
                new SpawnSpec(new Position(30.75, 64.75, 30.75),
                        ItemStack.of(BuiltinItems.DIRT, 4), 0, 0, false),
                // no merge: the third axis 0.76
                new SpawnSpec(new Position(40.0, 64.0, 40.0),
                        ItemStack.of(BuiltinItems.STICK, 2), 0, 0, false),
                new SpawnSpec(new Position(40.75, 64.75, 40.76),
                        ItemStack.of(BuiltinItems.STICK, 4), 0, 0, false),
                // merges: exactly 0.75 on y alone
                new SpawnSpec(new Position(50.0, 64.0, 50.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false),
                new SpawnSpec(new Position(50.0, 64.75, 50.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false)
        ), 2);
    }

    @Test
    void pairsStraddlingCellBoundariesMerge() {
        // The 1.0-block cell grid must never lose a window pair to a cell
        // edge: these pairs sit in DIFFERENT cells and merge anyway.
        assertSceneAgrees("cell straddles", List.of(
                // x cells 0 | 1
                new SpawnSpec(new Position(0.9, 5.0, 0.9),
                        ItemStack.of(BuiltinItems.DIRT, 3), 0, 0, false),
                new SpawnSpec(new Position(1.65, 5.0, 0.9),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false),
                // the sign boundary: x cells -1 | 0
                new SpawnSpec(new Position(-0.1, 15.0, -0.1),
                        ItemStack.of(BuiltinItems.STICK, 4), 0, 0, false),
                new SpawnSpec(new Position(0.65, 15.0, -0.1),
                        ItemStack.of(BuiltinItems.STICK, 6), 0, 0, false),
                // y cells 63 | 64
                new SpawnSpec(new Position(10.9, 63.9, 10.0),
                        ItemStack.of(BuiltinItems.DIRT, 2), 0, 0, false),
                new SpawnSpec(new Position(10.9, 64.65, 10.0),
                        ItemStack.of(BuiltinItems.DIRT, 2), 0, 0, false),
                // the z sign boundary: z cells -1 | 0
                new SpawnSpec(new Position(-10.9, 5.0, -0.9),
                        ItemStack.of(BuiltinItems.EGG, 8), 0, 0, false),
                new SpawnSpec(new Position(-10.9, 5.0, -0.15),
                        ItemStack.of(BuiltinItems.EGG, 8), 0, 0, false),
                // a straddling NON-merge: 0.76 across the same boundary
                new SpawnSpec(new Position(100.9, 5.0, 0.9),
                        ItemStack.of(BuiltinItems.STICK, 1), 0, 0, false),
                new SpawnSpec(new Position(101.66, 5.0, 0.9),
                        ItemStack.of(BuiltinItems.STICK, 1), 0, 0, false)
        ), 2);
    }

    @Test
    void coordinateClampEdgesStayEquivalent() {
        // Beyond the packed key's clamps (far past the world border
        // horizontally, past the vertical packing band) the clamp folds
        // border cells together — in-window pairs must still merge, far
        // pairs must still stay apart.
        assertSceneAgrees("clamp edges", List.of(
                // both far beyond +33,554,432 x-cells: folds onto the border cell, merges
                new SpawnSpec(new Position(40_000_000.1, 5.0, 0.0),
                        ItemStack.of(BuiltinItems.DIRT, 3), 0, 0, false),
                new SpawnSpec(new Position(40_000_000.8, 5.0, 0.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false),
                // straddling the packing limit itself: cells 33,554,431 | 33,554,432 -> folds, merges
                new SpawnSpec(new Position(33_554_431.9, 15.0, 0.0),
                        ItemStack.of(BuiltinItems.STICK, 3), 0, 0, false),
                new SpawnSpec(new Position(33_554_432.6, 15.0, 0.0),
                        ItemStack.of(BuiltinItems.STICK, 5), 0, 0, false),
                // the far pair: opposite sides, no merge (the range check rejects)
                new SpawnSpec(new Position(40_000_000.0, 25.0, 0.0),
                        ItemStack.of(BuiltinItems.STICK, 7), 0, 0, false),
                new SpawnSpec(new Position(-40_000_000.0, 25.0, 0.0),
                        ItemStack.of(BuiltinItems.STICK, 9), 0, 0, false),
                // both far beyond -33,554,432 x-cells: folds onto the other border, merges
                new SpawnSpec(new Position(-40_000_000.1, 5.0, 40.0),
                        ItemStack.of(BuiltinItems.DIRT, 3), 0, 0, false),
                new SpawnSpec(new Position(-40_000_000.8, 5.0, 40.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false),
                // vertical: y-cells -2001 | -2000 both clamp to -1024, merges
                new SpawnSpec(new Position(0.0, -2000.3, 10.0),
                        ItemStack.of(BuiltinItems.EGG, 8), 0, 0, false),
                new SpawnSpec(new Position(0.0, -1999.8, 10.0),
                        ItemStack.of(BuiltinItems.EGG, 8), 0, 0, false),
                // deep vertical fold: both below the clamp, merges
                new SpawnSpec(new Position(0.0, -5000.1, 20.0),
                        ItemStack.of(BuiltinItems.DIRT, 3), 0, 0, false),
                new SpawnSpec(new Position(0.0, -5000.7, 20.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false),
                // deep vertical NON-window pair: stays apart
                new SpawnSpec(new Position(0.0, -5000.0, 30.0),
                        ItemStack.of(BuiltinItems.STICK, 1), 0, 0, false),
                new SpawnSpec(new Position(0.0, -5002.0, 30.0),
                        ItemStack.of(BuiltinItems.STICK, 1), 0, 0, false),
                // the positive vertical clamp edge: y-cells 1023 | 1024 -> folds, merges
                new SpawnSpec(new Position(0.0, 1023.9, 60.0),
                        ItemStack.of(BuiltinItems.DIRT, 2), 0, 0, false),
                new SpawnSpec(new Position(0.0, 1024.6, 60.0),
                        ItemStack.of(BuiltinItems.DIRT, 2), 0, 0, false)
        ), 2);
    }

    @Test
    void expiredBodiesAreSkippedButSurviveTheWalk() {
        // The walk skips expired bodies (no merge) but never removes them —
        // removal is the tick iterator's own arm; expired-and-pinned
        // (never despawn) bodies DO participate.
        assertSceneAgrees("expired mix", List.of(
                // expired pair in window: NOT merged, both survive
                new SpawnSpec(new Position(10.0, 64.0, 10.0),
                        ItemStack.of(BuiltinItems.DIRT, 3), 6500, 0, false),
                new SpawnSpec(new Position(10.0, 64.0, 10.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 6500, 0, false),
                // expired exactly at the boundary (age 6000): skipped too
                new SpawnSpec(new Position(20.0, 64.0, 20.0),
                        ItemStack.of(BuiltinItems.DIRT, 3), 6000, 0, false),
                new SpawnSpec(new Position(20.0, 64.0, 20.0),
                        ItemStack.of(BuiltinItems.DIRT, 5), 6000, 0, false),
                // expired-and-pinned pair in window: merges
                new SpawnSpec(new Position(30.0, 64.0, 30.0),
                        ItemStack.of(BuiltinItems.STICK, 4), 6500, 0, true),
                new SpawnSpec(new Position(30.0, 64.0, 30.0),
                        ItemStack.of(BuiltinItems.STICK, 6), 6500, 0, true),
                // one tick short of expiry: merges
                new SpawnSpec(new Position(40.0, 64.0, 40.0),
                        ItemStack.of(BuiltinItems.EGG, 8), 5999, 0, false),
                new SpawnSpec(new Position(40.0, 64.0, 40.0),
                        ItemStack.of(BuiltinItems.EGG, 8), 5999, 0, false)
        ), 2);
    }

    @Test
    void pickupDelayNeverBlocksMerging() {
        // The discipline: freshly thrown bodies (40-tick delay) still merge.
        assertSceneAgrees("pickup delay", List.of(
                new SpawnSpec(new Position(10.5, 64.2, 10.5),
                        ItemStack.of(BuiltinItems.DIRT, 3), 0, 40, false),
                new SpawnSpec(new Position(10.5, 64.2, 10.5),
                        ItemStack.of(BuiltinItems.DIRT, 5), 0, 40, false)
        ), 1);
    }

    @Test
    void theDenseNeighborhoodFallbackBailBoundaryStaysEquivalent() {
        // Population 130 -> bail limit 32: the 30-body cluster (29 visible
        // candidates per keeper) rides the SORTED bucketed path; the
        // 40-body cluster (39 candidates) exceeds the limit and rides the
        // plain full scan. Both clusters carry a mergeable pair so real
        // merges happen on BOTH paths; the 60 spread singles ride the
        // zero-candidate path.
        List<SpawnSpec> specs = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            specs.add(new SpawnSpec(new Position(100.0 + (i % 4) * 0.1, 5.0 + (i / 4) * 0.1, 100.0),
                    ItemStack.of(BuiltinItems.BOW, 1).withDamage(1 + i), 0, 0, false));
        }
        specs.add(new SpawnSpec(new Position(100.05, 5.05, 100.0),
                ItemStack.of(BuiltinItems.DIRT, 3), 0, 0, false));
        specs.add(new SpawnSpec(new Position(100.05, 5.05, 100.0),
                ItemStack.of(BuiltinItems.DIRT, 5), 0, 0, false));
        for (int i = 0; i < 38; i++) {
            specs.add(new SpawnSpec(new Position(200.0 + (i % 4) * 0.1, 5.0 + (i / 4) * 0.1, 200.0),
                    ItemStack.of(BuiltinItems.BOW, 1).withDamage(100 + i), 0, 0, false));
        }
        specs.add(new SpawnSpec(new Position(200.05, 5.05, 200.0),
                ItemStack.of(BuiltinItems.STICK, 3), 0, 0, false));
        specs.add(new SpawnSpec(new Position(200.05, 5.05, 200.0),
                ItemStack.of(BuiltinItems.STICK, 5), 0, 0, false));
        for (int i = 0; i < 60; i++) {
            specs.add(new SpawnSpec(new Position(300.0 + (i % 10) * 2.0, 5.0, 300.0 + (i / 10) * 2.0),
                    ItemStack.of(BuiltinItems.STICK, 1), 0, 0, false));
        }
        assertEquals(130, specs.size(), "the scene's own population");
        assertSceneAgrees("bail boundary", specs, 2);
    }

    @Test
    void theDenseMixedTypePileTakesTheFallbackAndStaysEquivalent() {
        // The realistic mob-grinder pile: hundreds of mutually-unmergeable
        // bodies on one spot (every keeper's neighborhood holds far more
        // than a quarter of the population -> the plain-scan fallback),
        // mid-cap eggs and dirt merging in the middle of it, and a spread
        // ring riding the sorted path at the same time.
        List<SpawnSpec> specs = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            specs.add(new SpawnSpec(new Position(50.0 + (i % 15) * 0.03, 5.0 + (i / 15) * 0.03, 50.0),
                    ItemStack.of(BuiltinItems.BOW, 1).withDamage(1 + i), 0, 0, false));
        }
        for (int i = 0; i < 60; i++) {
            specs.add(new SpawnSpec(new Position(50.1 + (i % 10) * 0.04, 5.1 + (i / 10) * 0.04, 50.1),
                    ItemStack.of(BuiltinItems.EGG, 1 + (i % 16)), 0, 0, false));
        }
        for (int i = 0; i < 40; i++) {
            specs.add(new SpawnSpec(new Position(50.2 + (i % 8) * 0.05, 5.2 + (i / 8) * 0.05, 50.2),
                    ItemStack.of(BuiltinItems.DIRT, 1 + (i % 64)), 0, 0, false));
        }
        for (int i = 0; i < 40; i++) {
            specs.add(new SpawnSpec(new Position(0.0 + (i % 8) * 2.0, 5.0, 0.0 + (i / 8) * 2.0),
                    ItemStack.of(BuiltinItems.STICK, 1), 0, 0, false));
        }
        assertEquals(440, specs.size(), "the pile scene's own population");
        assertSceneAgrees("dense pile", specs, 2);
    }

    @Test
    void cascadingPartialRemaindersAcrossWalksStayEquivalent() {
        // The chain shape: a first keeper that fills and leaves a partial
        // remainder feeding later keepers, a mid-cap cluster draining
        // one-by-one, and multiple walk rounds over the leftovers.
        List<SpawnSpec> specs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int count = i == 0 ? 60 : 30;
            specs.add(new SpawnSpec(new Position(10.0, 64.0, 10.0),
                    ItemStack.of(BuiltinItems.DIRT, count), 0, 0, false));
        }
        for (int i = 0; i < 3; i++) {
            specs.add(new SpawnSpec(new Position(30.0, 64.0, 30.0),
                    ItemStack.of(BuiltinItems.ENDER_PEARL, 15), 0, 0, false));
        }
        assertSceneAgrees("cascade", specs, 3);
    }

    @Test
    void anEmptyWorldAndSingletonsStayEquivalent() {
        assertSceneAgrees("empty", List.of(), 2);
        assertSceneAgrees("singleton", List.of(new SpawnSpec(
                new Position(1.5, 64.0, 1.5),
                ItemStack.of(BuiltinItems.DIRT, 1), 0, 0, false)), 3);
        // two bodies, one window pair, ids in list order
        assertSceneAgrees("pair", List.of(
                new SpawnSpec(new Position(1.5, 64.0, 1.5),
                        ItemStack.of(BuiltinItems.DIRT, 1), 0, 0, false),
                new SpawnSpec(new Position(1.5, 64.0, 1.5),
                        ItemStack.of(BuiltinItems.DIRT, 1), 0, 0, false)), 3);
    }

    @Test
    void theLawItselfStillHoldsAtTheEdges() {
        // A few law-level sanity pins (the equivalence above is the main
        // event; these keep a reader of a failed run oriented): the
        // inclusive boundary merges, the 0.751 pair does not.
        ItemEntityManager manager = new ItemEntityManager(OPEN_AIR, new Random(1), 1);
        manager.spawnRestored(new Position(10.0, 64.0, 10.0),
                ItemStack.of(BuiltinItems.DIRT, 3));
        manager.spawnRestored(new Position(10.75, 64.0, 10.0),
                ItemStack.of(BuiltinItems.DIRT, 5));
        manager.spawnRestored(new Position(20.0, 64.0, 20.0),
                ItemStack.of(BuiltinItems.STICK, 7));
        manager.spawnRestored(new Position(20.751, 64.0, 20.0),
                ItemStack.of(BuiltinItems.STICK, 9));
        manager.mergeNearbyItems();
        assertEquals(3, manager.size(), "the 0.75 pair merged; the 0.751 pair did not");
        assertNotEquals(List.of(), manager.all(), "sanity: the population is readable");
    }
}
