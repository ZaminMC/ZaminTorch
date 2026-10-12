package net.minecraft.client.render;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;

public class TextRenderer implements ResourceReloadListener {
    private static final Identifier[] UNICODE_PAGE_LOCATIONS = new Identifier[256];
    private int[] characterWidths = new int[256];
    public int fontHeight = 9;
    public Random random = new Random();
    private byte[] glyphSizes = new byte[65536];
    private int[] colors = new int[32];
    private final Identifier fontLocation;
    private final TextureManager textureManager;
    private float x;
    private float y;
    private boolean unicode;
    private boolean bidirectional;
    private float r;
    private float g;
    private float b;
    private float a;
    private int color;
    private boolean obfuscated;
    private boolean bold;
    private boolean italic;
    private boolean underlined;
    private boolean strikethrough;

    public TextRenderer(GameOptions options, Identifier fontLocation, TextureManager textureManager, boolean unicode) {
        this.fontLocation = fontLocation;
        this.textureManager = textureManager;
        this.unicode = unicode;
        textureManager.bind(this.fontLocation);

        for (int i = 0; i < 32; i++) {
            int j = (i >> 3 & 1) * 85;
            int k = (i >> 2 & 1) * 170 + j;
            int l = (i >> 1 & 1) * 170 + j;
            int i1 = (i >> 0 & 1) * 170 + j;
            if (i == 6) {
                k += 85;
            }

            if (options.anaglyph) {
                int j1 = (k * 30 + l * 59 + i1 * 11) / 100;
                int k1 = (k * 30 + l * 70) / 100;
                int l1 = (k * 30 + i1 * 70) / 100;
                k = j1;
                l = k1;
                i1 = l1;
            }

            if (i >= 16) {
                k /= 4;
                l /= 4;
                i1 /= 4;
            }

            this.colors[i] = (k & 0xFF) << 16 | (l & 0xFF) << 8 | i1 & 0xFF;
        }

        this.initGlyphSizes();
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        this.init();
    }

    private void init() {
        BufferedImage bufferedimage;
        try {
            bufferedimage = TextureUtil.readImage(Minecraft.getInstance().getResourceManager().getResource(this.fontLocation).asStream());
        } catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        }

        int i = bufferedimage.getWidth();
        int j = bufferedimage.getHeight();
        int[] aint = new int[i * j];
        bufferedimage.getRGB(0, 0, i, j, aint, 0, i);
        int k = j / 16;
        int l = i / 16;
        int i1 = 1;
        float f = 8.0F / l;

