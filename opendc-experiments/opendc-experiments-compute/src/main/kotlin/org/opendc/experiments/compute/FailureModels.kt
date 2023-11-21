/*
 * Copyright (c) 2021 AtLarge Research
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

@file:JvmName("FailureModels")

package org.opendc.experiments.compute

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.math3.distribution.LogNormalDistribution
import org.apache.commons.math3.random.Well19937c
import org.opendc.compute.service.ComputeService
import org.opendc.compute.simulator.SimHost
import org.opendc.compute.simulator.failure.HostFaultInjector
import org.opendc.compute.simulator.failure.StartStopHostFault
import org.opendc.compute.simulator.failure.StochasticVictimSelector
import org.opendc.simulator.compute.workload.SimRuntimeWorkload
import org.opendc.trace.failure.FailureTraceReader
import java.nio.file.Paths
import java.time.Clock
import java.time.Duration
import java.time.InstantSource
import java.util.Random
import java.util.random.RandomGenerator
import kotlin.coroutines.CoroutineContext
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Obtain a [FailureModel] based on the GRID'5000 failure trace.
 *
 * This fault injector uses parameters from the GRID'5000 failure trace as described in
 * "A Framework for the Study of Grid Inter-Operation Mechanisms", A. Iosup, 2009.
 */
public fun grid5000(failureInterval: Duration): FailureModel {
    return object : FailureModel {
        override fun createInjector(
            context: CoroutineContext,
            clock: InstantSource,
            service: ComputeService,
            random: RandomGenerator
        ): HostFaultInjector {
            val rng = Well19937c(random.nextLong())
            val hosts = service.hosts.map { it as SimHost }.toSet()

            // Parameters from A. Iosup, A Framework for the Study of Grid Inter-Operation Mechanisms, 2009
            // GRID'5000
            return HostFaultInjector(
                context,
                clock,
                hosts,
                iat = LogNormalDistribution(rng, ln(failureInterval.toHours().toDouble()), 1.03),
                selector = StochasticVictimSelector(LogNormalDistribution(rng, 1.88, 1.25), random),
                fault = StartStopHostFault(LogNormalDistribution(rng, 8.89, 2.71))
            )
        }

        override fun toString(): String = "Grid5000FailureModel"
    }
}

public fun cloudUptimeArchive(traceName: String): FailureModel {
    return object : FailureModel {
        override fun createInjector(
            context: CoroutineContext,
            clock: InstantSource,
            service: ComputeService,
            random: RandomGenerator
        ): HostFaultInjector {
            val hosts = service.hosts.map { it as SimHost }.toSet()

            return TraceBasedFaultInjector(
                context,
                clock,
                hosts,
                traceName,
                InstantMigrationHostFault(service, clock)// StopHostFault()
            )
        }

        override fun toString(): String = "CloudUptimeArchiveFailureModel"
    }
}

public class TraceBasedFaultInjector(
    private val context: CoroutineContext,
    private val clock: InstantSource,
    private val hosts: Set<SimHost>,
    private val traceName: String,
    private val faultType: DurationHostFault
) : HostFaultInjector {
    /**
     * The scope in which the injector runs.
     */
    private val scope = CoroutineScope(context + Job())

    /**
     * The [Job] that awaits the nearest fault in the system.
     */
    private var job: Job? = null

    // TODO: replace with traceName
    private var failureList = FailureTraceReader.get(Paths.get("resources/failure_traces/$traceName")).iterator()

    /**
     * Start the fault injection into the system.
     */
    override fun start() {
        if (job != null) {
            return
        }

        job = scope.launch {
            runInjector()
            job = null
        }
    }

    /**
     * Converge the injection process.
     */
    private suspend fun runInjector() {
        while (failureList.hasNext()) {
            val failure = failureList.next()

            delay(failure.start - clock.millis())

            val numVictims = (failure.intensity * hosts.size).roundToInt()
            val victims = hosts.shuffled().take(numVictims)

            scope.launch {
                faultType.apply(clock, failure.duration, victims)
            }
        }
    }

    /**
     * Stop the fault injector.
     */
    public override fun close() {
        scope.cancel()
    }
}

public interface DurationHostFault {
    /**
     * Apply the fault to the specified [victims].
     */
    public suspend fun apply(clock: InstantSource, duration: Long, victims: List<SimHost>)
}

public class StopHostFault : DurationHostFault {

    override suspend fun apply(clock: InstantSource, duration: Long, victims: List<SimHost>) {
        for (host in victims) {
            host.fail()
        }

        // Handle long overflow
        if (clock.millis() + duration <= 0) {
            return
        }

        delay(duration)

        for (host in victims) {
            host.recover()
        }
    }

    override fun toString(): String = "StopHostFault"
}

public class InstantMigrationHostFault(
    private val service: ComputeService, private val clock: InstantSource //TODO: clock not needed?
) : DurationHostFault {

    override suspend fun apply(clock: InstantSource, duration: Long, victims: List<SimHost>) {
        val client = service.newClient()

        for (host in victims) {
            val servers = host.instances // TODO: why is did shuffled?
            val snapshots = servers.map { (it.meta["workload"] as SimRuntimeWorkload).snapshot() }
            host.fail()

            for ((server, snapshot) in servers.zip(snapshots)) {
//                snapshot.cpuCount = 2 // TODO: Hardcoded cpu_count?
                client.rescheduleServer(server, snapshot)
            }
        }

        // Handle long overflow
        if (clock.millis() + duration <= 0) {
            return
        }

        delay(duration)

        for (host in victims) {
            host.recover()
        }
    }

    override fun toString(): String = "StopHostFault"
}

