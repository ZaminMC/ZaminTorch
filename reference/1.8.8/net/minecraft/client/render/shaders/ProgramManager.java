package net.minecraft.client.render.shaders;

import java.io.IOException;
import net.minecraft.client.render.Effect;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.server.ChainedJsonException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ProgramManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static ProgramManager instance;

    public static void createInstance() {
        instance = new ProgramManager();
    }

    public static ProgramManager getInstance() {
        return instance;
    }

    private ProgramManager() {
    }

    public void releaseProgram(Effect effect) {
        effect.getFragmentProgram().close(effect);
        effect.getVertexProgram().close(effect);
        GLX.deleteProgram(effect.getId());
    }

    public int createProgram() throws ChainedJsonException {
        int i = GLX.createProgram();
        if (i <= 0) {
            throw new ChainedJsonException("Could not create shader program (returned program ID " + i + ")");
        } else {
            return i;
        }
    }

    public void linkProgram(Effect effect) throws IOException {
        effect.getFragmentProgram().attachToEffect(effect);
        effect.getVertexProgram().attachToEffect(effect);
        GLX.linkProgram(effect.getId());
        int i = GLX.getProgram(effect.getId(), GLX.GL_LINK_STATUS);
        if (i == 0) {
            LOGGER.warn(
                "Error encountered when linking program containing VS "
                    + effect.getVertexProgram().getName()
                    + " and FS "
                    + effect.getFragmentProgram().getName()
                    + ". Log output:"
            );
            LOGGER.warn(GLX.getProgramInfoLog(effect.getId(), 32768));
        }
    }
}
