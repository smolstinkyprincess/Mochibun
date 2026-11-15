package one.crinklecod.mochibun.pet

/**
 * Turns power events into "a charger was really plugged in just now".
 *
 * - The first reading (the sticky ACTION_BATTERY_CHANGED or the initial query) only records a
 *   baseline: the phone may already have been charging when the launcher started.
 * - After that, only ACTION_POWER_CONNECTED following an unplugged state counts. Battery status
 *   updates never grant, so CHARGING / FULL / NOT_CHARGING flaps while plugged can't farm treats.
 *
 * Pure Kotlin (no Android imports) so the rule is JVM-testable. Not thread-safe: call it from the
 * receiver's (main) thread only.
 */
class ChargerPlugTracker {
    /** null until the first reading. */
    var isPlugged: Boolean? = null
        private set

    /** Records the plug state from a battery reading if there is no baseline yet. Never grants. */
    fun onBaseline(plugged: Boolean) {
        if (isPlugged == null) isPlugged = plugged
    }

    /** ACTION_POWER_CONNECTED. Returns true when this is a real unplugged -> plugged change. */
    fun onPowerConnected(): Boolean {
        val wasUnplugged = isPlugged == false
        isPlugged = true
        return wasUnplugged
    }

    /** ACTION_POWER_DISCONNECTED. */
    fun onPowerDisconnected() {
        isPlugged = false
    }
}
