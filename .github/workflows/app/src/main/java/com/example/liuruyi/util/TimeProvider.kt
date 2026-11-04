package com.example.liuruyi.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface TimeProvider {
    fun nowMillis(): Long
    fun today(): String
    fun nowText(): String
}

class SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    override fun nowText(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
}
