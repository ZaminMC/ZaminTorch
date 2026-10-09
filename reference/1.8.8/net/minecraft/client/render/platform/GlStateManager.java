package net.minecraft.client.render.platform;

import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;

public class GlStateManager {
    private static GlStateManager.Alpha ALPHA_TEST = new GlStateManager.Alpha();
    private static GlStateManager.GlState LIGHTING = new GlStateManager.GlState(2896);
    private static GlStateManager.GlState[] LIGHT_STATES = new GlStateManager.GlState[8];
    private static GlStateManager.ColorMaterial COLOR_MATERIAL = new GlStateManager.ColorMaterial();
    private static GlStateManager.Blend BLEND = new GlStateManager.Blend();
    private static GlStateManager.Depth DEPTH = new GlStateManager.Depth();
    private static GlStateManager.Fog FOG = new GlStateManager.Fog();
    private static GlStateManager.Cull CULL = new GlStateManager.Cull();
    private static GlStateManager.PolygonOffset POLYGON_OFFSET = new GlStateManager.PolygonOffset();
    private static GlStateManager.ColorLogicOp COLOR_LOGIC = new GlStateManager.ColorLogicOp();
    private static GlStateManager.TexGen TEX_GEN = new GlStateManager.TexGen();
    private static GlStateManager.Clear CLEAR = new GlStateManager.Clear();
    private static GlStateManager.Stencil STENCIL = new GlStateManager.Stencil();
    private static GlStateManager.GlState NORMALIZE = new GlStateManager.GlState(2977);
    private static int texture = 0;
    private static GlStateManager.Texture[] TEXTURES = new GlStateManager.Texture[8];
    private static int shadeModel = 7425;
    private static GlStateManager.GlState RESCALE_NORMAL = new GlStateManager.GlState(32826);
    private static GlStateManager.ColorMask COLOR_MASK = new GlStateManager.ColorMask();
    private static GlStateManager.Color COLOR = new GlStateManager.Color();

    public static void pushLightingAttributes() {
        GL11.glPushAttrib(8256);
    }

    public static void popAttributes() {
        GL11.glPopAttrib();
    }

    public static void disableAlphaTest() {
        ALPHA_TEST.state.disable();
    }

    public static void enableAlphaTest() {
        ALPHA_TEST.state.enable();
    }

    public static void alphaFunc(int func, float ref) {
        if (func != ALPHA_TEST.func || ref != ALPHA_TEST.ref) {
            ALPHA_TEST.func = func;
            ALPHA_TEST.ref = ref;
            GL11.glAlphaFunc(func, ref);
        }
    }

    public static void enableLighting() {
        LIGHTING.enable();
    }

    public static void disableLighting() {
        LIGHTING.disable();
    }

    public static void enableLight(int light) {
        LIGHT_STATES[light].enable();
    }

    public static void disableLight(int light) {
        LIGHT_STATES[light].disable();
    }

    public static void enableColorMaterial() {
        COLOR_MATERIAL.state.enable();
    }

    public static void disableColorMaterial() {
        COLOR_MATERIAL.state.disable();
    }

    public static void colorMaterial(int face, int mode) {
        if (face != COLOR_MATERIAL.face || mode != COLOR_MATERIAL.mode) {
            COLOR_MATERIAL.face = face;
            COLOR_MATERIAL.mode = mode;
            GL11.glColorMaterial(face, mode);
        }
    }

    public static void disableDepthTest() {
        DEPTH.state.disable();
    }

    public static void enableDepthTest() {
        DEPTH.state.enable();
    }

    public static void depthFunc(int func) {
        if (func != DEPTH.func) {
            DEPTH.func = func;
            GL11.glDepthFunc(func);
        }
    }

    public static void depthMask(boolean mask) {
        if (mask != DEPTH.mask) {
            DEPTH.mask = mask;
            GL11.glDepthMask(mask);
        }
    }

    public static void disableBlend() {
        BLEND.state.disable();
    }

    public static void enableBlend() {
        BLEND.state.enable();
    }

