package com.smartattendance.app

import com.smartattendance.app.core.StudentCsvImporter
import com.smartattendance.app.domain.model.Student
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StudentCsvImporterTest {

    private fun resolveTinhoc28File(): File {
        val root = System.getProperty("projectRoot")?.let { File(it, "sample-data/tinhoc28.csv") }
        if (root != null && root.exists()) return root
        val direct = File("sample-data/tinhoc28.csv")
        if (direct.exists()) return direct
        val parent = File("../sample-data/tinhoc28.csv")
        if (parent.exists()) return parent
        error("Could not find sample-data/tinhoc28.csv")
    }

    @Test
    fun `parse tinhoc28 csv yields exactly 1084 students and 35 distinct classes`() {
        val file = resolveTinhoc28File()
        assertTrue("sample-data/tinhoc28.csv should exist", file.exists())

        val (validRows, errors) = StudentCsvImporter.parse(file.inputStream())
        assertEquals("Should parse exactly 1084 students", 1084, validRows.size)
        assertEquals("Should have 0 errors for clean sample file", 0, errors.size)

        val classes = validRows.map { it.lop }.distinct().sorted()
        assertEquals("Should have exactly 35 distinct classes", 35, classes.size)

        val pmClasses = classes.filter { it.startsWith("PM28") }
        val udClasses = classes.filter { it.startsWith("UD28") }
        assertEquals("Should have 22 PM28 classes", 22, pmClasses.size)
        assertEquals("Should have 13 UD28 classes", 13, udClasses.size)

        // First student
        val first = validRows.first()
        assertEquals("2823250124", first.masv)
        assertEquals("Trần Quốc Bảo", first.hoTen)
        assertEquals("PM28.01", first.lop)

        // Last student
        val last = validRows.last()
        assertEquals("2823215211", last.masv)
        assertEquals("Vũ Công Vinh", last.hoTen)
        assertEquals("UD28.13", last.lop)
    }

    @Test
    fun `csv parser handles empty lines quotes and utf8 bom`() {
        val sampleWithBomAndEmptyLines = "\uFEFFMASV,HoTen,Lop\r\n" +
                ",,\r\n" +
                "  \r\n" +
                "2823000001,\"Nguyễn Văn An, Jr.\",PM28.01\r\n" +
                "2823000002,Trần Thị Bình,UD28.01\r\n"

        val (validRows, errors) = StudentCsvImporter.parse(sampleWithBomAndEmptyLines)
        assertEquals(2, validRows.size)
        assertEquals(0, errors.size)
        assertEquals("Nguyễn Văn An, Jr.", validRows[0].hoTen)
        assertEquals("2823000001", validRows[0].masv)
    }

    @Test
    fun `csv parser reports missing fields and internal duplicates`() {
        val badCsv = "MASV,HoTen,Lop\n" +
                ",Nguyễn Không Mã,PM28.01\n" +
                "2823000003,,PM28.01\n" +
                "2823000004,Lê Thị Có Lớp,\n" +
                "2823000005,Trần Trùng,PM28.01\n" +
                "2823000005,Trần Trùng Lặp Lại,PM28.02\n"

        val (validRows, errors) = StudentCsvImporter.parse(badCsv)
        assertEquals(1, validRows.size) // Only 2823000005 first occurrence is valid
        assertEquals(4, errors.size) // 3 missing fields + 1 duplicate
        assertTrue(errors.any { it.contains("Thiếu Mã sinh viên") })
        assertTrue(errors.any { it.contains("Thiếu Họ tên") })
        assertTrue(errors.any { it.contains("Thiếu Lớp") })
        assertTrue(errors.any { it.contains("bị lặp lại") })
    }

    @Test
    fun `buildPreview accurately computes new and existing student counts`() {
        val (validRows, errors) = StudentCsvImporter.parse(
            "MASV,HoTen,Lop\n" +
                    "202610001,Sinh Viên Cũ,CNTT01\n" +
                    "2823999999,Sinh Viên Mới,PM28.01\n"
        )
        val existing = listOf(
            Student("old-1", "202610001", "Sinh Viên Cũ")
        )

        val preview = StudentCsvImporter.buildPreview(validRows, errors, existing, emptyList())
        assertEquals(2, preview.totalRows)
        assertEquals(1, preview.newCount)
        assertEquals(1, preview.existingCount)
        assertEquals(listOf("CNTT01", "PM28.01"), preview.detectedClasses)
    }
}

