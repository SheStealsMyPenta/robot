package com.example.myapplication
import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.view.AudioWaveformView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_main)
        val REQUEST_RECORD_AUDIO_PERMISSION = 0
        ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)

        // 启动VoiceWebSocketService
        val serviceIntent = Intent(this, VoiceWebSocketService::class.java)
        startService(serviceIntent)
        val testWaveform = ByteArray(128)
        for (i in testWaveform.indices) {
            testWaveform[i] = (Math.sin(i * 0.1) * 128).toInt().toByte() // 简单的正弦波模拟数据
        }
        val audioWaveformView = findViewById<AudioWaveformView>(R.id.audioWaveformView2)
        audioWaveformView.updateWaveform(testWaveform)
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        Greeting("Android")
        AutoWaveformView()
    }
}

@Composable
fun AutoWaveformView() {
    TODO("Not yet implemented")

}
