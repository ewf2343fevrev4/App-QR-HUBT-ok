package com.smartattendance.app.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartattendance.app.domain.model.ScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ScannerState(val busy: Boolean=false,val result: ScanResult?=null,val error: String?=null)
class ScannerViewModel: ViewModel() {
    private val mutable=MutableStateFlow(ScannerState()); val state=mutable.asStateFlow()
    private var lastCode=""; private var lastTime=0L
    fun scan(raw: String,record: suspend (String) -> ScanResult) {
        val now=android.os.SystemClock.elapsedRealtime()
        if(mutable.value.busy || (raw==lastCode && now-lastTime<2500)) return
        lastCode=raw;lastTime=now;mutable.value=ScannerState(busy=true)
        viewModelScope.launch {
            try {mutable.value=ScannerState(result=record(raw))}
            catch(e: Exception) {if(e is CancellationException) throw e;mutable.value=ScannerState(error=e.message ?: "Không lưu được điểm danh.")}
        }
    }
}
