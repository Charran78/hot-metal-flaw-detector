package com.ejemplo.arcelormittaldemo

import android.graphics.Bitmap
import java.math.BigDecimal
import java.util.UUID

data class DefectType(
    val id: String,
    val category: String,
    val name: String,
    val englishName: String
) {
    val displayName: String
        get() = "$category · $name ($englishName)"
}

object DefectCatalog {
    val types = listOf(
        DefectType("orange_peel", "Superficial", "Piel de naranja", "Orange peel"),
        DefectType("luders_lines", "Superficial", "Líneas de Lüders", "Stretcher strains"),
        DefectType("roll_marks", "Superficial", "Marcas de rodillo", "Roll marks"),
        DefectType("pitting", "Superficial", "Picaduras", "Pitting"),
        DefectType("scratches", "Superficial", "Rayas", "Scratches"),
        DefectType("inclusions", "Superficial", "Incrustaciones", "Inclusions"),
        DefectType("water_stains", "Superficial", "Manchas de agua", "Water stains"),
        DefectType("edge_wave", "Geométrico", "Ondeado de bordes", "Edge wave"),
        DefectType("center_buckle", "Geométrico", "Abombamiento central", "Center buckle"),
        DefectType("camber", "Geométrico", "Curvatura lateral", "Camber"),
        DefectType("burr", "Geométrico", "Rebaba", "Burr"),
        DefectType("delamination", "Estructural", "Delaminación", "Delamination"),
        DefectType("edge_cracking", "Estructural", "Agrietamiento de bordes", "Edge cracking"),
        DefectType("laps_folds", "Estructural", "Solapes o pliegues", "Laps / Folds"),
        DefectType("gauge_variation", "Estructural", "Variación de espesor", "Gauge variation")
    )

    fun find(id: String): DefectType? = types.firstOrNull { it.id == id }
}

object PreventiveRecommendations {
    val options = listOf(
        "Valorar según criterios de calidad",
        "Segregar lote para revisión",
        "Proponer rechazo (requiere aprobación)"
    )
}

object MeasurementInstruments {
    val options = listOf(
        "Calibre",
        "Micrómetro",
        "Medidor de profundidad",
        "Perfilómetro / sensor",
        "Otro instrumento"
    )
}

data class InspectionReportDraft(
    val coilId: String = "",
    val packageId: String = "",
    val barcodeOrQr: String = "",
    val heatNumber: String = "",
    val inspectorName: String = "",
    val netWeightKg: String = "",
    val thicknessMm: String = "",
    val widthMm: String = "",
    val lengthMm: String = "",
    val steelGrade: String = "",
    val manufacturingStandard: String = "",
    val surfaceFinish: String = "",
    val customerOrderNumber: String = "",
    val manufacturerOrPlant: String = "",
    val defectTypeId: String = "",
    val recommendation: String = "",
    val areaMm2: String = "",
    val depthMinMm: String = "",
    val depthMaxMm: String = "",
    val instrument: String = "",
    val instrumentDetails: String = "",
    val defectLocation: String = "",
    val notes: String = ""
)

data class InspectionReport(
    val reportId: String,
    val createdAtEpochMillis: Long,
    val capturedAtEpochMillis: Long,
    val coilId: String,
    val packageId: String,
    val barcodeOrQr: String,
    val heatNumber: String,
    val inspectorName: String,
    val netWeightKg: String,
    val thicknessMm: String,
    val widthMm: String,
    val lengthMm: String,
    val steelGrade: String,
    val manufacturingStandard: String,
    val surfaceFinish: String,
    val customerOrderNumber: String,
    val manufacturerOrPlant: String,
    val defectType: DefectType,
    val recommendation: String,
    val areaMm2: String,
    val depthMinMm: String,
    val depthMaxMm: String,
    val instrument: String,
    val instrumentDetails: String,
    val defectLocation: String,
    val notes: String
) {
    companion object {
        fun fromDraft(
            draft: InspectionReportDraft,
            defectType: DefectType,
            capturedAtEpochMillis: Long,
            reportId: String
        ): InspectionReport = InspectionReport(
            reportId = reportId,
            createdAtEpochMillis = System.currentTimeMillis(),
            capturedAtEpochMillis = capturedAtEpochMillis,
            coilId = draft.coilId.trim(),
            packageId = draft.packageId.trim(),
            barcodeOrQr = draft.barcodeOrQr.trim(),
            heatNumber = draft.heatNumber.trim(),
            inspectorName = draft.inspectorName.trim(),
            netWeightKg = normalizeDecimal(draft.netWeightKg),
            thicknessMm = normalizeDecimal(draft.thicknessMm),
            widthMm = normalizeDecimal(draft.widthMm),
            lengthMm = normalizeDecimal(draft.lengthMm),
            steelGrade = draft.steelGrade.trim(),
            manufacturingStandard = draft.manufacturingStandard.trim(),
            surfaceFinish = draft.surfaceFinish.trim(),
            customerOrderNumber = draft.customerOrderNumber.trim(),
            manufacturerOrPlant = draft.manufacturerOrPlant.trim(),
            defectType = defectType,
            recommendation = draft.recommendation.trim(),
            areaMm2 = normalizeDecimal(draft.areaMm2),
            depthMinMm = normalizeDecimal(draft.depthMinMm),
            depthMaxMm = normalizeDecimal(draft.depthMaxMm),
            instrument = draft.instrument.trim(),
            instrumentDetails = draft.instrumentDetails.trim(),
            defectLocation = draft.defectLocation.trim(),
            notes = draft.notes.trim()
        )

        private fun normalizeDecimal(value: String): String = value.trim().replace(',', '.')
    }
}

