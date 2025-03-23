package com.goldberg.law.splitpdftool

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

class SplitDocumentRequest() {

    @JsonCreator
    constructor(
        @JsonProperty("InputFile") inputFile: String,
        @JsonProperty("OutputDirectory") outputDirectory: String? = "./",
        @JsonProperty("UseAllPages") allPages: Boolean? = false,
        @JsonProperty("Range") range: Pair<Int, Int>? = null,
        @JsonProperty("Pages") pages: Set<Int>? = null,
        @JsonProperty("OutputFilename") outputFile: String? = null,
    ): this() {
        this.inputFile = inputFile
        this.outputDirectory = outputDirectory ?: "./"
        this.allPages = allPages ?: false
        this.range = range
        this.pages = pages
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
    @JsonProperty("UseAllPages")
    @Option(name = "-a", usage = "use all pages", required = false, forbids = ["r", "p"])
    var allPages: Boolean = false
    @JsonProperty("Range")
    @Option(name = "-r", usage = "start page to end page", required = false, handler = PairOptionHandler::class)
    var range: Pair<Int, Int>? = null
    @JsonProperty("Pages")
    @Option(name = "-p", usage = "list of page numbers", required = false, handler = IntSetOptionHandler::class)
    var pages: Set<Int>? = null
    @JsonProperty("OutputFilename")
    @Option(name = "-of", aliases = ["--outputFilename"], usage = "Output filename", required = false)
    var outputFile: String? = null
    @JsonProperty("SeparatePages")
    @Option(name = "-sep", usage = "separate pages?", required = false)
    var isSeparate: Boolean = false


    fun parseArgs(args: Array<String>): SplitDocumentRequest {
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
        if (this.range == null && this.pages == null && !this.allPages) {
            throw IllegalArgumentException("You must specify at least one of range (-r), pages (-p), or all (-a)")
        }

        if (this.allPages) {
            return emptyList()
        }
        val range: List<Int> = this.range?.first?.rangeTo(this.range!!.second)?.toList()
            .let { it ?: Collections.emptyList() }

        val pages: List<Int> = this.pages?.toList()
            .let { it ?: Collections.emptyList() }

        val totalPages = (range + pages).distinct().sorted()
        if (totalPages.isEmpty()) {
            throw IllegalArgumentException("No pages were specified")
        }
        return totalPages
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