package de.moba.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LayoutTest {
    private val layout = Layout(
        sections = listOf(TrackSection("b1", "Bahnhof Gleis 1")),
        feedback = listOf(FeedbackContact(module = 2, contact = 5, sectionId = "b1")),
    )

    @Test
    fun findsSectionForS88Contact() {
        assertEquals("Bahnhof Gleis 1", layout.sectionFor(2, 5)?.name)
        assertNull(layout.sectionFor(2, 6))
    }

    @Test
    fun rejectsInvalidS88Contact() {
        assertFailsWith<IllegalArgumentException> { FeedbackContact(module = 1, contact = 17) }
    }

    @Test
    fun repositoryAppliesUpdates() {
        val repo = InMemoryLayoutRepository()
        repo.update { it.copy(locos = it.locos + Loco("br218", "BR 218", address = 3)) }
        assertEquals(3, repo.layout.value.locos.single().address)
    }
}
