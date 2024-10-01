package org.opendc.simulator.compute.v2.machine;

import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow3.Multiplexer;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;

import java.time.InstantSource;

public class SimMachineContextNew extends FlowNode {
    private SimMachineNew machine;
    private Multiplexer cpuMux;
    private FlowGraphNew graph;
    private FlowStage stage;
    private InstantSource clock;

    private long lastCounterUpdate;
    private final double cpuFrequencyInv;
    private float cpuDemand;
    private float cpuSupply;
    private float cpuCapacity;

    private PerformanceCounters performanceCounters;

    public SimMachineContextNew(SimMachineNew machine) {
        super(machine.getGraph());
        this.machine = machine;
        this.graph = machine.getGraph();

        this.clock = machine.getGraph().getEngine().getClock();
        this.cpuMux = machine.getCpuMux();

        this.performanceCounters = new PerformanceCounters();
        this.lastCounterUpdate = clock.millis();

        // TODO: check if this links correctly
        this.cpuFrequencyInv = 1 / machine.getCpu().getFrequency();
    }

    public long onUpdate(long now) {
        updateCounters(now);

        this.cpuDemand = cpuMux.getTotalDemand();
        this.cpuSupply = cpuMux.getTotalSupply();
        this.cpuCapacity = cpuMux.getCapacity();

        return Long.MAX_VALUE;
    }

    /**
     * Update the performance counters of the hypervisor.
     *
     * @param now The timestamp at which to update the counter.
     */
    void updateCounters(long now) {
        long lastUpdate = this.lastCounterUpdate;
        this.lastCounterUpdate = now;
        long delta = now - lastUpdate;

        if (delta > 0) {
            float demand = this.cpuDemand;
            float rate = this.cpuSupply;
            float capacity = this.cpuCapacity;

            final double factor = this.cpuFrequencyInv * delta;

            this.performanceCounters.addCpuActiveTime(Math.round(rate * factor));
            this.performanceCounters.setCpuIdleTime(Math.round((capacity - rate) * factor));
            this.performanceCounters.addCpuStealTime(Math.round((demand - rate) * factor));
        }

        this.performanceCounters.setCpuDemand(this.cpuDemand);
        this.performanceCounters.setCpuSupply(this.cpuSupply);
        this.performanceCounters.setCpuCapacity(this.cpuCapacity);
    }

    void stop() {
        updateCounters(clock.millis());

        this.stage.close();
        this.stage = null;

        this.machine = null;
        this.cpuMux = null;
        this.performanceCounters = null;
        this.graph = null;
        this.clock = null;
    }
}
