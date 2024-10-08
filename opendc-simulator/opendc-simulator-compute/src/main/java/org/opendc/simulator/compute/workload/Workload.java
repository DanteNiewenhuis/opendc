package org.opendc.simulator.compute.workload;

import org.opendc.simulator.engine.FlowSupplier;

public interface Workload {

    long getCheckpointInterval();

    long getCheckpointDuration();

    double getCheckpointIntervalScaling();

    SimWorkload startWorkload(FlowSupplier supplier, long now);
}
