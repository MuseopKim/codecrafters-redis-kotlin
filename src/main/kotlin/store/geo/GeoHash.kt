package store.geo

import kotlin.math.*

object GeoHash {
    private const val EARTH_RADIUS_METERS = 6372797.560856
    private const val DEG_TO_RAD = PI / 180.0

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

    fun decode(hash: Double): Pair<Double, Double> {
        val hashLong = hash.toLong()

        val latitudeSpread = hashLong
        val longitudeSpread = hashLong shr 1

        val normalizedLatitude = compact(latitudeSpread)
        val normalizedLongitude = compact(longitudeSpread)

        val minLatitude = MIN_LATITUDE + LATITUDE_RANGE * normalizedLatitude / (1L shl 26)
        val maxLatitude = MIN_LATITUDE + LATITUDE_RANGE * (normalizedLatitude + 1) / (1L shl 26)
        val latitude = (minLatitude + maxLatitude) / 2

        val minimumLongitude = MIN_LONGITUDE + LONGITUDE_RANGE * normalizedLongitude / (1L shl 26)
        val maximumLongitude = MIN_LONGITUDE + LONGITUDE_RANGE * (normalizedLongitude + 1) / (1L shl 26)
        val longitude = (minimumLongitude + maximumLongitude) / 2

        return Pair(longitude, latitude)
    }

    private fun compact(v: Long): Long {
        var x = v and 0x5555555555555555L
        x = (x or (x shr 1)) and 0x3333333333333333L
        x = (x or (x shr 2)) and 0x0F0F0F0F0F0F0F0FL
        x = (x or (x shr 4)) and 0x00FF00FF00FF00FFL
        x = (x or (x shr 8)) and 0x0000FFFF0000FFFFL
        x = (x or (x shr 16)) and 0x00000000FFFFFFFFL
        return x
    }

    fun distance(longitude1: Double, latitude1: Double, longitude2: Double, latitude2: Double): Double {
        val latitude1Radians = latitude1 * DEG_TO_RAD
        val latitude2Radians = latitude2 * DEG_TO_RAD
        val longitude1Radians = longitude1 * DEG_TO_RAD
        val longitude2Radians = longitude2 * DEG_TO_RAD

        val latitudeDifferenceHalf = sin((latitude2Radians - latitude1Radians) / 2)
        val longitudeDifferenceHalf = sin((longitude2Radians - longitude1Radians) / 2)

        val haversineOfCentralAngle =
            latitudeDifferenceHalf * latitudeDifferenceHalf +
            cos(latitude1Radians) * cos(latitude2Radians) *
            longitudeDifferenceHalf * longitudeDifferenceHalf

        return EARTH_RADIUS_METERS * 2 * asin(sqrt(haversineOfCentralAngle))
    }
}
