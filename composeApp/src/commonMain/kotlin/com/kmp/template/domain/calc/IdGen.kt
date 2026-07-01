package com.kmp.hook.domain.calc

import kotlin.random.Random

/** Lightweight unique-id generator (KMP-safe; no java.util.UUID). */
object IdGen {
    fun next(prefix: String = ""): String {
        val t = AppClock.nowMillis().toString(36)
        val r = Random.nextInt(0, 1_679_616).toString(36) // 36^4
        return "$prefix$t$r"
    }
}
