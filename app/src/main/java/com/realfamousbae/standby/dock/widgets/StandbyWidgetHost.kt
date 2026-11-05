package com.realfamousbae.standby.dock.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF

/**
 * Обёртка над [AppWidgetHost]: один экземпляр на процесс (живёт в AppGraph). Виджеты
 * выбираются в настройках, показываются в StandbyActivity.
 *
 * Привязка виджета к стороннему хосту требует согласия пользователя: если
 * [bindIfAllowed] вернул false — показать системный диалог [bindIntent].
 */
class StandbyWidgetHost(context: Context) {

    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    /** Виджеты для рабочего стола из всех установленных приложений, по имени. */
    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .sortedBy { label(it).lowercase() }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(appContext.packageManager).orEmpty()

    /** Имя приложения, которому принадлежит виджет. */
    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(widgetId: Int, provider: ComponentName): Boolean =
        manager.bindAppWidgetIdIfAllowed(widgetId, provider)

    /** Системный диалог «Разрешить приложению создавать виджеты и получать их данные?». */
    fun bindIntent(widgetId: Int, provider: ComponentName): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)

    /**
     * Открыть экран настройки виджета, если он есть. Через хост, а не startActivity: экран
     * настройки обычно не экспортирован и доступен только хосту. Результат придёт в
     * `onActivityResult` с [requestCode]. Возвращает false, если настраивать нечего.
     */
    fun startConfigureIfNeeded(activity: Activity, widgetId: Int, requestCode: Int): Boolean {
        val info = manager.getAppWidgetInfo(widgetId) ?: return false
        if (info.configure == null) return false
        host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode, null)
        return true
    }

    fun delete(widgetId: Int) = host.deleteAppWidgetId(widgetId)

    /** null — виджет удалён (например, удалили его приложение). */
    fun info(widgetId: Int?): AppWidgetProviderInfo? = widgetId?.let { manager.getAppWidgetInfo(it) }

    fun createView(context: Context, widgetId: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(widgetId) ?: return null
        return host.createView(context, widgetId, info)
    }

    /** Сообщить виджету реальный размер карточки, чтобы он выбрал подходящую раскладку. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
    }

    /** Удалить все выделенные хостом id, кроме [keep] (остатки отменённых выборов). */
    fun deleteAllExcept(keep: Set<Int>) {
        host.appWidgetIds.filter { it !in keep }.forEach(host::deleteAppWidgetId)
    }

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    companion object {
        private const val HOST_ID = 0x5717
    }
}
