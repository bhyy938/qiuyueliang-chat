package com.example.liuruyi.data

import com.example.liuruyi.util.newId

/**
 * 会话相关的高层操作：索引维护 + 会话消息 + 摘要。
 */
class SessionRepository(private val store: LocalStore) {

    fun loadIndex(): IndexFile = store.readIndex()

    fun saveIndex(index: IndexFile) = store.writeIndex(index)

    fun loadMessages(sessionId: String): List<Message> = store.readMessages(sessionId)

    fun appendMessage(sessionId: String, message: Message) = store.appendMessage(sessionId, message)

    fun loadSummary(sessionId: String): SummaryFile = store.readSummary(sessionId)

    fun saveSummary(sessionId: String, summary: SummaryFile) = store.writeSummary(sessionId, summary)

    fun createSession(now: Long): IndexFile {
        val index = store.readIndex()
        val id = newId()
        val meta = SessionMeta(
            id = id,
            title = "新会话",
            createdAt = now,
            updatedAt = now,
            messageCount = 0,
            lastMessagePreview = "",
            summaryFile = "sessions/$id.summary.json",
        )
        val updated = index.copy(
            sessions = index.sessions + meta,
            activeSessionId = id,
        )
        store.writeIndex(updated)
        return updated
    }

    fun switchSession(index: IndexFile, sessionId: String): IndexFile {
        val updated = index.copy(activeSessionId = sessionId)
        store.writeIndex(updated)
        return updated
    }

    /** 追加一条消息并同步更新索引的预览信息。原始消息永久保留，不删除。 */
    fun appendAndUpdateIndex(sessionId: String, message: Message, now: Long): IndexFile {
        store.appendMessage(sessionId, message)
        val index = store.readIndex()
        val updated = index.copy(
            sessions = index.sessions.map { meta ->
                if (meta.id == sessionId) {
                    meta.copy(
                        updatedAt = now,
                        messageCount = meta.messageCount + 1,
                        lastMessagePreview = message.content.take(40),
                        title = if (meta.title == "新会话") message.content.take(12) else meta.title,
                    )
                } else meta
            },
        )
        store.writeIndex(updated)
        return updated
    }

    fun updateSummaryCoverage(sessionId: String, summary: SummaryFile, now: Long): IndexFile {
        store.writeSummary(sessionId, summary)
        val index = store.readIndex()
        val updated = index.copy(
            sessions = index.sessions.map { meta ->
                if (meta.id == sessionId) meta.copy(lastSummarizedMessageId = summary.coveredUntilMessageId)
                else meta
            },
        )
        store.writeIndex(updated)
        return updated
    }

    fun rewriteMessages(sessionId: String, messages: List<Message>) =
        store.writeMessages(sessionId, messages)

    /** 从索引中移除会话（消息/摘要文件保留，供撤销删除恢复）。 */
    fun removeSession(index: IndexFile, sessionId: String, now: Long): IndexFile {
        val remaining = index.sessions.filter { it.id != sessionId }
        val newActive = if (index.activeSessionId == sessionId) remaining.firstOrNull()?.id.orEmpty() else index.activeSessionId
        val updated = index.copy(sessions = remaining, activeSessionId = newActive)
        store.writeIndex(updated)
        return updated
    }

    /** 撤销删除：把 meta 重新加回索引、设为当前会话。 */
    fun restoreSession(sessionId: String, meta: SessionMeta): IndexFile {
        val index = store.readIndex()
        val updated = index.copy(
            sessions = (index.sessions + meta).distinctBy { it.id },
            activeSessionId = sessionId,
        )
        store.writeIndex(updated)
        return updated
    }

    fun deleteSessionFiles(sessionId: String) = store.deleteSessionFiles(sessionId)
}
