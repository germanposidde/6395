package com.kmp.hook.feature.feedcalc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.FeedMath
import com.kmp.hook.domain.calc.FeedResult
import com.kmp.hook.domain.calc.IdGen
import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.ActivityType
import com.kmp.hook.domain.model.FeedCalculation
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.repository.ActivityRepository
import com.kmp.hook.domain.repository.FeedRepository

class FeedCalculatorViewModel(
    private val feedRepo: FeedRepository,
    private val activityRepo: ActivityRepository,
) : ViewModel() {

    var label by mutableStateOf("Manual estimate")
    var quantity by mutableStateOf(800)
    var avgWeight by mutableStateOf(200.0)
    var feedingPercent by mutableStateOf(3.0)
    var fcr by mutableStateOf(1.5)
    var horizonDays by mutableStateOf(30)
    var pricePerKg by mutableStateOf(1.2)
    var selectedGroupId by mutableStateOf<String?>(null)

    val result: FeedResult
        get() = FeedMath.compute(quantity, avgWeight, feedingPercent, fcr, horizonDays, pricePerKg)

    fun prefillFrom(group: FishGroup) {
        selectedGroupId = group.id
        label = group.species
        quantity = group.quantity
        avgWeight = group.avgWeightGrams
        pricePerKg = group.feedPricePerKg
    }

    fun save() {
        val r = result
        val now = AppClock.nowMillis()
        feedRepo.add(
            FeedCalculation(
                id = IdGen.next("c_"),
                createdAtEpochMillis = now,
                label = label.ifBlank { "Estimate" },
                biomassKg = r.biomassKg,
                feedingPercent = feedingPercent,
                dailyFeedKg = r.dailyFeedKg,
                fcr = r.fcr,
                predictedWeightGrams = r.predictedWeightGrams,
                horizonDays = horizonDays,
                feedCost = r.feedCost,
                groupId = selectedGroupId,
            )
        )
        activityRepo.add(
            ActivityEntry(
                id = IdGen.next("a_"),
                epochMillis = now,
                type = ActivityType.CALCULATION,
                title = "Saved feed calculation",
                detail = label.ifBlank { "Estimate" },
            )
        )
    }
}
