/*
 * Copyright (c) 2020 AtLarge Research
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

package org.opendc.experiments.base

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.opendc.compute.simulator.provisioner.Provisioner
import org.opendc.compute.simulator.provisioner.registerComputeMonitor
import org.opendc.compute.simulator.provisioner.setupComputeService
import org.opendc.compute.simulator.provisioner.setupHosts
import org.opendc.compute.simulator.scheduler.FilterScheduler
import org.opendc.compute.simulator.scheduler.filters.ComputeFilter
import org.opendc.compute.simulator.scheduler.filters.RamFilter
import org.opendc.compute.simulator.scheduler.filters.VCpuFilter
import org.opendc.compute.simulator.scheduler.weights.CoreRamWeigher
import org.opendc.compute.simulator.service.ComputeService
import org.opendc.compute.simulator.telemetry.ComputeMonitor
import org.opendc.compute.simulator.telemetry.table.HostTableReader
import org.opendc.compute.simulator.telemetry.table.ServiceTableReader
import org.opendc.compute.simulator.telemetry.table.TaskTableReader
import org.opendc.compute.topology.clusterTopology
import org.opendc.compute.topology.specs.ClusterSpec
import org.opendc.compute.workload.ComputeWorkloadLoader
import org.opendc.compute.workload.Task
import org.opendc.experiments.base.runner.replay
import org.opendc.simulator.compute.workload.trace.TraceFragment
import org.opendc.simulator.compute.workload.trace.TraceWorkload
import org.opendc.simulator.compute.workload.trace.scaling.NoDelayScaling
import org.opendc.simulator.compute.workload.trace.scaling.PerfectScaling
import org.opendc.simulator.compute.workload.trace.scaling.ScalingPolicy
import org.opendc.simulator.kotlin.runSimulation
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayList
import java.util.UUID

/**
 * Testing suite containing tests that specifically test the scaling of trace fragments
 */
class FragmentScalingTest {
    /**
     * The monitor used to keep track of the metrics.
     */
    private lateinit var monitor: TestComputeMonitor

    /**
     * The [FilterScheduler] to use for all experiments.
     */
    private lateinit var computeScheduler: FilterScheduler

    /**
     * The [ComputeWorkloadLoader] responsible for loading the traces.
     */
    private lateinit var workloadLoader: ComputeWorkloadLoader

    private val basePath = "src/test/resources/Scaling"

    /**
     * Set up the experimental environment.
     */
    @BeforeEach
    fun setUp() {
        monitor = TestComputeMonitor()
        computeScheduler =
            FilterScheduler(
                filters = listOf(ComputeFilter(), VCpuFilter(16.0), RamFilter(1.0)),
                weighers = listOf(CoreRamWeigher(multiplier = 1.0)),
            )
        workloadLoader = ComputeWorkloadLoader(File("$basePath/traces"), 0L, 0L, 0.0)
    }

    private fun createTestTask(
        name: String,
        cpuCount: Int = 1,
        cpuCapacity: Double = 0.0,
        memCapacity: Long = 0L,
        submissionTime: String = "1970-01-01T00:00",
        duration: Long = 0L,
        fragments: ArrayList<TraceFragment>,
        scalingPolicy: ScalingPolicy = NoDelayScaling(),
    ): Task {
        return Task(
            UUID.nameUUIDFromBytes(name.toByteArray()),
            name,
            cpuCount,
            cpuCapacity,
            memCapacity,
            1800000.0,
            LocalDateTime.parse(submissionTime).atZone(ZoneId.systemDefault()).toInstant(),
            duration,
            TraceWorkload(
                fragments,
                0L, 0L, 0.0,
                scalingPolicy
            ),
        )
    }

    private fun runTest(
        topology: List<ClusterSpec>,
        workload: ArrayList<Task>
    ): TestComputeMonitor {
        runSimulation {
            val monitor = monitor
            val seed = 0L
            Provisioner(dispatcher, seed).use { provisioner ->
                provisioner.runSteps(
                    setupComputeService(serviceDomain = "compute.opendc.org", { computeScheduler }),
                    registerComputeMonitor(serviceDomain = "compute.opendc.org", monitor, exportInterval = Duration.ofMinutes(1)),
                    setupHosts(serviceDomain = "compute.opendc.org", topology),
                )

                val service = provisioner.registry.resolve("compute.opendc.org", ComputeService::class.java)!!
                service.replay(timeSource, workload)
            }
        }
        return monitor
    }

