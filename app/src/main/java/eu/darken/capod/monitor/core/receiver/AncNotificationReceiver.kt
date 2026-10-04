package eu.darken.capod.monitor.core.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.common.upgrade.isPro
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.aap.protocol.AapCommand
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the listening mode buttons of the monitor notification, which also work on the lock screen. */
@AndroidEntryPoint
class AncNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var aapManager: AapConnectionManager
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject @AppScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { name -> AapSetting.AncMode.Value.entries.firstOrNull { it.name == name } }
            ?: return

        val pending = goAsync()
        appScope.launch {
            try {
                if (!upgradeRepo.isPro()) {
                    log(TAG, WARN) { "Ignoring $mode, not pro" }
                    return@launch
                }
                aapManager.sendCommand(address, AapCommand.SetAncMode(mode))
                log(TAG) { "Sent SetAncMode($mode)" }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Sending $mode failed: ${e.asLog()}" }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "AncNotificationReceiver")
        private const val ACTION = "eu.darken.capod.monitor.SET_ANC_MODE"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_MODE = "mode"
        private const val REQUEST_CODE_BASE = 100

        fun pendingIntent(context: Context, address: BluetoothAddress, mode: AapSetting.AncMode.Value): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + mode.ordinal,
                Intent(context, AncNotificationReceiver::class.java).apply {
                    action = ACTION
                    putExtra(EXTRA_ADDRESS, address)
                    putExtra(EXTRA_MODE, mode.name)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentCompat.FLAG_IMMUTABLE,
            )
    }
}
