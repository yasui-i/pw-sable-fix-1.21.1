package com.yasui_i.pwsablefix.mixin;
import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.content.connector.ConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.yasui_i.pwsablefix.compat.PawArmTerminals;
import de.mrjulsen.paw.block.abstractions.AbstractCantileverBlock;
import de.mrjulsen.paw.block.abstractions.AbstractRotatableWireConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Map;

/** CEE wire spools can feed the actual PAW connector, without a second hidden wire network. */
@Mixin(value=AbstractRotatableWireConnectorBlock.class,remap=false)
public abstract class PawConnectorElectricalMixin implements ElectricalDeviceBlock<ConnectorDevice> {
    @Override public SimulatedDeviceType<ConnectorDevice> getDevice(){return CEESimulatedDevices.CONNECTOR.get();}
    @Override public Map<Integer,Vec3> getNodePositions(Level level,BlockPos pos,BlockState state){return PawArmTerminals.ceePositions(level,pos,state);}
    @Override public Vec3 getNodePosition(Level level,BlockPos pos,BlockState state,int id){return getNodePositions(level,pos,state).get(id);}
    @Override public float getNodeSize(Level level,BlockPos pos,BlockState state,int id){return state.getBlock() instanceof AbstractCantileverBlock?.07f:.25f;}
    @Override public MutableComponent getNodeLabel(Level level,BlockPos pos,BlockState state,int id){
        return state.getBlock() instanceof AbstractCantileverBlock
                ?Component.translatable("pwsablefix.nodes.arm",id+1)
                :Component.translatable("electroenergetics.nodes.node");
    }
}
