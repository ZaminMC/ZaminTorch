package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.World;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item entity lifecycle on a deterministic world: spawn, physics settle,
 * pickup delay, validated pickup (§433) with partial pickup, despawn (§450).
 */
class ItemEntityManagerTest {

    /** Solid everywhere below y=4 (like the flat world's surface at ground level 4). */
    private static final ItemEntity.Ground GROUND = (x, y, z) -> y < 4.0;

    private PlayerSession playingPlayer(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    /** Minimal world stub: sessions need a World reference only. */
    private net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(net.zaminmc.torch.block.BlockPosition p, net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(net.zaminmc.torch.block.ChunkPosition p) { return true; }
        };
    }

    private static final class Events implements ItemEntityManager.Listener {
        int spawned, moved, collected, stackChanged, removed;
        final List<String> removeReasons = new ArrayList<>();

        @Override public void onItemSpawned(ItemEntity entity) { spawned++; }
        @Override public void onItemMoved(ItemEntity entity) { moved++; }
        @Override public void onItemCollected(ItemEntity entity, PlayerSession c, int n) { collected++; }
        @Override public void onItemStackChanged(ItemEntity entity) { stackChanged++; }
        @Override public void onItemRemoved(ItemEntity entity, String reason) {
            removed++;
            removeReasons.add(reason);
        }
    }

    @Test
    void spawnPublishesAndEntityFallsToGround() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(events);

