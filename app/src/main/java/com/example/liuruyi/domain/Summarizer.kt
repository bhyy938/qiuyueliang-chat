package com.example.liuruyi.domain

import com.example.liuruyi.Config
import com.example.liuruyi.Prompts
import com.example.liuruyi.data.Message
import com.example.liuruyi.data.Usage
import com.example.liuruyi.network.ApiMessage
import com.example.liuruyi.network.ChatRequest
import com.example.liuruyi.network.DeepSeekClient

/**
 * 摘要：把已有长期摘要与新增旧对话合并成新摘要，目标 ≤800 token。
 */
class Summarizer(private val client: DeepSeekClient) {

    data class Result(val content: String, val usage: Usage)

    suspend fun summarize(
        existingSummary: String,
        newOldMessages: List<Message>,
        apiKey: String,
    ): Result {
        val userContent = buildString {
            append("【已有长期摘要】\n").append(existingSummary.ifBlank { "（暂无）" })
            append("\n\n【新增旧对话】\n")
            for (m in newOldMessages) {
                if (m.recalled || m.content.isBlank()) continue
                append(if (m.role == "user") "用户：" else "邱岳靓：")
                append(m.content).append("\n")
            }
        }
        val request = ChatRequest(
            messages = listOf(
                ApiMessage("system", Prompts.SUMMARY_PROMPT),
                ApiMessage("user", userContent),
            ),
            maxTokens = Config.SUMMARY_TARGET_TOKENS,
            temperature = 0.3,
        )
        val completion = client.complete(request, apiKey)
        return Result(completion.content.trim(), completion.usage)
    }
}