        for (int j1 = 0; j1 < 256; j1++) {
            int k1 = j1 % 16;
            int l1 = j1 / 16;
            if (j1 == 32) {
                this.characterWidths[j1] = 3 + i1;
            }

            int i2;
            for (i2 = l - 1; i2 >= 0; i2--) {
                int j2 = k1 * l + i2;
                boolean flag = true;

                for (int k2 = 0; k2 < k && flag; k2++) {
                    int l2 = (l1 * l + k2) * i;
                    if ((aint[j2 + l2] >> 24 & 0xFF) != 0) {
                        flag = false;
                    }
                }

                if (!flag) {
                    break;
                }
            }

            this.characterWidths[j1] = (int)(0.5 + ++i2 * f) + i1;
        }
    }

    private void initGlyphSizes() {
        InputStream inputstream = null;

        try {
            inputstream = Minecraft.getInstance().getResourceManager().getResource(new Identifier("font/glyph_sizes.bin")).asStream();
            inputstream.read(this.glyphSizes);
        } catch (IOException ioexception) {
            throw new RuntimeException(ioexception);
        } finally {
            IOUtils.closeQuietly(inputstream);
        }
    }

    private float drawGlyph(char chr, boolean italic) {
        if (chr == ' ') {
            return 4.0F;
        }

        int i = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
            .indexOf(chr);
        return i != -1 && !this.unicode ? this.drawBasicGlyph(i, italic) : this.drawUnicodeGlyph(chr, italic);
    }

    private float drawBasicGlyph(int index, boolean italic) {
        int i = index % 16 * 8;
        int j = index / 16 * 8;
        int k = italic ? 1 : 0;
        this.textureManager.bind(this.fontLocation);
        int l = this.characterWidths[index];
        float f = l - 0.01F;
        GL11.glBegin(5);
        GL11.glTexCoord2f(i / 128.0F, j / 128.0F);
        GL11.glVertex3f(this.x + k, this.y, 0.0F);
        GL11.glTexCoord2f(i / 128.0F, (j + 7.99F) / 128.0F);
        GL11.glVertex3f(this.x - k, this.y + 7.99F, 0.0F);
        GL11.glTexCoord2f((i + f - 1.0F) / 128.0F, j / 128.0F);
        GL11.glVertex3f(this.x + f - 1.0F + k, this.y, 0.0F);
        GL11.glTexCoord2f((i + f - 1.0F) / 128.0F, (j + 7.99F) / 128.0F);
        GL11.glVertex3f(this.x + f - 1.0F - k, this.y + 7.99F, 0.0F);
        GL11.glEnd();
        return l;
    }

    private Identifier getFontPage(int page) {
        if (UNICODE_PAGE_LOCATIONS[page] == null) {
            UNICODE_PAGE_LOCATIONS[page] = new Identifier(String.format("textures/font/unicode_page_%02x.png", page));
        }

        return UNICODE_PAGE_LOCATIONS[page];
    }

    private void bindFontPageTexture(int page) {
        this.textureManager.bind(this.getFontPage(page));
    }

    private float drawUnicodeGlyph(char chr, boolean italic) {
        if (this.glyphSizes[chr] == 0) {
            return 0.0F;
        }

        int i = chr / 256;
        this.bindFontPageTexture(i);
        int j = this.glyphSizes[chr] >>> 4;
        int k = this.glyphSizes[chr] & 15;
        float f = j;
        float f1 = k + 1;
        float f2 = chr % 16 * 16 + f;
        float f3 = (chr & 255) / 16 * 16;
        float f4 = f1 - f - 0.02F;
        float f5 = italic ? 1.0F : 0.0F;
        GL11.glBegin(5);
        GL11.glTexCoord2f(f2 / 256.0F, f3 / 256.0F);
        GL11.glVertex3f(this.x + f5, this.y, 0.0F);
        GL11.glTexCoord2f(f2 / 256.0F, (f3 + 15.98F) / 256.0F);
        GL11.glVertex3f(this.x - f5, this.y + 7.99F, 0.0F);
        GL11.glTexCoord2f((f2 + f4) / 256.0F, f3 / 256.0F);
        GL11.glVertex3f(this.x + f4 / 2.0F + f5, this.y, 0.0F);
        GL11.glTexCoord2f((f2 + f4) / 256.0F, (f3 + 15.98F) / 256.0F);
        GL11.glVertex3f(this.x + f4 / 2.0F - f5, this.y + 7.99F, 0.0F);
        GL11.glEnd();
        return (f1 - f) / 2.0F + 1.0F;
    }

    public int drawWithShadow(String text, float x, float y, int color) {
        return this.draw(text, x, y, color, true);
    }

    public int draw(String text, int x, int y, int color) {
        return this.draw(text, x, y, color, false);
    }

    public int draw(String text, float x, float y, int color, boolean shadow) {
        GlStateManager.enableAlphaTest();
        this.reset();
        int i;
        if (shadow) {
            i = this.drawLayer(text, x + 1.0F, y + 1.0F, color, true);
            i = Math.max(i, this.drawLayer(text, x, y, color, false));
        } else {
            i = this.drawLayer(text, x, y, color, false);
        }

        return i;
    }

    private String bidirectionalShaping(String text) {
        try {
            Bidi bidi = new Bidi(new ArabicShaping(8).shape(text), 127);
            bidi.setReorderingMode(0);
            return bidi.writeReordered(2);
        } catch (ArabicShapingException arabicshapingexception) {
            return text;
        }
    }

    private void reset() {
        this.obfuscated = false;
        this.bold = false;
        this.italic = false;
        this.underlined = false;
        this.strikethrough = false;
    }

    private void drawLayer(String text, boolean shadow) {
        for (int i = 0; i < text.length(); i++) {
            char c0 = text.charAt(i);
            if (c0 == 167 && i + 1 < text.length()) {
                int i1 = "0123456789abcdefklmnor".indexOf(text.toLowerCase(Locale.ENGLISH).charAt(i + 1));
                if (i1 < 16) {
                    this.obfuscated = false;
                    this.bold = false;
                    this.strikethrough = false;
                    this.underlined = false;
                    this.italic = false;
                    if (i1 < 0 || i1 > 15) {
                        i1 = 15;
                    }

                    if (shadow) {
                        i1 += 16;
                    }

                    int j1 = this.colors[i1];
                    this.color = j1;
                    GlStateManager.color4f((j1 >> 16) / 255.0F, (j1 >> 8 & 0xFF) / 255.0F, (j1 & 0xFF) / 255.0F, this.a);
                } else if (i1 == 16) {
                    this.obfuscated = true;
                } else if (i1 == 17) {
                    this.bold = true;
                } else if (i1 == 18) {
                    this.strikethrough = true;
                } else if (i1 == 19) {
                    this.underlined = true;
                } else if (i1 == 20) {
                    this.italic = true;
                } else if (i1 == 21) {
                    this.obfuscated = false;
                    this.bold = false;
                    this.strikethrough = false;
                    this.underlined = false;
                    this.italic = false;
                    GlStateManager.color4f(this.r, this.g, this.b, this.a);
                }

                i++;
            } else {
                int j = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
                    .indexOf(c0);
                if (this.obfuscated && j != -1) {
                    int k = this.getWidth(c0);

                    char c1;
                    do {
                        j = this.random
                            .nextInt(
                                "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
                                    .length()
                            );
                        c1 = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
                            .charAt(j);
                    } while (k != this.getWidth(c1));

                    c0 = c1;
                }

                float f1 = this.unicode ? 0.5F : 1.0F;
                boolean flag = (c0 == 0 || j == -1 || this.unicode) && shadow;
                if (flag) {
                    this.x -= f1;
                    this.y -= f1;
                }

                float f = this.drawGlyph(c0, this.italic);
                if (flag) {
                    this.x += f1;
                    this.y += f1;
                }

                if (this.bold) {
                    this.x += f1;
                    if (flag) {
                        this.x -= f1;
                        this.y -= f1;
                    }

                    this.drawGlyph(c0, this.italic);
                    this.x -= f1;
                    if (flag) {
                        this.x += f1;
                        this.y += f1;
                    }

                    f++;
                }

                if (this.strikethrough) {
                    Tesselator tesselator = Tesselator.getInstance();
                    BufferBuilder bufferbuilder = tesselator.getBuffer();
                    GlStateManager.disableTexture();
                    bufferbuilder.begin(7, DefaultVertexFormat.POSITION);
                    bufferbuilder.vertex(this.x, this.y + this.fontHeight / 2, 0.0).nextVertex();
                    bufferbuilder.vertex(this.x + f, this.y + this.fontHeight / 2, 0.0).nextVertex();
                    bufferbuilder.vertex(this.x + f, this.y + this.fontHeight / 2 - 1.0F, 0.0).nextVertex();
                    bufferbuilder.vertex(this.x, this.y + this.fontHeight / 2 - 1.0F, 0.0).nextVertex();
                    tesselator.end();
                    GlStateManager.enableTexture();
                }

                if (this.underlined) {
                    Tesselator tesselator1 = Tesselator.getInstance();
                    BufferBuilder bufferbuilder1 = tesselator1.getBuffer();
                    GlStateManager.disableTexture();
                    bufferbuilder1.begin(7, DefaultVertexFormat.POSITION);
                    int l = this.underlined ? -1 : 0;
                    bufferbuilder1.vertex(this.x + l, this.y + this.fontHeight, 0.0).nextVertex();
                    bufferbuilder1.vertex(this.x + f, this.y + this.fontHeight, 0.0).nextVertex();
                    bufferbuilder1.vertex(this.x + f, this.y + this.fontHeight - 1.0F, 0.0).nextVertex();
                    bufferbuilder1.vertex(this.x + l, this.y + this.fontHeight - 1.0F, 0.0).nextVertex();
                    tesselator1.end();
                    GlStateManager.enableTexture();
                }

                this.x += (int)f;
            }
        }
    }

    private int drawLayerAligned(String text, int x, int y, int width, int color, boolean shadow) {
        if (this.bidirectional) {
            int i = this.getWidth(this.bidirectionalShaping(text));
            x = x + width - i;
        }

        return this.drawLayer(text, x, y, color, shadow);
    }

    private int drawLayer(String text, float x, float y, int color, boolean shadow) {
        if (text == null) {
            return 0;
        }

        if (this.bidirectional) {
            text = this.bidirectionalShaping(text);
        }

        if ((color & -67108864) == 0) {
            color |= -16777216;
        }

        if (shadow) {
            color = (color & 16579836) >> 2 | color & 0xFF000000;
        }

        this.r = (color >> 16 & 0xFF) / 255.0F;
        this.g = (color >> 8 & 0xFF) / 255.0F;
        this.b = (color & 0xFF) / 255.0F;
        this.a = (color >> 24 & 0xFF) / 255.0F;
        GlStateManager.color4f(this.r, this.g, this.b, this.a);
        this.x = x;
        this.y = y;
        this.drawLayer(text, shadow);
        return (int)this.x;
    }

    public int getWidth(String text) {
        if (text == null) {
            return 0;
        }

        int i = 0;
        boolean flag = false;

        for (int j = 0; j < text.length(); j++) {
            char c0 = text.charAt(j);
            int k = this.getWidth(c0);
            if (k < 0 && j < text.length() - 1) {
                c0 = text.charAt(++j);
                if (c0 == 'l' || c0 == 'L') {
                    flag = true;
                } else if (c0 == 'r' || c0 == 'R') {
                    flag = false;
                }

                k = 0;
            }

            i += k;
            if (flag && k > 0) {
                i++;
            }
        }

        return i;
    }

    public int getWidth(char chr) {
        if (chr == 167) {
            return -1;
        }

        if (chr == ' ') {
            return 4;
        }

        int i = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000"
            .indexOf(chr);
        if (chr > 0 && i != -1 && !this.unicode) {
            return this.characterWidths[i];
        }

        if (this.glyphSizes[chr] != 0) {
            int j = this.glyphSizes[chr] >>> 4;
            int k = this.glyphSizes[chr] & 15;
            if (k > 7) {
                k = 15;
                j = 0;
            }

            k++;
            return (k - j) / 2 + 1;
        } else {
            return 0;
        }
    }

    public String trim(String text, int width) {
        return this.trim(text, width, false);
    }

    public String trim(String text, int width, boolean inverse) {
        StringBuilder stringbuilder = new StringBuilder();
        int i = 0;
        int j = inverse ? text.length() - 1 : 0;
        int k = inverse ? -1 : 1;
        boolean flag = false;
        boolean flag1 = false;

        for (int l = j; l >= 0 && l < text.length() && i < width; l += k) {
            char c0 = text.charAt(l);
            int i1 = this.getWidth(c0);
            if (flag) {
                flag = false;
                if (c0 == 'l' || c0 == 'L') {
                    flag1 = true;
                } else if (c0 == 'r' || c0 == 'R') {
                    flag1 = false;
                }
            } else if (i1 < 0) {
                flag = true;
            } else {
                i += i1;
                if (flag1) {
                    i++;
                }
            }

            if (i > width) {
                break;
            }

            if (inverse) {
                stringbuilder.insert(0, c0);
            } else {
                stringbuilder.append(c0);
            }
        }

        return stringbuilder.toString();
    }

    private String removeTrailingLineBreaks(String text) {
        while (text != null && text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }

        return text;
    }

    public void splitAndDraw(String text, int x, int y, int width, int color) {
        this.reset();
        this.color = color;
        text = this.removeTrailingLineBreaks(text);
        this.splitAndDrawLayers(text, x, y, width, false);
    }

    private void splitAndDrawLayers(String text, int x, int y, int width, boolean colored) {
        for (String s : this.split(text, width)) {
            this.drawLayerAligned(s, x, y, width, this.color, colored);
            y += this.fontHeight;
        }
    }

    public int splitAndGetHeight(String text, int width) {
        return this.fontHeight * this.split(text, width).size();
    }

    public void setUnicode(boolean unicode) {
        this.unicode = unicode;
    }

    public boolean getUnicode() {
        return this.unicode;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
    }

    public List<String> split(String text, int width) {
        return Arrays.asList(this.insertLineBreaks(text, width).split("\n"));
    }

    String insertLineBreaks(String text, int width) {
        int i = this.indexAtWidth(text, width);
        if (text.length() <= i) {
            return text;
        }

        String s = text.substring(0, i);
        char c0 = text.charAt(i);
        boolean flag = c0 == ' ' || c0 == '\n';
        String s1 = isolateFormatting(s) + text.substring(i + (flag ? 1 : 0));
        return s + "\n" + this.insertLineBreaks(s1, width);
    }

    private int indexAtWidth(String text, int width) {
        int i = text.length();
        int j = 0;
        int k = 0;
        int l = -1;
        boolean flag = false;

        while (k < i) {
            char c0 = text.charAt(k);
            switch (c0) {
                case '\n':
                    k--;
                    break;
                case ' ':
                    l = k;
                default:
                    j += this.getWidth(c0);
                    if (flag) {
                        j++;
                    }
                    break;
                case '§':
                    if (k < i - 1) {
                        char c1 = text.charAt(++k);
                        if (c1 == 'l' || c1 == 'L') {
                            flag = true;
                        } else if (c1 == 'r' || c1 == 'R' || isColor(c1)) {
                            flag = false;
                        }
                    }
            }

            if (c0 == '\n') {
                l = ++k;
                break;
            }

            if (j > width) {
                break;
            }

            k++;
        }

        return k != i && l != -1 && l < k ? l : k;
    }

    /**
     * Checks if a certain formatting character is a color. The different colors are:
     * <ul>
     * <li><b>0:</b> <span style="color:#000000">black</span></li>
     * <li><b>1:</b> <span style="color:#0000AA">dark blue</span></li>
     * <li><b>2:</b> <span style="color:#00AA00">dark green</span></li>
     * <li><b>3:</b> <span style="color:#00AAAA">dark aqua</span></li>
     * <li><b>4:</b> <span style="color:#AA0000">dark red</span></li>
     * <li><b>5:</b> <span style="color:#AA00AA">dark purple</span></li>
     * <li><b>6:</b> <span style="color:#FFAA00">gold</span></li>
     * <li><b>7:</b> <span style="color:#AAAAAA">gray</span></li>
     * <li><b>8:</b> <span style="color:#555555">dark gray</span></li>
     * <li><b>9:</b> <span style="color:#5555FF">blue</span></li>
     * <li><b>a/A:</b> <span style="color:#55FF55">green</span></li>
     * <li><b>b/B:</b> <span style="color:#55FFFF">aqua</span></li>
     * <li><b>c/C:</b> <span style="color:#FF5555">red</span></li>
     * <li><b>d/D:</b> <span style="color:#FF55FF">light_purple</span></li>
     * <li><b>e/E:</b> <span style="color:#FFFF55">yellow</span></li>
     * <li><b>f/F:</b> <span style="color:#FFFFFF">white</span></li>
     * <li><b>g/G:</b> <span style="color:#DDD605">minecoin gold</span></li>
     * </ul>
     */
    private static boolean isColor(char chr) {
        return chr >= '0' && chr <= '9' || chr >= 'a' && chr <= 'f' || chr >= 'A' && chr <= 'F';
    }

    /**
     * Checks the type of formatting character.
     * <ul>
     * <li><b>k:</b> <span style="color:#000000">obfuscated</span></li>
     * <li><b>l:</b> <span style="color:#000000"><b>bold</b></span></li>
     * <li><b>m:</b> <span style="color:#000000"><s>strikethrough</s></span></li>
     * <li><b>n:</b> <span style="color:#000000"><u>underline</u></span></li>
     * <li><b>o:</b> <span style="color:#000000"><i>italic</i></span></li>
     * <li><b>r:</b> <span style="color:#000000">reset</span></li>
     * </ul>
     */
    private static boolean isFormatting(char chr) {
        return chr >= 'k' && chr <= 'o' || chr >= 'K' && chr <= 'O' || chr == 'r' || chr == 'R';
    }

    /**
     * Strips all non formatting characters from the string.
     */
    public static String isolateFormatting(String text) {
        String s = "";
        int i = -1;
        int j = text.length();

        while ((i = text.indexOf(167, i + 1)) != -1) {
            if (i < j - 1) {
                char c0 = text.charAt(i + 1);
                if (isColor(c0)) {
                    s = "§" + c0;
                } else if (isFormatting(c0)) {
                    s = s + "§" + c0;
                }
            }
        }

        return s;
    }

    public boolean isBidirectional() {
        return this.bidirectional;
    }

    public int getColor(char formatting) {
        return this.colors["0123456789abcdef".indexOf(formatting)];
    }
}
