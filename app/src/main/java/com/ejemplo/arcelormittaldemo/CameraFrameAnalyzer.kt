package com.ejemplo.arcelormittaldemo

import android.graphics.PixelFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat

class CameraFrameAnalyzer(
    private val detector: AnomalyDetector,
    private val onFrame: (InspectionFrame) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            require(image.format == PixelFormat.RGBA_8888) {
                "CameraX entregó un formato de imagen no compatible."
            }
            val rgbaFrame = Mat(image.height, image.width, CvType.CV_8UC4)
            try {
                copyRgbaPixels(image, rgbaFrame)
                onFrame(
                    InspectionFrame(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        candidates = detector.findCandidates(rgbaFrame)
                    )
                )
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

    private companion object {
        const val CHANNELS = 4
    }
}
