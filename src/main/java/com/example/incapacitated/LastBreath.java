package com.example.incapacitated;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(LastBreath.MOD_ID)
public final class LastBreath {
    public static final String MOD_ID = "lastbreath";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LastBreath(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(IncapacitationHandler.class);
        NeoForge.EVENT_BUS.register(TaczCompat.class);
        modEventBus.addListener(NetworkHandler::register);
    }
}
