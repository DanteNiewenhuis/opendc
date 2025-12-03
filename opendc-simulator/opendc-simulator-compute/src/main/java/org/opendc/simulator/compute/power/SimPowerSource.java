/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.compute.power;

import java.util.List;
import java.util.Map;
import org.opendc.common.ResourceType;
import org.opendc.simulator.compute.cpu.SimCpu;
import org.opendc.simulator.compute.power.carbon.CarbonFragments.CarbonEBAFragment;
import org.opendc.simulator.compute.power.carbon.CarbonModel;
import org.opendc.simulator.compute.power.carbon.CarbonReceiver;
import org.opendc.simulator.engine.engine.FlowEngine;
import org.opendc.simulator.engine.graph.FlowEdge;
import org.opendc.simulator.engine.graph.FlowNode;
import org.opendc.simulator.engine.graph.FlowSupplier;

/**
 * A {@link SimPsu} implementation that estimates the power consumption based on CPU usage.
 */
public final class SimPowerSource extends FlowNode implements FlowSupplier, CarbonReceiver {
    private long lastUpdate;

    private double powerDemand = 0.0;
    private double powerSupplied = 0.0;
    private double totalEnergyUsage = 0.0;

    private double carbonIntensity = 0.0;
    private double totalCarbonEmission = 0.0;

    private FlowEdge distributorEdge;

    private final double capacity;

    private CarbonModel carbonModel = null;

