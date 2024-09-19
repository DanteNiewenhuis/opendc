package org.opendc.simulator.compute.cpu;


import org.opendc.simulator.compute.model.CpuModel;
import org.opendc.simulator.compute.power.SimplePsu;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.InPort;

/**
 * A {@link SimProcessingUnit} of a bare-metal machine.
 */
public final class SimCpu implements SimProcessingUnit {
    private final SimplePsu psu;
    private final CpuModel cpuModel;
    private final InPort muxInPort;

    private final CpuPowerModel cpuPowerModel;

    private double currentDemand;
    private double currentRate;
    private double maxRate;

    private long lastUpdate;

    public SimCpu(FlowGraph graph, SimplePsu psu, CpuModel cpuModel, int id) {
        this.psu = psu;
        this.cpuModel = cpuModel;
        this.maxRate = this.cpuModel.getTotalCapacity();
        this.muxInPort = psu.getCpuPower(id, cpuModel);

        this.muxInPort.pull((float) cpuModel.getTotalCapacity());

        this.cpuPowerModel = CpuPowerModels.linear(400, 200);

        this.lastUpdate = graph.getEngine().getClock().millis();
    }

    @Override
    public double getFrequency() {
        return muxInPort.getCapacity();
    }

    @Override
    public void setFrequency(double frequency) {
        // Clamp the capacity of the CPU between [0.0, maxFreq]
        frequency = Math.max(0, Math.min(this.maxRate, frequency));
        psu.setCpuFrequency(muxInPort, frequency);
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
        return muxInPort.getDemand();
    }

    @Override
    public double getSpeed() {
        return muxInPort.getRate();
    }

    @Override
    public CpuModel getCpuModel() {
        return cpuModel;
    }

    @Override
    public InPort getInput() {
        return muxInPort;
    }

    @Override
    public String toString() {
        return "SimBareMetalMachine.Cpu[model=" + cpuModel + "]";
    }

    @Override
    public long onUpdate(FlowStage ctx, long now) {
        return 0;
    }
}
