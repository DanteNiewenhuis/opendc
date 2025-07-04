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

package org.opendc.compute.simulator.host;

import java.util.List;

/**
 * Record describing the static machine properties of the host.
 *
 * @param cpuCapacity    The total CPU capacity of the host in MHz.
 * @param coreCount      The number of logical processing cores available for this host.
 * @param memoryCapacity The amount of memory available for this host in MB.
 */
public record HostModel(double cpuCapacity, int coreCount, long memoryCapacity, List<GpuHostModel> gpuHostModels) {
    /**
     * Create a new host model.
     *
     * @param cpuCapacity    The total CPU capacity of the host in MHz.
     * @param coreCount      The number of logical processing cores available for this host.
     * @param memoryCapacity The amount of memory available for this host in MB.
     */
    public HostModel(double cpuCapacity, int coreCount, long memoryCapacity) {
        this(cpuCapacity, coreCount, memoryCapacity, null);
    }
}
