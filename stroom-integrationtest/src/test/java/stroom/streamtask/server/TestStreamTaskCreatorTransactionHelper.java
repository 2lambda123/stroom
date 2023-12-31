/*
 * Copyright 2016 Crown Copyright
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
 */

package stroom.streamtask.server;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.CommonTestScenarioCreator;
import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.feed.shared.Feed;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamTask;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class TestStreamTaskCreatorTransactionHelper extends AbstractCoreIntegrationTest {

    public static final StroomLogger LOGGER = StroomLogger.getLogger(TestStreamTaskCreatorTransactionHelper.class);

    @Resource
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private StreamTaskCreatorTransactionHelper streamTaskCreatorTransactionHelper;
    @Resource
    private StreamTaskDeleteExecutor streamTaskDeleteExecutor;
    @Resource
    private StroomEntityManager stroomEntityManager;

    @Test
    public void testBasic() {
        final Feed feed1 = commonTestScenarioCreator.createSimpleFeed();

        commonTestScenarioCreator.createSample2LineRawFile(feed1, StreamType.RAW_EVENTS);
        Assert.assertEquals(0, commonTestControl.countEntity(StreamTask.class));

        FindStreamCriteria findStreamCriteria = new FindStreamCriteria();
        Assert.assertEquals(1,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1);
        Assert.assertEquals(1,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFeeds().obtainInclude().add(feed1.getId() + 1);
        Assert.assertEquals(0,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainPipelineIdSet().add(1L);
        Assert.assertEquals(0,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFolderIdSet().add(feed1.getFolder());
        Assert.assertEquals(1,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());

        findStreamCriteria = new FindStreamCriteria();
        findStreamCriteria.obtainFolderIdSet().add(feed1.getFolder().getId() + 1);
        Assert.assertEquals(0,
                streamTaskCreatorTransactionHelper.runSelectStreamQuery(null, findStreamCriteria, 0, 100).size());
    }

    @Test
    public void testDeleteQuery() {
        streamTaskDeleteExecutor.delete(0);
    }

    @Ignore //performance test to compare time
    @Test
    public void testMultiInsertPerformance() throws SQLException {

        /*
        create table insert_test (
            id INT,
            col2 varchar(255),
            col3 varchar(255),
            col4 varchar(255),
            col5 varchar(255),
            col6 varchar(255),
            col7 varchar(255),
            col8 varchar(255),
            col9 varchar(255),
            col10 varchar(255),
            col11 varchar(255)
        );
         */

        stroomEntityManager.executeNativeUpdate(new SqlBuilder("delete from insert_test"));
        stroomEntityManager.executeNativeUpdate(new SqlBuilder("delete from insert_test2"));
        stroomEntityManager.executeNativeUpdate(new SqlBuilder("delete from insert_test3"));

        SqlBuilder singleStmt = new SqlBuilder();
        singleStmt.append("insert into insert_test (id, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11)")
                .append(" values (")
                .arg(1).append(",")
                .arg("col2 text").append(",")
                .arg("col3 text").append(",")
                .arg("col4 text").append(",")
                .arg("col5 text").append(",")
                .arg("col6 text").append(",")
                .arg("col7 text").append(",")
                .arg("col8 text").append(",")
                .arg("col9 text").append(",")
                .arg("col10 text").append(",")
                .arg("col11 text")
                .append(")");

        LOGGER.info("Inserting records one by one");

        LOGGER.info("SQL: %s", singleStmt.toString());

        int n = 10_000;
        int batchSize = 1;

        Instant startTime = Instant.now();

        IntStream.rangeClosed(1, n).forEach(i -> {
            Object[] args = {i, "x", "x", "x", "x", "x", "x", "x", "x", "x", "x"};
            SqlBuilder stmt = new SqlBuilder(singleStmt.toString(), args);
//            dumpSqlBuilder(stmt);
            stroomEntityManager.executeNativeUpdate(stmt);
        });

        LOGGER.info("Finished %s inserts in %s", n, Duration.between(startTime, Instant.now()));
        LOGGER.info("Batch size: %s", batchSize);

        SqlBuilder multiStmt = null;
        String header = "insert into insert_test2 (id, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11) values ";

        int qryCnt = 0;
        startTime = Instant.now();

        for (int i = 1; i <= n; i++) {
            if (multiStmt == null) {
                multiStmt = new SqlBuilder();
                multiStmt.append(header);
            }
            multiStmt
                    .append("(")
                    .arg(i).append(",")
                    .arg("col2 text").append(",")
                    .arg("col3 text").append(",")
                    .arg("col4 text").append(",")
                    .arg("col5 text").append(",")
                    .arg("col6 text").append(",")
                    .arg("col7 text").append(",")
                    .arg("col8 text").append(",")
                    .arg("col9 text").append(",")
                    .arg("col10 text").append(",")
                    .arg("col11 text")
                    .append(")");

            if (i % batchSize == 0) {
                try {
                    stroomEntityManager.executeNativeUpdate(multiStmt);
                    qryCnt++;
                } catch (Exception e) {
                    dumpSqlBuilder(multiStmt);
                    throw e;
                }
                multiStmt = null;
            } else {
                multiStmt.append(",");
            }
        }

        LOGGER.info("Finished %s hibernate multi inserts in %s", qryCnt, Duration.between(startTime, Instant.now()));

        String header3 = "insert into insert_test3 (id, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11) values ";
        qryCnt = 0;
        StringBuilder stringBuilder = null;
        List<Object> args = null;
        startTime = Instant.now();

        try (Connection connection = ConnectionUtil.getConnection()) {
            for (int i = 1; i <= n; i++) {
                if (stringBuilder == null) {
                    stringBuilder = new StringBuilder();
                    stringBuilder.append(header3);
                    args = new ArrayList<>();
                }

                args.add(i);
                args.add("col2 text");
                args.add("col3 text");
                args.add("col4 text");
                args.add("col5 text");
                args.add("col6 text");
                args.add("col7 text");
                args.add("col8 text");
                args.add("col9 text");
                args.add("col10 text");
                args.add("col11 text");

                stringBuilder.append("(?,?,?,?,?,?,?,?,?,?,?)");

                if (i % batchSize == 0) {
                    try {
                        final int count = ConnectionUtil.executeUpdate(
                                connection,
                                stringBuilder.toString(),
                                args);

                        qryCnt++;
                    } catch (Exception e) {
                        dumpStringBuilder(stringBuilder, args);
                        throw e;
                    }
                    stringBuilder = null;
                    args = null;
                } else {
                    stringBuilder.append(",");
                }
            }
        }

        LOGGER.info("Finished %s direct multi inserts in %s", qryCnt, Duration.between(startTime, Instant.now()));
    }

    private void dumpSqlBuilder(final SqlBuilder sqlBuilder) {

        String argsStr = StreamSupport.stream(sqlBuilder.getArgs().spliterator(), false)
                .map(Object::toString)
                .map(str -> "\"" + str + "\"")
                .collect(Collectors.joining(","));
        LOGGER.info("SQL: [%s], args [%s]", sqlBuilder.toString(), argsStr);
    }

    private void dumpStringBuilder(final StringBuilder stringBuilder, List<Object> args) {

        String argsStr = args.stream()
                .map(Object::toString)
                .map(str -> "\"" + str + "\"")
                .collect(Collectors.joining(","));
        LOGGER.info("SQL: [%s], args [%s]", stringBuilder.toString(), argsStr);
    }
}
