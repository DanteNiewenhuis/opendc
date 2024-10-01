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

package org.opendc.simulator.compute.old;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import org.opendc.simulator.compute.old.cpu.SimCpu;
import org.opendc.simulator.compute.old.memory.Memory;
import org.opendc.simulator.compute.old.model.MachineModel;
import org.opendc.simulator.compute.old.cpu.CpuPowerModel;
import org.opendc.simulator.compute.old.power.SimplePsu;
import org.opendc.simulator.compute.old.workload.SimWorkload;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.mux.MaxMinFlowMultiplexer;

/**
 * A simulated bare-metal machine that is able to run a single workload.
 *
 * <p>
 * A {@link SimBareMetalMachine} is a stateful object, and you should be careful when operating this object concurrently. For
 * example, the class expects only a single concurrent call to {@link #startWorkload(SimWorkload, Map, Consumer)} )}.
 */
public final class SimBareMetalMachine extends SimAbstractMachine {
    /**
     * The {@link FlowGraph} in which the simulation takes places.
     */
    private final FlowGraph graph;

    /**
     * The {@link SimplePsu} of this bare metal machine.
     */
    private final SimplePsu psu;

    private final MaxMinFlowMultiplexer cpuMux;

    /**
     * The resources of this machine.
     */
    private final SimCpu cpu;

    private final Memory memory;

    private final ArrayList<SimWorkload> workloads = new ArrayList<>();

    /**
     * Construct a {@link SimBareMetalMachine} instance.
     *
     * @param graph The {@link FlowGraph} to which the machine belongs.
     * @param model The machine model to simulate.
     * @param cpuPowerModel The {@link CpuPowerModel} to construct the power supply of the machine.
     */
    private SimBareMetalMachine(FlowGraph graph, MachineModel model, CpuPowerModel cpuPowerModel) {
        super(model);

        this.graph = graph;
        this.psu = new SimplePsu(graph, cpuPowerModel);

        this.cpu = new SimCpu(graph, psu, model.getCpu(), 0);
        this.memory = new Memory(graph, model.getMemory());

        this.cpuMux = new MaxMinFlowMultiplexer(graph);
    }

    /**
     * Create a {@link SimBareMetalMachine} instance.
     *
     * @param graph The {@link FlowGraph} to which the machine belongs.
     * @param model The machine model to simulate.
     * @param cpuPowerModel The {@link CpuPowerModel} to construct the power supply of the machine.
     */
    public static SimBareMetalMachine create(FlowGraph graph, MachineModel model, CpuPowerModel cpuPowerModel) {
        return new SimBareMetalMachine(graph, model, cpuPowerModel);
    }

    public FlowGraph getGraph() {
        return graph;
    }

    /**
     * Return the {@link SimplePsu} belonging to this bare metal machine.
     */
    public SimplePsu getPsu() {
        return psu;
    }

    public SimCpu getCpu() {
        return cpu;
    }

    public Memory getMemory() {
        return memory;
    }

    /**
     * Return the CPU capacity of the machine in MHz.
     */
    public double getCpuCapacity() {
        final SimBareMetalMachineContext context = (SimBareMetalMachineContext) getActiveContext();

        if (context == null) {
            return 0.0;
        }

        return cpu.getFrequency();
    }

    public void addWorkload(SimWorkload simWorkload) {
//        simWorkload.addCpu();

        this.workloads.add(simWorkload);
    }

    /**
     * The CPU demand of the machine in MHz.
     */
    public double getCpuDemand() {
        final SimBareMetalMachineContext context = (SimBareMetalMachineContext) getActiveContext();

        if (context == null) {
            return 0.0;
        }

        return cpu.getDemand();
    }

    /**
     * The CPU usage of the machine in MHz.
     */
    public double getCpuUsage() {
        final SimBareMetalMachineContext context = (SimBareMetalMachineContext) getActiveContext();

        if (context == null) {
            return 0.0;
        }

        return cpu.getSpeed();
    }

    @Override
    protected SimBareMetalMachineContext createContext(
            SimWorkload workload, Map<String, Object> meta, Consumer<Exception> completion) {
        return new SimBareMetalMachineContext(this, workload, meta, completion);
    }

}
