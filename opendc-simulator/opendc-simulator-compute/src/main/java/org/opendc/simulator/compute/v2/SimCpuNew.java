package org.opendc.simulator.compute.v2;


import org.opendc.simulator.compute.old.cpu.CpuPowerModel;
import org.opendc.simulator.compute.old.cpu.CpuPowerModels;
import org.opendc.simulator.compute.old.cpu.SimProcessingUnit;
import org.opendc.simulator.compute.old.model.CpuModel;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow3.engine.FlowConsumer;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;
import org.opendc.simulator.flow3.engine.FlowSupplier;

/**
 * A {@link SimProcessingUnit} of a bare-metal machine.
 */
public final class SimCpuNew extends FlowNode implements SimProcessingUnitNew, FlowSupplier, FlowConsumer {
    private final CpuModel cpuModel;

    private final CpuPowerModel cpuPowerModel;

    private float currentCpuDemand = 0.0f; // cpu capacity demanded by the mux
    private float currentCpuUtilization = 0.0f;
    private float currentPowerDemand = 0.0f; // power demanded of the psu
    private float currentCpuSupplied = 0.0f; // cpu capacity supplied to the mux
    private float currentPowerSupplied = 0.0f; // cpu capacity supplied by the psu

    private float maxCapacity;

    private long lastUpdate;

    private FlowEdge muxEdge;
    private FlowEdge psuEdge;

    public SimCpuNew(FlowGraphNew graph, CpuModel cpuModel, int id) {
        super(graph);
        this.cpuModel = cpuModel;
        this.maxCapacity = (float) this.cpuModel.getTotalCapacity();

        // TODO: connect this to the front-end
        this.cpuPowerModel = CpuPowerModels.linear(400, 200);

        this.lastUpdate = graph.getEngine().getClock().millis();
    }


    @Override
    public double getFrequency() {
        return cpuModel.getTotalCapacity();
    }

    @Override
    public void setFrequency(double frequency) {
        // Clamp the capacity of the CPU between [0.0, maxFreq]
        frequency = Math.max(0, Math.min(this.maxCapacity, frequency));
//        psu.setCpuFrequency(muxInPort, frequency);
    }

    @Override
    public double getPowerDemand() {
        return this.currentPowerDemand;
    }

    @Override
    public double getPowerDraw() {
        return this.currentPowerSupplied;
    }

    @Override
    public double getDemand() {
        return this.currentCpuDemand;
    }

    @Override
    public double getSpeed() {
        return this.currentCpuSupplied;
    }

    @Override
    public CpuModel getCpuModel() {
        return cpuModel;
    }

    @Override
    public InPort getInput() {
        return null;
    }

    @Override
    public String toString() {
        return "SimBareMetalMachine.Cpu[model=" + cpuModel + "]";
    }

    @Override
    public long onUpdate(long now) {
        return Long.MAX_VALUE;
    }


    /**
     * Push new demand to the psu
     */
    @Override
    public void pushDemand(FlowEdge supplierEdge, float newPowerDemand) {
        this.psuEdge.pushDemand(newPowerDemand);
    }

    /**
     * Push updated supply to the mux
     */
    @Override
    public void pushSupply(FlowEdge consumerEdge, float newCpuSupply) {
        this.currentCpuSupplied = newCpuSupply;

        this.muxEdge.pushSupply(newCpuSupply);
    }

    /**
     * Handle new demand coming in from the mux
     */
    @Override
    public void handleDemand(FlowEdge consumerEdge, float newCpuDemand) {
        if (newCpuDemand == this.currentCpuDemand) {
            return;
        }

        this.currentCpuDemand = newCpuDemand;
        this.currentCpuUtilization = this.currentCpuDemand / this.maxCapacity;

        this.currentPowerDemand = (float) cpuPowerModel.computePower(this.currentCpuUtilization);

        pushDemand(this.psuEdge, this.currentPowerDemand);
    }

    /**
     * Handle updated supply from the psu
     */
    @Override
    public void handleSupply(FlowEdge supplierEdge,  float newPowerSupply) {
        // TODO: Implement this
        this.currentPowerSupplied = newPowerSupply;
        this.pushSupply(this.muxEdge, this.currentCpuDemand); // TODO update this with a reverse power model
    }

    /**
     * Add a connection to the mux
     */
    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.muxEdge = consumerEdge;
    }

    /**
     * Add a connection to the psu
     */
    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.psuEdge = supplierEdge;
    }

    /**
     * Remove the connection to the mux
     */
    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        this.muxEdge = null;
    }

    @Override
    public float getCapacity() {
        return maxCapacity;
    }

    /**
     * Remove the connection to the psu
     */
    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.psuEdge = null;
    }
}
