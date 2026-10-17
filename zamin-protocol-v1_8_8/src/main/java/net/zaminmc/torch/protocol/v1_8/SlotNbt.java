package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;

/**
 * The slot encoding's optional NBT compound (protocol 47): the engine models
 * exactly one entry — the historical {@code tag.display.Name} anvil rename —
 * and this class is its only wire writer (and the test client's reader).
 *
 * <p>Layout, community-verified against the real client's parser and vanilla
 * {@code PacketBuffer.writeItemStackToBuffer}: the slot's NBT marker byte is
 * the root tag's TYPE byte — {@code 0} (TAG_End) when the stack carries no
 * compound, otherwise {@code 10} (TAG_Compound) followed by the full payload:
 * a short-length root name (empty), then the compounds/strings in vanilla NBT
 * order, then TAG_End. Strings inside NBT are <b>short-length modified
 * UTF-8</b> (Java's {@code DataOutput.writeUTF}: CESU-8 with {@code U+0000}
 * as {@code C0 80}) — a different encoding from the protocol's varint-length
 * UTF-8 strings, which is exactly the kind of detail the real client notices.</p>
 */
final class SlotNbt {

    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;

    private SlotNbt() {
    }

    /** @return whether a stack's display name produces NBT (blank names never do). */
    static boolean hasPayload(String displayName) {
        return displayName != null && !displayName.isEmpty();
    }

    /** @return whether a stack carries any NBT the engine models (the Wave G arms included). */
    static boolean hasPayload(net.zaminmc.torch.item.ItemStack stack) {
        return hasPayload(stack.displayName())
                || (stack.enchantments() != null && !stack.enchantments().isEmpty())
                || stack.unbreakable()
                || stack.repairCost() > 0
                || stack.hideFlags() > 0
                || (stack.lore() != null && !stack.lore().isEmpty())
                || (stack.blockEntityTag() != null && !stack.blockEntityTag().isEmpty())
                || (stack.canDestroy() != null && !stack.canDestroy().isEmpty())
                || (stack.canPlaceOn() != null && !stack.canPlaceOn().isEmpty())
                || stack.displayColor() != null
                || stack.attributeModifiers() != null // present-even-empty (Wave J's wipe)
                || stack.skullOwner() != null; // the player head's placement row (Wave K)
    }

