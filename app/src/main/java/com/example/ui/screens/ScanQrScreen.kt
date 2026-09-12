package com.example.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.example.model.Business
import com.example.model.BusinessLocation
import com.example.ui.components.SQButton
import com.example.ui.components.SQButtonVariant
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.PrimaryCyan
import com.example.viewmodel.SmartQueueUiState
import com.example.viewmodel.SmartQueueViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanQrScreen(
    uiState: SmartQueueUiState,
    viewModel: SmartQueueViewModel,
    onBack: () -> Unit,
    onQrResolved: (Business, BusinessLocation) -> Unit
) {
    var manualCode by remember { mutableStateOf("") }
    var isFlashlightOn by remember { mutableStateOf(false) }
    var isProcessingQr by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraInstance by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Control torch when flashlight state changes
    LaunchedEffect(isFlashlightOn) {
        try {
            cameraInstance?.cameraControl?.enableTorch(isFlashlightOn)
        } catch (e: Exception) {
            // Flashlight not available on some emulators
        }
    }

    // Laser Animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Digital QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (cameraPermissionState.status.isGranted) {
                        IconButton(onClick = { isFlashlightOn = !isFlashlightOn }) {
                            Icon(
                                imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Flash",
                                tint = if (isFlashlightOn) PrimaryCyan else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Point camera at SmartQueue counter code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Viewfinder Box with Real Camera or Permission Request
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.9f))
                        .border(3.dp, PrimaryCyan, RoundedCornerShape(24.dp))
                        .testTag("qr_camera_viewfinder"),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraPermissionState.status.isGranted) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !isProcessingQr) {
                                            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            scanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        val raw = barcode.rawValue
                                                        if (!raw.isNullOrBlank() && !isProcessingQr) {
                                                            isProcessingQr = true
                                                            viewModel.scanQrCode(raw) { b, l ->
                                                                onQrResolved(b, l)
                                                            }
                                                            coroutineScope.launch {
                                                                kotlinx.coroutines.delay(2500)
                                                                isProcessingQr = false
                                                            }
                                                            break
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraInstance = cameraProvider.bindToLifecycle(
                                            lifecycleOwner, cameraSelector, preview, imageAnalysis
                                        )
                                    } catch (exc: Exception) {
                                        // Ignore binding failure on emulators
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = PrimaryCyan,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Camera Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Animated Laser Bar Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = (280 * laserOffset).dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        PrimaryCyan,
                                        Color.White,
                                        PrimaryCyan,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Point your camera at the SmartQueue standee or counter QR code to join instantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
            }

            // Manual Code Input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter Public Code manually") },
                    placeholder = { Text("e.g. SQ-APEX-HAZRATGANJ-2026") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (manualCode.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.scanQrCode(manualCode) { b, l ->
                                    onQrResolved(b, l)
                                }
                            }) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Validate", tint = PrimaryCyan)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SQButton(
                    text = "Validate & Open Queue",
                    onClick = {
                        if (manualCode.isNotBlank()) {
                            viewModel.scanQrCode(manualCode) { b, l ->
                                onQrResolved(b, l)
                            }
                        } else {
                            viewModel.showToast("⚠️ Please enter a public code (e.g. SQ-APEX-HAZRATGANJ-2026)")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.CheckCircle
                )
            }
        }
    }
}
