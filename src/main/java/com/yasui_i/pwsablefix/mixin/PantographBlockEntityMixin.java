package com.yasui_i.pwsablefix.mixin;

import com.yasui_i.pwsablefix.PWSableFix;
import com.yasui_i.pwsablefix.config.PantographConfig;
import de.mrjulsen.paw.block.PantographBlock;
import de.mrjulsen.paw.blockentity.PantographBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes PAW pantographs work on Sable physics trains.
 *
 * <p>PAW only resolves catenary contact for pantographs that are part of a Create contraption
 * ({@code PantographMovementBehaviour}). A block placed on a Sable physics sub-level never goes through
 * that path and its block position lives in the sub-level plot coordinates, so it can never find the
 * catenary of the real world.</p>
 *
 * <p>CEE solves this by converting the pantograph position with the sub-level's logical pose before
 * querying the wire network. This mixin does the same for PAW: on the client it feeds
 * {@link PantographBlockEntity#updateContraptionValues} a world-space head position and a world-space
 * rotation, which makes PAW's own wire collision query run in world coordinates and therefore find the
 * catenary above the train.</p>
 *
 * <p>The vanilla {@code tick()} is <b>replaced</b> for sub-level pantographs instead of being extended,
 * because it starts with {@code setExpanded(isExpandable())}. Calling
 * {@code updateContraptionValues()} after that would call {@code setExpanded()} a second time in the
 * same tick, which clears PAW's {@code stateChanged} flag before GeckoLib reads it - the expand/collapse
 * animations then never play and the arm stays stuck at its last height. A contraption pantograph hits
 * exactly the same state machine through {@code contraptionTick()} (which only ticks the animation
 * chaser), so this mixin mirrors that path and adds the chaser tick itself.</p>
 */
@Mixin(value = PantographBlockEntity.class, remap = false)
public abstract class PantographBlockEntityMixin implements com.yasui_i.pwsablefix.compat.RegisteredPantograph,
        com.yasui_i.pwsablefix.compat.PantographWireContact {

    @Unique private double pwsablefix$wireHeight = -1;
    @Override public double pwsablefix$wireHeight(){return pwsablefix$wireHeight;}

    @Unique private String pwsablefix$consist="";
    @Unique private int pwsablefix$car;
    @Override public String pwsablefix$consist(){return pwsablefix$consist;}
    @Override public int pwsablefix$car(){return pwsablefix$car;}
    @Override public void pwsablefix$configure(String consist,int car){
        var self=(PantographBlockEntity)(Object)this;
        if(!(self.getLevel() instanceof net.minecraft.server.level.ServerLevel level)||(car!=0&&car!=3&&car!=6))return;
        String clean=consist.replaceAll("[\\p{Cntrl}§]","").strip();
        pwsablefix$consist=clean.substring(0,Math.min(24,clean.length()));pwsablefix$car=car;
        com.yasui_i.pwsablefix.compat.PWSableElectricalBridge.forget(level,self.getBlockPos());
        self.notifyUpdate();
    }
    @Inject(method="write",at=@At("TAIL"),remap=false)
    private void pwsablefix$save(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider provider,boolean packet,CallbackInfo ci){
        tag.putString("PWSableConsist",pwsablefix$consist);tag.putInt("PWSableCar",pwsablefix$car);
    }
    @Inject(method="read",at=@At("TAIL"),remap=false)
    private void pwsablefix$load(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider provider,boolean packet,CallbackInfo ci){
        pwsablefix$consist=tag.getString("PWSableConsist");pwsablefix$car=tag.getInt("PWSableCar");
        if(pwsablefix$car!=3&&pwsablefix$car!=6)pwsablefix$car=0;
    }

    @Unique
    private boolean csima$loggedSubLevelContact;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void csima$tickOnSubLevel(CallbackInfo ci) {
        PantographBlockEntity self = (PantographBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            boolean active = PantographConfig.ENABLE_REDSTONE_CONTROL.get()
                    ? server.hasNeighborSignal(self.getBlockPos()) : self.isExpandable();
            if (self.isExpandable() != active) {
                self.setExpandable(active);
                // setExpandable only changes a field; it does not save or send the value.
                // PAW's client owns the GeckoLib state machine, so every transition needs this packet.
                self.notifyUpdate();
            }
            com.yasui_i.pwsablefix.compat.PWSableElectricalBridge.observe(server,self.getBlockPos(),active);
            return;
        }
        if (level == null || !level.isClientSide) {
            // No world is available during construction.
            return;
        }
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, self.getBlockPos());
        if(subLevel!=null&&!PantographConfig.ENABLE_SUBLEVEL_CONTACT.get())return;
        Pose3d pose = subLevel==null?null:new Pose3d(subLevel.logicalPose());
        BlockPos pos = self.getBlockPos();
        // Same anchor PAW uses for contraption pantographs: block centre raised to the model's minimum height.
        Vector3d localAnchor=new Vector3d(pos.getX() + 0.5, pos.getY() + PantographBlockEntity.MIN_HEIGHT, pos.getZ() + 0.5);
        Vector3d worldAnchor=pose==null?localAnchor:pose.transformPosition(localAnchor,new Vector3d());

        BlockState state = self.getBlockState();
        Direction facing = state.getValue(PantographBlock.FACING);
        if (facing.getAxis() == Direction.Axis.X) {
            facing = facing.getOpposite();
        }
        double yRot = facing.toYRot();

        // Hand PAW a world-space head position and a world-space rotation, mirroring what its
        // contraption behaviour does for normal Create trains. This also drives setExpanded() and the
        // arm height chaser, exactly like a contraption pantograph.
        self.updateContraptionValues(worldAnchor, local -> {
            Vec3 rotated = VecHelper.rotate(new Vec3(local.x(), local.y(), local.z()), yRot, Direction.Axis.Y);
            Vector3d vector=new Vector3d(rotated.x,rotated.y,rotated.z);
            return pose==null?vector:pose.transformNormal(vector, new Vector3d());
        });
        // PAW's own public entry point for a pantograph that is not ticked as a normal block entity.
        // It runs commonTick() (super.tick() + the arm height chaser) without resetting the expanded state.
        self.contraptionTick();

        if (subLevel!=null&&!this.csima$loggedSubLevelContact) {
            this.csima$loggedSubLevelContact = true;
            PWSableFix.LOGGER.info(
                    "Pantograph at {} is on sub-level {} - resolving catenary in world space at {}",
                    pos, subLevel.getUniqueId(), worldAnchor);
        }

        ci.cancel();
    }

    @Inject(method = "toggleExpandable", at = @At("HEAD"), cancellable = true, remap = false)
    private void pwsablefix$ignoreManualLatchInRedstoneMode(CallbackInfo ci) {
        if (PantographConfig.ENABLE_REDSTONE_CONTROL.get()) ci.cancel();
    }

    @Inject(method="calculateWireContact",at=@At("RETURN"),cancellable=true,remap=false)
    private void pwsablefix$contactCeeWires(Vector3d origin,Vector3d up,Vector3d right,CallbackInfoReturnable<Double> cir){
        var self=(PantographBlockEntity)(Object)this;
        if(self.getLevel()==null||!self.getLevel().isClientSide)return;
        if(PantographConfig.ENABLE_CEE_BRIDGE.get()){
            double cee=com.yasui_i.pwsablefix.compat.CeeWireContact.height(self.getLevel(),origin,up,right);
            if(cee>=0&&(cir.getReturnValue()<0||cee<cir.getReturnValue()))cir.setReturnValue(cee);
        }
        pwsablefix$wireHeight=cir.getReturnValue();
        // Redstone commands raise/lower the mechanism, independently of a wire
        // being overhead. Electrical contact still requires an actual PAW/CEE conductor.
        if(cir.getReturnValue()<0&&self.isExpandable())cir.setReturnValue(PantographBlockEntity.DELTA_HEIGHT);
    }
}
