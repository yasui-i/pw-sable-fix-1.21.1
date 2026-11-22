package com.yasui_i.pwsablefix.sound;

import com.yasui_i.pwsablefix.network.PantographContactSoundPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Sync electrical facts, not an animation timer. */
public final class PantographContactSounds {
    private record Snapshot(PantographContactSoundPayload packet,long sentAt){}
    private static final Map<ServerLevel, Map<BlockPos, Snapshot>> CONTACTS = new WeakHashMap<>();
    private static final double DELIVERY_RADIUS_SQUARED = 128 * 128;

    public static void observe(ServerLevel level, BlockPos pos, boolean physicalContact, boolean energizedContact,
                               double current, Vec3 point) {
        var states = CONTACTS.computeIfAbsent(level, ignored -> new HashMap<>());
        var previous=states.get(pos);
        // A departure starts at the last real wire hit, never at a guessed height.
        if(point==null)point=previous==null?Vec3.atCenterOf(pos):previous.packet.localPosition();
        var packet = new PantographContactSoundPayload(pos.immutable(),(float)(point.x-pos.getX()),
                (float)(point.y-pos.getY()),(float)(point.z-pos.getZ()),physicalContact,energizedContact,
                (float)(Double.isFinite(current)?Math.min(Math.abs(current),Float.MAX_VALUE):0));
        long now=level.getGameTime();
        if(previous!=null&&now-previous.sentAt<5&&packet.equals(previous.packet))return;
        states.put(pos.immutable(),new Snapshot(packet,now));
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
