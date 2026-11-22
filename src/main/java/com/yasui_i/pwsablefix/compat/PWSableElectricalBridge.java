package com.yasui_i.pwsablefix.compat;

import com.george_vi.electroenergetics.CEEBlockEntityTypes;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlock;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.yasui_i.pwsablefix.config.PantographConfig;
import com.yasui_i.pwsablefix.mixin.CeePantographAccess;
import com.yasui_i.pwsablefix.sound.PantographContactSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import java.util.*;

/** PAW arms feed a real CEE terminal from PAW conductors or CEE's Sable-aware contact solver. */
@EventBusSubscriber(modid="pwsablefix")
public final class PWSableElectricalBridge {
    private static final Map<ServerLevel, Map<BlockPos, PantographBlockEntity>> PANTOGRAPHS = new WeakHashMap<>();
    private static final Map<ServerLevel,Map<BlockPos,PawWireElectricity.Contact>> PAW_CONTACTS=new WeakHashMap<>();
    private record Receiver(String consist,int car){}
    private record Breaker(boolean closed,long tick){}
    private static final Map<ServerLevel,Map<Receiver,Breaker>> BREAKERS=new WeakHashMap<>();
    private record CircuitContact(PantographBlockEntity delegate,Node node,boolean breaker){}
    private record CircuitSnapshot(CircuitBuilder builder,Map<BlockPos,CircuitContact> contacts){}
    private record SolvedContact(double voltage,double current){}
    private record SolvedSnapshot(long tick,Map<BlockPos,SolvedContact> contacts){}
    private static final Map<ServerLevel,CircuitSnapshot> IN_FLIGHT=new WeakHashMap<>();
    private static final Map<ServerLevel,SolvedSnapshot> SOLVED=new WeakHashMap<>();
    private static final SolvedContact ZERO=new SolvedContact(0,0);
    private PWSableElectricalBridge() {}

