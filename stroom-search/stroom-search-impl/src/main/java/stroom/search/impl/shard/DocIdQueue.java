package stroom.search.impl.shard;

import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextUtil;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;

public class DocIdQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocIdQueue.class);
    private static final CompleteException COMPLETE = new CompleteException();

    private final TaskContext taskContext;
    private final ArrayBlockingQueue<Object> queue;

    public DocIdQueue(final TaskContext taskContext,
                      final int capacity) {
        this.taskContext = taskContext;
        queue = new ArrayBlockingQueue<>(capacity);
    }

    public void put(final int value) {
        doPut(value);
    }

    private void doPut(final Object value) {
        try {
            TaskContextUtil.runInterruptibly(taskContext, () -> {
                queue.put(value);
                return null;
            });
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Clear the doc id queue to ensure the search code is not blocked from completing.
            clear();
            throw new TaskTerminatedException();
        }
    }

    public int take() throws CompleteException {
        try {
            final Object object = TaskContextUtil.runInterruptibly(taskContext, queue::take);
            if (COMPLETE == object) {
                complete();
                throw COMPLETE;
            }
            return (int) object;
        } catch (final InterruptedException e) {
            LOGGER.trace(e::getMessage, e);
            // Clear the doc id queue to ensure the search code is not blocked from completing.
            clear();
            throw new TaskTerminatedException();
        }
    }

    public void complete() {
        doPut(COMPLETE);
    }

    public void clear() {
        queue.clear();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
