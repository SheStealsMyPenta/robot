package com.example.myapplication.service.robotControl;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.example.myapplication.datamodel.MessageViewModel;
import com.ubtechinc.umap.data.Point;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

public class RobotCloudService {
    public enum PointType {
        NORMAL("normal"),
        CHARGING("charging");

        private final String value;

        // 构造函数，传入值
        PointType(String value) {
            this.value = value;
        }

        // 获取对应的字符串值
        public String getValue() {
            return value;
        }

        // 静态方法：根据名称获取枚举值
        public static PointType fromString(String value) {
            for (PointType pointType : PointType.values()) {
                if (pointType.value.equalsIgnoreCase(value)) {
                    return pointType;
                }
            }
            throw new IllegalArgumentException("Unexpected value: " + value);
        }
    }

    private static final String TAG = "RobotCloudService";
    public static final String PREFERENCE_NAME = "RobotPrefs";
    public static final String IDENTITY_KEY = "identity";
    public static final String ROBOT_TYPE = "catering";

    // Getter for IDENTITY_KEY
    public static String getIdentityKey() {
        return IDENTITY_KEY;
    }

    private static RobotCloudService instance;
    private Context context;

    // 构造函数，传入Context
    private RobotCloudService(Context context) {
        this.context = context.getApplicationContext();  // 使用ApplicationContext避免内存泄漏
    }

    // 获取单例实例
    public static synchronized RobotCloudService getInstance(Context context) {
        if (instance == null) {
            instance = new RobotCloudService(context);
        }
        return instance;
    }

    // 获取单例实例
    public static synchronized RobotCloudService getInstance() {

        return instance;
    }


    // 判断本地是否有identity，如果没有则进行注册
    public void checkAndRegisterRobot() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        String identity = sharedPreferences.getString(IDENTITY_KEY, null);

        if (identity == null) {
            // 本地没有identity，执行注册
            registerRobot();
        } else {
            // 本地已有identity
            Log.d(TAG, "Identity exists: " + identity);
        }
    }

    // 执行设备注册
    private void registerRobot() {
        OkHttpClient client = new OkHttpClient();

        // 构建设备注册请求体
        String requestBody = "{ \"name\": \"friday\", \"robot_type\": \"catering\" }";

        RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"),
                requestBody);

        // 创建POST请求
        Request request = new Request.Builder()
                .url("https://friday.wonderbyte.ai/robot/register")
                .post(body)
                .addHeader("Authorization", "Basic " + Base64.encodeToString("admin:123456".getBytes(), Base64.NO_WRAP))
                .build();

        // 发起请求
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        // 解析返回的JSON响应
                        String jsonResponse = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        JSONObject data = jsonObject.getJSONObject("data");
                        String identity = data.getString("identity");

                        // 保存identity到SharedPreferences
                        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(IDENTITY_KEY, identity);
                        editor.apply();

                        MessageViewModel.INSTANCE.addLogMessage("注册成功！注册ID：" + identity);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    // 执行点位注册
    public void registerPoints(List<Point> points) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        String identity = sharedPreferences.getString(IDENTITY_KEY, null);
        JSONArray pointsArray = new JSONArray();
        if (points == null) {
            Log.e(TAG, "无法注册点位，未找到identity！");
            try {

                JSONObject pointObject = new JSONObject();
                pointObject.put("point_type", PointType.NORMAL.getValue());
                pointObject.put("label", "default");
                pointObject.put("point_x", "default");
                pointObject.put("point_y", "default");
                pointObject.put("point_z", "default");
                pointObject.put("identity", "default");
                pointsArray.put(pointObject);
//                return;
            } catch (Exception e) {

            }

        }
        if(points!=null){
            try {
                for (Point point : points) {
                    JSONObject pointObject = new JSONObject();
                    pointObject.put("point_type", point.getName().contains("充电桩") ? PointType.CHARGING.getValue() : PointType.NORMAL.getValue());
                    pointObject.put("label", point.getName());
                    pointObject.put("point_x", point.getPosition().getX());
                    pointObject.put("point_y", point.getPosition().getY());
                    pointObject.put("point_z", point.getAttitude().getYaw());
                    pointObject.put("identity", point.getId());
                    pointsArray.put(pointObject);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
        }


        OkHttpClient client = new OkHttpClient();

        // 构建设备点位注册请求体
        RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"),
                pointsArray.toString());

        // 创建POST请求
        Request request = new Request.Builder()
                .url("https://friday.wonderbyte.ai/robot/" + identity + "/register_points")
                .post(body)
                .addHeader("Authorization", "Basic " + Base64.encodeToString("admin:123456".getBytes(), Base64.NO_WRAP))
                .build();

        // 发起请求
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // 处理成功的响应
                    Log.d(TAG, "点位注册成功！");
                    MessageViewModel.INSTANCE.addLogMessage("注册成功！注册ID：" + identity);
                } else {
                    // 处理失败的响应
                    Log.e(TAG, "点位注册失败：" + response.code());
                    MessageViewModel.INSTANCE.addLogMessage("点位注册失败：" + response.code());
                }
            }
        });
    }


}
