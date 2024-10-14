package org.opendc.simulator.compute.workload;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// CheckPoint Model
// TODO: Move this to a separate file
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

import org.jetbrains.annotations.NotNull;
import org.opendc.simulator.engine.FlowGraphNew;
import org.opendc.simulator.engine.FlowNode;

import java.time.InstantSource;

public class CheckpointModel extends FlowNode {
    private SimWorkload simWorkload;
    private long checkpointInterval;
    private final long checkpointDuration;
    private double checkpointIntervalScaling;
    private FlowGraphNew graph;

    private long startOfInterval;

    public CheckpointModel(
        @NotNull SimWorkload simWorkload) {
        super(simWorkload.getGraph());

        this.checkpointInterval = simWorkload.getCheckpointInterval();
        this.checkpointDuration = simWorkload.getCheckpointDuration();
        this.checkpointIntervalScaling = simWorkload.getCheckpointIntervalScaling();
        this.simWorkload = simWorkload;

        this.graph = simWorkload.getGraph();

        InstantSource clock = graph.getEngine().getClock();

        this.startOfInterval = clock.millis();
    }

    @Override
    public long onUpdate(long now) {
        if (this.simWorkload == null) {
            return Long.MAX_VALUE;
        }

        long passedTime = now - startOfInterval;
        long remainingTime = this.checkpointInterval - passedTime;

        // Interval not completed
        if (remainingTime > 0) {
            return now + remainingTime;
        }

        simWorkload.makeSnapshot(now);

        // start new fragment
        this.startOfInterval = now - passedTime;

        // Scale the interval time between checkpoints based on the provided scaling
        this.checkpointInterval = (long) (this.checkpointInterval * this.checkpointIntervalScaling);

        return now + this.checkpointInterval + this.checkpointDuration;
    }

    public void start() {
        this.invalidate();
    }

    public void close() {
        this.closeNode();

        this.simWorkload = null;
        this.graph = null;
    }
}
