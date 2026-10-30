package com.sirak.liftlog.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sirak.liftlog.wear.data.WorkoutRepository
import com.sirak.liftlog.wear.model.WatchSet
import com.sirak.liftlog.wear.model.WatchTemplate
import com.sirak.liftlog.wear.model.WatchWorkout
import com.sirak.liftlog.wear.sync.WearSync
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun StartScreen(
    templates: List<WatchTemplate>,
    phoneConnected: Boolean?,
    onStart: (unit: String) -> Unit,
    onStartTemplate: (WatchTemplate, String) -> Unit,
) {
    var unit by remember { mutableStateOf("lb") }
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    LaunchedEffect(Unit) {
        while (true) {
            WearSync.refreshPhoneLink(context)
            delay(4000)
        }
    }
    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            autoCentering = null,
        ) {
            item {
                Text("LiftLog", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.onBackground)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UnitChip("lb", unit == "lb") { unit = "lb" }
                    UnitChip("kg", unit == "kg") { unit = "kg" }
                }
            }
            item {
                Chip(
                    label = { Text("Start empty") },
                    onClick = { onStart(unit) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (phoneConnected != null) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (phoneConnected) MaterialTheme.colors.primary else Warning,
                                    CircleShape,
                                ),
                        )
                        Text(
                            if (phoneConnected) "Phone connected" else "Not connected",
                            style = MaterialTheme.typography.caption2,
                            color = if (phoneConnected) MaterialTheme.colors.onSurfaceVariant else Warning,
                        )
                    }
                }
            }
            if (templates.isNotEmpty()) {
                item {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
                items(templates.size) { index ->
                    val template = templates[index]
                    val subtitle = template.subtitle()
                    Chip(
                        label = { Text(template.name, maxLines = 1) },
                        secondaryLabel = if (subtitle.isEmpty()) null else {
                            { Text(subtitle, maxLines = 1) }
                        },
                        onClick = { onStartTemplate(template, unit) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        PositionIndicator(scalingLazyListState = listState)
    }
}

private fun WatchTemplate.subtitle(): String {
    val names = exercises.take(2).joinToString(", ") { it.name }
    val extra = exercises.size - 2
    return if (extra > 0) "$names +$extra" else names
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Chip(
        label = { Text(label) },
        onClick = onClick,
        colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
    )
}

@Composable
fun WorkoutScreen(workout: WatchWorkout, repo: WorkoutRepository) {
    var exerciseIndex by remember(workout.id) { mutableIntStateOf(0) }
    var setIndex by remember(workout.id) { mutableIntStateOf(0) }
    var restTarget by remember(workout.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var restEndsAt by remember(workout.id) { mutableLongStateOf(0L) }
    var restLeft by remember(workout.id) { mutableIntStateOf(0) }
    var confirmDiscard by remember(workout.id) { mutableStateOf(false) }

    fun goTo(target: Pair<Int, Int>) {
        exerciseIndex = target.first
        setIndex = target.second
        restTarget = null
    }

    LaunchedEffect(restEndsAt) {
        val target = restTarget ?: return@LaunchedEffect
        while (restTarget == target) {
            val left = ((restEndsAt - System.currentTimeMillis()) / 1000.0).roundToInt().coerceAtLeast(0)
            restLeft = left
            if (System.currentTimeMillis() >= restEndsAt) {
                if (restTarget == target) goTo(target)
                break
            }
            delay(250)
        }
    }

    // A set was finished on the phone (or its rest changed or was skipped there). Mirror that rest here.
    LaunchedEffect(workout.id, workout.restEndsAt) {
        val endsAt = workout.restEndsAt
        if (endsAt == null) {
            restTarget?.let(::goTo)
            return@LaunchedEffect
        }
        if (endsAt <= System.currentTimeMillis()) return@LaunchedEffect
        val target = restTarget
            ?: firstUnfinishedSet(workout)
            ?: (exerciseIndex to setIndex)
        restTarget = target
        restEndsAt = endsAt
        restLeft = ((endsAt - System.currentTimeMillis()) / 1000.0).roundToInt().coerceAtLeast(0)
    }

    if (workout.exercises.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(workout.name, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            WorkoutClock(workout)
            Spacer(Modifier.height(8.dp))
            Chip(
                label = { Text(if (workout.pausedAt != null) "Resume" else "Pause") },
                onClick = repo::togglePause,
                colors = ChipDefaults.secondaryChipColors(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add an exercise on your phone",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val exercise = workout.exercises[exerciseIndex.coerceIn(0, workout.exercises.lastIndex)]
    val safeExercise = exerciseIndex.coerceIn(0, workout.exercises.lastIndex)
    val safeSet = setIndex.coerceIn(0, exercise.sets.lastIndex)
    val set = exercise.sets[safeSet]
    val allDone = workout.exercises.all { ex -> ex.sets.all { it.completed } }

    if (confirmDiscard) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Discard workout?", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "It will not be saved.",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Chip(
                label = { Text("Discard") },
                onClick = repo::discardWorkout,
                colors = dangerChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Chip(
                label = { Text("Back") },
                onClick = { confirmDiscard = false },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    if (restTarget != null) {
        val upcoming = restTarget
        val nextExercise = upcoming?.let { workout.exercises.getOrNull(it.first) }
        val nextIndex = upcoming?.second ?: 0
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Rest", style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.onSurfaceVariant)
            Text("$restLeft", style = MaterialTheme.typography.display1)
            if (nextExercise != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    nextExercise.name,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Text(
                    setCaption(nextExercise.sets[nextIndex.coerceIn(0, nextExercise.sets.lastIndex)], nextIndex),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Chip(
                label = { Text("Skip") },
                onClick = { restTarget?.let(::goTo) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        return
    }

    val lastText = formatPrevious(exercise.previous.getOrNull(safeSet), exercise.fields)
    val multipleExercises = workout.exercises.size > 1
    val screenFocus = remember { FocusRequester() }
    var focusedField by remember(exercise.id, safeSet) { mutableIntStateOf(0) }
    var wheel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(exercise.id, safeSet) {
        delay(60)
        runCatching { screenFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                val field = exercise.fields.getOrNull(focusedField) ?: return@onRotaryScrollEvent false
                wheel += event.verticalScrollPixels
                var steps = 0
                while (wheel >= 50f && steps < 8) {
                    wheel -= 50f
                    steps += 1
                }
                while (wheel <= -50f && steps > -8) {
                    wheel += 50f
                    steps -= 1
                }
                if (steps != 0) {
                    repo.updateSet(safeExercise, safeSet) { adjust(it, field, steps, workout.unit) }
                }
                true
            }
            .focusRequester(screenFocus)
            .focusable()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (multipleExercises) {
                            StepArrow(label = "‹", enabled = safeExercise > 0) {
                                exerciseIndex = (safeExercise - 1).coerceAtLeast(0)
                                setIndex = 0
                            }
                        }
                        Text(
                            exercise.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.title3,
                            color = MaterialTheme.colors.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                        if (multipleExercises) {
                            StepArrow(label = "›", enabled = safeExercise < workout.exercises.lastIndex) {
                                exerciseIndex = (safeExercise + 1).coerceAtMost(workout.exercises.lastIndex)
                                setIndex = 0
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WorkoutClock(workout)
                        Text(
                            buildString {
                                append("  ·  ")
                                append(setCaption(set, safeSet))
                                if (multipleExercises) {
                                    append("  ·  ")
                                    append(safeExercise + 1)
                                    append(" of ")
                                    append(workout.exercises.size)
                                }
                            },
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                        )
                    }
                    if (lastText.isNotEmpty()) {
                        Text(
                            "Last $lastText",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (set.record) {
                        Text(
                            "Personal record",
                            style = MaterialTheme.typography.caption2,
                            color = RecordGold,
                            textAlign = TextAlign.Center,
                        )
                    }
        }
        if (exercise.sets.size > 1) {
            Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exercise.sets.forEachIndexed { index, item ->
                            SetDot(
                                label = if (item.completed) "✓" else "${index + 1}",
                                selected = index == safeSet,
                                onClick = { setIndex = index },
                            )
                        }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(MaterialTheme.colors.secondary, RoundedCornerShape(16.dp))
                .padding(vertical = 2.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            exercise.fields.forEachIndexed { index, field ->
                Stepper(
                    label = if (field == "weight") "Weight (${workout.unit})" else fieldLabel(field),
                    value = fieldValue(set, field),
                    focused = focusedField == index,
                    onSelect = {
                        focusedField = index
                        runCatching { screenFocus.requestFocus() }
                    },
                    onChange = { delta ->
                        focusedField = index
                        runCatching { screenFocus.requestFocus() }
                        repo.updateSet(safeExercise, safeSet) { adjust(it, field, delta, workout.unit) }
                    },
                )
            }
        }
        if (!set.completed) {
            Chip(
                        label = { Text("Finish set") },
                        onClick = {
                            val following = nextSet(workout, safeExercise, safeSet)
                            val record = isPersonalRecord(exercise, safeSet, set)
                            repo.updateSet(safeExercise, safeSet) { it.copy(completed = true, record = record) }
                            if (following == null) return@Chip
                            val rest = restBefore(workout, safeExercise, following)
                            if (rest > 0) {
                                restTarget = following
                                restEndsAt = System.currentTimeMillis() + rest * 1000L
                                restLeft = rest
                            } else {
                                goTo(following)
                            }
                        },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
            )
        } else if (allDone) {
            Text(
                "All sets finished",
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompactChip(
                            label = { Text("Add set", maxLines = 1) },
                            onClick = {
                                repo.addSet(safeExercise)
                                setIndex = exercise.sets.size
                            },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                        )
                        CompactChip(
                            label = { Text(if (workout.pausedAt != null) "Resume" else "Pause", maxLines = 1) },
                            onClick = repo::togglePause,
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompactChip(
                            label = { Text("Finish", maxLines = 1) },
                            onClick = repo::finishWorkout,
                            colors = successChipColors(),
                            modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                        )
                        CompactChip(
                            label = { Text("Discard", maxLines = 1) },
                            onClick = { confirmDiscard = true },
                            colors = dangerChipColors(),
                            modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                        )
                    }
        }
    }
}

@Composable
private fun StepArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .width(32.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.title2,
        color = if (enabled) MaterialTheme.colors.onBackground else MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.35f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SetDot(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.secondary, CircleShape)
            .border(
                1.dp,
                if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.2f),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.caption2,
            color = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface,
        )
    }
}

@Composable
private fun WorkoutClock(workout: WatchWorkout) {
    val paused = workout.pausedAt != null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(workout.pausedAt) {
        while (!paused) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsedMs = ((workout.pausedAt ?: now) - workout.startedAt - workout.pausedTotalMs).coerceAtLeast(0)
    Text(
        formatClock(elapsedMs),
        style = MaterialTheme.typography.title3,
        color = if (paused) Warning else MaterialTheme.colors.onSurface,
    )
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .focusProperties { canFocus = false }
            .background(MaterialTheme.colors.primary, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.title3, color = MaterialTheme.colors.onPrimary)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    focused: Boolean,
    onSelect: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val accent = if (focused) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .clickable(onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.caption2, color = accent, maxLines = 1)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton("−") {
                onSelect()
                onChange(-1)
            }
            Text(
                value,
                modifier = Modifier.widthIn(min = 48.dp),
                style = MaterialTheme.typography.title2,
                color = if (focused) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            )
            StepButton("+") {
                onSelect()
                onChange(1)
            }
        }
    }
}

private fun restBefore(workout: WatchWorkout, exerciseIndex: Int, following: Pair<Int, Int>): Int {
    val next = workout.exercises[following.first].sets[following.second]
    if (next.type == "myo") return 15
    if (next.type == "drop") return 0
    val current = workout.exercises[exerciseIndex]
    val nextExercise = workout.exercises[following.first]
    if (current.supersetId != null && current.supersetId == nextExercise.supersetId && following.first != exerciseIndex) return 0
    return current.restSeconds.coerceAtLeast(0)
}

private fun firstUnfinishedSet(workout: WatchWorkout): Pair<Int, Int>? {
    workout.exercises.forEachIndexed { exerciseIndex, exercise ->
        val setIndex = exercise.sets.indexOfFirst { !it.completed }
        if (setIndex >= 0) return exerciseIndex to setIndex
    }
    return null
}

private fun nextSet(workout: WatchWorkout, exerciseIndex: Int, setIndex: Int): Pair<Int, Int>? {
    val exercise = workout.exercises[exerciseIndex]
    if (setIndex + 1 <= exercise.sets.lastIndex) return exerciseIndex to (setIndex + 1)
    for (i in exerciseIndex + 1..workout.exercises.lastIndex) {
        if (workout.exercises[i].sets.isNotEmpty()) return i to 0
    }
    return null
}

private fun isPersonalRecord(exercise: com.sirak.liftlog.wear.model.WatchExercise, setIndex: Int, set: WatchSet): Boolean {
    if (set.type == "warmup") return false
    var bestWeight = exercise.bestWeight
    var bestReps = exercise.bestReps
    for (index in 0 until setIndex) {
        val earlier = exercise.sets[index]
        if (!earlier.completed || earlier.type == "warmup") continue
        val weight = earlier.weight
        if (weight != null && (bestWeight == null || weight > bestWeight)) bestWeight = weight
        val reps = earlier.reps
        if (reps != null && (bestReps == null || reps > bestReps)) bestReps = reps
    }
    val weightRecord = bestWeight != null && set.weight != null && set.weight > bestWeight + 0.05
    val repsRecord = bestReps != null && set.reps != null && set.reps > bestReps
    return weightRecord || repsRecord
}

private fun formatPrevious(previous: com.sirak.liftlog.wear.model.WatchPrevious?, fields: List<String>): String {
    if (previous == null) return ""
    val parts = fields.mapNotNull { field ->
        when (field) {
            "weight" -> previous.weight?.let(::formatNum)
            "reps" -> previous.reps?.toString()
            "duration" -> previous.duration?.let(::formatDuration)
            "distance" -> previous.distance?.let(::formatNum)
            "jumps" -> previous.jumps?.let { "$it jumps" }
            "steps" -> previous.steps?.let { "$it steps" }
            else -> null
        }
    }
    if (parts.isEmpty()) return ""
    return if (fields.firstOrNull() == "weight" && fields.getOrNull(1) == "reps" && parts.size >= 2) {
        "${parts[0]} × ${parts[1]}"
    } else {
        parts.joinToString(" · ")
    }
}

private fun setCaption(set: WatchSet, index: Int): String {
    val label = when (set.type) {
        "warmup" -> "Warm-up"
        "drop" -> "Drop set"
        "failure" -> "Failure"
        "myo" -> "Myo-rep"
        else -> "Set ${index + 1}"
    }
    return if (set.completed) "$label · done" else label
}

private fun fieldLabel(field: String) = when (field) {
    "weight" -> "Weight"
    "reps" -> "Reps"
    "duration" -> "Time"
    "distance" -> "Distance"
    "jumps" -> "Jumps"
    "steps" -> "Steps"
    else -> field
}

private fun fieldValue(set: WatchSet, field: String): String = when (field) {
    "weight" -> set.weight?.let(::formatNum) ?: "—"
    "reps" -> set.reps?.toString() ?: "—"
    "duration" -> set.duration?.let(::formatDuration) ?: "—"
    "distance" -> set.distance?.let(::formatNum) ?: "—"
    "jumps" -> set.jumps?.toString() ?: "—"
    "steps" -> set.steps?.toString() ?: "—"
    else -> "—"
}

private fun adjust(set: WatchSet, field: String, delta: Int, unit: String): WatchSet = when (field) {
    "weight" -> {
        val weight = set.weight
        val step = if (unit == "lb") 5.0 else 2.5
        val start = if (unit == "lb") 45.0 else 20.0
        val next = when {
            weight == null && delta > 0 -> start
            weight == null -> null
            else -> (weight + delta * step).coerceAtLeast(0.0)
        }
        set.copy(weight = next)
    }
    "reps" -> set.copy(reps = stepInt(set.reps, delta, 1))
    "jumps" -> set.copy(jumps = stepInt(set.jumps, delta, 1))
    "steps" -> set.copy(steps = stepInt(set.steps, delta, 1))
    "duration" -> set.copy(duration = stepInt(set.duration, delta * 15, 15))
    "distance" -> {
        val distance = set.distance
        val next = when {
            distance == null && delta > 0 -> 0.1
            distance == null -> null
            else -> ((distance + delta * 0.1).coerceAtLeast(0.0) * 10).roundToInt() / 10.0
        }
        set.copy(distance = next)
    }
    else -> set
}

private fun stepInt(current: Int?, delta: Int, start: Int): Int? = when {
    current == null && delta > 0 -> start
    current == null -> null
    else -> (current + delta).coerceAtLeast(0)
}

private fun formatNum(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private fun formatClock(elapsedMs: Long): String {
    val total = (elapsedMs / 1000).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes == 0) "${rest}s" else "$minutes:${rest.toString().padStart(2, '0')}"
}
