package com.example.myapplication

import java.lang.Math.floor


class MKAverage(m: Int, k: Int) {
    var k: Int = 0
    var m: Int = 0
    var arrUsedToCal:Array<Int>;
    init {
        this.m = m
        this.k = k
        this.arrUsedToCal = Array(m){0};
    }
    val arr = arrayListOf<Int>()

    fun addElement(num: Int) {
        arr.add(num)
    }

    fun calculateMKAverage(): Int {
        if (arr.size < m){
            return -1
        }
        //从array中删除最大的k个数和最小的k个数
        val result =  arr.toIntArray().takeLast(3).sorted().drop(k).dropLast(k);
        return floor(result.average()).toInt();
    }

}
