package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.Random;

/**
 * The dragon egg family (Slice 55 — DragonEggBlock 1:1): the block 122
 * ({@code new DragonEggBlock().setStrength(3.0F).setBlastResistance(
 * 15.0F).setSounds(STONE_SOUNDS).setLight(0.125F)} — the emission
 * {@code (int)(0.125 * 15)} = 1) with its 1/16-inset box
 * ({@code setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 1.0F, 0.9375F)}), the
 * OWN fall schedule ({@code getTickRate} 5 — not the sand family's 2), and
 * the teleport walk every click arms (use and startMining alike —
 * {@code tryTeleport}: 1000 attempts at the {@code nextInt(16) -
 * nextInt(16)} / {@code nextInt(8) - nextInt(8)} / {@code nextInt(16) -
 * nextInt(16)} offsets, the target an AIR cell; a success moves the egg
 * (the portal particles are the client's own rendering over the block
 * change). An out-of-band target (below the world) is the reference's own
 * vanish arm — the move "succeeds", the placement fails, the source
 * removes).
 *
 * <p>No item form ({@code getPickItem} null — the reference registers no
 * dragon egg item; the landing drop consult finds nothing, so the egg
 * never drops as a stack either).</p>
 */
public final class DragonEggBlocks {

    private DragonEggBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 122;

    /** The egg's own fall schedule (DragonEggBlock.getTickRate — 5, not the sand's 2). */
    public static final int FALL_CHECK_DELAY = 5;

    /** The egg block (the single meta-0 state). */
    public static final Identifier DRAGON_EGG = Identifier.parse("minecraft:dragon_egg");

    /** The engine's dragon egg type (the single state). */
    public static EngineBlockType dragonEgg() {
        return new EngineBlockType(DRAGON_EGG, "Dragon Egg");
    }

    /** @return whether the identifier is the dragon egg. */
    public static boolean isDragonEgg(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && id.value().equals("dragon_egg");
    }

    /** The world surface the walks need (tick-thread context). */
    public interface EggWorld {
        BlockType getBlock(BlockPosition at);

        void setBlock(BlockPosition at, BlockType type);

        /** The world's own air type (the AIR gate's read). */
        BlockType air();
    }

    /**
     * The teleport walk (DragonEggBlock.tryTeleport, 1:1): 1000 attempts
     * at the reference's own offsets (nextInt(16) - nextInt(16) over X/Z,
     * nextInt(8) - nextInt(8) over Y), the target an AIR cell. A success
     * moves the egg; an out-of-band target (below the world, above the
     * ceiling) is the reference's vanish arm — the move "succeeds", the
     * placement fails, the source removes. The walk gives up silently
     * after the thousand.
     *
     * @return whether the egg moved (or vanished).
     */
    public static boolean tryTeleport(EggWorld world, Random random, BlockPosition pos) {
        BlockType egg = world.getBlock(pos);
        if (!isDragonEgg(egg.identifier())) {
            return false;
        }
        BlockType air = world.air();
        for (int attempt = 0; attempt < 1000; attempt++) {
            int dx = random.nextInt(16) - random.nextInt(16);
            int dy = random.nextInt(8) - random.nextInt(8);
            int dz = random.nextInt(16) - random.nextInt(16);
            BlockPosition target = pos.offset(dx, dy, dz);
            boolean inBand = target.y() >= 0 && target.y() < 256;
            // The reference's own AIR gate (the out-of-band cells read air
            // in the 1.8 world — those arms are the vanish band).
            if (inBand && !world.getBlock(target).equals(air)) {
                continue;
            }
            if (inBand) {
                world.setBlock(target, egg);
            }
            // The source removes in both arms (the vanish band included —
            // the reference's setBlockState-fails-below-the-world read).
            world.setBlock(pos, air);
            return true;
        }
        return false;
    }
}
