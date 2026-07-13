package com.kmp.hook.elite

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.kmp.hook.AbOverride
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Self-contained A/B test for the push-permission prompt. Drop this one file into any app and
 * wire it up with just three calls — nothing in the Activity is required:
 *
 *  1. [onOfferStarted] — call once per launch that reaches your main content (offer).
 *  2. [PushPromptHost] — place once in your Compose tree; it caches an internal trigger.
 *  3. [onPageFinished] — call when the content is shown (e.g. WebView `onPageFinished`); it fires
 *     the cached trigger, which drives both variants itself.
 *
 * Variant (sticky 50/50, drawn once and cached):
 *  - [VARIANT_SYSTEM] ("0") — control: on page-finish, just launch the default system
 *    POST_NOTIFICATIONS dialog, nothing else.
 *  - [VARIANT_CUSTOM] ("1") — variant: on page-finish, show a custom "subscribe & get a bonus"
 *    pop-up, and only launch the system dialog when the user taps OK.
 *
 * Cadence (an "offer start" = a launch that reaches the content):
 *  - The request is made on the FIRST content page-finish.
 *  - If the user cancels / dismisses (or the system grant is denied) we back off and ask again
 *    every [SHOW_EVERY] offer starts.
 *  - Once notifications are granted we never ask again.
 */
object PushDialogAb {

    const val VARIANT_SYSTEM = "0"
    const val VARIANT_CUSTOM = "1"

    /** After a cancel/dismiss/deny, ask again this many offer starts later. */
    private const val SHOW_EVERY = 3

    /** First prompt is due immediately (on the first offer page-finish). */
    private const val FIRST_SHOW_AT = 0

    private const val KEY_PREFS = "ljh79yhb"
    private const val KEY_AB_VARIANT = "pdlg_ab_variant"
    private const val KEY_OFFER_COUNT = "pdlg_offer_count"
    private const val KEY_NEXT_SHOW_AT = "pdlg_next_show_at"
    private const val KEY_PROMPT_SHOWN = "pdlg_prompt_shown"

    /** Sticky flag: the user tapped OK in the custom pop-up at least once — never show it again. */
    private const val KEY_CUSTOM_OK_TAPPED = "pdlg_custom_ok_tapped"

    private fun prefs(context: Context) =
        context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------
    // A/B variant
    // ---------------------------------------------------------------------

    private val variantMutex = Mutex()

    suspend fun getABVariant(context: Context): String {
        if (AbOverride.forceDisabled) return VARIANT_SYSTEM
        val prefs = prefs(context)
        prefs.getString(KEY_AB_VARIANT, null)?.let { return it }
        return variantMutex.withLock {
            prefs.getString(KEY_AB_VARIANT, null) ?: run {
                val chosen = listOf(VARIANT_SYSTEM, VARIANT_CUSTOM).random()
                prefs.edit(commit = true) { putString(KEY_AB_VARIANT, chosen) }
                chosen
            }
        }
    }

    /**
     * Non-suspending read of the already-assigned bucket, for synchronous call sites (analytics
     * tagging). Safe because the value is immutable once [getABVariant] has set it; falls back to
     * [VARIANT_SYSTEM] if read before any assignment (never writes).
     */
    private fun peekVariant(context: Context): String {
        if (AbOverride.forceDisabled) return VARIANT_SYSTEM
        return prefs(context).getString(KEY_AB_VARIANT, VARIANT_SYSTEM) ?: VARIANT_SYSTEM
    }

    suspend fun isCustomVariant(context: Context): Boolean = getABVariant(context) == VARIANT_CUSTOM


    fun onOfferStarted(context: Context) {
        if (permissionGranted(context)) return
        val prefs = prefs(context)
        val count = prefs.getInt(KEY_OFFER_COUNT, 0) + 1
        prefs.edit(commit = true) { putInt(KEY_OFFER_COUNT, count) }
    }

    /**
     * True when a push request is due (either variant): notifications aren't granted yet, we're
     * on API 33+, and we're on the first offer page-finish or past the current back-off window.
     */
    private fun shouldRequest(context: Context): Boolean {
        if (permissionGranted(context)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val prefs = prefs(context)
        val count = prefs.getInt(KEY_OFFER_COUNT, 0)
        val nextShowAt = prefs.getInt(KEY_NEXT_SHOW_AT, FIRST_SHOW_AT)
        return count >= nextShowAt
    }

    /** Increments the presented-prompt counter that backs `attempt_number` in analytics. */
    private fun countPromptShown(context: Context) {
        val prefs = prefs(context)
        val shown = prefs.getInt(KEY_PROMPT_SHOWN, 0) + 1
        prefs.edit(commit = true) { putInt(KEY_PROMPT_SHOWN, shown) }
    }

    /** True once the user has tapped OK in the custom pop-up — we then never show it again. */
    private fun customOkTapped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CUSTOM_OK_TAPPED, false)

