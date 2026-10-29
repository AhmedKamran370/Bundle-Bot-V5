package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream

object PackageParser {
    fun fromText(text: String, forcedNet: String): List<Package> {
        val out = mutableListOf<Package>()
        var currentSec = "Other"
        var currentDays = 30 

        for (rawLine in text.lines()) {
            var l = rawLine.trim()
            if (l.isEmpty()) continue

            if (l.contains("Package", true) && !l.any { it.isDigit() } && !l.contains("SR.NO", true)) {
                currentSec = l.replace("|", "").trim()
                currentDays = when {
                    currentSec.contains("Weekly", true) -> 7
                    currentSec.contains("Daily", true) -> 1
                    currentSec.contains("Monthly", true) -> 30
                    currentSec.contains("Fortnightly", true) -> 15
                    else -> 30
                }
                continue
            }

            var gb = 0
            var price = 0
            var name = ""

            if (l.contains("|")) {
                val cells = l.split("|").map { it.trim() }
                for (i in cells.indices) {
                    val cell = cells[i]
                    val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(cell)
                    if (gbMatch != null && gb == 0) {
                        gb = gbMatch.groupValues[1].toIntOrNull() ?: 0
                        if (cell.replace(gbMatch.value, "").replace(Regex("[^a-zA-Z]"), "").trim().isEmpty() && i > 0) {
                            name = cells[i-1]
                        } else {
                            name = cell.substringBefore(gbMatch.value).trim()
                        }
                    }
                    if (price == 0 && (cell.contains("SAR", true) || cell.contains("Rs", true) || cell.contains("PKR", true))) {
                        val numOnly = cell.replace(Regex("[^0-9]"), "") 
                        if (numOnly.isNotEmpty()) {
                            var rawPrice = numOnly.toIntOrNull() ?: 0
                            if (cell.contains("SAR", true)) rawPrice = (rawPrice * 74.0).toInt()
                            price = rawPrice
                        }
                    }
                }
            } else {
                l = l.replace("*", "").replace("\\", "").replace(Regex("\\s+"), " ")
                val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(l)
                if (gbMatch != null) {
                    gb = gbMatch.groupValues[1].toIntOrNull() ?: 0
                    name = l.substringBefore(gbMatch.value)
                }
                val priceMatch = Regex("""(?:SAR|Rs\.?|PKR)\s*(\d+)""", RegexOption.IGNORE_CASE).find(l)
                if (priceMatch != null) {
                    var rawPrice = priceMatch.groupValues[1].toIntOrNull() ?: 0
                    if (priceMatch.value.contains("SAR", true)) rawPrice = (rawPrice * 74.0).toInt()
                    price = rawPrice
                }
            }

            name = name.replace(Regex("^\\d+\\.?\\s*-?\\s*"), "").replace("*", "").trim()
            if (name.length > 40 || name.contains("On-mint") || name.contains("Off-mint")) {
                name = if (gb > 0) "$gb GB Package" else "Data Package"
            }
            if (name.isEmpty() && gb > 0) name = "$gb GB Package"

            if (gb > 0 && price > 0) {
                out.add(Package(name, forcedNet, currentSec, gb, currentDays, price))
            }
        }
        return out
    }

    fun fromDocx(input: InputStream, forcedNet: String): List<Package> {
        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlText = zip.bufferedReader().readText()
                        val cleanText = xmlText
                            .replace("</w:tc>", "|") 
                            .replace("</w:tr>", "\n") 
                            .replace(Regex("<[^>]*>"), " ") 
                            .replace("&amp;", "&")
                            .replace(Regex("[ \t]+"), " ") 
                            .replace(Regex("\n+"), "\n") 

                        return fromText(cleanText, forcedNet)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { }
        return emptyList()
    }
}