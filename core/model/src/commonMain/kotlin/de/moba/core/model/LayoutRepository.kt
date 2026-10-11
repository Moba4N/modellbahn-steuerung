package de.moba.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Zugriff auf das Datenmodell der Anlage. Die SQLite-Umsetzung folgt; bis dahin gibt es [InMemoryLayoutRepository]. */
interface LayoutRepository {
    val layout: StateFlow<Layout>
    fun update(transform: (Layout) -> Layout)
}

class InMemoryLayoutRepository(initial: Layout = Layout()) : LayoutRepository {
    private val state = MutableStateFlow(initial)
    override val layout: StateFlow<Layout> = state
    override fun update(transform: (Layout) -> Layout) = state.update(transform)
}
