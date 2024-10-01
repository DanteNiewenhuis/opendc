package org.opendc.simulator.flow3;

import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow3.engine.FlowConsumer;
import org.opendc.simulator.flow3.engine.FlowEdge;
import org.opendc.simulator.flow3.engine.FlowGraphNew;
import org.opendc.simulator.flow3.engine.FlowNode;
import org.opendc.simulator.flow3.engine.FlowSupplier;

import java.util.ArrayList;
import java.util.Arrays;

public class Multiplexer extends FlowNode implements FlowSupplier, FlowConsumer {
    private ArrayList<FlowEdge> consumerEdges = new ArrayList<>();
    private FlowEdge supplierEdge;

    private ArrayList<Float> demands = new ArrayList<>(); // What is demanded by the consumers
    private ArrayList<Float> supplies = new ArrayList<>(); // What is supplied to the consumers

    private float totalDemand; // The total demand of all the consumers
    private float totalSupply; // The total supply from the supplier
    private float capacity; // What is the max capacity

    public Multiplexer(FlowGraphNew graph) {
        super(graph);
    }

    public float getTotalDemand() {
        return totalDemand;
    }

    public float getTotalSupply() {
        return totalSupply;
    }

    public float getCapacity() {
        return capacity;
    }


    public long onUpdate(long now) {

        float totalSupply = this.totalDemand;

        if (totalDemand > capacity) {
            totalSupply = redistributeSupply(consumerEdges, supplies, capacity);
        }

        if (this.totalSupply != totalSupply) {
            this.totalSupply = totalSupply;

            pushDemand(this.supplierEdge, this.totalSupply);
        }

        return Long.MAX_VALUE;
    }

    private static float redistributeSupply(ArrayList<FlowEdge> consumerEdges, ArrayList<Float> supplies, float capacity) {
        final long[] consumers = new long[consumerEdges.size()];

        for (int i = 0; i < consumers.length; i++) {
            FlowEdge consumer = consumerEdges.get(i);

            if (consumer == null) {
                break;
            }

            consumers[i] = ((long) Float.floatToRawIntBits(consumer.getDemand()) << 32) | (i & 0xFFFFFFFFL);
        }
        Arrays.sort(consumers);

        float availableCapacity = capacity;
        int inputSize = consumers.length;

        for (int i = 0; i < inputSize; i++) {
            long v = consumers[i];
            int slot = (int) v;
            float d = Float.intBitsToFloat((int) (v >> 32));

            if (d == 0.0) {
                continue;
            }

            float availableShare = availableCapacity / (inputSize - i);
            float r = Math.min(d, availableShare);

            supplies.set(slot, r); // Update the rates
            availableCapacity -= r;
        }

        // Return the used capacity
        return capacity - availableCapacity;
    }

    /**
     * Add a new consumer.
     * Set its demand and supply to 0.0
     */
    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        this.consumerEdges.add(consumerEdge);
        this.demands.add(0.f);
        this.supplies.add(0.f);
    }

    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.supplierEdge = supplierEdge;
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        int idx = this.consumerEdges.indexOf(consumerEdge);

        if (idx != -1) {
            return;
        }

        this.consumerEdges.remove(idx);
        this.demands.remove(idx);
        this.supplies.remove(idx);
    }

    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.supplierEdge = null;
    }

    @Override
    public void handleDemand(FlowEdge consumerEdge, float newDemand) {
        int idx = consumerEdges.indexOf(consumerEdge);

        if (idx == -1) {
            System.out.println("Error (Multiplexer): Demand pushed by an unknown consumer");
            return;
        }

        float prevDemand = demands.get(idx);
        demands.set(idx, newDemand);
        float totalDemand = this.totalDemand - prevDemand + newDemand;

        if (totalDemand != this.totalDemand) {
            this.totalDemand = totalDemand;
            this.pushDemand(this.supplierEdge, totalDemand);
        }
    }

    @Override
    public void handleSupply(FlowEdge supplierEdge, float newSupply) {
        if (newSupply == this.totalSupply) {
            return;
        }

        this.totalSupply = newSupply;
        this.invalidate();
    }

    @Override
    public void pushDemand(FlowEdge supplierEdge, float newDemand) {
        this.supplierEdge.pushDemand(newDemand);
    }

    @Override
    public void pushSupply(FlowEdge consumerEdge, float newSupply) {
        int idx = consumerEdges.indexOf(consumerEdge);

        if (idx == -1) {
            System.out.println("Error (Multiplexer): pushing supply to an unknown consumer");
        }

        supplies.set(idx, newSupply);
        consumerEdge.pushSupply(newSupply);
    }
}
