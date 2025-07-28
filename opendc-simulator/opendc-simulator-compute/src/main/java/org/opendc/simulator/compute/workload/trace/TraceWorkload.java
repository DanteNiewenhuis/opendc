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

package org.opendc.simulator.compute.workload.trace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.opendc.common.ResourceType;
import org.opendc.common.util.ResizeableDoubleArray;
import org.opendc.simulator.compute.machine.SimMachine;
import org.opendc.simulator.compute.workload.SimWorkload;
import org.opendc.simulator.compute.workload.Workload;
import org.opendc.simulator.compute.workload.trace.scaling.ScalingPolicy;
import org.opendc.simulator.engine.graph.FlowSupplier;

public class TraceWorkload implements Workload {
    private final long checkpointInterval;
    private final long checkpointDuration;
    private final double checkpointIntervalScaling;
    private final double maxCpuDemand;
    private final double maxGpuDemand;
    private final int maxGpuMemoryDemand;
    private final int taskId;
    private final Set<ResourceType> resourceTypes;

    private final ArrayList<TraceFragment> fragments;

    private int fragmentIndex = 0;

    private final long[] fragmentDurations;
    private final Map<ResourceType, ResizeableDoubleArray> resourceUsages = new HashMap<>();

    public ScalingPolicy getScalingPolicy() {
        return scalingPolicy;
    }

    private final ScalingPolicy scalingPolicy;

    public TraceWorkload(
            ArrayList<TraceFragment> fragments,
            long checkpointInterval,
            long checkpointDuration,
            double checkpointIntervalScaling,
            ScalingPolicy scalingPolicy,
            int taskId,
            Set<ResourceType> resourceTypes) {
        this.fragments = fragments;
        this.resourceTypes = resourceTypes;

        this.fragmentDurations = new long[fragments.size()];

        for (ResourceType resourceType : this.resourceTypes) {
            this.resourceUsages.put(resourceType, new ResizeableDoubleArray(fragments.size()));
        }

        int i = 0;
        for (TraceFragment fragment : fragments) {
            this.fragmentDurations[i] = fragment.getDuration();
            for (ResourceType resourceType : resourceTypes) {
                this.resourceUsages.get(resourceType).add(fragment.getCpuUsage());
            }
            i++;
        }

        this.checkpointInterval = checkpointInterval;
        this.checkpointDuration = checkpointDuration;
        this.checkpointIntervalScaling = checkpointIntervalScaling;
        this.scalingPolicy = scalingPolicy;
        this.taskId = taskId;

        // TODO: remove if we decide not to use it.
        this.maxCpuDemand = fragments.stream()
                .max(Comparator.comparing(TraceFragment::getCpuUsage))
                .get()
                .getResourceUsage(ResourceType.CPU);
        this.maxGpuDemand = fragments.stream()
                .max(Comparator.comparing(TraceFragment::getGpuUsage))
                .get()
                .getResourceUsage(ResourceType.GPU);
        this.maxGpuMemoryDemand = 0; // TODO: add GPU memory demand to the trace fragments


    }

    public ArrayList<TraceFragment> getFragments() {
        return fragments;
    }

    public TraceFragment getNextFragment() {
        Map<ResourceType, Double> currentResourceUsages = new HashMap<>();
        for (ResourceType resourceType : this.resourceTypes) {
            currentResourceUsages.put(resourceType, this.resourceUsages.get(resourceType).get(fragmentIndex));
        }

        fragmentIndex++;

        return new TraceFragment(this.fragmentDurations[fragmentIndex], currentResourceUsages);

//        fragment.setCpuUsage(this.fragmentCpuUsages[fragmentIndex]);
//
//
//
//        if (Arrays.asList(resourceTypes).contains(ResourceType.GPU)) {
//            return new TraceFragment(this.fragmentDurations[fragmentIndex], this.fragmentCpuUsages[fragmentIndex],
//                this.fragmentGpuUsages[fragmentIndex++]);
//        }
//        return new TraceFragment(this.fragmentDurations[fragmentIndex], this.fragmentCpuUsages[fragmentIndex++]);
    }

