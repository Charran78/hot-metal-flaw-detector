package com.ejemplo.arcelormittaldemo

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class CameraFrameAnalyzer(
    private val detector: AnomalyDetector,
    private val onFrame: (InspectionFrame) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val pendingCapture = AtomicReference<PendingCapture?>(null)
    private val paused = AtomicBoolean(false)

    fun setPaused(value: Boolean) {
        paused.set(value)
    }

    fun requestCapture(
        candidate: CandidateBox,
        callback: (CandidateCaptureResult) -> Unit
    ): Boolean = pendingCapture.compareAndSet(null, PendingCapture(candidate, callback))

    fun cancelPendingCapture(reason: String) {
        pendingCapture.getAndSet(null)?.callback?.invoke(CandidateCaptureResult.Failed(reason))
    }

    override fun analyze(image: ImageProxy) {
        if (paused.get()) {
            image.close()
            return
        }
        try {
            require(image.format == PixelFormat.RGBA_8888) {
                "CameraX entregó un formato de imagen no compatible."
            }
            val rgbaFrame = Mat(image.height, image.width, CvType.CV_8UC4)
            try {
                copyRgbaPixels(image, rgbaFrame)
                val rotationDegrees = image.imageInfo.rotationDegrees
                val candidates = detector.findCandidates(rgbaFrame)
                onFrame(
                    InspectionFrame(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = rotationDegrees,
                        candidates = candidates
                    )
                )
                pendingCapture.getAndSet(null)?.let { capture ->
                    captureCandidate(rgbaFrame, candidates, rotationDegrees, capture)
                }
            } finally {
                rgbaFrame.release()
            }
        } catch (error: CvException) {
            onError("OpenCV no pudo procesar el fotograma.")
        } catch (error: IllegalArgumentException) {
            onError(error.message ?: "El formato del fotograma no es compatible.")
        } finally {
            image.close()
        }
    }

    private fun copyRgbaPixels(image: ImageProxy, destination: Mat) {
        val plane = image.planes.firstOrNull()
            ?: throw IllegalArgumentException("CameraX no entregó datos de imagen.")
        val buffer = plane.buffer.duplicate()
        val width = image.width
        val height = image.height
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowBytes = width * CHANNELS
        val bufferStart = buffer.position()
        val pixels = ByteArray(rowBytes * height)

        require(pixelStride >= CHANNELS && rowStride >= (width - 1) * pixelStride + CHANNELS) {
            "El paso de píxel del fotograma no es compatible."
        }
        require(buffer.limit() - bufferStart >= (height - 1) * rowStride + rowBytes) {
            "El búfer de cámara está incompleto."
        }

        if (pixelStride == CHANNELS && rowStride == rowBytes) {
            buffer.get(pixels)
        } else {
            for (y in 0 until height) {
                val sourceRow = bufferStart + y * rowStride
                val destinationRow = y * rowBytes
                for (x in 0 until width) {
                    val sourcePixel = sourceRow + x * pixelStride
                    val destinationPixel = destinationRow + x * CHANNELS
                    for (channel in 0 until CHANNELS) {
                        pixels[destinationPixel + channel] = buffer.get(sourcePixel + channel)
                    }
                }
            }
        }
        destination.put(0, 0, pixels)
    }

    private fun captureCandidate(
        rgbaFrame: Mat,
        candidates: List<CandidateBox>,
        rotationDegrees: Int,
        capture: PendingCapture
    ) {
        // Keep the selected location when per-frame contours jitter or disappear briefly.
        val match = candidates.maxByOrNull { intersectionOverUnion(capture.candidate, it) }
        val selectedBox = match
            ?.takeIf { isSameCandidate(capture.candidate, it) }
            ?: clampToFrame(capture.candidate, rgbaFrame.cols(), rgbaFrame.rows())

        try {
            Imgproc.rectangle(
                rgbaFrame,
                Point(selectedBox.left.toDouble(), selectedBox.top.toDouble()),
                Point(selectedBox.right.toDouble(), selectedBox.bottom.toDouble()),
                ANNOTATION_COLOR,
                ANNOTATION_THICKNESS
            )
            val uprightBitmap = createAnnotatedBitmap(rgbaFrame, rotationDegrees)
            capture.callback(
                CandidateCaptureResult.Captured(
                    CapturedEvidence(
                        capturedAtEpochMillis = System.currentTimeMillis(),
                        image = uprightBitmap,
                        candidate = selectedBox
                    )
                )
            )
        } catch (error: CvException) {
            capture.callback(
                CandidateCaptureResult.Failed("No se pudo crear la imagen de evidencia.")
            )
        } catch (error: IllegalArgumentException) {
            capture.callback(
                CandidateCaptureResult.Failed("El fotograma capturado no es válido.")
            )
        }
    }

    private fun isSameCandidate(requested: CandidateBox, detected: CandidateBox): Boolean {
        if (intersectionOverUnion(requested, detected) >= MIN_MATCH_IOU) return true
        val requestedCenterX = (requested.left + requested.right) / 2f
        val requestedCenterY = (requested.top + requested.bottom) / 2f
        val detectedCenterX = (detected.left + detected.right) / 2f
        val detectedCenterY = (detected.top + detected.bottom) / 2f
        val maxDistance = max(
            max(requested.right - requested.left, requested.bottom - requested.top),
            max(detected.right - detected.left, detected.bottom - detected.top)
        ) * MAX_MATCH_DISTANCE_FACTOR
        val dx = requestedCenterX - detectedCenterX
        val dy = requestedCenterY - detectedCenterY
        return dx * dx + dy * dy <= maxDistance * maxDistance
    }

    private fun clampToFrame(box: CandidateBox, width: Int, height: Int): CandidateBox {
        val left = box.left.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
        val top = box.top.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat())
        return CandidateBox(
            left = left,
            top = top,
            right = box.right.coerceIn(left + 1f, width.toFloat()),
            bottom = box.bottom.coerceIn(top + 1f, height.toFloat())
        )
    }

    private fun createAnnotatedBitmap(rgbaFrame: Mat, rotationDegrees: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(
            rgbaFrame.cols(),
            rgbaFrame.rows(),
            Bitmap.Config.ARGB_8888
        )
        return try {
            Utils.matToBitmap(rgbaFrame, bitmap)
            rotateBitmap(bitmap, rotationDegrees)
        } catch (error: CvException) {
            bitmap.recycle()
            throw error
        } catch (error: IllegalArgumentException) {
            bitmap.recycle()
            throw error
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { rotated ->
                if (rotated !== bitmap) bitmap.recycle()
            }
    }

    private fun intersectionOverUnion(first: CandidateBox, second: CandidateBox): Float {
        val intersectionWidth = max(0f, min(first.right, second.right) - max(first.left, second.left))
        val intersectionHeight = max(0f, min(first.bottom, second.bottom) - max(first.top, second.top))
        val intersectionArea = intersectionWidth * intersectionHeight
        val firstArea = max(0f, first.right - first.left) * max(0f, first.bottom - first.top)
        val secondArea = max(0f, second.right - second.left) * max(0f, second.bottom - second.top)
        val unionArea = firstArea + secondArea - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    private companion object {
        const val CHANNELS = 4
        const val MIN_MATCH_IOU = 0.15f
        const val MAX_MATCH_DISTANCE_FACTOR = 1.5f
        const val ANNOTATION_THICKNESS = 5
        val ANNOTATION_COLOR = Scalar(224.0, 16.0, 33.0, 255.0)
    }

    private data class PendingCapture(
        val candidate: CandidateBox,
        val callback: (CandidateCaptureResult) -> Unit
    )
}

sealed class CandidateCaptureResult {
    data class Captured(val evidence: CapturedEvidence) : CandidateCaptureResult()
    data class Failed(val message: String) : CandidateCaptureResult()
}
