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
                // TABLE FORMAT
                val cells = l.split("|").map { it.trim() }
                
                // In your Word doc, the columns are: SR.NO | Name | Data | Price
                // We can find the Name and GB by looking at the cells
                for (i in cells.indices) {
                    val cell = cells[i]
                    
                    // Extract GB (usually in column 2 or 3)
                    val gbMatch = Regex("""(\d+)\s*(?:GB|gb)""").find(cell)
                    if (gbMatch != null && gb == 0) {
                        gb = gbMatch.groupValues[1].toIntOrNull() ?: 0
                        // If the cell is JUST "15GB", the name is in the previous cell
                        if (cell.replace(gbMatch.value, "").replace(Regex("[^a-zA-Z]"), "").trim().isEmpty() && i > 0) {
                            name = cells[i-1]
                        } else {
                            name = cell.substringBefore(gbMatch.value).trim()
                        }
                    }
                    
                    // Extract Price (look for SAR, Rs, PKR)
                    if (price == 0 && (cell.contains("SAR", true) || cell.contains("Rs", true) || cell.contains("PKR", true))) {
                        val numOnly = cell.replace(Regex("[^0-9]"), "") 
                        if (numOnly.isNotEmpty()) {
                            var rawPrice = numOnly.toIntOrNull() ?: 0
                            // Convert to PKR base (assuming 1 SAR = 74 PKR)
                            if (cell.contains("SAR", true)) rawPrice = (rawPrice * 74.0).toInt()
                            price = rawPrice
                        }
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
                    var rawPrice = priceMatch.groupValues[1].toIntOrNull() ?: 0
                    if (priceMatch.value.contains("SAR", true)) rawPrice = (rawPrice * 74.0).toInt()
                    price = rawPrice
                }
            }

            // CLEAN UP THE NAME
            // Remove serial numbers like "1. " or "19. - "
            name = name.replace(Regex("^\\d+\\.?\\s*-?\\s*"), "")
                .replace("*", "")
                .trim()
            
            // If the name has weird symbols or is too long, fallback to GB
            if (name.length > 40 || name.contains("On-mint") || name.contains("Off-mint")) {
                name = if (gb > 0) "$gb GB Package" else "Data Package"
            }
            if (name.isEmpty() && gb > 0) name = "$gb GB Package"

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