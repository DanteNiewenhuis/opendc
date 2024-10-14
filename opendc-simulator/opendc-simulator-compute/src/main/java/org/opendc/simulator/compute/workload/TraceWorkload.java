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
        return builder(0L, 0L, 0L);
    }

    public static Builder builder(long checkpointInterval, long checkpointDuration, double checkpointIntervalScaling) {
        return new Builder(checkpointInterval, checkpointDuration, checkpointIntervalScaling);
    }

    public static final class Builder {
        private final ArrayList<TraceFragment> fragments;
        private final long checkpointInterval;
        private final long checkpointDuration;
        private final double checkpointIntervalScaling;

        /**
         * Construct a new {@link Builder} instance.
         */
        private Builder(long checkpointInterval, long checkpointDuration, double checkpointIntervalScaling) {
            this.fragments = new ArrayList<TraceFragment>();
            this.checkpointInterval = checkpointInterval;
            this.checkpointDuration = checkpointDuration;
            this.checkpointIntervalScaling = checkpointIntervalScaling;
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
            return new TraceWorkload(this.fragments, this.checkpointInterval, this.checkpointDuration,this.checkpointIntervalScaling);
        }
    }
}
