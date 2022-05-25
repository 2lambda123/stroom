package stroom.task.api;

import stroom.task.shared.TaskId;

import java.util.function.Supplier;

public class SimpleTaskContext implements TaskContext {

    @Override
    public void info(final Supplier<String> messageSupplier) {
    }

    @Override
    public TaskId getTaskId() {
        return null;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean addTerminateHandler(final TerminateHandler terminateHandler) {
        return true;
    }

    @Override
    public boolean removeTerminateHandler(final TerminateHandler terminateHandler) {
        return true;
    }
}
