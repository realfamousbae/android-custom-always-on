package com.realfamousbae.standby.dock.notifications

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenPolicy
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
                log.log("Тихий режим: включён")
            } else {
                active = false
                userDndActive = false
                if (!hasAccess()) return
                val id = findRuleId() ?: return
                manager.setAutomaticZenRuleState(id, condition(Condition.STATE_FALSE))
                log.log("Тихий режим: выключен")
            }
        }.onFailure {
            log.log("Тихий режим: ошибка (${it.javaClass.simpleName})")
        }
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
    }
}
