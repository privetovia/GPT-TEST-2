package com.example.incapacitated;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = LastBreath.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private static KeyMapping reviveKey;
    private static KeyMapping giveUpKey;
    private static boolean previousReviveDown;
    private static boolean locallyDowned;
    private static int ticksRemaining;
    private static int downedAge;
    private static int headEffectTicks;
    private static int visualTick;

    private ClientEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        reviveKey = new KeyMapping("key.lastbreath.revive", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R, "key.categories.lastbreath");
        giveUpKey = new KeyMapping("key.lastbreath.give_up", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, "key.categories.lastbreath");
        event.register(reviveKey);
        event.register(giveUpKey);
    }

    static void updateDownedStatus(boolean downed, int remainingTicks) {
        if (downed && !locallyDowned) downedAge = 0;
        locallyDowned = downed;
        ticksRemaining = Math.max(0, remainingTicks);
        if (!downed) {
            downedAge = 0;
            previousReviveDown = false;
        }
    }

    static void triggerHeadEffect(int durationTicks) {
        // Repeated head hits add duration rather than resetting the current effect.
        headEffectTicks = Math.min(400, headEffectTicks + Math.max(1, durationTicks));
    }

    @EventBusSubscriber(modid = LastBreath.MOD_ID, value = Dist.CLIENT)
    public static final class ClientTickSubscriber {
        private ClientTickSubscriber() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            visualTick++;
            if (headEffectTicks > 0) headEffectTicks--;
            if (locallyDowned) downedAge++;

            if (reviveKey == null || giveUpKey == null) return;
            boolean down = reviveKey.isDown();
            if (down != previousReviveDown) {
                NetworkHandler.sendReviveState(down);
                previousReviveDown = down;
            }

            while (giveUpKey.consumeClick()) {
                if (locallyDowned) NetworkHandler.sendGiveUp();
            }
        }
    }

    @EventBusSubscriber(modid = LastBreath.MOD_ID, value = Dist.CLIENT)
    public static final class HudSubscriber {
        private HudSubscriber() {}

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.font == null || mc.screen != null) return;

            int width = mc.getWindow().getGuiScaledWidth();
            int height = mc.getWindow().getGuiScaledHeight();
            var graphics = event.getGuiGraphics();

            if (locallyDowned) {
                // Strong initial blackout fades into a persistent darkened view.
                float fade = Math.max(0.0F, 1.0F - downedAge / 40.0F);
                int alpha = (int) (Math.min(0.94F, 0.34F + fade * 0.60F) * 255);
                graphics.fill(0, 0, width, height, (alpha << 24));

                int seconds = (ticksRemaining + 19) / 20;
                String timer = String.format("ДО ИСХОДА  %02d:%02d", seconds / 60, seconds % 60);
                String hint = "R — удерживать для реанимации   •   K — сдаться";
                int boxWidth = Math.max(mc.font.width(timer), mc.font.width(hint)) + 28;
                int left = (width - boxWidth) / 2;

                // Top-center panel stays clear of the hotbar and inventory slots.
                graphics.fill(left, 12, left + boxWidth, 48, 0xB8000000);
                graphics.fill(left, 12, left + 2, 48, 0xFFB84A4A);
                graphics.drawCenteredString(mc.font, timer, width / 2, 17, 0xFFFFFFFF);
                graphics.drawCenteredString(mc.font, hint, width / 2, 33, 0xFFD8D8D8);
            } else if (headEffectTicks > 0) {
                float strength = Math.min(1.0F, headEffectTicks / 80.0F);
                int alpha = (int) (strength * 78.0F);
                graphics.fill(0, 0, width, height, (alpha << 24) | 0x00101820);

                // Soft translucent moving bands provide a lightweight disorientation cue.
                int bandAlpha = (int) (strength * 24.0F);
                int band = (bandAlpha << 24) | 0x00DDE7F0;
                int offset = (visualTick % 7) - 3;
                graphics.fill(0, height / 3 + offset, width, height / 3 + offset + 3, band);
                graphics.fill(0, height * 2 / 3 - offset, width, height * 2 / 3 - offset + 3, band);
            }
        }

        @SubscribeEvent
        public static void cameraShake(ViewportEvent.ComputeCameraAngles event) {
            if (headEffectTicks <= 0 && !locallyDowned) return;
            float intensity = locallyDowned ? 0.18F : Math.min(0.85F, headEffectTicks / 90.0F);
            float waveA = (float) Math.sin(visualTick * 0.91) * intensity;
            float waveB = (float) Math.cos(visualTick * 0.67) * intensity;
            event.setYaw(event.getYaw() + waveA);
            event.setPitch(event.getPitch() + waveB);
            if (locallyDowned) event.setRoll(event.getRoll() + waveA * 0.7F);
        }
    }
}
