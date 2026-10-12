package net.minecraft.util;

import com.google.common.util.concurrent.ListenableFuture;

public interface BlockableEventLoop {
    ListenableFuture<Object> execute(Runnable task);

    boolean isOnSameThread();
}
