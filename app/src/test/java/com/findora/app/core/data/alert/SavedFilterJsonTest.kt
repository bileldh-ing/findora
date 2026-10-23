package com.findora.app.core.data.alert

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFilterJsonTest {
    @Test
    fun everyFieldRoundTrips() {
        val filter =
            PropertyFilter(
                type = PropertyType.Apartment,
                minRent = 8_000,
                maxRent = 20_000,
                minBedrooms = 2,
                verifiedOnly = true,
                query = "Kakkanad",
                minBathrooms = 1,
                minAreaSqm = 60,
                maxAreaSqm = 140,
                furnishedOnly = true,
                petFriendlyOnly = true,
                parkingOnly = true,
                amenities = setOf(Amenity.Gym, Amenity.Wifi),
                sort = SortOrder.PriceLowToHigh,
            )
        assertEquals(filter, SavedFilterJson.decode(SavedFilterJson.encode(filter)))
        assertEquals(PropertyFilter(), SavedFilterJson.decode(SavedFilterJson.encode(PropertyFilter())))
    }

    @Test
    fun unknownOrBrokenValuesNeverBreakASavedSearch() {
        assertEquals(PropertyFilter(), SavedFilterJson.decode("not json"))
        assertEquals(PropertyFilter(), SavedFilterJson.decode("{}"))
        // A min above max (written by hand or a future version) falls back to the unfiltered search.
        assertEquals(PropertyFilter(), SavedFilterJson.decode("""{"minRent":5,"maxRent":1}"""))
        assertEquals(
            PropertyFilter(amenities = setOf(Amenity.Gym), sort = SortOrder.Recommended),
            SavedFilterJson.decode("""{"type":"castle","amenities":["gym","helipad"],"sort":"Magic","extra":1}"""),
        )
    }
}
