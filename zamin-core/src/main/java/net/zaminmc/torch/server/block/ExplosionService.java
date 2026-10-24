package net.zaminmc.torch.server.block;

import net.zaminmc.torch.World;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The explosion: the reference's own two-phase walk
 * (world/explosion/Explosion.java 1:1). Phase one — {@code damageEntities}
 * (lines 60-105): the boundary-cell ray fan over the 16&times;16&times;16
 * direction cube, each ray a random-strength march paying every block it
 * crosses the {@code (resistance + 0.3) * 0.3} charge, COLLECTING the
 * damaged cells — nothing is written. Phase two — {@code damageBlocks}
 * (lines 156-176): the collected cells commit to air. The two phases are
 * separate because every ray of the fan must read the world as it stood
 * before the blast: a fan that destroys as it casts lets an earlier ray's
 * hole pay nothing for a later ray crossing it, carving deeper than the
 * reference ever does.
 *
 * <p>The fan fires the BOUNDARY cells alone (lines 64-66: {@code j == 0 ||
 * j == 15 || k == 0 || k == 15 || l == 0 || l == 15}) — 1352 rays, the
 * cube's shell. The interior directions the 4096-cell fan once carried are
 * the retired engine's own shape, not the reference's.</p>
 *
 * <p>Blast resistance is the table's own READ value (the reference's
 * {@code Entity.getBlastResistance} consult): bedrock and obsidian shrug
 * off a creeper while dirt and planks do not. The per-block drop decision
 * stays with the caller (the reference's {@code dropItems} chance roll —
 * the {@code 1.0F / power} form). Damage and knockback stay in the
 * engine's combat paths, which already carry the hurt-window and death
 * semantics.</p>
 */
public final class ExplosionService {

    /** The world an explosion commits into (tick thread). */
    public interface World {
        BlockType getBlock(BlockPosition position);

        boolean setBlock(BlockPosition position, BlockType type);
    }

    private final World world;
    private final Random random;

