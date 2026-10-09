package net.minecraft.world.chunk.storage.io;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;

public class ChunkIo implements Runnable {
    private static final ChunkIo INSTANCE = new ChunkIo();
    private List<ChunkIoTask> tasks = Collections.synchronizedList(Lists.newArrayList());
    private volatile long registered;
    private volatile long completed;
    private volatile boolean waiting;

    private ChunkIo() {
        Thread thread = new Thread(this, "File IO Thread");
        thread.setPriority(1);
        thread.start();
    }

    public static ChunkIo getInstance() {
        return INSTANCE;
    }

    @Override
    public void run() {
        while (true) {
            this.runTasks();
        }
    }

    private void runTasks() {
        for (int i = 0; i < this.tasks.size(); i++) {
            ChunkIoTask chunkiotask = this.tasks.get(i);
            boolean flag = chunkiotask.run();
            if (!flag) {
                this.tasks.remove(i--);
                this.completed++;
            }

            try {
                Thread.sleep(this.waiting ? 0L : 10L);
            } catch (InterruptedException interruptedexception1) {
                interruptedexception1.printStackTrace();
            }
        }

        if (this.tasks.isEmpty()) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interruptedexception) {
                interruptedexception.printStackTrace();
            }
        }
    }

    public void registerTask(ChunkIoTask task) {
        if (!this.tasks.contains(task)) {
            this.registered++;
            this.tasks.add(task);
        }
    }

    public void waitUntilFinished() throws InterruptedException {
        this.waiting = true;

        while (this.registered != this.completed) {
            Thread.sleep(10L);
        }

        this.waiting = false;
    }
}
