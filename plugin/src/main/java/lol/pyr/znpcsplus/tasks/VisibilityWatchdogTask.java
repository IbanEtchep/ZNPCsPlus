package lol.pyr.znpcsplus.tasks;

import lol.pyr.znpcsplus.util.FutureUtil;
import lol.pyr.znpcsplus.util.Viewable;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Consumer;

// TEMPORARY debug: detects if Viewable's shared single-thread executor stalls, which would
// freeze every npc/hologram show/hide for every player at once. Remove once confirmed either way.
public class VisibilityWatchdogTask extends BukkitRunnable {
    private static final long STALL_THRESHOLD_MILLIS = 5000;

    private final Consumer<String> log;

    public VisibilityWatchdogTask(Consumer<String> log) {
        this.log = log;
    }

    @Override
    public void run() {
        // tags FutureUtil's own thread count onto the stall message, to tell apart "znpcsplus's pool
        // is maxed out" from "something else exhausted the process's threads and we're a victim of it"
        Viewable.checkStalled(STALL_THRESHOLD_MILLIS, message -> log.accept(message + " FutureUtil pool size: " + FutureUtil.activeThreads() + "/200"));
        Viewable.heartbeat();
    }
}
