package com.yasui_i.pwsablefix.compat;

import com.george_vi.electroenergetics.client.WireRenderer;
import org.joml.Vector3d;
import net.minecraft.world.level.Level;

/** CEE's loaded catenary geometry, intersected with PAW's physical collector head. */
public final class CeeWireContact {
    public static double height(Level level,Vector3d origin,Vector3d up,Vector3d right) {
        Vector3d a=new Vector3d(origin).sub(right), b=new Vector3d(origin).add(right);
        double best=Double.POSITIVE_INFINITY;
        for(var wire:WireRenderer.WIRE_CONNECTIONS){
            if(wire.getSecond().wireType().getSag()!=0)continue;
            var p=wire.getFirst().node1().getPosition(level);var q=wire.getFirst().node2().getPosition(level);
            if(p==null||q==null)continue;
            double h=intersect(new Vector3d(p.x,p.y,p.z),new Vector3d(q.x,q.y,q.z),a,b,up,origin);
            if(h>=0)best=Math.min(best,h);
        }
        for(var wire:WireRenderer.CATENARY){
            var p=wire.getStartPos();var q=wire.getEndingPos();
            double h=intersect(new Vector3d(p.x,p.y,p.z),new Vector3d(q.x,q.y,q.z),a,b,up,origin);
            if(h>=0)best=Math.min(best,h);
        }
        return Double.isFinite(best)?best:-1;
    }
    static double intersect(Vector3d c,Vector3d d,Vector3d a,Vector3d b,Vector3d up,Vector3d origin){
        Vector3d width=new Vector3d(b).sub(a),normal=new Vector3d(width).cross(up),segment=new Vector3d(d).sub(c);
        double denominator=normal.dot(segment);
        if(Math.abs(denominator)<1E-8)return -1;
        double t=normal.dot(new Vector3d(a).sub(c))/denominator;
        if(t<0||t>1)return -1;
        Vector3d hit=new Vector3d(segment).mul(t).add(c),relative=new Vector3d(hit).sub(a);
        double u=relative.dot(width)/width.lengthSquared(),v=relative.dot(up)/up.lengthSquared();
        return u>=0&&u<=1&&v>=0&&v<=1?new Vector3d(hit).sub(origin).dot(new Vector3d(up).normalize()):-1;
    }
    private CeeWireContact(){}
}
