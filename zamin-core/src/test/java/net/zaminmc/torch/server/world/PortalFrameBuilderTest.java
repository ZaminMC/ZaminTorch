package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.net.ClientLink;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether-portal slice 8a against the reference (reference/1.8.8
 * block/PortalBlock.java lines 85-99 create / 271-397 PortalBuilder /
 * 101-117 neighborChanged, entity/Entity.java lines 282-313 the portal
 * tick, PlayerEntity lines 300-317 the player overrides): the obsidian
 * frame scan with the exact bounds (interior 2..21 wide, 3..21 tall), the
 * axis order (X then Z), the no-re-ignite arm, the neighbor-break
 * re-validation, the walk-through cells, and the player's stand clock
 * (80 survival ticks, instant creative crossing, the 10-tick cooldown
 * with the re-arm, the 4-per-tick decay).
 */
class PortalFrameBuilderTest {

    private static FrozenBlockRegistry registry;
    private static EngineWorld world;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("portals", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
    }

    private static void set(int x, int y, int z,
                            net.zaminmc.torch.block.BlockType type) {
        world.setBlock(new BlockPosition(x, y, z), type);
    }

    /** Builds an obsidian frame: the interior spans (x..x+wide-1, y..y+high-1) at z. */
    private static void buildFrame(int x, int y, int z, int wide, int high) {
        for (int i = -1; i <= wide; i++) {
            set(x + i, y - 1, z, BuiltinBlocks.OBSIDIAN);
            set(x + i, y + high, z, BuiltinBlocks.OBSIDIAN);
        }
        for (int j = 0; j < high; j++) {
            set(x - 1, y + j, z, BuiltinBlocks.OBSIDIAN);
            set(x + wide, y + j, z, BuiltinBlocks.OBSIDIAN);
        }
        for (int i = 0; i < wide; i++) {
            for (int j = 0; j < high; j++) {
                set(x + i, y + j, z, world.airType());
            }
        }
    }

