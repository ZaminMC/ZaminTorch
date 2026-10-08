package net.zamin.launcher;

import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Process entry point. Bootstraps the server: it must stay a thin composition
 * root, never become the server itself.
 */
public final class ZaminLauncher {

    static {
        // One-line concise console logging: time, level, message (+stack if any).
        // The old format leaked fully-qualified class.method/thread noise into
        // every line; the console is an operator surface, not a trace dump.
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%6$s%n");
    }

    private static final Logger LOGGER = Logger.getLogger(ZaminLauncher.class.getName());

    public static void main(String[] args) throws Exception {
        LogManager.getLogManager();
        LOGGER.info("ZaminTorch launcher");
        new LauncherRuntime(args).run();
    }

    private ZaminLauncher() {
    }
}
