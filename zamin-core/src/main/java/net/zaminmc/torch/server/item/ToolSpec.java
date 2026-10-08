package net.zaminmc.torch.server.item;

import java.util.Objects;

/**
 * What a tool item is, beyond being an item: its class and material.
 * Immutable value; identity of the tool stays with the {@link net.zaminmc.torch.item.ItemType}.
 */
public record ToolSpec(ToolClass toolClass, ToolMaterial material) {

    public ToolSpec {
        Objects.requireNonNull(toolClass, "toolClass");
        Objects.requireNonNull(material, "material");
    }
}
