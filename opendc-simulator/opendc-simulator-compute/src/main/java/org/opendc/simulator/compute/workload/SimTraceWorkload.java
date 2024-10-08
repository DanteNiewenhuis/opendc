package org.opendc.simulator.compute.workload;

import org.opendc.simulator.engine.FlowConsumer;
import org.opendc.simulator.engine.FlowEdge;
import org.opendc.simulator.engine.FlowGraphNew;
import org.opendc.simulator.engine.FlowNode;
import org.opendc.simulator.engine.FlowSupplier;

import java.util.ArrayList;
import java.util.LinkedList;

public class SimTraceWorkload extends SimWorkload implements FlowConsumer {
    private LinkedList<TraceFragment> remainingFragments;

    private TraceFragment currentFragment;
    private long startOfFragment;

    private FlowEdge machineEdge;
    private float currentDemand;
    private float currentSupply;

    private long checkpointInterval = 0;
    private long checkpointDuration = 0;
    private double checkpointIntervalScaling = 1.0;

    private TraceWorkload snapshot;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic Getters and Setters
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public long getPassedTime(long now) {return now - this.startOfFragment;}

    public TraceWorkload getSnapshot(){return snapshot;}

    @Override
    long getCheckpointInterval() {return 0;}

    @Override
    long getCheckpointDuration() {return 0;}

    @Override
    double getCheckpointIntervalScaling() {return 0;}

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimTraceWorkload(FlowSupplier supplier, TraceWorkload workload, long now) {
        super(((FlowNode)supplier).getGraph());

        this.snapshot = workload;
        this.remainingFragments = new LinkedList<TraceFragment>(workload.getFragments());

        final FlowGraphNew graph = ((FlowNode)supplier).getGraph();
        graph.addEdge(this, supplier);

        this.currentFragment = this.remainingFragments.pop();
        pushDemand(machineEdge, (float) this.currentFragment.cpuUsage());
        this.startOfFragment = now;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fragment related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long onUpdate(long now) {
        long passedTime = getPassedTime(now);
        long duration = this.currentFragment.duration();

        // The current Fragment has not yet been finished, continue
        if (passedTime < duration) {
            return now + (duration - passedTime);
        }

        // Loop through fragments until the passed time is filled.
        // We need a while loop to account for skipping of fragments.
        while (passedTime >= duration) {
            if (this.remainingFragments.isEmpty()) {
                this.stopWorkload();
                return Long.MAX_VALUE;
            }

            passedTime = passedTime - duration;

            // get next Fragment
            currentFragment = this.remainingFragments.pop();
            duration = currentFragment.duration();
        }

        // start new fragment
        this.startOfFragment = now - passedTime;

        // Change the cpu Usage to the new Fragment
        pushDemand(machineEdge, (float) this.currentFragment.cpuUsage());

        // Return the time when the current fragment will complete
        return this.startOfFragment + duration;
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
    void createCheckpointModel() {}

    /**
     * Create a new snapshot based on the current status of the workload.
     * @param now
     */
    public void makeSnapshot(long now){
        final LinkedList<TraceFragment> newFragments = this.remainingFragments;

        TraceFragment currentFragment = this.currentFragment;
        long passedTime = getPassedTime(now);
        long remainingTime = currentFragment.duration() - passedTime;

        // Alter the current fragment to have the duration as the remaining time
        if (remainingTime > 0) {
            TraceFragment newFragment =
                new TraceFragment(remainingTime, currentFragment.cpuUsage(), currentFragment.coreCount());

            newFragments.addFirst(newFragment);
        }

        if (newFragments.isEmpty()) {
            this.stopWorkload();
            return;
        }

        // A Workload with the new fragments is the new snapshot
        this.snapshot = new TraceWorkload(new ArrayList<>(newFragments), this.checkpointInterval, this.checkpointDuration, this.checkpointIntervalScaling);

        // Add a processing Fragment to the start
        long checkpointDuration = 1000L; // TODO: connect to front-end
        TraceFragment snapshotFragment = new TraceFragment(checkpointDuration, 123456, 1);
        newFragments.addFirst(snapshotFragment);

        this.updateFragments(newFragments, now);
    }

    /**
     * Update the Fragments that are being used by the SimTraceWorkload
     * @param newFragments
     * @param offset
     */
    public void updateFragments(LinkedList<TraceFragment> newFragments, long offset) {
        this.remainingFragments = newFragments;

        // Start the first Fragment
        this.currentFragment = this.remainingFragments.pop();
        pushDemand(this.machineEdge, (float) this.currentFragment.cpuUsage());
        this.startOfFragment = offset;

        this.invalidate();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // FlowGraph Related functionality
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handle updates in supply from the Virtual Machine
     *
     * @param supplierEdge
     * @param newSupply
     */
    @Override
    public void handleSupply(FlowEdge supplierEdge, float newSupply) {
        if (newSupply == this.currentSupply) {
            return;
        }

        this.currentSupply = newSupply;
    }

    /**
     * Push a new demand to the Virtual Machine
     *
     * @param supplierEdge
     * @param newDemand
     */
    @Override
    public void pushDemand(FlowEdge supplierEdge, float newDemand) {
        if (newDemand == this.currentDemand) {
            return;
        }

        this.currentDemand = newDemand;
        this.machineEdge.pushDemand(newDemand);
    }

    /**
     * Add the connection to the Virtual Machine
     *
     * @param supplierEdge
     */
    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.machineEdge = supplierEdge;
    }

    /**
     * Handle the removal of the connection to the Virtual Machine
     * When the connection to the Virtual Machine is removed, the SimTraceWorkload is removed
     *
     * @param supplierEdge
     */
    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.stopWorkload();
    }
}
