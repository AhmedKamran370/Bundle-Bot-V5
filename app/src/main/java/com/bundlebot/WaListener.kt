package com.bundlebot

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

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

        // 30-second cooldown
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
            reply(n, "Please send a valid mobile number (e.g., 03123456789).")
            Store.addMessage("OUT: [asks for valid number]")
            return
        }
        
        val match = Store.matchPackage(parsed.network, parsed.gb, parsed.days, parsed.name, parsed.price)
        
        if (match.pkg == null) {
            reply(n, "Sorry, we couldn't find that exact bundle. Please tell us the GB amount.")
            Store.addMessage("OUT: [asks which package]")
            return
        }

        if (match.needsConfirmation) {
            Store.addMessage("⚠ ALARM: Guessed package '${match.pkg.name}'. Please do this manually.")
            Store.alarmActive = true
            Alarm.start()
        } else {
            val success = reply(n, "Received request for ${parsed.number} • ${match.pkg.name} (${match.pkg.gb}GB). Please wait...")
            
            if (success) Store.addMessage("OUT: Confirmed ${match.pkg.name}")
            else Store.addMessage("⚠ FAILED TO REPLY: WhatsApp didn't provide a reply button for this notification.")
            
            if (!A11y.isRunning) {
                Store.alarmActive = true
                Alarm.start()
                Store.addMessage("ALARM: Screen Control is OFF. Turn on Accessibility for BundleBot.")
            } else {
                val task = "${parsed.network},${match.pkg.gb},${match.pkg.days},${match.pkg.name},${parsed.number}"
                Store.pendingTask = task
                val intent = packageManager.getLaunchIntentForPackage("pk.com.telenor.phoenix")
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
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