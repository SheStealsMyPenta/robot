package com.example.myapplication;

import android.app.Application;

import com.ubtechinc.coreservice.headerpower.util.LogUtils;
import com.ubtechinc.coreservice.headerpowersdk.HeaderPowerManager;


import com.ubtechinc.coreservice.robotcontrolsdk.RobotControlManager;
import com.ubtechinc.coreservice.robotcontrolsdk.service.lamp.LampService;
import com.ubtechinc.coreservice.robotcontrolsdk.service.odom.OdomService;
import com.ubtechinc.delivery.settingipcsdk.SettingIpcSdk;
import com.ubtechinc.umap.UMapManager;
import com.ubtechinc.umap.data.UMapConst;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化地图导航库
        RobotControlManager.Companion.get().init(this);
        RobotControlManager.Companion.get().startMapService();
        // 初始化诊断服务库
        RobotControlManager.Companion.get().startDiagnosisService();

        // 初始化属性服务库
        RobotControlManager.Companion.get().startPropertyService();

        // 初始化底盘灯服务库
        LampService.Companion.get().init();

        // 初始化 odm 服务库
        OdomService.Companion.get();

        // 初始化地图查询/编辑库
        UMapManager.getInstance().init(this, UMapConst.FileProvider.HOST, false);

        // 初始化头部吧库
        HeaderPowerManager.Companion.get().initManager(this);

        // 延迟监听头部版状态
//        ThreadUtils.runInMainDelay(() -> {
//            HeaderPowerManager.Companion.get().setObjectInBoxStatusListener(boxObjectStatusEntity -> {
//                if (mDetectionListeners.size() > 0) {
//                    LogUtils.i(TAG, "boxObject status " + boxObjectStatusEntity);
//                    List<DetectionResult> results = new ArrayList<>();
//                    results.add(new DetectionResult(1, boxObjectStatusEntity.getObject_left_top()));
//                    results.add(new DetectionResult(2, boxObjectStatusEntity.getObject_right_bottom()));
//                    results.add(new DetectionResult(3, boxObjectStatusEntity.getObject_right_top()));
//                    results.add(new DetectionResult(4, boxObjectStatusEntity.getObject_left_bottom()));
//
//                    ThreadUtils.runInMain(() -> {
//                        DetectionListener[] listeners = mDetectionListeners.toArray(new DetectionListener[0]);
//                        for (DetectionListener listener : listeners) {
//                            listener.onDetection(results);
//                        }
//                    });
//                }
//            });
//        }, 1500);

        // 绑定服务监听地图操作事件
        SettingIpcSdk.getInstance().bindService(this);
    }
}
