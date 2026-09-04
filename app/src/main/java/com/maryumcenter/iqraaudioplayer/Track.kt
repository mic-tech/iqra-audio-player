package com.maryumcenter.iqraaudioplayer

/**
 * One playable audio file found under the root directory.
 *
 * [folder] is the path of the containing directory relative to the root ("" for
 * files sitting directly in the root). It is kept separate from [name] so the
 * playlist can be ordered directory-by-directory rather than by a flat filename
 * comparison.
 */
data class Track(
    val uri: String,
    val name: String,
    val folder: String,
) {
    /** File name without its extension — what gets shown as the track title. */
    val title: String
        get() = name.substringBeforeLast('.', name)

    val relativePath: String
        get() = if (folder.isEmpty()) name else "$folder/$name"
}
