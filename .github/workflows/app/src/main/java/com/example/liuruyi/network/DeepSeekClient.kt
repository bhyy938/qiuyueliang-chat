package com.example.liuruyi.network

import com.example.liuruyi.Config
import com.example.liuruyi.data.JsonUtil
import com.example.liuruyi.data.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ApiMessage(val role: String, val content: String)

data class ChatRequest(
    val messages: List<ApiMessage>,
    val maxTokens: Int = Config.MAX_NEW_TOKENS,
    val temperature: Double = 0.9,
    val topP: Double = 0.9,
)

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    data class Done(val content: String, val usage: Usage) : StreamEvent()
}

data class Completion(val content: String, val usage: Usage)

class DeepSeekException(val statusCode: Int?, message: String) : Exception(message)

class DeepSeekClient(private val baseUrl: String = Config.BASE_URL) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE：不限读超时
        .build()

    /** 流式输出。取消收集协程即停止生成。 */
    fun stream(request: ChatRequest, apiKey: String): Flow<StreamEvent> = channelFlow {
        val call = client.newCall(buildRequest(request, apiKey, stream = true))
        invokeOnClose { call.cancel() }
        val full = StringBuilder()
        var usage = Usage()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw DeepSeekException(response.code, "HTTP " + response.code + " " + response.message)
                }
                val source = response.body?.source()
                    ?: throw DeepSeekException(null, "empty response body")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (SseParser.isDone(data)) break
                    val delta = SseParser.extractDeltaContent(data)
                    if (!delta.isNullOrEmpty()) {
                        full.append(delta)
                        send(StreamEvent.Delta(delta))
                    }
                    SseParser.extractUsage(data)?.let { usage = it }
                }
            }
            if (full.isEmpty()) {
                throw DeepSeekException(null, "empty completion")
            }
            send(StreamEvent.Done(full.toString(), usage))
        } catch (e: Throwable) {
            close(e)
        }
    }.flowOn(Dispatchers.IO)

    /** 非流式完成（用于摘要、主动消息）。 */
    suspend fun complete(request: ChatRequest, apiKey: String): Completion =
        withContext(Dispatchers.IO) {
            val call = client.newCall(buildRequest(request, apiKey, stream = false))
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = SseParser.parseError(body) ?: ("HTTP " + response.code + " " + response.message)
                    throw DeepSeekException(response.code, msg)
                }
                val usage = SseParser.extractUsage(body) ?: Usage()
                Completion(SseParser.parseCompletionText(body).orEmpty(), usage)
            }
        }

    private fun buildRequest(request: ChatRequest, apiKey: String, stream: Boolean): Request {
        val messages = JsonArray(
            request.messages.map { m ->
                buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                }
            },
        )
        val bodyJson = buildJsonObject {
            put("model", Config.MODEL_NAME)
            put("messages", messages)
            put("stream", stream)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("top_p", request.topP)
        }
        val body = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        return Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
    }
}
