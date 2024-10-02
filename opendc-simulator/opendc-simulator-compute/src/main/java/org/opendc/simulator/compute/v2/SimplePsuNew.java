package org.opendc.simulator.compute.v2;


import org.opendc.simulator.compute.old.model.CpuModel;
import org.opendc.simulator.compute.old.cpu.CpuPowerModel;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.InHandler;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;
import org.opendc.simulator.flow3.engine.FlowSupplier;
import org.opendc.simulator.power.SimPowerOutPort;

import java.time.InstantSource;

/**
 * A {@link org.opendc.simulator.compute.old.power.SimplePsu} implementation that estimates the power consumption based on CPU usage.
 */
public final class SimplePsuNew extends FlowNode implements FlowSupplier {
    private final CpuPowerModel model;
    private final InstantSource clock;

    private float targetFreq;
    private float totalUsage;
    private long lastUpdate;

    private float powerDemand = 0.0f;
    private float powerSupplied = 0.0f;
    private float totalEnergyUsage = 0.0f;

    private FlowEdge cpuEdge;

    private float capacity = Long.MAX_VALUE;

    public SimplePsuNew(FlowGraphNew graph, CpuPowerModel model) {
        super(graph);

        this.model = model;
        this.clock = graph.getEngine().getClock();

        lastUpdate = graph.getEngine().getClock().millis();
    }

    /**
     * Determine whether the InPort is connected to a {@link SimPowerOutPort}.
     *
     * @return <code>true</code> if the InPort is connected to an OutPort, <code>false</code> otherwise.
     */
    public boolean isConnected() {
        return cpuEdge != null;
    }

    /**
     * Return the power demand of the machine (in W) measured in the PSU.
     * <p>
     * This method provides access to the power consumption of the machine before PSU losses are applied.
     */
    public double getPowerDemand() {
        return this.powerDemand;
    }

    /**
     * Return the instantaneous power usage of the machine (in W) measured at the InPort of the power supply.
     */
    public float getPowerDraw() {
        return this.powerSupplied;
    }

    /**
     * Return the cumulated energy usage of the machine (in J) measured at the InPort of the powers supply.
     */
    public float getEnergyUsage() {
        updateEnergyUsage(clock.millis());
        return totalEnergyUsage;
    }

    @Override
    public long onUpdate(long now) {
        this.pushSupply(this.cpuEdge, this.powerDemand);

        updateEnergyUsage(now);

        return Long.MAX_VALUE;
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
            this.totalEnergyUsage += this.powerSupplied * duration * 0.001;
        }
    }

    @Override
    public void handleDemand(FlowEdge consumerEdge, float newPowerDemand) {
        if (newPowerDemand == this.powerDemand) {
            return;
        }

        this.powerDemand = newPowerDemand;
        this.invalidate();
    }

    @Override
    public void pushSupply(FlowEdge consumerEdge, float newSupply) {
        if (newSupply == this.powerSupplied) {
            return;
        }

        this.powerSupplied = newSupply;
        consumerEdge.pushSupply(newSupply);
    }

    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.cpuEdge = consumerEdge;
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        this.cpuEdge = null;
    }

    @Override
    public float getCapacity() {
        return this.capacity;
    }


}
