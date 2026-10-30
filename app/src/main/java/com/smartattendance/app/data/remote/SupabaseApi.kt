package com.smartattendance.app.data.remote

import com.smartattendance.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseApi(private val vault: TokenVault) {
    val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    val configured = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val refreshMutex=Mutex()
    @Volatile private var access: String?=null
    @Volatile var userId: String?=null; private set
    suspend fun login(email: String, password: String) {
        val result=send("/auth/v1/token?grant_type=password",buildJsonObject { put("email",email); put("password",password) },null)
        install(result.jsonObject)
    }
    suspend fun restore(): Boolean {
        if(!configured || vault.read()==null) return false
        refresh(); return true
    }
    private fun install(data: JsonObject) {
        val token=data["refresh_token"]?.jsonPrimitive?.content ?: error("Phản hồi xác thực không hợp lệ.")
        vault.save(token)
        access=data.getValue("access_token").jsonPrimitive.content
        userId=data.getValue("user").jsonObject.getValue("id").jsonPrimitive.content
    }
    private suspend fun refresh() {
        val refreshToken=vault.read() ?: throw ApiException(401,"Phiên đăng nhập đã hết hạn.")
        try { install(send("/auth/v1/token?grant_type=refresh_token",buildJsonObject { put("refresh_token",refreshToken) },null).jsonObject) }
        catch(e: ApiException) { if(e.status in setOf(400,401,403)) clear(); throw e }
    }
    suspend fun rpc(name: String, body: JsonObject=buildJsonObject {}): JsonElement {
        val used=access ?: throw ApiException(401,"Vui lòng đăng nhập lại.")
        return try { send("/rest/v1/rpc/$name",body,used) } catch(e: ApiException) {
            if(e.status!=401) throw e
            refreshMutex.withLock { if(access==used) refresh() }
            send("/rest/v1/rpc/$name",body,access)
        }
    }
    suspend fun logout() {
        try { access?.let { send("/auth/v1/logout?scope=local",buildJsonObject {},it) } } finally { clear() }
    }
    fun clear() { vault.clear(); access=null; userId=null }
    private suspend fun send(path: String, body: JsonObject, token: String?): JsonElement = withContext(Dispatchers.IO) {
        check(configured) { "Chưa cấu hình Supabase. Hãy sử dụng Demo." }
        val builder=Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/')+path).header("apikey",BuildConfig.SUPABASE_ANON_KEY).header("Accept","application/json").post(body.toString().toRequestBody("application/json".toMediaType()))
        if(token!=null) builder.header("Authorization","Bearer $token")
        try {
            client.newCall(builder.build()).execute().use { response ->
                val text=response.body?.string().orEmpty()
                if(!response.isSuccessful) {
                    val errJson = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    val serverMessage = errJson?.get("message")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val serverCode = errJson?.get("code")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val serverDetails = errJson?.get("details")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    val serverHint = errJson?.get("hint")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

                    val customDescription = when {
                        response.code == 401 -> "Đăng nhập thất bại hoặc phiên đã hết hạn."
                        response.code == 429 -> "Quá nhiều yêu cầu. Vui lòng thử lại sau."
                        serverMessage?.contains("FORBIDDEN") == true || response.code == 403 -> "Bạn không có quyền thực hiện thao tác này."
                        serverMessage?.contains("SESSION_CLOSED") == true -> "Buổi học đã đóng hoặc hết thời gian."
                        serverMessage?.contains("DUPLICATE_STUDENT") == true || serverMessage?.contains("duplicate key") == true -> "Mã sinh viên hoặc dữ liệu này đã tồn tại."
                        serverMessage?.contains("INACTIVE_ACCOUNT") == true -> "Tài khoản chưa được quản trị viên kích hoạt."
                        else -> null
                    }

                    val fullMessage = buildString {
                        if (customDescription != null) {
                            append(customDescription)
                            if (serverMessage != null && serverMessage != customDescription) {
                                append(" (")
                                append(serverMessage)
                                append(")")
                            }
                        } else if (serverMessage != null) {
                            append(serverMessage)
                        } else {
                            append("Máy chủ từ chối yêu cầu (HTTP ${response.code}). Vui lòng kiểm tra cấu hình hoặc dữ liệu.")
                        }

                        if (serverCode != null) {
                            append(" [Mã lỗi: $serverCode]")
                        }
                        if (serverDetails != null) {
                            append(" - Chi tiết: $serverDetails")
                        }
                        if (serverHint != null) {
                            append(" - Gợi ý: $serverHint")
                        }
                    }
                    throw ApiException(response.code, fullMessage)
                }
                if(text.isBlank()) JsonNull else json.parseToJsonElement(text)
            }
        } catch(e: IOException) { throw IOException("Không kết nối được máy chủ. Chưa xác nhận lưu dữ liệu; hãy kiểm tra mạng và tải lại.",e) }
    }
}
class ApiException(val status: Int, message: String): Exception(message)
