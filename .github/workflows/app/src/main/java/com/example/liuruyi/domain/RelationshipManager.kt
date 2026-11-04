package com.example.liuruyi.domain

import com.example.liuruyi.data.RelationshipFile
import kotlin.random.Random

/**
 * 情感演进：亲密度随对话增长，阶段决定语气注入。
 */
class RelationshipManager(private val random: Random = Random.Default) {

    fun stage(closeness: Int): String = when {
        closeness < 15 -> "初识"
        closeness < 35 -> "熟悉"
        closeness < 55 -> "熟络"
        closeness < 75 -> "亲密"
        else -> "心照不宣"
    }

    /** 随阶段给出语气指导，让态度自然演进、不千篇一律。 */
    fun systemBlock(closeness: Int): String {
        val s = stage(closeness)
        return "\n\n【关系状态（动态，随聊天自然变化）】你们当前亲密度 " + closeness +
            "/100，阶段「" + s +
            "」。请保持人设和高中女生分寸，让语气和称呼随阶段自然过渡：阶段越浅越嘴硬、矜持、点到为止，称呼偏\"你/你这小孩\"；" +
            "阶段越深越放松、更依赖、更敢直白表达在意和暧昧，称呼也会更亲昵、更黏人。变化要润物细无声，不突兀，也不要主动向用户提及这个数值或阶段。"
    }

    /** 每完成一轮正常问答，亲密度小幅增长（有上限）。 */
    fun advance(current: RelationshipFile, now: Long, today: String): RelationshipFile {
        val inc = if (current.closeness >= 100) 0 else random.nextInt(1, 4)
        return current.copy(
            closeness = (current.closeness + inc).coerceAtMost(100),
            exchanged = current.exchanged + 1,
            updatedAt = now,
            lastAdvanceDay = today,
        )
    }
}
