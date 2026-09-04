package com.maryumcenter.iqraaudioplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackOrderTest {

    private fun track(path: String): Track {
        val folder = path.substringBeforeLast('/', "")
        return Track(uri = "content://doc/$path", name = path.substringAfterLast('/'), folder = folder)
    }

    private fun ordered(vararg paths: String): List<String> =
        paths.map(::track).sortedWith(TrackOrder).map { it.relativePath }

    @Test
    fun `numbers in file names sort numerically, not lexically`() {
        assertEquals(
            listOf("track1.mp3", "track2.mp3", "track9.mp3", "track10.mp3", "track100.mp3"),
            ordered("track10.mp3", "track100.mp3", "track2.mp3", "track9.mp3", "track1.mp3"),
        )
    }

    @Test
    fun `zero padding does not change the order`() {
        assertEquals(
            listOf("003.mp3", "07.mp3", "10.mp3"),
            ordered("10.mp3", "003.mp3", "07.mp3"),
        )
    }

    @Test
    fun `sorting is case insensitive`() {
        assertEquals(
            listOf("apple.mp3", "Banana.mp3", "cherry.mp3"),
            ordered("cherry.mp3", "apple.mp3", "Banana.mp3"),
        )
    }

    @Test
    fun `files are grouped by directory and directories sort naturally`() {
        assertEquals(
            listOf(
                "intro.mp3",
                "Disc 1/02 - b.mp3",
                "Disc 1/10 - c.mp3",
                "Disc 2/01 - a.mp3",
                "Disc 10/01 - a.mp3",
            ),
            ordered(
                "Disc 10/01 - a.mp3",
                "Disc 2/01 - a.mp3",
                "Disc 1/10 - c.mp3",
                "intro.mp3",
                "Disc 1/02 - b.mp3",
            ),
        )
    }

    @Test
    fun `nested directories keep their parent grouping`() {
        assertEquals(
            listOf(
                "Book/Part 1/01.mp3",
                "Book/Part 1/02.mp3",
                "Book/Part 2/01.mp3",
            ),
            ordered("Book/Part 2/01.mp3", "Book/Part 1/02.mp3", "Book/Part 1/01.mp3"),
        )
    }

    @Test
    fun `title strips only the extension`() {
        assertEquals("Surah 2. Al-Baqarah", track("Quran/Surah 2. Al-Baqarah.mp3").title)
        assertEquals("no-extension", track("no-extension").title)
    }
}
