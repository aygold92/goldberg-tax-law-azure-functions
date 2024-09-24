package com.goldberg.law.document

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.kohsuke.args4j.*
import org.kohsuke.args4j.spi.OptionHandler
import org.kohsuke.args4j.spi.Parameters
import org.kohsuke.args4j.spi.Setter
import java.util.*
import kotlin.system.exitProcess

class AnalyzeDocumentRequest() {

    @JsonCreator
    constructor(
        @JsonProperty("InputFile") inputFile: String,
        @JsonProperty("OutputDirectory") outputDirectory: String? = "./",
        @JsonProperty("UseProd") isProd: Boolean? = false,
        @JsonProperty("UseAllPages") allPages: Boolean? = false,
        @JsonProperty("Range") range: Pair<Int, Int>? = null,
        @JsonProperty("Pages") pages: Set<Int>? = null,
        @JsonProperty("ClassifiedTypeOverride") classifiedTypeOverride: String? = null,
        @JsonProperty("OutputFilename") outputFile: String? = null,
    ): this() {
        this.inputFile = inputFile
        this.outputDirectory = outputDirectory ?: "./"
        this.isProd = isProd ?: false
        this.allPages = allPages ?: false
        this.range = range
        this.pages = pages
        this.classifiedTypeOverride = classifiedTypeOverride
        this.outputFile = outputFile
    }

    @JsonIgnore
    private val logger = KotlinLogging.logger {}

    @JsonProperty("InputFile")
    @Option(name = "-if", aliases = ["--inputFile"], usage = "Input file path", required = true)
    lateinit var inputFile: String
    @JsonProperty("OutputDirectory")
    @Option(name = "-od", aliases = ["outputDirectory"], usage = "Output directory", required = false)
    var outputDirectory: String = "./"
    @JsonProperty("UseProd")
    @Option(name = "-prod", usage = "use the prod endpoint", required = false)
    var isProd: Boolean = false
    @JsonProperty("UseAllPages")
    @Option(name = "-a", usage = "use all pages", required = false, forbids = [])
    var allPages: Boolean = false
    @JsonProperty("Range")
    @Option(name = "-r", usage = "start page to end page", required = false, handler = PairOptionHandler::class)
    var range: Pair<Int, Int>? = null
    @JsonProperty("Pages")
    @Option(name = "-p", usage = "list of page numbers", required = false, handler = IntSetOptionHandler::class)
    var pages: Set<Int>? = null
    @JsonProperty("ClassifiedTypeOverride")
    @Option(name = "-to", usage = "type override -- used to preclassify documents", required = false)
    var classifiedTypeOverride: String? = null
    @JsonProperty("OutputFilename")
    @Option(name = "-of", aliases = ["--outputFilename"], usage = "Output filename", required = false)
    var outputFile: String? = null
    @JsonProperty("SeparatePages")
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

    fun forFile(filename: String): AnalyzeDocumentRequest = AnalyzeDocumentRequest().apply {
        this@apply.inputFile = filename
        this@apply.outputDirectory = this@AnalyzeDocumentRequest.outputDirectory
        this@apply.isProd = this@AnalyzeDocumentRequest.isProd
        this@apply.allPages = this@AnalyzeDocumentRequest.allPages
        this@apply.range = this@AnalyzeDocumentRequest.range
        this@apply.pages = this@AnalyzeDocumentRequest.pages
        this@apply.classifiedTypeOverride = this@AnalyzeDocumentRequest.classifiedTypeOverride
        this@apply.outputFile = this@AnalyzeDocumentRequest.outputFile
        this@apply.isSeparate = this@AnalyzeDocumentRequest.isSeparate
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