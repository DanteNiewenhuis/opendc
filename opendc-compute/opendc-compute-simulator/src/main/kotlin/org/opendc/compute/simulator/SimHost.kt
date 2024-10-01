/*
 * Copyright (c) 2020 AtLarge Research
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

package org.opendc.compute.simulator

import org.opendc.compute.api.Flavor
import org.opendc.compute.api.TaskState
import org.opendc.compute.service.ServiceTask
import org.opendc.compute.service.driver.Host
import org.opendc.compute.service.driver.HostListener
import org.opendc.compute.service.driver.HostModel
import org.opendc.compute.service.driver.HostState
import org.opendc.compute.service.driver.telemetry.GuestCpuStats
import org.opendc.compute.service.driver.telemetry.GuestSystemStats
import org.opendc.compute.service.driver.telemetry.HostCpuStats
import org.opendc.compute.service.driver.telemetry.HostSystemStats
import org.opendc.compute.simulator.internal.Guest
import org.opendc.compute.simulator.internal.GuestListener
import org.opendc.simulator.compute.old.SimBareMetalMachine
import org.opendc.simulator.compute.old.SimMachineContext
import org.opendc.simulator.compute.old.kernel.SimHypervisor
import org.opendc.simulator.compute.old.model.MachineModel
import org.opendc.simulator.compute.old.model.MemoryUnit
import org.opendc.simulator.compute.old.workload.SimWorkload
import org.opendc.simulator.compute.v2.machine.SimMachineNew
import java.time.Duration
import java.time.Instant
import java.time.InstantSource
import java.util.UUID

/**
 * A [Host] implementation that simulates virtual machines on a physical machine using [SimHypervisor].
 *
 * @param uid The unique identifier of the host.
 * @param name The name of the host.
 * @param meta The metadata of the host.
 * @param clock The (virtual) clock used to track time.
 * @param machine The [SimBareMetalMachine] on which the host runs.
 * @param hypervisor The [SimHypervisor] to run on top of the machine.
 * @param mapper A [SimWorkloadMapper] to map a [Task] to a [SimWorkload].
 */
