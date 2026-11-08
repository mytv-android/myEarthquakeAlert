package com.github.mytv.myearthquakealert.diagnostics

/**
 * Tags of [LogEntry]. The settings page lists recent service starts by filtering
 * [START] entries, whose message begins with the [com.github.mytv.myearthquakealert.service.StartOrigin] name.
 */
object LogTags {
    const val START = "START"
    const val STOP = "STOP"
    const val PAUSE = "PAUSE"
    const val RESUME = "RESUME"
    const val BOOT = "BOOT"
    const val WATCHDOG = "WATCHDOG"
    const val CONNECTED = "CONNECTED"
    const val DISCONNECTED = "DISCONNECT"
    const val RECONNECT = "RECONNECT"
    const val FALLBACK = "FALLBACK"
    const val REPORT = "REPORT"
    const val ALERT = "ALERT"
    const val CLOCK = "CLOCK"
    const val SELF_TEST = "SELFTEST"
    const val ERROR = "ERROR"
    /** Default network lost / changed / back. */
    const val NETWORK = "NETWORK"
    /** Location source or position changed. */
    const val LOCATION = "LOCATION"
    /** A drill started. */
    const val DRILL = "DRILL"
    /** Configuration changes that affect monitoring (e.g. subscribed sources). */
    const val SYSTEM = "SYSTEM"
}
