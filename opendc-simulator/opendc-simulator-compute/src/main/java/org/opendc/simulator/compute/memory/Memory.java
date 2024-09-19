package org.opendc.simulator.compute.memory;

import org.opendc.simulator.compute.model.MemoryUnit;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.sink.SimpleFlowSink;

/**
 * The [SimMemory] implementation for a machine.
 */
public final class Memory implements SimMemory {
    private final SimpleFlowSink sink;
    private final MemoryUnit memoryUnit;

    public Memory(FlowGraph graph, MemoryUnit memoryUnit) {

        this.memoryUnit = memoryUnit;

        this.sink = new SimpleFlowSink(graph, (float) memoryUnit.getSize());
    }

    @Override
    public double getCapacity() {
        return sink.getCapacity();
    }

    @Override
    public MemoryUnit getMemoryUnit() {
        return memoryUnit;
    }

    @Override
    public InPort getInput() {
        return sink.getInput();
    }

    @Override
    public String toString() {
        return "SimAbstractMachine.Memory";
    }
}
