package com.example.incapacitated;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Small in-game test command for the downed-state flow. */
@EventBusSubscriber(modid = LastBreath.MOD_ID)
public final class CommandEvents {
    private CommandEvents() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kill_undeath")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    IncapacitationHandler.forceDowned(player);
                    return 1;
                }));
    }
}
