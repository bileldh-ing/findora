package com.findora.app.core.data.local

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        (1..FindoraDatabase.VERSION).forEach { version ->
            val file = File("schemas/${FindoraDatabase::class.java.name}/$version.json")
            assertTrue("missing ${file.path}", file.isFile)
        }
        val schema = File("schemas/${FindoraDatabase::class.java.name}/${FindoraDatabase.VERSION}.json")
        listOf(
            "properties",
            "favorites",
            "users",
            "messages",
            "saved_searches",
            "viewings",
            "recent_searches",
            "interaction_events",
            "login_attempts",
        ).forEach { table -> assertTrue(table, schema.readText().contains("\"tableName\": \"$table\"")) }
    }

    @Test
    fun exportedSchemaMatchesTheEntities() {
        helper.createDatabase(TEST_DB, FindoraDatabase.VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, FindoraDatabase.VERSION, true).close()
    }

    @Test
    fun version1MigratesToTheCurrentSchemaKeepingUsers() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO users (id, email, display_name, password_hash, password_salt, created_at) " +
                    "VALUES ('u1', 'ada@findora.app', 'Ada', 'h', 's', 0)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, FindoraDatabase.VERSION, true).use { db ->
            db.query("SELECT email FROM users WHERE id = 'u1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ada@findora.app", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM login_attempts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
