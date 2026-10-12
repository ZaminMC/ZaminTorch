package net.minecraft.client.entity.living.player;

import net.minecraft.client.options.GameOptions;

public class KeyboardInput extends Input {
    private final GameOptions options;

    public KeyboardInput(GameOptions options) {
        this.options = options;
    }

    @Override
    public void tick() {
        this.movementSideways = 0.0F;
        this.movementForward = 0.0F;
        if (this.options.forwardKey.isPressed()) {
            this.movementForward++;
        }

        if (this.options.backKey.isPressed()) {
            this.movementForward--;
        }

        if (this.options.leftKey.isPressed()) {
            this.movementSideways++;
        }

        if (this.options.rightKey.isPressed()) {
            this.movementSideways--;
        }

        this.jumping = this.options.jumpKey.isPressed();
        this.sneaking = this.options.sneakKey.isPressed();
        if (this.sneaking) {
            this.movementSideways = (float)(this.movementSideways * 0.3);
            this.movementForward = (float)(this.movementForward * 0.3);
        }
    }
}
