package net.minecraft.client.render.shaders;

import com.google.gson.JsonObject;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.JsonUtils;
import org.lwjgl.opengl.GL14;

public class BlendMode {
    private static BlendMode lastApplied = null;
    private final int srcColorFactor;
    private final int srcAlphaFactor;
    private final int dstColorFactor;
    private final int dstAlphaFactor;
    private final int blendFunc;
    private final boolean separateBlend;
    private final boolean opaque;

    private BlendMode(boolean separateBlend, boolean opaque, int srcColorFactor, int dstColorFactor, int srcAlphaFactor, int dstAlphaFactor, int blendFunc) {
        this.separateBlend = separateBlend;
        this.srcColorFactor = srcColorFactor;
        this.dstColorFactor = dstColorFactor;
        this.srcAlphaFactor = srcAlphaFactor;
        this.dstAlphaFactor = dstAlphaFactor;
        this.opaque = opaque;
        this.blendFunc = blendFunc;
    }

    public BlendMode() {
        this(false, true, 1, 0, 1, 0, 32774);
    }

    public BlendMode(int srcColorFactor, int dstColorFactor, int blendFunc) {
        this(false, false, srcColorFactor, dstColorFactor, srcColorFactor, dstColorFactor, blendFunc);
    }

    public BlendMode(int srcColorFactor, int dstColorFactor, int srcAlphaFactor, int dstAlphaFactor, int blendFunc) {
        this(true, false, srcColorFactor, dstColorFactor, srcAlphaFactor, dstAlphaFactor, blendFunc);
    }

    public void apply() {
        if (!this.equals(lastApplied)) {
            if (lastApplied == null || this.opaque != lastApplied.isOpaque()) {
                lastApplied = this;
                if (this.opaque) {
                    GlStateManager.disableBlend();
                    return;
                }

                GlStateManager.enableBlend();
            }

            GL14.glBlendEquation(this.blendFunc);
            if (this.separateBlend) {
                GlStateManager.blendFuncSeparate(this.srcColorFactor, this.dstColorFactor, this.srcAlphaFactor, this.dstAlphaFactor);
            } else {
                GlStateManager.blendFunc(this.srcColorFactor, this.dstColorFactor);
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof BlendMode)) {
            return false;
        }

        BlendMode blendmode = (BlendMode)object;
        return this.blendFunc == blendmode.blendFunc
            && this.dstAlphaFactor == blendmode.dstAlphaFactor
            && this.dstColorFactor == blendmode.dstColorFactor
            && this.opaque == blendmode.opaque
            && this.separateBlend == blendmode.separateBlend
            && this.srcAlphaFactor == blendmode.srcAlphaFactor
            && this.srcColorFactor == blendmode.srcColorFactor;
    }

    @Override
    public int hashCode() {
        int i = this.srcColorFactor;
        i = 31 * i + this.srcAlphaFactor;
        i = 31 * i + this.dstColorFactor;
        i = 31 * i + this.dstAlphaFactor;
        i = 31 * i + this.blendFunc;
        i = 31 * i + (this.separateBlend ? 1 : 0);
        return 31 * i + (this.opaque ? 1 : 0);
    }

    public boolean isOpaque() {
        return this.opaque;
    }

    public static BlendMode fromJson(JsonObject json) {
        if (json == null) {
            return new BlendMode();
        }

        int i = 32774;
        int j = 1;
        int k = 0;
        int l = 1;
        int i1 = 0;
        boolean flag = true;
        boolean flag1 = false;
        if (JsonUtils.hasString(json, "func")) {
            i = stringToBlendFunc(json.get("func").getAsString());
            if (i != 32774) {
                flag = false;
            }
        }

        if (JsonUtils.hasString(json, "srcrgb")) {
            j = stringToBlendFactor(json.get("srcrgb").getAsString());
            if (j != 1) {
                flag = false;
            }
        }

        if (JsonUtils.hasString(json, "dstrgb")) {
            k = stringToBlendFactor(json.get("dstrgb").getAsString());
            if (k != 0) {
                flag = false;
            }
        }

        if (JsonUtils.hasString(json, "srcalpha")) {
            l = stringToBlendFactor(json.get("srcalpha").getAsString());
            if (l != 1) {
                flag = false;
            }

            flag1 = true;
        }

        if (JsonUtils.hasString(json, "dstalpha")) {
            i1 = stringToBlendFactor(json.get("dstalpha").getAsString());
            if (i1 != 0) {
                flag = false;
            }

            flag1 = true;
        }

        if (flag) {
            return new BlendMode();
        } else {
            return flag1 ? new BlendMode(j, k, l, i1, i) : new BlendMode(j, k, i);
        }
    }

    private static int stringToBlendFunc(String s) {
        String sx = s.trim().toLowerCase();
        if (sx.equals("add")) {
            return 32774;
        } else if (sx.equals("subtract")) {
            return 32778;
        } else if (sx.equals("reversesubtract")) {
            return 32779;
        } else if (sx.equals("reverse_subtract")) {
            return 32779;
        } else if (sx.equals("min")) {
            return 32775;
        } else {
            return sx.equals("max") ? 32776 : 32774;
        }
    }

    private static int stringToBlendFactor(String s) {
        String sx = s.trim().toLowerCase();
        sx = sx.replaceAll("_", "");
        sx = sx.replaceAll("one", "1");
        sx = sx.replaceAll("zero", "0");
        sx = sx.replaceAll("minus", "-");
        if (sx.equals("0")) {
            return 0;
        } else if (sx.equals("1")) {
            return 1;
        } else if (sx.equals("srccolor")) {
            return 768;
        } else if (sx.equals("1-srccolor")) {
            return 769;
        } else if (sx.equals("dstcolor")) {
            return 774;
        } else if (sx.equals("1-dstcolor")) {
            return 775;
        } else if (sx.equals("srcalpha")) {
            return 770;
        } else if (sx.equals("1-srcalpha")) {
            return 771;
        } else if (sx.equals("dstalpha")) {
            return 772;
        } else {
            return sx.equals("1-dstalpha") ? 773 : -1;
        }
    }
}
