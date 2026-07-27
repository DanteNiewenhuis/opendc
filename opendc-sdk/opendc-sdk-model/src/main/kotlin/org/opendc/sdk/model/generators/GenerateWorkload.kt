package org.opendc.sdk.model.generators

import org.opendc.common.units.DataSize
import org.opendc.common.units.Frequency
import org.opendc.common.units.TimeDelta
import org.opendc.sdk.model.workload.TaskSpec
import org.opendc.sdk.model.workload.toTaskWorkload
import org.opendc.simulator.compute.workload.trace.TaskFragment
import java.time.LocalDateTime
import java.time.ZoneOffset

public fun generateWorkload(numTasks: Int) : List<TaskSpec> {
    val submissionTime = "2022-01-01T00:00:00"
    val submitMs = LocalDateTime.parse(submissionTime).toInstant(ZoneOffset.UTC).toEpochMilli()
    val taskDuration = 10 * 60 * 1000
    val cpuUsage = 1000
    val gpuUsage = 0.0

    val workload = mutableListOf<TaskSpec>()
    for (i in 0 until numTasks) {
        val fragments = ArrayList<TaskFragment>()
        fragments += TaskFragment(taskDuration.toLong(), cpuUsage.toDouble(), gpuUsage)
        val totalLoad = fragments.sumOf { it.cpuUsage() * (it.duration() / 3_600_000.0) }
        workload.add(TaskSpec(
            id = i,
            name = "$i",
            submissionTime = TimeDelta.ofMillis(submitMs),
            duration = TimeDelta.ofMillis(taskDuration),
            cpuCoreCount = 1,
            cpuCapacity = Frequency.ofMHz(1000),
            totalLoad = totalLoad,
            memory = DataSize.ofMiB(10000.0),
            workload = fragments.toTaskWorkload(i),
        ))
    }

    return workload
}
