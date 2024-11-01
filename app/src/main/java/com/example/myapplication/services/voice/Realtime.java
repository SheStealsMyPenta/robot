package com.example.myapplication.services.voice;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class Realtime {
    public static WebSocket webSocket;
   public void  ChatGPT_realtime(){
       OkHttpClient client = new OkHttpClient();
       Request request = new Request.Builder()
               .url("https://realtime.wonderbyte.ai/websocket")
               .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
           @Override
           public void onOpen(WebSocket webSocket, Response response) {
               // 连接成功
               System.out.println("连接成功");
           }

           @Override
           public void onMessage(WebSocket webSocket, String text) {
               // 处理接收到的消息
               System.out.println("连接成功");
           }

           @Override
           public void onFailure(WebSocket webSocket, Throwable t, Response response) {
               // 处理连接失败
           }
       });

   }

}
