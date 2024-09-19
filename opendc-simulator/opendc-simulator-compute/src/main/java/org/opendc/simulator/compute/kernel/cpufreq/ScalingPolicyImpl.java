package org.opendc.simulator.compute.kernel.cpufreq;

import org.opendc.simulator.compute.cpu.SimProcessingUnit;

/**
 * A {@link ScalingPolicy} for a physical CPU of the hypervisor.
 */
public final class ScalingPolicyImpl implements ScalingPolicy {
    private final SimProcessingUnit cpu;

    public ScalingPolicyImpl(SimProcessingUnit cpu) {
        this.cpu = cpu;
    }

    @Override
    public SimProcessingUnit getCpu() {
        return cpu;
    }

    @Override
    public double getTarget() {
        return cpu.getFrequency();
    }

    @Override
    public void setTarget(double target) {
        cpu.setFrequency(target);
    }

    @Override
    public double getMin() {
        return 0;
    }

    @Override
    public double getMax() {
        return cpu.getCpuModel().getTotalCapacity();
    }
}
