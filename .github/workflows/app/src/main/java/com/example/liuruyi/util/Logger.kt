package com.example.liuruyi.util

import android.util.Log

object Logger {
    private const val TAG = "Liuruyi"

    @Volatile
    var enabled: Boolean = true

    fun d(msg: String) { if (enabled) Log.d(TAG, msg) }
    fun w(msg: String) { if (enabled) Log.w(TAG, msg) }
    fun e(msg: String, t: Throwable? = null) { if (enabled) Log.e(TAG, msg, t) }

    /** 脱敏 API Key，绝不打完整 Key 或完整 prompt。 */
    fun scrub(text: String): String =
        text.replace(Regex("(sk-[A-Za-z0-9_-]+)"), "sk-***")
}
