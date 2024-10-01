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

package org.opendc.simulator.compute.old.workload;

import java.time.InstantSource;
import java.util.Map;
import org.opendc.simulator.compute.old.SimMachineContext;
import org.opendc.simulator.compute.old.memory.SimMemory;
import org.opendc.simulator.compute.old.cpu.SimProcessingUnit;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.FlowStageLogic;

/**
 * A {@link SimWorkload} that composes two {@link SimWorkload}s.
 */
final class SimChainWorkload implements SimWorkload {
    private final SimWorkload[] workloads; // TODO: Turn into QUEUE
    private int activeWorkloadIndex;

    private SimChainWorkloadContext activeContext;

    private long checkpointInterval = 0;
    private long checkpointDuration = 0;

    private double checkpointIntervalScaling = 1.0;
    private CheckPointModel checkpointModel;
    private SimChainWorkload snapshot;

    /**
     * Construct a {@link SimChainWorkload} instance.
     *
     * @param workloads The workloads to chain.
     * @param activeWorkloadIndex The index of the active workload.
     */
    SimChainWorkload(SimWorkload[] workloads, int activeWorkloadIndex) {
        this.workloads = workloads;

        if (this.workloads.length > 1) {
            checkpointInterval = this.workloads[1].getCheckpointInterval();
            checkpointDuration = this.workloads[1].getCheckpointDuration();
            checkpointIntervalScaling = this.workloads[1].getCheckpointIntervalScaling();
        }

        this.activeWorkloadIndex = activeWorkloadIndex;
    }

