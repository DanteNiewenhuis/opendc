package org.opendc.sdk.model.workload

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.common.ResourceType
import org.opendc.simulator.compute.workload.trace.TaskFragment
import org.opendc.simulator.compute.workload.trace.TaskWorkload
import org.opendc.simulator.compute.workload.trace.scaling.NoDelayScaling

/**
 * Wraps [fragments] in a [TaskWorkload], the engine-native carrier [TaskSpec] stores directly.
 *
 * The checkpoint interval/duration/scaling and [org.opendc.simulator.compute.workload.trace.scaling.ScalingPolicy]
 * baked into the result are placeholders: those are scenario-scoped and only known once a run
 * assembles the final `ServiceTask` (see `TaskSpec.toServiceTask`), which always builds a fresh,
 * correctly-configured [TaskWorkload] rather than reusing this one directly.
 */
public fun List<TaskFragment>.toTaskWorkload(taskId: Int): TaskWorkload {
    val fragments = this
    val resourceTypes =
        buildList {
            if (fragments.any { it.cpuUsage() > 0.0 }) add(ResourceType.CPU)
            if (fragments.any { it.gpuUsage() > 0.0 }) add(ResourceType.GPU)
        }.toTypedArray()
    return TaskWorkload(ArrayList(fragments), 0L, 0L, 1.0, NoDelayScaling(), taskId, resourceTypes)
}

/**
 * A [KSerializer] for the engine-native [TaskWorkload], embedded directly in [TaskSpec].
 *
 * Only the fragments travel on the wire (as a plain array, matching the pre-existing `"fragments"`
 * format) — the checkpoint/scaling placeholders [toTaskWorkload] fills in are never meaningful
 * outside a single run, so a deserialized [TaskWorkload] gets a placeholder task id of `0`, fixed up
 * for real once `toServiceTask` rebuilds the workload with the task's actual id.
 */
public object TaskWorkloadSerializer : KSerializer<TaskWorkload> {
    private val fragmentListSerializer = ListSerializer(TaskFragmentSerializer)

    override val descriptor: SerialDescriptor = fragmentListSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: TaskWorkload,
    ) {
        encoder.encodeSerializableValue(fragmentListSerializer, value.fragments)
    }

    override fun deserialize(decoder: Decoder): TaskWorkload {
        val fragments = decoder.decodeSerializableValue(fragmentListSerializer)
        return fragments.toTaskWorkload(taskId = 0)
    }
}
