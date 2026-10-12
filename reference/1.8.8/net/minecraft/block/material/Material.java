package net.minecraft.block.material;

public class Material {
    public static final Material AIR = new AirMaterial(MapColor.AIR);
    public static final Material GRASS = new Material(MapColor.GRASS);
    public static final Material DIRT = new Material(MapColor.DIRT);
    public static final Material WOOD = new Material(MapColor.WOOD).setFlammable();
    public static final Material STONE = new Material(MapColor.STONE).setRequiresTool();
    public static final Material IRON = new Material(MapColor.IRON).setRequiresTool();
    public static final Material ANVIL = new Material(MapColor.IRON).setRequiresTool().setBlocksPistonMove();
    public static final Material WATER = new LiquidMaterial(MapColor.WATER).setDestroyOnPistonMove();
    public static final Material LAVA = new LiquidMaterial(MapColor.LAVA).setDestroyOnPistonMove();
    public static final Material LEAVES = new Material(MapColor.FOLIAGE).setFlammable().setTranslucent().setDestroyOnPistonMove();
    public static final Material PLANT = new PlantMaterial(MapColor.FOLIAGE).setDestroyOnPistonMove();
    public static final Material REPLACEABLE_PLANT = new PlantMaterial(MapColor.FOLIAGE).setFlammable().setDestroyOnPistonMove().setReplaceable();
    public static final Material SPONGE = new Material(MapColor.YELLOW);
    public static final Material WOOL = new Material(MapColor.WEB).setFlammable();
    public static final Material FIRE = new AirMaterial(MapColor.AIR).setDestroyOnPistonMove();
    public static final Material SAND = new Material(MapColor.SAND);
    public static final Material DECORATION = new PlantMaterial(MapColor.AIR).setDestroyOnPistonMove();
    public static final Material CARPET = new PlantMaterial(MapColor.WEB).setFlammable();
    public static final Material GLASS = new Material(MapColor.AIR).setTranslucent().setCanBeBrokenInAdventureMode();
    public static final Material REDSTONE_LAMP = new Material(MapColor.AIR).setCanBeBrokenInAdventureMode();
    public static final Material TNT = new Material(MapColor.LAVA).setFlammable().setTranslucent();
    public static final Material CORAL = new Material(MapColor.FOLIAGE).setDestroyOnPistonMove();
    public static final Material ICE = new Material(MapColor.ICE).setTranslucent().setCanBeBrokenInAdventureMode();
    public static final Material PACKED_ICE = new Material(MapColor.ICE).setCanBeBrokenInAdventureMode();
    public static final Material SNOW_LAYER = new PlantMaterial(MapColor.WHITE).setReplaceable().setTranslucent().setRequiresTool().setDestroyOnPistonMove();
    public static final Material SNOW = new Material(MapColor.WHITE).setRequiresTool();
    public static final Material CACTUS = new Material(MapColor.FOLIAGE).setTranslucent().setDestroyOnPistonMove();
    public static final Material CLAY = new Material(MapColor.CLAY);
    public static final Material PUMPKIN = new Material(MapColor.FOLIAGE).setDestroyOnPistonMove();
    public static final Material EGG = new Material(MapColor.FOLIAGE).setDestroyOnPistonMove();
    public static final Material PORTAL = new PortalMaterial(MapColor.AIR).setBlocksPistonMove();
    public static final Material CAKE = new Material(MapColor.AIR).setDestroyOnPistonMove();
    public static final Material COBWEB = (new Material(MapColor.WEB) {
        @Override
        public boolean blocksMovement() {
            return false;
        }
    }).setRequiresTool().setDestroyOnPistonMove();
    public static final Material PISTON = new Material(MapColor.STONE).setBlocksPistonMove();
    public static final Material BARRIER = new Material(MapColor.AIR).setRequiresTool().setBlocksPistonMove();
    private boolean flammable;
    private boolean replaceable;
    private boolean translucent;
    private final MapColor color;
    private boolean toolNotRequired = true;
    private int pistonMoveBehavior;
    private boolean canBeBrokenInAdventureMode;

    public Material(MapColor color) {
        this.color = color;
    }

    public boolean isLiquid() {
        return false;
    }

    public boolean isSolid() {
        return true;
    }

    public boolean isOpaque() {
        return true;
    }

    public boolean blocksMovement() {
        return true;
    }

    private Material setTranslucent() {
        this.translucent = true;
        return this;
    }

    protected Material setRequiresTool() {
        this.toolNotRequired = false;
        return this;
    }

    protected Material setFlammable() {
        this.flammable = true;
        return this;
    }

    public boolean isFlammable() {
        return this.flammable;
    }

    public Material setReplaceable() {
        this.replaceable = true;
        return this;
    }

    public boolean isReplaceable() {
        return this.replaceable;
    }

    public boolean isSolidBlocking() {
        return !this.translucent && this.blocksMovement();
    }

    public boolean isToolNotRequired() {
        return this.toolNotRequired;
    }

    public int getPistonMoveBehavior() {
        return this.pistonMoveBehavior;
    }

    protected Material setDestroyOnPistonMove() {
        this.pistonMoveBehavior = 1;
        return this;
    }

    protected Material setBlocksPistonMove() {
        this.pistonMoveBehavior = 2;
        return this;
    }

    protected Material setCanBeBrokenInAdventureMode() {
        this.canBeBrokenInAdventureMode = true;
        return this;
    }

    public MapColor getColor() {
        return this.color;
    }
}
