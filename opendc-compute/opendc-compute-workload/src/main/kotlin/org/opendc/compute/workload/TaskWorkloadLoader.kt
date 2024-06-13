package org.opendc.compute.workload

import org.opendc.trace.Trace
import org.opendc.trace.conv.TABLE_RESOURCES
import org.opendc.trace.conv.resourceCpuCapacity
import org.opendc.trace.conv.resourceCpuCount
import org.opendc.trace.conv.resourceID
import org.opendc.trace.conv.resourceMemCapacity
import org.opendc.trace.conv.resourceStartTime
import org.opendc.trace.conv.resourceStopTime
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong

public class TaskWorkloadLoader : ComputeWorkloadLoaderNew() {
    override fun get(pathToFolder: File): List<VirtualMachine> {
        val trace = Trace.open(pathToFolder, "opendc-vm")

        val vms = parseTaskMeta(trace)

        return vms
    }

    /**
     * Read the metadata into a workload.
     */
    private fun parseTaskMeta(
        trace: Trace
    ): List<VirtualMachine> {
        val reader = checkNotNull(trace.getTable(TABLE_RESOURCES)).newReader()

        val idCol = reader.resolve(resourceID)
        val startTimeCol = reader.resolve(resourceStartTime)
        val stopTimeCol = reader.resolve(resourceStopTime)
        val cpuCountCol = reader.resolve(resourceCpuCount)
        val cpuCapacityCol = reader.resolve(resourceCpuCapacity)
        val memCol = reader.resolve(resourceMemCapacity)

        var counter = 0
        val entries = mutableListOf<VirtualMachine>()

        return try {
            while (reader.nextRow()) {
                val id = reader.getString(idCol)!!

                val submissionTime = reader.getInstant(startTimeCol)!!
                val endTime: Instant = reader.getInstant(stopTimeCol)!!
                val cpuCount = reader.getInt(cpuCountCol)
                val cpuCapacity = reader.getDouble(cpuCapacityCol)
                val memCapacity = reader.getDouble(memCol) / 1000.0 // Convert from KB to MB
                val uid = UUID.nameUUIDFromBytes("$id-${counter++}".toByteArray())

                val duration = Duration.between(submissionTime, endTime).toMillis()
                val totalLoad = (cpuCapacity * duration) / 1000.0 // avg MHz * duration = MFLOPs

                entries.add(
                    VirtualMachine(
                        uid,
                        id,
                        cpuCount,
                        cpuCapacity,
                        memCapacity.roundToLong(),
                        totalLoad,
                        submissionTime,
                        endTime
                    ),
                )
            }

            // Make sure the virtual machines are ordered by start time
            entries.sortBy { it.startTime }

            entries
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            reader.close()
        }
    }
}
