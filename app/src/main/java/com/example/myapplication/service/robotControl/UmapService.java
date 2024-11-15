package com.example.myapplication.service.robotControl;

import android.util.Log;

import com.example.myapplication.datamodel.MapViewModel;
import com.example.myapplication.datamodel.MessageViewModel;
import com.ubtechinc.coreservice.robotcontrol.data.NavTaskType;
import com.ubtechinc.coreservice.robotcontrol.data.RobotConst;
import com.ubtechinc.coreservice.robotcontrol.message.ExtraParams;
import com.ubtechinc.coreservice.robotcontrol.message.map.DockMsg;
import com.ubtechinc.coreservice.robotcontrol.message.map.LiftPose;
import com.ubtechinc.coreservice.robotcontrol.message.map.Location;
import com.ubtechinc.coreservice.robotcontrol.message.map.MapProgress;
import com.ubtechinc.coreservice.robotcontrol.message.map.RequestNavLiftMsg;
import com.ubtechinc.coreservice.robotcontrol.message.map.response.Pose;
import com.ubtechinc.coreservice.robotcontrolsdk.conflict.ConflictStrategy;
import com.ubtechinc.coreservice.robotcontrolsdk.service.map.MapService;
import com.ubtechinc.delivery.common.exception.CodeException;
import com.ubtechinc.umap.UMapManager;
import com.ubtechinc.umap.callback.IUMapCallback;
import com.ubtechinc.umap.data.LocalMapData;
import com.ubtechinc.umap.data.MapElementType;
import com.ubtechinc.umap.data.Point;
import com.ubtechinc.umap.request.BaseRequest;
import com.ubtechinc.umap.request.ReadRequest;
import com.ubtechinc.umap.request.RelocationRequest;
import com.ubtechinc.umap.response.BaseResponse;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UmapService {
    private static volatile Point currentPosition;
    private static volatile String mMapName;
    public void getMapList() {
        UMapManager.getInstance().getReader().getLocalMapList(new IUMapCallback<BaseResponse<BaseRequest, List<LocalMapData>>>() {
            @Override
            public void onStart() {
                // 请求开始时的处理逻辑
                Log.d("MapList", "开始获取本地地图列表...");
            }

            @Override
            public void onSuccess(BaseResponse<BaseRequest, List<LocalMapData>> response) {
                if (response != null && response.isSuccess()) {
                    List<LocalMapData> mapList = response.getData();
                    // 成功获取地图列表后的处理逻辑
                    for (LocalMapData mapData : mapList) {
                        Log.d("MapList", "地图名称: " + mapData.getMapName());
                        MapViewModel.INSTANCE.updateMapId(mapData.getMapId() + "---" + mapData.getMapName());
                        UMapManager.getInstance().getEditor().useMap(mapData.getMapId(), new IUMapCallback<BaseResponse>() {
                            @Override
                            public void onStart() {

                            }

                            @Override
                            public void onSuccess(BaseResponse response) {
                                getPointsByType(mapData.getMapId());

                            }

                            @Override
                            public void onFail(CodeException exception) {

                            }
                        });
                        // 其他处理逻辑，如将地图列表展示在 UI 上
                    }
                } else {
                    Log.e("MapList", "获取地图列表失败，未知错误");
                }
            }

            @Override
            public void onFail(CodeException e) {
                // 获取地图列表失败时的处理逻辑
                if (e != null) {
                    Log.e("MapList", "获取地图列表失败，错误码: " + e.getCode() + ", 错误信息: " + e.getMessage());
                }
            }
        });
    }

    public void getPointsByType(String mapId) {
        // 定义要查询的业务点类型
        String[] pointTypes = new String[]{
                MapElementType.POINT_DESTINATION,   // 目的点
                MapElementType.POINT_CHARGE,        // 充电桩
                MapElementType.POINT_ANCHOR_POINT,  // 定位点
                MapElementType.POINT_DINING,        // 出餐点
                MapElementType.POINT_RECYCLE,       // 回收点
                MapElementType.POINT_WELCOME        // 迎宾点
        };

        // 调用 UMapManager 的 getPointsByType 方法
        UMapManager.getInstance().getReader().getPointsByType(mapId, pointTypes, new IUMapCallback<BaseResponse<ReadRequest, List<Point>>>() {
            @Override
            public void onStart() {
                // 请求开始的处理逻辑

            }

            @Override
            public void onSuccess(BaseResponse<ReadRequest, List<Point>> response) {
                // 请求成功时的处理逻辑
                if (response != null && response.isSuccess()) {
                    List<Point> points = response.getData();
                    MapViewModel.INSTANCE.updatePoints(points);
                } else {

                }
            }

            @Override
            public void onFail(CodeException e) {

            }
        });
    }

    public void getCurrentId() {
//        MapViewModel.INSTANCE.updateMapId("1234");

        ExtraParams extraParams = new ExtraParams()
                .conflictStategy(ConflictStrategy.FIX) // 冲突处理策略，可以根据需要调整
                .fixConflictTimeout(RobotConst.UN_SET_TIMEOUT)
                .ignoreConflictFail(false);
        MapService.Companion.get().getMap(extraParams)
                .timeout(5000)
                .done(result -> {
                    String currentMapId = result;
                    mMapName = result;
//                    getPointsByType(currentMapId);
                    MapViewModel.INSTANCE.updateMapId(currentMapId);
                    MessageViewModel.INSTANCE.addChatMessage("获取到地图id，正在获取地图的位置");
                    // 成功获取当前地图 ID，您可以在此处理 mapId
                })
                .fail(e -> {
                    // 获取失败，处理错误情况
                    int errorCode = e.getCode();
                    String errorMsg = e.getMessage();
                    MessageViewModel.INSTANCE.addChatMessage(errorMsg);
                })
                .start();
    }
    public void initializeLocation(float x, float y, float theta,Point point) {
        getCurrentId();
        currentPosition = point;
        MessageViewModel.INSTANCE.addChatMessage("定位点： "+currentPosition.getPosition().getX());
        try {
            UMapManager.getInstance().getEditor().relocation(x, y, theta, new IUMapCallback<BaseResponse<RelocationRequest, Location>>() {
                @Override
                public void onStart() {
                    // 可以在此添加定位开始的日志或 UI 提示
                    MessageViewModel.INSTANCE.addChatMessage("开始定位，请稍等");
                }

                @Override
                public void onSuccess(BaseResponse<RelocationRequest, Location> response) {
                    if (response != null && response.isSuccess()) {
                        // 定位成功，回调 onSuccess
//                        currentPosition = point;
                        MessageViewModel.INSTANCE.addChatMessage("定位成功，请点击导航到任意点位");
                    } else {
                        // 如果返回的 response 为空或者失败，回调 onFail
                        MessageViewModel.INSTANCE.addChatMessage("定位失败，请重试！");
                    }
                }

                @Override
                public void onFail(CodeException e) {
                    // 定位失败，回调 onFail，并传递错误码和错误信息
                    MessageViewModel.INSTANCE.addChatMessage("定位失败，请重试！"+e.getMessage());
                }
            });
        }catch (Exception e){
            MessageViewModel.INSTANCE.addChatMessage("定位失败，请重试！"+e.getMessage());
        }
    }
    public void navigate(Point destination) {
        MapService mMapService = (MapService) MapService.Companion.get();
        MessageViewModel.INSTANCE.addChatMessage("导航到" + destination.getPosition().getX());
        // 获取当前地图 ID
//        MessageViewModel.INSTANCE.addChatMessage("获取到地图, 地图id： "+ mapId);
        // 获取当前位置
        if(currentPosition==null){
            MessageViewModel.INSTANCE.addChatMessage("获取当前位置失败，无法导航");
            return;
        }
        MessageViewModel.INSTANCE.addChatMessage("开始导航, 当前地点 " + currentPosition.getPosition().getX() + "," +currentPosition.getPosition().getY() + "," + currentPosition.getAttitude().getYaw());
        // 设定目标点
        LiftPose targetPoint = new LiftPose(0, (float) destination.getPosition().getX(), (float) destination.getPosition().getY(), (float) destination.getAttitude().getYaw());
        // 配置导航任务
        RequestNavLiftMsg liftMsg = new RequestNavLiftMsg();
        liftMsg.setCurrentPose(new LiftPose(0, (float) currentPosition.getPosition().getX(),(float)  currentPosition.getPosition().getY(), (float) currentPosition.getAttitude().getYaw()));
        liftMsg.setDestPose(targetPoint);
        liftMsg.setTask_type(NavTaskType.NAVIGATION_DELIVER_4); // 根据任务类型设定导航类型

        ExtraParams extraParams = new ExtraParams()
                .conflictStategy(ConflictStrategy.FIX)
                .fixConflictTimeout(RobotConst.UN_SET_TIMEOUT)
                .ignoreConflictFail(false);

        // 执行导航
        mMapService.navigationToLift(liftMsg, extraParams)
                .progress(progress -> {
                    // 在此处理导航过程中的状态
                    int responseCode = progress.getResponseCode();
                    String msg = progress.getProgress();

                    if (MapProgress.PROGRESS_OBSTACLE.equals(msg)) {
                        // 遇到障碍物暂停导航
                    } else if (MapProgress.PROGRESS_DOWN_CHARGE_ING.equals(msg)) {
                        // 正在从充电桩下桩
                    } else if (MapProgress.PROGRESS_OBSTACLE_CLEAR.equals(msg)) {
                        // 障碍物清理完成
                    } else if (MapProgress.PROGRESS_DOWN_CHARGE_SUCCESS.equals(msg)) {
                        // 从充电桩成功下桩，开始导航
                    }
                })
                .done(o -> {
                    // 导航成功到达目标点后的处理
                })
                .fail(e -> {
                    // 导航失败的处理
                    MessageViewModel.INSTANCE.addChatMessage(e.getMessage());
                })
                .start();
//        mMapService.getLocation()
//                .timeout(10000)
//                .done(currentPoint -> {
//
//                })
//                .fail(e -> {
//                    // 处理当前位置获取失败的情况
//                    MessageViewModel.INSTANCE.addChatMessage(e.getMessage());
//                });
        mMapService.getMap(null)
                .done(mapId -> {

                })
                .fail(e -> {
                    // 获取当前地图 ID 失败的处理
                    MessageViewModel.INSTANCE.addChatMessage(e.getMessage());
                });
    }

    public void navigateToCharge(@NotNull Point point) {

        Pose pose = new Pose((float) point.getPosition().getX(),  (float) point.getPosition().getY(), (float) point.getAttitude().getYaw());
        DockMsg dockMsg = new DockMsg(DockMsg.Type.INSTANCE.getUP(), mMapName, NavTaskType.AUTO_GO_STOP_NORMAL_0, pose);
        MapService mMapService = (MapService) MapService.Companion.get();
        mMapService.autoGoStop(dockMsg)
                .progress(mapProgress -> {
                    // 回桩过程中，有需要处理的状态信息，在这个回调中处理

                    // 状态码，可根据此值来处理状态逻辑（详细定义见状态和错误码定义）
                    int responseCode = mapProgress.getResponseCode();

                    // 状态名，某些不同的状态码由于情况相似，可统一对应到一个状态名（也有一些状态码只对应一个状态名），
                    // 这时可以通过读取状态名统一处理一些共性情况
                    String msg = mapProgress.getProgress();

                    if (MapProgress.PROGRESS_TO_CHARGE.equals(mapProgress.getProgress())) {

                        // 开始回桩，先导航到充电桩

                    } else if (MapProgress.PROGRESS_TO_DOCK.equals(mapProgress.getProgress())) {

                        // 到达充电桩的位置，开始上桩

                    } else if (MapProgress.PROGRESS_OBSTACLE.equals(msg)) {

                        // 回桩过程中，遇到了需要手动清除的障碍而暂停回桩（例如遇到杂物在地上或者有物体挡路）

                    } else if (MapProgress.PROGRESS_OBSTACLE_CLEAR.equals(msg)) {

                        // 检测到需要手动清除的障碍已经清理了，将继续回桩

                    }
                })
                .done(o -> {

                    // 上桩成功，回充任务结束，在这个回调处理相关业务逻辑

                })
                .fail(e -> {

                    // 导航或者上桩过程出现异常而任务失败，这时可以根据异常里携带的错误码来处理相关逻辑（错误码详细定义见状态和错误码定义）
                    int code = e.getCode();


                })
                .start();
    }


    // 定义回调接口
    public interface PointsCallback {
        void onStart(); // 请求开始回调

        void onSuccess(List<Point> points); // 请求成功回调，返回点位列表

        void onFail(int errorCode, String errorMessage); // 请求失败回调，返回错误码和错误信息
    }

}
