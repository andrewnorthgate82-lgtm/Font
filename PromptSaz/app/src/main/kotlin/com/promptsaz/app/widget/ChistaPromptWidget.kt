package com.promptsaz.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.promptsaz.app.MainActivity
import com.promptsaz.app.R

/**
 * ویجت «پرامپت جدید» چیستا: a home-screen pill that opens the app straight
 * on a FRESH prompt screen — one tap from idea to prompt.
 */
class ChistaPromptWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val openPrompt = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(MainActivity.EXTRA_OPEN_SECTION, MainActivity.SECTION_PROMPT_NEW)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            openPrompt,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_new_prompt).apply {
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            manager.updateAppWidget(id, views)
        }
    }

    private companion object {
        const val REQUEST_CODE = 4001
    }
}
