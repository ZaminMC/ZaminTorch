package net.zaminmc.torch.server.border;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-025 (first numbered VT-014 on the discovering lane's branch —
 * RENUMBERED by the public-first protocol: PR #117's arrow-stick placement
 * owns VT-014 publicly, opened 05:40:04Z before this lane's record) — the
 * nether's border containment consults use the overworld's raw center:
 * vanilla's nether border is the overworld's synced rows with the CENTER
 * REPORTED AT /8.
 *
 * <p>THE LAW (the reference, 1.8.8): the {@code /worldborder} command
 * and the wire both target {@code worlds[0]}'s border ALONE
 * (WorldBorderCommand lines 171-172, PlayerManager.sendWorldInfo lines
 * 678-679 — the engine's single shared border MATCHES those lanes), but
 * every world owns its border for the CONTAINMENT lanes: the nether and
 * the End are ReadOnlyServerWorlds whose constructor wires the
 * overworld's border listener to copy every change into their own
 * borders (ReadOnlyServerWorld lines 17-37), and the NetherDimension's
 * border reports the CENTER AT /8 (createWorldBorder lines 75-86:
 * {@code getCenterX()/getCenterZ()} return {@code super/8.0}). The dig
 * consult reads the ACTING world's border
 * ({@code serverworld.getWorldBorder().contains(blockpos)}, the dig
 * gate ServerPlayNetworkHandler lines 437-445).</p>
 *
 * <p>THE SEAT: the border configured center (1600, 1600) size 1000 —
 * the overworld's geometry spans [1100..2100], the NETHER's vanilla
 * geometry is centered (200, 200) with the SAME size, spanning
 * [-300..700]. A nether player standing at (200, y, 200) is at
 * vanilla's nether-border exact CENTER: the dig at their feet MUST
 * open (vanilla). The engine's shared-border dig gate reads the
 * overworld's geometry — (200, 200) is 900 blocks outside [1100..2100]
 * — and refuses the dig.</p>
 */
class NetherBorderContainmentParityTest {

    @TempDir
    Path dataDir;

    private net.zaminmc.torch.server.EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "borderdim" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
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
        server.ticker().submit(session::markPlaying);
        server.ticker().submit(() -> session.setOpLevel(2));
        return session;
    }

    /**
     * The nether dig at vanilla's nether-border center must open: the
     * seat digs DIRT (0.5 hardness, the bare hand's 15-tick dig) at the
     * player's feet and awaits the AIR commit.
     */
    @Test
    void theNetherDigOpensAtVanillasNetherBorderCenter() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession player = join("NetherSurveyor");
        player.setDimension(-1);

        // The border: center (1600, 1600), size 1000 — the overworld
        // spans [1100..2100]; vanilla's NETHER border is centered
        // (200, 200) spanning [-300..700] (the /8 projection).
        server.chatService().submitChat(player, "/worldborder center 1600 1600");
        server.chatService().submitChat(player, "/worldborder set 1000");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.worldBorder().getCenterX() == 1600.0
                        && server.worldBorder().getSizeLerpTarget() == 1000.0,
                40, "the border configured");

        // The nether platform at vanilla's nether-border center: dirt
        // floor at y=100 (the dig target), air above.
        int fy = 100;
        BlockPosition target = new BlockPosition(200, fy, 200);
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    nether.setBlock(new BlockPosition(200 + dx, fy, 200 + dz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.DIRT);
                    for (int y = fy + 1; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(200 + dx, y, 200 + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.DIRT.equals(
                        nether.getBlock(target)),
                40, "the nether dig site landed");

        player.applyMovement(new Position(200.5, fy + 1, 200.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        // The dig start at the player's feet cell — the real dig-start
        // route (the gate consult rides it).
        server.blockInteraction().submitMiningStart(player, target);
        server.ticker().submit(() -> server.blockInteraction()
                .submitMiningFinished(player, target));

        // The bare hand's dirt dig: 15 ticks nominal; the wall budget
        // covers the completion walk (the ML-002 shape — the engine's
        // tick clock, generously).
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(target).equals(nether.airType()),
                120, "the nether dig at vanilla's border center completed");
        assertTrue(nether.getBlock(target).equals(nether.airType()),
                "the dig at (200, 200) in the nether opened — vanilla's nether "
                        + "border is centered at the overworld's center /8 "
                        + "(NetherDimension.createWorldBorder lines 75-86), so "
                        + "the player at (200, 200) stands at the nether "
                        + "border's CENTER and the dig MUST open; the engine's "
                        + "shared-border gate reads the overworld's [1100..2100] "
                        + "geometry and refuses");
    }

    /**
     * THE NO-BORDER NETHER CONTROL: the same nether dig with the border
     * UNCONFIGURED (the default 6.0E7 — the reference's own
     * unconfigured infinity) MUST open — the seat's own attribution
     * proof that the nether member's refusal is the border gate alone
     * (not the reach band, not the spawn protection, not the nether
     * dig route itself).
     */
    @Test
    void theNetherDigOpensWithNoBorderConfigured() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession player = join("NetherDigger");
        player.setDimension(-1);

        int fy = 100;
        BlockPosition target = new BlockPosition(200, fy, 200);
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    nether.setBlock(new BlockPosition(200 + dx, fy, 200 + dz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.DIRT);
                    for (int y = fy + 1; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(200 + dx, y, 200 + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.DIRT.equals(
                        nether.getBlock(target)),
                40, "the nether dig site landed");

        player.applyMovement(new Position(200.5, fy + 1, 200.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        server.blockInteraction().submitMiningStart(player, target);
        server.ticker().submit(() -> server.blockInteraction()
                .submitMiningFinished(player, target));

        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(target).equals(nether.airType()),
                120, "the unconfigured-border nether dig completed");
        assertTrue(nether.getBlock(target).equals(nether.airType()),
                "the nether dig with the default border opens — the nether "
                        + "member's refusal is the border gate alone");
    }

    /**
     * THE OVERWORLD CONTROL: the same configured border refuses the dig
     * OUTSIDE its geometry in the overworld (the engine's shared border
     * is CORRECT for the overworld's own containment — the lane the fix
     * must not disturb).
     */
    @Test
    void theOverworldDigOutsideTheBorderStillRefuses() throws Exception {
        PlayerSession player = join("GroundWatcher");

        server.chatService().submitChat(player, "/worldborder center 1600 1600");
        server.chatService().submitChat(player, "/worldborder set 1000");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.worldBorder().getCenterX() == 1600.0
                        && server.worldBorder().getSizeLerpTarget() == 1000.0,
                40, "the border configured");

        // (200, 4, 200) in the OVERWORLD: 900 blocks outside [1100..2100]
        // — the dig must refuse in BOTH vanilla and the engine (the
        // overworld's own border).
        BlockPosition target = new BlockPosition(200, 4, 200);
        server.world().execute(() -> server.world().setBlock(target,
                net.zaminmc.torch.server.block.BuiltinBlocks.DIRT));
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.DIRT.equals(
                        server.world().getBlock(target)),
                40, "the overworld dig site landed");

        player.applyMovement(new Position(200.5, 5.0, 200.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        server.blockInteraction().submitMiningStart(player, target);
        server.ticker().submit(() -> server.blockInteraction()
                .submitMiningFinished(player, target));

        EngineAwaits.awaitTicks(server.world(), 60, "the refusal window");
        assertEquals(net.zaminmc.torch.server.block.BuiltinBlocks.DIRT,
                server.world().getBlock(target),
                "the dig outside the OVERWORLD's own border stays refused "
                        + "(the shared border's correct lane)");
    }

    /**
     * THE NETHER DAMAGE WALK: with the border configured center (1600,
     * 1600) size 1000, a nether player at (200, y, 200) stands at
     * vanilla's NETHER border center (the /8 projection) — NO border
     * damage may fire (vanilla). The engine's shared-geometry walk reads
     * the overworld's [1100..2100] — (200, 200) is ~900 blocks outside —
     * and chews the player at ~181 per tick.
     */
    @Test
    void theNetherPlayerAtVanillasNetherBorderCenterTakesNoBorderDamage() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession player = join("NetherWatcher");
        player.setDimension(-1);

        // The nether platform at vanilla's nether-border center (the same
        // shape the dig seat builds).
        int fy = 100;
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    nether.setBlock(new BlockPosition(200 + dx, fy, 200 + dz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.DIRT);
                    for (int y = fy + 1; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(200 + dx, y, 200 + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.DIRT.equals(
                        nether.getBlock(new BlockPosition(200, fy, 200))),
                40, "the nether watch post landed");

        player.applyMovement(new Position(200.5, fy + 1, 200.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        // The health baseline BEFORE the border configures — the engine's
        // pre-fix chew is instant (the shared geometry puts the projected
        // center ~894 blocks outside -> ~178 per tick, the 20-health body
        // dead on the first walk tick), so the baseline must never race
        // the border's own configuration await (the probe-verified shape:
        // a post-configuration read observes 0.0 and the no-damage assert
        // passes spuriously on the unfixed tree).
        float before = player.health();

        server.chatService().submitChat(player, "/worldborder center 1600 1600");
        server.chatService().submitChat(player, "/worldborder set 1000");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.worldBorder().getCenterX() == 1600.0
                        && server.worldBorder().getSizeLerpTarget() == 1000.0,
                40, "the border configured");

        EngineAwaits.awaitTicks(server.world(), 40, "the border-damage window");
        assertEquals(before, player.health(), 1e-6,
                "the nether player at (200, 200) — vanilla's nether border CENTER "
                        + "(the overworld's center /8, NetherDimension.createWorldBorder "
                        + "lines 75-86) — takes NO border damage; the engine's "
                        + "shared-geometry walk reads the overworld's [1100..2100] and "
                        + "would chew ~181 per tick there");
    }

    /**
     * THE OVERWORLD DAMAGE CONTROL: the same configured border chews an
     * OVERWORLD player standing AT SPAWN — the spawn (0, 0) is ~1100
     * blocks outside the overworld's own [1100..2100] — proving the damage
     * walk is armed and the nether member's silence is the projection's
     * law, not a dead lane (the WorldBorderTest damage shape: the player
     * stays inside the pregen band, the border moves away from them).
     */
    @Test
    void theOverworldPlayerOutsideTheBorderTakesTheDamage() throws Exception {
        PlayerSession player = join("GroundRunner");
        EngineAwaits.awaitCondition(server.world(),
                () -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the runner plays");

        // The health baseline BEFORE the border configures — the chew is
        // instant (a ~1100-block depth at rate 0.2 -> ~218 per tick, the
        // 20-health body dead on the first walk tick), so the baseline
        // must never race the border's own configuration await.
        float before = player.health();

        server.chatService().submitChat(player, "/worldborder center 1600 1600");
        server.chatService().submitChat(player, "/worldborder set 1000");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.worldBorder().getCenterX() == 1600.0
                        && server.worldBorder().getSizeLerpTarget() == 1000.0,
                40, "the border configured");

        // The player stands at the world spawn — inside the pregen band,
        // ~1100 blocks outside the overworld's own [1100..2100].
        EngineAwaits.awaitCondition(server.world(),
                () -> player.health() < before, 120, "the outside-border damage lands");
        assertTrue(player.health() < before,
                "the overworld player outside the overworld's own border takes the "
                        + "damage (the shared border's correct lane — the walk is armed)");
    }
}
