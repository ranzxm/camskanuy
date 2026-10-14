package com.ranzxm.camskanuy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun documentsScreenSurvivesActivityRecreation() {
        compose.onNodeWithText(compose.activity.getString(R.string.doc_import)).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(compose.activity.getString(R.string.doc_import)).assertIsDisplayed()
    }

    @Test
    fun savedDocumentOpensPreviewAndPageArrangement() {
        val app = compose.activity.application as CamskanuyApp
        val id = runBlocking {
            val source = app.imageProcessor.newCaptureFile()
            val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            } finally { bitmap.recycle() }
            try {
                val documentId = app.repository.createDraft()
                app.repository.addPage(documentId, source, source, source, "0,0;1,0;1,1;0,1", 0, "ORIGINAL")
                app.repository.saveDocument(documentId, "Compose test document")
                documentId
            } finally { app.imageProcessor.deleteTemporary(source) }
        }
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText("Compose test document")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Compose test document").performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.doc_export)).assertIsDisplayed()
            screenshot("detail")
            compose.onNodeWithText(compose.activity.getString(R.string.doc_edit_pages)).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.doc_save_document)).assertIsDisplayed()
            screenshot("pages")
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.doc_edit)).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText(compose.activity.getString(R.string.scan_save_changes)).and(isEnabled()))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            screenshot("editor")
        } finally { runBlocking { app.repository.deleteDocument(id) } }
    }

    private fun screenshot(screen: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/camskanuy-$screen.png")
            .use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
    }
}
