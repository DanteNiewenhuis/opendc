package org.opendc.compute.workload.greenifier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.compute.workload.VirtualMachine
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

public class GreenifierWorkloadLoader(private val baseDir: File) {

    private fun readWorkloadFile(): GreenifierWorkloadSpec {
        return Json.decodeFromStream<GreenifierWorkloadSpec>(baseDir.resolve("workload.json").inputStream())
    }

    public fun loadWorkload():  List<VirtualMachine> {
        val workloadSpec = readWorkloadFile()

        val vms = mutableListOf<VirtualMachine>();

        var counter = 0;
        for (task in workloadSpec.tasks) {
            val id = task.id
            val uid = UUID.nameUUIDFromBytes("$id-${counter++}".toByteArray())

            val cpuCount = task.cpuCount
            val cpuCapacity = task.cpuUsage.toMHz()
            val memCapacity = task.memCapacity.toMB()

            val ldt: LocalDateTime = LocalDateTime.of(LocalDate.parse(task.submissionTime), LocalTime.of(0, 0, 0))
            val submissionTime: Instant = ldt.atZone(ZoneId.systemDefault()).toInstant()

            val dependencies = task.dependencies

            val duration = task.runTimes.entries.iterator().next().value // gets the first runTime
            val totalLoad = (cpuCapacity * duration) / 1000.0 // avg MHz * duration = MFLOPs

            val dependenciesString = dependencies.map{it.toString()}
            val endTime = submissionTime.plusMillis(duration)
            vms.add(
                VirtualMachine(
                    uid,
                    "$id",
                    cpuCount,
                    cpuCapacity,
                    memCapacity.toLong(),
                    totalLoad,
                    submissionTime,
                    endTime,
                    dependencies = dependenciesString,
                    durations = task.runTimes
                )
            )
        }

        return vms
    }
}
