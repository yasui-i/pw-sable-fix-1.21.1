package com.yasui_i.pwsablefix.mixin;

import com.yasui_i.pwsablefix.config.PantographConfig;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native CEE pantograph uses the same level-triggered redstone command. */
@Mixin(value=PantographBlockEntity.class,remap=false)
public abstract class CeePantographRedstoneMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void pwsablefix$redstone(CallbackInfo ci){
        var self=(PantographBlockEntity)(Object)this;
        if(self.getLevel() instanceof ServerLevel level && level.getBlockEntity(self.getBlockPos())==self
                && PantographConfig.ENABLE_REDSTONE_CONTROL.get()){
            boolean raise=level.hasNeighborSignal(self.getBlockPos());
            if(self.extended!=raise){self.extended=raise;self.targetExtensionState=raise?1:0;self.notifyUpdate();}
        }
    }
}
