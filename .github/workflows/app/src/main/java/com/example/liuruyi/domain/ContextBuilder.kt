package com.example.liuruyi.domain

import com.example.liuruyi.Config
import com.example.liuruyi.data.Message
import com.example.liuruyi.data.SummaryFile
import com.example.liuruyi.network.ApiMessage
import com.example.liuruyi.util.TokenEstimator

/**
 * 上下文组装（纯逻辑，可单元测试）。
 *
 * 正常模式顺序：system → 全局记忆 → 会话摘要 → coveredUntil 之后的最近消息 → 当前输入。
 * 软阈值 >4500 且未摘要消息 ≥600 时触发摘要；
 * >7000 返回告警状态，由上层决定是否走全原文模式。
 */
class ContextBuilder {

    data class NormalPlan(
        val messages: List<ApiMessage>,
        val systemContent: String,
        val estimatedTokens: Int,
        val recentTokens: Int,
        val needsSummary: Boolean,
        val overHardLimit: Boolean,
    )

    data class FullRawPlan(
        val messages: List<ApiMessage>,
        val estimatedTokens: Int,
    )

    fun buildNormal(
        systemPrompt: String,
        globalMemory: String,
        summary: SummaryFile?,
        messages: List<Message>,
        currentInput: String,
    ): NormalPlan {
        val recent = unsummarizedMessages(messages, summary?.coveredUntilMessageId)
        val (systemContent, apiMessages) = assemble(systemPrompt, globalMemory, summary?.content, recent, currentInput)
        val recentTokens = TokenEstimator.estimateMessages(recent)
        val estimatedTokens = estimateTotal(systemContent, recent, currentInput)
        return NormalPlan(
            messages = apiMessages,
            systemContent = systemContent,
            estimatedTokens = estimatedTokens,
            recentTokens = recentTokens,
            needsSummary = estimatedTokens > Config.SOFT_TOKEN_LIMIT &&
                recentTokens >= Config.SUMMARY_MIN_NEW_TOKENS,
            overHardLimit = estimatedTokens > Config.HARD_TOKEN_LIMIT,
        )
    }

    fun buildFullRaw(
        systemPrompt: String,
        globalMemory: String,
        messages: List<Message>,
        currentInput: String,
    ): FullRawPlan {
        val (systemContent, apiMessages) = assemble(systemPrompt, globalMemory, null, messages, currentInput)
        return FullRawPlan(
            messages = apiMessages,
            estimatedTokens = estimateTotal(systemContent, messages, currentInput),
        )
    }

    /** coveredUntil 之后还未摘要的消息；coveredUntil 为 null 时返回全部。 */
    fun unsummarizedMessages(messages: List<Message>, coveredUntil: String?): List<Message> {
        if (coveredUntil == null) return messages
        val idx = messages.indexOfFirst { it.id == coveredUntil }
        return if (idx >= 0) messages.subList(idx + 1, messages.size) else messages
    }

    private fun assemble(
        systemPrompt: String,
        globalMemory: String,
        summaryContent: String?,
        recent: List<Message>,
        currentInput: String,
    ): Pair<String, List<ApiMessage>> {
        val sb = StringBuilder(systemPrompt)
        if (globalMemory.isNotBlank()) {
            sb.append("\n\n【全局长期记忆】\n").append(globalMemory)
        }
        if (!summaryContent.isNullOrBlank()) {
            sb.append("\n\n【当前会话摘要】\n").append(summaryContent)
        }
        val systemContent = sb.toString()
        val apiMessages = ArrayList<ApiMessage>(recent.size + 2)
        apiMessages.add(ApiMessage("system", systemContent))
        for (m in recent) {
            if (m.recalled || m.content.isBlank()) continue
            apiMessages.add(ApiMessage(m.role, m.content))
        }
        apiMessages.add(ApiMessage("user", currentInput))
        return systemContent to apiMessages
    }

    private fun estimateTotal(systemContent: String, recent: List<Message>, input: String): Int =
        TokenEstimator.estimate(systemContent) +
            TokenEstimator.estimateMessages(recent) +
            TokenEstimator.estimate(input) +
            TokenEstimator.STRUCTURE_OVERHEAD
}
