package net.minecraft.util;

import java.io.OutputStream;
import java.io.PrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrintStreamLogger extends PrintStream {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String name;

    public PrintStreamLogger(String name, OutputStream os) {
        super(os);
        this.name = name;
    }

    @Override
    public void println(String x) {
        this.log(x);
    }

    @Override
    public void println(Object x) {
        this.log(String.valueOf(x));
    }

    private void log(String x) {
        StackTraceElement[] astacktraceelement = Thread.currentThread().getStackTrace();
        StackTraceElement stacktraceelement = astacktraceelement[Math.min(3, astacktraceelement.length)];
        LOGGER.info("[{}]@.({}:{}): {}", this.name, stacktraceelement.getFileName(), stacktraceelement.getLineNumber(), x);
    }
}
