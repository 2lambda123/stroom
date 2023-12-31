/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.search;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonIndexingTest;
import stroom.dashboard.server.ActiveQuery;
import stroom.dashboard.server.QueryMarshaller;
import stroom.dashboard.server.SearchDataSourceProviderRegistry;
import stroom.dashboard.server.SearchResultCreator;
import stroom.dashboard.shared.BasicQueryKey;
import stroom.dashboard.shared.ParamUtil;
import stroom.dashboard.shared.Query;
import stroom.dashboard.shared.Row;
import stroom.dashboard.shared.TableResult;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.shared.DocRef;
import stroom.index.shared.FindIndexCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.SearchDataSourceProvider;
import stroom.query.SearchResultCollector;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.ComponentSettings;
import stroom.query.shared.Condition;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionOperator.Op;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.Field;
import stroom.query.shared.Format;
import stroom.query.shared.QueryData;
import stroom.query.shared.Search;
import stroom.query.shared.SearchRequest;
import stroom.query.shared.SearchResult;
import stroom.query.shared.TableSettings;
import stroom.search.server.EventRef;
import stroom.search.server.EventRefs;
import stroom.search.server.EventSearchTask;
import stroom.search.server.LuceneSearchDataSourceProvider;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.util.config.StroomProperties;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.SharedObject;
import stroom.util.task.ServerTask;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class TestInteractiveSearch extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestInteractiveSearch.class);

    @Resource
    private CommonIndexingTest commonIndexingTest;
    @Resource
    private IndexService indexService;
    @Resource
    private QueryMarshaller queryMarshaller;
    @Resource
    private DictionaryService dictionaryService;
    @Resource
    private SearchDataSourceProviderRegistry searchDataSourceProviderRegistry;
    @Resource
    private TaskManager taskManager;
    @Resource
    private SearchResultCreator searchResultCreator;

    @Override
    protected boolean doSingleSetup() {
        commonIndexingTest.setup();
        return true;
    }

    /**
     * Positive case insensitive test.
     */
    @Test
    public void positiveCaseInsensitiveTest() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 5);
    }

    @Test
    public void positiveCaseInsensitiveTestMultiComponent() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        final String[] componentIds = new String[]{"table-1", "table-2"};
        test(expression, 5, componentIds, true);
    }

    /**
     * Positive case insensitive test.
     */
    @Test
    public void positiveCaseInsensitiveTestWithoutExtraction() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        final String[] componentIds = new String[]{"table-1"};
        test(expression, 5, componentIds, false);
    }

    /**
     * Positive case insensitive test with wildcard.
     */
    @Test
    public void positiveCaseInsensitiveTest2() {
        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description", "e0567");
        test(expression, 25);
    }
    
    /**
     * Negative test for case sensitive field.
     */
    @Test
    public void negativeCaseSensitiveTest() {
        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "e0567");
        test(expression, 0);
    }

    /**
     * Negative test for case sensitive field.
     */
    @Test
    public void negativeCaseSensitiveTest2() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "e0567");
        test(expression, 0);
    }

    /**
     * Positive test case sensitive field.
     */
    @Test
    public void positiveCaseSensitiveTest() {
        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 25);
    }

    /**
     * Test case sensitive field plus other field.
     */
    @Test
    public void positiveCaseSensitiveTest2() {
        final ExpressionOperator expression = buildExpression("UserId", "user5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 5);
    }

    /**
     * Test case sensitive field plus other field.
     */
    @Test
    public void positiveCaseSensitiveTest3() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        test(expression, 5);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg");
        test(expression, 4);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTestWithLeadingWildcard() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "*msg");
        test(expression, 4);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void negativeAnalysedFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg foobar");
        test(expression, 0);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void positiveAnalysedFieldTestWithIn() {
        final ExpressionOperator expression = buildInExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "msg foo bar");
        test(expression, 4);
    }

    /**
     * Negative test on keyword field.
     */
    @Test
    public void negativeKeywordFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "foo");
        test(expression, 0);
    }

    /**
     * Positive test on keyword field.
     */
    @Test
    public void positiveKeywordFieldTest() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "msg=foo bar");
        test(expression, 4);
    }

    /**
     * Positive test on keyword field.
     */
    @Test
    public void positiveKeywordFieldTestWithLeadingWildcard() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command (Keyword)", "*foo bar");
        test(expression, 4);
    }

    /**
     * Test not equals.
     */
    @Test
    public void notEqualsTest() {
        final ExpressionTerm eventTime = new ExpressionTerm();
        eventTime.setField("EventTime");
        eventTime.setCondition(Condition.EQUALS);
        eventTime.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.addChild(eventTime);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.addChild(not);
        test(expression, 24);
    }

    /**
     * Test exclusion of multiple items.
     */
    @Test
    public void notEqualsTest2() {
        final ExpressionTerm eventTime1 = new ExpressionTerm();
        eventTime1.setField("EventTime");
        eventTime1.setCondition(Condition.EQUALS);
        eventTime1.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionTerm eventTime2 = new ExpressionTerm();
        eventTime2.setField("EventTime");
        eventTime2.setCondition(Condition.EQUALS);
        eventTime2.setValue("2007-01-18T13:56:42.000Z");
        final ExpressionOperator or = new ExpressionOperator(Op.OR);
        or.addChild(eventTime1);
        or.addChild(eventTime2);
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.addChild(or);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.addChild(not);
        test(expression, 23);
    }

    /**
     * Test exclusion of multiple items.
     */
    @Test
    public void notEqualsTest3() {
        final ExpressionTerm eventTime1 = new ExpressionTerm();
        eventTime1.setField("EventTime");
        eventTime1.setCondition(Condition.EQUALS);
        eventTime1.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.EQUALS);
        user.setValue("user4");
        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.addChild(eventTime1);
        and.addChild(user);
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.addChild(and);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.addChild(not);
        test(expression, 24);
    }

    /**
     * Test more complex exclusion of multiple items.
     */
    @Test
    public void notEqualsTest4() {
        final ExpressionTerm eventTime1 = new ExpressionTerm();
        eventTime1.setField("EventTime");
        eventTime1.setCondition(Condition.EQUALS);
        eventTime1.setValue("2007-08-18T13:50:56.000Z");
        final ExpressionTerm eventTime2 = new ExpressionTerm();
        eventTime2.setField("EventTime");
        eventTime2.setCondition(Condition.EQUALS);
        eventTime2.setValue("2007-01-18T13:56:42.000Z");
        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.EQUALS);
        user.setValue("user4");
        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.addChild(eventTime1);
        and.addChild(user);
        final ExpressionOperator or = new ExpressionOperator(Op.OR);
        or.addChild(and);
        or.addChild(eventTime2);
        final ExpressionOperator not = new ExpressionOperator(Op.NOT);
        not.addChild(or);

        final ExpressionOperator expression = buildExpression("UserId", "user*", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Description (Case Sensitive)", "E0567");
        expression.addChild(not);
        test(expression, 23);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest1() {
        Dictionary dic = dictionaryService.create(null, "users");
        dic.setData("user1\nuser2\nuser5");
        dic = dictionaryService.save(dic);

        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.IN_DICTIONARY);
        user.setValue("users");
        user.setDictionary(DocRef.create(dic));

        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.addChild(user);

        test(and, 15);

        dictionaryService.delete(dic);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest2() {
        Dictionary dic1 = dictionaryService.create(null, "users");
        dic1.setData("user1\nuser2\nuser5");
        dic1 = dictionaryService.save(dic1);

        Dictionary dic2 = dictionaryService.create(null, "command");
        dic2.setData("msg");
        dic2 = dictionaryService.save(dic2);

        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.IN_DICTIONARY);
        user.setDictionary(DocRef.create(dic1));

        final ExpressionTerm command = new ExpressionTerm();
        command.setField("Command");
        command.setCondition(Condition.IN_DICTIONARY);
        command.setValue("command");
        command.setDictionary(DocRef.create(dic2));

        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.addChild(user);
        and.addChild(command);

        test(and, 10);

        dictionaryService.delete(dic1);
        dictionaryService.delete(dic2);
    }

    /**
     * Test the use of a dictionary.
     */
    @Test
    public void dictionaryTest3() {
        Dictionary dic1 = dictionaryService.create(null, "users");
        dic1.setData("user1\nuser2\nuser5");
        dic1 = dictionaryService.save(dic1);

        Dictionary dic2 = dictionaryService.create(null, "command");
        dic2.setData("msg foo bar");
        dic2 = dictionaryService.save(dic2);

        final ExpressionTerm user = new ExpressionTerm();
        user.setField("UserId");
        user.setCondition(Condition.IN_DICTIONARY);
        user.setValue("users");
        user.setDictionary(DocRef.create(dic1));

        final ExpressionTerm command = new ExpressionTerm();
        command.setField("Command");
        command.setCondition(Condition.IN_DICTIONARY);
        command.setValue("command");
        command.setDictionary(DocRef.create(dic2));

        final ExpressionOperator and = new ExpressionOperator(Op.AND);
        and.addChild(user);
        and.addChild(command);

        test(and, 10);

        dictionaryService.delete(dic1);
        dictionaryService.delete(dic2);
    }

    /**
     * Test analysed field search.
     */
    @Test
    public void testBug173() {
        final ExpressionOperator expression = buildExpression("UserId", "use*5", "2000-01-01T00:00:00.000Z",
                "2016-01-02T00:00:00.000Z", "Command", "!");
        test(expression, 5);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount) {
        final String[] componentIds = new String[]{"table-1"};
        test(expressionIn, expectResultCount, componentIds, true);
    }

    private void test(final ExpressionOperator expressionIn, final int expectResultCount, final String[] componentIds,
                      final boolean extractValues) {
        testInteractive(expressionIn, expectResultCount, componentIds, extractValues);
        testEvents(expressionIn, expectResultCount);
    }

    private void testInteractive(final ExpressionOperator expressionIn, final int expectResultCount,
                                 final String[] componentIds, final boolean extractValues) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRef.create(index);

        final Query query = buildQuery(dataSourceRef, expressionIn);
        final QueryData searchData = query.getQueryData();
        final ExpressionOperator expression = searchData.getExpression();

        final Map<String, ComponentSettings> resultComponentMap = new HashMap<String, ComponentSettings>();
        final Map<String, ComponentResultRequest> componentResultRequests = new HashMap<String, ComponentResultRequest>();
        for (final String componentId : componentIds) {
            final TableSettings tableSettings = createTableSettings(index);
            tableSettings.setExtractValues(extractValues);
            resultComponentMap.put(componentId, tableSettings);

            final TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings(tableSettings);
            tableResultRequest.setWantsData(true);
            componentResultRequests.put(componentId, tableResultRequest);
        }

        SearchResult result = null;
        boolean complete = false;
        final Map<String, SharedObject> results = new HashMap<String, SharedObject>();

        final Search search = new Search(searchData.getDataSource(), expression, resultComponentMap);
        final SearchRequest searchRequest = new SearchRequest(search, componentResultRequests);

        final SearchDataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                .getProvider(LuceneSearchDataSourceProvider.ENTITY_TYPE);
        final SearchResultCollector searchResultCollector = dataSourceProvider.createCollector(ServerTask.INTERNAL_PROCESSING_USER_TOKEN,
                new BasicQueryKey(query.getName()), searchRequest);
        final ActiveQuery activeQuery = new ActiveQuery(searchResultCollector);

        // Start asynchronous search execution.
        searchResultCollector.start();

        try {
            while (!complete) {
                result = searchResultCreator.createResult(activeQuery, searchRequest);

                // We need to remember results when they are returned as search
                // will no longer return duplicate results to prevent us
                // overwhelming the UI and transferring unnecessary data to the
                // client.
                if (result.getResults() != null) {
                    for (final Entry<String, SharedObject> entry : result.getResults().entrySet()) {
                        if (entry.getValue() != null) {
                            results.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                complete = result.isComplete();

                if (!complete) {
                    ThreadUtil.sleep(10);
                }
            }
            LOGGER.info("Search completed");
        } finally {
            searchResultCollector.destroy();
        }

        final Map<String, List<Row>> resultMap = new HashMap<String, List<Row>>();
        if (result != null) {
            if (results != null) {
                for (final Entry<String, SharedObject> entry : results.entrySet()) {
                    final String componentId = entry.getKey();
                    final TableResult tableResult = (TableResult) entry.getValue();

                    if (tableResult.getResultRange() != null && tableResult.getRows() != null) {
                        final OffsetRange<Integer> range = tableResult.getResultRange();

                        for (int i = range.getOffset(); i < range.getLength(); i++) {
                            List<Row> values = resultMap.get(componentId);
                            if (values == null) {
                                values = new ArrayList<Row>();
                                resultMap.put(componentId, values);
                            }
                            values.add(tableResult.getRows().get(i));
                        }
                    }
                }
            }
        }

        if (expectResultCount == 0) {
            Assert.assertEquals(0, resultMap.size());
        } else {
            Assert.assertEquals(componentIds.length, resultMap.size());
        }

        for (final List<Row> values : resultMap.values()) {
            if (expectResultCount == 0) {
                Assert.assertEquals(0, values.size());

            } else {
                // Make sure we got what we expected.
                Row firstResult = null;
                if (values != null && values.size() > 0) {
                    firstResult = values.get(0);
                }
                Assert.assertNotNull("No results found", firstResult);

                if (extractValues) {
                    final SharedObject time = firstResult.getValues()[1];
                    Assert.assertNotNull("Incorrect heading", time);
                    Assert.assertEquals("Incorrect number of hits found", expectResultCount, values.size());
                    boolean found = false;
                    for (final Row hit : values) {
                        final SharedObject obj = hit.getValues()[1];
                        final String str = obj.toString();
                        if ("2007-03-18T14:34:41.000".equals(str)) {
                            found = true;
                        }
                    }
                    Assert.assertTrue("Unable to find expected hit", found);
                }
            }
        }
    }

    private void testEvents(final ExpressionOperator expressionIn, final int expectResultCount) {
        // ADDED THIS SECTION TO TEST SPRING VALUE INJECTION.
        StroomProperties.setOverrideProperty("stroom.search.shard.concurrentTasks", "1", StroomProperties.Source.TEST);
        StroomProperties.setOverrideProperty("stroom.search.extraction.concurrentTasks", "1", StroomProperties.Source.TEST);

        final Index index = indexService.find(new FindIndexCriteria()).getFirst();
        Assert.assertNotNull("Index is null", index);
        final DocRef dataSourceRef = DocRef.create(index);

        final Query query = buildQuery(dataSourceRef, expressionIn);
        final QueryData searchData = query.getQueryData();
        final ExpressionOperator expression = searchData.getExpression();

        final CountDownLatch complete = new CountDownLatch(1);
        final Search search = new Search(searchData.getDataSource(), expression, null);

        final EventSearchTask eventSearchTask = new EventSearchTask(ServerTask.INTERNAL_PROCESSING_USER_TOKEN, new FindStreamCriteria(), search,
                new EventRef(1, 1), new EventRef(Long.MAX_VALUE, Long.MAX_VALUE), 1000, 1000, 1000, 100);
        final AtomicReference<EventRefs> results = new AtomicReference<>();
        taskManager.execAsync(eventSearchTask, new TaskCallback<EventRefs>() {
            @Override
            public void onSuccess(final EventRefs result) {
                results.set(result);
                complete.countDown();
            }

            @Override
            public void onFailure(final Throwable t) {
                complete.countDown();
            }
        });

        try {
            complete.await();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        final EventRefs result = results.get();

        int count = 0;
        if (result != null) {
            count += result.size();
        }

        Assert.assertEquals(expectResultCount, count);
    }

    private TableSettings createTableSettings(final Index index) {
        final TableSettings tableSettings = new TableSettings();

        final Field idField = new Field("Id");
        idField.setExpression(ParamUtil.makeParam("StreamId"));
        tableSettings.addField(idField);

        final Field timeField = new Field("Event Time");
        timeField.setExpression(ParamUtil.makeParam("EventTime"));
        timeField.setFormat(new Format(Format.Type.DATE_TIME));
        tableSettings.addField(timeField);

        final PipelineEntity resultPipeline = commonIndexingTest.getSearchResultPipeline();
        tableSettings.setExtractionPipeline(DocRef.create(resultPipeline));

        return tableSettings;
    }

    private Query buildQuery(final DocRef dataSourceRef, final ExpressionOperator expression) {
        final QueryData queryData = new QueryData();
        queryData.setExpression(expression);
        queryData.setDataSource(dataSourceRef);
        Query query = new Query();
        query.setQueryData(queryData);
        query = queryMarshaller.marshal(query);

        return query;
    }

    private ExpressionOperator buildExpression(final String userField, final String userTerm, final String from,
                                               final String to, final String wordsField, final String wordsTerm) {
        final ExpressionTerm userId = new ExpressionTerm();
        userId.setField(userField);
        userId.setCondition(Condition.CONTAINS);
        userId.setValue(userTerm);

        final ExpressionTerm eventTime = new ExpressionTerm();
        eventTime.setField("EventTime");
        eventTime.setCondition(Condition.BETWEEN);
        eventTime.setValue(from + "," + to);

        final ExpressionTerm words = new ExpressionTerm();
        words.setField(wordsField);
        words.setCondition(Condition.CONTAINS);
        words.setValue(wordsTerm);

        final ExpressionOperator operator = new ExpressionOperator();
        operator.addChild(userId);
        operator.addChild(eventTime);
        operator.addChild(words);

        return operator;
    }

    private ExpressionOperator buildInExpression(final String userField, final String userTerm, final String from,
                                                 final String to, final String wordsField, final String wordsTerm) {
        final ExpressionTerm userId = new ExpressionTerm();
        userId.setField(userField);
        userId.setCondition(Condition.CONTAINS);
        userId.setValue(userTerm);

        final ExpressionTerm eventTime = new ExpressionTerm();
        eventTime.setField("EventTime");
        eventTime.setCondition(Condition.BETWEEN);
        eventTime.setValue(from + "," + to);

        final ExpressionTerm words = new ExpressionTerm();
        words.setField(wordsField);
        words.setCondition(Condition.IN);
        words.setValue(wordsTerm);

        final ExpressionOperator operator = new ExpressionOperator();
        operator.addChild(userId);
        operator.addChild(eventTime);
        operator.addChild(words);

        return operator;
    }
}