    /** Remember that the user tapped OK in the custom pop-up (sticky; suppresses it forever). */
    private fun markCustomOkTapped(context: Context) {
        prefs(context).edit(commit = true) { putBoolean(KEY_CUSTOM_OK_TAPPED, true) }
    }

    /** Back off: ask again after another [SHOW_EVERY] offer starts (cancel / dismiss / deny). */
    private fun scheduleNextPrompt(context: Context) {
        val prefs = prefs(context)
        val count = prefs.getInt(KEY_OFFER_COUNT, 0)
        prefs.edit(commit = true) { putInt(KEY_NEXT_SHOW_AT, count + SHOW_EVERY) }
    }

    private fun permissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------------
    // BigQuery (Firebase Analytics) — every event carries the A/B test tag
    // ---------------------------------------------------------------------

    // Params (present on every event)
    private const val PARAM_TEST_TAG = "push_ab_variant"   // "0" = system, "1" = custom
    private const val PARAM_API_LEVEL = "api_level"         // Build.VERSION.SDK_INT
    private const val PARAM_ATTEMPT = "attempt_number"      // # of times a push prompt was actually presented
    // Extra params (only on the relevant events)
    private const val PARAM_CHOICE = "choice"               // custom pop-up: "ok" | "cancel"
    private const val PARAM_RESULT = "result"               // system dialog: "granted" | "denied"

    /** (1) A/B bucket assigned — the user reached the offer while bucketed. Exposure / denominator. */
    private const val EVENT_ASSIGNED = "push_ab_assigned"

    /** (2) API < 33: no runtime prompt exists, the user is auto-subscribed to notifications. */
    private const val EVENT_AUTO_SUBSCRIBED = "push_auto_subscribed"

    /** (3) Permission was already granted before we asked (earlier launch / system settings). */
    private const val EVENT_ALREADY_GRANTED = "push_permission_already_granted"

    /** (4) Custom variant only: which button the user tapped in the bonus pop-up ([PARAM_CHOICE]). */
    private const val EVENT_CUSTOM_CHOICE = "push_custom_dialog_choice"

    /** (5) What the user answered in the system POST_NOTIFICATIONS dialog ([PARAM_RESULT]). */
    private const val EVENT_SYSTEM_RESULT = "push_system_dialog_result"

    // Values
    private const val CHOICE_OK = "ok"
    private const val CHOICE_CANCEL = "cancel"
    private const val RESULT_GRANTED = "granted"
    private const val RESULT_DENIED = "denied"

    private const val KEY_LOGGED_ASSIGNED = "pdlg_logged_assigned"
    private const val KEY_LOGGED_AUTO = "pdlg_logged_auto"
    private const val KEY_LOGGED_ALREADY_GRANTED = "pdlg_logged_already_granted"

    /**
     * Logs [event] to Firebase Analytics (streamed to BigQuery), tagged with the A/B variant,
     * API level, and the current offer-open count (`attempt_number`). Any [extras] are added as
     * string params (e.g. the custom-dialog `choice` or the system-dialog `result`).
     */
    private fun logTagged(context: Context, event: String, vararg extras: Pair<String, String>) {
        val attempt = prefs(context).getInt(KEY_PROMPT_SHOWN, 0).toLong()
        Firebase.analytics.logEvent(event) {
            param(PARAM_TEST_TAG, peekVariant(context))
            param(PARAM_API_LEVEL, Build.VERSION.SDK_INT.toLong())
            param(PARAM_ATTEMPT, attempt)
            extras.forEach { (key, value) -> param(key, value) }
        }
    }

    /** Logs [event] at most once per install (guarded by [flagKey]). */
    private fun logTaggedOnce(context: Context, flagKey: String, event: String) {
        val prefs = prefs(context)
        if (prefs.getBoolean(flagKey, false)) return
        prefs.edit(commit = true) { putBoolean(flagKey, true) }
        logTagged(context, event)
    }

    // ---------------------------------------------------------------------
    // UI
    // ---------------------------------------------------------------------

    /** Trigger cached by [PushPromptHost] while it's in the tree; invoked by [onPageFinished]. */
    @Volatile
    private var pageFinishedCallback: (() -> Unit)? = null

