package com.maryumcenter.iqraaudioplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Owns the chosen root directory, the ordered track list built from it, and the
 * "where did I stop" bookmark.
 *
 * Scanning a large tree over the Storage Access Framework takes seconds, so the
 * result is cached to disk and reloaded on launch; a rescan is explicit.
 */
class Library private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cacheFile = File(appContext.filesDir, "tracks.json")

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _rootUri = MutableStateFlow(prefs.getString(KEY_ROOT, null)?.toUri())
    val rootUri: StateFlow<Uri?> = _rootUri.asStateFlow()

    private val _rootLabel = MutableStateFlow(prefs.getString(KEY_ROOT_LABEL, null))
    val rootLabel: StateFlow<String?> = _rootLabel.asStateFlow()

    // Starts true when a root is already configured: the cached list is read
    // asynchronously, and the UI must not flash "no audio files" in the meantime.
    private val _scanning = MutableStateFlow(prefs.getString(KEY_ROOT, null) != null)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /**
     * Whether the queue wraps from the last file back to the first. Persisted so
     * a shared tablet keeps the setting a teacher chose once.
     */
    var repeatAll: Boolean
        get() = prefs.getBoolean(KEY_REPEAT, false)
        set(value) = prefs.edit { putBoolean(KEY_REPEAT, value) }

    val savedTrackIndex: Int get() = prefs.getInt(KEY_INDEX, 0)
    val savedPositionMs: Long get() = prefs.getLong(KEY_POSITION, 0L)

    /**
     * Records the folder the user picked, takes a long-lived read grant on it and
     * scans it. Any previous bookmark is dropped: it referred to a different tree.
     */
    suspend fun setRoot(treeUri: Uri) {
        val previousRoot = _rootUri.value
        val label = withContext(Dispatchers.IO) {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not persist permission for $treeUri", e)
            }
            // The system caps how many trees an app may hold on to, so hand the
            // old one back rather than leaking a grant on every folder change.
            if (previousRoot != null && previousRoot != treeUri) {
                try {
                    appContext.contentResolver.releasePersistableUriPermission(
                        previousRoot,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not release permission for $previousRoot", e)
                }
            }
            describe(treeUri)
        }

        prefs.edit {
            putString(KEY_ROOT, treeUri.toString())
            putString(KEY_ROOT_LABEL, label)
            putInt(KEY_INDEX, 0)
            putLong(KEY_POSITION, 0L)
        }

        _rootUri.value = treeUri
        _rootLabel.value = label
        _tracks.value = emptyList()
        rescan()
    }

    /** Reloads the cached track list, scanning from scratch if there is no cache. */
    suspend fun load() {
        val root = _rootUri.value
        if (root == null || _tracks.value.isNotEmpty()) {
            _scanning.value = false
            return
        }
        _scanning.value = true
        try {
            val cached = withContext(Dispatchers.IO) { readCache(root) }
            if (cached != null) _tracks.value = cached else performScan(root)
        } finally {
            _scanning.value = false
        }
    }

    suspend fun rescan() {
        val root = _rootUri.value ?: return
        if (_scanning.value) return
        _scanning.value = true
        try {
            performScan(root)
        } finally {
            _scanning.value = false
        }
    }

    private suspend fun performScan(root: Uri) {
        val found = withContext(Dispatchers.IO) { MediaScanner.scan(appContext, root) }
        _tracks.value = found
        withContext(Dispatchers.IO) { writeCache(root, found) }
    }

    fun saveProgress(trackIndex: Int, positionMs: Long) {
        prefs.edit {
            putInt(KEY_INDEX, trackIndex)
            putLong(KEY_POSITION, positionMs)
        }
    }

    // --- cache -------------------------------------------------------------

    private fun readCache(root: Uri): List<Track>? {
        if (!cacheFile.exists()) return null
        return try {
            val json = JSONObject(cacheFile.readText())
            if (json.optString("root") != root.toString()) return null
            val array = json.getJSONArray("tracks")
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                Track(
                    uri = item.getString("uri"),
                    name = item.getString("name"),
                    folder = item.optString("folder"),
                )
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Discarding unreadable track cache", e)
            null
        }
    }

    private fun writeCache(root: Uri, tracks: List<Track>) {
        try {
            val array = JSONArray()
            for (track in tracks) {
                array.put(
                    JSONObject()
                        .put("uri", track.uri)
                        .put("name", track.name)
                        .put("folder", track.folder),
                )
            }
            cacheFile.writeText(
                JSONObject().put("root", root.toString()).put("tracks", array).toString(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not write track cache", e)
        }
    }

    /** Best-effort human readable name for the picked tree. */
    private fun describe(treeUri: Uri): String {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        try {
            appContext.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { return it }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read folder name", e)
        }
        return DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':')
    }

    companion object {
        private const val TAG = "Library"
        private const val PREFS = "folder_player"
        private const val KEY_ROOT = "root_uri"
        private const val KEY_ROOT_LABEL = "root_label"
        private const val KEY_INDEX = "track_index"
        private const val KEY_POSITION = "track_position"
        private const val KEY_REPEAT = "repeat_all"

        @Volatile
        private var instance: Library? = null

        fun get(context: Context): Library =
            instance ?: synchronized(this) {
                instance ?: Library(context).also { instance = it }
            }
    }
}
