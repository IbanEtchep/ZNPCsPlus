package lol.pyr.znpcsplus.util;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class FutureUtil {
    // named so a thread dump can tell these apart from other plugins/vanilla threads
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "ZNPCsPlus-async-" + count.incrementAndGet());
        }
    };

    // like newCachedThreadPool() but capped, so a burst of npc spawns can't exhaust the process's thread limit
    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0, 200, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            THREAD_FACTORY,
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // current live thread count of this pool, for diagnosing whether it (as opposed to something else) is what's exhausting the process's threads
    public static int activeThreads() {
        return executor.getPoolSize();
    }

    public static CompletableFuture<Void> allOf(Collection<CompletableFuture<?>> futures) {
        return exceptionPrintingRunAsync(() -> {
            for (CompletableFuture<?> future : futures) future.join();
        });
    }

    public static <T> CompletableFuture<T> newExceptionPrintingFuture() {
        return new CompletableFuture<T>().exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }

    public static CompletableFuture<Void> exceptionPrintingRunAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, executor).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }

    public static <T> CompletableFuture<T> exceptionPrintingSupplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });
    }

    public static void shutdownExecutor() {
        executor.shutdownNow();
    }
}
