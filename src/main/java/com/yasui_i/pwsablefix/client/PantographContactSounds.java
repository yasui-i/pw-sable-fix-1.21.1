package com.yasui_i.pwsablefix.client;

import com.george_vi.electroenergetics.CEESoundEvents;
import com.yasui_i.pwsablefix.network.PantographContactSoundPayload;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/** User-selected long CEE arc, cut short at actual moving collector edges. */
public final class PantographContactSounds {
    private static final double AUDIBLE_RADIUS = 24;
    public static final int CONTACT_DURATION_TICKS = 7;
    public static final int DEPARTURE_DURATION_TICKS = 24;

    public static void play(PantographContactSoundPayload packet) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !Float.isFinite(packet.offsetX()) || !Float.isFinite(packet.offsetY())
                || !Float.isFinite(packet.offsetZ()) || Math.abs(packet.offsetX()) > 2
                || packet.offsetY() < 0 || packet.offsetY() > 4 || Math.abs(packet.offsetZ()) > 2) return;
        var collector = level.getBlockEntity(packet.pos());
        if (!(collector instanceof de.mrjulsen.paw.blockentity.PantographBlockEntity)) return;
        mc.getSoundManager().play(new MovingArc(level, collector, packet));
    }

    private static final class MovingArc extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockEntity collector;
        private final PantographContactSoundPayload packet;
        private final int duration;
        private final int fadeTicks;
        private int age;

        private MovingArc(ClientLevel level, BlockEntity collector, PantographContactSoundPayload packet) {
            super(CEESoundEvents.ARC.get(), SoundSource.BLOCKS, RandomSource.create());
            this.level = level;
            this.collector = collector;
            this.packet = packet;
            duration = packet.departure() ? DEPARTURE_DURATION_TICKS : CONTACT_DURATION_TICKS;
            fadeTicks = packet.departure() ? 3 : 2;
            attenuation = Attenuation.NONE;
            updatePosition();
        }

        private void updatePosition() {
            var local = packet.localPosition();
            var sub = SableCompanion.INSTANCE.getContaining(level, packet.pos());
            var worldPosition = sub instanceof ClientSubLevelAccess client
                    ? client.renderPose().transformPosition(local) : packet.worldPosition(level);
            // Sable wraps sounds and transforms these plot coordinates exactly once.
            x = local.x; y = local.y; z = local.z;
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double distance = camera.distanceTo(worldPosition);
            float envelope = Math.clamp((duration - age) / (float) fadeTicks, 0, 1);
            volume = 0.9F * envelope * (float) Math.clamp((AUDIBLE_RADIUS - distance) / (AUDIBLE_RADIUS - 4), 0, 1);
        }

        @Override public boolean canStartSilent() { return true; }

        @Override public void tick() {
            if (Minecraft.getInstance().level != level || collector.isRemoved() || ++age >= duration
                    || level.getBlockEntity(packet.pos()) != collector) {
                stop();
                return;
            }
            updatePosition();
        }
    }

    private PantographContactSounds() {}
}