    /**
     * Construct a {@link SimChainWorkload} instance.
     *
     * @param workloads The workloads to chain.
     */
    SimChainWorkload(SimWorkload... workloads) {
        this(workloads, 0);
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
    public void setOffset(long now) {
        for (SimWorkload workload : this.workloads) {
            workload.setOffset(now);
        }
    }

    @Override
    public void onStart(SimMachineContext ctx) {
        if (this.activeWorkloadIndex >= this.workloads.length) {
            return;
        }

        this.activeContext = new SimChainWorkloadContext(ctx);

        // Create and start a checkpoint model if initiated
        if (checkpointInterval > 0) {
            this.createCheckpointModel();
            this.checkpointModel.start();
        }

        tryThrow(this.activeContext.doStart(workloads[activeWorkloadIndex]));
    }

    @Override
    public void onStop(SimMachineContext ctx) {
        if (this.activeWorkloadIndex >= this.workloads.length) {
            return;
        }

        final SimChainWorkloadContext context = this.activeContext;
        this.activeContext = null;

        if (this.checkpointModel != null) {
            this.checkpointModel.stop();
        }

        tryThrow(context.doStop(this.workloads[this.activeWorkloadIndex]));
    }

    @Override
    public void makeSnapshot(long now) {
        final SimWorkload[] newWorkloads = new SimWorkload[this.workloads.length - this.activeWorkloadIndex];

        for (int i = 0; i < newWorkloads.length; i++) {
            this.workloads[this.activeWorkloadIndex + i].makeSnapshot(now);
            newWorkloads[i] = this.workloads[this.activeWorkloadIndex + i].getSnapshot();
        }

        this.snapshot = new SimChainWorkload(newWorkloads, 0);
    }

    @Override
    public SimChainWorkload getSnapshot() {
        return this.snapshot;
    }

    @Override
    public void createCheckpointModel() {
        this.checkpointModel = new CheckPointModel(
                activeContext, this, this.checkpointInterval, this.checkpointDuration, this.checkpointIntervalScaling);
    }


    /**
     * A {@link SimMachineContext} that intercepts the shutdown calls.
     */
    private class SimChainWorkloadContext implements SimMachineContext {
        private final SimMachineContext machineContext;
        private SimWorkload snapshot;

        private SimChainWorkloadContext(SimMachineContext ctx) {
            this.machineContext = ctx;
        }

        @Override
        public FlowGraph getGraph() {
            return machineContext.getGraph();
        }

        @Override
        public Map<String, Object> getMeta() {
            return machineContext.getMeta();
        }

        @Override
        public SimProcessingUnit getCpu() {
            return machineContext.getCpu();
        }

        @Override
        public SimMemory getMemory() {
            return machineContext.getMemory();
        }

        @Override
        public void makeSnapshot(long now) {
            this.snapshot = workloads[activeWorkloadIndex].getSnapshot();
        }

        @Override
        public SimWorkload getSnapshot(long now) {
            this.makeSnapshot(now);

            return this.snapshot;
        }

        @Override
        public void reset() {
            machineContext.reset();
        }

        @Override
        public void shutdown() {
            shutdown(null);
        }

        @Override
        public void shutdown(Exception cause) {

            // Stop the current workload
            final Exception stopException = doStop(workloads[activeWorkloadIndex]);
            if (cause == null) {
                cause = stopException;
            } else if (stopException != null) {
                cause.addSuppressed(stopException);
            }

            // increase the current activeWorkLoadIndex
            SimChainWorkload.this.activeWorkloadIndex++;

            // Start the next workload if no exceptions were caused before
            if (stopException == null && activeWorkloadIndex < workloads.length) {
                machineContext.reset();

                final Exception startException = doStart(workloads[activeWorkloadIndex]);

                if (startException == null) {
                    return;
                } else if (cause == null) {
                    cause = startException;
                } else {
                    cause.addSuppressed(startException);
                }
            }

            if (SimChainWorkload.this.checkpointModel != null) {
                SimChainWorkload.this.checkpointModel.stop();
            }
            machineContext.shutdown(cause);
        }

        /**
         * Start the specified workload.
         *
         * @return The {@link Exception} that occurred while starting the workload or <code>null</code> if the workload
         *         started successfully.
         */
        private Exception doStart(SimWorkload workload) {
            try {
                workload.onStart(this);
            } catch (Exception cause) {
                final Exception stopException = doStop(workload);
                if (stopException != null) {
                    cause.addSuppressed(stopException);
                }
                return cause;
            }

            return null;
        }

        /**
         * Stop the specified workload.
         *
         * @return The {@link Exception} that occurred while stopping the workload or <code>null</code> if the workload
         *         stopped successfully.
         */
        private Exception doStop(SimWorkload workload) {
            try {
                workload.onStop(this);
            } catch (Exception cause) {
                return cause;
            }

            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void tryThrow(Throwable e) throws T {
        if (e == null) {
            return;
        }
        throw (T) e;
    }

    private class CheckPointModel implements FlowStageLogic {
        private SimChainWorkload workload;
        private long checkpointInterval;
        private long checkpointDuration;
        private double checkpointIntervalScaling;
        private FlowStage stage;

        private long startOfInterval;
        private Boolean firstCheckPoint = true;

        CheckPointModel(
            SimChainWorkloadContext context,
            SimChainWorkload workload,
            long checkpointInterval,
            long checkpointDuration,
            double checkpointIntervalScaling) {
            this.checkpointInterval = checkpointInterval;
            this.checkpointDuration = checkpointDuration;
            this.checkpointIntervalScaling = checkpointIntervalScaling;
            this.workload = workload;

            this.stage = context.getGraph().newStage(this);

            InstantSource clock = this.stage.getGraph().getEngine().getClock();

            this.startOfInterval = clock.millis();
        }

        @Override
        public long onUpdate(FlowStage ctx, long now) {
            long passedTime = now - startOfInterval;
            long remainingTime = this.checkpointInterval - passedTime;

            if (!this.firstCheckPoint) {
                remainingTime += this.checkpointDuration;
            }

            // Interval not completed
            if (remainingTime > 0) {
                return now + remainingTime;
            }

            workload.makeSnapshot(now);
            if (firstCheckPoint) {
                this.firstCheckPoint = false;
            }

            // Scale the interval time between checkpoints based on the provided scaling
            this.checkpointInterval = (long) (this.checkpointInterval * this.checkpointIntervalScaling);

            return now + this.checkpointInterval + this.checkpointDuration;
        }

        public void start() {
            this.stage.invalidate();
        }

        public void stop() {
            this.stage.close();
        }
    }
}
