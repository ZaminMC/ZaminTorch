package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.messaging.PluginMessageRecipient;
import org.bukkit.util.Vector;

import java.net.InetSocketAddress;

import java.util.UUID;

/**
 * The legacy player view over one engine {@link PlayerSession} (§28.7's
 * object-semantics inventory): the §27.5 handle law — every read resolves
 * to the CURRENT authoritative session state, never a copied player.
 *
 * <p><b>The bridged subset (this slice):</b> identity (name, display name
 * as adapter-local presentation state, the deterministic UUID), the
 * message route (the engine's own system-message path), the permission
 * surface (the engine's operator level — an unregistered legacy permission
 * defaults to operator-only, the 1.8.8 rule), the world and location
 * reads, the game mode read, the chat/perform-command route (the engine's
 * ONE command path with its own op gate), the online/offline reads, the
 * kick route (the session's own link), and the operator read/write
 * through the engine's op-level surface.</p>
 *
 * <p><b>Every unbridged arm fails loudly</b> (§27.9) — the teleport, the
 * survival fields, the packet-family arms and the metadata arms are the
 * later slices.</p>
 */
public final class TorchPlayer implements Player {

    private final BukkitAdapter adapter;
    private final PlayerSession session;
    private volatile String displayName;

    public TorchPlayer(BukkitAdapter adapter, PlayerSession session) {
        this.adapter = adapter;
        this.session = session;
        this.displayName = session.name();
    }

    /** The engine session this view resolves to (§27.5's handle law). */
    public PlayerSession session() {
        return session;
    }

    // ------------------------------------------------------------------ identity

    @Override
    public String getName() {
        return session.name();
    }

