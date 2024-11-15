package com.example.myapplication


import FftVisualizer
import SpectrumVisualizer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.airbnb.lottie.compose.*
import com.example.myapplication.datamodel.MapViewModel
import com.example.myapplication.datamodel.MessageViewModel
import com.example.myapplication.datamodel.SoundDetectionViewModel
import com.example.myapplication.datamodel.SoundDetectionViewModel.amplitudes
import com.example.myapplication.service.VoiceVolumeService
import com.example.myapplication.service.robotControl.NavigationService
import com.example.myapplication.service.robotControl.UmapService
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.linc.audiowaveform.AudioWaveform
import com.linc.audiowaveform.infiniteLinearGradient
import com.ubtechinc.umap.UMapManager
import com.ubtechinc.umap.data.Point
import  android.Manifest
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState


import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.ActivityCompat

import com.example.myapplication.service.robotControl.RobotCloudService
import kotlin.math.log

class MainActivity : ComponentActivity() {

    private var voiceService: VoiceVolumeService? = null
    private var isBound = false
    private var sessionId1: Int = 0
    private var sessionId2: Int = 0
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceVolumeService.VoiceBinder
            voiceService = binder.getService()
            isBound = true
            setComposeContent(0,0)

            // 观察 sessionId1 的变化（AudioRecord 的 sessionId）
            SoundDetectionViewModel.sessionId.observe(this@MainActivity) { newSessionId1 ->
                sessionId1 = newSessionId1
                setComposeContent(sessionId1, sessionId2) // 使用最新的 sessionId1 和当前的 sessionId2 重新渲染
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
            isBound = false
        }
    }
    var umapService:UmapService= UmapService()
    // 修改 setComposeContent 方法，使其接收 sessionId 参数
    private fun setComposeContent(sessionId: Int?,sessionId2: Int) {
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    voiceService?.let {
                        RobotVoiceInteractionScreen(
                            modifier = Modifier.padding(innerPadding),
                            context = this,
                            umapService = umapService,
                            voiceService = it,
                            sessionId = sessionId?:0,// 将 sessionId 传递给组件
                            sessionId2 = sessionId2
                        )
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // 解绑服务
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RobotCloudService.getInstance(this).checkAndRegisterRobot()
        // 绑定服务
        Intent(this, VoiceVolumeService::class.java).also { intent ->
            startService(intent)  // 启动服务
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        umapService.getMapList()
        umapService.getCurrentId()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 检查 RECORD_AUDIO 权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // 检查 READ_EXTERNAL_STORAGE 权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 检查 WRITE_EXTERNAL_STORAGE 权限
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }


        // 请求权限
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }
    }
}

