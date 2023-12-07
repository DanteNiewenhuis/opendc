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

package org.opendc.experiments.uptime.portfolio

import org.opendc.experiments.uptime.model.OperationalPhenomena
import org.opendc.experiments.uptime.model.Scenario
import org.opendc.experiments.uptime.model.Topology
import org.opendc.experiments.uptime.model.Workload
import org.opendc.experiments.compute.sampleByLoad
import org.opendc.experiments.compute.trace

/**
 * A [Portfolio] that explores the difference between horizontal and vertical scaling.
 */
public class UptimePortfolio : Portfolio {
    private val topology = Topology("single")

    private val workload = Workload("bitbrains-small", trace("trace").sampleByLoad(1.0));

    private val check_times = listOf(
        5 * 60000L, // 5 minutes
    )

    private val check_waits = listOf(
        30 * 60000L, // 30 minutes
        60 * 60000L,
        90 * 60000L,
        2 * 60 * 60000L,
        5 * 60 * 60000L,
        10 * 60 * 60000L,
        50 * 60 * 60000L,
        100 * 60 * 60000L,
    )

    private val operationalPhenomena = OperationalPhenomena(0.0, false)
    private val allocationPolicy = "active-servers"

    override val scenarios: Iterable<Scenario> = check_times.flatMap { check_time ->
        check_waits.map { check_wait ->
            Scenario(
                topology,
                workload,
                operationalPhenomena,
                allocationPolicy,
                check_time,
                check_wait,
                mapOf("topology" to topology.name, "workload" to workload.name)
            )
        }
    }
}
