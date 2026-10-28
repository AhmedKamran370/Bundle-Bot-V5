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
            if (l.contains("Package", true) && !l.any { it.isDigit() }) {
                currentSec = l
                continue
            }

            // 3. Extract GB
            val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(l)
            val gb = gbMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // 4. Extract Price (look for SAR, Rs, or numbers at the end)
            val textWithoutGb = if (gbMatch != null) l.replace(gbMatch.value, "") else l
            
            // Find all numbers, the last one is usually the price
            val priceMatch = Regex("""(?:SAR|Rs\.?|PKR)?\s*(\d+)""", RegexOption.IGNORE_CASE).findAll(textWithoutGb).lastOrNull()
            val price = priceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // If no GB and no price, it's just text or voice minutes. Skip.
            if (gb == 0 && price == 0) continue

            // 5. Extract Name
            var namePart = textWithoutGb
            // Remove leading serial numbers like "1." or "1 "
            namePart = namePart.replace(Regex("^\\d+\\.?\\s*"), "")
            
            // Take everything before the price numbers
            if (priceMatch != null) {
                namePart = namePart.substringBefore(priceMatch.value)
            }
            
            namePart = namePart.trim()
            if (namePart.isEmpty()) namePart = if (gb > 0) "$gb GB" else "Unknown Package"

            // 6. Determine Network (Defaults to Jazz based on your file)
            val net = guessNetwork(line).ifBlank { "Jazz" } 

            // Default days to 30 if not specified, you can edit this later
            out.add(Package(namePart, net, currentSec, gb, 30, price))
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
                            .replace("</w:tr>", "\n") // End of table row = newline
                            .replace(Regex("<[^>]*>"), " ") // Remove XML tags
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
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