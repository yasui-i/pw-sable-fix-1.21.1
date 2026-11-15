package com.yasui_i.pwsablefix.compat;

import de.mrjulsen.paw.block.abstractions.AbstractCantileverBlock;
import de.mrjulsen.paw.blockentity.CantileverBlockEntity;
import de.mrjulsen.wires.graph.data.provider.BasicConnectorDataProvider;
import de.mrjulsen.wires.graph.data.provider.ConnectorDataProvider;
import de.mrjulsen.wires.item.CustomData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Uses PAW's own arm selection and rotation, rather than a second geometry calculation. */
public final class PawArmTerminals {
    private static final double MATCH_DISTANCE_SQUARED = 1e-8;
    private static final Map<Integer, Vec3> SINGLE_CONNECTOR = Map.of(0, new Vec3(.5, .5, .5));

    /** CEE clicks use the mounting plate: PAW's long arms are selected by its own wire tool,
     * while a vanilla block hit can only reach the mounting plate. Keep the three CEE
     * feeds selectable there from left to right in the same order as the actual arms,
     * without changing the conductor geometry.
     */
    public static Map<Integer, Vec3> ceePositions(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AbstractCantileverBlock)) return SINGLE_CONNECTOR;
        var arms = positions(level, pos, state);
        if (arms.isEmpty()) return Map.of();
        var shape = state.getShape(level, pos);
        if (shape.isEmpty()) return Map.of();
        var bounds = shape.bounds();
        Vec3 center = new Vec3((bounds.minX + bounds.maxX) * .5,
                (bounds.minY + bounds.maxY) * .5, (bounds.minZ + bounds.maxZ) * .5);
        // The arm endpoints may also be staggered longitudinally. Use the transverse
        // direction, rotated with the cantilever, so the terminals form a horizontal row.
        var block = (AbstractCantileverBlock) state.getBlock();
        double angle = Math.toRadians(block.getYRotation(state));
        Vec3 right = new Vec3(Math.cos(angle), 0, -Math.sin(angle));
        if (arms.size() > 1) {
            var values = new java.util.ArrayList<>(arms.values());
            if (values.get(values.size() - 1).subtract(values.get(0)).dot(right) < 0) right = right.scale(-1);
        }
        double halfWidth = Double.POSITIVE_INFINITY;
        if (Math.abs(right.x) > 1e-6) halfWidth = Math.min(halfWidth, (bounds.maxX - bounds.minX) * .44 / Math.abs(right.x));
        if (Math.abs(right.z) > 1e-6) halfWidth = Math.min(halfWidth, (bounds.maxZ - bounds.minZ) * .44 / Math.abs(right.z));
        Map<Integer, Vec3> result = new LinkedHashMap<>();
        int index = 0;
        for (int arm : arms.keySet()) {
            double offset = arms.size() == 1 ? 0 : (2.0 * index / (arms.size() - 1) - 1) * halfWidth;
            result.put(arm, center.add(right.scale(offset)));
            index++;
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<Integer, Vec3> positions(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof AbstractCantileverBlock block)) return SINGLE_CONNECTOR;
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof CantileverBlockEntity entity))
            return Map.of();
        int count = Math.min(3, entity.getCantileverData().length);
        Map<Integer, Vec3> result = new LinkedHashMap<>();
        for (int arm = 0; arm < count; arm++) {
            CompoundTag selection = new CompoundTag();
            selection.putInt("CantileverIndex", arm);
            CustomData custom = new CustomData(new CompoundTag());
            custom.setCustomDataForPoint(0, selection);
            // This is a fresh, one-point query. Existing wire edges may use different point indices;
            // their already resolved endpoint providers are matched below instead of rereading NBT.
            ConnectorDataProvider connector = block.getConnectorData(level, pos, custom, 0);
            if (connector instanceof BasicConnectorDataProvider basic) {
                var offset = basic.getAttachOffset();
                if (Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z))
                    result.put(arm, new Vec3(offset.x, offset.y, offset.z));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** A stale/deleted arm must never silently fall back to another arm's electrical supply. */
    public static int armForConnector(Level level, BlockPos pos, ConnectorDataProvider connector) {
        if (!level.hasChunkAt(pos)
                || !(level.getBlockState(pos).getBlock() instanceof AbstractCantileverBlock)
                || !(connector instanceof BasicConnectorDataProvider basic)) return -1;
        var offset = basic.getAttachOffset();
        if (!Double.isFinite(offset.x) || !Double.isFinite(offset.y) || !Double.isFinite(offset.z)) return -1;
        Vec3 actual = new Vec3(offset.x, offset.y, offset.z);
        int match = -1;
        for (var entry : positions(level, pos, level.getBlockState(pos)).entrySet()) {
            if (entry.getValue().distanceToSqr(actual) <= MATCH_DISTANCE_SQUARED) {
                if (match >= 0) return -1; // Ambiguous geometry is not permission to join circuits.
                match = entry.getKey();
            }
        }
        return match;
    }

    private PawArmTerminals() {}
}