    /**
     * Writes the full slot NBT for a stack: the root TAG_Compound marker
     * (which doubles as the slot's "NBT present" signal) plus the payload
     * {@code {ench:[{id:short,lvl:short}...], display:{Name:"…", Lore:["…"]},
     * Unbreakable:byte, RepairCost:int, HideFlags:int}} — the enchantments
     * first, the display after, then Slice 94's Wave G scalars (vanilla NBT
     * is a name-keyed map; the order is the engine's, the client reads
     * either).
     */
    static void writeFor(ByteBuf out, net.zaminmc.torch.item.ItemStack stack) {
        var enchantments = stack.enchantments();
        boolean named = hasPayload(stack.displayName());
        boolean lored = stack.lore() != null && !stack.lore().isEmpty();
        boolean enchanted = enchantments != null && !enchantments.isEmpty();
        // The enchanted book's own NBT shape (Slice 12c): the book stores
        // its enchantments under StoredEnchantments, never the item "ench"
        // — the reference's EnchantedBookItem.addEnchantment family.
        boolean stored = enchanted
                && stack.type().identifier().toString().equals("minecraft:enchanted_book");
        out.writeByte(TAG_COMPOUND);   // the marker itself: root compound type
        out.writeShort(0);             // root name: empty
        if (enchanted) {
            // tag.ench (or StoredEnchantments for the book): a list of
            // anonymous id/lvl compounds (the vanilla shape — list elements
            // carry no name).
            out.writeByte(TAG_LIST);
            writeShortString(out, stored ? "StoredEnchantments" : "ench");
            out.writeByte(TAG_COMPOUND); // the element type
            out.writeInt(enchantments.size());
            for (var entry : enchantments.entrySet()) {
                out.writeByte(TAG_SHORT);
                writeShortString(out, "id");
                out.writeShort(entry.getKey());
                out.writeByte(TAG_SHORT);
                writeShortString(out, "lvl");
                out.writeShort(entry.getValue());
                out.writeByte(TAG_END);    // end of the element compound
            }
        }
        if (named || lored || stack.displayColor() != null) {
            out.writeByte(TAG_COMPOUND); // tag.display
            writeShortString(out, "display");
            {
                if (named) {
                    out.writeByte(TAG_STRING); // tag.display.Name
                    writeShortString(out, "Name");
                    writeShortString(out, stack.displayName());
                }
                if (lored) {
                    // tag.display.Lore (the reference's getList("Lore", 8):
                    // a TAG_LIST of TAG_STRING, the tooltip's own lines).
                    out.writeByte(TAG_LIST);
                    writeShortString(out, "Lore");
                    out.writeByte(TAG_STRING); // the element type
                    out.writeInt(stack.lore().size());
                    for (String line : stack.lore()) {
                        writeShortString(out, line);
                    }
                }
                if (stack.displayColor() != null) {
                    // display.color (the leather tint, an INT row).
                    out.writeByte(TAG_INT);
                    writeShortString(out, "color");
                    out.writeInt(stack.displayColor());
                }
                out.writeByte(TAG_END);    // end of tag.display
            }
        }
        // The Wave G scalars — the reference's own rows, each riding only
        // when the model carries it (absent-means-default).
        if (stack.unbreakable()) {
            out.writeByte(TAG_BYTE);   // tag.Unbreakable
            writeShortString(out, "Unbreakable");
            out.writeByte(1);
        }
        if (stack.repairCost() > 0) {
            out.writeByte(TAG_INT);    // tag.RepairCost
            writeShortString(out, "RepairCost");
            out.writeInt(stack.repairCost());
        }
        if (stack.hideFlags() > 0) {
            out.writeByte(TAG_INT);    // tag.HideFlags
            writeShortString(out, "HideFlags");
            out.writeInt(stack.hideFlags());
        }
        // The raw compound (Wave H2 — tag.BlockEntityTag, the whole
        // subtree walked from the model's boxed form, every NBT family
        // of the tree's own: the api module cannot see a tag type, so the
        // wire writer walks the boxes directly).
        if (stack.blockEntityTag() != null && !stack.blockEntityTag().isEmpty()) {
            out.writeByte(TAG_COMPOUND); // tag.BlockEntityTag
            writeShortString(out, "BlockEntityTag");
            writeBoxedCompound(out, stack.blockEntityTag());
        }
        // The adventure gates' lists (Wave I — the Lore walk's own shape:
        // a TAG_LIST of TAG_STRING over the block keys).
        if (stack.canDestroy() != null && !stack.canDestroy().isEmpty()) {
            out.writeByte(TAG_LIST);
            writeShortString(out, "CanDestroy");
            out.writeByte(TAG_STRING);
            out.writeInt(stack.canDestroy().size());
            for (String key : stack.canDestroy()) {
                writeShortString(out, key);
            }
        }
        if (stack.canPlaceOn() != null && !stack.canPlaceOn().isEmpty()) {
            out.writeByte(TAG_LIST);
            writeShortString(out, "CanPlaceOn");
            out.writeByte(TAG_STRING);
            out.writeInt(stack.canPlaceOn().size());
            for (String key : stack.canPlaceOn()) {
                writeShortString(out, key);
            }
        }
        // The attribute modifiers (Wave J — the reference's
        // getAttributeModifiers family): a TAG_LIST of TAG_COMPOUND, each
        // element carrying AttributeName/Name/Amount/Operation/UUIDMost/
        // UUIDLeast. PRESENCE gates the row — an explicitly empty list
        // (the wipe, or an all-skipped read) rides as the type-0 empty
        // list, exactly vanilla's own empty-list wire form.
        if (stack.attributeModifiers() != null) {
            var modifiers = stack.attributeModifiers();
            out.writeByte(TAG_LIST);
            writeShortString(out, "AttributeModifiers");
            out.writeByte(modifiers.isEmpty() ? TAG_END : TAG_COMPOUND);
            out.writeInt(modifiers.size());
            for (var modifier : modifiers) {
                out.writeByte(TAG_STRING); // AttributeName
                writeShortString(out, "AttributeName");
                writeShortString(out, modifier.attributeName());
                out.writeByte(TAG_STRING); // Name
                writeShortString(out, "Name");
                writeShortString(out, modifier.name());
                out.writeByte(TAG_DOUBLE);  // Amount
                writeShortString(out, "Amount");
                out.writeDouble(modifier.amount());
                out.writeByte(TAG_INT);     // Operation
                writeShortString(out, "Operation");
                out.writeInt(modifier.operation());
                out.writeByte(TAG_LONG);    // UUIDMost
                writeShortString(out, "UUIDMost");
                out.writeLong(modifier.uuid().getMostSignificantBits());
                out.writeByte(TAG_LONG);    // UUIDLeast
                writeShortString(out, "UUIDLeast");
                out.writeLong(modifier.uuid().getLeastSignificantBits());
                out.writeByte(TAG_END);     // end of the element compound
            }
        }
        // The player head's SkullOwner (Wave K — the placement consult's
        // own row, the compound form): Name/Id/Properties rows, the same
        // walk the skull tile's Owner compound rides.
        if (stack.skullOwner() != null) {
            var profile = stack.skullOwner();
            out.writeByte(TAG_COMPOUND);
            writeShortString(out, "SkullOwner");
            if (profile.name() != null && !profile.name().isEmpty()) {
                out.writeByte(TAG_STRING);
                writeShortString(out, "Name");
                writeShortString(out, profile.name());
            }
            if (profile.id() != null) {
                out.writeByte(TAG_STRING);
                writeShortString(out, "Id");
                writeShortString(out, profile.id().toString());
            }
            if (profile.properties() != null && !profile.properties().isEmpty()) {
                out.writeByte(TAG_COMPOUND);
                writeShortString(out, "Properties");
                for (var keyRow : profile.properties().entrySet()) {
                    out.writeByte(TAG_LIST);
                    writeShortString(out, keyRow.getKey());
                    out.writeByte(TAG_COMPOUND);
                    out.writeInt(keyRow.getValue().size());
                    for (var property : keyRow.getValue()) {
                        out.writeByte(TAG_STRING);
                        writeShortString(out, "Value");
                        writeShortString(out, property.value());
                        if (property.signature() != null) {
                            out.writeByte(TAG_STRING);
                            writeShortString(out, "Signature");
                            writeShortString(out, property.signature());
                        }
                        out.writeByte(TAG_END);
                    }
                }
                out.writeByte(TAG_END);
            }
            out.writeByte(TAG_END);
        }
        out.writeByte(TAG_END);        // end of root
    }

