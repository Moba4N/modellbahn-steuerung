package de.moba.modules.routes.api

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteCodecTest {
    @Test
    fun roundTrip() {
        val book = RouteBook(
            routes = listOf(
                Route("fs-1", "Gleis 1 nach\tGleis 3", listOf(RouteSetting(12, true), RouteSetting(30, false)), Feedback(1, 3), Feedback(2, 5)),
                Route("fs-2", "Leer"),
            ),
            shuttles = listOf(Shuttle("pe-1", "Nebenbahn", 5, 50, Feedback(1, 1), Feedback(1, 8), null, Feedback(1, 7), 15, 20)),
            trainRoutes = listOf(
                TrainRoute(
                    "zr-1", "Runde", 3, 70, false,
                    listOf(TrainRouteStep("fs-1", Feedback(2, 5), 30), TrainRouteStep("fs-2", null, 0, 40)),
                    repeat = true,
                ),
            ),
        )
        val back = RouteCodec.decode(RouteCodec.encode(book))
        assertEquals(book.copy(routes = listOf(book.routes[0].copy(name = "Gleis 1 nach Gleis 3"), book.routes[1])), back)
    }
}
