package com.example.liuruyi.util

import com.example.liuruyi.data.Message
import kotlin.math.ceil

/**
 * 保守的 token 估算器。
 * 中文按 1 字 ≈ 1.5 token（取上界，倾向高估，避免超预算），
 * 其他字符按 4 字符 ≈ 1 token，另加每条消息的结构开销。
 */
object TokenEstimator {

    /** 每条消息额外的角色标记 / 结构开销。 */
    const val STRUCTURE_OVERHEAD = 8

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (c in text) {
            if (isCjk(c)) cjk++ else other++
        }
        val cjkTokens = cjk * 1.5
        val otherTokens = other / 4.0
        return ceil(cjkTokens + otherTokens).toInt().coerceAtLeast(1)
    }

    fun estimateMessages(messages: List<Message>): Int =
        messages.sumOf { estimate(it.content) + STRUCTURE_OVERHEAD }

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return (code in 0x4E00..0x9FFF) ||
            (code in 0x3400..0x4DBF) ||
            (code in 0xF900..0xFAFF) ||
            (code in 0x3000..0x303F) // CJK 标点
    }
}
