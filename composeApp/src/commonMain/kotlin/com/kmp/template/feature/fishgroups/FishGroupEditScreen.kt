package com.kmp.hook.feature.fishgroups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmp.hook.domain.calc.AppClock
import com.kmp.hook.domain.calc.IdGen
import com.kmp.hook.domain.model.ActivityEntry
import com.kmp.hook.domain.model.ActivityType
import com.kmp.hook.domain.model.FishGroup
import com.kmp.hook.domain.model.GroupStatus
import com.kmp.hook.domain.model.GrowthPoint
import com.kmp.hook.di.LocalAppContainer
import com.kmp.hook.ui.components.AppTextField
import com.kmp.hook.ui.components.PhotoField
import com.kmp.hook.ui.components.PrimaryActionButton
import com.kmp.hook.ui.components.ScreenHeader
import com.kmp.hook.ui.components.SegmentedControl
import com.kmp.hook.ui.components.dismissKeyboardOnTap

@Composable
fun FishGroupEditScreen(
    groupId: String?,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val groups by container.fishGroupRepo.groups.collectAsStateWithLifecycle()
    val existing = remember(groups, groupId) { groupId?.let { id -> groups.firstOrNull { it.id == id } } }

    var species by rememberSaveable(existing) { mutableStateOf(existing?.species ?: "") }
    var quantity by rememberSaveable(existing) { mutableStateOf(existing?.quantity?.toString() ?: "") }
    var avgWeight by rememberSaveable(existing) { mutableStateOf(existing?.avgWeightGrams?.toInt()?.toString() ?: "") }
    var targetWeight by rememberSaveable(existing) { mutableStateOf(existing?.targetWeightGrams?.toInt()?.toString() ?: "") }
    var ageDays by rememberSaveable(existing) {
        mutableStateOf(
            existing?.let { AppClock.daysBetween(it.stockingEpochMillis, AppClock.nowMillis()).toString() } ?: "0"
        )
    }
    var feedPrice by rememberSaveable(existing) { mutableStateOf(existing?.feedPricePerKg?.toString() ?: "1.2") }
    var notes by rememberSaveable(existing) { mutableStateOf(existing?.notes ?: "") }
    var photo by rememberSaveable(existing) { mutableStateOf(existing?.photoBase64) }
    var statusIndex by rememberSaveable(existing) {
        mutableStateOf(GroupStatus.entries.indexOf(existing?.status ?: GroupStatus.HEALTHY))
    }

    val valid = species.isNotBlank() &&
        (quantity.toIntOrNull() ?: 0) > 0 &&
        (avgWeight.toDoubleOrNull() ?: 0.0) > 0.0

    fun save() {
        if (!valid) return
        val now = AppClock.nowMillis()
        val qty = quantity.toIntOrNull() ?: 0
        val weight = avgWeight.toDoubleOrNull() ?: 0.0
        val target = targetWeight.toDoubleOrNull() ?: (weight * 2.5)
        val age = ageDays.toIntOrNull() ?: 0
        val stocking = now - age * AppClock.DAY_MS
        val status = GroupStatus.entries[statusIndex]

        val points = if (existing != null) {
            val last = existing.growthPoints.lastOrNull()
            if (last == null || kotlin.math.abs(last.avgWeightGrams - weight) > 0.01)
                existing.growthPoints + GrowthPoint(now, weight)
            else existing.growthPoints
        } else {
            listOf(GrowthPoint(stocking, weight * 0.4), GrowthPoint(now, weight))
        }

        val group = FishGroup(
            id = existing?.id ?: IdGen.next("g_"),
            species = species.trim(),
            quantity = qty,
            avgWeightGrams = weight,
            stockingEpochMillis = stocking,
            notes = notes.trim(),
            status = status,
            targetWeightGrams = target,
            feedPricePerKg = feedPrice.toDoubleOrNull() ?: 1.2,
            growthPoints = points,
            photoBase64 = photo,
        )
        container.fishGroupRepo.upsert(group)
        container.activityRepo.add(
            ActivityEntry(
                id = IdGen.next("a_"),
                epochMillis = now,
                type = if (existing == null) ActivityType.STOCKING else ActivityType.MEASUREMENT,
                title = if (existing == null) "New group stocked" else "Updated group",
                detail = "${group.species} • ${group.quantity} fish",
            )
        )
        onBack()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .dismissKeyboardOnTap(),
    ) {
        ScreenHeader(title = if (existing == null) "Add group" else "Edit group", onBack = onBack)

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PhotoField(photoBase64 = photo, onPhotoChange = { photo = it })
            AppTextField(species, { species = it }, "Species", imeAction = ImeAction.Next)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(quantity, { quantity = it.filter { c -> c.isDigit() } }, "Quantity", Modifier.weight(1f), KeyboardType.Number)
                AppTextField(avgWeight, { avgWeight = it.filter { c -> c.isDigit() } }, "Avg weight", Modifier.weight(1f), KeyboardType.Number, suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(targetWeight, { targetWeight = it.filter { c -> c.isDigit() } }, "Target weight", Modifier.weight(1f), KeyboardType.Number, suffix = "g")
                AppTextField(ageDays, { ageDays = it.filter { c -> c.isDigit() } }, "Age", Modifier.weight(1f), KeyboardType.Number, suffix = "days")
            }
            AppTextField(
                feedPrice,
                { feedPrice = it.filter { c -> c.isDigit() || c == '.' } },
                "Feed price per kg",
                keyboardType = KeyboardType.Decimal,
            )
            Column {
                Text("Status", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SegmentedControl(
                    options = GroupStatus.entries.map {
                        when (it) {
                            GroupStatus.HEALTHY -> "Healthy"
                            GroupStatus.MONITOR -> "Monitor"
                            GroupStatus.CRITICAL -> "Critical"
                        }
                    },
                    selectedIndex = statusIndex,
                    onSelected = { statusIndex = it },
                )
            }
            AppTextField(notes, { notes = it }, "Notes", imeAction = ImeAction.Default, onImeAction = { save() }, singleLine = false)
            Spacer(Modifier.height(4.dp))
            PrimaryActionButton(
                text = if (existing == null) "Add group" else "Save changes",
                onClick = { save() },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
