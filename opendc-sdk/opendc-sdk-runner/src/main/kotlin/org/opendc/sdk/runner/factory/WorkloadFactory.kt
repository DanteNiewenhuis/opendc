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

package org.opendc.sdk.runner.factory

import org.opendc.compute.simulator.service.ServiceTask
import org.opendc.sdk.model.workload.loader.ComputeWorkloadLoader
import org.opendc.sdk.model.checkpoint.CheckpointModelSpec
import org.opendc.sdk.model.resource.ResourceReference
import org.opendc.sdk.model.workload.InlineWorkloadSpec
import org.opendc.sdk.model.workload.ScalingPolicySpec
import org.opendc.sdk.model.workload.TaskSpec
import org.opendc.sdk.model.workload.TraceWorkloadSpec
import org.opendc.sdk.model.workload.WorkloadSpec
import org.opendc.simulator.compute.workload.trace.scaling.NoDelayScaling
import org.opendc.simulator.compute.workload.trace.scaling.PerfectScaling
import java.nio.file.Path
import org.opendc.simulator.compute.workload.trace.scaling.ScalingPolicy as EngineScalingPolicy

/**
 * Materializes an SDK [WorkloadSpec] into the engine's list of [ServiceTask]s. Trace workloads are
 * loaded from the resource resolved by [resolve]; inline workloads are built in memory.
 */
internal fun WorkloadSpec.getTasks(
    checkpointModel: CheckpointModelSpec?,
    resolve: (ResourceReference) -> Path,
): List<TaskSpec> =
    when (this) {
        is TraceWorkloadSpec -> return loadTrace(resolve(source), checkpointModel)
        is InlineWorkloadSpec -> {
            tasks.forEach{ it.setCheckpointModel(checkpointModel)}
            return tasks
        }
    }

private fun TraceWorkloadSpec.loadTrace(
    path: Path,
    checkpoint: CheckpointModelSpec?,
): List<TaskSpec> =
    ComputeWorkloadLoader(
        path.toFile(),
        submissionTime,
        checkpoint.intervalMs(),
        checkpoint.durationMs(),
        checkpoint.scaling(),
        scalingPolicy.toEngine(),
        deferAll,
    ).sampleByLoad(sampleFraction)

public fun TaskSpec.toServiceTask(): ServiceTask {
    // NOTE: `workload.fragments` here *is* this TaskSpec's own ArrayList - no copy is made.
    // EngineTraceWorkload mutates the list it is given in place as it consumes fragments (it
    // clears/prepends entries), so once this TaskSpec has been materialized into a running
    // ServiceTask, its fragments are no longer a reliable snapshot: they get drained as the
    // simulation progresses, and the same TaskSpec must not be materialized into a second
    // ServiceTask concurrently/afterwards. `workload` itself only carries the checkpoint/scaling
    // placeholders `toTaskWorkload` filled in at construction time, so a fresh, correctly-configured
    // EngineTraceWorkload is still built here from its fragments and resource types.
    //
    // ServiceTask reads its other resource fields (id, name, cpuCapacity, memory, ...) straight
    // from this TaskSpec (passed as a TaskDescription) instead of duplicating them - see
    // ServiceTask.java. `totalLoad()` is still recomputed separately, since it currently uses a
    // different formula than the stored TaskSpec.totalLoad (see ServiceTask's totalCPULoad field).
    return ServiceTask(
        this,
        workload,
        ArrayList(parents),
        totalLoad(),
    )
}


private fun TaskSpec.totalLoad(): Double = workload.fragments.sumOf { it.cpuUsage() * (it.duration() / 3_600_000.0) }

private fun ScalingPolicySpec.toEngine(): EngineScalingPolicy =
    when (this) {
        ScalingPolicySpec.NoDelay -> NoDelayScaling()
        ScalingPolicySpec.Perfect -> PerfectScaling()
    }

private fun CheckpointModelSpec?.intervalMs(): Long = this?.interval?.toMsLong() ?: 0L

private fun CheckpointModelSpec?.durationMs(): Long = this?.duration?.toMsLong() ?: 0L

private fun CheckpointModelSpec?.scaling(): Double = this?.intervalScaling ?: 1.0
