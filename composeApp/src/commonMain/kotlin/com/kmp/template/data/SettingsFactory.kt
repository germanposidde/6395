package com.kmp.hook.data

import com.russhwolf.settings.Settings

/**
 * No-arg multiplatform-settings factory — backed by SharedPreferences on Android
 * and NSUserDefaults on iOS, with zero platform plumbing.
 */
fun createSettings(): Settings = Settings()
