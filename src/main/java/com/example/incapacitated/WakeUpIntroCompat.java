package com.example.incapacitated;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

/** Optional compatibility with Wake Up Intro. */
final class WakeUpIntroCompat {
    private WakeUpIntroCompat() {}

    static void playWakeUp(ServerPlayer player) {
        if (!ModList.get().isLoaded("wakeupintro")) return;
        try {
            Class<?> kindClass = Class.forName("com.od105.wakeupintro.IntroKind");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object shortIntro = Enum.valueOf((Class<? extends Enum>) kindClass.asSubclass(Enum.class), "SHORT");

            Class<?> eventsClass = Class.forName("com.od105.wakeupintro.ServerEvents");
            Method play = eventsClass.getDeclaredMethod("play", ServerPlayer.class, kindClass, Optional.class);
            play.setAccessible(true);
            play.invoke(null, player, shortIntro, Optional.empty());
        } catch (Throwable error) {
            LastBreath.LOGGER.warn("Wake Up Intro compatibility call failed for {}", player.getGameProfile().getName(), error);
        }
    }
}
