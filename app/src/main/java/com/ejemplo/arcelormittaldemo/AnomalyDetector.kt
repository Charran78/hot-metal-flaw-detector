package com.ejemplo.arcelormittaldemo

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class AnomalyDetector {
    fun findCandidates(rgbaFrame: Mat): List<CandidateBox> {
        if (rgbaFrame.empty() || rgbaFrame.cols() < THRESHOLD_BLOCK_SIZE ||
            rgbaFrame.rows() < THRESHOLD_BLOCK_SIZE
        ) {
            return emptyList()
        }

        val grayscale = Mat()
        val blurred = Mat()
        val threshold = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        return try {
            Imgproc.cvtColor(rgbaFrame, grayscale, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(grayscale, blurred, BLUR_KERNEL, 0.0)
            Imgproc.adaptiveThreshold(
                blurred,
                threshold,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                THRESHOLD_BLOCK_SIZE,
                THRESHOLD_CONSTANT
            )
            Imgproc.findContours(
                threshold,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            contours.mapNotNull { contour ->
                if (Imgproc.contourArea(contour) <= MIN_CANDIDATE_AREA) {
                    null
                } else {
                    val bounds = Imgproc.boundingRect(contour)
                    CandidateBox(
                        left = bounds.x.toFloat(),
                        top = bounds.y.toFloat(),
                        right = (bounds.x + bounds.width).toFloat(),
                        bottom = (bounds.y + bounds.height).toFloat()
                    )
                }
            }
        } finally {
            grayscale.release()
            blurred.release()
            threshold.release()
            hierarchy.release()
            contours.forEach(Mat::release)
        }
    }

    private companion object {
        const val MIN_CANDIDATE_AREA = 150.0
        const val THRESHOLD_BLOCK_SIZE = 11
        const val THRESHOLD_CONSTANT = 5.0
        val BLUR_KERNEL = Size(5.0, 5.0)
    }
}
