package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Identifier;

/**
 * The public identity and observation surface of one engine entity.
 *
 * <p>Handles represent the authoritative engine object (architecture
 * §27.5): a handle is not a copy of state — it resolves to the live
 * entity. Live reads on the subtype interfaces require the world's
 * simulation context; {@link #snapshot()} captures an immutable,
 * any-thread-consumable view instead.</p>
 */
public interface Entity {

    /** The engine id (stable for the entity's lifetime, never reused). */
    int entityId();

    /** The entity kind's canonical identifier ({@code minecraft:pig}). */
    Identifier typeIdentifier();

    /**
     * Captures an immutable point-in-time view of the entity.
     *
     * <p>Consistent when captured on the entity's simulation context
     * (inside {@code World.execute} — the documented owner path);
     * off-owner captures are best-effort and may tear across fields.
     * The snapshot itself is freely consumable from any thread.</p>
     */
    EntitySnapshot snapshot();
}
