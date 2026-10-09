package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The material category of one path-scanned cell — the engine's translation
 * of the vanilla {@code Block.getMaterial} + {@code canWalkThrough} pairs
 * the WalkNodeEvaluator branches on. The mapping lives with the caller
 * ({@code PathBlocks}); the evaluator only sees these categories.
 *
 * <p>Vanilla arms the engine cannot reach are folded away and documented in
 * WalkNodeEvaluator: the engine's block registry registers no doors and no
 * trapdoors, so the wooden-door pass/open arms ({@code DoorBlock}) and the
 * trapdoor {@code -4} arm have no triggering block. Fire keeps its vanilla
 * meaning ({@code Material.FIRE} is an AirMaterial — {@code blocksMovement}
 * false — so {@code canWalkThrough} passes and mobs PATH THROUGH burning
 * cells, exactly vanilla) and lands in WALK_THROUGH here.</p>
 */
public enum CellMaterial {
    /** Air — the scan's nothing-here. */
    AIR,
    /** Water (a LiquidMaterial, replaceable, the swimmable flag). */
    WATER,
    /** Lava (a LiquidMaterial; lethal to non-fire-immune mobs, the -2 arm). */
    LAVA,
    /** Fence / fence gate / wall — the 1.5-tall unpathable barrier (-3). */
    FENCE,
    /** Rails — pathed around unless the mob already stands on rails (-3). */
    RAIL,
    /**
     * Non-colliding, non-fluid, non-fire — the vanilla canWalkThrough set
     * (flora, torches, sugar cane): the scan walks straight past them.
     */
    WALK_THROUGH,
    /** Everything collidable (the 0 arm: the cell is blocked). */
    SOLID
}
