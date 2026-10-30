package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.entity.FallingBlockEntity;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.List;

/**
 * The gravity families' fuse migration onto the scheduled block-tick
 * surface (reference/1.8.8 net/minecraft/block/FallingBlock.java lines
 * 22-33 + DragonEggBlock.java lines 33-58, 1:1): every gravity family's
 * wake — {@code FallingBlock.onAdded}/{@code neighborChanged} →
 * {@code world.scheduleTick(pos, this, getTickRate(world))} — now rides
 * the {@link ScheduledTickSystem} (the second family migrated off the
 * block-update system's own queues, after the fire).
 *
 * <p><b>The families</b>: the sand family (FallingBlock over
 * Material.SAND — sand, red sand, gravel), the anvil band (Slice 53 —
 * FallingBlock over Material.ANVIL, every state joins the wake exactly
 * like the sand family), and the dragon egg (Slice 55 — DragonEggBlock
 * extends Block, not FallingBlock: its own tryFall over the same
 * canFallThrough gate, the reference's own five-tick band against the
 * sand family's two).</p>
 *
 * <p><b>The tick</b> (FallingBlock.tick → tryFall, lines 37-59): the
 * block converts when the cell below can be fallen through (air, water,
 * lava, fire — sand stacked over a pond collapses into it) and the block
 * itself still occupies the cell — the block leaves the world, the
 * falling entity takes over at {@code startFall}. The reference's
 * instant-settle arm (the unloaded 32-area's remove-and-drop-to-the-
 * bottom) never fires: the engine keeps every chunk it touches loaded —
 * the entity arm is the engine's whole shape (named).</p>
 *
 * <p><b>The unwired window</b>: the block-update system schedules through
 * this surface when its seam is wired (the engine's boot always wires
 * it); a standalone-constructed system keeps its own queue with the same
 * delays and the same tryFall — the engine's own unit-test harness rides
 * that window (named, not a second behavior).</p>
 */
public final class GravityFalls {

    /** The sand family's fuse (FallingBlock.getTickRate: 2 game ticks). */
    public static final int DELAY = 2;

    private static final Identifier SAND = Identifier.parse("minecraft:sand");
    private static final Identifier RED_SAND = Identifier.parse("minecraft:red_sand");
    private static final Identifier GRAVEL = Identifier.parse("minecraft:gravel");

    private GravityFalls() {
    }

    /** @return whether the identifier is any gravity family's state. */
    public static boolean isGravityFamily(Identifier id) {
        if (id == null) {
            return false;
        }
        return id.equals(SAND) || id.equals(RED_SAND) || id.equals(GRAVEL)
                || AnvilBlocks.isAnvil(id)
                || DragonEggBlocks.isDragonEgg(id);
    }

    /** @return the family's fuse: 2 for the sand/anvil families, the egg's own 5. */
    public static int delayOf(Identifier id) {
        return DragonEggBlocks.isDragonEgg(id)
                ? DragonEggBlocks.FALL_CHECK_DELAY
                : DELAY;
    }

    /** The sand family's three ids (the anvil and egg bands enumerate through their own classes). */
    static List<Identifier> sandFamilyIds() {
        return List.of(SAND, RED_SAND, GRAVEL);
    }

    /**
     * The reference's tryFall (FallingBlock lines 37-59, the engine's
     * verified shape from Slice 470): the cell below can be fallen
     * through and the block itself still occupies the cell — the block
     * leaves the world, the falling entity takes over. Tick-thread
     * context.
     */
    public static void tryFall(EngineWorld world, BlockPosition position,
                                FallingBlockEntityManager fallingEntities) {
        BlockType type = world.getBlock(position);
        if (type.equals(world.airType())) {
            return;
        }
        Identifier identifier = type.identifier();
        if (!isGravityFamily(identifier)) {
            return;
        }
        if (FallingBlockEntity.canFallThrough(
                FallingBlockEntity.blockOrAir(world, position.offset(0, -1, 0)))) {
            world.setBlock(position, world.airType());
            fallingEntities.startFall(world, position, type);
        }
    }

    /**
     * Registers the gravity ticker for every family id — the sand family's
     * three, the anvil band, the dragon egg — dispatched through the
     * scheduled-tick surface (the engine's boot wiring).
     */
    public static void registerTicker(ScheduledTickSystem system, EngineWorld world,
                                       FallingBlockEntityManager fallingEntities) {
        ScheduledTickSystem.BlockTicker ticker =
                (tickWorld, pos, state, random) -> tryFall(tickWorld, pos, fallingEntities);
        system.registerTicker(SAND, ticker);
        system.registerTicker(RED_SAND, ticker);
        system.registerTicker(GRAVEL, ticker);
        for (EngineBlockType anvil : AnvilBlocks.familyBlocks()) {
            system.registerTicker(anvil.identifier(), ticker);
        }
        system.registerTicker(DragonEggBlocks.DRAGON_EGG, ticker);
    }
}
