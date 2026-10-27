package com.ejemplo.arcelormittaldemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.Date

class InspectionReportExporter(context: Context) {
    private val reportsDirectory = File(context.cacheDir, REPORT_DIRECTORY)

    @Throws(IOException::class)
    fun createAttachments(report: InspectionReport, evidence: CapturedEvidence): List<File> {
        if (!reportsDirectory.exists() && !reportsDirectory.mkdirs()) {
            throw IOException("No se pudo preparar el almacenamiento temporal del informe.")
        }

        val imageFile = File(reportsDirectory, "${report.reportId}.jpg")
        val pdfFile = File(reportsDirectory, "${report.reportId}.pdf")
        try {
            writeEvidenceImage(imageFile, evidence.image)
            writePdf(pdfFile, report, evidence.image)
            return listOf(pdfFile, imageFile)
        } catch (error: IOException) {
            imageFile.delete()
            pdfFile.delete()
            throw error
        } catch (error: RuntimeException) {
            imageFile.delete()
            pdfFile.delete()
            throw error
        }
    }

    @Throws(IOException::class)
    private fun writeEvidenceImage(file: File, image: Bitmap) {
        FileOutputStream(file).use { output ->
            if (!image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw IOException("No se pudo guardar la fotografía de evidencia.")
            }
            output.flush()
        }
    }

    @Throws(IOException::class)
    private fun writePdf(file: File, report: InspectionReport, image: Bitmap) {
        val document = PdfDocument()
        try {
            InspectionPdfWriter(document, report, image).write()
            FileOutputStream(file).use { output ->
                document.writeTo(output)
                output.flush()
            }
        } finally {
            document.close()
        }
    }

