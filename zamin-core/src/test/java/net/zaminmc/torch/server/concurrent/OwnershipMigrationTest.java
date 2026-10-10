package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ownership migration protocol over its fault-injection list (the
 * design's §9 + the rollout's "migration fault-injection green"
 * prerequisite): the safe point refuses a mid-task start, admission holds
 * through the transfer and releases at publish, queued work drains at the
 * old generation before the state moves, the publish bumps the generation
 * so old-generation results are diagnosably stale, a failed state move
 * aborts with no half-migrated authority, and the protocol runs once.
 */
class OwnershipMigrationTest {

    @Test
    void theHappyHandoffWalksEveryPhase() {
        OwnershipDomain domain = OwnershipDomain.create("region:migrating");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        assertEquals(OwnershipMigration.Phase.NOT_STARTED, migration.phase());

        migration.begin();
        assertEquals(OwnershipMigration.Phase.DRAINING, migration.phase());
        assertTrue(scheduler.isAdmissionHeld(domain), "admission holds through the transfer");

        long before = domain.generation();
        migration.publish();
        assertEquals(OwnershipMigration.Phase.PUBLISHED, migration.phase());
        assertEquals(before + 1, domain.generation(), "the publish bumps the generation");
        assertFalse(scheduler.isAdmissionHeld(domain), "admission releases at publish");
    }

    @Test
    void aMigrationNeverInterruptsAnActiveTask() {
        OwnershipDomain domain = OwnershipDomain.create("region:busy");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        domain.tryBeginTick(); // a task holds the single-writer slot
        try {
            assertThrows(IllegalStateException.class, migration::begin,
                    "the safe point check: a migration starts between tasks, never mid-task");
            assertFalse(scheduler.isAdmissionHeld(domain),
                    "the refused migration never left a hold behind");
        } finally {
            domain.endTick();
        }
    }

    @Test
    void admissionHoldsRefuseNewSubmissionsLoudly() {
        OwnershipDomain domain = OwnershipDomain.create("region:held");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();
        assertThrows(IllegalStateException.class,
                () -> scheduler.submit(domain, () -> { }),
                "the hold refuses new authoritative work (§9 step 2, loud backpressure)");
        migration.publish();
        scheduler.submit(domain, () -> { });
        assertEquals(1, scheduler.pendingFor(domain), "admission resumes after publish");
    }

    @Test
    void queuedWorkDrainsAtTheOldGenerationBeforeTheStateMoves() {
        OwnershipDomain domain = OwnershipDomain.create("region:drain");
        SimulationScheduler scheduler = new SimulationScheduler();
        List<String> order = new ArrayList<>();
        scheduler.submit(domain, () -> order.add("queued-1"));
        scheduler.submit(domain, () -> order.add("queued-2"));

        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();
        domain.enter();
        try {
            int drained = migration.drainQueued(1);
            assertEquals(2, drained, "the already-queued work finishes at the old generation");
            assertEquals(List.of("queued-1", "queued-2"), order, "FIFO held through the drain");
            assertEquals(0, scheduler.pendingFor(domain), "nothing left behind for the new owner");
        } finally {
            domain.exit();
        }
    }

    @Test
    void drainQueuedDemandsTheOldOwnersContext() {
        OwnershipDomain domain = OwnershipDomain.create("region:context");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();
        assertThrows(OwnershipViolationException.class, () -> migration.drainQueued(1),
                "the drain is an authoritative apply: the old owner's context is demanded");
        migration.abort();
    }

    @Test
    void theStateTransferRunsAndThePublishMakesOldResultsStale() {
        OwnershipDomain domain = OwnershipDomain.create("region:transfer");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();

        long oldGeneration = domain.generation();
        AtomicBoolean stateMoved = new AtomicBoolean(false);

        migration.transfer(() -> stateMoved.set(true));
        assertEquals(OwnershipMigration.Phase.DRAINING, migration.phase(),
                "after the completed transfer the protocol stays in its draining window "
                        + "(only publish or abort closes it)");
        assertTrue(stateMoved.get(), "the protocol ran the state move");

        migration.publish();

        assertNotEquals(oldGeneration, domain.generation(),
                "the publish bumped the generation: every old-generation reference "
                        + "(compute tickets, router envelopes) is now diagnosably stale (§9 step 6)");
    }

    @Test
    void aFailedStateMoveAbortsAndLeavesNoHalfMigratedAuthority() {
        OwnershipDomain domain = OwnershipDomain.create("region:poisoned-move");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.begin();
        long before = domain.generation();
        RuntimeException boom = new RuntimeException("state move exploded");
        assertThrows(RuntimeException.class, () -> migration.transfer(() -> { throw boom; }),
                "the failure surfaces");
        assertEquals(OwnershipMigration.Phase.ABORTED, migration.phase(),
                "the protocol aborted");
        assertNotEquals(before, domain.generation(),
                "the abort bumps the generation: no half-migrated observer keeps authority");
        assertFalse(scheduler.isAdmissionHeld(domain), "admission released on abort");
        // The protocol is closed: the same instance cannot run again.
        assertThrows(IllegalStateException.class, migration::begin);
    }

    @Test
    void abortWithoutBeginIsHarmlessAndTheProtocolRunsOnce() {
        OwnershipDomain domain = OwnershipDomain.create("region:once");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        migration.abort(); // never begun: nothing to unwind, no generation bump
        assertEquals(0, domain.generation());
        assertEquals(OwnershipMigration.Phase.ABORTED, migration.phase());

        migration.begin();
        migration.publish();
        assertThrows(IllegalStateException.class, migration::begin,
                "a migration instance runs once");
    }

    @Test
    void stepsRefuseWhenThePhaseMachineIsOutOfOrder() {
        OwnershipDomain domain = OwnershipDomain.create("region:order");
        SimulationScheduler scheduler = new SimulationScheduler();
        OwnershipMigration migration = new OwnershipMigration(domain, scheduler);
        assertThrows(IllegalStateException.class, () -> migration.transfer(() -> { }),
                "transfer before begin refuses");
        assertThrows(IllegalStateException.class, migration::publish,
                "publish before begin refuses");
        migration.begin();
        migration.publish();
        assertThrows(IllegalStateException.class, migration::publish,
                "the publish is single-shot");
        assertThrows(IllegalStateException.class, () -> migration.transfer(() -> { }),
                "no step runs after the protocol closed");
    }
}
