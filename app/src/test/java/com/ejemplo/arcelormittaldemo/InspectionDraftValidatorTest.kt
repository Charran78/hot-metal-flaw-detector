package com.ejemplo.arcelormittaldemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionDraftValidatorTest {
    private val validDraft = InspectionReportDraft(
        coilId = "COIL-123",
        defectTypeId = "pitting",
        recommendation = PreventiveRecommendations.options.first()
    )

    @Test
    fun requiresCoilOrPackageIdentifier() {
        val result = InspectionDraftValidator.validate(validDraft.copy(coilId = " "))

        assertInvalid(result, "ID de paquete")
    }

    @Test
    fun acceptsPackageIdWhenCoilIdIsUnavailable() {
        val result = InspectionDraftValidator.validate(
            validDraft.copy(coilId = "", packageId = "PACKAGE-123")
        )

        assertTrue(result is DraftValidation.Valid)
    }

    @Test
    fun requiresConfirmedDefectAndRecommendation() {
        assertInvalid(
            InspectionDraftValidator.validate(validDraft.copy(defectTypeId = "")),
            "tipo de defecto"
        )
        assertInvalid(
            InspectionDraftValidator.validate(validDraft.copy(recommendation = "")),
            "recomendación"
        )
    }

    @Test
    fun normalizesSpanishDecimalSeparators() {
        val result = InspectionDraftValidator.validate(
            validDraft.copy(
                areaMm2 = "200,5",
                depthMinMm = "0,08",
                depthMaxMm = "0,4",
                instrument = "Medidor de profundidad"
            )
        )

        assertTrue(result is DraftValidation.Valid)
        assertEquals("200.5", (result as DraftValidation.Valid).draft.areaMm2)
        assertEquals("0.08", result.draft.depthMinMm)
        assertEquals("0.4", result.draft.depthMaxMm)
    }

    @Test
    fun rejectsNegativeAndMalformedMeasurements() {
        assertInvalid(
            InspectionDraftValidator.validate(validDraft.copy(areaMm2 = "-1")),
            "área"
        )
        assertInvalid(
            InspectionDraftValidator.validate(validDraft.copy(thicknessMm = "1,2,3")),
            "espesor"
        )
    }

    @Test
    fun rejectsInvertedDepthRange() {
        val result = InspectionDraftValidator.validate(
            validDraft.copy(depthMinMm = "0.5", depthMaxMm = "0.4")
        )

        assertInvalid(result, "profundidad mínima")
    }

    @Test
    fun requiresAnInstrumentWhenMeasurementsAreEntered() {
        assertInvalid(
            InspectionDraftValidator.validate(validDraft.copy(areaMm2 = "200")),
            "instrumento utilizado"
        )
        assertTrue(
            InspectionDraftValidator.validate(
                validDraft.copy(
                    areaMm2 = "200",
                    instrument = "Calibre"
                )
            ) is DraftValidation.Valid
        )
    }

    @Test
    fun requiresInstrumentDetailsForOtherInstrument() {
        val result = InspectionDraftValidator.validate(
            validDraft.copy(
                areaMm2 = "200",
                instrument = "Otro instrumento"
            )
        )

        assertInvalid(result, "cuál fue el instrumento")
    }

    @Test
    fun productSpecificationsDoNotRequireDefectMeasurementInstrument() {
        val result = InspectionDraftValidator.validate(
            validDraft.copy(
                netWeightKg = "23000",
                thicknessMm = "1.2",
                widthMm = "1250"
            )
        )

        assertTrue(result is DraftValidation.Valid)
    }

    private fun assertInvalid(result: DraftValidation, expectedText: String) {
        assertTrue(result is DraftValidation.Invalid)
        assertTrue((result as DraftValidation.Invalid).reason.contains(expectedText, ignoreCase = true))
    }
}
