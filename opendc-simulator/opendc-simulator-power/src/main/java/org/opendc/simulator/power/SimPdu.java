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

package org.opendc.simulator.power;

import org.jetbrains.annotations.NotNull;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.OutPort;
import org.opendc.simulator.flow2.mux.FlowMultiplexer;
import org.opendc.simulator.flow2.mux.MaxMinFlowMultiplexer;
import org.opendc.simulator.flow2.util.FlowTransform;
import org.opendc.simulator.flow2.util.FlowTransformer;

/**
 * A model of a Power Distribution Unit (PDU).
 */
public final class SimPdu extends SimPowerInPort {
    /**
     * The {@link FlowMultiplexer} that distributes the electricity over the PDU OutPorts.
     */
    private final MaxMinFlowMultiplexer mux;

    /**
     * A {@link FlowTransformer} that applies the power loss to the PDU's power InPort.
     */
    private final FlowTransformer transformer;

    /**
     * Construct a {@link SimPdu} instance.
     *
     * @param graph The underlying {@link FlowGraph} to which the PDU belongs.
     * @param idlePower The idle power consumption of the PDU independent of the load on the PDU.
     * @param lossCoefficient The coefficient for the power loss of the PDU proportional to the square load.
     */
    public SimPdu(FlowGraph graph, float idlePower, float lossCoefficient) {
        this.mux = new MaxMinFlowMultiplexer(graph);
        this.transformer = new FlowTransformer(graph, new FlowTransform() {
            @Override
            public float apply(float value) {
                // See https://download.schneider-electric.com/files?p_Doc_Ref=SPD_NRAN-66CK3D_EN
                return value * (lossCoefficient * value + 1) + idlePower;
            }

            @Override
            public float applyInverse(float value) {
                float c = lossCoefficient;
                if (c != 0.f) {
                    return (float) (1 + Math.sqrt(4 * value * c - 4 * idlePower * c + 1)) / (2 * c);
                } else {
                    return value - idlePower;
                }
            }
        });

        graph.connect(mux.newOutPort(), transformer.getInput());
    }

    /**
     * Construct a {@link SimPdu} instance without any loss.
     *
     * @param graph The underlying {@link FlowGraph} to which the PDU belongs.
     */
    public SimPdu(FlowGraph graph) {
        this(graph, 0.f, 0.f);
    }

    /**
     * Create a new PDU OutPort.
     */
    public PowerOutPort newOutPort() {
        return new PowerOutPort(mux);
    }

    @NotNull
    @Override
    public OutPort getFlowOutPort() {
        return transformer.getOutput();
    }

    @Override
    public String toString() {
        return "SimPdu";
    }

    /**
     * A PDU OutPort.
     */
    public static final class PowerOutPort extends SimPowerOutPort implements AutoCloseable {
        private final FlowMultiplexer mux;
        private final InPort InPort;
        private boolean isClosed;

        private PowerOutPort(FlowMultiplexer mux) {
            this.mux = mux;
            this.InPort = mux.newInput();
        }

        /**
         * Remove the OutPort from the PDU.
         */
        @Override
        public void close() {
            isClosed = true;
            mux.releaseInput(InPort);
        }

        @Override
        public String toString() {
            return "SimPdu.OutPort";
        }

        @NotNull
        @Override
        protected InPort getFlowInPort() {
            if (isClosed) {
                throw new IllegalStateException("OutPort is closed");
            }
            return InPort;
        }
    }
}
