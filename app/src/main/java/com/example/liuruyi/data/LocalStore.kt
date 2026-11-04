package com.example.liuruyi.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException

/**
 * 应用私有目录 files/liuruyi/ 下的本地文件存取。
 * 纯 JVM 依赖（不引用 android.*），可单元测试。
 * 所有写 index / memory 均走临时文件 + rename 的原子写；
 * 会话消息为 JSONL 追加写；损坏文件备份为 .bak 后重建。
 */
class LocalStore(val baseDir: File) {

    val indexFile: File get() = File(baseDir, "index.json")
    val memoryFile: File get() = File(baseDir, "memory.json")
    val sessionsDir: File get() = File(baseDir, "sessions")
    val relationshipFile: File get() = File(baseDir, "relationship.json")

    fun ensureDirs() {
        baseDir.mkdirs()
        sessionsDir.mkdirs()
    }

    // ---------- index ----------
    fun readIndex(): IndexFile {
        ensureDirs()
        if (!indexFile.exists()) return IndexFile()
        return try {
            JsonUtil.json.decodeFromString<IndexFile>(indexFile.readText())
        } catch (e: Exception) {
            backupAndLog(indexFile, e)
            IndexFile()
        }
    }

    fun writeIndex(index: IndexFile) {
        ensureDirs()
        atomicWrite(indexFile, JsonUtil.json.encodeToString(index))
    }

    // ---------- memory ----------
    fun readMemory(): MemoryFile {
        ensureDirs()
        if (!memoryFile.exists()) return MemoryFile()
        return try {
            JsonUtil.json.decodeFromString<MemoryFile>(memoryFile.readText())
        } catch (e: Exception) {
            backupAndLog(memoryFile, e)
            MemoryFile()
        }
    }

    fun writeMemory(memory: MemoryFile) {
        ensureDirs()
        atomicWrite(memoryFile, JsonUtil.json.encodeToString(memory))
    }

    fun readRelationship(): RelationshipFile {
        ensureDirs()
        if (!relationshipFile.exists()) return RelationshipFile()
        return try {
            JsonUtil.json.decodeFromString<RelationshipFile>(relationshipFile.readText())
        } catch (e: Exception) {
            backupAndLog(relationshipFile, e)
            RelationshipFile()
        }
    }

    fun writeRelationship(rel: RelationshipFile) {
        ensureDirs()
        atomicWrite(relationshipFile, JsonUtil.json.encodeToString(rel))
    }

    // ---------- avatar / image ----------
    val avatarFile: File get() = File(baseDir, "avatar.jpg")
    val myAvatarFile: File get() = File(baseDir, "my_avatar.jpg")
    val imagesDir: File get() = File(baseDir, "images")

    fun saveAvatar(bytes: ByteArray) {
        ensureDirs()
        avatarFile.writeBytes(bytes)
    }

    fun saveMyAvatar(bytes: ByteArray) {
        ensureDirs()
        myAvatarFile.writeBytes(bytes)
    }

    fun saveImage(id: String, bytes: ByteArray): File {
        ensureDirs()
        imagesDir.mkdirs()
        val f = File(imagesDir, id + ".jpg")
        f.writeBytes(bytes)
        return f
    }

    fun imageFile(id: String): File = File(imagesDir, id + ".jpg")

    // ---------- sessions ----------
    private fun sessionFile(sessionId: String): File = File(sessionsDir, sessionId + ".jsonl")
    private fun summaryFile(sessionId: String): File = File(sessionsDir, sessionId + ".summary.json")

    fun appendMessage(sessionId: String, message: Message) {
        ensureDirs()
        sessionFile(sessionId).appendText(JsonUtil.json.encodeToString(message) + "\n")
    }

    fun readMessages(sessionId: String): List<Message> {
        val f = sessionFile(sessionId)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            if (line.isBlank()) null
            else runCatching { JsonUtil.json.decodeFromString<Message>(line) }.getOrNull()
        }
    }

    fun writeMessages(sessionId: String, messages: List<Message>) {
        ensureDirs()
        val text = if (messages.isEmpty()) ""
        else messages.joinToString("\n") { JsonUtil.json.encodeToString(it) } + "\n"
        atomicWrite(sessionFile(sessionId), text)
    }

    fun deleteSessionFiles(sessionId: String) {
        runCatching { sessionFile(sessionId).delete() }
        runCatching { summaryFile(sessionId).delete() }
    }

    fun readSummary(sessionId: String): SummaryFile {
        val f = summaryFile(sessionId)
        if (!f.exists()) return SummaryFile()
        return try {
            JsonUtil.json.decodeFromString<SummaryFile>(f.readText())
        } catch (e: Exception) {
            backupAndLog(f, e)
            SummaryFile()
        }
    }

    fun writeSummary(sessionId: String, summary: SummaryFile) {
        ensureDirs()
        atomicWrite(summaryFile(sessionId), JsonUtil.json.encodeToString(summary))
    }

    // ---------- internals ----------
    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                throw IOException("atomic write failed: " + target.name)
            }
        }
    }

    private fun backupAndLog(file: File, cause: Exception) {
        runCatching {
            val bak = File(file.parentFile, file.name + ".bak")
            if (bak.exists()) bak.delete()
            file.renameTo(bak)
        }
        // 不抛异常：损坏时尽力备份并重建，保持可用。
        println("[LocalStore] corrupt file backed up: " + file.name + " (" + cause.message + ")")
    }
}
