package com.yasui_i.pwsablefix;

import com.yasui_i.pwsablefix.config.PantographConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P&amp;W Sable Fix - makes Create: Pantographs &amp; Wires pantographs work on Sable physics trains.
 */
@Mod(PWSableFix.MOD_ID)
public class PWSableFix {
    public static final String MOD_ID = "pwsablefix";
    public static final Logger LOGGER = LoggerFactory.getLogger("P&W Sable Fix");

    public PWSableFix(IEventBus modEventBus, ModContainer modContainer) {
        // Redstone commands are decided by the logical server, including dedicated servers.
        modContainer.registerConfig(ModConfig.Type.COMMON, PantographConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
