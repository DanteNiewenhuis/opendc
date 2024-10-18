package org.opendc.simulator.compute.workload;

import org.opendc.simulator.engine.FlowConsumer;
import org.opendc.simulator.engine.FlowEdge;
import org.opendc.simulator.engine.FlowGraphNew;
import org.opendc.simulator.engine.FlowNode;
import org.opendc.simulator.engine.FlowSupplier;

import java.util.LinkedList;

public class SimTraceWorkload extends SimWorkload implements FlowConsumer {
    private LinkedList<TraceFragment> remainingFragments;
    private int fragmentIndex;

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

    public TraceFragment getNextFragment() {
        this.currentFragment = this.remainingFragments.pop();
        this.fragmentIndex++;

        return this.currentFragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public SimTraceWorkload(FlowSupplier supplier, TraceWorkload workload, long now) {
        super(((FlowNode)supplier).getGraph());

        this.snapshot = workload;
        this.remainingFragments = new LinkedList<>(workload.getFragments());
        this.fragmentIndex = 0;

        final FlowGraphNew graph = ((FlowNode)supplier).getGraph();
        graph.addEdge(this, supplier);

        this.currentFragment = this.getNextFragment();
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
            currentFragment = this.getNextFragment();
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
        long checkpointDuration = 1000L; // TODO: connect to front-end
        TraceFragment snapshotFragment = new TraceFragment(checkpointDuration, 123456, 1);
        this.remainingFragments.addFirst(snapshotFragment);

        this.fragmentIndex = -1;
        this.currentFragment = getNextFragment();
        pushDemand(this.machineEdge, (float) this.currentFragment.cpuUsage());
        this.startOfFragment = now;

        this.invalidate();
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
