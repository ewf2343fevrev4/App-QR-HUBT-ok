package com.smartattendance.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smartattendance.app.data.local.*
import com.smartattendance.app.data.repository.LocalAttendanceRepository
import com.smartattendance.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class LocalRepositoryTest {
    private lateinit var db: AttendanceDatabase
    private lateinit var repo: LocalAttendanceRepository
    private val now=1_700_000_000_000L
    private val sessionId="40000000-0000-0000-0000-000000000004"
    @Before fun setup()=runBlocking {db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AttendanceDatabase::class.java).build();DemoSeed.populate(db,now);repo=LocalAttendanceRepository(db,DemoSeed.ADMIN) {now}}
    @After fun teardown() {db.close()}
    @Test fun `seed and scan mappings persist and reject duplicates`()=runBlocking {
        assertEquals(30,repo.snapshot().students.size)
        // Raw MASV without STUDENT_ prefix records attendance successfully
        assertEquals("PRESENT",repo.scan(sessionId,"202610001").code)
        // Re-scanning with STUDENT_ prefix returns DUPLICATE for the same student
        assertEquals("DUPLICATE",repo.scan(sessionId,"STUDENT_202610001").code)
        assertEquals("INVALID_CARD",repo.scan(sessionId,"unknown").code)
        assertEquals("WRONG_CLASS",repo.scan(sessionId,"STUDENT_202610025").code)
        assertEquals("INACTIVE_STUDENT",repo.scan(sessionId,"STUDENT_202610030").code)
        assertEquals(1,repo.snapshot().records.count {it.session_id==sessionId})
    }
    @Test fun `concurrent requests produce one record`()=runBlocking {
        val results=coroutineScope {(1..20).map {async(Dispatchers.Default) {repo.scan(sessionId,"STUDENT_202610001")}}.awaitAll()}
        assertEquals(1,results.count {it.code=="PRESENT"});assertEquals(19,results.count {it.code=="DUPLICATE"})
    }
    @Test fun `late close audit and permissions`()=runBlocking {
        val lateRepo=LocalAttendanceRepository(db,DemoSeed.ADMIN) {now+900_000}
        assertEquals("LATE",lateRepo.scan(sessionId,"STUDENT_202610001").code)
        repo.adjust(sessionId,DemoSeed.studentId(1),"EXCUSED","Có giấy phép")
        assertTrue(repo.snapshot().logs.any {it.action=="ADJUST_ATTENDANCE" && it.reason=="Có giấy phép"})
        repo.setSessionStatus(sessionId,"CLOSED")
        assertEquals("SESSION_CLOSED",repo.scan(sessionId,"STUDENT_202610002").code)
        val other=LocalAttendanceRepository(db,DemoSeed.LECTURER_TWO) {now}
        assertEquals(3,other.snapshot().classes.size)
        assertEquals(1,other.snapshot().sessions.size)
        assertEquals("FORBIDDEN",other.scan(sessionId,"STUDENT_202610002").code)
    }
    @Test fun `roster remains stable after enrollment change`()=runBlocking {
        val enrollment=repo.snapshot().enrollments.first {it.class_id==DemoSeed.classId(1)}
        repo.administer("enrollments",Json.encodeToJsonElement(enrollment.copy(status="INACTIVE")).jsonObject)
        assertEquals(20,repo.snapshot().rows(sessionId).size)
    }
    @Test fun `failed adjustment rolls back without audit`()=runBlocking {
        val count=repo.snapshot().logs.size
        assertTrue(runCatching {repo.adjust(sessionId,DemoSeed.studentId(1),"PRESENT","")}.isFailure)
        assertEquals(count,repo.snapshot().logs.size);assertEquals(0,repo.snapshot().records.count {it.session_id==sessionId})
    }
    @Test fun `scheduled session snapshots members and transitions once`()=runBlocking {
        val s=AttendanceSession("test-created",DemoSeed.classId(1),"Buổi kiểm thử","2026-10-26",now-1000,now+60000,now+3600000,"SCHEDULED",DemoSeed.ADMIN,subject_name="Lập trình Android",lecturer_id=DemoSeed.LECTURER)
        repo.createSession(s)
        assertEquals(20,repo.snapshot().rows(s.id).size)
        assertEquals("SESSION_NOT_OPEN",repo.scan(s.id,"STUDENT_202610001").code)
        repo.setSessionStatus(s.id,"OPEN")
        assertEquals("PRESENT",repo.scan(s.id,"STUDENT_202610001").code)
        repo.setSessionStatus(s.id,"CLOSED")
        assertTrue(runCatching {repo.setSessionStatus(s.id,"OPEN")}.isFailure)
        assertTrue(repo.snapshot().logs.any {it.action=="CREATE_SESSION" && it.entity_id==s.id})
    }
    @Test fun `database constraint rejects bypass duplicate and unknown roster`()=runBlocking {
        val result=repo.scan(sessionId,"STUDENT_202610001")
        assertTrue(runCatching {db.dao().insert(RecordEntity(result.record!!.copy(id="duplicate-id")))}.isFailure)
        assertTrue(runCatching {db.dao().insert(RecordEntity(result.record!!.copy(id="outside-id",student_id=DemoSeed.studentId(25))))}.isFailure)
        assertEquals(1,repo.snapshot().records.count {it.session_id==sessionId})
    }
    @Test fun `card revocation manual absence and history are consistent`()=runBlocking {
        repo.scan(sessionId,"STUDENT_202610001")
        repo.adjust(sessionId,DemoSeed.studentId(1),"ABSENT","Ghi nhận nhầm thẻ")
        assertEquals("ABSENT",repo.snapshot().rows(sessionId).first {it.student.id==DemoSeed.studentId(1)}.status)
        val card=repo.snapshot().cards.first {it.card_code=="STUDENT_202610001"}
        repo.administer("student_cards",Json.encodeToJsonElement(card.copy(is_active=false)).jsonObject)
        assertEquals("INVALID_CARD",repo.scan(sessionId,"STUDENT_202610001").code)
        assertNull(repo.scan(sessionId,"STUDENT_202610025").student)
    }
    @Test fun `server-side pagination returns correct page slices and clamps page sizes`()=runBlocking {
        val page1 = repo.getStudentsPage(page = 1, pageSize = 10)
        assertEquals(1, page1.page)
        assertEquals(10, page1.pageSize)
        assertEquals(30, page1.totalItems)
        assertEquals(3, page1.totalPages)
        assertEquals(10, page1.items.size)
        assertTrue(page1.hasNextPage)
        assertFalse(page1.hasPreviousPage)
        assertEquals("202610001", page1.items.first().student_code)

        val page2 = repo.getStudentsPage(page = 2, pageSize = 10)
        assertEquals(2, page2.page)
        assertEquals(10, page2.items.size)
        assertTrue(page2.hasNextPage)
        assertTrue(page2.hasPreviousPage)
        assertEquals("202610011", page2.items.first().student_code)

        val page3 = repo.getStudentsPage(page = 3, pageSize = 10)
        assertEquals(3, page3.page)
        assertEquals(10, page3.items.size)
        assertFalse(page3.hasNextPage)
        assertTrue(page3.hasPreviousPage)
        assertEquals("202610021", page3.items.first().student_code)

        val clamped = repo.getStudentsPage(page = 1, pageSize = 500)
        assertEquals(100, clamped.pageSize)
        assertEquals(30, clamped.items.size)
    }
    @Test fun `search by code name or class returns filtered pagination`()=runBlocking {
        val searchCode = repo.getStudentsPage(page = 1, pageSize = 10, search = "202610005")
        assertEquals(1, searchCode.totalItems)
        assertEquals("202610005", searchCode.items.first().student_code)

        val searchClass = repo.getStudentsPage(page = 1, pageSize = 10, classCode = "CNTT01")
        assertTrue(searchClass.totalItems > 0)
        assertTrue(searchClass.items.all { it.class_code.contains("CNTT01") })

        val firstStudent = searchCode.items.first()
        val fetched = repo.getStudentById(firstStudent.id)
        assertNotNull(fetched)
        assertEquals("202610005", fetched?.student_code)
    }
    @Test fun `non-admin cannot access getStudentsPage`()=runBlocking {
        val lecturerRepo = LocalAttendanceRepository(db, DemoSeed.LECTURER) { now }
        val result = runCatching { lecturerRepo.getStudentsPage(page = 1, pageSize = 10) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("ADMIN") == true)
    }
}
