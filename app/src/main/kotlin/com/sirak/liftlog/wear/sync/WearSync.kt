package com.sirak.liftlog.wear.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import com.sirak.liftlog.wear.WearApp
import com.sirak.liftlog.wear.data.WorkoutRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sirak.liftlog.wear.model.WatchWorkout
import com.sirak.liftlog.wear.model.parseWorkout
import com.sirak.liftlog.wear.model.startRequest
import com.sirak.liftlog.wear.model.toJson
import com.sirak.liftlog.wear.ui.AppPalette
import com.sirak.liftlog.wear.ui.savePalette

/**
 * The watch publishes the in-progress workout at `/liftlog/active` and a finished
 * workout at `/liftlog/finished/<id>`. The phone can read those items with the Wearable Data Layer.
 */
object WearSync {
    private const val TAG = "LiftLogSync"
    private var listening = false

    /** Null until the first check, so the start screen does not flash "Not connected". */
    var phoneConnected by mutableStateOf<Boolean?>(null)
        private set

    /** Reads the phone's live workout. A change event is not sent again for an item that is already there. */
    fun pullActive(context: Context, repo: WorkoutRepository) {
        Thread {
            runCatching {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 8, TimeUnit.SECONDS)
                Log.i(TAG, "connected nodes=${nodes.size} ${nodes.joinToString { it.displayName }}")
                val connected = nodes.isNotEmpty()
                Handler(Looper.getMainLooper()).post { phoneConnected = connected }
                if (repo.workout == null) {
                    val local = Tasks.await(Wearable.getNodeClient(context).localNode, 8, TimeUnit.SECONDS)
                    if (repo.workout == null) {
                        Tasks.await(
                            Wearable.getDataClient(context).deleteDataItems(Uri.parse("wear://${local.id}/liftlog/active")),
                            8,
                            TimeUnit.SECONDS,
                        )
                        Log.i(TAG, "removed stale watch workout item")
                    }
                }
                val buffer = Tasks.await(
                    Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*/liftlog/active")),
                    8,
                    TimeUnit.SECONDS,
                )
                try {
                    Log.i(TAG, "pull active items=${buffer.count}")
                    var json: String? = null
                    var updatedAt = Long.MIN_VALUE
                    for (index in 0 until buffer.count) {
                        val map = DataMapItem.fromDataItem(buffer[index]).dataMap
                        val source = map.getString("source")
                        Log.i(TAG, "pull item source=$source bytes=${map.getString("workout")?.length ?: 0}")
                        if (source == "watch") continue
                        val workout = map.getString("workout") ?: continue
                        val at = if (map.containsKey("updatedAt")) map.getLong("updatedAt") else 0L
                        if (json == null || at >= updatedAt) {
                            json = workout
                            updatedAt = at
                        }
                    }
                    val found = json
                    if (found == null) {
                        Log.i(TAG, "pull found no phone workout")
                        return@runCatching
                    }
                    Handler(Looper.getMainLooper()).post {
                        val parsed = parseWorkout(found)
                        if (parsed == null) {
                            Log.w(TAG, "pull could not read phone workout")
                            return@post
                        }
                        if (parsed.ended || parsed.discarded) {
                            Log.i(TAG, "pull phone workout ${parsed.id} is closed")
                            if (repo.workout?.id == parsed.id) repo.clearFromRemote()
                            return@post
                        }
                        repo.replaceFromRemote(found)
                    }
                } finally {
                    buffer.release()
                }
            }.onFailure { error ->
                Log.w(TAG, "pull active failed", error)
            }
        }.start()
    }

    /** Keeps receiving phone workouts while this process is alive, not only via the listener service. */
    fun listen(context: Context, repo: WorkoutRepository) {
        if (listening) return
        listening = true
        Wearable.getDataClient(context).addListener(
            DataClient.OnDataChangedListener { events ->
                try {
                    dispatch(context, repo, events)
                } finally {
                    events.release()
                }
            },
            Uri.parse("wear://*/liftlog"),
            DataClient.FILTER_PREFIX,
        )
    }

    fun pushActive(context: Context, json: String) {
        put(context, "/liftlog/active") {
            putString("source", "watch")
            putString("workout", json)
            putLong("updatedAt", System.currentTimeMillis())
        }
    }

    fun clearActive(context: Context) {
        runCatching {
            Wearable.getDataClient(context).deleteDataItems(Uri.parse("wear://*/liftlog/active"))
        }
    }

    /** Asks the phone to open this template with the same exercise and set ids. */
    fun requestTemplateStart(context: Context, templateId: String, workout: WatchWorkout) {
        val request = workout.startRequest(templateId)
        put(context, "/liftlog/start-template") {
            putString("source", "watch")
            putString("request", request)
            putLong("updatedAt", System.currentTimeMillis())
        }
        runCatching {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 8, TimeUnit.SECONDS)
            if (nodes.isEmpty()) {
                Log.w(TAG, "template start has no connected phone")
                return@runCatching
            }
            val client = Wearable.getMessageClient(context)
            val bytes = request.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                Tasks.await(client.sendMessage(node.id, "/liftlog/start-template", bytes), 8, TimeUnit.SECONDS)
                Log.i(TAG, "template start message to ${node.displayName} bytes=${bytes.size}")
            }
        }.onFailure { error ->
            Log.w(TAG, "template start message failed", error)
        }
    }

    fun pullTemplates(context: Context, repo: WorkoutRepository) {
        Thread {
            runCatching {
                val buffer = Tasks.await(
                    Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*/liftlog/templates")),
                    8,
                    TimeUnit.SECONDS,
                )
                try {
                    var catalog: String? = null
                    var updatedAt = Long.MIN_VALUE
                    for (index in 0 until buffer.count) {
                        val map = DataMapItem.fromDataItem(buffer[index]).dataMap
                        if (map.getString("source") == "watch") continue
                        val json = map.getString("catalog") ?: continue
                        val at = if (map.containsKey("updatedAt")) map.getLong("updatedAt") else 0L
                        if (catalog == null || at >= updatedAt) {
                            catalog = json
                            updatedAt = at
                        }
                    }
                    catalog?.let { json ->
                        Handler(Looper.getMainLooper()).post { repo.replaceTemplates(json) }
                    }
                } finally {
                    buffer.release()
                }
            }.onFailure { error ->
                Log.w(TAG, "pull templates failed", error)
            }
        }.start()
    }

    fun refreshPhoneLink(context: Context) {
        Thread {
            val connected = runCatching {
                Tasks.await(Wearable.getNodeClient(context).connectedNodes, 8, TimeUnit.SECONDS).isNotEmpty()
            }.getOrNull() ?: return@Thread
            Handler(Looper.getMainLooper()).post { phoneConnected = connected }
        }.start()
    }

    fun pullTheme(context: Context) {
        Thread {
            runCatching {
                val buffer = Tasks.await(
                    Wearable.getDataClient(context).getDataItems(Uri.parse("wear://*/liftlog/theme")),
                )
                try {
                    for (index in 0 until buffer.count) {
                        val map = DataMapItem.fromDataItem(buffer[index]).dataMap
                        if (map.getString("source") == "watch") continue
                        applyTheme(context, map.getString("text"), map.getString("header"), map.getString("accent"))
                    }
                } finally {
                    buffer.release()
                }
            }
        }.start()
    }

    fun pushFinished(context: Context, workout: WatchWorkout) {
        put(context, "/liftlog/finished/${workout.id}") {
            putString("source", "watch")
            putString("workout", workout.toJson())
            putLong("updatedAt", System.currentTimeMillis())
        }
    }

    private fun put(context: Context, path: String, fill: com.google.android.gms.wearable.DataMap.() -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Thread { putNow(context, path, fill) }.start()
            return
        }
        putNow(context, path, fill)
    }

    private fun putNow(context: Context, path: String, fill: com.google.android.gms.wearable.DataMap.() -> Unit) {
        runCatching {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.fill()
            }.asPutDataRequest().setUrgent()
            Tasks.await(Wearable.getDataClient(context).putDataItem(request), 8, TimeUnit.SECONDS)
            Log.i(TAG, "put $path")
        }.onFailure { error ->
            Log.w(TAG, "put $path failed", error)
        }
    }
}

