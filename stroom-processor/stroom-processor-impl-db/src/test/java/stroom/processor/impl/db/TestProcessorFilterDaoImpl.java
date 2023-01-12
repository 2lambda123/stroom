package stroom.processor.impl.db;

import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.TaskStatus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;
import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TestProcessorFilterDaoImpl extends AbstractProcessorTest{

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProcessorFilterDaoImpl.class);

    Processor processor1;
    Processor processor2;
    Processor processor3;
    ProcessorFilter processorFilter1;
    ProcessorFilter processorFilter2;
    ProcessorFilter processorFilter3;
    ProcessorFilterTracker processorFilterTracker1;
    ProcessorFilterTracker processorFilterTracker2;
    ProcessorFilterTracker processorFilterTracker3;

    @Test
    void logicalDeleteByProcessorFilterId() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();

        processorFilter1 = createProcessorFilter(processor1);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter2 = createProcessorFilter(processor2);
        createProcessorTask(processorFilter2, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        dumpProcessorTable();
        dumpProcessorFilterTable();

        processorFilterDao.logicalDeleteByProcessorFilterId(processorFilter1.getId());

        dumpProcessorTable();
        dumpProcessorFilterTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(2);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(2);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(6);

        // Now make sure the right number have been set to a deleted state
        // Processors not effected
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(1);
        // Tasks not effected
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);
    }

    @Test
    void testLogicallyDeleteOldProcessorFilters() {
        assertThat(getProcessorCount(null))
                .isEqualTo(0);

        processor1 = createProcessor();
        processor2 = createProcessor();
        processor3 = createProcessor();

        processorFilter1 = createProcessorFilter(processor1);
        processorFilterTracker1 = processorFilter1.getProcessorFilterTracker();
        processorFilterTracker1.setLastPollMs(Instant.now().toEpochMilli());
        processorFilterTracker1.setStatus(ProcessorFilterTracker.COMPLETE);
        processorFilterTrackerDao.update(processorFilterTracker1);
        createProcessorTask(processorFilter1, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter1, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter2 = createProcessorFilter(processor2);
        processorFilterTracker2 = processorFilter2.getProcessorFilterTracker();
        processorFilterTracker2.setLastPollMs(Instant.now().toEpochMilli());
        processorFilterTracker2.setStatus(ProcessorFilterTracker.ERROR);
        processorFilterTrackerDao.update(processorFilterTracker2);
        createProcessorTask(processorFilter2, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter2, TaskStatus.PROCESSING, NODE1, FEED);

        processorFilter3 = createProcessorFilter(processor3);
        createProcessorTask(processorFilter3, TaskStatus.UNPROCESSED, NODE1, FEED);
        createProcessorTask(processorFilter3, TaskStatus.ASSIGNED, NODE1, FEED);
        createProcessorTask(processorFilter3, TaskStatus.PROCESSING, NODE1, FEED);

        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(9);

        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        processorFilterDao.logicallyDeleteOldProcessorFilters(threshold);

        dumpProcessorFilterTable();

        // No change to row counts as they have been logically deleted
        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3);
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(3);
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(9);

        // Now make sure the right number have been set to a deleted state
        // Processors not effected
        assertThat(getProcessorCount(PROCESSOR.DELETED.eq(true)))
                .isEqualTo(0);
        assertThat(getProcessorFilterCount(PROCESSOR_FILTER.DELETED.eq(true)))
                .isEqualTo(2);
        // Tasks not effected
        assertThat(getProcessorTaskCount(PROCESSOR_TASK.STATUS.eq(TaskStatus.DELETED.getPrimitiveValue())))
                .isEqualTo(0);
    }

    @Test
    void testPhysicalDeleteOldProcessorFilters() {
        testLogicallyDeleteOldProcessorFilters();

        final Instant threshold = Instant.now().plus(1, ChronoUnit.DAYS);

        processorTaskDao.logicalDeleteForDeletedProcessorFilters(threshold);
        processorTaskDao.physicallyDeleteOldTasks(threshold);

        dumpProcessorFilterTable();
        dumpProcessorFilterTrackerTable();

        processorFilterDao.physicalDeleteOldProcessorFilters(threshold);

        dumpProcessorFilterTable();

        // No change to processors
        assertThat(getProcessorCount(null))
                .isEqualTo(3);
        // Deleted 2 filters
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(3 - 2);
        // Deleted 2 trackers
        assertThat(getProcessorFilterTrackerCount(null))
                .isEqualTo(3 - 2);
        // Deleted 3 tasks for each of two trackers
        assertThat(getProcessorTaskCount(null))
                .isEqualTo(9 - (3 * 2));
    }

    @Test
    void create() {
        assertThat(getProcessorFilterCount(null))
                .isEqualTo(0);
        final Processor processor1 = createProcessor();

        final ProcessorFilter processorFilter1 = createProcessorFilter(processor1);
        final ProcessorFilterTracker processorFilterTracker1 = processorFilter1.getProcessorFilterTracker();

        Assertions.assertThat(processorFilter1.getId())
                .isNotNull();
        Assertions.assertThat(processorFilterTracker1.getId())
                .isNotNull();

        final ProcessorFilter processorFilter2 = processorFilterDao.fetch(processorFilter1.getId())
                .orElseThrow();
        final ProcessorFilterTracker processorFilterTracker2 = processorFilter2.getProcessorFilterTracker();

        Assertions.assertThat(processorFilter1.getProcessor())
                        .isEqualTo(processorFilter2.getProcessor());
        Assertions.assertThat(processorFilter1.getProcessorFilterTracker())
                .isEqualTo(processorFilter2.getProcessorFilterTracker());
        Assertions.assertThat(processorFilterTracker1.getId())
                .isEqualTo(processorFilterTracker2.getId());
    }
}
