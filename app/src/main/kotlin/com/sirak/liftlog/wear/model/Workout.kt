package com.sirak.liftlog.wear.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WatchSet(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "normal",
    val weight: Double? = null,
    val reps: Int? = null,
    val duration: Int? = null,
    val distance: Double? = null,
    val jumps: Int? = null,
    val steps: Int? = null,
    val completed: Boolean = false,
    val record: Boolean = false,
)

data class WatchPrevious(
    val weight: Double? = null,
    val reps: Int? = null,
    val duration: Int? = null,
    val distance: Double? = null,
    val jumps: Int? = null,
    val steps: Int? = null,
)

data class WatchExercise(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fields: List<String> = listOf("weight", "reps"),
    val sets: List<WatchSet> = listOf(WatchSet()),
    val restSeconds: Int = 0,
    val supersetId: String? = null,
    val previous: List<WatchPrevious> = emptyList(),
    val bestWeight: Double? = null,
    val bestReps: Int? = null,
)

data class WatchWorkout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startedAt: Long,
    val unit: String = "lb",
    val exercises: List<WatchExercise>,
    val ended: Boolean = false,
    val discarded: Boolean = false,
    val pausedAt: Long? = null,
    val pausedTotalMs: Long = 0,
    val revision: Long = 0,
    /** When the phone's rest countdown ends (epoch ms), or null when it is not resting. */
    val restEndsAt: Long? = null,
)

fun defaultWorkoutName(now: Long = System.currentTimeMillis()): String {
    val hour = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Night Workout"
        hour < 12 -> "Morning Workout"
        hour < 17 -> "Afternoon Workout"
        hour < 21 -> "Evening Workout"
        else -> "Night Workout"
    }
}

fun WatchWorkout.toJson(): String = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("startedAt", startedAt)
    put("unit", unit)
    put("ended", ended)
    put("discarded", discarded)
    putNullable("pausedAt", pausedAt)
    put("pausedTotalMs", pausedTotalMs)
    put("revision", revision)
    putNullable("restEndsAt", restEndsAt)
    put("exercises", JSONArray().apply {
        exercises.forEach { exercise ->
            put(JSONObject().apply {
                put("id", exercise.id)
                put("name", exercise.name)
                put("fields", JSONArray(exercise.fields))
                put("restSeconds", exercise.restSeconds)
                putNullable("supersetId", exercise.supersetId)
                putNullable("bestWeight", exercise.bestWeight)
                putNullable("bestReps", exercise.bestReps)
                put("previous", JSONArray().apply {
                    exercise.previous.forEach { previous ->
                        put(JSONObject().apply {
                            putNullable("weight", previous.weight)
                            putNullable("reps", previous.reps)
                            putNullable("duration", previous.duration)
                            putNullable("distance", previous.distance)
                            putNullable("jumps", previous.jumps)
                            putNullable("steps", previous.steps)
                        })
                    }
                })
                put("sets", JSONArray().apply {
                    exercise.sets.forEach { set -> put(set.toJson()) }
                })
            })
        }
    })
}.toString()

private fun WatchSet.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("type", type)
    putNullable("weight", weight)
    putNullable("reps", reps)
    putNullable("duration", duration)
    putNullable("distance", distance)
    putNullable("jumps", jumps)
    putNullable("steps", steps)
    put("completed", completed)
    put("record", record)
}

fun parseWorkout(json: String): WatchWorkout? = runCatching {
    val obj = JSONObject(json)
    val exercises = obj.getJSONArray("exercises").let { arr ->
        List(arr.length()) { index -> arr.getJSONObject(index).toExercise() }
    }
    WatchWorkout(
        id = obj.optString("id", UUID.randomUUID().toString()),
        name = obj.optString("name", "Workout"),
        startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
        unit = obj.optString("unit", "lb").ifBlank { "lb" },
        exercises = exercises,
        ended = obj.optBoolean("ended", false),
        discarded = obj.optBoolean("discarded", false),
        pausedAt = obj.optLongOrNull("pausedAt"),
        pausedTotalMs = obj.optLong("pausedTotalMs", 0).coerceAtLeast(0),
        revision = obj.optLong("revision", 0).coerceAtLeast(0),
        restEndsAt = obj.optLongOrNull("restEndsAt"),
    )
}.getOrNull()

private fun JSONObject.toExercise(): WatchExercise {
    val fieldArr = optJSONArray("fields")
    val fields = if (fieldArr == null || fieldArr.length() == 0) {
        listOf("weight", "reps")
    } else {
        List(fieldArr.length()) { fieldArr.getString(it) }
    }
    val setArr = getJSONArray("sets")
    val sets = if (setArr.length() == 0) {
        listOf(WatchSet())
    } else {
        List(setArr.length()) { setArr.getJSONObject(it).toSet() }
    }
    return WatchExercise(
        id = optString("id", UUID.randomUUID().toString()),
        name = optString("name", "Exercise"),
        fields = fields,
        sets = sets,
        restSeconds = optInt("restSeconds", 0).coerceAtLeast(0),
        supersetId = optString("supersetId", "").ifBlank { null },
        previous = optJSONArray("previous")?.let { arr ->
            List(arr.length()) { arr.getJSONObject(it).toPrevious() }
        } ?: emptyList(),
        bestWeight = optDoubleOrNull("bestWeight"),
        bestReps = optIntOrNull("bestReps"),
    )
}

private fun JSONObject.toSet(): WatchSet = WatchSet(
    id = optString("id", UUID.randomUUID().toString()),
    type = optString("type", "normal"),
    weight = optDoubleOrNull("weight"),
    reps = optIntOrNull("reps"),
    duration = optIntOrNull("duration"),
    distance = optDoubleOrNull("distance"),
    jumps = optIntOrNull("jumps"),
    steps = optIntOrNull("steps"),
    completed = optBoolean("completed", false),
    record = optBoolean("record", false),
)

private fun JSONObject.toPrevious(): WatchPrevious = WatchPrevious(
    weight = optDoubleOrNull("weight"),
    reps = optIntOrNull("reps"),
    duration = optIntOrNull("duration"),
    distance = optDoubleOrNull("distance"),
    jumps = optIntOrNull("jumps"),
    steps = optIntOrNull("steps"),
)

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)
