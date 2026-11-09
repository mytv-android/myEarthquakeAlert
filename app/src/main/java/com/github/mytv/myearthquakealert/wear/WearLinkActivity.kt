package com.github.mytv.myearthquakealert.wear

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.github.mytv.myearthquakealert.MainActivity
import com.github.mytv.myearthquakealert.ui.navigation.Destination

/**
 * Deep links opened on the phone from the watch (手表 › 设置 › 在手机上查看更新).
 *
 * Wear's RemoteActivityHelper only delivers an ACTION_VIEW + CATEGORY_BROWSABLE intent with
 * a data URI and strips every extra, so the watch opens [uriFor] and this invisible
 * trampoline turns it into MainActivity's [Destination.EXTRA]. Only the four destinations
 * are accepted; anything else just opens the app.
 */
class WearLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        destinationOf(intent?.data)?.let { open.putExtra(Destination.EXTRA, it.route) }
        startActivity(open)
        finish()
    }

    companion object {
        const val SCHEME = "myearthquakealert"
        const val HOST = "open"

        /** `myearthquakealert://open/settings` → [Destination.SETTINGS]. */
        fun uriFor(destination: Destination): Uri =
            Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(destination.route).build()

        fun destinationOf(uri: Uri?): Destination? {
            if (uri == null || uri.scheme != SCHEME || uri.host != HOST) return null
            return Destination.fromRoute(routeOf(uri.path))
        }

        /** The first path segment of a link path (`/settings` → `settings`). */
        internal fun routeOf(path: String?): String? =
            path?.trim('/')?.substringBefore('/')?.takeIf { it.isNotEmpty() }
    }
}
