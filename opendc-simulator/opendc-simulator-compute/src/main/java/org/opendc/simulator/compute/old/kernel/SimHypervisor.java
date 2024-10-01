/*
 * Copyright (c) 2022 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.compute.old.kernel;

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import org.opendc.simulator.compute.old.SimAbstractMachine;
import org.opendc.simulator.compute.old.SimAbstractMachineContext;
import org.opendc.simulator.compute.old.SimMachine;
import org.opendc.simulator.compute.old.SimMachineContext;
import org.opendc.simulator.compute.old.kernel.cpufreq.ScalingPolicyImpl;
import org.opendc.simulator.compute.old.memory.Memory;
import org.opendc.simulator.compute.old.memory.SimMemory;
import org.opendc.simulator.compute.old.cpu.SimProcessingUnit;
import org.opendc.simulator.compute.old.kernel.cpufreq.ScalingGovernor;
import org.opendc.simulator.compute.old.kernel.cpufreq.ScalingGovernorFactory;
import org.opendc.simulator.compute.old.kernel.interference.VmInterferenceDomain;
import org.opendc.simulator.compute.old.model.CpuModel;
import org.opendc.simulator.compute.old.model.MachineModel;
import org.opendc.simulator.compute.old.workload.SimWorkload;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.FlowStageLogic;
import org.opendc.simulator.flow2.InHandler;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.OutHandler;
import org.opendc.simulator.flow2.OutPort;
import org.opendc.simulator.flow2.mux.FlowMultiplexer;
import org.opendc.simulator.flow2.mux.FlowMultiplexerFactory;

/**
 * A SimHypervisor facilitates the execution of multiple concurrent {@link SimWorkload}s, while acting as a single
 * workload to another {@link SimMachine}.
 */
public final class SimHypervisor implements SimWorkload {
    private final FlowMultiplexerFactory muxFactory;
    private final ScalingGovernorFactory scalingGovernorFactory;

    private SimHyperVisorContext activeContext;
    private final ArrayList<SimVirtualMachine> vms = new ArrayList<>();
    private final HvCounters counters = new HvCounters();

    @Override
    public void setOffset(long now) {}

    /**
     * Construct a {@link SimHypervisor} instance.
     *
     * @param muxFactory The factory for the {@link FlowMultiplexer} to multiplex the workloads.
     * @param random A randomness generator for the interference calculations.
     * @param scalingGovernorFactory The factory for the scaling governor to use for scaling the CPU frequency.
     * @param interferenceDomain The interference domain to which the hypervisor belongs.
     */
    private SimHypervisor(
            FlowMultiplexerFactory muxFactory,
            SplittableRandom random,
            ScalingGovernorFactory scalingGovernorFactory,
            VmInterferenceDomain interferenceDomain) {
        this.muxFactory = muxFactory;
        this.scalingGovernorFactory = scalingGovernorFactory;
    }

    /**
     * Create a {@link SimHypervisor} instance.
     *
     * @param muxFactory The factory for the {@link FlowMultiplexer} to multiplex the workloads.
     * @param random A randomness generator for the interference calculations.
     * @param scalingGovernorFactory The factory for the scaling governor to use for scaling the CPU frequency.
     * @param interferenceDomain The interference domain to which the hypervisor belongs.
     */
    public static SimHypervisor create(
            FlowMultiplexerFactory muxFactory,
            SplittableRandom random,
            ScalingGovernorFactory scalingGovernorFactory,
            VmInterferenceDomain interferenceDomain) {
        return new SimHypervisor(muxFactory, random, scalingGovernorFactory, interferenceDomain);
    }

    /**
     * Create a {@link SimHypervisor} instance with a default interference domain.
     *
     * @param muxFactory The factory for the {@link FlowMultiplexer} to multiplex the workloads.
     * @param random A randomness generator for the interference calculations.
     * @param scalingGovernorFactory The factory for the scaling governor to use for scaling the CPU frequency.
     */
    public static SimHypervisor create(
            FlowMultiplexerFactory muxFactory, SplittableRandom random, ScalingGovernorFactory scalingGovernorFactory) {
        return create(muxFactory, random, scalingGovernorFactory, new VmInterferenceDomain());
    }

