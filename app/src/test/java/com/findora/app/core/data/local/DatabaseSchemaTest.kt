package com.findora.app.core.data.local

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

/** The exported schema must exist and match the entities, so future migrations are testable against it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FindoraDatabase::class.java)

    @Test
    fun exportedSchemaIsCommitted() {
        val schema = File("schemas/${FindoraDatabase::class.java.name}/${FindoraDatabase.VERSION}.json")
        assertTrue("missing ${schema.path}", schema.isFile)
        listOf(
            "properties",
            "favorites",
            "users",
            "messages",
            "saved_searches",
            "viewings",
            "recent_searches",
            "interaction_events",
        ).forEach { table -> assertTrue(table, schema.readText().contains("\"tableName\": \"$table\"")) }
    }

    @Test
    fun exportedSchemaMatchesTheEntities() {
        helper.createDatabase(TEST_DB, FindoraDatabase.VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, FindoraDatabase.VERSION, true).close()
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
