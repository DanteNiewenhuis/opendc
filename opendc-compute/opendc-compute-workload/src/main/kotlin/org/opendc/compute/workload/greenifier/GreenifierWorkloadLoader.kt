package org.opendc.compute.workload.greenifier

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opendc.compute.workload.Task
import org.opendc.compute.workload.WorkloadLoader
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

public class GreenifierWorkloadLoader(private val baseDir: File): WorkloadLoader() {

    @OptIn(ExperimentalSerializationApi::class)
    private fun readWorkloadFile(): GreenifierWorkloadSpec {
        return Json.decodeFromStream<GreenifierWorkloadSpec>(baseDir.resolve("workload.json").inputStream())
    }

    override fun load(): List<Task> {

        val workloadSpec = readWorkloadFile()

        println(workloadSpec)

        val vms = mutableListOf<Task>();

        var counter = 0;
        for (task in workloadSpec.tasks) {
            val id = task.id
            val uid = UUID.nameUUIDFromBytes("$id-${counter++}".toByteArray())

            val cpuCount = task.cpuCount
            val cpuCapacity = task.cpuUsage.toMHz()
            val memCapacity = task.memCapacity.toMB()

            val ldt: LocalDateTime = LocalDateTime.of(LocalDate.parse(task.submissionTime), LocalTime.of(0, 0, 0))
            val submissionTime: Long = ldt.atZone(ZoneId.systemDefault()).toInstant()!!.toEpochMilli()

            val dependencies = task.dependencies

            val duration = task.runTimes.entries.iterator().next().value // gets the first runTime
            val totalLoad = (cpuCapacity * duration) / 1000.0 // avg MHz * duration = MFLOPs

            val dependenciesString = dependencies.map{it.toString()}
            vms.add(
                Task(
                    uid,
                    "$id",
                    cpuCount,
                    cpuCapacity,
                    memCapacity.toLong(),
                    totalLoad,
                    submissionTime,
                    duration = duration,
                    durations = task.runTimes,
                    deadline = submissionTime,
                    nature = "",
                    dependencies = dependenciesString
                )
            )
        }

        return vms
    }
}
