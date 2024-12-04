/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.compute.workload.trace;

import java.util.LinkedList;
import org.opendc.simulator.engine.graph.FlowConsumer;
import org.opendc.simulator.engine.graph.FlowEdge;
import org.opendc.simulator.engine.graph.FlowGraph;
import org.opendc.simulator.engine.graph.FlowNode;
import org.opendc.simulator.engine.graph.FlowSupplier;

import org.opendc.simulator.compute.workload.SimWorkload;
import org.opendc.simulator.compute.workload.trace.scaling.NoDelay;
import org.opendc.simulator.compute.workload.trace.scaling.ScalingPolicy;

public class SimTraceWorkload extends SimWorkload implements FlowConsumer {
    private LinkedList<TraceFragment> remainingFragments;
    private int fragmentIndex;

    private TraceFragment currentFragment;
    private long startOfFragment;

    private FlowEdge machineEdge;

    private double cpuFreqDemand; // The Cpu demanded by fragment
    private double cpuFreqSupplied; // The Cpu speed supplied
    private double remainingWork; // The duration of the fragment at the demanded speed

    private long checkpointDuration;

    private TraceWorkload snapshot;

    private ScalingPolicy scalingPolicy = new NoDelay();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Getters and Setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public long getPassedTime(long now) {
        return now - this.startOfFragment;
    }

    public TraceWorkload getSnapshot() {
        return snapshot;
    }

    @Override
    public long getCheckpointInterval() {
        return 0;
    }

    @Override
    public long getCheckpointDuration() {
        return 0;
    }

    @Override
    public double getCheckpointIntervalScaling() {
        return 0;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimTraceWorkload(FlowSupplier supplier, TraceWorkload workload, long now) {
        super(((FlowNode) supplier).getGraph());

        this.snapshot = workload;
        this.checkpointDuration = workload.getCheckpointDuration();
        this.remainingFragments = new LinkedList<>(workload.getFragments());
        this.fragmentIndex = 0;

        final FlowGraph graph = ((FlowNode) supplier).getGraph();
        graph.addEdge(this, supplier);

        this.startNextFragment();
        this.startOfFragment = now;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fragment related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long onUpdate(long now) {
        long passedTime = getPassedTime(now);
        this.startOfFragment = now;

        // The amount of work done since last update
        double finishedWork = this.scalingPolicy.getFinishedWork(this.cpuFreqDemand, this.cpuFreqSupplied, passedTime);

        this.remainingWork -= finishedWork;
        if (this.remainingWork <= 0) {
            if (!this.startNextFragment()) {
                return Long.MAX_VALUE;
            }
        }

        if (this.cpuFreqSupplied == 0) {
            return Long.MAX_VALUE;
        }

        // The amount of time required to finish the fragment at this speed
        long remainingDuration = this.scalingPolicy.getRemainingDuration(this.cpuFreqDemand, this.cpuFreqSupplied, this.remainingWork);

        return now + remainingDuration;
    }

    public TraceFragment getNextFragment() {
        if (this.remainingFragments.isEmpty()) {
            return null;
        }
        this.currentFragment = this.remainingFragments.pop();
        this.fragmentIndex++;

        return this.currentFragment;
    }

    private boolean startNextFragment() {

        // TODO: turn this into a loop, should not be needed, but might be safer
        TraceFragment nextFragment = this.getNextFragment();
        if (nextFragment == null) {
            this.stopWorkload();
            return false;
        }
        double demand = nextFragment.cpuUsage();
        this.remainingWork = this.scalingPolicy.getRemainingWork(demand, nextFragment.duration());
        this.pushDemand(this.machineEdge, demand);

        return true;
    }



    @Override
    public void stopWorkload() {
        this.closeNode();

        this.machineEdge = null;
        this.remainingFragments = null;
        this.currentFragment = null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Checkpoint related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * SimTraceWorkload does not make a checkpoint, checkpointing is handled by SimChainWorkload
     * TODO: Maybe add checkpoint models for SimTraceWorkload
     */
    @Override
    public void createCheckpointModel() {}

    /**
     * Create a new snapshot based on the current status of the workload.
     * @param now Moment on which the snapshot is made in milliseconds
     */
    public void makeSnapshot(long now) {

        // Check if fragments is empty

        // Get remaining time of current fragment
        long passedTime = getPassedTime(now);
        long remainingTime = currentFragment.duration() - passedTime;

        // Create a new fragment based on the current fragment and remaining duration
        TraceFragment newFragment =
                new TraceFragment(remainingTime, currentFragment.cpuUsage(), currentFragment.coreCount());

        // Alter the snapshot by removing finished fragments
        this.snapshot.removeFragments(this.fragmentIndex);
        this.snapshot.addFirst(newFragment);

        this.remainingFragments.addFirst(newFragment);

        // Create and add a fragment for processing the snapshot process
        // TODO: improve the implementation of cpuUsage and coreCount
        TraceFragment snapshotFragment = new TraceFragment(this.checkpointDuration, 123456, 1);
        this.remainingFragments.addFirst(snapshotFragment);

        this.fragmentIndex = -1;
        this.currentFragment = getNextFragment();
        pushDemand(this.machineEdge, this.currentFragment.cpuUsage());
        this.startOfFragment = now;

        this.invalidate();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowGraph Related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handle updates in supply from the Virtual Machine
     *
     * @param supplierEdge edge to the VM on which this is running
     * @param newSupply The new demand that needs to be sent to the VM
     */
    @Override
    public void handleSupply(FlowEdge supplierEdge, double newSupply) {
        if (newSupply == this.cpuFreqSupplied) {
            return;
        }

        this.cpuFreqSupplied = newSupply;
        this.invalidate();
    }

    /**
     * Push a new demand to the Virtual Machine
     *
     * @param supplierEdge edge to the VM on which this is running
     * @param newDemand The new demand that needs to be sent to the VM
     */
    @Override
    public void pushDemand(FlowEdge supplierEdge, double newDemand) {
        if (newDemand == this.cpuFreqDemand) {
            return;
        }

        this.cpuFreqDemand = newDemand;
        this.machineEdge.pushDemand(newDemand);
    }

    /**
     * Add the connection to the Virtual Machine
     *
     * @param supplierEdge edge to the VM on which this is running
     */
    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.machineEdge = supplierEdge;
    }

    /**
     * Handle the removal of the connection to the Virtual Machine
     * When the connection to the Virtual Machine is removed, the SimTraceWorkload is removed
     *
     * @param supplierEdge edge to the VM on which this is running
     */
    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        if (this.machineEdge == null) {
            return;
        }

        this.stopWorkload();
    }
}
