/*
 * Copyright (c) 2022 AtLarge Research
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

package org.opendc.simulator.flow2;

/**
 * A representation of a flow network. A flow network is a directed graph where each edge has a capacity and receives an
 * amount of flow that cannot exceed the edge's capacity.
 */
public interface FlowGraph {
    /**
     * Return the {@link FlowEngine} driving the simulation of the graph.
     */
    FlowEngine getEngine();

    /**
     * Create a new {@link FlowStage} representing a node in the flow network.
     *
     * @param logic The logic for handling the events of the stage.
     */
    FlowStage newStage(FlowStageLogic logic);

    /**
     * Add an edge between the specified OutPort port and InPort port in this graph.
     *
     * @param OutPort The OutPort of the source from which the flow originates.
     * @param InPort The InPort of the sink that should receive the flow.
     */
    void connect(OutPort OutPort, InPort InPort);

    /**
     * Disconnect the specified {@link OutPort} (if connected).
     *
     * @param OutPort The OutPort to disconnect.
     */
    void disconnect(OutPort OutPort);

    /**
     * Disconnect the specified {@link InPort} (if connected).
     *
     * @param InPort The InPort to disconnect.
     */
    void disconnect(InPort InPort);
}
