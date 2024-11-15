package com.example.myapplication.service;

import static com.example.myapplication.consts.StaticConst.websocketUrl;
import static com.example.myapplication.service.robotControl.RobotCloudService.IDENTITY_KEY;
import static com.example.myapplication.service.robotControl.RobotCloudService.PREFERENCE_NAME;
import static com.example.myapplication.service.robotControl.RobotCloudService.ROBOT_TYPE;
import static com.ubtech.carbot.devconfigsdk.utils.SPHelper.context;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.myapplication.datamodel.MessageViewModel;
import com.example.myapplication.datamodel.SoundDetectionViewModel;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class VoiceVolumeService extends Service {

    MyWebSocketClient socketClient = new MyWebSocketClient();
    private static String defaultStr = "null";
    private AudioTrack audioTrack;
    private static final String TAG = "VoiceVolumeService";
    private static int SAMPLE_RATE = 24000;
    private volatile String item_id = defaultStr;
    private volatile String previous_item_id = defaultStr;
    private volatile boolean release = false;
    private int lengthOfCurrentPlay;
    //    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
//            SAMPLE_RATE,
//            AudioFormat.CHANNEL_IN_MONO,
//            AudioFormat.ENCODING_PCM_16BIT
//    );
    private static final int BUFFER_SIZE = 4800;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private boolean isPause = true;
    private static volatile boolean isPlayingResponse = false; // 播放标志，防止自我拾音
    private WebSocket webSocket;
    private final OkHttpClient client = new OkHttpClient();
    private ExecutorService audioExecutor;
    private volatile boolean isHolding = false;
    private final IBinder binder = new VoiceBinder();

    // VoiceBinder 内部类，继承 Binder，用于返回 Service 实例
    public class VoiceBinder extends Binder {
        public VoiceVolumeService getService() {
            return VoiceVolumeService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {


        return START_STICKY;
    }

    // 挂起录音
    public void pauseRecording() {
        isPause = true;
        MessageViewModel.INSTANCE.addChatMessage("录音已挂起");
    }

    // 恢复录音
    public void resumeRecording() {
        isPause = false;
        MessageViewModel.INSTANCE.addChatMessage("录音已恢复");
    }

    // 挂起录音
    public void holdRecording() {
        isHolding = true;
        if(checkInterrupt()){
            interrupt();
        }
        MessageViewModel.INSTANCE.addChatMessage("开始录音");

    }

    private boolean checkInterrupt() {
        return isPlayingResponse && audioTrack.getPlaybackHeadPosition() < lengthOfCurrentPlay;
    }

    // 恢复录音
    public void releaseRecord() {
        isHolding = false;
        MessageViewModel.INSTANCE.addChatMessage("停止录音");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("recordedDataBuffer:---",recordedDataBuffer.size()+" ");
                if(recordedDataBuffer.size()<=2){
                    recordedDataBuffer.clear();
                    return;
                }
                socketClient.send("input_audio_buffer.append", recordedDataBuffer);
                recordedDataBuffer.clear();
            }
        }).start();

    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioExecutor = Executors.newSingleThreadExecutor();
        connectWebSocket();
        startRecording();
    }

    private void connectWebSocket() {
        // 获取注册时返回的 identity
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        String identity = sharedPreferences.getString(IDENTITY_KEY, null);
        if (identity == null) {

            Log.e(TAG, "Identity not found! Cannot connect WebSocket.");
            return;
        }

        // 构造 WebSocket URL 并添加 query 参数
        String websocketUrlWithQuery = websocketUrl + "?client_type="+ROBOT_TYPE+"&client_identity=" + identity;

        // 创建 WebSocket 请求
        Request request = new Request.Builder()
                .url(websocketUrlWithQuery)  // 在 URL 中添加查询参数
                .addHeader("Sec-WebSocket-Protocol", "realtime") // Optional: add any necessary headers
                .build();

        // 连接 WebSocket
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                socketClient.setWebSocket(webSocket);
                Log.d(TAG, "WebSocket opened with response: " + response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "Received message from server: " + text);
                MyWebSocketClient.ServerMessageResult result = socketClient.handleServerMessage(text);
                if (result != null) {
                    switch (result.type) {
                        case AUDIO_DATA:
                            playAudioData(result.audioData);
                            item_id = result.content;
                            break;
                        case SPEECH_STOPPED:
//                        stopAudioData();
                            break;
                        case SPEECH_TRANSCRIPT:
                            MessageViewModel.INSTANCE.addLogMessage(result.content);
                            break;
                        case SPEED_START:
                            // Implement logic for interrupting current recording.
                            if (!defaultStr.equals(item_id)) {
                                if (checkInterrupt()) {
                                    Log.d("playAudioData", "+" + audioTrack.getPlaybackHeadPosition());
                                    interrupt();
                                    Log.d("truncated", "truncated!");
                                    item_id = "";
                                }
                            }
                            break;
                        case CONVERSATION_CREATE:
                            Log.d("truncated", "truncated!");
                            break;
                        case OTHER:
                            MessageViewModel.INSTANCE.addChatMessage(result.repType);
                    }
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed: " + t.getMessage());
                connectWebSocket(); // Retry connection on failure
            }
        });
    }


    public void stopPlayback() {
        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {

        }
    }

    private void interrupt() {
        socketClient.send_cancel_event("response.cancel");

        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            int playbackHeadPosition = audioTrack.getPlaybackHeadPosition();
            int offsetMilliseconds = (int) ((playbackHeadPosition / (float) SAMPLE_RATE) * 1000);
            audioTrack.flush();   // 清空缓冲区，删除所有未播放的数据
            audioTrack.release();
            release = true;
            isPlayingResponse = false;  // 重置播放状态标志
            lengthOfCurrentPlay=0;
            // 如果需要考虑硬件延迟，可以加上延迟补偿
            // int adjustedOffsetMilliseconds = offsetMilliseconds + hardwareDelayMs;
            if (playTask != null) {
                playTask.cancel(true);
            }
//            audioTrack.stop();
//            audioTrack.flush();
            // 发送截断事件，使用计算得到的偏移量
            socketClient.send_trancate_event("conversation.item.truncate", item_id, offsetMilliseconds);
        } else {
            // 如果音频未在播放，可能需要处理这种情况
            socketClient.send_trancate_event("conversation.item.truncate", item_id, 0);
        }
        isPlayingResponse = false;

    }

    private volatile List<short[]> recordedDataBuffer = new ArrayList<>(); // 暂存录音数据

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            MessageViewModel.INSTANCE.addChatMessage("权限申请失败！");
            return;
        }
