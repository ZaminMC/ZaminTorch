package net.minecraft.client.resource.metadata;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

public class AnimationMetadata implements ResourceMetadataSection {
    private final List<AnimationFrame> frames;
    private final int frameWidth;
    private final int frameHeight;
    private final int defaultFrameTime;
    private final boolean interpolated;

    public AnimationMetadata(List<AnimationFrame> frames, int frameWidth, int height, int defaultFrameTime, boolean interpolated) {
        this.frames = frames;
        this.frameWidth = frameWidth;
        this.frameHeight = height;
        this.defaultFrameTime = defaultFrameTime;
        this.interpolated = interpolated;
    }

    public int getFrameHeight() {
        return this.frameHeight;
    }

    public int getFrameWidth() {
        return this.frameWidth;
    }

    public int getFrameCount() {
        return this.frames.size();
    }

    public int getDefaultFrameTime() {
        return this.defaultFrameTime;
    }

    public boolean isInterpolated() {
        return this.interpolated;
    }

    private AnimationFrame getFrame(int i) {
        return this.frames.get(i);
    }

    public int getFrameTime(int i) {
        AnimationFrame animationframe = this.getFrame(i);
        return animationframe.isTimeUnknown() ? this.defaultFrameTime : animationframe.getTime();
    }

    public boolean hasFrameTime(int i) {
        return !this.frames.get(i).isTimeUnknown();
    }

    public int getFrameIndex(int i) {
        return this.frames.get(i).getIndex();
    }

    public Set<Integer> getUniqueFrameIndices() {
        Set<Integer> set = Sets.newHashSet();

        for (AnimationFrame animationframe : this.frames) {
            set.add(animationframe.getIndex());
        }

        return set;
    }
}
