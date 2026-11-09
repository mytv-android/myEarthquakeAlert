package com.github.mytv.myearthquakealert.wear.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Everything the watch shows, in one process-wide store fed by the phone (via
 * [com.github.mytv.myearthquakealert.wear.service.WatchListenerService]) and by standalone
 * monitoring. The phone status, the recent quakes, the active alert and the 我已安全 mark
 * are kept in a small file so the tile and the complication have something to show after
 * the process restarts.
 */
object WatchStore {

    private const val TAG = "WatchStore"
    private const val FILE = "watch_store.json"
    const val QUAKE_LIMIT = 10

    @Serializable
    private data class Persisted(
        val status: WireStatus? = null,
        val quakes: List<WireQuake> = emptyList(),
        val alert: WatchAlert? = null,
        val dismissedEventId: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    private val _status = MutableStateFlow<WireStatus?>(null)
    /** The phone's last synced status (null until the first sync). */
    val status: StateFlow<WireStatus?> = _status.asStateFlow()

    private val _quakes = MutableStateFlow<List<WireQuake>>(emptyList())
    /** Recent quakes, newest first (the phone's list, plus what standalone monitoring saw). */
    val quakes: StateFlow<List<WireQuake>> = _quakes.asStateFlow()

    private val _alert = MutableStateFlow<WatchAlert?>(null)
    val alert: StateFlow<WatchAlert?> = _alert.asStateFlow()

    private val _dismissed = MutableStateFlow<String?>(null)
    /** Event the user marked 我已安全; its later reports do not alert again unless stronger. */
    val dismissedEventId: StateFlow<String?> = _dismissed.asStateFlow()

    private val _phoneConnected = MutableStateFlow<Boolean?>(null)
    /** A phone with the app is reachable; null until the first capability answer. */
    val phoneConnected: StateFlow<Boolean?> = _phoneConnected.asStateFlow()

    private val _standalone = MutableStateFlow(StandaloneState())
    val standalone: StateFlow<StandaloneState> = _standalone.asStateFlow()

    private val _commandResults = MutableSharedFlow<WireCommandResult>(extraBufferCapacity = 8)
    /** Replies of the phone to watch commands ([PhoneLink.send] waits on these). */
    val commandResults: SharedFlow<WireCommandResult> = _commandResults.asSharedFlow()

    private val _pending = MutableStateFlow<String?>(null)
    /** The command waiting for the phone's answer (buttons show progress meanwhile). */
    val pendingCommand: StateFlow<String?> = _pending.asStateFlow()

    private val _outcome = MutableStateFlow<CommandOutcome?>(null)
    val commandOutcome: StateFlow<CommandOutcome?> = _outcome.asStateFlow()
    private var outcomeId = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var file: File? = null

    /** Loads the saved state once per process; then saves every change. */
    @OptIn(FlowPreview::class)
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        try {
            if (f.exists()) {
                val p = json.decodeFromString(Persisted.serializer(), f.readText())
                _status.value = p.status
                _quakes.value = p.quakes
                _alert.value = p.alert
                _dismissed.value = p.dismissedEventId
            }
        } catch (e: Exception) {
            Log.w(TAG, "saved state unreadable", e)
        }
        scope.launch {
            combine(_status, _quakes, _alert, _dismissed) { s, q, a, d -> Persisted(s, q, a, d) }
                .drop(1)
                .distinctUntilChanged()
                .debounce(300)
                .collect { save(it) }
        }
    }

    private fun save(p: Persisted) {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, "$FILE.tmp")
            tmp.writeText(json.encodeToString(Persisted.serializer(), p))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "state not saved", e)
        }
    }

    /** Whatever the surfaces (tile, complication) depend on; emits on a change. */
    fun surfaceInputs(): Flow<Any> =
        combine(_status, _quakes, _alert, _phoneConnected, _standalone) { s, q, a, p, sa ->
            listOf(s?.copy(updatedAt = 0L), q.firstOrNull()?.eventId, q.firstOrNull()?.reports, a?.eventId, a?.strong, p, sa.running, sa.link)
        }.distinctUntilChanged()

    fun phase(now: Long = System.currentTimeMillis()): WatchPhase =
        WatchPhases.resolve(_status.value, _phoneConnected.value, _standalone.value, now)

    fun setStatus(status: WireStatus) {
        val current = _status.value
        // DataItems may arrive out of order after a reconnect.
        if (current != null && status.updatedAt in 1 until current.updatedAt) return
        _status.value = status
    }

    fun setPhoneQuakes(quakes: WireQuakes) {
        _quakes.value = quakes.quakes.take(QUAKE_LIMIT)
    }

    /** A report seen by standalone monitoring: upserted by event, newest first. */
    fun addQuake(quake: WireQuake) {
        _quakes.update { list ->
            val old = list.firstOrNull { it.eventId == quake.eventId }
            val merged = if (old != null && old.reports > quake.reports) old else quake
            (listOf(merged) + list.filter { it.eventId != quake.eventId })
                .sortedByDescending { if (it.receivedAt > 0) it.receivedAt else it.originTime }
                .take(QUAKE_LIMIT)
        }
    }

    fun setAlert(alert: WatchAlert?) {
        _alert.value = alert
    }

    fun clearAlert(eventId: String? = null) {
        _alert.update { current -> if (eventId == null || current?.eventId == eventId) null else current }
    }

    fun markDismissed(eventId: String) {
        _dismissed.value = eventId
    }

    fun setPhoneConnected(connected: Boolean) {
        _phoneConnected.value = connected
    }

    fun updateStandalone(transform: (StandaloneState) -> StandaloneState) {
        _standalone.update(transform)
    }

    fun onCommandResult(result: WireCommandResult) {
        _commandResults.tryEmit(result)
        // Show the new phase right away; the status DataItem follows.
        val phase = result.phase ?: return
        _status.update { s -> s?.copy(phase = phase, pauseUntil = result.pauseUntil) }
    }

    fun setPending(command: String?) {
        _pending.value = command
    }

    @Synchronized
    fun postOutcome(command: String, ok: Boolean, error: String?) {
        outcomeId += 1
        _outcome.value = CommandOutcome(outcomeId, command, ok, error)
    }

    fun consumeOutcome(id: Long) {
        _outcome.update { if (it?.id == id) null else it }
    }
}
