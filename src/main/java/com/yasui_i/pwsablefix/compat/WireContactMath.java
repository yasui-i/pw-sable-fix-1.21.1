package com.yasui_i.pwsablefix.compat;
import org.joml.Vector3d;
public final class WireContactMath {
    public static Vector3d intersection(Vector3d c,Vector3d d,Vector3d a,Vector3d b,Vector3d up){
        var width=new Vector3d(b).sub(a);var normal=new Vector3d(width).cross(up);var segment=new Vector3d(d).sub(c);
        double denominator=normal.dot(segment);if(Math.abs(denominator)<1E-8)return null;
        double t=normal.dot(new Vector3d(a).sub(c))/denominator;if(t<0||t>1)return null;
        var hit=new Vector3d(segment).mul(t).add(c);var relative=new Vector3d(hit).sub(a);
        double u=relative.dot(width)/width.lengthSquared(),v=relative.dot(up)/up.lengthSquared();
        return u>=0&&u<=1&&v>=0&&v<=1?hit:null;
    }
    private WireContactMath(){}
}
