package net.minecraft.client.resource.pack;

import com.google.common.collect.Sets;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

public class DirectoryResourcePack extends CustomResourcePack {
    public DirectoryResourcePack(File file) {
        super(file);
    }

    @Override
    protected InputStream openResource(String path) throws IOException {
        return new BufferedInputStream(new FileInputStream(new File(this.file, path)));
    }

    @Override
    protected boolean hasResource(String path) {
        return new File(this.file, path).isFile();
    }

    @Override
    public Set<String> getNamespaces() {
        Set<String> set = Sets.newHashSet();
        File file1 = new File(this.file, "assets/");
        if (file1.isDirectory()) {
            for (File file2 : file1.listFiles(DirectoryFileFilter.DIRECTORY)) {
                String s = relativize(file1, file2);
                if (!s.equals(s.toLowerCase())) {
                    this.warnNonLowercaseNamespace(s);
                } else {
                    set.add(s.substring(0, s.length() - 1));
                }
            }
        }

        return set;
    }
}
