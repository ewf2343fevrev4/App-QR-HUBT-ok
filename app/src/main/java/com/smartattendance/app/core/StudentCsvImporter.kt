package com.smartattendance.app.core

import com.smartattendance.app.domain.model.ImportPreview
import com.smartattendance.app.domain.model.ImportStudentRow
import com.smartattendance.app.domain.model.SchoolClass
import com.smartattendance.app.domain.model.Student
import java.io.InputStream
import java.nio.charset.StandardCharsets

object StudentCsvImporter {

    fun parse(inputStream: InputStream): Pair<List<ImportStudentRow>, List<String>> {
        val text = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return parse(text)
    }

    fun parse(rawText: String): Pair<List<ImportStudentRow>, List<String>> {
        val content = rawText.removePrefix("\uFEFF").trim()
        if (content.isBlank()) return Pair(emptyList(), listOf("Tệp CSV trống."))

        val rawLines = content.split(Regex("\r\n|\r|\n"))
        if (rawLines.isEmpty()) return Pair(emptyList(), listOf("Tệp CSV trống."))

        var headerFound = false
        var masvIdx = 0
        var hoTenIdx = 1
        var lopIdx = 2
        var dataStartLine = 0

        // Find header
        for (i in rawLines.indices) {
            val line = rawLines[i].trim()
            if (line.isBlank() || line.all { it == ',' }) continue
            val tokens = splitCsvLine(line).map { normalizeHeader(it) }
            val m = tokens.indexOfFirst { it in setOf("MASV", "MA_SV", "MASINHVIEN", "STUDENTCODE", "CODE") }
            val h = tokens.indexOfFirst { it in setOf("HOTEN", "HO_TEN", "HOVATEN", "FULLNAME", "NAME") }
            val l = tokens.indexOfFirst { it in setOf("LOP", "CLASS", "CLASSCODE") }

            if (m != -1 && h != -1 && l != -1) {
                masvIdx = m
                hoTenIdx = h
                lopIdx = l
                headerFound = true
                dataStartLine = i + 1
                break
            } else if (!headerFound && tokens.size >= 3) {
                // If line 0 doesn't match known keywords, assume standard 0=MASV, 1=HoTen, 2=Lop
                masvIdx = 0
                hoTenIdx = 1
                lopIdx = 2
                headerFound = true
                dataStartLine = i + 1
                break
            }
        }

        val validRows = mutableListOf<ImportStudentRow>()
        val errorLines = mutableListOf<String>()
        val seenMasvInFile = mutableSetOf<String>()

        for (i in dataStartLine until rawLines.size) {
            val lineNum = i + 1
            val line = rawLines[i].trim()
            // Skip entirely blank or comma-only rows
            if (line.isBlank() || line.all { it == ',' }) continue

            val tokens = splitCsvLine(line)
            if (tokens.size <= maxOf(masvIdx, hoTenIdx, lopIdx)) {
                errorLines.add("Dòng $lineNum: Thiếu cột dữ liệu (cần ít nhất 3 cột: MASV, Họ tên, Lớp).")
                continue
            }

            val masv = tokens[masvIdx].trim()
            val hoTen = tokens[hoTenIdx].trim()
            val lop = tokens[lopIdx].trim()

            // Skip if completely blank
            if (masv.isBlank() && hoTen.isBlank() && lop.isBlank()) continue

            when {
                masv.isBlank() -> errorLines.add("Dòng $lineNum: Thiếu Mã sinh viên (MASV).")
                hoTen.isBlank() -> errorLines.add("Dòng $lineNum: Thiếu Họ tên sinh viên.")
                lop.isBlank() -> errorLines.add("Dòng $lineNum: Thiếu Lớp.")
                seenMasvInFile.contains(masv) -> {
                    errorLines.add("Dòng $lineNum: MASV '$masv' bị lặp lại trong tệp CSV.")
                }
                else -> {
                    seenMasvInFile.add(masv)
                    validRows.add(ImportStudentRow(masv = masv, hoTen = hoTen, lop = lop, line = lineNum))
                }
            }
        }

        return Pair(validRows, errorLines)
    }

    fun buildPreview(
        validRows: List<ImportStudentRow>,
        errorLines: List<String>,
        existingStudents: List<Student>,
        existingClasses: List<SchoolClass>
    ): ImportPreview {
        val existingCodes = existingStudents.map { it.student_code }.toSet()
        val newCount = validRows.count { it.masv !in existingCodes }
        val existingCount = validRows.count { it.masv in existingCodes }
        val detectedClasses = validRows.map { it.lop }.distinct().sorted()

        return ImportPreview(
            totalRows = validRows.size,
            validRows = validRows,
            errorRows = errorLines,
            newCount = newCount,
            existingCount = existingCount,
            detectedClasses = detectedClasses
        )
    }

    private fun normalizeHeader(text: String): String {
        return text.trim()
            .uppercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("Đ", "D")
            .replace("Á", "A").replace("À", "A").replace("Ả", "A").replace("Ã", "A").replace("Ạ", "A")
            .replace("Â", "A").replace("Ấ", "A").replace("Ầ", "A").replace("Ẩ", "A").replace("Ẫ", "A").replace("Ậ", "A")
            .replace("Ă", "A").replace("Ắ", "A").replace("Ằ", "A").replace("Ẳ", "A").replace("Ẵ", "A").replace("Ặ", "A")
            .replace("É", "E").replace("È", "E").replace("Ẻ", "E").replace("Ẽ", "E").replace("Ẹ", "E")
            .replace("Ê", "E").replace("Ế", "E").replace("Ề", "E").replace("Ể", "E").replace("Ễ", "E").replace("Ệ", "E")
            .replace("Í", "I").replace("Ì", "I").replace("Ỉ", "I").replace("Ĩ", "I").replace("Ị", "I")
            .replace("Ó", "O").replace("Ò", "O").replace("Ỏ", "O").replace("Õ", "O").replace("Ọ", "O")
            .replace("Ô", "O").replace("Ố", "O").replace("Ồ", "O").replace("Ổ", "O").replace("Ỗ", "O").replace("Ộ", "O")
            .replace("Ơ", "O").replace("Ớ", "O").replace("Ờ", "O").replace("Ở", "O").replace("Ỡ", "O").replace("Ợ", "O")
            .replace("Ú", "U").replace("Ù", "U").replace("Ủ", "U").replace("Ũ", "U").replace("Ụ", "U")
            .replace("Ư", "U").replace("Ứ", "U").replace("Ừ", "U").replace("Ử", "U").replace("Ữ", "U").replace("Ự", "U")
            .replace("Ý", "Y").replace("Ỳ", "Y").replace("Ỷ", "Y").replace("Ỹ", "Y").replace("Ỵ", "Y")
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                        current.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }
}

