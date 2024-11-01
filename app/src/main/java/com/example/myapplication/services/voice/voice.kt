import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

class VoiceService : Service() {

    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate() {
        super.onCreate()
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { /* Do nothing */ }
            override fun onBeginningOfSpeech() { /* Do nothing */ }
            override fun onRmsChanged(rmsdB: Float) { /* Do nothing */ }
            override fun onBufferReceived(buffer: ByteArray?) { /* Do nothing */ }
            override fun onEndOfSpeech() { /* Do nothing */ }
            override fun onError(error: Int) { /* Restart recognition if an error occurs */ }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { recognizedText ->
                    sendToServer(recognizedText.joinToString(" "))
                }
                startListening() // Restart listening for continuous recognition
            }
            override fun onPartialResults(partialResults: Bundle?) { /* Do nothing */ }
            override fun onEvent(eventType: Int, params: Bundle?) { /* Do nothing */ }
        })
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        speechRecognizer.startListening(intent)
    }

    private fun sendToServer(data: String) {
        val url = "https://yourserver.com/api/sendData"
        val requestBody = RequestBody.create("text/plain".toMediaTypeOrNull(), data)
        val request = Request.Builder().url(url).post(requestBody).build()

        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("VoiceService", "Failed to send data: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.body?.string()?.let {
                        Log.d("VoiceService", "Response from server: $it")
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}