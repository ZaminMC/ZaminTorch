package net.minecraft.client.render.texture;

public class StitcherException extends RuntimeException {
    private final Stitcher.Holder sprite;

    public StitcherException(Stitcher.Holder sprite, String reason) {
        super(reason);
        this.sprite = sprite;
    }
}
