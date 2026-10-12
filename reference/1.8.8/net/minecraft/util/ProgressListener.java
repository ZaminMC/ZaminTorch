package net.minecraft.util;

public interface ProgressListener {
    void progressStartNoAbort(String title);

    void updateTitle(String title);

    void progressStage(String stage);

    void progressStagePercentage(int percentage);

    void setDone();
}
