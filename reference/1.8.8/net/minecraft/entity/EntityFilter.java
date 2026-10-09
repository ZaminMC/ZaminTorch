package net.minecraft.entity;

import com.google.common.base.Predicate;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public final class EntityFilter {
    public static final Predicate<Entity> ALIVE = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity.isAlive();
        }
    };
    public static final Predicate<Entity> NOT_RIDING = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity.isAlive() && entity.rider == null && entity.vehicle == null;
        }
    };
    public static final Predicate<Entity> INVENTORY = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity instanceof Inventory && entity.isAlive();
        }
    };
    public static final Predicate<Entity> NOT_SPECTATOR = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return !(entity instanceof PlayerEntity) || !((PlayerEntity)entity).isSpectator();
        }
    };

    public static class CanPickUpItemsFilter implements Predicate<Entity> {
        private final ItemStack item;

        public CanPickUpItemsFilter(ItemStack item) {
            this.item = item;
        }

        public boolean apply(Entity entity) {
            if (!entity.isAlive()) {
                return false;
            } else if (!(entity instanceof LivingEntity)) {
                return false;
            } else {
                LivingEntity livingentity = (LivingEntity)entity;
                if (livingentity.getEquipment(MobEntity.getEquipmentSlot(this.item)) != null) {
                    return false;
                } else {
                    return livingentity instanceof MobEntity
                        ? ((MobEntity)livingentity).canPickupLoot()
                        : livingentity instanceof ArmorStandEntity || livingentity instanceof PlayerEntity;
                }
            }
        }
    }
}
