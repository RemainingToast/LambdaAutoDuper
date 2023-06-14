package com.lambda.utils

class Timer {
    private var time: Long = -1

    fun passed(ms: Long): Boolean {
        return System.currentTimeMillis() - time >= ms
    }

    fun reset() {
        time = System.currentTimeMillis()
    }
}
