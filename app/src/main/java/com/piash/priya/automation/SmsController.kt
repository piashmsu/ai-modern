package com.piash.priya.automation

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.piash.priya.util.AuditLog

/**
 * Send and read SMS messages on behalf of the user.
 *
 * Send goes through the platform [SmsManager] which automatically segments
 * long messages. Read is paged via the `content://sms` URIs — newest first.
 * Every send is recorded in [AuditLog] so the user has a permanent record
 * of what Priya did on their behalf.
 */
class SmsController(private val context: Context) {

    fun canSend(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED

    fun canRead(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    fun send(phoneNumber: String, message: String): Result<Unit> = runCatching {
        if (!canSend()) error("SEND_SMS permission missing")
        val mgr = SmsManager.getDefault()
        val parts = mgr.divideMessage(message)
        if (parts.size <= 1) {
            mgr.sendTextMessage(phoneNumber, null, message, null, null)
        } else {
            mgr.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
        }
        AuditLog.record(
            AuditLog.Kind.SMS_SENT,
            "SMS to $phoneNumber",
            detail = message.take(160),
        )
    }

    fun readInbox(limit: Int = 20): List<SmsRecord> = runCatching {
        if (!canRead()) return emptyList()
        val cr: ContentResolver = context.contentResolver
        val cursor = cr.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date", "read"),
            null, null,
            "date DESC LIMIT $limit"
        ) ?: return emptyList()
        val out = mutableListOf<SmsRecord>()
        cursor.use { c ->
            while (c.moveToNext()) {
                out += SmsRecord(
                    id = c.getLong(0),
                    address = c.getString(1).orEmpty(),
                    body = c.getString(2).orEmpty(),
                    timestamp = c.getLong(3),
                    read = c.getInt(4) == 1,
                )
            }
        }
        out
    }.getOrDefault(emptyList())
}

data class SmsRecord(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val read: Boolean,
)
