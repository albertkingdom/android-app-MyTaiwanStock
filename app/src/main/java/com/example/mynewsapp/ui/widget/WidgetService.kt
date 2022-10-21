package com.example.mynewsapp.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.mynewsapp.R
import com.example.mynewsapp.model.WidgetStockData
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types


// For Collection View of widget
class WidgetService: RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        println("onGetViewFactory")
        return WidgetItemFactory(applicationContext, intent)
    }

    class WidgetItemFactory(val context: Context, val intent: Intent): RemoteViewsFactory {

        var appWidgetId: Int = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        lateinit var dataList: List<WidgetStockData>
        override fun onCreate() {
            //println("WidgetItemFactory onCreate")

            if (intent.getSerializableExtra("updatedPrice")!= null) {

                dataList = intent.getSerializableExtra("updatedPrice") as List<WidgetStockData>
                //println("WidgetItemFactory onCreate from intent $dataList")
            }else {
                //println("WidgetItemFactory onCreate default")
                dataList = listOf(WidgetStockData(stockNo = "0050", stockPrice = "130", stockName = "台50", yesterDayPrice = "100"))
            }
        }

        override fun onDataSetChanged() {
            //Log.d("WidgetItemFactory", "onDataSetChanged")
            // Get price info from shared preference and update dataList
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<List<WidgetStockData>> = moshi.adapter(Types.newParameterizedType(List::class.java, WidgetStockData::class.java))

            val sharedPreferences = context.getSharedPreferences("sharedPref", Context.MODE_PRIVATE)
            val stringFromPref = sharedPreferences.getString("sharedPref1", "default")
            val stringDecode = jsonAdapter.fromJson(stringFromPref)
            //Log.d("WidgetItemFactory", "stringDecode..$stringDecode")
            if (stringDecode != null) {
                dataList = stringDecode
            }



        }

        override fun onDestroy() {

        }

        override fun getCount(): Int {
           return dataList.size
        }

        // configure remoteview with data
        override fun getViewAt(position: Int): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_listview_item)
            val diffPrice = calculateDiff(dataList[position])

            remoteViews.setTextViewText(R.id.widget_item_stock_number, dataList[position].stockNo)
            remoteViews.setTextViewText(R.id.widget_item_stock_price, handlePrice(dataList[position]))
            remoteViews.setTextViewText(R.id.widget_item_stock_name, dataList[position].stockName)
            remoteViews.setTextViewText(R.id.widget_item_stock_diff, calculateDiff(dataList[position]))
            if (diffPrice != "-" && diffPrice.toFloat() > 0) {
                remoteViews.setTextColor(R.id.widget_item_stock_diff, Color.RED)
            }
            if (diffPrice != "-" && diffPrice.toFloat() < 0) {
                remoteViews.setTextColor(R.id.widget_item_stock_diff, Color.GREEN)
            }
            val intent = Intent().apply {
                putExtra("stockNo", dataList[position].stockNo)
            }
            remoteViews.setOnClickFillInIntent(R.id.widget_item, intent)

            return remoteViews
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
           return position.toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        private fun calculateDiff(stock: WidgetStockData): String {
            if (stock.stockPrice != "-") {
                return String.format("%.2f", (stock.stockPrice.toFloat() - stock.yesterDayPrice.toFloat()))
            }
            return "-"
        }
        private fun handlePrice(stock: WidgetStockData): String {
            if (stock.stockPrice != "-") {
                return String.format("%.2f", stock.stockPrice.toFloat())
            }
            return "-"
        }

    }
}