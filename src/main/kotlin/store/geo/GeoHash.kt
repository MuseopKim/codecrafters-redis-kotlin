package store.geo

object GeoHash {
    private const val MIN_LATITUDE = -85.05112878
    private const val MAX_LATITUDE = 85.05112878
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0

    private const val LATITUDE_RANGE = MAX_LATITUDE - MIN_LATITUDE
    private const val LONGITUDE_RANGE = MAX_LONGITUDE - MIN_LONGITUDE

    fun encode(longitude: Double, latitude: Double): Double {
        val normalizedLatitude = ((1L shl 26) * (latitude - MIN_LATITUDE) / LATITUDE_RANGE).toLong()
        val normalizedLongitude = ((1L shl 26) * (longitude - MIN_LONGITUDE) / LONGITUDE_RANGE).toLong()

        val spreadLatitude = spread(normalizedLatitude)
        val spreadLongitude = spread(normalizedLongitude)

        val hash = spreadLatitude or (spreadLongitude shl 1)

        return hash.toDouble()
    }

    private fun spread(v: Long): Long {
        var x = v and 0xFFFFFFFFL
        x = (x or (x shl 16)) and 0x0000FFFF0000FFFFL
        x = (x or (x shl 8)) and 0x00FF00FF00FF00FFL
        x = (x or (x shl 4)) and 0x0F0F0F0F0F0F0F0FL
        x = (x or (x shl 2)) and 0x3333333333333333L
        x = (x or (x shl 1)) and 0x5555555555555555L
        return x
    }
}
