import com.yasui_i.pwsablefix.sound.PantographContactSoundState;

public final class PantographContactSoundStateCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        animatedTouchAndLowering();
        wireGaps();
        voltageChanges();
        initializationAndInterruptedRaise();
        cooldownAndStaleFrames();
        System.out.println("PASS animation contact and separation, rebound, wire gaps, power changes, startup, cooldown and stale frames");
    }

    private static void animatedTouchAndLowering() {
        var state = new PantographContactSoundState();
        check(!state.update(0, false, true, false, false), "Lowered startup must be silent");
        check(!state.update(1, true, true, false, true), "Raise command alone must be silent");
        check(!state.update(6, true, true, false, true), "Head has not reached the wire");
        check(state.update(6.5, true, true, true, true) && !state.departure(),
                "First powered touch must play on the touching frame");
        check(!state.update(9, true, true, false, true), "An animation rebound is not a departure");
        check(!state.update(20, true, true, false, true), "Even a long rebound is not a wire gap");
        check(!state.update(22, true, true, true, true), "The second touch after rebound must be silent");
        check(!state.update(26, true, true, true, false), "A packet can lose voltage before the lower command arrives");
        check(!state.update(27, false, true, true, false), "Redstone loss alone is not a departure");
        check(!state.update(50, false, true, true, false), "Lowering but still touching must be silent");
        check(state.update(50.25, false, true, false, false) && state.departure(),
                "The first frame below the wire must play a separation");
        check(!state.update(51, false, true, false, false), "Lowered collector cannot replay departure");
    }

    private static void wireGaps() {
        var state = new PantographContactSoundState();
        check(!state.update(0, true, true, true, true), "Existing powered contact initializes silently");
        check(!state.update(10, true, false, false, false), "Wire seam starts a debounce window");
        check(!state.update(13.75, true, false, false, false), "A short wire seam must stay silent");
        check(!state.update(14, true, true, true, true), "Rejoining a wire seam must stay silent");
        check(!state.update(30, true, false, false, false), "A new gap needs its own debounce window");
        check(!state.update(33.75, true, false, false, false), "Loss is not confirmed before four ticks");
        check(state.update(34, true, false, false, false) && state.departure(),
                "Continuous wire loss for four ticks must play separation");
        check(!state.update(35, true, false, false, false), "Remaining away must stay silent");
        check(state.update(54, true, true, true, true) && !state.departure(),
                "A new powered wire must play contact after the cooldown");
        check(!state.update(60, false, false, false, false), "Unknown geometry during lowering still needs debounce");
        check(state.update(64, false, false, false, false) && state.departure(), "A sustained gap confirms separation");
    }

    private static void voltageChanges() {
        var state = new PantographContactSoundState();
        check(!state.update(0, true, true, false, false), "Head below a dead wire must be silent");
        check(!state.update(1, true, true, true, false), "Physical touch of a dead wire must be silent");
        check(!state.update(25, true, true, true, true), "Voltage restoration alone is not a new touch");
        check(!state.update(26, true, true, true, false), "Voltage drop alone is not a departure");
        check(!state.update(30, true, true, true, false), "Prolonged voltage loss is still not a departure");
        check(!state.update(31, false, true, false, false), "Leaving a dead wire must stay silent");
        check(!state.update(32, true, false, true, true), "Energized fact without a real wire is insufficient");
        check(state.update(41, true, true, true, true) && !state.departure(), "A later powered touch must play");
    }

    private static void initializationAndInterruptedRaise() {
        var state = new PantographContactSoundState();
        check(!state.update(0, true, true, true, true), "Loading an already raised collector must stay silent");
        check(!state.update(1, true, true, true, true), "Loading while touching cannot replay on the next frame");
        check(state.update(2, false, true, false, false) && state.departure(), "Existing powered contact can separate");
        check(!state.update(25, true, true, false, true), "A new rise below the wire must stay silent");
        check(!state.update(45, false, true, false, false), "Interrupting before contact must stay silent");
        check(!state.update(46, true, true, false, true), "A second rise still needs contact");
        check(state.update(90, true, true, true, true) && !state.departure(), "Touch time follows height, not a timer");
    }

    private static void cooldownAndStaleFrames() {
        var state = new PantographContactSoundState();
        check(!state.update(0, true, true, false, true), "Initial approach must stay silent");
        check(state.update(1, true, true, true, true), "A powered touch must play");
        check(!state.update(1, false, true, false, false), "A duplicate timestamp cannot separate");
        check(!state.update(0.5, false, true, false, false), "An older frame cannot separate");
        check(!state.update(Double.NaN, false, true, false, false), "An invalid timestamp cannot separate");
        check(state.update(2, false, true, false, false) && state.departure(), "A valid separation still plays");
        check(!state.update(21.9, true, true, true, true), "New contact is suppressed inside twenty ticks");
        check(!state.update(24, true, true, true, true), "A suppressed contact must not play after cooldown");
        check(state.update(25, false, true, false, false) && state.departure(), "The next real separation plays");
        check(state.update(45, true, true, true, true) && !state.departure(), "Contact can play after twenty ticks");
    }
}
