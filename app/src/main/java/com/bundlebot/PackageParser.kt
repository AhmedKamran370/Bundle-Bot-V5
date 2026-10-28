package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream

object PackageParser {
    fun fromText(text: String, forcedNet: String): List<Package> {
        val out = mutableListOf<Package>()
        var currentSec = "Other"

        for (line in text.lines()) {
            // Clean the line of table borders, asterisks, backslashes
            var l = line.replace("|", " ")
                .replace("+", " ")
                .replace("-", " ")
                .replace("*", "")
                .replace("\\", "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (l.isEmpty()) continue

            // Detect Section Headers (e.g., "Monthly All Data Packages")
            if (l.contains("Package", true) && !l.any { it.isDigit() } && !l.contains("SR.NO", true)) {
                currentSec = l
                continue
            }

            // Extract GB
            val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(l)
            val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // Extract Price
            var price = 0
            // 1. Look for explicit currency like SAR 46, Rs 100, PKR 200
            val explicitPrice = Regex("""(?:SAR|Rs\.?|PKR)\s*(\d+)""", RegexOption.IGNORE_CASE).find(l)
            
            if (explicitPrice != null) {
                price = explicitPrice.groupValues[1].toIntOrNull() ?: 0
            } else {
                // 2. If no currency found, find ALL numbers in the line
                val allNumbers = Regex("""\d+""").findAll(l).map { it.value.toInt() }.toList()
                if (allNumbers.isNotEmpty()) {
                    // The price is usually the last number in the row (excluding tiny numbers)
                    val possiblePrices = allNumbers.filter { it != gb && it > 5 }
                    if (possiblePrices.isNotEmpty()) {
                        price = possiblePrices.last() 
                    }
                }
            }

            // Skip lines without GB or price (like voice-only packages)
            if (gb == 0 || price == 0) continue

            // Extract Name
            var name = l
            if (gbMatch != null) name = name.substringBefore(gbMatch.value)
            
            // Remove leading serial numbers like "1. " or "10 "
            name = name.replace(Regex("^\\d+\\.?\\s*"), "").trim()
            if (name.isEmpty()) name = "$gb GB"

            // Use the forcedNet (the SIM card the user clicked on)
            out.add(Package(name, forcedNet, currentSec, gb, 30, price))
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
                        
                        // MAGIC FIX: Convert Word XML tables properly
                        val cleanText = xmlText
                            .replace("</w:tc>", " ")  // End of a cell -> space (keeps row together)
                            .replace("</w:tr>", "\n") // End of a row -> newline
                            .replace(Regex("<[^>]*>"), " ") // Remove all XML tags
                            .replace("&amp;", "&")
                            .replace(Regex("[ \t]+"), " ") // Collapse spaces but NOT newlines
                            .replace(Regex("\n+"), "\n") // Collapse multiple newlines

                        return fromText(cleanText, forcedNet)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { }
        return emptyList()
    }
}