package com.example.schoolbuswidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.schoolbuswidget.widget.SchoolBusAppWidgetProvider

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        runWidgetSelfCheck()
    }

    private fun runWidgetSelfCheck() {
        val selfCheckText = findViewById<TextView>(R.id.textSelfCheck)
        val manager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, SchoolBusAppWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(componentName)
        val providerCount = manager.installedProviders.count {
            it.provider == componentName
        }
        val registered = providerCount > 0

        if (registered) {
            val message = getString(R.string.main_self_check_ok, providerCount)
            selfCheckText.text = "$message\n当前已添加实例数：${widgetIds.size}"
            Log.i(TAG, "Widget self-check OK. providerCount=$providerCount, widgetIds=${widgetIds.size}")
        } else {
            val message = getString(R.string.main_self_check_fail)
            selfCheckText.text = message
            Log.e(TAG, "Widget self-check FAIL. providerCount=0")
        }
    }

    companion object {
        private const val TAG = "SchoolBusWidgetCheck"
    }
}
