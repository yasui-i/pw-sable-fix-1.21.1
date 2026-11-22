package com.yasui_i.pwsablefix.compat;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import de.mrjulsen.paw.block.PantographBlock;
import de.mrjulsen.paw.blockentity.PantographBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.HashMap;

/** The actual CEE conductor hit in PAW's collector plane, including while lowering. */
public final class CeeServerWireContact {
    public static Vec3 localPosition(ServerLevel level, BlockPos pos) {
        var facing=level.getBlockState(pos).getValue(PantographBlock.FACING);
        if(facing.getAxis()==Direction.Axis.X)facing=facing.getOpposite();
        double angle=Math.toRadians(-facing.toYRot());
        var origin=new Vector3d(pos.getX()+.5,pos.getY()+PantographBlockEntity.MIN_HEIGHT,pos.getZ()+.5)
                .add(new Vector3d(0,0,.25).rotateY(angle));
        var up=new Vector3d(0,PantographBlockEntity.DELTA_HEIGHT,0);
        var right=new Vector3d(1.25,0,0).rotateY(angle);
        var sub=SableCompanion.INSTANCE.getContaining(level,pos);
        if(sub!=null){var pose=sub.logicalPose();pose.transformPosition(origin,origin);pose.transformNormal(up,up);pose.transformNormal(right,right);}
        var a=new Vector3d(origin).sub(right);var b=new Vector3d(origin).add(right);
        var lo=new Vector3d(a).min(b).min(new Vector3d(a).add(up)).min(new Vector3d(b).add(up));
        var hi=new Vector3d(a).max(b).max(new Vector3d(a).add(up)).max(new Vector3d(b).add(up));
        var infrastructure=InfrastructureSavedData.load(level);
        var connections=new HashMap<InWorldNodeConnection,ConnectionEntry>();
        for(int x=(int)Math.floor(lo.x)>>4;x<=((int)Math.floor(hi.x)>>4);x++)
            for(int y=(int)Math.floor(lo.y)>>4;y<=((int)Math.floor(hi.y)>>4);y++)
                for(int z=(int)Math.floor(lo.z)>>4;z<=((int)Math.floor(hi.z)>>4);z++)
                    infrastructure.wireSimulationState.getConnectionsInSection(SectionPos.asLong(x,y,z),connections::put);
        Vector3d best=null;
        for(var entry:connections.entrySet()){
            if(entry.getValue().wireData.wireType().getSag()!=0)continue;
            var p=entry.getKey().node1().getPosition(level);var q=entry.getKey().node2().getPosition(level);
            var hit=intersection(p,q,a,b,up);
            if(hit!=null&&(best==null||new Vector3d(hit).sub(origin).dot(up)<new Vector3d(best).sub(origin).dot(up)))best=hit;
        }
        for(var connection:infrastructure.getAllCatenaryConnections()){
            var hit=intersection(connection.getStartPos(),connection.getEndingPos(),a,b,up);
            if(hit!=null&&(best==null||new Vector3d(hit).sub(origin).dot(up)<new Vector3d(best).sub(origin).dot(up)))best=hit;
        }
        if(best==null)return null;
        var point=new Vec3(best.x,best.y,best.z);
        return sub==null?point:sub.logicalPose().transformPositionInverse(point);
    }
    private static Vector3d intersection(Vec3 p,Vec3 q,Vector3d a,Vector3d b,Vector3d up){
        return p==null||q==null?null:WireContactMath.intersection(new Vector3d(p.x,p.y,p.z),new Vector3d(q.x,q.y,q.z),a,b,up);
    }
    private CeeServerWireContact(){}
}
