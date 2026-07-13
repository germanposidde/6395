package com.kmp.hook.feature.fishgroups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.IdGen
import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.ActivityType
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.model.GroupStatus
import com.kmp.hook.domain.repository.ActivityRepository
import com.kmp.hook.domain.repository.FishGroupRepository

enum class GroupSort(val label: String) {
    RECENT("Newest"), WEIGHT("Weight"), QUANTITY("Count"), NAME("Name")
}

class FishGroupsViewModel(
    private val repo: FishGroupRepository,
    private val activityRepo: ActivityRepository,
) : ViewModel() {

    var query by mutableStateOf("")
    var sort by mutableStateOf(GroupSort.RECENT)
    var statusFilter by mutableStateOf<GroupStatus?>(null)

    fun visible(all: List<FishGroup>): List<FishGroup> {
        val q = query.trim().lowercase()
        val filtered = all.filter { g ->
            (statusFilter == null || g.status == statusFilter) &&
                (q.isEmpty() || g.species.lowercase().contains(q) || g.notes.lowercase().contains(q))
        }
        return when (sort) {
            GroupSort.RECENT -> filtered.sortedByDescending { it.stockingEpochMillis }
            GroupSort.WEIGHT -> filtered.sortedByDescending { it.avgWeightGrams }
            GroupSort.QUANTITY -> filtered.sortedByDescending { it.quantity }
            GroupSort.NAME -> filtered.sortedBy { it.species.lowercase() }
        }
    }

    fun delete(group: FishGroup) {
        repo.delete(group.id)
        activityRepo.add(
            ActivityEntry(
                id = IdGen.next("a_"),
                epochMillis = AppClock.nowMillis(),
                type = ActivityType.NOTE,
                title = "Removed group",
                detail = group.species,
            )
        )
    }
}
