package com.example.liuruyi.domain

import com.example.liuruyi.data.Message
import com.example.liuruyi.data.SummaryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    private val builder = ContextBuilder()
    private val system = "你是一个测试助手。"

    private fun msg(id: String, role: String, content: String) =
        Message(id = id, role = role, content = content, createdAt = 0L)

    @Test
    fun normalModeMergesSystemMemoryAndSummaryInOrder() {
        val messages = listOf(
            msg("1", "user", "你好"),
            msg("2", "assistant", "嗨，咋啦"),
        )
        val summary = SummaryFile(content = "之前聊过天气", coveredUntilMessageId = "1")
        val plan = builder.buildNormal(system, "记忆：她喜欢猫", summary, messages, "今天想吃啥")

        assertEquals("system", plan.messages.first().role)
        assertTrue(plan.messages.first().content.contains("你是一个测试助手"))
        assertTrue(plan.messages.first().content.contains("记忆：她喜欢猫"))
        assertTrue(plan.messages.first().content.contains("之前聊过天气"))

        // coveredUntil=1 之后只有 id=2 那条，再跟上当前输入
        val roles = plan.messages.drop(1).map { it.role }
        assertEquals(listOf("assistant", "user"), roles)
        assertEquals("今天想吃啥", plan.messages.last().content)
    }

    @Test
    fun unsummarizedMessagesHonorsCoveredUntil() {
        val messages = listOf(msg("a", "user", "1"), msg("b", "assistant", "2"), msg("c", "user", "3"))
        assertEquals(listOf("b", "c"), builder.unsummarizedMessages(messages, "a").map { it.id })
        assertEquals(listOf("a", "b", "c"), builder.unsummarizedMessages(messages, null).map { it.id })
    }

    @Test
    fun longInputTriggersHardLimit() {
        val long = "啊".repeat(6000)
        val plan = builder.buildNormal(system, "", null, listOf(msg("1", "user", long)), "继续")
        assertTrue(plan.overHardLimit)
        assertTrue(plan.estimatedTokens > 7000)
    }

    @Test
    fun fullRawOmitsSummaryAndKeepsEveryMessage() {
        val messages = listOf(msg("1", "user", "第一句"), msg("2", "assistant", "第二句"))
        val plan = builder.buildFullRaw(system, "记忆", messages, "第三句")
        assertFalse(plan.messages.first().content.contains("摘要"))
        assertEquals(4, plan.messages.size)
        assertEquals("第三句", plan.messages.last().content)
    }
}
