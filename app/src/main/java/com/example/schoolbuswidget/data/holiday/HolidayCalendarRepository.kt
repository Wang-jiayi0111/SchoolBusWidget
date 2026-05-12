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
        val isRest = load.map[mmdd] ?: return HolidayDayLookup.Unknown
        return HolidayDayLookup.Resolved(isRestDay = isRest, fromNetwork = load.fromNetwork)
    }

    private data class YearHolidayLoad(
        val map: Map<String, Boolean>,
        val fromNetwork: Boolean,
    )

    private suspend fun loadYearHolidayMap(year: Int): YearHolidayLoad? {
        val prefs = context.timetableDataStore.data.first()
        val cachedJson = prefs[stringKey(year)]
        val fetchedAt = prefs[longKey(year)] ?: 0L
        val now = System.currentTimeMillis()
        val cacheFresh = cachedJson != null && (now - fetchedAt) < CACHE_TTL_MS

        if (cacheFresh) {
            val parsed = HolidayJsonParser.parseYearHolidayFlags(cachedJson!!)
            if (parsed != null) return YearHolidayLoad(parsed, fromNetwork = false)
        }

        val networkJson = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { fetchYearJson(year) }
        if (networkJson != null) {
            val parsed = HolidayJsonParser.parseYearHolidayFlags(networkJson)
            if (parsed != null) {
                context.timetableDataStore.edit { data ->
                    data[stringKey(year)] = networkJson
                    data[longKey(year)] = now
                }
                return YearHolidayLoad(parsed, fromNetwork = true)
            }
        }

        if (cachedJson != null) {
            val parsed = HolidayJsonParser.parseYearHolidayFlags(cachedJson)
            if (parsed != null) {
                AppLog.w("Using stale holiday cache for year $year")
                return YearHolidayLoad(parsed, fromNetwork = false)
            }
        }

        AppLog.w("Holiday data unavailable for year $year; falling back to week rule")
        return null
    }

    private suspend fun fetchYearJson(year: Int): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://timor.tech/api/holiday/year/$year")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                AppLog.w("Holiday API HTTP $code for year $year")
                return@withContext null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLog.w("Holiday API request failed for year $year", e)
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
    }
}
