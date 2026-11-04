package com.example.liuruyi.domain

import com.example.liuruyi.Config
import com.example.liuruyi.data.Message
import com.example.liuruyi.data.SummaryFile
import com.example.liuruyi.data.Usage
import com.example.liuruyi.network.ApiMessage
import com.example.liuruyi.network.ChatRequest
import com.example.liuruyi.network.DeepSeekClient
import com.example.liuruyi.network.StreamEvent
import com.example.liuruyi.util.TokenEstimator
import com.example.liuruyi.util.TypingPacer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 串起发送、摘要、硬阈值告警、流式、停止。由 ViewModel 驱动。
 */
class ChatOrchestrator(
    private val client: DeepSeekClient,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val summarizer: Summarizer,
) {
    enum class Mode { NORMAL, FULL_RAW }

    sealed class Prepared {
        data class Ready(
            val messages: List<ApiMessage>,
            val mode: Mode,
            val estimatedTokens: Int,
        ) : Prepared()

        data class OverHard(val estimatedTokens: Int, val fullRawTokens: Int) : Prepared()
        data class OverSafe(val estimatedTokens: Int) : Prepared()
    }

    data class PrepareOutcome(
        val prepared: Prepared,
        val updatedSummary: SummaryFile?,
    )

    /** 组装上下文；必要时先跑一次摘要。返回结果 + 可能产生的新摘要（供持久化）。 */
    suspend fun prepare(
        systemPrompt: String,
        globalMemory: String,
        summary: SummaryFile?,
        messages: List<Message>,
        currentInput: String,
        apiKey: String,
    ): PrepareOutcome {
        var summaryFile = summary
        var plan = contextBuilder.buildNormal(systemPrompt, globalMemory, summaryFile, messages, currentInput)

        if (plan.needsSummary) {
            val toSummarize = contextBuilder.unsummarizedMessages(messages, summaryFile?.coveredUntilMessageId)
            if (toSummarize.isNotEmpty()) {
                val result = summarizer.summarize(summaryFile?.content.orEmpty(), toSummarize, apiKey)
                summaryFile = SummaryFile(
                    content = result.content,
                    coveredUntilMessageId = toSummarize.last().id,
                    updatedAt = System.currentTimeMillis(),
                    tokenEstimate = TokenEstimator.estimate(result.content),
                )
                plan = contextBuilder.buildNormal(systemPrompt, globalMemory, summaryFile, messages, currentInput)
            }
        }

        val prepared: Prepared = if (plan.overHardLimit) {
            val fullRaw = contextBuilder.buildFullRaw(systemPrompt, globalMemory, messages, currentInput)
            if (fullRaw.estimatedTokens > Config.FULL_RAW_SAFE_LIMIT) {
                Prepared.OverSafe(plan.estimatedTokens)
            } else {
                Prepared.OverHard(plan.estimatedTokens, fullRaw.estimatedTokens)
            }
        } else {
            Prepared.Ready(plan.messages, Mode.NORMAL, plan.estimatedTokens)
        }
        return PrepareOutcome(prepared, summaryFile)
    }

    /** 用户确认告警后，走全原文模式。 */
    fun prepareFullRaw(
        systemPrompt: String,
        globalMemory: String,
        messages: List<Message>,
        currentInput: String,
    ): Pair<List<ApiMessage>, Int> {
        val plan = contextBuilder.buildFullRaw(systemPrompt, globalMemory, messages, currentInput)
        return plan.messages to plan.estimatedTokens
    }

    data class StreamSummary(val content: String, val usage: Usage)

    /** 流式发送；onDelta 逐段回调。取消收集协程即停止生成。 */
    suspend fun stream(
        messages: List<ApiMessage>,
        apiKey: String,
        onDelta: (String) -> Unit,
    ): StreamSummary {
        var content = ""
        var usage = Usage()
        client.stream(ChatRequest(messages = messages), apiKey).collect { event ->
            when (event) {
                is StreamEvent.Delta -> {
                    content += event.text
                    onDelta(event.text)
                }
                is StreamEvent.Done -> {
                    content = event.content
                    usage = event.usage
                }
            }
        }
        return StreamSummary(content, usage)
    }

    /** 带拟真打字节奏的流式：先“正在输入”停顿，再逐字放出。 */
    suspend fun streamWithPacing(
        messages: List<ApiMessage>,
        apiKey: String,
        onContent: (String) -> Unit,
    ): StreamSummary = coroutineScope {
        val pending = Channel<Char>(Channel.UNLIMITED)
        var fullContent = ""
        var usage = Usage()
        val producer = launch {
            try {
                client.stream(ChatRequest(messages = messages), apiKey).collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> event.text.forEach { pending.send(it) }
                        is StreamEvent.Done -> { fullContent = event.content; usage = event.usage }
                    }
                }
            } finally {
                pending.close()
            }
        }
        TypingPacer.initialDelay()
        var acc = ""
        for (ch in pending) {
            TypingPacer.delayFor(ch)
            acc += ch
            onContent(acc)
        }
        producer.join()
        StreamSummary(if (fullContent.isNotBlank()) fullContent else acc, usage)
    }
}
