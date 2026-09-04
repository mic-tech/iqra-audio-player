package com.maryumcenter.iqraaudioplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * Walks a document tree picked with ACTION_OPEN_DOCUMENT_TREE and returns every
 * audio file underneath it, sorted alphabetically.
 *
 * This queries DocumentsContract directly rather than going through
 * DocumentFile: DocumentFile issues one provider query per file just to read a
 * name, which is painfully slow on libraries with thousands of tracks.
 */
object MediaScanner {

    private const val TAG = "MediaScanner"

    /** Extensions we hand to ExoPlayer. MP3 is the target; the rest come free. */
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "mp4", "mka",
    )

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    fun scan(context: Context, treeUri: Uri): List<Track> {
        val resolver = context.contentResolver
        val tracks = ArrayList<Track>()

        // Depth-first walk over an explicit stack of (documentId, path relative to root).
        val pending = ArrayDeque<Pair<String, String>>()
        pending.addLast(DocumentsContract.getTreeDocumentId(treeUri) to "")
        val seen = HashSet<String>()

        while (pending.isNotEmpty()) {
            val (documentId, relativePath) = pending.removeLast()
            if (!seen.add(documentId)) continue

            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            try {
                resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(0) ?: continue
                        val name = cursor.getString(1) ?: continue
                        val mimeType = cursor.getString(2)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val childPath =
                                if (relativePath.isEmpty()) name else "$relativePath/$name"
                            pending.addLast(childId to childPath)
                        } else if (isAudio(name, mimeType)) {
                            tracks += Track(
                                uri = DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, childId)
                                    .toString(),
                                name = name,
                                folder = relativePath,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // A single unreadable subdirectory shouldn't abort the whole scan.
                Log.w(TAG, "Skipping unreadable directory: $relativePath", e)
            }
        }

        tracks.sortWith(TrackOrder)
        return tracks
    }

    private fun isAudio(name: String, mimeType: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in AUDIO_EXTENSIONS) return true
        // Some providers report a usable MIME type for extension-less files.
        return extension.isEmpty() && mimeType != null && mimeType.startsWith("audio/")
    }
}
