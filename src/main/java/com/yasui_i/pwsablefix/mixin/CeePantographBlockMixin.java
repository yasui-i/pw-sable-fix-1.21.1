package com.yasui_i.pwsablefix.mixin;

import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlock;
import com.yasui_i.pwsablefix.config.PantographConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PantographBlock.class,remap=false)
public abstract class CeePantographBlockMixin {
    @Inject(method="useItemOn",at=@At("HEAD"),cancellable=true,remap=false)
    private void pwsablefix$noManualLatch(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,
            InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<ItemInteractionResult> cir){
        if(stack.isEmpty()&&PantographConfig.ENABLE_REDSTONE_CONTROL.get())cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }
}
