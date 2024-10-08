package org.opendc.simulator.compute.workload;

import org.opendc.simulator.engine.FlowSupplier;

import java.util.ArrayList;
import java.util.List;

public record ChainWorkload (
    ArrayList<Workload> workloadQueue, long checkpointInterval, long checkpointDuration, double checkpointIntervalScaling
) implements Workload {

    public List<Workload> getWorkloadQueue() {
        return workloadQueue;
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

    @Override
    public SimWorkload startWorkload(FlowSupplier supplier, long now) {
        return new SimChainWorkload(supplier,this, now);
    }
}
