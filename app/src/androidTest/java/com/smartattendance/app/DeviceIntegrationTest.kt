package com.smartattendance.app

import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.smartattendance.app.data.local.*
import com.smartattendance.app.data.repository.LocalAttendanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    @Test fun mlKitDecodesAllSupportedFormats() {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val samples=listOf("202610001.png" to "STUDENT_202610001","code128-202610001.png" to "STUDENT_202610001","code39-202610001.png" to "STUDENT-202610001","ean13-202610002.png" to "5901234123457")
        val scanner=BarcodeScanning.getClient()
        try {samples.forEach {(file,raw)->val bitmap=assets.open(file).use {BitmapFactory.decodeStream(it)};val results=Tasks.await(scanner.process(InputImage.fromBitmap(bitmap,0)),20,TimeUnit.SECONDS);assertTrue("Could not decode $file",results.any {it.rawValue==raw});bitmap.recycle()}}
        finally {scanner.close()}
    }
    @Test fun roomPersistsAcrossDatabaseReopen()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="persistence-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,AttendanceDatabase::class.java,name).addMigrations(AttendanceDatabase.MIGRATION_1_2).build()
        try {
            DemoSeed.populate(db)
            val session="40000000-0000-0000-0000-000000000004"
            assertEquals("PRESENT",LocalAttendanceRepository(db,DemoSeed.ADMIN).scan(session,"STUDENT_202610001").code)
            db.close();db=Room.databaseBuilder(context,AttendanceDatabase::class.java,name).addMigrations(AttendanceDatabase.MIGRATION_1_2).build()
            assertEquals("DUPLICATE",LocalAttendanceRepository(db,DemoSeed.ADMIN).scan(session,"STUDENT_202610001").code)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
