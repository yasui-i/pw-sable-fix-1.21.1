package com.yasui_i.pwsablefix.mixin;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import de.mrjulsen.paw.blockentity.CantileverBlockEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refresh only cantilevers already wired into CEE when PAW changes/loads their settings. */
@Mixin(value = CantileverBlockEntity.class, remap = false)
public abstract class CantileverElectricalUpdateMixin {
    @Inject(method = {"update", "dragonlib$onBlockEntityLoad"}, at = @At("RETURN"), remap = false)
    private void pwsablefix$updateRegisteredTerminals(CallbackInfo ci) {
        var entity = (CantileverBlockEntity) (Object) this;
        if (!(entity.getLevel() instanceof ServerLevel level)) return;
        var pos = entity.getBlockPos();
        if (InfrastructureSavedData.load(level).getNodesAt(pos).isEmpty()) return;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof ElectricalDeviceBlock<?> device)
            device.ensureNodesExist(level, pos, state);
    }
}
