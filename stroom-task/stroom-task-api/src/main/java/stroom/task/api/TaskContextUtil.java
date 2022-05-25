package stroom.task.api;

public class TaskContextUtil {

    public static <R> R runInterruptibly(final TaskContext taskContext,
                                         final InterruptableSupplier<R> supplier) throws InterruptedException {
        final TerminateHandler terminateHandler = () -> Thread.currentThread().interrupt();
        taskContext.addTerminateHandler(terminateHandler);
        try {
            return supplier.get();
        } finally {
            taskContext.removeTerminateHandler(terminateHandler);
        }
    }

    public interface InterruptableSupplier<T> {

        T get() throws InterruptedException;
    }
}
