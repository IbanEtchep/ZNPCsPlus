package lol.pyr.znpcsplus.util;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FutureUtil {
    // like newCachedThreadPool() but capped, so a burst of npc spawns can't exhaust the process's thread limit
    private static final ExecutorService executor = new ThreadPoolExecutor(
            0, 200, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

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
