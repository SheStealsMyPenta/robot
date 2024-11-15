import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import kotlin.math.log10

@Composable
fun SpectrumVisualizer(sessionId: Int, modifier: Modifier = Modifier) {
    var fftData by remember { mutableStateOf(ByteArray(0)) }
    var smoothedData by remember { mutableStateOf(FloatArray(0)) }
    var visualizer: Visualizer? by remember { mutableStateOf(null) }

    if (sessionId == 0) return

    DisposableEffect(sessionId) {
        visualizer = Visualizer(sessionId).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]
            setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(
                    visualizer: Visualizer?,
                    waveform: ByteArray?,
                    samplingRate: Int
                ) {}

                override fun onFftDataCapture(
                    visualizer: Visualizer?,
                    fft: ByteArray?,
                    samplingRate: Int
                ) {
                    fft?.let {
                        if (fftData.size != fft.size) {
                            fftData = ByteArray(fft.size)
                            smoothedData = FloatArray(fft.size / 2)
                        }
                        System.arraycopy(fft, 0, fftData, 0, fft.size)
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 4, false, true) // Further reduce capture rate for slower updates
            enabled = true
        }

        onDispose {
            visualizer?.release()
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height * 0.8f // Adjust bar height scale
        val numBars = 12
        val barWidth = width / numBars.toFloat()

        for (i in 0 until numBars) {
            val index = i * 4
            if (index * 2 + 1 >= fftData.size) break
            val real = fftData[index * 2].toInt()
            val imag = fftData[index * 2 + 1].toInt()
            val magnitude = (real * real + imag * imag).toFloat()
            val db = 10 * log10(magnitude.toDouble() + 1e-10).toFloat()

            // Increase smoothing effect
            val targetHeight = (db / 60) * height
            val constrainedHeight = targetHeight.coerceIn(0f, height)
            smoothedData[i] = smoothedData[i] * 0.9f + constrainedHeight * 0.1f // More smoothing for slower response

            drawRect(
                color = Color.Green,
                topLeft = androidx.compose.ui.geometry.Offset(i * barWidth, height - smoothedData[i]),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.6f, smoothedData[i]),
                style = Fill
            )
        }
    }
}