    private class InspectionPdfWriter(
        private val document: PdfDocument,
        private val report: InspectionReport,
        private val image: Bitmap
    ) {
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = 19f
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED_COLOR
            textSize = 9f
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED_COLOR
            textSize = 9f
        }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textSize = 10f
        }
        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED_COLOR
            textSize = 8f
        }
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        fun write() {
            startPage(includeImage = true)
            drawFields()
            finishPage()
        }

        private fun startPage(includeImage: Boolean) {
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            val canvas = requireNotNull(page).canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawText("Informe preliminar de inspección", PAGE_MARGIN, 42f, titlePaint)
            canvas.drawText(
                "Registro asistido: tipo y mediciones confirmados manualmente por el técnico.",
                PAGE_MARGIN,
                60f,
                subtitlePaint
            )
            y = 84f

            if (includeImage) {
                canvas.drawText(
                    "Evidencia visual · capturada ${formatDate(report.capturedAtEpochMillis)}",
                    PAGE_MARGIN,
                    y,
                    labelPaint
                )
                y += 10f
                val destination = fitImage(
                    image,
                    RectF(
                        PAGE_MARGIN,
                        y,
                        PAGE_WIDTH - PAGE_MARGIN,
                        y + IMAGE_HEIGHT
                    )
                )
                canvas.drawBitmap(image, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
                y += IMAGE_HEIGHT + SECTION_GAP
            } else {
                canvas.drawText(
                    "Continuación · informe ${report.reportId.take(8).uppercase()}",
                    PAGE_MARGIN,
                    y,
                    labelPaint
                )
                y += SECTION_GAP
            }
        }

        private fun drawFields() {
            val fields = buildList {
                add("ID de informe" to report.reportId)
                add("Fecha del informe" to formatDate(report.createdAtEpochMillis))
                add("ID de bobina" to report.coilId)
                add("ID de paquete" to report.packageId)
                add("Código de barras / QR" to report.barcodeOrQr)
                add("Número de colada" to report.heatNumber)
                add("Pedido de cliente" to report.customerOrderNumber)
                add("Técnico / inspector" to report.inspectorName)
                add("Grado del acero" to report.steelGrade)
                add("Norma de fabricación" to report.manufacturingStandard)
                add("Acabado / recubrimiento" to report.surfaceFinish)
                add("Peso neto (kg)" to report.netWeightKg)
                add("Espesor nominal (mm)" to report.thicknessMm)
                add("Ancho nominal (mm)" to report.widthMm)
                add("Longitud (mm)" to report.lengthMm)
                add("Fabricante / planta (declarado)" to report.manufacturerOrPlant)
                add("Tipo confirmado por el técnico" to report.defectType.displayName)
                add("Recomendación para revisión" to report.recommendation)
                add("Ubicación en el producto (referencia manual)" to report.defectLocation)
                add("Área medida manualmente (mm²)" to report.areaMm2)
                add("Profundidad mínima medida (mm)" to report.depthMinMm)
                add("Profundidad máxima medida (mm)" to report.depthMaxMm)
                add(
                    "Instrumento declarado" to listOf(
                        report.instrument,
                        report.instrumentDetails
                    ).filter(String::isNotBlank).joinToString(" · ")
                )
                add("Observaciones del técnico" to report.notes)
            }

            fields.forEach { (label, value) ->
                if (value.isNotBlank()) drawField(label, value)
            }
            drawWrapped(
                "Las mediciones se transcriben tal como las registró el técnico. " +
                    "La imagen no calcula área física ni profundidad.",
                valuePaint
            )
        }

        private fun drawField(label: String, value: String) {
            val lines = wrap(value, valuePaint, CONTENT_WIDTH)
            val requiredSpace = LABEL_LINE_HEIGHT + lines.size * VALUE_LINE_HEIGHT + FIELD_GAP
            ensureSpace(requiredSpace)
            val canvas = requireNotNull(page).canvas
            canvas.drawText(label, PAGE_MARGIN, y, labelPaint)
            y += LABEL_LINE_HEIGHT
            lines.forEach { line ->
                canvas.drawText(line, PAGE_MARGIN, y, valuePaint)
                y += VALUE_LINE_HEIGHT
            }
            y += FIELD_GAP
        }

        private fun drawWrapped(text: String, paint: Paint) {
            val lines = wrap(text, paint, CONTENT_WIDTH)
            ensureSpace(lines.size * VALUE_LINE_HEIGHT + FIELD_GAP)
            val canvas = requireNotNull(page).canvas
            lines.forEach { line ->
                canvas.drawText(line, PAGE_MARGIN, y, paint)
                y += VALUE_LINE_HEIGHT
            }
            y += FIELD_GAP
        }

        private fun ensureSpace(requiredSpace: Float) {
            if (y + requiredSpace <= PAGE_HEIGHT - FOOTER_MARGIN) return
            finishPage()
            startPage(includeImage = false)
        }

        private fun finishPage() {
            val currentPage = page ?: return
            currentPage.canvas.drawText(
                "Informe ${report.reportId.take(8).uppercase()} · página $pageNumber",
                PAGE_MARGIN,
                PAGE_HEIGHT - 22f,
                footerPaint
            )
            document.finishPage(currentPage)
            page = null
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            val result = mutableListOf<String>()
            text.split('\n').forEach { paragraph ->
                var remaining = paragraph.trim()
                if (remaining.isEmpty()) {
                    result.add("")
                    return@forEach
                }
                while (remaining.isNotEmpty()) {
                    val count = paint.breakText(remaining, true, maxWidth, null)
                    if (count <= 0) {
                        result.add(remaining.take(1))
                        remaining = remaining.drop(1)
                    } else if (count >= remaining.length) {
                        result.add(remaining)
                        remaining = ""
                    } else {
                        val breakAt = remaining.lastIndexOf(' ', count - 1)
                            .takeIf { it > 0 } ?: count
                        result.add(remaining.substring(0, breakAt))
                        remaining = remaining.drop(breakAt).trimStart()
                    }
                }
            }
            return result.ifEmpty { listOf("") }
        }

        private fun formatDate(epochMillis: Long): String =
            DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT
            ).format(Date(epochMillis))

        private fun fitImage(bitmap: Bitmap, container: RectF): RectF {
            val scale = minOf(
                container.width() / bitmap.width,
                container.height() / bitmap.height
            )
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val left = container.centerX() - width / 2f
            val top = container.centerY() - height / 2f
            return RectF(left, top, left + width, top + height)
        }

        private companion object {
            const val PAGE_WIDTH = 612
            const val PAGE_HEIGHT = 792
            const val PAGE_MARGIN = 40f
            const val FOOTER_MARGIN = 42f
            const val IMAGE_HEIGHT = 225f
            const val SECTION_GAP = 18f
            const val LABEL_LINE_HEIGHT = 12f
            const val VALUE_LINE_HEIGHT = 13f
            const val FIELD_GAP = 5f
            const val CONTENT_WIDTH = 532f
            val TEXT_COLOR = android.graphics.Color.rgb(33, 37, 41)
            val MUTED_COLOR = android.graphics.Color.rgb(93, 100, 108)
        }
    }

    private companion object {
        const val REPORT_DIRECTORY = "inspection-reports"
        const val JPEG_QUALITY = 92
    }
}
