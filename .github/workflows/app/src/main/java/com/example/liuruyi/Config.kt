package com.example.liuruyi

/**
 * 全局配置常量（规格第 9 节）。
 * 模型名集中在一处，以你账号实际可用模型名为准。
 */
object Config {
    const val MODEL_NAME = "deepseek-flash"
    const val BASE_URL = "https://api.deepseek.com"

    // 上下文预算
    const val SOFT_TOKEN_LIMIT = 4500
    const val HARD_TOKEN_LIMIT = 7000
    const val FULL_RAW_SAFE_LIMIT = 60000

    // 生成
    const val MAX_NEW_TOKENS = 200
    const val SUMMARY_TARGET_TOKENS = 800
    const val SUMMARY_MIN_NEW_TOKENS = 600

    // 主动消息
    const val PROACTIVE_DAILY_LIMIT = 5
    const val PROACTIVE_OPEN_LIMIT = 1
    const val RECENT_MESSAGES_FOR_PROACTIVE = 10

    const val DATA_DIR_NAME = "liuruyi"

    // 图片（仅用户→她）
    const val IMAGE_PROMPT = "（系统提示：用户刚刚发来一张图片。你现在看不到图片的实际内容，不要假装知道图里是什么；请自然回应——可以问问他发的是什么、夸一夸、或顺着之前的话题继续。保持人设和拟真语气，别每句都带表情。）"
}
