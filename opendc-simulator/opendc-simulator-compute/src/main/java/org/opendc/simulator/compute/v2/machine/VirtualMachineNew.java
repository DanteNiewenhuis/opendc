package org.opendc.simulator.compute.v2.machine;

import org.opendc.simulator.compute.old.model.MachineModel;
import org.opendc.simulator.compute.v2.SimProcessingUnitNew;
import org.opendc.simulator.compute.v2.workload.SimWorkloadNew;
import org.opendc.simulator.compute.v2.workload.Workload;
import org.opendc.simulator.flow3.engine.FlowConsumer;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;
import org.opendc.simulator.flow3.engine.FlowSupplier;

import java.time.InstantSource;

/*
   A virtual Machine created to run a single workload
 */
public class VirtualMachineNew extends FlowNode implements FlowConsumer, FlowSupplier {
    private SimMachineNew machine;
    private FlowGraphNew graph;
    private InstantSource clock;

    private SimWorkloadNew activeWorkload;

    private long lastUpdate;
    private long lastCounterUpdate;
    private final double d;

    private FlowEdge cpuEdge; // The edge to the cpu
    private FlowEdge workloadEdge; // The edge to the workload

    private float cpuDemand;
    private float cpuSupply;
    private float cpuCapacity;

    private PerformanceCounters performanceCounters = new PerformanceCounters();


    public VirtualMachineNew(
        SimMachineNew machine,
        MachineModel model
    ) {
        super(machine.getGraph());
        this.machine = machine;
        this.clock = this.machine.getClock();

        this.graph = machine.getGraph();
        this.graph.addEdge(this, this.machine.getCpuMux());

        this.lastUpdate = clock.millis();
        this.lastCounterUpdate = clock.millis();

        this.d = 1 / machine.getCpu().getFrequency();
    }

    public void startWorkload(Workload workload) {
        this.activeWorkload = workload.onStart(this, this.clock.millis());
    }

    public float getDemand() {
        return cpuDemand;
    }

    public void setDemand(float demand) {
        this.cpuDemand = demand;
    }

    public float getCpuCapacity() {
        return cpuCapacity;
    }

    public void setCpuCapacity(float cpuCapacity) {
        this.cpuCapacity = cpuCapacity;
    }

    public FlowGraphNew getGraph() {
        return this.graph;
    }

    public SimProcessingUnitNew getCpu() {
        return machine.getCpu();
    }

    public void updateCounters(long now) {
        long lastUpdate = this.lastCounterUpdate;
        this.lastCounterUpdate = now;
        long delta = now - lastUpdate;

        if (delta > 0) {
            final double factor = this.d * delta;

            this.performanceCounters.addCpuActiveTime(Math.round(this.cpuSupply * factor));
            this.performanceCounters.setCpuIdleTime(Math.round((this.cpuCapacity - this.cpuSupply) * factor));
            this.performanceCounters.addCpuStealTime(Math.round((this.cpuDemand - this.cpuSupply) * factor));
        }

        this.performanceCounters.setCpuDemand(this.cpuDemand);
        this.performanceCounters.setCpuSupply(this.cpuSupply);
        this.performanceCounters.setCpuCapacity(this.cpuCapacity);
    }


    @Override
    public long onUpdate(long now) {
        updateCounters(now);

        return Long.MAX_VALUE;
    }

    /**
     * Add an edge to the workload
     * TODO: maybe add a check if there is already an edge
     */
    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.workloadEdge = consumerEdge;
    }

    /**
     * Add an edge to the cpuMux
     * TODO: maybe add a check if there is already an edge
     */
    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.cpuEdge = supplierEdge;
    }

    /**
     * Push demand to the cpuMux if the demand has changed
     **/
    @Override
    public void pushDemand(FlowEdge supplierEdge, float newDemand) {
        supplierEdge.pushDemand(newDemand);
    }

    /**
     * Push supply to the workload if the supply has changed
     **/
    @Override
    public void pushSupply(FlowEdge consumerEdge, float newSupply) {
        if (this.cpuSupply == newSupply) {
            return;
        }

        cpuEdge.pushDemand(newSupply);
    }

    /**
     * Handle new demand from the workload by sending it through to the cpuMux
     **/
    @Override
    public void handleDemand(FlowEdge consumerEdge, float newDemand) {
        if (this.cpuDemand == newDemand) {
            return;
        }

        updateCounters(this.clock.millis());
        this.cpuDemand = newDemand;

        pushDemand(this.cpuEdge, newDemand);
    }

    /**
     * Handle a new supply pushed by the cpuMux by sending it through to the workload
     **/
    @Override
    public void handleSupply(FlowEdge supplierEdge, float newSupply) {
        if (this.cpuSupply == this.cpuSupply) {
            return;
        }

        updateCounters(this.clock.millis());
        this.cpuSupply = newSupply;

        pushSupply(this.workloadEdge, newSupply);
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
//        this.updateCounters(this.clock.millis());
        this.close();
    }

    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.close();
    }
}
