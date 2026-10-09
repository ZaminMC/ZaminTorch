package net.minecraft.client.options;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Int2ObjectHashMap;

public class KeyBinding implements Comparable<KeyBinding> {
    private static final List<KeyBinding> ALL = Lists.newArrayList();
    private static final Int2ObjectHashMap<KeyBinding> BY_KEY_CODE = new Int2ObjectHashMap<>();
    private static final Set<String> CATEGORIES = Sets.newHashSet();
    private final String name;
    private final int defaultKeyCode;
    private final String category;
    private int keyCode;
    private boolean pressed;
    private int clickCount;

    public static void click(int keyCode) {
        if (keyCode != 0) {
            KeyBinding keybinding = BY_KEY_CODE.get(keyCode);
            if (keybinding != null) {
                keybinding.clickCount++;
            }
        }
    }

    public static void set(int keyCode, boolean pressed) {
        if (keyCode != 0) {
            KeyBinding keybinding = BY_KEY_CODE.get(keyCode);
            if (keybinding != null) {
                keybinding.pressed = pressed;
            }
        }
    }

    public static void releaseAll() {
        for (KeyBinding keybinding : ALL) {
            keybinding.release();
        }
    }

    public static void resetMapping() {
        BY_KEY_CODE.clear();

        for (KeyBinding keybinding : ALL) {
            BY_KEY_CODE.put(keybinding.keyCode, keybinding);
        }
    }

    public static Set<String> getCategories() {
        return CATEGORIES;
    }

    public KeyBinding(String name, int keyCode, String category) {
        this.name = name;
        this.keyCode = keyCode;
        this.defaultKeyCode = keyCode;
        this.category = category;
        ALL.add(this);
        BY_KEY_CODE.put(keyCode, this);
        CATEGORIES.add(category);
    }

    public boolean isPressed() {
        return this.pressed;
    }

    public String getCategory() {
        return this.category;
    }

    public boolean consumeClick() {
        if (this.clickCount == 0) {
            return false;
        }

        this.clickCount--;
        return true;
    }

    private void release() {
        this.clickCount = 0;
        this.pressed = false;
    }

    public String getName() {
        return this.name;
    }

    public int getDefaultKeyCode() {
        return this.defaultKeyCode;
    }

    public int getKeyCode() {
        return this.keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public int compareTo(KeyBinding keyBinding) {
        int i = I18n.translate(this.category).compareTo(I18n.translate(keyBinding.category));
        if (i == 0) {
            i = I18n.translate(this.name).compareTo(I18n.translate(keyBinding.name));
        }

        return i;
    }
}
