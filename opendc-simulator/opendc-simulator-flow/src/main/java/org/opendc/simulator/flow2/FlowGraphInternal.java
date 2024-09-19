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
 * Interface implemented by {@link FlowGraph} implementations.
 */
interface FlowGraphInternal extends FlowGraph {
    /**
     * Internal method to remove the specified {@link FlowStage} from the graph.
     */
    void detach(FlowStage stage);

    /**
     * Helper method to connect an OutPort to an InPort.
     */
    static void connect(FlowGraph graph, OutPort OutPort, InPort InPort) {
        if (!(OutPort instanceof OutPort) || !(InPort instanceof InPort)) {
            throw new IllegalArgumentException("Invalid OutPort or InPort passed to graph");
        }

        InPort inPort = (InPort) InPort;
        OutPort outPort = (OutPort) OutPort;

        if (!graph.equals(outPort.getGraph()) || !graph.equals(inPort.getGraph())) {
            throw new IllegalArgumentException("OutPort or InPort does not belong to graph");
        } else if (outPort.input != null || inPort.output != null) {
            throw new IllegalStateException("InPort or OutPort already connected");
        }

        outPort.input = inPort;
        inPort.output = outPort;

        inPort.connect();
        outPort.connect();
    }

    /**
     * Helper method to disconnect an OutPort.
     */
    static void disconnect(FlowGraph graph, OutPort OutPort) {
        if (!(OutPort instanceof OutPort)) {
            throw new IllegalArgumentException("Invalid OutPort passed to graph");
        }

        OutPort outPort = (OutPort) OutPort;

        if (!graph.equals(outPort.getGraph())) {
            throw new IllegalArgumentException("OutPort or InPort does not belong to graph");
        }

        outPort.cancel(null);
        outPort.complete();
    }

    /**
     * Helper method to disconnect an InPort.
     */
    static void disconnect(FlowGraph graph, InPort InPort) {
        if (!(InPort instanceof InPort)) {
            throw new IllegalArgumentException("Invalid OutPort passed to graph");
        }

        InPort inPort = (InPort) InPort;

        if (!graph.equals(inPort.getGraph())) {
            throw new IllegalArgumentException("OutPort or InPort does not belong to graph");
        }

        inPort.finish(null);
        inPort.cancel(null);
    }
}
