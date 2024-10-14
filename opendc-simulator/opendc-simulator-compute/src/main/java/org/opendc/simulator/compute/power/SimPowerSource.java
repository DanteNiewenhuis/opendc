package org.opendc.simulator.compute.power;

import org.opendc.simulator.compute.cpu.SimCpu;
import org.opendc.simulator.engine.FlowEdge;
import org.opendc.simulator.engine.FlowGraphNew;
import org.opendc.simulator.engine.FlowNode;
import org.opendc.simulator.engine.FlowSupplier;

import java.time.InstantSource;

/**
 * A {@link SimPsu} implementation that estimates the power consumption based on CPU usage.
 */
public final class SimPowerSource extends FlowNode implements FlowSupplier {
    private final InstantSource clock;

    private long lastUpdate;

    private float powerDemand = 0.0f;
    private float powerSupplied = 0.0f;
    private float totalEnergyUsage = 0.0f;

    private FlowEdge cpuEdge;

    private float capacity = Long.MAX_VALUE;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Getters and Setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Determine whether the InPort is connected to a {@link SimCpu}.
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
        updateCounters();
        return totalEnergyUsage;
    }

    @Override
    public float getCapacity() {
        return this.capacity;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimPowerSource(FlowGraphNew graph) {
        super(graph);

        this.clock = graph.getEngine().getClock();

        lastUpdate = graph.getEngine().getClock().millis();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowNode related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long onUpdate(long now) {
        updateCounters();
        float powerSupply = this.powerDemand;

        if (powerSupply != this.powerSupplied) {
            this.pushSupply(this.cpuEdge, powerSupply);
        }

        return Long.MAX_VALUE;
    }

    public void updateCounters() {
        updateCounters(clock.millis());
    }

    /**
     * Calculate the energy usage up until <code>now</code>.
     */
    public void updateCounters(long now) {
        long lastUpdate = this.lastUpdate;
        this.lastUpdate = now;

        long duration = now - lastUpdate;
        if (duration > 0) {
            // Compute the energy usage of the machine
            this.totalEnergyUsage += (float) (this.powerSupplied * duration * 0.001);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowGraph Related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


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
}