data class CapturedEvidence(
    val id: String = UUID.randomUUID().toString(),
    val capturedAtEpochMillis: Long,
    val image: Bitmap,
    val candidate: CandidateBox
)

sealed class DraftValidation {
    data class Valid(val draft: InspectionReportDraft) : DraftValidation()
    data class Invalid(val reason: String) : DraftValidation()
}

object InspectionDraftValidator {
    fun validate(draft: InspectionReportDraft): DraftValidation {
        if (draft.coilId.isBlank() && draft.packageId.isBlank()) {
            return DraftValidation.Invalid("Indica el ID de bobina o el ID de paquete.")
        }
        if (DefectCatalog.find(draft.defectTypeId) == null) {
            return DraftValidation.Invalid("Confirma el tipo de defecto para continuar.")
        }
        if (draft.recommendation !in PreventiveRecommendations.options) {
            return DraftValidation.Invalid("Selecciona una recomendación para revisión.")
        }

        val decimalFields = listOf(
            "peso neto" to draft.netWeightKg,
            "espesor" to draft.thicknessMm,
            "ancho" to draft.widthMm,
            "longitud" to draft.lengthMm,
            "área" to draft.areaMm2,
            "profundidad mínima" to draft.depthMinMm,
            "profundidad máxima" to draft.depthMaxMm
        )
        decimalFields.forEach { (label, value) ->
            if (value.isNotBlank() && parseNonNegativeDecimal(value) == null) {
                return DraftValidation.Invalid(
                    "El valor de $label debe ser un número igual o mayor que cero."
                )
            }
        }

        val depthMin = parseNonNegativeDecimal(draft.depthMinMm)
        val depthMax = parseNonNegativeDecimal(draft.depthMaxMm)
        if (depthMin != null && depthMax != null && depthMin > depthMax) {
            return DraftValidation.Invalid(
                "La profundidad mínima no puede superar la profundidad máxima."
            )
        }

        val hasMeasurements = listOf(
            draft.areaMm2,
            draft.depthMinMm,
            draft.depthMaxMm
        ).any(String::isNotBlank)
        if (hasMeasurements && draft.instrument !in MeasurementInstruments.options) {
            return DraftValidation.Invalid(
                "Selecciona el instrumento utilizado para registrar las mediciones."
            )
        }
        if (
            hasMeasurements &&
            draft.instrument == "Otro instrumento" &&
            draft.instrumentDetails.isBlank()
        ) {
            return DraftValidation.Invalid("Indica cuál fue el instrumento utilizado.")
        }

        return DraftValidation.Valid(
            draft.copy(
                coilId = draft.coilId.trim(),
                packageId = draft.packageId.trim(),
                barcodeOrQr = draft.barcodeOrQr.trim(),
                heatNumber = draft.heatNumber.trim(),
                inspectorName = draft.inspectorName.trim(),
                netWeightKg = normalizeDecimal(draft.netWeightKg),
                thicknessMm = normalizeDecimal(draft.thicknessMm),
                widthMm = normalizeDecimal(draft.widthMm),
                lengthMm = normalizeDecimal(draft.lengthMm),
                steelGrade = draft.steelGrade.trim(),
                manufacturingStandard = draft.manufacturingStandard.trim(),
                surfaceFinish = draft.surfaceFinish.trim(),
                customerOrderNumber = draft.customerOrderNumber.trim(),
                manufacturerOrPlant = draft.manufacturerOrPlant.trim(),
                areaMm2 = normalizeDecimal(draft.areaMm2),
                depthMinMm = normalizeDecimal(draft.depthMinMm),
                depthMaxMm = normalizeDecimal(draft.depthMaxMm),
                instrumentDetails = draft.instrumentDetails.trim(),
                defectLocation = draft.defectLocation.trim(),
                notes = draft.notes.trim()
            )
        )
    }

    private fun parseNonNegativeDecimal(value: String): BigDecimal? {
        val normalized = value.trim().replace(',', '.')
        if (!DECIMAL_PATTERN.matches(normalized)) return null
        return normalized.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
    }

    private fun normalizeDecimal(value: String): String = value.trim().replace(',', '.')

    private val DECIMAL_PATTERN = Regex("""\d+(?:[.]\d+)?""")
}
