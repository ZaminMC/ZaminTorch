package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE WR — THE WIRE FAMILY-RESOLUTION LAW (the standing
 * CreativeMenuWire flake's root closed whole): the reverse maps must be
 * a PURE FUNCTION of the table's content. The JDK's immutable maps
 * iterate in a SALT-randomized order that re-seeds every JVM run, so the
 * retired blind last-writer-wins reverse resolved every shared legacy id
 * to a RANDOM family member per boot — legacy 3 answered dirt on one
 * boot, coarse_dirt on the next, podzol on a third, and a creative dirt
 * pick that resolved to podzol found no item form and VANISHED (the
 * owner's own "take something from creative to inventory it disappears"
 * report; the anvil import read a different world than the wire wrote).
 *
 * <p>THE LAW, 1:1 with the reference's own API split: the plain reverse
 * ({@code identifierOf(id)}) answers the family's BASE form — exactly
 * what {@code Block.getBlockById}/{@code Item.getItemById} answer — and
 * the damage-aware reverse ({@code identifierOf(id, meta)}) answers the
 * reference's own {@code getStateFromMeta(meta)} state, with the meta-0
 * key landing the base state for every family (never falling through to
 * the plain reverse).</p>
 */
class WireFamilyResolutionParityTest {

    private static Identifier id(String value) {
        return Identifier.parse("minecraft:" + value);
    }

    /** THE FLAKE'S OWN LAW: the dirt family resolves dirt, every boot. */
    @Test
    void theDirtFamilyResolvesItsBaseDeterministically() {
        assertEquals(Optional.of(id("dirt")), LegacyBlockIds.identifierOf(3),
                "the plain reverse answers the family base (Block.getBlockById(3))");
        assertEquals(Optional.of(id("dirt")), LegacyBlockIds.identifierOf(3, 0),
                "the meta-0 consult answers the base state");
        assertEquals(Optional.of(id("coarse_dirt")), LegacyBlockIds.identifierOf(3, 1),
                "DirtBlock.VARIANT coarse 1");
        assertEquals(Optional.of(id("podzol")), LegacyBlockIds.identifierOf(3, 2),
                "DirtBlock.VARIANT podzol 2");
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems
                        .lookup(id("dirt")).isPresent(),
                "the resolved base carries an item form (the creative pick lands)");
    }

    /** The stone family (the same class over legacy 1). */
    @Test
    void theStoneFamilyResolvesItsBaseDeterministically() {
        assertEquals(Optional.of(id("stone")), LegacyBlockIds.identifierOf(1, 0));
        assertEquals(Optional.of(id("granite")), LegacyBlockIds.identifierOf(1, 1));
        assertEquals(Optional.of(id("diorite")), LegacyBlockIds.identifierOf(1, 3));
        assertEquals(Optional.of(id("andesite")), LegacyBlockIds.identifierOf(1, 5));
    }

    /**
     * The wood families' registry forms win the plain reverse (the bare
     * dataset names planks/log/leaves ride as wire aliases; the engine's
     * block registry carries the oak-prefixed forms).
     */
    @Test
    void theWoodFamiliesAnswerTheRegistryForms() {
        assertEquals(Optional.of(id("oak_planks")), LegacyBlockIds.identifierOf(5, 0));
        assertEquals(Optional.of(id("oak_sapling")), LegacyBlockIds.identifierOf(6, 0));
        assertEquals(Optional.of(id("oak_log")), LegacyBlockIds.identifierOf(17, 0));
        assertEquals(Optional.of(id("oak_leaves")), LegacyBlockIds.identifierOf(18, 0));
        assertEquals(Optional.of(id("birch_planks")), LegacyBlockIds.identifierOf(5, 2));
        assertEquals(Optional.of(id("spruce_log")), LegacyBlockIds.identifierOf(17, 1));
        assertEquals(Optional.of(id("acacia_leaves")), LegacyBlockIds.identifierOf(161, 0));
        assertEquals(Optional.of(id("acacia_log")), LegacyBlockIds.identifierOf(162, 0));
    }

    /**
     * THE SANDSTONE BAND REPAIRED (SandstoneBlock.Type: DEFAULT(0),
     * CHISELED(1), SMOOTH(2)): the retired chiseled row carried 3 — the
     * STONE-BRICKS band copied onto the family — and the smooth row was
     * missing whole.
     */
    @Test
    void theSandstoneBandMatchesTheReference() {
        assertEquals(1, LegacyBlockIds.metadataOf(id("chiseled_sandstone")),
                "SandstoneBlock.Type CHISELED(1)");
        assertEquals(2, LegacyBlockIds.metadataOf(id("smooth_sandstone")),
                "SandstoneBlock.Type SMOOTH(2)");
        assertEquals(0, LegacyBlockIds.metadataOf(id("sandstone")));
        assertEquals(Optional.of(id("chiseled_sandstone")),
                LegacyBlockIds.identifierOf(24, 1));
        assertEquals(Optional.of(id("smooth_sandstone")),
                LegacyBlockIds.identifierOf(24, 2));
        assertEquals(Optional.of(id("sandstone")), LegacyBlockIds.identifierOf(24, 0));
        // The engine's own nibble and the anvil import's row agree.
        assertEquals(1, net.zaminmc.torch.server.block.BuiltinBlocks
                .CHISELED_SANDSTONE.legacyMetadata());
        assertEquals(Optional.of(id("chiseled_sandstone")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(24, 1));
        // The red sandstone's own band was already right — the pin keeps it.
        assertEquals(1, LegacyBlockIds.metadataOf(id("chiseled_red_sandstone")));
        assertEquals(2, LegacyBlockIds.metadataOf(id("smooth_red_sandstone")));
    }

    /**
     * THE CROP AGE BANDS (CropBlock: AGE 0..7 rides the nibble whole):
     * the engine's model carried the stages' nibbles all along (the anvil
     * import derived its rows from them) but the wire table answered 0
     * for every stage — crops never visually grew on the real client.
     */
    @Test
    void theCropStagesCarryTheirAgeOnTheWire() {
        for (int stage = 0; stage <= 7; stage++) {
            assertEquals(stage, LegacyBlockIds.metadataOf(id("wheat_stage" + stage)),
                    "wheat stage " + stage);
            assertEquals(stage, LegacyBlockIds.metadataOf(id("carrots_stage" + stage)),
                    "carrots stage " + stage);
            assertEquals(stage, LegacyBlockIds.metadataOf(id("potatoes_stage" + stage)),
                    "potatoes stage " + stage);
        }
        assertEquals(Optional.of(id("wheat_stage5")), LegacyBlockIds.identifierOf(59, 5));
        assertEquals(Optional.of(id("carrots_stage7")), LegacyBlockIds.identifierOf(141, 7));
        assertEquals(Optional.of(id("potatoes_stage0")), LegacyBlockIds.identifierOf(142, 0));
        // The stems and the wart carried their bands already — the pin
        // keeps them (the pumpkin/melon stems ride AGE 0..7, the wart 0..3).
        assertEquals(6, LegacyBlockIds.metadataOf(id("pumpkin_stem_stage6")));
        assertEquals(3, LegacyBlockIds.metadataOf(id("nether_wart_stage3")));
    }

    /**
     * THE SKULL FACING BAND (SkullBlock 1:1: facing | nodrop << 3, the
     * NODROP bit server-only): 1 the floor mount, 2-5 the wall facings,
     * 0 the ceiling — the engine's own SkullBlocks law, the wire table
     * and the anvil import now agreeing.
     */
    @Test
    void theSkullFacingsCarryTheirBand() {
        assertEquals(1, LegacyBlockIds.metadataOf(id("skull")));
        assertEquals(0, LegacyBlockIds.metadataOf(id("skull_down")));
        assertEquals(2, LegacyBlockIds.metadataOf(id("skull_north")));
        assertEquals(3, LegacyBlockIds.metadataOf(id("skull_south")));
        assertEquals(4, LegacyBlockIds.metadataOf(id("skull_west")));
        assertEquals(5, LegacyBlockIds.metadataOf(id("skull_east")));
        assertEquals(Optional.of(id("skull_east")), LegacyBlockIds.identifierOf(144, 5));
        assertEquals(Optional.of(id("skull")), LegacyBlockIds.identifierOf(144, 1));
    }

    /**
     * THE DYE ITEM'S DAMAGE DOMAIN (ItemDye: the ink sac "dye" at 0, the
     * lapis at 4, the bone meal at 15): a creative pick of lapis fell
     * through to the plain reverse and landed as a RANDOM one of the
     * three per boot.
     */
    @Test
    void theDyeItemResolvesItsDamageDomain() {
        assertEquals(Optional.of(id("dye")), LegacyBlockIds.identifierOf(351, 0));
        assertEquals(Optional.of(id("lapis")), LegacyBlockIds.identifierOf(351, 4));
        assertEquals(Optional.of(id("bone_meal")), LegacyBlockIds.identifierOf(351, 15));
    }

    /**
     * THE META-0 STATE EXCEPTIONS: the families whose meta-0 state is a
     * registered state rather than the bare item form — the prismarine's
     * ROUGH(0) (the reference's PrismarineBlock), the same answer the
     * anvil import table carries.
     */
    @Test
    void theMetaZeroStatesAnswerTheRegisteredForms() {
        assertEquals(Optional.of(id("prismarine_rough")),
                LegacyBlockIds.identifierOf(168, 0),
                "PrismarineBlock ROUGH(0) is the meta-0 state");
        assertEquals(Optional.of(id("prismarine")), LegacyBlockIds.identifierOf(168),
                "the bare item form answers the plain reverse");
        assertEquals(Optional.of(id("prismarine_bricks")),
                LegacyBlockIds.identifierOf(168, 1));
        assertEquals(Optional.of(id("prismarine_dark")),
                LegacyBlockIds.identifierOf(168, 2));
        // The double-plant family: 175:0 is the sunflower (the reference's
        // Variant id 0), the plain reverse answers the item form.
        assertEquals(Optional.of(id("sunflower")), LegacyBlockIds.identifierOf(175, 0));
        assertEquals(Optional.of(id("double_plant")), LegacyBlockIds.identifierOf(175));
        // The rail family: 66:0 is the north-south flat rail (the bare
        // form), the east-west its meta 1.
        assertEquals(Optional.of(id("rail")), LegacyBlockIds.identifierOf(66, 0));
        assertEquals(Optional.of(id("rail_ew")), LegacyBlockIds.identifierOf(66, 1));
    }

    /**
     * THE NO-STRANGER SWEEP (the whole-registry net): for every block the
     * engine registers, the wire's own nibble round-trips the identifier
     * back to a member of the SAME family — the damage-aware reverse can
     * never leak a resolution across families, whatever the boot's salt.
     */
    @Test
    void theWholeRegistryRoundTripsWithinItsFamily() {
        var builder = new BlockRegistryBuilder();
        builder = net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.block.FluidBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.beacon.BeaconSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.chest.EnderChestBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.cauldron.CauldronBlocks.registerAll(builder);
        var registry = builder.freeze();

        // The family index: every identifier sharing a legacy id.
        Map<Integer, java.util.Set<String>> familyMembers = new HashMap<>();
        Map<String, Integer> identifierToLegacy = new HashMap<>();
        for (var type : registry.all()) {
            Optional<Integer> legacy = LegacyBlockIds.legacyId(type.identifier());
            if (legacy.isEmpty()) {
                continue; // the wire cannot spell it (the oak sapling's known gap)
            }
            identifierToLegacy.put(type.identifier().value(), legacy.get());
            familyMembers.computeIfAbsent(legacy.get(), k -> new java.util.HashSet<>())
                    .add(type.identifier().value());
        }
        for (var type : registry.all()) {
            Optional<Integer> legacy = LegacyBlockIds.legacyId(type.identifier());
            if (legacy.isEmpty()) {
                continue;
            }
            int engineMeta = type instanceof EngineBlockType engineType
                    ? engineType.legacyMetadata() : 0;
            int wireMeta = LegacyBlockIds.metadataOf(type.identifier());
            int meta = engineMeta != 0 ? engineMeta : wireMeta;
            Optional<Identifier> resolved = LegacyBlockIds.identifierOf(legacy.get(), meta);
            assertTrue(resolved.isPresent(), "the wire state (" + legacy.get() + "," + meta
                    + ") of " + type.identifier().value() + " resolves");
            assertEquals(legacy.get(),
                    identifierToLegacy.getOrDefault(resolved.get().value(), legacy.get()),
                    "the resolution of (" + legacy.get() + "," + meta + ") stays in the family");
        }
    }
}
