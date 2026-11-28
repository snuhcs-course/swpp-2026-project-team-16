package com.example.runtime.data.run

import android.content.Context
import android.util.AtomicFile
import com.example.runtime.data.remote.ApiClient
import com.example.runtime.data.remote.RunPlan
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Serializable
data class RunSnapshot(
    val owner: String,
    val routeId: Int? = null,
    val temporaryRouteId: Int? = null,
    val groupId: Int? = null,
    val plan: RunPlan? = null,
    val isFree: Boolean = false,
    val startName: String = "",
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0L,
    val splitTimes: List<Long> = emptyList(),
    val offRouteCount: Int = 0,
    val offRouteSeconds: Long = 0L,
    val path: List<List<Double>> = emptyList(),
    val startedAtMillis: Long = 0L,
    val savedAtMillis: Long = 0L,
)

interface RunSnapshotStore {
    fun load(): RunSnapshot?
    fun save(snapshot: RunSnapshot)
    fun clear()
}

class InMemoryRunSnapshotStore : RunSnapshotStore {
    @Volatile
    private var snapshot: RunSnapshot? = null

    override fun load(): RunSnapshot? = snapshot

    override fun save(snapshot: RunSnapshot) {
        this.snapshot = snapshot
    }

    override fun clear() {
        snapshot = null
    }
}

class FileRunSnapshotStore(
    directory: File,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : RunSnapshotStore {
    private val file = AtomicFile(File(directory, FILE_NAME))

    override fun load(): RunSnapshot? = executor.submit<RunSnapshot?> {
        runCatching {
            val text = file.readFully().toString(Charsets.UTF_8)
            ApiClient.json.decodeFromString(RunSnapshot.serializer(), text)
        }.getOrNull()
    }.get()

    override fun save(snapshot: RunSnapshot) {
        executor.execute {
            val bytes = ApiClient.json.encodeToString(RunSnapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
            val stream = runCatching { file.startWrite() }.getOrNull() ?: return@execute
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (e: Exception) {
                file.failWrite(stream)
            }
        }
    }

    override fun clear() {
        executor.execute { file.delete() }
    }

    private companion object {
        const val FILE_NAME = "run_snapshot.json"
    }
}

object RunStorage {
    @Volatile
    var snapshots: RunSnapshotStore = InMemoryRunSnapshotStore()
        private set

    fun initialize(context: Context) {
        snapshots = FileRunSnapshotStore(context.applicationContext.filesDir)
    }
}
