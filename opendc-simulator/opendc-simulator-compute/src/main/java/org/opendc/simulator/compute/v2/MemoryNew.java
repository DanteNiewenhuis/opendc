package org.opendc.simulator.compute.v2;

import org.opendc.simulator.compute.old.model.MemoryUnit;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow3.engine.FlowGraphNew;

/**
 * The [SimMemory] implementation for a machine.
 */
public final class MemoryNew {
//    private final SimpleFlowSink sink;
    private final MemoryUnit memoryUnit;

    public MemoryNew(FlowGraphNew graph, MemoryUnit memoryUnit) {

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

    public InPort getInput() {
//        return sink.getInput();
        return null;
    }

    @Override
    public String toString() {
        return "SimAbstractMachine.Memory";
    }
}
