package org.opendc.simulator.compute.power.carbon.CarbonModels;

import org.opendc.simulator.compute.power.carbon.CarbonFragment;
import org.opendc.simulator.compute.power.carbon.CarbonFragments.CarbonEBAFragment;
import org.opendc.simulator.compute.power.carbon.CarbonFragments.CarbonOpenDCFragment;
import org.opendc.simulator.compute.power.carbon.CarbonModel;
import org.opendc.simulator.compute.power.carbon.CarbonReceiver;
import org.opendc.simulator.engine.engine.FlowEngine;
import org.opendc.simulator.engine.graph.FlowEdge;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CarbonEBAModel extends CarbonModel {
    private final List<CarbonEBAFragment> fragments;
    private CarbonEBAFragment current_fragment;

    private int fragment_index;

    /**
     * Construct a CarbonModel
     *
     * @param engine The {@link FlowEngine} the node belongs to
     * @param carbonFragments A list of Carbon Fragments defining the carbon intensity at different time frames
     * @param startTime The start time of the simulation. This is used to go from relative time (used by the clock)
     *                  to absolute time (used by carbon fragments).
     */
    public CarbonEBAModel(FlowEngine engine, List<CarbonEBAFragment> carbonFragments, long startTime) {
        super(engine, startTime);

        this.fragments = carbonFragments;

        this.fragment_index = 0;
        this.current_fragment = this.fragments.get(this.fragment_index);
        this.pushCarbonIntensity(this.current_fragment.getCarbonIntensity());
    }

    /**
     * Traverse the fragments to find the fragment that matches the given absoluteTime
     */
    @Override
    protected void findCorrectFragment(long absoluteTime) {

        // Traverse to the previous fragment, until you reach the correct fragment
        while (absoluteTime < this.current_fragment.getStartTime()) {
            this.current_fragment = fragments.get(--this.fragment_index);
        }

        // Traverse to the next fragment, until you reach the correct fragment
        while (absoluteTime >= this.current_fragment.getEndTime()) {
            this.current_fragment = fragments.get(++this.fragment_index);
        }
    }

    @Override
    public long onUpdate(long now) {
        long absolute_time = getAbsoluteTime(now);

        // Check if the current fragment is still the correct fragment,
        // Otherwise, find the correct fragment.
        if ((absolute_time < current_fragment.getStartTime()) || (absolute_time >= current_fragment.getEndTime())) {
            this.findCorrectFragment(absolute_time);

            pushCarbonIntensity(current_fragment.getCarbonIntensity());
        }

        // Update again at the end of this fragment
        return getRelativeTime(current_fragment.getEndTime());
    }

    private void pushCarbonIntensity(double carbonIntensity) {
        for (CarbonReceiver receiver : this.receivers) {
            receiver.updateCarbonIntensity(carbonIntensity);
        }
    }

    public void addReceiver(CarbonReceiver receiver) {
        this.receivers.add(receiver);

        receiver.setCarbonModel(this);

        receiver.updateCarbonIntensity(this.current_fragment.getCarbonIntensity());
    }

    public double[] getForecast(int forecastSize) {
        return this.fragments
            .subList(
                Math.min(this.fragment_index + 1, this.fragments.size() - 1),
                Math.min(this.fragment_index + forecastSize, this.fragments.size()))
            .stream()
            .mapToDouble(CarbonEBAFragment::getCarbonIntensity)
            .toArray();
    }

    public Map<Integer, Long> getAvailablePowers() {
        return this.current_fragment.getAvailablePowers();
    }

    @Override
    public Map<FlowEdge.NodeType, List<FlowEdge>> getConnectedEdges() {
        return Map.of();
    }
}
