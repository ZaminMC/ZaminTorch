package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The milk family's parity seats (the verifier's eighth pass —
 * MilkBucketItem + CowEntity.interactMob lines 95-106, verified against
 * reference/1.8.8): the drink rides the potion family's 32-tick clock
 * with NO canEat consult of its own (milk begins at ANY hunger — the
 * item's startUsing sets the item-in-use outright), the completion
 * clears every status effect server-side, the LAST unit answers the
 * empty bucket through the hand slot itself (a stacked milk drinks on
 * without the bucket), the creative hand keeps the stack but still
 * clears and pays the stat, and the cow family's milk arm — the empty
 * bucket on an adult, the size-- == 1 idiom, the stacked pair's
 * consume-then-add (or drop at a full inventory), the creative and baby
 * refusals, and the mooshroom's inheritance of the whole arm.
 */
class MilkFamilyParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "milk" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        await(() -> server.state() == net.zaminmc.torch.ServerState.RUNNING,
                "the server up");
        return server;
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        // The stub link never answers the handshake — the scripted
        // client's own promotion (the WolfCollarDye form).
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the joined player reached PLAYING");
        return session;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(description);
    }

    /** The held stack into the selected hotbar slot. */
    private void hold(net.zaminmc.torch.item.ItemStack stack) {
        player.inventory().setSlot(0, stack);
        player.inventory().selectHotbarSlot(0);
    }

    private void holdMilk(int count) {
        hold(net.zaminmc.torch.item.ItemStack.of(BuiltinItems.MILK_BUCKET, count));
    }

    // ------------------------------------------------------------------
    // The drink walk (MilkBucketItem)
    // ------------------------------------------------------------------

    @Test
    void theDrinkBeginsAtAnyHunger() throws Exception {
        boot();
        try {
            player = join("Full");
            // The body FULL (the FoodItem gate would refuse any food here —
            // even the always-edible row is irrelevant): milk's own
            // startUsing carries no canEat consult at all.
            assertEquals(20, player.food(), "the body starts full");
            holdMilk(1);
            server.useItem(player);
            await(() -> player.drinking(), "the milk drink began at full hunger");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCompletionClearsTheEffectsAndAnswersTheBucket() throws Exception {
        boot();
        try {
            player = join("Sick");
            holdMilk(1);
            // Two effects installed ahead of the drink: the clear walks
            // every row (clearStatusEffects — the item's whole purpose).
            server.potions().add(player, new StatusEffectInstance(
                    StatusEffects.SPEED, 600, 0));
            server.potions().add(player, new StatusEffectInstance(
                    StatusEffects.POISON, 600, 0));
            assertTrue(server.potions().hasEffect(player, StatusEffects.SPEED),
                    "the speed installed");
            assertTrue(server.potions().hasEffect(player, StatusEffects.POISON),
                    "the poison installed");

            server.useItem(player);
            await(() -> player.drinking(), "the drink began");
            // The 32-tick completion: every effect gone, the empty bucket
            // in the hand (finishUsingItem's returned-stack write — the
            // bucket takes the emptied hand slot itself).
            await(() -> !player.drinking()
                            && player.inventory().held().type() != null
                            && player.inventory().held().type()
                                    .equals(BuiltinItems.BUCKET),
                    "the drink completed and the bucket replaced the hand");
            assertFalse(server.potions().hasEffect(player, StatusEffects.SPEED),
                    "the speed cleared");
            assertFalse(server.potions().hasEffect(player, StatusEffects.POISON),
                    "the poison cleared");
            assertTrue(player.inventory().held().type().equals(BuiltinItems.BUCKET),
                    "the empty bucket answers the last unit");
            assertEquals(1, player.inventory().held().count());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMilkStacksToOneSoEverySurvivalDrinkAnswersTheBucket() throws Exception {
        boot();
        try {
            player = join("Lone");
            // The registration law (MilkBucketItem's ctor setMaxStackSize(1)):
            // the milk bucket can never ride a stack — the pass-6 lava
            // bucket lesson again — so the size-- remainder fires on EVERY
            // survival drink (a "stacked milk drinks on without the bucket"
            // seat is an impossible shape and was removed).
            assertEquals(1, BuiltinItems.MILK_BUCKET.maxStackSize(),
                    "the milk bucket stacks to one");
            holdMilk(1);
            server.potions().add(player, new StatusEffectInstance(
                    StatusEffects.SPEED, 600, 0));
            server.useItem(player);
            await(() -> player.inventory().held().type() != null
                            && player.inventory().held().type()
                                    .equals(BuiltinItems.BUCKET),
                    "the single-unit drink answered the bucket");
            assertFalse(server.potions().hasEffect(player, StatusEffects.SPEED),
                    "and cleared the effect");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCreativeHandKeepsTheStackAndStillClears() throws Exception {
        boot();
        try {
            player = join("Free");
            player.setGamemode(net.zaminmc.torch.GameMode.CREATIVE);
            holdMilk(1);
            server.potions().add(player, new StatusEffectInstance(
                    StatusEffects.SPEED, 600, 0));
            server.useItem(player);
            await(() -> player.drinking(), "the creative drink began");
            await(() -> !player.drinking(), "the creative drink completed");
            // The creative guard sits ahead of the decrement alone: the
            // stack stands (the milk STAYS — no bucket for the creative
            // hand), but the effects still clear and the stat still
            // lands (both ahead of the return).
            assertTrue(player.inventory().held().type().equals(BuiltinItems.MILK_BUCKET),
                    "the creative hand keeps the milk");
            assertEquals(1, player.inventory().held().count(),
                    "the stack stands undecremented");
            assertEquals(0, player.inventory().countOf(BuiltinItems.BUCKET),
                    "no bucket for the creative hand");
            assertFalse(server.potions().hasEffect(player, StatusEffects.SPEED),
                    "the effects still cleared");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theUseStatLands() throws Exception {
        boot();
        try {
            player = join("Counted");
            holdMilk(1);
            int before = player.stats().get(net.zaminmc.torch.server.stat.EngineStats
                    .useItemOf(BuiltinItems.MILK_BUCKET.identifier()));
            server.useItem(player);
            await(() -> player.stats().get(net.zaminmc.torch.server.stat.EngineStats
                    .useItemOf(BuiltinItems.MILK_BUCKET.identifier())) > before,
                    "the use stat landed");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theEarlyReleaseCancelsTheDrink() throws Exception {
        boot();
        try {
            player = join("Hasty");
            holdMilk(1);
            server.potions().add(player, new StatusEffectInstance(
                    StatusEffects.SPEED, 600, 0));
            server.useItem(player);
            await(() -> player.drinking(), "the drink began");
            server.releaseUsingItem(player);
            await(() -> !player.drinking(), "the early release cancelled");
            // Nothing paid: the stack stands, the effects stand.
            assertTrue(player.inventory().held().type().equals(BuiltinItems.MILK_BUCKET),
                    "nothing was consumed");
            assertTrue(server.potions().hasEffect(player, StatusEffects.SPEED),
                    "the effects stand");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------
    // The cow family's milk arm (CowEntity.interactMob lines 95-106)
    // ------------------------------------------------------------------

    /** An adult cow beside the joined player. */
    private MobEntity cowBeside(MobType kind) {
        Position at = player.position();
        return server.mobs().spawnAt(kind,
                new Position(at.x() + 1.0, at.y(), at.z()));
    }

    private void holdBuckets(int count) {
        hold(net.zaminmc.torch.item.ItemStack.of(BuiltinItems.BUCKET, count));
    }

    @Test
    void theSingleBucketBecomesTheMilkInTheHand() throws Exception {
        boot();
        try {
            player = join("Milker");
            MobEntity cow = cowBeside(MobType.COW);
            holdBuckets(1);
            server.interactEntity(player, cow.entityId());
            await(() -> player.inventory().held().type() != null
                            && player.inventory().held().type()
                                    .equals(BuiltinItems.MILK_BUCKET),
                    "the single bucket became the milk in the hand");
            assertEquals(1, player.inventory().held().count(),
                    "the milk stacks to one");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theStackedPairConsumesOneAndAddsTheMilk() throws Exception {
        boot();
        try {
            player = join("PairMilker");
            MobEntity cow = cowBeside(MobType.COW);
            holdBuckets(3);
            server.interactEntity(player, cow.entityId());
            await(() -> player.inventory().held().count() == 2,
                    "one bucket consumed from the stack");
            assertEquals(BuiltinItems.BUCKET, player.inventory().held().type(),
                    "the hand keeps the buckets");
            assertEquals(1, player.inventory().countOf(BuiltinItems.MILK_BUCKET),
                    "the milk added to the inventory");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFullInventoryDropsTheMilk() throws Exception {
        boot();
        try {
            player = join("Packed");
            MobEntity cow = cowBeside(MobType.COW);
            // Fill every slot (36): the milk can neither join the
            // inventory nor stay in the occupied hand — it drops.
            for (int slot = 0; slot < 36; slot++) {
                player.inventory().setSlot(slot, net.zaminmc.torch.item.ItemStack.of(
                        BuiltinItems.STONE, 64));
            }
            holdBuckets(2);
            server.interactEntity(player, cow.entityId());
            await(() -> player.inventory().held().count() == 1
                            && server.itemEntities().size() >= 1,
                    "the milk dropped at the full inventory");
            assertEquals(0, player.inventory().countOf(BuiltinItems.MILK_BUCKET),
                    "no milk entered the inventory");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCreativeHandIsRefusedOutright() throws Exception {
        boot();
        try {
            player = join("Gifted");
            player.setGamemode(net.zaminmc.torch.GameMode.CREATIVE);
            MobEntity cow = cowBeside(MobType.COW);
            holdBuckets(2);
            server.interactEntity(player, cow.entityId());
            Thread.sleep(150);
            assertEquals(2, player.inventory().held().count(),
                    "the creative hand refused outright (the reference's guard)");
            assertEquals(0, player.inventory().countOf(BuiltinItems.MILK_BUCKET),
                    "no milk at all");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theBabyIsRefused() throws Exception {
        boot();
        try {
            player = join("Calf");
            MobEntity baby = cowBeside(MobType.COW);
            baby.setBreedingAge(-24_000);
            assertTrue(baby.isBaby(), "the calf is a baby");
            holdBuckets(1);
            server.interactEntity(player, baby.entityId());
            Thread.sleep(150);
            assertTrue(player.inventory().held().type().equals(BuiltinItems.BUCKET),
                    "the baby refused the milking");
            assertEquals(1, player.inventory().held().count());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMooshroomInheritsTheMilkArm() throws Exception {
        boot();
        try {
            player = join("Shroomer");
            MobEntity mooshroom = cowBeside(MobType.MOOSHROOM);
            holdBuckets(1);
            server.interactEntity(player, mooshroom.entityId());
            await(() -> player.inventory().held().type() != null
                            && player.inventory().held().type()
                                    .equals(BuiltinItems.MILK_BUCKET),
                    "the mooshroom milked through the cow's inherited arm");
        } finally {
            server.shutdown(null);
        }
    }
}
