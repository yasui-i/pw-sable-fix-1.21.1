package pwsablemotortest;

import com.yasui_i.pwsablefix.motor.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.*;

/** Real client model, renderer and positional sound checks; no changes to player installations. */
@EventBusSubscriber(modid="pwsablemotortest",value=Dist.CLIENT)
public final class MotorClientCheck {
    static int ticks,exitTicks;static boolean connected,done,ready,staticChecked,movingChecked;static String screenshot;
    static final StringBuilder evidence=new StringBuilder();
    static Object field(Object object,String name)throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static void check(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    static Map<net.minecraft.client.resources.sounds.SoundInstance,?> channels()throws Exception {
        return (Map<net.minecraft.client.resources.sounds.SoundInstance,?>)field(field(Minecraft.getInstance().getSoundManager(),"soundEngine"),"instanceToChannel");
    }
    static void validate(VvvfMotorBlockEntity motor,boolean moving)throws Exception {
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(motor.getBlockState());
        check(model!=mc.getModelManager().getMissingModel(),"Missing motor model");
        check(model.getParticleIcon().contents().name().getNamespace().equals("electroenergetics"),"Motor texture does not reference CEE");
        var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(motor);
        check(renderer!=null&&renderer.getClass().getName().contains("ElectricMotorRenderer"),"CEE shaft renderer missing");
        var camera=mc.gameRenderer.getMainCamera();var old=camera.getPosition();
        var set=net.minecraft.client.Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);set.setAccessible(true);
        var local=motor.getBlockPos().getCenter();
        var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(mc.level,motor.getBlockPos());
        var world=sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client?client.renderPose().transformPosition(local):local;
        var ctor=Class.forName("com.yasui_i.pwsablefix.motor.VvvfMotorClient$MovingMotorSound").getDeclaredConstructors()[0];ctor.setAccessible(true);
        var sound=(com.george_vi.electroenergetics.content.ElectricHumSoundInstance)ctor.newInstance(com.george_vi.electroenergetics.CEESoundEvents.DC_TRAIN.get(),motor);
        sound.resolve(mc.getSoundManager());
        var volume=sound.getClass().getDeclaredMethod("setMotorVolume",float.class);volume.setAccessible(true);volume.invoke(sound,1f);
        try {
            for(double d:new double[]{1,16,32,47,49,1}) {
                set.invoke(camera,world.x+d,world.y,world.z);
                for(int i=0;i<12;i++){sound.keepAlive();sound.tick();}
                if(d==32)check(sound.getVolume()>.2,"Motor inaudible beyond old range");
                if(d==49)check(sound.getVolume()<.02,"Motor not muted beyond48 blocks");
                if(d==1)check(sound.getVolume()>.8,"Motor did not recover on returning in range");
                check(!sound.isStopped(),"Transient radius crossing stopped motor permanently");
            }
            if(moving) {
                check(sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess,"Moving motor has no Sable client body");
                set.invoke(camera,world.x+1,world.y,world.z);
                mc.getSoundManager().play(sound);
                var delegate=channels().keySet().stream().filter(s->s instanceof dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate d&&d.instance==sound).findFirst().orElseThrow(()->new AssertionError("No Sable sound delegate"));
                var render=(dev.ryanhcode.sable.companion.math.Pose3d)((dev.ryanhcode.sable.companion.ClientSubLevelAccess)sub).renderPose();
                var logical=(dev.ryanhcode.sable.companion.math.Pose3d)sub.logicalPose();
                var rp=new org.joml.Vector3d(render.position());var lp=new org.joml.Vector3d(logical.position());
                try {
                    for(int i=0;i<10;i++) {
                        render.position().set(rp).add(350/3.6/20*i,0,0);logical.position().set(lp).add(350/3.6/20*i,0,0);
                        var w=render.transformPosition(local);set.invoke(camera,w.x+1,w.y,w.z);
                        sound.keepAlive();((TickableSoundInstance)delegate).tick();
                        check(delegate.getVolume()>.8,"Motor lost volume during350 km/h pose update");
                        check(Math.abs(delegate.getX()-logical.transformPosition(local).x)<1e-5,"Motor sound transformed twice");
                    }
                }finally{render.position().set(rp);logical.position().set(lp);mc.getSoundManager().stop(delegate);}
            }
            evidence.append(moving?"moving CEE model/shaft renderer, real Sable sound delegate once, ten synthetic350km/h pose steps; ":"static CEE model/shaft renderer, sound audible32 blocks, mute49 and recovery; ");
        }finally {set.invoke(camera,old.x,old.y,old.z);mc.getSoundManager().stop(sound);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();if(done){if(mc.level==null||++exitTicks>100)mc.stop();return;}
        if(mc.player==null||mc.level==null) {
            if(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            if(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&!connected) {
                connected=true;net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25582"),new net.minecraft.client.multiplayer.ServerData("Motor test","127.0.0.1:25582",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
            }
            return;
        }
        try {
            if(!ready){ready=true;mc.player.connection.sendChat("MOTOR_CLIENT_READY");}
            ticks++;
            var positions=new ArrayList<BlockPos>();
            var p=mc.player.blockPosition();for(int x=-12;x<=12;x++)for(int y=-5;y<=5;y++)for(int z=-12;z<=12;z++)positions.add(p.offset(x,y,z));
            for(var sub:dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getAllSubLevels())positions.add(sub.getPlot().getCenterBlock());
            for(var pos:positions)if(mc.level.getBlockEntity(pos) instanceof VvvfMotorBlockEntity motor) {
                boolean moving=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(mc.level,pos)!=null;
                if(moving&&!movingChecked){validate(motor,true);movingChecked=true;screenshot="motor-moving.png";}
                else if(!moving&&!staticChecked){validate(motor,false);staticChecked=true;screenshot="motor-static.png";}
            }
            if(staticChecked&&movingChecked){Files.writeString(mc.gameDirectory.toPath().resolve("motor-client-check.txt"),"PASS "+evidence);mc.player.connection.sendChat("MOTOR_CLIENT_DONE");done=true;}
            if(ticks>1400)throw new AssertionError("Timed out static="+staticChecked+" moving="+movingChecked);
        }catch(Throwable error){error.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("motor-client-check.txt"),"FAIL "+error);}catch(Exception ignored){}done=true;mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(screenshot==null)return;var mc=Minecraft.getInstance();var name=screenshot;screenshot=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
    private MotorClientCheck(){}
}
