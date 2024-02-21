/*
 * Copyright (c) 2022 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:JvmName("FootPrinterCli")

package org.opendc.experiments.footprinter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.opendc.compute.workload.sampleByLoad
import org.opendc.compute.workload.trace
import java.io.File
import java.util.concurrent.ForkJoinPool
import java.util.stream.LongStream

import org.opendc.experiments.base.portfolio.model.OperationalPhenomena
import org.opendc.experiments.base.portfolio.model.Scenario
import org.opendc.experiments.base.portfolio.model.Topology
import org.opendc.experiments.base.portfolio.model.Workload

/**
 * Main entrypoint of the application.
 */
fun main(args: Array<String>): Unit = FootPrinterCommand().main(args)

/**
 * Represents the command for the FootPrinter experiments.
 */
internal class FootPrinterCommand : CliktCommand(name = "footprinter") {
    /**
     * The path to the environment directory.
     */
    private val topologyPath by option("--topology-path", help = "path to environment directory")
        .file(canBeDir = false, canBeFile = true)
        .defaultLazy { File("input/environments") }

    /**
     * The path to the trace directory.
     */
    private val tracePath by option("--trace-path", help = "path to trace directory")
        .file(canBeDir = true, canBeFile = false)
        .defaultLazy { File("input/traces") }

    /**
     * The path to the experiment output.
     */
    private val outputPath by option("-O", "--output", help = "path to experiment output")
        .file(canBeDir = true, canBeFile = false)
        .defaultLazy { File("output") }

    private val basePartitions: Map<String, String> by option("-P", "--base-partitions").associate()

    override fun run() {
        val runner = FootPrinterRunner(topologyPath, tracePath.parentFile, outputPath)

        val topology  = Topology(topologyPath.nameWithoutExtension)
        val workload = Workload(tracePath.nameWithoutExtension, trace(tracePath.nameWithoutExtension).sampleByLoad(1.0))
        val scenario = Scenario(
            topology,
            workload,
            OperationalPhenomena(0.0, false),
            "active-servers",
            mapOf("topology" to topology.name, "workload" to workload.name)
        )

        val pool = ForkJoinPool(1)

        runScenario(runner, pool, scenario)

        pool.shutdown()
    }

    /**
     * Run a single scenario.
     */
    private fun runScenario(runner: FootPrinterRunner, pool: ForkJoinPool, scenario: Scenario) {
        val pb = ProgressBarBuilder()
            .setInitialMax(1)
            .setStyle(ProgressBarStyle.ASCII)
            .setTaskName("Simulating...")
            .build()

        pool.submit {
            LongStream.range(0, 1)
                .parallel()
                .forEach { repeat ->
                    val augmentedScenario = scenario.copy(partitions = basePartitions + scenario.partitions)
                    runner.runScenario(augmentedScenario, 0 + repeat)
                    pb.step()
                }

            pb.close()
        }.join()
    }
}
