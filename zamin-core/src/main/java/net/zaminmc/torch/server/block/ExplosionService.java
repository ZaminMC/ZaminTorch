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
 * The explosion: rays of force from the blast center, each weakened by every
 * block it crosses, removing what it still has strength to remove. The
 * historical 16&times;16&times;16 ray fan (step 0.3) with resistance costing —
 * blast resistance is modeled as hardness &times; 5, so bedrock and obsidian
 * shrug off a creeper while dirt and planks do not.
 *
 * <p>The service owns geometry only: it commits the destroyed blocks through
 * the world (each commit wakes the fluid and update systems — an explosion
 * hole floods or drops sand naturally) and reports what vanished, with the
 * per-block drop decision left to the caller (the historical
 * one-in-radius drop roll). Damage and knockback stay in the engine's combat
 * paths, which already carry the hurt-window and death semantics.</p>
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
        Set<Destroyed> destroyed = new LinkedHashSet<>();
        double cx = center.x() + 0.5;
        double cy = center.y() + 0.5;
        double cz = center.z() + 0.5;

        // The historical ray fan: 16 samples per axis across the unit cube,
        // each ray a random-strength cylinder of force marching outward.
        for (int ix = 0; ix < 16; ix++) {
            for (int iy = 0; iy < 16; iy++) {
                for (int iz = 0; iz < 16; iz++) {
                    if (ix == 0 && iy == 0 && iz == 0) {
                        continue; // degenerate zero ray
                    }
                    double dx = ix / 15.0 * 2.0 - 1.0;
                    double dy = iy / 15.0 * 2.0 - 1.0;
                    double dz = iz / 15.0 * 2.0 - 1.0;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double strength = radius * (0.7 + random.nextDouble() * 0.6);
                    double px = cx;
                    double py = cy;
                    double pz = cz;
                    while (strength > 0.0) {
                        BlockPosition cell = new BlockPosition(
                                (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
                        BlockType type = world.getBlock(cell);
                        if (!type.equals(worldAir())
                                && destroyed.add(new Destroyed(cell, type))) {
                            float resistance = blastResistance(type);
                            strength -= (resistance / 5.0f + 0.3f) * 0.3f;
                            if (strength > 0.0f) {
                                world.setBlock(cell, worldAir());
                            }
                        }
                        strength -= 0.22500001f; // the historical per-step cost
                        px += dx / length * 0.3;
                        py += dy / length * 0.3;
                        pz += dz / length * 0.3;
                    }
                }
            }
        }
        return new ArrayList<>(destroyed);
    }

    /** One removed block: where it stood and what it was (the drop roll reads it). */
    public record Destroyed(BlockPosition position, BlockType type) {
    }

    private BlockType worldAir() {
        // The world's air type, read through any cell of the sky: the
        // y-255 cell of an empty column. Cached per call — the flat world's
        // registry is frozen at boot, so the answer never changes.
        return world.getBlock(new BlockPosition(0, BlockPosition.MAX_Y, 0));
    }

    /**
     * The blast resistance model: hardness &times; 5 (the historical
     * proportion for the common blocks). Unbreakable blocks (negative
     * hardness) resist everything; fluids are skipped by callers — a blast
     * hole under water floods, it does not drain the ocean.
     */
    public static float blastResistance(BlockType type) {
        var behavior = net.zaminmc.torch.server.block.BlockBehaviorTable.of(type.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            return Float.MAX_VALUE; // unregistered or unbreakable: the blast yields
        }
        if (behavior.hardness() < 0) {
            return Float.MAX_VALUE;
        }
        return (float) (behavior.hardness() * 5.0);
    }
}