    public static void observe(ServerLevel level, BlockPos pos, boolean active) {
        if(!PantographConfig.ENABLE_CEE_BRIDGE.get()){forget(level,pos);return;}
        var commands=BREAKERS.get(level);
        if(commands!=null)commands.values().removeIf(command->level.getGameTime()-command.tick>20);
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof de.mrjulsen.paw.block.PantographBlock)){forget(level,pos);return;}
        var map=PANTOGRAPHS.computeIfAbsent(level,k->new HashMap<>());
        var delegate=map.get(pos);
        var ceeState=CEEBlocks.PANTOGRAPH.getDefaultState().setValue(PantographBlock.FACING,
                state.getValue(de.mrjulsen.paw.block.PantographBlock.FACING)).setValue(PantographBlock.DOUBLE,false);
        if(delegate==null){
            delegate=new PantographBlockEntity(CEEBlockEntityTypes.PANTOGRAPH.get(),pos.immutable(),ceeState);
            delegate.setLevel(level);map.put(pos.immutable(),delegate);
            ((ElectricalDeviceBlock<?>)state.getBlock()).ensureNodesExist(level,pos,state);
        } else delegate.setBlockState(ceeState);
        delegate.extended=active;
        delegate.tick();
        var contacts=PAW_CONTACTS.computeIfAbsent(level,k->new HashMap<>());
        var contact=active?PawWireElectricity.contact(level,pos,state):null;
        if(contact==null)contacts.remove(pos);else contacts.put(pos.immutable(),contact);
        var node=active?contact(level,pos,delegate):null;
        var measurement=node!=null?solved(level,pos):ZERO;
        // The collector touches the overhead supply before the main breaker, so an open VCB
        // must not suppress its contact arc. An unpowered wire cannot produce an arc.
        // Keep geometry while lowering: loss of redstone precedes the animated
        // head leaving the wire. Never substitute a fixed 1.7-block wire height.
        var visualContact=contact!=null?contact:(!active?PawWireElectricity.contact(level,pos,state):null);
        var point=visualContact!=null?visualContact.localPosition():CeeServerWireContact.localPosition(level,pos);
        boolean physical=point!=null&&(!active||node!=null);
        PantographContactSounds.observe(level,pos,physical,node!=null&&measurement.voltage>1,
                measurement.current,point);
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        PantographContactSounds.forget(level,pos);
        var contacts=PAW_CONTACTS.get(level);if(contacts!=null)contacts.remove(pos);
        var solved=SOLVED.get(level);if(solved!=null)solved.contacts.remove(pos);
        var map=PANTOGRAPHS.get(level);if(map==null)return;
        var delegate=map.remove(pos);if(delegate!=null)((CeePantographAccess)delegate).pwsablefix$disconnect();
    }

    public static void setBreaker(Level level,String consist,int car,boolean closed){
        if(level instanceof ServerLevel server&&(car==3||car==6)&&!consist.isBlank())
            BREAKERS.computeIfAbsent(server,k->new HashMap<>()).put(new Receiver(consist,car),new Breaker(closed,server.getGameTime()));
    }
    private static boolean breakerClosed(ServerLevel level,BlockPos pos){
        if(!(level.getBlockEntity(pos) instanceof RegisteredPantograph receiver))return false;
        if(receiver.pwsablefix$car()==0||receiver.pwsablefix$consist().isBlank())return true;
        var command=BREAKERS.getOrDefault(level,Map.of()).get(new Receiver(receiver.pwsablefix$consist(),receiver.pwsablefix$car()));
        return command!=null&&command.closed&&level.getGameTime()-command.tick<=20;
    }
    /** [online, raised, physical contact, breaker, line volts, collector amps]. */
    public static double[] sample(Level world,String consist,int car){
        double[] result=new double[6];if(!(world instanceof ServerLevel level))return result;
        var map=PANTOGRAPHS.get(level);if(map==null)return result;
        for(var entry:map.entrySet()){
            var pos=entry.getKey();if(!level.hasChunkAt(pos))continue;
            var be=level.getBlockEntity(pos);
            if(!(be instanceof RegisteredPantograph receiver)||be.isRemoved()||receiver.pwsablefix$car()!=car||!receiver.pwsablefix$consist().equals(consist))continue;
            var paw=(de.mrjulsen.paw.blockentity.PantographBlockEntity)be;
            var contact=contact(level,pos,entry.getValue());
            boolean raised=paw.isExpandable(),touching=raised&&contact!=null,closed=breakerClosed(level,pos);
            result[0]=1;result[1]=Math.max(result[1],raised?1:0);result[2]=Math.max(result[2],touching?1:0);result[3]=Math.max(result[3],closed?1:0);
            if(touching){
                var solved=solved(level,pos);
                result[4]=Math.max(result[4],solved.voltage);
                if(closed)result[5]+=solved.current;
            }
        }
        return result;
    }

    @SubscribeEvent public static void addToCircuit(AddToElectricGraphEvent event) {
        if(!PantographConfig.ENABLE_CEE_BRIDGE.get()){
            IN_FLIGHT.remove(event.level);SOLVED.remove(event.level);return;
        }
        var map=PANTOGRAPHS.get(event.level);
        if(map==null){
            PawWireElectricity.addCircuit(event,List.of());
            IN_FLIGHT.put(event.level,new CircuitSnapshot(event.builder,Map.of()));return;
        }
        for(var pos:List.copyOf(map.keySet())){
            if(!event.level.hasChunkAt(pos)||!(event.level.getBlockEntity(pos) instanceof de.mrjulsen.paw.blockentity.PantographBlockEntity)){
                forget(event.level,pos);continue;
            }
        }
        PawWireElectricity.addCircuit(event,PAW_CONTACTS.getOrDefault(event.level,Map.of()).values());
        var contacts=new HashMap<BlockPos,CircuitContact>();
        for(var pos:List.copyOf(map.keySet())){
            var contact=contact(event.level,pos,map.get(pos));
            boolean closed=breakerClosed(event.level,pos);
            if(contact!=null)contacts.put(pos,new CircuitContact(map.get(pos),contact,closed));
            if(contact!=null&&closed){
                var terminal=new InWorldNode(0,pos);
                PawWireElectricity.ensureNode(event,terminal);
                event.builder.connect(terminal,contact,ElectricalProperties.resistor(.01));
            }
        }
        // A collector's node changes at PAW/CEE wire seams. Keep the exact node and
        // breaker used to build this circuit, rather than querying a newer contact
        // against the previous circuit's results (missing nodes are reported as 0).
        IN_FLIGHT.put(event.level,new CircuitSnapshot(event.builder,contacts));
    }
    @SubscribeEvent public static void finishCircuit(FinishElectricSimulationEvent event){
        var circuit=IN_FLIGHT.remove(event.level);
        var contacts=new HashMap<BlockPos,SolvedContact>();
        if(PantographConfig.ENABLE_CEE_BRIDGE.get()&&circuit!=null&&circuit.builder==event.results.circuitBuilder){
            var delegates=PANTOGRAPHS.getOrDefault(event.level,Map.of());
            for(var entry:circuit.contacts.entrySet()){
                var pos=entry.getKey();var contact=entry.getValue();
                if(delegates.get(pos)!=contact.delegate)continue;
                double voltage=Math.abs(event.results.getVoltageAt(contact.node));
                double current=contact.breaker?Math.abs(event.results.getCurrentThrough(new InWorldNode(0,pos),contact.node)):0;
                contacts.put(pos,new SolvedContact(Double.isFinite(voltage)?voltage:0,Double.isFinite(current)?current:0));
            }
        }
        // Publish every completed result, including genuine zero volts/amps. This
        // is one coherent measurement, not a hold-last-nonzero display filter.
        SOLVED.put(event.level,new SolvedSnapshot(event.level.getGameTime(),contacts));
    }
    private static SolvedContact solved(ServerLevel level,BlockPos pos){
        var result=SOLVED.get(level);
        return result==null||level.getGameTime()-result.tick>2?ZERO:result.contacts.getOrDefault(pos,ZERO);
    }
    private static com.george_vi.electroenergetics.foundation.nodes.Node contact(ServerLevel level,BlockPos pos,PantographBlockEntity delegate){
        var paw=PAW_CONTACTS.getOrDefault(level,Map.of()).get(pos);
        if(paw!=null){
            var graph=de.mrjulsen.wires.graph.WireGraphManager.get(level,de.mrjulsen.wires.WiresApi.PAW_CATENARY_WIRES);
            if(graph.hasEdge(paw.edge()))return paw.node();
        }
        return ((CeePantographAccess)delegate).pwsablefix$attachedNode();
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){
        if(event.getLevel() instanceof ServerLevel level){
            PantographContactSounds.unload(level);
            BREAKERS.remove(level);
            IN_FLIGHT.remove(level);
            SOLVED.remove(level);
            PAW_CONTACTS.remove(level);
            var map=PANTOGRAPHS.remove(level);
            if(map!=null)for(var delegate:map.values())((CeePantographAccess)delegate).pwsablefix$disconnect();
        }
    }
}
