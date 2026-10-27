package com.bundlebot

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WaListener : NotificationListenerService() {
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
        
        Store.addRaw("$group: $text")
        if (group.isBlank() || text.isBlank()) return
        
        val g = Store.groupFor(group) ?: run {
            Store.addGroup(group); Store.groupFor(group)
        } ?: return
        if (!g.watched) return

        val parsed = Parser.parseWithAI(text)
        Store.addMessage("IN: $group — $text")
        
        if (parsed.number == null) {
            reply(n, "Please send the mobile number you want to recharge.")
            Store.addMessage("OUT: [asks for number]")
            return
        }
        
        val match = Store.matchPackage(parsed.network, parsed.gb, parsed.days, parsed.name, parsed.price)
        
        if (match.pkg == null) {
            reply(n, "That bundle is not in our list. Please tell us the GB or name again.")
            Store.addMessage("OUT: [asks which package]")
            return
        }

        if (match.needsConfirmation) {
            reply(n, match.suggestion)
            Store.addMessage("OUT: [asks for confirmation on ${match.pkg.name}]")
            // Trigger alarm for user to check
            Store.alarmActive = true
            Alarm.start()
        } else {
            reply(n, "Request received: ${parsed.number} • ${match.pkg.name} — processing now.")
            Store.addMessage("OUT: [confirms ${match.pkg.name}]")
            
            if (!A11y.isRunning) {
                Store.alarmActive = true
                Alarm.start()
                Store.addMessage("ALARM: Screen Control is OFF. Turn on Accessibility for BundleBot.")
            } else {
                val task = "${parsed.network},${match.pkg.gb},${match.pkg.days},${match.pkg.name},${parsed.number}"
                Store.pendingTask = task
                // Start Easypaisa
                val intent = packageManager.getLaunchIntentForPackage("pk.com.telenor.phoenix")
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    private fun reply(n: Notification, msg: String) {
        val actions = n.actions ?: return
        for (a in actions) {
            val inputs = a.remoteInputs ?: continue
            val results = Bundle()
            for (inp in inputs) results.putCharSequence(inp.resultKey, msg)
            val intent = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            try { a.actionIntent.send(this, 0, intent) } catch (_: Exception) {}
            return
        }
    }
}