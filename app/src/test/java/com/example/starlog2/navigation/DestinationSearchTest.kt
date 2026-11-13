package com.example.starlog2.navigation

import com.example.starlog2.contracts.*
import org.junit.Assert.*
import org.junit.Test

class DestinationSearchTest {
    private val place = DestinationCandidate("1", "Station", "Seoul", null, LatLon(37.5, 127.0))
    private class SearchFake : DestinationSearchProvider {
        val callbacks = mutableListOf<(Outcome<List<DestinationCandidate>>) -> Unit>()
        var cancels = 0
        override fun search(query: String, done: (Outcome<List<DestinationCandidate>>) -> Unit): CancelHandle {
            callbacks += done
            return CancelHandle { cancels++ }
        }
    }
    private class RoutesFake : RouteProvider {
        val requests = mutableListOf<RouteRequest>()
        val callbacks = mutableListOf<(Outcome<Route>) -> Unit>()
        var cancels = 0
        override fun request(input: RouteRequest, done: (Outcome<Route>) -> Unit): CancelHandle {
            requests += input; callbacks += done
            return CancelHandle { cancels++ }
        }
    }
    private val start = LatLon(37.0, 127.0)
    private val fix = LocationFix(start, 5.0, null, Reference.UNKNOWN)
    private val fresh = TimedValue(fix, Quality.VALID, 10)
    private fun error(code: String) = Outcome.Err(AppError(code, code, true, null))

    @Test fun searchResultsEmptyAndFailuresRemainDistinct() {
        val fake = SearchFake(); val search = DestinationSearch(fake)
        val results = mutableListOf<Outcome<List<DestinationCandidate>>>()
        search.search("a", results::add); fake.callbacks.last()(Outcome.Ok(listOf(place)))
        search.search("b", results::add); fake.callbacks.last()(Outcome.Ok(emptyList()))
        search.search("c", results::add); fake.callbacks.last()(error("NETWORK_TIMEOUT"))
        search.search("d", results::add); fake.callbacks.last()(error("NETWORK_UNAVAILABLE"))
        assertEquals(listOf(place), (results[0] as Outcome.Ok).value)
        assertTrue((results[1] as Outcome.Ok).value.isEmpty())
        assertEquals("NETWORK_TIMEOUT", (results[2] as Outcome.Err).error.code)
        assertEquals("NETWORK_UNAVAILABLE", (results[3] as Outcome.Err).error.code)
    }
    @Test fun searchCancellationAndLateResponse() {
        val fake = SearchFake(); val search = DestinationSearch(fake); val results = mutableListOf<Outcome<List<DestinationCandidate>>>()
        search.search("old", results::add); search.search("new", results::add)
        fake.callbacks[0](Outcome.Ok(listOf(place))); fake.callbacks[1](Outcome.Ok(emptyList()))
        assertEquals(1, fake.cancels); assertEquals(1, results.size)
        search.search("cancel", results::add); search.cancel(); fake.callbacks[2](Outcome.Ok(listOf(place)))
        assertEquals(2, fake.cancels); assertEquals(1, results.size)
    }
    @Test fun missingAndStaleLocationsDoNotRequestRoute() {
        val fake = RoutesFake(); val routes = DestinationRoutes(fake, 100); val results = mutableListOf<Outcome<Route>>()
        routes.request("a", place.point, null, results::add)
        routes.request("b", place.point, TimedValue(null, Quality.STALE, 0), results::add)
        routes.request("c", place.point, fresh.copy(ageMs = 101), results::add)
        assertEquals(listOf("LOCATION_UNAVAILABLE", "LOCATION_STALE", "LOCATION_STALE"), results.map { (it as Outcome.Err).error.code })
        assertTrue(fake.requests.isEmpty())
    }
    @Test fun destinationReplacementCancelsAndIgnoresOldResponse() {
        val fake = RoutesFake(); val routes = DestinationRoutes(fake, 100); val results = mutableListOf<Outcome<Route>>()
        routes.request("a", place.point, fresh, results::add)
        val other = LatLon(36.0, 128.0); routes.request("b", other, fresh, results::add)
        assertEquals(start, fake.requests[1].start); assertEquals(other, fake.requests[1].end)
        fake.callbacks[0](error("NO_DATA")); fake.callbacks[1](error("NETWORK_UNAVAILABLE"))
        assertEquals(1, fake.cancels); assertEquals("NETWORK_UNAVAILABLE", (results.single() as Outcome.Err).error.code)
        routes.request("c", other, fresh, results::add); fake.callbacks[2](error("NO_DATA"))
        assertEquals("NO_DATA", (results.last() as Outcome.Err).error.code)
    }
    @Test fun synchronousCompletionIsDeliveredOnce() {
        val search = DestinationSearch(DestinationSearchProvider { _, done ->
            done(Outcome.Ok(listOf(place))); done(Outcome.Ok(emptyList())); CancelHandle { }
        })
        var count = 0; search.search("station") { count++ }; assertEquals(1, count)
    }
    @Test fun routeSuccessPreservesCanonicalRoute() {
        val fake = RoutesFake(); val routes = DestinationRoutes(fake, 100)
        val results = mutableListOf<Outcome<Route>>()
        routes.request("ok", place.point, fresh, results::add)
        val route = Route("r", "ok", listOf(start, place.point), 100.0, 20.0,
            listOf(Maneuver(0, 0, 1, "turn", null, 100.0, 20.0)))
        fake.callbacks.single()(Outcome.Ok(route))
        assertEquals(route, (results.single() as Outcome.Ok).value)
    }
}
