package net.minecraft.client.resource;

import net.minecraft.resource.Identifier;
import org.apache.commons.lang3.StringUtils;

public class ModelIdentifier extends Identifier {
    private final String variant;

    protected ModelIdentifier(int i, String... strings) {
        super(0, strings[0], strings[1]);
        this.variant = StringUtils.isEmpty(strings[2]) ? "normal" : strings[2].toLowerCase();
    }

    public ModelIdentifier(String string) {
        this(0, splitModelIdentifier(string));
    }

    public ModelIdentifier(Identifier identifier, String variant) {
        this(identifier.toString(), variant);
    }

    public ModelIdentifier(String string, String string2) {
        this(0, splitModelIdentifier(string + '#' + (string2 == null ? "normal" : string2)));
    }

    protected static String[] splitModelIdentifier(String modelIdentifier) {
        String[] astring = new String[]{null, modelIdentifier, null};
        int i = modelIdentifier.indexOf(35);
        String s = modelIdentifier;
        if (i >= 0) {
            astring[2] = modelIdentifier.substring(i + 1, modelIdentifier.length());
            if (i > 1) {
                s = modelIdentifier.substring(0, i);
            }
        }

        System.arraycopy(Identifier.splitIdentifier(s), 0, astring, 0, 2);
        return astring;
    }

    public String getVariant() {
        return this.variant;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof ModelIdentifier && super.equals(object)) {
            ModelIdentifier modelidentifier = (ModelIdentifier)object;
            return this.variant.equals(modelidentifier.variant);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + this.variant.hashCode();
    }

    @Override
    public String toString() {
        return super.toString() + '#' + this.variant;
    }
}
