package org.opendc.simulator.compute.v2.workload;

import org.opendc.simulator.compute.old.workload.SimTraceFragment;
import org.opendc.simulator.flow3.engine.FlowConsumer;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;
import org.opendc.simulator.flow3.engine.FlowSupplier;

import java.util.LinkedList;

public class SimTraceWorkloadNew extends SimWorkloadNew implements FlowConsumer {
    private LinkedList<SimTraceFragment> fragments;

    private SimTraceFragment currentFragment;
    private long startOfFragment;

    private FlowEdge machineEdge;
    private float currentDemand;
    private float currentSupply;

    private long checkpointInterval = 0;
    private long checkpointDuration = 0;
    private double checkpointIntervalScaling = 1.0;

    private TraceWorkload snapshot;

    public SimTraceWorkloadNew(FlowSupplier supplier, TraceWorkload workload, long now) {
        super(((FlowNode)supplier).getGraph());

        this.snapshot = workload;
        this.fragments = workload.getFragments();

        final FlowGraphNew graph = ((FlowNode)supplier).getGraph();
        graph.addEdge(this, supplier);

        this.currentFragment = this.fragments.pop();
        pushDemand(machineEdge, (float) this.currentFragment.cpuUsage());
        this.startOfFragment = now;
    }

    public long getPassedTime(long now) {
        return now - this.startOfFragment;
    }

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
            if (this.fragments.isEmpty()) {
                doStop();
                return Long.MAX_VALUE;
            }

            passedTime = passedTime - duration;

            // get next Fragment
            currentFragment = this.fragments.pop();
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
    public void onStop() {

    }

    private void doStop() {
        // Close the FlowNode, and remove it from the graph
        this.close(null);

        fragments = null;
        currentFragment = null;
    }

    public void makeSnapshot(long now){
        final LinkedList<SimTraceFragment> newFragments = this.fragments;

        SimTraceFragment currentFragment = this.currentFragment;
        long passedTime = getPassedTime(now);
        long remainingTime = currentFragment.duration() - passedTime;

        // Alter the current fragment to have the duration as the remaining time
        if (remainingTime > 0) {
            SimTraceFragment newFragment =
                new SimTraceFragment(remainingTime, currentFragment.cpuUsage(), currentFragment.coreCount());

            newFragments.addFirst(newFragment);
        }

        // A Workload with the new fragments is the new snapshot
        this.snapshot = new TraceWorkload(newFragments, this.checkpointInterval, this.checkpointDuration, this.checkpointIntervalScaling);

        // Add a processing Fragment to the start
        long checkpointDuration = 1000L; // TODO: connect to front-end
        SimTraceFragment snapshotFragment = new SimTraceFragment(checkpointDuration, 123456, 1);
        newFragments.addFirst(snapshotFragment);

        this.updateFragments(newFragments, now);
    }

    public void updateFragments(LinkedList<SimTraceFragment> newFragments, long offset) {
        this.fragments = newFragments;

        // Start the first Fragment
        this.currentFragment = this.fragments.element();
        pushDemand(this.machineEdge, (float) this.currentFragment.cpuUsage());
        this.startOfFragment = offset;

        this.invalidate();
    }

    public TraceWorkload getSnapshot(){
        return snapshot;
    }

    @Override
    void createCheckpointModel() {

    }

    @Override
    long getCheckpointInterval() {
        return 0;
    }

    @Override
    long getCheckpointDuration() {
        return 0;
    }

    @Override
    double getCheckpointIntervalScaling() {
        return 0;
    }

    @Override
    public void handleSupply(FlowEdge supplierEdge, float newSupply) {
        if (newSupply == this.currentSupply) {
            return;
        }

        this.currentSupply = newSupply;
    }

    @Override
    public void pushDemand(FlowEdge supplierEdge, float newDemand) {
        if (newDemand == this.currentDemand) {
            return;
        }

        this.currentDemand = newDemand;
        this.machineEdge.pushDemand(newDemand);
    }

    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.machineEdge = supplierEdge;
    }

    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.machineEdge = null;
    }
}
