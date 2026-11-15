package com.yasui_i.pwsablefix.sound;

import com.yasui_i.pwsablefix.network.PantographContactSoundPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-authoritative contact edges. No synthetic voltage or proximity-only contact is accepted. */
public final class PantographContactSounds {
    private static final Map<ServerLevel, Map<BlockPos, PantographContactSoundState>> CONTACTS = new WeakHashMap<>();
    private static final double DELIVERY_RADIUS_SQUARED = 128 * 128;

    public static void observe(ServerLevel level, BlockPos pos, boolean active, boolean physicalContact, boolean energizedContact,
                               float offsetX, float offsetY, float offsetZ) {
        var states = CONTACTS.computeIfAbsent(level, ignored -> new HashMap<>());
        var state = states.computeIfAbsent(pos.immutable(), ignored -> new PantographContactSoundState());
        if (!state.update(level.getGameTime(), active, physicalContact, energizedContact)) return;
        var packet = new PantographContactSoundPayload(pos, offsetX, offsetY, offsetZ,state.departure());
        var worldPosition = packet.worldPosition(level);
        for (var player : level.players()) {
            if (player.getEyePosition().distanceToSqr(worldPosition) <= DELIVERY_RADIUS_SQUARED)
                PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        var states = CONTACTS.get(level);
        if (states != null) states.remove(pos);
    }

    public static void unload(ServerLevel level) { CONTACTS.remove(level); }

    private PantographContactSounds() {}
}
