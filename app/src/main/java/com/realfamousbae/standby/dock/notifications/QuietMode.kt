package com.realfamousbae.standby.dock.notifications

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.core.content.edit
import com.realfamousbae.standby.dock.diag.DiagLog
import com.realfamousbae.standby.dock.settings.SettingsActivity

/**
 * Собственный режим «Не беспокоить» (Android Modes) на время показа StandBy: прячет системные
 * всплывающие уведомления, чтобы поверх экрана были только наши баннеры.
 *
 * Скрыть всплывашку система позволяет только для уведомлений, перехваченных DND, поэтому
 * обычные уведомления в это время приходят без звука. Звонки, повторные звонки, будильники
 * и медиа разрешены. В шторке, на значках и в строке состояния всё остаётся, а слушатель
 * уведомлений по-прежнему получает их все.
 *
 * Нужен доступ к «Не беспокоить»; у приложения с включённым доступом к уведомлениям он
 * обычно есть автоматически. Любой отказ системы пишется в журнал и не ломает режим.
 */
class QuietMode(context: Context, private val log: DiagLog) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("quiet_mode", Context.MODE_PRIVATE)
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val conditionId: Uri = Uri.Builder()
        .scheme(Condition.SCHEME)
        .authority(appContext.packageName)
        .appendPath("standby")
        .build()

    /** Наш режим сейчас включён (по нашему последнему вызову). */
    @Volatile
    var active: Boolean = false
        private set

    /** «Не беспокоить» был включён пользователем до включения нашего режима. */
    @Volatile
    var userDndActive: Boolean = false
        private set

    fun hasAccess(): Boolean = manager.isNotificationPolicyAccessGranted

    /**
     * Включить или выключить режим. Выключение не создаёт правило, если его нет, и
     * выполняется всегда (в том числе после падения процесса, когда правило могло остаться).
     */
    fun setActive(value: Boolean) {
        runCatching {
            if (value) {
                if (!hasAccess()) {
                    log.log("Тихий режим: нет доступа к «Не беспокоить»")
                    return
                }
                userDndActive = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                val id = findRuleId() ?: manager.addAutomaticZenRule(newRule())
                manager.setAutomaticZenRuleState(id, condition(Condition.STATE_TRUE))
                active = true
                suppressPeekGlobally()
                log.log("Тихий режим: включён (${stateSummary(id)})")
            } else {
                active = false
                userDndActive = false
                if (!hasAccess()) return
                restoreGlobalPeek()
                val id = findRuleId() ?: return
                manager.setAutomaticZenRuleState(id, condition(Condition.STATE_FALSE))
                log.log("Тихий режим: выключен")
            }
        }.onFailure {
            log.log("Тихий режим: ошибка (${it.javaClass.simpleName})")
        }
    }

    /**
     * One UI со старым интерфейсом «Не беспокоить» берёт визуальные эффекты из общей
     * политики («Скрытие уведомлений»), а не из `ZenPolicy` правила. Поэтому на время режима
     * добавляем в общую политику только «скрывать всплывающие» и запоминаем это на диске,
     * чтобы вернуть даже после смерти процесса.
     */
    private fun suppressPeekGlobally() {
        val policy = manager.notificationPolicy
        if (policy.suppressedVisualEffects and PEEK != 0) return
        manager.notificationPolicy = policy.withVisualEffects(policy.suppressedVisualEffects or PEEK)
        prefs.edit(commit = true) { putBoolean(KEY_PEEK_ADDED, true) }
    }

    /** Снять «скрывать всплывающие», только если его добавили мы; остальное не трогаем. */
    private fun restoreGlobalPeek() {
        if (!prefs.getBoolean(KEY_PEEK_ADDED, false)) return
        val policy = manager.notificationPolicy
        if (policy.suppressedVisualEffects and PEEK != 0) {
            manager.notificationPolicy = policy.withVisualEffects(policy.suppressedVisualEffects and PEEK.inv())
        }
        prefs.edit(commit = true) { putBoolean(KEY_PEEK_ADDED, false) }
        log.log("Тихий режим: общая настройка всплывающих возвращена")
    }

    private fun NotificationManager.Policy.withVisualEffects(effects: Int) = NotificationManager.Policy(
        priorityCategories,
        priorityCallSenders,
        priorityMessageSenders,
        effects,
        priorityConversationSenders,
    )

    /** Для журнала: что система считает включённым после нашего вызова. */
    private fun stateSummary(id: String): String {
        val filter = manager.currentInterruptionFilter
        val ruleState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ", правило=${manager.getAutomaticZenRuleState(id)}"
        } else {
            ""
        }
        val peek = manager.notificationPolicy.suppressedVisualEffects and PEEK != 0
        return "фильтр=$filter$ruleState, общая peek=${if (peek) "скрыта" else "видна"}"
    }

    private fun findRuleId(): String? =
        manager.automaticZenRules.entries.firstOrNull { it.value.conditionId == conditionId }?.key

    private fun condition(state: Int) = Condition(conditionId, RULE_NAME, state)

    private fun newRule(): AutomaticZenRule {
        val policy = ZenPolicy.Builder()
            .disallowAllSounds()
            .allowAlarms(true)
            .allowMedia(true)
            .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
            .allowRepeatCallers(true)
            // Главное: без всплывающих уведомлений. Остальные эффекты оставляем, чтобы
            // уведомления не терялись из шторки.
            .showPeeking(false)
            .showFullScreenIntent(true)
            .showInNotificationList(true)
            .showBadges(true)
            .showStatusBarIcons(true)
            .showLights(true)
            .showInAmbientDisplay(true)
            .build()
        val settingsActivity = ComponentName(appContext, SettingsActivity::class.java)
        val filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
        // Тип и описание правила в Modes задаются только через Builder (Android 15+).
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            AutomaticZenRule.Builder(RULE_NAME, conditionId)
                .setType(AutomaticZenRule.TYPE_OTHER)
                .setTriggerDescription("Пока на экране режим StandBy")
                .setConfigurationActivity(settingsActivity)
                .setZenPolicy(policy)
                .setInterruptionFilter(filter)
                .setEnabled(true)
                .build()
        } else {
            AutomaticZenRule(RULE_NAME, null, settingsActivity, conditionId, policy, filter, true)
        }
    }

    private companion object {
        const val RULE_NAME = "StandBy"
        const val PEEK = NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
        const val KEY_PEEK_ADDED = "global_peek_added"
    }
}
