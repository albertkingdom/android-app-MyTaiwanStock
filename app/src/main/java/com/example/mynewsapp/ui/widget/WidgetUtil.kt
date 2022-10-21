package com.example.mynewsapp.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.example.mynewsapp.model.WidgetStockData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

class WidgetUtil {

    companion object {
        fun updateWidget(listOfWidgetStockData: List<WidgetStockData>, context: Context) {
            // Send broadcast to widgetProvider to invoke onRecieve method
            val updateIntent = Intent(context, StockAppWidgetProvider::class.java)
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            context.sendBroadcast(updateIntent)


            // Write stock price info to shared preference
            val sharedPref = context.getSharedPreferences("sharedPref", Context.MODE_PRIVATE)
                ?: return
            val moshi = Moshi.Builder()
                .build()
            val jsonAdapter: JsonAdapter<List<WidgetStockData>> = moshi.adapter(
                Types.newParameterizedType(
                    List::class.java,
                    WidgetStockData::class.java
                )
            )

            val jsonString = jsonAdapter.toJson(listOfWidgetStockData)
            with(sharedPref.edit()) {
                putString("sharedPref1", jsonString)
                apply()
            }
        }
    }
}