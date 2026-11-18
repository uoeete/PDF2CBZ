package com.pdf2cbz.app

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.pdf2cbz.app.data.model.ConversionHistoryEntry
import com.pdf2cbz.app.data.model.ImageFormat
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.QueueItemStatus
import com.pdf2cbz.app.data.queue.QueueStateStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Pdf2CbzRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun appNameResourceMatchesPdf2Cbz() {
        assertEquals("PDF2CBZ", context.getString(R.string.app_name))
    }

    @Test
    fun homeToSettingsNavigationAndDarkLightThemeWindowBackgrounds() {
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()

        composeRule.onNodeWithTag("open_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_back_button").assertIsDisplayed()

        // Switch format SegmentedButtons cleanly
        composeRule.onNodeWithTag("format_chip_WEBP").performClick()
        composeRule.onNodeWithTag("format_chip_JPEG").performClick()

        // Disable Dynamic Color and switch to Dark mode, then verify dark window background (#121318)
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("dynamic_color_switch"))
        composeRule.onNodeWithTag("dynamic_color_switch").performClick()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("theme_chip_DARK"))
        composeRule.onNodeWithTag("theme_chip_DARK").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            (composeRule.activity.window.decorView.background as? ColorDrawable)?.color == Color(0xFF121318).toArgb()
        }
        val darkWindowBg = (composeRule.activity.window.decorView.background as? ColorDrawable)?.color
        assertEquals(Color(0xFF121318).toArgb(), darkWindowBg)

        // Navigate back Settings -> Home via top-bar back button
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()

        // Re-open Settings, switch to Light mode, verify light background (#F6F6FB), and return via system back
        composeRule.onNodeWithTag("open_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("theme_chip_LIGHT"))
        composeRule.onNodeWithTag("theme_chip_LIGHT").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            (composeRule.activity.window.decorView.background as? ColorDrawable)?.color == Color(0xFFF6F6FB).toArgb()
        }
        val lightWindowBg = (composeRule.activity.window.decorView.background as? ColorDrawable)?.color
        assertEquals(Color(0xFFF6F6FB).toArgb(), lightWindowBg)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()
    }

    @Test
    fun queueStateStorePersistsAndRecoversInterruptedProcessingItems() {
        val store = QueueStateStore(context)
        val items = listOf(
            PdfQueueItem(
                id = "1",
                uriString = "content://pdfs/1",
                fileName = "vol01.pdf",
                fileSizeBytes = 1024L,
                status = QueueItemStatus.PROCESSING,
                errorMessage = "stale"
            ),
            PdfQueueItem(
                id = "2",
                uriString = "content://pdfs/2",
                fileName = "vol02.pdf",
                fileSizeBytes = 2048L,
                status = QueueItemStatus.COMPLETED,
                outputFileName = "vol02.cbz",
                pageCount = 240,
                durationMillis = 4200L
            )
        )
        store.saveQueue(items)

        val loaded = store.loadQueue()
        assertEquals(2, loaded.size)
        assertEquals(QueueItemStatus.PENDING, loaded[0].status)
        assertEquals(null, loaded[0].errorMessage)
        assertEquals(QueueItemStatus.COMPLETED, loaded[1].status)
        assertEquals(4200L, loaded[1].durationMillis)
    }

    @Test
    fun queueStateStorePersistsHistoryWithDuration() {
        val store = QueueStateStore(context)
        val entry = ConversionHistoryEntry(
            id = "hist-1",
            sourceFileName = "manga.pdf",
            outputFileName = "manga.cbz",
            outputUriString = "content://cbz/manga",
            outputFolderName = "CBZ_Out",
            pageCount = 300,
            outputSizeBytes = 42_000_000L,
            imageFormat = ImageFormat.JPEG,
            dpi = 150,
            timestampMillis = 1_700_000_000_000L,
            success = true,
            durationMillis = 5_300L
        )
        store.saveHistory(listOf(entry))

        val loaded = store.loadHistory()
        assertEquals(1, loaded.size)
        assertEquals(entry, loaded.first())
    }
}
