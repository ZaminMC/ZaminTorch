package net.zaminmc.torch.event.entity;

import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.event.Event;

/**
 * A living entity — a mob — died (the §27.7 post-commit observation).
 *
 * <p>Fires on the engine context at the death walk: the health reached
 * zero, the death animation starts, the loot and the experience release
 * right after. The event is not cancellable — the death is already
 * authoritative; it is the observation point for kill counters, quest
 * hooks and statistics.</p>
 */
public final class MobDeathEvent implements Event {

    private final LivingEntity entity;

    public MobDeathEvent(LivingEntity entity) {
        this.entity = entity;
    }

    /** The dead entity (the live handle; the body resolves on the live tick). */
    public LivingEntity entity() {
        return entity;
    }
}
