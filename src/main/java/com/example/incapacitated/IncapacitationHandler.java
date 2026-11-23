package com.example.incapacitated;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Comparator;
import java.util.UUID;

/** Server-authoritative downed-state controller. */
public final class IncapacitationHandler {
    private static final String DOWNED = "lastbreath_downed";
    private static final String FINAL_DEATH = "lastbreath_final_death";
    private static final String DOWN_TICKS = "lastbreath_down_ticks";
    private static final String REVIVE_TICKS = "lastbreath_revive_ticks";
    private static final String REVIVE_HELD = "lastbreath_revive_held";
    private static final String REVIVE_TARGET = "lastbreath_revive_target";
    private static final String ANCHOR_X = "lastbreath_anchor_x";
    private static final String ANCHOR_Y = "lastbreath_anchor_y";
    private static final String ANCHOR_Z = "lastbreath_anchor_z";
    private static final String OLD_MAYFLY = "lastbreath_old_mayfly";
    private static final String OLD_FLYING = "lastbreath_old_flying";
    private static final String SABLE_ACTIVE = "lastbreath_sable_active";

    private static final int BLEEDOUT_TICKS = 60 * 20;
    private static final int REVIVE_TICKS_REQUIRED = 8 * 20;
    private static final double REVIVE_DISTANCE_SQR = 3.0 * 3.0;

    private IncapacitationHandler() {}

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();

