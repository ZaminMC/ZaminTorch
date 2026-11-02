package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.SkullBlocks;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-033 PASS-2 SEAT — the wither-summon family's dimension
 * ownership (docs/vanilla-conformance-audit.md, the forty-second pass — the
 * census tail's second head, widened from the wither-poof member; the
 * pre-Slice-C core discovered independently, the post-Slice-C residues
 * the correction).
 *
 * <p>THE REFERENCE LAW (SkullBlock.trySpawn lines 167-223, re-walked for
 * this seat): the whole summon rides the ACTING world — the parameter the
 * item's placement passes (SkullItem.useOn line 81 passes the SAME world
 * its block write at line 55 used), the world the pattern was built in.
 * Every consult is a member of the law: the skull row's NODROP write, the
 * clear walk's AIR write, {@code world.addEntity(witherentity)} — the
 * wither joins THAT world's entity list (the dimension implicit in the
 * world object, Entity's constructor line 166 stamping at BIRTH), the
 * 120-particle column over {@code world.addParticle} (World lines
 * 796-812: the per-world eventListeners list alone), the achievement row
 * over {@code world.getEntitiesOfType(PlayerEntity.class, ...)} — the
 * ACTING world's player list — and the update walk. A nether summon is
 * fully legal vanilla: no dimension gate exists anywhere in the
 * reference.</p>
 *
 * <p>THE ENGINE'S TWO ERAS. Pre-Slice-C (develop 149eb1f0): the single
 * overworld-bound store — the tile family and the whole summon consult
 * read the OVERWORLD for every dimension's placement (a nether T never
 * matched — the mechanic missing whole; a mirrored overworld geometry
 * answered instead — the clear walk writing AIR into the OVERWORLD build,
 * the wither stamped 0). THE SEAT'S ARMS A/B ARE RED THERE at the core
 * awaits. Post-Slice-C (the FL4-GAP2 Slice C landing, develop efcd0c4f):
 * the per-dimension stores + hosts own the consult family — the seat's
 * core pins turn GREEN on that tree (this seat is the independent
 * cross-tree verification of that landing), and the RESIDUE members my
 * census names stay RED: (a) the poof on the no-dimension fx overload
 * (the unconditional overworld 0 stamp — the census tail's named member);
 * (b) the birth stamp POST-FAN (spawnAt's 2-arg then setDimension — the
 * wire's sendMobSpawn gate reads the Java-default 0 at the event, the
 * nether observers never receiving the spawn packet); (c) the achievement
 * row ungated (awardPlayersInBox walks every player — the overworld
 * players at mirrored coordinates earning the nether summon's stat);
 * (d) the break's typed drop on the dimensionless spawnDropAtBlock (the
 * nether break's head spawning in the overworld).</p>
 *
 * <p>THE SEAT: three full-boot constructions, every skull placed through
 * the REAL use wire (the FL4-F7 seat's own form — no store seeding, only
 * stable public APIs). THE CONTROL (green throughout): the overworld
 * summon whole. ARM A: the nether summon — the wither births in the
 * NETHER roster, the build clears, the poof carries -1, the builder earns
 * the achievement, and a broken nether skull's typed drop lands IN THE
 * NETHER. ARM B: the cross-dimension corruption — a complete unsummoned
 * overworld T (built through the wire with one sand holed, the sand fixed
 * afterwards — the placement consult fires on skull placements alone)
 * must STAND when the nether player completes their own T; the overworld
 * player at the mirrored coordinates must NOT earn the nether summon's
 * achievement.</p>
 */
class WitherSummonDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private final List<FxManager.Poof> capturedPoofs = java.util.Collections.synchronizedList(
            new ArrayList<>());

    /** The build's anchor: the T runs along X, the base center at (64, 101, 64). */
    private static final int BASE_Y = 101;
    private static final int SAND_Y = BASE_Y + 1;
    private static final int SKULL_Y = BASE_Y + 2;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "withersummon" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(event -> {
            if (event instanceof FxManager.Poof poof) {
                capturedPoofs.add(poof);
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        // The full login handshake's PLAYING transition (the award walk's
        // own gate reads it — the established seat idiom).
        server.ticker().submit(session::markPlaying);
        return session;
    }

    /**
     * Seeds the standing geometry in one world (the ticker lane): the 7x7
     * stone floor at y 100, air at 101-104 (the nether's own terrain
     * cleared), the soul-sand T — base center + the sand row, with the
     * {@code sandHole} cell left as netherrack when building the
     * complete-but-unsummoned overworld T (the hole breaks the pattern
     * during the wire placements; the sand fix afterwards never consults —
     * the summon fires on skull placements alone).
     */
    private void seedFloorAndT(EngineWorld world, boolean sandHole) throws Exception {
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = 101; y <= 104; y++) {
                        world.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                world.airType());
                    }
                }
            }
            // The base center (the flanks stay air — the "~#~" row).
            world.setBlock(new BlockPosition(64, BASE_Y, 64), BuiltinBlocks.SOUL_SAND);
            // The sand row "###".
            for (int i = -1; i <= 1; i++) {
                world.setBlock(new BlockPosition(64 + i, SAND_Y, 64),
                        i == -1 && sandHole ? BuiltinBlocks.NETHERRACK : BuiltinBlocks.SOUL_SAND);
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.SOUL_SAND.equals(world.getBlock(new BlockPosition(64, SAND_Y, 64))),
                40, "the T's sand row landed");
    }

    /**
     * Places one wither skull through the REAL use wire: held stack damage
     * 1, click UP on the sand below the target. The landing await accepts
     * EITHER the standing block OR the fired summon — the block write and
     * the summon consult are one tick-lane task, so the summon-completing
     * placement clears its own block within the same tick (the seat must
     * stay green on the corrected tree, where the nether summons fire).
     */
    private void placeWitherSkull(PlayerSession placer, BlockPosition mountBelow) throws Exception {
        server.useItemOnBlock(placer, mountBelow, 1, java.util.Optional.empty(), windowId -> { });
        BlockPosition target = mountBelow.offset(0, 1, 0);
        // The landing await: the block, OR — only when no wither existed
        // yet — the summon that consumed it (the block write and the
        // summon consult are one tick-lane task; the summon-completing
        // placement clears its own block within the same tick). Once a
        // wither stands in the roster the await is STRICT: a later
        // off-pattern placement must land its block or the seat fails
        // (no vacuous pass through the standing wither).
        boolean witherExisted = wither() != null;
        EngineAwaits.awaitCondition(server.world(),
                () -> SkullBlocks.isSkull(worldOf(placer).getBlock(target).identifier())
                        || (!witherExisted && wither() != null),
                40, "the placement landed at " + target
                        + " (the block, or the summon consumed it)");
    }

    private EngineWorld worldOf(PlayerSession session) {
        return session.dimension() == 0 ? server.world() : server.worldFor(session.dimension());
    }

    /** The wither in the live roster, or null. */
    private MobEntity wither() {
        return server.mobs().all().stream()
                .filter(mob -> mob.type() == MobType.WITHER)
                .findFirst().orElse(null);
    }

    /**
     * Grants the summon achievement's parent (the reference's own chain,
     * Achievements line 109: spawnWither's parent is LEAVE_THE_END — the
     * engine's increment gate honors it 1:1). The seat's builders "have
     * left the End once" so the summon award's landing is observable —
     * WITHOUT the grant the award refuses everywhere and the negative
     * pins would pass vacuously.
     */
    private void grantWitherAchievementParent(PlayerSession player) {
        player.stats().set(net.zaminmc.torch.server.stat.EngineAchievements
                .LEAVE_THE_END.key(), 1);
    }

    private static boolean isStandingT(EngineWorld world) {
        for (int i = -1; i <= 1; i++) {
            if (!SkullBlocks.isSkull(world.getBlock(
                    new BlockPosition(64 + i, SKULL_Y, 64)).identifier())) {
                return false; // the skull row
            }
            if (!BuiltinBlocks.SOUL_SAND.equals(world.getBlock(
                    new BlockPosition(64 + i, SAND_Y, 64)))) {
                return false; // the sand row
            }
        }
        return BuiltinBlocks.SOUL_SAND.equals(world.getBlock(
                new BlockPosition(64, BASE_Y, 64))); // the base
    }

    @Test
    void theControlTheOverworldSummonStandsWhole() throws Exception {
        EngineWorld overworld = server.world();
        seedFloorAndT(overworld, false);
        PlayerSession builder = join("WitherOWBuilder");
        builder.applyMovement(new Position(64.5, 101, 67.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the builder settles");
        grantWitherAchievementParent(builder);
        builder.inventory().setSlot(0, new ItemStack(BuiltinItems.SKULL, 3, 1, null, null));
        builder.inventory().selectHotbarSlot(0);

        // Three wither skulls through the real wire — the third completes the T.
        placeWitherSkull(builder, new BlockPosition(63, SAND_Y, 64));
        placeWitherSkull(builder, new BlockPosition(65, SAND_Y, 64));
        placeWitherSkull(builder, new BlockPosition(64, SAND_Y, 64));

        // THE CONTROL PINS (green on develop): the summon fired whole in
        // the overworld — the build cleared, the wither in the overworld
        // roster, the poof stamped 0.
        EngineAwaits.awaitCondition(server.world(),
                () -> wither() != null, 40, "the overworld summon birthed the wither");
        MobEntity wither = wither();
        assertNotNull(wither, "the wither exists");
        assertEquals(0, wither.dimension(),
                "the control: the overworld summon's wither carries the overworld's 0");
        EngineAwaits.awaitCondition(server.world(),
                () -> !isStandingT(overworld), 40, "the overworld build cleared");
        FxManager.Poof poof = capturedPoofs.isEmpty() ? null : capturedPoofs.get(0);
        assertNotNull(poof, "the control: the summon's 120-particle column fired the poof");
        assertEquals(0, poof.dimension(),
                "the control: the overworld summon's poof stamps the overworld's 0");
        assertTrue(builder.stats().hasAchievement(
                        net.zaminmc.torch.server.stat.EngineAchievements.SUMMON_WITHER.key()),
                "the control: the overworld builder earns the summon achievement "
                        + "(SkullBlock lines 197-199: the acting world's players)");
    }

    @Test
    void armATheNetherSummonMustBirthInTheNether() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        seedFloorAndT(nether, false);
        PlayerSession builder = join("WitherNBuilder");
        builder.setDimension(-1);
        builder.applyMovement(new Position(64.5, 101, 67.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the builder settles in the nether");
        grantWitherAchievementParent(builder);
        // Four wither skulls: three for the pattern, one off-pattern for
        // the break-drop member below.
        builder.inventory().setSlot(0, new ItemStack(BuiltinItems.SKULL, 4, 1, null, null));
        builder.inventory().selectHotbarSlot(0);

        // The same T through the real wire in the NETHER — vanilla births
        // the wither there (SkullBlock.trySpawn carries no dimension gate;
        // every consult rides the acting world).
        placeWitherSkull(builder, new BlockPosition(63, SAND_Y, 64));
        placeWitherSkull(builder, new BlockPosition(65, SAND_Y, 64));
        placeWitherSkull(builder, new BlockPosition(64, SAND_Y, 64));

        // THE EVENT AWAIT — the summon must fire (vanilla births the
        // wither on the nether's own T). RED on develop at this very pin:
        // the summon consult reads the OVERWORLD's cells at these
        // coordinates (empty air 38 blocks above the flat surface) — the
        // pattern never matches, no wither exists anywhere, and a whole
        // vanilla mechanic is missing in the nether.
        EngineAwaits.awaitCondition(server.world(),
                () -> wither() != null,
                40, "VT-033 arm A: the nether summon birthed the wither "
                        + "(SkullBlock.trySpawn lines 167-223: every consult on the ACTING "
                        + "world — the block writes, the clear walk, addEntity, the "
                        + "120-particle column over World.addParticle 796-812, that "
                        + "world's listener list alone). Observed: no wither anywhere — "
                        + "the engine's SkullManager host reads the OVERWORLD for every "
                        + "dimension's placement");

        // THE LAW PINS (the fix's parity pins): the wither carries the
        // NETHER's dimension (the reference's addEntity on the acting
        // world), the NETHER build cleared (the clear walk on the acting
        // world), the poof stamped -1 (the census tail's named member
        // — the no-dimension overload's unconditional 0), and the builder
        // earns the achievement (the acting world's player list).
        assertEquals(-1, wither().dimension(),
                "VT-033 arm A: the nether summon's wither must carry the NETHER's "
                        + "dimension (the reference's world.addEntity — the acting "
                        + "world's entity list). Observed: " + wither().dimension());
        EngineAwaits.awaitCondition(server.world(),
                () -> !isStandingT(nether), 40,
                "the nether build cleared (the clear walk on the acting world)");
        FxManager.Poof poof = capturedPoofs.isEmpty() ? null : capturedPoofs.get(0);
        assertNotNull(poof, "the poof fired (the reference's 120-particle column)");
        assertEquals(-1, poof.dimension(),
                "VT-033: the nether summon's poof must carry the NETHER's dimension "
                        + "(the reference's world.addParticle — the summoning world's "
                        + "listener list alone). Observed stamp: " + poof.dimension());
        assertTrue(builder.stats().hasAchievement(
                        net.zaminmc.torch.server.stat.EngineAchievements.SUMMON_WITHER.key()),
                "VT-033 arm A: the nether builder earns the summon achievement "
                        + "(SkullBlock lines 197-199: the ACTING world's players — "
                        + "world.getEntitiesOfType walks the summoning world's list)");

        // THE BREAK-DROP MEMBER — a fourth skull off the pattern, broken
        // through the real dig wire: the typed drop (SkullBlock.onRemoved
        // lines 132-152 — the tile's type riding the stack's damage) must
        // land IN THE NETHER (the reference's dropItem on the acting
        // world). RED where the drop rides the dimensionless
        // spawnDropAtBlock: the head spawning in the overworld, the nether
        // digger never seeing it.
        // The off-pattern mount: the platform's own stone (inside the
        // seeded 7x7 — an air click would be refused, the skull never
        // landing), the skull two cells off the pattern's east flank.
        BlockPosition offPatternMount = new BlockPosition(66, 100, 64);
        placeWitherSkull(builder, offPatternMount);
        server.blockInteraction().submitMiningStart(builder, offPatternMount.offset(0, 1, 0));
        EngineAwaits.awaitTicks(server.world(), 12, "the dig accumulates past the accept band");
        server.blockInteraction().submitMiningFinished(builder, offPatternMount.offset(0, 1, 0));
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(offPatternMount.offset(0, 1, 0)).equals(nether.airType()),
                40, "the off-pattern nether skull broke");
        assertTrue(server.itemEntities().all().stream().anyMatch(item ->
                        item.dimension() == -1
                                && Math.abs(item.position().x() - 66.5) < 1.5
                                && Math.abs(item.position().z() - 64.5) < 1.5
                                && item.stack().type().identifier().toString()
                                        .equals("minecraft:skull")),
                "VT-033 arm A: the nether skull break's typed drop must land IN THE "
                        + "NETHER (the reference's dropItem on the acting world — "
                        + "SkullBlock.onRemoved lines 132-152). Observed drops: "
                        + server.itemEntities().all().stream()
                                        .map(item -> item.stack().type().identifier() + "@dim"
                                                + item.dimension())
                                        .toList());
    }

    @Test
    void armBTheOverworldGeometryMustNeverAnswerForTheNetherPlacement() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");

        // The OVERWORLD's complete-but-unsummoned T: built through the real
        // wire with the west sand holed (the placement consults find no
        // pattern — no summon), the sand fixed afterwards (a block write
        // alone: the summon consult fires on skull placements only).
        seedFloorAndT(overworld, true);
        PlayerSession overworldBuilder = join("WitherOWFixer");
        overworldBuilder.applyMovement(new Position(64.5, 101, 67.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the overworld builder settles");
        grantWitherAchievementParent(overworldBuilder);
        overworldBuilder.inventory().setSlot(0, new ItemStack(BuiltinItems.SKULL, 3, 1, null, null));
        overworldBuilder.inventory().selectHotbarSlot(0);
        placeWitherSkull(overworldBuilder, new BlockPosition(63, SAND_Y, 64)); // on the holed netherrack
        placeWitherSkull(overworldBuilder, new BlockPosition(65, SAND_Y, 64));
        placeWitherSkull(overworldBuilder, new BlockPosition(64, SAND_Y, 64));
        assertTrue(wither() == null, "the holed sand kept the overworld build unsummoned");
        server.ticker().submit(() -> overworld.setBlock(
                new BlockPosition(63, SAND_Y, 64), BuiltinBlocks.SOUL_SAND));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.SOUL_SAND.equals(
                        overworld.getBlock(new BlockPosition(63, SAND_Y, 64))),
                40, "the overworld sand fixed — the complete unsummoned T stands");

        // The NETHER's own T, completed through the real wire by a nether
        // player at the SAME coordinates.
        seedFloorAndT(nether, false);
        PlayerSession netherBuilder = join("WitherNCompltr");
        netherBuilder.setDimension(-1);
        netherBuilder.applyMovement(new Position(64.5, 101, 67.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether builder settles");
        grantWitherAchievementParent(netherBuilder);
        netherBuilder.inventory().setSlot(0, new ItemStack(BuiltinItems.SKULL, 3, 1, null, null));
        netherBuilder.inventory().selectHotbarSlot(0);
        placeWitherSkull(netherBuilder, new BlockPosition(63, SAND_Y, 64));
        placeWitherSkull(netherBuilder, new BlockPosition(65, SAND_Y, 64));
        placeWitherSkull(netherBuilder, new BlockPosition(64, SAND_Y, 64));

        // THE EVENT AWAIT — the summon must fire on the nether's own T
        // (vanilla: the consult reads the nether's cells). On develop this
        // await PASSES — but the wither observed is stamped 0: the consult
        // read the OVERWORLD, where the mirrored complete T answered
        // instead (the law pins below catch the corruption whole).
        EngineAwaits.awaitCondition(server.world(),
                () -> wither() != null,
                40, "VT-033 arm B: the nether completion birthed a wither");

        // THE LAW PINS — each RED on develop with the corruption observed:
        assertEquals(-1, wither().dimension(),
                "VT-033 arm B: the nether completion's wither must carry the "
                        + "NETHER's dimension (the reference's addEntity on the acting "
                        + "world). Observed: " + wither().dimension()
                        + " — the OVERWORLD's mirrored geometry answered the consult");
        assertTrue(isStandingT(overworld),
                "VT-033 arm B: the overworld's complete T must stand — the nether "
                        + "placement's clear walk consults the ACTING world alone "
                        + "(trySpawn's setBlockState on the parameter world). Observed: "
                        + "the overworld build was cleared by a nether placement — "
                        + "cross-dimension world corruption");
        assertFalse(isStandingT(nether),
                "the nether build cleared (the summon's clear walk on the acting world)");
        FxManager.Poof poof = capturedPoofs.isEmpty() ? null : capturedPoofs.get(0);
        assertNotNull(poof, "the poof fired (the reference's 120-particle column)");
        assertEquals(-1, poof.dimension(),
                "VT-033: the nether summon's poof must carry the NETHER's dimension. "
                        + "Observed stamp: " + poof.dimension());
        assertFalse(overworldBuilder.stats().hasAchievement(
                        net.zaminmc.torch.server.stat.EngineAchievements.SUMMON_WITHER.key()),
                "VT-033 arm B: the overworld player at the mirrored coordinates must "
                        + "NOT earn the nether summon's achievement (SkullBlock lines "
                        + "197-199: world.getEntitiesOfType walks the ACTING world's "
                        + "player list — the overworld's players are not in the nether's "
                        + "list). Observed: the ungated awardPlayersInBox walk reached "
                        + "the overworld player");
        assertTrue(netherBuilder.stats().hasAchievement(
                        net.zaminmc.torch.server.stat.EngineAchievements.SUMMON_WITHER.key()),
                "the nether builder earns the achievement (the positive control)");
    }
}
