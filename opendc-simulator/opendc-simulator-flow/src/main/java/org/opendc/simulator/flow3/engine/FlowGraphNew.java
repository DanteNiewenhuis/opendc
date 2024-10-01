package org.opendc.simulator.flow3.engine;

import java.util.ArrayList;
import java.util.HashMap;

public class FlowGraphNew {
    private final FlowEngineNew engine;
    private final ArrayList<FlowNode> nodes = new ArrayList<>();
    private final ArrayList<FlowEdge> edges = new ArrayList<>();
    private final HashMap<FlowNode, ArrayList<FlowEdge>> nodeToEdge = new HashMap<>();

    public FlowGraphNew(FlowEngineNew engine) {
        this.engine = engine;
    }

    /**
     * Return the {@link FlowEngineNew} driving the simulation of the graph.
     */
    public FlowEngineNew getEngine() {
        return engine;
    }

    /**
     * Create a new {@link FlowNode} representing a node in the flow network.
     */
    public void addNode(FlowNode node) {
        if (nodes.contains(node)) {
            System.out.println("Node already exists");
        }
        nodes.add(node);
        nodeToEdge.put(node, new ArrayList<>());
        long now = this.engine.getClock().millis();
        node.invalidate(now);
    }

    /**
     * Internal method to remove the specified {@link FlowNode} from the graph.
     */
    public void removeNode(FlowNode node) {

        // Remove all edges connected to node
        final ArrayList<FlowEdge> connectedEdges = nodeToEdge.get(node);
        while (connectedEdges.size() > 0) {
            removeEdge(connectedEdges.get(0));
        }

        nodeToEdge.remove(node);

        // remove the node
        nodes.remove(node);
    }

    /**
     * Add an edge between the specified consumer and supplier in this graph.
     */
    public void addEdge(FlowConsumer flowConsumer, FlowSupplier flowSupplier) {
        // Check if the consumer and supplier are both FlowNodes
        if (!(flowConsumer instanceof FlowNode)){
            throw new IllegalArgumentException("Flow consumer is not a FlowNode");
        }
        if (!(flowSupplier instanceof FlowNode)){
            throw new IllegalArgumentException("Flow consumer is not a FlowNode");
        }

        // Check of the consumer and supplier are present in this graph
        if (!(this.nodes.contains((FlowNode) flowConsumer))) {
            throw new IllegalArgumentException("The consumer is not a node in this graph");
        }
        if (!(this.nodes.contains((FlowNode) flowSupplier))) {
            throw new IllegalArgumentException("The consumer is not a node in this graph");
        }

        final FlowEdge flowEdge = new FlowEdge(flowConsumer, flowSupplier);

        edges.add(flowEdge);

        nodeToEdge.get((FlowNode) flowConsumer).add(flowEdge);
        nodeToEdge.get((FlowNode) flowSupplier).add(flowEdge);
    }

    public void removeEdge(FlowEdge flowEdge) {
        final FlowConsumer consumer = flowEdge.getConsumer();
        final FlowSupplier supplier = flowEdge.getSupplier();
        nodeToEdge.get((FlowNode) consumer).remove(flowEdge);
        nodeToEdge.get((FlowNode) supplier).remove(flowEdge);

        edges.remove(flowEdge);
        flowEdge.close();
    }
}
