package net.minecraft.server.dedicated;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerProperties {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Properties properties = new Properties();
    private final File file;

    public ServerProperties(File file) {
        this.file = file;
        if (file.exists()) {
            FileInputStream fileinputstream = null;

            try {
                fileinputstream = new FileInputStream(file);
                this.properties.load(fileinputstream);
            } catch (Exception exception) {
                LOGGER.warn("Failed to load " + file, exception);
                this.generate();
            } finally {
                if (fileinputstream != null) {
                    try {
                        fileinputstream.close();
                    } catch (IOException ioexception) {
                    }
                }
            }
        } else {
            LOGGER.warn(file + " does not exist");
            this.generate();
        }
    }

    public void generate() {
        LOGGER.info("Generating new properties file");
        this.save();
    }

    public void save() {
        FileOutputStream fileoutputstream = null;

        try {
            fileoutputstream = new FileOutputStream(this.file);
            this.properties.store(fileoutputstream, "Minecraft server properties");
        } catch (Exception exception) {
            LOGGER.warn("Failed to save " + this.file, exception);
            this.generate();
        } finally {
            if (fileoutputstream != null) {
                try {
                    fileoutputstream.close();
                } catch (IOException ioexception) {
                }
            }
        }
    }

    public File getFile() {
        return this.file;
    }

    public String getString(String key, String defaultValue) {
        if (!this.properties.containsKey(key)) {
            this.properties.setProperty(key, defaultValue);
            this.save();
            this.save();
        }

        return this.properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(this.getString(key, "" + defaultValue));
        } catch (Exception exception) {
            this.properties.setProperty(key, "" + defaultValue);
            this.save();
            return defaultValue;
        }
    }

    public long getOrDefault(String key, long defaultValue) {
        try {
            return Long.parseLong(this.getString(key, "" + defaultValue));
        } catch (Exception exception) {
            this.properties.setProperty(key, "" + defaultValue);
            this.save();
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(this.getString(key, "" + defaultValue));
        } catch (Exception exception) {
            this.properties.setProperty(key, "" + defaultValue);
            this.save();
            return defaultValue;
        }
    }

    public void set(String key, Object value) {
        this.properties.setProperty(key, "" + value);
    }
}
