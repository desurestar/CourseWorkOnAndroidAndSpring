package ru.zagrebin.culinaryblog.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.zagrebin.culinaryblog.R
import ru.zagrebin.culinaryblog.data.repository.ChecklistRepository
import ru.zagrebin.culinaryblog.data.local.entity.ChecklistEntity
import javax.inject.Inject

@AndroidEntryPoint
class ChecklistWidgetConfigureActivity : AppCompatActivity() {

    @Inject lateinit var checklistRepository: ChecklistRepository
    @Inject lateinit var gson: Gson

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) finish()

        // Simple UI: check for pending checklist id saved before pin request. If present, auto-bind.
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(container)

        val prefs = getSharedPreferences("checklist_widget_prefs", MODE_PRIVATE)
        val pendingId = prefs.getLong("pending_widget_checklist", -1L)
        if (pendingId > 0L) {
            lifecycleScope.launch {
                val checklist = checklistRepository.getById(pendingId)
                if (checklist != null) {
                    // bind immediately to the new widget id
                    saveWidgetSelection(checklist)
                    prefs.edit().remove("pending_widget_checklist").apply()
                    return@launch
                }
                // if not found, fall back to listing
                renderChecklistList(container)
            }
        } else {
            lifecycleScope.launch {
                renderChecklistList(container)
            }
        }
    }

    private suspend fun renderChecklistList(container: LinearLayout) {
        val data = checklistRepository.getAll().first()
        if (data.isEmpty()) {
            val tv = TextView(this@ChecklistWidgetConfigureActivity)
            tv.text = getString(R.string.profile_empty)
            container.addView(tv)
            return
        }
        data.forEach { checklist ->
            val tv = TextView(this@ChecklistWidgetConfigureActivity)
            tv.text = checklist.title
            tv.textSize = 18f
            tv.setPadding(20,20,20,20)
            tv.setOnClickListener {
                saveWidgetSelection(checklist)
            }
            container.addView(tv)
        }
    }

    private fun saveWidgetSelection(checklist: ChecklistEntity) {
        val prefs = getSharedPreferences("checklist_widget_prefs", MODE_PRIVATE)
        val items = runCatching {
            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, ru.zagrebin.culinaryblog.model.ChecklistItem::class.java).type
            gson.fromJson<List<ru.zagrebin.culinaryblog.model.ChecklistItem>>(checklist.itemsJson, type)
        }.getOrNull() ?: emptyList()
        val preview = items.take(3).joinToString(", ") { it.text }

        val data = ChecklistWidgetProvider.WidgetData(checklist.title, preview)
        prefs.edit().putString("widget_$appWidgetId", gson.toJson(data)).apply()

        val appWidgetManager = AppWidgetManager.getInstance(this)
        ChecklistWidgetProvider().onUpdate(this, appWidgetManager, intArrayOf(appWidgetId))

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}
