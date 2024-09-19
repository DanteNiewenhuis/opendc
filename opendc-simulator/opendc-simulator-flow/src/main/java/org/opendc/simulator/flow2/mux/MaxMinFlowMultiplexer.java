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

package org.opendc.simulator.flow2.mux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.FlowStage;
import org.opendc.simulator.flow2.FlowStageLogic;
import org.opendc.simulator.flow2.InHandler;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.OutHandler;
import org.opendc.simulator.flow2.OutPort;

/**
 * A {@link FlowMultiplexer} implementation that distributes the available capacity of the outputs over the inputs
 * using max-min fair sharing.
 * <p>
 * The max-min fair sharing algorithm of this multiplexer ensures that each input receives a fair share of the combined
 * output capacity, but allows individual inputs to use more capacity if there is still capacity left.
 */
public final class MaxMinFlowMultiplexer implements FlowMultiplexer, FlowStageLogic {
    /**
     * Factory implementation for this implementation.
     */
    static FlowMultiplexerFactory FACTORY = MaxMinFlowMultiplexer::new;

    private final FlowStage stage;

    private float capacity = 0.f;
    private float demand = 0.f;
    private float rate = 0.f;

    private final ArrayList<InPort> demandPorts;
    private final ArrayList<Float> rates;
    private final ArrayList<OutPort> capacityPorts;

    private final MultiplexerInHandler inHandler = new MultiplexerInHandler();
    private final MultiplexerOutHandler outHandler = new MultiplexerOutHandler();

    /**
     * Construct a {@link MaxMinFlowMultiplexer} instance.
     *
     * @param graph The {@link FlowGraph} to add the multiplexer to.
     */
    public MaxMinFlowMultiplexer(FlowGraph graph) {
        this.stage = graph.newStage(this);

        this.demandPorts = new ArrayList<>(4);
        this.rates = new ArrayList<>(4);
        this.capacityPorts = new ArrayList<>(4);
    }

    @Override
    public float getCapacity() {
        return capacity;
    }

    @Override
    public float getDemand() {
        return demand;
    }

    @Override
    public float getRate() {
        return rate;
    }

    @Override
    public int getMaxInputs() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxOutputs() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long onUpdate(FlowStage ctx, long now) {
        float capacity = this.capacity;
        float demand = this.demand;
        float rate = demand;

        if (demand > capacity) {
            rate = redistributeCapacity(demandPorts, rates, capacity);
        }

        if (this.rate != rate) {
            // Only update the outputs if the output rate has changed
            this.rate = rate;

            changeRate(capacityPorts, capacity, rate);
        }

        return Long.MAX_VALUE;
    }

    @Override
    public int getInputCount() {
        return demandPorts.size();
    }

    @Override
    public InPort newInput() {
        int slot = this.demandPorts.size();

        InPort port = stage.getInPort("in_" + slot, slot);
        port.setHandler(inHandler);
        port.pull(this.capacity);

        this.demandPorts.add(port);
        this.rates.add(0.0f);

        return port;
    }

    @Override
    public void releaseInput(InPort inPort) {
        int idx = this.demandPorts.indexOf(inPort);

        this.demandPorts.remove(idx);
        this.rates.remove(idx);
        inPort.cancel(null);
    }

    @Override
    public int getOutputCount() {
        return capacityPorts.size();
    }

    @Override
    public OutPort newOutPort() {
        int slot = this.capacityPorts.size();

        OutPort port = stage.getOutPort("out_" + slot, slot);
        port.setHandler(outHandler);

        this.capacityPorts.add(port);
        return port;
    }

    @Override
    public void releaseOutput(OutPort OutPort) {
        this.capacityPorts.remove(OutPort);
        OutPort.complete();
    }

    /**
     * Helper function to redistribute the specified capacity across the InPorts.
     */
    private static float redistributeCapacity(ArrayList<InPort> InPorts, ArrayList<Float> rates, float capacity) {

        final long[] inputs = new long[InPorts.size()];

        // If the demand is higher than the capacity, we need use max-min fair sharing to distribute the
        // constrained capacity across the inputs.
        for (int i = 0; i < inputs.length; i++) {
            InPort InPort = InPorts.get(i);
            if (InPort == null) {
                break;
            }

            inputs[i] = ((long) Float.floatToRawIntBits(InPort.getDemand()) << 32) | (i & 0xFFFFFFFFL);
        }
        Arrays.sort(inputs);

        float availableCapacity = capacity;
        int inputSize = inputs.length;

        // Divide the available output capacity fairly over the inputs using max-min fair sharing
        for (int i = 0; i < inputs.length; i++) {
            long v = inputs[i];
            int slot = (int) v;
            float d = Float.intBitsToFloat((int) (v >> 32));

            if (d == 0.0) {
                continue;
            }

            float availableShare = availableCapacity / (inputSize - i);
            float r = Math.min(d, availableShare);

            rates.set(slot, r); // Update the rates
            availableCapacity -= r;
        }

        return capacity - availableCapacity;
    }

    /**
     * Helper method to change the rate of the OutPorts.
     */
    private static void changeRate(ArrayList<OutPort> OutPorts, float capacity, float rate) {
        // Divide the requests over the available capacity of the input resources fairly
        for (OutPort OutPort : OutPorts) {
            float fraction = OutPort.getCapacity() / capacity;
            OutPort.push(rate * fraction);
        }
    }

    /**
     * A {@link InHandler} implementation for the multiplexer inputs.
     */
    private class MultiplexerInHandler implements InHandler {
        @Override
        public float getRate(InPort port) {
            return rates.get(demandPorts.indexOf(port));
        }

        @Override
        public void onPush(InPort port, float demand) {
            MaxMinFlowMultiplexer.this.demand += -port.getDemand() + demand;

            rates.set(demandPorts.indexOf(port), demand);
        }

        @Override
        public void onUpstreamFinish(InPort port, Throwable cause) {
            MaxMinFlowMultiplexer.this.demand -= port.getDemand();
            releaseInput(port);
            rates.set(demandPorts.indexOf(port), 0.f);
        }
    }

    /**
     * A {@link OutHandler} implementation for the multiplexer outputs.
     */
    private class MultiplexerOutHandler implements OutHandler {
        @Override
        public void onPull(OutPort port, float capacity) {
            float newCapacity = MaxMinFlowMultiplexer.this.capacity - port.getCapacity() + capacity;
            MaxMinFlowMultiplexer.this.capacity = newCapacity;
            changeInPortCapacity(newCapacity);
        }

        @Override
        public void onDownstreamFinish(OutPort port, Throwable cause) {
            float newCapacity = MaxMinFlowMultiplexer.this.capacity - port.getCapacity();
            MaxMinFlowMultiplexer.this.capacity = newCapacity;
            releaseOutput(port);
            changeInPortCapacity(newCapacity);
        }

        private void changeInPortCapacity(float capacity) {
            for (InPort inPort : MaxMinFlowMultiplexer.this.demandPorts) {
                inPort.pull(capacity);
            }
        }
    }
}
