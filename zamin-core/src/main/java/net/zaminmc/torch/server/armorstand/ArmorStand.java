package net.zaminmc.torch.server.armorstand;

import net.zaminmc.torch.item.ItemStack;

/**
 * One armor stand body (Slice 28c — the reference port of
 * entity/living/ArmorStandEntity): the five equipment slots (the hand,
 * the boots, the leggings, the chestplate, the helmet — the wire's own
 * Entity Equipment order), the status byte (small / noGravity /
 * showArms / noBasePlate / marker — the synced slot 10), the six-part
 * pose (the head, the body, the arms, the legs — the synced slots
 * 11-16, each a pitch/yaw/roll triple), and the melee break clock (the
 * five-tick double-hit window).
 *
 * <p>The default pose is the reference's own: the arms at (-10, 0, -10)
 * and (-15, 0, 10), the legs at (-1, 0, -1) and (1, 0, 1), the head and
 * body straight.</p>
 */
public final class ArmorStand {

    /** One pose part: pitch, yaw, roll (the wire's Rotation triple). */
    public record Pose(float pitch, float yaw, float roll) {
        public static final Pose ZERO = new Pose(0.0f, 0.0f, 0.0f);
    }

    // The status byte's bits (the synced slot 10).
    /** The small flag (bit 0). */
    static final byte FLAG_SMALL = 0x01;
    /** The no-gravity flag (bit 1). */
    static final byte FLAG_NO_GRAVITY = 0x02;
    /** The show-arms flag (bit 2). */
    static final byte FLAG_SHOW_ARMS = 0x04;
    /** The no-base-plate flag (bit 3). */
    static final byte FLAG_NO_BASE_PLATE = 0x08;
    /** The marker flag (bit 4). */
    static final byte FLAG_MARKER = 0x10;

    /** The stand's default health (the LivingEntity's own 20). */
    static final float DEFAULT_HEALTH = 20.0f;
    /** The fire burn's break floor (takeFireDamage's own 0.5). */
    static final float FIRE_BREAK_FLOOR = 0.5f;
    /** The on-fire tick toll (takeFireDamage(4.0F)). */
    static final float ON_FIRE_TOLL = 4.0f;
    /** The standing-in-fire tick toll (takeFireDamage(0.15F)). */
    static final float FIRE_CONTACT_TOLL = 0.15f;
    /** The ignite length (setOnFireFor(5) — 100 ticks). */
    static final int IGNITE_TICKS = 100;

    /** The reference's own default pose constants. */
    static final Pose DEFAULT_HEAD = Pose.ZERO;
    static final Pose DEFAULT_BODY = Pose.ZERO;
    static final Pose DEFAULT_LEFT_ARM = new Pose(-10.0f, 0.0f, -10.0f);
    static final Pose DEFAULT_RIGHT_ARM = new Pose(-15.0f, 0.0f, 10.0f);
    static final Pose DEFAULT_LEFT_LEG = new Pose(-1.0f, 0.0f, -1.0f);
    static final Pose DEFAULT_RIGHT_LEG = new Pose(1.0f, 0.0f, 1.0f);

