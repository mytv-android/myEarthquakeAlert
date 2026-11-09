package com.github.mytv.myearthquakealert.ui.navigation

/**
 * The four top-level destinations, identical on every device (design §2). Only the
 * navigation component that hosts them changes.
 */
enum class Destination(val route: String) {
    OVERVIEW("overview"),
    QUAKES("quakes"),
    DIAGNOSTICS("diagnostics"),
    SETTINGS("settings");

    companion object {
        /** Intent extra on MainActivity selecting the start destination (e.g. from the 诊断 notification action). */
        const val EXTRA = "com.github.mytv.myearthquakealert.DESTINATION"

        /** Optional settings sub-page with [EXTRA] = settings, e.g. "update" from 发现新版本. */
        const val EXTRA_PAGE = "com.github.mytv.myearthquakealert.SETTINGS_PAGE"

        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
