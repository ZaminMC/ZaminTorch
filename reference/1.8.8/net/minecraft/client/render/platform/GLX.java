package net.minecraft.client.render.platform;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTBlendFuncSeparate;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import oshi.SystemInfo;
import oshi.hardware.Processor;

public class GLX {
    public static boolean isNvidia;
    public static boolean isAmd;
    public static int GL_FRAMEBUFFER;
    public static int GL_RENDERBUFFER;
    public static int GL_COLOR_ATTACHMENT0;
    public static int GL_DEPTH_ATTACHMENT;
    public static int GL_FRAMEBUFFER_COMPLETE;
    public static int GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
    public static int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
    public static int GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER;
    public static int GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER;
    private static int fboMode;
    public static boolean useFramebufferObjects;
    private static boolean hasShaders;
    private static boolean useShaderArb;
    public static int GL_LINK_STATUS;
    public static int GL_COMPILE_STATUS;
    public static int GL_VERTEX_SHADER;
    public static int GL_FRAGMENT_SHADER;
    private static boolean useMultitextureArb;
    public static int GL_TEXTURE0;
    public static int GL_TEXTURE1;
    public static int GL_TEXTURE2;
    private static boolean useTextureEnvCombineArb;
    public static int GL_COMBINE;
    public static int GL_INTERPOLATE;
    public static int GL_PRIMARY_COLOR;
    public static int GL_CONSTANT;
    public static int GL_PREVIOUS;
    public static int GL_COMBINE_RGB;
    public static int GL_SOURCE0_RGB;
    public static int GL_SOURCE1_RGB;
    public static int GL_SOURCE2_RGB;
    public static int GL_OPERAND0_RGB;
    public static int GL_OPERAND1_RGB;
    public static int GL_OPERAND2_RGB;
    public static int GL_COMBINE_ALPHA;
    public static int GL_SOURCE0_ALPHA;
    public static int GL_SOURCE1_ALPHA;
    public static int GL_SOURCE2_ALPHA;
    public static int GL_OPERAND0_ALPHA;
    public static int GL_OPERAND1_ALPHA;
    public static int GL_OPERAND2_ALPHA;
    private static boolean separateBlend;
    public static boolean useSeparateBlendExt;
    public static boolean openGl21;
    public static boolean usePostProcess;
    private static String glCapsInfo = "";
    private static String cpuInfo;
    public static boolean useVbos;
    public static boolean useVbosAmd;
    private static boolean useVboArb;
    public static int GL_ARRAY_BUFFER;
    public static int GL_STATIC_DRAW;

