package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Random;

/**
 * The boat (community entities.json object type 1): it floats — in a fluid
 * the body rests on the surface with light drag — and the rider steers by
 * looking (the boat's heading follows the mounted look, the historical
 * look-steer); on land it slides to a stop under heavy drag.
 */
public final class BoatEntity extends VehicleEntity {

    public static final double HALF_HEIGHT = 0.2;
    /** Water acceleration per tick of forward steer. */
    public static final double WATER_ACCELERATION = 0.035;
    /** Land acceleration per tick of forward steer. */
    public static final double LAND_ACCELERATION = 0.02;
    /** The land slide cap (the historical paddling on solid ground). */
    public static final double LAND_SPEED_CAP = 0.25;

    public BoatEntity(int entityId, Position position, Random random) {
        super(entityId, Kind.BOAT, position, random);
    }

    @Override
    protected void tickKind(WorldQuery world) {
        Position at = position();
        SteerInput input = consumeSteer();
        boolean inWater = world.inFluid(at.x(), at.y(), at.z());

        // The rider steers by looking: the boat's heading is the mounted
        // look's yaw (the engine pushes it each tick from the seat).
        double forward = input.forward();
        double accel = inWater ? WATER_ACCELERATION : LAND_ACCELERATION;

        if (inWater) {
            // Buoyancy: the body rests at the surface (no vertical motion).
            setVelocity(velocityX(), 0.0, velocityZ());
        } else {
            setVelocity(velocityX(), (velocityY() - GRAVITY_PER_TICK) * 0.98, velocityZ());
        }

        double newX = at.x() + velocityX();
        double newZ = at.z() + velocityZ();
        double newY = at.y() + velocityY();

        if (forward != 0.0f) {
            double radians = Math.toRadians(rotation().yaw());
            double steerX = -Math.sin(radians) * forward * accel;
            double steerZ = Math.cos(radians) * forward * accel;
            newX += steerX * 2.0; // the steer impulse rides the drag decay
            newZ += steerZ * 2.0;
        }

        // Horizontal block refusal (the flat model: zero the step).
        if (blocked(world, newX, at.z(), at.y() + HALF_HEIGHT)) {
            newX = at.x();
            setVelocity(0.0, velocityY(), velocityZ());
        }
        if (blocked(world, newX, newZ, at.y() + HALF_HEIGHT)) {
            newZ = at.z();
            setVelocity(velocityX(), velocityY(), 0.0);
        }

        boolean grounded;
        if (inWater) {
            grounded = false; // floating bodies are never grounded
        } else if (velocityY() <= 0 && world.isSolidAt(newX, newY - HALF_HEIGHT - GROUND_EPSILON, newZ)) {
            newY = Math.floor(newY - HALF_HEIGHT - GROUND_EPSILON) + 1.0 + HALF_HEIGHT;
            setVelocity(velocityX(), 0.0, velocityZ());
            grounded = true;
        } else {
            grounded = false;
        }

        // The drag order mirrors the body model: water glides, land grinds.
        decay(inWater ? 0.85 : 0.6);
        if (!inWater) {
            capHorizontal(LAND_SPEED_CAP);
        }
        commit(newX, newY, newZ, grounded);
    }

    private void capHorizontal(double cap) {
        double vx = velocityX();
        double vz = velocityZ();
        if (Math.abs(vx) > cap) {
            vx = Math.signum(vx) * cap;
        }
        if (Math.abs(vz) > cap) {
            vz = Math.signum(vz) * cap;
        }
        setVelocity(vx, velocityY(), vz);
    }

    /** The seat: the boat's heading tracks the rider's look every tick. */
    public void followRiderLook(Rotation riderLook) {
        setRotation(new Rotation(riderLook.yaw(), 0.0f));
    }
}
