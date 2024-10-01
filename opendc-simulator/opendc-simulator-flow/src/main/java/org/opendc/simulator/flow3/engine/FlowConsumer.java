package org.opendc.simulator.flow3.engine;

public interface FlowConsumer {

    void handleSupply(FlowEdge supplierEdge, float newSupply);

    void pushDemand(FlowEdge supplierEdge, float newDemand);

    void addSupplierEdge(FlowEdge supplierEdge);

    void removeSupplierEdge(FlowEdge supplierEdge);
}
