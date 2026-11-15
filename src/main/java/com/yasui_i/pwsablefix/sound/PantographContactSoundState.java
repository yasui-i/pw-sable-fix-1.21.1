package com.yasui_i.pwsablefix.sound;

/** Debounces the physical, energized contact instead of treating every wire segment as a new contact. */
public final class PantographContactSoundState {
    // PAW's expand animation lasts four seconds at 0.25 speed. Cue the
    // initial touch during the rise, about two seconds before final settling.
    public static final int RAISE_TICKS = 40;
    private static final int DISCONNECT_TICKS = 4;
    private static final int RETRIGGER_TICKS = 20;
    private long raisedAt = Long.MIN_VALUE;
    private long disconnectedAt = Long.MIN_VALUE;
    private long lastSoundAt = Long.MIN_VALUE;
    private long lastTick = Long.MIN_VALUE;
    private boolean raised;
    private boolean contactLatched;
    private long contactEvents, disconnectEvents;
    private boolean departure;
    public boolean departure(){return departure;}

    public boolean update(long tick, boolean active, boolean energizedContact) {
        return update(tick, active, energizedContact, energizedContact);
    }

    public boolean update(long tick, boolean active, boolean physicalContact, boolean energizedContact) {
        if (tick <= lastTick) return false;
        lastTick = tick;
        if (!active) {
            boolean departing = contactLatched;
            raised = false;
            contactLatched = false;
            disconnectedAt = Long.MIN_VALUE;
            if (departing) { disconnectEvents++; lastSoundAt = tick; departure=true; }
            return departing;
        }
        if (!raised) {
            raised = true;
            raisedAt = tick;
        }
        if (!physicalContact) {
            if (disconnectedAt == Long.MIN_VALUE) disconnectedAt = tick;
            if (tick - disconnectedAt + 1 >= DISCONNECT_TICKS && contactLatched) {
                contactLatched = false;
                disconnectEvents++;
                departure=true;
                lastSoundAt = tick;
                return true;
            }
            return false;
        }
        disconnectedAt = Long.MIN_VALUE;
        if (contactLatched || !energizedContact || tick - raisedAt < RAISE_TICKS) return false;
        contactLatched = true;
        if (lastSoundAt != Long.MIN_VALUE && tick - lastSoundAt < RETRIGGER_TICKS) return false;
        lastSoundAt = tick;
        contactEvents++;
        departure=false;
        return true;
    }
}
