package auto.parto.config;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoundedProviderScheduler implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final Scheduler scheduler;
    private final Duration shutdownGrace;

    public BoundedProviderScheduler(String name, int threads, int queueCapacity, Duration shutdownGrace) {
        if (threads < 1 || threads > 64) {
            throw new IllegalArgumentException("provider threads must be between 1 and 64");
        }
        if (queueCapacity < 1 || queueCapacity > 4096) {
            throw new IllegalArgumentException("provider queue capacity must be between 1 and 4096");
        }
        if (shutdownGrace == null || shutdownGrace.isNegative()) {
            throw new IllegalArgumentException("provider shutdown grace must be non-negative");
        }
        this.shutdownGrace = shutdownGrace;
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, name + "-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        this.scheduler = Schedulers.fromExecutorService(executor, name);
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    int queuedTasks() {
        return executor.getQueue().size();
    }

    boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownGrace.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(Math.max(1, shutdownGrace.toMillis()), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            scheduler.dispose();
        }
    }
}
