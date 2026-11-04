package com.example.liuruyi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageTest {

    private fun tempStore(): LocalStore =
        LocalStore(Files.createTempDirectory("liuruyi-test").toFile())

    @Test
    fun indexRoundTrip() {
        val store = tempStore()
        val meta = SessionMeta(
            id = "s1", title = "标题", createdAt = 1L, updatedAt = 2L,
            messageCount = 0, lastMessagePreview = "", summaryFile = "sessions/s1.summary.json",
        )
        store.writeIndex(IndexFile(activeSessionId = "s1", sessions = listOf(meta)))
        val read = store.readIndex()
        assertEquals("s1", read.activeSessionId)
        assertEquals("标题", read.sessions.first().title)
    }

    @Test
    fun appendAndReadMessagesInOrder() {
        val store = tempStore()
        store.appendMessage("s1", Message(id = "1", role = "user", content = "你好", createdAt = 1L))
        store.appendMessage("s1", Message(id = "2", role = "assistant", content = "嗨", createdAt = 2L))
        val msgs = store.readMessages("s1")
        assertEquals(2, msgs.size)
        assertEquals("你好", msgs[0].content)
        assertEquals("assistant", msgs[1].role)
    }

    @Test
    fun summaryRoundTrip() {
        val store = tempStore()
        store.writeSummary("s1", SummaryFile(content = "摘要", coveredUntilMessageId = "3", updatedAt = 9L, tokenEstimate = 4))
        val s = store.readSummary("s1")
        assertEquals("3", s.coveredUntilMessageId)
        assertEquals("摘要", s.content)
    }

    @Test
    fun corruptIndexRebuildsEmptyAndBacksUp() {
        val dir = Files.createTempDirectory("liuruyi-test").toFile()
        dir.mkdirs()
        File(dir, "index.json").writeText("{ not valid json")
        val store = LocalStore(dir)
        val read = store.readIndex()
        assertEquals("", read.activeSessionId)
        assertTrue(File(dir, "index.json.bak").exists())
    }
}