    /**
     * Scaling test 1: A single fitting task
     * In this test, a single task is scheduled that should fit the Multiplexer.
     * This means nothing is delayed, and thus the workload finishes after 20 min.
     * We check if both the host and the Task show the correct cpu usage and demand during the two fragments.
     */
    @Test
    fun testScaling1() {
        val scalingPolicy = PerfectScaling();
        val workload: ArrayList<Task> =
            arrayListOf(
                createTestTask(
                    name = "0",
                    fragments =
                        arrayListOf(
                            TraceFragment(10 * 60 * 1000, 1000.0, 1),
                            TraceFragment(10 * 60 * 1000, 2000.0, 1),
                        ),
                    scalingPolicy = scalingPolicy
                ),
            )
        val topology = createTopology("single_1_2000.json")

        monitor = runTest(topology, workload)

        println(monitor.hostCpuDemands)
        println(monitor.hostCpuSupplied)
        println(monitor.finalTimestamp)

        assertAll(
            { assertEquals(1200000, monitor.finalTimestamp) { "The workload took longer to finish than expected." } },
            { assertEquals(1000.0, monitor.taskCpuDemands["0"]?.get(1)) { "The cpu demanded by task 0 is incorrect" } },
            { assertEquals(2000.0, monitor.taskCpuDemands["0"]?.get(10)) { "The cpu demanded by task 0 is incorrect" } },
            { assertEquals(1000.0, monitor.taskCpuSupplied["0"]?.get(1)) { "The cpu used by task 0 is incorrect" } },
            { assertEquals(2000.0, monitor.taskCpuSupplied["0"]?.get(10)) { "The cpu used by task 0 is incorrect" } },
            { assertEquals(1000.0, monitor.hostCpuDemands[1]) { "The cpu demanded by the host is incorrect" } },
            { assertEquals(2000.0, monitor.hostCpuDemands[10]) { "The cpu demanded by the host is incorrect" } },
            { assertEquals(1000.0, monitor.hostCpuSupplied[1]) { "The cpu used by the host is incorrect" } },
            { assertEquals(2000.0, monitor.hostCpuSupplied[10]) { "The cpu used by the host is incorrect" } },
            { assertEquals(0.0, monitor.hostCpuDemands.last()) { "When the task is finished, the host should have 0.0 demand" } },
            { assertEquals(0.0, monitor.hostCpuSupplied.last()) { "When the task is finished, the host should have 0.0 demand" } },
        )
    }

    /**
     * Scaling test 2: A single non-fitting task
     * In this test, a single task is scheduled that should not fit the Multiplexer.
     * This the second fragment is delayed, and thus the workload finishes after 30 min.
     * We check if both the host and the Task show the correct cpu usage and demand during the two fragments.
     */
    @Test
    fun testScaling2() {
        val scalingPolicy = PerfectScaling();
        val workload: ArrayList<Task> =
            arrayListOf(
                createTestTask(
                    name = "0",
                    fragments =
                        arrayListOf(
                            TraceFragment(10 * 60 * 1000, 1000.0, 1),
                            TraceFragment(10 * 60 * 1000, 4000.0, 1),
                        ),
                    scalingPolicy = scalingPolicy
                ),
            )
        val topology = createTopology("single_1_2000.json")

        monitor = runTest(topology, workload)

        println(monitor.hostCpuDemands)
        println(monitor.hostCpuSupplied)
        println(monitor.finalTimestamp)

        assertAll(
            { assertEquals(1800000, monitor.finalTimestamp) { "The workload took longer to finish than expected." } },
            { assertEquals(1000.0, monitor.taskCpuDemands["0"]?.get(1)) { "The cpu demanded by task 0 is incorrect" } },
            { assertEquals(4000.0, monitor.taskCpuDemands["0"]?.get(10)) { "The cpu demanded by task 0 is incorrect" } },
            { assertEquals(1000.0, monitor.taskCpuSupplied["0"]?.get(1)) { "The cpu used by task 0 is incorrect" } },
            { assertEquals(2000.0, monitor.taskCpuSupplied["0"]?.get(10)) { "The cpu used by task 0 is incorrect" } },
            { assertEquals(1000.0, monitor.hostCpuDemands[1]) { "The cpu demanded by the host is incorrect" } },
            { assertEquals(4000.0, monitor.hostCpuDemands[10]) { "The cpu demanded by the host is incorrect" } },
            { assertEquals(1000.0, monitor.hostCpuSupplied[1]) { "The cpu used by the host is incorrect" } },
            { assertEquals(2000.0, monitor.hostCpuSupplied[10]) { "The cpu used by the host is incorrect" } },
            { assertEquals(0.0, monitor.hostCpuDemands.last()) { "When the task is finished, the host should have 0.0 demand" } },
            { assertEquals(0.0, monitor.hostCpuSupplied.last()) { "When the task is finished, the host should have 0.0 demand" } },
        )
    }

    /**
     * Obtain the topology factory for the test.
     */
    private fun createTopology(name: String): List<ClusterSpec> {
        val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/Scaling/topologies/$name"))
        return stream.use { clusterTopology(stream) }
    }

    class TestComputeMonitor : ComputeMonitor {
        var finalTimestamp: Long = 0L;


        override fun record(reader: ServiceTableReader) {
            finalTimestamp = reader.timestamp.toEpochMilli();

            super.record(reader)
        }



        var hostCpuDemands = ArrayList<Double>()
        var hostCpuSupplied = ArrayList<Double>()

        override fun record(reader: HostTableReader) {
            hostCpuDemands.add(reader.cpuDemand)
            hostCpuSupplied.add(reader.cpuUsage)
        }

        var taskCpuDemands = mutableMapOf<String, ArrayList<Double>>()
        var taskCpuSupplied = mutableMapOf<String, ArrayList<Double>>()

        override fun record(reader: TaskTableReader) {
            val taskName: String = reader.taskInfo.name

            if (taskName in taskCpuDemands) {
                taskCpuDemands[taskName]?.add(reader.cpuDemand)
                taskCpuSupplied[taskName]?.add(reader.cpuUsage)
            } else {
                taskCpuDemands[taskName] = arrayListOf(reader.cpuDemand)
                taskCpuSupplied[taskName] = arrayListOf(reader.cpuUsage)
            }
        }
    }
}
