package org.opendc.sdk.model.workload

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.opendc.common.units.DataSize
import org.opendc.common.units.Frequency
import org.opendc.common.units.TimeDelta
import org.opendc.simulator.compute.workload.trace.TaskFragment

/**
 * A [KSerializer] for the engine-native [TaskFragment] record.
 *
 * [TaskFragment] is a plain Java record, so the `kotlinx.serialization` compiler plugin can never
 * generate a serializer for it directly (it only processes Kotlin classes). This serializer lets
 * [TaskFragment] be embedded directly in `@Serializable` SDK model types (e.g. [TaskSpec]) so the
 * same fragment instances used by the simulation engine can also be (de)serialized, without keeping
 * a separate, parallel SDK-side representation around in memory.
 *
 * The wire format goes through [TimeDelta]/[Frequency]/[DataSize] (the same types [TaskFragmentSpec]
 * used to expose) so hand-authored JSON can still use human-friendly units (`"10 minutes"`,
 * `"1 GHz"`); only the in-memory representation is the raw-primitive [TaskFragment].
 */
public object TaskFragmentSerializer : KSerializer<TaskFragment> {
    @Serializable
    private data class Surrogate(
        val duration: TimeDelta,
        val cpuUsage: Frequency,
        val gpuUsage: Frequency = Frequency.ofMHz(0),
        val gpuMemoryUsage: DataSize = DataSize.ofBytes(0),
    )

    override val descriptor: SerialDescriptor = Surrogate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: TaskFragment,
    ) {
        encoder.encodeSerializableValue(
            Surrogate.serializer(),
            Surrogate(
                TimeDelta.ofMillis(value.duration()),
                Frequency.ofMHz(value.cpuUsage()),
                Frequency.ofMHz(value.gpuUsage()),
                DataSize.ofMiB(value.gpuMemoryUsage().toDouble()),
            ),
        )
    }

    override fun deserialize(decoder: Decoder): TaskFragment {
        val surrogate = decoder.decodeSerializableValue(Surrogate.serializer())
        return TaskFragment(
            surrogate.duration.toMsLong(),
            surrogate.cpuUsage.toMHz(),
            surrogate.gpuUsage.toMHz(),
            surrogate.gpuMemoryUsage.toMiB().toInt(),
        )
    }
}
