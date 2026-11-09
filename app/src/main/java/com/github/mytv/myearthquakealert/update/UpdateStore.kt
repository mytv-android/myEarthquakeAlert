package com.github.mytv.myearthquakealert.update

import android.content.Context
import androidx.core.content.edit
import com.github.mytv.myearthquakealert.BuildConfig
import com.github.mytv.myearthquakealert.data.model.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A downloaded package that passed every check and waits for 立即安装. */
@Serializable
data class ReadyUpdate(val info: UpdateInfo, val path: String, val sha256: String)

/** The download being worked on and whether the user allowed mobile data for it. */
@Serializable
data class PendingDownload(val info: UpdateInfo, val allowMetered: Boolean)

/**
 * What the update pipeline remembers across process restarts: the last check, the newest
 * known release, the running download, the verified package, and which version was already
 * announced by a notification. Shared by the UI and the workers through [UpdateController].
 */
class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val installedCode = BuildConfig.VERSION_CODE.toLong()

    init {
        // 1.x / early 2.0 kept only the name and code of the known release.
        if (prefs.contains(LEGACY_KNOWN_NAME) || prefs.contains(LEGACY_KNOWN_CODE)) {
            prefs.edit { remove(LEGACY_KNOWN_NAME).remove(LEGACY_KNOWN_CODE) }
        }
    }

    private val _lastCheckedAt = MutableStateFlow(prefs.getLong(KEY_LAST_CHECK, 0L))
    /** Epoch millis of the last successful check; 0 when never checked. */
    val lastCheckedAt: StateFlow<Long> = _lastCheckedAt.asStateFlow()

    private val _known = MutableStateFlow(read<UpdateInfo>(KEY_KNOWN)?.takeIf { it.versionCode > installedCode })
    /** Newest release found by a check that is newer than this build, or null. */
    val known: StateFlow<UpdateInfo?> = _known.asStateFlow()

    private val _ready = MutableStateFlow(read<ReadyUpdate>(KEY_READY)?.takeIf { it.info.versionCode > installedCode })
    val ready: StateFlow<ReadyUpdate?> = _ready.asStateFlow()

    private val _pending = MutableStateFlow(read<PendingDownload>(KEY_PENDING)?.takeIf { it.info.versionCode > installedCode })
    val pending: StateFlow<PendingDownload?> = _pending.asStateFlow()

    private val _dismissedWork = MutableStateFlow(prefs.getString(KEY_DISMISSED_WORK, null))
    /** Id of a finished download whose result (failure) the user dismissed. */
    val dismissedWork: StateFlow<String?> = _dismissedWork.asStateFlow()

    /** versionCode of the last 发现新版本 notification. */
    var notifiedVersion: Long
        get() = prefs.getLong(KEY_NOTIFIED, 0L)
        set(value) = prefs.edit { putLong(KEY_NOTIFIED, value) }

    @Synchronized
    fun recordCheck(now: Long, info: UpdateInfo?) {
        _lastCheckedAt.value = now
        _known.value = info
        prefs.edit { putLong(KEY_LAST_CHECK, now).write(KEY_KNOWN, info) }
    }

    /** Forgets the known release, e.g. a prerelease after switching back to 稳定版. */
    @Synchronized
    fun clearKnown() {
        _known.value = null
        prefs.edit { remove(KEY_KNOWN) }
    }

    @Synchronized
    fun setReady(value: ReadyUpdate?) {
        _ready.value = value
        prefs.edit { write(KEY_READY, value) }
    }

    @Synchronized
    fun setPending(value: PendingDownload?) {
        _pending.value = value
        prefs.edit { write(KEY_PENDING, value) }
    }

    @Synchronized
    fun setDismissedWork(id: String?) {
        _dismissedWork.value = id
        prefs.edit { putString(KEY_DISMISSED_WORK, id) }
    }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> android.content.SharedPreferences.Editor.write(key: String, value: T?) {
        if (value == null) remove(key) else putString(key, json.encodeToString(kotlinx.serialization.serializer<T>(), value))
    }

    private companion object {
        /** Same file as the 2.0 settings page used for the last check. */
        const val PREFS = "update_check"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_KNOWN = "known_update"
        const val KEY_READY = "ready_update"
        const val KEY_PENDING = "pending_download"
        const val KEY_NOTIFIED = "notified_version"
        const val KEY_DISMISSED_WORK = "dismissed_work"
        const val LEGACY_KNOWN_NAME = "known_version_name"
        const val LEGACY_KNOWN_CODE = "known_version_code"
    }
}
