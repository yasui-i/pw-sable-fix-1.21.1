package pwsablemultiarmtest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.authlib.GameProfile;
import com.yasui_i.pwsablefix.compat.PawArmTerminals;
import de.mrjulsen.paw.block.abstractions.AbstractCantileverBlock;
import de.mrjulsen.paw.blockentity.CantileverBlockEntity;
import de.mrjulsen.paw.registry.InsulatorWireDecoration;
import de.mrjulsen.paw.registry.ModBlocks;
import de.mrjulsen.paw.registry.ModWireRegistry;
import de.mrjulsen.wires.WiresApi;
import de.mrjulsen.wires.graph.*;
import de.mrjulsen.wires.graph.data.node.BlockConnectorNodeData;
import de.mrjulsen.wires.graph.data.provider.CantileverConnectorDataProvider;
import de.mrjulsen.wires.item.CustomData;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.commons.lang3.mutable.MutableInt;
import java.nio.file.*;
import java.util.*;

/** The actual CEE solver, real PAW graph/provider serialization, and Minecraft block-first spool clicks. */
@Mod("pwsablemultiarmtest")
public final class MultiarmHarness {
    static final BlockPos A=new BlockPos(0,100,0),B=A.south(12),C=A.south(24);
    static final BlockPos X=A.east(14),Y=X.south(24);
    final BlockPos[] sources={A.east(6),A.east(6).south(4),A.east(6).south(8)};
    final BlockPos[] loads={C.east(6),C.east(6).south(4),C.east(6).south(8)};
    final List<WireEdge> first=new ArrayList<>(),second=new ArrayList<>();
    final StringBuilder evidence=new StringBuilder();
    MinecraftServer server;ServerLevel level;FakePlayer player;InfrastructureSavedData circuit;WireGraph graph;
    WireEdge power;int tick;boolean done;
    public MultiarmHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean condition,String message){if(!condition)throw new AssertionError(message);evidence.append("PASS ").append(message).append('\n');System.out.println("MULTIARM_CHECK "+message);}
    void place(BlockPos pos,BlockState state){level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,state);if(state.getBlock() instanceof ElectricalDeviceBlock<?> d)d.ensureNodesExist(level,pos,state);}
    ElectricalDeviceBlock<?> device(BlockPos p){return (ElectricalDeviceBlock<?>)level.getBlockState(p).getBlock();}
    void pole(BlockPos p){
        var block=ModBlocks.CANTILEVERS.values().iterator().next().get();place(p,block.defaultBlockState());
        var be=(CantileverBlockEntity)level.getBlockEntity(p);
        be.setWidth(5);be.setHeight(2);be.setCatenaryHeight(1);
        be.getSubCantileverSettings().clear();
        for(int i=0;i<3;i++)be.getSubCantileverSettings().add(CantileverBlockEntity.SubCantileverSetting.EMPTY);
        be.update();
        var settings=be.saveWithFullMetadata(level.registryAccess());settings.putFloat("YOffset",.5f);be.loadWithComponents(settings,level.registryAccess());
        device(p).ensureNodesExist(level,p,level.getBlockState(p));
        check(device(p).getNodePositions(level,p,level.getBlockState(p)).size()==3,"three independent CEE terminals at "+p);
    }
    void started(ServerStartedEvent event){
        server=event.getServer();level=server.overworld();
        try{
            for(int x=-2;x<=2;x++)for(int z=-1;z<=3;z++)level.setChunkForced(x,z,true);
            player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000144"),"MultiarmCheck"));
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            graph=WireGraphManager.get(level,WiresApi.PAW_CATENARY_WIRES);
            for(var edge:List.copyOf(graph.getEdges()))graph.removeEdge(edge.getId(),new org.joml.Vector3d(),Optional.empty());
            pole(A);pole(B);pole(C);
            for(int i=0;i<3;i++){place(sources[i],CEEBlocks.CREATIVE_BATTERY.getDefaultState());place(loads[i],CEEBlocks.CREATIVE_RESISTOR.getDefaultState());}
            place(X,ModBlocks.INSULATOR_BROWN.getDefaultState());place(Y,ModBlocks.INSULATOR_BROWN.getDefaultState());
            circuit=InfrastructureSavedData.load(level);
            var testBlocks=new ArrayList<>(List.of(A,B,C,X,Y));testBlocks.addAll(Arrays.asList(sources));testBlocks.addAll(Arrays.asList(loads));
            for(var pos:testBlocks)for(int id:device(pos).getNodePositions(level,pos,level.getBlockState(pos)).keySet())
                for(var connection:List.copyOf(circuit.getConnections(new InWorldNode(id,pos))))circuit.removeConnectionNoDrops(connection);
            for(int arm=0;arm<3;arm++){
                var data=new CustomData(new CompoundTag());
                for(int point=0;point<4;point++){var tag=new CompoundTag();tag.putInt("CantileverIndex",arm);data.setCustomDataForPoint(point,tag);}
                var cursor=new MutableInt(0);
                first.add(edge(A,B,data,cursor,false));
                // A later edge must use its persisted providers, not blindly re-read points 0/1.
                var later=new CustomData(data.nbt().copy());
                for(int point=0;point<2;point++){var decoy=new CompoundTag();decoy.putInt("CantileverIndex",(arm+1)%3);later.setCustomDataForPoint(point,decoy);}
                second.add(edge(B,C,later,cursor,false));
                check(cursor.intValue()==4,"continuous multi-point construction consumes point indices 0..3 for arm "+arm);
                for(var edge:List.of(first.get(arm),second.get(arm))){
                    check(PawArmTerminals.armForConnector(level,((BlockConnectorNodeData)graph.getNode(edge.getNodeAId()).getData()).getPos(),edge.getWireConnectionData().connectorA())==arm,"edge A provider maps to correct arm "+arm);
                    check(PawArmTerminals.armForConnector(level,((BlockConnectorNodeData)graph.getNode(edge.getNodeBId()).getData()).getPos(),edge.getWireConnectionData().connectorB())==arm,"edge B provider maps to correct arm "+arm);
                }
            }
            power=edge(X,Y,new CustomData(new CompoundTag()),new MutableInt(),true);
        }catch(Throwable error){finish(error);}
    }
    WireEdge edge(BlockPos a,BlockPos b,CustomData data,MutableInt cursor,boolean feeder){
        var result=graph.createEdge(feeder?ModWireRegistry.ENERGY_WIRE:ModWireRegistry.CATENARY_WIRE,data,new BlockConnectorNodeData(a),new BlockConnectorNodeData(b),cursor,true);
        if(!result.success())throw new AssertionError("PAW edge creation failed "+a+" -> "+b+" code="+result.code());return result.edge().orElseThrow();
    }
    void click(ItemStack stack,BlockPos pos,int terminal){
        var state=level.getBlockState(pos);Vec3 local=device(pos).getNodePosition(level,pos,state,terminal);
        if(local==null)throw new AssertionError("missing terminal "+terminal+" at "+pos);
        var world=local.add(pos.getX(),pos.getY(),pos.getZ());
        player.setPos(world.x,world.y+.5,world.z+1.5);
        player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(world,Direction.UP,pos,false));
    }
    void spool(BlockPos a,int ai,BlockPos b,int bi,boolean copper){
        var stack=copper?CEEItems.COPPER_WIRE_SPOOL.asStack():CEEItems.CREATIVE_WIRE_SPOOL.asStack();
        player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        click(stack,a,ai);
        check(new InWorldNode(ai,a).equals(stack.get(CEEDataComponents.SELECTED_NODE)),"block-first "+(copper?"copper":"creative")+" spool selects "+ai+" at "+a);
        click(stack,b,bi);
        check(circuit.isConnected(new InWorldNode(ai,a),new InWorldNode(bi,b)),"block-first spool connects selected arm "+bi+" at "+b);
    }
    void connect(BlockPos a,int ai,BlockPos b,int bi){circuit.connect(new InWorldNode(ai,a),new InWorldNode(bi,b),CEEWireTypes.CREATIVE.get());}
    void disconnect(BlockPos a,int ai,BlockPos b,int bi){circuit.removeConnectionNoDrops(new InWorldNodeConnection(new InWorldNode(ai,a),new InWorldNode(bi,b)));}
    double volts(int arm){if(circuit.ticker.lastResults==null)throw new AssertionError("CEE solver produced no result");return Math.abs(circuit.ticker.lastResults.getVoltageAt(loads[arm],0,1));}
    void voltage(int arm,double expected,String detail){double value=volts(arm);double current=Math.abs(circuit.ticker.lastResults.getCurrentThrough(loads[arm],0,1));check(Double.isFinite(value)&&Math.abs(value-expected)<1&&Double.isFinite(current)&&(expected==0?current<.001:current>.01),detail+" measured="+value+" V / "+current+" A expected="+expected);}
    void all(double a,double b,double c,String detail){voltage(0,a,detail+" arm1");voltage(1,b,detail+" arm2");voltage(2,c,detail+" arm3");}
    void removeInsulators(WireEdge edge,String wire){edge.removeDecorations(level,Optional.empty(),wire,edge.getDecorations().stream().filter(d->d.getWireName().equals(wire)).toList());}
    void installInsulator(WireEdge edge,String wire){
        var collision=graph.getCollisionById(edge.getId()).orElseThrow();double distance=collision.length(wire)*.5;
        var world=collision.wirePosToWorldPos(wire,distance);
        var hit=new de.mrjulsen.paw.data.WireHitResult(new Vec3(world.x,world.y,world.z),distance,BlockPos.containing(world.x,world.y,world.z),graph.getId(),new de.mrjulsen.wires.network.WireId(edge.getId(),wire,edge.getType()));
        player.setItemInHand(InteractionHand.MAIN_HAND,ModBlocks.INSULATOR_BROWN.asStack().copyWithCount(2));
        if(edge.getType() instanceof de.mrjulsen.paw.item.CatenaryWireType type)type.use(level,player,InteractionHand.MAIN_HAND,hit);
        else ((de.mrjulsen.paw.item.PowerWireType)edge.getType()).use(level,player,InteractionHand.MAIN_HAND,hit);
        check(edge.getDecorations().stream().anyMatch(d->d.getWireName().equals(wire)&&d.getDecoration() instanceof InsulatorWireDecoration),"native PAW item interaction installs "+wire+" insulator");
        if(wire.equals("contact"))check(edge.getDecorations().stream().anyMatch(d->d.getWireName().equals("tension")&&d.getDecoration() instanceof InsulatorWireDecoration),"native catenary placement installs paired tension insulator");
    }
    void reload(){
        var saved=graph.save(new CompoundTag(),level.registryAccess());
        var copy=new WireGraph(graph.getId(),level).load(level,saved);
        check(copy.getEdges().size()==graph.getEdges().size(),"PAW NBT restores all edges");
        for(var edge:graph.getEdges()){
            var restored=copy.getEdge(edge.getId());check(restored!=null&&restored.getWireConnectionData().connectorA().serializeNbt().equals(edge.getWireConnectionData().connectorA().serializeNbt())&&restored.getWireConnectionData().connectorB().serializeNbt().equals(edge.getWireConnectionData().connectorB().serializeNbt()),"endpoint geometry and conductor UUID survive PAW NBT "+edge.getId());
        }
        for(var p:List.of(A,B,C)){
            var be=(CantileverBlockEntity)level.getBlockEntity(p);var before=device(p).getNodePositions(level,p,level.getBlockState(p));
            var tag=be.saveWithFullMetadata(level.registryAccess());be.loadWithComponents(tag,level.registryAccess());
            check(before.equals(device(p).getNodePositions(level,p,level.getBlockState(p))),"arm node IDs and positions survive block entity NBT "+p);
        }
        for(var edge:List.copyOf(graph.getEdges()))graph.removeEdge(edge.getId(),new org.joml.Vector3d(),Optional.empty());
        graph.load(level,saved);
        for(int i=0;i<3;i++){first.set(i,graph.getEdge(first.get(i).getId()));second.set(i,graph.getEdge(second.get(i).getId()));}power=graph.getEdge(power.getId());
    }
    void tick(ServerTickEvent.Post event){
        if(done||server==null)return;
        try{tick++;
            if(tick==2){
                var original=level.getBlockState(A);
                for(var facing:Direction.Plane.HORIZONTAL){
                    level.setBlockAndUpdate(A,original.setValue(de.mrjulsen.paw.block.abstractions.AbstractRotatableBlock.FACING,facing));
                    ArmClickChecks.run(level,player,A);
                }
                level.setBlockAndUpdate(A,original);
                device(A).ensureNodesExist(level,A,original);
                check(true,"all arms selectable through actual world ray and CEE spool in four facings");
            }
            if(tick==5){
                for(int i=0;i<3;i++){
                    var battery=DevicesSavedData.load(level).getDevice(sources[i],CreativeBatteryDevice.class);battery.voltage=(i+1)*120;battery.acFrequency=0;
                    DevicesSavedData.load(level).getDevice(loads[i],ResistorDevice.class).properties=ElectricalProperties.resistor(1000);
                    connect(loads[i],1,sources[i],0);spool(C,i,loads[i],0,i%2==1);
                }
                spool(sources[0],1,A,0,false);
            }
            if(tick==15){all(120,0,0,"single arm supply cannot energize other arms");spool(sources[1],1,A,1,true);}
            if(tick==25){all(120,240,0,"different-voltage supplies remain independent");spool(sources[2],1,A,2,false);}
            if(tick==35){all(120,240,360,"three separately fed arms and same-arm continuity");installInsulator(first.get(1),"contact");reload();}
            if(tick==45){all(120,0,360,"saved and reloaded contact insulator cuts only its arm");removeInsulators(first.get(1),"contact");}
            if(tick==55){all(120,240,360,"removing contact insulator restores supply");for(int i=0;i<3;i++)disconnect(sources[i],1,A,i);}
            if(tick==65){all(0,0,0,"disconnecting CEE feeds removes all PAW power");spool(sources[2],1,A,2,true);}
            if(tick==75){all(0,0,360,"third arm alone remains isolated after NBT reload");disconnect(sources[2],1,A,2);disconnect(C,0,loads[0],0);spool(sources[0],1,X,0,false);spool(Y,0,loads[0],0,true);}
            if(tick==85){voltage(0,120,"ordinary single-node PAW PowerWire supplies real CEE load");installInsulator(power,"main");}
            if(tick==95){voltage(0,0,"PowerWire main insulator cuts current");removeInsulators(power,"main");}
            if(tick==105){voltage(0,120,"removing PowerWire insulator restores current");finish(null);}
            if(tick>200)throw new AssertionError("test timeout");
        }catch(Throwable error){finish(error);}
    }
    void finish(Throwable error){done=true;if(error!=null){error.printStackTrace();evidence.append("FAIL ").append(error).append('\n');}else evidence.append("RESULT PASS\n");try{Files.writeString(Path.of("multiarm-check-result.txt"),evidence.toString());}catch(Exception e){throw new RuntimeException(e);}System.out.println("MULTIARM_RESULT "+(error==null?"PASS":"FAIL"));server.halt(false);}
}