    public static void init() {
        ContextCapabilities contextcapabilities = GLContext.getCapabilities();
        useMultitextureArb = contextcapabilities.GL_ARB_multitexture && !contextcapabilities.OpenGL13;
        useTextureEnvCombineArb = contextcapabilities.GL_ARB_texture_env_combine && !contextcapabilities.OpenGL13;
        if (useMultitextureArb) {
            glCapsInfo = glCapsInfo + "Using ARB_multitexture.\n";
            GL_TEXTURE0 = 33984;
            GL_TEXTURE1 = 33985;
            GL_TEXTURE2 = 33986;
        } else {
            glCapsInfo = glCapsInfo + "Using GL 1.3 multitexturing.\n";
            GL_TEXTURE0 = 33984;
            GL_TEXTURE1 = 33985;
            GL_TEXTURE2 = 33986;
        }

        if (useTextureEnvCombineArb) {
            glCapsInfo = glCapsInfo + "Using ARB_texture_env_combine.\n";
            GL_COMBINE = 34160;
            GL_INTERPOLATE = 34165;
            GL_PRIMARY_COLOR = 34167;
            GL_CONSTANT = 34166;
            GL_PREVIOUS = 34168;
            GL_COMBINE_RGB = 34161;
            GL_SOURCE0_RGB = 34176;
            GL_SOURCE1_RGB = 34177;
            GL_SOURCE2_RGB = 34178;
            GL_OPERAND0_RGB = 34192;
            GL_OPERAND1_RGB = 34193;
            GL_OPERAND2_RGB = 34194;
            GL_COMBINE_ALPHA = 34162;
            GL_SOURCE0_ALPHA = 34184;
            GL_SOURCE1_ALPHA = 34185;
            GL_SOURCE2_ALPHA = 34186;
            GL_OPERAND0_ALPHA = 34200;
            GL_OPERAND1_ALPHA = 34201;
            GL_OPERAND2_ALPHA = 34202;
        } else {
            glCapsInfo = glCapsInfo + "Using GL 1.3 texture combiners.\n";
            GL_COMBINE = 34160;
            GL_INTERPOLATE = 34165;
            GL_PRIMARY_COLOR = 34167;
            GL_CONSTANT = 34166;
            GL_PREVIOUS = 34168;
            GL_COMBINE_RGB = 34161;
            GL_SOURCE0_RGB = 34176;
            GL_SOURCE1_RGB = 34177;
            GL_SOURCE2_RGB = 34178;
            GL_OPERAND0_RGB = 34192;
            GL_OPERAND1_RGB = 34193;
            GL_OPERAND2_RGB = 34194;
            GL_COMBINE_ALPHA = 34162;
            GL_SOURCE0_ALPHA = 34184;
            GL_SOURCE1_ALPHA = 34185;
            GL_SOURCE2_ALPHA = 34186;
            GL_OPERAND0_ALPHA = 34200;
            GL_OPERAND1_ALPHA = 34201;
            GL_OPERAND2_ALPHA = 34202;
        }

        useSeparateBlendExt = contextcapabilities.GL_EXT_blend_func_separate && !contextcapabilities.OpenGL14;
        separateBlend = contextcapabilities.OpenGL14 || contextcapabilities.GL_EXT_blend_func_separate;
        useFramebufferObjects = separateBlend
            && (contextcapabilities.GL_ARB_framebuffer_object || contextcapabilities.GL_EXT_framebuffer_object || contextcapabilities.OpenGL30);
        if (useFramebufferObjects) {
            glCapsInfo = glCapsInfo + "Using framebuffer objects because ";
            if (contextcapabilities.OpenGL30) {
                glCapsInfo = glCapsInfo + "OpenGL 3.0 is supported and separate blending is supported.\n";
                fboMode = 0;
                GL_FRAMEBUFFER = 36160;
                GL_RENDERBUFFER = 36161;
                GL_COLOR_ATTACHMENT0 = 36064;
                GL_DEPTH_ATTACHMENT = 36096;
                GL_FRAMEBUFFER_COMPLETE = 36053;
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 36054;
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 36055;
                GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER = 36059;
                GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER = 36060;
            } else if (contextcapabilities.GL_ARB_framebuffer_object) {
                glCapsInfo = glCapsInfo + "ARB_framebuffer_object is supported and separate blending is supported.\n";
                fboMode = 1;
                GL_FRAMEBUFFER = 36160;
                GL_RENDERBUFFER = 36161;
                GL_COLOR_ATTACHMENT0 = 36064;
                GL_DEPTH_ATTACHMENT = 36096;
                GL_FRAMEBUFFER_COMPLETE = 36053;
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 36055;
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 36054;
                GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER = 36059;
                GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER = 36060;
            } else if (contextcapabilities.GL_EXT_framebuffer_object) {
                glCapsInfo = glCapsInfo + "EXT_framebuffer_object is supported.\n";
                fboMode = 2;
                GL_FRAMEBUFFER = 36160;
                GL_RENDERBUFFER = 36161;
                GL_COLOR_ATTACHMENT0 = 36064;
                GL_DEPTH_ATTACHMENT = 36096;
                GL_FRAMEBUFFER_COMPLETE = 36053;
                GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 36055;
                GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 36054;
                GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER = 36059;
                GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER = 36060;
            }
        } else {
            glCapsInfo = glCapsInfo + "Not using framebuffer objects because ";
            glCapsInfo = glCapsInfo + "OpenGL 1.4 is " + (contextcapabilities.OpenGL14 ? "" : "not ") + "supported, ";
            glCapsInfo = glCapsInfo + "EXT_blend_func_separate is " + (contextcapabilities.GL_EXT_blend_func_separate ? "" : "not ") + "supported, ";
            glCapsInfo = glCapsInfo + "OpenGL 3.0 is " + (contextcapabilities.OpenGL30 ? "" : "not ") + "supported, ";
            glCapsInfo = glCapsInfo + "ARB_framebuffer_object is " + (contextcapabilities.GL_ARB_framebuffer_object ? "" : "not ") + "supported, and ";
            glCapsInfo = glCapsInfo + "EXT_framebuffer_object is " + (contextcapabilities.GL_EXT_framebuffer_object ? "" : "not ") + "supported.\n";
        }

        openGl21 = contextcapabilities.OpenGL21;
        hasShaders = openGl21
            || contextcapabilities.GL_ARB_vertex_shader && contextcapabilities.GL_ARB_fragment_shader && contextcapabilities.GL_ARB_shader_objects;
        glCapsInfo = glCapsInfo + "Shaders are " + (hasShaders ? "" : "not ") + "available because ";
        if (hasShaders) {
            if (contextcapabilities.OpenGL21) {
                glCapsInfo = glCapsInfo + "OpenGL 2.1 is supported.\n";
                useShaderArb = false;
                GL_LINK_STATUS = 35714;
                GL_COMPILE_STATUS = 35713;
                GL_VERTEX_SHADER = 35633;
                GL_FRAGMENT_SHADER = 35632;
            } else {
                glCapsInfo = glCapsInfo + "ARB_shader_objects, ARB_vertex_shader, and ARB_fragment_shader are supported.\n";
                useShaderArb = true;
                GL_LINK_STATUS = 35714;
                GL_COMPILE_STATUS = 35713;
                GL_VERTEX_SHADER = 35633;
                GL_FRAGMENT_SHADER = 35632;
            }
        } else {
            glCapsInfo = glCapsInfo + "OpenGL 2.1 is " + (contextcapabilities.OpenGL21 ? "" : "not ") + "supported, ";
            glCapsInfo = glCapsInfo + "ARB_shader_objects is " + (contextcapabilities.GL_ARB_shader_objects ? "" : "not ") + "supported, ";
            glCapsInfo = glCapsInfo + "ARB_vertex_shader is " + (contextcapabilities.GL_ARB_vertex_shader ? "" : "not ") + "supported, and ";
            glCapsInfo = glCapsInfo + "ARB_fragment_shader is " + (contextcapabilities.GL_ARB_fragment_shader ? "" : "not ") + "supported.\n";
        }

        usePostProcess = useFramebufferObjects && hasShaders;
        String s = GL11.glGetString(7936).toLowerCase();
        isNvidia = s.contains("nvidia");
        useVboArb = !contextcapabilities.OpenGL15 && contextcapabilities.GL_ARB_vertex_buffer_object;
        useVbos = contextcapabilities.OpenGL15 || useVboArb;
        glCapsInfo = glCapsInfo + "VBOs are " + (useVbos ? "" : "not ") + "available because ";
        if (useVbos) {
            if (useVboArb) {
                glCapsInfo = glCapsInfo + "ARB_vertex_buffer_object is supported.\n";
                GL_STATIC_DRAW = 35044;
                GL_ARRAY_BUFFER = 34962;
            } else {
                glCapsInfo = glCapsInfo + "OpenGL 1.5 is supported.\n";
                GL_STATIC_DRAW = 35044;
                GL_ARRAY_BUFFER = 34962;
            }
        }

        isAmd = s.contains("ati");
        if (isAmd) {
            if (useVbos) {
                useVbosAmd = true;
            } else {
                GameOptions.Option.RENDER_DISTANCE.setMax(16.0F);
            }
        }

        try {
            Processor[] aprocessor = new SystemInfo().getHardware().getProcessors();
            cpuInfo = String.format("%dx %s", aprocessor.length, aprocessor[0]).replaceAll("\\s+", " ");
        } catch (Throwable throwable) {
        }
    }

