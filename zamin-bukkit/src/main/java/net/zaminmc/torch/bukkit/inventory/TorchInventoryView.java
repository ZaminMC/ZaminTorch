package net.zaminmc.torch.bukkit.inventory;

import net.zaminmc.torch.server.player.PlayerSession;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

/**
 * The legacy {@link InventoryView} over one open container window (the
 * slice-4 inventory family): the container's view on top, the player's own
 * inventory on the bottom, the player themselves — the engine's own
 * open-window state (the §27 window family) dressed in the legacy shape
 * the {@code InventoryOpenEvent} constructor demands.
 *
 * <p><b>The inherited arms:</b> {@code setItem}/{@code getItem} route the
 * API's own raw-slot walk (the top's slots first, then the bottom's);
 * {@code getTitle()} answers the top inventory's own title;
 * {@code countSlots()} the two sizes' sum; {@code getCursor()}/{@code
 * setCursor()} delegate to the player's cursor arms (the engine's own
 * cursor surface, bridged on the player view this slice); {@code close()}
 * delegates to {@code player.closeInventory()} (the engine's own close
 * walk, the same surface the open event's cancellation rides).</p>
 *
 * <p><b>The type:</b> {@link #getType()} answers the TOP inventory's type
 * (the legacy view-type law — a chest window's view type is CHEST).</p>
 */
public final class TorchInventoryView extends InventoryView {

    private final Inventory top;
    private final Inventory bottom;
    private final HumanEntity player;

    public TorchInventoryView(Inventory top, TorchPlayerInventory bottom,
            HumanEntity player) {
        this.top = top;
        this.bottom = bottom;
        this.player = player;
    }

    /** The view over one session's currently-open modeled container window. */
    public static TorchInventoryView of(
            net.zaminmc.torch.bukkit.BukkitAdapter adapter,
            PlayerSession session, Inventory top) {
        // The bottom routes through the player handle's own cached view —
        // the legacy identity law (view.getBottomInventory() ==
        // player.getInventory()).
        return new TorchInventoryView(top,
                (TorchPlayerInventory) adapter.player(session).getInventory(),
                adapter.player(session));
    }

    @Override
    public Inventory getTopInventory() {
        return top;
    }

    @Override
    public Inventory getBottomInventory() {
        return bottom;
    }

    @Override
    public HumanEntity getPlayer() {
        return player;
    }

    @Override
    public InventoryType getType() {
        return top.getType();
    }

    @Override
    public String toString() {
        InventoryHolder holder = top.getHolder();
        return "TorchInventoryView{type=" + getType() + ", top=" + top.getName()
                + ", holder=" + holder + ", player=" + player.getName() + "}";
    }
}
