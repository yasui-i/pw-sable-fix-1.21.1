package com.yasui_i.pwsablefix.network;

import com.yasui_i.pwsablefix.PWSableFix;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Electrical facts; clients align their presentation with the actual animated collector head. */
@EventBusSubscriber(modid = PWSableFix.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record PantographContactSoundPayload(BlockPos pos, float offsetX, float offsetY, float offsetZ,
                                          boolean wirePresent, boolean energized, float current)
        implements CustomPacketPayload {
    public static final Type<PantographContactSoundPayload> TYPE =
            new Type<>(PWSableFix.id("pantograph_contact_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PantographContactSoundPayload> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.pos);
                buffer.writeFloat(packet.offsetX);
                buffer.writeFloat(packet.offsetY);
                buffer.writeFloat(packet.offsetZ);
                buffer.writeBoolean(packet.wirePresent);
                buffer.writeBoolean(packet.energized);
                buffer.writeFloat(packet.current);
            }, buffer -> new PantographContactSoundPayload(buffer.readBlockPos(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),buffer.readBoolean(),
                    buffer.readBoolean(),buffer.readFloat()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public Vec3 localPosition() {
        return new Vec3(pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ);
    }

    public Vec3 worldPosition(Level level) {
        var sub = SableCompanion.INSTANCE.getContaining(level, pos);
        return sub == null ? localPosition() : sub.logicalPose().transformPosition(localPosition());
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(TYPE, CODEC, (packet, context) ->
                context.enqueueWork(() -> com.yasui_i.pwsablefix.client.PantographContactSounds.receive(packet)));
    }
}
