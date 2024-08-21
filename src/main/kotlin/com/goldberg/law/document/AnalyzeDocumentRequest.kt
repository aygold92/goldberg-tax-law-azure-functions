package com.goldberg.law.document

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.kohsuke.args4j.*
import org.kohsuke.args4j.spi.OptionHandler
import org.kohsuke.args4j.spi.Parameters
import org.kohsuke.args4j.spi.Setter
import java.util.*
import kotlin.system.exitProcess

class AnalyzeDocumentRequest {
    private val logger = KotlinLogging.logger {}

    @JsonProperty("InputFile")
    @Option(name = "-if", aliases = ["--inputFile"], usage = "Input file path", required = true)
    lateinit var inputFile: String
    @Option(name = "-od", aliases = ["outputDirectory"], usage = "Output directory", required = false)
    var outputDirectory: String = "./"
    @Option(name = "-prod", usage = "use the prod endpoint", required = false)
    var isProd: Boolean = false
    @Option(name = "-a", usage = "use all pages", required = false, forbids = [])
    var allPages: Boolean = false
    @Option(name = "-r", usage = "start page to end page", required = false, handler = PairOptionHandler::class)
    var range: Pair<Int, Int>? = null
    @Option(name = "-p", usage = "list of page numbers", required = false, handler = IntSetOptionHandler::class)
    var pages: Set<Int>? = null
    @Option(name = "-to", usage = "type override -- used to preclassify documents", required = false)
    var classifiedTypeOverride: String? = null
    @Option(name = "-of", aliases = ["--outputFilename"], usage = "Output filename", required = false)
    var outputFile: String? = null
    @Option(name = "-sep", usage = "separate pages?", required = false)
    var isSeparate: Boolean = false


    fun parseArgs(args: Array<String>): AnalyzeDocumentRequest {
        // Register the custom handler
        OptionHandlerRegistry.getRegistry().registerHandler(Pair::class.java, PairOptionHandler::class.java)
        val parser = CmdLineParser(this)
        try {
            parser.parseArgument(*args)
        } catch (e: CmdLineException) {
            logger.error { e.message }
            parser.printUsage(System.err)
            exitProcess(1)
        }
        return this
    }

    fun getDesiredPages(): List<Int> {
        val range: List<Int> = this.range?.first?.rangeTo(this.range!!.second)?.toList()
            .let { it ?: Collections.emptyList() }

        val pages: List<Int> = this.pages?.toList()
            .let { it ?: Collections.emptyList() }

        return (range + pages).distinct().sorted()
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