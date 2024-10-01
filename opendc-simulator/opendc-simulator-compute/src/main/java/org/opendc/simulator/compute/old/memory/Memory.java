package org.opendc.simulator.compute.old.memory;

import org.opendc.simulator.compute.old.model.MemoryUnit;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.sink.SimpleFlowSink;
import org.opendc.simulator.flow3.engine.FlowGraphNew;

/**
 * The [SimMemory] implementation for a machine.
 */
public final class Memory implements SimMemory {
    private final SimpleFlowSink sink;
    private final MemoryUnit memoryUnit;

    public Memory(FlowGraph graph, MemoryUnit memoryUnit) {

        this.memoryUnit = memoryUnit;
        // TODO: Fix this
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