private fun applyTheme(context: Context, text: String?, header: String?, accent: String?) {
    Handler(Looper.getMainLooper()).post {
        AppPalette.apply(text, header, accent)
        savePalette(context)
    }
}

private fun dispatch(context: Context, repo: WorkoutRepository, events: DataEventBuffer) {
    for (event in events) {
        val path = event.dataItem.uri.path ?: continue
        if (path == "/liftlog/templates" && event.type == DataEvent.TYPE_CHANGED) {
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            if (map.getString("source") == "watch") continue
            val json = map.getString("catalog") ?: continue
            Handler(Looper.getMainLooper()).post { repo.replaceTemplates(json) }
            continue
        }
        if (path == "/liftlog/theme" && event.type == DataEvent.TYPE_CHANGED) {
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            if (map.getString("source") == "watch") continue
            applyTheme(context, map.getString("text"), map.getString("header"), map.getString("accent"))
            continue
        }
        if (path != "/liftlog/active") continue
        if (event.type == DataEvent.TYPE_DELETED) {
            // Ignore a delete that arrives after this watch has already opened a workout.
            if (repo.workout == null) Handler(Looper.getMainLooper()).post { repo.clearFromRemote() }
            continue
        }
        if (event.type != DataEvent.TYPE_CHANGED) continue
        val map = DataMapItem.fromDataItem(event.dataItem).dataMap
        if (map.getString("source") == "watch") continue
        val json = map.getString("workout") ?: continue
        Handler(Looper.getMainLooper()).post { repo.replaceFromRemote(json) }
    }
}

class WorkoutListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        try {
            val app = application as WearApp
            dispatch(app, app.repo, events)
        } finally {
            events.release()
        }
    }
}
