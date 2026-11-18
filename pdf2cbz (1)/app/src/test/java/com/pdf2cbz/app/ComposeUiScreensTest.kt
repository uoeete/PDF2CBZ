package com.pdf2cbz.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.pdf2cbz.app.data.model.AppSettings
import com.pdf2cbz.app.data.model.ConversionProgress
import com.pdf2cbz.app.data.model.PdfQueueItem
import com.pdf2cbz.app.data.model.QueueItemStatus
import com.pdf2cbz.app.data.model.ThemeMode
import com.pdf2cbz.app.ui.screens.CompletionScreen
import com.pdf2cbz.app.ui.screens.ConversionScreen
import com.pdf2cbz.app.ui.screens.FileSelectionScreen
import com.pdf2cbz.app.ui.theme.Pdf2CbzTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeUiScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h568dp")
    fun fileSelectionSupportsLongFilenamesAndBatchSelectionOnCompactScreen() {
        val longName = "Very_Long_Manga_Series_Title_Volume_042_Special_Edition_2026_HighRes_Scan.pdf"
        val items = listOf(
            PdfQueueItem(
                id = "a",
                uriString = "content://pdfs/a",
                fileName = longName,
                fileSizeBytes = 120_000_000L,
                status = QueueItemStatus.PENDING
            ),
            PdfQueueItem(
                id = "b",
                uriString = "content://pdfs/b",
                fileName = "manga_vol_02.pdf",
                fileSizeBytes = 85_000_000L,
                status = QueueItemStatus.PENDING
            )
        )
        val removed = mutableListOf<String>()

        composeRule.setContent {
            Pdf2CbzTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                FileSelectionScreen(
                    queue = items,
                    settings = AppSettings(outputTreeUri = "content://tree/cbz", outputTreeDisplayName = "Comics"),
                    onBack = {},
                    onAddMorePdfs = {},
                    onRemoveItem = { removed.add(it) },
                    onClearAll = {},
                    onStartConversion = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(2, composeRule.onAllNodesWithTag("queue_item_card").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("toggle_select_all_button").performClick()
        composeRule.onNodeWithTag("remove_selected_button").assertIsDisplayed().performClick()
        assertEquals(listOf("a", "b"), removed)
    }

    @Test
    fun conversionScreenDisplaysMetricsAndConfirmsCancel() {
        var cancelled = false
        composeRule.setContent {
            Pdf2CbzTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                ConversionScreen(
                    progress = ConversionProgress(
                        isRunning = true,
                        currentFileIndex = 2,
                        totalFiles = 10,
                        currentFileName = "Long_Title_Comic_Book_Vol_02.pdf",
                        currentPage = 120,
                        totalPages = 240,
                        completedCount = 1,
                        remainingCount = 9,
                        errorCount = 0,
                        lastModeNote = "直接抽出 · 高速ストリーミング",
                        elapsedMillis = 12_500L,
                        pagesPerSecond = 28.4f
                    ),
                    onPause = {},
                    onResume = {},
                    onCancel = { cancelled = true },
                    onFinishedNavigate = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("28.4 ページ/秒").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("12.5秒").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_conversion_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_cancel_conversion_button").assertIsDisplayed().performClick()
        assertTrue(cancelled)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp")
    fun completionScreenDisplaysSummaryDurationSpeedAndErrors() {
        composeRule.setContent {
            Pdf2CbzTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                CompletionScreen(
                    queue = listOf(
                        PdfQueueItem(
                            id = "1",
                            uriString = "content://1",
                            fileName = "vol01.pdf",
                            fileSizeBytes = 1000L,
                            status = QueueItemStatus.COMPLETED,
                            outputFileName = "vol01.cbz",
                            outputSizeBytes = 50_000_000L,
                            pageCount = 200,
                            extractedDirectPages = 200,
                            durationMillis = 5_000L
                        ),
                        PdfQueueItem(
                            id = "2",
                            uriString = "content://2",
                            fileName = "locked.pdf",
                            fileSizeBytes = 1000L,
                            status = QueueItemStatus.FAILED,
                            errorMessage = "パスワードで保護されたPDFのため変換できません。"
                        )
                    ),
                    settings = AppSettings(outputTreeUri = "content://tree/out", outputTreeDisplayName = "MangaCBZ"),
                    onOpenOutputFolder = {},
                    onRetryFailed = {},
                    onBackHome = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("40.0 P/秒").assertIsDisplayed()
        composeRule.onNodeWithTag("retry_failed_button").assertIsDisplayed()
        composeRule.onNodeWithTag("back_home_button").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("locked.pdf"))
        composeRule.onNodeWithText("locked.pdf").assertIsDisplayed()
    }
}
