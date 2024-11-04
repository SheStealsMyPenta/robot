package com.example.myapplication.service;

import static com.example.myapplication.consts.StaticConst.websocketUrl;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class VoiceVolumeService extends Service {
    MyWebSocketClient socketClient = new MyWebSocketClient();
    private AudioTrack audioTrack;
    private static final String TAG = "VoiceVolumeService";
    private static final int SAMPLE_RATE = 16000; // 采样率 16kHz
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
    );

    private static final float VOLUME_THRESHOLD = 2000.0f; // 声音阈值，根据需要调整

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private boolean isSendingAudio = false; // 是否正在发送音频数据的标志
    private WebSocket webSocket;
    private final OkHttpClient client = new OkHttpClient();
    private String event_id = "";
    private String session_id = "";
    private ExecutorService audioExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        audioExecutor = Executors.newSingleThreadExecutor();
        //build websocket;
        connectWebSocket(); // 在 Service 创建时连接 WebSocket

        startRecording();
    }

    private void connectWebSocket() {
        Request request = new Request.Builder()
                .url(websocketUrl)
                .addHeader("Sec-WebSocket-Protocol", "realtime") // 添加协议参数
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                socketClient.setWebSocket(webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received message from server: " + text);
                MyWebSocketClient.ServerMessageResult result = socketClient.handleServerMessage(text);
                if (result != null) {
                    switch (result.type) {
                        case AUDIO_DATA:
                            playAudioData(result.audioData);
                            break;
                        case SPEECH_STOPPED:
                            Log.d(TAG, "stop audio");
                            stopAudioData();
                    }

                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed: " + t.getMessage());
                stopRecording(); // 连接失败时停止录音
            }
        });
    }

    private void stopAudioData() {
        if(audioTrack!=null){
            audioTrack.stop();
        }
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
        );

        audioRecord.startRecording();
        isRecording = true;

        new Thread(() -> {
            short[] buffer = new short[BUFFER_SIZE];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    boolean speed = isSpeech(buffer);
                    if (speed) {
                        Log.d("Robot log", " detect voice");
                        socketClient.send("input_audio_buffer.append", buffer);
                    }
                }
            }
        }).start();
    }

    private void playAudioData(byte[] audioData) {
        if (audioTrack == null) {
            initAudioTrack();
        }

        // 使用音频线程池来播放音频数据
        audioExecutor.execute(() -> {
            if (audioTrack != null) {
                audioTrack.write(audioData, 0, audioData.length);
            }
        });
    }

    private void initAudioTrack() {
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
        );
        audioTrack.play();
    }

    public boolean isSpeech(short[] audioData) {
        double energy = 0;

        // 计算能量
        for (short sample : audioData) {
            energy += Math.abs(sample);
        }

        energy /= audioData.length;

        // 判断是否为语音
        return energy > 1000;
    }

    private void sendAudioData(byte[] audioData, int length) {
        if (webSocket != null && isSendingAudio) {
            Log.d("sendingMsgToServer", Arrays.toString(audioData));
            webSocket.send(ByteString.of(audioData, 0, length));
        }
    }

    private void stopRecording() {
        isRecording = false;
        isSendingAudio = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (webSocket != null) {
            webSocket.close(1000, "Service stopped");
        }
        stopSelf(); // 停止 Service
    }

    private float calculateRms(byte[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i++) {
            sum += buffer[i] * buffer[i];
        }
        double mean = sum / (double) length;
        return (float) Math.sqrt(mean);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording(); // 停止录音和 WebSocket 连接
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
