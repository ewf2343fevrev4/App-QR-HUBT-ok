package com.smartattendance.app

import com.google.zxing.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartattendance.app.data.local.AttendanceDatabase
import com.smartattendance.app.data.local.DemoSeed
import com.smartattendance.app.core.CsvExporter
import kotlinx.coroutines.runBlocking
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class SampleDataTest {
    @Test fun `generate and decode printable samples`() {
        val root=File(System.getProperty("projectRoot"),"sample-data").apply {mkdirs()}
        val output=File(root,"qr-codes").apply {mkdirs()}
        val writer=MultiFormatWriter()
        fun code(raw: String,format: BarcodeFormat,name: String) {
            val matrix=writer.encode(raw,format,if(format==BarcodeFormat.QR_CODE) 400 else 1000,if(format==BarcodeFormat.QR_CODE) 400 else 280,mapOf(EncodeHintType.MARGIN to 20))
            val file=File(output,name)
            val pixels=IntArray(matrix.width*matrix.height) {index -> if(matrix[index%matrix.width,index/matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()}
            val bitmap=Bitmap.createBitmap(pixels,matrix.width,matrix.height,Bitmap.Config.ARGB_8888)
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            val read=BitmapFactory.decodeFile(file.absolutePath);val readPixels=IntArray(read.width*read.height);read.getPixels(readPixels,0,read.width,0,0,read.width,read.height)
            val decoded=MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(read.width,read.height,readPixels))))
            read.recycle()
            assertEquals(raw,decoded.text)
        }
        val students=(1..30).map {"20261${it.toString().padStart(4,'0')}"}
        students.forEach {code("STUDENT_$it",BarcodeFormat.QR_CODE,"$it.png")}
        code("STUDENT_202610001",BarcodeFormat.CODE_128,"code128-202610001.png")
        code("STUDENT-202610001",BarcodeFormat.CODE_39,"code39-202610001.png")
        code("5901234123457",BarcodeFormat.EAN_13,"ean13-202610002.png")
        code("UNKNOWN_CARD",BarcodeFormat.QR_CODE,"invalid-card.png")
        File(root,"qr-test-data.csv").writeText("\uFEFFstudent_code,raw_value,format,expected_CNTT01\r\n"+students.mapIndexed {i,s->"$s,STUDENT_$s,QR_CODE,${when {i==29->"INACTIVE_STUDENT";i>=20->"WRONG_CLASS";else->"PRESENT_OR_LATE"}}"}.joinToString("\r\n")+"\r\n202610001,STUDENT-202610001,CODE_39,PRESENT_OR_LATE\r\n202610002,5901234123457,EAN_13,PRESENT_OR_LATE\r\n,UNKNOWN_CARD,QR_CODE,INVALID_CARD\r\n",Charsets.UTF_8)
        val database=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AttendanceDatabase::class.java).build()
        val profiles=try {runBlocking {DemoSeed.populate(database);database.dao().students().map {it.value}}} finally {database.close()}
        File(root,"students.csv").writeText("\uFEFFstudent_code,full_name,status\r\n"+profiles.sortedBy {it.student_code}.joinToString("\r\n") {listOf(it.student_code,it.full_name,it.status).joinToString(",",transform=CsvExporter::cell)}+"\r\n",Charsets.UTF_8)
        File(root,"classes.csv").writeText("\uFEFFclass_code,class_name,subject_name,lecturer\r\nCNTT01,Công nghệ thông tin 01,Lập trình Android,Minh Anh\r\nCNTT02,Công nghệ thông tin 02,Cơ sở dữ liệu,Minh Anh\r\nCNTT03,Công nghệ thông tin 03,Kỹ thuật phần mềm,Hoàng Nam\r\n",Charsets.UTF_8)
        File(root,"enrollments.csv").writeText("\uFEFFclass_code,student_code\r\n"+(listOf("CNTT01" to 1..20,"CNTT02" to 11..29,"CNTT03" to 21..29).flatMap {(cls,range)->range.map {"$cls,${students[it-1]}"}}).joinToString("\r\n")+"\r\n",Charsets.UTF_8)
        File(root,"print-cards.html").writeText("""<!doctype html><html lang="vi"><meta charset="utf-8"><title>Thẻ mẫu Smart Attendance</title><style>body{font:16px Arial;background:#f4f7fb;color:#173756;padding:24px}main{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}article{background:white;border:1px solid #ccd7e3;border-radius:16px;text-align:center;padding:16px;break-inside:avoid}img{width:180px;max-width:100%}h1{font-size:28px}small{display:block;margin:8px}@media print{body{padding:0;background:white}article{border-radius:0}h1,p{display:none}}</style><h1>Smart Attendance · Thẻ sinh viên mẫu</h1><p>Dữ liệu giả lập. Mở Demo, chọn CNTT01 → Thực hành quét thẻ. Thẻ 21–29 sai lớp, thẻ 30 bị vô hiệu hóa. In trang này hoặc mở từng ảnh PNG.</p><main>"""+students.joinToString("") {"<article><b>THẺ SINH VIÊN • DEMO</b><small>$it</small><img src=\"qr-codes/$it.png\" alt=\"QR $it\"><small>STUDENT_$it</small></article>"}+"</main></html>",Charsets.UTF_8)
    }
}
