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

    private val checkTimes = listOf(
        5 * 60000L, // 5 minutes
    )

    private val checkWaits = listOf(
        30 * 60000L, // 30 minutes
//        60 * 60000L,
//        2 * 60 * 60000L,
//        4 * 60 * 60000L,
//        6 * 60 * 60000L,
//        12 * 60 * 60000L,
//        24 * 60 * 60000L, // 1 day
//        24 * 80 * 60000L, // 1 day
//        24 * 100 * 60000L, // 1 day
//        2 * 24 * 60 * 60000L, // 1 day
//        3 * 24 * 60 * 60000L, // 1 day
//        4 * 24 * 60 * 60000L, // 1 day
//        5 * 24 * 60 * 60000L,
//        7 * 24 * 60 * 60000L,
//        8 * 24 * 60 * 60000L,
//        9 * 24 * 60 * 60000L,
//        10 * 24 * 60 * 60000L,
//        11 * 24 * 60 * 60000L,
//        12 * 24 * 60 * 60000L,
//        15 * 24 * 60 * 60000L,
//        20 * 24 * 60 * 60000L,
//        30 * 24 * 60 * 60000L,
    )

    private val operationalPhenomena = OperationalPhenomena(0.0, false)
    private val allocationPolicy = "active-servers"

    private val failureTraces = listOf(
        "no_failure",
        "single_failure",
        "two_failures",
    )

    override val scenarios: Iterable<Scenario> = failureTraces.flatMap { failureTrace ->
        checkTimes.flatMap{ checkTime ->
            checkWaits.map { checkWait ->
                Scenario(
                    topology,
                    workload,
                    operationalPhenomena,
                    allocationPolicy,
                    failureTrace,
                    checkTime,
                    checkWait,
                    mapOf("topology" to topology.name, "workload" to workload.name)
                )
            }
        }
    }
}
