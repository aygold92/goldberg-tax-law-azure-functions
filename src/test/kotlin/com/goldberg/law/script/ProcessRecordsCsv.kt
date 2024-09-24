package com.goldberg.law.script
import com.goldberg.law.util.addQuotes
import org.junit.jupiter.api.Test
import java.io.File

class ProcessRecordsCsv {
    fun readFileRelative(filename: String): File =
        File(javaClass.getResource("/com/goldberg/law/script/$filename")!!.toURI())

    fun processCSV(inputFile: String, outputFile: String) {
        val inputLines = readFileRelative(inputFile).readLines().toMutableList()
        val outputLines = mutableListOf<String>()
        var startSpot: Int? = null
        var accountString: String? = null
        var statementDate: String? = null

        for (i in inputLines.indices) {
            val line = inputLines[i]
            val cells = parseCsvLine(line).toMutableList()

            // If the first cell starts with "[" and there's only 1 cell
            if (cells[0].startsWith("[") && cells.size == 1) {
                // add statement balance
                if (i + 1 < inputLines.size) {
                    val nextCells = parseCsvLine(inputLines[i + 1]).toMutableList()
                    if (nextCells.size < 7) continue // no records

                    accountString = nextCells[4]
                    statementDate = nextCells[6]

                    // Ensure row has at least 7 columns and update the 5th and 7th columns
                    while (cells.size < 7) {
                        cells.add("")
                    }
                    cells[4] = accountString // Copy 5th column
                    cells[6] = statementDate // Copy 7th column
                }
            }

            // If the first cell starts with "[Beginning balance"
            if (cells[0].startsWith("[Beginning balance")) {
                startSpot = outputLines.size + 1
            }

            // If startSpot is set and we've found a blank line, add the formula
            if (startSpot != null && line.isBlank()) {
                val endSpot = outputLines.size // The current blank line
                val formula = "=SUM(C$startSpot:C$endSpot)"
                val formulaRow = listOf("", "", formula, "", accountString, "", statementDate)
                outputLines.add(formulaRow.joinToString(","))
                outputLines.add("") // Add another blank line
                startSpot = null
            }

            outputLines.add(cells.joinToString(",") {it.addQuotes()})
        }

        // Write the output CSV
        File(outputFile).writeText(outputLines.joinToString("\n"))
    }

    // A simple CSV parser to handle quoted commas properly
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false

        line.forEach { char ->
            when {
                char == '"' -> insideQuotes = !insideQuotes // Toggle quote state
                char == ',' && !insideQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        // Add the last field
        result.add(current.toString().trim())
        return result
    }

    // @Test
    fun main() {
        val inputFile = "2023-2024_Records.csv"   // Path to input CSV file
        val outputFile = "./2023-2024_Records_Updated.csv" // Path to output CSV file

        processCSV(inputFile, outputFile)
    }
}
