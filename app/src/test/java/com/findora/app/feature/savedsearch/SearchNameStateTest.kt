package com.findora.app.feature.savedsearch

import com.findora.app.core.domain.model.SavedSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchNameStateTest {
    @Test
    fun theSuggestedNameIsKeptAndSavedTrimmed() {
        val state = SearchNameState("  Kakkanad · Apartment ")
        assertEquals("  Kakkanad · Apartment ", state.name)
        assertEquals("Kakkanad · Apartment", state.nameToSave)
    }

    @Test
    fun aBlankNameHasNothingToSave() {
        val state = SearchNameState("Marsa flats")
        state.onNameChange("")
        assertNull(state.nameToSave)
        state.onNameChange(" \t ")
        assertNull(state.nameToSave)
        state.onNameChange(" Beach flats ")
        assertEquals("Beach flats", state.nameToSave)
    }

    @Test
    fun namesAreCappedWhileTypingAndWhenSuggested() {
        val long = "x".repeat(SavedSearch.MAX_NAME_LENGTH + 15)
        assertEquals(SavedSearch.MAX_NAME_LENGTH, SearchNameState(long).name.length)
        val state = SearchNameState("")
        state.onNameChange(long)
        assertEquals(SavedSearch.MAX_NAME_LENGTH, state.name.length)
        assertEquals("x".repeat(SavedSearch.MAX_NAME_LENGTH), state.nameToSave)
    }
}
