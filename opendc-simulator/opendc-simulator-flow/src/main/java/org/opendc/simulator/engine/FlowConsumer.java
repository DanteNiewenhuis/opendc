package org.opendc.simulator.engine;

public interface FlowConsumer {

    void handleSupply(FlowEdge supplierEdge, float newSupply);

    void pushDemand(FlowEdge supplierEdge, float newDemand);

    void addSupplierEdge(FlowEdge supplierEdge);

    void removeSupplierEdge(FlowEdge supplierEdge);
}
