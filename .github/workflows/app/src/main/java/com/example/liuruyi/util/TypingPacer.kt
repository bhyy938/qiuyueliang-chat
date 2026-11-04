package com.example.liuruyi.util

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 拟真打字节奏：先有一个“正在输入”的思考停顿，再逐字放出；
 * 标点、换行处稍作停顿，模拟真人打字，而非瞬间刷屏。
 */
object TypingPacer {

    suspend fun initialDelay(random: Random = Random.Default) {
        delay(random.nextLong(600, 1500))
    }

    suspend fun delayFor(ch: Char, random: Random = Random.Default) {
        val ms = when {
            ch == '\n' -> random.nextLong(260, 560)
            ch in "。！？…!?" -> random.nextLong(220, 440)
            ch in "，、；：,;:" -> random.nextLong(140, 300)
            ch in "～~—·" -> random.nextLong(120, 260)
            else -> random.nextLong(45, 110)
        }
        delay(ms)
    }
}
