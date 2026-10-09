package net.minecraft.network;

public final class DifferentThreadException extends RuntimeException {
    public static final DifferentThreadException INSTANCE = new DifferentThreadException();

    private DifferentThreadException() {
        this.setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        this.setStackTrace(new StackTraceElement[0]);
        return this;
    }
}
