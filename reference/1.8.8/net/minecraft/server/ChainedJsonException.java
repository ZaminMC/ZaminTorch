package net.minecraft.server;

import com.google.common.collect.Lists;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class ChainedJsonException extends IOException {
    private final List<ChainedJsonException.Entry> entries = Lists.newArrayList();
    private final String message;

    public ChainedJsonException(String message) {
        this.entries.add(new ChainedJsonException.Entry());
        this.message = message;
    }

    public ChainedJsonException(String message, Throwable exception) {
        super(exception);
        this.entries.add(new ChainedJsonException.Entry());
        this.message = message;
    }

    public void prependJsonKey(String key) {
        this.entries.get(0).addJsonKey(key);
    }

    public void setFileNameAndFlush(String name) {
        this.entries.get(0).fileName = name;
        this.entries.add(0, new ChainedJsonException.Entry());
    }

    @Override
    public String getMessage() {
        return "Invalid " + this.entries.get(this.entries.size() - 1).toString() + ": " + this.message;
    }

    public static ChainedJsonException forException(Exception exception) {
        if (exception instanceof ChainedJsonException) {
            return (ChainedJsonException)exception;
        }

        String s = exception.getMessage();
        if (exception instanceof FileNotFoundException) {
            s = "File not found";
        }

        return new ChainedJsonException(s, exception);
    }

    public static class Entry {
        private String fileName = null;
        private final List<String> jsonKeys = Lists.newArrayList();

        private Entry() {
        }

        private void addJsonKey(String key) {
            this.jsonKeys.add(0, key);
        }

        public String getJsonKeys() {
            return StringUtils.join(this.jsonKeys, "->");
        }

        @Override
        public String toString() {
            if (this.fileName != null) {
                return !this.jsonKeys.isEmpty() ? this.fileName + " " + this.getJsonKeys() : this.fileName;
            } else {
                return !this.jsonKeys.isEmpty() ? "(Unknown file) " + this.getJsonKeys() : "(Unknown file)";
            }
        }
    }
}
