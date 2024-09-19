package org.opendc.simulator.compute;

import org.opendc.simulator.compute.cpu.SimCpu;
import org.opendc.simulator.compute.memory.Memory;
import org.opendc.simulator.compute.memory.SimMemory;
import org.opendc.simulator.compute.workload.SimWorkload;
import org.opendc.simulator.flow2.FlowGraph;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The execution context for a {@link SimBareMetalMachine}.
 */
public final class SimBareMetalMachineContext extends SimAbstractMachineContext {
    private final FlowGraph graph;
    private final SimCpu cpu;
    private final Memory memory;

    public SimBareMetalMachineContext(
        SimBareMetalMachine machine,
        SimWorkload workload,
        Map<String, Object> meta,
        Consumer<Exception> completion) {
        super(machine, workload, meta, completion);

        this.graph = machine.getGraph();
        this.cpu = machine.getCpu();
        this.memory = machine.getMemory();
    }

    @Override
    public FlowGraph getGraph() {
        return graph;
    }

    @Override
    public SimCpu getCpu() {
        return cpu;
    }

    @Override
    public SimMemory getMemory() {
        return memory;
    }
}
