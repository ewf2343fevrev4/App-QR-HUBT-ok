package com.smartattendance.app.feature.scanner

import android.annotation.SuppressLint
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
@Composable fun CameraPreview(modifier: Modifier,torch: Boolean,busy: Boolean,onCode: (String) -> Unit,onReady: (Boolean) -> Unit,onError: (String) -> Unit) {
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current
    val previewView=remember {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FILL_CENTER}}
    var camera by remember {mutableStateOf<Camera?>(null)}
    val currentCode by rememberUpdatedState(onCode); val currentError by rememberUpdatedState(onError); val currentReady by rememberUpdatedState(onReady); val isBusy by rememberUpdatedState(busy)
    LaunchedEffect(torch,camera) {camera?.let {if(it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torch)}}
    DisposableEffect(owner,previewView) {
        val alive=AtomicBoolean(true); val processing=AtomicBoolean(false)
        val executor=Executors.newSingleThreadExecutor(); val main=ContextCompat.getMainExecutor(context)
        val scanner=BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE,Barcode.FORMAT_CODE_128,Barcode.FORMAT_CODE_39,Barcode.FORMAT_EAN_13).build())
        val preview=Preview.Builder().build().apply {setSurfaceProvider(previewView.surfaceProvider)}
        val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor,object: ImageAnalysis.Analyzer {
            @androidx.camera.core.ExperimentalGetImage
            override fun analyze(proxy: ImageProxy) {
                if(!alive.get() || isBusy || !processing.compareAndSet(false,true)) {proxy.close();return}
                val media=proxy.image
                if(media==null) {proxy.close();processing.set(false);return}
                try {
                    scanner.process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener(main) {codes -> if(alive.get()) codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let {currentCode(it)} }
                        .addOnFailureListener(main) {if(alive.get()) currentError("Không đọc được khung hình. Thử giữ thẻ rõ nét trong khung.")}
                        .addOnCompleteListener {proxy.close();processing.set(false);if(!alive.get()) scanner.close()}
                } catch(e: Exception) {proxy.close();processing.set(false);main.execute {if(alive.get()) currentError("Lỗi nhận diện mã. Hãy mở lại camera.")}}
            }
        })
        val future=ProcessCameraProvider.getInstance(context); var provider: ProcessCameraProvider?=null
        future.addListener({
            if(alive.get()) try {
                provider=future.get()
                camera=provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
                currentReady(camera!!.cameraInfo.hasFlashUnit())
            } catch(e: Exception) {currentError("Camera sau không khả dụng. Kiểm tra thiết bị hoặc ứng dụng khác đang sử dụng camera.")}
        },main)
        onDispose {
            alive.set(false);analysis.clearAnalyzer();provider?.unbind(preview,analysis);camera=null
            executor.shutdown();if(!processing.get()) scanner.close()
        }
    }
    AndroidView(factory={previewView},modifier=modifier)
}
