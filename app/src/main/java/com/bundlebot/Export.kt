package com.bundlebot

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Export {
    fun csv(a: Activity, recs: List<Rec>, currency: String) {
        val sb = StringBuilder("Date,Number,Package,Bank,Price ($currency)\n")
        val rate = when (currency) {
            "USD" -> 280.0; "AED" -> 76.0; "SAR" -> 74.0; "INR" -> 3.3; "GBP" -> 355.0; else -> 1.0
        }
        for (r in recs) {
            val p = if (currency == "PKR") r.price else (r.price / rate).toInt()
            sb.append("${r.time},${r.number},${r.packageName.replace(",", " ")},${r.bank},$p\n")
        }
        val file = File(a.getExternalFilesDir(null), "bundlebot_report.csv")
        file.writeText(sb.toString())
        try {
            val uri = FileProvider.getUriForFile(a, "com.bundlebot.provider", file)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            a.startActivity(Intent.createChooser(i, "Open report"))
        } catch (_: Exception) {
            // FileProvider not set up, silently fail for now
        }
    }
}