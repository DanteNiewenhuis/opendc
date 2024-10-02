/*
 * Copyright (c) 2021 AtLarge Research
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

package org.opendc.compute.simulator.internal

import mu.KotlinLogging
import org.opendc.compute.api.TaskState
import org.opendc.compute.service.ServiceTask
import org.opendc.compute.service.driver.telemetry.GuestCpuStats
import org.opendc.compute.service.driver.telemetry.GuestSystemStats
import org.opendc.compute.simulator.SimHost
import org.opendc.simulator.compute.old.SimMachineContext
import org.opendc.simulator.compute.v2.machine.VirtualMachineNew
import org.opendc.simulator.compute.v2.workload.ChainWorkload
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.LinkedList

/**
 * A virtual machine instance that is managed by a [SimHost].
 */
internal class Guest(
    private val clock: InstantSource,
    val host: SimHost,
    private val listener: GuestListener,
    val task: ServiceTask,
    val machine: VirtualMachineNew,
) {
    /**
     * The state of the [Guest].
     *
     * [TaskState.PROVISIONING] is an invalid value for a guest, since it applies before the host is selected for
     * a task.
     */
    var state: TaskState = TaskState.TERMINATED
        private set


    /**
     * The [SimMachineContext] representing the current active virtual machine instance or `null` if no virtual machine
     * is active.
     */
    private var machineContext: SimMachineContext? = null

    private var localUptime = 0L
    private var localDowntime = 0L
    private var localLastReport = clock.millis()
    private var localBootTime: Instant? = null
    private val localCpuLimit = machine.cpu.cpuModel.totalCapacity

    /**
     * Start the guest.
     */
    fun start() {
        when (state) {
            TaskState.TERMINATED, TaskState.ERROR -> {
                LOGGER.info { "User requested to start task ${task.uid}" }
                doStart()
            }
            TaskState.RUNNING -> return
            TaskState.DELETED -> {
                LOGGER.warn { "User tried to start deleted task" }
                throw IllegalArgumentException("Task is deleted")
            }
            else -> assert(false) { "Invalid state transition" }
        }
    }

    /**
     * Launch the guest on the simulated Virtual machine
     */
    private fun doStart() {
        assert(machineContext == null) { "Concurrent job running" }

        onStart()

        val workload = task.workload;
        workload.setOffset(clock.millis())

        val newChainWorkload = ChainWorkload(LinkedList(listOf(task.workloadNew)))

        machine.startWorkload(newChainWorkload) { cause ->
            onStop(if (cause != null) TaskState.ERROR else TaskState.TERMINATED)
            machineContext = null
        };
    }

    /**
     * This method is invoked when the guest was started on the host and has booted into a running state.
     */
    private fun onStart() {
        localBootTime = clock.instant()
        state = TaskState.RUNNING
        listener.onStart(this)
    }

    /**
     * Stop the guest.
     */
    fun stop() {
        when (state) {
            TaskState.RUNNING -> doStop(TaskState.TERMINATED)
            TaskState.ERROR -> state = TaskState.TERMINATED
            TaskState.TERMINATED, TaskState.DELETED -> return
            else -> assert(false) { "Invalid state transition" }
        }
    }

    /**
     * Attempt to stop the task and put it into [target] state.
     */
    private fun doStop(target: TaskState) {
        assert(machineContext != null) { "Invalid job state" }

        val machineContext = this.machineContext ?: return
        if (target == TaskState.ERROR) {
            machineContext.shutdown(Exception("Stopped because of ERROR"))
        } else {
            machineContext.shutdown()
        }

        this.state = target
    }

    /**
     * This method is invoked when the guest stopped.
     */
    private fun onStop(target: TaskState) {
        updateUptime()

        state = target
        listener.onStop(this)
    }

    /**
     * Delete the guest.
     *
     * This operation will stop the guest if it is running on the host and remove all resources associated with the
     * guest.
     */
    fun delete() {
        stop()

        state = TaskState.DELETED
    }

    /**
     * Fail the guest if it is active.
     *
     * This operation forcibly stops the guest and puts the task into an error state.
     */
    fun fail() {
        if (state != TaskState.RUNNING) {
            return
        }

        doStop(TaskState.ERROR)
    }

    /**
     * Recover the guest if it is in an error state.
     */
    fun recover() {
        if (state != TaskState.ERROR) {
            return
        }

        doStart()
    }

    /**
     * Obtain the system statistics of this guest.
     */
    fun getSystemStats(): GuestSystemStats {
        updateUptime()

        return GuestSystemStats(
            Duration.ofMillis(localUptime),
            Duration.ofMillis(localDowntime),
            localBootTime,
        )
    }

    /**
     * Obtain the CPU statistics of this guest.
     */
    fun getCpuStats(): GuestCpuStats {
        machine.updateCounters(this.clock.millis())
        val counters = machine.performanceCounters

        return GuestCpuStats(
            counters.cpuActiveTime / 1000L,
            counters.cpuIdleTime / 1000L,
            counters.cpuStealTime / 1000L,
            counters.cpuLostTime / 1000L,
            counters.cpuCapacity,
            counters.cpuSupply,
            counters.cpuSupply / localCpuLimit,
        )
    }

    /**
     * Helper function to track the uptime and downtime of the guest.
     */
    fun updateUptime() {
        val now = clock.millis()
        val duration = now - localLastReport
        localLastReport = now

        if (state == TaskState.RUNNING) {
            localUptime += duration
        } else if (state == TaskState.ERROR) {
            localDowntime += duration
        }
    }

    private companion object {
        @JvmStatic
        private val LOGGER = KotlinLogging.logger {}
    }
}
