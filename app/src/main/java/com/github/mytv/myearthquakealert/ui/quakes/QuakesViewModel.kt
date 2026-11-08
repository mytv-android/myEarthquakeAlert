package com.github.mytv.myearthquakealert.ui.quakes

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.mytv.myearthquakealert.MyEarthQuakeAlertApp
import com.github.mytv.myearthquakealert.data.model.EarthquakeInfo
import com.github.mytv.myearthquakealert.data.repository.EewRepository
import com.github.mytv.myearthquakealert.data.repository.SettingsRepository
import com.github.mytv.myearthquakealert.util.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The CENC catalogue for the 地震 destination, the overview's 最近地震 and the TV / kiosk
 * tickers. Browsing data only — it never takes part in alerting (design §3 浏览数据另算).
 * One instance per activity, shared by every destination.
 */
class QuakesViewModel(
    private val appContext: Context,
    private val repository: EewRepository,
    private val settingsRepository: SettingsRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    @Immutable
    data class UiState(
        /** Whole catalogue, newest first. */
        val all: List<QuakeUi> = emptyList(),
        /** [all] after filter, query and sort. */
        val visible: List<QuakeUi> = emptyList(),
        val loading: Boolean = false,
        val error: Boolean = false,
        /** Epoch millis of the last successful load; 0 before the first one. */
        val loadedAt: Long = 0L,
        val user: UserPoint? = null,
        /** True once the automatic location lookup has finished (successfully or not). */
        val locationResolved: Boolean = false,
        val filter: QuakeFilter = QuakeFilter.ALL,
        val sort: QuakeSort = QuakeSort.TIME,
        val query: String = "",
        val selectedKey: String? = null,
    ) {
        val selected: QuakeUi? get() = selectedKey?.let { key -> all.firstOrNull { it.key == key } }
        val hasData: Boolean get() = loadedAt > 0L
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var raw: List<EarthquakeInfo> = emptyList()
    private var manualPoint: UserPoint? = null
    private var autoPoint: UserPoint? = null
    private var loadJob: Job? = null
    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { s ->
                    if (s.hasManualLocation) {
                        UserPoint(s.manualLatitude!!, s.manualLongitude!!, UserPoint.Source.MANUAL, s.manualLocationName)
                    } else {
                        null
                    }
                }
                .distinctUntilChanged()
                .collect { manual ->
                    val hadManual = manualPoint != null
                    manualPoint = manual
                    // Switching back to automatic: look the position up again.
                    if (manual == null && hadManual && autoPoint == null) refreshLocation()
                    rebuild()
                }
        }
        refresh()
        refreshLocation()
    }

    /** Reloads the catalogue. Keeps showing the previous list while loading. */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = false) }
            try {
                raw = withContext(Dispatchers.IO) { repository.getEarthquakeHistory() }
                _state.update { it.copy(loadedAt = System.currentTimeMillis()) }
                rebuild()
                _state.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load the earthquake catalogue", e)
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /** Refreshes when the data is older than [STALE_MS] (called when the app comes back). */
    fun refreshIfStale(now: Long = System.currentTimeMillis()) {
        val s = _state.value
        if (s.loading) return
        if (!s.hasData || now - s.loadedAt > STALE_MS) refresh()
    }

    /** Looks up the device position (last GPS / network fix, else IP). Manual locations win. */
    fun refreshLocation() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            val point = try {
                withContext(Dispatchers.IO) {
                    val location = locationProvider.getLocation()
                    if (location.latitude == 0.0 && location.longitude == 0.0) {
                        null
                    } else {
                        val source = when (location.source) {
                            "gps" -> UserPoint.Source.GPS
                            "network" -> UserPoint.Source.NETWORK
                            else -> UserPoint.Source.IP
                        }
                        UserPoint(location.latitude, location.longitude, source, placeName(location.latitude, location.longitude))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Location lookup failed", e)
                null
            }
            if (point != null) autoPoint = point
            _state.update { it.copy(locationResolved = true) }
            rebuild()
        }
    }

    fun setFilter(filter: QuakeFilter) {
        _state.update { it.copy(filter = filter) }
        rebuild()
    }

    fun setSort(sort: QuakeSort) {
        _state.update { it.copy(sort = sort) }
        rebuild()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        rebuild()
    }

    fun select(key: String?) {
        _state.update { it.copy(selectedKey = key) }
    }

    private fun rebuild() {
        val user = manualPoint ?: autoPoint
        val all = raw.mapNotNull { QuakeCatalog.fromInfo(it, user) }
            .sortedByDescending { it.epochMillis ?: Long.MIN_VALUE }
        _state.update { s ->
            s.copy(
                all = all,
                user = user,
                visible = QuakeCatalog.apply(all, s.filter, s.sort, s.query),
            )
        }
    }

    /** "成都市武侯区" via the platform geocoder when the device has one; empty otherwise. */
    private fun placeName(latitude: Double, longitude: Double): String {
        if (!Geocoder.isPresent()) return ""
        return try {
            @Suppress("DEPRECATION")
            val address = Geocoder(appContext, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
                ?: return ""
            listOfNotNull(address.locality ?: address.adminArea, address.subLocality)
                .distinct()
                .joinToString(if (Locale.getDefault().language == "zh") "" else ", ")
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "QuakesViewModel"
        const val STALE_MS = 5 * 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MyEarthQuakeAlertApp
                QuakesViewModel(app, app.eewRepository, app.settingsRepository, app.locationProvider)
            }
        }
    }
}
