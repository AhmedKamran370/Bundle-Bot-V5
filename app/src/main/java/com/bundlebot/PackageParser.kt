package com.bundlebot

import java.io.InputStream
import java.util.zip.ZipInputStream

object PackageParser {
    fun fromText(text: String, forcedNet: String): List<Package> {
        val out = mutableListOf<Package>()
        var currentSec = "Other"

        for (rawLine in text.lines()) {
            var l = rawLine.trim()
            if (l.isEmpty()) continue

            // Detect Section Headers (e.g., "Monthly All Data Packages")
            if (l.contains("Package", true) && !l.any { it.isDigit() } && !l.contains("SR.NO", true)) {
                currentSec = l.replace("|", "").trim()
                continue
            }

            var gb = 0
            var price = 0
            var name = ""

            if (l.contains("|")) {
                // TABLE FORMAT (From our custom DOCX parser)
                val cells = l.split("|").map { it.trim() }
                
                for (i in cells.indices) {
                    val cell = cells[i]
                    
                    // Extract GB
                    val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(cell)
                    if (gbMatch != null && gb == 0) {
                        gb = gbMatch.groupValues[1].toIntOrNull() ?: 0
                        if (cell.replace(gbMatch.value, "").trim().isEmpty() && i > 0) {
                            name = cells[i-1] // Name is in the previous cell
                        } else {
                            name = cell.substringBefore(gbMatch.value).trim()
                        }
                    }
                    
                    // Extract Price (Extract ALL digits from the price cell to fix split numbers)
                    if (price == 0 && (cell.contains("SAR", true) || cell.contains("Rs", true) || cell.contains("PKR", true))) {
                        val numOnly = cell.replace(Regex("[^0-9]"), "") // Removes spaces, SAR, etc.
                        if (numOnly.isNotEmpty()) price = numOnly.toIntOrNull() ?: 0
                    }
                }
            } else {
                // STANDARD TEXT FORMAT
                l = l.replace("*", "").replace("\\", "").replace(Regex("\\s+"), " ")
                val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(l)
                if (gbMatch != null) {
                    gb = gbMatch.groupValues[1].toIntOrNull() ?: 0
                    name = l.substringBefore(gbMatch.value)
                }
                
                val priceMatch = Regex("""(?:SAR|Rs\.?|PKR)\s*(\d+)""", RegexOption.IGNORE_CASE).find(l)
                if (priceMatch != null) {
                    price = priceMatch.groupValues[1].toIntOrNull() ?: 0
                } else {
                    val allNums = Regex("""\d+""").findAll(l).map { it.value.toInt() }.toList()
                    if (allNums.isNotEmpty()) {
                        val possible = allNums.filter { it != gb && it > 5 }
                        if (possible.isNotEmpty()) price = possible.last()
                    }
                }
            }

            name = name.replace(Regex("^\\d+\\.?\\s*"), "") // Remove "1. "
                .replace("*", "")
                .trim()
            
            if (name.isEmpty() && gb > 0) name = "$gb GB"

            if (gb > 0 && price > 0) {
                out.add(Package(name, forcedNet, currentSec, gb, 30, price))
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
                        
                        // XML to Text conversion that keeps table cells together
                        val cleanText = xmlText
                            .replace("</w:tc>", "|") // Mark end of a cell
                            .replace("</w:tr>", "\n") // Mark end of a row
                            .replace(Regex("<[^>]*>"), " ") // Remove XML tags
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