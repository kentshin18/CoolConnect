package com.example.coolconnect

import com.google.android.gms.maps.model.LatLng
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

data class RouteResult(
    val points: List<LatLng>,
    val durationText: String? = null,
    val distanceText: String? = null
)

object NavigationUtils {
    private val client = OkHttpClient()

    suspend fun getRoutePoints(origin: LatLng, destination: LatLng, apiKey: String): List<LatLng> {
        return getRouteDetails(origin, destination, apiKey).points
    }

    suspend fun getRouteDetails(origin: LatLng, destination: LatLng, apiKey: String): RouteResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Try OSRM Driving Engine
                val osrmResult = fetchOsrmRoadRoute(origin, destination)
                if (osrmResult.points.isNotEmpty()) return@withContext osrmResult

                // 2. Try Google Routes API v2
                val routesApiResult = fetchRoutesApiV2(origin, destination, apiKey)
                if (routesApiResult.points.isNotEmpty()) return@withContext routesApiResult

                // 3. Try Google Directions API v1
                val directionsResult = fetchDirectionsApiV1(origin, destination, apiKey)
                if (directionsResult.points.isNotEmpty()) return@withContext directionsResult

                // 4. Fallback to Smart Road Route with no fake ETA
                RouteResult(generateSmartRoadRoute(origin, destination), null, null)
            } catch (e: Exception) {
                e.printStackTrace()
                RouteResult(generateSmartRoadRoute(origin, destination), null, null)
            }
        }
    }

    private fun fetchOsrmRoadRoute(origin: LatLng, destination: LatLng): RouteResult {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                    "${origin.longitude},${origin.latitude};" +
                    "${destination.longitude},${destination.latitude}" +
                    "?overview=full&geometries=geojson"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val code = json.optString("code")
                if (code == "Ok") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val routeObj = routes.getJSONObject(0)
                        val coords = routeObj.getJSONObject("geometry").getJSONArray("coordinates")

                        val list = mutableListOf<LatLng>()
                        for (i in 0 until coords.length()) {
                            val coord = coords.getJSONArray(i)
                            list.add(LatLng(coord.getDouble(1), coord.getDouble(0)))
                        }

                        val durationSec = routeObj.optDouble("duration", 0.0)
                        val distanceM = routeObj.optDouble("distance", 0.0)

                        val durText = if (durationSec > 0) "${(durationSec / 60.0).roundToInt()} mins" else null
                        val distText = if (distanceM > 0) String.format(Locale.getDefault(), "%.1f km", distanceM / 1000.0) else null

                        if (list.isNotEmpty()) return RouteResult(list, durText, distText)
                    }
                }
            }
            RouteResult(emptyList())
        } catch (e: Exception) {
            RouteResult(emptyList())
        }
    }

    private fun fetchRoutesApiV2(origin: LatLng, destination: LatLng, apiKey: String): RouteResult {
        return try {
            val jsonBody = JSONObject().apply {
                put("origin", JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))))
                put("destination", JSONObject().put("location", JSONObject().put("latLng", JSONObject().put("latitude", destination.latitude).put("longitude", destination.longitude))))
                put("travelMode", "DRIVE")
            }

            val request = Request.Builder()
                .url("https://routes.googleapis.com/v2:computeRoutes")
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "routes.polyline.encodedPolyline,routes.duration,routes.distanceMeters")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val routeObj = routes.getJSONObject(0)
                    val encodedPolyline = routeObj.getJSONObject("polyline").getString("encodedPolyline")
                    val decoded = decodePolyline(encodedPolyline)

                    val durRaw = routeObj.optString("duration", "").replace("s", "")
                    val durSec = durRaw.toDoubleOrNull() ?: 0.0
                    val distM = routeObj.optDouble("distanceMeters", 0.0)

                    val durText = if (durSec > 0) "${(durSec / 60.0).roundToInt()} mins" else null
                    val distText = if (distM > 0) String.format(Locale.getDefault(), "%.1f km", distM / 1000.0) else null

                    if (decoded.isNotEmpty()) return RouteResult(decoded, durText, distText)
                }
            }
            RouteResult(emptyList())
        } catch (e: Exception) {
            RouteResult(emptyList())
        }
    }

    private fun fetchDirectionsApiV1(origin: LatLng, destination: LatLng, apiKey: String): RouteResult {
        return try {
            val directionsUrl = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${origin.latitude},${origin.longitude}&" +
                    "destination=${destination.latitude},${destination.longitude}&" +
                    "mode=driving&" +
                    "key=$apiKey"

            val request = Request.Builder().url(directionsUrl).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val status = json.optString("status")

                if (status == "OK") {
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val routeObj = routes.getJSONObject(0)
                        val points = routeObj.getJSONObject("overview_polyline").getString("points")
                        val decoded = decodePolyline(points)

                        val legs = routeObj.optJSONArray("legs")
                        var durText: String? = null
                        var distText: String? = null
                        if (legs != null && legs.length() > 0) {
                            val leg = legs.getJSONObject(0)
                            durText = leg.optJSONObject("duration")?.optString("text")
                            distText = leg.optJSONObject("distance")?.optString("text")
                        }

                        if (decoded.isNotEmpty()) return RouteResult(decoded, durText, distText)
                    }
                } else {
                    android.util.Log.w("NavigationUtils", "Google Directions API Status: $status (${json.optString("error_message")})")
                }
            }
            RouteResult(emptyList())
        } catch (e: Exception) {
            RouteResult(emptyList())
        }
    }

    private fun generateSmartRoadRoute(start: LatLng, end: LatLng): List<LatLng> {
        val points = mutableListOf<LatLng>()
        
        val minLat = minOf(start.latitude, end.latitude)
        val maxLat = maxOf(start.latitude, end.latitude)
        val minLng = minOf(start.longitude, end.longitude)
        val maxLng = maxOf(start.longitude, end.longitude)

        // Detect Cavite / Southern Luzon to Metro Manila highway corridor
        val isCaviteCorridor = (minLat > 14.1 && maxLat < 14.7) && (minLng > 120.7 && maxLng < 121.1)

        if (isCaviteCorridor) {
            val westPoint = if (start.longitude < end.longitude) start else end
            val eastPoint = if (start.longitude < end.longitude) end else start

            points.add(westPoint)
            
            val wp1 = LatLng(
                westPoint.latitude + (eastPoint.latitude - westPoint.latitude) * 0.35 + 0.015,
                westPoint.longitude + (eastPoint.longitude - westPoint.longitude) * 0.3
            )
            val wp2 = LatLng(14.4580, 120.9550)
            val wp3 = LatLng(14.4480, 120.9780)

            interpolateSegment(points, westPoint, wp1, 6)
            interpolateSegment(points, wp1, wp2, 6)
            interpolateSegment(points, wp2, wp3, 5)
            interpolateSegment(points, wp3, eastPoint, 5)

            return if (start.longitude < end.longitude) points else points.reversed()
        }

        points.add(start)
        
        val mid1 = LatLng(start.latitude, start.longitude + (end.longitude - start.longitude) * 0.6)
        val mid2 = LatLng(end.latitude - (end.latitude - start.latitude) * 0.2, end.longitude)

        interpolateSegment(points, start, mid1, 8)
        interpolateSegment(points, mid1, mid2, 8)
        interpolateSegment(points, mid2, end, 6)

        return points
    }

    private fun interpolateSegment(list: MutableList<LatLng>, from: LatLng, to: LatLng, steps: Int) {
        for (i in 1..steps) {
            val fraction = i.toDouble() / steps
            val lat = from.latitude + (to.latitude - from.latitude) * fraction
            val lng = from.longitude + (to.longitude - from.longitude) * fraction
            list.add(LatLng(lat, lng))
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }
}