    /**
     * Create a {@link SimHypervisor} instance with a default interference domain and scaling governor.
     *
     * @param muxFactory The factory for the {@link FlowMultiplexer} to multiplex the workloads.
     * @param random A randomness generator for the interference calculations.
     */
    public static SimHypervisor create(FlowMultiplexerFactory muxFactory, SplittableRandom random) {
        return create(muxFactory, random, null);
    }

    /**
     * Return the performance counters of the hypervisor.
     */
    public SimHypervisorCounters getCounters() {
        return counters;
    }

    /**
     * Return the virtual machines running on this hypervisor.
     */
    public List<? extends SimVirtualMachine> getVirtualMachines() {
        return Collections.unmodifiableList(vms);
    }

    /**
     * Create a {@link SimVirtualMachine} instance on which users may run a [SimWorkload].
     *
     * @param model The machine to create.
     */
    public SimVirtualMachine newMachine(MachineModel model) {
        if (!canFit(model)) {
            throw new IllegalArgumentException("Machine does not fit");
        }

        SimVirtualMachine vm = new SimVirtualMachine(model);
        vms.add(vm);
        return vm;
    }

    /**
     * Remove the specified <code>machine</code> from the hypervisor.
     *
     * @param machine The machine to remove.
     */
    public void removeMachine(SimVirtualMachine machine) {
        if (vms.remove(machine)) {
            // This cast must always succeed, since `_vms` only contains `VirtualMachine` types.
            machine.close();
        }
    }

    /**
     * Return the CPU capacity of the hypervisor in MHz.
     */
    public double getCpuCapacity() {
        final SimHyperVisorContext context = activeContext;

        if (context == null) {
            return 0.0;
        }

        return context.previousCapacity;
    }

    /**
     * The CPU demand of the hypervisor in MHz.
     */
    public double getCpuDemand() {
        final SimHyperVisorContext context = activeContext;

        if (context == null) {
            return 0.0;
        }

        return context.previousDemand;
    }

    /**
     * The CPU usage of the hypervisor in MHz.
     */
    public double getCpuUsage() {
        final SimHyperVisorContext context = activeContext;

        if (context == null) {
            return 0.0;
        }

        return context.previousRate;
    }

    /**
     * Determine whether the specified machine characterized by <code>model</code> can fit on this hypervisor at this
     * moment.
     */
    public boolean canFit(MachineModel model) {
        final SimHyperVisorContext context = activeContext;
        if (context == null) {
            return false;
        }

        final FlowMultiplexer multiplexer = context.multiplexer;
        return (multiplexer.getMaxInputs() - multiplexer.getInputCount()) >= 1;
    }

    @Override
    public void onStart(SimMachineContext ctx) {
        final SimHyperVisorContext context =
                new SimHyperVisorContext(ctx, muxFactory, scalingGovernorFactory, counters);
        context.start();
        activeContext = context;
    }

    @Override
    public void onStop(SimMachineContext ctx) {
        final SimHyperVisorContext context = activeContext;
        if (context != null) {
            activeContext = null;
            context.stop();
        }
    }

    @Override
    public void makeSnapshot(long now) {
        throw new UnsupportedOperationException("Unable to snapshot hypervisor");
    }

    @Override
    public SimWorkload getSnapshot() {
        throw new UnsupportedOperationException("Unable to snapshot hypervisor");
    }

    @Override
    public void createCheckpointModel() {
        throw new UnsupportedOperationException("Unable to create a checkpointing system for a hypervisor");
    }

    @Override
    public long getCheckpointInterval() {
        return -1;
    }

    @Override
    public long getCheckpointDuration() {
        return -1;
    }

    @Override
    public double getCheckpointIntervalScaling() {
        return -1;
    }

    /**
     * The context which carries the state when the hypervisor is running on a machine.
     */
    private static final class SimHyperVisorContext implements FlowStageLogic {
        private final SimMachineContext machineContext;
        private final FlowMultiplexer multiplexer;
        private final FlowStage stage;
        private final ScalingGovernor scalingGovernor;
        private final InstantSource clock;
        private final HvCounters counters;

        private long lastCounterUpdate;
        private final double d;
        private float previousDemand;
        private float previousRate;
        private float previousCapacity;

