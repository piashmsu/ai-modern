package com.piash.priya.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Bridges incoming SMS into Priya so she can read or react to them.
 *
 * Currently logs to in-memory buffer. Future: forward to ChatEngine for
 * AI-summarized push notifications.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        msgs.forEach { sms ->
            recent += SmsRecord(
                id = System.currentTimeMillis(),
                address = sms.originatingAddress.orEmpty(),
                body = sms.messageBody.orEmpty(),
                timestamp = sms.timestampMillis,
                read = false,
            )
            if (recent.size > 50) recent.removeAt(0)
        }
    }

    companion object {
        val recent: MutableList<SmsRecord> = mutableListOf()
    }
}
