package com.yasui_i.pwsablefix.compat;

import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import de.mrjulsen.paw.item.CatenaryWireType;
import de.mrjulsen.paw.item.PowerWireType;
import de.mrjulsen.paw.registry.InsulatorWireDecoration;
import de.mrjulsen.wires.WiresApi;
import de.mrjulsen.wires.graph.*;
import de.mrjulsen.wires.graph.data.node.*;
import de.mrjulsen.wires.graph.data.provider.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import java.util.*;

/** Electrical topology is derived from real PAW edges, never from adjacent poles or wire proximity. */
public final class PawWireElectricity {
    public record Contact(UUID edge,String wire,double distance,AttachedNode node,double height,
                          net.minecraft.world.phys.Vec3 localPosition){}
    private record Tap(double distance,Node node){}
    private static AttachedNode junction(UUID id){return new AttachedNode(0,"PWSable/PAW/node/"+id);}
    /** PAW intentionally stores all arms on one WireNode; the endpoint provider
     * still records which actual arm a conductor reaches. Keep those contacts
     * separate even while the owning chunk is unloaded. No world-position proximity joins.
     */
    private static AttachedNode junction(WireNode node,ConnectorDataProvider connector){
        // Inline feeder nodes also return a Cantilever provider with a zero offset;
        // they must retain the same junction used by their exact parent-wire tap.
        if(node.getData() instanceof BlockConnectorNodeData&&connector instanceof CantileverConnectorDataProvider cantilever){
            var p=cantilever.getAttachOffset();
            return new AttachedNode(0,"PWSable/PAW/node/"+node.getId()+"/arm/"+
                    Math.round(p.x*10000)+"/"+Math.round(p.y*10000)+"/"+Math.round(p.z*10000));
        }
        return junction(node.getId());
    }
    private static AttachedNode tap(UUID edge,String id){return new AttachedNode(0,"PWSable/PAW/"+edge+"/"+id);}
    public static boolean conductive(WireEdge edge){return edge.getType() instanceof CatenaryWireType||edge.getType() instanceof PowerWireType;}
    private static String wireName(WireEdge edge){return edge.getType() instanceof CatenaryWireType?"contact":"main";}
    public static Contact contact(ServerLevel level,BlockPos pos,BlockState state){
        var graph=WireGraphManager.get(level,WiresApi.PAW_CATENARY_WIRES);
        var facing=state.getValue(de.mrjulsen.paw.block.PantographBlock.FACING);
        if(facing.getAxis()==net.minecraft.core.Direction.Axis.X)facing=facing.getOpposite();
        double angle=Math.toRadians(-facing.toYRot());
        var origin=new Vector3d(pos.getX()+.5,pos.getY()+de.mrjulsen.paw.blockentity.PantographBlockEntity.MIN_HEIGHT,pos.getZ()+.5);
        var up=new Vector3d(0,3.6,0);var right=new Vector3d(1.25,0,0).rotateY(angle);
        origin.add(new Vector3d(0,0,.25).rotateY(angle));
        var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,pos);
        if(sub!=null){var pose=sub.logicalPose();pose.transformPosition(origin,origin);pose.transformNormal(up,up);pose.transformNormal(right,right);}
        var lo=new Vector3d(origin).sub(right);var hi=new Vector3d(origin).add(right);
        var c=new Vector3d(lo).add(up);var d=new Vector3d(hi).add(up);
        double minX=Math.min(Math.min(lo.x,hi.x),Math.min(c.x,d.x)),maxX=Math.max(Math.max(lo.x,hi.x),Math.max(c.x,d.x));
        double minY=Math.min(Math.min(lo.y,hi.y),Math.min(c.y,d.y)),maxY=Math.max(Math.max(lo.y,hi.y),Math.max(c.y,d.y));
        double minZ=Math.min(Math.min(lo.z,hi.z),Math.min(c.z,d.z)),maxZ=Math.max(Math.max(lo.z,hi.z),Math.max(c.z,d.z));
        Set<UUID> checked=new HashSet<>();Contact best=null;
        for(var block:BlockPos.betweenClosed((int)Math.floor(minX)-1,(int)Math.floor(minY)-1,(int)Math.floor(minZ)-1,(int)Math.floor(maxX)+1,(int)Math.floor(maxY)+1,(int)Math.floor(maxZ)+1)){
            for(var collision:graph.getCollisionsInBlock(block)){
                if(!checked.add(collision.getId()))continue;
                var edge=graph.getEdge(collision.getId());if(edge==null||!conductive(edge))continue;
                String name=wireName(edge);if(!collision.hasWire(name))continue;
                for(var segment:collision.getAllCollisions()){
                    if(!segment.getWireName().equals(name))continue;
                    var hit=WireContactMath.intersection(segment.getAbsoluteInVector(),segment.getAbsoluteOutVector(),lo,hi,up);
                    if(hit==null)continue;
                    double height=new Vector3d(hit).sub(origin).dot(new Vector3d(up).normalize());
                    if(height>de.mrjulsen.paw.blockentity.PantographBlockEntity.DELTA_HEIGHT+.02)continue;
                    double distance=collision.worldPosToWirePos(name,hit);
                    boolean insulated=edge.getDecorations().stream().anyMatch(v->v.getDecoration() instanceof InsulatorWireDecoration&&v.getWireName().equals(name)&&Math.abs(v.getPos()-distance)<.12);
                    if(!insulated&&(best==null||height<best.height)){
                        var point=new net.minecraft.world.phys.Vec3(hit.x,hit.y,hit.z);
                        if(sub!=null)point=sub.logicalPose().transformPositionInverse(point);
                        best=new Contact(edge.getId(),name,distance,tap(edge.getId(),"collector/"+pos.asLong()),height,point);
                    }
                }
            }
        }
        return best;
    }
    public static void addCircuit(AddToElectricGraphEvent event,Collection<Contact> contacts){
        var graph=WireGraphManager.get(event.level,WiresApi.PAW_CATENARY_WIRES);
        Map<UUID,List<Tap>> taps=new HashMap<>();
        for(var contact:contacts)taps.computeIfAbsent(contact.edge,k->new ArrayList<>()).add(new Tap(contact.distance,contact.node));
        // PAW feeder taps store their exact parent wire ID and distance fraction.
        for(var node:graph.getNodes()){
            if(!(node.getData() instanceof CatenaryWireConnectorNodeData data))continue;
            var nbt=data.serializeNbt();var id=de.mrjulsen.wires.network.WireId.fromNbt(nbt.getCompound("WireId")).orElse(null);
            if(id==null)continue;var parent=graph.getEdge(id.id());var collision=graph.getCollisionById(id.id()).orElse(null);
            if(parent!=null&&conductive(parent)&&wireName(parent).equals(id.name())&&collision!=null&&collision.hasWire(id.name()))
                taps.computeIfAbsent(id.id(),k->new ArrayList<>()).add(new Tap(Math.clamp(nbt.getFloat("PosPercentage"),0,1)*collision.length(id.name()),junction(node.getId())));
        }
        for(var edge:graph.getEdges()){
            if(!conductive(edge))continue;
            var a=graph.getNode(edge.getNodeAId());var b=graph.getNode(edge.getNodeBId());if(a==null||b==null)continue;
            var data=edge.getWireConnectionData();
            var start=junction(a,data.connectorA());var end=junction(b,data.connectorB());
            String name=wireName(edge);var collision=graph.getCollisionById(edge.getId()).orElse(null);
            double length=collision!=null&&collision.hasWire(name)?collision.length(name):a.getPos().distance(b.getPos());
            if(!Double.isFinite(length)||length<=0)continue;
            var points=new ArrayList<Tap>();points.add(new Tap(0,start));points.add(new Tap(length,end));
            for(var point:taps.getOrDefault(edge.getId(),List.of()))points.add(new Tap(Math.clamp(point.distance,0,length),point.node));
            // Ignore malformed/outdated decorations outside this conductor's actual span.
            List<Double> cuts=edge.getDecorations().stream().filter(v->v.getDecoration() instanceof InsulatorWireDecoration&&v.getWireName().equals(name)).map(v->v.getPos()).filter(distance->Double.isFinite(distance)&&distance>=0&&distance<=length).sorted().toList();
            for(int i=0;i<cuts.size();i++){
                double distance=Math.clamp(cuts.get(i),0,length);
                points.add(new Tap(Math.max(0,distance-.05),tap(edge.getId(),"insulator/"+i+"/left")));
                points.add(new Tap(Math.min(length,distance+.05),tap(edge.getId(),"insulator/"+i+"/right")));
            }
            points.sort(Comparator.comparingDouble(Tap::distance));
            for(var point:points)ensureNode(event,point.node);
            for(int i=1;i<points.size();i++){
                var from=points.get(i-1);var to=points.get(i);
                // A tap exactly on an insulator must not bridge its two sides through a shared node.
                boolean crosses=cuts.stream().anyMatch(cut->from.distance<cut+.05&&to.distance>cut-.05);
                if(!crosses&&!from.node.equals(to.node))event.builder.connect(from.node,to.node,ElectricalProperties.resistor(Math.max(.00001,(to.distance-from.distance)*.001)));
            }
            terminal(event,a,data.connectorA(),start);terminal(event,b,data.connectorB(),end);
        }
    }
    private static void terminal(AddToElectricGraphEvent event,WireNode node,ConnectorDataProvider connector,AttachedNode junction){
        if(!(node.getData() instanceof BlockConnectorNodeData data))return;
        var pos=data.getPos();if(!event.level.hasChunkAt(pos))return;
        if(!(event.level.getBlockState(pos).getBlock() instanceof com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock<?>))return;
        int arm=0;
        if(event.level.getBlockState(pos).getBlock() instanceof de.mrjulsen.paw.block.abstractions.AbstractCantileverBlock){
            arm=PawArmTerminals.armForConnector(event.level,pos,connector);
            if(arm<0)return; // A deleted or changed arm must never be remapped to another live circuit.
        }
        var terminal=new InWorldNode(arm,pos);ensureNode(event,terminal);ensureNode(event,junction);
        event.builder.connect(terminal,junction,ElectricalProperties.resistor(.00001));
    }
    static void ensureNode(AddToElectricGraphEvent event,Node node){if(event.builder.getNode(node)==null)event.builder.addNode(node);}
    private PawWireElectricity(){}
}
