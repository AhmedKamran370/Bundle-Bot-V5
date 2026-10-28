package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream

object PackageParser {
    fun fromText(text: String): List<Package> {
        val out = mutableListOf<Package>()
        var currentSec = "Other"

        for (line in text.lines()) {
            // 1. Clean the line of table borders, asterisks, backslashes
            var l = line.replace("|", " ")
                .replace("+", " ")
                .replace("-", " ")
                .replace("*", "")
                .replace("\\", "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (l.isEmpty()) continue

            // 2. Detect Section Headers (e.g., "Monthly All Data Packages")
            if (l.contains("Package", true) && !l.any { it.isDigit() } && !l.contains("SR.NO", true)) {
                currentSec = l
                continue
            }

            // 3. Extract GB
            val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(l)
            val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // 4. Extract Price
            // Look for SAR 46, Rs. 500, PKR 200
            val priceMatch = Regex("""(?:SAR|Rs\.?|PKR)\s*(\d+)""", RegexOption.IGNORE_CASE).find(l)
            var price = priceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            
            // If no currency found, try finding the last number in the string
            if (price == 0) {
                val lastNum = Regex("""(\d+)""").findAll(l).lastOrNull()
                if (lastNum != null) price = lastNum.groupValues[1].toIntOrNull() ?: 0
            }

            // Skip lines that don't have a GB or a Price (like voice-only packages)
            if (gb == 0 || price == 0) continue

            // 5. Extract Name
            var name = l
            if (gbMatch != null) name = name.substringBefore(gbMatch.value)
            if (priceMatch != null) name = name.substringBefore(priceMatch.value)
            else if (l.endsWith(price.toString())) name = name.substringBefore(price.toString())

            // Remove leading serial numbers like "1. " or "10 "
            name = name.replace(Regex("^\\d+\\.?\\s*"), "").trim()
            if (name.isEmpty()) name = "$gb GB"

            // 6. Determine Network
            val net = guessNetwork(line).ifBlank { "Jazz" } 

            out.add(Package(name, net, currentSec, gb, 30, price))
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
                        
                        // Convert Word XML to text
                        val cleanText = xmlText
                            .replace("</w:p>", "\n")
                            .replace("</w:tr>", "\n") // End of table row becomes newline
                            .replace(Regex("<[^>]*>"), " ") // Remove XML tags
                            .replace("&amp;", "&")
                            .replace(Regex("[ \t]+"), " ") // Collapse spaces but NOT newlines
                            .replace(Regex("\n+"), "\n") // Collapse multiple newlines

                        return fromText(cleanText)
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return emptyList()
    }
}