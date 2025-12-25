package ru.zagrebin.culinaryblog.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.google.gson.Gson

class ChecklistWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("checklist_widget_prefs", Context.MODE_PRIVATE)
        val gson = Gson()
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, ru.zagrebin.culinaryblog.R.layout.widget_checklist)
            val raw = prefs.getString("widget_$appWidgetId", null)
            if (raw != null) {
                try {
                    val data = gson.fromJson(raw, WidgetData::class.java)
                    views.setTextViewText(ru.zagrebin.culinaryblog.R.id.widgetChecklistTitle, data.title)
                    views.setTextViewText(ru.zagrebin.culinaryblog.R.id.widgetChecklistItems, data.preview)
                } catch (t: Exception) {
                    views.setTextViewText(ru.zagrebin.culinaryblog.R.id.widgetChecklistTitle, "Список")
                    views.setTextViewText(ru.zagrebin.culinaryblog.R.id.widgetChecklistItems, "Нет элементов")
                }
            } else {
                // No configured checklist for this widget id — show hint
                views.setTextViewText(ru.zagrebin.culinaryblog.R.id.widgetChecklistTitle, "Список покупок")
                views.setTextViewText(ru.zagrebin.culinaryblog.R.id.widgetChecklistItems, "Добавьте список в настройках виджета")
            }

            // Click opens the app (Profile) — try main activity
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                val pending = android.app.PendingIntent.getActivity(context, appWidgetId, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or getImmutableFlag())
                views.setOnClickPendingIntent(ru.zagrebin.culinaryblog.R.id.widgetChecklistTitle, pending)
                views.setOnClickPendingIntent(ru.zagrebin.culinaryblog.R.id.widgetChecklistItems, pending)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    data class WidgetData(val title: String, val preview: String)

    private fun getImmutableFlag(): Int {
        return try {
            android.app.PendingIntent::class.java.getField("FLAG_IMMUTABLE").getInt(null)
        } catch (t: Exception) {
            0
        }
    }
}
