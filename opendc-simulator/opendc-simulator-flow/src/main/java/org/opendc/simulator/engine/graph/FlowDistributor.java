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

package org.opendc.simulator.engine.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.opendc.common.ResourceType;
import org.opendc.simulator.engine.engine.FlowEngine;
import org.opendc.simulator.engine.graph.distributionPolicies.DistributionPolicy;
import org.opendc.simulator.engine.graph.distributionPolicies.MaxMinFairnessPolicy;

public class FlowDistributor extends FlowNode implements FlowSupplier, FlowConsumer {
    private final ArrayList<FlowEdge> consumerEdges = new ArrayList<>();
    private FlowEdge supplierEdge;

    private final ArrayList<Double> incomingDemands = new ArrayList<>(); // What is demanded by the consumers
    private final ArrayList<Double> outgoingSupplies = new ArrayList<>(); // What is supplied to the consumers

    private double totalIncomingDemand; // The total demand of all the consumers
    private double currentIncomingSupply; // The current supply provided by the supplier

    private boolean outgoingDemandUpdateNeeded = false;
    private Set<Integer> updatedDemands = new HashSet<>(); // Array of consumers that updated their demand in this cycle

    private boolean overloaded = false;

    private double capacity; // What is the max capacity. Can probably be removed
    private DistributionPolicy distributionPolicy;

    public FlowDistributor(FlowEngine engine) {
        super(engine);
        this.distributionPolicy = new MaxMinFairnessPolicy();
    }

    public FlowDistributor(FlowEngine engine, DistributionPolicy distributionPolicy) {
        super(engine);
        this.distributionPolicy = distributionPolicy;
    }

    public double getTotalIncomingDemand() {
        return totalIncomingDemand;
    }

    public double getCurrentIncomingSupply() {
        return currentIncomingSupply;
    }

    public double getCapacity() {
        return capacity;
    }

    public long onUpdate(long now) {

        // Check if current supply is different from total demand
        if (this.outgoingDemandUpdateNeeded) {
            this.updateOutgoingDemand();

            return Long.MAX_VALUE;
        }

        if (!this.outgoingSupplies.isEmpty()) {
            this.updateOutgoingSupplies();
        }

        return Long.MAX_VALUE;
    }

    private void updateOutgoingDemand() {
        this.pushOutgoingDemand(this.supplierEdge, this.totalIncomingDemand);

        this.outgoingDemandUpdateNeeded = false;

        this.invalidate();
    }

    // TODO: This should probably be moved to the distribution strategy
    private void updateOutgoingSupplies() {

        // If the demand is higher than the current supply, the system is overloaded.
        // The available supply is distributed based on the current distribution function.
        if (this.totalIncomingDemand > this.currentIncomingSupply) {
            this.overloaded = true;

            double[] supplies =
                    this.distributionPolicy.distributeSupply(this.incomingDemands, this.currentIncomingSupply);

            for (int idx = 0; idx < this.consumerEdges.size(); idx++) {
                this.pushOutgoingSupply(this.consumerEdges.get(idx), supplies[idx], this.getConsumerResourceType());
            }

        } else {

            // If the distributor was overloaded before, but is not anymore:
            //      provide all consumers with their demand
            if (this.overloaded) {
                for (int idx = 0; idx < this.consumerEdges.size(); idx++) {
                    if (!Objects.equals(this.outgoingSupplies.get(idx), this.incomingDemands.get(idx))) {
                        this.pushOutgoingSupply(
                                this.consumerEdges.get(idx),
                                this.incomingDemands.get(idx),
                                this.getConsumerResourceType());
                    }
                }
                this.overloaded = false;
            }

            // Update the supplies of the consumers that changed their demand in the current cycle
            else {
                for (int idx : this.updatedDemands) {
                    this.pushOutgoingSupply(
                            this.consumerEdges.get(idx), this.incomingDemands.get(idx), this.getConsumerResourceType());
                }
            }
        }

        this.updatedDemands.clear();
    }

    /**
     * Add a new consumer.
     * Set its demand and supply to 0.0
     */
    @Override
    public void addConsumerEdge(FlowEdge consumerEdge) {
        consumerEdge.setConsumerIndex(this.consumerEdges.size());

        this.consumerEdges.add(consumerEdge);
        this.incomingDemands.add(0.0);
        this.outgoingSupplies.add(0.0);
    }

