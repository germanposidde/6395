package com.kmp.hook.di

import com.kmp.hook.data.createSettings
import com.kmp.hook.data.export.BackupBundle
import com.kmp.hook.data.repository.ActivityRepositoryImpl
import com.kmp.hook.data.repository.FeedRepositoryImpl
import com.kmp.hook.data.repository.FishGroupRepositoryImpl
import com.kmp.hook.data.repository.SettingsRepositoryImpl
import com.kmp.hook.data.seed.SeedData
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.model.AppSettings
import com.kmp.hook.domain.repository.ActivityRepository
import com.kmp.hook.domain.repository.FeedRepository
import com.kmp.hook.domain.repository.FishGroupRepository
import com.kmp.hook.domain.repository.SettingsRepository
import com.kmp.hook.platform.FileExporter
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/** Manual service-locator constructed once at the App root and provided via CompositionLocal. */
class AppContainer {

    private val settings: Settings = createSettings()

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val fishGroupRepo: FishGroupRepository = FishGroupRepositoryImpl(settings, json)
    val feedRepo: FeedRepository = FeedRepositoryImpl(settings, json)
    val activityRepo: ActivityRepository = ActivityRepositoryImpl(settings, json)
    val settingsRepo: SettingsRepository = SettingsRepositoryImpl(settings, json)
    val fileExporter: FileExporter = FileExporter()

    init { seedIfNeeded() }

    private fun seedIfNeeded() {
        if (settings.getBoolean(SEED_FLAG, false)) return
        val bundle = SeedData.build()
        fishGroupRepo.replaceAll(bundle.groups)
        feedRepo.replaceAll(bundle.feed)
        activityRepo.replaceAll(bundle.activity)
        settings.putBoolean(SEED_FLAG, true)
    }

    fun resetAllData() {
        fishGroupRepo.clear()
        feedRepo.clear()
        activityRepo.clear()
        settingsRepo.set(AppSettings())
    }

    fun reseedSampleData() {
        val bundle = SeedData.build()
        fishGroupRepo.replaceAll(bundle.groups)
        feedRepo.replaceAll(bundle.feed)
        activityRepo.replaceAll(bundle.activity)
    }

    fun exportJson(pretty: Boolean = true): String {
        val bundle = BackupBundle(
            exportedAtMillis = AppClock.nowMillis(),
            groups = fishGroupRepo.groups.value,
            feedHistory = feedRepo.history.value,
            activity = activityRepo.entries.value,
            settings = settingsRepo.settings.value,
        )
        val encoder = if (pretty) Json(json) { prettyPrint = true } else json
        return encoder.encodeToString(BackupBundle.serializer(), bundle)
    }

    /** Returns true on a successful import. */
    fun importJson(raw: String): Boolean = runCatching {
        val bundle = json.decodeFromString(BackupBundle.serializer(), raw)
        fishGroupRepo.replaceAll(bundle.groups)
        feedRepo.replaceAll(bundle.feedHistory)
        activityRepo.replaceAll(bundle.activity)
        settingsRepo.set(bundle.settings)
        true
    }.getOrElse { false }

    companion object { private const val SEED_FLAG = "seeded_v1" }
}
