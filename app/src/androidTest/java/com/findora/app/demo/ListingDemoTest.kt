package com.findora.app.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.findora.app.R
import com.findora.app.demo.DemoRobot.Companion.BEAT_MILLIS
import com.findora.app.demo.DemoRobot.Companion.LONG_MILLIS
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Demo segments: listing detail and gallery, deep links, viewings, affordability, price history and radar. */
@DemoRecording
@RunWith(AndroidJUnit4::class)
class ListingDemoTest {
    private val robot = DemoRobot()

    @After
    fun lingerOnTheLastFrame() = robot.hold(BEAT_MILLIS)

    private fun demo(script: DemoRobot.() -> Unit) = robot.script()

    @Test
    fun listingDetailAndGallery() =
        demo {
            openFirstListing()
            chapter("Photo carousel")
            hold(2_000)
            swipeHorizontally("detailPhotos")
            swipeHorizontally("detailPhotos")
            chapter("Full-screen gallery")
            tap("detailPhotos")
            waitFor("gallery")
            hold(BEAT_MILLIS)
            swipeHorizontally("gallery")
            swipeHorizontally("gallery")
            hold(BEAT_MILLIS)
            tap("galleryClose")
            chapter("Details, amenities, agent and location")
            for (section in DETAIL_SECTIONS) {
                scrollUntil("detailList", By.res(section), maxSwipes = 4)
                hold(BEAT_MILLIS)
            }
            tap("detailBack")
        }

    @Test
    fun shareAndDeepLink() =
        demo {
            openFirstListing()
            chapter("Share a listing link")
            tap("detailShare")
            hold(LONG_MILLIS)
            back()
            tap("detailBack")
            chapter("Open findora://property/... from outside the app")
            device.pressHome()
            hold(2_000)
            openLinkFromOutside(DEEP_LINK)
            waitFor("detailList")
            hold(LONG_MILLIS)
            scroll("detailList")
            hold(BEAT_MILLIS)
        }

    @Test
    fun bookAViewingWithReminder() =
        demo {
            openFirstListing()
            chapter("Schedule a viewing")
            tap("detailSchedule")
            waitFor("scheduleViewing")
            hold(BEAT_MILLIS)
            tryTap(By.res("day_1"))
            hold(BEAT_MILLIS)
            tap(By.res(Pattern.compile("slot_.*")).enabled(true), "a free slot")
            if (scrollUntil("scheduleViewing", By.res("scheduleNote"))) {
                type("scheduleNote", "Could we meet at the main entrance?")
                hideKeyboard()
            }
            tap("scheduleConfirm")
            waitFor("scheduleBooked")
            hold(LONG_MILLIS)
            chapter("Booking confirmation and reminder notification")
            showNotificationShade(4_500)
            chapter("My viewings")
            scrollUntil(null, By.res("bookedMyViewings"))
            tap("bookedMyViewings")
            waitFor("viewingsList")
            hold(4_000)
        }

    @Test
    fun affordabilityCalculator() =
        demo {
            openFirstListing()
            chapter("Affordability calculator")
            scrollUntil("detailList", By.res("affordabilityOpen"), maxSwipes = 14)
            hold(BEAT_MILLIS)
            tap("affordabilityOpen")
            waitFor(By.text(str(R.string.afford_done)), "affordability sheet")
            hold(BEAT_MILLIS)
            type(By.clazz("android.widget.EditText"), "85000", "monthly income")
            hideKeyboard()
            hold(LONG_MILLIS)
            scroll(null)
            hold(LONG_MILLIS)
            scrollUntil(null, By.text(str(R.string.afford_done)))
            tapText(str(R.string.afford_done))
            hold(BEAT_MILLIS)
            scrollUntil("detailList", By.res("affordabilityCard"), towardsEnd = false)
            hold(BEAT_MILLIS)
        }

    @Test
    fun priceHistoryAndNeighbourhoodRadar() =
        demo {
            openFirstListing()
            chapter("Price history")
            scrollUntil("detailList", By.res("detailPriceHistory"), maxSwipes = 12)
            hold(LONG_MILLIS)
            chapter("Neighbourhood radar")
            scrollUntil("detailList", By.res("neighbourhoodRadar"), maxSwipes = 16)
            hold(4_000)
            scrollUntil("detailList", By.res("detailSimilar"), maxSwipes = 6)
            hold(BEAT_MILLIS)
        }

    private fun DemoRobot.openFirstListing() {
        startSignedIn()
        resetHome()
        tap(By.res(Pattern.compile("card_.*")), "first listing")
        waitFor("detailList")
    }

    private companion object {
        const val DEEP_LINK = "findora://property/fd-kl-0003"
        val DETAIL_SECTIONS = listOf("detailDescription", "detailAmenities", "detailAgent", "detailLocation")
    }
}
