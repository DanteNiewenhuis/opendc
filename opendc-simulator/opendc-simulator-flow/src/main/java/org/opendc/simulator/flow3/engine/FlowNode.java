/*
 * Copyright (c) 2022 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.flow3.engine;

import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStageLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.InstantSource;

/**
 * A {@link FlowNode} represents a node in a {@link FlowGraph}.
 */
public abstract class FlowNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowNode.class);


    private enum NodeState {
        PENDING, // Stage is active, but is not running any updates
        UPDATING, // Stage is active, and running an update
        INVALIDATED, // Stage is deemed invalid, and should run an update
        CLOSING, // Stage is being closed, final updates can still be run
        CLOSED // Stage is closed and should not run any updates
    }

    private NodeState nodeState = NodeState.PENDING;

    /**
     * The deadline of the stage after which an update should run.
     */
    long deadline = Long.MAX_VALUE;

    /**
     * The index of the timer in the {@link FlowTimerQueueNew}.
     */
    int timerIndex = -1;

    final InstantSource clock;
    final FlowGraphNew parentGraph;
    private final FlowEngineNew engine;

    /**
     * Construct a new {@link FlowNode} instance.
     *
     * @param parentGraph The {@link FlowGraph} this stage belongs to.
     */
    public FlowNode(FlowGraphNew parentGraph) {
        this.parentGraph = parentGraph;
        this.engine = parentGraph.getEngine();
        this.clock = engine.getClock();

        this.parentGraph.addNode(this);
    }

    /**
     * Return the {@link FlowGraph} to which this stage belongs.
     */
    public FlowGraphNew getGraph() {
        return parentGraph;
    }

    /**
     * Return the current deadline of the {@link FlowNode}'s timer (in milliseconds after epoch).
     */
    public long getDeadline() {
        return deadline;
    }

    /**
     * Invalidate the {@link FlowNode} forcing the stage to update.
     *
     * <p>
     * This method is similar to {@link #invalidate()}, but allows the user to manually pass the current timestamp to
     * prevent having to re-query the clock. This method should not be called during an update.
     */
    public void invalidate(long now) {
        // If there is already an update running,
        // notify the update, that a next update should be run after
        if (this.nodeState == NodeState.UPDATING) {
            this.nodeState = NodeState.INVALIDATED;
        }
        else {
            engine.scheduleImmediate(now, this);
        }
    }

    /**
     * Invalidate the {@link FlowNode} forcing the stage to update.
     */
    public void invalidate() {
        invalidate(clock.millis());
    }

    /**
     * Update the state of the stage.
     */
    public void update(long now) {
        this.nodeState = NodeState.UPDATING;

        long newDeadline = this.deadline;

        try {
            newDeadline = this.onUpdate(now);
        } catch (Exception e) {
            doFail(e);
        }


        // Check whether the stage is marked as closing.
        if (this.nodeState == NodeState.INVALIDATED) {
            newDeadline = now;
        }
        if (this.nodeState == NodeState.CLOSING) {
            close(null);
            return;
        }

        this.deadline = newDeadline;

        // Update the timer queue with the new deadline
        engine.scheduleDelayedInContext(this);

        this.nodeState = NodeState.PENDING;
    }

    /**
     * This method is invoked when the one of the stage's InPorts or OutPorts is invalidated.
     *
     * @param now The virtual timestamp in milliseconds after epoch at which the update is occurring.
     * @return The next deadline for the stage.
     */
    public abstract long onUpdate(long now);

    /**
     * This method is invoked when an uncaught exception is caught by the engine. When this happens, the
     * {@link FlowStageLogic} "fails" and disconnects all its inputs and outputs.
     */
    void doFail(Throwable cause) {
        LOGGER.warn("Uncaught exception (closing stage)", cause);

        close(cause);
    }

    public void close() {
        close(null);
    }

    /**
     * This method is invoked when the {@link FlowStageLogic} exits successfully or due to failure.
     */
    public void close(Throwable cause) {
        if (this.nodeState == NodeState.CLOSED) {
            LOGGER.warn("Flowstage:doClose() => Tried closing a stage that was already closed");
            return;
        }

        // If this stage is running an update, notify it that is should close after.
        if (this.nodeState == NodeState.UPDATING) {
            LOGGER.warn("Flowstage:doClose() => Tried closing a stage, but update was active");
            this.nodeState = NodeState.CLOSING;
            return;
        }

        // Mark the stage as closed
        this.nodeState = NodeState.CLOSED;

        // Remove stage from parent graph
        parentGraph.removeNode(this);

        // Remove stage from the timer queue
        this.deadline = Long.MAX_VALUE;
        engine.scheduleDelayedInContext(this);
    }
}
