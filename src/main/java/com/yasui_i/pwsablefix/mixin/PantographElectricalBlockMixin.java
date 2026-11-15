package com.yasui_i.pwsablefix.mixin;

import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.content.connector.ConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import de.mrjulsen.paw.block.PantographBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Map;

/** Exposes a real CEE terminal so the CEE wire spool can connect the vehicle circuit. */
@Mixin(value=PantographBlock.class,remap=false)
public abstract class PantographElectricalBlockMixin extends Block implements ElectricalDeviceBlock<ConnectorDevice> {
    protected PantographElectricalBlockMixin(BlockBehaviour.Properties properties){super(properties);}
    @Override public SimulatedDeviceType<ConnectorDevice> getDevice(){return CEESimulatedDevices.CONNECTOR.get();}
    @Override public Map<Integer,Vec3> getNodePositions(Level level,BlockPos pos,BlockState state){return Map.of(0,new Vec3(.5,.375,.5));}
    @Override public Vec3 getNodePosition(Level level,BlockPos pos,BlockState state,int id){return id==0?new Vec3(.5,.375,.5):null;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,
                                                       Player player,InteractionHand hand,BlockHitResult hit){
        // PAW's useWithoutItem consumes right-click even while holding a spool.
        // Skip that fallback so Minecraft invokes the CEE item's own connection logic.
        var item=stack.getItem();
        if(item instanceof com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem
                ||item instanceof com.george_vi.electroenergetics.content.wire_spool.HangingWireSpoolItem
                ||item instanceof com.george_vi.electroenergetics.content.bundled_wire.BundledWireItem)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&level instanceof ServerLevel server)
            com.yasui_i.pwsablefix.compat.PWSableElectricalBridge.forget(server,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}