@Composable
fun RobotVoiceInteractionScreen(
    modifier: Modifier = Modifier,
    context: Context,
    umapService: UmapService,
    voiceService: VoiceVolumeService,
    sessionId: Int,
    sessionId2: Int
) {
    val isSoundDetected by SoundDetectionViewModel.voiceDetected.observeAsState(false)
    var generatedText by remember { mutableStateOf("欢迎！我在等待声音检测。") }
    val amplitudes by SoundDetectionViewModel.amplitudes.observeAsState(listOf(1, 40))
    val chatHistory by MessageViewModel.chatHistory.observeAsState(listOf("欢迎！我在等待声音检测。"))
    val logHistory by MessageViewModel.logHistory.observeAsState(listOf("欢迎!"))
    val mapId by MapViewModel.currentMapId.observeAsState("未知地图")
    val sampleRate by SoundDetectionViewModel.sampleRate.observeAsState(0)
    val fftData by SoundDetectionViewModel.fftData.observeAsState(emptyList<Float>())

    LaunchedEffect(isSoundDetected) {
        generatedText = if (isSoundDetected) {
            "我检测到了声音！"
        } else {
            "等待中...请发出声音。"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("机器人语音交互") },
                actions = {
                    IconButton(onClick = { /* TODO: 进入设置页面 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        content = { paddingValues ->
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .fillMaxHeight()
                    .padding(paddingValues)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 左侧列：机器人动画和控制按钮
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight() // 添加此行
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RobotAnimationSection(
                        sessionId = sessionId,
                        generatedText = generatedText,

                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ControlButtonsSection(
                        context = context,
                        voiceService = voiceService
                    )
                    FftVisualizer(magnitudes = fftData, modifier = Modifier.fillMaxSize())
                }

                // 中间列：聊天记录和机器人说话内容
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    ChatHistorySection(
                        chatHistory = chatHistory,
                        modifier = Modifier
                            .weight(1.5f)
                            .fillMaxWidth()  // 确保填满宽度
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RobotSpeechContentSection(
                        logHistory=logHistory,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()  // 确保填满宽度
                    )
                }


                // 右侧列：机器人控制
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight() // 添加此行
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    RobotControlSection(
                        umapService = umapService,
                        mapId = mapId,
                        sampleRate = sampleRate
                    )
                }
            }
        }
    )
}

@Composable
fun RobotAnimationSection(sessionId: Int?, generatedText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val robotComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.robot))
        LottieAnimation(
            composition = robotComposition,
            iterations = LottieConstants.IterateForever,
            modifier = Modifier.size(100.dp).height(200.dp) // 缩小动画尺寸
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .height(180.dp)
                .fillMaxWidth()
        ) {
            sessionId?.let {
                SpectrumVisualizer(
                    sessionId = it,
                    modifier = Modifier.fillMaxSize()// Use the full size of the placeholder Box
                    )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

//        TextBox(
//            text = generatedText,
//            modifier = Modifier.heightIn(max = 60.dp) // 限制高度
//        )
    }
}

@Composable
fun ControlButtonsSection(context: Context, voiceService: VoiceVolumeService) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ServiceControlButton(context = context, voiceService = voiceService)
        Spacer(modifier = Modifier.height(8.dp))
        VoiceRecordingButton(context = context, voiceService = voiceService)
    }
}

@Composable
fun ChatHistorySection(chatHistory: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = "日志",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                .padding(4.dp)
                .fillMaxHeight()
        ) {
            val listState = rememberLazyListState()

            // Auto-scroll to the bottom when chat history changes
            LaunchedEffect(chatHistory.size) {
                listState.animateScrollToItem(chatHistory.size - 1)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(chatHistory) { message ->
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}



@Composable
fun RobotSpeechContentSection(logHistory: List<String>,modifier: Modifier = Modifier) {
    Column(
        modifier = modifier // 应用传入的 modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "机器人说话内容",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(4.dp))
        RobotSpeechLogBox(
           logHistory
        )
    }
}


@Composable
fun RobotSpeechLogBox(logHistory: List<String>) {
    val listState = rememberLazyListState()

    // Auto-scroll to the bottom when log history changes
    LaunchedEffect(logHistory.size) {
        listState.animateScrollToItem(logHistory.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .padding(4.dp)
            .fillMaxHeight()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(logHistory) { message ->
                Text(
                    text = message,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}


@Composable
fun RobotControlSection(
    umapService: UmapService,
    mapId: String,
    sampleRate: Int
) {
    Text(
        text = "机器人控制",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start)
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "地图ID: $mapId",
        fontSize = 14.sp,
        color = Color.Gray,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start)
    )
    Text(
        text = "当前采样率: $sampleRate",
        fontSize = 14.sp,
        color = Color.Gray,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.Start)
    )
    Spacer(modifier = Modifier.height(4.dp))
    MapPointsDisplay(umapService = umapService)
}

@Composable
fun MapPointsDisplay(umapService: UmapService) {
    val mapPoints by MapViewModel.points.observeAsState(listOf())
    LaunchedEffect(Unit) {
        umapService.getMapList()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
            .verticalScroll(rememberScrollState())
    ) {

        if (mapPoints.isEmpty()) {
            OutlinedButton(
                onClick = {
                    RobotCloudService.getInstance().registerPoints(null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(
                    text = "注册点位",
                    fontSize = 14.sp
                )
            }
        } else {

            mapPoints.forEach { point ->
                if (point is Point) {
                    OutlinedButton(
                        onClick = {
                            if (point.name == "定位点") {
                                MessageViewModel.addChatMessage("进行定位！")
                                umapService.initializeLocation(
                                    point.position.x.toFloat(),
                                    point.position.y.toFloat(),
                                    point.attitude.yaw.toFloat(),
                                    point
                                )
                            } else if (point.name.contains("充电桩")) {
                                umapService.navigateToCharge(point)
                            } else {
                                navigateToMapPoint(point, umapService = umapService)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            text = point.name,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { System.exit(0) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentPadding = PaddingValues(4.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("返回", fontSize = 14.sp)
        }
    }
}

@Composable
fun ServiceControlButton(context: Context, voiceService: VoiceVolumeService) {
    val isServiceRunning = remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (isServiceRunning.value) {
                SoundDetectionViewModel.updateFftData(emptyList())
                voiceService.pauseRecording()
            } else {
                voiceService.resumeRecording()
            }
            isServiceRunning.value = !isServiceRunning.value
        },
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isServiceRunning.value) Color.Red else MaterialTheme.colors.primary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
    ) {
        Icon(
            imageVector = if (isServiceRunning.value) Icons.Default.Close else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isServiceRunning.value) "停止聊天" else "开始聊天",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
fun VoiceRecordingButton(context: Context,voiceService: VoiceVolumeService) {
    val isRecording = remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth() // Make the width match the upper button
            .padding(horizontal = 8.dp)
            .height(40.dp) // Set the same height
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // 按下开始录音
                        voiceService.holdRecording()
                        isRecording.value = true

                        // 等待手势释放
                        tryAwaitRelease()

                        // 手指抬起后停止录音并发送
                        voiceService.releaseRecord()
                        isRecording.value = false
                    }
                )
            }
            .background(
                color = if (isRecording.value) Color.Red else Color(0xFF6200EA),
                shape = RoundedCornerShape(2.dp)
            ) // Changes to red when recording
    ) {
        Text(
            text = if (isRecording.value) "Recording..." else "Hold to Record",
            color = Color.White,
        )
    }


}

@Composable
fun TextBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}

// 导航到地图点的函数
fun navigateToMapPoint(
    point: Point,
    umapService: UmapService
) {
    umapService.navigate(point)
}
// 地图点的数据类
data class MapPoint(val id: String, val name: String)
