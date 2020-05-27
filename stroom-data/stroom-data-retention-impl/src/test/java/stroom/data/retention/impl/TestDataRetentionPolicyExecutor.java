package stroom.data.retention.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.data.retention.shared.TimeUnit;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContextFactory;
import stroom.util.time.TimePeriod;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataRetentionPolicyExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionPolicyExecutor.class);

    private static final ExpressionOperator EMPTY_AND_OP = new ExpressionOperator.Builder(
            true, ExpressionOperator.Op.AND).build();
    private static final String RULES_VERSION = "1234567";

    private ClusterLockService clusterLockService = new MockClusterLockService();
    private DataRetentionConfig dataRetentionConfig = new DataRetentionConfig();
    private TaskContextFactory taskContextFactory = new SimpleTaskContextFactory();

    @Mock
    private MetaService metaService;

    @Captor
    private ArgumentCaptor<List<DataRetentionRuleAction>> ruleExpressionsCaptor;

    @Captor
    private ArgumentCaptor<TimePeriod> periodCaptor;

    @Test
    void testDataRetention_fourRules1() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(4, true, 10, TimeUnit.DAYS),
                buildRule(3, true, 1, TimeUnit.MONTHS),
                buildRule(2, true, 1, TimeUnit.YEARS),
                buildForeverRule(1, true));

        final Instant now = Instant.now();

        runDataRretention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 3;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(10), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
    }

    @Test
    void testDataRetention_fourRules2() {

        final List<DataRetentionRule> rules = List.of(
                buildForeverRule(4, true),
                buildRule(3, true, 1, TimeUnit.YEARS),
                buildRule(2, true, 1, TimeUnit.MONTHS),
                buildRule(1, true, 1, TimeUnit.DAYS));

        final Instant now = Instant.now();

        runDataRretention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 3;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_fourRulesWithRecentTracker() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(4, true, 10, TimeUnit.DAYS),
                buildRule(3, true, 1, TimeUnit.MONTHS),
                buildRule(2, true, 1, TimeUnit.YEARS),
                buildForeverRule(1, true));

        final Instant now = Instant.now();
        final LocalDateTime nowUTC = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        DataRetentionTracker tracker = new DataRetentionTracker(
                now.minus(Duration.ofDays(2)), RULES_VERSION);

        runDataRretention(rules, now, tracker);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 3;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofDays(10).plusDays(2), Period.ofDays(10), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1).plusDays(2), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1).plusDays(2), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
    }

    @Test
    void testDataRetention_fourRulesWithOldTracker() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(4, true, 10, TimeUnit.DAYS),
                buildRule(3, true, 1, TimeUnit.MONTHS),
                buildRule(2, true, 2, TimeUnit.MONTHS),
                buildRule(1, true, 1, TimeUnit.YEARS)
        );

        final Instant now = Instant.now();

        int trackerAgeDays = 90;
        // Tracker is 90days old so should be ignored for periods:
        // 1month ago => 10days ago
        // 2months ago => 1months ago
        final DataRetentionTracker tracker = new DataRetentionTracker(
                now.minus(Duration.ofDays(trackerAgeDays)), RULES_VERSION);

        runDataRretention(rules, now, tracker);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 4;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        // period same as if no tracker as tracker is so old
        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(10), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(2), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(2).plusDays(trackerAgeDays), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1).plusDays(trackerAgeDays), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(4, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_noRules() {

        final List<DataRetentionRule> rules = Collections.emptyList();

        final Instant now = Instant.now();

        final DataRetentionPolicyExecutor dataRetentionPolicyExecutor = createExecutor(rules);

        dataRetentionPolicyExecutor.exec(now);

        Mockito.verifyZeroInteractions(metaService);
    }

    @Test
    void testDataRetention_oneRule() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 2, TimeUnit.MONTHS)
        );

        final Instant now = Instant.now();

        runDataRretention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 1;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_twoActiveOneInactive() {

        // rule 2 gets ignored
        final List<DataRetentionRule> rules = List.of(
                buildRule(3, true, 1, TimeUnit.MONTHS),
                buildRule(2, false, 2, TimeUnit.MONTHS),
                buildRule(1, true, 3, TimeUnit.MONTHS)
        );

        final Instant now = Instant.now();

        runDataRretention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 2;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(3), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(3), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_sameRuleAges() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(2, true, 2, TimeUnit.MONTHS),
                buildRule(1, true, 2, TimeUnit.MONTHS));

        final Instant now = Instant.now();

        runDataRretention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        int expectedPeriodCount = 1;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
    }

    private void runDataRretention(final List<DataRetentionRule> rules,
                                   final Instant now,
                                   final DataRetentionTracker tracker) {
        final DataRetentionPolicyExecutor dataRetentionPolicyExecutor = createExecutor(rules);

        when(metaService.delete(
                ruleExpressionsCaptor.capture(),
                periodCaptor.capture()))
                .thenReturn(0);

        when(metaService.getRetentionTracker())
                .thenReturn(Optional.ofNullable(tracker));

        dataRetentionPolicyExecutor.exec(now);
    }

    private DataRetentionPolicyExecutor createExecutor(final List<DataRetentionRule> rules) {
        return new DataRetentionPolicyExecutor(
                    clusterLockService,
                    () -> buildRules(rules),
                    dataRetentionConfig,
                    metaService,
                    taskContextFactory);
    }

    private void assertPeriod(final TimePeriod actualPeriod,
                              final Period expectedTimeSinceFrom,
                              final Period expectedTimeSinceTo,
                              final Instant now) {
        LOGGER.debug("actualPeriod: {}", actualPeriod);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getFrom(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC).minus(expectedTimeSinceFrom));
        assertThat(LocalDateTime.ofInstant(actualPeriod.getTo(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC).minus(expectedTimeSinceTo));
    }

    private void assertPeriod(final TimePeriod actualPeriod,
                              final Instant expectedFrom,
                              final Period expectedTimeSinceTo,
                              final Instant now) {
        LOGGER.debug("actualPeriod: {}", actualPeriod);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getFrom(), ZoneOffset.UTC))
                .isEqualTo(expectedFrom);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getTo(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC).minus(expectedTimeSinceTo));
    }

    private void assertEpochPeriod(final TimePeriod actualPeriod,
                                   final Period expectedTimeSinceTo,
                                   final Instant now) {
        assertThat(actualPeriod.getFrom())
                .isEqualTo(Instant.EPOCH);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getTo(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC).minus(expectedTimeSinceTo));
    }

    private void assertRuleActions(final List<DataRetentionRuleAction> actualRuleActions,
                                   final List<Tuple2<Integer, RetentionRuleOutcome>> expectedRuleOutcomes) {

        final List<Tuple2<Integer, RetentionRuleOutcome>> actualOutcomes = actualRuleActions.stream()
                .map(ruleAction ->
                        Tuple.of(ruleAction.getRule().getRuleNumber(), ruleAction.getOutcome())
                )
                .collect(Collectors.toList());

        assertThat(actualOutcomes)
                .containsExactlyElementsOf(expectedRuleOutcomes);
    }

    private DataRetentionRules buildRules(List<DataRetentionRule> rules) {
        final DataRetentionRules dataRetentionRules = new DataRetentionRules(rules);
        dataRetentionRules.setVersion(RULES_VERSION);
        return dataRetentionRules;
    }
    private DataRetentionRule buildRule(final int ruleNo,
                                        final boolean isEnabled,
                                        final int age,
                                        final TimeUnit timeUnit,
                                        final ExpressionOperator expressionOperator) {


        return DataRetentionRule.ageRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo + "_" + age + timeUnit.getDisplayValue(),
                isEnabled,
                expressionOperator,
                age,
                timeUnit);
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final boolean isEnabled,
                                        final int age,
                                        final TimeUnit timeUnit) {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(true, ExpressionOperator.Op.AND)
                .addTerm(MetaFields.FIELD_FEED, ExpressionTerm.Condition.EQUALS, "RULE_" + ruleNo + "FEED")
                .build();
        return buildRule(ruleNo, isEnabled, age, timeUnit, expressionOperator);
    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final boolean isEnabled) {
        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(true, ExpressionOperator.Op.AND)
                .addTerm(MetaFields.FIELD_FEED, ExpressionTerm.Condition.EQUALS, "RULE_" + ruleNo + "FEED")
                .build();
        return buildForeverRule(ruleNo, isEnabled, expressionOperator);

    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final boolean isEnabled,
                                               final ExpressionOperator expressionOperator) {
        return DataRetentionRule.foreverRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo + "_Forever",
                isEnabled,
                expressionOperator);
    }

}