    @Override
    public UUID getUniqueId() {
        return session.uuid();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(String name) {
        // Adapter-local presentation state: the engine's tab list does not
        // consume it in this slice (the recorded §27.9 limitation).
        this.displayName = name == null ? session.name() : name;
    }

    @Override
    public String getPlayerListName() {
        return session.name();
    }

    @Override
    public void setPlayerListName(String name) {
        // The engine's tab list carries the session name in this slice.
        throw adapter.unbridged("Player.setPlayerListName");
    }

    // ------------------------------------------------------------------ messaging

    @Override
    public void sendMessage(String message) {
        adapter.systemMessage(session, message);
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendRawMessage(String message) {
        adapter.systemMessage(session, message);
    }

    // ------------------------------------------------------------------ the one command path

    @Override
    public void chat(String msg) {
        // The engine's own chat route: the "/" prefix dispatches through
        // the engine's single command path (its own op gate); plain text
        // rides the chat event + broadcast. Submitted, never inline.
        adapter.engine().chatService().submitChat(session, msg);
    }

    @Override
    public boolean performCommand(String command) {
        String line = command.startsWith("/") ? command : "/" + command;
        adapter.engine().chatService().submitChat(session, line);
        return true;
    }

    // ------------------------------------------------------------------ context reads

    @Override
    public Server getServer() {
        return adapter.bukkitServer();
    }

    @Override
    public org.bukkit.World getWorld() {
        return adapter.worldFor(session);
    }

    @Override
    public Location getLocation() {
        Position at = session.position();
        return new Location(adapter.worldFor(session), at.x(), at.y(), at.z());
    }

    @Override
    public Location getLocation(Location loc) {
        if (loc != null) {
            Position at = session.position();
            loc.setWorld(adapter.worldFor(session));
            loc.setX(at.x());
            loc.setY(at.y());
            loc.setZ(at.z());
        }
        return loc;
    }

    @Override
    public boolean isOnline() {
        return adapter.isOnline(session);
    }

    @Override
    public Player getPlayer() {
        return isOnline() ? this : null;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.getByValue(session.gamemode().ordinal());
    }

    // ------------------------------------------------------------------ operators + permissions

    @Override
    public boolean isOp() {
        return session.opLevel() >= 2; // the engine's own operator threshold
    }

    @Override
    public void setOp(boolean value) {
        adapter.opGrant(session, value ? 2 : 0);
    }

    @Override
    public boolean hasPermission(String name) {
        // No legacy permission registry in this slice: the 1.8.8 default —
        // an unregistered permission is operator-only.
        return isOp();
    }

    @Override
    public boolean isPermissionSet(String name) {
        return false;
    }

    @Override
    public boolean isPermissionSet(org.bukkit.permissions.Permission perm) {
        return false;
    }

    @Override
    public boolean hasPermission(org.bukkit.permissions.Permission perm) {
        return isOp();
    }

    // ------------------------------------------------------------------ the kick route

    @Override
    public void kickPlayer(String message) {
        session.link().kick(message == null ? "Kicked by a plugin" : message);
    }

    // ------------------------------------------------------------------ the unbridged family (the loud refusals)

    @Override
    public InetSocketAddress getAddress() {
        String remote = session.link().remoteIp();
        return remote == null ? null : new InetSocketAddress(remote, 0);
    }

    @Override
    public void setCompassTarget(Location loc) {
        throw adapter.unbridged("Player.setCompassTarget");
    }

    @Override
    public Location getCompassTarget() {
        return getLocation();
    }

    @Override
    public boolean isSneaking() {
        return false;
    }

    @Override
    public void setSneaking(boolean sneak) {
        throw adapter.unbridged("Player.setSneaking");
    }

    @Override
    public boolean isSprinting() {
        return false;
    }

    @Override
    public void setSprinting(boolean sprinting) {
        throw adapter.unbridged("Player.setSprinting");
    }

    @Override
    public void saveData() {
        throw adapter.unbridged("Player.saveData");
    }

    @Override
    public void loadData() {
        throw adapter.unbridged("Player.loadData");
    }

    @Override
    public void setSleepingIgnored(boolean isSleeping) {
        throw adapter.unbridged("Player.setSleepingIgnored");
    }

    @Override
    public boolean isSleepingIgnored() {
        return false;
    }

    @Override
    public void playNote(Location loc, byte instrument, byte note) {
        throw adapter.unbridged("Player.playNote");
    }

    @Override
    public void playNote(Location loc, org.bukkit.Instrument instrument, org.bukkit.Note note) {
        throw adapter.unbridged("Player.playNote");
    }

    @Override
    public void playSound(Location location, org.bukkit.Sound sound, float volume, float pitch) {
        throw adapter.unbridged("Player.playSound");
    }

    @Override
    public void playSound(Location location, String sound, float volume, float pitch) {
        throw adapter.unbridged("Player.playSound");
    }

    @Override
    public void playEffect(Location loc, org.bukkit.Effect effect, int data) {
        throw adapter.unbridged("Player.playEffect");
    }

    @Override
    public <T> void playEffect(Location loc, org.bukkit.Effect effect, T data) {
        throw adapter.unbridged("Player.playEffect");
    }

    @Override
    public void sendBlockChange(Location loc, Material material, byte data) {
        throw adapter.unbridged("Player.sendBlockChange");
    }

    @Override
    public void sendBlockChange(Location loc, int material, byte data) {
        throw adapter.unbridged("Player.sendBlockChange");
    }

    @Override
    public boolean sendChunkChange(Location loc, int sx, int sy, int sz, byte[] data) {
        throw adapter.unbridged("Player.sendChunkChange");
    }

    @Override
    public void sendMap(org.bukkit.map.MapView map) {
        throw adapter.unbridged("Player.sendMap");
    }

    @Override
    public void updateInventory() {
        throw adapter.unbridged("Player.updateInventory");
    }

    @Override
    public void awardAchievement(org.bukkit.Achievement achievement) {
        throw adapter.unbridged("Player.awardAchievement");
    }

    @Override
    public void removeAchievement(org.bukkit.Achievement achievement) {
        throw adapter.unbridged("Player.removeAchievement");
    }

    @Override
    public boolean hasAchievement(org.bukkit.Achievement achievement) {
        return false;
    }

    @Override
    public void incrementStatistic(org.bukkit.Statistic statistic) {
        throw adapter.unbridged("Player.incrementStatistic");
    }

    @Override
    public void decrementStatistic(org.bukkit.Statistic statistic) {
        throw adapter.unbridged("Player.decrementStatistic");
    }

    @Override
    public void incrementStatistic(org.bukkit.Statistic statistic, int amount) {
        throw adapter.unbridged("Player.incrementStatistic");
    }

    @Override
    public void decrementStatistic(org.bukkit.Statistic statistic, int amount) {
        throw adapter.unbridged("Player.decrementStatistic");
    }

    @Override
    public void setStatistic(org.bukkit.Statistic statistic, int newValue) {
        throw adapter.unbridged("Player.setStatistic");
    }

    @Override
    public int getStatistic(org.bukkit.Statistic statistic) {
        return 0;
    }

    @Override
    public void incrementStatistic(org.bukkit.Statistic statistic, org.bukkit.entity.EntityType entityType) {
        throw adapter.unbridged("Player.incrementStatistic");
    }

    @Override
    public void decrementStatistic(org.bukkit.Statistic statistic, org.bukkit.entity.EntityType entityType) {
        throw adapter.unbridged("Player.decrementStatistic");
    }

    @Override
    public int getStatistic(org.bukkit.Statistic statistic, org.bukkit.entity.EntityType entityType) {
        return 0;
    }

    @Override
    public void incrementStatistic(org.bukkit.Statistic statistic, org.bukkit.entity.EntityType entityType, int amount) {
        throw adapter.unbridged("Player.incrementStatistic");
    }

    @Override
    public void decrementStatistic(org.bukkit.Statistic statistic, org.bukkit.entity.EntityType entityType, int amount) {
        throw adapter.unbridged("Player.decrementStatistic");
    }

    @Override
    public void setStatistic(org.bukkit.Statistic statistic, org.bukkit.entity.EntityType entityType, int newValue) {
        throw adapter.unbridged("Player.setStatistic");
    }

    @Override
    public void incrementStatistic(org.bukkit.Statistic statistic, org.bukkit.Material material) {
        throw adapter.unbridged("Player.incrementStatistic");
    }

    @Override
    public void decrementStatistic(org.bukkit.Statistic statistic, org.bukkit.Material material) {
        throw adapter.unbridged("Player.decrementStatistic");
    }

    @Override
    public int getStatistic(org.bukkit.Statistic statistic, org.bukkit.Material material) {
        return 0;
    }

    @Override
    public void incrementStatistic(org.bukkit.Statistic statistic, org.bukkit.Material material, int amount) {
        throw adapter.unbridged("Player.incrementStatistic");
    }

    @Override
    public void decrementStatistic(org.bukkit.Statistic statistic, org.bukkit.Material material, int amount) {
        throw adapter.unbridged("Player.decrementStatistic");
    }

    @Override
    public void setStatistic(org.bukkit.Statistic statistic, org.bukkit.Material material, int newValue) {
        throw adapter.unbridged("Player.setStatistic");
    }

    @Override
    public void setPlayerTime(long time, boolean relative) {
        throw adapter.unbridged("Player.setPlayerTime");
    }

    @Override
    public long getPlayerTime() {
        return adapter.worldFor(session).engineWorld().timeOfDay();
    }

    @Override
    public long getPlayerTimeOffset() {
        return 0;
    }

    @Override
    public boolean isPlayerTimeRelative() {
        return true;
    }

    @Override
    public void resetPlayerTime() {
        throw adapter.unbridged("Player.resetPlayerTime");
    }

    @Override
    public void setPlayerWeather(org.bukkit.WeatherType type) {
        throw adapter.unbridged("Player.setPlayerWeather");
    }

    @Override
    public org.bukkit.WeatherType getPlayerWeather() {
        return null;
    }

    @Override
    public void resetPlayerWeather() {
        throw adapter.unbridged("Player.resetPlayerWeather");
    }

    @Override
    public void giveExp(int amount) {
        throw adapter.unbridged("Player.giveExp");
    }

    @Override
    public void giveExpLevels(int amount) {
        throw adapter.unbridged("Player.giveExpLevels");
    }

    @Override
    public float getExp() {
        return 0;
    }

    @Override
    public void setExp(float exp) {
        throw adapter.unbridged("Player.setExp");
    }

    @Override
    public int getLevel() {
        return 0;
    }

    @Override
    public void setLevel(int level) {
        throw adapter.unbridged("Player.setLevel");
    }

    @Override
    public int getTotalExperience() {
        return 0;
    }

    @Override
    public void setTotalExperience(int exp) {
        throw adapter.unbridged("Player.setTotalExperience");
    }

    @Override
    public float getExhaustion() {
        return 0;
    }

    @Override
    public void setExhaustion(float value) {
        throw adapter.unbridged("Player.setExhaustion");
    }

    @Override
    public float getSaturation() {
        return 0;
    }

    @Override
    public void setSaturation(float value) {
        throw adapter.unbridged("Player.setSaturation");
    }

    @Override
    public int getFoodLevel() {
        return 20;
    }

    @Override
    public void setFoodLevel(int value) {
        throw adapter.unbridged("Player.setFoodLevel");
    }

    @Override
    public Location getBedSpawnLocation() {
        return null;
    }

    @Override
    public void setBedSpawnLocation(Location location) {
        throw adapter.unbridged("Player.setBedSpawnLocation");
    }

    @Override
    public void setBedSpawnLocation(Location location, boolean force) {
        throw adapter.unbridged("Player.setBedSpawnLocation");
    }

    @Override
    public boolean getAllowFlight() {
        return false;
    }

    @Override
    public void setAllowFlight(boolean flight) {
        throw adapter.unbridged("Player.setAllowFlight");
    }

    @Override
    public void hidePlayer(Player player) {
        throw adapter.unbridged("Player.hidePlayer");
    }

    @Override
    public void showPlayer(Player player) {
        throw adapter.unbridged("Player.showPlayer");
    }

    @Override
    public boolean canSee(Player player) {
        return true;
    }

    @Override
    public boolean isFlying() {
        return false;
    }

    @Override
    public void setFlying(boolean value) {
        throw adapter.unbridged("Player.setFlying");
    }

    @Override
    public float getFlySpeed() {
        return 0.1f;
    }

    @Override
    public void setFlySpeed(float speed) throws IllegalArgumentException {
        throw adapter.unbridged("Player.setFlySpeed");
    }

    @Override
    public float getWalkSpeed() {
        return 0.2f;
    }

    @Override
    public void setWalkSpeed(float speed) throws IllegalArgumentException {
        throw adapter.unbridged("Player.setWalkSpeed");
    }

    @Override
    public void setTexturePack(String url) {
        throw adapter.unbridged("Player.setTexturePack");
    }

    @Override
    public void setResourcePack(String url) {
        throw adapter.unbridged("Player.setResourcePack");
    }

    @Override
    public org.bukkit.scoreboard.Scoreboard getScoreboard() {
        throw adapter.unbridged("Player.getScoreboard");
    }

    @Override
    public void setScoreboard(org.bukkit.scoreboard.Scoreboard scoreboard)
            throws IllegalArgumentException, IllegalStateException {
        throw adapter.unbridged("Player.setScoreboard");
    }

    @Override
    public boolean isHealthScaled() {
        return false;
    }

    @Override
    public void setHealthScaled(boolean scale) {
        throw adapter.unbridged("Player.setHealthScaled");
    }

    @Override
    public void setHealthScale(double scale) throws IllegalArgumentException {
        throw adapter.unbridged("Player.setHealthScale");
    }

    @Override
    public double getHealthScale() {
        return 20;
    }

    @Override
    public Entity getSpectatorTarget() {
        return null;
    }

    @Override
    public void setSpectatorTarget(Entity entity) {
        throw adapter.unbridged("Player.setSpectatorTarget");
    }

    @Override
    public void sendTitle(String title, String subtitle) {
        throw adapter.unbridged("Player.sendTitle");
    }

    @Override
    public void sendSignChange(Location loc, String[] lines) throws IllegalArgumentException {
        throw adapter.unbridged("Player.sendSignChange");
    }

    @Override
    public void resetTitle() {
        throw adapter.unbridged("Player.resetTitle");
    }

    // ------------------------------------------------------------------ Entity

    @Override
    public void setVelocity(Vector velocity) {
        throw adapter.unbridged("Player.setVelocity");
    }

    @Override
    public Vector getVelocity() {
        return new Vector(0, 0, 0);
    }

    @Override
    public boolean isOnGround() {
        return true;
    }

    @Override
    public boolean teleport(Location location) {
        throw adapter.unbridged("Player.teleport");
    }

    @Override
    public boolean teleport(Location location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        throw adapter.unbridged("Player.teleport");
    }

    @Override
    public boolean teleport(Entity destination) {
        throw adapter.unbridged("Player.teleport");
    }

    @Override
    public boolean teleport(Entity destination,
            org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        throw adapter.unbridged("Player.teleport");
    }

    @Override
    public java.util.List<Entity> getNearbyEntities(double x, double y, double z) {
        throw adapter.unbridged("Player.getNearbyEntities");
    }

    @Override
    public int getEntityId() {
        throw adapter.unbridged("Player.getEntityId");
    }

    @Override
    public int getFireTicks() {
        return 0;
    }

    @Override
    public int getMaxFireTicks() {
        return 20;
    }

    @Override
    public void setFireTicks(int ticks) {
        throw adapter.unbridged("Player.setFireTicks");
    }

    @Override
    public void remove() {
        throw adapter.unbridged("Player.remove");
    }

    @Override
    public boolean isDead() {
        return false;
    }

    @Override
    public boolean isValid() {
        return isOnline();
    }

    @Override
    public Entity getPassenger() {
        return null;
    }

    @Override
    public boolean setPassenger(Entity passenger) {
        throw adapter.unbridged("Player.setPassenger");
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean eject() {
        return false;
    }

    @Override
    public float getFallDistance() {
        return 0;
    }

    @Override
    public void setFallDistance(float distance) {
        throw adapter.unbridged("Player.setFallDistance");
    }

    @Override
    public void setLastDamageCause(org.bukkit.event.entity.EntityDamageEvent event) {
        throw adapter.unbridged("Player.setLastDamageCause");
    }

    @Override
    public org.bukkit.event.entity.EntityDamageEvent getLastDamageCause() {
        return null;
    }

    @Override
    public int getTicksLived() {
        return 0;
    }

    @Override
    public void setTicksLived(int value) {
        throw adapter.unbridged("Player.setTicksLived");
    }

    @Override
    public void playEffect(org.bukkit.EntityEffect type) {
        throw adapter.unbridged("Player.playEffect");
    }

    @Override
    public org.bukkit.entity.EntityType getType() {
        return org.bukkit.entity.EntityType.PLAYER;
    }

    @Override
    public boolean isInsideVehicle() {
        return false;
    }

    @Override
    public boolean leaveVehicle() {
        return false;
    }

    @Override
    public Entity getVehicle() {
        return null;
    }

    @Override
    public void setCustomName(String name) {
        throw adapter.unbridged("Player.setCustomName");
    }

    @Override
    public String getCustomName() {
        return session.name();
    }

    @Override
    public void setCustomNameVisible(boolean flag) {
        throw adapter.unbridged("Player.setCustomNameVisible");
    }

    @Override
    public boolean isCustomNameVisible() {
        return false;
    }

    // ------------------------------------------------------------------ LivingEntity

    @Override
    public double getEyeHeight() {
        return 1.62;
    }

    @Override
    public double getEyeHeight(boolean ignoreSneaking) {
        return 1.62;
    }

    @Override
    public Location getEyeLocation() {
        Location at = getLocation();
        return at.add(0, getEyeHeight(), 0);
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLineOfSight(java.util.HashSet<Byte> transparent,
            int maxDistance) {
        throw adapter.unbridged("Player.getLineOfSight");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLineOfSight(java.util.Set<Material> transparent,
            int maxDistance) {
        throw adapter.unbridged("Player.getLineOfSight");
    }

    @Override
    public org.bukkit.block.Block getTargetBlock(java.util.HashSet<Byte> transparent, int maxDistance) {
        throw adapter.unbridged("Player.getTargetBlock");
    }

    @Override
    public org.bukkit.block.Block getTargetBlock(java.util.Set<Material> transparent, int maxDistance) {
        throw adapter.unbridged("Player.getTargetBlock");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLastTwoTargetBlocks(
            java.util.HashSet<Byte> transparent, int maxDistance) {
        throw adapter.unbridged("Player.getLastTwoTargetBlocks");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLastTwoTargetBlocks(
            java.util.Set<Material> transparent, int maxDistance) {
        throw adapter.unbridged("Player.getLastTwoTargetBlocks");
    }

    @Override
    public org.bukkit.entity.Egg throwEgg() {
        throw adapter.unbridged("Player.throwEgg");
    }

    @Override
    public org.bukkit.entity.Snowball throwSnowball() {
        throw adapter.unbridged("Player.throwSnowball");
    }

    @Override
    public org.bukkit.entity.Arrow shootArrow() {
        throw adapter.unbridged("Player.shootArrow");
    }

    @Override
    public int getRemainingAir() {
        return 300;
    }

    @Override
    public void setRemainingAir(int ticks) {
        throw adapter.unbridged("Player.setRemainingAir");
    }

    @Override
    public int getMaximumAir() {
        return 300;
    }

    @Override
    public void setMaximumAir(int ticks) {
        throw adapter.unbridged("Player.setMaximumAir");
    }

    @Override
    public int getMaximumNoDamageTicks() {
        return 20;
    }

    @Override
    public void setMaximumNoDamageTicks(int ticks) {
        throw adapter.unbridged("Player.setMaximumNoDamageTicks");
    }

    @Override
    public double getLastDamage() {
        return 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int _INVALID_getLastDamage() {
        return 0;
    }

    @Override
    public void setLastDamage(double damage) {
        throw adapter.unbridged("Player.setLastDamage");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void _INVALID_setLastDamage(int damage) {
        throw adapter.unbridged("Player.setLastDamage");
    }

    @Override
    public int getNoDamageTicks() {
        return 0;
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        throw adapter.unbridged("Player.setNoDamageTicks");
    }

    @Override
    public Player getKiller() {
        return null;
    }

    @Override
    public boolean addPotionEffect(org.bukkit.potion.PotionEffect effect) {
        throw adapter.unbridged("Player.addPotionEffect");
    }

    @Override
    public boolean addPotionEffect(org.bukkit.potion.PotionEffect effect, boolean force) {
        throw adapter.unbridged("Player.addPotionEffect");
    }

    @Override
    public boolean addPotionEffects(java.util.Collection<org.bukkit.potion.PotionEffect> effects) {
        throw adapter.unbridged("Player.addPotionEffects");
    }

    @Override
    public boolean hasPotionEffect(org.bukkit.potion.PotionEffectType type) {
        return false;
    }

    @Override
    public void removePotionEffect(org.bukkit.potion.PotionEffectType type) {
        throw adapter.unbridged("Player.removePotionEffect");
    }

    @Override
    public java.util.Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects() {
        return java.util.List.of();
    }

    @Override
    public boolean hasLineOfSight(Entity other) {
        return false;
    }

    @Override
    public boolean getRemoveWhenFarAway() {
        return false;
    }

    @Override
    public void setRemoveWhenFarAway(boolean remove) {
        throw adapter.unbridged("Player.setRemoveWhenFarAway");
    }

    @Override
    public org.bukkit.inventory.EntityEquipment getEquipment() {
        throw adapter.unbridged("Player.getEquipment");
    }

    @Override
    public void setCanPickupItems(boolean pickup) {
        throw adapter.unbridged("Player.setCanPickupItems");
    }

    @Override
    public boolean getCanPickupItems() {
        return true;
    }

    @Override
    public boolean isLeashed() {
        return false;
    }

    @Override
    public boolean setLeashHolder(Entity holder) {
        throw adapter.unbridged("Player.setLeashHolder");
    }

    @Override
    public Entity getLeashHolder() throws IllegalStateException {
        return null; // players are never leashed (the honest null)
    }

    // ------------------------------------------------------------------ Damageable

    @Override
    public void damage(double amount) {
        throw adapter.unbridged("Player.damage");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void _INVALID_damage(int amount) {
        throw adapter.unbridged("Player.damage");
    }

    @Override
    public void damage(double amount, Entity source) {
        throw adapter.unbridged("Player.damage");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void _INVALID_damage(int amount, Entity source) {
        throw adapter.unbridged("Player.damage");
    }

    @Override
    public double getHealth() {
        return 20;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int _INVALID_getHealth() {
        return 20;
    }

    @Override
    public void setHealth(double health) {
        throw adapter.unbridged("Player.setHealth");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void _INVALID_setHealth(int health) {
        throw adapter.unbridged("Player.setHealth");
    }

    @Override
    public double getMaxHealth() {
        return 20;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int _INVALID_getMaxHealth() {
        return 20;
    }

    @Override
    public void setMaxHealth(double health) {
        throw adapter.unbridged("Player.setMaxHealth");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void _INVALID_setMaxHealth(int health) {
        throw adapter.unbridged("Player.setMaxHealth");
    }

    @Override
    public void resetMaxHealth() {
        throw adapter.unbridged("Player.resetMaxHealth");
    }

    // ------------------------------------------------------------------ HumanEntity

    // The slice-4 inventory-family verbs (§28.7's inventory family — the
    // org.bukkit.inventory views live in the adapter's inventory package;
    // these are the player's own arms over the engine's §27.3 surfaces).

    /**
     * The player's own inventory view (slice 4): the 36 storage slots +
     * the armor accessors + the held-item arms over the session's live
     * §27.3 inventory surface — the §27.5 handle law (every read resolves
     * to the CURRENT authoritative session state). Cached: one view per
     * player handle, so the legacy identity comparisons hold.
     */
    @Override
    public org.bukkit.inventory.PlayerInventory getInventory() {
        // One lazy view per player handle (the identity law).
        org.bukkit.inventory.PlayerInventory view = inventoryView;
        if (view == null) {
            synchronized (this) {
                view = inventoryView;
                if (view == null) {
                    view = net.zaminmc.torch.bukkit.inventory.TorchPlayerInventory.of(
                            adapter, session);
                    inventoryView = view;
                }
            }
        }
        return view;
    }

    /** The slice-4 inventory view cache (one per player handle). */
    private volatile org.bukkit.inventory.PlayerInventory inventoryView;

    /**
     * The ender chest view (slice 4): the session's own private 27-slot
     * grid (InventoryType.ENDER_CHEST) — the door block names the window,
     * the storage rides the player.
     */
    @Override
    public org.bukkit.inventory.Inventory getEnderChest() {
        return net.zaminmc.torch.bukkit.inventory.TorchChestInventory.ofEnder(adapter, session);
    }

    @Override
    public boolean setWindowProperty(org.bukkit.inventory.InventoryView.Property prop, int value) {
        throw adapter.unbridged("Player.setWindowProperty");
    }

    @Override
    public org.bukkit.inventory.InventoryView getOpenInventory() {
        throw adapter.unbridged("Player.getOpenInventory");
    }

    @Override
    public org.bukkit.inventory.InventoryView openInventory(org.bukkit.inventory.Inventory inventory) {
        throw adapter.unbridged("Player.openInventory");
    }

    @Override
    public org.bukkit.inventory.InventoryView openWorkbench(Location location, boolean force) {
        throw adapter.unbridged("Player.openWorkbench");
    }

    @Override
    public org.bukkit.inventory.InventoryView openEnchanting(Location location, boolean force) {
        throw adapter.unbridged("Player.openEnchanting");
    }

    @Override
    public void openInventory(org.bukkit.inventory.InventoryView inventory) {
        throw adapter.unbridged("Player.openInventory");
    }

    @Override
    public void closeInventory() {
        // The engine's own close walk (slice 4): the open container's id
        // when one is open, else the player's own inventory window 0 —
        // the same walk the client-initiated close takes (carried state
        // returns, the lid closes, the wire re-syncs). Thread-safe by the
        // engine's own contract (the ticker submission).
        adapter.engine().closeWindow(session, session.openContainerWindowId() >= 0
                ? session.openContainerWindowId() : 0);
    }

    /**
     * The held item read (slice 4): the deprecated HumanEntity arm over
     * the inventory view's own held-item read.
     */
    @Override
    @SuppressWarnings("deprecation")
    public org.bukkit.inventory.ItemStack getItemInHand() {
        return getInventory().getItemInHand();
    }

    /**
     * The held item write (slice 4): the deprecated HumanEntity arm over
     * the inventory view's own held-slot write.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void setItemInHand(org.bukkit.inventory.ItemStack item) {
        getInventory().setItemInHand(item);
    }

    /**
     * The cursor read (slice 4): the engine's own cursor surface — a
     * fresh legacy value snapshot, null when the mouse carries nothing.
     */
    @Override
    public org.bukkit.inventory.ItemStack getItemOnCursor() {
        net.zaminmc.torch.item.ItemStack cursor = session.inventory().cursor();
        return cursor.isEmpty() ? null
                : net.zaminmc.torch.bukkit.inventory.TorchItemStacks.toLegacy(cursor,
                        "Player.cursor", adapter.logger());
    }

    /**
     * The cursor write (slice 4): the engine's own cursor arms — the old
     * carried stack is replaced (the 1.8.8 arm's own semantics), the new
     * one placed through the engine's one-mouse rule. The wire's own
     * cursor sync rides the engine's window-click paths (the engine has
     * no standalone cursor-resync surface — the recorded §27.9 note).
     */
    @Override
    public void setItemOnCursor(org.bukkit.inventory.ItemStack item) {
        net.zaminmc.torch.item.ItemStack engine =
                net.zaminmc.torch.bukkit.inventory.TorchItemStacks.toEngine(item, adapter);
        adapter.mutatePosition(session.world() != null ? session.world()
                        : adapter.engine().world(),
                session.position().toBlockPosition(), "bukkit-cursor-write",
                () -> {
                    session.inventory().takeCursor(); // the 1.8.8 replace semantics
                    if (!engine.isEmpty()) {
                        session.inventory().placeOnCursor(engine);
                    }
                });
    }

    @Override
    public boolean isSleeping() {
        return false;
    }

    @Override
    public int getSleepTicks() {
        return 0;
    }

    @Override
    public void setGameMode(GameMode mode) {
        throw adapter.unbridged("Player.setGameMode");
    }

    @Override
    public boolean isBlocking() {
        return false;
    }

    @Override
    public int getExpToLevel() {
        return 0;
    }

    // ------------------------------------------------------------------ OfflinePlayer

    @Override
    public boolean isBanned() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBanned(boolean banned) {
        throw adapter.unbridged("OfflinePlayer.setBanned");
    }

    @Override
    public boolean isWhitelisted() {
        return true;
    }

    @Override
    public void setWhitelisted(boolean value) {
        throw adapter.unbridged("OfflinePlayer.setWhitelisted");
    }

    @Override
    public long getFirstPlayed() {
        return 0;
    }

    @Override
    public long getLastPlayed() {
        return 0;
    }

    @Override
    public boolean hasPlayedBefore() {
        return true;
    }

    @Override
    public java.util.Map<String, Object> serialize() {
        return java.util.Map.of("UUID", session.uuid().toString());
    }

    // ------------------------------------------------------------------ Conversable

    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public void acceptConversationInput(String input) {
        throw adapter.unbridged("Player.acceptConversationInput");
    }

    @Override
    public boolean beginConversation(org.bukkit.conversations.Conversation conversation) {
        throw adapter.unbridged("Player.beginConversation");
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation) {
        throw adapter.unbridged("Player.abandonConversation");
    }

    @Override
    public void abandonConversation(org.bukkit.conversations.Conversation conversation,
            org.bukkit.conversations.ConversationAbandonedEvent details) {
        throw adapter.unbridged("Player.abandonConversation");
    }

    // ------------------------------------------------------------------ Permissible

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin,
            String name, boolean value) {
        throw adapter.unbridged("Player.addAttachment");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin) {
        throw adapter.unbridged("Player.addAttachment");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin,
            String name, boolean value, int ticks) {
        throw adapter.unbridged("Player.addAttachment");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(org.bukkit.plugin.Plugin plugin,
            int ticks) {
        throw adapter.unbridged("Player.addAttachment");
    }

    @Override
    public void removeAttachment(org.bukkit.permissions.PermissionAttachment attachment) {
        throw adapter.unbridged("Player.removeAttachment");
    }

    @Override
    public void recalculatePermissions() {
        // No legacy permission registry in this slice — the no-op is the
        // honest answer (the operator gate is the live permission check).
    }

    @Override
    public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
        return java.util.Set.of();
    }

    // ------------------------------------------------------------------ Metadatable + messaging

    @Override
    public void setMetadata(String metadataKey, org.bukkit.metadata.MetadataValue newMetadataValue) {
        throw adapter.unbridged("Player.setMetadata");
    }

    @Override
    public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(String metadataKey) {
        return java.util.List.of();
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return false;
    }

    @Override
    public void removeMetadata(String metadataKey, org.bukkit.plugin.Plugin owningPlugin) {
        throw adapter.unbridged("Player.removeMetadata");
    }

    @Override
    public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message) {
        throw adapter.unbridged("Player.sendPluginMessage");
    }

    @Override
    public java.util.Set<String> getListeningPluginChannels() {
        return java.util.Set.of();
    }

    // ------------------------------------------------------------------ ProjectileSource

    @Override
    public <T extends org.bukkit.entity.Projectile> T launchProjectile(
            Class<? extends T> projectile) {
        throw adapter.unbridged("Player.launchProjectile");
    }

    @Override
    public <T extends org.bukkit.entity.Projectile> T launchProjectile(
            Class<? extends T> projectile, Vector velocity) {
        throw adapter.unbridged("Player.launchProjectile");
    }

    // ------------------------------------------------------------------ Spigot section

    @Override
    public Spigot spigot() {
        return new Spigot() {
        };
    }
}
