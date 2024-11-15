package com.example.myapplication.datamodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.random.Random

object SoundDetectionViewModel : ViewModel() {
    private val _voiceDetected = MutableLiveData<Boolean>()
    private val _sampleRate = MutableLiveData<Int>()
    private val _amplitudes = MutableLiveData<List<Int>>() // 用于存储振幅数据
    private val _sessionId = MutableLiveData<Int>() // 存储 sessionId
    private val _fftData = MutableLiveData<List<Float>>() // 用于存储 FFT 数据 (List<Float> 类型)
    private val lowAmplitudeData = List(100) {
        val randomValue = Random.nextFloat() * (0.0001f - 0.00005f) + 0.00005f  // 生成 0.0005 到 0.001 之间的随机数
        randomValue * if (it % 2 == 0) 1 else -1  // 随机正负微小波动
    }
    val voiceDetected: LiveData<Boolean> get() = _voiceDetected
    val sampleRate: LiveData<Int> get() = _sampleRate
    val amplitudes: LiveData<List<Int>> get() = _amplitudes // 公开的 LiveData 供观察振幅数据
    val sessionId: LiveData<Int> get() = _sessionId // 公开的 LiveData 供观察 sessionId
    val fftData: LiveData<List<Float>> get() = _fftData // 公开的 LiveData 供观察 FFT 数据 (List<Float>)

    fun updateVoiceDetected(isDetected: Boolean) {
        _voiceDetected.postValue(isDetected)
    }

    fun updateSampleRate(rate: Int) {
        _sampleRate.postValue(rate)
    }

    fun updateAmplitudes(newAmplitudes: List<Int>) {
        _amplitudes.postValue(newAmplitudes) // 更新振幅数据
    }

    fun updateSessionId(id: Int) {
        _sessionId.postValue(id) // 更新 sessionId
    }

    // 更新 FFT 数据，使用 List<Float> 类型来存储频谱数据
    fun updateFftData(data: List<Float>) {
        _fftData.postValue(data)
    }
}
