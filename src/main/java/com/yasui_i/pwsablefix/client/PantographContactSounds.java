package com.yasui_i.pwsablefix.client;

import com.george_vi.electroenergetics.CEESoundEvents;
import com.yasui_i.pwsablefix.PWSableFix;
import com.yasui_i.pwsablefix.compat.PantographWireContact;
import com.yasui_i.pwsablefix.config.PantographConfig;
import com.yasui_i.pwsablefix.network.PantographContactSoundPayload;
import com.yasui_i.pwsablefix.sound.PantographContactSoundState;
import de.mrjulsen.paw.blockentity.PantographBlockEntity;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.HashMap;
import java.util.Map;

/** Electrical facts come from the server; the rendered head determines the presentation edge. */
@EventBusSubscriber(modid = PWSableFix.MOD_ID, value = Dist.CLIENT)
public final class PantographContactSounds {
    private static final double AUDIBLE_RADIUS = 24;
    private static final double CONTACT_TOLERANCE = .03;
    private static final int SNAPSHOT_TIMEOUT = 20;
    public static final int CONTACT_DURATION_TICKS = 7;
    public static final int DEPARTURE_DURATION_TICKS = 24;
    private static final Map<BlockPos, Contact> CONTACTS = new HashMap<>();
    private static ClientLevel currentLevel;

    private static final class Contact {
        PantographContactSoundPayload packet;
        long receivedAt;
        BlockEntity collector;
        PantographContactSoundState state = new PantographContactSoundState();
        Vec3 lastWirePoint;
        long lastSparkTick = Long.MIN_VALUE;
    }

    public static void receive(PantographContactSoundPayload packet) {
        var level = Minecraft.getInstance().level;
        useLevel(level);
        if (level == null || !Float.isFinite(packet.offsetX()) || !Float.isFinite(packet.offsetY())
                || !Float.isFinite(packet.offsetZ()) || !Float.isFinite(packet.current())
                || Math.abs(packet.offsetX()) > 2 || packet.offsetY() < 0
                || packet.offsetY() > 4 || Math.abs(packet.offsetZ()) > 2) return;
        var contact = CONTACTS.computeIfAbsent(packet.pos(), ignored -> new Contact());
        contact.packet = packet;
        contact.receivedAt = level.getGameTime();
        if (packet.wirePresent()) contact.lastWirePoint = packet.localPosition();
    }

    private static void useLevel(ClientLevel level) {
        if (level == currentLevel) return;
        CONTACTS.clear();
        currentLevel = level;
    }

    @SubscribeEvent public static void rendered(GeoRenderEvent.Block.Post event) {
        if (!(event.getBlockEntity() instanceof PantographBlockEntity collector)
                || !(collector.getLevel() instanceof ClientLevel level)) return;
        useLevel(level);
        var contact = CONTACTS.get(collector.getBlockPos());
        if (contact == null || level.getGameTime() - contact.receivedAt > SNAPSHOT_TIMEOUT) return;
        // PAW's head is independent. Its highest contact strip starts at 12.5 model pixels.
        // Read immediately: GeckoLib shares baked bones between instances.
        event.getModel().getBone("head").ifPresent(head ->
                sample(level, collector, contact, (12.5 + head.getPosY()) / 16.0,
                        (double) level.getGameTime() + event.getPartialTick()));
    }

