package com.example.myapplication.datamodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ubtechinc.umap.data.Point


data class Position(val x: Double, val y: Double, val theta: Double) // 定义位置数据类

object MapViewModel : ViewModel() {
    private val _currentMapId = MutableLiveData<String>()
    val currentMapId: LiveData<String> get() = _currentMapId

    private val _currentPosition = MutableLiveData<Position>()
    val currentPosition: LiveData<Position> get() = _currentPosition

    private val _points = MutableLiveData<List<Point>>() // 使用外部的 Point 类
    val points: LiveData<List<Point>> get() = _points

    fun updateMapId(mapId: String) {
        _currentMapId.postValue(mapId)
    }

    fun updatePosition(x: Double, y: Double, theta: Double) {
        _currentPosition.postValue(Position(x, y, theta))
    }

    fun updatePoints(pointsList: List<Point>) {
        _points.postValue(pointsList)
    }
}
