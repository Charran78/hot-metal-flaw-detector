package com.ejemplo.arcelormittaldemo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.CameraUnavailableException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var statusView: TextView
    private lateinit var flashlightButton: FloatingActionButton
    @OptIn(ExperimentalCoroutinesApi::class)
    private val cameraExecutor: Executor = Dispatchers.Default.limitedParallelism(1).asExecutor()
    private lateinit var viewModel: InspectionViewModel
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var frameAnalyzer: CameraFrameAnalyzer? = null
    private var reportFormDialog: InspectionReportFormDialog? = null
    private var openEvidenceId: String? = null
    private var cameraProviderRequestPending = false
    private var permissionRequestAttempted = false

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            viewModel.reportError(getString(R.string.camera_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[InspectionViewModel::class.java]
        previewView = findViewById(R.id.camera_preview)
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        detectionOverlay = findViewById(R.id.detection_overlay)
        statusView = findViewById(R.id.inspection_status)
        flashlightButton = findViewById(R.id.fab_flashlight)
        detectionOverlay.onCandidateClick = ::requestCandidateCapture

        if (!OpenCVLoader.initLocal()) {
            statusView.text = getString(R.string.camera_start_failed)
            finish()
            return
        }

        flashlightButton.setOnClickListener { toggleFlashlight() }
        statusView.setOnClickListener { retryCameraIfNeeded() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) {
            startCamera()
        } else if (!permissionRequestAttempted) {
            requestCameraPermissionIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission() && camera == null) {
            viewModel.cameraStopped(getString(R.string.camera_restarting))
            startCamera()
        }
    }

    override fun onStop() {
        reportFormDialog?.dismiss()
        frameAnalyzer?.setPaused(true)
        frameAnalyzer?.cancelPendingCapture(getString(R.string.camera_stopped))
        imageAnalysis?.clearAnalyzer()
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        imageAnalysis = null
        frameAnalyzer = null
        viewModel.cameraStopped(getString(R.string.camera_stopped))
        super.onStop()
    }

    private fun requestCameraPermissionIfNeeded() {
        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionRequestAttempted = true
            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
        }
    }

    private fun retryCameraIfNeeded() {
        if (!hasCameraPermission()) {
            requestCameraPermissionIfNeeded()
        } else if (!viewModel.state.value.cameraReady) {
            startCamera()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val existingProvider = cameraProvider
        if (existingProvider != null) {
            bindCameraUseCases(existingProvider)
            return
        }

        if (cameraProviderRequestPending || !hasCameraPermission()) return
        cameraProviderRequestPending = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                cameraProviderRequestPending = false
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                    !hasCameraPermission()
                ) {
                    return@addListener
                }
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    bindCameraUseCases(provider)
                } catch (error: ExecutionException) {
                    viewModel.reportError(getString(R.string.camera_start_failed))
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    viewModel.reportError(getString(R.string.camera_start_failed))
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            !hasCameraPermission()
        ) {
            return
        }

        try {
            provider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            val analyzer = CameraFrameAnalyzer(
                detector = AnomalyDetector(),
                onFrame = viewModel::reportFrame,
                onError = viewModel::reportError
            )
            frameAnalyzer = analyzer
            analysis.setAnalyzer(cameraExecutor, analyzer)
            imageAnalysis = analysis
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            val flashAvailable = camera?.cameraInfo?.hasFlashUnit() == true
            viewModel.cameraReady(flashAvailable)
            if (!flashAvailable) {
                viewModel.reportError(getString(R.string.torch_unavailable))
            }
        } catch (error: CameraUnavailableException) {
            viewModel.reportError(getString(R.string.camera_start_failed))
        } catch (error: IllegalArgumentException) {
            viewModel.reportError(getString(R.string.camera_start_failed))
        }
    }

    private fun toggleFlashlight() {
        val currentCamera = camera ?: return
        val requestedState = !viewModel.state.value.torchEnabled
        val torchFuture = currentCamera.cameraControl.enableTorch(requestedState)
        torchFuture.addListener(
            {
                try {
                    torchFuture.get()
                    viewModel.torchEnabled(requestedState)
                } catch (error: ExecutionException) {
                    viewModel.reportError(getString(R.string.torch_failed))
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    viewModel.reportError(getString(R.string.torch_failed))
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun render(state: InspectionUiState) {
        detectionOverlay.showFrame(state.frame)
        flashlightButton.isEnabled = state.cameraReady && state.torchAvailable
        flashlightButton.contentDescription = getString(
            if (state.torchEnabled) R.string.flashlight_disable else R.string.flashlight_enable
        )
        statusView.text = when {
            state.message.isNotBlank() -> state.message
            state.captureInProgress -> getString(R.string.capture_in_progress)
            !state.cameraReady -> getString(R.string.status_waiting_for_camera)
            else -> resources.getQuantityString(
                R.plurals.candidate_count,
                state.frame?.candidates?.size ?: 0,
                state.frame?.candidates?.size ?: 0
            ) + "\n" + getString(R.string.candidate_tap_hint)
        }

        state.evidence?.let(::showReportForm)
    }

    private fun requestCandidateCapture(candidate: CandidateBox) {
        if (viewModel.state.value.captureInProgress || viewModel.state.value.evidence != null) return
        val analyzer = frameAnalyzer ?: return
        viewModel.captureStarted()
        val accepted = analyzer.requestCapture(candidate) { result ->
            runOnUiThread {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    if (result is CandidateCaptureResult.Captured) {
                        result.evidence.image.recycle()
                    }
                    return@runOnUiThread
                }
                when (result) {
                    is CandidateCaptureResult.Captured -> {
                        analyzer.setPaused(true)
                        viewModel.captureCompleted(result.evidence)
                    }
                    is CandidateCaptureResult.Failed -> viewModel.captureFailed(result.message)
                }
            }
        }
        if (!accepted) {
            viewModel.captureFailed("Ya se está preparando otra captura. Inténtalo de nuevo.")
        }
    }

    private fun showReportForm(evidence: CapturedEvidence) {
        if (openEvidenceId == evidence.id) return
        openEvidenceId = evidence.id
        reportFormDialog = InspectionReportFormDialog(
            context = this,
            evidence = evidence,
            onSubmit = { draft -> createAndShareReport(draft, evidence) },
            onDismiss = {
                openEvidenceId = null
                reportFormDialog = null
                viewModel.clearEvidence()
                resumeAnalysisAfterReport()
            }
        ).also(InspectionReportFormDialog::show)
    }

    private fun resumeAnalysisAfterReport() {
        val analyzer = frameAnalyzer ?: return
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            analyzer.setPaused(false)
        }
    }

    private fun createAndShareReport(draft: InspectionReportDraft, evidence: CapturedEvidence) {
        val defectType = DefectCatalog.find(draft.defectTypeId) ?: return
        val report = InspectionReport.fromDraft(
            draft = draft,
            defectType = defectType,
            capturedAtEpochMillis = evidence.capturedAtEpochMillis,
            reportId = evidence.id
        )
        val form = reportFormDialog ?: return
        form.setBusy(true)
        lifecycleScope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    InspectionReportExporter(applicationContext).createAttachments(report, evidence)
                }
                val attachments = ArrayList(
                    files.map { file ->
                        FileProvider.getUriForFile(
                            this@MainActivity,
                            "$packageName.fileprovider",
                            file
                        )
                    }
                )
                val primaryId = report.coilId.ifBlank { report.packageId }
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/jpeg"))
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.share_report_subject, primaryId)
                    )
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(R.string.share_report_body)
                    )
                    clipData = ClipData.newUri(
                        contentResolver,
                        getString(R.string.share_report_chooser),
                        attachments.first()
                    ).apply {
                        attachments.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_report_chooser)))
                form.dismiss()
            } catch (error: IOException) {
                form.setBusy(false, getString(R.string.report_export_failed))
            } catch (error: ActivityNotFoundException) {
                form.setBusy(false, getString(R.string.report_share_unavailable))
            } catch (error: IllegalArgumentException) {
                form.setBusy(false, getString(R.string.report_export_failed))
            }
        }
    }
}
