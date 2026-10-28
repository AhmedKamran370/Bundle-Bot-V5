package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object PackageParser {
    fun fromText(text: String): List<Package> {
        val out = mutableListOf<Package>()
        var currentNet = ""
        var currentSec = ""
        for (line in text.lines()) {
            val l = line.trim()
            if (l.isEmpty()) continue
            
            // If it's a header like "Weekly:" or "Jazz:"
            if (l.endsWith(":") && !l.any { it.isDigit() }) {
                val word = l.dropLast(1).trim()
                if (word.equals("Weekly", true) || word.equals("Daily", true) ||
                    word.contains("Monthly", true) || word.contains("Call", true)) currentSec = word
                else currentNet = word
                continue
            }
            
            val gbMatch = Regex("""(\d+)\s*(gb|GB)""", RegexOption.IGNORE_CASE).find(l)
            val priceMatch = Regex("""(?:Rs\.?|PKR)?\s*(\d{2,5})""", RegexOption.IGNORE_CASE).find(l.replace(gbMatch?.value ?: ""))
            val dayMatch = Regex("""(\d+)\s*days?""", RegexOption.IGNORE_CASE).find(l)
            
            if (gbMatch == null && priceMatch == null) continue
            
            val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val days = dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val price = priceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            // FIXED: Split by Regex instead of substringBefore
            val nameRaw = l.split(Regex("""\d+\s*(gb|GB)""", RegexOption.IGNORE_CASE)).firstOrNull()?.trim() ?: ""
            val name = nameRaw.ifEmpty { "$gb GB" }
            
            val net = guessNetwork(l).ifBlank { currentNet }
            out.add(Package(name, net, currentSec.ifBlank { "Other" }, gb, days, price))
        }
        return out
    }

    private fun guessNetwork(line: String): String = when {
        line.contains("jazz", true) || line.contains("warid", true) -> "Jazz"
        line.contains("zong", true) -> "Zong"
        line.contains("telenor", true) -> "Telenor"
        line.contains("ufone", true) -> "Ufone"
        else -> ""
    }

    fun fromDocx(input: InputStream): List<Package> {
        val out = mutableListOf<Package>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(zip)
                    val rows = doc.getElementsByTagName("w:tr")
                    for (i in 0 until rows.length) {
                        val tr = rows.item(i)
                        val cells = tr.childNodes
                        val rowText = StringBuilder()
                        for (j in 0 until cells.length) {
                            val cn = cells.item(j)
                            if (cn.nodeName == "w:tc") {
                                val t = cn.textContent.trim()
                                if (t.isNotEmpty()) { rowText.append(t).append(" ") }
                            }
                        }
                        out.addAll(fromText(rowText.toString()))
                    }
                    return out
                }
                entry = zip.nextEntry
            }
        }
        return out
    }
}