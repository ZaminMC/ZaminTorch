package net.zaminmc.torch.server.item;

/**
 * The class of a tool, matching the dataset's per-material speed tables
 * (pickaxe accelerates "rock", axe accelerates "wood", shovel accelerates
 * "dirt"; shears cover leaves/wool/web, swords are combat-first with small
 * harvest accelerations). Community source: PrismarineJS/minecraft-data
 * pc/1.8 materials.json + items.json (MIT, see ADR-0002).
 */
public enum ToolClass {
    PICKAXE,
    AXE,
    SHOVEL,
    SWORD,
    SHEARS
}
