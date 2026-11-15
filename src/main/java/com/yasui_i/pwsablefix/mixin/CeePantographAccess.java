package com.yasui_i.pwsablefix.mixin;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Uses CEE's own contact solver and owns the same lifecycle as a real pantograph. */
@Mixin(value=PantographBlockEntity.class,remap=false)
public interface CeePantographAccess {
    @Accessor("attachedNode") AttachedNode pwsablefix$attachedNode();
    @Invoker("disconnect") void pwsablefix$disconnect();
}
