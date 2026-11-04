package com.example.liuruyi.data

import kotlinx.serialization.Serializable

@Serializable
data class ProactiveState(
    val date: String = "",
    val sentCount: Int = 0,
    val lastOpenDate: String = "",
    val lastOpenSent: Boolean = false,
)

@Serializable
data class SessionMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val lastMessagePreview: String,
    val summaryFile: String,
    val lastSummarizedMessageId: String? = null,
)

@Serializable
data class IndexFile(
    val version: Int = 1,
    val activeSessionId: String = "",
    val sessions: List<SessionMeta> = emptyList(),
    val proactive: ProactiveState = ProactiveState(),
)

@Serializable
data class MemoryFile(
    val version: Int = 1,
    val updatedAt: Long = 0,
    val content: String = "",
    val tokenEstimate: Int = 0,
)

@Serializable
data class SummaryFile(
    val content: String = "",
    val coveredUntilMessageId: String? = null,
    val updatedAt: Long = 0,
    val tokenEstimate: Int = 0,
)

@Serializable
data class RelationshipFile(
    val version: Int = 1,
    val updatedAt: Long = 0,
    val closeness: Int = 35,
    val exchanged: Int = 0,
    val lastAdvanceDay: String = "",
)

@Serializable
data class Usage(
    val prompt: Int = 0,
    val completion: Int = 0,
    val total: Int = 0,
)

@Serializable
data class Message(
    val id: String,
    val role: String,          // "user" | "assistant"
    val content: String,
    val createdAt: Long,
    val tokenEstimate: Int = 0,
    val kind: String = "chat", // "chat" | "proactive"
    val status: String = "complete", // "complete" | "stopped" | "error"
    val usage: Usage = Usage(),
    val quoteId: String? = null,
    val quoteText: String? = null,
    val quoteRole: String? = null,
    val recalled: Boolean = false,
    val imagePath: String? = null,
)
