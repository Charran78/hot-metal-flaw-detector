# Hot Metal Flaw Detector

Prototipo Android de inspección visual en tiempo real. CameraX captura la imagen; OpenCV resalta regiones oscuras de alto contraste como **candidatas a anomalía** y, si el teléfono lo permite, la linterna se puede activar desde la pantalla.

> **Alcance:** demostración técnica en condiciones controladas. No es un detector validado de defectos siderúrgicos y no debe utilizarse para decisiones de calidad o control de maquinaria.

## Qué hace

- Muestra la cámara trasera con una superposición de regiones candidatas.
- Permite controlar la linterna del dispositivo.
- Procesa fotogramas localmente con CameraX y OpenCV.
- Descarta fotogramas atrasados para mantener la interfaz receptiva.
- Indica cuántas regiones candidatas ha encontrado el análisis.

El procesamiento aplica escala de grises, desenfoque Gaussiano, umbral adaptativo y extracción de contornos. Es una heurística visual: **no clasifica** grietas, picaduras, roturas de borde u otras clases de defecto.

## Requisitos

- Android Studio con Android SDK Platform 34 instalado.
- JDK 17.
- Teléfono Android con cámara para probar la captura y la linterna. La linterna puede no estar disponible en todos los dispositivos.

## Compilar y probar

Desde la raíz del proyecto, en Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

El APK de depuración queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

La app solicita permiso de cámara al iniciarse. Para ver candidatos, apunta a una superficie clara preparada con marcas oscuras y buena iluminación. El resultado depende de la superficie, la distancia, el movimiento y la iluminación; un reflejo también puede aparecer como candidato.

## Arquitectura

- **CameraX:** previsualización, análisis de fotogramas, ciclo de vida de cámara y linterna.
- **OpenCV:** operaciones clásicas de procesamiento de imagen y extracción de contornos.
- **Kotlin/StateFlow:** estado de la inspección separado de la vista.
- **Overlay:** dibuja las cajas candidatas y compensa rotación y recorte de la previsualización.

Los fotogramas se procesan en el dispositivo. Esta demo no incluye subida de imágenes, modelos de aprendizaje automático, medición en milímetros ni integración con sistemas industriales.

## Límites y trabajo futuro

No se ha validado con chapa caliente o en movimiento, cámaras industriales ni un conjunto etiquetado de defectos. Tampoco se han establecido métricas de precisión, falsos positivos, latencia o rendimiento en teléfonos de producción. El resultado no acciona PLC, SCADA, Edge ni Azure.

Para evolucionar el prototipo habría que definir criterios de aceptación, reunir imágenes representativas y etiquetadas, evaluar errores y rendimiento en el dispositivo objetivo, y validar por separado cualquier uso industrial con los responsables de planta y seguridad.

## Estructura

```text
app/src/main/java/.../
  MainActivity.kt             Coordinación de pantalla y CameraX
  CameraFrameAnalyzer.kt      Conversión y análisis de fotogramas
  AnomalyDetector.kt          Heurística OpenCV para regiones candidatas
  DetectionOverlayView.kt     Superposición visual
  InspectionModels.kt         Modelos de estado y coordenadas
  InspectionViewModel.kt      Estado observable de la inspección
```
