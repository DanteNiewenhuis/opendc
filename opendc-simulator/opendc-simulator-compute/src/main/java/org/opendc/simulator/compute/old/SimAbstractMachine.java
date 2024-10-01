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

package org.opendc.simulator.compute.old;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.opendc.simulator.compute.old.model.MachineModel;
import org.opendc.simulator.compute.old.workload.SimWorkload;

/**
 * Abstract implementation of the {@link SimMachine} interface.
 */
public abstract class SimAbstractMachine implements SimMachine {
    private final MachineModel machineModel;
    SimAbstractMachineContext activeContext;

    /**
     * Construct a {@link SimAbstractMachine} instance.
     *
     * @param machineModel The machineModel of the machine.
     */
    public SimAbstractMachine(MachineModel machineModel) {
        this.machineModel = machineModel;
    }

    @Override
    public final MachineModel getMachineModel() {
        return machineModel;
    }

    @Override
    public final SimMachineContext startWorkload(
            SimWorkload workload, Map<String, Object> meta, Consumer<Exception> completion) {
        if (activeContext != null) {
            throw new IllegalStateException("A machine cannot run multiple workloads concurrently");
        }

        final SimAbstractMachineContext machineContext = createContext(workload, new HashMap<>(meta), completion);
        machineContext.start();
        return machineContext;
    }

    @Override
    public final void cancel() {
        final SimAbstractMachineContext context = activeContext;
        if (context != null) {
            context.shutdown();
        }
    }

    /**
     * Construct a new {@link SimAbstractMachineContext} instance representing the active execution.
     *
     * @param workload   The workload to start on the machine.
     * @param meta       The metadata to pass to the workload.
     * @param completion A block that is invoked when the workload completes carrying an exception if thrown by the workload.
     */
    protected abstract SimAbstractMachineContext createContext(
            SimWorkload workload, Map<String, Object> meta, Consumer<Exception> completion);

    /**
     * Return the active {@link SimAbstractMachineContext} instance (if any).
     */
    protected SimAbstractMachineContext getActiveContext() {
        return activeContext;
    }
}
