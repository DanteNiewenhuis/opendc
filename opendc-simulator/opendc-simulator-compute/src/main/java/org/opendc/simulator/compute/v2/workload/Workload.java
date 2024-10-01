package org.opendc.simulator.compute.v2.workload;

import org.opendc.simulator.flow3.engine.FlowSupplier;

public interface Workload {

    long getCheckpointInterval();

    long getCheckpointDuration();

    double getCheckpointIntervalScaling();

    SimWorkloadNew onStart(FlowSupplier supplier, long now);
}
