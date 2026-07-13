package com.kmp.hook.elite

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Minimal A/B-event logger for Firebase Analytics (streamed to BigQuery).
 *
 * Logging only — variant assignment / bucketing lives in your existing A/B system;
 * you pass the already-assigned variant in. ONE event per test: the event name IS the
 * test key, and what happened (`exposed` / `choice` / `result` / …) is the `action`
 * param, the bucket is `ab_variant`, and your own values ride alongside.
 *
 * See README.md for the BigQuery event shape and example queries.
 *
 * Quick start:
 *   AbTracker.init(app)                                       // Application.onCreate
 *   AbTracker.log("push_permission", variant, "exposed")
 *   AbTracker.log("push_permission", variant, "choice", "choice" to "ok")
 *   // → event "push_permission" { action:"choice", ab_variant:<variant>, choice:"ok" }
 */
object AbTracker {

    // GA4 names: letters/digits/underscore, start with a letter, ≤40 chars.
    private val NAME_RE = Regex("^[a-z][a-z0-9_]{0,39}$")
    private const val PREFS = "ab_logger"

    private lateinit var prefs: SharedPreferences
    private lateinit var fa: FirebaseAnalytics

    /** Call once (Application.onCreate). Safe to call again. */
    fun init(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fa = FirebaseAnalytics.getInstance(app)
    }

    /**
     * Log an A/B event. Event name = [test] (one event per test). The already-assigned
     * [variant] and the [action] (e.g. "exposed" / "choice" / "result") are attached as
     * params, along with your extra [params] (String / Int / Long / Double / Float /
     * Boolean).
     */
    fun log(test: String, variant: String, action: String, vararg params: Pair<String, Any>) {
        require(test.matches(NAME_RE)) { "invalid test name '$test' (a-z, 0-9, _, start with letter, ≤40)" }
        require(action.matches(NAME_RE)) { "invalid action '$action'" }
        val b = Bundle(2 + params.size).apply {
            putString("ab_variant", variant)
            putString("action", action)
            params.forEach { (k, v) -> putParam(k, v) }
        }
        fa.logEvent(test, b)
    }

    /** Like [log], but fires at most once per install for this (test, action). */
    fun logOnce(test: String, variant: String, action: String, vararg params: Pair<String, Any>) {
        val flag = "once_${test}_$action"
        if (prefs.getBoolean(flag, false)) return
        prefs.edit(commit = true) { putBoolean(flag, true) }
        log(test, variant, action, *params)
    }

    private fun Bundle.putParam(key: String, value: Any) {
        when (value) {
            is Long -> putLong(key, value)
            is Int -> putLong(key, value.toLong())
            is Double -> putDouble(key, value)
            is Float -> putDouble(key, value.toDouble())
            is Boolean -> putString(key, value.toString())
            is String -> putString(key, value)
            else -> putString(key, value.toString())
        }
    }
}
