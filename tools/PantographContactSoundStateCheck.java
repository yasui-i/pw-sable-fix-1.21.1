import com.yasui_i.pwsablefix.sound.PantographContactSoundState;
public final class PantographContactSoundStateCheck {
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args){
        var state=new PantographContactSoundState();
        check(PantographContactSoundState.RAISE_TICKS==80-40,"First-touch sound must lead animation completion by two seconds");
        check(!state.update(0,false,false,false),"Startup down must be silent");
        for(int i=1;i<=40;i++)check(!state.update(i,true,true,true),"First-touch animation gate");
        check(state.update(41,true,true,true),"Contact should arc two seconds before animation completion");
        check(!state.departure(),"Contact classified as departure");
        for(int i=42;i<110;i++)check(!state.update(i,true,true,true),"Animation bounce/continuous contact must not repeat");
        for(int i=110;i<113;i++)check(!state.update(i,true,false,false),"Short physical contact gap");
        check(!state.update(113,true,true,true),"Short gap should not retrigger");
        for(int i=114;i<117;i++)check(!state.update(i,true,false,false),"Departure debounce");
        check(state.update(117,true,false,false),"Leaving energized contact must arc");
        check(state.departure(),"Physical departure classified as contact");
        for(int i=118;i<145;i++)check(!state.update(i,true,false,false),"Continuous no-contact must not repeat");
        check(state.update(145,true,true,true),"Rejoining contact must arc");
        check(!state.departure(),"Rejoining kept departure cue");
        check(state.update(146,false,false,false),"Lowering away from contact must arc");
        check(state.departure(),"Lowering departure cue missing");
        check(!state.update(147,false,false,false),"Already lowered must be silent");
        for(int i=148;i<240;i++)check(!state.update(i,true,true,false),"Dead wire has no contact arc");
        check(state.update(240,true,true,true),"Energizing first contact must arc");
        check(!state.update(241,true,true,false),"Power loss while physically touching must not fake departure");
        check(!state.update(242,true,true,true),"Power restoration while touching must not repeat");
        var noWire=new PantographContactSoundState();
        for(int i=0;i<=100;i++)check(!noWire.update(i,true,false,false),"Early sound must still require a real energized wire");
        check(noWire.update(101,true,true,true),"Already raised collector should arc immediately on reaching energized wire");
        var interrupted=new PantographContactSoundState();
        check(!interrupted.update(0,true,true,true),"Raise command must not immediately arc");
        check(!interrupted.update(20,false,false,false),"Lowering before first touch must be silent");
        check(!interrupted.update(21,true,true,true),"Second raise starts its own timer");
        check(!interrupted.update(60,true,true,true),"Old raise must not shorten the new timer");
        check(interrupted.update(61,true,true,true),"Second raise reaches first-touch time");
        System.out.println("PASS first touch at40ticks (two seconds before animation completion), no replay during final bounce; contact/departure edges; no-wire/dead-wire silence; short-gap debounce; interrupted raise reset; voltage loss distinct from departure");
    }
}