//        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
//        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 对于 API 23 及以上，使用 AudioRecord.Builder
            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(audioFormat)

                    .setBufferSizeInBytes(BUFFER_SIZE)
                    // 如果需要指定 sessionId，可以取消注释以下行
                    // .setSessionId(audioSessionId)
                    .build();

        } else {
            // 对于 API 23 以下，使用传统的构造函数
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    BUFFER_SIZE
            );

        }

        // 启用 NoiseSuppressor 和 AutomaticGainControl
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(audioRecord.getAudioSessionId()).setEnabled(true);
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(audioRecord.getAudioSessionId()).setEnabled(true);
        }

        audioRecord.startRecording();
        isRecording = true;

        new Thread(() -> {
            short[] buffer = new short[BUFFER_SIZE];
            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    if (isHolding) {
                        // 按住说话模式：暂存数据
                        Log.d(TAG, "appending 。。。。: ");
                        recordedDataBuffer.add(buffer.clone());
                        SoundDetectionViewModel.INSTANCE.updateFftData(processAudioData(buffer));

                    }
                    if (isPause) {
                        continue;
                    }
                    Log.d(TAG, "sending 。。。。: ");
                    socketClient.send("input_audio_buffer.append", buffer);

                    SoundDetectionViewModel.INSTANCE.updateFftData(processAudioData(buffer));
                } else {
                    SoundDetectionViewModel.INSTANCE.updateVoiceDetected(false);

                }
            }
        }).start();
    }

    // Process the audio data with FFT
    private List<Float> processAudioData(short[] data) {
        // Convert short array to double array
        double[] audioData = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            audioData[i] = (double) data[i]; // Convert short to double
        }

        // Perform FFT using JTransform
        DoubleFFT_1D fft = new DoubleFFT_1D(audioData.length);
        fft.realForward(audioData);

        // Calculate magnitudes
        ArrayList<Float> magnitudes = new ArrayList<>();
        for (int i = 0; i < audioData.length; i += 2) {
            double real = audioData[i];
            double imag = audioData[i + 1];
            double magnitude = Math.sqrt(real * real + imag * imag);
            magnitudes.add((float) magnitude);
        }

        return magnitudes;
    }
    // 创建一个 Future 任务来控制播放和写入操作
    Future<?> playTask = null;

    public void playAudioData(byte[] audioData) {
        if (audioTrack == null || release) {
            initAudioTrack();
            audioTrack.play();
            release = false;
        }
        lengthOfCurrentPlay += audioData.length/2;
        audioExecutor.submit(() -> {
            if (audioTrack != null) {
                audioTrack.play();
                audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                    @Override
                    public void onMarkerReached(AudioTrack track) {
                        // 播放完毕时会触发这个回调
                        isPlayingResponse = false;
                        audioTrack.stop();
                        audioTrack.flush();
                    }

                    @Override
                    public void onPeriodicNotification(AudioTrack track) {
                        // 每次播放位置更新时都会触发这个回调

                    }
                });
                isPlayingResponse = true;
                audioTrack.write(audioData, 0, audioData.length);
            }
        });
//        audioExecutor.execute(() -> {
//            if (audioTrack != null) {
//                audioTrack.play();
//                audioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
//                    @Override
//                    public void onMarkerReached(AudioTrack track) {
//                        // 播放完毕时会触发这个回调
//                        isPlayingResponse=false;
//                        audioTrack.stop();
//                        audioTrack.flush();
//                    }
//
//                    @Override
//                    public void onPeriodicNotification(AudioTrack track) {
//                        // 每次播放位置更新时都会触发这个回调
//
//                    }
//                });
//                isPlayingResponse=true;
//                audioTrack.write(audioData, 0, audioData.length);
//            }
//        });
//        audioTrack.stop();
    }

    private void initAudioTrack() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int audioRecordSessionId = audioManager.generateAudioSessionId();
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT) // 添加音频编码格式
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO) // 添加通道配置，例如单声道
                        .build())
                .setBufferSizeInBytes(BUFFER_SIZE)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(audioRecordSessionId)
                .build();
        SoundDetectionViewModel.INSTANCE.updateSessionId(audioTrack.getAudioSessionId());

    }

    private void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;  // 返回绑定服务的 Binder
    }
}