    public static void blendFunc(int sfactor, int dfactor) {
        if (sfactor != BLEND.sfactorRGB || dfactor != BLEND.dfactorRGB) {
            BLEND.sfactorRGB = sfactor;
            BLEND.dfactorRGB = dfactor;
            GL11.glBlendFunc(sfactor, dfactor);
        }
    }

    public static void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
        if (sfactorRGB != BLEND.sfactorRGB || dfactorRGB != BLEND.dfactorRGB || sfactorAlpha != BLEND.sfactorAlpha || dfactorAlpha != BLEND.dfactorAlpha) {
            BLEND.sfactorRGB = sfactorRGB;
            BLEND.dfactorRGB = dfactorRGB;
            BLEND.sfactorAlpha = sfactorAlpha;
            BLEND.dfactorAlpha = dfactorAlpha;
            GLX.blendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
        }
    }

    public static void enableFog() {
        FOG.state.enable();
    }

    public static void disableFog() {
        FOG.state.disable();
    }

    public static void fogMode(int mode) {
        if (mode != FOG.mode) {
            FOG.mode = mode;
            GL11.glFogi(2917, mode);
        }
    }

    public static void fogDensity(float density) {
        if (density != FOG.density) {
            FOG.density = density;
            GL11.glFogf(2914, density);
        }
    }

    public static void fogStart(float start) {
        if (start != FOG.start) {
            FOG.start = start;
            GL11.glFogf(2915, start);
        }
    }

    public static void fogEnd(float end) {
        if (end != FOG.end) {
            FOG.end = end;
            GL11.glFogf(2916, end);
        }
    }

    public static void enableCull() {
        CULL.state.enable();
    }

    public static void disableCull() {
        CULL.state.disable();
    }

    public static void cullFace(int mode) {
        if (mode != CULL.mode) {
            CULL.mode = mode;
            GL11.glCullFace(mode);
        }
    }

    public static void enablePolygonOffset() {
        POLYGON_OFFSET.fill.enable();
    }

    public static void disablePolygonOffset() {
        POLYGON_OFFSET.fill.disable();
    }

    public static void polygonOffset(float factor, float units) {
        if (factor != POLYGON_OFFSET.factor || units != POLYGON_OFFSET.units) {
            POLYGON_OFFSET.factor = factor;
            POLYGON_OFFSET.units = units;
            GL11.glPolygonOffset(factor, units);
        }
    }

    public static void enableColorLogicOp() {
        COLOR_LOGIC.state.enable();
    }

    public static void disableColorLogicOp() {
        COLOR_LOGIC.state.disable();
    }

    public static void logicOp(int op) {
        if (op != COLOR_LOGIC.op) {
            COLOR_LOGIC.op = op;
            GL11.glLogicOp(op);
        }
    }

    public static void enableTexGen(GlStateManager.TexGenMode texGen) {
        getTexGenCoord(texGen).state.enable();
    }

    public static void disableTexGen(GlStateManager.TexGenMode texGen) {
        getTexGenCoord(texGen).state.disable();
    }

    public static void texGenMode(GlStateManager.TexGenMode mode, int param) {
        GlStateManager.TexGenCoord glstatemanager$texgencoord = getTexGenCoord(mode);
        if (param != glstatemanager$texgencoord.mode) {
            glstatemanager$texgencoord.mode = param;
            GL11.glTexGeni(glstatemanager$texgencoord.coord, 9472, param);
        }
    }

    public static void texGenParam(GlStateManager.TexGenMode mode, int pname, FloatBuffer buffer) {
        GL11.glTexGen(getTexGenCoord(mode).coord, pname, buffer);
    }

    private static GlStateManager.TexGenCoord getTexGenCoord(GlStateManager.TexGenMode mode) {
        switch (mode) {
            case S:
                return TEX_GEN.s;
            case T:
                return TEX_GEN.t;
            case R:
                return TEX_GEN.r;
            case Q:
                return TEX_GEN.q;
            default:
                return TEX_GEN.s;
        }
    }

    public static void activeTexture(int texture) {
        if (GlStateManager.texture != texture - GLX.GL_TEXTURE0) {
            GlStateManager.texture = texture - GLX.GL_TEXTURE0;
            GLX.activeTexture(texture);
        }
    }

    public static void enableTexture() {
        TEXTURES[texture].state.enable();
    }

    public static void disableTexture() {
        TEXTURES[texture].state.disable();
    }

    public static int genTextures() {
        return GL11.glGenTextures();
    }

    public static void deleteTextures(int texture) {
        GL11.glDeleteTextures(texture);

        for (GlStateManager.Texture glstatemanager$texture : TEXTURES) {
            if (glstatemanager$texture.texture == texture) {
                glstatemanager$texture.texture = -1;
            }
        }
    }

    public static void bindTexture(int texture) {
        if (texture != TEXTURES[GlStateManager.texture].texture) {
            TEXTURES[GlStateManager.texture].texture = texture;
            GL11.glBindTexture(3553, texture);
        }
    }

    public static void enableNormalize() {
        NORMALIZE.enable();
    }

    public static void disableNormalize() {
        NORMALIZE.disable();
    }

    public static void shadeModel(int model) {
        if (model != shadeModel) {
            shadeModel = model;
            GL11.glShadeModel(model);
        }
    }

    public static void enableRescaleNormal() {
        RESCALE_NORMAL.enable();
    }

    public static void disableRescaleNormal() {
        RESCALE_NORMAL.disable();
    }

    public static void viewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
        if (r != COLOR_MASK.r || g != COLOR_MASK.g || b != COLOR_MASK.b || a != COLOR_MASK.a) {
            COLOR_MASK.r = r;
            COLOR_MASK.g = g;
            COLOR_MASK.b = b;
            COLOR_MASK.a = a;
            GL11.glColorMask(r, g, b, a);
        }
    }

    public static void clearDepth(double depth) {
        if (depth != CLEAR.depth) {
            CLEAR.depth = depth;
            GL11.glClearDepth(depth);
        }
    }

    public static void clearColor(float r, float g, float b, float a) {
        if (r != CLEAR.color.r || g != CLEAR.color.g || b != CLEAR.color.b || a != CLEAR.color.a) {
            CLEAR.color.r = r;
            CLEAR.color.g = g;
            CLEAR.color.b = b;
            CLEAR.color.a = a;
            GL11.glClearColor(r, g, b, a);
        }
    }

    public static void clear(int mask) {
        GL11.glClear(mask);
    }

    public static void matrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    public static void loadIdentity() {
        GL11.glLoadIdentity();
    }

    public static void pushMatrix() {
        GL11.glPushMatrix();
    }

    public static void popMatrix() {
        GL11.glPopMatrix();
    }

    public static void getFloat(int pname, FloatBuffer buffer) {
        GL11.glGetFloat(pname, buffer);
    }

    public static void ortho(double l, double r, double b, double t, double n, double f) {
        GL11.glOrtho(l, r, b, t, n, f);
    }

    public static void rotatef(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
    }

    public static void scalef(float x, float y, float z) {
        GL11.glScalef(x, y, z);
    }

    public static void scaled(double x, double y, double z) {
        GL11.glScaled(x, y, z);
    }

    public static void translatef(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    public static void translated(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
    }

    public static void multMatrix(FloatBuffer m) {
        GL11.glMultMatrix(m);
    }

    public static void color4f(float r, float g, float b, float a) {
        if (r != COLOR.r || g != COLOR.g || b != COLOR.b || a != COLOR.a) {
            COLOR.r = r;
            COLOR.g = g;
            COLOR.b = b;
            COLOR.a = a;
            GL11.glColor4f(r, g, b, a);
        }
    }

    public static void color3f(float r, float g, float b) {
        color4f(r, g, b, 1.0F);
    }

    public static void clearColor() {
        COLOR.r = COLOR.g = COLOR.b = COLOR.a = -1.0F;
    }

    public static void callList(int list) {
        GL11.glCallList(list);
    }

    static {
        for (int i = 0; i < 8; i++) {
            LIGHT_STATES[i] = new GlStateManager.GlState(16384 + i);
        }

        for (int j = 0; j < 8; j++) {
            TEXTURES[j] = new GlStateManager.Texture();
        }
    }

    static class Alpha {
        public GlStateManager.GlState state = new GlStateManager.GlState(3008);
        public int func = 519;
        public float ref = -1.0F;

        private Alpha() {
        }
    }

    static class Blend {
        public GlStateManager.GlState state = new GlStateManager.GlState(3042);
        public int sfactorRGB = 1;
        public int dfactorRGB = 0;
        public int sfactorAlpha = 1;
        public int dfactorAlpha = 0;

        private Blend() {
        }
    }

    static class Clear {
        public double depth = 1.0;
        public GlStateManager.Color color = new GlStateManager.Color(0.0F, 0.0F, 0.0F, 0.0F);
        public int stencil = 0;

        private Clear() {
        }
    }

    static class Color {
        public float r = 1.0F;
        public float g = 1.0F;
        public float b = 1.0F;
        public float a = 1.0F;

        public Color() {
        }

        public Color(float r, float g, float b, float a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    static class ColorLogicOp {
        public GlStateManager.GlState state = new GlStateManager.GlState(3058);
        public int op = 5379;

        private ColorLogicOp() {
        }
    }

    static class ColorMask {
        public boolean r = true;
        public boolean g = true;
        public boolean b = true;
        public boolean a = true;

        private ColorMask() {
        }
    }

    static class ColorMaterial {
        public GlStateManager.GlState state = new GlStateManager.GlState(2903);
        public int face = 1032;
        public int mode = 5634;

        private ColorMaterial() {
        }
    }

    static class Cull {
        public GlStateManager.GlState state = new GlStateManager.GlState(2884);
        public int mode = 1029;

        private Cull() {
        }
    }

    static class Depth {
        public GlStateManager.GlState state = new GlStateManager.GlState(2929);
        public boolean mask = true;
        public int func = 513;

        private Depth() {
        }
    }

    static class Fog {
        public GlStateManager.GlState state = new GlStateManager.GlState(2912);
        public int mode = 2048;
        public float density = 1.0F;
        public float start = 0.0F;
        public float end = 1.0F;

        private Fog() {
        }
    }

    static class GlState {
        private final int cap;
        private boolean enabled = false;

        public GlState(int cap) {
            this.cap = cap;
        }

        public void disable() {
            this.setEnabled(false);
        }

        public void enable() {
            this.setEnabled(true);
        }

        public void setEnabled(boolean enabled) {
            if (enabled != this.enabled) {
                this.enabled = enabled;
                if (enabled) {
                    GL11.glEnable(this.cap);
                } else {
                    GL11.glDisable(this.cap);
                }
            }
        }
    }

    static class PolygonOffset {
        public GlStateManager.GlState fill = new GlStateManager.GlState(32823);
        public GlStateManager.GlState line = new GlStateManager.GlState(10754);
        public float factor = 0.0F;
        public float units = 0.0F;

        private PolygonOffset() {
        }
    }

    static class Stencil {
        public GlStateManager.StencilFunc func = new GlStateManager.StencilFunc();
        public int mask = -1;
        public int fail = 7680;
        public int zfail = 7680;
        public int zpass = 7680;

        private Stencil() {
        }
    }

    static class StencilFunc {
        public int func = 519;
        public int ref = 0;
        public int mask = -1;

        private StencilFunc() {
        }
    }

    static class TexGen {
        public GlStateManager.TexGenCoord s = new GlStateManager.TexGenCoord(8192, 3168);
        public GlStateManager.TexGenCoord t = new GlStateManager.TexGenCoord(8193, 3169);
        public GlStateManager.TexGenCoord r = new GlStateManager.TexGenCoord(8194, 3170);
        public GlStateManager.TexGenCoord q = new GlStateManager.TexGenCoord(8195, 3171);

        private TexGen() {
        }
    }

    static class TexGenCoord {
        public GlStateManager.GlState state;
        public int coord;
        public int mode = -1;

        public TexGenCoord(int coord, int state) {
            this.coord = coord;
            this.state = new GlStateManager.GlState(state);
        }
    }

    public enum TexGenMode {
        S,
        T,
        R,
        Q;
    }

    static class Texture {
        public GlStateManager.GlState state = new GlStateManager.GlState(3553);
        public int texture = 0;

        private Texture() {
        }
    }
}
