package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;

/**
 * The no-op base over the manager's listener surface: every abstract arm
 * answered empty, so a seat (or an embedding host) captures exactly the
 * family it needs and inherits the rest silent. The manager's own
 * {@link MobManager.Listener} defaults stay untouched — this base only
 * fills the required arms.
 */
public class NoOpMobListener implements MobManager.Listener {
    @Override public void onMobSpawned(MobEntity mob) { }

    @Override public void onMobMoved(MobEntity mob, PlayerSession rider) { }

    @Override public void onMobHurt(MobEntity mob) { }

    @Override public void onMobDied(MobEntity mob) { }

    @Override public void onMobRemoved(MobEntity mob, String reason) { }

    @Override public void onMobAttackedPlayer(MobEntity mob, PlayerSession target,
            float damage) { }

    @Override public void onMobSound(MobEntity mob, String soundName) { }

    @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }

    @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }

    @Override public void onMobSheared(MobEntity mob, int woolCount) { }

    @Override public void onMobCoatRegrown(MobEntity mob) { }

    @Override public void onMobExploded(MobEntity mob) { }
}
