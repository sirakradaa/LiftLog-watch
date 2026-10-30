package com.sirak.liftlog.wear.data

import android.content.Context
import android.util.Log
import com.sirak.liftlog.wear.model.WatchExercise
import com.sirak.liftlog.wear.model.WatchSet
import com.sirak.liftlog.wear.model.WatchTemplate
import com.sirak.liftlog.wear.model.WatchWorkout
import com.sirak.liftlog.wear.model.defaultWorkoutName
import com.sirak.liftlog.wear.model.parseTemplateCatalog
import com.sirak.liftlog.wear.model.parseWorkout
import com.sirak.liftlog.wear.model.toJson
import com.sirak.liftlog.wear.model.toWorkout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sirak.liftlog.wear.sync.WearSync
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class WorkoutRepository(private val context: Context) {
    private val activeFile = File(context.filesDir, "active-workout.json")
    private val historyFile = File(context.filesDir, "history.json")
    private val templateFile = File(context.filesDir, "templates.json")
    private val io = Executors.newSingleThreadExecutor()
    var workout by mutableStateOf<WatchWorkout?>(null)
        private set
    var templates by mutableStateOf<List<WatchTemplate>>(emptyList())
        private set
    private var templateUnit: String? = null

    fun load() {
        if (activeFile.exists()) {
            workout = runCatching { parseWorkout(activeFile.readText()) }.getOrNull()
        }
        if (templateFile.exists()) {
            applyCatalog(templateFile.readText())
        }
    }

    fun replaceTemplates(json: String) {
        if (!applyCatalog(json)) return
        val empty = templates.isEmpty()
        io.execute {
            if (empty) {
                if (templateFile.exists()) templateFile.delete()
            } else {
                templateFile.writeText(json)
            }
        }
    }

    /** Opens a phone template on the watch and asks the phone to open the same one. */
    fun startTemplate(template: WatchTemplate, unit: String) {
        val now = System.currentTimeMillis()
        val workout = template.toWorkout(templateUnit ?: unit, now)
        save(workout)
        // The data-layer call cannot run on the tap thread. It was failing there, so the phone never opened the template.
        io.execute { WearSync.requestTemplateStart(context, template.id, workout) }
    }

    fun start(unit: String, exerciseName: String = "Exercise") {
        val now = System.currentTimeMillis()
        save(
            WatchWorkout(
                name = defaultWorkoutName(now),
                startedAt = now,
                unit = if (unit == "kg") "kg" else "lb",
                exercises = listOf(WatchExercise(name = exerciseName)),
            ),
        )
    }

    fun updateSet(exerciseIndex: Int, setIndex: Int, block: (WatchSet) -> WatchSet) {
        val current = workout ?: return
        val exercise = current.exercises.getOrNull(exerciseIndex) ?: return
        val sets = exercise.sets.toMutableList()
        val set = sets.getOrNull(setIndex) ?: return
        sets[setIndex] = block(set)
        save(current.replaceExercise(exerciseIndex, exercise.copy(sets = sets)))
    }

    fun togglePause() {
        val current = workout ?: return
        val now = System.currentTimeMillis()
        val next = if (current.pausedAt != null) {
            current.copy(
                pausedTotalMs = current.pausedTotalMs + (now - current.pausedAt),
                pausedAt = null,
            )
        } else {
            current.copy(pausedAt = now)
        }
        save(next)
    }

    fun addSet(exerciseIndex: Int) {
        val current = workout ?: return
        val exercise = current.exercises.getOrNull(exerciseIndex) ?: return
        val last = exercise.sets.lastOrNull()
        val type = if (last?.type == "warmup" || last?.type == "myo" || last?.type == "drop") last.type else "normal"
        val added = WatchSet(
            type = type,
            weight = last?.weight,
            reps = last?.reps,
            duration = last?.duration,
            distance = last?.distance,
            jumps = last?.jumps,
            steps = last?.steps,
        )
        save(current.replaceExercise(exerciseIndex, exercise.copy(sets = exercise.sets + added)))
    }

    fun finishWorkout() {
        val current = workout ?: return
        val finished = current.copy(
            ended = true,
            exercises = current.exercises.map { exercise ->
                exercise.copy(sets = exercise.sets.map { set ->
                    if (set.completed || set.hasLoggedValue()) set.copy(completed = true) else set
                })
            },
        )
        workout = null
        io.execute {
            if (activeFile.exists()) activeFile.delete()
            val history = if (historyFile.exists()) {
                runCatching { JSONArray(historyFile.readText()) }.getOrDefault(JSONArray())
            } else {
                JSONArray()
            }
            history.put(JSONObject(finished.toJson()))
            historyFile.writeText(history.toString())
            // Leave the data item in place until the phone saves it and deletes it.
            WearSync.pushActive(context, finished.toJson())
            WearSync.pushFinished(context, finished)
        }
    }

    /** Closes the live workout on the watch and the phone without saving it. */
    fun discardWorkout() {
        val current = workout ?: return
        workout = null
        io.execute {
            if (activeFile.exists()) activeFile.delete()
            WearSync.pushActive(context, current.copy(discarded = true).toJson())
        }
    }

    /** Phone deleted the live workout. Do not write that deletion back. */
    fun clearFromRemote() {
        workout = null
        io.execute { if (activeFile.exists()) activeFile.delete() }
    }

    /** Phone pushed a workout. Do not echo it back onto the data layer. */
    fun replaceFromRemote(json: String) {
        val parsed = parseWorkout(json)
        if (parsed == null) {
            Log.w(TAG, "phone workout could not be read")
            return
        }
        val current = workout
        if (parsed.ended || parsed.discarded) {
            // Finished or discarded on the phone. Close it here only if it is the same workout.
            if (current?.id == parsed.id) {
                Log.i(TAG, "phone closed workout ${parsed.id}")
                clearFromRemote()
            }
            return
        }
        if (current != null && current.id != parsed.id && current.startedAt > parsed.startedAt) {
            Log.i(TAG, "kept newer local workout ${current.id} over phone ${parsed.id}")
            return
        }
        if (current != null && current.id == parsed.id && parsed.revision < current.revision) {
            val merged = mergeRemoteDetails(current, parsed)
            if (merged.toJson() == current.toJson()) {
                Log.i(TAG, "kept local workout ${current.id} rev ${current.revision} over phone rev ${parsed.revision}")
                return
            }
            Log.i(TAG, "kept local sets for ${current.id}, took phone history")
            workout = merged
            io.execute { activeFile.writeText(merged.toJson()) }
            return
        }
        if (parsed.toJson() == current?.toJson()) return
        Log.i(TAG, "showing phone workout ${parsed.id} exercises=${parsed.exercises.size}")
        workout = parsed
        io.execute { activeFile.writeText(parsed.toJson()) }
    }

    private fun applyCatalog(json: String): Boolean {
        val catalog = parseTemplateCatalog(json)
        if (catalog == null) {
            Log.w(TAG, "template list could not be read")
            return false
        }
        templateUnit = catalog.unit
        templates = catalog.templates.take(5)
        return true
    }

    private fun save(workout: WatchWorkout) {
        val next = workout.copy(revision = workout.revision + 1)
        this.workout = next
        val json = next.toJson()
        io.execute {
            activeFile.writeText(json)
            WearSync.pushActive(context, json)
        }
    }
}

private const val TAG = "LiftLogSync"

private fun WatchSet.hasLoggedValue(): Boolean =
    weight != null || reps != null || duration != null || distance != null || jumps != null || steps != null

/** Phone history and names, without letting an older snapshot replace sets already logged here. */
private fun mergeRemoteDetails(local: WatchWorkout, remote: WatchWorkout): WatchWorkout {
    val exercises = local.exercises.map { exercise ->
        val incoming = remote.exercises.find { it.id == exercise.id } ?: return@map exercise
        exercise.copy(
            name = incoming.name,
            fields = incoming.fields,
            restSeconds = incoming.restSeconds,
            supersetId = incoming.supersetId,
            previous = incoming.previous,
            bestWeight = incoming.bestWeight,
            bestReps = incoming.bestReps,
        )
    }
    return local.copy(name = remote.name, exercises = exercises, restEndsAt = remote.restEndsAt)
}

private fun WatchWorkout.replaceExercise(index: Int, exercise: WatchExercise): WatchWorkout {
    val exercises = exercises.toMutableList()
    exercises[index] = exercise
    return copy(exercises = exercises)
}
