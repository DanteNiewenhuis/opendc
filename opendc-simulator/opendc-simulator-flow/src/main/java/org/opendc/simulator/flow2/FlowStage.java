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

package org.opendc.simulator.flow2;

import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link FlowStage} represents a node in a {@link FlowGraph}.
 */
public final class FlowStage {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowStage.class);


    private enum StageState {
        PENDING, // Stage is active, but is not running any updates
        UPDATING, // Stage is active, and running an update
        INVALIDATED, // Stage is deemed invalid, and should run an update
        CLOSING, // Stage is being closed, final updates can still be run
        CLOSED // Stage is closed and should not run any updates
    }

    private StageState stageState = StageState.PENDING;

    /**
     * The deadline of the stage after which an update should run.
     */
    long deadline = Long.MAX_VALUE;

    /**
     * The index of the timer in the {@link FlowTimerQueue}.
     */
    int timerIndex = -1;

    final InstantSource clock;
    private final FlowStageLogic logic;
    final FlowGraphInternal parentGraph;
    private final FlowEngine engine;

    private Map<String, InPort> inPorts = new HashMap<>();
    private Map<String, OutPort> outPorts = new HashMap<>();
    private int nextInPort = 0;
    private int nextOutPort = 0;

    /**
     * Construct a new {@link FlowStage} instance.
     *
     * @param parentGraph The {@link FlowGraph} this stage belongs to.
     * @param logic The logic of the stage.
     */
    FlowStage(FlowGraphInternal parentGraph, FlowStageLogic logic) {
        this.parentGraph = parentGraph;
        this.logic = logic;
        this.engine = parentGraph.getEngine();
        this.clock = engine.getClock();
    }

    /**
     * Return the {@link FlowGraph} to which this stage belongs.
     */
    public FlowGraph getGraph() {
        return parentGraph;
    }

    /**
     * Return the {@link InPort} (an in-going edge) with the specified <code>name</code> for this {@link FlowStage}.
     * If an InPort with that name does not exist, a new one is allocated for the stage.
     *
     * @param name The name of the InPort.
     * @return The {@link InPort} representing an {@link InPort} with the specified <code>name</code>.
     */
    public InPort getInPort(String name, int id) {
        return inPorts.computeIfAbsent(name, (key) -> new InPort(this, key, id));
    }

    public InPort getInPort(String name) {
        return inPorts.computeIfAbsent(name, (key) -> new InPort(this, key, nextInPort++));
    }

    /**
     * Return the {@link OutPort} (an out-going edge) with the specified <code>name</code> for this {@link FlowStage}.
     * If an OutPort with that name does not exist, a new one is allocated for the stage.
     *
     * @param name The name of the OutPort.
     * @return The {@link OutPort} representing an {@link OutPort} with the specified <code>name</code>.
     */
    public OutPort getOutPort(String name, int id) {
        return outPorts.computeIfAbsent(name, (key) -> new OutPort(this, key, id));
    }

    public OutPort getOutPort(String name) {
        return outPorts.computeIfAbsent(name, (key) -> new OutPort(this, key, nextOutPort++));
    }

    /**
     * Return the current deadline of the {@link FlowStage}'s timer (in milliseconds after epoch).
     */
    public long getDeadline() {
        return deadline;
    }

    /**
     * Invalidate the {@link FlowStage} forcing the stage to update.
     *
     * <p>
     * This method is similar to {@link #invalidate()}, but allows the user to manually pass the current timestamp to
     * prevent having to re-query the clock. This method should not be called during an update.
     */
    public void invalidate(long now) {
        // If there is already an update running,
        // notify the update, that a next update should be run after
        if (this.stageState == StageState.UPDATING) {
            this.stageState = StageState.INVALIDATED;
        }
        else {
            engine.scheduleImmediate(now, this);
        }
    }

    /**
     * Invalidate the {@link FlowStage} forcing the stage to update.
     */
    public void invalidate() {
        invalidate(clock.millis());
    }

    /**
     * Update the state of the stage.
     */
    public void update(long now) {
        this.stageState = StageState.UPDATING;

        long newDeadline = this.deadline;

        try {
            newDeadline = logic.onUpdate(this, now);
        } catch (Exception e) {
            doFail(e);
        }


        // Check whether the stage is marked as closing.
        if (this.stageState == StageState.INVALIDATED) {
            newDeadline = now;
        }
        if (this.stageState == StageState.CLOSING) {
            doClose(null);
            return;
        }

        this.deadline = newDeadline;

        // Update the timer queue with the new deadline
        engine.scheduleDelayedInContext(this);

        this.stageState = StageState.PENDING;
    }

    /**
     * This method is invoked when an uncaught exception is caught by the engine. When this happens, the
     * {@link FlowStageLogic} "fails" and disconnects all its inputs and outputs.
     */
    void doFail(Throwable cause) {
        LOGGER.warn("Uncaught exception (closing stage)", cause);

        doClose(cause);
    }

    /**
     * Close the {@link FlowStage} and disconnect all InPorts and OutPorts.
     */
    public void close() {
        doClose(null);
    }

    /**
     * This method is invoked when the {@link FlowStageLogic} exits successfully or due to failure.
     */
    private void doClose(Throwable cause) {
        if (this.stageState == StageState.CLOSED) {
            LOGGER.warn("Flowstage:doClose() => Tried closing a stage that was already closed");
//            return;
        }

        // If this stage is running an update, notify it that is should close after.
        if (this.stageState == StageState.UPDATING) {
            LOGGER.warn("Flowstage:doClose() => Tried closing a stage, but update was active");
            this.stageState = StageState.CLOSING;
            return;
        }

        // Mark the stage as closed
        this.stageState = StageState.CLOSED;

        // Remove stage from parent graph
        parentGraph.detach(this);

        // Cancel all input ports
        for (InPort port : this.inPorts.values()) {
            if (port != null) {
                port.cancel(cause);
            }
        }

        this.inPorts = new HashMap<>();

        // Cancel all output ports
        for (OutPort port : outPorts.values()) {
            if (port != null) {
                port.fail(cause);
            }
        }

        // Remove stage from the timer queue
        this.deadline = Long.MAX_VALUE;
        engine.scheduleDelayedInContext(this);
    }
}
