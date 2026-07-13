package com.kmp.hook.domain.repository

import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.AppSettings
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.domain.model.FishGroup
import kotlinx.coroutines.flow.StateFlow

interface FishGroupRepository {
    val groups: StateFlow<List<FishGroup>>
    fun upsert(group: FishGroup)
    fun delete(id: String)
    fun getById(id: String): FishGroup?
    fun replaceAll(list: List<FishGroup>)
    fun clear()
}

interface FeedRepository {
    val history: StateFlow<List<FeedCalculation>>
    fun add(calculation: FeedCalculation)
    fun delete(id: String)
    fun replaceAll(list: List<FeedCalculation>)
    fun clear()
}

interface ActivityRepository {
    val entries: StateFlow<List<ActivityEntry>>
    fun add(entry: ActivityEntry)
    fun replaceAll(list: List<ActivityEntry>)
    fun clear()
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
    fun set(settings: AppSettings)
}
