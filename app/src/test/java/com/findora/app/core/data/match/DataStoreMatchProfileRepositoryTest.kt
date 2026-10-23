package com.findora.app.core.data.match

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.LocationAnchor
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreMatchProfileRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    private val file by lazy { File(tmp.root, "settings.preferences_pb") }

    private val profile =
        PreferenceProfile(
            budget = BudgetRange(8_000, 30_000, Currency.INR),
            minBedrooms = 2,
            minBathrooms = 1,
            propertyTypes = setOf(PropertyType.Apartment, PropertyType.House),
            anchors =
                listOf(
                    LocationAnchor("Kakkanad", GeoPoint(10.0159, 76.3419), LocationAnchor.Kind.Neighbourhood),
                    LocationAnchor("Edappally", GeoPoint(10.0261, 76.3125), LocationAnchor.Kind.Commute),
                ),
            lifestyle = setOf(LifestyleTag.RemoteWorker, LifestyleTag.Nature),
            mustHaves = setOf(MustHave.Parking, MustHave.Verified),
        )

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.repository(store: DataStore<Preferences> = dataStore(backgroundScope)) =
        DataStoreMatchProfileRepository(store, json)

    @Test
    fun noProfileUntilOneIsSaved() =
        runTest(UnconfinedTestDispatcher()) {
            assertNull(repository().observe("user-1").first())
        }

    @Test
    fun aSavedProfileRoundTripsExactly() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()
            repo.save("user-1", profile)
            assertEquals(profile, repo.observe("user-1").first())
        }

    @Test
    fun profilesAreKeptPerUserAndClearedPerUser() =
        runTest(UnconfinedTestDispatcher()) {
            val repo = repository()
            repo.save("user-1", profile)
            repo.save("guest", PreferenceProfile(minBedrooms = 1))
            assertEquals(PreferenceProfile(minBedrooms = 1), repo.observe("guest").first())
            repo.clear("user-1")
            assertNull(repo.observe("user-1").first())
            assertEquals(PreferenceProfile(minBedrooms = 1), repo.observe("guest").first())
        }

    @Test
    fun theProfileSurvivesAnAppRestart() =
        runTest(UnconfinedTestDispatcher()) {
            val firstRun = Job()
            DataStoreMatchProfileRepository(dataStore(CoroutineScope(coroutineContext + firstRun)), json)
                .save("user-1", profile)
            firstRun.cancelAndJoin()
            assertEquals(profile, repository().observe("user-1").first())
        }

    @Test
    fun anUnreadableDocumentReadsAsNoProfileAndUnknownIdsAreDropped() =
        runTest(UnconfinedTestDispatcher()) {
            val store = dataStore(backgroundScope)
            val repo = repository(store)
            store.edit { it[stringPreferencesKey("match_profile_user-1")] = "{not json" }
            assertNull(repo.observe("user-1").first())

            store.edit {
                it[stringPreferencesKey("match_profile_user-1")] =
                    """{"version":2,"lifestyle":["quiet","astronaut"],"mustHaves":["pool","parking"],"types":["castle"]}"""
            }
            assertEquals(
                PreferenceProfile(lifestyle = setOf(LifestyleTag.Quiet), mustHaves = setOf(MustHave.Parking)),
                repo.observe("user-1").first(),
            )
        }
}
