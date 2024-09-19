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
 * A model of an Uninterruptible Power Supply (UPS).
 * <p>
 * This model aggregates multiple power sources into a single source in order to ensure that power is always available.
 */
public final class SimUps extends SimPowerOutPort {
    /**
     * The {@link FlowMultiplexer} that distributes the electricity over the PDU OutPorts.
     */
    private final MaxMinFlowMultiplexer mux;

    /**
     * A {@link FlowTransformer} that applies the power loss to the PDU's power InPort.
     */
    private final FlowTransformer transformer;

    /**
     * Construct a {@link SimUps} instance.
     *
     * @param graph The underlying {@link FlowGraph} to which the UPS belongs.
     * @param idlePower The idle power consumption of the UPS independent of the load.
     * @param lossCoefficient The coefficient for the power loss of the UPS proportional to the load.
     */
    public SimUps(FlowGraph graph, float idlePower, float lossCoefficient) {
        this.mux = new MaxMinFlowMultiplexer(graph);
        this.transformer = new FlowTransformer(graph, new FlowTransform() {
            @Override
            public float apply(float value) {
                // See https://download.schneider-electric.com/files?p_Doc_Ref=SPD_NRAN-66CK3D_EN
                return value * (lossCoefficient + 1) + idlePower;
            }

            @Override
            public float applyInverse(float value) {
                return (value - idlePower) / (lossCoefficient + 1);
            }
        });

        graph.connect(transformer.getOutput(), mux.newInput());
    }

    /**
     * Construct a {@link SimUps} instance without any loss.
     *
     * @param graph The underlying {@link FlowGraph} to which the UPS belongs.
     */
    public SimUps(FlowGraph graph) {
        this(graph, 0.f, 0.f);
    }

    /**
     * Create a new UPS InPort.
     */
    public PowerInPort newInPort() {
        return new PowerInPort(mux);
    }

    @Override
    protected InPort getFlowInPort() {
        return transformer.getInput();
    }

    @Override
    public String toString() {
        return "SimUps";
    }

    /**
     * A UPS InPort.
     */
    public static final class PowerInPort extends SimPowerInPort implements AutoCloseable {
        private final FlowMultiplexer mux;
        private final OutPort OutPort;
        private boolean isClosed;

        private PowerInPort(FlowMultiplexer mux) {
            this.mux = mux;
            this.OutPort = mux.newOutPort();
        }

        /**
         * Remove the InPort from the PDU.
         */
        @Override
        public void close() {
            isClosed = true;
            mux.releaseOutput(OutPort);
        }

        @Override
        public String toString() {
            return "SimPdu.InPort";
        }

        @NotNull
        @Override
        protected OutPort getFlowOutPort() {
            if (isClosed) {
                throw new IllegalStateException("InPort is closed");
            }
            return OutPort;
        }
    }
}
