package com.example.myapplication.service.robotControl;

import android.util.Log;

import com.ubtechinc.coreservice.robotcontrol.data.NavTaskType;
import com.ubtechinc.coreservice.robotcontrol.data.RobotConst;
import com.ubtechinc.coreservice.robotcontrol.message.ExtraParams;
import com.ubtechinc.coreservice.robotcontrol.message.map.LiftPose;
import com.ubtechinc.coreservice.robotcontrol.message.map.MapProgress;
import com.ubtechinc.coreservice.robotcontrol.message.map.RequestNavLiftMsg;
import com.ubtechinc.coreservice.robotcontrolsdk.async.ProgressiveAsyncTask;
import com.ubtechinc.coreservice.robotcontrolsdk.conflict.ConflictStrategy;
import com.ubtechinc.coreservice.robotcontrolsdk.interfaces.IMapService;
import com.ubtechinc.coreservice.robotcontrolsdk.service.map.MapService;
import com.ubtechinc.delivery.common.exception.CodeException;
import com.ubtechinc.umap.callback.IUMapProgressCallback;
import com.ubtechinc.umap.data.Point;
import com.ubtrobot.async.DoneCallback;
import com.ubtrobot.async.FailCallback;
import com.ubtrobot.async.ProgressCallback;


public class NavigationService {

    private static final String TAG = "NavigationService";
    private IMapService mMapService;
    private ProgressiveAsyncTask mNavigationTask;

    public NavigationService() {
        mMapService = MapService.Companion.get();
    }
    public interface MyProgressCallback {
        void onProgress(MapProgress progress);
    }
    /**
     * 导航到指定目的地
     *
     * @param currentPoint 起始点的坐标
     * @param targetPoint  目的地的坐标
     * @param callback     导航的回调接口
     */
    public void navigateToPoint(Point currentPoint, Point targetPoint, final NavigationCallback callback) {
        // 构建导航命令配置类
        RequestNavLiftMsg liftMsg = new RequestNavLiftMsg();
        liftMsg.setCurrentPose(new LiftPose(0,
                (float) currentPoint.getPosition().getX(),
                (float) currentPoint.getPosition().getY(),
                (float) currentPoint.getAttitude().getYaw()));
        liftMsg.setDestPose(new LiftPose(0,
                (float) targetPoint.getPosition().getX(),
                (float) targetPoint.getPosition().getY(),
                (float) targetPoint.getAttitude().getYaw()));
        liftMsg.setTask_type(NavTaskType.NAVIGATION_DELIVER_4);

        // 设置导航任务的额外参数
        ExtraParams extraParams = new ExtraParams()
                .conflictStategy(ConflictStrategy.FIX)
                .fixConflictTimeout(RobotConst.UN_SET_TIMEOUT)
                .ignoreConflictFail(false);

        // 启动导航任务
        mNavigationTask = mMapService.navigationToLift(liftMsg, extraParams);
        mNavigationTask.progress(new ProgressCallback<MapProgress>() {
            @Override
            public void onProgress(MapProgress mapProgress) {


                String msg = mapProgress.getProgress();

                if (MapProgress.PROGRESS_OBSTACLE.equals(msg)) {
                    Log.d(TAG, "导航暂停，遇到障碍物");
                    if (callback != null) callback.onObstacleDetected();
                } else if (MapProgress.PROGRESS_DOWN_CHARGE_ING.equals(msg)) {
                    Log.d(TAG, "机器人正在从充电桩下移，准备导航");
                } else if (MapProgress.PROGRESS_OBSTACLE_CLEAR.equals(msg)) {
                    Log.d(TAG, "障碍物清除，继续导航");
                } else if (MapProgress.PROGRESS_DOWN_CHARGE_SUCCESS.equals(msg)) {
                    Log.d(TAG, "充电桩下移成功，导航开始");
                } else {
                    Log.d(TAG, "导航进度更新: " + msg + "，状态码: " +   mapProgress.getResponseCode());
                }
            }
        });


        // 导航成功的回调
        mNavigationTask.done( new DoneCallback() {
            public void onDone(Object result) {
                Log.d(TAG, "成功到达目的地");
                if (callback != null) callback.onSuccess();
            }
        });

        // 导航失败的回调
        mNavigationTask.fail(new FailCallback() {


            @Override
            public void onFail(Object o) {

            }

//            @Override
//            public void onFail(CodeException e) {
//                if (e != null) {
//                    Log.e(TAG, "导航失败，错误码: " + e.getCode() + ", 错误信息: " + e.getMessage());
//                    if (callback != null) callback.onFailure(e.getCode(), e.getMessage());
//                } else {
//                    Log.e(TAG, "导航失败，错误信息为空");
//                    if (callback != null) callback.onFailure(-1, "Unknown error");
//                }
//            }
        });

        // 启动导航任务
        mNavigationTask.start();
    }

    public interface NavigationCallback {
        void onSuccess(); // 导航成功回调
        void onFailure(int errorCode, String errorMessage); // 导航失败回调
        void onObstacleDetected(); // 遇到障碍物时的回调
    }
}