public class SimHost(
    private val uid: UUID,
    private val name: String,
    private val meta: Map<String, Any>,
    private val clock: InstantSource,
    private val machine: SimBareMetalMachine,
    private val hypervisor: SimHypervisor,
    private val machineNew: SimMachineNew,
) : Host, AutoCloseable {
    /**
     * The event listeners registered with this host.
     */
    private val hostListeners = mutableListOf<HostListener>()

    /**
     * The virtual machines running on the hypervisor.
     */
    private val taskToGuestMap = HashMap<ServiceTask, Guest>()
    private val guests = mutableListOf<Guest>()

    private var hostState: HostState = HostState.DOWN
        set(value) {
            if (value != field) {
                hostListeners.forEach { it.onStateChanged(this, value) }
            }
            field = value
        }

    private val model: HostModel =
        HostModel(
            machine.machineModel.cpu.totalCapacity,
            machine.machineModel.cpu.coreCount,
            machine.machineModel.memory.size,
        )

    /**
     * The [GuestListener] that listens for guest events.
     */
    private val guestListener =
        object : GuestListener {
            override fun onStart(guest: Guest) {
                hostListeners.forEach { it.onStateChanged(this@SimHost, guest.task, guest.state) }
            }

            override fun onStop(guest: Guest) {
                hostListeners.forEach { it.onStateChanged(this@SimHost, guest.task, guest.state) }
            }
        }

    private var lastReport = clock.millis()
    private var totalUptime = 0L
    private var totalDowntime = 0L
    private var bootTime: Instant? = null
    private val cpuLimit = machine.machineModel.cpu.totalCapacity

    init {
        launch()
    }

    override fun getUid(): UUID {
        return uid
    }

    override fun getName(): String {
        return name
    }

    override fun getModel(): HostModel {
        return model
    }

    override fun getMeta(): Map<String, *> {
        return meta
    }

    override fun getState(): HostState {
        return hostState
    }

    override fun getInstances(): Set<ServiceTask> {
        return taskToGuestMap.keys
    }

    override fun canFit(task: ServiceTask): Boolean {
        val sufficientMemory = model.memoryCapacity >= task.flavor.memorySize
        val enoughCpus = model.coreCount >= task.flavor.coreCount
        val canFit = hypervisor.canFit(task.flavor.toMachineModel())

        return sufficientMemory && enoughCpus && canFit
    }

    /**
     * Spawn A Virtual machine that run the Task and put this Task as a Guest on it
     *
     * @param task
     */
    override fun spawn(task: ServiceTask) {
        taskToGuestMap.computeIfAbsent(task) { key ->
            require(canFit(key)) { "Task does not fit" }

            val machine = hypervisor.newMachine(key.flavor.toMachineModel())
            val virtualMachine = machineNew.newMachine(key.flavor.toMachineModel())
            val newGuest =
                Guest(
                    clock,
                    this,
                    hypervisor,
                    guestListener,
                    task,
                    machine,
                    virtualMachine
                )

            guests.add(newGuest)
            newGuest.start()
            newGuest
        }
    }

    override fun contains(task: ServiceTask): Boolean {
        return task in taskToGuestMap
    }

    override fun start(task: ServiceTask) {
        val guest = requireNotNull(taskToGuestMap[task]) { "Unknown task ${task.uid} at host $uid" }
        guest.start()
    }

    override fun stop(task: ServiceTask) {
        val guest = requireNotNull(taskToGuestMap[task]) { "Unknown task ${task.uid} at host $uid" }
        guest.stop()
    }

    override fun delete(task: ServiceTask) {
        val guest = taskToGuestMap[task] ?: return
        guest.delete()

        taskToGuestMap.remove(task)
        guests.remove(guest)
    }

    override fun addListener(listener: HostListener) {
        hostListeners.add(listener)
    }

    override fun removeListener(listener: HostListener) {
        hostListeners.remove(listener)
    }

    override fun close() {
        reset(HostState.DOWN)
        machine.cancel()
    }

    override fun getSystemStats(): HostSystemStats {
        updateUptime()

        var terminated = 0
        var running = 0
        var error = 0
        var invalid = 0

        val guests = guests.listIterator()
        for (guest in guests) {
            when (guest.state) {
                TaskState.TERMINATED -> terminated++
                TaskState.RUNNING -> running++
                TaskState.ERROR -> error++
                TaskState.DELETED -> {
                    // Remove guests that have been deleted
                    this.taskToGuestMap.remove(guest.task)
                    guests.remove()
                }
                else -> invalid++
            }
        }

        return HostSystemStats(
            Duration.ofMillis(totalUptime),
            Duration.ofMillis(totalDowntime),
            bootTime,
            machine.psu.powerDraw,
            machine.psu.energyUsage,
            terminated,
            running,
            error,
            invalid,
        )
    }

    override fun getSystemStats(task: ServiceTask): GuestSystemStats {
        val guest = requireNotNull(taskToGuestMap[task]) { "Unknown task ${task.uid} at host $uid" }
        return guest.getSystemStats()
    }

    override fun getCpuStats(): HostCpuStats {
        val counters = hypervisor.counters
        counters.sync()

        return HostCpuStats(
            counters.cpuActiveTime,
            counters.cpuIdleTime,
            counters.cpuStealTime,
            counters.cpuLostTime,
            hypervisor.cpuCapacity,
            hypervisor.cpuDemand,
            hypervisor.cpuUsage,
            hypervisor.cpuUsage / cpuLimit,
        )
    }

    override fun getCpuStats(task: ServiceTask): GuestCpuStats {
        val guest = requireNotNull(taskToGuestMap[task]) { "Unknown task ${task.uid} at host $uid" }
        return guest.getCpuStats()
    }

    override fun hashCode(): Int = uid.hashCode()

    override fun equals(other: Any?): Boolean {
        return other is SimHost && uid == other.uid
    }

    override fun toString(): String = "SimHost[uid=$uid,name=$name,model=$model]"

    public fun fail() {
        reset(HostState.ERROR)

        for (guest in guests) {
            guest.fail()
        }
    }

    public fun recover() {
        updateUptime()

        launch()
    }

    /**
     * The [SimMachineContext] that represents the machine running the hypervisor.
     */
    private var ctx: SimMachineContext? = null

    /**
     * Launch the hypervisor.
     */
    private fun launch() {
        check(ctx == null) { "Concurrent hypervisor running" }

        val hypervisor = hypervisor
        val hypervisorWorkload =
            object : SimWorkload by hypervisor {
                override fun onStart(ctx: SimMachineContext) {
                    try {
                        bootTime = clock.instant()
                        hostState = HostState.UP
                        hypervisor.onStart(ctx)

                        // Recover the guests that were running on the hypervisor.
                        for (guest in guests) {
                            guest.recover()
                        }
                    } catch (cause: Throwable) {
                        hostState = HostState.ERROR
                        throw cause
                    }
                }
            }

        val workload = hypervisorWorkload

        // Launch hypervisor onto machine
        ctx =
            machine.startWorkload(workload, emptyMap()) { cause ->
                hostState = if (cause != null) HostState.ERROR else HostState.DOWN
                ctx = null
            }
    }

    /**
     * Reset the machine.
     */
    private fun reset(state: HostState) {
        updateUptime()

        // Stop the hypervisor
        ctx?.shutdown()
        hostState = state
    }

    /**
     * Convert flavor to machine model.
     */
    private fun Flavor.toMachineModel(): MachineModel {
        return MachineModel(machine.machineModel.cpu, MemoryUnit("Generic", "Generic", 3200.0, memorySize))
    }

    /**
     * Helper function to track the uptime of a machine.
     */
    private fun updateUptime() {
        val now = clock.millis()
        val duration = now - lastReport
        lastReport = now

        if (hostState == HostState.UP) {
            totalUptime += duration
        } else if (hostState == HostState.ERROR) {
            // Only increment downtime if the machine is in a failure state
            totalDowntime += duration
        }

        val guests = guests
        for (i in guests.indices) {
            guests[i].updateUptime()
        }
    }
}
