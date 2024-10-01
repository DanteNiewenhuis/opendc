package org.opendc.simulator.compute.v2.workload;

import org.opendc.simulator.compute.old.workload.SimWorkload;
import org.opendc.simulator.flow3.engine.FlowSupplier;

import java.util.Arrays;
import java.util.LinkedList;

public class ChainWorkload implements Workload {
    private final LinkedList<Workload> workloadQueue;

    private long checkpointInterval = 0;
    private long checkpointDuration = 0;
    private double checkpointIntervalScaling = 1.0;

    public ChainWorkload(LinkedList<Workload> workloadQueue) {
        this.workloadQueue = workloadQueue;
    }

    public ChainWorkload(Workload[] workloads) {
        this(new LinkedList<>(Arrays.asList(workloads)));
    }

    public LinkedList<Workload> getWorkloadQueue() {
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
    public SimWorkloadNew onStart(FlowSupplier supplier, long now) {
        return new SimChainWorkloadNew(supplier,this, now);
    }
}
