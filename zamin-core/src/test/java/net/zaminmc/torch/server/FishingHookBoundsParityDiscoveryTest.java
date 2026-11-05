package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.fishing.Fishing;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-9 — the fishing hook's clip law over the Phase E boundary
 * arms: the reference's {@code FishingBobberEntity} lines 244-265 — the
 * flight segment clipped against every hookable body's OWN box grown
 * 0.3 ({@code Box.clip}), the CLOSEST hit along the segment winning,
 * the census {@code hasCollision()}: LivingEntity's !removed (the mobs,
 * the players, THE ARMOR STANDS), the vehicles, the projectiles — the
 * ITEMS and ORBS excluded (the base Entity's false).
 *
 * <p>The pre-fix consult sampled the segment at 0.1 granularity against
 * CENTER-band stand-ins (the raw type box with a -0.1 feet slack) — the
 * 0.3 growth wholly absent (a bobber passing 0.2 from a mob's side
 * hooks in vanilla, never in the engine), and the stand/vehicle families
 * never hookable at all. Three arms RED pre-fix; the item control green
 * by design (the census's own exclusion).</p>
 */
class FishingHookBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "fishhook" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private boolean hooked(PlayerSession angler) {
        return server.fishing().bobberOf(angler.engineEntityId())
                .map(Fishing.Bobber::hooked).orElse(false);
    }

    private boolean grounded(PlayerSession angler) {
        return server.fishing().bobberOf(angler.engineEntityId())
                .map(Fishing.Bobber::grounded).orElse(false);
    }

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    /**
     * The rig: a stone corridor at y=39 (the floor) with the flight lane
     * cleared to y=45 and the sky-blocking roof at 46 (the seat
     * discipline: the undead burn consult reads sky access — a burning
     * zombie's death would empty the target mid-flight). The corridor
     * stands BEFORE the angler teleports in (the angler never falls
     * through unbuilt air), and the cast rides the FLAT pitch-0 lob:
     * the bobber spawns at the eye (y+1.52) and descends 0.04/tick —
     * the targets placed on the flight's own schedule (tick 3-4 at
     * z+4, y≈41.3; tick 7-8 at z+8.5, y≈40.9).
     */
    private PlayerSession rigAngler(int x, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -1; dz <= 14; dz++) {
                    server.world().setBlock(new BlockPosition(x + dx, 39, z + dz),
                            BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 6; dy++) {
                        server.world().setBlock(new BlockPosition(x + dx, 40 + dy, z + dz),
                                BuiltinBlocks.AIR);
                    }
                    server.world().setBlock(new BlockPosition(x + dx, 46, z + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                        new BlockPosition(x, 39, z)))
                        && BuiltinBlocks.STONE.equals(server.world().getBlock(
                        new BlockPosition(x, 46, z + 8))),
                "the corridor stands");
        PlayerSession angler = join("Angler" + Math.abs(x));
        server.teleportPlayer(angler, new Position(x + 0.5, 40, z + 0.5));
        await(() -> angler.position().y() == 40.0 && angler.position().z() == z + 0.5,
                "the angler standing on the corridor floor");
        // The rod granted and the flat aim taken (+z, pitch 0).
        assertTrue(angler.inventory().pickUp(ItemStack.of(BuiltinItems.FISHING_ROD)).isEmpty(),
                "the rod granted into the held slot");
        server.ticker().submit(() -> angler.applyMovement(angler.position(),
                new Rotation(0.0f, 0.0f), angler.onGround()));
        await(() -> angler.rotation().yaw() == 0.0f, "the angler aims +z");
        return angler;
    }

    /** The flight line's x (the bobber's spawn offset at yaw 0: -0.16). */
    private static double flightX(int x) {
        return x + 0.5 - 0.16;
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE GRAZE: the flight passing 0.15 outside the raw box
    // but 0.15 inside the GROWN box hooks
    // ------------------------------------------------------------------

    /**
     * A zombie standing with its center 0.45 east of the flight line —
     * its RAW box's west face 0.15 east of the line (never touched) but
     * its 0.3-GROWN box's west face 0.15 WEST of the line (the flight
     * passes inside): the bobber hooks it exactly like vanilla. Pre-fix
     * RED: the raw-band stand-in with its -0.1 slack never saw the
     * flight — the bobber flew on and grounded.
     */
    @Test
    void theGrazingFlightHooksTheMob() throws Exception {
        int x = 10, z = 10;
        PlayerSession angler = rigAngler(x, z);
        double lineX = flightX(x);
        AtomicReference<MobEntity> zombie = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(MobType.ZOMBIE,
                    new Position(lineX + 0.45, 40, z + 4.0), 0);
            if (spawned != null) {
                spawned.setNoAi(true); // the seat discipline: no wandering
                zombie.set(spawned);
            }
        });
        await(() -> zombie.get() != null, "the zombie placed at the graze");

        server.useItem(angler); // the cast
        await(() -> hooked(angler),
                "the grazing flight hooks the zombie (the 0.3-grown box — "
                        + "the raw box's face never touched)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE GRAZE'S CONTROL: 0.15 outside the grown box never
    // hooks
    // ------------------------------------------------------------------

    /**
     * The same zombie 0.3 further out — its grown west face 0.15 EAST
     * of the flight line: the bobber never hooks, the flight grounds
     * beyond (the boundary's pair, both sides pinned).
     */
    @Test
    void theOutsideFlightNeverHooks() throws Exception {
        int x = 30, z = 10;
        PlayerSession angler = rigAngler(x, z);
        double lineX = flightX(x);
        AtomicReference<MobEntity> zombie = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(MobType.ZOMBIE,
                    new Position(lineX + 0.75, 40, z + 4.0), 0);
            if (spawned != null) {
                spawned.setNoAi(true);
                zombie.set(spawned);
            }
        });
        await(() -> zombie.get() != null, "the control zombie placed");

        server.useItem(angler); // the cast
        await(() -> grounded(angler), "the flight grounds beyond the target");
        await(() -> !hooked(angler),
                "the flight 0.15 outside the grown box never hooks");
        assertTrue(Math.abs(zombie.get().position().x() - (lineX + 0.75)) < 0.01,
                "the control zombie stayed at its post (never hooked, never moved)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE ARMOR STAND'S CENSUS (LivingEntity's own member,
    // the DEFLECT verdict — Wave PE-20's repair of this seat's own form)
    // ------------------------------------------------------------------

    /**
     * The reference's hasCollision census includes the armor stand
     * (ArmorStandEntity lines 727-728 over LivingEntity 1594-1595): a
     * bobber flying through a stand's cell FINDS it — the stand's own
     * 0.5x1.975 hull grown 0.3 (the pre-PE-9 engine's census never
     * carried the family whole) — but the HOOK VERDICT refuses it
     * (Wave PE-20 — the reference's own law, FishingBobberEntity.tick
     * lines 269-271: {@code takeDamage(DamageSource.thrown(...), 0.0F)}
     * on ArmorStandEntity's source-name gate, lines 394-395 — "thrown"
     * is neither "arrow" nor "player", FALSE answered whole): the
     * bobber DEFLECTS — no hook, no stick, the flight continuing
     * through the stand's cell. This seat's own first form asserted
     * the hook — the PE-9 wave's misread of the verdict, repaired
     * here.
     */
    @Test
    void theFlightDeflectsOffTheArmorStand() throws Exception {
        int x = 50, z = 10;
        PlayerSession angler = rigAngler(x, z);
        int cellX = (int) Math.floor(flightX(x));
        server.ticker().submit(() -> server.armorStands().place(
                new BlockPosition(cellX, 39, z + 4), 0, 1, 0.0f,
                new java.util.Random(1)));
        await(() -> {
            for (var stand : server.armorStands().allStands()) {
                if ((int) Math.floor(stand.z()) == z + 4) {
                    return true;
                }
            }
            return false;
        }, "the stand placed on the flight line");

        server.useItem(angler); // the cast
        EngineAwaits.awaitTicks(server.world(), 60, "the deflect window");
        assertFalse(hooked(angler),
                "the bobber DEFLECTS off the armor stand (the source-name gate's"
                        + " FALSE verdict — the PE-9 form's own repair)");
        await(() -> {
            for (var stand : server.armorStands().allStands()) {
                if ((int) Math.floor(stand.z()) == z + 4) {
                    return true;
                }
            }
            return false;
        }, "the stand still stands (the refusal's own side-effect-free law)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE MINECART'S CENSUS (the vehicles' hasCollision true)
    // ------------------------------------------------------------------

    /**
     * The reference's hasCollision census includes the minecart
     * (MinecartEntity's own override): a bobber flying over a cart
     * hooks it — the cart's own 0.98x0.7 hull grown 0.3. The cart sits
     * on the flight's own schedule (tick 7-8, the descent through
     * y≈40.9 at z+8.5 — the cart's grown top 41.0). Pre-fix RED: the
     * vehicles were absent from the census whole.
     */
    @Test
    void theFlightHooksTheMinecart() throws Exception {
        int x = 70, z = 10;
        PlayerSession angler = rigAngler(x, z);
        double lineX = flightX(x);
        server.ticker().submit(() -> server.vehicles().spawnMinecart(
                new Position(lineX, 40.35, z + 8.5), 0));
        await(() -> server.vehicles().all().stream()
                        .anyMatch(v -> (int) Math.floor(v.position().z()) == z + 8),
                "the minecart placed on the flight's descent");

        server.useItem(angler); // the cast
        await(() -> hooked(angler),
                "the flight hooks the minecart (the vehicles' own "
                        + "hasCollision arm — the pre-fix engine never hooked a cart)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE ITEM CONTROL: the census's own exclusion
    // ------------------------------------------------------------------

    /**
     * The ItemEntity carries NO hasCollision override (the base Entity's
     * false): a bobber flying through a dropped item never hooks it —
     * the flight grounds beyond. The control pins the census's own
     * exclusion (green by design in both engines).
     */
    @Test
    void theFlightNeverHooksTheDroppedItem() throws Exception {
        int x = 90, z = 10;
        PlayerSession angler = rigAngler(x, z);
        double lineX = flightX(x);
        server.ticker().submit(() -> server.itemEntities().spawnThrown(
                new Position(lineX, 41.2, z + 4.0),
                ItemStack.of(BuiltinItems.DIRT, 1), 0));
        await(() -> server.itemEntities().all().stream()
                        .anyMatch(i -> (int) Math.floor(i.position().z()) == z + 4),
                "the dropped item placed on the flight line");

        server.useItem(angler); // the cast
        await(() -> grounded(angler), "the flight grounds beyond the item");
        assertFalse(hooked(angler),
                "the flight never hooks the dropped item (the ItemEntity's "
                        + "own hasCollision false — the census's exclusion)");
    }
}
