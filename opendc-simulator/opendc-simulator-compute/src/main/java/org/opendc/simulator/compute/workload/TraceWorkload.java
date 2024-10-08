package org.opendc.simulator.compute.workload;

import org.opendc.simulator.engine.FlowSupplier;

import java.util.ArrayList;

public record TraceWorkload(ArrayList<TraceFragment> fragments,
                            long checkpointInterval,
                            long checkpointDuration,
                            double checkpointIntervalScaling)
    implements Workload {

    public ArrayList<TraceFragment> getFragments() {
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
    public SimWorkload startWorkload(FlowSupplier supplier, long now) {
        return new SimTraceWorkload(supplier, this, now);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ArrayList<TraceFragment> fragments;

        /**
         * Construct a new {@link Builder} instance.
         */
        private Builder() {
            this.fragments = new ArrayList<TraceFragment>();
        }

        /**
         * Add a fragment to the trace.
         *
         * @param duration The timestamp at which the fragment ends (in epoch millis).
         * @param usage The CPU usage at this fragment.
         * @param cores The number of cores used during this fragment.
         */
        public void add(long duration, double usage, int cores) {
            fragments.add(0, new TraceFragment(duration, usage, cores));
        }

        /**
         * Build the {@link TraceWorkload} instance.
         */
        public TraceWorkload build() {
            return new TraceWorkload(fragments, 0, 0,0);
        }
    }
}
