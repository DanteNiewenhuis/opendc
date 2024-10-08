package org.opendc.simulator.compute.memory;

import org.opendc.simulator.compute.models.MemoryUnit;
import org.opendc.simulator.engine.FlowGraphNew;

/**
 * The [SimMemory] implementation for a machine.
 */
public final class Memory {
//    private final SimpleFlowSink sink;
    private final MemoryUnit memoryUnit;

    public Memory(FlowGraphNew graph, MemoryUnit memoryUnit) {

        this.memoryUnit = memoryUnit;
        // TODO: Fix this
//        this.sink = new SimpleFlowSink(graph, (float) memoryUnit.getSize());
    }

    public double getCapacity() {
//        return sink.getCapacity();
        return 0.0f;
    }

    public MemoryUnit getMemoryUnit() {
        return memoryUnit;
    }

    @Override
    public String toString() {
        return "SimAbstractMachine.Memory";
    }
}
