package stroom.task.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ThreadPool;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class TestTaskManagerImpl extends AbstractCoreIntegrationTest {

    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestTaskManagerImpl.class);

    @Resource
    ExecutorProvider executorProvider;

    @Test
    public void testMoreItemsThanThreads_boundedPool() throws ExecutionException, InterruptedException {

        LOGGER.info("Starting");
        int poolSize = 4;
        ThreadPool threadPool = new ThreadPoolImpl(this.getClass().getName(),
                3,
                poolSize,
                poolSize);

        final Executor executor = executorProvider.getExecutor(threadPool);


        CompletableFuture.runAsync(() ->
                LOGGER.info("Warming up thread pool")).get();


        AtomicInteger counter = new AtomicInteger();
        final Queue<Thread> threadsUsed = new ConcurrentLinkedQueue<>();

        final int taskCount = 10;
        CompletableFuture[] futures = IntStream.rangeClosed(1, taskCount)
                .mapToObj(i ->
                        CompletableFuture.runAsync(() -> {
                                    ThreadUtil.sleep(50);
                                    LOGGER.info("Running task %s", i);
                                    counter.incrementAndGet();
                                    threadsUsed.add(Thread.currentThread());
                                },
                                executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get();

        Assert.assertEquals(taskCount, counter.get());

        long distinctThreads = threadsUsed.stream()
                .distinct()
                .count();

        LOGGER.info("Threads used: %s", distinctThreads);

        Assert.assertTrue(distinctThreads <= poolSize);

        LOGGER.info("Finished");
    }

    @Test
    public void testMoreItemsThanThreads_unBoundedPool() throws ExecutionException, InterruptedException {

        LOGGER.info("Starting");
        int poolSize = Integer.MAX_VALUE; //unbounded
        ThreadPool threadPool = new ThreadPoolImpl(this.getClass().getName(),
                3,
                0,
                poolSize);

        final Executor executor = executorProvider.getExecutor(threadPool);

        AtomicInteger counter = new AtomicInteger();
        final Queue<Thread> threadsUsed = new ConcurrentLinkedQueue<>();

        final int taskCount = 10;
        CompletableFuture[] futures = IntStream.rangeClosed(1, taskCount)
                .mapToObj(i ->
                        CompletableFuture.runAsync(() -> {
                                    ThreadUtil.sleep(50);
                                    LOGGER.info("Running task %s", i);
                                    counter.incrementAndGet();
                                    threadsUsed.add(Thread.currentThread());
                                },
                                executor))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).get();

        Assert.assertEquals(taskCount, counter.get());

        long distinctThreads = threadsUsed.stream()
                .distinct()
                .count();

        LOGGER.info("Threads used: %s", distinctThreads);

        Assert.assertTrue(distinctThreads <= poolSize);
        LOGGER.info("Finished");
    }

}
