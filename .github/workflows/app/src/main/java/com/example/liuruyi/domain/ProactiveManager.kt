package com.example.liuruyi.domain

import com.example.liuruyi.Config
import com.example.liuruyi.Prompts
import com.example.liuruyi.data.IndexFile
import com.example.liuruyi.data.Message
import com.example.liuruyi.data.ProactiveState
import com.example.liuruyi.network.ApiMessage
import com.example.liuruyi.network.ChatRequest
import com.example.liuruyi.network.DeepSeekClient
import kotlin.random.Random

/**
 * 主动消息：每天最多 5 条、每次打开最多 1 条、可 SKIP、锚点词或 30% 概率。
 */
class ProactiveManager(
    private val client: DeepSeekClient,
    private val random: Random = Random.Default,
) {
    private val anchorWords = listOf("明天", "考试", "放假", "答应", "约定", "下次", "周末", "生日", "回来")

    /** 打开 APP / onResume 时调用：重置当次打开标志，跨天重置计数。 */
    fun onAppOpened(index: IndexFile, today: String): IndexFile {
        val p = index.proactive
        val newP = if (p.date != today) {
            ProactiveState(date = today, sentCount = 0, lastOpenDate = today, lastOpenSent = false)
        } else {
            p.copy(lastOpenDate = today, lastOpenSent = false)
        }
        return index.copy(proactive = newP)
    }

    fun shouldTrigger(proactive: ProactiveState, recentMessages: List<Message>): Boolean {
        if (proactive.sentCount >= Config.PROACTIVE_DAILY_LIMIT) return false
        if (proactive.lastOpenSent) return false
        val recentText = recentMessages.joinToString(" ") { it.content }
        val hasAnchor = anchorWords.any { recentText.contains(it) }
        if (hasAnchor) return true
        return random.nextInt(100) < 30
    }

    /** 生成主动消息文本；返回 null 表示 SKIP 或失败。 */
    suspend fun generate(
        systemPrompt: String,
        recentMessages: List<Message>,
        nowText: String,
        apiKey: String,
    ): String? {
        val recent = recentMessages
            .filter { !it.recalled && it.content.isNotBlank() }
            .takeLast(Config.RECENT_MESSAGES_FOR_PROACTIVE)
        val recentText = recent.joinToString("\n") { m ->
            (if (m.role == "user") "用户：" else "邱岳靓：") + m.content
        }
        val userContent = buildString {
            append(Prompts.PROACTIVE_PROMPT)
            append("\n\n【最近对话】\n").append(recentText)
            append("\n\n【当前时间】\n").append(nowText)
        }
        val request = ChatRequest(
            messages = listOf(
                ApiMessage("system", systemPrompt),
                ApiMessage("user", userContent),
            ),
            maxTokens = 80,
            temperature = 0.9,
        )
        return try {
            val completion = client.complete(request, apiKey)
            val text = completion.content.trim()
            if (text.isEmpty() || text.equals("SKIP", ignoreCase = true)) null else text
        } catch (e: Exception) {
            null
        }
    }
}