    /**
     * Call from your WebView's `onPageFinished` (main thread). Fires the prompt trigger cached by
     * [PushPromptHost] — a no-op if that host composable isn't currently in the tree. This is the
     * whole integration surface for the WebView layer: it never has to hold the Compose lambda.
     */
    fun onPageFinished() {
        pageFinishedCallback?.invoke()
    }

    /**
     * Place once in your Compose tree. While it's composed, it registers an internal page-finished
     * trigger in [PushDialogAb]; drive it by calling [PushDialogAb.onPageFinished] from your
     * WebView. When a request is due it runs the right variant: control launches the system dialog
     * directly; custom shows the bonus pop-up (rendered here). The host keeps no push state of its own.
     *
     *     PushDialogAb.PushPromptHost()
     *     ...
     *     onPageFinished = { PushDialogAb.onPageFinished() }
     */
    @Composable
    fun PushPromptHost() {
        val context = LocalContext.current
        // Guards against re-firing within one launch (page-finished can fire on every redirect).
        var handled by remember { mutableStateOf(false) }
        var showCustom by remember { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // (5) What the user answered in the system dialog.
            if (granted) {
                logTagged(context, EVENT_SYSTEM_RESULT, PARAM_RESULT to RESULT_GRANTED)
                // Granted -> permissionGranted() short-circuits from now on (never ask again).
            } else {
                logTagged(context, EVENT_SYSTEM_RESULT, PARAM_RESULT to RESULT_DENIED)
                scheduleNextPrompt(context) // back off; ask again after SHOW_EVERY more offer starts
            }
        }

        if (showCustom) {
            BonusDialog(
                onConfirm = {
                    showCustom = false
                    // Sticky: OK was tapped once -> never show the custom pop-up again, even if the
                    // system dialog is later denied (Android then stops showing it, but the custom
                    // pop-up must not keep re-appearing on top of a dead system prompt).
                    markCustomOkTapped(context)
                    // (4) User chose OK in the custom pop-up.
                    logTagged(context, EVENT_CUSTOM_CHOICE, PARAM_CHOICE to CHOICE_OK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onDismiss = {
                    showCustom = false
                    // (4) User chose Cancel / dismissed the custom pop-up.
                    logTagged(context, EVENT_CUSTOM_CHOICE, PARAM_CHOICE to CHOICE_CANCEL)
                    scheduleNextPrompt(context)
                },
            )
        }

        val scope = rememberCoroutineScope()
        val trigger = remember(launcher, scope) {
            {
                if (!handled) {
                    handled = true
                    scope.launch {
                        // Assign the bucket once (suspends only on the first install) BEFORE any
                        // logging, so every event below is tagged with the correct variant.
                        val variant = getABVariant(context)

                        // (1) Exposure: the A/B bucket the user landed in (once per install).
                        logTaggedOnce(context, KEY_LOGGED_ASSIGNED, EVENT_ASSIGNED)

                        onOfferStarted(context)

                        when {
                            // (2) API < 33: no runtime prompt; notifications are auto-subscribed.
                            // FCM registration (PushRegistrar) still happens in the flow as before —
                            // here we just log that auto-subscription separately for BigQuery.
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                                logTaggedOnce(context, KEY_LOGGED_AUTO, EVENT_AUTO_SUBSCRIBED)

                            // (3) Already granted (earlier launch / system settings): record once.
                            permissionGranted(context) ->
                                logTaggedOnce(context, KEY_LOGGED_ALREADY_GRANTED, EVENT_ALREADY_GRANTED)

                            // Not granted and a request is due: run the assigned variant.
                            // countPromptShown() (drives attempt_number) fires only when a prompt is
                            // actually presented — once per cycle, inside each branch below.
                            shouldRequest(context) -> {
                                if (variant == VARIANT_CUSTOM) {
                                    // Custom variant: the system dialog is ONLY ever launched as a
                                    // follow-up to OK in the bonus pop-up. Once OK has been tapped we
                                    // never show the pop-up again — and therefore never the system
                                    // dialog again either (no custom pop-up => no system dialog).
                                    if (!customOkTapped(context)) {
                                        countPromptShown(context)
                                        showCustom = true
                                    }
                                } else {
                                    // Control variant: just the default system dialog.
                                    countPromptShown(context)
                                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cache the trigger in the object while this composable is alive, so the WebView layer can
        // fire it via onPageFinished() without holding the lambda itself. Cleared on dispose.
        DisposableEffect(trigger) {
            pageFinishedCallback = trigger
            onDispose { if (pageFinishedCallback === trigger) pageFinishedCallback = null }
        }
    }

    /**
     * The custom "subscribe & get a bonus" pop-up (a Dialog window, so it draws on top of the
     * offer WebView). [onConfirm] should launch the system dialog; [onDismiss] backs off.
     */
    @Composable
    private fun BonusDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        val texts = remember { bonusPromptTexts(Locale.getDefault().language) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(texts.title) },
            text = { Text(texts.body) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(texts.ok) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(texts.cancel) } },
        )
    }

    private data class BonusPromptTexts(
        val title: String,
        val body: String,
        val ok: String,
        val cancel: String,
    )

    /**
     * Localized copy for the "subscribe & get a bonus" priming pop-up, keyed on the device's
     * ISO 639-1 language code ([Locale.getDefault().language]). Falls back to English for any
     * unlisted language. Covers Tier-1/2 plus a broad set of Tier-3 markets (LATAM, SEA, South
     * Asia, MENA, Africa, Eastern Europe).
     */
    private fun bonusPromptTexts(langCode: String): BonusPromptTexts = when (langCode.lowercase()) {
        // --- Tier 1 / 2 ---
        "es" -> BonusPromptTexts(
            "🎁 ¡Consigue tu bono!", "Activa las notificaciones🔔 para no perderte ningún bono, drop u oferta por tiempo limitado 🔥", "OK", "Cancelar"
        )
        "pt" -> BonusPromptTexts(
            "🎁 Ganhe seu bônus!", "Ative as notificações🔔 para não perder nenhum bônus, drop ou oferta por tempo limitado 🔥", "OK", "Cancelar"
        )
        "fr" -> BonusPromptTexts(
            "🎁 Recevez votre bonus !", "Activez les notifications🔔 pour ne jamais manquer un bonus, un drop ou une offre à durée limitée 🔥", "OK", "Annuler"
        )
        "de" -> BonusPromptTexts(
            "🎁 Hol dir deinen Bonus!", "Aktiviere Benachrichtigungen🔔, um keinen Bonus, Drop oder kein zeitlich begrenztes Angebot zu verpassen 🔥", "OK", "Abbrechen"
        )
        "it" -> BonusPromptTexts(
            "🎁 Ottieni il tuo bonus!", "Attiva le notifiche🔔 per non perderti mai un bonus, un drop o un'offerta a tempo limitato 🔥", "OK", "Annulla"
        )
        "nl" -> BonusPromptTexts(
            "🎁 Ontvang je bonus!", "Zet meldingen aan🔔 zodat je nooit een bonus, drop of tijdelijke deal mist 🔥", "OK", "Annuleren"
        )
        "ko" -> BonusPromptTexts(
            "🎁 보너스를 받으세요!", "알림을 켜고🔔 보너스, 드롭, 한정 특가를 놓치지 마세요 🔥", "확인", "취소"
        )
        "ja" -> BonusPromptTexts(
            "🎁 ボーナスを獲得！", "通知をオンにして🔔 ボーナスやドロップ、期間限定オファーを見逃さないで 🔥", "OK", "キャンセル"
        )
        "zh" -> BonusPromptTexts(
            "🎁 领取你的奖励！", "开启通知🔔，绝不错过任何奖励、掉落或限时优惠 🔥", "确定", "取消"
        )

        // --- Eastern Europe ---
        "ru" -> BonusPromptTexts(
            "🎁 Получите бонус!", "Включите уведомления🔔, чтобы не пропустить бонус, дроп или предложение с ограниченным сроком 🔥", "OK", "Отмена"
        )
        "uk" -> BonusPromptTexts(
            "🎁 Отримайте бонус!", "Увімкніть сповіщення🔔, щоб не пропустити бонус, дроп чи пропозицію з обмеженим часом 🔥", "OK", "Скасувати"
        )
        "pl" -> BonusPromptTexts(
            "🎁 Odbierz swój bonus!", "Włącz powiadomienia🔔, aby nie przegapić bonusu, dropa ani oferty ograniczonej czasowo 🔥", "OK", "Anuluj"
        )
        "ro" -> BonusPromptTexts(
            "🎁 Primește-ți bonusul!", "Activează notificările🔔 ca să nu ratezi niciun bonus, drop sau ofertă pe timp limitat 🔥", "OK", "Anulează"
        )
        "hu" -> BonusPromptTexts(
            "🎁 Szerezd meg a bónuszod!", "Kapcsold be az értesítéseket🔔, hogy soha ne maradj le bónuszról, dropról vagy időszakos ajánlatról 🔥", "OK", "Mégse"
        )

        // --- MENA / Middle East ---
        "ar" -> BonusPromptTexts(
            "🎁 احصل على مكافأتك!", "فعّل الإشعارات🔔 حتى لا تفوّت أي مكافأة أو دروب أو عرض محدود المدة 🔥", "حسنًا", "إلغاء"
        )
        "fa" -> BonusPromptTexts(
            "🎁 جایزه‌ات را بگیر!", "اعلان‌ها را روشن کن🔔 تا هیچ جایزه، دراپ یا پیشنهاد زمان‌محدودی را از دست ندهی 🔥", "باشه", "لغو"
        )
        "tr" -> BonusPromptTexts(
            "🎁 Bonusunu al!", "Bildirimleri aç🔔 ki hiçbir bonusu, drop'u veya sınırlı süreli fırsatı kaçırma 🔥", "Tamam", "İptal"
        )
        "az" -> BonusPromptTexts(
            "🎁 Bonusunu al!", "Bildirişləri aktivləşdir🔔 ki, heç bir bonusu, dropu və ya məhdud müddətli təklifi qaçırmayasan 🔥", "OK", "Ləğv et"
        )
        "uz" -> BonusPromptTexts(
            "🎁 Bonusingizni oling!", "Bildirishnomalarni yoqing🔔, hech qachon bonus, drop yoki cheklangan muddatli taklifni o'tkazib yubormang 🔥", "OK", "Bekor qilish"
        )

        // --- South Asia ---
        "hi" -> BonusPromptTexts(
            "🎁 अपना बोनस पाएं!", "सूचनाएं चालू करें🔔 ताकि आप कोई भी बोनस, ड्रॉप या सीमित समय का ऑफर न चूकें 🔥", "ठीक है", "रद्द करें"
        )
        "bn" -> BonusPromptTexts(
            "🎁 আপনার বোনাস নিন!", "নোটিফিকেশন চালু করুন🔔 যাতে কোনো বোনাস, ড্রপ বা সীমিত সময়ের অফার মিস না করেন 🔥", "ঠিক আছে", "বাতিল"
        )
        "ur" -> BonusPromptTexts(
            "🎁 اپنا بونس حاصل کریں!", "نوٹیفیکیشنز آن کریں🔔 تاکہ آپ کوئی بونس، ڈراپ یا محدود وقت کی پیشکش نہ چھوڑیں 🔥", "ٹھیک ہے", "منسوخ کریں"
        )

        // --- Southeast Asia ---
        "id", "in" -> BonusPromptTexts( // Android reports Indonesian as legacy "in"
            "🎁 Dapatkan bonusmu!", "Aktifkan notifikasi🔔 agar tidak pernah melewatkan bonus, drop, atau penawaran waktu terbatas 🔥", "OK", "Batal"
        )
        "ms" -> BonusPromptTexts(
            "🎁 Dapatkan bonus anda!", "Hidupkan pemberitahuan🔔 supaya anda tidak terlepas bonus, drop, atau tawaran masa terhad 🔥", "OK", "Batal"
        )
        "vi" -> BonusPromptTexts(
            "🎁 Nhận tiền thưởng của bạn!", "Bật thông báo🔔 để không bỏ lỡ tiền thưởng, drop hay ưu đãi có thời hạn 🔥", "OK", "Hủy"
        )
        "th" -> BonusPromptTexts(
            "🎁 รับโบนัสของคุณ!", "เปิดการแจ้งเตือน🔔 เพื่อไม่พลาดโบนัส ดรอป หรือข้อเสนอเวลาจำกัด 🔥", "ตกลง", "ยกเลิก"
        )
        "tl", "fil" -> BonusPromptTexts(
            "🎁 Kunin ang iyong bonus!", "I-on ang mga notification🔔 para hindi ka makaligtaan ng bonus, drop, o limited-time deal 🔥", "OK", "Kanselahin"
        )

        // --- Africa ---
        "sw" -> BonusPromptTexts(
            "🎁 Pata bonasi yako!", "Washa arifa🔔 ili usikose bonasi, drop, au ofa ya muda mfupi 🔥", "Sawa", "Ghairi"
        )

        // --- Fallback ---
        else -> BonusPromptTexts(
            "\uD83C\uDF81 Get your bonus!", "Turn on notifications\uD83D\uDD14 so you never miss a bonus, drop, or limited-time deal \uD83D\uDD25", "OK", "Cancel"
        )
    }
}
