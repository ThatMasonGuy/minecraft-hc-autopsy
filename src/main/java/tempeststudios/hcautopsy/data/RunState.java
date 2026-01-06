package tempeststudios.hcautopsy.data;

/**
 * Represents the lifecycle state of a hardcore run.
 */
public enum RunState {
    /**
     * Run is actively tracking player statistics.
     * No deaths have occurred yet.
     */
    ACTIVE,

    /**
     * Run has ended due to a player death.
     * The run is locked and no further deaths will be recorded.
     * Stats have been snapshotted and preserved.
     */
    WIPED,

    /**
     * A previously wiped run that was continued via admin command.
     * The death was struck from the record and tracking resumed.
     * This state transitions back to ACTIVE but preserves audit trail.
     */
    CONTINUED;

    /**
     * Returns true if the run is currently tracking statistics.
     */
    public boolean isTracking() {
        return this == ACTIVE;
    }

    /**
     * Returns true if the run has been terminated by death.
     */
    public boolean isTerminated() {
        return this == WIPED;
    }
}
