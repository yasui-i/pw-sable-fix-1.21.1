package pwsablemovingelectricaltest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.yasui_i.pwsablefix.compat.*;
import de.mrjulsen.paw.blockentity.CantileverBlockEntity;
import de.mrjulsen.paw.blockentity.PantographBlockEntity;
import de.mrjulsen.paw.registry.*;
import de.mrjulsen.wires.WiresApi;
import de.mrjulsen.wires.graph.*;
import de.mrjulsen.wires.graph.data.node.BlockConnectorNodeData;
import de.mrjulsen.wires.item.CustomData;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.commons.lang3.mutable.MutableInt;
import org.joml.Vector3d;
import java.nio.file.*;
import java.util.*;

/** Real PAW three-arm endpoints, real assembled Sable body and CEE closed load circuit.
 * Movement uses a prescribed pose rather than a player's driven train, to isolate electrical seams.
 */
@Mod("pwsablemovingelectricaltest")
public final class MovingElectricalHarness {
    static final BlockPos FIRST=new BlockPos(0,100,0), SOURCE=FIRST.east(8), P=new BlockPos(8,94,4);
    static final String CONSIST="MOVING-CHECK";
    static final boolean AC=Boolean.getBoolean("pwsable.test.movingAC");
    final List<BlockPos> poles=new ArrayList<>();
    final List<WireEdge> edges=new ArrayList<>();
    final StringBuilder evidence=new StringBuilder(),samples=new StringBuilder("tick,phase,targetZ,edge,contact,nodeVoltage,sampleVoltage,sampleCurrent,loadVoltage\n");
    MinecraftServer server;ServerLevel level;WireGraph graph;InfrastructureSavedData circuit;
    ServerSubLevel body;BlockPos collector,load;Vector3d firstContact;int tick,zeroSamples,zeroLoads,seams;UUID previousEdge;boolean done;
    public MovingElectricalHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::pre);NeoForge.EVENT_BUS.addListener(this::post);NeoForge.EVENT_BUS.addListener(this::returnCircuit);}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);evidence.append("PASS ").append(message).append('\n');System.out.println("MOVING_ELECTRICAL_CHECK "+message);}
    void place(BlockPos p,BlockState state){level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(p,state);if(state.getBlock() instanceof ElectricalDeviceBlock<?> d)d.ensureNodesExist(level,p,state);}
    void connect(BlockPos a,int ai,BlockPos b,int bi){circuit.connect(new InWorldNode(ai,a),new InWorldNode(bi,b),CEEWireTypes.CREATIVE.get());}
    void started(ServerStartedEvent event){
        server=event.getServer();level=server.overworld();
        try{
            for(int x=-2;x<=2;x++)for(int z=-2;z<=5;z++)level.setChunkForced(x,z,true);
            graph=WireGraphManager.get(level,WiresApi.PAW_CATENARY_WIRES);
            for(var edge:List.copyOf(graph.getEdges()))graph.removeEdge(edge.getId(),new Vector3d(),Optional.empty());
            var poleBlock=ModBlocks.CANTILEVERS.values().iterator().next().get();
            for(int i=0;i<6;i++){
                var p=FIRST.south(i*12);poles.add(p);place(p,poleBlock.defaultBlockState());
                var be=(CantileverBlockEntity)level.getBlockEntity(p);be.setWidth(5);be.setHeight(2);be.setCatenaryHeight(1);
                be.getSubCantileverSettings().clear();for(int a=0;a<3;a++)be.getSubCantileverSettings().add(CantileverBlockEntity.SubCantileverSetting.EMPTY);be.update();
                var tag=be.saveWithFullMetadata(level.registryAccess());tag.putFloat("YOffset",.5f);be.loadWithComponents(tag,level.registryAccess());
                ((ElectricalDeviceBlock<?>)poleBlock).ensureNodesExist(level,p,level.getBlockState(p));
            }
            var custom=new CustomData(new CompoundTag());for(int i=0;i<12;i++){var tag=new CompoundTag();tag.putInt("CantileverIndex",0);custom.setCustomDataForPoint(i,tag);}
            var cursor=new MutableInt();for(int i=1;i<poles.size();i++){
                var result=graph.createEdge(ModWireRegistry.CATENARY_WIRE,custom,new BlockConnectorNodeData(poles.get(i-1)),new BlockConnectorNodeData(poles.get(i)),cursor,true);
                check(result.success(),"native PAW continuous wire edge "+i);edges.add(result.edge().orElseThrow());
            }
            var collision=graph.getCollisionById(edges.getFirst().getId()).orElseThrow();firstContact=collision.wirePosToWorldPos("contact",0);
            place(SOURCE,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            place(P,de.mrjulsen.paw.registry.ModBlocks.PANTOGRAPH.getDefaultState());
            place(P.south(2),CEEBlocks.CREATIVE_RESISTOR.getDefaultState());level.setBlockAndUpdate(P.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            // The load must remain in the same connected body. Isolated support blocks let
            // Sable split it into a falling second body and remove the stretched test cables.
            level.setBlockAndUpdate(P.south().below(),Blocks.STONE.defaultBlockState());
            var positions=List.of(P,P.east(),P.south(2),P.below(),P.east().below(),P.south().below(),P.south(2).below());
            body=SubLevelAssemblyHelper.assembleBlocks(level,P,positions,BoundingBox3i.from(positions));collector=body.getPlot().getCenterBlock();load=collector.south(2);
            check(level.getBlockEntity(collector) instanceof PantographBlockEntity,"PAW collector assembled on Sable plot");
            ((RegisteredPantograph)level.getBlockEntity(collector)).pwsablefix$configure(CONSIST,6);
            circuit=InfrastructureSavedData.load(level);
            for(var p:List.of(SOURCE,collector,load))((ElectricalDeviceBlock<?>)level.getBlockState(p).getBlock()).ensureNodesExist(level,p,level.getBlockState(p));
            ((ResistorBlockEntity)level.getBlockEntity(load)).setResistance(1000);
            connect(SOURCE,1,FIRST,0);connect(collector,0,load,0);
        }catch(Throwable error){finish(error);}
    }
    void returnCircuit(AddToElectricGraphEvent event){
        if(event.level!=level||load==null)return;
        // This isolated bench has no rail return. Model an ideal shared return in
        // the real solver; a moving physical cord to a fixed battery stretches
        // and breaks, which would test cord mechanics rather than collection.
        var sourceReturn=new InWorldNode(0,SOURCE);var loadReturn=new InWorldNode(1,load);
        if(event.builder.getNode(sourceReturn)==null)event.builder.addNode(sourceReturn);
        if(event.builder.getNode(loadReturn)==null)event.builder.addNode(loadReturn);
        event.builder.connect(sourceReturn,loadReturn,ElectricalProperties.resistor(.00001));
    }
    double targetZ(){if(tick<30)return firstContact.z+2;if(tick>=190)return firstContact.z+8;int n=tick-30;double travel=n*4.861111111;double length=56;double reflected=travel%(2*length);return firstContact.z+2+(reflected<=length?reflected:2*length-reflected);}
    void move(double z,boolean offWire){
        var pose=body.logicalPose();pose.orientation().identity();
        var state=level.getBlockState(collector);var facing=state.getValue(de.mrjulsen.paw.block.PantographBlock.FACING);if(facing.getAxis()==Direction.Axis.X)facing=facing.getOpposite();
        double angle=Math.toRadians(-facing.toYRot());
        var local=new Vector3d(collector.getX()+.5,collector.getY()+PantographBlockEntity.MIN_HEIGHT,collector.getZ()+.5).add(new Vector3d(0,0,.25).rotateY(angle));
        var world=pose.transformPosition(local,new Vector3d());
        var delta=new Vector3d(firstContact.x+(offWire?10:0),firstContact.y-1.7,z).sub(world);
        pose.position().add(delta);body.forceUpdateGlobalBounds();
        var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();pipeline.teleport(body,new Vector3d(pose.position()),pose.orientation());pipeline.resetVelocity(body);
    }
    void pre(ServerTickEvent.Pre event){if(done||server==null||body==null)return;try{tick++;move(targetZ(),tick>=170&&tick<180);PWSableElectricalBridge.setBreaker(level,CONSIST,6,!(tick>=150&&tick<160));}catch(Throwable error){finish(error);}}
    void post(ServerTickEvent.Post event){
        if(done||server==null||body==null)return;
        try{
            if(tick==5){var source=DevicesSavedData.load(level).getDevice(SOURCE,CreativeBatteryDevice.class);source.voltage=AC?120*Math.sqrt(2):120;source.acFrequency=AC?50:0;}
            if(tick==25)check(level.getBlockEntity(load) instanceof ResistorBlockEntity,"actual CEE load remains attached to collector body after warmup");
            var panto=(PantographBlockEntity)level.getBlockEntity(collector);
            // Refresh after the deterministic pose update as production block ticks do.
            PWSableElectricalBridge.observe(level,collector,panto.isExpandable());
            var contact=PawWireElectricity.contact(level,collector,level.getBlockState(collector));
            var reading=PWSableElectricalBridge.sample(level,CONSIST,6);var result=circuit.ticker.lastResults;
            double v=result==null?0:Math.abs(result.getVoltageAt(load,0,1));double node=contact==null||result==null?0:result.getVoltageAt(contact.node());
            String phase=tick<30?"warmup":tick<150?"moving":tick<160?"breaker-open":tick<170?"restored":tick<180?"off-wire":tick<190?"restored":tick<200?"insulated":"restored";
            samples.append(tick).append(',').append(phase).append(',').append(targetZ()).append(',').append(contact==null?"none":contact.edge()).append(',').append(reading[2]).append(',').append(node).append(',').append(reading[4]).append(',').append(reading[5]).append(',').append(v).append('\n');
            if(tick>=30&&tick<150){
                check(contact!=null,"geometric wire contact at moving tick "+tick);
                if(previousEdge!=null&&!previousEdge.equals(contact.edge()))seams++;previousEdge=contact.edge();
                if(reading[4]<100||reading[5]<.01){zeroSamples++;System.out.println("MOVING_ELECTRICAL_DROPOUT tick="+tick+" sample="+Arrays.toString(reading)+" load="+v+" edge="+contact.edge());}
                if(v<100)zeroLoads++;
            }
            if(tick==157)check(reading[5]<.001&&v<1,"opening VCB removes real load current without holding stale power");
            if(tick==166)check(reading[4]>100&&reading[5]>.01&&v>100,"closing VCB restores actual supply");
            if(tick==177)check(reading[2]==0&&reading[4]<1&&reading[5]<.001&&v<1,"moving physically off wire loses real contact and supply");
            if(tick==188){check(seams>=12,"crossed multiple wire seams at synthetic 350 km/h: "+seams);check(zeroLoads==0,"real CEE load has no moving voltage dropouts: "+zeroLoads);check(zeroSamples==0,"telemetry has no moving voltage/current dropouts: "+zeroSamples);}
            if(tick==190){var edge=edges.getFirst();var c=graph.getCollisionById(edge.getId()).orElseThrow();check(edge.addDecoration(c.length("contact")*.5,"contact",new InsulatorWireDecoration(ModBlocks.INSULATOR_BROWN.asStack())),"installed insulator before stationary collector");}
            if(tick==197)check(reading[2]==1&&reading[4]<1&&reading[5]<.001&&v<1,"insulator cuts real voltage and current without cached power across section");
            if(tick==200){var edge=edges.getFirst();edge.removeDecorations(level,Optional.empty(),"contact",List.copyOf(edge.getDecorations()));}
            if(tick==207){check(reading[4]>100&&reading[5]>.01&&v>100,"removing insulator restores real supply");finish(null);}
            if(tick>240)throw new AssertionError("test timeout");
        }catch(Throwable error){finish(error);}
    }
    void finish(Throwable error){if(done)return;done=true;if(error!=null){error.printStackTrace();evidence.append("FAIL ").append(error).append('\n');}else evidence.append("RESULT PASS\n");try{Files.writeString(Path.of("moving-electrical-result.txt"),evidence.toString());Files.writeString(Path.of("moving-electrical-samples.csv"),samples.toString());}catch(Exception e){throw new RuntimeException(e);}System.out.println("MOVING_ELECTRICAL_RESULT "+(error==null?"PASS":"FAIL"));server.halt(false);}
}
