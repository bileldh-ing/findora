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
            "conversations",
            "notifications",
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

    @Test
    fun version2MigratesToVersion3KeepingMessagesAsSent() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO messages (id, conversation_id, property_id, sender_id, recipient_id, body, sent_at, " +
                    "read_at) VALUES ('m1', 'c1', 'p1', 'agent:a1', 'u1', 'Viewing confirmed', 1000, NULL)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true).use { db ->
            db.query("SELECT body, status FROM messages WHERE id = 'm1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Viewing confirmed", cursor.getString(0))
                assertEquals("sent", cursor.getString(1))
            }
            db.execSQL(
                "INSERT INTO conversations (id, user_id, property_id, property_title, agent_id, agent_name, " +
                    "agent_initials, agent_color, created_at) " +
                    "VALUES ('c1', 'u1', 'p1', 'Home', 'a1', 'Anu', 'AT', 0, 0)",
            )
            db.execSQL(
                "INSERT INTO notifications (user_id, kind, subject, preview, property_id, conversation_id, " +
                    "event_at, created_at, read_at) " +
                    "VALUES ('u1', 'new_message', 'Anu', 'Hi', 'p1', 'c1', NULL, 0, NULL)",
            )
            db.query("SELECT COUNT(*) FROM notifications WHERE user_id = 'u1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
