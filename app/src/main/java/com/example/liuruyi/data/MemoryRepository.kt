package com.example.liuruyi.data

import com.example.liuruyi.util.TokenEstimator

/**
 * 跨会话全局记忆。新会话加载并计入上下文预算。
 */
class MemoryRepository(private val store: LocalStore) {

    fun load(): MemoryFile = store.readMemory()

    fun save(memory: MemoryFile) = store.writeMemory(memory)

    /** 写入记忆正文，并同步更新 token 估算。 */
    fun saveContent(content: String, now: Long): MemoryFile {
        val memory = MemoryFile(
            version = 1,
            updatedAt = now,
            content = content,
            tokenEstimate = TokenEstimator.estimate(content),
        )
        store.writeMemory(memory)
        return memory
    }
}
