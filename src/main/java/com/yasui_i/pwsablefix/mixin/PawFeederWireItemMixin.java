package com.yasui_i.pwsablefix.mixin;

import de.mrjulsen.paw.data.WireHitResult;
import de.mrjulsen.paw.item.CatenaryWireType;
import de.mrjulsen.paw.item.FeederWireItem;
import de.mrjulsen.paw.item.PowerWireType;
import de.mrjulsen.wires.WiresApi;
import de.mrjulsen.wires.graph.data.node.CatenaryWireConnectorNodeData;
import de.mrjulsen.wires.graph.data.node.NodeData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let PAW's normal feeder placement attach to a real conductor, preserving the parent edge ID. */
@Mixin(value=FeederWireItem.class,remap=false)
public abstract class PawFeederWireItemMixin {
    @Inject(method="createNodeData",at=@At("HEAD"),cancellable=true,remap=false)
    private void pwsablefix$wireTap(Level level,Player player,InteractionHand hand,HitResult result,
                                    CallbackInfoReturnable<NodeData> cir) {
        if(!(result instanceof WireHitResult hit)||!hit.getGraphId().equals(WiresApi.PAW_CATENARY_WIRES))return;
        var wire=hit.getWireId();
        boolean conductor=wire.type() instanceof CatenaryWireType&&wire.name().equals("contact")
                ||wire.type() instanceof PowerWireType&&wire.name().equals("main");
        if(!conductor||hit.getCollision(level).filter(c->c.hasWire(wire.name())).isEmpty())return;
        double fraction=hit.getPosPercentage(level);
        if(Double.isFinite(fraction)&&fraction>=0&&fraction<=1)
            cir.setReturnValue(new CatenaryWireConnectorNodeData(wire,(float)fraction));
    }
}
