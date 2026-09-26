package com.netscope.zone;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Runs a task per item with at most {@code concurrency} tasks in flight (never thousands at once). */
public final class BoundedRunner {

    private BoundedRunner() {
    }

    public static <T, R> void run(List<T> items, int concurrency, Function<T, R> task, Consumer<R> onResult,
                                  BooleanSupplier cancelled) {
        int limit = Math.max(1, Math.min(concurrency, 500));
        Semaphore permits = new Semaphore(limit);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (T item : items) {
                if (cancelled.getAsBoolean()) break;
                try {
                    permits.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                pool.submit(() -> {
                    try {
                        R r = task.apply(item);
                        if (r != null) onResult.accept(r);
                    } catch (RuntimeException ignored) {
                        // one failing probe must never stop the scan
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for all submitted tasks
    }
}
