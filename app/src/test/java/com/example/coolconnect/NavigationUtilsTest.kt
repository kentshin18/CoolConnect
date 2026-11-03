package com.example.coolconnect

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationUtilsTest {

    @Test
    fun testRoutePointsFallbackReturnsValidPoints() = runBlocking {
        val origin = LatLng(14.5995, 120.9842)
        val destination = LatLng(14.6095, 121.0000)

        // Calling getRoutePoints with invalid/dummy API key invokes smart road route generator
        val route = NavigationUtils.getRoutePoints(origin, destination, "INVALID_KEY")

        // Should return a multi-point road corridor route (20+ points following highways)
        assertTrue("Smart road route should generate multiple highway points", route.size > 5)
        assertEquals(origin, route.first())
        assertEquals(destination, route.last())
    }

    @Test
    fun testPolylinePointsAreNotEmpty() = runBlocking {
        val origin = LatLng(14.5995, 120.9842)
        val destination = LatLng(14.6095, 121.0000)

        val points = NavigationUtils.getRoutePoints(origin, destination, "DUMMY_KEY")

        assertTrue("Route points should not be empty", points.isNotEmpty())
    }
}
