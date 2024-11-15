package com.example.myapplication.datamodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

object MessageViewModel : ViewModel() {
    // 使用 LiveData 存储 chatHistory 列表
    private val _chatHistory = MutableLiveData<List<String>>(listOf("欢迎！我在等待声音检测。"))
    private val _logHistory = MutableLiveData<List<String>>(listOf(""))
    val chatHistory: LiveData<List<String>> get() = _chatHistory
    val logHistory: LiveData<List<String>> get() = _logHistory
    // 添加消息到 chatHistory
    fun addChatMessage(message: String) {
        // 获取当前的历史记录列表，添加新消息后更新列表
        val updatedChatHistory = _chatHistory.value.orEmpty() + message
        _chatHistory.postValue(updatedChatHistory)
    }
    // 添加消息到 chatHistory
    fun addLogMessage(message: String) {
        // 获取当前的历史记录列表，添加新消息后更新列表
        val updatedChatHistory = _logHistory.value.orEmpty() + message
        _logHistory.postValue(updatedChatHistory)
    }
}
