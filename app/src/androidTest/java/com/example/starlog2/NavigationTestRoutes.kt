package com.example.starlog2

import androidx.test.platform.app.InstrumentationRegistry
import com.example.starlog2.contracts.*

/** Keep UI/recovery tests off the public routing service. Map/WebView remain real. */
object NavigationTestRoutes {
    private var original: Any? = null
    private val field get() = GlNavigationActivity::class.java.getDeclaredField("routeProviderFactory").apply { isAccessible=true }
    fun install() {
        original=field.get(null)
        val route=ContractJson.decodeRoute(InstrumentationRegistry.getInstrumentation().context.assets
            .open("contracts/route-valid.json").bufferedReader().use{it.readText()})
        val factory:()->RouteProvider = { object:RouteProvider {
            override fun request(input:RouteRequest,done:(Outcome<Route>)->Unit):CancelHandle {
                done(Outcome.Ok(route.copy(requestId=input.requestId)));return CancelHandle{}
            }
        } }
        field.set(null,factory)
    }
    fun restore() { original?.let {field.set(null,it)};original=null }
}
