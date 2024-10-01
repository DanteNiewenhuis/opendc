package org.opendc.simulator.flow3.engine;

/**
 * An edge that connects two FlowStages.
 * A connection between FlowStages always consist of a FlowStage that demands
 * something, and a FlowStage that Delivers something
 * For instance, this could be the connection between a workload, and its machine
 */
public class FlowEdge {
    private FlowConsumer consumer;
    private FlowSupplier supplier;

    private float demand = 0.0f;
    private float supply = 0.0f;

    public FlowEdge(FlowConsumer consumer, FlowSupplier supplier) {
        if (!(consumer instanceof FlowNode)){
            throw new IllegalArgumentException("Flow consumer is not a FlowNode");
        }
        if (!(supplier instanceof FlowNode)){
            throw new IllegalArgumentException("Flow consumer is not a FlowNode");
        }

        this.consumer = consumer;
        this.supplier = supplier;

        this.consumer.addSupplierEdge(this);
        this.supplier.addConsumerEdge(this);
    }

    public void close() {
        if (this.consumer != null) {
            this.consumer.removeSupplierEdge(this);
            this.consumer = null;
        }

        if (this.supplier != null) {
            this.supplier.removeConsumerEdge(this);
            this.supplier = null;
        }
    }

    public FlowEdge() {};

    public FlowConsumer getConsumer() {
        return consumer;
    }
    public FlowSupplier getSupplier() {
        return supplier;
    }

    public float getDemand() {
        return this.demand;
    }
    public float getSupply() {
        return this.supply;
    }

    /**
     * Push new demand from the Consumer to the Supplier
     */
    public void pushDemand(float newDemand) {
        if (newDemand == this.demand) {
            return;
        }

        this.demand = newDemand;
        supplier.handleDemand(this, newDemand);
    }

    /**
     * Push new supply from the Supplier to the Consumer
     */
    public void pushSupply(float newSupply) {
        if (newSupply == this.supply) {
            return;
        }

        this.supply = newSupply;
        consumer.handleSupply(this, newSupply);
    }
}