        ItemEntity entity = manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIRT, 1), ItemEntity.PICKUP_DELAY_DROP_TICKS);
        assertEquals(1, events.spawned);
        assertEquals(1, manager.size());

        // First ticks: gravity pulls down; pickup delay counts.
        assertFalse(entity.pickupAllowed());
        for (int i = 0; i < 12; i++) {
            manager.tick(List.of());
        }
        assertTrue(entity.pickupAllowed());
        // Settled on the surface: bottom on y=4 -> center 4.125.
        assertEquals(4.125, entity.position().y(), 0.01);
        assertTrue(entity.onGround());
        assertTrue(events.moved > 0);
    }

    @Test
    void pickupTransfersToInventoryAndRemovesEntity() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(events);

        PlayerSession player = playingPlayer("Collector", new Position(2.5, 4, 2.5));
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIRT, 3), 0);

        manager.tick(List.of(player));
        assertEquals(0, manager.size());
        assertEquals(1, events.collected);
        assertEquals(3, player.inventory().held().count());
    }

    @Test
    void pickupDelayBlocksEarlyCollection() {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        PlayerSession player = playingPlayer("Patient", new Position(2.5, 4, 2.5));
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIRT, 1), ItemEntity.PICKUP_DELAY_DROP_TICKS);

        manager.tick(List.of(player));
        manager.tick(List.of(player));
        assertEquals(1, manager.size()); // still delayed

        for (int i = 0; i < 10; i++) {
            manager.tick(List.of(player));
        }
        assertEquals(0, manager.size()); // delay over, collected
    }

    @Test
    void partialPickupKeepsRemainderInTheWorld() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(events);

        PlayerSession player = playingPlayer("Hoarder", new Position(2.5, 4, 2.5));
        // Partial dirt stack plus every other slot full: no empty slot anywhere.
        player.inventory().pickUp(ItemStack.of(BuiltinItems.DIRT, 60));
        for (int i = 0; i < 35; i++) {
            player.inventory().pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        }
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIRT, 10), 0);

        manager.tick(List.of(player));
        assertEquals(1, manager.size()); // remainder stays
        assertEquals(64, player.inventory().held().count());
        assertEquals(6, manager.all().get(0).stack().count());
        assertEquals(1, events.stackChanged);
        assertEquals(0, events.collected); // not fully collected
    }

    @Test
    void fullInventoryLeavesTheItemUntouched() {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        PlayerSession player = playingPlayer("Full", new Position(2.5, 4, 2.5));
        for (int i = 0; i < 36; i++) {
            player.inventory().pickUp(ItemStack.of(BuiltinItems.OAK_LOG, 64));
        }
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIRT, 1), 0);

        manager.tick(List.of(player));
        assertEquals(1, manager.size());
        assertTrue(player.inventory().held().type().equals(BuiltinItems.OAK_LOG));
    }

    @Test
    void itemsDespawnAfterHistoricalAge() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(events);
        manager.spawnDropAtBlock(new Position(2, 4, 2),
                ItemStack.of(BuiltinItems.DIRT, 1), 0);

        for (int i = 0; i < ItemEntity.DESPAWN_TICKS; i++) {
            manager.tick(List.of());
        }
        assertEquals(1, manager.size());
        manager.tick(List.of());
        assertEquals(0, manager.size());
        assertEquals(1, events.removed);
        assertEquals("despawned", events.removeReasons.get(0));
    }

    @Test
    void itemsThatFallIntoTheVoidAreRemovedSilently() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(
                (x, y, z) -> false, // open air everywhere: the void swallows all
                new Random(1), 1);
        manager.addListener(events);

        ItemEntity dropped = manager.spawnDropAtBlock(new Position(2, 0, 2),
                ItemStack.of(BuiltinItems.DIRT, 1), 0);
        assertFalse(dropped.inVoid(), "starts inside the world");

        int ticks = 0;
        while (manager.size() > 0 && ticks < 400) {
            manager.tick(List.of());
            ticks++;
        }
        assertEquals(0, manager.size(), "the stack fell out of the world");
        assertEquals(1, events.removed, "the removal fired exactly once");
        assertEquals(List.of("fell out of world"), events.removeReasons,
                "the void removal is silent (no drop-back) and names the void");
    }

    @Test
    void nearbySameItemsMergeIntoTheOlderEntity() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(events);

        // Same spot: the older entity (lower id) is deterministic keeper.
        ItemEntity older = manager.spawnThrown(new Position(2.5, 4.2, 2.5),
                ItemStack.of(BuiltinItems.DIRT, 3));
        ItemEntity younger = manager.spawnThrown(new Position(2.5, 4.2, 2.5),
                ItemStack.of(BuiltinItems.DIRT, 5));
        assertTrue(younger.entityId() > older.entityId());

        manager.tick(List.of());
        assertEquals(1, manager.size(), "the younger entity was absorbed");
        assertEquals(8, older.stack().count(), "the stacks combined");
        assertTrue(events.removeReasons.contains("merged"));
        assertEquals(0, events.collected);
    }

    @Test
    void differentItemsNeverMerge() {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.spawnThrown(new Position(2.5, 4.2, 2.5), ItemStack.of(BuiltinItems.DIRT, 3));
        manager.spawnThrown(new Position(2.5, 4.2, 2.5), ItemStack.of(BuiltinItems.OAK_LOG, 5));

        manager.tick(List.of());
        assertEquals(2, manager.size(), "dirt and logs stay separate");
    }

    @Test
    void mergeCapsAtTheStackLimitAndKeepsTheRemainder() {
        Events events = new Events();
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.addListener(events);

        ItemEntity older = manager.spawnThrown(new Position(2.5, 4.2, 2.5),
                ItemStack.of(BuiltinItems.DIRT, 60));
        manager.spawnThrown(new Position(2.5, 4.2, 2.5), ItemStack.of(BuiltinItems.DIRT, 10));

        manager.tick(List.of());
        assertEquals(2, manager.size(), "a partial merge keeps the remainder alive");
        assertEquals(64, older.stack().count(), "the keeper filled to the limit");
        ItemEntity remainder = manager.all().stream()
                .filter(e -> e.entityId() != older.entityId())
                .findFirst().orElseThrow();
        assertEquals(6, remainder.stack().count(), "10 - 4 moved");
    }

    @Test
    void distantItemsDoNotMerge() {
        ItemEntityManager manager = new ItemEntityManager(GROUND, new Random(1), 1);
        manager.spawnThrown(new Position(2.5, 4.2, 2.5), ItemStack.of(BuiltinItems.DIRT, 3));
        manager.spawnThrown(new Position(6.5, 4.2, 2.5), ItemStack.of(BuiltinItems.DIRT, 5));

        manager.tick(List.of());
        assertEquals(2, manager.size(), "out of the historical 0.5 search box");
    }
}