    public static boolean isNextGen() {
        return usePostProcess;
    }

    public static String getGlCapsInfo() {
        return glCapsInfo;
    }

    public static int getProgram(int program, int pname) {
        return useShaderArb ? ARBShaderObjects.glGetObjectParameteriARB(program, pname) : GL20.glGetProgrami(program, pname);
    }

    public static void attachShader(int program, int shader) {
        if (useShaderArb) {
            ARBShaderObjects.glAttachObjectARB(program, shader);
        } else {
            GL20.glAttachShader(program, shader);
        }
    }

    public static void deleteShader(int shader) {
        if (useShaderArb) {
            ARBShaderObjects.glDeleteObjectARB(shader);
        } else {
            GL20.glDeleteShader(shader);
        }
    }

    public static int createShader(int type) {
        return useShaderArb ? ARBShaderObjects.glCreateShaderObjectARB(type) : GL20.glCreateShader(type);
    }

    public static void shaderSource(int shader, ByteBuffer buffer) {
        if (useShaderArb) {
            ARBShaderObjects.glShaderSourceARB(shader, buffer);
        } else {
            GL20.glShaderSource(shader, buffer);
        }
    }

    public static void compileShader(int shader) {
        if (useShaderArb) {
            ARBShaderObjects.glCompileShaderARB(shader);
        } else {
            GL20.glCompileShader(shader);
        }
    }

    public static int getShader(int shader, int pname) {
        return useShaderArb ? ARBShaderObjects.glGetObjectParameteriARB(shader, pname) : GL20.glGetShaderi(shader, pname);
    }

    public static String getShaderInfoLog(int shader, int maxLength) {
        return useShaderArb ? ARBShaderObjects.glGetInfoLogARB(shader, maxLength) : GL20.glGetShaderInfoLog(shader, maxLength);
    }

