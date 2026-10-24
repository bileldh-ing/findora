package com.findora.app.widget

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PicksWidgetContentTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun pick(
        id: String,
        title: String,
        match: String? = null,
    ) = PickSummary(
        propertyId = id,
        title = title,
        locality = "Kochi",
        priceText = "₹10,000 / month",
        matchText = match,
        thumbnailUrl = "https://example.com/$id.jpg",
        contentDescription = "$title, Kochi, ₹10,000 / month",
    )

    private fun ui(
        picks: List<PickSummary>,
        viewingLine: String? = null,
        signedIn: Boolean = true,
    ) = PicksWidgetUi(
        model =
            PicksWidgetModel(
                signedIn = signedIn,
                source = PicksSource.Matches,
                heading = "Top matches for you",
                picks = picks,
                viewingLine = viewingLine,
                emptyText = if (signedIn) "No homes to show yet." else "Sign in to Findora to see your picks.",
            ),
        // Only the first home has a loaded photo; the others fall back to the house icon.
        thumbnails = mapOf("p1" to Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)),
        isRtl = false,
        openAppLabel = "Open Findora",
    )

    @Test
    fun showsEachPickWithPriceMatchAndTheNextViewing() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(320.dp, 240.dp))
            provideComposable {
                PicksWidgetContent(
                    ui(
                        listOf(
                            pick("p1", "Georgie House", "92% match"),
                            pick("p2", "Luna Haven"),
                            pick("p3", "Palm Court"),
                        ),
                        viewingLine = "Next viewing: Georgie House · Oct 26, 14:00",
                    ),
                )
            }
            onNode(hasTextEqualTo("Top matches for you")).assertExists()
            onNode(hasTextEqualTo("Georgie House")).assertExists()
            onNode(hasTextEqualTo("92% match")).assertExists()
            onNode(hasTextEqualTo("Palm Court")).assertExists()
            onAllNodes(hasTextEqualTo("₹10,000 / month")).assertCountEquals(3)
            onNode(hasTextEqualTo("Next viewing: Georgie House · Oct 26, 14:00")).assertExists()
            // TalkBack reads each home as one item.
            onNode(hasContentDescription("Luna Haven, Kochi, ₹10,000 / month")).assertExists()
        }

    @Test
    fun tappingAHomeOpensItsDetailLink() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { PicksWidgetContent(ui(listOf(pick("p1", "Georgie House")))) }
            onNode(hasStartActivityClickAction(openPropertyIntent(context, "p1"))).assertExists()
            onNode(hasStartActivityClickAction(openAppIntent(context))).assertExists()
        }

    @Test
    fun aSignedOutWidgetAsksToSignIn() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { PicksWidgetContent(ui(emptyList(), signedIn = false)) }
            onNode(hasTextEqualTo("Sign in to Findora to see your picks.")).assertExists()
            onNode(hasTextEqualTo("Georgie House")).assertDoesNotExist()
        }
}
