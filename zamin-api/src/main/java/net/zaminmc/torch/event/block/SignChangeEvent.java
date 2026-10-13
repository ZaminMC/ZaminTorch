package net.zaminmc.torch.event.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

import java.util.UUID;

/**
 * A player submitted new sign text (§27.7's observe/cancel/modify gate
 * over the sign editor — the Update Sign wire path, the editor's four
 * lines).
 *
 * <p>Fires on the world's simulation context BEFORE the text commits:
 * the sign still carries its old lines. Cancelling keeps the old text —
 * the sign never changes and no viewer replay leaves the wire. The
 * event's lines are mutable: a handler may rewrite them (sanitize,
 * translate, decorate) and the walk applies the handler's answer when
 * not cancelled.</p>
 */
public final class SignChangeEvent implements Event, Cancellable {

    private final String worldName;
    private final BlockPosition position;
    private final UUID editorId;
    private final String editorName;
    private String[] lines;
    private boolean cancelled;

    /**
     * @param lines the four submitted lines (the event holds the array —
     *              {@link #setLines} replaces it wholesale)
     */
    public SignChangeEvent(String worldName, BlockPosition position,
                           UUID editorId, String editorName, String[] lines) {
        this.worldName = worldName;
        this.position = position;
        this.editorId = editorId;
        this.editorName = editorName;
        this.lines = lines;
    }

    /** @return the world the sign stands in (by name — the handle-free form). */
    public String worldName() {
        return worldName;
    }

    /** @return the sign's position. */
    public BlockPosition position() {
        return position;
    }

    /** @return the editing player's uuid. */
    public UUID editorId() {
        return editorId;
    }

    /** @return the editing player's name. */
    public String editorName() {
        return editorName;
    }

    /** @return the submitted lines (the live array — the walk applies the final state). */
    public String[] lines() {
        return lines;
    }

    /**
     * Replaces the submitted lines wholesale. The walk applies this
     * answer when the event is not cancelled; a null or wrong-length
     * array is ignored (the submitted lines stand).
     *
     * @return whether the replacement was accepted
     */
    public boolean setLines(String[] newLines) {
        if (newLines == null || newLines.length != this.lines.length) {
            return false;
        }
        this.lines = newLines;
        return true;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String eventName() {
        return "SignChange";
    }
}
