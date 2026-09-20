package lol.pyr.znpcsplus.debug;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// TEMPORARY: verifies where the vanilla entity id counter actually lives on 26.2+. Delete once confirmed.
public final class EntityCounterDebug {
    private EntityCounterDebug() {}

    public static void dump(Consumer<String> log) {
        log.accept("[ZNPCsPlus DEBUG] Dumping AtomicInteger fields for entity id counter investigation:");
        dumpClass(log, "net.minecraft.world.entity.Entity");
        dumpClass(log, "net.minecraft.server.level.ServerLevel");
    }

    private static void dumpClass(Consumer<String> log, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            log.accept("[ZNPCsPlus DEBUG] " + className + ":");
            boolean found = false;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() != AtomicInteger.class) continue;
                found = true;
                field.setAccessible(true);
                boolean isStatic = Modifier.isStatic(field.getModifiers());
                Object value = null;
                if (isStatic) try {
                    value = field.get(null);
                } catch (Throwable ignored) {}
                log.accept("[ZNPCsPlus DEBUG]   - " + field.getName() + " (static=" + isStatic + ", value=" + value + ")");
            }
            if (!found) log.accept("[ZNPCsPlus DEBUG]   (no AtomicInteger fields found)");
        } catch (ClassNotFoundException e) {
            log.accept("[ZNPCsPlus DEBUG] " + className + " not found");
        }
    }
}
