package org.opendc.simulator.compute.v2.workload;

import org.opendc.simulator.compute.old.workload.SimTraceFragment;
import org.opendc.simulator.flow3.engine.FlowSupplier;

import java.util.ArrayDeque;
import java.util.LinkedList;

public class TraceWorkload implements Workload {
    private LinkedList<SimTraceFragment> fragments;

    private long checkpointInterval = 0; // How long to wait until a new checkpoint is made
    private long checkpointDuration = 0; // How long does it take to make a checkpoint
    private double checkpointIntervalScaling = 0;

    public TraceWorkload(LinkedList<SimTraceFragment> fragments, long checkpointInterval,
                         long checkpointDuration, double checkpointIntervalScaling) {
        this.fragments = fragments;
        this.checkpointInterval = checkpointInterval;
        this.checkpointDuration = checkpointDuration;
        this.checkpointIntervalScaling = checkpointIntervalScaling;
    }

    public LinkedList<SimTraceFragment> getFragments() {
        return fragments;
    }

    @Override
    public long getCheckpointInterval() {
        return checkpointInterval;
    }

    @Override
    public long getCheckpointDuration() {
        return checkpointDuration;
    }

    @Override
    public double getCheckpointIntervalScaling() {
        return checkpointIntervalScaling;
    }

    @Override
    public SimWorkloadNew onStart(FlowSupplier supplier, long now) {
        return new SimTraceWorkloadNew(supplier, this, now);
    }
}
