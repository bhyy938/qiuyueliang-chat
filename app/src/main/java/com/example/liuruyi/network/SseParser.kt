package com.example.liuruyi.network

import com.example.liuruyi.data.JsonUtil
import com.example.liuruyi.data.Usage
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 纯函数，解析 DeepSeek SSE 的 data: 行与普通完成响应。
 */
object SseParser {

    fun isDone(data: String): Boolean = data.trim() == "[DONE]"

    /** data 行中 stream 的 delta 内容。 */
    fun extractDeltaContent(data: String): String? = runCatching {
        val root = JsonUtil.json.parseToJsonElement(data)
        val choices = root.jsonObject["choices"]?.jsonArray
        val delta = choices?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
        delta?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** data 行中的 usage（出现在最后一个 chunk）。 */
    fun extractUsage(data: String): Usage? = runCatching {
        val root = JsonUtil.json.parseToJsonElement(data)
        val usage = root.jsonObject["usage"]?.jsonObject ?: return null
        Usage(
            prompt = usage["prompt_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            completion = usage["completion_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            total = usage["total_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        )
    }.getOrNull()

    /** 非流式响应中的 message.content。 */
    fun parseCompletionText(body: String): String? = runCatching {
        val root = JsonUtil.json.parseToJsonElement(body)
        root.jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** 响应中的错误信息（若有）。 */
    fun parseError(body: String): String? = runCatching {
        val root = JsonUtil.json.parseToJsonElement(body)
        root.jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
