package net.minecraft.client.render.shaders;

import com.google.common.collect.Maps;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Map;
import net.minecraft.client.render.Effect;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import net.minecraft.server.ChainedJsonException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.BufferUtils;

public class Program {
    private final Program.Type type;
    private final String name;
    private int id;
    private int references = 0;

    private Program(Program.Type type, int id, String name) {
        this.type = type;
        this.id = id;
        this.name = name;
    }

    public void attachToEffect(Effect effect) {
        this.references++;
        GLX.attachShader(effect.getId(), this.id);
    }

    public void close(Effect effect) {
        this.references--;
        if (this.references <= 0) {
            GLX.deleteShader(this.id);
            this.type.getPrograms().remove(this.name);
        }
    }

    public String getName() {
        return this.name;
    }

    public static Program compileShader(ResourceManager resourceManager, Program.Type type, String name) throws IOException {
        Program program = type.getPrograms().get(name);
        if (program == null) {
            Identifier identifier = new Identifier("shaders/program/" + name + type.getExtension());
            BufferedInputStream bufferedinputstream = new BufferedInputStream(resourceManager.getResource(identifier).asStream());
            byte[] abyte = toByteArray(bufferedinputstream);
            ByteBuffer bytebuffer = BufferUtils.createByteBuffer(abyte.length);
            bytebuffer.put(abyte);
            ((Buffer)bytebuffer).position(0);
            int i = GLX.createShader(type.getGlType());
            GLX.shaderSource(i, bytebuffer);
            GLX.compileShader(i);
            if (GLX.getShader(i, GLX.GL_COMPILE_STATUS) == 0) {
                String s = StringUtils.trim(GLX.getShaderInfoLog(i, 32768));
                ChainedJsonException chainedjsonexception = new ChainedJsonException("Couldn't compile " + type.getName() + " program: " + s);
                chainedjsonexception.setFileNameAndFlush(identifier.getPath());
                throw chainedjsonexception;
            }

            program = new Program(type, i, name);
            type.getPrograms().put(name, program);
        }

        return program;
    }

    protected static byte[] toByteArray(BufferedInputStream bis) throws IOException {
        try {
            return IOUtils.toByteArray(bis);
        } finally {
            bis.close();
        }
    }

    public enum Type {
        VERTEX("vertex", ".vsh", GLX.GL_VERTEX_SHADER),
        FRAGMENT("fragment", ".fsh", GLX.GL_FRAGMENT_SHADER);

        private final String name;
        private final String extension;
        private final int glType;
        private final Map<String, Program> programs = Maps.newHashMap();

        Type(String name, String extension, int glType) {
            this.name = name;
            this.extension = extension;
            this.glType = glType;
        }

        public String getName() {
            return this.name;
        }

        protected String getExtension() {
            return this.extension;
        }

        protected int getGlType() {
            return this.glType;
        }

        protected Map<String, Program> getPrograms() {
            return this.programs;
        }
    }
}
