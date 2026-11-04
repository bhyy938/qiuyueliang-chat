package com.example.liuruyi

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.liuruyi.data.KeystoreHelper
import com.example.liuruyi.data.LocalStore
import com.example.liuruyi.data.MemoryRepository
import com.example.liuruyi.data.Message
import com.example.liuruyi.data.SessionRepository
import com.example.liuruyi.domain.ProactiveManager
import com.example.liuruyi.domain.RelationshipManager
import com.example.liuruyi.network.DeepSeekClient
import com.example.liuruyi.util.Notifier
import com.example.liuruyi.util.SystemTimeProvider
import com.example.liuruyi.util.newId
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** 后台定时检查她是否要主动找你，并弹系统通知。 */
class ProactiveWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val key = KeystoreHelper.loadApiKey(context) ?: return Result.success()
        val store = LocalStore(File(context.filesDir, Config.DATA_DIR_NAME))
        val repo = SessionRepository(store)
        val index = repo.loadIndex()
        if (index.proactive.sentCount >= Config.PROACTIVE_DAILY_LIMIT) return Result.success()

        val active = index.activeSessionId
        if (active.isBlank()) return Result.success()
        val messages = repo.loadMessages(active)
        if (messages.isEmpty()) return Result.success()

        // 最近 30 分钟还在聊，说明用户正活跃，不打扰
        if (System.currentTimeMillis() - messages.last().createdAt < 30 * 60 * 1000L) return Result.success()

        val recentText = messages.joinToString(" ") { it.content }
        val anchors = listOf("明天", "考试", "放假", "约定", "周末", "生日", "下次", "想我", "回来")
        val hasAnchor = anchors.any { recentText.contains(it) }
        if (!hasAnchor && Random.nextInt(100) >= 35) return Result.success()

        val memory = MemoryRepository(store).load()
        val rel = store.readRelationship()
        val systemPrompt = Prompts.SYSTEM_PROMPT +
            RelationshipManager().systemBlock(rel.closeness) +
            (if (memory.content.isNotBlank()) "\n\n【全局长期记忆】\n" + memory.content else "") +
            "\n\n【今天的日期】\n" + dateNote()

        val text = ProactiveManager(DeepSeekClient()).generate(
            systemPrompt, messages, SystemTimeProvider().nowText(), key,
        ) ?: return Result.success()

        val msg = Message(
            id = newId(),
            role = "assistant",
            content = text,
            createdAt = System.currentTimeMillis(),
            kind = "proactive",
            status = "complete",
        )
        repo.appendAndUpdateIndex(active, msg, System.currentTimeMillis())
        val updated = repo.loadIndex()
        repo.saveIndex(
            updated.copy(
                proactive = updated.proactive.copy(
                    sentCount = updated.proactive.sentCount + 1,
                    lastOpenSent = true,
                ),
            ),
        )
        Notifier.post(context, "邱岳靓", text)
        return Result.success()
    }

    private fun dateNote(): String {
        val n = Instant.now().atZone(ZoneId.systemDefault())
        val week = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        return "今天是" + n.year + "年" + n.monthValue + "月" + n.dayOfMonth + "日 " + week[n.dayOfWeek.value - 1]
    }
}
