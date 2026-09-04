package com.maryumcenter.iqraaudioplayer

/**
 * Case-insensitive alphabetical comparison that treats runs of digits as
 * numbers, so "track2" sorts before "track10" the way a listener expects.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++

                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val byDigits = na.compareTo(nb)
                if (byDigits != 0) return byDigits

                i = ei
                j = ej
            } else {
                val byChar = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

/** Orders tracks by directory first, then by file name, both naturally. */
object TrackOrder : Comparator<Track> {
    override fun compare(a: Track, b: Track): Int {
        val byFolder = NaturalOrder.compare(a.folder, b.folder)
        if (byFolder != 0) return byFolder
        return NaturalOrder.compare(a.name, b.name)
    }
}