    @Test
    void theMinimumFrameBuildsAnXAxisPortal() {
        buildFrame(10, 6, 10, 2, 3);
        assertTrue(PortalFrameBuilder.createAt(world, new BlockPosition(10, 6, 10)),
                "the 2x3 interior is the reference's minimum frame");
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(BuiltinBlocks.NETHER_PORTAL,
                        world.getBlock(new BlockPosition(10 + i, 6 + j, 10)),
                        "the interior fills with the X-plane portal cells");
            }
        }
    }

    @Test
    void aZAxisFrameBuildsWhenTheXScanFails() {
        // The frame spans z at fixed x: the X-axis scan finds no frame; the
        // Z-axis scan builds.
        buildFrame(30, 6, 20, 2, 3);
        // Reorient: the same geometry works for either axis because the
        // frame is planar — a z-spanning frame needs the width along z.
        for (int i = -1; i <= 2; i++) {
            set(30 + i, 6 - 1, 20, world.airType());
            set(30 + i, 6 + 3, 20, world.airType());
            set(30 - 1, 6, 20, world.airType());
            set(30 + 2, 6, 20, world.airType());
            for (int j = 0; j < 3; j++) {
                set(30 + i, 6 + j, 20, world.airType());
            }
        }
        for (int k = -1; k <= 2; k++) {
            set(30, 5, 20 + k, BuiltinBlocks.OBSIDIAN);
            set(30, 9, 20 + k, BuiltinBlocks.OBSIDIAN);
        }
        for (int j = 0; j < 3; j++) {
            set(30, 6 + j, 19, BuiltinBlocks.OBSIDIAN);
            set(30, 6 + j, 22, BuiltinBlocks.OBSIDIAN);
        }
        assertTrue(PortalFrameBuilder.createAt(world, new BlockPosition(30, 6, 20)),
                "the Z-plane frame builds through the second scan");
        assertEquals(BuiltinBlocks.NETHER_PORTAL_Z,
                world.getBlock(new BlockPosition(30, 6, 20)),
                "the interior fills with the Z-plane portal cells");
    }

    @Test
    void anInteriorTooNarrowRefuses() {
        // Interior 1 wide (the reference's width < 2 gate).
        buildFrame(50, 6, 10, 1, 3);
        assertFalse(PortalFrameBuilder.createAt(world, new BlockPosition(50, 6, 10)),
                "a 1-wide interior is below the reference's minimum");
    }

    @Test
    void anInteriorTooShortRefuses() {
        // Interior 2 wide but 2 tall (the reference's height < 3 gate).
        buildFrame(60, 6, 10, 2, 2);
        assertFalse(PortalFrameBuilder.createAt(world, new BlockPosition(60, 6, 10)),
                "a 2-tall interior is below the reference's minimum");
    }

    @Test
    void aBrokenTopCapRefuses() {
        buildFrame(70, 6, 10, 2, 3);
        set(71, 9, 10, world.airType()); // a hole in the top cap
        assertFalse(PortalFrameBuilder.createAt(world, new BlockPosition(70, 6, 10)),
                "the reference's top-row obsidian gate");
    }

    @Test
    void aLivePortalNeverReignites() {
        buildFrame(80, 6, 10, 2, 3);
        assertTrue(PortalFrameBuilder.createAt(world, new BlockPosition(80, 6, 10)));
        // The frame cells cleared and rebuilt with the portal still inside:
        // the second ignite finds portal blocks (foundPortalBlocks > 0) and
        // refuses — the reference's create arm.
        assertFalse(PortalFrameBuilder.createAt(world, new BlockPosition(80, 6, 10)),
                "an already-built frame never re-ignites");
    }

    @Test
    void aBrokenFrameKillsThePortalCell() {
        buildFrame(90, 6, 10, 2, 3);
        assertTrue(PortalFrameBuilder.createAt(world, new BlockPosition(90, 6, 10)));
        // Break one frame block: the re-validation finds the frame invalid.
        set(90, 5, 10, world.airType());
        assertFalse(PortalFrameBuilder.survivesNeighborChange(
                        world, new BlockPosition(90, 6, 10)),
                "the cell whose frame broke dies");
        // An intact portal survives every neighbor wake.
        buildFrame(100, 6, 10, 2, 3);
        assertTrue(PortalFrameBuilder.createAt(world, new BlockPosition(100, 6, 10)));
        assertTrue(PortalFrameBuilder.survivesNeighborChange(
                        world, new BlockPosition(100, 6, 10)),
                "the intact frame's cell survives");
    }

    @Test
    void thePortalCellIsWalkThroughAndIndestructibleByHand() {
        buildFrame(110, 6, 10, 2, 3);
        assertTrue(PortalFrameBuilder.createAt(world, new BlockPosition(110, 6, 10)));
        assertFalse(net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                        world.getBlock(new BlockPosition(110, 6, 10))),
                "the portal cell walks through (the reference's null collision shape)");
        assertEquals(java.util.Optional.empty(),
                net.zaminmc.torch.server.block.BlockBehaviorTable.of(
                        BuiltinBlocks.NETHER_PORTAL.identifier()),
                "no behavior row: not diggable, no drops (the reference's hardness -1)");
    }

    // ---------------------------------------------------------- the stand clock

    private static PlayerSession session() {
        return new PlayerSession(UUID.randomUUID(), "portalist", new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        });
    }

    @Test
    void theSurvivalStandClockCrossesAtEightyTicks() {
        PlayerSession session = session();
        // The reference's post-increment check: the 81st advance sees the
        // old value 80 and crosses.
        for (int i = 0; i < 80; i++) {
            assertFalse(session.advancePortalClock(true, false),
                    "no crossing before the 81st stand tick");
        }
        assertTrue(session.advancePortalClock(true, false),
                "the old value 80 crosses (the reference's portalTime++ >= i)");
        assertEquals(80, session.portalTime(), "the crossing pins the stand time");
        assertEquals(10, session.portalCooldown(),
                "the crossing arms the player's 10-tick cooldown");
    }

    @Test
    void theCreativeBodyCrossesImmediately() {
        PlayerSession session = session();
        assertTrue(session.advancePortalClock(true, true),
                "the invulnerable body's max is 0: the first tick crosses");
    }

    @Test
    void theStandTimeDecaysByFourOutOfThePortal() {
        PlayerSession session = session();
        for (int i = 0; i < 40; i++) {
            session.advancePortalClock(true, false);
        }
        assertEquals(40, session.portalTime());
        session.advancePortalClock(false, false);
        assertEquals(36, session.portalTime(), "the reference's portalTime -= 4");
        for (int i = 0; i < 9; i++) {
            session.advancePortalClock(false, false);
        }
        assertEquals(0, session.portalTime(), "the decay clamps at zero");
    }

    @Test
    void theCooldownReArmsOnTheEntryEdgeOnly() {
        PlayerSession session = session();
        for (int i = 0; i < 80; i++) {
            session.advancePortalClock(true, false);
        }
        assertTrue(session.advancePortalClock(true, false));
        assertEquals(10, session.portalCooldown());
        // Standing inside the portal: the pinned stand time re-crosses (the
        // reference's own re-fire — the 8b teleport moves the body out) and
        // the crossing's threshold arm re-sets the cooldown to 10; the
        // standing itself never refreshes it (the collision arm is
        // movement-driven — the walker section below pins the difference).
        assertTrue(session.advancePortalClock(true, false),
                "the pinned body re-crosses (the reference's own re-fire)");
        assertEquals(10, session.portalCooldown(),
                "the re-cross's threshold arm re-set the cooldown");
        // Walking out and back in during an active cooldown: the entry edge
        // re-arms (the collision arm's early return).
        PlayerSession walker = session();
        for (int i = 0; i < 80; i++) {
            walker.advancePortalClock(true, false);
        }
        assertTrue(walker.advancePortalClock(true, false)); // the crossing arms 10
        walker.advancePortalClock(false, false); // the walk-out: the cooldown to 9
        walker.advancePortalClock(true, false);  // the entry edge re-arms
        assertEquals(10, walker.portalCooldown(),
                "the entry edge during an active cooldown re-arms");
        assertFalse(walker.advancePortalClock(false, false),
                "the walk-out decays");
        assertEquals(9, walker.portalCooldown());
    }
}
