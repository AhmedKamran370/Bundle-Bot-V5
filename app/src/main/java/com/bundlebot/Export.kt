package com.bundlebot

import android.app.Activity
import android.widget.Toast
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

        try {
            // Saves to a folder you can access via your phone's File Manager
            val dir = a.getExternalFilesDir(null)
            val file = File(dir, "BundleBot_Report_${currency}.csv")
            file.writeText(sb.toString())
            Toast.makeText(a, "Saved to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(a, "Error saving CSV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}