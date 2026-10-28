package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream

object PackageParser {
    fun fromText(text: String): List<Package> {
        // Detect if the file is binary (like a PDF or Image read as text)
        val printableChars = text.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,:-/()" }
        if (text.isNotEmpty() && printableChars < text.length * 0.8) {
            return emptyList() // Reject binary garbage
        }

        val out = mutableListOf<Package>()
        var currentNet = ""
        var currentSec = ""
        for (line in text.lines()) {
            val l = line.trim()
            if (l.isEmpty()) continue
            
            if (l.endsWith(":") && !l.any { it.isDigit() }) {
                val word = l.dropLast(1).trim()
                if (word.equals("Weekly", true) || word.equals("Daily", true) ||
                    word.contains("Monthly", true) || word.contains("Call", true)) currentSec = word
                else currentNet = word
                continue
            }
            
            val gbMatch = Regex("""(\d+)\s*(gb|GB)""", RegexOption.IGNORE_CASE).find(l)
            val priceMatch = Regex("""(?:Rs\.?|PKR)?\s*(\d{2,5})""", RegexOption.IGNORE_CASE).find(l.replace(gbMatch?.value ?: "", ""))
            val dayMatch = Regex("""(\d+)\s*days?""", RegexOption.IGNORE_CASE).find(l)
            
            if (gbMatch == null && priceMatch == null) continue
            
            val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val days = dayMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val price = priceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
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
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlText = zip.bufferedReader().readText()
                        
                        // Convert Word XML tables into readable text lines
                        val cleanText = xmlText
                            .replace("</w:p>", "\n")
                            .replace("</w:tr>", "\n")
                            .replace(Regex("<[^>]*>"), " ")
                            .replace("&amp;", "&")
                            .replace(Regex("\\s+"), " ")

                        return fromText(cleanText)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            // Ignore corrupted docx
        }
        return emptyList()
    }
}