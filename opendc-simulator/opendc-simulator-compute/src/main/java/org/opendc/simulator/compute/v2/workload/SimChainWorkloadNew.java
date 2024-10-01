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

package org.opendc.simulator.compute.v2.workload;

import org.opendc.simulator.compute.old.SimMachineContext;
import org.opendc.simulator.compute.old.cpu.SimProcessingUnit;
import org.opendc.simulator.compute.old.memory.SimMemory;
import org.opendc.simulator.compute.old.workload.SimWorkload;
import org.opendc.simulator.compute.v2.machine.VirtualMachineNew;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;
import org.opendc.simulator.flow3.engine.FlowSupplier;

import java.time.InstantSource;
import java.util.LinkedList;
import java.util.Map;

/**
 * A {@link SimWorkload} that composes two {@link SimWorkload}s.
 */
final class SimChainWorkloadNew extends SimWorkloadNew implements FlowSupplier {
    private final LinkedList<Workload> workloadQueue;
    private final FlowGraphNew graph;
    private final InstantSource clock;

    private SimWorkloadNew activeWorkload;
    private float demand = 0.0f;
    private float supply = 0.0f;

    private FlowEdge workloadEdge;
    private FlowEdge machineEdge;

    private long checkpointInterval = 0;
    private long checkpointDuration = 0;
    private double checkpointIntervalScaling = 1.0;
    private CheckPointModel checkpointModel;

    private ChainWorkload snapshot;

    SimChainWorkloadNew(FlowSupplier supplier, ChainWorkload workload, long now) {
        super(((FlowNode) supplier).getGraph());

        this.graph = ((FlowNode) supplier).getGraph();

        this.graph.addEdge(this, supplier);


        this.clock = this.graph.getEngine().getClock();
        this.workloadQueue = workload.getWorkloadQueue();
        this.checkpointInterval = workload.getCheckpointInterval();
        this.checkpointDuration = workload.getCheckpointDuration();
        this.checkpointIntervalScaling = workload.getCheckpointIntervalScaling();

        if (checkpointInterval > 0) {
            this.createCheckpointModel();
        }
        this.snapshot = workload;

        this.onStart();
    }

    public void onStart() {
        if (this.workloadQueue.isEmpty()) {
            return;
        }

        // Create and start a checkpoint model if initiated
        if (checkpointInterval > 0) {
            this.checkpointModel.start();
        }

        tryThrow(this.doStart(workloadQueue.pop()));
    }

    /**
     * Start the specified workload.
     *
     * @return The {@link Exception} that occurred while starting the workload or <code>null</code> if the workload
     *         started successfully.
     */
    private Exception doStart(Workload workload) {
        try {
            this.activeWorkload = workload.onStart(this, this.clock.millis());
        } catch (Exception cause) {
            final Exception stopException = doStop(this.activeWorkload);
            if (stopException != null) {
                cause.addSuppressed(stopException);
            }
            return cause;
        }

        return null;
    }

    @Override
    public long onUpdate(long now) {
        return Long.MAX_VALUE;
    }

    @Override
    public void onStop() {
        if (this.workloadQueue.isEmpty()) {
            return;
        }

        if (this.checkpointModel != null) {
            this.checkpointModel.stop();
        }

        tryThrow(this.doStop(activeWorkload));
    }



    /**
     * Stop the specified workload.
     *
     * @return The {@link Exception} that occurred while stopping the workload or <code>null</code> if the workload
     *         stopped successfully.
     */
    private Exception doStop(SimWorkloadNew workload) {
        try {
            workload.onStop();
        } catch (Exception cause) {
            return cause;
        }

        return null;
    }

    @Override
    public void makeSnapshot(long now) {
        final LinkedList<Workload> newWorkloadQueue = this.workloadQueue;

        activeWorkload.makeSnapshot(now);
        final Workload activeWorkloadSnapshot = activeWorkload.getSnapshot();

        newWorkloadQueue.addFirst(activeWorkloadSnapshot);

        this.snapshot = new ChainWorkload(newWorkloadQueue);
    }

    @Override
    public void createCheckpointModel() {
        this.checkpointModel = new CheckPointModel(this);
    }

    @Override
    public ChainWorkload getSnapshot() {
        return this.snapshot;
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
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.workloadEdge = consumerEdge;
    }

    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.machineEdge = supplierEdge;
    }

    @Override
    public void pushDemand(FlowEdge supplierEdge, float newDemand) {
        this.machineEdge.pushDemand(newDemand);
    }

    @Override
    public void pushSupply(FlowEdge consumerEdge, float newSupply) {
        this.workloadEdge.pushSupply(newSupply);
    }

    @Override
    public void handleDemand(FlowEdge consumerEdge, float newDemand) {
        if (newDemand == this.demand) {
            return;
        }

        this.demand = newDemand;
        this.pushDemand(this.machineEdge, newDemand);
    }

    @Override
    public void handleSupply(FlowEdge supplierEdge, float newSupply) {
        if (newSupply == this.supply) {
            return;
        }

        this.pushSupply(this.machineEdge, newSupply);
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        this.close(); // TODO: implement multiple workloads
    }

    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.close();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void tryThrow(Throwable e) throws T {
        if (e == null) {
            return;
        }
        throw (T) e;
    }

    private class CheckPointModel extends FlowNode {
        private SimChainWorkloadNew simWorkload;
        private long checkpointInterval;
        private final long checkpointDuration;
        private double checkpointIntervalScaling;
        private FlowGraphNew graph;

        private long startOfInterval;
        private Boolean firstCheckPoint = true;

        CheckPointModel(
            SimChainWorkloadNew simWorkload) {
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
            long passedTime = now - startOfInterval;
            long remainingTime = this.checkpointInterval - passedTime;

            if (!this.firstCheckPoint) {
                remainingTime += this.checkpointDuration;
            }

            // Interval not completed
            if (remainingTime > 0) {
                return now + remainingTime;
            }

            simWorkload.makeSnapshot(now);
            if (firstCheckPoint) {
                this.firstCheckPoint = false;
            }

            // start new fragment
            this.startOfInterval = now - passedTime;

            // Scale the interval time between checkpoints based on the provided scaling
            this.checkpointInterval = (long) (this.checkpointInterval * this.checkpointIntervalScaling);

            return now + this.checkpointInterval + this.checkpointDuration;
        }

        public void start() {
            this.invalidate();
        }

        public void stop() {
            this.close();

            this.simWorkload = null;
            this.graph = null;
        }
    }
}