    private final String name;
    private final String clusterName;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Getters and Setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Determine whether the InPort is connected to a {@link SimCpu}.
     *
     * @return <code>true</code> if the InPort is connected to an OutPort, <code>false</code> otherwise.
     */
    public boolean isConnected() {
        return distributorEdge != null;
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
    public double getPowerDraw() {
        return this.powerSupplied;
    }

    public double getCarbonIntensity() {
        return this.carbonIntensity;
    }

    /**
     * Return the cumulated energy usage of the machine (in J) measured at the InPort of the powers supply.
     */
    public double getEnergyUsage() {
        return totalEnergyUsage;
    }

    public double getCarbonEmission() {
        return this.totalCarbonEmission;
    }

    public double getWndEnergyUsed() {
        return wndEnergyUsed;
    }

    public double getWndCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (wndEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getSunEnergyUsed() {
        return sunEnergyUsed;
    }

    public double getSunCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (sunEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getWatEnergyUsed() {
        return watEnergyUsed;
    }

    public double getWatCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (watEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getOilEnergyUsed() {
        return oilEnergyUsed;
    }

    public double getOilCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (oilEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getNgEnergyUsed() {
        return ngEnergyUsed;
    }

    public double getNgCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (ngEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getColEnergyUsed() {
        return colEnergyUsed;
    }

    public double getColCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (colEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getNucEnergyUsed() {
        return nucEnergyUsed;
    }

    public double getNucCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (nucEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getOthEnergyUsed() {
        return othEnergyUsed;
    }

    public double getOthCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (othEnergyUsed / totalEnergyUsage) * 100.0;
    }

    public double getWndSunEnergyUsed() {
        return wndSunEnergyUsed;
    }

    public double getWndSunCoverage() {
        if (totalEnergyUsage == 0.0) {
            return 0.0;
        }
        return (wndSunEnergyUsed / totalEnergyUsage) * 100.0;
    }

    @Override
    public double getCapacity() {
        return this.capacity;
    }

    public String getName() {
        return name;
    }

    public String getClusterName() {
        return clusterName;
    }

    public Map<Integer, Long> getAvailablePowers() {
        if (this.carbonModel != null) {
            return this.carbonModel.getAvailablePowers();
        } else {
            return Map.of();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimPowerSource(FlowEngine engine, double max_capacity, String name, String clusterName) {
        super(engine);

        this.capacity = max_capacity;

        lastUpdate = this.clock.millis();

        this.name = name;
        this.clusterName = clusterName;
    }

    public void close() {
        if (this.carbonModel != null) {
            this.carbonModel.close();
        }

        this.closeNode();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowNode related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long onUpdate(long now) {
        return Long.MAX_VALUE;
    }

    private double wndEnergyUsed = 0.0;
    private double sunEnergyUsed = 0.0;
    private double watEnergyUsed = 0.0;
    private double oilEnergyUsed = 0.0;
    private double ngEnergyUsed = 0.0;
    private double colEnergyUsed = 0.0;
    private double nucEnergyUsed = 0.0;
    private double othEnergyUsed = 0.0;

    private double wndSunEnergyUsed = 0.0;

    private void updateCoverage(long passedTime) {
        Map<Integer, Long> availablePowers = getAvailablePowers();
        if (availablePowers.isEmpty()) {
            return;
        }

        double maxWndPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.WND.ordinal(), 0L);
        double wndPower = Math.min(this.powerSupplied, maxWndPower);

        double maxSunPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.SUN.ordinal(), 0L);
        double sunPower = Math.min(this.powerSupplied, maxSunPower);

        double maxWatPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.WAT.ordinal(), 0L);
        double watPower = Math.min(this.powerSupplied, maxWatPower);

        double maxOilPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.OIL.ordinal(), 0L);
        double oilPower = Math.min(this.powerSupplied, maxOilPower);

        double maxNgPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.NG.ordinal(), 0L);
        double ngPower = Math.min(this.powerSupplied, maxNgPower);

        double maxColPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.COL.ordinal(), 0L);
        double colPower = Math.min(this.powerSupplied, maxColPower);

        double maxNucPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.NUC.ordinal(), 0L);
        double nucPower = Math.min(this.powerSupplied, maxNucPower);

        double maxOthPower = (double) availablePowers.getOrDefault(CarbonEBAFragment.PowerSourceType.OTH.ordinal(), 0L);
        double othPower = Math.min(this.powerSupplied, maxOthPower);

        double maxWndSunPower = maxWndPower + maxSunPower;
        double wndSunPower = Math.min(this.powerSupplied, maxWndSunPower);

        wndEnergyUsed += (wndPower * (passedTime * 0.001)); // in Wh
        sunEnergyUsed += (sunPower * (passedTime * 0.001)); // in Wh
        watEnergyUsed += (watPower * (passedTime * 0.001)); // in Wh
        oilEnergyUsed += (oilPower * (passedTime * 0.001)); // in Wh
        ngEnergyUsed += (ngPower * (passedTime * 0.001)); // in Wh
        colEnergyUsed += (colPower * (passedTime * 0.001)); // in Wh
        nucEnergyUsed += (nucPower * (passedTime * 0.001)); // in Wh
        othEnergyUsed += (othPower * (passedTime * 0.001)); // in Wh

        wndSunEnergyUsed += (wndSunPower * (passedTime * 0.001)); // in Wh
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

        long passedTime = now - lastUpdate;
        if (passedTime > 0) {
            double energyUsage = (this.powerSupplied * passedTime * 0.001);

            updateCoverage(passedTime);

            // Compute the energy usage of the machine
            this.totalEnergyUsage += energyUsage;
            this.totalCarbonEmission += this.carbonIntensity * (energyUsage / 3600000.0);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowGraph Related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleIncomingDemand(FlowEdge consumerEdge, double newPowerDemand) {
        this.powerDemand = newPowerDemand;

        double powerSupply = this.powerDemand;

        if (powerSupply != this.powerSupplied) {
            this.pushOutgoingSupply(this.distributorEdge, powerSupply);
        }
    }

    @Override
    public void pushOutgoingSupply(FlowEdge consumerEdge, double newSupply) {
        updateCounters();

        this.powerSupplied = newSupply;
        consumerEdge.pushSupply(newSupply);
    }

    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.distributorEdge = consumerEdge;
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        this.distributorEdge = null;
    }

    // Update the carbon intensity of the power source
    public void updateCarbonIntensity(double carbonIntensity) {
        this.updateCounters();
        this.carbonIntensity = carbonIntensity;
    }

    @Override
    public void setCarbonModel(CarbonModel carbonModel) {
        this.carbonModel = carbonModel;
    }

    @Override
    public void removeCarbonModel(CarbonModel carbonModel) {
        this.carbonModel = null;
    }

    @Override
    public Map<FlowEdge.NodeType, List<FlowEdge>> getConnectedEdges() {
        List<FlowEdge> supplierEdges = this.distributorEdge != null ? List.of(this.distributorEdge) : List.of();

        return Map.of(FlowEdge.NodeType.SUPPLYING, supplierEdges);
    }

    @Override
    public ResourceType getSupplierResourceType() {
        return ResourceType.POWER;
    }
}
