package net.zaminmc.torch.bukkit.server;

import net.zaminmc.torch.bukkit.BukkitAdapter;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.CommandException;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;

import java.util.logging.Logger;

/**
 * The console's legacy sender view: the one route through the engine's own
 * command path (§30.3's "one command path" law — the console never grows a
 * second dispatcher), the operator arms bridged to the console's own
 * level-4 authority.
 */
public final class TorchConsoleSender implements ConsoleCommandSender {

    private final BukkitAdapter adapter;
    private final PermissibleBase permissible;

    public TorchConsoleSender(BukkitAdapter adapter) {
        this.adapter = adapter;
        this.permissible = new PermissibleBase(this) {
            @Override
            public boolean hasPermission(Permission perm) {
                return true; // the console's own authority
            }

            @Override
            public boolean hasPermission(String name) {
                return true;
            }
        };
    }

    @Override
    public void sendMessage(String message) {
        Logger.getLogger("net.zaminmc.torch.bukkit").info(ChatColor.stripColor(message));
    }

    @Override
    public void sendRawMessage(String message) {
        sendMessage(message); // the console's own echo path
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String message : messages) {
            sendMessage(message);
        }
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
        return permissible.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return permissible.isPermissionSet(perm);
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
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin, String name, boolean value) {
        return permissible.addAttachment(plugin, name, value);
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin) {
        return permissible.addAttachment(plugin);
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin, String name, boolean value, int ticks) {
        return permissible.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin, int ticks) {
        return permissible.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(org.bukkit.permissions.PermissionAttachment attachment) {
        permissible.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        permissible.recalculatePermissions();
    }

    @Override
    public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
        return permissible.getEffectivePermissions();
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
    public void dispatch(String line) throws CommandException {
        adapter.engine().consoleCommand(line);
    }
}
