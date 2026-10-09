package net.minecraft.resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * Identifiers are used to point to resources of the game, like sounds, textures, models, etc.
 * An Identifier consists of two parts: a namespace and a path. Its string representation is
 * its namespace and path, separated by a colon (':'): "&lt;namespace&gt;:&lt;path&gt;".
 * 
 * <p>The namespace is the domain in which the resource is located. The default namespace is "minecraft",
 * which is where all the Vanilla resources are located. Any resources provided by a resource pack, data
 * pack, mod, etc. should be put in their own namespace to keep them distinct. For mods it is custom to
 * use their mod id as the namespace for their resources.
 * 
 * <p>The path is the location of the resource within the namespace.
 * 
 * <p> Namespaces may only contain lowercase letters ([a-z]), digits ([0-9]), and the characters '_', '.', and '-'.
 * Paths may also contain the standard path separator '/'.
 */
public class Identifier {
    protected final String namespace;
    protected final String path;

    protected Identifier(int i, String... identifier) {
        this.namespace = StringUtils.isEmpty(identifier[0]) ? "minecraft" : identifier[0].toLowerCase();
        this.path = identifier[1];
        Validate.notNull(this.path);
    }

    public Identifier(String identifier) {
        this(0, splitIdentifier(identifier));
    }

    public Identifier(String namespace, String path) {
        this(0, namespace, path);
    }

    protected static String[] splitIdentifier(String identifier) {
        String[] astring = new String[]{null, identifier};
        int i = identifier.indexOf(58);
        if (i >= 0) {
            astring[1] = identifier.substring(i + 1, identifier.length());
            if (i > 1) {
                astring[0] = identifier.substring(0, i);
            }
        }

        return astring;
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public String toString() {
        return this.namespace + ':' + this.path;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Identifier)) {
            return false;
        }

        Identifier identifier = (Identifier)object;
        return this.namespace.equals(identifier.namespace) && this.path.equals(identifier.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }
}
