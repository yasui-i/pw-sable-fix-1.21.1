package com.yasui_i.pwsablefix.client;

import com.george_vi.electroenergetics.config.CEEConfigs;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** CEE's medium pantograph spark, with sound handled by the animated contact edge. */
final class PantographArcEffects {
    private PantographArcEffects() {}

    static void emit(ClientLevel level, Vec3 localPosition) {
        // Sable transforms particles emitted in plot coordinates, including their velocities.
        double x = localPosition.x;
        double y = localPosition.y;
        double z = localPosition.z;
        for (int i = 0; i < 15; i++) {
            var velocity = Vec3.ZERO.offsetRandom(level.getRandom(), 0.1F);
            level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, velocity.x, velocity.y, velocity.z);
        }
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0, 0);
    }

    static boolean shouldSpark(ClientLevel level, Vec3 worldPosition, double blocksPerTick, double amps) {
        if (!(Math.abs(amps) > 7) || !Double.isFinite(blocksPerTick)) return false;

        var serverConfig = CEEConfigs.server();
        if (serverConfig == null || serverConfig.specification == null || !serverConfig.specification.isLoaded()) return false;
        var config = serverConfig.trainValues;
        var random = level.getRandom();
        double speed = Math.abs(blocksPerTick);
        double highSpeedThreshold = config.highSpeedPantographSparks.get();
        if (highSpeedThreshold > 0 && speed > highSpeedThreshold
                && random.nextFloat() < config.highSpeedPantographSparkChance.get()) return true;

        if (speed <= 0.1) return false;
        BlockPos contact = BlockPos.containing(worldPosition);
        if (!level.isLoaded(contact)) return false;
        if (config.winterPantographSparks.get() && random.nextFloat() < config.winterPantographSparkChance.get()
                && level.getBiome(contact).value().getBaseTemperature() < 0.15F) return true;
        return config.rainPantographSparks.get() && random.nextFloat() < config.rainPantographSparkChance.get()
                && level.isRainingAt(contact);
    }
}
