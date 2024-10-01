package org.opendc.simulator.compute.old.power;

import org.jetbrains.annotations.NotNull;
import org.opendc.simulator.compute.old.model.CpuModel;
import org.opendc.simulator.compute.old.cpu.CpuPowerModel;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.FlowStageLogic;
import org.opendc.simulator.flow2.InHandler;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.OutPort;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.power.SimPowerOutPort;

import java.time.InstantSource;

/**
 * A {@link SimplePsu} implementation that estimates the power consumption based on CPU usage.
 */
public final class SimplePsu implements FlowStageLogic{
    private final FlowStage stage;
    private final OutPort out;
    SimPowerOutPort OutPort;

    private final CpuPowerModel model;
    private final InstantSource clock;

    private double targetFreq;
    private double totalUsage;
    private long lastUpdate;

    private double powerDraw;
    private double energyUsage;

    private FlowEdge cpuEdge;

    private final InHandler handler = new InHandler() {
        @Override
        public void onPush(InPort port, float demand) {
            totalUsage += -port.getDemand() + demand;
        }

        @Override
        public void onUpstreamFinish(InPort port, Throwable cause) {
            totalUsage -= port.getDemand();
        }
    };

    public SimplePsu(FlowGraph graph, CpuPowerModel model) {
        this.stage = graph.newStage(this);
        this.model = model;
        this.clock = graph.getEngine().getClock();
        this.out = stage.getOutPort("out");
        this.out.setMask(true);

        lastUpdate = graph.getEngine().getClock().millis();
    }

    /**
     * Determine whether the InPort is connected to a {@link SimPowerOutPort}.
     *
     * @return <code>true</code> if the InPort is connected to an OutPort, <code>false</code> otherwise.
     */
    public boolean isConnected() {
        return OutPort != null;
    }

    /**
     * Return the {@link SimPowerOutPort} to which the InPort is connected.
     */
    public SimPowerOutPort getOutPort() {
        return OutPort;
    }

    /**
     * Return the power demand of the machine (in W) measured in the PSU.
     * <p>
     * This method provides access to the power consumption of the machine before PSU losses are applied.
     */
    public double getPowerDemand() {
        return totalUsage;
    }

    /**
     * Return the instantaneous power usage of the machine (in W) measured at the InPort of the power supply.
     */
    public double getPowerDraw() {
        return powerDraw;
    }

    /**
     * Return the cumulated energy usage of the machine (in J) measured at the InPort of the powers supply.
     */
    public double getEnergyUsage() {
        updateEnergyUsage(clock.millis());
        return energyUsage;
    }

    /**
     * Return an {@link InPort} that converts processing demand (in MHz) into energy demand (J) for the specified CPU
     * <code>model</code>.
     *
     * @param id The unique identifier of the CPU for this machine.
     * @param model The details of the processing unit.
     */
    public InPort getCpuPower(int id, CpuModel model) {
        targetFreq += model.getTotalCapacity();

        final InPort port = stage.getInPort("cpu" + id);
        port.setHandler(handler);
        return port;
    }

    /**
     * This method is invoked when the CPU frequency is changed for the specified <code>port</code>.
     *
     * @param port The {@link InPort} for which the capacity is changed.
     * @param capacity The capacity to change to.
     */
    public void setCpuFrequency(InPort port, double capacity) {
        targetFreq += -port.getCapacity() + capacity;

        port.pull((float) capacity);
    }

    public long onUpdate(FlowStage ctx, long now) {
        updateEnergyUsage(now);

        double usage = model.computePower(totalUsage / targetFreq);
        out.push((float) usage);
        powerDraw = usage;

        return Long.MAX_VALUE;
    }

    @NotNull
    public OutPort getFlowOutPort() {
        return out;
    }

    /**
     * Calculate the energy usage up until <code>now</code>.
     */
    private void updateEnergyUsage(long now) {
        long lastUpdate = this.lastUpdate;
        this.lastUpdate = now;

        long duration = now - lastUpdate;
        if (duration > 0) {
            // Compute the energy usage of the machine
            energyUsage += powerDraw * duration * 0.001;
        }
    }
}
