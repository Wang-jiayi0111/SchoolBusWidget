package com.example.schoolbuswidget.util

import android.util.Log
import com.example.schoolbuswidget.BuildConfig

object AppLog {
    private const val TAG = "SchoolBusWidget"

    fun d(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.d(TAG, message, throwable) else Log.d(TAG, message)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}