        private SimHyperVisorContext(
                SimMachineContext machineContext,
                FlowMultiplexerFactory muxFactory,
                ScalingGovernorFactory scalingGovernorFactory,
                HvCounters counters) {

            this.machineContext = machineContext;
            this.counters = counters;

            final FlowGraph graph = machineContext.getGraph();
            this.multiplexer = muxFactory.newMultiplexer(graph);
            this.stage = graph.newStage(this);
            this.clock = graph.getEngine().getClock();

            this.lastCounterUpdate = clock.millis();

            final SimProcessingUnit cpu = machineContext.getCpu();

            if (scalingGovernorFactory != null) {
                this.scalingGovernor = scalingGovernorFactory.newGovernor(new ScalingPolicyImpl(cpu));
            } else {
                this.scalingGovernor = null;
            }

            this.d = 1 / cpu.getFrequency();
        }

        /**
         * Start the hypervisor on a new machine.
         */
        void start() {
            final FlowGraph graph = machineContext.getGraph();
            final FlowMultiplexer multiplexer = this.multiplexer;

            graph.connect(multiplexer.newOutPort(), machineContext.getCpu().getInput());

            if (this.scalingGovernor != null) {
                this.scalingGovernor.onStart();
            }
        }

        /**
         * Stop the hypervisor.
         */
        void stop() {
            // Synchronize the counters before stopping the hypervisor. Otherwise, the last report is missed.
            updateCounters(clock.millis());

            stage.close();
        }

        /**
         * Invalidate the {@link FlowStage} of the hypervisor.
         */
        void invalidate() {
            stage.invalidate();
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
                final HvCounters counters = this.counters;

                float demand = previousDemand;
                float rate = previousRate;
                float capacity = previousCapacity;

                final double factor = this.d * delta;

                counters.cpuActiveTime += Math.round(rate * factor);
                counters.cpuIdleTime += Math.round((capacity - rate) * factor);
                counters.cpuStealTime += Math.round((demand - rate) * factor);
            }
        }

        /**
         * Update the performance counters of the hypervisor.
         */
        void updateCounters() {
            updateCounters(clock.millis());
        }

