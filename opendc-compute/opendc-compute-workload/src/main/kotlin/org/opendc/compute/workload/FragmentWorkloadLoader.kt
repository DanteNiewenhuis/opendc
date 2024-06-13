package org.opendc.compute.workload

import org.opendc.simulator.compute.workload.SimTrace
import org.opendc.trace.Trace
import org.opendc.trace.conv.TABLE_RESOURCES
import org.opendc.trace.conv.TABLE_RESOURCE_STATES
import org.opendc.trace.conv.resourceCpuCapacity
import org.opendc.trace.conv.resourceCpuCount
import org.opendc.trace.conv.resourceID
import org.opendc.trace.conv.resourceMemCapacity
import org.opendc.trace.conv.resourceStartTime
import org.opendc.trace.conv.resourceStateCpuUsage
import org.opendc.trace.conv.resourceStateDuration
import org.opendc.trace.conv.resourceStateTimestamp
import org.opendc.trace.conv.resourceStopTime
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong

public class FragmentWorkloadLoader : ComputeWorkloadLoaderNew() {
    override fun get(pathToFolder: File): List<VirtualMachine> {
        val trace = Trace.open(pathToFolder, "opendc-vm")

        val fragments = parseTaskFragments(trace)

        val vms = parseTaskMeta(trace, fragments)

        return vms
    }

    /**
     * Read the fragments into memory.
     */
    private fun parseTaskFragments(trace: Trace): Map<String, Builder> {
        val reader = checkNotNull(trace.getTable(TABLE_RESOURCE_STATES)).newReader()

        val idCol = reader.resolve(resourceID)
        val timestampCol = reader.resolve(resourceStateTimestamp)
        val durationCol = reader.resolve(resourceStateDuration)
        val coresCol = reader.resolve(resourceCpuCount)
        val usageCol = reader.resolve(resourceStateCpuUsage)

        val fragments = mutableMapOf<String, Builder>()

        return try {
            while (reader.nextRow()) {
                val id = reader.getString(idCol)!!
                val time = reader.getInstant(timestampCol)!!
                val durationMs = reader.getDuration(durationCol)!!
                val cores = reader.getInt(coresCol)
                val cpuUsage = reader.getDouble(usageCol)

                val builder = fragments.computeIfAbsent(id) { Builder() }
                builder.add(time, durationMs, cpuUsage, cores)
            }

            fragments
        } finally {
            reader.close()
        }
    }

    /**
     * Read the metadata into a workload.
     */
    private fun parseTaskMeta(
        trace: Trace,
        fragments: Map<String, Builder>,
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
                if (!fragments.containsKey(id)) {
                    continue
                }

                val submissionTime = reader.getInstant(startTimeCol)!!
                val endTime = reader.getInstant(stopTimeCol)!!
                val cpuCount = reader.getInt(cpuCountCol)
                val cpuCapacity = reader.getDouble(cpuCapacityCol)
                val memCapacity = reader.getDouble(memCol) / 1000.0 // Convert from KB to MB
                val uid = UUID.nameUUIDFromBytes("$id-${counter++}".toByteArray())

                val builder = fragments.getValue(id) // Get all fragments related to this VM
                val totalLoad = builder.totalLoad

                entries.add(
                    VirtualMachine(
                        uid,
                        id,
                        cpuCount,
                        cpuCapacity,
                        memCapacity.roundToLong(),
                        totalLoad,
                        submissionTime,
                        endTime,
                        trace = builder.build()
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

    /**
     * A builder for a VM trace.
     */
    private class Builder {
        /**
         * The total load of the trace.
         */
        @JvmField
        var totalLoad: Double = 0.0

        /**
         * The internal builder for the trace.
         */
        private val builder = SimTrace.builder()

        /**
         * The deadline of the previous fragment.
         */
        private var previousDeadline = Long.MIN_VALUE

        /**
         * Add a fragment to the trace.
         *
         * @param timestamp Timestamp at which the fragment starts (in epoch millis).
         * @param deadline Timestamp at which the fragment ends (in epoch millis).
         * @param usage CPU usage of this fragment.
         * @param cores Number of cores used.
         */
        fun add(
            deadline: Instant,
            duration: Duration,
            usage: Double,
            cores: Int,
        ) {
            val startTimeMs = (deadline - duration).toEpochMilli()
            totalLoad += (usage * duration.toMillis()) / 1000.0 // avg MHz * duration = MFLOPs

            if ((startTimeMs != previousDeadline) && (previousDeadline != Long.MIN_VALUE)) {
                // There is a gap between the previous and current fragment; fill the gap
                builder.add(startTimeMs, 0.0, cores)
            }

            builder.add(deadline.toEpochMilli(), usage, cores)
            previousDeadline = deadline.toEpochMilli()
        }

        /**
         * Build the trace.
         */
        fun build(): SimTrace = builder.build()
    }
}

