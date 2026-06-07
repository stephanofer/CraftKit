package com.hera.craftkit.database.internal;

import com.hera.craftkit.database.DatabaseException;
import com.hera.craftkit.database.ExecutorConfig;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatabaseExecutors {

    private DatabaseExecutors() {
    }

    public static ExecutorService createExecutor(final ExecutorConfig config) {
        final ExecutorConfig resolvedConfig = Objects.requireNonNull(config, "Executor config must not be null.");
        final ThreadFactory threadFactory = new DatabaseThreadFactory(resolvedConfig.threadNamePrefix(), resolvedConfig.daemon());

        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            resolvedConfig.threadCount(),
            resolvedConfig.threadCount(),
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity(resolvedConfig.threadCount())),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
        executor.prestartAllCoreThreads();
        return executor;
    }

    public static void shutdown(final ExecutorService executorService, final ExecutorConfig config) {
        final ExecutorService resolvedExecutorService = Objects.requireNonNull(executorService, "Executor service must not be null.");
        final ExecutorConfig resolvedConfig = Objects.requireNonNull(config, "Executor config must not be null.");

        resolvedExecutorService.shutdown();
        try {
            if (!resolvedExecutorService.awaitTermination(resolvedConfig.shutdownTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                resolvedExecutorService.shutdownNow();
            }
        } catch (final InterruptedException exception) {
            resolvedExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
            throw new DatabaseException("Interrupted while shutting down the database executor.", exception);
        }
    }

    public static void shutdownQuietly(final ExecutorService executorService, final ExecutorConfig config, final RuntimeException failure) {
        try {
            shutdown(executorService, config);
        } catch (final RuntimeException shutdownFailure) {
            failure.addSuppressed(shutdownFailure);
        }
    }

    static int queueCapacity(final int threadCount) {
        return Math.max(64, threadCount * 128);
    }

    private static final class DatabaseThreadFactory implements ThreadFactory {

        private final String prefix;
        private final boolean daemon;
        private final AtomicInteger counter = new AtomicInteger();

        private DatabaseThreadFactory(final String prefix, final boolean daemon) {
            this.prefix = prefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(final Runnable runnable) {
            final Thread thread = new Thread(runnable, this.prefix + '-' + this.counter.incrementAndGet());
            thread.setDaemon(this.daemon);
            return thread;
        }
    }
}