    private final int entityId;
    private double x;
    private double y;
    private double z;
    private float yaw;
    /** The five equipment slots: 0 = hand, 1 = boots, 2 = legs, 3 = chest, 4 = head. */
    private final ItemStack[] equipment = new ItemStack[] {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
    private byte status;
    private Pose head = DEFAULT_HEAD;
    private Pose body = DEFAULT_BODY;
    private Pose leftArm = DEFAULT_LEFT_ARM;
    private Pose rightArm = DEFAULT_RIGHT_ARM;
    private Pose leftLeg = DEFAULT_LEFT_LEG;
    private Pose rightLeg = DEFAULT_RIGHT_LEG;
    /** The reference's lastDamageTime — the double-hit window stamp (0 initial, the reference's own). */
    private long lastDamageTime;
    /** The burn clock (fire ticks remaining). */
    private int fireTicks;
    /** The stand's burning health (the LivingEntity float). */
    private float health = DEFAULT_HEALTH;
    /** The walk's velocity (the reference's motion fields — the minecart push feeds these). */
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    /** The walk's landing state (the reference's onGround). */
    private boolean onGround;

    ArmorStand(int entityId, double x, double y, double z, float yaw) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    /** The stand's engine entity id (the Spawn Object 78 wire id). */
    public int entityId() {
        return entityId;
    }

    /** The stand's feet position. */
    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    /** The walk's own position move (the system's tick — package-private). */
    void moveTo(double newX, double newY, double newZ) {
        this.x = newX;
        this.y = newY;
        this.z = newZ;
    }

    /** The stand's body yaw (the snapped 45-degree placement walk). */
    public float yaw() {
        return yaw;
    }

    /** The equipment slot (0 = hand .. 4 = head); EMPTY when bare. */
    public ItemStack equipment(int slot) {
        return equipment[slot];
    }

    /** The five slots (the join view's walk). */
    public ItemStack[] equipment() {
        return equipment.clone();
    }

    void setEquipment(int slot, ItemStack stack) {
        equipment[slot] = stack == null ? ItemStack.EMPTY : stack;
    }

    /** The status byte (the synced slot 10 — the wire's own form). */
    public byte status() {
        return status;
    }

    /** The no-gravity flag (the synced byte's bit 1): a flagged stand never walks. */
    public boolean noGravity() {
        return (status & FLAG_NO_GRAVITY) != 0;
    }

    /**
     * The marker flag (the synced byte's bit 4, ArmorStandEntity line
     * 657's own bit): the marker stand's hitbox-less quirk — no interact
     * (interactAt line 274's refusal), no damage (takeDamage line 378's
     * gate), the updateSize(0, 0) box.
     */
    public boolean marker() {
        return (status & FLAG_MARKER) != 0;
    }

    /**
     * The small flag (the synced byte's bit 0, ArmorStandEntity line
     * 597's own bit): the small form's own box rows (updateSize(0.25,
     * 0.9875) against the normal (0.5, 1.975) — the piston-push family's
     * box test reads them).
     */
    public boolean small() {
        return (status & FLAG_SMALL) != 0;
    }

    /** The walk's velocity x (the reference's motionX). */
    public double velocityX() {
        return velocityX;
    }

    /** The walk's velocity y (the reference's motionY). */
    public double velocityY() {
        return velocityY;
    }

    /** The walk's velocity z (the reference's motionZ). */
    public double velocityZ() {
        return velocityZ;
    }

    /** The reference's addVelocity (Entity.addVelocity lines 1008-1014). */
    public void addVelocity(double dx, double dy, double dz) {
        velocityX += dx;
        velocityY += dy;
        velocityZ += dz;
    }

    /** The walk's landing state (the reference's onGround). */
    public boolean onGround() {
        return onGround;
    }

    /** The walk's own setter (the system's tick — package-private like every field arm). */
    void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    /** The walk's own velocity setter (the system's tick). */
    void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    void setStatus(byte status) {
        this.status = status;
    }

    public Pose head() {
        return head;
    }

    public Pose body() {
        return body;
    }

    public Pose leftArm() {
        return leftArm;
    }

    public Pose rightArm() {
        return rightArm;
    }

    public Pose leftLeg() {
        return leftLeg;
    }

    public Pose rightLeg() {
        return rightLeg;
    }

    void setHead(Pose pose) {
        head = pose;
    }

    void setBody(Pose pose) {
        body = pose;
    }

    void setLeftArm(Pose pose) {
        leftArm = pose;
    }

    void setRightArm(Pose pose) {
        rightArm = pose;
    }

    void setLeftLeg(Pose pose) {
        leftLeg = pose;
    }

    void setRightLeg(Pose pose) {
        rightLeg = pose;
    }

    /** The double-hit clock's stamp (the system's own tick source). */
    long lastDamageTime() {
        return lastDamageTime;
    }

    void setLastDamageTime(long time) {
        lastDamageTime = time;
    }

    /** The burn clock's remaining ticks. */
    int fireTicks() {
        return fireTicks;
    }

    void setFireTicks(int ticks) {
        fireTicks = ticks;
    }

    /** The stand's health (the burn-down model's own float). */
    float health() {
        return health;
    }

    void setHealth(float health) {
        this.health = health;
    }
}