        @Override
        public long onUpdate(FlowStage ctx, long now) {
            updateCounters(now);

            final FlowMultiplexer multiplexer = this.multiplexer;
            final ScalingGovernor scalingGovernors = this.scalingGovernor;

            float demand = multiplexer.getDemand();
            float rate = multiplexer.getRate();
            float capacity = multiplexer.getCapacity();

            this.previousDemand = demand;
            this.previousRate = rate;
            this.previousCapacity = capacity;

            double load = rate / Math.min(1.0, capacity);

            if (scalingGovernor != null) {
                scalingGovernor.onLimit(load);
            }

            return Long.MAX_VALUE;
        }
    }

    /**
     * A virtual machine running on the hypervisor.
     */
    public class SimVirtualMachine extends SimAbstractMachine {
        private boolean isClosed;
        private VmCounters vmCounters = new VmCounters(this);

        private SimVirtualMachine(MachineModel model) {
            super(model);
        }

        public SimHypervisorCounters getVmCounters() {
            return vmCounters;
        }

        public double getCpuDemand() {
            final VmContext context = (VmContext) getActiveContext();

            if (context == null) {
                return 0.0;
            }

            return context.previousDemand;
        }

        public double getCpuUsage() {
            final VmContext context = (VmContext) getActiveContext();

            if (context == null) {
                return 0.0;
            }

            return context.usage;
        }

        public double getCpuCapacity() {
            final VmContext context = (VmContext) getActiveContext();

            if (context == null) {
                return 0.0;
            }

            return context.previousCapacity;
        }

        @Override
        protected VmContext createContext(
                SimWorkload workload, Map<String, Object> meta, Consumer<Exception> completion) {
            if (isClosed) {
                throw new IllegalStateException("Virtual machine does not exist anymore");
            }

            final SimHyperVisorContext context = activeContext;
            if (context == null) {
                throw new IllegalStateException("Hypervisor is inactive");
            }

            return new VmContext(
                    context,
                    this,
                    vmCounters,
                    workload,
                    meta,
                    completion);
        }

        @Override
        public SimAbstractMachineContext getActiveContext() {
            return super.getActiveContext();
        }

        void close() {
            if (isClosed) {
                return;
            }

            this.vmCounters.cancel();
            this.vmCounters = null;

            isClosed = true;
            cancel();
        }
    }

    /**
     * A {@link SimAbstractMachineContext} for a virtual machine instance.
     */
    private static final class VmContext extends SimAbstractMachineContext
            implements FlowStageLogic {
        private final SimHyperVisorContext simHyperVisorContext;
        private VmCounters vmCounters;
        private final FlowStage stage;
        private final FlowMultiplexer cpuMux;
        private final InstantSource clock;

        private final VCpu vCpu;
        private final Memory memory;

        private InPort[] muxInPorts;
        private long lastUpdate;
        private long lastCounterUpdate;
        private final double d;

        private float demand;
        private float usage;
        private float capacity;

        private float previousDemand;
        private float previousCapacity;

        private VmContext(
                SimHyperVisorContext simHyperVisorContext,
                SimVirtualMachine machine,
                VmCounters vmCounters,
                SimWorkload workload,
                Map<String, Object> meta,
                Consumer<Exception> completion) {
            super(machine, workload, meta, completion);

            this.simHyperVisorContext = simHyperVisorContext;
            this.vmCounters = vmCounters;
            this.clock = simHyperVisorContext.clock;

            final FlowGraph graph = simHyperVisorContext.machineContext.getGraph();
            final FlowStage stage = graph.newStage(this);
            this.stage = stage;
            this.lastUpdate = clock.millis();
            this.lastCounterUpdate = clock.millis();

            final FlowMultiplexer multiplexer = simHyperVisorContext.multiplexer;
            this.cpuMux = multiplexer;

            final MachineModel model = machine.getMachineModel();
            final CpuModel cpuModel = model.getCpu();

            this.muxInPorts = new InPort[1];

            this.muxInPorts[0] = multiplexer.newInput();

            final InPort cpuInPort = stage.getInPort("cpu");
            final OutPort muxOutPort = stage.getOutPort("mux");

            final Handler handler = new Handler(this, cpuInPort, muxOutPort);
            cpuInPort.setHandler(handler);
            muxOutPort.setHandler(handler);

            this.vCpu = new VCpu(cpuModel, cpuInPort);

            graph.connect(muxOutPort, this.muxInPorts[0]);

            this.d = 1 / cpuModel.getTotalCapacity();

            this.memory = new Memory(graph, model.getMemory());
        }

        /**
         * Update the performance counters of the virtual machine.
         *
         * @param now The timestamp at which to update the counter.
         */
        void updateCounters(long now) {
            long lastUpdate = this.lastCounterUpdate;
            this.lastCounterUpdate = now;
            long delta = now - lastUpdate; // time between updates

            if (delta > 0) {
                final VmCounters counters = this.vmCounters;

                float demand = this.previousDemand;
                float rate = this.usage;
                float capacity = this.previousCapacity;

                final double factor = this.d * delta; // time between divided by total capacity
                final double active = rate * factor;

                counters.cpuActiveTime += Math.round(active);
                counters.cpuIdleTime += Math.round((capacity - rate) * factor);
                counters.cpuStealTime += Math.round((demand - rate) * factor);
            }
        }

        void removeCounters() {
            this.vmCounters.cancel();
            this.vmCounters = null;
        }

        /**
         * Update the performance counters of the virtual machine.
         */
        void updateCounters() {
            updateCounters(clock.millis());
        }

        @Override
        public FlowGraph getGraph() {
            return stage.getGraph();
        }

        @Override
        public SimProcessingUnit getCpu() {
            return vCpu;
        }

        @Override
        public SimMemory getMemory() {
            return memory;
        }

        @Override
        public long onUpdate(FlowStage ctx, long now) {
            float usage = 0.f;
            for (InPort InPort : muxInPorts) {
                usage += InPort.getRate();
            }
            this.usage = usage;
            this.previousDemand = this.demand;
            this.previousCapacity = this.capacity;

            this.updateCounters(now);
            this.lastUpdate = now;

            // Invalidate the FlowStage of the hypervisor to update its counters (via onUpdate)
            simHyperVisorContext.invalidate();

            return Long.MAX_VALUE;
        }

        @Override
        protected void doCancel() {
            super.doCancel();

            // Synchronize the counters before stopping the hypervisor. Otherwise, the last report is missed.
            updateCounters(clock.millis());
            removeCounters();

            stage.close();

            final FlowMultiplexer multiplexer = this.cpuMux;
            for (InPort muxInPort : muxInPorts) {
                multiplexer.releaseInput(muxInPort);
            }
            muxInPorts = new InPort[0];
        }
    }

    /**
     * A {@link SimProcessingUnit} of a virtual machine.
     */
    private static final class VCpu implements SimProcessingUnit {
        private final CpuModel model;
        private final InPort input;

        private VCpu(CpuModel model, InPort input) {
            this.model = model;
            this.input = input;

            input.pull((float) model.getTotalCapacity());
        }

        @Override
        public double getFrequency() {
            return input.getCapacity();
        }

        @Override
        public void setFrequency(double frequency) {
            input.pull((float) frequency);
        }

        @Override
        public double getPowerDraw() {
            return 0;
        }

        @Override
        public double getEnergyUsage() {
            return 0;
        }

        @Override
        public double getDemand() {
            return input.getDemand();
        }

        @Override
        public double getSpeed() {
            return input.getRate();
        }

        @Override
        public CpuModel getCpuModel() {
            return model;
        }

        @Override
        public InPort getInput() {
            return input;
        }

        @Override
        public String toString() {
            return "SimHypervisor.VCpu[model" + model + "]";
        }

        @Override
        public long onUpdate(FlowStage ctx, long now) {
            return 0;
        }
    }

    /**
     * A handler for forwarding flow between an InPort and OutPort.
     */
    private static class Handler implements InHandler, OutHandler {
        private final InPort input;
        private final OutPort output;
        private final VmContext context;

        private Handler(VmContext context, InPort input, OutPort output) {
            this.context = context;
            this.input = input;
            this.output = output;
        }

        @Override
        public void onPush(InPort port, float demand) {
            context.demand += -port.getDemand() + demand;

            output.push(demand);
        }

        @Override
        public void onUpstreamFinish(InPort port, Throwable cause) {
            context.demand -= port.getDemand();

            output.push(0.f);
        }

        @Override
        public float getRate(InPort port) {
            return output.getRate();
        }

        @Override
        public void onPull(OutPort port, float capacity) {
            context.capacity += -port.getCapacity() + capacity;

            input.pull(capacity);
        }

        @Override
        public void onDownstreamFinish(OutPort port, Throwable cause) {
            context.capacity -= port.getCapacity();

            input.pull(0.f);
        }
    }

    /**
     * Implementation of {@link SimHypervisorCounters} for the hypervisor.
     */
    private class HvCounters implements SimHypervisorCounters {
        private long cpuActiveTime;
        private long cpuIdleTime;
        private long cpuStealTime;
        private long cpuLostTime;

        @Override
        public long getCpuActiveTime() {
            return cpuActiveTime;
        }

        @Override
        public long getCpuIdleTime() {
            return cpuIdleTime;
        }

        @Override
        public long getCpuStealTime() {
            return cpuStealTime;
        }

        @Override
        public long getCpuLostTime() {
            return cpuLostTime;
        }

        @Override
        public void sync() {
            final SimHyperVisorContext context = activeContext;

            if (context != null) {
                context.updateCounters();
            }
        }
    }

    /**
     * Implementation of {@link SimHypervisorCounters} for the virtual machine.
     */
    private static class VmCounters implements SimHypervisorCounters {
        private SimVirtualMachine vm;
        private long cpuActiveTime;
        private long cpuIdleTime;
        private long cpuStealTime;
        private long cpuLostTime;

        private VmCounters(SimVirtualMachine vm) {
            this.vm = vm;
        }

        @Override
        public long getCpuActiveTime() {
            return cpuActiveTime;
        }

        @Override
        public long getCpuIdleTime() {
            return cpuIdleTime;
        }

        @Override
        public long getCpuStealTime() {
            return cpuStealTime;
        }

        @Override
        public long getCpuLostTime() {
            return cpuLostTime;
        }

        @Override
        public void sync() {
            final VmContext context = (VmContext) vm.getActiveContext();

            if (context != null) {
                context.updateCounters();
            }
        }

        public void cancel() {
            this.vm = null;
        }
    }
}
