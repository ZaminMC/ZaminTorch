package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;

/**
 * The named blast SOURCE models (the reference's Entity overrides over
 * Explosion.damageEntities' consults, lines 87-93): the wither's charged
 * skull caps destroyable blocks at 0.8 (WitherSkullEntity lines 43-51 over
 * WitherEntity.canDestroy 320-326); the primed TNT cart spares rails
 * (TntMinecartEntity lines 142-152 — a rail cell reads free to the rays
 * but is never destroyed).
 *
 * <p>Everything else reads PLAIN (the Entity default, line 1732 — the
 * block table's own value).</p>
 */
public final class BlastSources {

    private BlastSources() {
    }

    /**
     * The wither's canDestroy (WitherEntity lines 320-326): the
     * unbreakable band excepted five — the shared list the sweep and the
     * charged skull's source both consult.
     */
    public static boolean witherCanDestroy(BlockType type) {
        String id = type.identifier().toString();
        return !id.equals("minecraft:bedrock")
                && !id.equals("minecraft:end_portal")
                && !id.equals("minecraft:end_portal_frame")
                && !id.equals("minecraft:command_block")
                && !id.equals("minecraft:barrier");
    }

    /**
     * The wither's CHARGED skull (WitherSkullEntity lines 43-51): a
     * destroyable block's resistance capped at 0.8 — the blue skull breaks
     * through everything but the unbreakable band, which reads its own
     * (enormous) value.
     */
    public static final ExplosionService.BlastSource CHARGED_SKULL =
            (world, pos, type) -> witherCanDestroy(type)
                    ? Math.min(0.8f, ExplosionService.blastResistance(type))
                    : ExplosionService.blastResistance(type);

    /**
     * The primed TNT cart (TntMinecartEntity lines 142-152): a rail cell
     * (the cell itself a rail, or the cell above one) reads FREE to the
     * rays but is never destroyed — the rail-sparing rule.
     */
    public static final ExplosionService.BlastSource PRIMED_CART =
            new ExplosionService.BlastSource() {
                @Override
                public float blastResistance(ExplosionService.World world,
                                              BlockPosition pos, BlockType type) {
                    return isRailCell(world, pos) ? 0.0f : ExplosionService.blastResistance(type);
                }

                @Override
                public boolean canExplodeBlock(ExplosionService.World world,
                                               BlockPosition pos, BlockType type) {
                    return !isRailCell(world, pos);
                }

                /** AbstractRailBlock.isRail(state) || isRail(world, pos.up()). */
                private boolean isRailCell(ExplosionService.World world, BlockPosition pos) {
                    return isRailBlock(world.getBlock(pos))
                            || isRailBlock(world.getBlock(new BlockPosition(
                                    pos.x(), pos.y() + 1, pos.z())));
                }

                /** The four rail families over their states (the reference's
                 * AbstractRailBlock.isRail — the block, not the state). */
                private boolean isRailBlock(BlockType type) {
                    String v = type.identifier().value();
                    return v.equals("rail") || v.startsWith("rail_")
                            || v.equals("golden_rail") || v.startsWith("golden_rail_")
                            || v.equals("detector_rail") || v.startsWith("detector_rail_")
                            || v.equals("activator_rail") || v.startsWith("activator_rail_");
                }
            };
}