    /**
     * Skips one NBT payload in a buffer whose position sits ON the marker
     * byte. Used by test clients: a {@code 0} marker is nothing; any other
     * type byte starts a full compound whose structure is parsed (values
     * skipped) so the reader lands exactly after it.
     */
    static void skip(ByteBuf in) {
        int type = in.readUnsignedByte();
        if (type == TAG_END) {
            return; // no NBT
        }
        if (type != TAG_COMPOUND) {
            throw new IllegalArgumentException("Slot NBT must root at a compound: " + type);
        }
        skipShortString(in); // root name
        skipCompoundPayload(in);
    }

    /**
     * Reads the display name out of a slot NBT payload (buffer positioned ON
     * the marker byte), or null when the payload is absent or carries no
     * {@code display.Name}. Structural NBT parsing, values otherwise ignored.
     */
    static String readDisplayName(ByteBuf in) {
        int type = in.readUnsignedByte();
        if (type == TAG_END) {
            return null; // no NBT
        }
        if (type != TAG_COMPOUND) {
            throw new IllegalArgumentException("Slot NBT must root at a compound: " + type);
        }
        skipShortString(in); // root name
        return readCompoundForName(in);
    }

    private static String readCompoundForName(ByteBuf in) {
        String found = null;
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return found;
            }
            String name = readShortString(in);
            if (type == TAG_COMPOUND) {
                String nested = readCompoundForName(in);
                if ("display".equals(name)) {
                    found = nested; // display.Name lands here
                }
            } else if (type == TAG_STRING) {
                String value = readShortString(in);
                if ("Name".equals(name)) {
                    found = value;
                }
            } else {
                skipPayload(in, type);
            }
        }
    }

    /**
     * Reads the tag.BlockEntityTag subtree out of a slot NBT payload
     * (buffer positioned ON the marker byte), in its boxed form, or null
     * when the payload is absent or carries no compound row. The test
     * client's reader for the Wave H2 arm — every family parsed, the
     * boxed tree mirrors the writer's walk.
     */
    static java.util.Map<String, Object> readBlockEntityTag(ByteBuf in) {
        int type = in.readUnsignedByte();
        if (type == TAG_END) {
            return null; // no NBT
        }
        if (type != TAG_COMPOUND) {
            throw new IllegalArgumentException("Slot NBT must root at a compound: " + type);
        }
        skipShortString(in); // root name
        java.util.Map<String, Object> found = null;
        while (true) {
            int rowType = in.readUnsignedByte();
            if (rowType == TAG_END) {
                return found;
            }
            String name = readShortString(in);
            if ("BlockEntityTag".equals(name) && rowType == TAG_COMPOUND) {
                if (found == null) {
                    found = parseCompoundPayload(in);
                } else {
                    skipCompoundPayload(in); // the first row wins (vanilla NBT is a name-keyed map)
                }
            } else {
                skipPayload(in, rowType);
            }
        }
    }

    // ------------------------------------------------------------------
    // The boxed-tree walk (Wave H2): the writer walks the model's boxes
    // directly, the reader parses every family back into boxes.
    // ------------------------------------------------------------------

    /** Writes one boxed compound's rows (the recursion's own end marker). */
    private static void writeBoxedCompound(ByteBuf out, java.util.Map<String, Object> rows) {
        for (java.util.Map.Entry<String, Object> row : rows.entrySet()) {
            Object value = row.getValue();
            if (value instanceof Byte b) {
                out.writeByte(TAG_BYTE);
                writeShortString(out, row.getKey());
                out.writeByte(b);
            } else if (value instanceof Short s) {
                out.writeByte(TAG_SHORT);
                writeShortString(out, row.getKey());
                out.writeShort(s);
            } else if (value instanceof Integer i) {
                out.writeByte(TAG_INT);
                writeShortString(out, row.getKey());
                out.writeInt(i);
            } else if (value instanceof Long l) {
                out.writeByte(TAG_LONG);
                writeShortString(out, row.getKey());
                out.writeLong(l);
            } else if (value instanceof Float f) {
                out.writeByte(TAG_FLOAT);
                writeShortString(out, row.getKey());
                out.writeFloat(f);
            } else if (value instanceof Double d) {
                out.writeByte(TAG_DOUBLE);
                writeShortString(out, row.getKey());
                out.writeDouble(d);
            } else if (value instanceof String str) {
                out.writeByte(TAG_STRING);
                writeShortString(out, row.getKey());
                writeShortString(out, str);
            } else if (value instanceof int[] array) {
                out.writeByte(TAG_INT_ARRAY);
                writeShortString(out, row.getKey());
                out.writeInt(array.length);
                for (int element : array) {
                    out.writeInt(element);
                }
            } else if (value instanceof java.util.List<?> list) {
                out.writeByte(TAG_LIST);
                writeShortString(out, row.getKey());
                byte elementType = list.isEmpty()
                        ? TAG_END // vanilla's empty list carries type 0
                        : wireTypeOf(list.get(0));
                out.writeByte(elementType);
                out.writeInt(list.size());
                for (Object element : list) {
                    writeBoxedPayload(out, elementType, element);
                }
            } else if (value instanceof java.util.Map<?, ?> nested) {
                out.writeByte(TAG_COMPOUND);
                writeShortString(out, row.getKey());
                //noinspection unchecked
                writeBoxedCompound(out, (java.util.Map<String, Object>) nested);
            } else {
                throw new IllegalArgumentException("Unsupported boxed NBT value: "
                        + value.getClass().getName());
            }
        }
        out.writeByte(TAG_END); // the compound's own end
    }

    /** One list element's payload (the element type already announced). */
    private static void writeBoxedPayload(ByteBuf out, byte type, Object value) {
        switch (type) {
            case TAG_BYTE -> out.writeByte((Byte) value);
            case TAG_SHORT -> out.writeShort((Short) value);
            case TAG_INT -> out.writeInt((Integer) value);
            case TAG_LONG -> out.writeLong((Long) value);
            case TAG_FLOAT -> out.writeFloat((Float) value);
            case TAG_DOUBLE -> out.writeDouble((Double) value);
            case TAG_STRING -> writeShortString(out, (String) value);
            case TAG_INT_ARRAY -> {
                int[] array = (int[]) value;
                out.writeInt(array.length);
                for (int element : array) {
                    out.writeInt(element);
                }
            }
            case TAG_LIST -> {
                java.util.List<?> nested = (java.util.List<?>) value;
                byte nestedType = nested.isEmpty()
                        ? TAG_END
                        : wireTypeOf(nested.get(0));
                out.writeByte(nestedType);
                out.writeInt(nested.size());
                for (Object element : nested) {
                    writeBoxedPayload(out, nestedType, element);
                }
            }
            case TAG_COMPOUND -> //noinspection unchecked
                    writeBoxedCompound(out, (java.util.Map<String, Object>) value);
            default -> throw new IllegalArgumentException("Unsupported list element type: " + type);
        }
    }

    /** The boxed value's wire family byte. */
    private static byte wireTypeOf(Object value) {
        if (value instanceof Byte) {
            return TAG_BYTE;
        }
        if (value instanceof Short) {
            return TAG_SHORT;
        }
        if (value instanceof Integer) {
            return TAG_INT;
        }
        if (value instanceof Long) {
            return TAG_LONG;
        }
        if (value instanceof Float) {
            return TAG_FLOAT;
        }
        if (value instanceof Double) {
            return TAG_DOUBLE;
        }
        if (value instanceof String) {
            return TAG_STRING;
        }
        if (value instanceof int[]) {
            return TAG_INT_ARRAY;
        }
        if (value instanceof java.util.List) {
            return TAG_LIST;
        }
        if (value instanceof java.util.Map) {
            return TAG_COMPOUND;
        }
        throw new IllegalArgumentException("Unsupported boxed NBT value: "
                + value.getClass().getName());
    }

    /** Parses one compound's rows into boxes (the reader's own walk). */
    private static java.util.Map<String, Object> parseCompoundPayload(ByteBuf in) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return out;
            }
            String name = readShortString(in);
            out.put(name, parsePayload(in, type));
        }
    }

    /** One payload's boxed form (the writer's inverse). */
    private static Object parsePayload(ByteBuf in, int type) {
        switch (type) {
            case TAG_BYTE -> { return in.readByte(); }
            case TAG_SHORT -> { return in.readShort(); }
            case TAG_INT -> { return in.readInt(); }
            case TAG_LONG -> { return in.readLong(); }
            case TAG_FLOAT -> { return in.readFloat(); }
            case TAG_DOUBLE -> { return in.readDouble(); }
            case TAG_STRING -> { return readShortString(in); }
            case TAG_LIST -> {
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                java.util.List<Object> elements = new java.util.ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    elements.add(parsePayload(in, elementType));
                }
                return java.util.List.copyOf(elements);
            }
            case TAG_INT_ARRAY -> {
                int length = in.readInt();
                int[] values = new int[length];
                for (int i = 0; i < length; i++) {
                    values[i] = in.readInt();
                }
                return values;
            }
            case TAG_COMPOUND -> { return parseCompoundPayload(in); }
            default -> throw new IllegalArgumentException("Unknown NBT tag type: " + type);
        }
    }

    private static void skipCompoundPayload(ByteBuf in) {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == TAG_END) {
                return;
            }
            skipShortString(in); // the entry's name
            skipPayload(in, type);
        }
    }

    private static void skipPayload(ByteBuf in, int type) {
        switch (type) {
            case 1 -> in.skipBytes(1);      // byte
            case 2 -> in.skipBytes(2);      // short
            case 3 -> in.skipBytes(4);      // int
            case 4 -> in.skipBytes(8);      // long
            case 5 -> in.skipBytes(4);      // float
            case 6 -> in.skipBytes(8);      // double
            case 7 -> {                     // byte array
                int length = in.readInt();
                in.skipBytes(length);
            }
            case 8 -> skipShortString(in);  // string
            case 9 -> {                     // list
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                for (int i = 0; i < length; i++) {
                    skipPayload(in, elementType);
                }
            }
            case 10 -> skipCompoundPayload(in);
            case 11 -> {                    // int array
                int length = in.readInt();
                in.skipBytes(length * 4);
            }
            case 12 -> {                    // long array
                int length = in.readInt();
                in.skipBytes(length * 8);
            }
            default -> throw new IllegalArgumentException("Unknown NBT tag type: " + type);
        }
    }

    /**
     * One NBT name/value string (Slice 18e: package-shared with the
     * spawner tile writer): the short-length modified UTF-8 — Java's
     * {@code DataOutput.writeUTF} shape, NOT the protocol's varint
     * strings.
     */
    static void writeShortString(ByteBuf out, String value) {
        byte[] encoded = ModifiedUtf8.encode(value);
        if (encoded.length > Short.MAX_VALUE) {
            throw new IllegalArgumentException("NBT string too long: " + encoded.length);
        }
        out.writeShort(encoded.length);
        out.writeBytes(encoded);
    }

    private static void skipShortString(ByteBuf in) {
        int length = in.readUnsignedShort();
        in.skipBytes(length);
    }

    private static String readShortString(ByteBuf in) {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return ModifiedUtf8.decode(bytes);
    }

    /**
     * Modified UTF-8 (Java {@code DataOutput.writeUTF} semantics): the exact
     * string encoding the historical NBT reader expects — ASCII as-is, Latin
     * ranges in 2-3 bytes, supplementary characters as CESU-8 surrogate pairs,
     * {@code U+0000} as {@code C0 80}.
     */
    static final class ModifiedUtf8 {

        private ModifiedUtf8() {
        }

        static byte[] encode(String value) {
            int length = value.length();
            byte[] out = new byte[bound(length)];
            int written = 0;
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                if (c > 0 && c <= 0x7F) {
                    out[written++] = (byte) c;
                } else if (c <= 0x7FF) {
                    out[written++] = (byte) (0xC0 | (c >> 6));
                    out[written++] = (byte) (0x80 | (c & 0x3F));
                } else {
                    out[written++] = (byte) (0xE0 | (c >> 12));
                    out[written++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                    out[written++] = (byte) (0x80 | (c & 0x3F));
                }
            }
            return java.util.Arrays.copyOf(out, written);
        }

        static String decode(byte[] bytes) {
            StringBuilder out = new StringBuilder(bytes.length);
            for (int i = 0; i < bytes.length; ) {
                int b = bytes[i++] & 0xFF;
                if (b < 0x80) {
                    out.append((char) b);
                } else if ((b & 0xE0) == 0xC0) {
                    int b2 = bytes[i++] & 0xFF;
                    out.append((char) (((b & 0x1F) << 6) | (b2 & 0x3F)));
                } else {
                    int b2 = bytes[i++] & 0xFF;
                    int b3 = bytes[i++] & 0xFF;
                    out.append((char) (((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F)));
                }
            }
            return out.toString();
        }

        /** Upper bound of the encoding's byte length (every char worst-case 3 bytes). */
        private static int bound(int chars) {
            long bytes = (long) chars * 3 + 2;
            if (bytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("String encodes too large: " + chars);
            }
            return (int) bytes;
        }
    }
}
