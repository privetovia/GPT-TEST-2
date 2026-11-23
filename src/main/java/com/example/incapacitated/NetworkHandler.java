package com.example.incapacitated;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ReviveInput.TYPE, ReviveInput.STREAM_CODEC, NetworkHandler::handleInput);
        registrar.playToClient(DownedStatus.TYPE, DownedStatus.STREAM_CODEC, NetworkHandler::handleStatus);
        registrar.playToClient(HeadEffect.TYPE, HeadEffect.STREAM_CODEC, NetworkHandler::handleHeadEffect);
    }

    private static void handleInput(ReviveInput payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.giveUp()) {
                    IncapacitationHandler.giveUp(player);
                } else {
                    IncapacitationHandler.setReviveHeld(player, payload.held());
                }
            }
        });
    }

    private static void handleStatus(DownedStatus payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientEvents.updateDownedStatus(payload.downed(), payload.remainingTicks()));
    }

    private static void handleHeadEffect(HeadEffect payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientEvents.triggerHeadEffect(payload.durationTicks()));
    }

    public record HeadEffect(int durationTicks) implements CustomPacketPayload {
        public static final Type<HeadEffect> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(LastBreath.MOD_ID, "head_effect"));
        public static final StreamCodec<ByteBuf, HeadEffect> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, HeadEffect::durationTicks, HeadEffect::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReviveInput(boolean held, boolean giveUp) implements CustomPacketPayload {
        public static final Type<ReviveInput> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(LastBreath.MOD_ID, "revive_input"));
        public static final StreamCodec<ByteBuf, ReviveInput> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, ReviveInput::held,
                        ByteBufCodecs.BOOL, ReviveInput::giveUp, ReviveInput::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DownedStatus(boolean downed, int remainingTicks) implements CustomPacketPayload {
        public static final Type<DownedStatus> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(LastBreath.MOD_ID, "downed_status"));
        public static final StreamCodec<ByteBuf, DownedStatus> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, DownedStatus::downed,
                        ByteBufCodecs.VAR_INT, DownedStatus::remainingTicks, DownedStatus::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendReviveState(boolean held) {
        PacketDistributor.sendToServer(new ReviveInput(held, false));
    }

    public static void sendGiveUp() {
        PacketDistributor.sendToServer(new ReviveInput(false, true));
    }

    static void sendStatus(ServerPlayer player, boolean downed, int remainingTicks) {
        PacketDistributor.sendToPlayer(player, new DownedStatus(downed, remainingTicks));
    }

    static void sendHeadEffect(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new HeadEffect(durationTicks));
    }
}
