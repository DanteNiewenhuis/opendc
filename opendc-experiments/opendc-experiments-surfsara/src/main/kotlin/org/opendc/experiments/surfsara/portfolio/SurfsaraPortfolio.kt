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

package org.opendc.experiments.surfsara.portfolio

import org.opendc.experiments.surfsara.model.OperationalPhenomena
import org.opendc.experiments.surfsara.model.Scenario
import org.opendc.experiments.surfsara.model.Topology
import org.opendc.experiments.surfsara.model.Workload
import org.opendc.experiments.compute.sampleByLoad
import org.opendc.experiments.compute.trace

/**
 * A [Portfolio] that explores the difference between horizontal and vertical scaling.
 */
public class SurfsaraPortfolio : Portfolio {
    private val topologies = listOf(
        Topology("277"),
        Topology("277_2"),
        Topology("277_3"),
//        Topology("surfsara_large"),
    )

    private val workloads = listOf(
//        Workload("surfsara/2022-10-10_2022-10-11", trace("surfsara/2022-10-10_2022-10-11").sampleByLoad(1.0)),
//        Workload("surfsara/2022-10-10_2022-10-25", trace("surfsara/2022-10-10_2022-10-25").sampleByLoad(1.0)),
//        Workload("surfsara/2022-10-13_2022-10-14", trace("surfsara/2022-10-13_2022-10-14").sampleByLoad(1.0)),
        Workload("surfsara/2022-10-07_2022-10-14", trace("surfsara/2022-10-07_2022-10-14").sampleByLoad(1.0)),
    )
    private val operationalPhenomena = OperationalPhenomena(0.0, false)
    private val allocationPolicy = "active-servers"

    override val scenarios: Iterable<Scenario> = topologies.flatMap { topology ->
        workloads.map { workload ->
            Scenario(
                topology,
                workload,
                operationalPhenomena,
                allocationPolicy,
                mapOf("topology" to topology.name, "workload" to workload.name)
            )
        }
    }
}
