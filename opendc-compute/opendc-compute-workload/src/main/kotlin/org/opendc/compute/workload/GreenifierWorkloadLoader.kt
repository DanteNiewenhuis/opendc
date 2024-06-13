package org.opendc.compute.workload

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToLong

@Serializable
public data class GreenifierWorkloadSpec(
    val tasks: List<GreenifierTaskSpec>,
)

@Serializable
public data class GreenifierTaskSpec(
    val id: Int,
    val name: String,
    val runTimes: Map<String, Long>,
    val cpuUsage: Double,
    val cpuCount: Int,
    val memCapacity: Double,
    val submissionTime: String,
    val dependencies: List<String> = emptyList(),
)

public class GreenifierWorkloadLoader: ComputeWorkloadLoaderNew() {

    override fun get(pathToFolder: File): List<VirtualMachine> {

        val file = pathToFolder.resolve("tasks.json")

        val inputStream = file.inputStream()
        val greenifierWorkload = Json.decodeFromStream<GreenifierWorkloadSpec>(inputStream)

        val vms = mutableListOf<VirtualMachine>();

        var counter = 0;
        for (task in greenifierWorkload.tasks) {
            val id = task.id
            val uid = UUID.nameUUIDFromBytes("$id-${counter++}".toByteArray())

            val cpuCount = task.cpuCount
            val cpuCapacity = task.cpuUsage
            val memCapacity = task.memCapacity

            val ldt: LocalDateTime = LocalDateTime.of(LocalDate.parse(task.submissionTime), LocalTime.of(0, 0, 0))
            val submissionTime: Instant = ldt.atZone(ZoneId.systemDefault()).toInstant()

            val dependencies = task.dependencies

            val duration = task.runTimes.entries.iterator().next().value // gets the first runTime
            val totalLoad = (cpuCapacity * duration) / 1000.0 // avg MHz * duration = MFLOPs

            val endTime = submissionTime.plusMillis(duration)
            vms.add(
                VirtualMachine(
                    uid,
                    "$id",
                    cpuCount,
                    cpuCapacity,
                    memCapacity.roundToLong(),
                    totalLoad,
                    submissionTime,
                    endTime,
                    dependencies = dependencies,
                    durations = task.runTimes
                )
            )
        }

        return vms
    }

}
