# Reglas de ProGuard para ArcelorMittal Defect Detector

# Mantener clases de OpenCV
-keep class org.opencv.** { *; }
-keep class org.opencv.android.** { *; }

# Mantener los métodos nativos
-keepclasseswithmembernames class * {
    native <methods>;
}

# Evitar que R8 elimine código necesario para el procesamiento de imagen
-dontwarn org.opencv.**
