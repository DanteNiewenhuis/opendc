package org.opendc.simulator.engine;

public interface FlowSupplier {

    void handleDemand(FlowEdge consumerEdge, float newDemand);

    void pushSupply(FlowEdge consumerEdge, float newSupply);

    void addConsumerEdge(FlowEdge consumerEdge);

    void removeConsumerEdge(FlowEdge consumerEdge);

    float getCapacity();
}
