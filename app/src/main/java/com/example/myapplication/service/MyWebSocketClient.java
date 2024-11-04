package com.example.myapplication.service;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.UUID;

import okhttp3.WebSocket;

public class MyWebSocketClient {

    public static class ServerMessageResult {
        public enum MessageType {
            AUDIO_DATA,
            ERROR,
            SESSION_CREATED,
            SPEECH_STOPPED,
            OTHER
        }

        public MessageType type;
        public byte[] audioData;
        public String errorMessage;
        public String eventId;

        public ServerMessageResult(MessageType type) {
            this.type = type;
        }
    }
    private WebSocket webSocket;
    private int count = 0;
    private String event_id = "";
    private String session_id = "";

    // 判断WebSocket是否已连接
    private boolean isConnected() {
        return webSocket != null;
    }

    // 生成带前缀的唯一事件ID
    private String generateEventId(String prefix) {
        return prefix + UUID.randomUUID().toString();
    }

    /**
     * 向WebSocket发送事件，并确保格式符合官方实现
     *
     * @param eventName 事件类型
     * @param data      事件数据
     */
    public void send(String eventName, JSONObject data) {
        if (!isConnected()) {
            throw new IllegalStateException("RealtimeAPI is not connected");
        }

        if (data == null) {
            data = new JSONObject();
        }

        // 创建事件对象
        JSONObject event = new JSONObject();
        try {
            event.put("event_id", generateEventId("evt_"));
            event.put("type", eventName);
            // 将data中的内容合并到event
            for (Iterator<String> it = data.keys(); it.hasNext(); ) {
                String key = it.next();
                event.put(key, data.get(key));
            }
            // 打印发送的消息（用于调试）
            Log.d("WebSocketClient", "Sent: " + eventName + " " + event.toString());
            // 发送消息到服务器
            webSocket.send(event.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String eventName, short[] data) {
        if (!isConnected()) {
            throw new IllegalStateException("RealtimeAPI is not connected");
        }
        if (event_id == "") return;
        // 创建事件对象
        JSONObject event = new JSONObject();

        try {
            event.put("event_id", event_id);
            byte[] byteBuffer = shortArrayToByteArray(data, data.length);
            // 将音频数据编码为Base64
            String base64Audio = Base64.encodeToString(byteBuffer, Base64.NO_WRAP);
            event.put("type", eventName);
            event.put("audio", base64Audio);
            // 打印发送的消息（用于调试）
            Log.d("WebSocketClient", "Sent: " + eventName + " " + event.toString());
            // 发送消息到服务器
            count++;
            webSocket.send(event.toString());
//            if (count > 18) {
//                webSocket.send("{\"type\": \"input_audio_buffer.commit\"}");
//                count=0;
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public ServerMessageResult handleServerMessage(String text) {
        try {
            JSONObject response = new JSONObject(text);
            String type = response.getString("type");
            if ("response.audio.delta".equals(type)) {
                String base64AudioDelta = response.getString("delta");
                byte[] audioData = Base64.decode(base64AudioDelta, Base64.NO_WRAP);
                ServerMessageResult result = new ServerMessageResult(ServerMessageResult.MessageType.AUDIO_DATA);
                result.audioData = audioData;
                return result;


            } else if ("error".equals(type)) {
                JSONObject error = response.getJSONObject("error");
                String message = error.getString("message");
                Log.e("WebSocket Error", message);
            } else if ("session.created".equals(type)) {
                this.event_id = response.getString("event_id");
            }else if ("input_audio_buffer.speech_stopped".equals(type)){
//                ServerMessageResult result = new ServerMessageResult(ServerMessageResult.MessageType.SPEECH_STOPPED);
//                return result;
            }
            // 根据需要处理其他消息类型
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
//    private void playAudioData(byte[] audioData) {
//        if (audioTrack == null) {
//            initAudioTrack();
//        }
//
//        // 使用音频线程池来播放音频数据
//        audioExecutor.execute(() -> {
//            if (audioTrack != null) {
//                audioTrack.write(audioData, 0, audioData.length);
//            }
//        });
//    }

    private byte[] shortArrayToByteArray(short[] shortArray, int length) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(length * 2);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN); // 设置字节序为小端序
        for (int i = 0; i < length; i++) {
            byteBuffer.putShort(shortArray[i]);
        }
        return byteBuffer.array();
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }
}
