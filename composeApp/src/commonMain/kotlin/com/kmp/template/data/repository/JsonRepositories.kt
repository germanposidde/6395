package com.kmp.hook.data.repository

import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.AppSettings
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.repository.ActivityRepository
import com.kmp.hook.domain.repository.FeedRepository
import com.kmp.hook.domain.repository.FishGroupRepository
import com.kmp.hook.domain.repository.SettingsRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Shared base: holds an in-memory StateFlow list, persisted as a JSON blob. */
abstract class JsonListStore<T>(
    private val settings: Settings,
    private val json: Json,
    private val key: String,
    serializer: KSerializer<T>,
) {
    private val listSerializer = ListSerializer(serializer)
    private val _items = MutableStateFlow(loadInitial())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private fun loadInitial(): List<T> {
        val raw = settings.getStringOrNull(key) ?: return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrElse { emptyList() }
    }

    protected fun persist(list: List<T>) {
        _items.value = list
        settings.putString(key, json.encodeToString(listSerializer, list))
    }

    protected fun current(): List<T> = _items.value
}

class FishGroupRepositoryImpl(
    settings: Settings,
    json: Json,
) : JsonListStore<FishGroup>(settings, json, KEY, FishGroup.serializer()), FishGroupRepository {

    override val groups: StateFlow<List<FishGroup>> get() = items

    override fun upsert(group: FishGroup) {
        val list = current().toMutableList()
        val idx = list.indexOfFirst { it.id == group.id }
        if (idx >= 0) list[idx] = group else list.add(group)
        persist(list)
    }

    override fun delete(id: String) = persist(current().filterNot { it.id == id })

    override fun getById(id: String): FishGroup? = current().firstOrNull { it.id == id }

    override fun replaceAll(list: List<FishGroup>) = persist(list)

    override fun clear() = persist(emptyList())

    companion object { private const val KEY = "fish_groups" }
}

class FeedRepositoryImpl(
    settings: Settings,
    json: Json,
) : JsonListStore<FeedCalculation>(settings, json, KEY, FeedCalculation.serializer()), FeedRepository {

    override val history: StateFlow<List<FeedCalculation>> get() = items

    override fun add(calculation: FeedCalculation) =
        persist((listOf(calculation) + current()).take(100))

    override fun delete(id: String) = persist(current().filterNot { it.id == id })

    override fun replaceAll(list: List<FeedCalculation>) = persist(list)

    override fun clear() = persist(emptyList())

    companion object { private const val KEY = "feed_history" }
}

class ActivityRepositoryImpl(
    settings: Settings,
    json: Json,
) : JsonListStore<ActivityEntry>(settings, json, KEY, ActivityEntry.serializer()), ActivityRepository {

    override val entries: StateFlow<List<ActivityEntry>> get() = items

    override fun add(entry: ActivityEntry) = persist((listOf(entry) + current()).take(60))

    override fun replaceAll(list: List<ActivityEntry>) = persist(list)

    override fun clear() = persist(emptyList())

    companion object { private const val KEY = "activity" }
}

class SettingsRepositoryImpl(
    private val store: Settings,
    private val json: Json,
) : SettingsRepository {

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> get() = _settings.asStateFlow()

    private fun load(): AppSettings {
        val raw = store.getStringOrNull(KEY) ?: return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrElse { AppSettings() }
    }

    override fun update(transform: (AppSettings) -> AppSettings) = set(transform(_settings.value))

    override fun set(settings: AppSettings) {
        _settings.value = settings
        store.putString(KEY, json.encodeToString(AppSettings.serializer(), settings))
    }

    companion object { private const val KEY = "app_settings" }
}
