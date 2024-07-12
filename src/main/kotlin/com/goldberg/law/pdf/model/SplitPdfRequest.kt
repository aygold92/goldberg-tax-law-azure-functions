package com.goldberg.law.pdf.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.kohsuke.args4j.*
import org.kohsuke.args4j.spi.OptionHandler
import org.kohsuke.args4j.spi.Parameters
import org.kohsuke.args4j.spi.Setter
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

class PairOptionHandler(parser: CmdLineParser?, option: OptionDef?, setter: Setter<in Pair<Int, Int>>?) : OptionHandler<Pair<Int, Int>>(parser, option, setter) {

    override fun parseArguments(params: Parameters): Int {
        val args = params.getParameter(0).split(",").map { it.toInt() }
        if (args.size != 2) {
            throw IllegalArgumentException("Expected format: key,value")
        }
        setter.addValue(Pair(args[0], args[1]))

        return 1
    }

    override fun getDefaultMetaVariable(): String {
        return "KEY,VALUE"
    }
}

class IntSetOptionHandler(parser: CmdLineParser?, option: OptionDef?, setter: Setter<in Set<Int>>?) : OptionHandler<Set<Int>>(parser, option, setter) {

    override fun parseArguments(params: Parameters): Int {
        val token = params.getParameter(0).split(",").map {
            it.toInt()
        }.toSet()
        setter.addValue(token)
        return 1
    }

    override fun getDefaultMetaVariable(): String {
        return "KEY,VALUE"
    }
}