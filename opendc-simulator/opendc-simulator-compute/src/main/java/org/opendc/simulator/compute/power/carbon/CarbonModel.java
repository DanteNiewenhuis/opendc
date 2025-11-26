/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.compute.power.carbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opendc.simulator.compute.power.SimPowerSource;
import org.opendc.simulator.compute.power.carbon.CarbonFragments.CarbonOpenDCFragment;
import org.opendc.simulator.engine.engine.FlowEngine;
import org.opendc.simulator.engine.graph.FlowEdge;
import org.opendc.simulator.engine.graph.FlowNode;

/**
 * CarbonModel used to provide the Carbon Intensity of a {@link SimPowerSource}
 * A CarbonModel is based on a list of {@link CarbonOpenDCFragment} that define the carbon intensity at specific time frames.
 */
public abstract class CarbonModel extends FlowNode {

    protected final ArrayList<CarbonReceiver> receivers = new ArrayList<>();
    protected final long startTime; // The absolute timestamp on which the workload started

    /**
     * Construct a CarbonModel
     *
     * @param engine The {@link FlowEngine} the node belongs to
     * @param carbonFragments A list of Carbon Fragments defining the carbon intensity at different time frames
     * @param startTime The start time of the simulation. This is used to go from relative time (used by the clock)
     *                  to absolute time (used by carbon fragments).
     */
    public CarbonModel(FlowEngine engine, List<CarbonOpenDCFragment> carbonFragments, long startTime) {
        super(engine);

        this.startTime = startTime;
    }

    public void close() {
        for (CarbonReceiver receiver : receivers) {
            receiver.removeCarbonModel(this);
        }

        receivers.clear();

        this.closeNode();
    }

    /**
     * Convert the given relative time to the absolute time by adding the start of workload
     */
    protected long getAbsoluteTime(long time) {
        return time + startTime;
    }

    /**
     * Convert the given absolute time to the relative time by subtracting the start of workload
     */
    protected long getRelativeTime(long time) {
        return time - startTime;
    }

    /**
     * Traverse the fragments to find the fragment that matches the given absoluteTime
     */
    protected abstract void findCorrectFragment(long absoluteTime);

    private void pushCarbonIntensity(double carbonIntensity) {
        for (CarbonReceiver receiver : this.receivers) {
            receiver.updateCarbonIntensity(carbonIntensity);
        }
    }

    public abstract void addReceiver(CarbonReceiver receiver);

    public abstract double[] getForecast(int forecastSize);

    @Override
    public Map<FlowEdge.NodeType, List<FlowEdge>> getConnectedEdges() {
        return Map.of();
    }
}
