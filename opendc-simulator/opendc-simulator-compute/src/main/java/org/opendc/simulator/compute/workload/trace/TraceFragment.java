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

import org.opendc.common.ResourceType;

import java.util.Map;

public class TraceFragment {
    private final long duration;
    private double cpuUsage;
    private double gpuUsage;
    private int gpuMemoryUsage;

    private Map<ResourceType, Double> resourceUsage;

    public long getDuration() {
        return duration;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getGpuUsage() {
        return gpuUsage;
    }

    public void setGpuUsage(double gpuUsage) {
        this.gpuUsage = gpuUsage;
    }

    public int getGpuMemoryUsage() {
        return gpuMemoryUsage;
    }

    public void setGpuMemoryUsage(int gpuMemoryUsage) {
        this.gpuMemoryUsage = gpuMemoryUsage;
    }

    /**
     * Constructs a new trace fragment with the specified duration, CPU usage, GPU usage, and GPU memory usage.
     *
     * @param duration         The duration of the fragment in nanoseconds.
     * @param cpuUsage        The CPU usage as a percentage (0.0 to 100.0).
     * @param gpuUsage        The GPU usage as a percentage (0.0 to 100.0).
     * @param gpuMemoryUsage  The GPU memory usage in bytes.
     */

    public TraceFragment(long duration, double cpuUsage, double gpuUsage, int gpuMemoryUsage) {
        this.duration = duration;
        this.cpuUsage = cpuUsage;
        this.gpuUsage = gpuUsage;
        this.gpuMemoryUsage = gpuMemoryUsage;
    }

    public TraceFragment(long duration, Map<ResourceType, Double> resourceUsage) {
        this.duration = duration;
        this.resourceUsage = resourceUsage;
    }


    public TraceFragment(long duration) {
        this(duration, 0.0, 0.0, 0);
    }

    public TraceFragment(long start, long duration, double cpuUsage) {
        this(duration, cpuUsage, 0.0, 0);
    }

    public TraceFragment(long duration, double cpuUsage) {
        this(duration, cpuUsage, 0.0, 0);
    }

    public TraceFragment(long duration, double cpuUsage, double gpuUsage) {
        this(duration, cpuUsage, gpuUsage, 0);
    }

    /**
     * Returns the resource usage for the specified resource type.
     *
     * @param resourceType the type of resource
     * @return the usage value for the specified resource type
     */
    public double getResourceUsage(ResourceType resourceType) throws IllegalArgumentException {
        if (this.resourceUsage == null) {
            return switch (resourceType) {
                case CPU -> cpuUsage;
                case GPU -> gpuUsage;
                //            case GPU_MEMORY -> gpuMemoryUsage;
                default -> throw new IllegalArgumentException("Invalid resource type: " + resourceType);
            };
        }


        if (!this.resourceUsage.containsKey(resourceType)) {
            throw new IllegalArgumentException("Invalid resource type: " + resourceType);
        }

        return this.resourceUsage.get(resourceType);

//        return switch (resourceType) {
//            case CPU -> cpuUsage;
//            case GPU -> gpuUsage;
//                //            case GPU_MEMORY -> gpuMemoryUsage;
//            default -> throw new IllegalArgumentException("Invalid resource type: " + resourceType);
//        };
    }

//    public double getResourceUsage(ResourceType resourceType) throws IllegalArgumentException {
//        return switch (resourceType) {
//            case CPU -> cpuUsage;
//            case GPU -> gpuUsage;
//            //            case GPU_MEMORY -> gpuMemoryUsage;
//            default -> throw new IllegalArgumentException("Invalid resource type: " + resourceType);
//        };
//    }
}
