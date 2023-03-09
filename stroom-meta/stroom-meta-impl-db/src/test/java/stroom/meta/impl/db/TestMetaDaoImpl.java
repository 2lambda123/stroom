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

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.util.shared.time.TimeUnit;
import stroom.data.shared.StreamTypeNames;
import stroom.db.util.JooqUtil;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docref.DocRef;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.impl.MetaValueDao;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.TestUtil;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.Period;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import com.google.common.base.Strings;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.meta.impl.db.MetaDaoImpl.meta;
import static stroom.meta.impl.db.MetaDaoImpl.metaFeed;
import static stroom.meta.impl.db.MetaDaoImpl.metaProcessor;
import static stroom.meta.impl.db.MetaDaoImpl.metaType;

class TestMetaDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaDaoImpl.class);

    private static final String RAW_STREAM_TYPE_NAME = "RAW_TEST_STREAM_TYPE";
    private static final String PROCESSED_STREAM_TYPE_NAME = "TEST_STREAM_TYPE";
    private static final String RAW_REF_STREAM_TYPE_NAME = "RAW_REF_STREAM_TYPE";
    private static final String REF_STREAM_TYPE_NAME = StreamTypeNames.REFERENCE;

    private static final String TEST1_FEED_NAME = "TEST1";
    private static final String TEST2_FEED_NAME = "TEST2";
    private static final String TEST3_FEED_NAME = "TEST3";
    private static final String REF1_FEED_NAME = "REF1";

    private static final DocRef TEST1_FEED =
            new DocRef(FeedDoc.DOCUMENT_TYPE, UUID.randomUUID().toString(), TEST1_FEED_NAME);
    private static final DocRef TEST2_FEED =
            new DocRef(FeedDoc.DOCUMENT_TYPE, UUID.randomUUID().toString(), TEST2_FEED_NAME);
    private static final DocRef TEST3_FEED =
            new DocRef(FeedDoc.DOCUMENT_TYPE, UUID.randomUUID().toString(), TEST3_FEED_NAME);
    private static final DocRef REF1_FEED =
            new DocRef(FeedDoc.DOCUMENT_TYPE, UUID.randomUUID().toString(), REF1_FEED_NAME);


    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaDaoImpl metaDao;
    @Inject
    private MetaValueDao metaValueDao;
    @Inject
    private MetaDbConnProvider metaDbConnProvider;
    private int totalMetaCount = 0;
    private int test1FeedCount = 0;
    private int test2FeedCount = 0;
    private int test3FeedCount = 0;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                        new MetaTestModule(),
                        new MetaDbModule(),
                        new MetaDaoModule(),
                        new MockClusterLockModule(),
                        new MockSecurityContextModule(),
                        new MockTaskModule(),
                        new MockCollectionModule(),
                        new MockDocRefInfoModule(),
                        new MockWordListProviderModule(),
                        new CacheModule(),
                        new DbTestModule())
                .injectMembers(this);

        populateDb();
    }

    private void populateDb() {
        // Delete everything`
        LOGGER.debug("Running cleanup");
        cleanup.cleanup();

        totalMetaCount = 0;
        test1FeedCount = 0;
        test2FeedCount = 0;

        LOGGER.debug("Populating DB");
        // Add some test data.
        for (int i = 0; i < 10; i++) {
            final Meta parent = metaDao.create(createRawProperties(TEST1_FEED_NAME));
            final Meta myMeta = metaDao.create(createProcessedProperties(parent, TEST1_FEED_NAME));

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(MetaFields.REC_READ.getName(), "" + 100 * i);
            attributeMap.put(MetaFields.REC_WRITE.getName(), "" + 10 * i);
            metaValueDao.addAttributes(myMeta, attributeMap);
            totalMetaCount += 2; // parent + myMeta
            test1FeedCount += 2; // parent + myMeta
        }
        for (int i = 0; i < 10; i++) {
            final Meta parent = metaDao.create(createRawProperties(TEST2_FEED_NAME));
            final Meta myMeta = metaDao.create(createProcessedProperties(parent, TEST2_FEED_NAME));
            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(MetaFields.REC_READ.getName(), "" + 1000 * i);
            attributeMap.put(MetaFields.REC_WRITE.getName(), "" + 100 * i);
            metaValueDao.addAttributes(myMeta, attributeMap);
            totalMetaCount += 2; // parent + myMeta
            test2FeedCount += 2; // parent + myMeta
        }

        metaValueDao.flush();
        // Unlock all streams.
        unlockAllStreams();
    }

    private void unlockAllStreams() {
        metaDao.updateStatus(new FindMetaCriteria(ExpressionOperator.builder().build()),
                Status.LOCKED,
                Status.UNLOCKED,
                System.currentTimeMillis());
    }

    private List<EffectiveMeta> populateDbWithRefStreams(final Instant baseEffectiveTime) {


        final List<EffectiveMeta> effectiveMetaList = new ArrayList<>();
        // 10 raw + 10 cooked ref with effective times 1 day apart
        for (int i = 0; i < 10; i++) {

            final Meta parent = metaDao.create(createRawRefProperties(
                    REF1_FEED_NAME,
                    baseEffectiveTime.plus(i, ChronoUnit.DAYS)));
            final Meta myMeta = metaDao.create(createProcessedRefMetaProperties(parent, REF1_FEED_NAME));
            final EffectiveMeta effectiveMeta = new EffectiveMeta(myMeta);
            effectiveMetaList.add(effectiveMeta);

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(MetaFields.REC_READ.getName(), "" + 100 * i);
            attributeMap.put(MetaFields.REC_WRITE.getName(), "" + 10 * i);
            metaValueDao.addAttributes(myMeta, attributeMap);
            totalMetaCount += 2; // parent + myMeta
            test1FeedCount += 2; // parent + myMeta
        }
        unlockAllStreams();

        return effectiveMetaList;
    }

    private void makeCreateTimesOlder() {
        final long newCreateTimeMs = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
        // Make sure all meta records are a day or so old
        JooqUtil.context(metaDbConnProvider, context -> {
            context
                    .update(meta)
                    .set(meta.CREATE_TIME, newCreateTimeMs)
                    .execute();
        });
    }

    @TestFactory
    Stream<DynamicTest> testLogicalDelete() {
        final TimePeriod allTimePeriod = TimePeriod.fromEpochTo(Instant.now());

        LOGGER.doIfDebugEnabled(this::dumpMetaTableToDebug);

        return TestUtil.buildDynamicTestStream()
                .withInputType(ExpressionOperator.class)
                .withOutputType(Integer.class)
                .withTestFunction(testCase -> {
                    final ExpressionOperator expressionOperator = testCase.getInput();
                    final List<DataRetentionRuleAction> ruleActions = buildRuleActions(expressionOperator);
                    return metaDao.logicalDelete(ruleActions, allTimePeriod);
                })
                .withSimpleEqualityAssertion()

                // Delete all
                .addCase(ExpressionOperator.builder().build(), totalMetaCount)

                // Delete one feed only
                .addCase(ExpressionOperator.builder()
                                .addTerm(createFeedTerm(TEST1_FEED, true))
                                .build(),
                        test1FeedCount)

                // Delete one feed that exists and one that doesn't
                .addCase(ExpressionOperator.builder()
                                .op(Op.OR)
                                .addTerm(createFeedTerm(TEST1_FEED, true))
                                .addTerm(createFeedTerm(TEST3_FEED, true))
                                .build(),
                        test1FeedCount)

                // Delete everything that is not feed3, i.e. all rows
                .addCase(ExpressionOperator.builder()
                                .op(Op.NOT)
                                .addTerm(createFeedTerm(TEST3_FEED, true))
                                .build(),
                        totalMetaCount)

                // Delete all processed by test1 pipeline
                .addCase(ExpressionOperator.builder()
                                .addTerm(createPipelineTerm(getPipelineUuid(TEST1_FEED.getName()), true))
                                .build(),
                        test1FeedCount / 2) // not the parent metas

                .withBeforeTestCaseAction(() -> {
                    populateDb();
                    makeCreateTimesOlder();
                })
                .build();
    }

    private void dumpMetaTableToDebug() {
        JooqUtil.context(metaDbConnProvider, context -> {
            final var metaRows = context
                    .select(
                            meta.ID,
                            meta.CREATE_TIME,
                            meta.EFFECTIVE_TIME,
                            meta.PARENT_ID,
                            meta.STATUS,
                            meta.FEED_ID,
                            metaFeed.NAME,
                            metaProcessor.PIPELINE_UUID,
                            metaType.NAME)
                    .from(meta)
                    .straightJoin(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                    .straightJoin(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                    .leftOuterJoin(metaProcessor).on(meta.PROCESSOR_ID.eq(metaProcessor.ID))
                    .orderBy(meta.ID)
                    .stream()
                    .collect(Collectors.toList());

            LOGGER.debug("meta rows:\n{}",
                    AsciiTable.builder(metaRows)
                            .withColumn(Column.of("Id", row -> row.get(meta.ID)))
                            .withColumn(Column.of("Create Time", row ->
                                    Instant.ofEpochMilli(row.get(meta.CREATE_TIME))
                                            + " (" + row.get(meta.CREATE_TIME) + ")"))
                            .withColumn(Column.of("Effective Time", row ->
                                    Instant.ofEpochMilli(row.get(meta.EFFECTIVE_TIME))
                                            + " (" + row.get(meta.EFFECTIVE_TIME) + ")"))
                            .withColumn(Column.of("Parent Id", row -> row.get(meta.PARENT_ID)))
                            .withColumn(Column.of("Status", row -> row.get(meta.STATUS)))
                            .withColumn(Column.of("Feed", row ->
                                    row.get(metaFeed.NAME) + " (" + row.get(meta.FEED_ID) + ")"))
                            .withColumn(Column.of("Pipe UUID", row -> row.get(metaProcessor.PIPELINE_UUID)))
                            .withColumn(Column.of("Type Name", row -> row.get(metaType.NAME)))
                            .build());
        });
    }


    private List<DataRetentionRuleAction> buildRuleActions(final ExpressionOperator expressionOperator) {
        final DataRetentionRuleAction dataRetentionRuleAction = new DataRetentionRuleAction(
                new DataRetentionRule(
                        1,
                        Instant.now().toEpochMilli(),
                        "My Rule",
                        true,
                        expressionOperator,
                        1,
                        TimeUnit.MINUTES,
                        false),
                RetentionRuleOutcome.DELETE);
        return List.of(dataRetentionRuleAction);
    }

    @TestFactory
    Stream<DynamicTest> testFind() {
        final AtomicInteger testNo = new AtomicInteger(1);
        return Stream.of(

                // Find all.
                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder().build(), totalMetaCount),

                // Find feed 1.
                makeTest(testNo.getAndIncrement(), MetaExpressionUtil.createFeedExpression(
                        TEST1_FEED_NAME), 20),

                // Find feed 2.
                makeTest(testNo.getAndIncrement(), MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME), 20),

                // Find both feeds.
                makeTest(testNo.getAndIncrement(), MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME,
                        TEST2_FEED_NAME), totalMetaCount),

                // Find none.
                makeTest(testNo.getAndIncrement(), MetaExpressionUtil.createFeedsExpression(), 0),

                // Find with doc ref.
                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, true))
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build(), 20),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, true))
                        .addTerm(createFeedTerm(TEST2_FEED, true))
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build(), 0),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, true))
                        .addTerm(createFeedTerm(TEST2_FEED, true))
                        .build(), 0),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, true))
                        .addTerm(createFeedTerm(TEST2_FEED, false))
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build(), 20),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, false))
                        .addTerm(createFeedTerm(TEST2_FEED, true))
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build(), 20),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, false))
                        .addTerm(createFeedTerm(TEST2_FEED, false))
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, false))
                        .addTerm(createFeedTerm(TEST2_FEED, false))
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, false))
                        .addTerm(createFeedTerm(TEST2_FEED, false))
                        .addTerm(createFeedTerm(TEST3_FEED, false))
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(createFeedTerm(TEST1_FEED, false))
                        .addTerm(createFeedTerm(TEST2_FEED, false))
                        .addTerm(createFeedTerm(TEST3_FEED, true))
                        .build(), 0),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addTerm(MetaFields.FEED, Condition.EQUALS, TEST1_FEED_NAME)
                        .addTerm(MetaFields.TYPE, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                        .build(), 10),

                // Or tests.
                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addOperator(
                                ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(createFeedTerm(TEST1_FEED, true))
                                        .addTerm(createFeedTerm(TEST2_FEED, true))
                                        .build())
                        .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addOperator(
                                ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(createFeedTerm(TEST1_FEED, true))
                                        .addTerm(createFeedTerm(TEST2_FEED, true))
                                        .build())
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addOperator(
                                ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(createFeedTerm(TEST1_FEED, true))
                                        .addTerm(createFeedTerm(TEST2_FEED, false))
                                        .build())
                        .build(), 20),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addOperator(
                                ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(createFeedTerm(TEST1_FEED, false))
                                        .addTerm(createFeedTerm(TEST2_FEED, false))
                                        .build())
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addOperator(
                                ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(createFeedTerm(TEST1_FEED, false))
                                        .addTerm(createFeedTerm(TEST2_FEED, false))
                                        .addTerm(createFeedTerm(TEST3_FEED, false))
                                        .build())
                        .build(), totalMetaCount),

                makeTest(testNo.getAndIncrement(), ExpressionOperator.builder()
                        .addOperator(
                                ExpressionOperator.builder()
                                        .op(Op.OR)
                                        .addTerm(createFeedTerm(TEST1_FEED, false))
                                        .addTerm(createFeedTerm(TEST2_FEED, false))
                                        .addTerm(createFeedTerm(TEST3_FEED, true))
                                        .build())
                        .build(), 0)
        ).sequential();
    }

    @Test
    void testComplexQuery() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(MetaFields.REC_READ.getName(), "" + 100);
        attributeMap.put(MetaFields.REC_WRITE.getName(), "" + 10);
        attributeMap.put(MetaFields.REC_ERROR.getName(), "" + 100);
        attributeMap.put(MetaFields.REC_FATAL.getName(), "" + 10);

        final Meta parent = metaDao.create(createRawProperties(TEST1_FEED_NAME));
        final Meta myMeta = metaDao.create(createErrorProperties(parent, TEST1_FEED_NAME));

        metaValueDao.addAttributes(myMeta, attributeMap);

        metaValueDao.flush();
        // Unlock all streams.
        unlockAllStreams();

        ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(MetaFields.STATUS, Condition.EQUALS, "Unlocked")
                .addTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN, "2000-01-01T00:00:00.000Z")
                .addTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                .addTerm(MetaFields.ID, Condition.EQUALS, myMeta.getId())
                .build();
        ResultPage<Meta> resultPage = metaDao.find(new FindMetaCriteria(expression));
        assertThat(resultPage.size()).isOne();

        expression = ExpressionOperator.builder()
                .addTerm(MetaFields.STATUS, Condition.EQUALS, "Unlocked")
                .addTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN, "2000-01-01T00:00:00.000Z")
                .addTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                .addTerm(MetaFields.ID, Condition.EQUALS, myMeta.getId())
                .addOperator(
                        ExpressionOperator.builder()
                                .op(Op.OR)
                                .addTerm(MetaFields.REC_ERROR, Condition.GREATER_THAN, 0)
                                .addTerm(MetaFields.REC_FATAL, Condition.GREATER_THAN, 0)
                                .build()
                )
                .build();
        resultPage = metaDao.find(new FindMetaCriteria(expression));
        assertThat(resultPage.size()).isOne();
    }

    private DynamicTest makeTest(final int testNo, final ExpressionOperator expression, final int expected) {
        return DynamicTest.dynamicTest(
                Strings.padStart(String.valueOf(testNo), 2, '0')
                        + " - "
                        + expression.toString(),
                () -> {
                    final ResultPage<Meta> resultPage = metaDao.find(new FindMetaCriteria(expression));
                    assertThat(resultPage.size()).isEqualTo(expected);
                });
    }

    private ExpressionTerm createFeedTerm(final DocRef feed, boolean enabled) {
        return ExpressionTerm
                .builder()
                .field(MetaFields.FEED.getName())
                .condition(Condition.IS_DOC_REF)
                .docRef(feed)
                .enabled(enabled)
                .build();
    }

    private ExpressionTerm createPipelineTerm(final String pipelineUuid, boolean enabled) {
        return ExpressionTerm
                .builder()
                .field(MetaFields.PIPELINE.getName())
                .condition(Condition.IS_DOC_REF)
                .docRef(DocRef.builder()
                        .type(PipelineDoc.DOCUMENT_TYPE)
                        .uuid(pipelineUuid)
                        .build())
                .enabled(enabled)
                .build();
    }

    @Test
    void testExtendedFind() {
        ResultPage<Meta> resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(
                TEST1_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(20);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(20);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME,
                TEST2_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(totalMetaCount);

        resultPage = metaDao.find(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(resultPage.size())
                .isEqualTo(0);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm(MetaFields.FEED, Condition.EQUALS, TEST1_FEED_NAME)
                        .addTerm(MetaFields.FEED, Condition.EQUALS, TEST2_FEED_NAME)
                        .build())
                .addTerm(MetaFields.TYPE, Condition.EQUALS, PROCESSED_STREAM_TYPE_NAME)
                .addTerm(MetaFields.REC_WRITE.getName(), Condition.EQUALS, "0")
                .addTerm(MetaFields.REC_READ.getName(), Condition.GREATER_THAN_OR_EQUAL_TO, "0")
                .build();

        System.err.println("About to find...");
        resultPage = metaDao.find(new FindMetaCriteria(expression));
        assertThat(resultPage.size())
                .isEqualTo(2);
    }

    @Test
    void testFindReprocess() {
        ResultPage<Meta> resultPage = metaDao.findReprocess(
                new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(10);

        resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(
                TEST2_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(10);

        resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(
                TEST1_FEED_NAME,
                TEST2_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(20);

        resultPage = metaDao.findReprocess(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(resultPage.size())
                .isEqualTo(0);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm(MetaFields.FEED, Condition.EQUALS, TEST1_FEED_NAME)
                        .addTerm(MetaFields.FEED, Condition.EQUALS, TEST2_FEED_NAME)
                        .build())
                .addTerm(MetaFields.TYPE, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        resultPage = metaDao.findReprocess(new FindMetaCriteria(expression));
        assertThat(resultPage.size())
                .isEqualTo(0);
    }

    @Test
    void testFindReprocess_ensureSingleParent() {
        final Meta parent = metaDao.create(createRawProperties(TEST1_FEED_NAME));
        final Meta processedMeta = metaDao.create(createProcessedProperties(parent, TEST1_FEED_NAME));
        final Meta errorMeta = metaDao.create(createErrorProperties(parent, TEST1_FEED_NAME));

        metaValueDao.flush();
        // Unlock all streams.
        unlockAllStreams();

        ResultPage<Meta> resultPage = metaDao.findReprocess(
                new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(resultPage.size())
                .isEqualTo(11);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addTerm(MetaFields.ID, Condition.EQUALS, processedMeta.getId())
                        .addTerm(MetaFields.ID, Condition.EQUALS, errorMeta.getId())
                        .build())
                .build();
        resultPage = metaDao.findReprocess(new FindMetaCriteria(expression));
        assertThat(resultPage.size())
                .isOne();
    }

    @Test
    void testGetSelectionSummary() {
        SelectionSummary selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(
                MetaExpressionUtil.createFeedExpression(TEST1_FEED_NAME)));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(20);

        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(
                TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(20);

        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(
                TEST1_FEED_NAME,
                TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(totalMetaCount);

        selectionSummary = metaDao.getSelectionSummary(
                new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(0);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(MetaFields.FEED, Condition.EQUALS, TEST1_FEED_NAME)
                .addTerm(MetaFields.TYPE, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        selectionSummary = metaDao.getSelectionSummary(new FindMetaCriteria(expression));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(10);
    }

    @Test
    void testGetReprocessSelectionSummary() {
        SelectionSummary selectionSummary = metaDao.getReprocessSelectionSummary(
                new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(
                        TEST1_FEED_NAME)));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(10);

        selectionSummary = metaDao.getReprocessSelectionSummary(
                new FindMetaCriteria(MetaExpressionUtil.createFeedExpression(TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(10);

        selectionSummary = metaDao.getReprocessSelectionSummary(
                new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression(TEST1_FEED_NAME, TEST2_FEED_NAME)));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(20);

        selectionSummary = metaDao.getReprocessSelectionSummary(
                new FindMetaCriteria(MetaExpressionUtil.createFeedsExpression()));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(0);

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(MetaFields.FEED, Condition.EQUALS, TEST1_FEED_NAME)
                .addTerm(MetaFields.TYPE, Condition.EQUALS, RAW_STREAM_TYPE_NAME)
                .build();
        selectionSummary = metaDao.getReprocessSelectionSummary(new FindMetaCriteria(expression));
        assertThat(selectionSummary.getItemCount())
                .isEqualTo(0);
    }

    @TestFactory
    Stream<DynamicTest> testGetEffectiveStreams() {
        final Instant baseEffectiveTime = LocalDateTime.of(2022, 1, 1, 1, 0)
                .toInstant(ZoneOffset.UTC);
        // Starting at the above time, creates 10 ref streams each a day apart
        final List<EffectiveMeta> allEffectiveMetaData = populateDbWithRefStreams(baseEffectiveTime);
        final Set<Instant> allTimes = allEffectiveMetaData.stream()
                .map(EffectiveMeta::getEffectiveMs)
                .map(Instant::ofEpochMilli)
                .collect(Collectors.toSet());

        final Instant time1 = baseEffectiveTime.plus(1, ChronoUnit.DAYS);
        final Instant time2 = baseEffectiveTime.plus(2, ChronoUnit.DAYS);
        final Instant time3 = baseEffectiveTime.plus(3, ChronoUnit.DAYS);

        LOGGER.doIfDebugEnabled(this::dumpMetaTableToDebug);

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(Instant.class, Instant.class)
                .withWrappedOutputType(new TypeLiteral<Set<Instant>>() {
                })
                .withTestFunction(testCase -> {
                    final EffectiveMetaDataCriteria criteria = new EffectiveMetaDataCriteria(
                            new Period(testCase.getInput()._1.toEpochMilli(), testCase.getInput()._2.toEpochMilli()),
                            REF1_FEED_NAME,
                            REF_STREAM_TYPE_NAME);

                    return metaDao.getEffectiveStreams(criteria)
                            .stream()
                            .map(EffectiveMeta::getEffectiveMs)
                            .map(Instant::ofEpochMilli)
                            .collect(Collectors.toSet());
                })
                .withAssertions(testOutcome -> {
                    assertThat(testOutcome.getActualOutput())
                            .containsExactlyInAnyOrderElementsOf(testOutcome.getExpectedOutput());
                })
                .addNamedCase(
                        "No effective streams", // Range before all streams
                        Tuple.of(
                                baseEffectiveTime.minus(10, ChronoUnit.DAYS),
                                baseEffectiveTime.minus(5, ChronoUnit.DAYS)),
                        Collections.emptySet())
                .addNamedCase(
                        "No streams in range", // Will get one stream prior
                        Tuple.of(
                                time1.plusSeconds(5),
                                time1.plusSeconds(10)),
                        Set.of(time1))
                .addNamedCase(
                        "One in range", // Will get one stream prior + one in range
                        Tuple.of(
                                time1.plusSeconds(5),
                                time1.plusSeconds(5).plus(1, ChronoUnit.DAYS)),
                        Set.of(
                                time1,
                                time2))
                .addNamedCase(
                        "Two in range", // Will get one stream prior + two in range
                        Tuple.of(
                                time1.plusSeconds(5),
                                time1.plusSeconds(5).plus(2, ChronoUnit.DAYS)),
                        Set.of(
                                time1,
                                time2,
                                time3))
                .build();
    }

    @Test
    void testGetLogicallyDeleted() {
        final List<Meta> metaList = new ArrayList<>();
        final Instant baseTime = addDeletedData(metaList);

        final Instant threshold = baseTime
                .minus(2, ChronoUnit.DAYS)
                .plus(1, ChronoUnit.HOURS);

        final List<SimpleMeta> simpleMetas = metaDao.getLogicallyDeleted(threshold, 4, Collections.emptySet());

        assertThat(simpleMetas)
                .hasSize(4);

        assertThat(simpleMetas)
                .extracting(SimpleMeta::getId)
                .containsExactly(
                        metaList.get(2).getId(),
                        metaList.get(3).getId(),
                        metaList.get(4).getId(),
                        metaList.get(5).getId());

        assertThat(simpleMetas)
                .extracting(SimpleMeta::getFeedName)
                .containsOnly(TEST1_FEED_NAME);

        assertThat(simpleMetas)
                .extracting(SimpleMeta::getTypeName)
                .containsOnly(RAW_STREAM_TYPE_NAME);

        for (final SimpleMeta simpleMeta : simpleMetas) {
            assertThat(Instant.ofEpochMilli(simpleMeta.getStatusMs()))
                    .isBeforeOrEqualTo(threshold);
        }
    }

    private Instant addDeletedData(final List<Meta> metaList) {
        assertThat(JooqUtil.getTableCount(metaDbConnProvider, meta))
                .isEqualTo(40);

//        dumpMetaTable();

        final Instant baseTime = LocalDateTime.of(
                        2016, 6, 20, 14, 0, 0)
                .toInstant(ZoneOffset.UTC);

        LOGGER.debug("baseTime: {}", baseTime);
        for (int i = 0; i < 10; i++) {
            final Instant time = baseTime.minus(Duration.ofDays(i));
            LOGGER.debug("time: {}", time);
            final Meta meta = metaDao.create(createRawProperties(TEST1_FEED_NAME, time));
            meta.setStatus(Status.DELETED);
            metaList.add(meta);
        }
        // Logically delete all the added ones
        JooqUtil.context(metaDbConnProvider, context ->
                context.update(meta)
                        .set(meta.STATUS, MetaStatusId.DELETED)
                        .where(meta.ID.in(metaList.stream()
                                .map(Meta::getId)
                                .collect(Collectors.toSet())))
                        .execute());

        assertThat(JooqUtil.getTableCount(metaDbConnProvider, meta))
                .isEqualTo(40 + 10);

        return baseTime;
    }

    private void dumpMetaTable() {
        JooqUtil.context(metaDbConnProvider, context ->
                LOGGER.debug("processor:\n{}", JooqUtil.toAsciiTable(context.select(
                                meta.ID,
                                meta.STATUS,
                                metaType.NAME,
                                metaFeed.NAME,
                                meta.CREATE_TIME,
                                meta.STATUS_TIME)
                        .from(meta)
                        .straightJoin(metaType).on(meta.TYPE_ID.eq(metaType.ID))
                        .straightJoin(metaFeed).on(meta.FEED_ID.eq(metaFeed.ID))
                        .orderBy(meta.ID)
                        .fetch(), false)));
    }

    private MetaProperties createRawProperties(final String feedName) {
        return MetaProperties.builder()
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .typeName(RAW_STREAM_TYPE_NAME)
                .build();
    }

    private MetaProperties createRawProperties(final String feedName, final Instant time) {
        return MetaProperties.builder()
                .createMs(time.toEpochMilli())
                .statusMs(time.toEpochMilli())
                .feedName(feedName)
                .typeName(RAW_STREAM_TYPE_NAME)
                .build();
    }

    private MetaProperties createRawRefProperties(final String feedName, final Instant effectiveTime) {
        return MetaProperties.builder()
                .createMs(System.currentTimeMillis())
                .effectiveMs(effectiveTime.toEpochMilli())
                .feedName(feedName)
                .typeName(RAW_REF_STREAM_TYPE_NAME)
                .build();
    }

    private MetaProperties createProcessedRefMetaProperties(final Meta parent,
                                                            final String feedName) {
        Objects.requireNonNull(parent.getEffectiveMs());
        return MetaProperties.builder()
                .parent(parent)
                .effectiveMs(parent.getEffectiveMs())
                .feedName(feedName)
                .processorUuid(getProcessorUuid(feedName))
                .pipelineUuid(getPipelineUuid(feedName))
                .typeName(REF_STREAM_TYPE_NAME)
                .build();
    }

    private MetaProperties createProcessedProperties(final Meta parent, final String feedName) {
        return createMetaProperties(parent, feedName, PROCESSED_STREAM_TYPE_NAME);
    }

    private MetaProperties createErrorProperties(final Meta parent, final String feedName) {
        return createMetaProperties(parent, feedName, StreamTypeNames.ERROR);
    }

    private MetaProperties createMetaProperties(final Meta parent, final String feedName, final String typeName) {
        return MetaProperties.builder()
                .parent(parent)
                .createMs(System.currentTimeMillis())
                .feedName(feedName)
                .processorUuid(getProcessorUuid(feedName))
                .pipelineUuid(getPipelineUuid(feedName))
                .typeName(typeName)
                .build();
    }

    private String getPipelineUuid(final String feedName) {
        return feedName.toUpperCase() + "_PIPELINE_UUID";
    }

    private String getProcessorUuid(final String feedName) {
        return feedName.toUpperCase() + "_PROCESSOR_UUID";
    }
}
