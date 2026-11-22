package com.yasui_i.pwsablefix.sound;

/** Tracks one collector's contact strip across animation frames and wire seams. */
public final class PantographContactSoundState {
    private static final double UNSET = Double.NEGATIVE_INFINITY;
    private static final int LOSS_DEBOUNCE_TICKS = 4;
    private static final int RETRIGGER_TICKS = 20;

    private double lastTick = UNSET;
    private double wireMissingSince = UNSET;
    private double unpoweredSince = UNSET;
    private double lastSoundAt = UNSET;
    private boolean contactLatched;
    private boolean poweredContact;
    private boolean departure;

    /** Whether the last event returned by {@link #update} was a separation. */
    public boolean departure() {
        return departure;
    }

    public boolean update(double tick, boolean active, boolean wirePresent, boolean headTouching,
                          boolean energizedContact) {
        if (!Double.isFinite(tick) || tick <= lastTick) return false;
        boolean firstSample = lastTick == UNSET;
        lastTick = tick;

        if (!contactLatched) {
            if (!wirePresent || !headTouching) return false;
            // Latch the physical touch even on a dead wire. A later voltage change alone is not a touch.
            contactLatched = true;
            poweredContact = energizedContact;
            return !firstSample && active && energizedContact && tick - lastSoundAt >= RETRIGGER_TICKS
                    && play(tick, false);
        }

        if (!wirePresent) {
            if (wireMissingSince == UNSET) wireMissingSince = tick;
            return tick - wireMissingSince >= LOSS_DEBOUNCE_TICKS && separate(tick);
        }
        wireMissingSince = UNSET;

        // While raised, the stock animation bounces off the wire once. During lowering, a
        // measured gap below the wire is a real separation and needs no additional delay.
        if (!active && !headTouching) return separate(tick);

        if (energizedContact) {
            poweredContact = true;
            unpoweredSince = UNSET;
        } else if (active && poweredContact) {
            // A transient telemetry gap (including the first packet for a redstone change)
            // should not clear the cue. Once lowering begins the circuit is disconnected,
            // but the head can still be on the wire until the animation pulls it away.
            if (unpoweredSince == UNSET) unpoweredSince = tick;
            if (tick - unpoweredSince >= LOSS_DEBOUNCE_TICKS) poweredContact = false;
        }
        return false;
    }

    private boolean separate(double tick) {
        boolean wasPowered = poweredContact;
        contactLatched = false;
        poweredContact = false;
        wireMissingSince = UNSET;
        unpoweredSince = UNSET;
        return wasPowered && play(tick, true);
    }

    private boolean play(double tick, boolean departing) {
        lastSoundAt = tick;
        departure = departing;
        return true;
    }
}
