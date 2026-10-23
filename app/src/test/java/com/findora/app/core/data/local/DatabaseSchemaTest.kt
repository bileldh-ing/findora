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
            "saved_search_seen",
            "price_watch",
            "owner_listings",
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

    @Test
    fun version3MigratesToVersion4KeepingSavedSearchesAndNotifications() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO saved_searches (user_id, name, query, filter_json, alerts_enabled, created_at, " +
                    "last_notified_at) VALUES ('u1', 'Houses', 'houses', '{\"type\":\"house\"}', 1, 5, NULL)",
            )
            db.execSQL(
                "INSERT INTO notifications (user_id, kind, subject, preview, property_id, conversation_id, " +
                    "event_at, created_at, read_at) " +
                    "VALUES ('u1', 'viewing_booked', 'Home', '', 'p1', NULL, 0, 0, NULL)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 4, true).use { db ->
            db.query("SELECT name, region_id, alert_frequency, last_checked_at FROM saved_searches").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Houses", cursor.getString(0))
                // Older searches run in the current region with daily alerts.
                assertEquals("", cursor.getString(1))
                assertEquals("daily", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
            db.query("SELECT saved_search_id, item_count FROM notifications").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(0, cursor.getInt(1))
            }
            db.execSQL("INSERT INTO saved_search_seen (search_id, property_id) VALUES (1, 'p1')")
            db.execSQL("INSERT INTO price_watch (user_id, property_id, rent_amount) VALUES ('u1', 'p1', 10000)")
            db.query("SELECT COUNT(*) FROM saved_search_seen").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version4MigratesToVersion5KeepingListingsAsMarketListings() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO properties (id, title, type, rent_amount, currency, bedrooms, bathrooms, area_sqm, " +
                    "floor, " +
                    "furnished, pet_friendly, parking, amenities, tags, rating, reviews_count, verified, latitude, " +
                    "longitude, locality, region_id, description, agent_id, agent_name, agent_phone, agent_initials, " +
                    "agent_avatar_argb, agent_response_minutes, images, video_url, panorama_url, created_at, " +
                    "price_history, sample_data, fetched_at) VALUES " +
                    "('p1', 'Home', 'house', 1000, 'INR', 1, 1, 50, 0, " +
                    "0, 0, 0, '[]', '[]', 4.5, 3, 1, 10.0, 76.0, 'Kochi', 'kerala', 'A home', 'a1', 'Agent', '1', " +
                    "'AG', 0, 30, '[]', NULL, NULL, 0, '[]', 1, 0)",
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 5, true).use { db ->
            db.query("SELECT owner_id, deposit_amount FROM properties WHERE id = 'p1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
            db.execSQL(
                "INSERT INTO owner_listings (id, owner_id, status, draft_json, published_json, created_at, " +
                    "updated_at, published_at) VALUES ('own-1', 'u1', 'draft', '{}', NULL, 0, 0, NULL)",
            )
            db.query("SELECT COUNT(*) FROM owner_listings").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
    }
}
