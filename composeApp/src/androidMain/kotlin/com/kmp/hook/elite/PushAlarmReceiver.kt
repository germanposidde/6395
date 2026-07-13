package com.kmp.hook.elite

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kmp.hook.R


const val INTENT_PUSH_KEY = "from_push"

fun isPushClicked(intent: Intent, context: Context) {
    val fromPush = intent.getBooleanExtra("from_push", false)

    if (fromPush){
        val variant = PushAlarmScheduler.isPushFeatureEnabled(context)
        Log.d("PushABTest", "clicked on push")
        AbTracker.log(PushAlarmScheduler.AB_TEST_TAG, variant, "push_clicked")
    }
}
class PushAlarmReceiver : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onReceive(context: Context, intent: Intent) {

        val variant = PushAlarmScheduler.isPushFeatureEnabled(context)

        val notificationId = intent.getIntExtra(
            PushAlarmScheduler.EXTRA_NOTIFICATION_ID,
            0
        )

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(INTENT_PUSH_KEY, true)
            }
            ?: return

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        AbTracker.log(PushAlarmScheduler.AB_TEST_TAG, variant, "push_received", "received" to true)

        PushNotifier.show(
            context = context,
            notificationId = notificationId,
            contentIntent = contentIntent
        )
    }
}

@Composable
fun LifecyclePushEffect(
    isNotStub: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        PushAlarmScheduler.providePushTimeout(context)
    }

    DisposableEffect(lifecycleOwner, isNotStub) {
        val variant = PushAlarmScheduler.isPushFeatureEnabled(context)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    PushAlarmScheduler.cancelAll(context)
                }

                Lifecycle.Event.ON_STOP -> {
                    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true
                    if (granted && isNotStub) {
                        AbTracker.log(
                            PushAlarmScheduler.AB_TEST_TAG,
                            variant,
                            "is_push_permission_granted",
                            "granted" to true
                        )
                        runCatching { PushAlarmScheduler.scheduleAll(context) }
                            .onFailure { Log.d("PushABTest", "Schedule failed", it) }
                            .onSuccess { Log.d("PushABTest", "Schedule succeeded") }
                    } else {
                        AbTracker.log(
                            PushAlarmScheduler.AB_TEST_TAG,
                            variant,
                            "is_push_permission_granted",
                            "granted" to false
                        )
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

object PushAlarmScheduler {
    const val EXTRA_NOTIFICATION_ID = "native_push_notification_id"

    private const val PUSHES_TYPE = "native_pushes_ab_test"
    private const val PUSHES_KEY = "dgaljrn948hg349"

    const val AB_TEST_TAG = "push_after_leave"

    private data class PushSchedule(val delayMs: Long, val requestCode: Int)

    fun isPushFeatureEnabled(context: Context): String {
        val prefs = context
            .applicationContext
            .getSharedPreferences(PUSHES_TYPE, Context.MODE_PRIVATE)

        if (prefs.contains(PUSHES_KEY)) {
            return prefs.getString(PUSHES_KEY, "false") ?: "false"
        } else {
            val randomValue = listOf("false", "10", "600").random()
            prefs.edit { putString(PUSHES_KEY, randomValue) }

            AbTracker.logOnce(PushAlarmScheduler.AB_TEST_TAG, randomValue, "push_variant")

            return randomValue
        }
    }

    private fun resolveSchedule(context: Context): PushSchedule? =
        when (isPushFeatureEnabled(context)) {
            "10"  -> PushSchedule(10_000L, 1001)
            "600" -> PushSchedule(600_000L,  1002)
            else  -> null
        }

    suspend fun providePushTimeout(context: Context): Boolean {
        val schedule = resolveSchedule(context)
        Log.d(
            "PushABTest",
            schedule?.let { "(${it.delayMs}, ${it.requestCode})" } ?: "null"
        )
        return schedule != null
    }

    fun scheduleAll(context: Context) {
        val schedule = resolveSchedule(context)
        if (schedule == null) {
            Log.w("PushABTest", "scheduleAll skipped: variant disabled")
            return
        }
        val alarmManager = context.getSystemService<AlarmManager>() ?: run {
            Log.w("PushABTest", "scheduleAll skipped: AlarmManager unavailable")
            return
        }

        val pi = buildPendingIntent(context, schedule.requestCode)
        val variant = isPushFeatureEnabled(context)
        val now = System.currentTimeMillis()

        AbTracker.log(
            PushAlarmScheduler.AB_TEST_TAG,
            variant,
            "scheduling_push",
            "delayInMs" to schedule.delayMs
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + schedule.delayMs,
            pi
        )
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val schedule = resolveSchedule(context)
        if (schedule != null) {
            alarmManager.cancel(buildPendingIntent(context, schedule.requestCode))
        }
    }

    private fun buildPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, PushAlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}

object PushNotifier {
    const val CHANNEL_ID = "native_push_channel"

    private data class NotificationContent(val title: String, val text: String)

    private val top20Locales = mapOf(
        "fr" to "\uD83D\uDCB8 Revenez vite pour réclamer votre bonus!",
        "ar" to "\uD83D\uDCB8 Վերադարձեք և ստացեք ձեր բոնուսը!",
        "es" to "\uD83D\uDCB8 ¡Vuelve y reclama tu bonificación!",
        "en" to "\uD83D\uDCB8 Come back and claim your bonus!",
        "pt" to "\uD83D\uDCB8 Volta e reclama o teu bónus!",
        "ru" to "\uD83D\uDCB8 Вернитесь и получите свой бонус!",
        "uz" to "\uD83D\uDCB8 Qaytib kelib, bonusingizni oling!",
        "tr" to "\uD83D\uDCB8 Geri dönün ve bonusunuzu alın!",
        "ko" to "\uD83D\uDCB8 다시 방문하셔서 보너스를 받아가세요!",
        "pl" to "\uD83D\uDCB8 Wróć i odbierz swój bonus!",
        "ro" to "\uD83D\uDCB8 Revino și revendică-ți bonusul!",
        "ms" to "\uD83D\uDCB8 Kembalilah dan tuntut bonus anda!",
        "it" to "\uD83D\uDCB8 Torna a ritirare il tuo bonus!",
        "de" to "\uD83D\uDCB8 Komm wieder vorbei und hol dir deinen Bonus!",
        "az" to "\uD83D\uDCB8 Qayıdın və bonusunuzu tələb edin!",
        "uk" to "\uD83D\uDCB8 Поверніться та отримайте свій бонус!",
        "id" to "\uD83D\uDCB8 Kembali dan klaim bonusmu!",
        "hu" to "\uD83D\uDCB8 Gyere vissza, és vedd át a bónuszodat!",
        "mn" to "\uD83D\uDCB8 Буцаж ирээд бонусаа аваарай!",
        "zh" to "\uD83D\uDCB8 快回来领取您的奖励吧！"
    )

    private fun resolveContent(context: Context): NotificationContent {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()

        val text = top20Locales[Locale.current.language] ?: top20Locales.getValue("en")
        return NotificationContent(title = appName, text = text)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "native_pushes",
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun show(context: Context, notificationId: Int, contentIntent: PendingIntent?) {
        val appLocaleConfig = resolveContent(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // set icon
            .setSmallIcon(R.drawable.win)
            .setContentTitle(appLocaleConfig.title)
            .setContentText(appLocaleConfig.text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
