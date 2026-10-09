package net.minecraft.world;

import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.NbtCompound;

public class GameRules {
    private TreeMap<String, GameRules.Value> values = new TreeMap<>();

    public GameRules() {
        this.add("doFireTick", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("mobGriefing", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("keepInventory", "false", GameRules.Type.BOOLEAN_VALUE);
        this.add("doMobSpawning", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("doMobLoot", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("doTileDrops", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("doEntityDrops", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("commandBlockOutput", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("naturalRegeneration", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("doDaylightCycle", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("logAdminCommands", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("showDeathMessages", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("randomTickSpeed", "3", GameRules.Type.NUMERICAL_VALUE);
        this.add("sendCommandFeedback", "true", GameRules.Type.BOOLEAN_VALUE);
        this.add("reducedDebugInfo", "false", GameRules.Type.BOOLEAN_VALUE);
    }

    public void add(String name, String defaultValue, GameRules.Type type) {
        this.values.put(name, new GameRules.Value(defaultValue, type));
    }

    public void set(String name, String value) {
        GameRules.Value gamerules$value = this.values.get(name);
        if (gamerules$value != null) {
            gamerules$value.set(value);
        } else {
            this.add(name, value, GameRules.Type.ANY_VALUE);
        }
    }

    public String get(String name) {
        GameRules.Value gamerules$value = this.values.get(name);
        return gamerules$value != null ? gamerules$value.get() : "";
    }

    public boolean getBoolean(String name) {
        GameRules.Value gamerules$value = this.values.get(name);
        return gamerules$value != null && gamerules$value.getBoolean();
    }

    public int getInt(String name) {
        GameRules.Value gamerules$value = this.values.get(name);
        return gamerules$value != null ? gamerules$value.getInt() : 0;
    }

    public NbtCompound toNbt() {
        NbtCompound nbtcompound = new NbtCompound();

        for (String s : this.values.keySet()) {
            GameRules.Value gamerules$value = this.values.get(s);
            nbtcompound.putString(s, gamerules$value.get());
        }

        return nbtcompound;
    }

    public void readNbt(NbtCompound nbt) {
        for (String s : nbt.getKeys()) {
            String s1 = s;
            String s2 = nbt.getString(s);
            this.set(s1, s2);
        }
    }

    public String[] getAll() {
        Set<String> set = this.values.keySet();
        return set.toArray(new String[set.size()]);
    }

    public boolean contains(String name) {
        return this.values.containsKey(name);
    }

    public boolean isType(String name, GameRules.Type type) {
        GameRules.Value gamerules$value = this.values.get(name);
        return gamerules$value != null && (gamerules$value.getType() == type || type == GameRules.Type.ANY_VALUE);
    }

    public enum Type {
        ANY_VALUE,
        BOOLEAN_VALUE,
        NUMERICAL_VALUE;
    }

    static class Value {
        private String value;
        private boolean booleanValue;
        private int intValue;
        private double doubleValue;
        private final GameRules.Type type;

        public Value(String value, GameRules.Type type) {
            this.type = type;
            this.set(value);
        }

        public void set(String value) {
            this.value = value;
            this.booleanValue = Boolean.parseBoolean(value);
            this.intValue = this.booleanValue ? 1 : 0;

            try {
                this.intValue = Integer.parseInt(value);
            } catch (NumberFormatException numberformatexception1) {
            }

            try {
                this.doubleValue = Double.parseDouble(value);
            } catch (NumberFormatException numberformatexception) {
            }
        }

        public String get() {
            return this.value;
        }

        public boolean getBoolean() {
            return this.booleanValue;
        }

        public int getInt() {
            return this.intValue;
        }

        public GameRules.Type getType() {
            return this.type;
        }
    }
}
