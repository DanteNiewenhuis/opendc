package org.opendc.simulator.compute.workload;

import org.opendc.simulator.engine.FlowSupplier;

import java.util.ArrayList;

public class ChainWorkload  implements Workload {
    private ArrayList<Workload> workloads;
    private final long checkpointInterval;
    private final long checkpointDuration;
    private final double checkpointIntervalScaling;


    public ChainWorkload(ArrayList<Workload> workloads, long checkpointInterval, long checkpointDuration, double checkpointIntervalScaling) {
        this.workloads = workloads;
        this.checkpointInterval = checkpointInterval;
        this.checkpointDuration = checkpointDuration;
        this.checkpointIntervalScaling = checkpointIntervalScaling;
    }

    public ArrayList<Workload> getWorkloads() {
        return workloads;
    }

    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    public long getCheckpointDuration() {
        return checkpointDuration;
    }

    public double getCheckpointIntervalScaling() {
        return checkpointIntervalScaling;
    }

    public void removeWorkloads(int numberOfWorkloads) {
        if (numberOfWorkloads <= 0) {
            return;
        }
        this.workloads.subList(0, numberOfWorkloads).clear();
    }

    @Override
    public SimWorkload startWorkload(FlowSupplier supplier, long now) {
        return new SimChainWorkload(supplier,this, now);
    }
}
