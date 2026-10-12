package net.minecraft.block;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;

public abstract class FlowerBlock extends PlantBlock {
    protected EnumProperty<FlowerBlock.Type> type;

    protected FlowerBlock() {
        this.setDefaultState(
            this.stateDefinition
                .any()
                .set(this.getTypeProperty(), this.getFlowerGroup() == FlowerBlock.Group.RED ? FlowerBlock.Type.POPPY : FlowerBlock.Type.DANDELION)
        );
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(this.getTypeProperty()).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (FlowerBlock.Type flowerblock$type : FlowerBlock.Type.byGroup(this.getFlowerGroup())) {
            inventory.add(new ItemStack(item, 1, flowerblock$type.getId()));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(this.getTypeProperty(), FlowerBlock.Type.byId(this.getFlowerGroup(), metadata));
    }

    public abstract FlowerBlock.Group getFlowerGroup();

    public Property<FlowerBlock.Type> getTypeProperty() {
        if (this.type == null) {
            this.type = EnumProperty.of("type", FlowerBlock.Type.class, new Predicate<FlowerBlock.Type>() {
                public boolean apply(FlowerBlock.Type type) {
                    return type.getGroup() == FlowerBlock.this.getFlowerGroup();
                }
            });
        }

        return this.type;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(this.getTypeProperty()).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, this.getTypeProperty());
    }

    @Override
    public Block.OffsetType getOffsetType() {
        return Block.OffsetType.XZ;
    }

    public enum Group {
        YELLOW,
        RED;

        public FlowerBlock getBlock() {
            return this == YELLOW ? Blocks.YELLOW_FLOWER : Blocks.RED_FLOWER;
        }
    }

    public enum Type implements StringSerializable {
        DANDELION(FlowerBlock.Group.YELLOW, 0, "dandelion"),
        POPPY(FlowerBlock.Group.RED, 0, "poppy"),
        BLUE_ORCHID(FlowerBlock.Group.RED, 1, "blue_orchid", "blueOrchid"),
        ALLIUM(FlowerBlock.Group.RED, 2, "allium"),
        HOUSTONIA(FlowerBlock.Group.RED, 3, "houstonia"),
        RED_TULIP(FlowerBlock.Group.RED, 4, "red_tulip", "tulipRed"),
        ORANGE_TULIP(FlowerBlock.Group.RED, 5, "orange_tulip", "tulipOrange"),
        WHITE_TULIP(FlowerBlock.Group.RED, 6, "white_tulip", "tulipWhite"),
        PINK_TULIP(FlowerBlock.Group.RED, 7, "pink_tulip", "tulipPink"),
        OXEY_DAISY(FlowerBlock.Group.RED, 8, "oxeye_daisy", "oxeyeDaisy");

        private static final FlowerBlock.Type[][] BY_ID_PER_GROUP = new FlowerBlock.Type[FlowerBlock.Group.values().length][];
        private final FlowerBlock.Group group;
        private final int id;
        private final String key;
        private final String name;

        Type(FlowerBlock.Group group, int id, String key) {
            this(group, id, key, key);
        }

        Type(FlowerBlock.Group group, int id, String key, String name) {
            this.group = group;
            this.id = id;
            this.key = key;
            this.name = name;
        }

        public FlowerBlock.Group getGroup() {
            return this.group;
        }

        public int getId() {
            return this.id;
        }

        public static FlowerBlock.Type byId(FlowerBlock.Group group, int id) {
            FlowerBlock.Type[] aflowerblock$type = BY_ID_PER_GROUP[group.ordinal()];
            if (id < 0 || id >= aflowerblock$type.length) {
                id = 0;
            }

            return aflowerblock$type[id];
        }

        public static FlowerBlock.Type[] byGroup(FlowerBlock.Group group) {
            return BY_ID_PER_GROUP[group.ordinal()];
        }

        @Override
        public String toString() {
            return this.key;
        }

        @Override
        public String serializeToString() {
            return this.key;
        }

        public String getName() {
            return this.name;
        }

        static {
            for (final FlowerBlock.Group flowerblock$group : FlowerBlock.Group.values()) {
                Collection<FlowerBlock.Type> collection = Collections2.filter(Lists.newArrayList(values()), new Predicate<FlowerBlock.Type>() {
                    public boolean apply(FlowerBlock.Type type) {
                        return type.getGroup() == flowerblock$group;
                    }
                });
                BY_ID_PER_GROUP[flowerblock$group.ordinal()] = collection.toArray(new FlowerBlock.Type[collection.size()]);
            }
        }
    }
}
