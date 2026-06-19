package com.example.schoolbuswidget.data.holiday

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.schoolbuswidget.data.timetableDataStore
import com.example.schoolbuswidget.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class HolidayCalendarRepository(
    private val context: Context,
) : HolidayCalendarSource {

    override suspend fun lookup(date: LocalDate): HolidayDayLookup {
        val year = date.year
        val mmdd = MM_DD.format(date)
        val load = loadYearHolidayMap(year) ?: return HolidayDayLookup.Unknown
        val isRest = load.map[mmdd] ?: isDefaultRestDay(date)
        return HolidayDayLookup.Resolved(isRestDay = isRest, origin = load.origin)
    }

    /** Days omitted from the API are ordinary Mon–Fri work / Sat–Sun rest. */
    private fun isDefaultRestDay(date: LocalDate): Boolean {
        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> true
            else -> false
        }
    }

    private data class YearHolidayLoad(
        val map: Map<String, Boolean>,
        val origin: HolidayDataOrigin,
    )

    private suspend fun loadYearHolidayMap(year: Int): YearHolidayLoad? {
        val prefs = context.timetableDataStore.data.first()
        val cachedJson = prefs[stringKey(year)]
        val fetchedAt = prefs[longKey(year)] ?: 0L
        val now = System.currentTimeMillis()
        val cacheFresh = cachedJson != null && (now - fetchedAt) < CACHE_TTL_MS

        if (cacheFresh) {
            val parsed = HolidayJsonParser.parseYearHolidayFlags(cachedJson!!)
            if (parsed != null) return YearHolidayLoad(parsed, HolidayDataOrigin.DISK_CACHE)
        }

        val networkJson = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { fetchYearJson(year) }
        if (networkJson != null) {
            val parsed = HolidayJsonParser.parseYearHolidayFlags(networkJson)
            if (parsed != null) {
                context.timetableDataStore.edit { data ->
                    data[stringKey(year)] = networkJson
                    data[longKey(year)] = now
                }
                return YearHolidayLoad(parsed, HolidayDataOrigin.NETWORK)
            }
        }

        if (cachedJson != null) {
            val parsed = HolidayJsonParser.parseYearHolidayFlags(cachedJson)
            if (parsed != null) {
                AppLog.w("Using stale holiday cache for year $year")
                return YearHolidayLoad(parsed, HolidayDataOrigin.DISK_CACHE)
            }
        }

        loadBundledYearJson(year)?.let { bundledJson ->
            val parsed = HolidayJsonParser.parseYearHolidayFlags(bundledJson)
            if (parsed != null) {
                AppLog.d("Using bundled holiday data for year $year")
                return YearHolidayLoad(parsed, HolidayDataOrigin.BUNDLED)
            }
        }

        AppLog.w("Holiday data unavailable for year $year; falling back to week rule")
        return null
    }

    private suspend fun loadBundledYearJson(year: Int): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open("holiday/$year.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchYearJson(year: Int): String? = withContext(Dispatchers.IO) {
        fetchFromUrl("https://timor.tech/api/holiday/year/$year", "timor")
            ?: fetchFromUrl(
                "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/$year.json",
                "jsdelivr",
            )
    }

    private fun fetchFromUrl(urlString: String, label: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                AppLog.w("Holiday API ($label) HTTP $code: $urlString")
                return null
            }
            AppLog.d("Holiday API ($label) OK: $urlString")
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.w("Holiday API ($label) failed: $urlString", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun stringKey(year: Int) = stringPreferencesKey("holiday_year_json_$year")

    private fun longKey(year: Int) = longPreferencesKey("holiday_year_fetched_at_$year")

    companion object {
        private val MM_DD: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.ROOT)

        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)
        private const val NETWORK_TIMEOUT_MS = 12_000L
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val USER_AGENT = "SchoolBusWidget/1.0 (Android)"
    }
}
