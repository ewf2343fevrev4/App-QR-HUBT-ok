package com.smartattendance.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.smartattendance.app.data.local.DemoSeed
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun waitFor(text: String) {compose.waitUntil(20000) {runCatching {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}}
    private fun screenshot(name: String) {val app=InstrumentationRegistry.getInstrumentation().targetContext;val file=File(app.getExternalFilesDir("test-evidence"),"$name.png");file.outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}}
    @Test fun loginDemoReportsAdminAndTheme() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SmartAttendanceApp
        app.preferences.edit().clear().commit()
        runBlocking {app.database.clearAllTables()}
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Vào Demo • Quản trị viên");screenshot("01-login")
            compose.onNodeWithText("Vào Demo • Quản trị viên").performScrollTo().performClick()
            waitFor("KHÔNG GIAN GIẢNG DẠY");screenshot("02-dashboard")
            compose.onAllNodesWithText("Báo cáo").onLast().performClick()
            waitFor("Báo cáo chuyên cần");screenshot("03-reports")
            compose.onNodeWithContentDescription("Cài đặt").performClick()
            waitFor("Quản trị dữ liệu");compose.onNodeWithText("Quản trị dữ liệu").performScrollTo().performClick()
            waitFor("Quản trị");screenshot("04-admin")
            compose.onNodeWithText("Thêm sinh viên").performClick();waitFor("Thêm dữ liệu")
            compose.onNodeWithText("Mã sinh viên").performTextInput("TEST2026")
            compose.onNodeWithText("Họ tên").performTextInput("Sinh viên kiểm thử")
            compose.onNodeWithText("Lưu").performClick();waitFor("Sinh viên kiểm thử")
            compose.onNodeWithContentDescription("Cài đặt").performClick();waitFor("Theo hệ thống")
            compose.onNodeWithText("Theo hệ thống").performClick();compose.onNodeWithText("Tối").performClick();compose.waitForIdle();screenshot("05-dark-settings")
        }
    }
    @Test fun cameraPermissionDenialThenPreview() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as SmartAttendanceApp
        app.preferences.edit().clear().putString("mode","DEMO").putString("actor",DemoSeed.ADMIN).commit()
        runBlocking {app.database.clearAllTables()}
        val device=UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("KHÔNG GIAN GIẢNG DẠY")
            compose.onNodeWithText("Quét thẻ").performClick();waitFor("Thực hành quét thẻ")
            compose.onNodeWithText("Thực hành quét thẻ").performClick()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val deny=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_deny_button"))),10000)
            if(deny!=null) {
                deny.click();waitFor("Cần quyền camera");screenshot("06-camera-denied")
                compose.onNodeWithText("Cấp quyền camera").performClick()
                compose.waitForIdle()
                val allow=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/permission_allow_foreground_only_button"))),10000)
                requireNotNull(allow) {"Camera permission prompt did not appear"}.click()
            }
            waitFor("Camera sẵn sàng");screenshot("07-camera-preview")
            compose.onNodeWithText("Camera sẵn sàng").assertExists()
        }
    }
    @Test fun createSessionAndExportThroughDocumentPicker() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as SmartAttendanceApp
        app.preferences.edit().clear().putString("mode","DEMO").putString("actor",DemoSeed.ADMIN).commit()
        runBlocking {app.database.clearAllTables()}
        val device=UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("KHÔNG GIAN GIẢNG DẠY")
            compose.onNodeWithText("Tạo buổi").performScrollTo().performClick();waitFor("Buổi học mới")
            compose.onNodeWithText("Tên buổi học").performTextInput("Buổi kiểm thử UI")
            compose.onNodeWithText("Tạo buổi học").performScrollTo().performClick();waitFor("Buổi kiểm thử UI")
            compose.onNodeWithText("Báo cáo và xuất CSV").performScrollTo().performClick();waitFor("Báo cáo chuyên cần")
            compose.onNodeWithText("Xuất báo cáo CSV").performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
            val save=device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile(".*:id/action_menu_select"))),10000)
                ?: device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)save"))),5000)
            if(save==null) {device.dumpWindowHierarchy(File(app.getExternalFilesDir("test-evidence"),"document-picker.xml"));error("Save button not found in Android document picker")}
            save.click()
            waitFor("Đã xuất CSV UTF-8. Có thể mở bằng Excel.")
            screenshot("08-csv-exported")
            val sessions=runBlocking {app.database.dao().sessions()}
            org.junit.Assert.assertTrue(sessions.any {it.value.session_name=="Buổi kiểm thử UI"})
        }
    }
}
