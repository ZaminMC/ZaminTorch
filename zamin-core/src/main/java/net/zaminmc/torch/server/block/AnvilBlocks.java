package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The anvil family (Slice 53 — AnvilBlock 1:1): the block 145 over the
 * reference's {@code AnvilBlock} — {@code AnvilBlock extends FallingBlock(
 * Material.ANVIL)} at {@code setStrength(5.0F).setBlastResistance(2000.0F)}
 * with the ANVIL_SOUNDS; the twelve states over the reference's exact band
 * ({@code getStateFromMetadata}: the FACING horizontal id whole in the low
 * pair, {@code DAMAGE << 2} in the high pair — the reference's
 * {@code Direction.byIdHorizontal(metadata & 3)} band S=0 W=1 N=2 E=3).
 *
 * <p>The anvil falls like the sand family ({@code FallingBlock} — the
 * two-tick schedule) and hurts what it lands on: the landing walk is the
 * reference's {@code FallingBlockEntity.takeFallDamage} —
 * {@code ceil(distance - 1)} over the accumulated fall, every body in the
 * 0.98 box takes {@code min(floor(i * 2.0F), 40)}, and the landing's own
 * progression roll {@code random.nextFloat() < 0.05F + i * 0.05} walks the
 * DAMAGE field forward (past 2 the anvil breaks — no block placed, no item
 * dropped, the {@code brokeAnvil} path).</p>
 *
 * <p>The family item ({@code Item.java}'s own BlockItem over 145) carries
 * the DAMAGE id in its damage field — the three forms the creative menu
 * lists ({@code addToCreativeMenu}'s damage 0/1/2 stacks); there is no
 * crafting row for the anvil in 1.8.8 (the reference registers none).</p>
 */
public final class AnvilBlocks {

    private AnvilBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 145;

    /**
     * The FACING band (the reference's horizontal ids: S=0 W=1 N=2 E=3 —
     * the bare name carries the south form like the gate/ender-chest
     * precedent).
     */
    static final List<String> FACINGS = List.of("", "_west", "_north", "_east");

    /**
     * The DAMAGE band ({@code AnvilBlock.DAMAGE}, 0-2): the prefix names —
     * the plain anvil, the chipped form, the damaged form.
     */
    static final List<String> DAMAGES = List.of("anvil", "anvil_chipped", "anvil_damaged");

    /** The display names (the reference's own three forms). */
    static final List<String> DISPLAY = List.of(
            "Anvil", "Anvil (Chipped)", "Anvil (Damaged)");

    /** The family item (Item.java's own BlockItem row over 145). */
    public static final Identifier FAMILY_ITEM = Identifier.parse("minecraft:anvil");

    /** One state's identifier over the bands. */
    public static Identifier stateId(int facing, int damage) {
        return Identifier.parse("minecraft:" + DAMAGES.get(damage) + FACINGS.get(facing));
    }

    /** One state (the FACING id | DAMAGE << 2 in the nibble). */
    public static EngineBlockType state(int facing, int damage) {
        return new EngineBlockType(stateId(facing, damage),
                DISPLAY.get(damage), facing | damage << 2);
    }

    /** Every registration this family owns (the twelve states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int damage = 0; damage < DAMAGES.size(); damage++) {
            for (int facing = 0; facing < FACINGS.size(); facing++) {
                blocks.add(state(facing, damage));
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the identifier is one of the twelve anvil states. */
    public static boolean isAnvil(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String damage : DAMAGES) {
            for (String facing : FACINGS) {
                if (value.equals(damage + facing)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** @return the state's DAMAGE id (0-2), or -1. */
    public static int damageOf(Identifier id) {
        if (id == null) {
            return -1;
        }
        String value = id.value();
        for (int d = 0; d < DAMAGES.size(); d++) {
            for (String facing : FACINGS) {
                if (value.equals(DAMAGES.get(d) + facing)) {
                    return d;
                }
            }
        }
        return -1;
    }

    /** @return the state's FACING id (0 S, 1 W, 2 N, 3 E), or -1. */
    public static int facingOf(Identifier id) {
        if (id == null) {
            return -1;
        }
        String value = id.value();
        for (String damage : DAMAGES) {
            for (int f = 0; f < FACINGS.size(); f++) {
                if (value.equals(damage + FACINGS.get(f))) {
                    return f;
                }
            }
        }
        return -1;
    }

    /**
     * The DAMAGE progression (the reference's takeFallDamage roll passing
     * the damage field forward): {@code damage + 1}, or -1 past the band
     * (the brokeAnvil path — the anvil is destroyed, nothing placed, the
     * reference's {@code ++j > 2} arm).
     */
    public static int progressedDamage(int damage) {
        int next = damage + 1;
        return next > 2 ? -1 : next;
    }

    /**
     * The placement consult's DAMAGE read (the item's damage field is the
     * DAMAGE id — the reference's {@code getPlacementState} reads
     * {@code metadata >> 2} from the stack): out-of-band damage clamps to
     * the plain form exactly like the creative menu's own three.
     */
    public static int placementDamageOf(int itemDamage) {
        return itemDamage < 0 || itemDamage > 2 ? 0 : itemDamage;
    }

    /**
     * The drop item's damage (the reference's {@code getDropItemMetadata}):
     * the state's DAMAGE id — the dropped stack is the same damaged form.
     */
    public static int dropDamageOf(Identifier id) {
        return damageOf(id);
    }
}
