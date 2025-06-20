package org.opendc.compute.workload.greenifier

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataSize
import org.opendc.common.units.Energy
import org.opendc.common.units.Frequency

/**
 * specification describing a workload
 */
@Serializable
public data class GreenifierWorkloadSpec (
    val tasks: List<TaskSpec>
)

@Serializable
public data class TaskSpec(
    val name: String,
    val id: Int,
    val cpuCount: Int,
    val cpuUsage: Frequency,
    val dependencies: List<Int>,
    val memCapacity: DataSize,
    val energyConsumption: Map<String, Energy>,
    val runTimes: Map<String, Long>,
    val submissionTime: String
)