    @Override
    public void addSupplierEdge(FlowEdge supplierEdge) {
        this.supplierEdge = supplierEdge;
        this.capacity = supplierEdge.getCapacity();
        this.currentIncomingSupply = 0;
    }

    @Override
    public void removeConsumerEdge(FlowEdge consumerEdge) {
        int idx = consumerEdge.getConsumerIndex();

        if (idx == -1) {
            return;
        }

        this.totalIncomingDemand -= consumerEdge.getDemand();

        // Remove idx from consumers that updated their demands
        this.updatedDemands.remove(idx);

        this.consumerEdges.remove(idx);
        this.incomingDemands.remove(idx);
        this.outgoingSupplies.remove(idx);

        // update the consumer index for all consumerEdges higher than this.
        for (int i = idx; i < this.consumerEdges.size(); i++) {
            FlowEdge other = this.consumerEdges.get(i);

            other.setConsumerIndex(other.getConsumerIndex() - 1);
        }

        HashSet<Integer> newUpdatedDemands = new HashSet<>();

        for (int idx_other : this.updatedDemands) {
            if (idx_other > idx) {
                newUpdatedDemands.add(idx_other - 1);
            } else {
                newUpdatedDemands.add(idx_other);
            }
        }

        this.updatedDemands = newUpdatedDemands;

        this.outgoingDemandUpdateNeeded = true;
        this.invalidate();
    }

    @Override
    public void removeSupplierEdge(FlowEdge supplierEdge) {
        this.supplierEdge = null;
        this.capacity = 0;
        this.currentIncomingSupply = 0;

        this.updatedDemands.clear();

        this.closeNode();
    }

    @Override
    public void handleIncomingDemand(FlowEdge consumerEdge, double newDemand) {
        int idx = consumerEdge.getConsumerIndex();

        if (idx == -1) {
            System.out.println("Error (FlowDistributor): Demand pushed by an unknown consumer");
            return;
        }

        // Update the total demand (This is cheaper than summing over all demands)
        double prevDemand = incomingDemands.get(idx);

        incomingDemands.set(idx, newDemand);
        this.totalIncomingDemand += (newDemand - prevDemand);

        this.updatedDemands.add(idx);

        this.outgoingDemandUpdateNeeded = true;
        this.invalidate();
    }

    @Override
    public void handleIncomingDemand(FlowEdge consumerEdge, double newDemand, ResourceType resourceType) {
        if (resourceType != this.getSupplierResourceType()) {
            throw new IllegalArgumentException("Resource type " + resourceType
                    + " does not match distributor resource type " + this.getSupplierResourceType());
        }
        this.handleIncomingDemand(consumerEdge, newDemand);
    }

    @Override
    public void handleIncomingSupply(FlowEdge supplierEdge, double newSupply) {
        this.currentIncomingSupply = newSupply;

        this.invalidate();
    }

    @Override
    public void pushOutgoingDemand(FlowEdge supplierEdge, double newDemand) {
        this.supplierEdge.pushDemand(newDemand, false, this.getSupplierResourceType());
    }

    @Override
    public void pushOutgoingSupply(FlowEdge consumerEdge, double newSupply) {
        int idx = consumerEdge.getConsumerIndex();

        if (idx == -1) {
            System.out.println("Error (FlowDistributor): pushing supply to an unknown consumer");
        }

        if (outgoingSupplies.get(idx) == newSupply) {
            return;
        }

        outgoingSupplies.set(idx, newSupply);
        consumerEdge.pushSupply(newSupply, false, this.getSupplierResourceType());
        consumerEdge.pushSupply(newSupply, false, this.getSupplierResourceType());
    }

    @Override
    public Map<FlowEdge.NodeType, List<FlowEdge>> getConnectedEdges() {
        List<FlowEdge> supplyingEdges = (this.supplierEdge != null) ? List.of(this.supplierEdge) : List.of();

        return Map.of(FlowEdge.NodeType.CONSUMING, supplyingEdges, FlowEdge.NodeType.SUPPLYING, this.consumerEdges);
    }

    @Override
    public ResourceType getSupplierResourceType() {
        return this.supplierEdge.getSupplierResourceType();
    }

    @Override
    public ResourceType getConsumerResourceType() {
        return this.consumerEdges.getFirst().getConsumerResourceType();
    }
}
