package com.bundlebot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        Alarm.init(this)
        showSplash()
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            gravity = android.view.Gravity.CENTER
        }
        val logo = TextView(this).apply {
            text = "⬇"
            setTextColor(Color.parseColor("#22C55E"))
            textSize = 90f
            alpha = 0f
            scaleX = 0.4f; scaleY = 0.4f
        }
        val name = TextView(this).apply {
            text = "BundleBot"
            setTextColor(Color.WHITE)
            textSize = 32f
            alpha = 0f
        }
        root.addView(logo); root.addView(name)
        setContentView(root)
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        name.animate().alpha(1f).setStartDelay(500).setDuration(500)
            .withEndAction { showApp() }.start()
    }

    private fun showApp() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 20)
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        header.addView(TextView(this).apply {
            text = "BundleBot"; setTextColor(Color.WHITE); textSize = 22f
        })
        status = TextView(this).apply { setTextColor(Color.parseColor("#94A3B8")); textSize = 12f }
        header.addView(status)
        root.addView(header)

        val tabsScroll = HorizontalScrollView(this).apply { setBackgroundColor(Color.parseColor("#1E293B")) }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (p in listOf("Home","Messages","Packages","Records","Proofs","Banks","Setup","Help")) {
            tabs.addView(TextView(this).apply {
                text = p; setTextColor(Color.WHITE); textSize = 14f
                setPadding(30, 25, 30, 25)
                setOnClickListener { show(p) }
            })
        }
        tabsScroll.addView(tabs); root.addView(tabsScroll)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh(); show("Home")
    }

    private fun refresh() {
        val notif = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        val a11y = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(packageName)
        status.text = "WhatsApp ${if (notif) "ON" else "OFF"}  •  Screen ${if (a11y) "ON" else "OFF"}"
    }
    override fun onResume() { super.onResume(); if (::content.isInitialized) refresh() }

    private fun show(p: String) {
        content.removeAllViews()
        val v: View = when (p) {
            "Home" -> Pages.home(this)
            "Messages" -> Pages.messages(this)
            "Packages" -> Pages.packages(this)
            "Records" -> Pages.records(this)
            "Proofs" -> Pages.proofs(this)
            "Banks" -> Pages.banks(this)
            "Setup" -> Pages.setup(this)
            else -> Pages.help(this)
        }
        content.addView(v)
    }

    fun rebuild() { refresh(); show("Home") }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42 || resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        
        if (Store.pickRecording) {
            Store.setScreenRecordingDone()
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
            Store.pickRecording = false
            return
        }

        val name = uri.lastPathSegment?.lowercase() ?: ""
        
        // Reject PDFs and Images so they don't become garbage text
        if (name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            Toast.makeText(this, "Please upload a TXT, CSV, or DOCX file. The app cannot read PDFs/Images directly.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val pkgs: List<Package> = when {
                name.endsWith(".docx") -> PackageParser.fromDocx(input)
                else -> PackageParser.fromText(input.bufferedReader().readText())
            }
            
            if (pkgs.isEmpty()) {
                Toast.makeText(this, "Could not read any packages from this file.", Toast.LENGTH_LONG).show()
            } else {
                for (p in pkgs) Store.upsertPackage(p, null)
                Toast.makeText(this, "Imported ${pkgs.size} packages", Toast.LENGTH_LONG).show()
                rebuild()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}