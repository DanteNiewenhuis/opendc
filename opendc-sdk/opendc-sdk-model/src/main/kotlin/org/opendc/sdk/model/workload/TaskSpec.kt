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

package org.opendc.sdk.model.workload

import kotlinx.serialization.Serializable
import org.opendc.common.units.DataSize
import org.opendc.common.units.Frequency
import org.opendc.common.units.TimeDelta
import org.opendc.compute.api.TaskDescription
import org.opendc.sdk.model.checkpoint.CheckpointModelSpec
import org.opendc.sdk.model.validation.Validatable
import org.opendc.sdk.model.validation.ValidationIssue
import org.opendc.simulator.compute.workload.trace.TaskFragment
import org.opendc.simulator.compute.workload.trace.TaskWorkload

/**
 * A schedulable unit of work with its resource requirements and execution profile.
 *
 * @property id Unique identifier of the task within its workload.
 * @property name Human-readable name of the task.
 * @property submissionTime Offset from the workload start at which the task is submitted.
 * @property duration Total wall-clock duration of the task.
 * @property cpuCoreCount Number of CPU cores the task requires.
 * @property cpuCapacity Per-core CPU capacity the task requires.
 * @property memory Amount of main memory the task requires.
 * @property gpuCoreCount Number of GPU cores the task requires.
 * @property gpuCapacity Per-core GPU capacity the task requires.
 * @property gpuMemory Amount of GPU memory the task requires.
 * @property workload The engine-native carrier of the task's ordered execution slices, describing its
 * resource demand over time.
 * @property deferrable Whether the task may be postponed by a time-shifting scheduler.
 * @property deadline Optional latest completion time, as an offset from the workload start.
 * @property parents Identifiers of tasks that must complete before this task may start.
 * @property children Identifiers of tasks that depend on this task.
 */
@Serializable
public data class TaskSpec(
    public override val id: Int,
    public override val name: String? = "",
    public val submissionTime: TimeDelta,
    public val duration: TimeDelta,
    public override val cpuCoreCount: Int,
    public val cpuCapacity: Frequency,
    public val totalLoad: Double, // TODO: See what custom unit this should be, and if this is needed.
    public val memory: DataSize,
    public override val gpuCoreCount: Int = 0,
    public val gpuCapacity: Frequency = Frequency.ofMHz(0),
    public val gpuMemory: DataSize = DataSize.ofBytes(0),
    public val workload: @Serializable(with = TaskWorkloadSerializer::class) TaskWorkload,
    public override val deferrable: Boolean = false,
    public var deadline: TimeDelta? = null, // TODO: look at the nullable unit -> can add a significant amount of RAM overhead.
    public val parents: Set<Int>? = emptySet(),
    public val children: Set<Int>? = emptySet(),
) : Validatable, TaskDescription {
    // TaskDescription: exposes the plain-primitive representations ServiceTask needs, so it can
    // hold a TaskDescription reference and delegate to it instead of duplicating these fields.
    override val submissionTimeMs: Long get() = submissionTime.toMsLong()
    override val durationMs: Long get() = duration.toMsLong()
    override val cpuCapacityMHz: Double get() = cpuCapacity.toMHz()
    override val memoryMiB: Long get() = memory.toMiB().toLong()
    override val gpuCapacityMHz: Double get() = gpuCapacity.toMHz()
    override val gpuMemoryMiB: Long get() = gpuMemory.toMiB().toLong()
    override val deadlineMs: Long get() = deadline?.toMsLong() ?: -1L
    override val childrenIds: Set<Int> get() = children ?: emptySet()

    override fun validate(): List<ValidationIssue> =
        buildList {
            if (cpuCoreCount <= 0) add(ValidationIssue("cpuCoreCount", "must be greater than zero"))
            if (cpuCapacity <= Frequency.zero) add(ValidationIssue("cpuCapacity", "must be greater than zero"))
            if (workload.fragments.isEmpty()) add(ValidationIssue("fragments", "must not be empty"))
        }

    // TaskWorkload is a plain (mutable, identity-equals) engine class, so the generated data-class
    // equals/hashCode - which would compare `workload` by reference - are overridden to compare it by
    // its fragments instead, the only part of it that is meaningful outside of a single run.
    override fun equals(other: Any?): Boolean = other is TaskSpec && snapshot() == other.snapshot()

    override fun hashCode(): Int = snapshot().hashCode()

    private fun snapshot() =
        Snapshot(
            id, name, submissionTime, duration, cpuCoreCount, cpuCapacity, totalLoad, memory,
            gpuCoreCount, gpuCapacity, gpuMemory, workload.fragments, deferrable, deadline, parents, children,
        )

    public fun setCheckpointInterval(intervalMs: Long) {
        this.workload.setCheckpointInterval(intervalMs)
    }

    public fun setCheckpointDuration(duration: Long) {
        this.workload.setCheckpointDuration(duration)
    }

    public fun setCheckpointIntervalScaling(scaling: Double) {
        this.workload.setCheckpointIntervalScaling(scaling)
    }

    public fun setCheckpointModel(checkpointModel: CheckpointModelSpec?) {
        if (checkpointModel == null) {
            return
        }

        this.setCheckpointInterval(checkpointModel.interval.toMsLong())
        this.setCheckpointDuration(checkpointModel.duration.toMsLong())
        this.setCheckpointIntervalScaling(checkpointModel.intervalScaling)

    }

    private data class Snapshot(
        val id: Int,
        val name: String?,
        val submissionTime: TimeDelta,
        val duration: TimeDelta,
        val cpuCoreCount: Int,
        val cpuCapacity: Frequency,
        val totalLoad: Double,
        val memory: DataSize,
        val gpuCoreCount: Int,
        val gpuCapacity: Frequency,
        val gpuMemory: DataSize,
        val fragments: List<TaskFragment>,
        val deferrable: Boolean,
        var deadline: TimeDelta?,
        val parents: Set<Int>?,
        val children: Set<Int>?,
    )
}
