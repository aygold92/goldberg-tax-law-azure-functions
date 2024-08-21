package com.goldberg.law.pdf.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.IntSetOptionHandler
import com.goldberg.law.document.PairOptionHandler
import org.kohsuke.args4j.*
import kotlin.system.exitProcess

class SplitPdfRequest {
    @JsonProperty("InputFile")
    @Option(name = "-if", aliases = ["--inputFile"], usage = "Input file path", required = true)
    lateinit var inputFile: String
    @Option(name = "-of", aliases = ["--outputFilename"], usage = "Output filename", required = true)
    lateinit var outputFile: String
    @Option(name = "-od", aliases = ["outputDirectory"], usage = "Output directory", required = false)
    var outputDirectory: String = "./"
    @Option(name = "-r", usage = "start page to end page", required = false, handler = PairOptionHandler::class)
    var range: Pair<Int, Int>? = null
    @Option(name = "-p", usage = "list of page numbers", required = false, handler = IntSetOptionHandler::class)
    var pages: Set<Int>? = null
    @Option(name = "-sep", usage = "separate pages?", required = false)
    var isSeparate: Boolean = false

    fun parseArgs(args: Array<String>): SplitPdfRequest {
        // Register the custom handler
        OptionHandlerRegistry.getRegistry().registerHandler(Pair::class.java, PairOptionHandler::class.java)
        val parser = CmdLineParser(this)
        try {
            parser.parseArgument(*args)
        } catch (e: CmdLineException) {
            println(e.message)
            parser.printUsage(System.err)
            exitProcess(1)
        }
        return this
    }
}

