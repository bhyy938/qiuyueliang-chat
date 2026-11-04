package com.example.liuruyi.data

import kotlinx.serialization.json.Json

/** 全局共享的 JSON 配置。 */
object JsonUtil {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
}