    private static void sample(ClientLevel level, PantographBlockEntity collector, Contact contact,
                               double headHeight, double time) {
        if (!PantographConfig.ENABLE_CEE_BRIDGE.get() || !Double.isFinite(headHeight)) return;
        if (contact.collector != collector) {
            contact.collector = collector;
            contact.state = new PantographContactSoundState();
        }
        double wireHeight = ((PantographWireContact) collector).pwsablefix$wireHeight();
        boolean wirePresent = contact.packet.wirePresent() && wireHeight >= 0;
        boolean touching = wireHeight >= 0
                && headHeight >= PantographBlockEntity.MIN_HEIGHT + wireHeight - CONTACT_TOLERANCE;
        if (contact.state.update(time, collector.isExpandable(), wirePresent, touching,
                contact.packet.energized())) {
            var point = contact.lastWirePoint;
            if (point != null) {
                PantographArcEffects.emit(level, point);
                Minecraft.getInstance().getSoundManager().play(
                        new MovingArc(level, collector, point, contact.state.departure()));
            }
            contact.lastSparkTick = level.getGameTime();
        }
        long tick = level.getGameTime();
        if (contact.lastSparkTick == tick) return;
        contact.lastSparkTick = tick;
        if (collector.isExpandable() && wirePresent && touching && contact.packet.energized()
                && contact.lastWirePoint != null) {
            var local = contact.lastWirePoint;
            var sub = SableCompanion.INSTANCE.getContaining(level, collector.getBlockPos());
            var world = worldPosition(level, collector.getBlockPos(), local);
            double speed = sub == null ? 0 : sub.logicalPose().transformPosition(local)
                    .distanceTo(sub.lastPose().transformPosition(local));
            if (PantographArcEffects.shouldSpark(level, world, speed, contact.packet.current()))
                PantographArcEffects.emit(level, local);
        }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        useLevel(level);
        if (level == null) return;
        CONTACTS.entrySet().removeIf(entry -> level.getGameTime() - entry.getValue().receivedAt > SNAPSHOT_TIMEOUT
                || (entry.getValue().collector != null && entry.getValue().collector.isRemoved()));
        if (Minecraft.getInstance().isPaused()) return;
        // GeckoLib normally advances only while rendering. Run the same controller once per tick
        // so looking away neither loses PAW's one-tick stateChanged flag nor suppresses sound.
        for (var entry : CONTACTS.entrySet()) {
            if (level.getBlockEntity(entry.getKey()) instanceof PantographBlockEntity collector)
                sampleUnrendered(level, collector, entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void sampleUnrendered(ClientLevel level, PantographBlockEntity collector, Contact contact) {
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(collector);
        if (!(renderer instanceof GeoBlockRenderer<?> geo)) return;
        var pawRenderer = (GeoBlockRenderer<PantographBlockEntity>) geo;
        var model = pawRenderer.getGeoModel();
        var baked = model.getBakedModel(model.getModelResource(collector, pawRenderer));
        var state = new AnimationState<>(collector, 0, 0, 0, false);
        long instance = pawRenderer.getInstanceId(collector);
        state.setData(DataTickets.TICK, collector.getTick(collector));
        state.setData(DataTickets.BLOCK_ENTITY, collector);
        model.addAdditionalStateData(collector, instance, state::setData);
        model.handleAnimations(collector, instance, state, 0);
        baked.getBone("head").ifPresent(head -> sample(level, collector, contact,
                (12.5 + head.getPosY()) / 16.0, level.getGameTime()));
    }

    private static Vec3 worldPosition(ClientLevel level, BlockPos pos, Vec3 local) {
        var sub = SableCompanion.INSTANCE.getContaining(level, pos);
        return sub instanceof ClientSubLevelAccess client ? client.renderPose().transformPosition(local)
                : sub == null ? local : sub.logicalPose().transformPosition(local);
    }

    /** Keep the chosen long CEE recording, clipped differently for contact and departure. */
    private static final class MovingArc extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockEntity collector;
        private final Vec3 localPosition;
        private final int duration;
        private final int fadeTicks;
        private int age;

        private MovingArc(ClientLevel level, BlockEntity collector, Vec3 localPosition, boolean departure) {
            super(CEESoundEvents.ARC.get(), SoundSource.BLOCKS, RandomSource.create());
            this.level = level;
            this.collector = collector;
            this.localPosition = localPosition;
            duration = departure ? DEPARTURE_DURATION_TICKS : CONTACT_DURATION_TICKS;
            fadeTicks = departure ? 3 : 2;
            attenuation = Attenuation.NONE;
            updatePosition();
        }

        private void updatePosition() {
            var world = worldPosition(level, collector.getBlockPos(), localPosition);
            // Sable wraps sounds and transforms these plot coordinates exactly once.
            x = localPosition.x; y = localPosition.y; z = localPosition.z;
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double distance = camera.distanceTo(world);
            float envelope = Math.clamp((duration - age) / (float) fadeTicks, 0, 1);
            volume = 0.9F * envelope * (float) Math.clamp((AUDIBLE_RADIUS - distance) / (AUDIBLE_RADIUS - 4), 0, 1);
        }

        @Override public boolean canStartSilent() { return true; }

        @Override public void tick() {
            if (Minecraft.getInstance().level != level || collector.isRemoved() || ++age >= duration
                    || level.getBlockEntity(collector.getBlockPos()) != collector) {
                stop();
                return;
            }
            updatePosition();
        }
    }

    private PantographContactSounds() {}
}
