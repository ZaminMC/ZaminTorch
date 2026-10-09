package net.minecraft.client.resource.pack;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.client.resource.metadata.ResourcePackMetadata;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.util.HttpUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ResourcePacks {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final FileFilter FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File file) {
            boolean flag = file.isFile() && file.getName().endsWith(".zip");
            boolean flag1 = file.isDirectory() && new File(file, "pack.mcmeta").isFile();
            return flag || flag1;
        }
    };
    private final File dir;
    public final ResourcePack defaultPack;
    private final File serverPackDir;
    public final ResourceMetadataSerializerRegistry metadataSerializers;
    private ResourcePack serverPack;
    private final ReentrantLock lock = new ReentrantLock();
    private ListenableFuture<Object> serverPackFuture;
    private List<ResourcePacks.Entry> availablePacks = Lists.newArrayList();
    private List<ResourcePacks.Entry> appliedPacks = Lists.newArrayList();

    public ResourcePacks(File dir, File serverPackDir, ResourcePack defaultPack, ResourceMetadataSerializerRegistry metadataSerializers, GameOptions options) {
        this.dir = dir;
        this.serverPackDir = serverPackDir;
        this.defaultPack = defaultPack;
        this.metadataSerializers = metadataSerializers;
        this.mkdirs();
        this.load();
        Iterator<String> iterator = options.resourcePacks.iterator();

        while (iterator.hasNext()) {
            String s = iterator.next();

            for (ResourcePacks.Entry resourcepacks$entry : this.availablePacks) {
                if (resourcepacks$entry.getName().equals(s)) {
                    if (resourcepacks$entry.getFormat() == 1 || options.incompatibleResourcePacks.contains(resourcepacks$entry.getName())) {
                        this.appliedPacks.add(resourcepacks$entry);
                        break;
                    }

                    iterator.remove();
                    LOGGER.warn("Removed selected resource pack {} because it's no longer compatible", new Object[]{resourcepacks$entry.getName()});
                }
            }
        }
    }

    private void mkdirs() {
        if (this.dir.exists()) {
            if (!this.dir.isDirectory() && (!this.dir.delete() || !this.dir.mkdirs())) {
                LOGGER.warn("Unable to recreate resourcepack folder, it exists but is not a directory: " + this.dir);
            }
        } else if (!this.dir.mkdirs()) {
            LOGGER.warn("Unable to create resourcepack folder: " + this.dir);
        }
    }

    private List<File> collectAvailablePackFiles() {
        return this.dir.isDirectory() ? Arrays.asList(this.dir.listFiles(FILE_FILTER)) : Collections.emptyList();
    }

    public void load() {
        List<ResourcePacks.Entry> list = Lists.newArrayList();

        for (File file1 : this.collectAvailablePackFiles()) {
            ResourcePacks.Entry resourcepacks$entry = new ResourcePacks.Entry(file1);
            if (!this.availablePacks.contains(resourcepacks$entry)) {
                try {
                    resourcepacks$entry.load();
                    list.add(resourcepacks$entry);
                } catch (Exception exception) {
                    list.remove(resourcepacks$entry);
                }
            } else {
                int i = this.availablePacks.indexOf(resourcepacks$entry);
                if (i > -1 && i < this.availablePacks.size()) {
                    list.add(this.availablePacks.get(i));
                }
            }
        }

        this.availablePacks.removeAll(list);

        for (ResourcePacks.Entry resourcepacks$entry1 : this.availablePacks) {
            resourcepacks$entry1.close();
        }

        this.availablePacks = list;
    }

    public List<ResourcePacks.Entry> getAvailable() {
        return ImmutableList.copyOf(this.availablePacks);
    }

    public List<ResourcePacks.Entry> getApplied() {
        return ImmutableList.copyOf(this.appliedPacks);
    }

    public void apply(List<ResourcePacks.Entry> packs) {
        this.appliedPacks.clear();
        this.appliedPacks.addAll(packs);
    }

    public File getDirectory() {
        return this.dir;
    }

    public ListenableFuture<Object> downloadServerPack(String url, String hash) {
        String s;
        if (hash.matches("^[a-f0-9]{40}$")) {
            s = hash;
        } else {
            s = "legacy";
        }

        final File file1 = new File(this.serverPackDir, s);
        this.lock.lock();

        try {
            this.removeServerPack();
            if (file1.exists() && hash.length() == 40) {
                try {
                    String s1 = Hashing.sha1().hashBytes(Files.toByteArray(file1)).toString();
                    if (s1.equals(hash)) {
                        return this.applyServerPack(file1);
                    }

                    LOGGER.warn("File " + file1 + " had wrong hash (expected " + hash + ", found " + s1 + "). Deleting it.");
                    FileUtils.deleteQuietly(file1);
                } catch (IOException ioexception) {
                    LOGGER.warn("File " + file1 + " couldn't be hashed. Deleting it.", ioexception);
                    FileUtils.deleteQuietly(file1);
                }
            }

            this.clearOldDownloads();
            final ProgressScreen progressscreen = new ProgressScreen();
            Map<String, String> map = Minecraft.getHttpRequestProperties();
            final Minecraft minecraft = Minecraft.getInstance();
            Futures.getUnchecked(minecraft.execute(new Runnable() {
                @Override
                public void run() {
                    minecraft.openScreen(progressscreen);
                }
            }));
            final SettableFuture<Object> settablefuture = SettableFuture.create();
            this.serverPackFuture = HttpUtil.downloadServerResourcePack(file1, url, map, 52428800, progressscreen, minecraft.getNetworkProxy());
            Futures.addCallback(this.serverPackFuture, new FutureCallback<Object>() {
                @Override
                public void onSuccess(Object result) {
                    ResourcePacks.this.applyServerPack(file1);
                    settablefuture.set(null);
                }

                @Override
                public void onFailure(Throwable t) {
                    settablefuture.setException(t);
                }
            });
            return this.serverPackFuture;
        } finally {
            this.lock.unlock();
        }
    }

    private void clearOldDownloads() {
        List<File> list = Lists.newArrayList(FileUtils.listFiles(this.serverPackDir, TrueFileFilter.TRUE, null));
        Collections.sort(list, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
        int i = 0;

        for (File file1 : list) {
            if (i++ >= 10) {
                LOGGER.info("Deleting old server resource pack " + file1.getName());
                FileUtils.deleteQuietly(file1);
            }
        }
    }

    public ListenableFuture<Object> applyServerPack(File file) {
        this.serverPack = new ZippedResourcePack(file);
        return Minecraft.getInstance().reloadResourcesAsync();
    }

    public ResourcePack getServerPack() {
        return this.serverPack;
    }

    public void removeServerPack() {
        this.lock.lock();

        try {
            if (this.serverPackFuture != null) {
                this.serverPackFuture.cancel(true);
            }

            this.serverPackFuture = null;
            if (this.serverPack != null) {
                this.serverPack = null;
                Minecraft.getInstance().reloadResourcesAsync();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public class Entry {
        private final File file;
        private ResourcePack pack;
        private ResourcePackMetadata metadata;
        private BufferedImage icon;
        private Identifier iconLocation;

        private Entry(File file) {
            this.file = file;
        }

        public void load() throws IOException {
            this.pack = this.file.isDirectory() ? new DirectoryResourcePack(this.file) : new ZippedResourcePack(this.file);
            this.metadata = this.pack.getMetadataSection(ResourcePacks.this.metadataSerializers, "pack");

            try {
                this.icon = this.pack.getIcon();
            } catch (IOException ioexception) {
            }

            if (this.icon == null) {
                this.icon = ResourcePacks.this.defaultPack.getIcon();
            }

            this.close();
        }

        public void bindIconTexture(TextureManager manager) {
            if (this.iconLocation == null) {
                this.iconLocation = manager.register("texturepackicon", new DynamicTexture(this.icon));
            }

            manager.bind(this.iconLocation);
        }

        public void close() {
            if (this.pack instanceof Closeable) {
                IOUtils.closeQuietly((Closeable)this.pack);
            }
        }

        public ResourcePack get() {
            return this.pack;
        }

        public String getName() {
            return this.pack.getName();
        }

        public String getDescription() {
            return this.metadata == null
                ? Formatting.RED + "Invalid pack.mcmeta (or missing 'pack' section)"
                : this.metadata.getDescription().getFormattedString();
        }

        public int getFormat() {
            return this.metadata.getFormat();
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof ResourcePacks.Entry && this.toString().equals(object.toString());
        }

        @Override
        public int hashCode() {
            return this.toString().hashCode();
        }

        @Override
        public String toString() {
            return String.format("%s:%s:%d", this.file.getName(), this.file.isDirectory() ? "folder" : "zip", this.file.lastModified());
        }
    }
}