    public boolean isCompleted() {
        return fragmentIndex >= this.fragmentDurations.length;
    }

    @Override
    public long checkpointInterval() {
        return checkpointInterval;
    }

    @Override
    public long checkpointDuration() {
        return checkpointDuration;
    }

    @Override
    public double checkpointIntervalScaling() {
        return checkpointIntervalScaling;
    }

    public double getMaxCpuDemand() {
        return maxCpuDemand;
    }

    public double getMaxGpuDemand() {
        return maxGpuDemand;
    }

    public int getMaxGpuMemoryDemand() {
        return maxGpuMemoryDemand;
    }

    public int getTaskId() {
        return taskId;
    }

    public void removeFragments(int numberOfFragments) {
        if (numberOfFragments <= 0) {
            return;
        }
        this.fragments.subList(0, numberOfFragments).clear();
    }

    public void addFirst(TraceFragment fragment) {
        this.fragments.addFirst(fragment);
    }

    public Set<ResourceType> getResourceTypes() {
        return this.resourceTypes;
    }

    @Override
    public SimWorkload startWorkload(FlowSupplier supplier) {
        return new SimTraceWorkload(supplier, this);
    }

    @Override
    public SimWorkload startWorkload(List<FlowSupplier> supplier, SimMachine machine, Consumer<Exception> completion) {
        return new SimTraceWorkload(supplier, this);
    }

    public static Builder builder(
            long checkpointInterval,
            long checkpointDuration,
            double checkpointIntervalScaling,
            ScalingPolicy scalingPolicy,
            int taskId) {
        return new Builder(checkpointInterval, checkpointDuration, checkpointIntervalScaling, scalingPolicy, taskId);
    }

    public static final class Builder {
        private final ArrayList<TraceFragment> fragments;
        private final long checkpointInterval;
        private final long checkpointDuration;
        private final double checkpointIntervalScaling;
        private final ScalingPolicy scalingPolicy;
        private final int taskId;
        private final Set<ResourceType> resourceTypes = new HashSet<>(1);

        /**
         * Construct a new {@link Builder} instance.
         */
        private Builder(
                long checkpointInterval,
                long checkpointDuration,
                double checkpointIntervalScaling,
                ScalingPolicy scalingPolicy,
                int taskId) {
            this.fragments = new ArrayList<>();
            this.checkpointInterval = checkpointInterval;
            this.checkpointDuration = checkpointDuration;
            this.checkpointIntervalScaling = checkpointIntervalScaling;
            this.scalingPolicy = scalingPolicy;
            this.taskId = taskId;
        }

        /**
         * Add a fragment to the trace.
         *
         * @param duration The timestamp at which the fragment ends (in epoch millis).
         * @param cpuUsage The CPU usage at this fragment.
         * @param gpuUsage The GPU usage at this fragment.
         * @param gpuMemoryUsage The GPU memory usage at this fragment.
         */
        public void add(long duration, double cpuUsage, double gpuUsage, int gpuMemoryUsage) {
            if (cpuUsage > 0.0) {
                this.resourceTypes.add(ResourceType.CPU);
            }
            if (gpuUsage > 0.0) {
                this.resourceTypes.add(ResourceType.GPU);
            }
            fragments.add(fragments.size(), new TraceFragment(duration, cpuUsage, gpuUsage, gpuMemoryUsage));
        }

        /**
         * Build the {@link TraceWorkload} instance.
         */
        public TraceWorkload build() {
            return new TraceWorkload(
                    this.fragments,
                    this.checkpointInterval,
                    this.checkpointDuration,
                    this.checkpointIntervalScaling,
                    this.scalingPolicy,
                    this.taskId,
                    this.resourceTypes);
        }
    }
}
