package net.zaminmc.torch.transport;

import java.util.logging.Logger;

/**
 * The transport's own logger face (JUL, the server's logging source of
 * truth): the saturation and lifetime warnings speak through one named
 * logger so the console's quiet laws stay the server's own decision.
 */
final class TransportLog {

    private static final Logger LOG = Logger.getLogger("net.zaminmc.torch.transport");

    static Logger get() {
        return LOG;
    }

    private TransportLog() {
    }
}
