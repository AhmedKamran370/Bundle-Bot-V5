package com.bundlebot

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.*

class WaListener : NotificationListenerService() {
    private var lastMessageHash = 0
    private var lastMessageTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Store.init(this)
        val p = sbn.packageName
        val allowed = when (Store.source()) {
            "wa" -> p == "com.whatsapp"
            "biz" -> p == "com.whatsapp.w4b"
            else -> p == "com.whatsapp" || p == "com.whatsapp.w4b"
        }
        if (!allowed) return
        
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        
        val e = n.extras ?: return
        val group = (e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: e.getCharSequence(Notification.EXTRA_TITLE))?.toString() ?: ""
        var text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        (e.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle)
            ?.getCharSequence("text")?.toString()?.let { text = it }
        
        if (group.isBlank() || text.isBlank()) return

        var g = Store.groupFor(group)
        if (g == null) {
            Store.addGroup(group)
            Store.addMessage("NEW GROUP DETECTED: '$group'. Go to Setup to enable it.")
            return 
        }

        if (!g.watched) return 

        // Voice Message
        if (text.contains("Audio (") || text.contains("Voice message") || text.contains("🎤")) {
            Alarm.start(this)
            Store.addMessage("⚠ VOICE MESSAGE RECEIVED in $group. Manual action required.")
            return
        }

        // Spam prevention
        val currentHash = text.hashCode()
        if (currentHash == lastMessageHash && System.currentTimeMillis() - lastMessageTime < 30000) {
            return
        }
        lastMessageHash = currentHash
        lastMessageTime = System.currentTimeMillis()

        Store.addRaw("$group: $text")
        val parsed = Parser.parseWithAI(text)
        Store.addMessage("IN: $group — $text")
        
        if (parsed.number == null) {
            reply(n, "Please send a valid mobile number.")
            return
        }
        
        val match = Store.matchPackage(parsed.network, parsed.gb, parsed.days, parsed.name, parsed.price)
        
        if (match.pkg == null) {
            val failKey = "${group}_${parsed.number}"
            if (System.currentTimeMillis() - Store.getLastFail(failKey) > 120000) {
                reply(n, "Sorry, that package is not in our list.")
                Store.setLastFail(failKey, System.currentTimeMillis())
            }
            return
        }

        // Pending Record
        val pending = PendingRec(
            UUID.randomUUID().toString(),
            parsed.number,
            match.pkg.name,
            match.pkg.price,
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
            System.currentTimeMillis()
        )
        Store.addPendingRecord(pending)

        // Custom Reply
        val customReplies = Store.getCustomReplies()
        var customReplySent = false
        
        for ((trigger, response) in customReplies) {
            if (text.contains(trigger, true)) {
                reply(n, response)
                customReplySent = true
                break
            }
        }

        // Default Reply
        if (!customReplySent) {
            val defaultReply = "${text}\n\nOk"
            reply(n, defaultReply)
        }
        
        Store.addMessage("OUT: Replied to ${parsed.number} (${match.pkg.name})")
    }

    private fun reply(n: Notification, msg: String): Boolean {
        val actions = n.actions ?: return false
        for (a in actions) {
            val inputs = a.remoteInputs ?: continue
            val results = Bundle()
            for (inp in inputs) results.putCharSequence(inp.resultKey, msg)
            val intent = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            try { 
                a.actionIntent.send(this, 0, intent) 
                return true 
            } catch (_: Exception) { return false }
        }
        return false 
    }
}