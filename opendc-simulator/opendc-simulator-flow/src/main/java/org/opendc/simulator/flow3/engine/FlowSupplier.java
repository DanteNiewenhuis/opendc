package org.opendc.simulator.flow3.engine;

public interface FlowSupplier {

    void handleDemand(FlowEdge consumerEdge, float newDemand);

    void pushSupply(FlowEdge consumerEdge, float newSupply);

    void addConsumerEdge(FlowEdge consumerEdge);

    void removeConsumerEdge(FlowEdge consumerEdge);
}
