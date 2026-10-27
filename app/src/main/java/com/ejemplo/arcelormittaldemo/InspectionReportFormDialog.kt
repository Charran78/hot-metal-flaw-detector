package com.ejemplo.arcelormittaldemo

import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class InspectionReportFormDialog(
    private val context: Context,
    private val evidence: CapturedEvidence,
    private val onSubmit: (InspectionReportDraft) -> Unit,
    private val onDismiss: () -> Unit
) {
    private val inputs = mutableMapOf<String, EditText>()
    private var selectedDefectTypeId = ""
    private var selectedRecommendation = ""
    private var selectedInstrument = ""
    private lateinit var dialog: androidx.appcompat.app.AlertDialog
    private lateinit var submitButton: android.widget.Button
    private lateinit var errorText: TextView

    fun show() {
        val content = createContent()
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Registrar inspección")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Generar informe", null)
            .create()

        dialog.setOnShowListener {
            submitButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            submitButton.setOnClickListener {
                when (val result = InspectionDraftValidator.validate(readDraft())) {
                    is DraftValidation.Valid -> {
                        errorText.text = ""
                        submitButton.isEnabled = false
                        onSubmit(result.draft)
                    }
                    is DraftValidation.Invalid -> errorText.text = result.reason
                }
            }
        }
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }

    fun setBusy(busy: Boolean, message: String = "") {
        if (!::dialog.isInitialized || !dialog.isShowing) return
        errorText.text = message
        submitButton.isEnabled = !busy
        submitButton.text = if (busy) "Preparando informe…" else "Generar informe"
        dialog.setCancelable(!busy)
        dialog.setCanceledOnTouchOutside(!busy)
    }

    fun dismiss() {
        if (::dialog.isInitialized) dialog.dismiss()
    }

    private fun createContent(): ScrollView {
        val density = context.resources.displayMetrics.density
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), (8 * density).toInt())
        }
        val preview = ImageView(context).apply {
            setImageBitmap(evidence.image)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Fotografía capturada con la región candidata marcada"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (176 * density).toInt()
            ).apply {
                bottomMargin = (8 * density).toInt()
            }
        }
        content.addView(preview)
        content.addView(
            createHint(
                "Informe ${evidence.id.take(8).uppercase()} · " +
                    "ID generado automáticamente; se registra la hora y la imagen de esta captura."
            )
        )
        content.addView(createSection("Identificación del producto"))
        addTextInput(content, "coilId", "Número de bobina (Coil ID)")
        addTextInput(content, "packageId", "ID de paquete")
        content.addView(createHint("Indica al menos el ID de bobina o el ID de paquete."))
        addTextInput(content, "barcodeOrQr", "Código de barras / QR (entrada manual)")
        addTextInput(content, "heatNumber", "Número de colada")
        addTextInput(content, "customerOrderNumber", "Pedido de cliente")

        content.addView(createSection("Especificación del material"))
        addTextInput(content, "steelGrade", "Grado del acero")
        addTextInput(content, "manufacturingStandard", "Norma de fabricación")
        addTextInput(content, "surfaceFinish", "Acabado / recubrimiento")
        addTextInput(content, "netWeightKg", "Peso neto (kg)", DECIMAL_INPUT)
        addTextInput(content, "thicknessMm", "Espesor nominal (mm)", DECIMAL_INPUT)
        addTextInput(content, "widthMm", "Ancho nominal (mm)", DECIMAL_INPUT)
        addTextInput(content, "lengthMm", "Longitud (mm, si aplica)", DECIMAL_INPUT)
        addTextInput(content, "manufacturerOrPlant", "Fabricante / planta (texto)")

        content.addView(createSection("Confirmación del técnico"))
        addTextInput(content, "inspectorName", "Técnico / inspector")
        content.addView(
            createHint(
                "La caja solo señala contraste visual. La app no clasifica el defecto; " +
                    "esta selección la confirma el técnico."
            )
        )
        addDropdown(
            content,
            "Tipo de defecto observado (selección manual)",
            DefectCatalog.types.map(DefectType::displayName)
        ) { selected ->
            selectedDefectTypeId = DefectCatalog.types.getOrNull(selected)?.id.orEmpty()
        }
        addDropdown(
            content,
            "Recomendación para revisión humana",
            PreventiveRecommendations.options
        ) { selected ->
            selectedRecommendation = PreventiveRecommendations.options.getOrNull(selected).orEmpty()
        }
        addTextInput(
            content,
            "defectLocation",
            "Ubicación en el producto (referencia manual)"
        )
        content.addView(
            createHint(
                "Ejemplo: 2,5 m desde la cabeza, lado operador. " +
                    "No se calcula automáticamente con esta cámara."
            )
        )

        content.addView(createSection("Mediciones manuales"))
        content.addView(
            createHint(
                "La app no deduce dimensiones de la imagen. Introduce solo valores medidos " +
                    "y el instrumento utilizado; si no se midió, deja el campo vacío."
            )
        )
        addTextInput(content, "areaMm2", "Área medida (mm²)", DECIMAL_INPUT)
        addTextInput(content, "depthMinMm", "Profundidad mínima (mm)", DECIMAL_INPUT)
        addTextInput(content, "depthMaxMm", "Profundidad máxima (mm)", DECIMAL_INPUT)
        addDropdown(content, "Instrumento usado para las mediciones", MeasurementInstruments.options) {
            selectedInstrument = MeasurementInstruments.options.getOrNull(it).orEmpty()
        }
        addTextInput(content, "instrumentDetails", "Detalle del instrumento (si seleccionas Otro)")
        addTextInput(content, "notes", "Observaciones del técnico", InputType.TYPE_CLASS_TEXT, multiline = true)

        errorText = TextView(context).apply {
            setTextColor(Color.rgb(176, 0, 32))
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        content.addView(errorText)

        return ScrollView(context).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun addTextInput(
        parent: LinearLayout,
        key: String,
        label: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        multiline: Boolean = false
    ) {
        val density = context.resources.displayMetrics.density
        val layout = TextInputLayout(context).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }
        val editText = EditText(context).apply {
            this.inputType = inputType
            setSingleLine(!multiline)
            filters = arrayOf(InputFilter.LengthFilter(if (multiline) MAX_NOTES_LENGTH else MAX_FIELD_LENGTH))
            if (multiline) {
                minLines = 2
                maxLines = 4
            }
        }
        layout.addView(editText)
        parent.addView(layout)
        inputs[key] = editText
    }

    private fun addDropdown(
        parent: LinearLayout,
        label: String,
        options: List<String>,
        onSelection: (Int) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val layout = TextInputLayout(context).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }
        val dropdown = MaterialAutoCompleteTextView(context).apply {
            setAdapter(
                ArrayAdapter(
                    context,
                    android.R.layout.simple_list_item_1,
                    options
                )
            )
            keyListener = null
            inputType = InputType.TYPE_NULL
            threshold = 0
            setOnItemClickListener { _, _, position, _ -> onSelection(position) }
        }
        layout.addView(dropdown)
        parent.addView(layout)
    }

    private fun createSection(title: String): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, (18 * density).toInt(), 0, (4 * density).toInt())
        }
    }

    private fun createHint(text: String): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, (4 * density).toInt(), 0, (4 * density).toInt())
        }
    }

    private fun readDraft(): InspectionReportDraft = InspectionReportDraft(
        coilId = value("coilId"),
        packageId = value("packageId"),
        barcodeOrQr = value("barcodeOrQr"),
        heatNumber = value("heatNumber"),
        inspectorName = value("inspectorName"),
        netWeightKg = value("netWeightKg"),
        thicknessMm = value("thicknessMm"),
        widthMm = value("widthMm"),
        lengthMm = value("lengthMm"),
        steelGrade = value("steelGrade"),
        manufacturingStandard = value("manufacturingStandard"),
        surfaceFinish = value("surfaceFinish"),
        customerOrderNumber = value("customerOrderNumber"),
        manufacturerOrPlant = value("manufacturerOrPlant"),
        defectTypeId = selectedDefectTypeId,
        recommendation = selectedRecommendation,
        areaMm2 = value("areaMm2"),
        depthMinMm = value("depthMinMm"),
        depthMaxMm = value("depthMaxMm"),
        instrument = selectedInstrument,
        instrumentDetails = value("instrumentDetails"),
        defectLocation = value("defectLocation"),
        notes = value("notes")
    )

    private fun value(key: String): String = inputs[key]?.text?.toString().orEmpty()

    private companion object {
        const val MAX_FIELD_LENGTH = 256
        const val MAX_NOTES_LENGTH = 2_000
        const val DECIMAL_INPUT =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
    }
}
