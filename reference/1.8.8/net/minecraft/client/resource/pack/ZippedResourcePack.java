package net.minecraft.client.resource.pack;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.resource.ResourceNotFoundException;

public class ZippedResourcePack extends CustomResourcePack implements Closeable {
    public static final Splitter TYPE_NAMESPACE_SPLITTER = Splitter.on('/').omitEmptyStrings().limit(3);
    private ZipFile zip;

    public ZippedResourcePack(File file) {
        super(file);
    }

    private ZipFile openZip() throws IOException {
        if (this.zip == null) {
            this.zip = new ZipFile(this.file);
        }

        return this.zip;
    }

    @Override
    protected InputStream openResource(String path) throws IOException {
        ZipFile zipfile = this.openZip();
        ZipEntry zipentry = zipfile.getEntry(path);
        if (zipentry == null) {
            throw new ResourceNotFoundException(this.file, path);
        } else {
            return zipfile.getInputStream(zipentry);
        }
    }

    @Override
    public boolean hasResource(String path) {
        try {
            return this.openZip().getEntry(path) != null;
        } catch (IOException ioexception) {
            return false;
        }
    }

    @Override
    public Set<String> getNamespaces() {
        ZipFile zipfile;
        try {
            zipfile = this.openZip();
        } catch (IOException ioexception) {
            return Collections.emptySet();
        }

        Enumeration<? extends ZipEntry> enumeration = zipfile.entries();
        Set<String> set = Sets.newHashSet();

        while (enumeration.hasMoreElements()) {
            ZipEntry zipentry = enumeration.nextElement();
            String s = zipentry.getName();
            if (s.startsWith("assets/")) {
                List<String> list = Lists.newArrayList(TYPE_NAMESPACE_SPLITTER.split(s));
                if (list.size() > 1) {
                    String s1 = list.get(1);
                    if (!s1.equals(s1.toLowerCase())) {
                        this.warnNonLowercaseNamespace(s1);
                    } else {
                        set.add(s1);
                    }
                }
            }
        }

        return set;
    }

    @Override
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }

    @Override
    public void close() throws IOException {
        if (this.zip != null) {
            this.zip.close();
            this.zip = null;
        }
    }
}
