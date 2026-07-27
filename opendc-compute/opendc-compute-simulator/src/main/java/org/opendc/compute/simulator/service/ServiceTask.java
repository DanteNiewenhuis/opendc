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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.opendc.compute.api.TaskDescription;
import org.opendc.compute.api.TaskState;
import org.opendc.compute.simulator.TaskWatcher;
import org.opendc.compute.simulator.host.SimHost;
import org.opendc.compute.simulator.scheduler.SchedulingRequest;
import org.opendc.simulator.compute.workload.Workload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ServiceTask} provided by {@link ComputeService}.
 */
public class ServiceTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTask.class);

    private ComputeService service;

    /**
     * The task's identity and resource requirements, as described by the workload model. Backs
     * most of this class's read-only getters instead of each one holding its own duplicate copy.
     */
    private final TaskDescription task;

    private final ArrayList<Integer> parents;

    public Workload workload;

    // Not backed by `task`: `TaskDescription.totalLoad` (as computed by the model that builds a
    // TaskSpec) and this value (as computed when a TaskSpec is materialized into a ServiceTask)
    // currently use different formulas - see the constructor. Kept as this task's own field,
    // computed the same way as before, until that discrepancy is deliberately resolved.
    private final double totalCPULoad;

    // Not backed by `task`: mutated at runtime (by the replayer adjusting for simulation offset),
    // so it needs to be this task's own copy rather than a shared, delegated value.
    private long deadline;

    // Not backed by `task`: `submittedAt` predates this class holding a `task` reference and has
    // its own setter; kept as an independent field to preserve that mutability.
    private long submittedAt;

    // Not backed by `task`: `copy()` deliberately resets this to 0, which isn't possible if it
    // were delegated to the (shared, immutable) `task`.
    private final long gpuMemorySize;

    private final List<TaskWatcher> watchers = new ArrayList<>(1);
    private int stateOrdinal = TaskState.CREATED.ordinal();
    private long scheduledAt;
    private long finishedAt;
    private SimHost host = null;
    private String hostName = null;

    private SchedulingRequest request = null;

    private int numFailures = 0;
    private int numPauses = 0;

    private long schedulingDelay = 0;

    /// //////////////////////////////////////////////////////////////////////////////////////////////////
    /// Getters and Setters
    /// //////////////////////////////////////////////////////////////////////////////////////////////////

    public ComputeService getService() {
        return service;
    }

    public void setService(ComputeService service) {
        this.service = service;
    }

    public int getId() {
        return task.getId();
    }

    public ArrayList<Integer> getParents() {
        return parents;
    }

    public Set<Integer> getChildren() {
        return task.getChildrenIds();
    }

    public String getName() {
        return task.getName();
    }

    public boolean getDeferrable() {
        return task.getDeferrable();
    }

    public long getDuration() {
        return task.getDurationMs();
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
        return task.getCpuCoreCount();
    }

    public double getCpuCapacity() {
        return task.getCpuCapacityMHz();
    }

    public double getTotalCPULoad() {
        return totalCPULoad;
    }

    public long getMemorySize() {
        return task.getMemoryMiB();
    }

    public int getGpuCoreCount() {
        return task.getGpuCoreCount();
    }

    public double getGpuCapacity() {
        return task.getGpuCapacityMHz();
    }

    public long getGpuMemorySize() {
        return gpuMemorySize;
    }

    public List<TaskWatcher> getWatchers() {
        return watchers;
    }

    @NotNull
    public TaskState getState() {
        return TaskState.getEntries().get(stateOrdinal);
    }

    void setState(TaskState newState) {
        if (this.getState() == newState) {
            return;
        }

        for (TaskWatcher watcher : watchers) {
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

        this.stateOrdinal = newState.ordinal();
    }

    public int getStateOrdinal() {
        return stateOrdinal;
    }

    public void setStateOrdinal(int stateOrdinal) {
        this.stateOrdinal = stateOrdinal;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
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
        this.numFailures = numFailures;
    }

    public int getNumPauses() {
        return numPauses;
    }

    public void setNumPauses(int numPauses) {
        this.numPauses = numPauses;
    }

    /// //////////////////////////////////////////////////////////////////////////////////////////////////
    /// Constructor and Public Methods
    /// //////////////////////////////////////////////////////////////////////////////////////////////////

    public ServiceTask(TaskDescription task, Workload workload, ArrayList<Integer> parents, double totalCPULoad) {
        this(task, workload, parents, totalCPULoad, task.getGpuMemoryMiB());
    }

    private ServiceTask(
            TaskDescription task,
            Workload workload,
            ArrayList<Integer> parents,
            double totalCPULoad,
            long gpuMemorySize) {
        this.task = task;
        this.workload = workload;
        this.submittedAt = task.getSubmissionTimeMs();
        this.deadline = task.getDeadlineMs();
        this.totalCPULoad = totalCPULoad;
        this.gpuMemorySize = gpuMemorySize;
        this.parents = parents;
    }

    public ServiceTask copy() {
        return new ServiceTask(
                this.task,
                this.workload,
                this.parents == null ? null : new ArrayList<>(this.parents),
                this.totalCPULoad,
                0);
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

    public void watch(@NotNull TaskWatcher watcher) {
        watchers.add(watcher);
    }

    public void unwatch(@NotNull TaskWatcher watcher) {
        watchers.remove(watcher);
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
        ServiceTask other = (ServiceTask) o;
        return service.equals(other.service) && getId() == other.getId();
    }

    public int hashCode() {
        return Objects.hash(service, getId());
    }

    public String toString() {
        return "Task[uid=" + this.getId() + ",name=" + this.getName() + ",state=" + this.getState() + "]";
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

    public void removeFromParents(List<Integer> completedTasks) {
        if (this.parents == null) {
            return;
        }

        for (int completedTask : completedTasks) {
            this.removeFromParents(completedTask);
        }
    }

    public void removeFromParents(int completedTask) {
        if (this.parents == null) {
            return;
        }

        this.parents.remove(Integer.valueOf(completedTask));
    }

    public boolean hasChildren() {
        return !task.getChildrenIds().isEmpty();
    }

    public boolean hasParents() {
        if (parents == null) {
            return false;
        }

        return !parents.isEmpty();
    }

    public long getSchedulingDelay() {
        return schedulingDelay;
    }

    public void setSchedulingDelay(long schedulingDelay) {
        this.schedulingDelay = schedulingDelay;
    }
}
