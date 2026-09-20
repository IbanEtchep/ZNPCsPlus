package lol.pyr.znpcsplus.util;

import org.bukkit.entity.Player;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class Viewable {
    private final static List<WeakReference<Viewable>> all = Collections.synchronizedList(new ArrayList<>());

    public static List<Viewable> all() {
        synchronized (all) {
            all.removeIf(reference -> reference.get() == null);
            return all.stream()
                    .map(Reference::get)
                    .collect(Collectors.toList());
        }
    }

    public static void shutdownExecutor() {
        visibilityExecutor.shutdown();
    }

    private final static ExecutorService visibilityExecutor = Executors.newSingleThreadExecutor();

    private static volatile long lastHeartbeatAt = System.currentTimeMillis();
    private static volatile Thread executorThread;
    private static volatile boolean stallLogged = false;

    // pings the executor so an outside watchdog can tell if it's still processing tasks
    public static void heartbeat() {
        visibilityExecutor.submit(() -> {
            executorThread = Thread.currentThread();
            lastHeartbeatAt = System.currentTimeMillis();
        });
    }

    // call from OUTSIDE visibilityExecutor: logs once (until it recovers) if the last heartbeat is older than thresholdMillis
    public static void checkStalled(long thresholdMillis, Consumer<String> log) {
        long since = System.currentTimeMillis() - lastHeartbeatAt;
        if (since <= thresholdMillis) {
            stallLogged = false;
            return;
        }
        if (stallLogged) return;
        stallLogged = true;
        StringBuilder sb = new StringBuilder("[ZNPCsPlus WATCHDOG] visibilityExecutor hasn't responded in " + since + "ms, it is likely stuck.");
        Thread thread = executorThread;
        if (thread != null) {
            sb.append(" Stack trace of the executor thread:\n");
            for (StackTraceElement element : thread.getStackTrace()) sb.append("    at ").append(element).append('\n');
        }
        log.accept(sb.toString());
    }
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    public Viewable() {
        all.add(new WeakReference<>(this));
    }

    public void delete() {
        visibilityExecutor.submit(() -> {
            UNSAFE_hideAll();
            viewers.clear();
            synchronized (all) {
                all.removeIf(reference -> reference.get() == null || reference.get() == this);
            }
        });
    }

    public CompletableFuture<Void> respawn() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        visibilityExecutor.submit(() -> {
            UNSAFE_hideAll();
            UNSAFE_showAll().thenRun(() -> future.complete(null));
        });
        return future;
    }

    public CompletableFuture<Void> respawn(Player player) {
        hide(player);
        return show(player);
    }

    public CompletableFuture<Void> show(Player player) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        visibilityExecutor.submit(() -> {
            if (viewers.contains(player)) {
                future.complete(null);
                return;
            }
            viewers.add(player);
            UNSAFE_show(player).thenRun(() -> future.complete(null));
        });
        return future;
    }

    public void hide(Player player) {
        visibilityExecutor.submit(() -> {
            if (!viewers.contains(player)) return;
            viewers.remove(player);
            UNSAFE_hide(player);
        });
    }

    public void UNSAFE_removeViewer(Player player) {
        viewers.remove(player);
    }

    protected void UNSAFE_hideAll() {
        for (Player viewer : viewers) UNSAFE_hide(viewer);
    }

    protected CompletableFuture<Void> UNSAFE_showAll() {
        return FutureUtil.allOf(viewers.stream()
                .map(this::UNSAFE_show)
                .collect(Collectors.toList()));
    }

    public Set<Player> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    public boolean isVisibleTo(Player player) {
        return viewers.contains(player);
    }

    protected abstract CompletableFuture<Void> UNSAFE_show(Player player);

    protected abstract void UNSAFE_hide(Player player);
}
