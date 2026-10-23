package com.findora.app.core.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.findora.app.navigation.ChatDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AndroidChatNotifierTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidChatNotifier(context)
    private val message = ChatNotification("conv-1", "Anu Thomas", "Georgie House", "Yes, it is still available.")

    @Test
    fun postsOnTheMessagesChannelAndOpensTheThread() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notifier.post(message))
        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(AndroidChatNotifier.CHANNEL_ID, posted.channelId)
        assertEquals("Anu Thomas", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(message.body, posted.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertEquals("Messages", manager.getNotificationChannel(AndroidChatNotifier.CHANNEL_ID).name)
        val tap = shadowOf(posted.contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, tap.action)
        assertEquals(ChatDeepLink.uriFor("conv-1"), tap.dataString)
        // A newer reply in the same thread replaces it, and opening the thread clears it.
        notifier.post(message.copy(body = "Viewing slots"))
        assertEquals(1, shadowOf(manager).allNotifications.size)
        notifier.cancel("conv-1")
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun aDeniedPermissionPostsNothing() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.post(message))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
