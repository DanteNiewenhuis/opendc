package org.opendc.simulator.compute.old;

import org.opendc.simulator.compute.old.workload.SimWorkload;
import org.opendc.simulator.flow2.FlowGraph;
import org.opendc.simulator.flow2.InPort;

import java.util.Map;
import java.util.function.Consumer;


/**
 * The execution context in which the workload runs.
 */
public abstract class SimAbstractMachineContext implements SimMachineContext {
    private final SimAbstractMachine machine;
    private final SimWorkload workload;
    private final Map<String, Object> meta;
    private final Consumer<Exception> completion;
    private boolean isClosed;
    private SimWorkload snapshot;

    /**
     * Construct a new {@link SimAbstractMachineContext} instance.
     *
     * @param machine The {@link SimAbstractMachine} to which the context belongs.
     * @param workload The {@link SimWorkload} to which the context belongs.
     * @param meta The metadata passed to the context.
     * @param completion A block that is invoked when the workload completes carrying an exception if thrown by the workload.
     */
    public SimAbstractMachineContext(
        SimAbstractMachine machine,
        SimWorkload workload,
        Map<String, Object> meta,
        Consumer<Exception> completion) {
        this.machine = machine;
        this.workload = workload;
        this.meta = meta;
        this.completion = completion;
    }

    @Override
    public final Map<String, Object> getMeta() {
        return meta;
    }

    @Override
    public void makeSnapshot(long now) {
        this.snapshot = workload.getSnapshot();
    }

    @Override
    public SimWorkload getSnapshot(long now) {
        return this.snapshot;
    }

    @Override
    public void reset() {
        final FlowGraph graph = getMemory().getInput().getGraph();

        final InPort InPort = getCpu().getInput();
        graph.disconnect(InPort);

        graph.disconnect(getMemory().getInput());
    }

    @Override
    public final void shutdown() {
        shutdown(null);
    }

    @Override
    public final void shutdown(Exception cause) {
        if (isClosed) {
            return;
        }

        isClosed = true;
        final SimAbstractMachine machine = this.machine;
        assert machine.getActiveContext() == this : "Invariant violation: multiple contexts active for a single machine";
        machine.activeContext = null;

        // Cancel all the resources associated with the machine
        doCancel();

        try {
            workload.onStop(this);
        } catch (Exception e) {
            if (cause == null) {
                cause = e;
            } else {
                cause.addSuppressed(e);
            }
        }

        completion.accept(cause);
    }

    /**
     * Start this context.
     */
    final void start() {
        try {
            machine.activeContext = this;
            workload.onStart(this);
        } catch (Exception cause) {
            shutdown(cause);
        }
    }

    /**
     * Run the stop procedures for the resources associated with the machine.
     */
    protected void doCancel() {
        reset();
    }

    @Override
    public String toString() {
        return "SimAbstractMachine.Context";
    }
}
