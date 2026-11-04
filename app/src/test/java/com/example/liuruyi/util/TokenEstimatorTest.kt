package com.example.liuruyi.util

import com.example.liuruyi.data.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun emptyIsZero() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun cjkCountsAboutOneAndHalfPerChar() {
        // 4 个汉字 * 1.5 = 6
        assertEquals(6, TokenEstimator.estimate("你好世界"))
    }

    @Test
    fun asciiCountsAboutQuarterPerChar() {
        // 5 / 4 = 1.25 -> ceil = 2
        assertEquals(2, TokenEstimator.estimate("hello"))
    }

    @Test
    fun messagesAddStructureOverhead() {
        val msgs = listOf(
            Message(id = "1", role = "user", content = "你好", createdAt = 0L),
            Message(id = "2", role = "assistant", content = "hello", createdAt = 0L),
        )
        val bare = TokenEstimator.estimate("你好") + TokenEstimator.estimate("hello")
        assertTrue(TokenEstimator.estimateMessages(msgs) > bare)
    }
}
