package net.minecraft.client.render.texture;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpTexture extends SimpleTexture {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicInteger threadIdCounter = new AtomicInteger(0);
    private final File fle;
    private final String url;
    private final HttpImageProcessor processor;
    private BufferedImage image;
    private Thread downloader;
    private boolean uploaded;

    public HttpTexture(File file, String url, Identifier location, HttpImageProcessor processor) {
        super(location);
        this.fle = file;
        this.url = url;
        this.processor = processor;
    }

    private void upload() {
        if (!this.uploaded) {
            if (this.image != null) {
                if (this.location != null) {
                    this.clearGlId();
                }

                TextureUtil.uploadTexture(super.getGlId(), this.image);
                this.uploaded = true;
            }
        }
    }

    @Override
    public int getGlId() {
        this.upload();
        return super.getGlId();
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        if (this.processor != null) {
            this.processor.onTextureDownloaded();
        }
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        if (this.image == null && this.location != null) {
            super.load(resourceManager);
        }

        if (this.downloader == null) {
            if (this.fle != null && this.fle.isFile()) {
                LOGGER.debug("Loading http texture from local cache ({})", new Object[]{this.fle});

                try {
                    this.image = ImageIO.read(this.fle);
                    if (this.processor != null) {
                        this.setImage(this.processor.process(this.image));
                    }
                } catch (IOException ioexception) {
                    LOGGER.error("Couldn't load skin " + this.fle, ioexception);
                    this.download();
                }
            } else {
                this.download();
            }
        }
    }

    protected void download() {
        this.downloader = new Thread("Texture Downloader #" + threadIdCounter.incrementAndGet()) {
            @Override
            public void run() {
                HttpURLConnection httpurlconnection = null;
                HttpTexture.LOGGER.debug("Downloading http texture from {} to {}", HttpTexture.this.url, HttpTexture.this.fle);

                try {
                    httpurlconnection = (HttpURLConnection)new URL(HttpTexture.this.url).openConnection(Minecraft.getInstance().getNetworkProxy());
                    httpurlconnection.setDoInput(true);
                    httpurlconnection.setDoOutput(false);
                    httpurlconnection.connect();
                    if (httpurlconnection.getResponseCode() / 100 == 2) {
                        BufferedImage bufferedimage;
                        if (HttpTexture.this.fle != null) {
                            FileUtils.copyInputStreamToFile(httpurlconnection.getInputStream(), HttpTexture.this.fle);
                            bufferedimage = ImageIO.read(HttpTexture.this.fle);
                        } else {
                            bufferedimage = TextureUtil.readImage(httpurlconnection.getInputStream());
                        }

                        if (HttpTexture.this.processor != null) {
                            bufferedimage = HttpTexture.this.processor.process(bufferedimage);
                        }

                        HttpTexture.this.setImage(bufferedimage);
                        return;
                    }
                } catch (Exception exception) {
                    HttpTexture.LOGGER.error("Couldn't download http texture", exception);
                    return;
                } finally {
                    if (httpurlconnection != null) {
                        httpurlconnection.disconnect();
                    }
                }
            }
        };
        this.downloader.setDaemon(true);
        this.downloader.start();
    }
}
