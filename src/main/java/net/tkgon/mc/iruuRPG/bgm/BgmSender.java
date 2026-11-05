package net.tkgon.mc.iruuRPG.bgm;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Plays things at a wall-clock time from a thread of its own, so that a server that is late with its ticks does not make the music stutter: the channel plans the notes a little
 * ahead (see {@link BgmClock}) and this sends each one when its time comes, whatever the main thread is doing.
 */
final class BgmSender implements BgmChannel.Later {

    private final ScheduledThreadPoolExecutor pool;
    private final Consumer<Throwable> onError;

    BgmSender(Consumer<Throwable> onError) {
        this.onError = onError;
        this.pool = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "iruurpg-bgm");
            thread.setDaemon(true);
            return thread;
        });
        this.pool.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void at(long dueNanos, Runnable task) {
        long delay = Math.max(0L, dueNanos - System.nanoTime());
        try {
            pool.schedule(() -> {
                try {
                    task.run();
                } catch (Throwable error) {
                    onError.accept(error);
                }
            }, delay, TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // (the plugin is being disabled)
        }
    }

    void stop() {
        pool.shutdownNow();
    }
}
