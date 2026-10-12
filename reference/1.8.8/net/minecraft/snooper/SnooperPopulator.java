package net.minecraft.snooper;

public interface SnooperPopulator {
    void populateSnooper(Snooper snooper);

    void initSnooper(Snooper snooper);

    boolean isSnooperEnabled();
}
