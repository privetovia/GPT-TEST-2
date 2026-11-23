package com.example.incapacitated;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Optional bridge: Last Breath does not hard-link Sable classes. */
final class SableCompat {
    private static final String API = "dev.leo.sableplayerragdoll.api.RagdollAPI";
    private static final String OPTIONS = "dev.leo.sableplayerragdoll.api.RagdollLaunchOptions";
    private static final String CONDITION = "dev.leo.sableplayerragdoll.api.DespawnCondition";

    private SableCompat() {}

    static boolean start(ServerPlayer player) {
        if (!ModList.get().isLoaded("sable_player_ragdoll")) return false;
        try {
            Class<?> api = Class.forName(API);
            Class<?> optionsClass = Class.forName(OPTIONS);
            Class<?> conditionClass = Class.forName(CONDITION);

            Object builder = optionsClass.getMethod("builder").invoke(null);
            Object neverDespawn = conditionClass.getMethod("never").invoke(null);
            builder.getClass().getMethod("despawnConditions", List.class)
                    .invoke(builder, List.of(neverDespawn));
            builder.getClass().getMethod("lockDismount", boolean.class).invoke(builder, true);
            Object options = builder.getClass().getMethod("build").invoke(builder);

            Method launch = api.getMethod("launch", ServerPlayer.class, Vec3.class, optionsClass);
            launch.invoke(null, player, Vec3.ZERO, options);
            return true;
        } catch (Throwable error) {
            LastBreath.LOGGER.warn("Could not start Sable ragdoll for {}", player.getGameProfile().getName(), error);
            return false;
        }
    }

    /**
     * Sable exposes detachActive(player, PlayerlessDespawnRule), not a
     * release() method on the session. Reflection keeps Sable optional.
     */
    static void stop(ServerPlayer player) {
        if (!ModList.get().isLoaded("sable_player_ragdoll")) return;
        try {
            Class<?> api = Class.forName(API);
            for (Method method : api.getMethods()) {
                if (!method.getName().equals("detachActive") || method.getParameterCount() != 2
                        || method.getParameterTypes()[0] != ServerPlayer.class
                        || !Modifier.isStatic(method.getModifiers())) continue;

                Class<?> ruleType = method.getParameterTypes()[1];
                Object rule = firstEnumConstant(ruleType);
                if (rule != null) {
                    method.invoke(null, player, rule);
                    return;
                }
            }
            // Compatibility fallback for Sable builds that expose a session release method.
            Object session = api.getMethod("activeSession", ServerPlayer.class).invoke(null, player);
            if (session != null) {
                for (Method method : session.getClass().getMethods()) {
                    if (method.getName().equals("release") && method.getParameterCount() == 0) {
                        method.invoke(session);
                        return;
                    }
                }
            }
            LastBreath.LOGGER.warn("No supported Sable ragdoll detach method was found for {}",
                    player.getGameProfile().getName());
        } catch (Throwable error) {
            LastBreath.LOGGER.warn("Could not detach Sable ragdoll for {}", player.getGameProfile().getName(), error);
        }
    }

    private static Object firstEnumConstant(Class<?> type) {
        if (!type.isEnum()) return null;
        Object[] values = type.getEnumConstants();
        if (values == null || values.length == 0) return null;

        // Prefer a rule that removes the playerless ragdoll immediately.
        for (String preferred : List.of("DESPAWN", "DISCARD", "REMOVE", "DEFAULT")) {
            for (Object value : values) {
                if (((Enum<?>) value).name().equalsIgnoreCase(preferred)) return value;
            }
        }
        return values[0];
    }
}
