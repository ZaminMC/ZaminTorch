package net.zaminmc.torch.server.item;

import net.zaminmc.torch.item.ItemRegistry;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.util.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * The public item registry view (§27.3's registry family): the engine's
 * own frozen {@link BuiltinItems} set dressed as the {@link ItemRegistry}
 * interface — every read delegates to the frozen map (any thread).
 */
public final class ItemRegistryView implements ItemRegistry {

    @Override
    public Collection<ItemType> all() {
        return BuiltinItems.all();
    }

    @Override
    public Optional<ItemType> byId(Identifier identifier) {
        return BuiltinItems.lookup(identifier);
    }
}
