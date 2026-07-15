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

package org.opendc.compute.simulator.service;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.opendc.compute.api.TaskState;
import org.opendc.compute.simulator.TaskWatcher;
import org.opendc.compute.simulator.host.SimHost;
import org.opendc.compute.simulator.scheduler.SchedulingRequest;
import org.opendc.simulator.compute.workload.Workload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The runtime state of a task being simulated by {@link ComputeService}.
 *
 * <p>The task's input data lives on the {@link TaskSpec} this was created from, and is exposed here by
 * delegation. Only state that exists once a task is in flight is held directly, so that a trace can be kept
 * resident as specs without paying for runtime fields on tasks that have not been submitted.
 */
public class ServiceTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTask.class);

    private final TaskSpec spec;

    private ComputeService service;
    private final int[] parents; // nullable; live entries in [0, numParents)
    private int numParents;

    /**
     * The deadline in simulation time, derived once from the spec's trace-time deadline. -1 if the task has
     * none.
     */
    private long deadline;

    /**
     * The current workload. Starts as the spec's, but is replaced by {@code Client.rescheduleTask} and nulled
     * by {@link #delete()}.
     */
    public Workload workload;

    private TaskWatcher watcher = null;
    private byte stateOrdinal = (byte) TaskState.CREATED.ordinal();
    private long scheduledAt;
    private long finishedAt;
    private SimHost host = null;
    private String hostName = null;

    private SchedulingRequest request = null;

    private short numFailures = 0;
    private short numPauses = 0;

    private long schedulingDelay = 0;

    /// //////////////////////////////////////////////////////////////////////////////////////////////////
    /// Getters and Setters
    /// //////////////////////////////////////////////////////////////////////////////////////////////////

    public TaskSpec getSpec() {
        return spec;
    }

    public ComputeService getService() {
        return service;
    }

    public void setService(ComputeService service) {
        this.service = service;
    }

    public int getId() {
        return spec.getId();
    }

    public int[] getChildren() {
        return spec.getChildren();
    }

    public String getName() {
        return spec.getName();
    }

    public boolean getDeferrable() {
        return spec.getDeferrable();
    }

    public long getDuration() {
        return spec.getDuration();
    }

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public Workload getWorkload() {
        return workload;
    }

    public void setWorkload(Workload workload) {
        this.workload = workload;
    }

    public int getCpuCoreCount() {
        return spec.getCpuCoreCount();
    }

    public double getCpuCapacity() {
        return spec.getCpuCapacity();
    }

    public double getTotalCPULoad() {
        return spec.getTotalCPULoad();
    }

    public long getMemorySize() {
        return spec.getMemorySize();
    }

    public int getGpuCoreCount() {
        return spec.getGpuCoreCount();
    }

    public double getGpuCapacity() {
        return spec.getGpuCapacity();
    }

    public long getGpuMemorySize() {
        return spec.getGpuMemorySize();
    }

    @NotNull
    public TaskState getState() {
        return TaskState.getEntries().get(stateOrdinal);
    }

    void setState(TaskState newState) {
        if (this.getState() == newState) {
            return;
        }

        final TaskWatcher watcher = this.watcher;
        if (watcher != null) {
            watcher.onStateChanged(this, newState);
        }
        if (newState == TaskState.FAILED) {
            this.numFailures++;
        } else if (newState == TaskState.PAUSED) {
            this.numPauses++;
        }

        if ((newState == TaskState.COMPLETED) || (newState == TaskState.FAILED) || (newState == TaskState.TERMINATED)) {
            this.finishedAt = this.service.getClock().millis();
        }

        this.stateOrdinal = (byte) newState.ordinal();
    }

    public long getSubmittedAt() {
        return spec.getSubmittedAt();
    }

    public long getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(long scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public SimHost getHost() {
        return host;
    }

    public void setHost(SimHost newHost) {
        this.host = newHost;
        if (newHost != null) {
            this.setHostName(newHost.getName());
        }
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public SchedulingRequest getRequest() {
        return request;
    }

    public void setRequest(SchedulingRequest request) {
        this.request = request;
    }

    public int getNumFailures() {
        return numFailures;
    }

    public void setNumFailures(int numFailures) {
        this.numFailures = (short) numFailures;
    }

    public int getNumPauses() {
        return numPauses;
    }

    public void setNumPauses(int numPauses) {
        this.numPauses = (short) numPauses;
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////
    /// Constructor and Public Methods
    /// //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Create the runtime state for a task described by {@code spec}.
     *
     * @param simulationOffset The offset between trace time and simulation time. The spec's trace-time
     *                         deadline is converted to simulation time by subtracting it.
     */
    public ServiceTask(TaskSpec spec, long simulationOffset) {
        this.spec = spec;

        // The spec's parents are shared across every task built from it; take a private copy, since
        // removeFromParents mutates it as the DAG resolves.
        final int[] specParents = spec.getParents();
        this.parents = specParents == null ? null : specParents.clone();
        this.numParents = this.parents == null ? 0 : this.parents.length;

        this.workload = spec.getWorkload();

        final long traceDeadline = spec.getDeadline();
        this.deadline = traceDeadline == -1L ? -1L : traceDeadline - simulationOffset;
    }

    public ServiceTask(TaskSpec spec) {
        this(spec, 0L);
    }

    public void start() {
        switch (this.getState()) {
            case PROVISIONING:
                LOGGER.debug("User tried to start task but request is already pending: doing nothing");
            case RUNNING:
                LOGGER.debug("User tried to start task but task is already running");
                break;
            case COMPLETED:
            case TERMINATED:
                LOGGER.warn("User tried to start deleted task");
                throw new IllegalStateException("Task is deleted");
            case CREATED:
                LOGGER.info("User requested to start task {}", getId());
                setState(TaskState.PROVISIONING);
                assert request == null : "Scheduling request already active";
                request = service.schedule(this);
                break;
            case PAUSED:
                LOGGER.info("User requested to start task after pause {}", getId());
                setState(TaskState.PROVISIONING);
                request = service.schedule(this, false);
                break;
            case FAILED:
                LOGGER.info("User requested to start task after failure {}", getId());
                setState(TaskState.PROVISIONING);
                request = service.schedule(this, false);
                break;
        }
    }

    /**
     * Attach a watcher to this task. Only a single watcher is supported; call {@link #unwatch} before
     * attaching a different one.
     *
     * @throws IllegalStateException if a watcher is already attached.
     */
    public void watch(@NotNull TaskWatcher watcher) {
        if (this.watcher != null) {
            throw new IllegalStateException("Task " + getId() + " already has a watcher attached");
        }
        this.watcher = watcher;
    }

    public void unwatch(@NotNull TaskWatcher watcher) {
        if (this.watcher == watcher) {
            this.watcher = null;
        }
    }

    public void delete() {
        cancelProvisioningRequest();
        final SimHost host = this.host;
        if (host != null) {
            host.delete(this);
        }
        service.delete(this);

        this.workload = null;

        this.setState(TaskState.DELETED);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceTask task = (ServiceTask) o;
        return service.equals(task.service) && getId() == task.getId();
    }

    public int hashCode() {
        return Objects.hash(service, getId());
    }

    public String toString() {
        return "Task[uid=" + getId() + ",name=" + getName() + ",state=" + this.getState() + "]";
    }

    /**
     * Cancel the provisioning request if active.
     */
    private void cancelProvisioningRequest() {
        final SchedulingRequest request = this.request;
        if (request != null) {
            this.request = null;
            request.setCancelled(true);
        }
    }

    public void removeFromParents(int completedTask) {
        if (this.parents == null) {
            return;
        }

        // Swap-remove: order is irrelevant, and a not-present id is a no-op.
        for (int i = 0; i < numParents; i++) {
            if (parents[i] == completedTask) {
                parents[i] = parents[numParents - 1];
                numParents--;
                return;
            }
        }
    }

    public boolean hasChildren() {
        final int[] children = spec.getChildren();
        return children != null && children.length > 0;
    }

    public boolean hasParents() {
        return numParents > 0;
    }

    public long getSchedulingDelay() {
        return schedulingDelay;
    }

    public void setSchedulingDelay(long schedulingDelay) {
        this.schedulingDelay = schedulingDelay;
    }
}
