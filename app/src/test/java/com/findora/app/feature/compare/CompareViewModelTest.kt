package com.findora.app.feature.compare

import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryCompareRepository
import com.findora.app.core.domain.compare.CompareAddResult
import com.findora.app.core.domain.compare.CompareMetric
import com.findora.app.core.domain.compare.ObserveComparedProperties
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.observeMatchContext
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompareViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val compare = InMemoryCompareRepository()
    private val properties = FakePropertyRepository()
    private val profiles = FakeMatchProfileRepository()
    private val guest = AuthRepository.GUEST_USER_ID
    private val kerala = seedProperties.filter { it.regionId == "kerala" }
    private val compared = ObserveComparedProperties(auth, compare, properties)

    private fun compareViewModel() =
        CompareViewModel(
            compared = compared,
            compare = compare,
            observeMatchContext = observeMatchContext(auth = auth, profiles = profiles, properties = properties),
        )

    @Test
    fun tableFollowsTheSelectionAndItsColumnOrder() =
        runTest(main.dispatcher) {
            val vm = compareViewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.isLoading) state = awaitItem()
                assertTrue(state.isEmpty)
                compare.add(guest, kerala[0].id)
                compare.add(guest, kerala[1].id)
                while (state.count < 2) state = awaitItem()
                assertEquals(listOf(kerala[0].id, kerala[1].id), state.table.properties.map { it.id })
                assertFalse(state.hasMatchRow)
                vm.onEvent(CompareUiEvent.Move(0, 1))
                assertEquals(listOf(kerala[1].id, kerala[0].id), awaitItem().table.properties.map { it.id })
                vm.onEvent(CompareUiEvent.Remove(kerala[0].id))
                assertEquals(listOf(kerala[1].id), awaitItem().table.properties.map { it.id })
                vm.onEvent(CompareUiEvent.Clear)
                assertTrue(awaitItem().isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun matchRowAppearsWithAProfile() =
        runTest(main.dispatcher) {
            profiles.save(guest, PreferenceProfile(budget = BudgetRange(null, 40_000, Currency.INR)))
            compare.add(guest, kerala[0].id)
            compare.add(guest, kerala[2].id)
            val vm = compareViewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.isLoading || state.count < 2) state = awaitItem()
                assertTrue(state.hasMatchRow)
                assertTrue(
                    state.table
                        .row(CompareMetric.Match)!!
                        .values
                        .all { it != null },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun trayAddsUpToThreeAndReportsWhenFullPerUser() =
        runTest(main.dispatcher) {
            val tray = CompareTrayViewModel(compared, compare)
            tray.effects.test {
                kerala.take(3).forEach { tray.onEvent(CompareTrayEvent.Add(it.id)) }
                repeat(3) { assertEquals(CompareAddResult.Added, awaitItem().result) }
                tray.onEvent(CompareTrayEvent.Add(kerala[3].id))
                assertEquals(CompareAddResult.Full, awaitItem().result)
                tray.onEvent(CompareTrayEvent.Add(kerala[0].id))
                assertEquals(CompareAddResult.AlreadyAdded, awaitItem().result)
            }
            tray.state.test {
                var state = awaitItem()
                while (state.items.size < 3) state = awaitItem()
                assertTrue(state.isVisible)
                tray.onEvent(CompareTrayEvent.Remove(kerala[1].id))
                assertEquals(listOf(kerala[0].id, kerala[2].id), awaitItem().items.map { it.id })
                // Another user has their own (empty) selection.
                auth.signIn()
                assertFalse(awaitItem().isVisible)
                tray.onEvent(CompareTrayEvent.Clear)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(2, compare.observe(guest).first().size)
            assertNull(compare.observe(guest).first().firstOrNull { it == kerala[1].id })
        }
}
