package org.opendc.simulator.compute.v2.machine;

import org.opendc.simulator.compute.old.SimMachineContext;
import org.opendc.simulator.compute.old.cpu.CpuPowerModel;
import org.opendc.simulator.compute.old.memory.SimMemory;
import org.opendc.simulator.compute.old.model.MachineModel;
import org.opendc.simulator.compute.old.workload.SimWorkload;
import org.opendc.simulator.compute.v2.MemoryNew;
import org.opendc.simulator.compute.v2.SimCpuNew;
import org.opendc.simulator.compute.v2.SimplePsuNew;
import org.opendc.simulator.flow3.Multiplexer;
import org.opendc.simulator.flow3.engine.FlowGraphNew;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * A machine that is able to execute {@link SimWorkload} objects.
 */
public class SimMachineNew {
    private final MachineModel machineModel;

    private final ArrayList<VirtualMachineNew> virtualMachines = new ArrayList<>();

    private final FlowGraphNew graph;

    private final InstantSource clock;

    private final SimCpuNew cpu;
    private final Multiplexer cpuMux;
    private final SimplePsuNew psu;
    private final MemoryNew memory;

    private float cpuDemand;
    private float cpuSupply;
    private float cpuCapacity;

    private PerformanceCounters performanceCounters = new PerformanceCounters();
    private long lastCounterUpdate;
    private final double cpuFrequencyInv;

    private Consumer<Exception> completion;

    public SimMachineNew(FlowGraphNew graph, MachineModel machineModel, CpuPowerModel cpuPowerModel, Consumer<Exception> completion) {
        this.graph = graph;
        this.machineModel = machineModel;
        this.clock = graph.getEngine().getClock();

        // Create the psu and cpu and connect them
        this.psu = new SimplePsuNew(graph, cpuPowerModel);
        this.cpu = new SimCpuNew(graph, this.machineModel.getCpu(), 0);

        graph.addEdge(this.cpu, this.psu);

        this.memory = new MemoryNew(graph, this.machineModel.getMemory());

        // Create a Multiplexer and add the cpu as supplier
        this.cpuMux = new Multiplexer(this.graph);
        graph.addEdge(this.cpuMux, this.cpu);

        this.cpuFrequencyInv = 1 / machineModel.getCpu().getTotalCapacity();
        this.completion = completion;
    }

    public PerformanceCounters getPerformanceCounters() {
        return performanceCounters;
    }

    /**
     * Return the model of the machine containing its specifications.
     */
    public MachineModel getMachineModel() {
        return machineModel;
    }

    public ArrayList<VirtualMachineNew> getVirtualMachines() {
        return virtualMachines;
    }

    public FlowGraphNew getGraph() {
        return graph;
    }

    public InstantSource getClock() {
        return clock;
    }

    public SimCpuNew getCpu() {
        return cpu;
    }

    public Multiplexer getCpuMux() {
        return cpuMux;
    }

    public MemoryNew getMemory() {
        return memory;
    }

    public SimplePsuNew getPsu() {
        return psu;
    }

    /**
     * Start the specified {@link SimWorkload} on this machine.
     *
     * @return A {@link SimMachineContext} that represents the execution context for the workload.
     * @throws IllegalStateException if a workload is already active on the machine or if the machine is closed.
     */
    public VirtualMachineNew newMachine(MachineModel model) {
        // TODO: Implement

        final VirtualMachineNew vm = new VirtualMachineNew(this, model);

        virtualMachines.add(vm);

        return vm;
    };

    /**
     * Remove the specified <code>Virtual Machine</code> from the machine.
     *
     * @param machine The machine to remove.
     */
    public void removeMachine(VirtualMachineNew machine) {
        if (virtualMachines.remove(machine)) {
            // This cast must always succeed, since `_vms` only contains `VirtualMachine` types.
            machine.close();
        }
    }

    /**
     * Update the performance counters of the hypervisor.
     * TODO: move this to the CPU
     * @param now The timestamp at which to update the counter.
     */
    public void updateCounters(long now) {
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

    public long onUpdate(long now) {
        updateCounters(now);

        this.cpuDemand = cpuMux.getTotalDemand();
        this.cpuSupply = cpuMux.getTotalSupply();
        this.cpuCapacity = cpuMux.getCapacity();

        return Long.MAX_VALUE;
    }

    /**
     * Cancel the active workloads on this machine (if any).
     */
    public void cancel() {
        for (VirtualMachineNew machine : virtualMachines) {
            removeMachine(machine);
        }
    }

    public void close() {

    }

    /**
     * Determine whether the specified machine characterized by <code>model</code> can fit on this hypervisor at this
     * moment.
     */
    public boolean canFit(MachineModel model) {
        final Multiplexer multiplexer = cpuMux;

        return true;
//        return (multiplexer.getMaxInputs() - multiplexer.getInputCount()) >= 1;
    }

    /**
     * Return the CPU capacity of the hypervisor in MHz.
     */
    public double getCpuCapacity() {
        return 0.0;
    }

    /**
     * The CPU demand of the hypervisor in MHz.
     */
    public double getCpuDemand() {
        return 0.0;
    }

    /**
     * The CPU usage of the hypervisor in MHz.
     */
    public double getCpuUsage() {
        return 0.0;
    }
}
