package com.yasui_i.pwsablefix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PantographConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_SUBLEVEL_CONTACT = BUILDER
            .comment(
                    "Whether pantographs that sit on a Sable physics sub-level (i.e. on a physics train)",
                    "should look for the catenary in world space, like CEE's pantograph does.",
                    "Disable this to restore the unpatched PAW behaviour.",
                    "Default: true")
            .define("pantographs.sublevel_wire_contact", true);

    public static final ModConfigSpec.BooleanValue ENABLE_REDSTONE_CONTROL = BUILDER
            .comment("Extend PAW pantographs when the block receives a redstone signal.")
            .define("pantographs.redstone_control", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CEE_BRIDGE = BUILDER
            .comment("Simulate PAW contact/feed wires in CEE and let PAW pantographs collect from PAW or CEE wires.")
            .define("pantographs.cee_bridge", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PantographConfig() {
    }
}