    public static String getProgramInfoLog(int program, int maxLength) {
        return useShaderArb ? ARBShaderObjects.glGetInfoLogARB(program, maxLength) : GL20.glGetProgramInfoLog(program, maxLength);
    }

    public static void useProgram(int program) {
        if (useShaderArb) {
            ARBShaderObjects.glUseProgramObjectARB(program);
        } else {
            GL20.glUseProgram(program);
        }
    }

    public static int createProgram() {
        return useShaderArb ? ARBShaderObjects.glCreateProgramObjectARB() : GL20.glCreateProgram();
    }

    public static void deleteProgram(int program) {
        if (useShaderArb) {
            ARBShaderObjects.glDeleteObjectARB(program);
        } else {
            GL20.glDeleteProgram(program);
        }
    }

    public static void linkProgram(int program) {
        if (useShaderArb) {
            ARBShaderObjects.glLinkProgramARB(program);
        } else {
            GL20.glLinkProgram(program);
        }
    }

    public static int getUniformLocation(int program, CharSequence name) {
        return useShaderArb ? ARBShaderObjects.glGetUniformLocationARB(program, name) : GL20.glGetUniformLocation(program, name);
    }

    public static void uniform1(int location, IntBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform1ARB(location, value);
        } else {
            GL20.glUniform1(location, value);
        }
    }

    public static void uniform1i(int location, int value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform1iARB(location, value);
        } else {
            GL20.glUniform1i(location, value);
        }
    }

    public static void uniform1(int location, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform1ARB(location, value);
        } else {
            GL20.glUniform1(location, value);
        }
    }

    public static void uniform2(int location, IntBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform2ARB(location, value);
        } else {
            GL20.glUniform2(location, value);
        }
    }

    public static void uniform2(int location, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform2ARB(location, value);
        } else {
            GL20.glUniform2(location, value);
        }
    }

    public static void uniform3(int location, IntBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform3ARB(location, value);
        } else {
            GL20.glUniform3(location, value);
        }
    }

    public static void uniform3(int location, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform3ARB(location, value);
        } else {
            GL20.glUniform3(location, value);
        }
    }

    public static void uniform4(int location, IntBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform4ARB(location, value);
        } else {
            GL20.glUniform4(location, value);
        }
    }

    public static void uniform4(int location, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniform4ARB(location, value);
        } else {
            GL20.glUniform4(location, value);
        }
    }

    public static void uniformMatrix2(int location, boolean transpose, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniformMatrix2ARB(location, transpose, value);
        } else {
            GL20.glUniformMatrix2(location, transpose, value);
        }
    }

    public static void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniformMatrix3ARB(location, transpose, value);
        } else {
            GL20.glUniformMatrix3(location, transpose, value);
        }
    }

    public static void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        if (useShaderArb) {
            ARBShaderObjects.glUniformMatrix4ARB(location, transpose, value);
        } else {
            GL20.glUniformMatrix4(location, transpose, value);
        }
    }

    public static int getAttribLocation(int program, CharSequence name) {
        return useShaderArb ? ARBVertexShader.glGetAttribLocationARB(program, name) : GL20.glGetAttribLocation(program, name);
    }

    public static int genBuffers() {
        return useVboArb ? ARBVertexBufferObject.glGenBuffersARB() : GL15.glGenBuffers();
    }

    public static void bindBuffer(int target, int buffer) {
        if (useVboArb) {
            ARBVertexBufferObject.glBindBufferARB(target, buffer);
        } else {
            GL15.glBindBuffer(target, buffer);
        }
    }

    public static void bufferData(int target, ByteBuffer data, int usage) {
        if (useVboArb) {
            ARBVertexBufferObject.glBufferDataARB(target, data, usage);
        } else {
            GL15.glBufferData(target, data, usage);
        }
    }

    public static void deleteBuffers(int buffer) {
        if (useVboArb) {
            ARBVertexBufferObject.glDeleteBuffersARB(buffer);
        } else {
            GL15.glDeleteBuffers(buffer);
        }
    }

    public static boolean useVbo() {
        return useVbos && Minecraft.getInstance().options.useVbo;
    }

    public static void bindFramebuffer(int target, int framebuffer) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glBindFramebuffer(target, framebuffer);
                    break;
                case 1:
                    ARBFramebufferObject.glBindFramebuffer(target, framebuffer);
                    break;
                case 2:
                    EXTFramebufferObject.glBindFramebufferEXT(target, framebuffer);
            }
        }
    }

    public static void bindRenderbuffer(int target, int renderbuffer) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glBindRenderbuffer(target, renderbuffer);
                    break;
                case 1:
                    ARBFramebufferObject.glBindRenderbuffer(target, renderbuffer);
                    break;
                case 2:
                    EXTFramebufferObject.glBindRenderbufferEXT(target, renderbuffer);
            }
        }
    }

    public static void deleteRenderbuffers(int renderbuffer) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glDeleteRenderbuffers(renderbuffer);
                    break;
                case 1:
                    ARBFramebufferObject.glDeleteRenderbuffers(renderbuffer);
                    break;
                case 2:
                    EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer);
            }
        }
    }

    public static void deleteFramebuffers(int framebuffer) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glDeleteFramebuffers(framebuffer);
                    break;
                case 1:
                    ARBFramebufferObject.glDeleteFramebuffers(framebuffer);
                    break;
                case 2:
                    EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
            }
        }
    }

    public static int genFramebuffers() {
        if (!useFramebufferObjects) {
            return -1;
        }

        switch (fboMode) {
            case 0:
                return GL30.glGenFramebuffers();
            case 1:
                return ARBFramebufferObject.glGenFramebuffers();
            case 2:
                return EXTFramebufferObject.glGenFramebuffersEXT();
            default:
                return -1;
        }
    }

    public static int genRenderbuffers() {
        if (!useFramebufferObjects) {
            return -1;
        }

        switch (fboMode) {
            case 0:
                return GL30.glGenRenderbuffers();
            case 1:
                return ARBFramebufferObject.glGenRenderbuffers();
            case 2:
                return EXTFramebufferObject.glGenRenderbuffersEXT();
            default:
                return -1;
        }
    }

    public static void renderbufferStorage(int target, int internalFormat, int width, int height) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glRenderbufferStorage(target, internalFormat, width, height);
                    break;
                case 1:
                    ARBFramebufferObject.glRenderbufferStorage(target, internalFormat, width, height);
                    break;
                case 2:
                    EXTFramebufferObject.glRenderbufferStorageEXT(target, internalFormat, width, height);
            }
        }
    }

    public static void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
                    break;
                case 1:
                    ARBFramebufferObject.glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
                    break;
                case 2:
                    EXTFramebufferObject.glFramebufferRenderbufferEXT(target, attachment, renderbufferTarget, renderbuffer);
            }
        }
    }

    public static int checkFramebufferStatus(int target) {
        if (!useFramebufferObjects) {
            return -1;
        }

        switch (fboMode) {
            case 0:
                return GL30.glCheckFramebufferStatus(target);
            case 1:
                return ARBFramebufferObject.glCheckFramebufferStatus(target);
            case 2:
                return EXTFramebufferObject.glCheckFramebufferStatusEXT(target);
            default:
                return -1;
        }
    }

    public static void framebufferTexture2D(int target, int attachment, int textTarget, int texture, int level) {
        if (useFramebufferObjects) {
            switch (fboMode) {
                case 0:
                    GL30.glFramebufferTexture2D(target, attachment, textTarget, texture, level);
                    break;
                case 1:
                    ARBFramebufferObject.glFramebufferTexture2D(target, attachment, textTarget, texture, level);
                    break;
                case 2:
                    EXTFramebufferObject.glFramebufferTexture2DEXT(target, attachment, textTarget, texture, level);
            }
        }
    }

    public static void activeTexture(int texture) {
        if (useMultitextureArb) {
            ARBMultitexture.glActiveTextureARB(texture);
        } else {
            GL13.glActiveTexture(texture);
        }
    }

    public static void clientActiveTexture(int texture) {
        if (useMultitextureArb) {
            ARBMultitexture.glClientActiveTextureARB(texture);
        } else {
            GL13.glClientActiveTexture(texture);
        }
    }

    public static void multiTexCoord2f(int texture, float s, float t) {
        if (useMultitextureArb) {
            ARBMultitexture.glMultiTexCoord2fARB(texture, s, t);
        } else {
            GL13.glMultiTexCoord2f(texture, s, t);
        }
    }

    public static void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
        if (separateBlend) {
            if (useSeparateBlendExt) {
                EXTBlendFuncSeparate.glBlendFuncSeparateEXT(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
            } else {
                GL14.glBlendFuncSeparate(sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha);
            }
        } else {
            GL11.glBlendFunc(sfactorRGB, dfactorRGB);
        }
    }

    public static boolean useFbo() {
        return useFramebufferObjects && Minecraft.getInstance().options.fboEnable;
    }

    public static String getCpuInfo() {
        return cpuInfo == null ? "<unknown>" : cpuInfo;
    }
}
