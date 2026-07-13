package com.kmp.hook.domain.calc

import kotlin.math.abs
import kotlin.math.round

/** KMP-safe number formatting (no String.format in commonMain). */

private fun pow10(n: Int): Double {
    var r = 1.0
    repeat(n) { r *= 10.0 }
    return r
}

/** Format a Double with a fixed number of decimals, e.g. 12.3456.format(2) -> "12.35". */
fun Double.format(decimals: Int = 1): String {
    if (isNaN() || isInfinite()) return "0"
    val factor = pow10(decimals)
    val neg = this < 0
    var scaled = round(abs(this) * factor).toLong()
    val whole = scaled / factor.toLong()
    val frac = scaled % factor.toLong()
    val sign = if (neg && scaled != 0L) "-" else ""
    if (decimals == 0) return sign + whole.toString()
    var fracStr = frac.toString()
    while (fracStr.length < decimals) fracStr = "0$fracStr"
    return "$sign$whole.$fracStr"
}

fun Float.format(decimals: Int = 1): String = this.toDouble().format(decimals)

/** Group thousands with commas, e.g. 12500 -> "12,500". */
fun Long.grouped(): String {
    val s = abs(this).toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return (if (this < 0) "-" else "") + sb.toString()
}

fun Int.grouped(): String = this.toLong().grouped()

/** Compact display, e.g. 1500 -> "1.5k", 2_300_000 -> "2.3M". */
fun Double.compact(): String = when {
    abs(this) >= 1_000_000 -> "${(this / 1_000_000).format(1)}M"
    abs(this) >= 1_000 -> "${(this / 1_000).format(1)}k"
    else -> format(if (this == this.toLong().toDouble()) 0 else 1)
}
