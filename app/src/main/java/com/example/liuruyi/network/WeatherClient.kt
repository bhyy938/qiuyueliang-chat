package com.example.liuruyi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WeatherInfo(val city: String, val desc: String, val tempMin: Double, val tempMax: Double) {
    fun text(): String = city + " " + desc + "，气温 " + tempMin.toInt() + "~" + tempMax.toInt() + "℃"
}

class WeatherClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun fetch(city: String): WeatherInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val coord = geocode(city) ?: return@runCatching null
            forecast(coord.first, coord.second, city)
        }.getOrNull()
    }

    private fun geocode(city: String): Pair<Double, Double>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(city, "UTF-8") + "&count=1&language=zh&format=json"
        val body = get(url) ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: return null
        if (results.isEmpty()) return null
        val first = results[0].jsonObject
        val lat = first["latitude"]?.jsonPrimitive?.double ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.double ?: return null
        return lat to lon
    }

    private fun forecast(lat: Double, lon: Double, city: String): WeatherInfo? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
            "&current_weather=true&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val body = get(url) ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        val cw = root["current_weather"]?.jsonObject
        val code = cw?.get("weathercode")?.jsonPrimitive?.int ?: return null
        val desc = codeDesc(code)
        val cur = cw?.get("temperature")?.jsonPrimitive?.doubleOrNull
        val daily = root["daily"]?.jsonObject
        val tMax = daily?.get("temperature_2m_max")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.doubleOrNull
        val tMin = daily?.get("temperature_2m_min")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.doubleOrNull
        val lo = tMin ?: cur
        val hi = tMax ?: cur
        if (lo == null || hi == null) return null
        return WeatherInfo(city, desc, lo, hi)
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    private fun codeDesc(code: Int): String = when (code) {
        0 -> "晴"
        1, 2, 3 -> "多云"
        45, 48 -> "雾"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 63, 65, 66, 67 -> "雨"
        71, 73, 75, 77 -> "雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷暴"
        else -> "阴"
    }
}
