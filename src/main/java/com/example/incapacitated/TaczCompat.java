package com.example.incapacitated;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;

/**
 * Optional TaCZ bridge. Reflection keeps TaCZ optional at compile time.
 * The integration reacts only to TaCZ's own headshot flags.
 */
public final class TaczCompat {
    private TaczCompat() {}

    @SubscribeEvent
    public static void onTaczEvent(Event event) {
        if (!ModList.get().isLoaded("tacz")) return;
        String name = event.getClass().getName();
        if (!name.startsWith("com.tacz.guns.api.event.common.")) return;

        try {
            if (name.endsWith("EntityHurtByGunEvent$Post")) {
                Object target = event.getClass().getMethod("getHurtEntity").invoke(event);
                boolean headshot = (boolean) event.getClass().getMethod("isHeadShot").invoke(event);
                if (headshot && target instanceof ServerPlayer player) {
                    IncapacitationHandler.onTaczNonlethalHeadshot(player);
                }
            } else if (name.endsWith("EntityKillByGunEvent")) {
                Object target = event.getClass().getMethod("getKilledEntity").invoke(event);
                boolean headshot = (boolean) event.getClass().getMethod("isHeadShot").invoke(event);
                if (headshot && target instanceof ServerPlayer player) {
                    IncapacitationHandler.onTaczLethalHeadshot(player);
                }
            }
        } catch (ReflectiveOperationException | ClassCastException error) {
            LastBreath.LOGGER.warn("Could not process TaCZ headshot event {}", name, error);
        }
    }
}
