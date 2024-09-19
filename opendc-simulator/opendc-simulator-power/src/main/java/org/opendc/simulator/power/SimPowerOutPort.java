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

import org.opendc.simulator.flow2.InPort;
import org.opendc.simulator.flow2.OutPort;

/**
 * An abstract OutPort that provides a source of electricity for datacenter components.
 */
public abstract class SimPowerOutPort {
    private SimPowerInPort InPort;

    /**
     * Determine whether the OutPort is connected to a {@link SimPowerInPort}.
     *
     * @return <code>true</code> if the OutPort is connected to an InPort, <code>false</code> otherwise.
     */
    public boolean isConnected() {
        return InPort != null;
    }

    /**
     * Return the {@link SimPowerInPort} to which the OutPort is connected.
     */
    public SimPowerInPort getInPort() {
        return InPort;
    }

    /**
     * Connect the specified power [InPort] to this OutPort.
     *
     * @param InPort The InPort to connect to the OutPort.
     */
    public void connect(SimPowerInPort InPort) {
        if (isConnected()) {
            throw new IllegalStateException("OutPort already connected");
        }
        if (InPort.isConnected()) {
            throw new IllegalStateException("InPort already connected");
        }

        this.InPort = InPort;
        this.InPort.OutPort = this;

        final InPort flowInPort = getFlowInPort();
        final OutPort flowOutPort = InPort.getFlowOutPort();

        flowInPort.getGraph().connect(flowOutPort, flowInPort);
    }

    /**
     * Disconnect the connected power OutPort from this InPort
     */
    public void disconnect() {
        SimPowerInPort InPort = this.InPort;
        if (InPort != null) {
            this.InPort = null;
            assert InPort.OutPort == this : "InPort state incorrect";
            InPort.OutPort = null;

            final InPort flowInPort = getFlowInPort();
            flowInPort.getGraph().disconnect(flowInPort);
        }
    }

    /**
     * Return the flow {@link InPort} that models the consumption of a power OutPort as flow input.
     */
    protected abstract InPort getFlowInPort();
}
