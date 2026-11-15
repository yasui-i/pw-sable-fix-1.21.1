package pwsablemultiarmtest;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;

/** No fabricated hit positions: the click must come from the world's actual outline ray cast. */
final class ArmClickChecks {
    static void run(ServerLevel level, FakePlayer player, BlockPos pos) {
        var state = level.getBlockState(pos);
        var device = (ElectricalDeviceBlock<?>) state.getBlock();
        var nodes = device.getNodePositions(level, pos, state);
        List<String> failures = new ArrayList<>();
        List<Vec3> aims = new ArrayList<>();
        // Include the real block outline, its edges, and the visible conductor endpoints.
        for (var box : state.getShape(level, pos).toAabbs()) {
            for (int x = 0; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 4; z++) {
                if (x != 0 && x != 4 && y != 0 && y != 4 && z != 0 && z != 4) continue;
                aims.add(new Vec3(box.minX + (box.maxX - box.minX) * x / 4,
                        box.minY + (box.maxY - box.minY) * y / 4,
                        box.minZ + (box.maxZ - box.minZ) * z / 4).add(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        for (var local : nodes.values()) {
            var point = local.add(pos.getX(), pos.getY(), pos.getZ());
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++)
                aims.add(point.add(x * .3, y * .3, z * .3));
        }
        player.setShiftKeyDown(false);
        for (int arm : nodes.keySet()) {
            var wanted = new InWorldNode(arm, pos);
            boolean selected = false;
            outer: for (var aim : aims) {
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    var offset = new Vec3(dx, dy, dz).normalize();
                    var from = aim.add(offset.scale(3));
                    var to = aim.subtract(offset.scale(1));
                    player.setPos(from.x, from.y - player.getEyeHeight(), from.z);
                    BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE, player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;
                    var nearest = InWorldNode.closestNode(level, pos, state, 1.5f, hit.getLocation());
                    if (!wanted.equals(nearest)) continue;
                    var spool = CEEItems.CREATIVE_WIRE_SPOOL.asStack();
                    player.setItemInHand(InteractionHand.MAIN_HAND, spool);
                    player.gameMode.useItemOn(player, level, spool, InteractionHand.MAIN_HAND, hit);
                    if (!wanted.equals(spool.get(CEEDataComponents.SELECTED_NODE)))
                        throw new AssertionError("Actual ray selected arm " + arm + " but block-first CEE spool rejected it: " + hit);
                    System.out.println("MULTIARM_CHECK PASS actual outline ray + spool arm=" + arm +
                            " from=" + from + " hit=" + hit.getLocation() + " anchor=" + hit.getBlockPos());
                    selected = true;
                    break outer;
                }
            }
            if (!selected) failures.add("arm=" + arm + " localEndpoint=" + nodes.get(arm));
        }
        if (!failures.isEmpty()) throw new AssertionError("CEE terminals have no actual outline-ray click path at " + pos +
                ": " + failures + "; outline=" + state.getShape(level, pos).toAabbs());
    }

    private ArmClickChecks() {}
}
