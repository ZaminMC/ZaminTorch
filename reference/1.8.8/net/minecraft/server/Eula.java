package net.minecraft.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Eula {
    private static final Logger LOGGER = LogManager.getLogger();
    private final File file;
    private final boolean accepted;

    public Eula(File file) {
        this.file = file;
        this.accepted = this.load(file);
    }

    private boolean load(File file) {
        FileInputStream fileinputstream = null;
        boolean flag = false;

        try {
            Properties properties = new Properties();
            fileinputstream = new FileInputStream(file);
            properties.load(fileinputstream);
            flag = Boolean.parseBoolean(properties.getProperty("eula", "false"));
        } catch (Exception exception) {
            LOGGER.warn("Failed to load " + file);
            this.write();
        } finally {
            IOUtils.closeQuietly(fileinputstream);
        }

        return flag;
    }

    public boolean isAccepted() {
        return this.accepted;
    }

    public void write() {
        FileOutputStream fileoutputstream = null;

        try {
            Properties properties = new Properties();
            fileoutputstream = new FileOutputStream(this.file);
            properties.setProperty("eula", "false");
            properties.store(
                fileoutputstream,
                "By changing the setting below to TRUE you are indicating your agreement to our EULA (https://account.mojang.com/documents/minecraft_eula)."
            );
        } catch (Exception exception) {
            LOGGER.warn("Failed to save " + this.file, exception);
        } finally {
            IOUtils.closeQuietly(fileoutputstream);
        }
    }
}
