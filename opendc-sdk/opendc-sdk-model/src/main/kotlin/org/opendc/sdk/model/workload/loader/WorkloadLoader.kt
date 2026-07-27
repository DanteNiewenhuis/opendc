/*
 * Copyright (c) 2025 AtLarge Research
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

package org.opendc.sdk.model.workload.loader

import mu.KotlinLogging
import org.opendc.common.units.TimeDelta
import org.opendc.sdk.model.workload.TaskSpec
import java.time.LocalDateTime
import java.time.ZoneOffset

public abstract class WorkloadLoader(private val submissionTime: String? = null) {
    private val logger = KotlinLogging.logger {}

    /**
     * Shifts every task in [workload] so the earliest submission time becomes [submissionTime],
     * preserving the relative offsets (including deadlines) between tasks. Returns [workload]
     * unchanged when no [submissionTime] was configured.
     */
    public fun reScheduleTasks(workload: List<TaskSpec>): List<TaskSpec> {
        if (submissionTime == null) {
            return workload
        }

        val workloadSubmissionTime = workload.minOf { it.submissionTime.toMsLong() }
        val submissionTimeLong = LocalDateTime.parse(submissionTime).toInstant(ZoneOffset.UTC).toEpochMilli()

        val timeShift = submissionTimeLong - workloadSubmissionTime

        return workload.map { task ->
            task.copy(
                submissionTime = TimeDelta.ofMillis(task.submissionTime.toMsLong() + timeShift),
                deadline = task.deadline?.let { TimeDelta.ofMillis(it.toMsLong() + timeShift) },
            )
        }
    }

    public abstract fun load(): List<TaskSpec>

    /**
     * Load the workload at sample tasks until a fraction of the workload is loaded
     */
    public fun sampleByLoad(fraction: Double): List<TaskSpec> {
        val workload = reScheduleTasks(this.load())

        if (fraction >= 1.0) {
            return workload
        }

        if (fraction <= 0.0) {
            throw Error("The fraction of tasks to load cannot be 0.0 or lower")
        }

        val res = mutableListOf<TaskSpec>()

        val totalLoad = workload.sumOf { it.totalLoad }
        val desiredLoad = totalLoad * fraction
        var currentLoad = 0.0

        while (currentLoad < desiredLoad) {
            val entry = workload.random()
            res += entry

            currentLoad += entry.totalLoad
        }

        logger.info { "Sampled ${workload.size} VMs (fraction $fraction) into subset of ${res.size} VMs" }

        return res.sortedBy { it.submissionTime.toMsLong() }
    }
}
