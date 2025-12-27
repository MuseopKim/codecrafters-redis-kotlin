package store.geo

object GeoHash {

    fun encode(longitude: Double, latitude: Double): Double {
        val longitudeBits = ((longitude + 180.0) / 360.0 * (1L shl 26)).toLong()
        val latitudeBits = ((latitude + 90.0) / 180.0 * (1L shl 26)).toLong()

        var hash = 0L
        for (i in 0 until 26) {
            hash = hash or ((longitudeBits shr i and 1L) shl (2 * i + 1))
            hash = hash or ((latitudeBits shr i and 1L) shl (2 * i))
        }

        return hash.toDouble()
    }
}
