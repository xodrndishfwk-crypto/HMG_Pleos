package com.example.starlog2.navigation

object Polyline6 {
    const val DEFAULT_MAX_POINTS = 50_000

    fun decode(encoded: String, maxPoints: Int = DEFAULT_MAX_POINTS): Outcome<List<LatLon>> {
        if (encoded.isEmpty() || maxPoints < 2) {
            return invalidResponse()
        }

        val result = ArrayList<LatLon>()
        var index = 0
        var latitude = 0L
        var longitude = 0L

        return try {
            while (index < encoded.length) {
                val latitudeDelta = decodeValue(encoded, index)
                index = latitudeDelta.nextIndex
                val longitudeDelta = decodeValue(encoded, index)
                index = longitudeDelta.nextIndex
                latitude = Math.addExact(latitude, latitudeDelta.value)
                longitude = Math.addExact(longitude, longitudeDelta.value)
                if (latitude !in -90_000_000L..90_000_000L || longitude !in -180_000_000L..180_000_000L) {
                    return invalidResponse()
                }
                result += LatLon(latitude / SCALE, longitude / SCALE)
                if (result.size > maxPoints) {
                    return invalidResponse()
                }
            }
            if (result.size < 2) invalidResponse() else Outcome.Ok(result)
        } catch (_: IllegalArgumentException) {
            invalidResponse()
        } catch (_: ArithmeticException) {
            invalidResponse()
        }
    }

    private fun decodeValue(encoded: String, startIndex: Int): DecodedValue {
        var index = startIndex
        var result = 0L
        var shift = 0
        while (true) {
            if (index >= encoded.length || shift > 60) {
                throw IllegalArgumentException("Truncated or overflowing polyline")
            }
            val charCode = encoded[index++].code
            if (charCode !in 63..126) {
                throw IllegalArgumentException("Invalid polyline character")
            }
            val chunk = charCode - 63
            result = result or ((chunk and 0x1f).toLong() shl shift)
            if (chunk < 0x20) break
            shift += 5
        }
        val signed = if ((result and 1L) != 0L) -(result shr 1) - 1 else result shr 1
        return DecodedValue(signed, index)
    }

    private fun invalidResponse(): Outcome.Err = Outcome.Err(navigationError("INVALID_RESPONSE"))

    private data class DecodedValue(val value: Long, val nextIndex: Int)

    private const val SCALE = 1_000_000.0
}
