package net.minecraft.client.resource;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.JsonUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AssetIndex {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<String, File> index = Maps.newHashMap();

    public AssetIndex(File dir, String index) {
        if (index != null) {
            File file1 = new File(dir, "objects");
            File file2 = new File(dir, "indexes/" + index + ".json");
            BufferedReader bufferedreader = null;

            try {
                bufferedreader = Files.newReader(file2, Charsets.UTF_8);
                JsonObject jsonobject = new JsonParser().parse(bufferedreader).getAsJsonObject();
                JsonObject jsonobject1 = JsonUtils.getJsonObjectOrDefault(jsonobject, "objects", null);
                if (jsonobject1 != null) {
                    for (Entry<String, JsonElement> entry : jsonobject1.entrySet()) {
                        JsonObject jsonobject2 = (JsonObject)entry.getValue();
                        String s = entry.getKey();
                        String[] astring = s.split("/", 2);
                        String s1 = astring.length == 1 ? astring[0] : astring[0] + ":" + astring[1];
                        String s2 = JsonUtils.getString(jsonobject2, "hash");
                        File file3 = new File(file1, s2.substring(0, 2) + "/" + s2);
                        this.index.put(s1, file3);
                    }
                }
            } catch (JsonParseException jsonparseexception) {
                LOGGER.error("Unable to parse resource index file: " + file2);
            } catch (FileNotFoundException filenotfoundexception) {
                LOGGER.error("Can't find the resource index file: " + file2);
            } finally {
                IOUtils.closeQuietly(bufferedreader);
            }
        }
    }

    public Map<String, File> getIndex() {
        return this.index;
    }
}
