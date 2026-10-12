package net.minecraft.client.render.world;

import java.util.BitSet;
import java.util.Set;
import net.minecraft.util.math.Direction;

public class OcclusionData {
    private static final int FACINGS = Direction.values().length;
    private final BitSet data = new BitSet(FACINGS * FACINGS);

    public void add(Set<Direction> faces) {
        for (Direction direction : faces) {
            for (Direction direction1 : faces) {
                this.set(direction, direction1, true);
            }
        }
    }

    public void set(Direction from, Direction to, boolean visible) {
        this.data.set(from.ordinal() + to.ordinal() * FACINGS, visible);
        this.data.set(to.ordinal() + from.ordinal() * FACINGS, visible);
    }

    public void fill(boolean visible) {
        this.data.set(0, this.data.size(), visible);
    }

    public boolean isVisible(Direction from, Direction to) {
        return this.data.get(from.ordinal() + to.ordinal() * FACINGS);
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append(' ');

        for (Direction direction : Direction.values()) {
            stringbuilder.append(' ').append(direction.toString().toUpperCase().charAt(0));
        }

        stringbuilder.append('\n');

        for (Direction direction2 : Direction.values()) {
            stringbuilder.append(direction2.toString().toUpperCase().charAt(0));

            for (Direction direction1 : Direction.values()) {
                if (direction2 == direction1) {
                    stringbuilder.append("  ");
                } else {
                    boolean flag = this.isVisible(direction2, direction1);
                    stringbuilder.append(' ').append((char)(flag ? 'Y' : 'n'));
                }
            }

            stringbuilder.append('\n');
        }

        return stringbuilder.toString();
    }
}
