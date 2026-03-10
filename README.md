# 🎬 TicoVision AI 🇨🇷🤖

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![TensorFlow](https://img.shields.io/badge/TensorFlow-%23FF6F00.svg?style=for-the-badge&logo=TensorFlow&logoColor=white)
![OpenCV](https://img.shields.io/badge/opencv-%23white.svg?style=for-the-badge&logo=opencv&logoColor=white)

**TicoVision AI** es una aplicación de edición de video para Android construida con Kotlin y principios de arquitectura moderna (MVVM). Integra algoritmos de Visión Computacional utilizando TensorFlow Lite y MediaPipe para procesamiento directamente en el dispositivo (*on-device*). El núcleo de renderizado está impulsado por herramientas robustas como FFmpeg, garantizando exportaciones de alta fidelidad. Un proyecto que une el desarrollo móvil con la innovación en IA, hecho con puro talento tico.

---

## ✨ Características Principales

* **Edición Lineal Intuitiva:** Recorte, división de clips y organización en línea de tiempo fluida gracias a Jetpack Compose.
* **Inteligencia Artificial On-Device:** Procesamiento de video sin necesidad de internet, protegiendo la privacidad del usuario.
* **Filtros y Segmentación Avanzada:** Uso de modelos neuronales (MediaPipe/TFLite) para eliminar fondos, aplicar seguimiento de movimiento y mejorar la imagen.
* **Renderizado de Alto Rendimiento:** Exportación eficiente utilizando wrappers de FFmpeg y MediaCodec API de Android.
* **Reproducción Fluida:** Integración de Media3 (ExoPlayer) para previsualizaciones en tiempo real sin trabas.

---

## 🛠️ Stack Tecnológico

* **Lenguaje:** [Kotlin](https://kotlinlang.org/)
* **Interfaz de Usuario:** [Jetpack Compose](https://developer.android.com/jetpack/compose)
* **Arquitectura:** MVVM (Model-View-ViewModel) + Clean Architecture
* **Asincronismo:** Coroutines & StateFlow
* **Reproducción Multimedia:** [Media3 (ExoPlayer)](https://developer.android.com/media/media3)
* **Procesamiento de Video:** [FFmpegKit](https://github.com/arthenica/ffmpeg-kit) / MediaCodec API
* **Inteligencia Artificial:** [TensorFlow Lite](https://www.tensorflow.org/lite) & [MediaPipe](https://developers.google.com/mediapipe)
* **Inyección de Dependencias:** [Hilt](https://dagger.dev/hilt/)

---

## 🚀 Requisitos e Instalación

Para compilar y correr este proyecto en tu entorno local, necesitás:

1.  **Android Studio** (Versión más reciente recomendada: Iguana o superior).
2.  **SDK de Android:** API Level 24 (Min) hasta API Level 34 (Target).
3.  Un dispositivo físico o emulador con al menos 4GB de RAM asignada para manejar el procesamiento de video y modelos de IA.

### Pasos para ejecutar:

1. Cloná el repositorio:
   ```bash
   git clone [https://github.com/tu-usuario/ticovision-ai.git](https://github.com/tu-usuario/ticovision-ai.git)
