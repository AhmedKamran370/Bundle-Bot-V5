package com.bundlebot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

class A11y : AccessibilityService() {
    private var currentTask: String = ""
    private var hasTypedPin = false
    
    override fun onServiceConnected() { super.onServiceConnected(); isRunning = true }
    override fun onDestroy() { super.onDestroy(); isRunning = false }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "pk.com.telenor.phoenix") return

        val task = Store.pendingTask
        if (task.isNotEmpty()) {
            currentTask = task
            executeTask()
        }
    }

    private fun executeTask() {
        val parts = currentTask.split(",")
        if (parts.size < 5) return
        val network = parts[0]
        val gb = parts[1]
        val days = parts[2]
        val pkgName = parts[3]
        val number = parts[4]

        val root = rootInActiveWindow ?: return
        
        // 1. Handle MPIN Login Screen
        if (containsText(root, "Enter MPIN") || containsText(root, "Login") || containsText(root, "Password")) {
            val pinField = findEditText(root, "")
            if (pinField != null && !hasTypedPin) {
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, Store.easypaisaPin)
                pinField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                hasTypedPin = true
                clickText(root, "Login") 
                clickText(root, "Confirm")
                return
            }
        } else {
            hasTypedPin = false // Reset once logged in
        }

        // 2. Check for Loan Warning
        if (containsText(root, "Loan") || containsText(root, "Advance")) {
            triggerError("Loan detected!")
            return
        }

        // 3. Click "Loads & Packages"
        if (clickText(root, "Loads & Packages") || clickText(root, "Load & Packages")) return

        // 4. Enter Number
        val numberField = findEditText(root, "Enter Number")
        if (numberField != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, number)
            numberField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return
        }

        // 5. Click Next
        if (clickText(root, "Next")) return

        // 6. Filter by Duration
        if (clickText(root, "Duration")) {
            val durText = when(days) {
                "1" -> "Daily"
                "7" -> "Weekly"
                "30" -> "Monthly"
                else -> "Monthly"
            }
            clickText(root, durText)
            return
        }

        // 7. Buy Now
        if (clickText(root, pkgName)) return 
        if (clickText(root, "Buy Now")) return

        // 8. Success
        if (containsText(root, "Transaction Successful") || containsText(root, "successful")) {
            Store.addMessage("A11y: Transaction Successful!")
            Store.pendingTask = ""
            currentTask = ""
            val p = Package(pkgName, network, "", gb.toIntOrNull() ?: 0, days.toIntOrNull() ?: 0, 0)
            Records.add(number, p, "Easypaisa", 0)
            return
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        if (node.className == "android.widget.EditText" && (node.text?.contains(hint, true) == true || node.hintText?.contains(hint, true) == true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findEditText(child, hint)
                if (found != null) return found
            }
        }
        return null
    }

    private fun clickText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.toString()?.contains(text, true) == true) {
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && clickText(child, text)) return true
        }
        return false
    }

    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.toString()?.contains(text, true) == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && containsText(child, text)) return true
        }
        return false
    }

    private fun triggerError(msg: String) {
        Store.addMessage("ALARM: $msg Please do manually.")
        Store.alarmActive = true
        Alarm.start()
        Store.pendingTask = ""
        currentTask = ""
    }

    companion object { var isRunning = false }
}