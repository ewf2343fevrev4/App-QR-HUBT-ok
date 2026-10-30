package com.smartattendance.app.core

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.app.SmartAttendanceApp
import com.smartattendance.app.data.local.DemoSeed
import com.smartattendance.app.data.preferences.StudentPreferences
import com.smartattendance.app.data.repository.*
import com.smartattendance.app.domain.model.*
import com.smartattendance.app.domain.repository.AttendanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class AppState(val starting: Boolean=true, val busy: Boolean=false, val user: Profile?=null, val demo: Boolean=true, val data: Snapshot=Snapshot(), val message: String?=null, val theme: String="SYSTEM", val revision: Int=0)

data class StudentPaginationUiState(
    val items: List<StudentItem> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 10,
    val totalItems: Int = 0,
    val totalPages: Int = 1,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false,
    val searchQuery: String = "",
    val selectedClass: String = "Tất cả",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AppViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as SmartAttendanceApp
    private val mutable=MutableStateFlow(AppState(theme=app.preferences.getString("theme","SYSTEM") ?: "SYSTEM"))
    val state=mutable.asStateFlow()
    private var repository: AttendanceRepository?=null
    private val mutationMutex=Mutex()
    val onlineConfigured get()=app.api.configured

    val studentPreferences = StudentPreferences(app)
    private val _studentState = MutableStateFlow(StudentPaginationUiState())
    val studentState = _studentState.asStateFlow()

    private var studentFetchJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var currentRequestId = 0L

    init {
        viewModelScope.launch {
            studentPreferences.pageSizeFlow.collect { size ->
                _studentState.update { it.copy(pageSize = size) }
                if (repository != null && state.value.user?.role == "ADMIN") {
                    loadStudentsPage(page = 1, pageSize = size)
                }
            }
        }
        viewModelScope.launch {
            try {
                when(app.preferences.getString("mode",null)) {
                    "DEMO" -> enterDemoInternal(app.preferences.getString("actor",DemoSeed.ADMIN) ?: DemoSeed.ADMIN)
                    "ONLINE" -> if(app.api.restore()) enterOnline()
                }
            } catch(e: Exception) { if(e is CancellationException) throw e; mutable.update { it.copy(message=e.message) } }
            finally { mutable.update { it.copy(starting=false) } }
        }
    }
    private suspend fun enterDemoInternal(actor: String) {
        DemoSeed.populate(app.database)
        val repo=LocalAttendanceRepository(app.database,actor)
        val data=repo.snapshot(); val user=data.profiles.first { it.id==actor }
        repository=repo; app.preferences.edit().putString("mode","DEMO").putString("actor",actor).apply()
        mutable.update { it.copy(user=user,demo=true,data=data,revision=it.revision+1) }
        if (user.role == "ADMIN") {
            loadStudentsPage(page = 1)
        }
    }
    fun demo(actor: String=DemoSeed.ADMIN)=action { enterDemoInternal(actor) }
    fun login(email: String,password: String)=action {
        require(email.contains("@") && password.isNotBlank()) { "Vui lòng nhập email và mật khẩu hợp lệ." }
        app.api.login(email.trim(),password)
        try { enterOnline() } catch(e: Exception) { app.api.clear(); throw e }
    }
    private suspend fun enterOnline() {
        val repo=RemoteAttendanceRepository(app.api); val data=repo.snapshot()
        val user=data.profiles.firstOrNull { it.id==app.api.userId && it.is_active } ?: error("Tài khoản chưa được kích hoạt.")
        repository=repo; app.preferences.edit().putString("mode","ONLINE").apply()
        mutable.update { it.copy(user=user,demo=false,data=data,revision=it.revision+1) }
        if (user.role == "ADMIN") {
            loadStudentsPage(page = 1)
        }
    }
    suspend fun reload() {
        val data=repository?.snapshot() ?: return
        val user=data.profiles.find { it.id==state.value.user?.id && it.is_active } ?: error("Tài khoản không còn hoạt động. Hãy đăng xuất.")
        mutable.update { it.copy(data=data,user=user) }
    }
    fun refresh()=action { reload() }
    fun createSession(session: AttendanceSession,onDone: () -> Unit)=action { repo().createSession(session); reload(); onDone() }
    fun setSession(id: String,status: String)=action { repo().setSessionStatus(id,status); reload(); notify("Đã cập nhật trạng thái buổi học.") }
    fun adjust(sessionId: String,studentId: String,status: String,reason: String,onDone: () -> Unit)=action { repo().adjust(sessionId,studentId,status,reason); reload(); onDone(); notify("Đã lưu điều chỉnh và lịch sử thay đổi.") }
    fun administer(entity: String,payload: JsonObject,onDone: () -> Unit)=action { repo().administer(entity,payload); reload(); onDone(); notify("Đã lưu dữ liệu.") }
    suspend fun listActiveLecturers(): List<LecturerProfile> = repo().listActiveLecturers()
    fun saveStudentWithClass(masv: String, hoTen: String, classCode: String, mode: String = "UPDATE", onDone: () -> Unit) = action {
        repo().saveStudentWithClass(masv, hoTen, classCode, mode)
        reload()
        loadStudentsPage()
        onDone()
        notify("Đã lưu thông tin sinh viên $masv - lớp $classCode.")
    }
    fun importStudents(
        students: List<ImportStudentRow>,
        mappings: List<ClassMapping> = emptyList(),
        mode: String = "SKIP",
        onDone: (ImportResult) -> Unit
    ) = action {
        val result = repo().importStudents(students, mappings, mode)
        reload()
        loadStudentsPage(page = 1)
        onDone(result)
        notify("Nhập thành công: ${result.newStudents} mới, ${result.updatedStudents} cập nhật, ${result.newClasses} lớp mới, ${result.newEnrollments} phân lớp.")
    }
    fun importStudents(
        students: List<ImportStudentRow>,
        mode: String,
        onDone: (ImportResult) -> Unit
    ) = importStudents(students, emptyList(), mode, onDone)
    suspend fun readCsvFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        app.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            ?: error("Không thể mở tệp CSV đã chọn.")
    }
    suspend fun readSampleCsv(): String = withContext(Dispatchers.IO) {
        runCatching {
            app.assets.open("tinhoc28.csv").use { it.bufferedReader(Charsets.UTF_8).readText() }
        }.getOrElse {
            java.io.File("sample-data/tinhoc28.csv").takeIf { f -> f.exists() }?.readText(Charsets.UTF_8)
                ?: error("Không tìm thấy tệp tinhoc28.csv")
        }
    }
    suspend fun scan(sessionId: String,raw: String): ScanResult = mutationMutex.withLock {
        val result=repo().scan(sessionId,raw)
        // A committed scan remains successful even if the subsequent read fails.
        try { reload() } catch(e: Exception) { if(e is CancellationException) throw e; notify("Điểm danh đã phản hồi; chưa tải lại được danh sách. Hãy làm mới.") }
        result
    }
    fun export(uri: Uri,bytes: ByteArray)=action {
        withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri,"wt")?.use { it.write(bytes) } ?: error("Không mở được vị trí lưu.") }
        notify("Đã xuất CSV UTF-8. Có thể mở bằng Excel.")
    }
    fun resetDemo()=action {
        check(state.value.demo)
        withContext(Dispatchers.IO) { app.database.clearAllTables() }
        enterDemoInternal(DemoSeed.ADMIN); notify("Đã tạo lại dữ liệu demo.")
    }
    fun logout()=action {
        var failure: String?=null
        if(!state.value.demo) try { app.api.logout() } catch(e: Exception) { if(e is CancellationException) throw e; failure="Đã xóa phiên trên máy. Không thể thu hồi phiên máy chủ vì lỗi mạng." }
        app.preferences.edit().remove("mode").remove("actor").apply(); repository=null
        _studentState.value = StudentPaginationUiState()
        mutable.update { AppState(starting=false,theme=it.theme,message=failure,revision=it.revision+1) }
    }

    fun onSearchQueryChanged(query: String) {
        _studentState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(400)
            loadStudentsPage(page = 1, search = query)
        }
    }

    fun onClassFilterChanged(classCode: String) {
        _studentState.update { it.copy(selectedClass = classCode) }
        loadStudentsPage(page = 1, classCode = classCode)
    }

    fun setStudentPageSize(size: Int) {
        viewModelScope.launch {
            studentPreferences.setPageSize(size)
            loadStudentsPage(page = 1, pageSize = size)
        }
    }

    fun loadStudentsPage(
        page: Int? = null,
        pageSize: Int? = null,
        search: String? = null,
        classCode: String? = null
    ) {
        val repo = repository ?: return
        val targetPage = page ?: _studentState.value.page
        val targetSize = pageSize ?: _studentState.value.pageSize
        val targetSearch = search ?: _studentState.value.searchQuery
        val targetClass = classCode ?: _studentState.value.selectedClass

        studentFetchJob?.cancel()
        val requestId = ++currentRequestId

        studentFetchJob = viewModelScope.launch {
            _studentState.update {
                it.copy(isLoading = true, errorMessage = null, page = targetPage, pageSize = targetSize)
            }
            try {
                val result = repo.getStudentsPage(
                    page = targetPage,
                    pageSize = targetSize,
                    search = targetSearch,
                    classCode = targetClass
                )
                if (requestId == currentRequestId) {
                    _studentState.update {
                        it.copy(
                            items = result.items,
                            page = result.page,
                            pageSize = result.pageSize,
                            totalItems = result.totalItems,
                            totalPages = result.totalPages,
                            hasNextPage = result.hasNextPage,
                            hasPreviousPage = result.hasPreviousPage,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (requestId == currentRequestId) {
                    _studentState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Lỗi khi tải danh sách sinh viên.")
                    }
                }
            }
        }
    }

    fun goToFirstPage() = loadStudentsPage(page = 1)
    fun goToPreviousPage() {
        val current = _studentState.value.page
        if (current > 1) loadStudentsPage(page = current - 1)
    }
    fun goToNextPage() {
        val current = _studentState.value.page
        val total = _studentState.value.totalPages
        if (current < total) loadStudentsPage(page = current + 1)
    }
    fun goToLastPage() {
        val total = _studentState.value.totalPages
        loadStudentsPage(page = total)
    }
    fun retryLoadStudents() = loadStudentsPage()
    suspend fun getStudentById(id: String): Student? = repo().getStudentById(id)
    fun theme(value: String) { app.preferences.edit().putString("theme",value).apply(); mutable.update { it.copy(theme=value) } }
    fun notify(message: String) { mutable.update { it.copy(message=message) } }
    fun clearMessage() { mutable.update { it.copy(message=null) } }
    private fun repo()=repository ?: error("Vui lòng đăng nhập.")
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        mutationMutex.withLock {
            mutable.update { it.copy(busy=true) }
            try { block() } catch(e: Exception) { if(e is CancellationException) throw e; notify(e.message ?: "Thao tác chưa hoàn tất. Vui lòng thử lại.") }
            finally { mutable.update { it.copy(busy=false) } }
        }
    } }
}