        // The vanilla /kill command uses the generic-kill damage type. It must
        // bypass the downed-state interception and always reach normal death.
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) {
            if (data.getBoolean(DOWNED)) {
                SableCompat.stop(player);
                data.putBoolean(DOWNED, false);
                data.putBoolean(REVIVE_HELD, false);
                restoreAbilities(player, data);
                player.setInvulnerable(false);
                player.invulnerableTime = 0;
                NetworkHandler.sendStatus(player, false, 0);
            }
            data.remove(FINAL_DEATH);
            return;
        }

        if (data.getBoolean(FINAL_DEATH)) {
            data.remove(FINAL_DEATH);
            return;
        }
        if (data.getBoolean(DOWNED)) {
            event.setCanceled(true);
            return;
        }

        event.setCanceled(true);
        beginDowned(player, data);
    }

    static void forceDowned(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(DOWNED)) return;
        data.remove(FINAL_DEATH);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        beginDowned(player, data);
    }

    /** Called only for TaCZ's explicit lethal-headshot event. */
    static void onTaczLethalHeadshot(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(DOWNED)) {
            finalDeath(player, data);
            return;
        }
        data.putBoolean(FINAL_DEATH, true);
        player.invulnerableTime = 0;
        player.setInvulnerable(false);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.hurt(player.damageSources().genericKill(), 1_000_000.0F);
    }

    /** A nonlethal TaCZ headshot affects only the victim's client presentation. */
    static void onTaczNonlethalHeadshot(ServerPlayer player) {
        NetworkHandler.sendHeadEffect(player, 80); // 4 seconds
    }

    private static void beginDowned(ServerPlayer player, CompoundTag data) {
        player.setHealth(1.0F);
        data.putBoolean(DOWNED, true);
        data.putInt(DOWN_TICKS, 0);
        data.putInt(REVIVE_TICKS, 0);
        data.putBoolean(REVIVE_HELD, false);
        data.remove(REVIVE_TARGET);
        data.putDouble(ANCHOR_X, player.getX());
        data.putDouble(ANCHOR_Y, player.getY());
        data.putDouble(ANCHOR_Z, player.getZ());

        data.putBoolean(OLD_MAYFLY, player.getAbilities().mayfly);
        data.putBoolean(OLD_FLYING, player.getAbilities().flying);
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();

        player.setDeltaMovement(0, 0, 0);
        player.setSprinting(false);
        player.setPose(Pose.STANDING);
        player.setAirSupply(player.getMaxAirSupply());

        boolean sableStarted = SableCompat.start(player);
        data.putBoolean(SABLE_ACTIVE, sableStarted);
        NetworkHandler.sendStatus(player, true, BLEEDOUT_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();

        if (data.getBoolean(DOWNED)) {
            tickDowned(player, data);
            return;
        }
        if (data.getBoolean(REVIVE_HELD)) {
            tickRevive(player, data);
        } else {
            data.putInt(REVIVE_TICKS, 0);
            data.remove(REVIVE_TARGET);
        }
    }

    private static void tickDowned(ServerPlayer player, CompoundTag data) {
        int elapsed = data.getInt(DOWN_TICKS) + 1;
        data.putInt(DOWN_TICKS, elapsed);

        // Keep the real player locked at the downed location; Sable renders the ragdoll separately.
        double x = data.getDouble(ANCHOR_X);
        double y = data.getDouble(ANCHOR_Y);
        double z = data.getDouble(ANCHOR_Z);
        if (player.distanceToSqr(x, y, z) > 0.0025) player.setPos(x, y, z);

        player.setDeltaMovement(0, 0, 0);
        player.setSprinting(false);
        player.setPose(Pose.STANDING);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;

        if (elapsed % 20 == 0) {
            NetworkHandler.sendStatus(player, true, Math.max(0, BLEEDOUT_TICKS - elapsed));
        }

        if (elapsed >= BLEEDOUT_TICKS) {
            if (player.getRandom().nextBoolean()) {
                wake(player, 0.35F);
            } else {
                finalDeath(player, data);
            }
        }
    }

    private static void tickRevive(ServerPlayer rescuer, CompoundTag rescuerData) {
        ServerPlayer target = rescuer.serverLevel().players().stream()
                .filter(p -> p != rescuer)
                .filter(p -> p.getPersistentData().getBoolean(DOWNED))
                .filter(p -> p.distanceToSqr(rescuer) <= REVIVE_DISTANCE_SQR)
                .min(Comparator.comparingDouble(rescuer::distanceToSqr))
                .orElse(null);

        if (target == null) {
            resetReviveProgress(rescuerData);
            return;
        }

        String targetId = target.getUUID().toString();
        if (!targetId.equals(rescuerData.getString(REVIVE_TARGET))) {
            rescuerData.putString(REVIVE_TARGET, targetId);
            rescuerData.putInt(REVIVE_TICKS, 0);
        }

        int progress = rescuerData.getInt(REVIVE_TICKS) + 1;
        rescuerData.putInt(REVIVE_TICKS, progress);
        if (progress >= REVIVE_TICKS_REQUIRED) {
            wake(target, 0.50F);
            resetReviveProgress(rescuerData);
            rescuerData.putBoolean(REVIVE_HELD, false);
        }
    }

    private static void resetReviveProgress(CompoundTag data) {
        data.putInt(REVIVE_TICKS, 0);
        data.remove(REVIVE_TARGET);
    }

    static void setReviveHeld(ServerPlayer player, boolean held) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(REVIVE_HELD, held);
        if (!held) resetReviveProgress(data);
    }

    static void giveUp(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(DOWNED)) return;
        finalDeath(player, data);
    }

    private static void finalDeath(ServerPlayer player, CompoundTag data) {
        SableCompat.stop(player);
        data.putBoolean(DOWNED, false);
        data.putBoolean(FINAL_DEATH, true);
        data.putBoolean(REVIVE_HELD, false);
        NetworkHandler.sendStatus(player, false, 0);
        restoreAbilities(player, data);
        player.setPose(Pose.STANDING);
        // setHealth(0) alone does not run the normal death/respawn pipeline.
        player.invulnerableTime = 0;
        player.setInvulnerable(false);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        player.hurt(player.damageSources().genericKill(), 1_000_000.0F);
    }

    private static void wake(ServerPlayer player, float healthFraction) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(DOWNED)) return;

        SableCompat.stop(player);
        data.putBoolean(DOWNED, false);
        data.remove(DOWN_TICKS);
        data.remove(REVIVE_TICKS);
        data.putBoolean(REVIVE_HELD, false);
        data.remove(REVIVE_TARGET);

        double x = data.getDouble(ANCHOR_X);
        double y = data.getDouble(ANCHOR_Y);
        double z = data.getDouble(ANCHOR_Z);
        player.setPos(x, y, z);
        restoreAbilities(player, data);
        player.setPose(Pose.STANDING);
        player.setDeltaMovement(0, 0, 0);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * healthFraction));
        player.invulnerableTime = 40;
        NetworkHandler.sendStatus(player, false, 0);
        WakeUpIntroCompat.playWakeUp(player);
    }

    private static void restoreAbilities(ServerPlayer player, CompoundTag data) {
        player.getAbilities().mayfly = data.getBoolean(OLD_MAYFLY);
        player.getAbilities().flying = data.getBoolean(OLD_FLYING) && data.getBoolean(OLD_MAYFLY);
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (isDowned(event.getEntity())) event.setCanceled(true);
    }

    private static boolean isDowned(Player player) {
        return player.getPersistentData().getBoolean(DOWNED);
    }
}
