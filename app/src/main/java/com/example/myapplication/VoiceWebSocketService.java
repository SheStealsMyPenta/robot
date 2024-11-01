package com.example.myapplication;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.myapplication.R;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class VoiceWebSocketService extends Service {

    private static final String TAG = "VoiceWebSocketService";
    private SpeechRecognizer speechRecognizer;
    private WebSocket webSocket;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "启动声音识别");
        initializeSpeechRecognizer();
        Log.d(TAG, "启动websocket");
        initializeWebSocket();
//        startForegroundService();
    }

    private void startForegroundService() {
        Notification notification = new NotificationCompat.Builder(this, "ServiceChannel")
                .setContentTitle("Voice Service Running")
                .setContentText("Listening for voice input")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        startForeground(1, notification);
    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                Log.e(TAG, "Error in SpeechRecognizer: " + error);
                startListening(); // Retry on error
            }

            @Override
            public void onResults(Bundle results) {
                if (results != null) {
                    for (String result : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)) {
                        Log.d("info00", result);

                        // sendMessageToWebSocket(result); // Send the recognized text to WebSocket
                        break;
                    }
                }
                startListening(); // Restart listening for continuous recognition
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
        startListening();
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        speechRecognizer.startListening(intent);
    }

    private void initializeWebSocket() {
        Request request = new Request.Builder().url("wss://realtime.wonderbyte.ai/websocket").build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connected");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received from OpenAI: " + text);
                // You can add code here to update UI or notify other parts of the app with the received message
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed: " + t.getMessage());
            }
        });
    }

    private void sendMessageToWebSocket(String message) {
        new Thread(() -> webSocket.send(message)).start();
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (webSocket != null) {
            webSocket.close(1000, null);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}