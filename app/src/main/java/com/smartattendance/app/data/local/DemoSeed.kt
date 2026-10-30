package com.smartattendance.app.data.local

import androidx.room.withTransaction
import com.smartattendance.app.domain.model.*
import java.time.*

object DemoSeed {
    const val ADMIN = "00000000-0000-0000-0000-000000000001"
    const val LECTURER = "00000000-0000-0000-0000-000000000002"
    const val LECTURER_TWO = "00000000-0000-0000-0000-000000000003"
    fun studentId(n: Int) = "10000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    fun classId(n: Int) = "20000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    suspend fun populate(db: AttendanceDatabase, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val dao = db.dao()
        if (dao.profiles().isNotEmpty()) return@withTransaction
        listOf(Profile(ADMIN,"Quản trị Demo","admin@example.invalid","ADMIN"), Profile(LECTURER,"Giảng viên Minh Anh","minhanh@example.invalid"), Profile(LECTURER_TWO,"Giảng viên Hoàng Nam","hoangnam@example.invalid")).forEach { dao.save(ProfileEntity(it)) }
        val names = listOf("Nguyễn Minh An", "Trần Hoài Anh", "Lê Ngọc Ánh", "Phạm Gia Bảo", "Hoàng Thanh Bình", "Vũ Khánh Chi", "Đặng Minh Châu", "Bùi Quốc Cường", "Đỗ Hải Đăng", "Hồ Thùy Dương", "Ngô Minh Đức", "Dương Quang Duy", "Lý Thanh Giang", "Nguyễn Thu Hà", "Trần Gia Hân", "Lê Đức Hiếu", "Phạm Quang Huy", "Hoàng Ngọc Khánh", "Vũ Bảo Khang", "Đặng Phương Linh", "Bùi Nhật Minh", "Đỗ Hoàng Nam", "Hồ Yến Nhi", "Ngô Hải Phong", "Dương Minh Quân", "Lý Anh Thư", "Nguyễn Bảo Trâm", "Trần Minh Triết", "Lê Thanh Tú", "Phạm Hải Yến")
        names.forEachIndexed { i, name ->
            val n = i + 1; val code = "20261${n.toString().padStart(4, '0')}"
            dao.save(StudentEntity(Student(studentId(n), code, "$name (mẫu)", status=if(n==30) "INACTIVE" else "ACTIVE", created_at=now)))
            dao.save(CardEntity(StudentCard("30000000-0000-0000-0000-${n.toString().padStart(12,'0')}",studentId(n),"STUDENT_$code",created_at=now)))
        }
        listOf(Triple("CNTT01","Lập trình Android",LECTURER),Triple("CNTT02","Cơ sở dữ liệu",LECTURER),Triple("CNTT03","Kỹ thuật phần mềm",LECTURER_TWO)).forEachIndexed { index, item ->
            val n=index+1; val cid=classId(n)
            dao.save(ClassEntity(SchoolClass(cid,item.first,"Công nghệ thông tin ${n.toString().padStart(2,'0')}",item.second,item.third,created_at=now)))
            val members = when(n) { 1 -> 1..20; 2 -> 11..29; else -> 21..29 }
            members.forEach { sid -> dao.save(EnrollmentEntity(Enrollment("$cid-${studentId(sid)}",cid,studentId(sid),created_at=now))) }
            val start = now - 86_400_000
            val session=AttendanceSession("40000000-0000-0000-0000-${n.toString().padStart(12,'0')}",cid,"Buổi mẫu • Tuần 1",Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate().toString(),start,start+900_000,start+5_400_000,"CLOSED",item.third,now,subject_name=item.second,lecturer_id=item.third)
            dao.save(SessionEntity(session))
            members.forEachIndexed { i,sid ->
                dao.insert(RosterEntity(RosterEntry(session.id, studentId(sid))))
                if(i<10) dao.insert(RecordEntity(AttendanceRecord("sample-$n-$sid",session.id,studentId(sid),if(i<7) "PRESENT" else "LATE",start+if(i<7) 120_000 else 1_000_000,item.third,"SEED","Bản ghi mẫu để trình diễn",now)))
            }
        }
        dao.save(CardEntity(StudentCard("30000000-0000-0000-0000-000000000031",studentId(1),"STUDENT-202610001","CODE_39",created_at=now)))
        dao.save(CardEntity(StudentCard("30000000-0000-0000-0000-000000000032",studentId(2),"5901234123457","EAN_13",created_at=now)))
        val open=AttendanceSession("40000000-0000-0000-0000-000000000004",classId(1),"Thực hành quét thẻ",Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(),now-60_000,now+900_000,now+7_200_000,"OPEN",LECTURER,now,subject_name="Lập trình Android",lecturer_id=LECTURER)
        dao.save(SessionEntity(open))
        (1..20).forEach { dao.insert(RosterEntity(RosterEntry(open.id,studentId(it)))) }
    }
}
