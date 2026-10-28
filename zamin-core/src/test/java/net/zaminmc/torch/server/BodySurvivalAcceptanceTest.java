package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.Foods;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
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

        server.chatService().submitChat(eater, "/give " + eater.name() + " beef 2");
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
    void theGoldenAppleEatsAtFullHungerButBreadRefuses() throws Exception {
        EngineServer server = boot();
        try {
            // FoodItem.startUsing line 59 consults canEat(this.alwaysEdible)
            // — the golden apple's own setAlwaysEdible (Item.java line 604,
            // the food family's single always-edible registration): a
            // SURVIVAL body at FULL hunger still begins the eat. The gate
            // the pre-audit engine paid refused everything at the cap.
            PlayerSession saint = join(server, "Saint");
            server.chatService().submitChat(saint, "/give " + saint.name() + " golden_apple 1");
            await(() -> !saint.inventory().held().isEmpty(), "the golden apple given");
            saint.setBody(20.0f, 20, 0.0f); // FULL hunger, zero saturation
            server.useItem(saint);
            await(() -> saint.eating(), "the always-edible eat begins at the cap");
            await(() -> !saint.eating(), "the 32-tick eat completes");
            assertEquals(20, saint.food(), "the nutrition clamps at the cap (add's own min)");
            assertEquals(9.6f, saint.saturation(), 0.0001f,
                    "the fold still pays: 4 x 1.2 x 2 = 9.6 restored under the cap");
            var potions = server.potions();
            await(() -> potions.hasEffect(saint,
                            net.zaminmc.torch.server.potion.StatusEffects.ABSORPTION),
                    "the effects pay at the full-hunger completion");
            assertTrue(saint.inventory().held().isEmpty(), "the apple consumed");

            // The plain food at the cap still refuses (needsFood alone —
            // PlayerEntity.canEat line 1488's first arm).
            server.chatService().submitChat(saint, "/give " + saint.name() + " bread 1");
            await(() -> !saint.inventory().held().isEmpty(), "the bread given");
            server.useItem(saint);
            Thread.sleep(150); // the submit lands within three ticks; no eat begins
            assertFalse(saint.eating(), "a plain food never begins at the cap");
            assertEquals(1, saint.inventory().held().count(), "nothing was consumed");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theGoldenAppleInstallsItsEffectSet() throws Exception {
        EngineServer server = boot();
        try {
            // The plain apple (metadata 0): Absorption I for 2400 ticks.
            PlayerSession eater = join(server, "Vip");
            server.chatService().submitChat(eater, "/give " + eater.name() + " golden_apple 1");
            await(() -> !eater.inventory().held().isEmpty(), "the apple given");
            eater.setBody(20.0f, 12, 0.0f);
            server.useItem(eater);
            await(() -> eater.food() == 16, "the apple eaten: food 12 + 4");
            var potions = server.potions();
            await(() -> potions.hasEffect(eater, net.zaminmc.torch.server.potion.StatusEffects.ABSORPTION),
                    "the plain apple installed the absorption");
            var absorption = potions.get(eater, net.zaminmc.torch.server.potion.StatusEffects.ABSORPTION);
            assertTrue(absorption.duration() <= 2400 && absorption.duration() >= 2380,
                    "Absorption I 2400 (2 min): " + absorption.duration());
            assertEquals(0, absorption.amplifier());
            // The plain apple's FoodItem pair (GoldenAppleItem's else-branch
            // reaches super.addEatEffects — the reference's own
            // setStatusEffect pair): Regeneration II for 5 s, always.
            var plainRegen = potions.get(eater,
                    net.zaminmc.torch.server.potion.StatusEffects.REGENERATION);
            assertTrue(plainRegen != null,
                    "the plain apple carries the FoodItem pair's regen");
            assertTrue(plainRegen.duration() <= 100 && plainRegen.duration() >= 80,
                    "Regeneration II 100 (5 s): " + plainRegen.duration());
            assertEquals(1, plainRegen.amplifier(), "amplifier II (1)");
            server.ticker().submit(() -> potions.clear(eater));

            // The notch pair (metadata 1): the full set — Absorption I 2400,
            // Regeneration V 600, Resistance I 6000, Fire Resistance I 6000.
            server.chatService().submitChat(eater, "/give " + eater.name() + " golden_apple 1 1");
            await(() -> !eater.inventory().held().isEmpty()
                            && eater.inventory().held().damage() == 1,
                    "the notch apple given");
            eater.setBody(20.0f, 12, 0.0f);
            server.useItem(eater);
            await(() -> eater.food() == 16, "the notch apple eaten");
            await(() -> potions.hasEffect(eater, net.zaminmc.torch.server.potion.StatusEffects.ABSORPTION)
                            && potions.hasEffect(eater, net.zaminmc.torch.server.potion.StatusEffects.REGENERATION)
                            && potions.hasEffect(eater, net.zaminmc.torch.server.potion.StatusEffects.RESISTANCE)
                            && potions.hasEffect(eater, net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE),
                    "the notch apple installed the full set");
            var regen = potions.get(eater, net.zaminmc.torch.server.potion.StatusEffects.REGENERATION);
            assertTrue(regen.duration() <= 600 && regen.duration() >= 580,
                    "Regeneration V 600 (30 s): " + regen.duration());
            assertEquals(4, regen.amplifier(), "amplifier V (4)");
            assertTrue(potions.get(eater, net.zaminmc.torch.server.potion.StatusEffects.RESISTANCE)
                    .duration() <= 6000, "Resistance I 6000 (5 min)");
            assertTrue(potions.get(eater, net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE)
                    .duration() <= 6000, "Fire Resistance I 6000 (5 min)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theSpiderEyeAlwaysPoisonsItsEater() throws Exception {
        EngineServer server = boot();
        try {
            // The spider eye's FoodItem pair (Item.java line 664): Poison I
            // for 5 s at chance 1.0 — the certain roll.
            PlayerSession diner = join(server, "Diner");
            server.chatService().submitChat(diner, "/give " + diner.name() + " spider_eye 1");
            await(() -> !diner.inventory().held().isEmpty(), "the eye given");
            diner.setBody(20.0f, 12, 0.0f);
            server.useItem(diner);
            await(() -> diner.food() == 14, "the eye eaten: food 12 + 2");
            var potions = server.potions();
            await(() -> potions.hasEffect(diner, net.zaminmc.torch.server.potion.StatusEffects.POISON),
                    "the spider eye poisoned its eater");
            var poison = potions.get(diner, net.zaminmc.torch.server.potion.StatusEffects.POISON);
            assertTrue(poison.duration() <= 100 && poison.duration() >= 80,
                    "Poison I 100 (5 s): " + poison.duration());
            assertEquals(0, poison.amplifier());
        } finally {
            server.shutdown(null);
        }
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
        EngineAwaits.awaitTicks(server.world(), 90, "another starvation cadence must not cross the floor");
        assertEquals(after, body.health(), 0.001f, "starvation cannot kill on easy");
        assertTrue(body.health() >= PlayerSession.STARVATION_FLOOR - 0.001f);
        server.shutdown(null);
    }

    @Test
    void landingFromAHighFallHurtsBeyondThreeBlocks() throws Exception {
        EngineServer server = boot();
        PlayerSession faller = join(server, "Faller");

        // Rise into the air (the engine's teleport path), then land 15 blocks
        // lower in per-tick fall steps (the client sends one position packet
        // per tick; no honest step exceeds the terminal velocity):
        // accumulated distance 15 -> damage = ceil(15 - 3) = 12.
        server.teleportPlayer(faller, new Position(0.5, 20.0, 0.5));
        await(() -> faller.position().y() == 20.0, "the teleport landed");
        server.movementProposal(faller, new Position(0.5, 16.0, 0.5), Rotation.ZERO, false);
        server.movementProposal(faller, new Position(0.5, 12.0, 0.5), Rotation.ZERO, false);
        server.movementProposal(faller, new Position(0.5, 8.0, 0.5), Rotation.ZERO, false);
        server.movementProposal(faller, new Position(0.5, 5.0, 0.5), Rotation.ZERO, true);
        await(() -> faller.health() == 8.0f, "the landing applied ceil(15 - 3) = 12 damage");
        server.shutdown(null);
    }

    @Test
    void deathDropsTheInventoryAndRespawnResetsTheBody() throws Exception {
        EngineServer server = boot();
        PlayerSession doomed = join(server, "Doomed");
        server.chatService().submitChat(doomed, "/give " + doomed.name() + " dirt 5");
        await(() -> !doomed.inventory().held().isEmpty(), "dirt given");
        server.chatService().submitChat(doomed, "/give " + doomed.name() + " beef 1");

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

    @Test
    void thePufferfishPoisonsItsEaterWithTheFullTriple() throws Exception {
        EngineServer server = boot();
        try {
            // The pufferfish's eat triple (FishItem.addEatEffects lines 38-47):
            // Poison IV 1200 (60 s), Hunger III 300 (15 s), Nausea II 300
            // (15 s) — all certain, no chance roll. One food, 0.2 saturation
            // (the fold: 1 x 0.1 x 2).
            PlayerSession swimmer = join(server, "Swimmer");
            server.chatService().submitChat(swimmer, "/give " + swimmer.name() + " fish 1 3");
            await(() -> !swimmer.inventory().held().isEmpty(), "the pufferfish given");
            assertEquals(3, swimmer.inventory().held().damage(), "metadata 3 is the pufferfish");
            swimmer.setBody(20.0f, 12, 0.0f);
            server.useItem(swimmer);
            await(() -> swimmer.food() == 13, "the pufferfish eaten: food 12 + 1");
            var potions = server.potions();
            await(() -> potions.hasEffect(swimmer, net.zaminmc.torch.server.potion.StatusEffects.POISON)
                            && potions.hasEffect(swimmer, net.zaminmc.torch.server.potion.StatusEffects.HUNGER)
                            && potions.hasEffect(swimmer, net.zaminmc.torch.server.potion.StatusEffects.NAUSEA),
                    "the pufferfish installed the full triple");
            var poison = potions.get(swimmer, net.zaminmc.torch.server.potion.StatusEffects.POISON);
            assertTrue(poison.duration() <= 1200 && poison.duration() >= 1180,
                    "Poison IV 1200 (60 s): " + poison.duration());
            assertEquals(3, poison.amplifier(), "amplifier IV (3)");
            var hunger = potions.get(swimmer, net.zaminmc.torch.server.potion.StatusEffects.HUNGER);
            assertTrue(hunger.duration() <= 300 && hunger.duration() >= 280,
                    "Hunger III 300 (15 s): " + hunger.duration());
            assertEquals(2, hunger.amplifier(), "amplifier III (2)");
            var nausea = potions.get(swimmer, net.zaminmc.torch.server.potion.StatusEffects.NAUSEA);
            assertTrue(nausea.duration() <= 300 && nausea.duration() >= 280,
                    "Nausea II 300 (15 s): " + nausea.duration());
            assertEquals(1, nausea.amplifier(), "amplifier II (1)");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFishFamilyNourishesByItsVariant() throws Exception {
        EngineServer server = boot();
        try {
            // The reference's own Type table (FishItem.Type lines 64-97):
            // the raw cod 2 food, the raw salmon 2, the clownfish 1; the
            // cooked cod 5, the cooked salmon 6. The saturation seats were
            // re-pinned by the seventh pass against HungerManager.add lines
            // 20-23 — the reference's Type columns carry the raw MODIFIERS
            // (0.1 across the board, cooked 0.6 / 0.8) and the hunger
            // manager folds food x modifier x 2 at every bite, so the
            // restored saturation is 0.4 raw, 6.0 cooked cod, 9.6 cooked
            // salmon (the pre-audit seats had pinned the modifiers
            // themselves as the restoration).
            assertEquals(2, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.FISH.identifier(), 0).foodPoints());
            assertEquals(0.4f, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.FISH.identifier(), 0).saturation(),
                    0.0001f, "the fold: 2 x 0.1 x 2 (HungerManager.add line 22)");
            assertEquals(2, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.FISH.identifier(), 1).foodPoints());
            assertEquals(1, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.FISH.identifier(), 2).foodPoints());
            assertEquals(0.2f, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.FISH.identifier(), 3).saturation(),
                    0.0001f, "the pufferfish's fold: 1 x 0.1 x 2");
            assertEquals(5, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 0).foodPoints());
            assertEquals(6.0f, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 0).saturation(),
                    0.0001f, "the fold: 5 x 0.6 x 2");
            assertEquals(6, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 1).foodPoints());
            assertEquals(9.6f, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 1).saturation(),
                    0.0001f, "the fold: 6 x 0.8 x 2");
            // The reference's own quirks: a cooked clownfish (non-cookable)
            // answers the RAW values, and an unknown metadata answers COD.
            assertEquals(1, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 2).foodPoints(),
                    "the cooked clownfish eats raw (the canBeCooked gate)");
            assertEquals(2, net.zaminmc.torch.server.item.FishItems
                            .nutritionOf(BuiltinItems.FISH.identifier(), 7).foodPoints(),
                    "the unknown metadata answers cod (the byId null guard)");

            // The live eat: a cooked salmon restores 6 food.
            PlayerSession diner = join(server, "Angler");
            server.chatService().submitChat(diner, "/give " + diner.name() + " cooked_fish 1 1");
            await(() -> !diner.inventory().held().isEmpty(), "the cooked salmon given");
            assertEquals(1, diner.inventory().held().damage());
            diner.setBody(20.0f, 12, 0.0f);
            server.useItem(diner);
            await(() -> diner.food() == 18, "the cooked salmon eaten: food 12 + 6");
            assertEquals(9.6f, diner.saturation(), 0.0001f,
                    "the fold's live arm: 6 x 0.8 x 2 restored (HungerManager.add)");

            // The reference's own quirk: a cooked fish WITH pufferfish
            // metadata eats (and poisons) like a raw pufferfish —
            // Type.byItem reads the metadata of any FishItem.
            server.chatService().submitChat(diner, "/give " + diner.name() + " cooked_fish 1 3");
            await(() -> !diner.inventory().held().isEmpty()
                            && diner.inventory().held().damage() == 3,
                    "the cooked pufferfish given");
            diner.setBody(20.0f, 12, 0.0f);
            server.useItem(diner);
            await(() -> diner.food() == 13, "the cooked pufferfish eaten raw: food 12 + 1");
            var potions = server.potions();
            await(() -> potions.hasEffect(diner, net.zaminmc.torch.server.potion.StatusEffects.POISON),
                    "the cooked pufferfish still poisons its eater");
        } finally {
            server.shutdown(null);
        }
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
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, name + " playing");
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
