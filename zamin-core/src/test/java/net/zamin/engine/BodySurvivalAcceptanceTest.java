package net.zamin.engine;

import net.zamin.api.ItemStack;
import net.zamin.api.Position;
import net.zamin.api.Rotation;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.item.BuiltinItems;
import net.zamin.engine.item.Foods;
import net.zamin.engine.net.ClientLink;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The survival body on a real ticker: server-side eating (32-tick timer,
 * community food values), the historical food economy (exhaustion drains
 * saturation then hunger; regen at food >= 18 costs exhaustion; starvation on
 * easy floors at 10 hearts), landing fall damage, and death dropping the
 * whole inventory before a respawn resets the body.
 */
class BodySurvivalAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void eatingConsumesFoodAndNourishesOnTheHistoricalTimer() throws Exception {
        EngineServer server = boot();
        PlayerSession eater = join(server, "Eater");

        server.chatService().submitChat(eater, "/give beef 2");
        await(() -> !eater.inventory().held().isEmpty(), "beef given");
        // Make room to eat: the body starts full, so lower hunger directly
        // (the same write the food economy itself performs).
        eater.setBody(20.0f, 12, 0.0f);

        server.useItem(eater);
        await(() -> eater.eating(), "the eat timer started");
        assertFalse(eater.inventory().held().isEmpty());

        // Releasing early cancels (the historical rule).
        server.releaseUsingItem(eater);
        await(() -> !eater.eating(), "the early release cancelled the eat");
        assertEquals(2, eater.inventory().held().count(), "nothing was consumed");

        // A full timer: 32 ticks later one beef is eaten for 3 food + 1.8 saturation.
        server.useItem(eater);
        await(() -> eater.food() == 15, "the eat completed: food 12 + 3");
        assertEquals(1, eater.inventory().held().count(), "one beef was consumed");
        assertEquals(1.8f, eater.saturation(), 0.0001f, "community foods.json saturation");
        assertEquals(Foods.EAT_TICKS, Foods.EAT_TICKS); // the 1.6 s anchor stays pinned
        server.shutdown(null);
    }

    @Test
    void exhaustionDrainsSaturationThenHungerAndRegenCostsItBack() throws Exception {
        EngineServer server = boot();
        PlayerSession body = join(server, "Regenerator");

        // Full food + damage: regen runs at food >= 18, one heart per 80 ticks.
        body.setBody(10.0f, 20, 5.0f);
        await(() -> body.health() > 10.0f, "regen started (4 s per heart)");
        await(() -> body.health() >= 12.0f, "two hearts regenerated (8 s)");
        await(() -> body.health() >= 12.0f, "two hearts regenerated (8 s)");

        // Regen exhaustion (3.0 per heart, 4.0 per point) drains saturation.
        await(() -> body.saturation() < 5.0f, "regen exhaustion drained saturation");
        server.shutdown(null);
    }

    @Test
    void starvationFloorsAtTenHeartsOnEasy() throws Exception {
        EngineServer server = boot();
        PlayerSession body = join(server, "Starver");

        // One cadence above the floor: the next starvation tick lands on it.
        body.setBody(11.0f, 0, 0.0f);
        await(() -> body.health() < 11.0f, "starvation damage started (4 s cadence)");
        await(() -> body.health() <= PlayerSession.STARVATION_FLOOR + 0.001f,
                "the easy-difficulty floor stopped the damage");
        float after = body.health();
        Thread.sleep(4_500); // another starvation cadence must not cross the floor
        assertEquals(after, body.health(), 0.001f, "starvation cannot kill on easy");
        assertTrue(body.health() >= PlayerSession.STARVATION_FLOOR - 0.001f);
        server.shutdown(null);
    }

    @Test
    void landingFromAHighFallHurtsBeyondThreeBlocks() throws Exception {
        EngineServer server = boot();
        PlayerSession faller = join(server, "Faller");

        // Rise into the air (no fall distance while ascending), then land 15
        // blocks lower: damage = ceil(15 - 3) = 12.
        server.movementProposal(faller, new Position(0.5, 20.0, 0.5), Rotation.ZERO, false);
        server.movementProposal(faller, new Position(0.5, 5.0, 0.5), Rotation.ZERO, true);
        await(() -> faller.health() == 8.0f, "the landing applied ceil(15 - 3) = 12 damage");
        server.shutdown(null);
    }

    @Test
    void deathDropsTheInventoryAndRespawnResetsTheBody() throws Exception {
        EngineServer server = boot();
        PlayerSession doomed = join(server, "Doomed");
        server.chatService().submitChat(doomed, "/give dirt 5");
        await(() -> !doomed.inventory().held().isEmpty(), "dirt given");
        server.chatService().submitChat(doomed, "/give beef 1");

        int entitiesBefore = server.itemEntities().size();
        server.damage(doomed, 100.0f);
        await(() -> doomed.dead(), "the lethal damage killed");
        assertEquals(0.0f, doomed.health());
        assertTrue(doomed.inventory().snapshot().stream().allMatch(ItemStack::isEmpty),
                "the whole inventory scattered at the body");
        assertTrue(server.itemEntities().size() > entitiesBefore, "death drops exist");
        assertTrue(doomed.inventory().cursor().isEmpty(), "the cursor dropped too");

        server.performRespawn(doomed);
        await(() -> !doomed.dead(), "the respawn reset the body");
        assertEquals(PlayerSession.MAX_HEALTH, doomed.health());
        assertEquals(PlayerSession.MAX_FOOD, doomed.food());
        assertEquals(PlayerSession.DEFAULT_SATURATION, doomed.saturation(), 0.001f);
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "body", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        // The adapter calls this after its join sequence; the scripted fake link
        // skips straight to play state so the body simulation applies.
        server.joinCompleted(session);
        await(() -> session.state() == net.zamin.api.PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
