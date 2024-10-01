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

package org.opendc.compute.service.driver;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.opendc.compute.service.ServiceTask;
import org.opendc.compute.service.driver.telemetry.GuestCpuStats;
import org.opendc.compute.service.driver.telemetry.GuestSystemStats;
import org.opendc.compute.service.driver.telemetry.HostCpuStats;
import org.opendc.compute.service.driver.telemetry.HostSystemStats;

/**
 * Base interface for representing compute resources that host virtualized {@link ServiceTask} instances.
 */
public interface Host {
    /**
     * Return a unique identifier representing the host.
     */
    UUID getUid();

    /**
     * Return the name of this host.
     */
    String getName();

    /**
     * Return the machine model of the host.
     */
    HostModel getModel();

    /**
     * Return the state of the host.
     */
    HostState getState();

    /**
     * Return the meta-data associated with the host.
     */
    Map<String, ?> getMeta();

    /**
     * Return the {@link ServiceTask} instances known to the host.
     */
    Set<ServiceTask> getInstances();

    /**
     * Determine whether the specified <code>ServiceTask</code> can still fit on this host.
     */
    boolean canFit(ServiceTask ServiceTask);

    /**
     * Register the specified <code>task</code> on the host.
     */
    void spawn(ServiceTask task);

    /**
     * Determine whether the specified <code>task</code> exists on the host.
     */
    boolean contains(ServiceTask task);

    /**
     * Start the task if it is currently not running on this host.
     *
     * @throws IllegalArgumentException if the task is not present on the host.
     */
    void start(ServiceTask task);

    /**
     * Stop the task if it is currently running on this host.
     *
     * @throws IllegalArgumentException if the task is not present on the host.
     */
    void stop(ServiceTask task);

    /**
     * Delete the specified <code>task</code> on this host and cleanup all resources associated with it.
     */
    void delete(ServiceTask task);

    /**
     * Add a [HostListener] to this host.
     */
    void addListener(HostListener listener);

    /**
     * Remove a [HostListener] from this host.
     */
    void removeListener(HostListener listener);

    /**
     * Query the system statistics of the host.
     */
    HostSystemStats getSystemStats();

    /**
     * Query the system statistics of a {@link ServiceTask} that is located on this host.
     *
     * @param task The {@link ServiceTask} to obtain the system statistics of.
     * @throws IllegalArgumentException if the task is not present on the host.
     */
    GuestSystemStats getSystemStats(ServiceTask task);

    /**
     * Query the CPU statistics of the host.
     */
    HostCpuStats getCpuStats();

    /**
     * Query the CPU statistics of a {@link ServiceTask} that is located on this host.
     *
     * @param task The {@link ServiceTask} to obtain the CPU statistics of.
     * @throws IllegalArgumentException if the task is not present on the host.
     */
    GuestCpuStats getCpuStats(ServiceTask task);
}
