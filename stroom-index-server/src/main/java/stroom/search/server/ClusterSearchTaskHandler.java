/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.search.server;

import org.springframework.context.annotation.Scope;
import stroom.annotation.api.AnnotationDataSource;
import stroom.pipeline.server.errorhandler.MessageUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.coprocessor.CoprocessorsFactory;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.NewCoprocessor;
import stroom.search.coprocessor.Receiver;
import stroom.search.extraction.ExpressionFilter;
import stroom.search.extraction.ExtractionDecoratorFactory;
import stroom.search.server.shard.IndexShardSearchFactory;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.MonitorImpl;

import javax.inject.Inject;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@TaskHandlerBean(task = ClusterSearchTask.class)
@Scope(StroomScope.TASK)
class ClusterSearchTaskHandler extends AbstractTaskHandler<ClusterSearchTask, VoidResult> implements Consumer<Error> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchTaskHandler.class);

    private final TaskContext taskContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final IndexShardSearchFactory indexShardSearchFactory;
    private final ExtractionDecoratorFactory extractionDecoratorFactory;
    private final RemoteSearchResults remoteSearchResults;
    private final SecurityContext securityContext;
    private final LinkedBlockingQueue<String> errors = new LinkedBlockingQueue<>();

    private ClusterSearchTask task;

    @Inject
    ClusterSearchTaskHandler(final TaskContext taskContext,
                             final CoprocessorsFactory coprocessorsFactory,
                             final IndexShardSearchFactory indexShardSearchFactory,
                             final ExtractionDecoratorFactory extractionDecoratorFactory,
                             final RemoteSearchResults remoteSearchResults,
                             final SecurityContext securityContext) {
        this.taskContext = taskContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.indexShardSearchFactory = indexShardSearchFactory;
        this.extractionDecoratorFactory = extractionDecoratorFactory;
        this.remoteSearchResults = remoteSearchResults;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final ClusterSearchTask task) {
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            taskContext.info("Initialising...");

            this.task = task;
            final stroom.query.api.v2.Query query = task.getQuery();

            // Get the search result factory for this search from the cache.
            // It should be present if search has been started properly and not been destroyed immediately.
            final RemoteSearchResultFactory remoteSearchResultFactory = remoteSearchResults.get(task.getKey());
            if (remoteSearchResultFactory == null) {
                throw new SearchException("No search result factory can be found");
            }

            try {
                // Make sure we have been given a query.
                if (query.getExpression() == null) {
                    throw new SearchException("Search expression has not been set");
                }

                // Get the stored fields that search is hoping to use.
                final String[] storedFields = task.getStoredFields();
                if (storedFields == null || storedFields.length == 0) {
                    throw new SearchException("No stored fields have been requested");
                }

                // Create coprocessors.
                final Coprocessors coprocessors = coprocessorsFactory.create(
                        task.getCoprocessorMap(),
                        storedFields,
                        query.getParams(),
                        this,
                        task);

                remoteSearchResultFactory.setCoprocessors(coprocessors);
                remoteSearchResultFactory.setErrors(errors);
                remoteSearchResultFactory.setTaskContext(taskContext);
                remoteSearchResultFactory.setStarted(true);

                if (coprocessors.size() > 0 && !taskContext.isTerminated()) {
                    // Start searching.
                    search(task, query, coprocessors);
                }

            } catch (final RuntimeException e) {
                errors.add(e.getMessage());
            } finally {
                LOGGER.trace(() -> "Search is complete, setting searchComplete to true and " +
                        "counting down searchCompleteLatch");
                // Tell the client that the search has completed.
                remoteSearchResultFactory.complete();
            }
        }

        return VoidResult.INSTANCE;
    }

    private void search(final ClusterSearchTask task,
                        final stroom.query.api.v2.Query query,
                        final Coprocessors coprocessors) {
        taskContext.info("Searching...");
        LOGGER.debug(() -> "Incoming search request:\n" + query.getExpression().toString());

        // Create a monitor to terminate all sub tasks on completion.
        final HasTerminate hasTerminate = new MonitorImpl(task.getMonitor());
        try {
            if (task.getShards().size() > 0) {
                final Receiver extractionReceiver = extractionDecoratorFactory.create(
                        this,
                        task.getStoredFields(),
                        coprocessors,
                        query,
                        hasTerminate);

                // Search all index shards.
                final ExpressionFilter expressionFilter = new ExpressionFilter.Builder()
                        .addPrefixExcludeFilter(AnnotationDataSource.ANNOTATION_FIELD_PREFIX)
                        .build();
                final ExpressionOperator expression = expressionFilter.copy(task.getQuery().getExpression());
                final AtomicLong hitCount = new AtomicLong();
                indexShardSearchFactory.search(task, expression, extractionReceiver, taskContext, hitCount, hasTerminate);

                // Wait for search completion.
                boolean allComplete = false;
                while (!allComplete) {
                    allComplete = true;
                    for (final NewCoprocessor coprocessor : coprocessors.getSet()) {
                        if (!task.isTerminated()) {
                            taskContext.info("" +
                                    "Searching... " +
                                    "found "
                                    + hitCount.get() +
                                    " documents" +
                                    " performed " +
                                    coprocessor.getValuesCount().get() +
                                    " extractions");

                            final boolean complete = coprocessor.awaitCompletion(1, TimeUnit.SECONDS);
                            if (!complete) {
                                allComplete = false;
                            }
                        }
                    }
                }
            }

            LOGGER.debug(() -> "Complete");
        } catch (final Exception pEx) {
            throw SearchException.wrap(pEx);
        } finally {
            hasTerminate.terminate();
        }
    }

    @Override
    public void accept(final Error error) {
        if (error != null) {
            LOGGER.debug(error::getMessage, error.getThrowable());
            if (!(error.getThrowable() instanceof TaskTerminatedException)) {
                final String msg = MessageUtil.getMessage(error.getMessage(), error.getThrowable());
                errors.offer(msg);
            }
        }
    }

    public ClusterSearchTask getTask() {
        return task;
    }
}