    public ExplosionService(World world, Random random) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Detonates at {@code center} with the given radius. Returns the
     * destroyed blocks in destruction order, each carrying the type it held
     * (the caller's drop rolls read the removed kind). Tick-thread context;
     * every destruction commits through the world listener path.
     */
    public List<Destroyed> detonate(BlockPosition center, float radius) {
        return detonate(center, radius, BlastSource.PLAIN);
    }

    /**
     * The block-center origin form (the historical callers' shape): the
     * exact origin is the center cell's middle.
     */
    public List<Destroyed> detonate(BlockPosition center, float radius, BlastSource source) {
        return detonate(center.x() + 0.5, center.y() + 0.5, center.z() + 0.5,
                radius, source);
    }

    /**
     * Detonates from an EXACT origin (the reference's own {@code x, y, z}
     * carry — the primed TNT's {@code y + height/16} hop, the wither's
     * eye-height rise): collect, then commit, then hand the destroyed
     * cells back.
     */
    public List<Destroyed> detonate(double cx, double cy, double cz,
                                    float radius, BlastSource source) {
        List<Destroyed> damaged = collectDamaged(cx, cy, cz, radius, source);
        commitDamaged(damaged);
        return damaged;
    }

    /**
     * Phase one (Explosion.damageEntities lines 60-105): the boundary ray
     * fan. NO writes — the collected cells ride back to the caller, so the
     * wound walk can read the world as it stood (the reference computes the
     * entity-density rays over the pre-blast world, then destroys in the
     * second phase).
     */
    public List<Destroyed> collectDamaged(double cx, double cy, double cz,
                                          float radius, BlastSource source) {
        Set<Destroyed> damaged = new LinkedHashSet<>();

        // The reference's own loop shape (lines 60-66): the boundary cells
        // of the 16x16x16 direction cube alone — the interior cell fires
        // nothing.
        for (int ix = 0; ix < 16; ix++) {
            for (int iy = 0; iy < 16; iy++) {
                for (int iz = 0; iz < 16; iz++) {
                    if (ix != 0 && ix != 15 && iy != 0 && iy != 15
                            && iz != 0 && iz != 15) {
                        continue; // interior direction: the reference never fires it
                    }
                    // j / 15.0F * 2.0F - 1.0F — the float-computed axis
                    // component (the reference's own arithmetic, kept
                    // bit-for-bit: the float division rounds before the
                    // double carry).
                    double d0 = ix / 15.0f * 2.0f - 1.0f;
                    double d1 = iy / 15.0f * 2.0f - 1.0f;
                    double d2 = iz / 15.0f * 2.0f - 1.0f;
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    d0 /= d3;
                    d1 /= d3;
                    d2 /= d3;
                    // float f = power * (0.7F + random.nextFloat() * 0.6F)
                    // — the reference's own per-ray strength draw (line 74).
                    float f = radius * (0.7f + random.nextFloat() * 0.6f);
                    double d4 = cx;
                    double d6 = cy;
                    double d8 = cz;

                    while (f > 0.0f) {
                        if (d6 < 0.0 || d6 > 255.0) {
                            break; // the world's vertical bounds end the ray
                        }
                        BlockPosition cell = new BlockPosition(
                                (int) Math.floor(d4), (int) Math.floor(d6),
                                (int) Math.floor(d8));
                        BlockType type = world.getBlock(cell);
                        if (!type.equals(worldAir())) {
                            // The resistance charge (lines 86-91):
                            // f -= (f2 + 0.3F) * 0.3F over the table's own
                            // READ value — stone 6, not 1.5.
                            float f2 = source.blastResistance(world, cell, type);
                            f -= (f2 + 0.3f) * 0.3f;
                        }
                        // The destroy gate (line 93): the strength
                        // surviving AND the source's own veto — a cell the
                        // ray could not afford never joins the damaged
                        // list (the reference collects only what it can
                        // pay).
                        if (f > 0.0f && (source.canExplodeBlock(world, cell, type))) {
                            damaged.add(new Destroyed(cell, type));
                        }
                        f -= 0.22500001f; // the per-step cost (line 103)
                        d4 += d0 * 0.3f;
                        d6 += d1 * 0.3f;
                        d8 += d2 * 0.3f;
                    }
                }
            }
        }
        return new ArrayList<>(damaged);
    }

    /**
     * Phase two (Explosion.damageBlocks lines 156-176): the collected cells
     * commit to air. Runs AFTER the whole fan — the writes never feed back
     * into another ray's resistance reads. The AIR cells the fan collected
     * (the reference's {@code set.add} rides outside the material check —
     * the packet's own offsets carry them) are no-ops here exactly like the
     * reference's own {@code material != AIR} gate: no write, no drop, no
     * listener fan.
     */
    public void commitDamaged(List<Destroyed> damaged) {
        BlockType air = worldAir();
        for (Destroyed cell : damaged) {
            if (!cell.type().equals(air)) {
                world.setBlock(cell.position(), air);
            }
        }
    }

    /** One removed block: where it stood and what it was (the drop roll reads it). */
    public record Destroyed(BlockPosition position, BlockType type) {
    }

    /**
     * The detonation's source model (the reference's Entity consults,
     * Explosion.damageEntities lines 87-93): the plain default reads the
     * block table (the Entity default, line 1732 —
     * {@code state.getBlock().getBlastResistance(this)}); the wither's
     * charged skull caps destroyable blocks at 0.8 (WitherSkullEntity lines
     * 43-51 over WitherEntity.canDestroy 320-326); the primed TNT cart
     * reads rail cells as free but never destroys them (TntMinecartEntity
     * lines 142-152).
     */
    public interface BlastSource {
        /** The per-crossing resistance the ray pays for this block. */
        float blastResistance(World world, BlockPosition pos, BlockType type);

        /** Whether this source's blast may destroy the block at all. */
        default boolean canExplodeBlock(World world, BlockPosition pos, BlockType type) {
            return true; // the Entity default (line 1739)
        }

        /** The plain source: the block table's own read, no veto. */
        BlastSource PLAIN = (world, pos, type) -> ExplosionService.blastResistance(type);
    }

    private BlockType worldAir() {
        // The world's air type, read through any cell of the sky: the
        // y-255 cell of an empty column. Cached per call — the flat world's
        // registry is frozen at boot, so the answer never changes.
        return world.getBlock(new BlockPosition(0, BlockPosition.MAX_Y, 0));
    }

    /**
     * The blast resistance model: the table's own READ value first (the
     * reference's Block.getBlastResistance read family); the unmapped fall
     * through to the standing convention. Unbreakable blocks (negative
     * hardness) resist everything; fluids are skipped by callers — a blast
     * hole under water floods, it does not drain the ocean.
     */
    public static float blastResistance(BlockType type) {
        // THE TABLE FIRST (the reference's own values — Block.getBlastResistance's
        // read family): the unmapped fall through to the standing convention.
        Float table = BlastResistanceTable.read(type.identifier());
        if (table != null) {
            return table;
        }
        var behavior = net.zaminmc.torch.server.block.BlockBehaviorTable.of(type.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            return Float.MAX_VALUE; // unregistered or unbreakable: the blast yields
        }
        if (behavior.hardness() < 0) {
            return Float.MAX_VALUE;
        }
        return (float) (behavior.hardness() * 5.0); // the retired approximation's last resort
    }
}
