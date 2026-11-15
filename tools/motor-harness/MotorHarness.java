package pwsablemotortest;

import com.yasui_i.pwsablefix.motor.*;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

@Mod("pwsablemotortest")
public final class MotorHarness {
    static final BlockPos M=new BlockPos(0,90,0),B=M.west(4),S=M.north(),R=M.east();
    ServerLevel level;net.minecraft.server.MinecraftServer server;VvvfMotorBlockEntity motor;InfrastructureSavedData circuit;
    BlockPos motorPos=M,shaftPos=S,redstonePos=R;int tick;boolean done,ready,clientDone;float full,previous;
    final StringBuilder evidence=new StringBuilder();
    public MotorHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.ServerChatEvent e)->{if(e.getRawText().equals("MOTOR_CLIENT_READY")){ready=true;e.setCanceled(true);}if(e.getRawText().equals("MOTOR_CLIENT_DONE")){clientDone=true;e.setCanceled(true);}});NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)p.teleportTo(level,4,91,5,140,20);});}
    void check(boolean condition,String text){if(!condition)throw new AssertionError(text);evidence.append("PASS ").append(text).append('\n');System.out.println("MOTOR_CHECK "+text);}
    void started(ServerStartedEvent event){
        server=event.getServer();level=server.overworld();
        try {
            for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)level.setChunkForced(x,z,true);
            for(int x=-6;x<=6;x++)for(int z=-3;z<=4;z++)level.setBlockAndUpdate(new BlockPos(x,89,z),Blocks.BEDROCK.defaultBlockState());
            level.setBlockAndUpdate(M,VvvfMotors.BLOCK.get().defaultBlockState());motor=(VvvfMotorBlockEntity)level.getBlockEntity(M);
            level.setBlockAndUpdate(S,com.simibubi.create.AllBlocks.SHAFT.getDefaultState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS,Direction.Axis.Z));
            level.setBlockAndUpdate(B,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            for(var p:List.of(M,B))((ElectricalDeviceBlock<?>)level.getBlockState(p).getBlock()).ensureNodesExist(level,p,level.getBlockState(p));
            circuit=InfrastructureSavedData.load(level);
            circuit.connect(new InWorldNode(1,B),new InWorldNode(0,M),CEEWireTypes.CREATIVE.get());
            circuit.connect(new InWorldNode(1,M),new InWorldNode(0,B),CEEWireTypes.CREATIVE.get());
            check(motor!=null,"Independent CEE motor block entity exists");
        }catch(Throwable error){finish(error);}
    }
    double voltage(){return Math.abs(circuit.ticker.lastResults.getVoltageAt(motorPos,0,1));}
    double current(){return Math.abs(circuit.ticker.lastResults.getCurrentThrough(motorPos,0,1));}
    float shaft(){return ((com.simibubi.create.content.kinetics.base.KineticBlockEntity)level.getBlockEntity(shaftPos)).getSpeed();}
    void tick(ServerTickEvent.Post event){
        if(done||server==null)return;if(Boolean.getBoolean("pwsable.test.motorClient")&&!ready)return;
        try {
            tick++;
            if(tick==5){var source=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level).getDevice(B,com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class);source.voltage=120;source.acFrequency=0;}
            if(tick==20){check(motor.getGeneratedSpeed()==0&&shaft()==0,"Powered motor without redstone remains stopped");check(current()<.001,"Zero command draws no load current "+current());level.setBlockAndUpdate(redstonePos,Blocks.REDSTONE_BLOCK.defaultBlockState());}
            if(tick==60){check(motor.getDriveFraction()>.1&&motor.getDriveFraction()<.3,"Redstone ramps gradually "+motor.getDriveFraction());check(voltage()>110&&current()>0,"Real CEE voltage/current "+voltage()+" V / "+current()+" A");check(Math.abs(shaft())>0&&Math.abs(shaft())<256,"Real connected shaft ramps "+shaft());previous=motor.getDriveFraction();}
            if(tick==235){full=motor.getDriveFraction();check(full>.99,"Full command reaches100%");check(Math.abs(shaft())==Math.abs(motor.getGeneratedSpeed()),"Shaft equals actual motor source speed "+shaft());check(motor.calculateAddedStressCapacity()>0,"Motor provides real kinetic capacity");level.removeBlock(redstonePos,false);level.setBlockAndUpdate(redstonePos,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(net.minecraft.world.level.block.WeightedPressurePlateBlock.POWER,7));}
            if(tick==240)check(motor.getDriveFraction()<full&&motor.getDriveFraction()>.8,"Nonzero lower command ramps down gradually "+motor.getDriveFraction());
            if(tick==245){level.removeBlock(redstonePos,false);}
            if(tick==248){check(motor.getDriveFraction()==0&&motor.getGeneratedSpeed()==0&&shaft()==0,"Zero redstone immediately clears drive and shaft");check(current()<.001,"Zero redstone opens CEE load circuit");level.setBlockAndUpdate(redstonePos,Blocks.REDSTONE_BLOCK.defaultBlockState());}
            if(tick==280){var data=motor.saveWithFullMetadata(level.registryAccess());var copy=new VvvfMotorBlockEntity(VvvfMotors.BLOCK_ENTITY.get(),motorPos,motor.getBlockState());copy.loadWithComponents(data,level.registryAccess());check(Math.abs(copy.getDriveFraction()-motor.getDriveFraction())<1e-6,"NBT preserves ramp command");
                circuit.removeConnectionNoDrops(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(new InWorldNode(1,motorPos),new InWorldNode(0,B)));}
            if(tick==300){check(Math.abs(motor.getGeneratedSpeed())<1&&shaft()==0,"Loss of real voltage stops motor and shaft");circuit.connect(new InWorldNode(1,motorPos),new InWorldNode(0,B),CEEWireTypes.CREATIVE.get());}
            if(tick==325){check(Math.abs(shaft())>0,"Reconnecting CEE supply restores output: volts="+voltage()+" source="+motor.getGeneratedSpeed()+" shaft="+shaft()+" drive="+motor.getDriveFraction()+" nodes="+circuit.getNodesAt(motorPos));
                var support=M.below();level.setBlockAndUpdate(support,Blocks.IRON_BLOCK.defaultBlockState());
                var positions=List.of(M,S,R,support);var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,M,positions,dev.ryanhcode.sable.companion.math.BoundingBox3i.from(positions));
                motorPos=body.getPlot().getCenterBlock();shaftPos=motorPos.north();redstonePos=motorPos.east();motor=(VvvfMotorBlockEntity)level.getBlockEntity(motorPos);
                check(motor!=null,"New motor assembles on real Sable plot");}
            if(tick==360){check(motor.getDriveFraction()>0&&voltage()>110&&Math.abs(shaft())>0,"Sable plot keeps local redstone, real CEE supply and shaft output");level.removeBlock(redstonePos,false);}
            if(tick==365){check(motor.getGeneratedSpeed()==0&&shaft()==0,"Sable redstone removal cuts output");level.setBlockAndUpdate(redstonePos,Blocks.REDSTONE_BLOCK.defaultBlockState());}
            if(tick>=430&&(!Boolean.getBoolean("pwsable.test.motorClient")||clientDone))finish(null);
            if(tick>1600)throw new AssertionError("Client did not complete");
        }catch(Throwable error){finish(error);}
    }
    void finish(Throwable error){done=true;if(error!=null){error.printStackTrace();evidence.append("FAIL ").append(error).append('\n');}try{Files.writeString(Path.of("motor-server-check.txt"),evidence);}catch(Exception ex){throw new RuntimeException(ex);}server.halt(false);}
}
