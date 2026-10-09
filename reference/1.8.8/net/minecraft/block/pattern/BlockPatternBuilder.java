package net.minecraft.block.pattern;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class BlockPatternBuilder {
    private static final Joiner CHAR_JOINER = Joiner.on(",");
    private final List<String[]> pattern = Lists.newArrayList();
    private final Map<Character, Predicate<BlockPointer>> chars = Maps.newHashMap();
    private int height;
    private int width;

    private BlockPatternBuilder() {
        this.chars.put(' ', Predicates.alwaysTrue());
    }

    public BlockPatternBuilder aisle(String... args) {
        if (!ArrayUtils.isEmpty(args) && !StringUtils.isEmpty(args[0])) {
            if (this.pattern.isEmpty()) {
                this.height = args.length;
                this.width = args[0].length();
            }

            if (args.length != this.height) {
                throw new IllegalArgumentException("Expected aisle with height of " + this.height + ", but was given one with a height of " + args.length + ")");
            }

            for (String s : args) {
                if (s.length() != this.width) {
                    throw new IllegalArgumentException(
                        "Not all rows in the given aisle are the correct width (expected " + this.width + ", found one with " + s.length() + ")"
                    );
                }

                for (char c0 : s.toCharArray()) {
                    if (!this.chars.containsKey(c0)) {
                        this.chars.put(c0, null);
                    }
                }
            }

            this.pattern.add(args);
            return this;
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    public static BlockPatternBuilder start() {
        return new BlockPatternBuilder();
    }

    public BlockPatternBuilder with(char chr, Predicate<BlockPointer> predicate) {
        this.chars.put(chr, predicate);
        return this;
    }

    public BlockPattern build() {
        return new BlockPattern(this.buildPattern());
    }

    private Predicate<BlockPointer>[][][] buildPattern() {
        this.validate();
        Predicate<BlockPointer>[][][] predicate = (Predicate<BlockPointer>[][][])Array.newInstance(
            Predicate.class, this.pattern.size(), this.height, this.width
        );

        for (int i = 0; i < this.pattern.size(); i++) {
            for (int j = 0; j < this.height; j++) {
                for (int k = 0; k < this.width; k++) {
                    predicate[i][j][k] = this.chars.get(this.pattern.get(i)[j].charAt(k));
                }
            }
        }

        return predicate;
    }

    private void validate() {
        List<Character> list = Lists.newArrayList();

        for (Entry<Character, Predicate<BlockPointer>> entry : this.chars.entrySet()) {
            if (entry.getValue() == null) {
                list.add(entry.getKey());
            }
        }

        if (!list.isEmpty()) {
            throw new IllegalStateException("Predicates for character(s) " + CHAR_JOINER.join(list) + " are missing");
        }
    }
}
