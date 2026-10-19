package net.zaminmc.torch.bukkit.server;

import net.zaminmc.torch.bukkit.BukkitAdapter;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Set;
import java.util.logging.Logger;

/**
 * The console's legacy sender view: the one route through the engine's own
 * command path (§30.3's "one command path" law — the console never grows a
 * second dispatcher), the operator arms bridged to the console's own
 * level-4 authority.
 *
 * <p><b>No {@code PermissibleBase}</b>: the API's own helper resolves the
 * global {@code Bukkit.getServer()} at construction time (its
 * recalculate walk), which the adapter's own wiring order cannot promise —
 * the arms are implemented directly instead (the console's authority is
 * total; the attachment registry is the honest no-op for it).</p>
 */
public final class TorchConsoleSender implements ConsoleCommandSender {

    private final BukkitAdapter adapter;

    public TorchConsoleSender(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void sendMessage(String message) {
        Logger.getLogger("net.zaminmc.torch.bukkit").info(ChatColor.stripColor(message));
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendRawMessage(String message) {
        sendMessage(message); // the console's own echo path
    }

    @Override
    public Server getServer() {
        return adapter.bukkitServer();
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public boolean isPermissionSet(String name) {
        return false;
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return false;
    }

    @Override
    public boolean hasPermission(String name) {
        return true; // the console's own authority (level 4)
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return true;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        throw adapter.unbridged("Console.addAttachment");
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        throw adapter.unbridged("Console.addAttachment");
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        throw adapter.unbridged("Console.addAttachment");
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        throw adapter.unbridged("Console.addAttachment");
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        throw adapter.unbridged("Console.removeAttachment");
    }

    @Override
    public void recalculatePermissions() {
        // The console's authority is total — nothing to recalculate.
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return Set.of();
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
        throw new UnsupportedOperationException("Console cannot change operator status");
    }

    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public void acceptConversationInput(String input) {
        throw adapter.unbridged("Console.acceptConversationInput");
    }

    @Override
    public boolean beginConversation(org.bukkit.conversations.Conversation conversation) {
        throw adapter.unbridged("Console.beginConversation");
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
        // No conversation family in this slice — the no-op is the honest
        // answer (nothing to abandon).
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation,
            org.bukkit.conversations.ConversationAbandonedEvent details) {
        // Same no-op (the same honest rule).
    }

    /** The engine's own console route (the ONE command path — §30.3). */
    public void dispatch(String line) {
        adapter.engine().consoleCommand(line);
    }
}
